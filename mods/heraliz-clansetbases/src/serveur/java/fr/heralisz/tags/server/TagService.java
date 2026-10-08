package fr.heralisz.tags.server;

import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.TagsConfig;
import fr.heralisz.tags.catalogue.TagCatalog;
import fr.heralisz.tags.catalogue.TagDef;
import fr.heralisz.tags.network.Packets;
import fr.heralisz.tags.network.TagsNet;
import fr.heralisz.tags.particle.BrouillardOptions;
import fr.heralisz.tags.registry.Sons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Regles serveur des tags : deblocage, roue, pose (verifications du §2.3 du cahier des charges), plafonds, expiration.
 * La pose verifie tout cote serveur (possession, cadence, distance, joueur vivant, rayon rejoue jusqu'a la surface,
 * bloc solide derriere / libre devant) puis diffuse le tag, le son de spray et le brouillard de peinture.
 */
public final class TagService {
    private TagService() {
    }

    /** Tag actif cote serveur (pour les plafonds et l'expiration). */
    public record TagActif(long id, UUID joueur, String tagId, ServerLevel level, Vec3 point, Direction face, float angle, long creeLe) {
    }

    /** Resultat d'une demande de pose ; {@link #signaler} affiche au joueur les refus qui lui sont utiles. */
    public enum Refus {
        OK, INCONNU, PAS_POSSEDE, JOUEUR, CADENCE, TROP_LOIN, SURFACE, TRAIN
    }

    private static final Deque<TagActif> ACTIFS = new ArrayDeque<>();
    private static final Map<UUID, Long> DERNIERE_POSE = new HashMap<>();
    private static final Map<UUID, Long> DERNIER_MESSAGE = new HashMap<>();
    /** Tolerance (au carre) entre le point envoye par le client et le point touche par le rayon rejoue. */
    private static final double TOLERANCE_POINT_2 = 0.25 * 0.25;
    /** Marge de distance au-dela de la portee (blocs), plus 3 ticks du deplacement du joueur ou de son vehicule
     *  (avance du client sur le serveur), plafonnee a 3 blocs de plus. */
    private static final double MARGE = 1.0, TICKS_AVANCE = 3.0, MARGE_DEPLACEMENT_MAX = 3.0;
    private static long prochainId = 1;
    private static int ticks;

    // ------------------------------------------------------------------ etat
    /** Envoie au joueur ses tags debloques, sa roue et la portee de pose du serveur (celle que le serveur verifie : le
     *  rayon du client s'y cale, quelle que soit la config locale du client). */
    public static void envoyerEtat(ServerPlayer p) {
        TagStore.Joueur j = TagStore.get(p.getUUID());
        TagsNet.toPlayer(p, new Packets.EtatS2C(new ArrayList<>(j.debloques), j.roueListe(), TagsConfig.PORTEE.get().floatValue()));
    }

    /** Debloque un tag ; message au joueur ; vrai si nouveau (l'objet peut etre consomme). */
    public static boolean debloquer(ServerPlayer p, String id) {
        TagDef def = TagCatalog.get(id);
        if (def == null) {
            p.displayClientMessage(Component.translatable("msg.heralisz_tags.inconnu", id), true);
            return false;
        }
        boolean francais = fr.heralisz.tags.util.Langue.francais();
        if (!TagStore.debloquer(p.getUUID(), id)) {
            p.displayClientMessage(Component.translatable("msg.heralisz_tags.deja", def.nom(francais)), true);
            return false;
        }
        p.displayClientMessage(Component.translatable("msg.heralisz_tags.debloque", def.nom(francais)), true);
        // le son « tag debloque » est joue par le client a la reception de l'EtatS2C (un deblocage de plus)
        envoyerEtat(p);
        return true;
    }

    /** Retire un tag (op) ; vrai s'il etait possede. */
    public static boolean retirer(ServerPlayer p, String id) {
        boolean ok = TagStore.retirer(p.getUUID(), id);
        if (ok) envoyerEtat(p);
        return ok;
    }

    /** Nouvelle roue demandee par le client : nettoyee par TagStore puis renvoyee. */
    public static void roue(ServerPlayer p, List<String> ids) {
        TagStore.roue(p.getUUID(), ids);
        envoyerEtat(p);
    }

    // ------------------------------------------------------------------ pose
    /**
     * Pose d'un tag. Verifications (§2.3) : possession ; cadence (cadence_s par joueur) ; joueur vivant ; pas a bord du
     * train ; distance du point aux yeux (≤ portee + 1, plus l'avance du client quand le joueur ou son vehicule bouge) ;
     * rayon rejoue cote serveur (ClipContext OUTLINE des yeux vers le point, prolonge de 0,3 bloc) : il doit toucher un
     * bloc, sur la face annoncee, a moins de 0,25 bloc du point — sinon le point est derriere un mur, en l'air ou au
     * milieu d'une vitre ; le bloc touche a une forme de collision non vide (pas une touffe d'herbe) et le bloc devant
     * la face n'est pas plein. Le point diffuse est celui du rayon serveur.
     * <p>Les yeux du serveur peuvent avoir un tick de retard sur ceux du client (position, accroupi) : la distance et le
     * rayon sont essayes depuis les yeux du serveur maintenant, au tick precedent, debout et accroupi (voir
     * {@link #origines}) ; un seul suffit. Un passager (bateau, cheval, wagonnet) est traite comme un pieton : ses yeux
     * sont ceux que le serveur lui connait (portes par son vehicule), la marge suit la vitesse du vehicule.
     * <p>Plafonds (max_actifs global → le plus ancien retire par TagRetireS2C ; max_par_joueur → le plus ancien du
     * joueur retire). Le serveur choisit le roulis (±6°) et l'id unique, puis diffuse TagS2C (avec le cap du tagueur)
     * a moins de distance_diffusion blocs.
     */
    public static Refus poser(ServerPlayer p, Packets.TagC2S msg) {
        TagDef def = TagCatalog.get(msg.tagId());
        if (def == null) return Refus.INCONNU;
        UUID u = p.getUUID();
        // possession
        if (!TagStore.possede(u, def.id())) return Refus.PAS_POSSEDE;
        // cadence
        long now = System.currentTimeMillis();
        long derniere = DERNIERE_POSE.getOrDefault(u, 0L);
        if (now - derniere < TagsConfig.CADENCE_S.get() * 1000L) return Refus.CADENCE;
        // joueur vivant, pas spectateur
        if (!p.isAlive() || p.isSpectator() || p.isDeadOrDying()) return Refus.JOUEUR;
        // a bord du train : les blocs du monde defilent derriere les parois (voir Vehicule.dansLeTrain)
        if (fr.heralisz.tags.util.Vehicule.dansLeTrain(p)) return Refus.TRAIN;
        // distance aux yeux
        Vec3 point = msg.point();
        if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return Refus.SURFACE;
        double portee = TagsConfig.PORTEE.get() + marge(p);
        List<Vec3> yeux = new ArrayList<>(4);
        for (Vec3 o : origines(p)) if (o.distanceTo(point) <= portee) yeux.add(o);
        if (yeux.isEmpty()) return Refus.TROP_LOIN;
        // rayon rejoue depuis l'une des origines a portee (la premiere qui retrouve la face annoncee au point)
        ServerLevel level = p.serverLevel();
        BlockHitResult hr = null;
        for (Vec3 o : yeux) {
            hr = rejouer(level, p, o, point, msg.face());
            if (hr != null) break;
        }
        if (hr == null) return Refus.SURFACE;
        point = hr.getLocation();
        // bloc touche avec une forme de collision non vide, bloc devant la face non plein (sinon on taguerait
        // l'interieur d'un mur)
        BlockPos derriere = hr.getBlockPos();
        BlockPos devant = derriere.relative(msg.face());
        if (!level.isLoaded(derriere) || !level.isLoaded(devant)) return Refus.SURFACE;
        if (level.getBlockState(derriere).getCollisionShape(level, derriere).isEmpty()) return Refus.SURFACE;
        if (level.getBlockState(devant).isSolidRender(level, devant)) return Refus.SURFACE;

        DERNIERE_POSE.put(u, now);
        float angle = (float) (ThreadLocalRandom.current().nextDouble(-6.0, 6.0));
        TagActif t = new TagActif(prochainId++, u, def.id(), level, point, msg.face(), angle, now);
        // plafonds : par joueur puis global (le plus ancien saute)
        int duJoueur = 0;
        for (TagActif a : ACTIFS) if (a.joueur.equals(u)) duJoueur++;
        if (duJoueur >= TagsConfig.MAX_PAR_JOUEUR.get()) {
            for (Iterator<TagActif> it = ACTIFS.iterator(); it.hasNext(); ) {
                TagActif a = it.next();
                if (a.joueur.equals(u)) {
                    it.remove();
                    retirer(a);
                    break;
                }
            }
        }
        while (ACTIFS.size() >= TagsConfig.MAX_ACTIFS.get()) retirer(ACTIFS.pollFirst());
        ACTIFS.addLast(t);
        TagsNet.toNear(level, point, TagsConfig.DISTANCE_DIFFUSION.get(),
                new Packets.TagS2C(t.id, u, def.id(), point, msg.face(), angle, p.getYRot(), now));
        level.playSound(null, point.x, point.y, point.z, Sons.TAG_SPRAY.get(), SoundSource.PLAYERS, 0.9f, 0.95f + ThreadLocalRandom.current().nextFloat() * 0.1f);
        // brouillard de peinture : une dizaine de particules juste devant la face, dans le plan du tag ; le client
        // choisit la couleur dominante du tag (atlas), la couleur de la rarete sert de repli
        Vec3 normale = Vec3.atLowerCornerOf(msg.face().getNormal());
        Vec3 origine = point.add(normale.scale(0.08));
        double etal = def.taille() * 0.22;
        double ex = etal * (1 - Math.abs(normale.x)), ey = etal * (1 - Math.abs(normale.y)), ez = etal * (1 - Math.abs(normale.z));
        level.sendParticles(new BrouillardOptions(def.cas(), def.rarete().couleur), origine.x, origine.y, origine.z, 10, ex, ey, ez, 0.0);
        return Refus.OK;
    }

    /**
     * Yeux possibles du joueur pour la verification : ceux du serveur maintenant et au tick precedent (le client a un
     * peu d'avance sur sa position), et a sa position actuelle debout et accroupi (l'accroupissement arrive au serveur
     * un tick apres le rayon du client). Doublons ecartes.
     */
    static List<Vec3> origines(ServerPlayer p) {
        List<Vec3> l = new ArrayList<>(4);
        ajouter(l, p.getEyePosition());
        ajouter(l, p.getEyePosition(0f));
        Vec3 pos = p.position();
        ajouter(l, pos.add(0, p.getEyeHeight(Pose.STANDING), 0));
        ajouter(l, pos.add(0, p.getEyeHeight(Pose.CROUCHING), 0));
        return l;
    }

    private static void ajouter(List<Vec3> l, Vec3 v) {
        for (Vec3 o : l) if (o.distanceToSqr(v) < 1e-4) return;
        l.add(v);
    }

    /** Marge de distance : 1 bloc, plus 3 ticks du dernier deplacement du joueur ou du vehicule qui le porte. */
    static double marge(ServerPlayer p) {
        Entity e = p.getRootVehicle();
        double deplacement = Math.sqrt(e.distanceToSqr(e.xo, e.yo, e.zo));
        return MARGE + Math.min(MARGE_DEPLACEMENT_MAX, deplacement * TICKS_AVANCE);
    }

    /**
     * Rayon rejoue depuis o vers le point, prolonge de 0,3 bloc dans la meme direction (le point du client peut etre un
     * soupcon devant la surface reelle, ex. muret de 5/16 de large) : le bloc touche, s'il l'est sur la face annoncee
     * a moins de 0,25 bloc du point ; sinon null.
     */
    static BlockHitResult rejouer(ServerLevel level, ServerPlayer p, Vec3 o, Vec3 point, Direction face) {
        Vec3 direction = point.subtract(o);
        if (direction.lengthSqr() < 1e-6) return null;
        Vec3 fin = point.add(direction.normalize().scale(0.3));
        BlockHitResult hr = level.clip(new ClipContext(o, fin, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (hr.getType() != HitResult.Type.BLOCK || hr.getDirection() != face || hr.getLocation().distanceToSqr(point) > TOLERANCE_POINT_2) {
            return null;
        }
        return hr;
    }

    /**
     * Affiche au joueur la raison d'un refus qui lui est utile (cadence, trop loin, surface impossible, a bord du
     * train) en overlay, au plus un message par seconde. Les autres refus (tag inconnu, non possede, joueur mort)
     * restent silencieux : le client les a deja filtres, seul un client modifie les atteint.
     */
    public static void signaler(ServerPlayer p, Refus refus) {
        String cle = switch (refus) {
            case CADENCE -> "msg.heralisz_tags.cadence";
            case TROP_LOIN -> "msg.heralisz_tags.trop_loin";
            case SURFACE -> "msg.heralisz_tags.surface";
            case TRAIN -> "msg.heralisz_tags.train";
            default -> null;
        };
        if (cle == null) return;
        long now = System.currentTimeMillis();
        Long dernier = DERNIER_MESSAGE.get(p.getUUID());
        if (dernier != null && now - dernier < 1000) return;
        DERNIER_MESSAGE.put(p.getUUID(), now);
        p.displayClientMessage(Component.translatable(cle), true);
    }

    private static void retirer(TagActif a) {
        if (a == null) return;
        TagsNet.toNear(a.level, a.point, TagsConfig.DISTANCE_DIFFUSION.get() + 32, new Packets.TagRetireS2C(a.id));
    }

    /** Toutes les secondes : oubli des tags expires (les clients ferment le tampon d'eux-memes a duree_s). */
    public static void tick(MinecraftServer server) {
        if (++ticks < 20) return;
        ticks = 0;
        long limite = System.currentTimeMillis() - TagsConfig.DUREE_S.get() * 1000L;
        while (!ACTIFS.isEmpty() && ACTIFS.peekFirst().creeLe < limite) ACTIFS.pollFirst();
    }

    /** Nombre de tags actifs (journal, /tags admin). */
    public static int actifs() {
        return ACTIFS.size();
    }

    /** Oubli d'un joueur deconnecte (cadence, messages). */
    public static void oublier(UUID joueur) {
        DERNIERE_POSE.remove(joueur);
        DERNIER_MESSAGE.remove(joueur);
        ServerHandlers.oublier(joueur);
    }

    /** Vide tout (arret du serveur). */
    public static void reinitialiser() {
        ACTIFS.clear();
        DERNIERE_POSE.clear();
        DERNIER_MESSAGE.clear();
        ServerHandlers.reinitialiser();
        HeraliszTags.LOGGER.debug("[HeraliZ Tags] tags actifs vides");
    }
}
