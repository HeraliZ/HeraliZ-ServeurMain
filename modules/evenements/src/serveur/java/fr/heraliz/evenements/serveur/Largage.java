package fr.heraliz.evenements.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.strike.Strike;
import fr.heraliz.airstrike.strike.StrikeManager;
import fr.heraliz.airstrike.strike.StrikeType;
import fr.heraliz.evenements.HeralizEvenements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
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
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Largage militaire disputé (docs/EVENEMENTS.md §4.1) : toutes les 3 h 30 (planning, 25/10, demande du propriétaire), un
 * point est tiré au hasard sur la carte ; annonce à tous, dans le chat et en notification, avec ses coordonnées 6 minutes
 * avant, rappel 3 minutes avant ; puis un avion cargo passe et la caisse descend en parachute avec sa fumée. Le clan du
 * PREMIER joueur qui ouvre la caisse gagne les points {@code events.points.largage} du barème de la saison (plafond
 * {@code events.par_jour.largage} par clan et par jour). Réglages : {@code config/heraliz_evenements/largage.json}
 * (relu par {@code /event recharger}) ; lieux posés par le staff ({@code /event point ajouter largage <nom>}) si le réglage
 * {@code lieux} le demande. Réutilise tel quel le largage des frappes aériennes (avion, parachute, caisse, butin,
 * disparition). Coût : une comparaison d'heure par seconde ; le tirage du point charge au plus {@code essais} tronçons, une
 * fois toutes les 3 h 30.
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
        BlockPos p = choisir(level);
        if (p == null) return false;
        String id = ID + "#" + numero;
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
        return idCourant != null;
    }

    @Override
    public boolean actif() {
        return idCourant != null;
    }

    @Override
    public void seconde(MinecraftServer server) {
        if (idCourant == null) return;
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
        if (maintenant > finMs) {
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
    private static BlockPos choisir(ServerLevel level) {
        boolean poses = lieux.equals("poses") || lieux.equals("les_deux") && ALEA.nextBoolean();
        BlockPos p = poses ? lieuPose(level) : pointAuHasard(level);
        if (p == null && lieux.equals("les_deux")) p = poses ? pointAuHasard(level) : lieuPose(level);
        if (p == null) Outils.journal("largage : aucun point valide (" + lieux + ")");
        return p;
    }

    @Nullable
    private static BlockPos lieuPose(ServerLevel level) {
        List<Lieux.Lieu> l = new ArrayList<>(Lieux.liste(ID));
        Collections.shuffle(l, ALEA);
        for (Lieux.Lieu x : l) {
            if (!procheDUneBase(level, x.pos()) && level.getWorldBorder().isWithinBounds(x.pos())) return x.pos();
        }
        return null;
    }

    /**
     * Un point au hasard dans la bordure du monde (moins {@code marge_bordure}) : sol à ciel ouvert, ni eau, ni lave, ni
     * feuillage, terrain à peu près plat sur 3 blocs autour (pas un bord de toit ni une falaise), loin des bases des clans.
     */
    @Nullable
    private static BlockPos pointAuHasard(ServerLevel level) {
        WorldBorder b = level.getWorldBorder();
        double x1 = b.getMinX() + margeBordure, x2 = b.getMaxX() - margeBordure;
        double z1 = b.getMinZ() + margeBordure, z2 = b.getMaxZ() - margeBordure;
        if (zone != null) {
            x1 = Math.max(x1, zone[0]);
            z1 = Math.max(z1, zone[1]);
            x2 = Math.min(x2, zone[2]);
            z2 = Math.min(z2, zone[3]);
        }
        if (x2 - x1 < 64 || z2 - z1 < 64) return null;
        if (x2 - x1 > 20_000 || z2 - z1 > 20_000) {
            Outils.journal("largage : bordure du monde trop grande pour tirer un point (" + Math.round(x2 - x1) + " blocs) : regler \"zone\" dans largage.json");
            return null;
        }
        for (int i = 0; i < essais; i++) {
            int x = (int) Math.floor(x1 + ALEA.nextDouble() * (x2 - x1));
            int z = (int) Math.floor(z1 + ALEA.nextDouble() * (z2 - z1));
            BlockPos haut = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
            if (haut.getY() <= level.getMinBuildHeight() + 2) continue;
            BlockState sol = level.getBlockState(haut.below());
            if (!sol.getFluidState().isEmpty() || sol.is(BlockTags.LEAVES) || sol.isAir()) continue;
            if (!plat(level, haut)) continue;
            if (procheDUneBase(level, haut)) continue;
            return haut;
        }
        return null;
    }

    private static boolean plat(ServerLevel level, BlockPos haut) {
        int[][] d = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int[] o : d) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, haut.getX() + o[0], haut.getZ() + o[1]);
            if (Math.abs(y - haut.getY()) > 2) return false;
        }
        return true;
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
                + " le tirage (absent : toute la bordure). Relu par /event recharger.");
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
