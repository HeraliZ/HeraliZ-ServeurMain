package fr.heraliz.airstrike.entity;

import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.api.HelicoApi;
import fr.heraliz.airstrike.helico.Veille;
import fr.heraliz.airstrike.registry.ModEntities;
import fr.heraliz.airstrike.registry.ModParticles;
import fr.heraliz.airstrike.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Partie SERVEUR de {@link HelicopterWreck} (docs/METHODE_CLIENT_SERVEUR.md §5) : l'épave posée à l'impact brûle 2 min à
 * 3 min 30 (tiré au hasard, réglable), avec une zone de feu sans bloc de feu ({@code EffectCloudEntity} NAPALM, rayon 6) et
 * une colonne de fumée noire ({@code MARKER}) ; à la fin du feu, le module des événements pose le butin (coffre T7, objet
 * d'événement) ; épave, fumée et feu sont retirés {@code retrait} secondes plus tard. Fin du feu et retrait en heure de
 * jeu absolue (vérifiés aussi par {@link Veille}, même si l'épave ne tourne pas). Pendant le feu, le tronçon reste chargé
 * et actif (ticket) : le butin apparaît même si personne n'est là. Jamais sauvegardée.
 * <p>11/11 : le module des événements pose le coffre dès l'impact, fermé jusqu'à la fin du feu ({@link #finDuFeu}), hors
 * des boîtes de collision de l'épave ({@link #libre}) ; une balle qui touche l'épave s'y arrête dans une gerbe
 * d'étincelles ({@link #touche}).
 */
public class HelicopterWreckServeur extends HelicopterWreck {
    /** Tient le tronçon de l'épave actif pendant le feu ; rafraîchi chaque seconde, expire seul. */
    public static final TicketType<Integer> TICKET = TicketType.create("airstrike_epave", Integer::compareTo, 300);
    private static final int RAYON_TICKET = 2;
    private static final Map<String, HelicopterWreckServeur> EPAVES = new HashMap<>();
    /** Couleurs de la zone de feu et de la fumée noire. */
    private static final int FEU = 0xFF6A00, FUMEE = 0x1E1B19;

    private String id = "";
    @Nullable
    private HelicoApi.Suivi suivi;
    /** Heures de jeu de la fin du feu et du retrait. */
    private long feuFin, fin;
    private boolean feuFini, fini;
    @Nullable
    private EffectCloudEntity feu, fumee;
    @Nullable
    private ChunkPos ticket;

    public HelicopterWreckServeur(EntityType<? extends HelicopterWreck> type, Level level) {
        super(type, level);
    }

    @Nullable
    public static HelicopterWreckServeur epave(String id) {
        HelicopterWreckServeur w = EPAVES.get(id);
        return w != null && !w.isRemoved() ? w : null;
    }

    /** Les épaves encore là (marques de la carte M, fin en heure de jeu). */
    public static Collection<HelicopterWreckServeur> toutes() {
        return EPAVES.values();
    }

    /** Heure de jeu où le feu de l'épave {@code id} s'éteint ; -1 : pas d'épave, ou feu déjà éteint. */
    public static long finDuFeu(String id) {
        HelicopterWreckServeur w = epave(id);
        return w == null || w.feuFini ? -1 : w.feuFin;
    }

    /** Aucune boîte de collision de l'épave {@code id} ne touche cette boîte (vrai aussi sans épave). */
    public static boolean libre(String id, AABB boite) {
        HelicopterWreckServeur w = epave(id);
        return w == null || !w.gene(boite);
    }

    /** Retire l'épave, son feu et sa fumée sans rien donner (staff). */
    public static boolean retirer(String id) {
        HelicopterWreckServeur w = EPAVES.remove(id);
        if (w == null) return false;
        w.fini = true;
        w.retirerNuages();
        w.prevenir(s -> s.fini(id, "annule"));
        w.discard();
        return true;
    }

    /** Pose l'épave à l'impact, tournée comme l'hélicoptère, avec son feu et sa fumée. */
    static void poser(ServerLevel level, String id, Vec3 p, float lacet, HelicoApi.Reglages r, @Nullable HelicoApi.Suivi suivi) {
        if (!(ModEntities.HELICOPTER_WRECK.get().create(level) instanceof HelicopterWreckServeur w)) return;
        w.id = id;
        w.suivi = suivi;
        int feuTicks = Math.max(20, (r.feuMinS + level.random.nextInt(Math.max(1, r.feuMaxS - r.feuMinS + 1))) * 20);
        int vieTicks = feuTicks + Math.max(0, r.retraitS) * 20;
        long now = level.getGameTime();
        w.feuFin = now + feuTicks;
        w.fin = now + vieTicks;
        // lacet arrondi comme dans le paquet d'apparition : boîtes de collision au même endroit chez le joueur
        w.moveTo(p.x, p.y, p.z, lacetEnvoye(lacet), 0f);
        w.placerMorceaux();
        w.entityData.set(FEU_FIN, (int) (w.feuFin & 0x7FFFFFFFL));
        ChunkPos cp = new ChunkPos(BlockPos.containing(p));
        level.getChunkSource().addRegionTicket(TICKET, cp, RAYON_TICKET, w.getId());
        w.ticket = cp;
        if (!level.addFreshEntity(w)) {
            w.relacherTicket();
            AirstrikeMod.LOGGER.warn("[helico] {} : epave impossible a poser en {}", id, cp);
            return;
        }
        EPAVES.put(id, w);
        // zone de feu sans bloc de feu, colonne de fumée noire : jamais sauvegardées, retirées avec l'épave
        w.feu = EffectCloudEntityServeur.create(level, EffectCloudEntity.NAPALM, p, 6.0f, feuTicks, FEU);
        w.fumee = EffectCloudEntityServeur.create(level, EffectCloudEntity.MARKER, p.add(0, 0.5, 0), 1.0f, vieTicks, FUMEE);
        for (EffectCloudEntity n : new EffectCloudEntity[]{w.feu, w.fumee}) {
            if (n instanceof EffectCloudEntityServeur s) s.pourEvenement();
            level.addFreshEntity(n);
        }
    }

    @Override
    void tickServeur() {
        if (suivi == null && id.isEmpty()) {
            discard();   // créée hors de l'hélicoptère : rien à faire
            return;
        }
        long now = level().getGameTime();
        if (!feuFini && now % 20 == 0 && level() instanceof ServerLevel sl && ticket != null) {
            sl.getChunkSource().addRegionTicket(TICKET, ticket, RAYON_TICKET, getId());   // rafraîchi pendant le feu
        }
        verifier(now);
    }

    /** Fin du feu puis retrait, en heure de jeu absolue (appelé par son tick et par la veille). */
    public void verifier(long now) {
        if (fini) return;
        if (!feuFini && now >= feuFin) {
            feuFini = true;
            prevenir(s -> s.feuEteint(id, blockPosition()));
            relacherTicket();
        }
        if (now >= fin) {
            fini = true;
            EPAVES.remove(id, this);
            retirerNuages();
            prevenir(s -> s.fini(id, "epave_retiree"));
            discard();
        }
    }

    /** Dernière gerbe d'étincelles (heure de jeu) : au plus une tous les 2 ticks. */
    private long derniersEclats = -10;

    /**
     * Une balle (ou tout projectile) touche une boîte de l'épave : elle s'y arrête (TaCZ la retire après le coup) ;
     * étincelles et claquement de métal au point d'entrée, au plus tous les 2 ticks. Coups et explosions : rien. L'épave
     * n'a pas de points de vie.
     */
    @Override
    boolean touche(HelicopterWreckPart m, DamageSource source) {
        Entity balle = source.getDirectEntity();
        if (balle == null || balle instanceof Player || source.is(DamageTypeTags.IS_EXPLOSION) || !(level() instanceof ServerLevel sl)) {
            return false;
        }
        long now = sl.getGameTime();
        if (now - derniersEclats < 2) return false;
        derniersEclats = now;
        Vec3 de = balle.position();
        Vec3 ou = m.getBoundingBox().clip(de, de.add(balle.getDeltaMovement())).orElse(m.getBoundingBox().getCenter());
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(ou) < 96 * 96) sl.sendParticles(p, ModParticles.SPARK.get(), true, ou.x, ou.y, ou.z, 3, 0.1, 0.1, 0.1, 0.2);
        }
        sl.playSound(null, ou.x, ou.y, ou.z, ModSounds.BULLET_IMPACT.get(), SoundSource.BLOCKS, 1.5f, 1.2f + random.nextFloat() * 0.3f);
        return false;
    }

    private void retirerNuages() {
        if (feu != null && !feu.isRemoved()) feu.discard();
        if (fumee != null && !fumee.isRemoved()) fumee.discard();
        feu = fumee = null;
    }

    private void relacherTicket() {
        if (ticket != null && level() instanceof ServerLevel sl) sl.getChunkSource().removeRegionTicket(TICKET, ticket, RAYON_TICKET, getId());
        ticket = null;
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        relacherTicket();
        RemovalReason raison = getRemovalReason();
        if (level() instanceof ServerLevel sl && raison != null && raison.shouldDestroy()) Veille.retire(sl, getId());
        if (!fini) {
            fini = true;
            EPAVES.remove(id, this);
            // déchargée avec son tronçon : ses nuages (jamais sauvegardés) partent avec lui ; sinon on les retire
            if (raison != null && raison != RemovalReason.UNLOADED_TO_CHUNK && raison != RemovalReason.UNLOADED_WITH_PLAYER) retirerNuages();
            prevenir(s -> s.fini(id, "perdu"));
        }
    }

    private void prevenir(Consumer<HelicoApi.Suivi> appel) {
        if (suivi == null) return;
        try {
            appel.accept(suivi);
        } catch (RuntimeException ex) {
            AirstrikeMod.LOGGER.error("[helico] {} : suivi de l'epave en erreur", id, ex);
        }
    }
}
