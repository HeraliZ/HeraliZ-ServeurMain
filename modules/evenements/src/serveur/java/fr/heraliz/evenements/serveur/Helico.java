package fr.heraliz.evenements.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.heraliz.airstrike.api.HelicoApi;
import fr.heraliz.airstrike.helico.PointCrash;
import fr.heraliz.evenements.HeralizEvenements;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Hélicoptère militaire (docs/EVENEMENTS.md §4.4, fiche docs/GAME_DESIGN_25OCT.md §5 ; règles du propriétaire du 25/10,
 * qui l'emportent sur la fiche). Le vol, les tirs, la chute et l'épave sont dans le module monde
 * ({@code fr.heraliz.airstrike.api.HelicoApi}) ; ici : quand il part, les annonces, les points de saison, le butin.
 * <ul>
 * <li><b>Imprévisible</b> : aucune heure dans le planning. Quand un hélicoptère se termine (abattu, parti ou annulé), on
 * attend {@code pause_minutes} (4 h), puis il apparaît à un instant tiré au hasard dans les {@code fenetre_minutes}
 * suivantes (2 h). Dès le départ, une heure provisoire (pause + hasard) est écrite : un arrêt du serveur pendant le vol ne
 * fait pas perdre le cycle. Heure réelle, gardée hors du monde ({@code <serveur>/heraliz_evenements/helico_etat.json}) ;
 * au démarrage, une heure déjà passée (ou absente, ou dans la minute) est repoussée de 30 à 60 min au hasard : jamais
 * d'hélicoptère juste après un redémarrage. À l'heure prévue, conditions du Directeur (joueurs en survie sur la carte, pas
 * de ClashWar, un seul à la fois) ; refusé : nouvel essai toutes les {@code reessai_minutes}. Jamais annoncé à l'avance aux
 * joueurs (le staff le voit : {@code /event helico info}).</li>
 * <li><b>Filet de sécurité</b> : un hélicoptère encore « en cours » après sa durée de vie la plus longue possible (vol
 * maximal, chute, feu, retrait, marge ; ×1,5 contre un serveur qui rame), en heure réelle, est annulé
 * ({@link #seconde}).</li>
 * <li><b>Annonces</b> aux joueurs de la carte : il patrouille (sans position), il s'est écrasé (secteur de la carte M), il
 * s'éloigne.</li>
 * <li><b>Points de saison</b> (choix du propriétaire, 25/10) : aucun à la destruction ; seulement la boîte noire, revendue à
 * Mila ({@code events.points.objet_boite_noire_helico}, plafond par clan et par jour). Lancé par le staff : sa boîte noire
 * est marquée « essai » (hérals à la revente, jamais de points) sauf {@code avec_points} ; de même si un joueur en
 * créatif ou en spectateur l'a touché ({@code Abattu.essai}). Les dégâts de chaque joueur (et son clan au moment du
 * coup) sont seulement notés au journal.</li>
 * <li><b>Butin</b> : le feu de l'épave éteint, un coffre T7 ({@code coffre_t7}, HeraliZ Loot, par réflexion) à 2 blocs
 * de hauteur au plus de l'épave, sur un sol de crash ({@code #airstrike:sol_crash}), et l'objet d'événement « Boîte noire
 * d'hélicoptère » ; explosion en l'air : seulement l'objet, posé sur le dernier sol valide survolé (choisi par le module
 * monde, {@code Abattu.sol}), sinon rien (journal). Tronçon pas chargé : un ticket le demande, l'objet est posé dès qu'il
 * est là ({@link #objetsEnAttente}).</li>
 * </ul>
 * Réglages : {@code config/heraliz_evenements/helico.json} (relu par {@code /event recharger}). Hors événement : une
 * comparaison d'heure par minute ({@link #minute}).
 */
final class Helico implements Evenement {
    static final String ID = "helico";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Random ALEA = new Random();
    /** L'instance déclarée au Directeur (commandes du staff). */
    @Nullable
    private static Helico instance;

    // ------------------------------------------------------------------ réglages (helico.json)
    private static int joueursMin = 10;
    private static long pauseMs = 240 * 60_000L, fenetreMs = 120 * 60_000L, reessaiMs = 5 * 60_000L;
    private static String coffreT7 = "t7_coffre_armes";
    private static int coffreT7Min = 15;
    private static final HelicoApi.Reglages R = new HelicoApi.Reglages();

    // ------------------------------------------------------------------ état
    @Nullable
    private String idCourant;
    @Nullable
    private MinecraftServer serveur;
    /** {@code essai} : touché par un joueur en créatif ou en spectateur (boîte noire sans points). */
    private boolean points, programme, essai;
    private long debutMs;
    private String secteur = "";
    /** Prochaine apparition (heure en ms, 0 : pas encore tirée). */
    private long prochainMs;
    @Nullable
    private Path dossierCharge;
    @Nullable
    private String dernierRefus;
    /** Départ demandé par le staff ({@code /event helico lancer ici}). */
    @Nullable
    private Vec3 departDemande;

    Helico() {
        instance = this;
        // objets d'événement en attente de leur tronçon : vérifiés chaque seconde, même hors événement
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, this::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppingEvent.class, this::arret);
    }

    @Override
    public String id() {
        return ID;
    }

    @Nullable
    @Override
    public String refus(MinecraftServer server) {
        return null;   // conditions communes vérifiées par le Directeur (joueurs, ClashWar, un seul à la fois)
    }

    // ------------------------------------------------------------------ cycle
    /**
     * Une fois par minute, actif ou non (planificateur propre : pas d'heure fixe). À l'heure tirée, demande le départ au
     * Directeur ; refusé, nouvel essai {@code reessai_minutes} plus tard.
     */
    @Override
    public void minute(MinecraftServer server) {
        if (dossierCharge == null) return;
        long maintenant = System.currentTimeMillis();
        if (prochainMs <= 0) {
            repousser(maintenant, "premiere apparition");
            return;
        }
        if (actif() || maintenant < prochainMs) return;
        String refus = Directeur.lancer(server, ID, true, "hasard", joueursMin);
        if (refus == null) {
            dernierRefus = null;
            return;
        }
        prochainMs = maintenant + reessaiMs;
        sauverEtat();
        // une ligne au journal par sorte de refus (pas à chaque essai, ni à chaque joueur de plus ou de moins)
        String sorte = refus.replaceAll("\\d+", "N");
        if (dernierRefus == null || !sorte.equals(dernierRefus.replaceAll("\\d+", "N"))) {
            Outils.journal("helico : pas lance (" + refus + "), nouvel essai toutes les " + reessaiMs / 60_000L + " min");
        }
        dernierRefus = refus;
    }

    /** Après un démarrage du serveur (heure absente, passée ou dans la minute) : 30 à 60 min au hasard, jamais tout de suite. */
    private void repousser(long maintenant, String pourquoi) {
        prochainMs = maintenant + DEMARRAGE_MIN_MS + (long) (ALEA.nextDouble() * (DEMARRAGE_MAX_MS - DEMARRAGE_MIN_MS));
        sauverEtat();
        Outils.journal("helico : " + pourquoi + " tiree au hasard, vers " + heure(prochainMs));
    }

    private static final long DEMARRAGE_MIN_MS = 30 * 60_000L, DEMARRAGE_MAX_MS = 60 * 60_000L;

    /** Pause, puis un instant au hasard dans la fenêtre (à partir de maintenant). */
    private long tirer() {
        return System.currentTimeMillis() + pauseMs + (long) (ALEA.nextDouble() * fenetreMs);
    }

    /** Fin d'un hélicoptère : pause, puis un instant au hasard dans la fenêtre. Une fois par hélicoptère. */
    private void programmer() {
        if (programme) return;
        programme = true;
        prochainMs = tirer();
        sauverEtat();
        Outils.journal("helico : prochain passage tire au hasard, vers " + heure(prochainMs));
    }

    @Override
    public boolean demarrer(MinecraftServer server, int numero, boolean points, String par) {
        ServerLevel level = server.overworld();
        String id = ID + "#" + numero;
        Vec3 depart = departDemande;
        departDemande = null;
        Vec3 p = HelicoApi.lancer(level, id, R, depart, Helico::clanDe, suivi(id));
        if (p == null) {
            Outils.journal(id + " : impossible a lancer");
            return false;
        }
        idCourant = id;
        serveur = server;
        this.points = points;
        essai = false;
        programme = false;
        debutMs = System.currentTimeMillis();
        secteur = "";
        // heure provisoire du suivant dès le départ (arrêt du serveur pendant le vol) ; refaite à la fin de celui-ci
        prochainMs = tirer();
        sauverEtat();
        Outils.journal(id + " lance par " + par + " vers " + Math.round(p.x) + " " + Math.round(p.z) + " (secteur "
                + Outils.secteur(level, p.x, p.z) + ")" + (points ? "" : ", sans points") + ", suivant provisoire vers " + heure(prochainMs));
        annoncer(server, Outils.t("heraliz_evenements.helico.apparition", "Un hélicoptère militaire patrouille la zone… on l'entend au loin.")
                .withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    @Override
    public boolean actif() {
        return idCourant != null;
    }

    @Override
    public void seconde(MinecraftServer server) {
        if (idCourant == null) return;
        long duree = System.currentTimeMillis() - debutMs;
        // filet de sécurité : plus rien dans le monde sans que le suivi l'ait dit (30 s après le départ)
        if (!HelicoApi.enCours(idCourant) && duree > 30_000L) {
            Outils.journal(idCourant + " : plus rien dans le monde, evenement termine");
            idCourant = null;
            programmer();
            return;
        }
        // garde en heure réelle : au-delà de la plus longue vie possible (×1,5 si le serveur rame), tout est retiré
        if (duree > R.vieMaxS() * 1500L) {
            String id = idCourant;
            idCourant = null;   // avant de retirer : le suivi ignore ce qui suit
            HelicoApi.annuler(id);
            programmer();
            Outils.journal(id + " : toujours en cours apres " + duree / 60_000L + " min (maximum " + R.vieMaxS() / 60
                    + " min), retire par le filet de securite");
        }
    }

    @Override
    public void annuler(MinecraftServer server, String motif) {
        if (idCourant == null) return;
        String id = idCourant;
        idCourant = null;   // avant de retirer : le suivi ignore ce qui suit
        HelicoApi.annuler(id);
        programmer();
        Outils.journal(id + " : annule (" + motif + "), rien n'est donne");
    }

    // ------------------------------------------------------------------ suivi du vol (module monde)
    private HelicoApi.Suivi suivi(String id) {
        return new HelicoApi.Suivi() {
            @Override
            public void abattu(HelicoApi.Abattu a) {
                if (id.equals(idCourant)) Helico.this.abattu(a);
            }

            @Override
            public void impact(String i, Vec3 ou) {
                if (id.equals(idCourant)) Helico.this.impact(ou);
            }

            @Override
            public void feuEteint(String i, BlockPos ou) {
                if (id.equals(idCourant)) Helico.this.feuEteint(ou);
            }

            @Override
            public void depart(String i) {
                if (!id.equals(idCourant) || serveur == null) return;
                Outils.journal(id + " : fin de patrouille, quitte la carte");
                annoncer(serveur, Outils.t("heraliz_evenements.helico.depart", "L'hélicoptère s'éloigne.").withStyle(ChatFormatting.GRAY), false);
            }

            @Override
            public void fini(String i, String cause) {
                if (!id.equals(idCourant)) return;
                idCourant = null;
                programmer();
                Outils.journal(id + " : fin (" + cause + ")");
            }
        };
    }

    private void abattu(HelicoApi.Abattu a) {
        MinecraftServer server = serveur;
        if (server == null) return;
        ServerLevel level = server.overworld();
        programmer();
        essai = a.essai();
        String sec = Outils.secteur(level, a.ou().x, a.ou().z);
        double total = 0;
        for (HelicoApi.Contribution c : a.contributions()) total += c.degats();
        Outils.journal(a.id() + " abattu (secteur " + sec + ") : " + a.contributions().size() + " tireur(s), " + Math.round(total) + " degats"
                + (a.enLAir() ? ", explose en l'air (aucun point de crash valide)" : ", crash prevu en " + a.crash().toShortString())
                + (a.essai() ? ", ESSAI (touche en creatif ou en spectateur : boite noire sans points)" : ""));
        // aucun point à la destruction (seulement la boîte noire, à la revente) : les tireurs sont notés pour le staff
        for (HelicoApi.Contribution c : a.contributions()) {
            Outils.journal(a.id() + " :   " + c.nom() + " " + Math.round(c.degats()) + " degats ("
                    + Math.round(total > 0 ? c.degats() / total * 100 : 0) + " %)" + (c.clan() != null ? ", clan " + c.clan().toString().substring(0, 8) : ""));
        }
        if (a.enLAir()) {
            annoncer(server, Outils.t("heraliz_evenements.helico.explose", "Un hélicoptère a explosé en vol dans le secteur %s.", sec)
                    .withStyle(ChatFormatting.GOLD), true);
            // la boîte noire sur le dernier sol valide survolé (sinon le plus proche, contrôles relâchés), sinon rien
            if (a.sol() != null) lacherObjet(level, a.sol(), idObjet(a.id()));
            else Outils.journal(a.id() + " : AUCUN sol pour la boite noire (ni survole, ni proche) : pas d'objet");
        }
    }

    /**
     * Numéro d'événement porté par la boîte noire : celui de l'hélicoptère, marqué « essai » s'il a été lancé par le staff
     * sans « avec_points », ou touché par un joueur en créatif ou en spectateur (hérals à la revente, jamais de points :
     * {@code ObjetsServeur.essai}).
     */
    private String idObjet(String id) {
        return points && !essai ? id : id + ObjetsServeur.SUFFIXE_ESSAI;
    }

    private void impact(Vec3 ou) {
        MinecraftServer server = serveur;
        if (server == null) return;
        secteur = Outils.secteur(server.overworld(), ou.x, ou.z);
        Outils.journal(idCourant + " : ecrase en " + BlockPos.containing(ou).toShortString() + " (secteur " + secteur + ")");
        annoncer(server, Outils.t("heraliz_evenements.helico.crash", "Un hélicoptère s'est écrasé dans le secteur %s.", secteur)
                .withStyle(ChatFormatting.GOLD), true);
    }

    private void feuEteint(BlockPos ou) {
        MinecraftServer server = serveur;
        if (server == null) return;
        ServerLevel level = server.overworld();
        String id = idCourant;
        // le coffre porte le numéro de l'événement : à sa première ouverture, HeraliZ Loot y met l'objet d'événement
        // (EvenementsApi.objetDuCoffre) et prévient l'événement (EvenementsApi.coffreOuvert)
        BlockPos coffre = poserCoffre(level, ou, idObjet(id));
        Outils.journal(id + " : feu eteint, " + (coffre != null ? "coffre " + coffreT7 + " en " + coffre.toShortString()
                : "AUCUN coffre T7 pose (pas de place) : objet d'evenement pose au sol"));
        if (coffre == null) lacherObjet(level, ou, idObjet(id));
    }

    @Override
    public void coffreOuvert(ServerPlayer p, String id) {
        // aucun point à l'ouverture : la boîte noire du coffre donne les siens à la revente (le coffre T7 garde sa règle)
        Outils.journal(id + " : coffre de l'epave ouvert par " + p.getGameProfile().getName());
    }

    // ------------------------------------------------------------------ clan des tireurs (journal), par réflexion
    @Nullable
    private static Method saisonClanDe;
    private static boolean saisonCherche;

    /** Clan du joueur maintenant (null : sans clan ou clans absents) ; noté avec chaque tir sur l'hélicoptère. */
    @Nullable
    static UUID clanDe(UUID joueur) {
        if (!saisonCherche) {
            saisonCherche = true;
            try {
                saisonClanDe = Class.forName("fr.heralisz.clans.saison.SaisonApi").getMethod("clanDe", UUID.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                HeralizEvenements.LOG.warn("[evenements] helico : clans absents, tireurs notes sans clan ({})", e.toString());
            }
        }
        if (saisonClanDe == null) return null;
        try {
            return (UUID) saisonClanDe.invoke(null, joueur);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ butin
    @Nullable
    private static Method apparaitre;
    private static boolean lootCherche;

    /**
     * Coffre T7 (HeraliZ Loot, {@code PointsT7.apparaitre} avec le numéro de l'événement, par réflexion) sur le sol libre le
     * plus proche de l'épave (deux cercles de 5 et 8 blocs autour), face avant vers l'extérieur : à 2 blocs de hauteur au
     * plus de l'épave (jamais sur un toit ni au fond d'un trou) et posé sur un sol de crash ({@code #airstrike:sol_crash}).
     */
    @Nullable
    private static BlockPos poserCoffre(ServerLevel level, BlockPos epave, String id) {
        if (!lootCherche) {
            lootCherche = true;
            try {
                apparaitre = Class.forName("fr.heraliz.loot.PointsT7").getMethod("apparaitre", ServerLevel.class, BlockPos.class,
                        Direction.class, String.class, int.class, String.class, String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                HeralizEvenements.LOG.warn("[evenements] helico : HeraliZ Loot absent, pas de coffre T7 ({})", e.toString());
            }
        }
        if (apparaitre == null) return null;
        int presence = Math.max(1, coffreT7Min) * 60 * 20;
        for (int i = 0; i < 16; i++) {
            int rayon = i < 8 ? 5 : 8;
            double a = i * (Math.PI / 4) + (i < 8 ? 0 : Math.PI / 8);
            int x = epave.getX() + (int) Math.round(Math.cos(a) * rayon), z = epave.getZ() + (int) Math.round(Math.sin(a) * rayon);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - epave.getY()) > 2) continue;
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getBlockState(p.below()).getFluidState().isEmpty() || !level.getBlockState(p.below()).is(PointCrash.SOL)) continue;
            Direction face = Direction.getNearest((float) Math.cos(a), 0f, (float) Math.sin(a));
            try {
                if ((boolean) apparaitre.invoke(null, level, p, face, coffreT7, presence, "", id)) return p;
            } catch (ReflectiveOperationException | RuntimeException e) {
                HeralizEvenements.LOG.warn("[evenements] helico : coffre T7 impossible : {}", e.toString());
                return null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ objet d'événement au sol
    /** Demande le tronçon où l'objet doit tomber ; rafraîchi chaque seconde, expire seul. */
    private static final TicketType<ChunkPos> TICKET_OBJET = TicketType.create("heraliz_helico_objet",
            Comparator.comparingLong(ChunkPos::toLong), 100);
    /** Attente maximale du tronçon (au-delà : objet perdu, noté au journal). */
    private static final long ATTENTE_OBJET_MS = 60_000L;

    private record ObjetEnAttente(ServerLevel level, BlockPos sol, String id, ChunkPos cp, long limiteMs) {
    }

    private final List<ObjetEnAttente> objetsEnAttente = new ArrayList<>();
    private int ticks;

    /**
     * L'objet d'événement (« Boîte noire d'hélicoptère », tirage {@code helico} de objets.json) posé au sol : explosion en
     * l'air (dernier sol valide survolé), ou épave sans place pour le coffre. Numéroté et signé comme celui d'un coffre.
     * Tronçon pas chargé : jamais chargé sur le fil du serveur, un ticket le demande et l'objet est posé dès qu'il est là.
     */
    private void lacherObjet(ServerLevel level, BlockPos sol, String id) {
        ChunkPos cp = new ChunkPos(sol);
        if (level.getChunkSource().getChunkNow(cp.x, cp.z) != null) {
            poserObjet(level, sol, id);
            return;
        }
        level.getChunkSource().addRegionTicket(TICKET_OBJET, cp, 1, cp);
        objetsEnAttente.add(new ObjetEnAttente(level, sol, id, cp, System.currentTimeMillis() + ATTENTE_OBJET_MS));
        Outils.journal(id + " : objet d'evenement en attente du troncon " + cp);
    }

    private static void poserObjet(ServerLevel level, BlockPos sol, String id) {
        ItemStack objet = ObjetsServeur.creer(id);
        if (objet.isEmpty()) {
            Outils.journal(id + " : aucun objet d'evenement (objets coupes ou tirage vide)");
            return;
        }
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, sol.getX(), sol.getZ()) + 0.5;
        ItemEntity e = new ItemEntity(level, sol.getX() + 0.5, y, sol.getZ() + 0.5, objet);
        e.setDeltaMovement(0, 0.1, 0);
        e.setUnlimitedLifetime();
        level.addFreshEntity(e);
        Outils.journal(id + " : objet d'evenement pose au sol en " + e.blockPosition().toShortString());
    }

    private void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || objetsEnAttente.isEmpty() || ++ticks % 20 != 0) return;
        long maintenant = System.currentTimeMillis();
        Iterator<ObjetEnAttente> it = objetsEnAttente.iterator();
        while (it.hasNext()) {
            ObjetEnAttente o = it.next();
            if (o.level().getChunkSource().getChunkNow(o.cp().x, o.cp().z) != null) {
                it.remove();
                poserObjet(o.level(), o.sol(), o.id());
                o.level().getChunkSource().removeRegionTicket(TICKET_OBJET, o.cp(), 1, o.cp());
            } else if (maintenant > o.limiteMs()) {
                it.remove();
                o.level().getChunkSource().removeRegionTicket(TICKET_OBJET, o.cp(), 1, o.cp());
                Outils.journal(o.id() + " : PERDU, troncon " + o.cp() + " jamais charge en " + ATTENTE_OBJET_MS / 1000 + " s : objet pas pose");
            } else {
                o.level().getChunkSource().addRegionTicket(TICKET_OBJET, o.cp(), 1, o.cp());   // rafraîchi
            }
        }
    }

    private void arret(ServerStoppingEvent e) {
        for (ObjetEnAttente o : objetsEnAttente) Outils.journal(o.id() + " : PERDU, serveur arrete avant que le troncon " + o.cp() + " soit charge");
        objetsEnAttente.clear();
    }

    // ------------------------------------------------------------------ annonces
    /**
     * Annonce aux joueurs de la carte seulement (overworld) : même forme que {@code Outils.annoncer} (notification en haut
     * à gauche, petit son ; {@code chat} : aussi une ligne dans le chat).
     */
    private static void annoncer(MinecraftServer server, Component texte, boolean chat) {
        MutableComponent m = Component.translatableWithFallback("heraliz_evenements.chat.prefixe", "[Événement] ").append(texte);
        MutableComponent ligne = chat ? Component.translatable("chat.type.announcement",
                Outils.t("heraliz_evenements.chat.nom", "Événement").withStyle(ChatFormatting.GOLD), texte) : null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() != Level.OVERWORLD) continue;
            p.sendSystemMessage(m);
            if (ligne != null) p.sendSystemMessage(ligne);
            p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.5f, 0.8f);
        }
        HeralizEvenements.LOG.info("[evenements] annonce (carte) : {}", texte.getString());
    }

    private static String heure(long ms) {
        ZonedDateTime t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), Outils.PARIS);
        return String.format(Locale.ROOT, "%02d/%02d %02d:%02d", t.getDayOfMonth(), t.getMonthValue(), t.getHour(), t.getMinute());
    }

    // ------------------------------------------------------------------ commandes du staff (/event helico …)
    /**
     * {@code /event helico info} (état, PV, agresseurs, prochaine apparition : jamais montré aux joueurs),
     * {@code /event helico prochain <minutes>} (essais), {@code /event helico lancer [ici] [avec_points]} (au bord de la
     * carte, ou 48 blocs devant le staff). Permission 4. Branché dans {@code /event} ({@link Commandes}).
     */
    static LiteralArgumentBuilder<CommandSourceStack> commande() {
        return Commands.literal(ID).requires(s -> s.hasPermission(4))
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(Commands.literal("prochain").then(Commands.argument("minutes", IntegerArgumentType.integer(0, 1440))
                        .executes(c -> prochain(c.getSource(), IntegerArgumentType.getInteger(c, "minutes")))))
                .then(Commands.literal("lancer").executes(c -> lancer(c.getSource(), false, false))
                        .then(Commands.literal("avec_points").executes(c -> lancer(c.getSource(), false, true)))
                        .then(Commands.literal("ici").executes(c -> lancer(c.getSource(), true, false))
                                .then(Commands.literal("avec_points").executes(c -> lancer(c.getSource(), true, true)))));
    }

    private static int info(CommandSourceStack s) {
        Helico h = instance;
        if (h == null) return 0;
        String id = h.idCourant;
        if (id == null) {
            s.sendSuccess(() -> Component.literal("Hélicoptère : aucun en cours."), false);
        } else {
            HelicoApi.Etat e = HelicoApi.etat(id);
            if (e == null) {
                s.sendSuccess(() -> Component.literal("Hélicoptère " + id + " : abattu, épave au sol (secteur " + h.secteur + ")"
                        + (h.essai ? ", essai (boîte noire sans points)." : ".")), false);
            } else {
                String pos = BlockPos.containing(e.position()).toShortString();
                s.sendSuccess(() -> Component.literal("Hélicoptère " + id + " : " + e.phase() + ", " + Math.round(e.pv()) + " / "
                        + Math.round(e.pvMax()) + " PV, en " + pos + (!h.points ? " (sans points)" : e.essai()
                        ? " (essai : touché en créatif ou en spectateur, boîte noire sans points)" : "")), false);
                s.sendSuccess(() -> Component.literal("Agresseurs : " + (e.agresseurs().isEmpty() ? "aucun" : String.join(", ", e.agresseurs()))), false);
                for (HelicoApi.Contribution c : e.contributions()) {
                    s.sendSuccess(() -> Component.literal("· " + c.nom() + " : " + Math.round(c.degats()) + " dégâts"
                            + (c.clan() != null ? " (clan " + c.clan().toString().substring(0, 8) + ")" : " (sans clan)")), false);
                }
            }
        }
        long reste = h.prochainMs - System.currentTimeMillis();
        String quand = h.prochainMs <= 0 ? "pas encore tirée" : heure(h.prochainMs) + (reste > 0 ? " (dans " + reste / 60_000L + " min)" : " (à l'essai)");
        s.sendSuccess(() -> Component.literal("Prochaine apparition : " + quand + (h.dernierRefus != null ? " · dernier refus : " + h.dernierRefus : "")
                + " · " + joueursMin + " joueur(s) sur la carte au moins.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int prochain(CommandSourceStack s, int minutes) {
        Helico h = instance;
        if (h == null) return 0;
        h.prochainMs = System.currentTimeMillis() + minutes * 60_000L;
        h.sauverEtat();
        s.sendSuccess(() -> Component.literal("Prochaine apparition de l'hélicoptère : " + heure(h.prochainMs) + " (conditions vérifiées à ce moment)."), false);
        Outils.journal("helico : prochaine apparition forcee a " + heure(h.prochainMs) + " par " + s.getTextName());
        return 1;
    }

    private static int lancer(CommandSourceStack s, boolean ici, boolean points) {
        Helico h = instance;
        if (h == null) return 0;
        if (ici) {
            ServerPlayer p = s.getPlayer();
            if (p == null || p.level().dimension() != Level.OVERWORLD) {
                s.sendFailure(Component.literal("« ici » : seulement en jeu, sur la carte."));
                return 0;
            }
            Vec3 regard = p.getLookAngle();
            double l = Math.max(1e-3, Math.sqrt(regard.x * regard.x + regard.z * regard.z));
            h.departDemande = new Vec3(p.getX() + regard.x / l * 48, p.getY(), p.getZ() + regard.z / l * 48);
        }
        String refus = Directeur.lancer(s.getServer(), ID, points, s.getTextName(), 0);
        if (refus != null) {
            h.departDemande = null;
            s.sendFailure(Component.literal("Pas lancé : " + refus + "."));
            return 0;
        }
        s.sendSuccess(() -> Component.literal("Hélicoptère lancé" + (points ? " (avec points de saison)." : " (sans points de saison).")), false);
        return 1;
    }

    // ------------------------------------------------------------------ état sur le disque (hors du monde)
    private void chargerEtat(Path dossier) {
        dossierCharge = dossier;
        prochainMs = 0;
        Path f = dossier.resolve("helico_etat.json");
        if (Files.exists(f)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                prochainMs = o.has("prochain") ? o.get("prochain").getAsLong() : 0;
            } catch (IOException | RuntimeException ex) {
                HeralizEvenements.LOG.error("[evenements] {} illisible : premiere apparition tiree a nouveau", f, ex);
            }
        }
        // au démarrage : heure absente, passée ou dans la minute -> 30 à 60 min au hasard (jamais juste après un démarrage)
        long maintenant = System.currentTimeMillis();
        if (prochainMs < maintenant + 60_000L) {
            repousser(maintenant, prochainMs <= 0 ? "premiere apparition" : "heure passee pendant l'arret (" + heure(prochainMs) + "), apparition");
        }
    }

    private void sauverEtat() {
        if (dossierCharge == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("prochain", prochainMs);
        o.addProperty("prochain_lisible", heure(prochainMs));
        Path dir = dossierCharge, f = dir.resolve("helico_etat.json");
        // 27/10 (perf) : écrit sur le fil d'écriture des événements (relevé ci-dessus, arbre neuf)
        EcrituresFond.ecrire(f, () -> {
            try {
                Files.createDirectories(dir);
                Path tmp = f.resolveSibling("helico_etat.json.tmp");
                Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                HeralizEvenements.LOG.error("[evenements] ecriture de {} impossible", f, ex);
            }
        }, null);
    }

    // ------------------------------------------------------------------ réglages
    @Override
    public void recharger() {
        if (Outils.dossier != null && !Outils.dossier.equals(dossierCharge)) chargerEtat(Outils.dossier);
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_evenements").resolve("helico.json");
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(defaut()), StandardCharsets.UTF_8);
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            joueursMin = entier(o, "joueurs_min", 10, 0, 200);
            pauseMs = entier(o, "pause_minutes", 240, 10, 7 * 24 * 60) * 60_000L;
            fenetreMs = entier(o, "fenetre_minutes", 120, 0, 24 * 60) * 60_000L;
            reessaiMs = entier(o, "reessai_minutes", 5, 1, 120) * 60_000L;
            coffreT7 = o.has("coffre_t7") ? o.get("coffre_t7").getAsString() : "t7_coffre_armes";
            coffreT7Min = entier(o, "coffre_t7_minutes", 15, 1, 120);
            R.pv = (float) reel(o, "pv", 600, 10, 100_000);
            R.multExplosifs = (float) reel(o, "mult_explosifs", 2.0, 0, 20);
            R.multQueue = (float) reel(o, "mult_queue", 1.25, 0, 20);
            R.regenApresS = entier(o, "regen_apres_s", 30, 0, 3600);
            R.regenPctParS = (float) reel(o, "regen_pct_par_s", 1.5, 0, 100);
            R.vitesse = reel(o, "vitesse", 0.45, 0.1, 2.0);
            R.altitudeMin = entier(o, "altitude_min", 30, 10, 150);
            R.altitudeMax = Math.max(R.altitudeMin, entier(o, "altitude_max", 45, 10, 150));
            R.patrouilleMinS = entier(o, "patrouille_min", 5, 1, 120) * 60;
            R.patrouilleMaxS = Math.max(R.patrouilleMinS, entier(o, "patrouille_max", 10, 1, 120) * 60);
            R.resteApresAttaqueS = entier(o, "reste_apres_attaque_s", 60, 0, 3600);
            R.volMaxS = Math.max(R.patrouilleMaxS, entier(o, "vol_max_minutes", 25, 1, 240) * 60);
            R.porteeTir = entier(o, "portee_tir", 60, 16, 256);
            R.poursuite = Math.max(R.porteeTir, entier(o, "poursuite", 96, 16, 512));
            R.rayonCercle = entier(o, "rayon_cercle", 40, 10, 120);
            R.altitudeCombat = entier(o, "altitude_combat", 22, 8, 80);
            R.degatsCoup = (float) reel(o, "degats_coup", 2.5, 0, 100);
            R.coupsRafale = entier(o, "coups_rafale", 8, 1, 60);
            R.pauseMinS = entier(o, "pause_rafale_min_s", 3, 1, 60);
            R.pauseMaxS = Math.max(R.pauseMinS, entier(o, "pause_rafale_max_s", 5, 1, 60));
            R.alerteTicks = (int) Math.round(reel(o, "alerte_s", 1.2, 0.1, 10) * 20);
            R.oubliAgresseursS = entier(o, "oubli_agresseurs_s", 60, 5, 3600);
            R.feuMinS = entier(o, "feu_min_s", 120, 5, 3600);
            R.feuMaxS = Math.max(R.feuMinS, entier(o, "feu_max_s", 210, 5, 3600));
            R.retraitS = entier(o, "retrait_minutes", 15, 0, 240) * 60;
            R.loinDesBases = entier(o, "loin_des_bases", 24, 0, 500);
            R.loinDuSpawn = entier(o, "loin_du_spawn", 64, 0, 2000);
            R.rechercheProche = entier(o, "recherche_proche", 96, 0, 256);
            R.rechercheLoin = Math.max(R.rechercheProche, entier(o, "recherche_loin", 200, 0, 512));
            R.crashDegats = (float) reel(o, "crash_degats", 8, 0, 100);
            R.crashRayon = entier(o, "crash_rayon", 6, 0, 32);
        } catch (IOException | RuntimeException ex) {
            HeralizEvenements.LOG.error("[evenements] helico.json illisible : reglages precedents gardes", ex);
        }
        HeralizEvenements.LOG.info("[evenements] helico : {} PV, patrouille {}-{} min (vol {} min au plus), pause {} min + fenetre {} min,"
                        + " {} joueur(s) au moins, prochain {}", Math.round(R.pv), R.patrouilleMinS / 60, R.patrouilleMaxS / 60, R.volMaxS / 60,
                pauseMs / 60_000L, fenetreMs / 60_000L, joueursMin, prochainMs > 0 ? heure(prochainMs) : "a tirer");
    }

    private static int entier(JsonObject o, String cle, int defaut, int min, int max) {
        return o.has(cle) ? Math.max(min, Math.min(max, o.get(cle).getAsInt())) : defaut;
    }

    private static double reel(JsonObject o, String cle, double defaut, double min, double max) {
        return o.has(cle) ? Math.max(min, Math.min(max, o.get(cle).getAsDouble())) : defaut;
    }

    static JsonObject defaut() {
        JsonObject o = new JsonObject();
        o.addProperty("_aide", "Helicoptere militaire (evenement aleatoire, aucune heure dans planning.json). Apres chaque helicoptere"
                + " (abattu, parti, annule) : pause_minutes, puis apparition a un instant tire au hasard dans fenetre_minutes ; refuse"
                + " (joueurs_min en survie sur la carte, ClashWar, un seul a la fois) : nouvel essai toutes les reessai_minutes."
                + " Il n'attaque que les joueurs qui lui ont fait des degats (oublies apres oubli_agresseurs_s), tire jusqu'a"
                + " portee_tir blocs, les suit jusqu'a poursuite blocs. Patrouille de patrouille_min a patrouille_max minutes (au"
                + " hasard) ; attaque, il ne part que reste_apres_attaque_s secondes apres le dernier coup recu, jamais apres"
                + " vol_max_minutes. Sans coup recu depuis regen_apres_s secondes, il regagne regen_pct_par_s % de ses PV par seconde."
                + " Points de saison : aucun a la destruction ; seulement la boite noire, revendue a Mila"
                + " (events.points.objet_boite_noire_helico). Butin : coffre_t7 (HeraliZ Loot) pendant coffre_t7_minutes a la fin du"
                + " feu de l'epave. Relu par /event recharger.");
        o.addProperty("joueurs_min", 10);
        o.addProperty("pause_minutes", 240);
        o.addProperty("fenetre_minutes", 120);
        o.addProperty("reessai_minutes", 5);
        o.addProperty("pv", 600);
        o.addProperty("mult_explosifs", 2.0);
        o.addProperty("mult_queue", 1.25);
        o.addProperty("regen_apres_s", 30);
        o.addProperty("regen_pct_par_s", 1.5);
        o.addProperty("vitesse", 0.45);
        o.addProperty("altitude_min", 30);
        o.addProperty("altitude_max", 45);
        o.addProperty("patrouille_min", 5);
        o.addProperty("patrouille_max", 10);
        o.addProperty("reste_apres_attaque_s", 60);
        o.addProperty("vol_max_minutes", 25);
        o.addProperty("portee_tir", 60);
        o.addProperty("poursuite", 96);
        o.addProperty("rayon_cercle", 40);
        o.addProperty("altitude_combat", 22);
        o.addProperty("degats_coup", 2.5);
        o.addProperty("coups_rafale", 8);
        o.addProperty("pause_rafale_min_s", 3);
        o.addProperty("pause_rafale_max_s", 5);
        o.addProperty("alerte_s", 1.2);
        o.addProperty("oubli_agresseurs_s", 60);
        o.addProperty("feu_min_s", 120);
        o.addProperty("feu_max_s", 210);
        o.addProperty("retrait_minutes", 15);
        o.addProperty("loin_des_bases", 24);
        o.addProperty("loin_du_spawn", 64);
        o.addProperty("recherche_proche", 96);
        o.addProperty("recherche_loin", 200);
        o.addProperty("crash_degats", 8);
        o.addProperty("crash_rayon", 6);
        o.addProperty("coffre_t7", "t7_coffre_armes");
        o.addProperty("coffre_t7_minutes", 15);
        return o;
    }
}
