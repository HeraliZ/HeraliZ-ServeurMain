package fr.heralisz.matchs.zombies.jeu.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Carte;
import fr.heralisz.matchs.moteur.api.Pose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Metadonnees zombies d'une carte (MATCHS_SPEC §4 + ZOMBIES_V2_SPEC §1, champs propres du JSON
 * {@code cartes/<carte>.json}) converties en coordonnees <b>absolues</b> pour une instance posee. Immuable, construit
 * une fois par partie ({@link #lire}). Lecture <b>tolerante</b> : un champ absent prend une valeur par defaut sensee
 * (l'ancienne carte comme la nouvelle fonctionnent), une entree invalide est ignoree avec un avertissement.
 * <p>Format lu (coordonnees relatives a la structure, AABB en blocs <b>inclus</b>, {@code face} =
 * nord|sud|est|ouest ou north|south|east|west) :
 * <pre>
 * spawn_joueurs [[x,y,z,yaw]...], zone_depart "id",
 * zones [{id, nom, nom_en, points_apparition [[x,y,z,yaw]...], apparitions_salle [[x,y,z]...], apparitions_salle_details [{pos, genre}],
 *         fenetres [{planches [x0,y0,z0,x1,y1,z1], apparition [x,y,z,yaw], exterieur [x,y,z], interieur [x,y,z], face}],
 *         volumes [[x0,y0,z0,x1,y1,z1]...]}],
 * portes [{id, zone_ouverte, relie [zoneA, zoneB], cout, type "debris|porte|grille" (ancien : style "porte|eboulis"),
 *          blocs [x0,y0,z0,x1,y1,z1], pancarte [x,y,z,face], pancarte_verso, nom_fr, courant_requis}],
 * armes_mur [{pos [x,y,z], face, gunId, cout, cout_munitions, zone}],
 * boite [[x,y,z,yaw]...], boite_details [{pos [x,y,z], face, zone}] (zone de chaque emplacement, meme position que
 * dans boite ; sans zone = toujours ouvert), atouts [{pos, face, type "blindage|reanimation|vigueur|double_impact|rechargement", cout, zone}],
 * courant [x,y,z,yaw],
 * pack_a_punch {pos, face, cout, zone}, ateliers [{pos, face, zone}], arme_carte {gunId, nom_fr},
 * easter_egg {etapes [{pos, type "radio|interrupteur|objet", zone}], recompense "texte ou id"},
 * objectifs [{id, texte_fr, texte_en}], intro_fr "ligne\nligne" ou ["ligne", ...], intro_en (idem, facultatif),
 * v3 : portes[].ouverte_par_courant, atouts[].secret, version_format, et tous les champs de {@link PlanGiant}
 * </pre>
 * Les fenetres sont numerotees globalement (ordre des zones puis ordre dans la zone) : {@link Fenetre#index()}.
 * Utilise par le jeu et par le contenu ({@link ZombiesDecor#installer}) pour poser les blocs-entites.
 */
public final class PlanZombies {
    /**
     * Zone de la carte : ouverte par une porte, ses points d'apparition et fenetres ne servent qu'une fois ouverte.
     *
     * @param apparitions       anciens {@code points_apparition} (sorties de terre) qui ne doublonnent pas un point de salle
     * @param apparitionsSalle  {@code apparitions_salle} : points DANS la zone (sortie du sol, coins, conduits)
     * @param nomEn             {@code nom_en} : nom anglais (vide = {@code nom}), affiche sous la boussole du HUD
     * @param genresSalle       genre de chaque point de salle ({@code sol|coin|conduit|egout|gravats|tunnel}, champ
     *                          {@code apparitions_salle_details}), {@code sol} par defaut ; meme ordre que {@code apparitionsSalle}
     */
    public record Zone(String id, String nom, List<Pose> apparitions, List<Integer> fenetres, List<AABB> volumes, List<Pose> apparitionsSalle,
                       List<String> genresSalle, String nomEn) {
        /** Vrai si le point (absolu) est dans un des volumes de la zone. */
        public boolean contient(double x, double y, double z) {
            for (int i = 0; i < volumes.size(); i++) if (volumes.get(i).contains(x, y, z)) return true;
            return false;
        }
    }

    /**
     * Fenetre barricadee (6 planches).
     *
     * @param index      numero global
     * @param zone       id de la zone
     * @param planches   volume des planches (absolu, max exclusif)
     * @param boite      meme volume en blocs inclus
     * @param apparition point d'apparition des zombies derriere la fenetre (dehors)
     * @param exterieur  direction de l'interieur vers l'exterieur (normale du mur)
     * @param centre     centre du volume des planches
     * @param dehors     ou se tient un zombie pour arracher les planches (pieds, dehors, colle a la fenetre, centre sur
     *                   l'ouverture) : champ {@code exterieur} de la carte, sinon calcule
     * @param dedans     ou un zombie retombe apres avoir enjambe (pieds, interieur ; hauteur reajustee au sol par le jeu) :
     *                   champ {@code interieur} de la carte, sinon calcule
     */
    public record Fenetre(int index, String zone, AABB planches, BoundingBox boite, Pose apparition, Direction exterieur, Vec3 centre,
                          Vec3 dehors, Vec3 dedans) {
        /** Epaisseur du volume le long de la normale. */
        public double largeurNormale() {
            return exterieur.getAxis() == Direction.Axis.X ? planches.getXsize() : planches.getZsize();
        }

        /** Distance signee du point au plan median de la fenetre, positive dehors. */
        public double cote(double x, double z) {
            return (x - centre.x) * exterieur.getStepX() + (z - centre.z) * exterieur.getStepZ();
        }
    }

    /**
     * Porte, tas de debris ou grille payants qui ouvrent des zones (les blocs du volume disparaissent a l'achat).
     *
     * @param style    ancien champ ({@code porte|eboulis})
     * @param type     {@code debris|porte|grille} (rendu different, tous clairement interactifs)
     * @param pancarte ou afficher le prix (yaw = face de la pancarte), null = au centre du volume
     * @param nomFr    nom affiche (« Porte du labo »), vide si absent
     * @param pancarteVerso  pancarte de l'autre cote (facultative)
     * @param courantRequis  la porte ne s'achete qu'une fois le courant retabli (monte-charge...)
     * @param ouverteParCourant la porte s'ouvre seule, gratuitement, quand le courant revient (v3 : portes de la cour)
     */
    public record Porte(String id, String zoneOuverte, List<String> relie, int cout, String style, AABB volume, BoundingBox boite,
                        String type, @Nullable Pose pancarte, String nomFr, @Nullable Pose pancarteVerso, boolean courantRequis,
                        boolean ouverteParCourant) {
        /** Vrai pour un tas de debris (eboulis) plutot qu'une porte ou une grille. */
        public boolean eboulis() {
            return "debris".equals(type) || "eboulis".equals(style);
        }
    }

    /** Arme dessinee a la craie sur un mur ({@code pos} = case d'air devant le mur, {@code face} = vers la salle). */
    public record ArmeMur(int index, BlockPos pos, Direction face, String gunId, int cout, int coutMunitions, String zone) {
    }

    /**
     * Machine d'atout (alimentee par le courant).
     *
     * @param secret machine secrete (v3 : sous le tas de neige, revelee par la tete du robot) : pas posee au depart
     */
    public record Atout(int index, BlockPos pos, Direction face, TypeAtout type, int cout, String zone, boolean secret) {
    }

    /** Machine Pack-a-Punch (cout par defaut 5 000, alimentee par le courant). */
    public record PackAPunch(BlockPos pos, Direction face, int cout, String zone) {
    }

    /** Etabli de fabrication (recettes du jeu, payees en materiaux de recuperation). */
    public record Atelier(int index, BlockPos pos, Direction face, String zone) {
    }

    /** Arme unique de la carte telle que la carte la propose (la configuration du jeu fait foi). */
    public record ArmeCarte(String gunId, String nomFr) {
    }

    /** Etape de l'easter egg ({@code type} = radio | interrupteur | objet), a trouver dans l'ordre. */
    public record EtapeEasterEgg(int index, BlockPos pos, String type, String zone) {
    }

    /** Easter egg de la carte : etapes dans l'ordre et recompense (texte ou identifiant). */
    public record EasterEgg(List<EtapeEasterEgg> etapes, String recompense) {
    }

    /** Texte d'un objectif de la carte (la logique d'enchainement est dans le jeu). */
    public record Objectif(String id, String texteFr, String texteEn) {
    }

    private final String carte;
    private final BlockPos origine;
    private final List<Pose> spawnJoueurs;
    @Nullable
    private final Pose spectateur;
    private final String zoneDepart;
    private final Map<String, Zone> zones;
    private final List<Fenetre> fenetres;
    private final List<Porte> portes;
    private final List<ArmeMur> armes;
    private final List<Pose> boites;
    /** Zone de chaque emplacement de boite ({@code boite_details[].zone}), "" si inconnue. */
    private final List<String> zonesBoites;
    private final List<Atout> atouts;
    @Nullable
    private final Pose courant;
    @Nullable
    private final PackAPunch packAPunch;
    private final List<Atelier> ateliers;
    @Nullable
    private final ArmeCarte armeCarte;
    @Nullable
    private final EasterEgg easterEgg;
    private final List<Objectif> objectifs;
    private final List<String> introFr;
    private final List<String> introEn;
    /** Champs v3 de The Giant (null sur une ancienne carte). */
    @Nullable
    private final PlanGiant giant;
    /** {@code version_format} de la carte (1 si absent). */
    private final int versionFormat;

    private PlanZombies(String carte, BlockPos origine, List<Pose> spawnJoueurs, @Nullable Pose spectateur, String zoneDepart,
                        Map<String, Zone> zones, List<Fenetre> fenetres, List<Porte> portes, List<ArmeMur> armes,
                        List<Pose> boites, List<String> zonesBoites, List<Atout> atouts, @Nullable Pose courant, @Nullable PackAPunch packAPunch,
                        List<Atelier> ateliers, @Nullable ArmeCarte armeCarte, @Nullable EasterEgg easterEgg, List<Objectif> objectifs,
                        List<String> introFr, List<String> introEn, @Nullable PlanGiant giant, int versionFormat) {
        this.carte = carte;
        this.origine = origine;
        this.spawnJoueurs = List.copyOf(spawnJoueurs);
        this.spectateur = spectateur;
        this.zoneDepart = zoneDepart;
        this.zones = Collections.unmodifiableMap(zones);
        this.fenetres = List.copyOf(fenetres);
        this.portes = List.copyOf(portes);
        this.armes = List.copyOf(armes);
        this.boites = List.copyOf(boites);
        this.zonesBoites = List.copyOf(zonesBoites);
        this.atouts = List.copyOf(atouts);
        this.courant = courant;
        this.packAPunch = packAPunch;
        this.ateliers = List.copyOf(ateliers);
        this.armeCarte = armeCarte;
        this.easterEgg = easterEgg;
        this.objectifs = List.copyOf(objectifs);
        this.introFr = List.copyOf(introFr);
        this.introEn = List.copyOf(introEn);
        this.giant = giant;
        this.versionFormat = versionFormat;
    }

    // ------------------------------------------------------------------ lecture
    /**
     * Lit les metadonnees de la carte pour une instance posee a {@code origine}. Les entrees invalides sont ignorees
     * avec un avertissement (jamais d'exception).
     */
    public static PlanZombies lire(Carte carte, BlockPos origine) {
        JsonObject m = carte.meta();
        List<Pose> spawns = new ArrayList<>();
        JsonElement sj = m.has("spawn_joueurs") ? m.get("spawn_joueurs") : null;
        if (sj != null && sj.isJsonArray()) for (JsonElement e : sj.getAsJsonArray()) ajouter(spawns, pose(e), origine);
        if (spawns.isEmpty()) for (Pose p : carte.pointsRelatifs("spawn_joueurs")) spawns.add(p.decaler(origine));
        List<Pose> spec = carte.pointsRelatifs("spectateur");
        Pose spectateur = spec.isEmpty() ? null : spec.get(0).decaler(origine);
        String depart = texte(m, "zone_depart", "depart");

        Map<String, Zone> zones = new LinkedHashMap<>();
        List<Fenetre> fenetres = new ArrayList<>();
        for (JsonElement ze : tableau(m, "zones")) {
            if (!ze.isJsonObject()) continue;
            JsonObject z = ze.getAsJsonObject();
            String id = texte(z, "id", "zone" + zones.size());
            List<Pose> salle = new ArrayList<>();
            List<String> genres = new ArrayList<>();
            for (JsonElement p : tableau(z, "apparitions_salle")) {
                Pose ps = pose(p);
                if (ps == null) continue;
                salle.add(ps.decaler(origine));
                genres.add(genre(z, p));
            }
            // anciens points de sortie de terre : gardes s'ils ne doublonnent pas un point de salle (cartes v2 : les deux listes)
            List<Pose> app = new ArrayList<>();
            for (JsonElement p : tableau(z, "points_apparition")) {
                Pose ps = pose(p);
                if (ps == null) continue;
                Pose a = ps.decaler(origine);
                boolean doublon = false;
                for (Pose q : salle) if (q.distanceCarre(a.x(), a.y(), a.z()) < 1.0) doublon = true;
                if (!doublon) app.add(a);
            }
            List<Integer> fz = new ArrayList<>();
            for (JsonElement fe : tableau(z, "fenetres")) {
                if (!fe.isJsonObject()) continue;
                Fenetre f = fenetre(carte.id(), id, fenetres.size(), fe.getAsJsonObject(), origine);
                if (f == null) continue;
                fenetres.add(f);
                fz.add(f.index());
            }
            List<AABB> vols = new ArrayList<>();
            for (JsonElement v : tableau(z, "volumes")) {
                BoundingBox bb = boite(v, origine);
                if (bb != null) vols.add(aabb(bb));
            }
            zones.put(id, new Zone(id, texte(z, "nom", id), List.copyOf(app), List.copyOf(fz), List.copyOf(vols), List.copyOf(salle),
                    List.copyOf(genres), texte(z, "nom_en", "")));
        }

        List<Porte> portes = new ArrayList<>();
        for (JsonElement pe : tableau(m, "portes")) {
            if (!pe.isJsonObject()) continue;
            JsonObject p = pe.getAsJsonObject();
            BoundingBox bb = boite(p.get("blocs"), origine);
            if (bb == null) continue;
            List<String> relie = new ArrayList<>();
            for (JsonElement r : tableau(p, "relie")) {
                try {
                    relie.add(r.getAsString());
                } catch (RuntimeException ignored) {
                }
            }
            String ouverte = texte(p, "zone_ouverte", "");
            if (!ouverte.isEmpty() && !relie.contains(ouverte)) relie.add(ouverte);
            String style = texte(p, "style", "porte");
            String type = texte(p, "type", "eboulis".equals(style) ? "debris" : "porte").toLowerCase(Locale.ROOT);
            if (!type.equals("debris") && !type.equals("grille")) type = "porte";
            Pose pancarte = pose(p.get("pancarte"));
            if (pancarte != null) pancarte = pancarte.decaler(origine);
            Pose verso = pose(p.get("pancarte_verso"));
            if (verso != null) verso = verso.decaler(origine);
            boolean courantRequis = false, parCourant = false;
            try {
                courantRequis = p.has("courant_requis") && p.get("courant_requis").getAsBoolean();
                parCourant = p.has("ouverte_par_courant") && p.get("ouverte_par_courant").getAsBoolean();
            } catch (RuntimeException ignored) {
            }
            portes.add(new Porte(texte(p, "id", "P" + portes.size()), ouverte, List.copyOf(relie), entier(p, "cout", 1000), style, aabb(bb), bb,
                    type, pancarte, texte(p, "nom_fr", ""), verso, courantRequis || parCourant, parCourant));
        }

        List<ArmeMur> armes = new ArrayList<>();
        for (JsonElement ae : tableau(m, "armes_mur")) {
            if (!ae.isJsonObject()) continue;
            JsonObject a = ae.getAsJsonObject();
            BlockPos pos = bloc(a.get("pos"), origine);
            String gun = texte(a, "gunId", texte(a, "gun", ""));
            if (pos == null || gun.isEmpty()) continue;
            int cout = entier(a, "cout", 1000);
            armes.add(new ArmeMur(armes.size(), pos, face(texte(a, "face", "north"), Direction.NORTH), gun, cout,
                    entier(a, "cout_munitions", Math.max(1, cout / 2)), texte(a, "zone", "")));
        }

        List<Pose> boites = new ArrayList<>();
        List<String> zonesBoites = new ArrayList<>();
        // zones des emplacements : boite_details, retrouve par la position (bloc) de chaque entree de boite
        Map<Long, String> zoneParBloc = new HashMap<>();
        for (JsonElement de : tableau(m, "boite_details")) {
            if (!de.isJsonObject()) continue;
            Pose dp = pose(de.getAsJsonObject().get("pos"));
            if (dp != null) zoneParBloc.put(dp.bloc().asLong(), texte(de.getAsJsonObject(), "zone", ""));
        }
        for (JsonElement b : tableau(m, "boite")) {
            Pose bp = pose(b);
            if (bp == null) continue;
            ajouter(boites, bp, origine);
            zonesBoites.add(zoneParBloc.getOrDefault(bp.bloc().asLong(), ""));
        }

        List<Atout> atouts = new ArrayList<>();
        for (JsonElement ae : tableau(m, "atouts")) {
            if (!ae.isJsonObject()) continue;
            JsonObject a = ae.getAsJsonObject();
            BlockPos pos = bloc(a.get("pos"), origine);
            TypeAtout t = TypeAtout.parId(texte(a, "type", ""));
            if (pos == null || t == null) {
                HeraliszMatchs.LOG.warn("[zombies] {} : atout invalide {}", carte.id(), a);
                continue;
            }
            boolean secret = false;
            try {
                secret = a.has("secret") && a.get("secret").getAsBoolean();
            } catch (RuntimeException ignored) {
            }
            atouts.add(new Atout(atouts.size(), pos, face(texte(a, "face", "north"), Direction.NORTH), t, entier(a, "cout", t.prixCod), texte(a, "zone", ""),
                    secret));
        }

        Pose courant = m.has("courant") ? pose(m.get("courant")) : null;
        if (courant != null) courant = courant.decaler(origine);

        PackAPunch pap = null;
        JsonElement pe = m.get("pack_a_punch");
        if (pe != null && pe.isJsonObject()) {
            JsonObject p = pe.getAsJsonObject();
            BlockPos pos = bloc(p.get("pos"), origine);
            if (pos != null) {
                pap = new PackAPunch(pos, face(texte(p, "face", "north"), Direction.NORTH), entier(p, "cout", 5000), texte(p, "zone", ""));
            } else {
                HeraliszMatchs.LOG.warn("[zombies] {} : pack_a_punch sans position", carte.id());
            }
        }

        List<Atelier> ateliers = new ArrayList<>();
        for (JsonElement ae : tableau(m, "ateliers")) {
            if (!ae.isJsonObject()) continue;
            JsonObject a = ae.getAsJsonObject();
            BlockPos pos = bloc(a.get("pos"), origine);
            if (pos == null) continue;
            ateliers.add(new Atelier(ateliers.size(), pos, face(texte(a, "face", "north"), Direction.NORTH), texte(a, "zone", "")));
        }

        ArmeCarte armeCarte = null;
        JsonElement ac = m.get("arme_carte");
        if (ac != null && ac.isJsonObject()) {
            String g = texte(ac.getAsJsonObject(), "gunId", texte(ac.getAsJsonObject(), "gun", ""));
            if (!g.isEmpty()) armeCarte = new ArmeCarte(g, texte(ac.getAsJsonObject(), "nom_fr", ""));
        } else if (ac != null && ac.isJsonPrimitive()) {
            armeCarte = new ArmeCarte(ac.getAsString(), "");
        }

        EasterEgg ee = null;
        JsonElement ej = m.get("easter_egg");
        if (ej != null && ej.isJsonObject()) {
            List<EtapeEasterEgg> etapes = new ArrayList<>();
            for (JsonElement se : tableau(ej.getAsJsonObject(), "etapes")) {
                if (!se.isJsonObject()) continue;
                JsonObject s = se.getAsJsonObject();
                BlockPos pos = bloc(s.get("pos"), origine);
                if (pos == null) continue;
                etapes.add(new EtapeEasterEgg(etapes.size(), pos, texte(s, "type", "objet").toLowerCase(Locale.ROOT), texte(s, "zone", "")));
            }
            if (!etapes.isEmpty()) ee = new EasterEgg(List.copyOf(etapes), texte(ej.getAsJsonObject(), "recompense", ""));
        }

        List<Objectif> objectifs = new ArrayList<>();
        for (JsonElement oe : tableau(m, "objectifs")) {
            if (!oe.isJsonObject()) continue;
            JsonObject o = oe.getAsJsonObject();
            String id = texte(o, "id", "");
            if (id.isEmpty()) continue;
            objectifs.add(new Objectif(id, texte(o, "texte_fr", ""), texte(o, "texte_en", "")));
        }

        return new PlanZombies(carte.id(), origine, spawns, spectateur, depart, zones, fenetres, portes, armes, boites, zonesBoites, atouts, courant,
                pap, ateliers, armeCarte, ee, objectifs, lignes(m.get("intro_fr")), lignes(m.get("intro_en")), PlanGiant.lire(m, origine, carte.id()),
                entier(m, "version_format", 1));
    }

    /** Lit une fenetre ; rend null (avertissement) si son volume ou son point d'apparition manque. */
    @Nullable
    private static Fenetre fenetre(String carte, String zone, int idx, JsonObject f, BlockPos origine) {
        BoundingBox bb = boite(f.get("planches"), origine);
        Pose ap = pose(f.get("apparition"));
        Vec3 ext = point(f.get("exterieur"), origine);
        Vec3 inte = point(f.get("interieur"), origine);
        if (bb == null || (ap == null && ext == null)) {
            HeraliszMatchs.LOG.warn("[zombies] {} : fenetre invalide dans la zone {}", carte, zone);
            return null;
        }
        AABB vol = aabb(bb);
        Vec3 c = vol.getCenter();
        Vec3 ref = ext != null ? ext : ap.decaler(origine).position();
        Direction dir = exterieur(bb, c, ref);
        Direction faceCarte = face(texte(f, "face", ""), null);
        // la face de la carte ne sert qu'a lever une ambiguite (fenetre carree sans point de reference decale)
        if (faceCarte != null && Math.abs(ref.x - c.x) < 0.01 && Math.abs(ref.z - c.z) < 0.01) dir = faceCarte;
        if (ap == null) ap = new Pose(ref.x + dir.getStepX() * 4, ref.y, ref.z + dir.getStepZ() * 4, dir.toYRot());
        else ap = ap.decaler(origine);
        double demi = (dir.getAxis() == Direction.Axis.X ? vol.getXsize() : vol.getZsize()) / 2.0;
        // dehors : centre sur l'ouverture le long du mur, a la distance de la carte le long de la normale (au moins colle :
        // demi-largeur du zombie 0,3 + 4 cm), jamais a plus de 2 blocs de la face
        double d;
        double y;
        if (ext != null) {
            d = (ext.x - c.x) * dir.getStepX() + (ext.z - c.z) * dir.getStepZ();
            d = Math.max(demi + 0.34, Math.min(d, demi + 2.0));
            y = ext.y;
        } else {
            d = demi + 0.36;
            y = ap.y();
        }
        Vec3 dehors = new Vec3(c.x + dir.getStepX() * d, y, c.z + dir.getStepZ() * d);
        Vec3 dedans;
        if (inte != null) {
            dedans = inte;
        } else {
            double di = demi + 1.0;
            dedans = new Vec3(c.x - dir.getStepX() * di, vol.minY, c.z - dir.getStepZ() * di);
        }
        return new Fenetre(idx, zone, vol, bb, ap, dir, c, dehors, dedans);
    }

    // ------------------------------------------------------------------ acces
    public String carte() {
        return carte;
    }

    public BlockPos origine() {
        return origine;
    }

    public List<Pose> spawnJoueurs() {
        return spawnJoueurs;
    }

    /** Point des spectateurs, ou null (alors premier spawn). */
    @Nullable
    public Pose spectateur() {
        return spectateur;
    }

    public String zoneDepart() {
        return zoneDepart;
    }

    /** Zones par id (ordre du JSON). */
    public Map<String, Zone> zones() {
        return zones;
    }

    @Nullable
    public Zone zone(String id) {
        return zones.get(id);
    }

    /** Zone contenant le point, ou null. */
    @Nullable
    public Zone zoneA(double x, double y, double z) {
        for (Zone zo : zones.values()) if (zo.contient(x, y, z)) return zo;
        return null;
    }

    public List<Fenetre> fenetres() {
        return fenetres;
    }

    public List<Porte> portes() {
        return portes;
    }

    public List<ArmeMur> armes() {
        return armes;
    }

    /** Emplacements possibles de la boite mystere. */
    public List<Pose> boites() {
        return boites;
    }

    /** Zone de l'emplacement de boite {@code i} ({@code boite_details}), "" si inconnue (toujours ouverte). */
    public String zoneBoite(int i) {
        return i >= 0 && i < zonesBoites.size() ? zonesBoites.get(i) : "";
    }

    public List<Atout> atouts() {
        return atouts;
    }

    /** Levier du courant, ou null (atouts alors toujours alimentes). */
    @Nullable
    public Pose courant() {
        return courant;
    }

    /** Machine Pack-a-Punch, ou null si la carte n'en a pas. */
    @Nullable
    public PackAPunch packAPunch() {
        return packAPunch;
    }

    /** Etablis de fabrication (liste vide si aucun). */
    public List<Atelier> ateliers() {
        return ateliers;
    }

    /** Arme unique proposee par la carte, ou null (la configuration du jeu fait foi). */
    @Nullable
    public ArmeCarte armeCarte() {
        return armeCarte;
    }

    /** Easter egg de la carte, ou null. */
    @Nullable
    public EasterEgg easterEgg() {
        return easterEgg;
    }

    /** Textes d'objectifs fournis par la carte (vide = textes par defaut du jeu). */
    public List<Objectif> objectifs() {
        return objectifs;
    }

    /** Texte d'objectif de la carte pour cet id, ou null. */
    @Nullable
    public Objectif objectif(String id) {
        for (Objectif o : objectifs) if (o.id().equals(id)) return o;
        return null;
    }

    /** Champs v3 de The Giant (machines, quete, pieges...), ou null sur une ancienne carte. */
    @Nullable
    public PlanGiant giant() {
        return giant;
    }

    /** {@code version_format} de la carte (1 = ancien format, 3 = The Giant). */
    public int versionFormat() {
        return versionFormat;
    }

    /** Lignes de lore d'intro en francais (vide = texte par defaut du jeu). */
    public List<String> introFr() {
        return introFr;
    }

    /** Lignes de lore d'intro en anglais (vide = francais ou defaut). */
    public List<String> introEn() {
        return introEn;
    }

    // ------------------------------------------------------------------ outils
    private static void ajouter(List<Pose> l, @Nullable Pose p, BlockPos origine) {
        if (p != null) l.add(p.decaler(origine));
    }

    private static Iterable<JsonElement> tableau(JsonObject o, String cle) {
        JsonElement e = o.get(cle);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static String texte(JsonObject o, String cle, String defaut) {
        JsonElement e = o.get(cle);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsString() : defaut;
        } catch (RuntimeException ex) {
            return defaut;
        }
    }

    private static int entier(JsonObject o, String cle, int defaut) {
        JsonElement e = o.get(cle);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : defaut;
        } catch (RuntimeException ex) {
            return defaut;
        }
    }

    /** Genre d'un point de salle : {@code apparitions_salle_details[{pos, genre}]} de la zone, sinon {@code sol}. */
    private static String genre(JsonObject zone, JsonElement point) {
        // 0.2.3 : point ecrit comme objet {pos, genre} : son genre d'abord
        if (point != null && point.isJsonObject() && point.getAsJsonObject().has("genre")) {
            return texte(point.getAsJsonObject(), "genre", "sol").toLowerCase(Locale.ROOT);
        }
        JsonElement cle = point != null && point.isJsonObject() ? point.getAsJsonObject().get("pos") : point;
        for (JsonElement d : tableau(zone, "apparitions_salle_details")) {
            if (!d.isJsonObject()) continue;
            JsonElement pos = d.getAsJsonObject().get("pos");
            if (pos != null && (pos.equals(cle) || memeCase(pos, cle))) return texte(d.getAsJsonObject(), "genre", "sol").toLowerCase(Locale.ROOT);
        }
        return "sol";
    }

    /** Vrai si deux positions [x, y, z] designent la meme case (50 et 50.5 : meme case). */
    private static boolean memeCase(JsonElement a, @Nullable JsonElement b) {
        try {
            if (b == null || !a.isJsonArray() || !b.isJsonArray() || a.getAsJsonArray().size() < 3 || b.getAsJsonArray().size() < 3) return false;
            for (int i = 0; i < 3; i++) {
                if (Math.floor(a.getAsJsonArray().get(i).getAsDouble()) != Math.floor(b.getAsJsonArray().get(i).getAsDouble())) return false;
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Lignes d'un texte : chaine avec retours a la ligne, ou tableau de chaines. */
    private static List<String> lignes(@Nullable JsonElement e) {
        List<String> l = new ArrayList<>();
        if (e == null) return l;
        try {
            if (e.isJsonPrimitive()) {
                for (String s : e.getAsString().split("\n")) if (!s.isBlank()) l.add(s.strip());
            } else if (e.isJsonArray()) {
                for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive() && !x.getAsString().isBlank()) l.add(x.getAsString().strip());
            }
        } catch (RuntimeException ignored) {
        }
        return l;
    }

    @Nullable
    private static BlockPos bloc(@Nullable JsonElement e, BlockPos origine) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 3) return null;
        JsonArray a = e.getAsJsonArray();
        try {
            return origine.offset((int) Math.floor(a.get(0).getAsDouble()), (int) Math.floor(a.get(1).getAsDouble()),
                    (int) Math.floor(a.get(2).getAsDouble()));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Point absolu {@code [x, y, z]} (x et z entiers = centre du bloc), ou null. */
    @Nullable
    private static Vec3 point(@Nullable JsonElement e, BlockPos origine) {
        Pose p = pose(e);
        return p == null ? null : new Vec3(p.x() + origine.getX(), p.y() + origine.getY(), p.z() + origine.getZ());
    }

    /**
     * Pose relative tolerante : {@code [x, y, z]}, {@code [x, y, z, yaw]}, {@code [x, y, z, "face"]} ou
     * {@code {pos: [...], face|yaw}} ; x et z entiers = centre du bloc. Null si illisible.
     */
    @Nullable
    public static Pose pose(@Nullable JsonElement e) {
        if (e == null) return null;
        try {
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                Pose p = pose(o.get("pos"));
                if (p == null) return null;
                float yaw = p.yaw();
                if (o.has("yaw")) yaw = o.get("yaw").getAsFloat();
                else if (o.has("face")) {
                    Direction d = face(o.get("face").getAsString(), null);
                    if (d != null) yaw = d.toYRot();
                }
                return new Pose(p.x(), p.y(), p.z(), yaw);
            }
            if (!e.isJsonArray() || e.getAsJsonArray().size() < 3) return null;
            JsonArray a = e.getAsJsonArray();
            double x = a.get(0).getAsDouble(), y = a.get(1).getAsDouble(), z = a.get(2).getAsDouble();
            float yaw = 0f;
            if (a.size() > 3 && a.get(3).isJsonPrimitive()) {
                if (a.get(3).getAsJsonPrimitive().isNumber()) yaw = a.get(3).getAsFloat();
                else {
                    Direction d = face(a.get(3).getAsString(), null);
                    if (d != null) yaw = d.toYRot();
                }
            }
            if (x == Math.floor(x)) x += 0.5;
            if (z == Math.floor(z)) z += 0.5;
            return new Pose(x, y, z, yaw);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Nullable
    private static BoundingBox boite(@Nullable JsonElement e, BlockPos origine) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 6) return null;
        JsonArray a = e.getAsJsonArray();
        try {
            int x0 = a.get(0).getAsInt(), y0 = a.get(1).getAsInt(), z0 = a.get(2).getAsInt();
            int x1 = a.get(3).getAsInt(), y1 = a.get(4).getAsInt(), z1 = a.get(5).getAsInt();
            return new BoundingBox(Math.min(x0, x1) + origine.getX(), Math.min(y0, y1) + origine.getY(), Math.min(z0, z1) + origine.getZ(),
                    Math.max(x0, x1) + origine.getX(), Math.max(y0, y1) + origine.getY(), Math.max(z0, z1) + origine.getZ());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Volume flottant d'une boite de blocs inclus (max exclusif). */
    public static AABB aabb(BoundingBox b) {
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
    }

    /** Direction horizontale depuis un nom (nord|sud|est|ouest ou north|south|east|west), {@code defaut} sinon. */
    @Nullable
    public static Direction face(@Nullable String s, @Nullable Direction defaut) {
        if (s == null || s.isEmpty()) return defaut;
        String t = s.toLowerCase(Locale.ROOT);
        Direction d = switch (t) {
            case "nord", "n" -> Direction.NORTH;
            case "sud", "s" -> Direction.SOUTH;
            case "est", "e" -> Direction.EAST;
            case "ouest", "o", "w" -> Direction.WEST;
            default -> Direction.byName(t);
        };
        return d == null || d.getAxis() == Direction.Axis.Y ? defaut : d;
    }

    /** Normale du mur orientee vers l'exterieur (cote du point de reference : point exterieur ou apparition). */
    private static Direction exterieur(BoundingBox bb, Vec3 c, Vec3 ref) {
        int sx = bb.getXSpan(), sz = bb.getZSpan();
        double dx = ref.x - c.x, dz = ref.z - c.z;
        boolean axeX;
        if (sx < sz) axeX = true;
        else if (sz < sx) axeX = false;
        else axeX = Math.abs(dx) >= Math.abs(dz);
        if (axeX) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
