package fr.heraliz.evenements.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.helico.PointCrash;
import fr.heraliz.airstrike.strike.Strike;
import fr.heraliz.airstrike.strike.StrikeManager;
import fr.heraliz.airstrike.strike.StrikeType;
import fr.heraliz.evenements.HeralizEvenements;
import fr.heralisz.utils.spawn.DepartServeur;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Largage militaire disputé (docs/EVENEMENTS.md §4.1) : toutes les 3 h 30 (planning, 25/10, demande du propriétaire), un
 * point est tiré au hasard sur la carte ; annonce à tous, dans le chat et en notification, avec ses coordonnées 6 minutes
 * avant, rappel 3 minutes avant ; puis un avion cargo passe et la caisse descend en parachute avec sa fumée. Le clan du
 * PREMIER joueur qui ouvre la caisse gagne les points {@code events.points.largage} du barème de la saison (plafond
 * {@code events.par_jour.largage} par clan et par jour). Réglages : {@code config/heraliz_evenements/largage.json}
 * (relu par {@code /event recharger}) ; lieux posés par le staff ({@code /event point ajouter largage <nom>}) si le réglage
 * {@code lieux} le demande. Réutilise tel quel le largage des frappes aériennes (avion, parachute, caisse, butin,
 * disparition). Coût : une comparaison d'heure par seconde.
 * <p>
 * Tirage du point (02/11, audit n° 16 ; même méthode que la borne de départ du 01/11) : rien n'est lu sur le disque par
 * le fil du serveur. Un point candidat n'est gardé que si les fichiers de régions de la carte existent autour de lui
 * (jamais de terrain neuf dans {@code dayz_map}), hors des zones VIP (HeraliZ Grades, comme l'hélicoptère et les frappes)
 * et loin des bases ; son tronçon est lu en tâche de fond (il doit être sur le disque, complet), puis chargé par
 * Minecraft en tâche de fond (ticket) et contrôlé à la seconde suivante. L'annonce part dès qu'un point passe (quelques
 * secondes après le lancement) ; {@code essais} tronçons lus au plus, 4 à la fois.
 */
final class Largage implements Evenement {
    static final String ID = "largage";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Random ALEA = new Random();

    // ------------------------------------------------------------------ réglages (largage.json)
    /** « hasard » (point tiré sur la carte), « poses » (lieux du staff), « les_deux » (un sur deux). */
    private static String lieux = "hasard";
    private static int annonceMin = 6;
    private static int rappelMin = 3;
    /** L'avion part ce temps avant la fin de l'annonce : passage au-dessus du point et chute de la caisse. */
    private static int avanceS = 40;
    private static int margeBordure = 150;
    private static int loinDesBases = 48;
    private static int essais = 40;
    /** Rectangle x1 z1 x2 z2 où tirer le point (dans la bordure du monde), null : toute la bordure. */
    @Nullable
    private static int[] zone;

    // ------------------------------------------------------------------ recherche du point (tâche de fond, audit n° 16)
    /** Tronçon d'un point candidat demandé à Minecraft (chargé en tâche de fond), 30 s au plus. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("heraliz_largage", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);
    /** Candidats contrôlés en même temps. */
    private static final int EN_PARALLELE = 4;
    /** Tirages sans rien lire par essai (régions absentes, zone VIP, base : refusés sans coût). */
    private static final int TIRAGES = 64;
    /**
     * Autour du point, les fichiers de régions doivent exister jusqu'à cette distance : Minecraft charge aussi, en partie,
     * les tronçons voisins d'un tronçon chargé (11 tronçons de rayon) ; hors de la carte construite, il les générerait.
     */
    private static final int RAYON_REGIONS = 11 * 16;
    /** Attente maximale d'un candidat (lecture du disque, puis chargement), en ms ; passé ce délai, il est refusé. */
    private static final long ATTENTE_MS = 20_000L;

    /** Un point candidat : son tronçon est lu sur le disque ({@code lecture}), puis chargé ({@code ticket}). */
    private static final class Candidat {
        final int x, z;
        final ChunkPos cp;
        @Nullable
        CompletableFuture<Optional<CompoundTag>> lecture;
        boolean ticket;
        long limiteMs;

        Candidat(int x, int z, long limiteMs) {
            this.x = x;
            this.z = z;
            this.cp = new ChunkPos(x >> 4, z >> 4);
            this.limiteMs = limiteMs;
        }
    }

    private final List<Candidat> candidats = new ArrayList<>();
    /** Régions vues absentes pendant cette recherche (plus de coup d'œil au disque pour elles). */
    private final Set<Long> regionsAbsentes = new HashSet<>();
    private boolean recherche, rechercheAvecPoints, posesEssayes;
    private String recherchePar = "";
    private int essaisRestants;
    private int rx1, rz1, rx2, rz2;
    private int refusRegion, refusVip, refusBase, refusDisque, refusSol;

    // ------------------------------------------------------------------ état
    @Nullable
    private String idCourant;
    private String secteur = "";
    private BlockPos cible = BlockPos.ZERO;
    private boolean enVol;
    private boolean rappelFait;
    private long rappelMs;
    private long lancementMs;
    private long finMs;

    @Override
    public String id() {
        return ID;
    }

    @Nullable
    @Override
    public String refus(MinecraftServer server) {
        if (lieux.equals("poses") && Lieux.liste(ID).isEmpty()) return "aucun lieu de largage (/event point ajouter largage <nom>)";
        return null;
    }

    @Override
    public boolean demarrer(MinecraftServer server, int numero, boolean points, String par) {
        ServerLevel level = server.overworld();
        String id = ID + "#" + numero;
        boolean poses = lieux.equals("poses") || lieux.equals("les_deux") && ALEA.nextBoolean();
        if (poses) {
            BlockPos p = lieuPose(level);
            if (p != null) {
                lancerSur(server, id, p, points, par);
                return idCourant != null;
            }
            if (!lieux.equals("les_deux")) {
                Outils.journal("largage : aucun lieu pose valide (" + lieux + ")");
                return false;
            }
        }
        // point au hasard : cherché en tâche de fond, l'annonce part dès qu'il est trouvé
        if (!rectangle(level)) {
            BlockPos p = !poses && lieux.equals("les_deux") ? lieuPose(level) : null;
            if (p == null) {
                Outils.journal("largage : aucun point valide (" + lieux + ")");
                return false;
            }
            lancerSur(server, id, p, points, par);
            return idCourant != null;
        }
        idCourant = id;
        recherche = true;
        rechercheAvecPoints = points;
        recherchePar = par;
        posesEssayes = poses;
        essaisRestants = essais;
        candidats.clear();
        regionsAbsentes.clear();
        refusRegion = refusVip = refusBase = refusDisque = refusSol = 0;
        Outils.journal(id + " lance par " + par + " : recherche du point en tache de fond (" + essais + " essais au plus)");
        chercher(server);
        return idCourant != null;   // déjà raté (aucun tirage possible) : refusé, comme avant
    }

    /** Le point est choisi : annonce, suivi, départ de l'avion à l'heure. */
    private void lancerSur(MinecraftServer server, String id, BlockPos p, boolean points, String par) {
        ServerLevel level = server.overworld();
        recherche = false;
        idCourant = id;
        cible = p;
        secteur = Outils.secteur(level, p.getX(), p.getZ());
        enVol = false;
        long maintenant = System.currentTimeMillis();
        lancementMs = maintenant + Math.max(0, annonceMin * 60_000L - avanceS * 1000L);
        rappelFait = rappelMin <= 0 || rappelMin >= annonceMin;
        rappelMs = maintenant + (annonceMin - rappelMin) * 60_000L;
        Etat.ouvrir(id, points, secteur);
        Outils.journal(id + " lance par " + par + " en " + p.toShortString() + " (secteur " + secteur + ")" + (points ? "" : ", sans points"));
        String x = String.valueOf(p.getX()), z = String.valueOf(p.getZ());
        if (annonceMin > 0) {
            Outils.annoncer(server, Outils.t("heraliz_evenements.largage.annonce",
                    "Largage militaire dans %1$s min · X %2$s, Z %3$s (secteur %4$s). Le premier clan qui ouvre la caisse gagne des points de saison.",
                    String.valueOf(annonceMin), x, z, secteur).withStyle(ChatFormatting.GOLD), true);
        } else {
            Outils.annoncer(server, Outils.t("heraliz_evenements.largage.approche",
                    "Largage militaire en approche · X %1$s, Z %2$s (secteur %3$s). Le premier clan qui ouvre la caisse gagne des points de saison.",
                    x, z, secteur).withStyle(ChatFormatting.GOLD), true);
        }
        if (lancementMs <= maintenant) decoller(server);
    }

    @Override
    public boolean actif() {
        return idCourant != null;
    }

    @Override
    public void seconde(MinecraftServer server) {
        if (idCourant == null) return;
        if (recherche) {
            chercher(server);
            return;
        }
        long maintenant = System.currentTimeMillis();
        if (!enVol) {
            if (!rappelFait && maintenant >= rappelMs) {
                rappelFait = true;
                Outils.annoncer(server, Outils.t("heraliz_evenements.largage.rappel", "Largage militaire dans %1$s min · X %2$s, Z %3$s (secteur %4$s).",
                        String.valueOf(rappelMin), String.valueOf(cible.getX()), String.valueOf(cible.getZ()), secteur).withStyle(ChatFormatting.GOLD), true);
            }
            if (maintenant >= lancementMs) decoller(server);
            return;
        }
        // 02/11 (audit mods-2 §10) : la caisse disparaît maintenant à l'heure, même loin de tout joueur ; l'événement reste
        // ouvert tant qu'elle est là (une caisse atterrie tard garde ses points jusqu'à sa disparition)
        if (maintenant > finMs && !fr.heraliz.airstrike.entity.SupplyCrateEntityServeur.caisseEnAttente(server.overworld(), idCourant)) {
            Outils.journal(idCourant + " : caisse pas ouverte a temps (secteur " + secteur + ")");
            Etat.fermer(idCourant);
            idCourant = null;
        }
    }

    /** L'avion part d'un point à 400 blocs du lieu, dans une direction au hasard. */
    private void decoller(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Vec3 c = new Vec3(cible.getX() + 0.5, cible.getY(), cible.getZ() + 0.5);
        double angle = ALEA.nextDouble() * Math.PI * 2;
        Strike s = StrikeManager.launch(level, StrikeType.SUPPLY, c, c.add(Math.cos(angle) * 400, 0, Math.sin(angle) * 400), null);
        if (s == null) {
            Outils.journal(idCourant + " : avion impossible a lancer (frappes aeriennes) : annule");
            Etat.fermer(idCourant);
            idCourant = null;
            return;
        }
        s.evenement = idCourant;
        enVol = true;
        finMs = System.currentTimeMillis() + (AirstrikeConfig.SUPPLY_DESPAWN_MINUTES.get() + 3) * 60_000L;
        Outils.journal(idCourant + " : avion parti");
    }

    @Override
    public void annuler(MinecraftServer server, String motif) {
        if (idCourant == null) return;
        if (recherche) {
            // rien n'a été annoncé ni noté ouvert : on lâche seulement les tronçons demandés
            lacherCandidats(server.overworld());
            recherche = false;
            Outils.journal(idCourant + " : recherche du point arretee (" + motif + ")");
            idCourant = null;
            return;
        }
        // pas encore parti : aucune caisse ne viendra. Parti : la caisse déjà en vol ou au sol reste (butin normal) et
        // garde son numéro : ouverte après un redémarrage, elle donne encore ses points (Etat la garde ouverte)
        if (!enVol) Etat.fermer(idCourant);
        Outils.journal(idCourant + " : suivi arrete (" + motif + ")" + (enVol ? "" : ", avant le depart de l'avion"));
        idCourant = null;
    }

    @Override
    public void coffreOuvert(ServerPlayer p, String id) {
        if (id.equals(idCourant)) idCourant = null;
        Etat.Ouvert o = Etat.ouvert(id);
        if (o == null) return;   // déjà récompensé, ou trop ancien
        String sec = o.secteur();
        if (!o.points()) {
            Etat.fermer(id);
            Outils.journal(id + " ouvert par " + p.getGameProfile().getName() + " (sans points)");
            return;
        }
        if (!Etat.recompenser(id)) return;   // noté sur le disque AVANT les points : jamais deux fois
        double pts = Outils.pointsSaison(p, ID);
        Outils.journal(id + " ouvert par " + p.getGameProfile().getName() + " (secteur " + sec + ") : " + pts + " point(s) de saison");
        Outils.annoncer(p.server, Outils.t("heraliz_evenements.largage.ouvert", "Le largage du secteur %s a été ouvert.", sec), false);
        if (pts > 0) {
            p.sendSystemMessage(Outils.t("heraliz_evenements.largage.points", "+%s points de saison pour ton clan (largage).",
                    String.valueOf(Math.round(pts))).withStyle(ChatFormatting.GREEN));
        }
    }

    // ------------------------------------------------------------------ choix du point
    @Nullable
    private static BlockPos lieuPose(ServerLevel level) {
        PointCrash.lireVip(level.getServer());
        List<Lieux.Lieu> l = new ArrayList<>(Lieux.liste(ID));
        Collections.shuffle(l, ALEA);
        for (Lieux.Lieu x : l) {
            if (!procheDUneBase(level, x.pos()) && level.getWorldBorder().isWithinBounds(x.pos())
                    && !PointCrash.dansZoneVip(x.pos().getX() + 0.5, x.pos().getZ() + 0.5)) return x.pos();
        }
        return null;
    }

    /**
     * Rectangle du tirage : la bordure du monde (moins {@code marge_bordure}), limitée par {@code zone}. Faux s'il est
     * trop petit ou trop grand (noté au journal).
     */
    private boolean rectangle(ServerLevel level) {
        WorldBorder b = level.getWorldBorder();
        double x1 = b.getMinX() + margeBordure, x2 = b.getMaxX() - margeBordure;
        double z1 = b.getMinZ() + margeBordure, z2 = b.getMaxZ() - margeBordure;
        if (zone != null) {
            x1 = Math.max(x1, zone[0]);
            z1 = Math.max(z1, zone[1]);
            x2 = Math.min(x2, zone[2]);
            z2 = Math.min(z2, zone[3]);
        }
        if (x2 - x1 < 64 || z2 - z1 < 64) return false;
        if (x2 - x1 > 20_000 || z2 - z1 > 20_000) {
            Outils.journal("largage : bordure du monde trop grande pour tirer un point (" + Math.round(x2 - x1) + " blocs) : regler \"zone\" dans largage.json");
            return false;
        }
        rx1 = Mth.ceil(x1);
        rz1 = Mth.ceil(z1);
        rx2 = Mth.floor(x2);
        rz2 = Mth.floor(z2);
        return true;
    }

    /**
     * Un pas de la recherche (au lancement, puis chaque seconde) : avance les candidats en cours (lecture du disque faite ?
     * tronçon chargé ?), contrôle ceux qui sont prêts, en tire de nouveaux. Rien n'est lu sur le disque par ce fil.
     */
    private void chercher(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long maintenant = System.currentTimeMillis();
        for (Iterator<Candidat> it = candidats.iterator(); it.hasNext(); ) {
            Candidat c = it.next();
            boolean fini = false;
            BlockPos p = null;
            if (!c.ticket && c.lecture != null) {
                if (c.lecture.isDone()) {
                    if (completSurDisque(c.lecture)) {
                        level.getChunkSource().addRegionTicket(TICKET, c.cp, 0, c.cp);
                        c.ticket = true;
                        c.limiteMs = maintenant + ATTENTE_MS;
                    } else {
                        refusDisque++;
                        fini = true;
                    }
                } else if (maintenant > c.limiteMs) {
                    refusDisque++;
                    fini = true;
                }
            }
            if (!fini && (c.ticket || c.lecture == null)) {
                if (level.getChunkSource().getChunkNow(c.cp.x, c.cp.z) != null) {
                    fini = true;
                    p = controler(level, c.x, c.z);
                    if (p == null) refusSol++;
                } else if (maintenant > c.limiteMs) {
                    refusDisque++;
                    fini = true;
                }
            }
            if (!fini) continue;
            it.remove();
            if (c.ticket) level.getChunkSource().removeRegionTicket(TICKET, c.cp, 0, c.cp);
            if (p != null) {
                lacherCandidats(level);
                String id = idCourant;
                if (id != null) lancerSur(server, id, p, rechercheAvecPoints, recherchePar);
                return;
            }
        }
        if (candidats.size() < EN_PARALLELE && essaisRestants > 0) PointCrash.lireVip(server);
        while (candidats.size() < EN_PARALLELE && essaisRestants > 0) {
            essaisRestants--;
            Candidat c = tirer(level, maintenant);
            if (c != null) candidats.add(c);
        }
        if (candidats.isEmpty() && essaisRestants <= 0) echecRecherche(server);
    }

    /**
     * Un candidat tiré sans rien lire : dans le rectangle, à l'intérieur de son tronçon (les relevés à 3 blocs autour
     * restent dans ce tronçon), régions de la carte présentes autour, hors zone VIP, loin des bases ; null si
     * {@link #TIRAGES} tirages sont refusés. Son tronçon est demandé au disque en tâche de fond (déjà chargé : rien à lire).
     */
    @Nullable
    private Candidat tirer(ServerLevel level, long maintenant) {
        for (int i = 0; i < TIRAGES; i++) {
            int x = ((rx1 + ALEA.nextInt(rx2 - rx1 + 1)) & ~15) + 3 + ALEA.nextInt(10);
            int z = ((rz1 + ALEA.nextInt(rz2 - rz1 + 1)) & ~15) + 3 + ALEA.nextInt(10);
            if (x < rx1 || x > rx2 || z < rz1 || z > rz2) continue;
            if (!regionsPresentes(level, x, z)) {
                refusRegion++;
                continue;
            }
            if (PointCrash.dansZoneVip(x + 0.5, z + 0.5)) {
                refusVip++;
                continue;
            }
            if (procheDUneBase(level, new BlockPos(x, level.getSeaLevel(), z))) {
                refusBase++;
                continue;
            }
            Candidat c = new Candidat(x, z, maintenant + ATTENTE_MS);
            if (level.getChunkSource().getChunkNow(c.cp.x, c.cp.z) == null) {
                try {
                    c.lecture = level.getChunkSource().chunkMap.read(c.cp);
                } catch (RuntimeException e) {
                    refusDisque++;
                    continue;
                }
            }
            return c;
        }
        return null;
    }

    /** Les fichiers de régions du point et de son voisinage ({@link #RAYON_REGIONS}) existent (régions absentes gardées). */
    private boolean regionsPresentes(ServerLevel level, int x, int z) {
        return region(level, x, z) && region(level, x - RAYON_REGIONS, z - RAYON_REGIONS) && region(level, x + RAYON_REGIONS, z - RAYON_REGIONS)
                && region(level, x - RAYON_REGIONS, z + RAYON_REGIONS) && region(level, x + RAYON_REGIONS, z + RAYON_REGIONS);
    }

    private boolean region(ServerLevel level, int x, int z) {
        long cle = ChunkPos.asLong(x >> 9, z >> 9);
        if (regionsAbsentes.contains(cle)) return false;
        if (DepartServeur.regionPresente(level, x, z)) return true;
        regionsAbsentes.add(cle);
        return false;
    }

    /** Le tronçon lu est sur le disque et complet (jamais un tronçon à générer ou à finir de générer). */
    private static boolean completSurDisque(CompletableFuture<Optional<CompoundTag>> lecture) {
        try {
            Optional<CompoundTag> o = lecture.getNow(Optional.empty());
            if (o.isEmpty()) return false;
            CompoundTag t = o.get();
            if (t.contains("Level", 10)) t = t.getCompound("Level");          // écrit par une ancienne version, pas encore réécrit
            if (!t.contains("Status", 8)) return t.getBoolean("TerrainPopulated");   // avant 1.13
            String statut = t.getString("Status");
            int i = statut.indexOf(':');
            if (i >= 0) statut = statut.substring(i + 1);
            return statut.equals("full") || statut.equals("postprocessed") || statut.equals("fullchunk");
        } catch (RuntimeException e) {
            return false;   // lecture en erreur
        }
    }

    /**
     * Contrôle d'un point dont le tronçon est chargé (rien d'autre n'est chargé : tous les relevés sont dans ce tronçon) :
     * sol à ciel ouvert, ni eau, ni lave, ni feuillage, terrain à peu près plat sur 3 blocs autour (pas un bord de toit ni
     * une falaise), loin des bases des clans. Null s'il ne passe pas.
     */
    @Nullable
    private static BlockPos controler(ServerLevel level, int x, int z) {
        BlockPos haut = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        if (haut.getY() <= level.getMinBuildHeight() + 2) return null;
        BlockState sol = level.getBlockState(haut.below());
        if (!sol.getFluidState().isEmpty() || sol.is(BlockTags.LEAVES) || sol.isAir()) return null;
        if (!plat(level, haut)) return null;
        if (procheDUneBase(level, haut)) return null;
        return haut;
    }

    private static boolean plat(ServerLevel level, BlockPos haut) {
        int[][] d = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int[] o : d) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, haut.getX() + o[0], haut.getZ() + o[1]);
            if (Math.abs(y - haut.getY()) > 2) return false;
        }
        return true;
    }

    /** Lâche les tronçons demandés par les candidats en cours (une lecture du disque en cours se finit seule, sans suite). */
    private void lacherCandidats(ServerLevel level) {
        for (Candidat c : candidats) {
            if (c.ticket) level.getChunkSource().removeRegionTicket(TICKET, c.cp, 0, c.cp);
        }
        candidats.clear();
    }

    /** Aucun point au hasard : lieux du staff si le réglage le permet, sinon le largage n'a pas lieu (noté au journal). */
    private void echecRecherche(MinecraftServer server) {
        String id = idCourant;
        recherche = false;
        Outils.journal(id + " : aucun point valide en " + essais + " essais (refus : " + refusRegion + " hors de la carte construite, "
                + refusVip + " zone VIP, " + refusBase + " pres d'une base, " + refusDisque + " troncon absent ou incomplet, " + refusSol
                + " sol)" + (refusVip > 0 && refusRegion + refusBase + refusDisque + refusSol == 0 ? " : zones VIP illisibles ?" : ""));
        BlockPos p = lieux.equals("les_deux") && !posesEssayes ? lieuPose(server.overworld()) : null;
        if (p != null && id != null) {
            lancerSur(server, id, p, rechercheAvecPoints, recherchePar);
            return;
        }
        HeralizEvenements.LOG.warn("[evenements] {} : aucun point de largage valide, pas de largage (journal heraliz_evenements)", id);
        idCourant = null;
    }

    // ------------------------------------------------------------------ réglages
    @Override
    public void recharger() {
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_evenements").resolve("largage.json");
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(defaut()), StandardCharsets.UTF_8);
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            String l = o.has("lieux") ? o.get("lieux").getAsString().toLowerCase(Locale.ROOT) : "hasard";
            lieux = l.equals("poses") || l.equals("les_deux") ? l : "hasard";
            annonceMin = borne(o, "annonce_minutes", 6, 0, 30);
            rappelMin = borne(o, "rappel_minutes", 3, 0, 30);
            avanceS = borne(o, "avance_secondes", 40, 0, 300);
            margeBordure = borne(o, "marge_bordure", 150, 0, 2000);
            loinDesBases = borne(o, "loin_des_bases", 48, 0, 500);
            essais = borne(o, "essais", 40, 1, 200);
            zone = null;
            if (o.has("zone") && o.get("zone").isJsonArray()) {
                JsonArray a = o.getAsJsonArray("zone");
                if (a.size() == 4) {
                    zone = new int[]{Math.min(a.get(0).getAsInt(), a.get(2).getAsInt()), Math.min(a.get(1).getAsInt(), a.get(3).getAsInt()),
                            Math.max(a.get(0).getAsInt(), a.get(2).getAsInt()), Math.max(a.get(1).getAsInt(), a.get(3).getAsInt())};
                }
            }
        } catch (IOException | RuntimeException ex) {
            HeralizEvenements.LOG.error("[evenements] largage.json illisible : reglages par defaut", ex);
        }
        HeralizEvenements.LOG.info("[evenements] largage : lieux {}, annonce {} min, rappel {} min", lieux, annonceMin, rappelMin);
    }

    private static int borne(JsonObject o, String cle, int defaut, int min, int max) {
        return o.has(cle) ? Math.max(min, Math.min(max, o.get(cle).getAsInt())) : defaut;
    }

    private static JsonObject defaut() {
        JsonObject o = new JsonObject();
        o.addProperty("_aide", "Largage militaire. lieux : hasard (point tire sur la carte), poses (lieux du staff, /event point ajouter"
                + " largage <nom>), les_deux. annonce_minutes : annonce avec les coordonnees avant l'arrivee ; rappel_minutes : second message ;"
                + " avance_secondes : l'avion part ce temps avant la fin de l'annonce (passage et chute) ; marge_bordure : blocs a eviter"
                + " pres de la bordure du monde ; loin_des_bases : blocs autour des bases des clans ; zone : [x1, z1, x2, z2] pour limiter"
                + " le tirage (absent : toute la bordure) ; essais : points lus en tache de fond au plus (jamais hors de la carte construite,"
                + " jamais dans une zone VIP). Relu par /event recharger.");
        o.addProperty("lieux", "hasard");
        o.addProperty("annonce_minutes", 6);
        o.addProperty("rappel_minutes", 3);
        o.addProperty("avance_secondes", 40);
        o.addProperty("marge_bordure", 150);
        o.addProperty("loin_des_bases", 48);
        o.addProperty("essais", 40);
        return o;
    }

    // ------------------------------------------------------------------ bases (CRac, par réflexion)
    @Nullable
    private static Method claimAt;
    private static boolean claimCherche;

    /** Un claim de base de clan (CRac) à moins de {@code loin_des_bases} blocs (contrôle léger : 9 points). */
    private static boolean procheDUneBase(ServerLevel level, BlockPos pos) {
        if (!claimCherche) {
            claimCherche = true;
            try {
                claimAt = Class.forName("fr.heraliz.crac.claim.ClaimManager").getMethod("getAt", ServerLevel.class, BlockPos.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                claimAt = null;
            }
        }
        if (claimAt == null || loinDesBases <= 0) return false;
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (claimAt.invoke(null, level, pos.offset(dx * loinDesBases, 0, dz * loinDesBases)) != null) return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
        return false;
    }
}
