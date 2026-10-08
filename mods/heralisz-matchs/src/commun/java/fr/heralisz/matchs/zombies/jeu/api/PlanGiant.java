package fr.heralisz.matchs.zombies.jeu.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Champs « The Giant » (format de carte v3, {@code work/zombies_v3/carte/README_FORMAT.md}) en coordonnees ABSOLUES :
 * armoire du courant et cachettes des fusibles, teleporteurs A/B/C (pads, pupitres, cuves a ames) et mainframe (pad
 * d'arrivee, pupitre, tour, plateforme validee facultative), rideau du Pack-a-Punch, passerelle, sauts, pieges
 * electriques, Bowie Knife, atouts melanges et machine secrete, quete (Fly Trap, bocaux, carte d'acces, porte de
 * confinement, arene du boss). Lecture TOLERANTE comme {@link PlanZombies} : tout champ absent prend une valeur sensee,
 * une entree invalide est ignoree avec un avertissement. {@link #lire} rend null si la carte n'a aucun de ces champs
 * (ancienne carte {@code zombies_labo_n07}).
 * <p>Champs facultatifs que le generateur peut ajouter (demandes du D-051) : {@code teleporteurs.<X>.pupitre}
 * ({@code [x,y,z]} ou {@code {pos, face}}), {@code teleporteurs.<X>.face}, {@code teleporteurs.mainframe.tour}
 * ({@code {pos, face}}, sinon le centre bas de {@code tour_emprise}), {@code teleporteurs.mainframe.plateforme_modele}
 * ({@code {pos, face}} : la plateforme validee est posee et la carte ne doit rien construire dans son volume).
 */
public final class PlanGiant {
    /** Position et orientation d'une machine ou d'un objet (face = vers la salle / le joueur). */
    public record Machine(BlockPos pos, Direction face) {
    }

    /** Cachette de fusible. */
    public record Cachette(int index, BlockPos pos, Direction face, String zone) {
    }

    /**
     * Teleporteur A, B ou C.
     *
     * @param pupitre pupitre du pad (null = place par le decor entre le pad et la cuve)
     * @param ames    ames requises en solo (le jeu ajoute 25 % par joueur en plus)
     */
    public record Teleporteur(int index, char lettre, BlockPos pad, AABB emprise, String zone, Direction face, @Nullable Machine pupitre,
                              @Nullable Machine cuve, int ames, int cout, int liaisonTicks) {
    }

    /** Mainframe : pad d'arrivee, pupitre A/B/C, tour, plateforme validee (facultative), point du bonus gratuit. */
    public record Mainframe(BlockPos pad, AABB emprise, @Nullable Machine console, @Nullable Machine tour, @Nullable Machine plateforme,
                            Vec3 bonusGratuit) {
    }

    /** Bloc a poser (passerelle, tas de neige). */
    public record BlocCarte(BlockPos pos, BlockState etat) {
    }

    /** Piege electrique : volume ou les zombies meurent, leviers, cout, duree et recharge (ticks). */
    public record Piege(int index, String id, String zone, AABB volume, List<Machine> panneaux, int cout, int dureeTicks, int rechargeTicks) {
    }

    /**
     * Decor cuit de la carte qui change d'etat (D-053) : bloc {@code zb_deco_*}, propriete booleenne, declencheur
     * ({@code courant}, {@code piege:<id>}, {@code fly_trap}) et emprise du prop (cases a mettre a jour).
     */
    public record EtatDecor(String bloc, String propriete, String declencheur, BoundingBox emprise) {
    }

    /**
     * Objet de quete (type : singe, nounours, bocal, carte...). {@code ancre} (0.2.3, absolue, en blocs) : point ou montrer
     * l'objet quand {@code pos} est une case d'un decor (sole d'un fourneau, surface d'une cuve), null sinon.
     */
    public record Objet(int index, String type, BlockPos pos, Direction face, String zone, @Nullable Vec3 ancre) {
        public Objet(int index, String type, BlockPos pos, Direction face, String zone) {
            this(index, type, pos, face, zone, null);
        }

        /** Meme objet sous un autre index. */
        public Objet index(int i) {
            return new Objet(i, type, pos, face, zone, ancre);
        }
    }

    /**
     * Etape cachee (0.2.3, contrat §5) : relais 935 a abattre ({@code tir}, hors de la zone de jeu, vu d'une fenetre ou d'un
     * toit) ou point ou lancer une grenade ({@code grenade}, bonus unique). {@code recompense} : case ou poser le bonus
     * d'une cible « grenade » (null = pres du lanceur).
     */
    public record CibleCachee(int index, String id, BlockPos pos, String type, String zone, @Nullable BlockPos recompense) {
        public boolean tir() {
            return !"grenade".equals(type);
        }
    }

    // ------------------------------------------------------------------ donnees
    @Nullable
    public final Machine courant;
    public final int fusiblesRequis;
    public final int fusiblesATirer;
    public final List<Cachette> cachettes;
    public final List<Teleporteur> teleporteurs;
    @Nullable
    public final Mainframe mainframe;
    /** Ouverture du rideau du Pack-a-Punch (blocs a vider), ou null. */
    @Nullable
    public final BoundingBox rideau;
    public final Direction faceRideau;
    /** Zone ouverte par le rideau ({@code porte_pack_a_punch.zone_ouverte}). */
    public final String zoneRideau;
    public final List<BlocCarte> passerelleRelevee, passerelleAbaissee;
    @Nullable
    public final BoundingBox passerelleVolume;
    /** Zone d'arrivee -> zones d'ou l'on y saute (sauts[] et zones[].acces_par_saut_depuis). */
    public final Map<String, List<String>> sautsVers;
    public final List<Piege> pieges;
    @Nullable
    public final Machine bowie;
    public final int coutBowie;
    public final boolean atoutsMelanges;
    // quete
    @Nullable
    public final Objet panneauFlyTrap;
    public final List<Objet> objetsFlyTrap;
    @Nullable
    public final Objet recompenseFlyTrap;
    public final String armeRecompense;
    /**
     * Candidats de chaque objet du Fly Trap (0.2.3, meme index que {@link #objetsFlyTrap}) : un est tire au hasard a chaque
     * partie ; une liste d'un seul element = position fixe (ancien format).
     */
    public final List<List<Objet>> candidatsFlyTrap;
    /** Bocaux a moelle : tous les candidats (0.2.3 : {@code {a_tirer, candidats}}) ; ancien format = liste fixe. */
    public final List<Objet> bocaux;
    /** Bocaux tires a chaque partie parmi {@link #bocaux} (ancien format : tous). */
    public final int bocauxATirer;
    /** Etapes cachees (relais 935 a abattre avant la carte d'acces, grenade bonus), 0.2.3. */
    public final List<CibleCachee> ciblesCachees;
    @Nullable
    public final Objet carteAcces;
    @Nullable
    public final Machine porteConfinement;
    @Nullable
    public final AABB areneBoss;
    /**
     * Acte IV (D-057) : salle du boss N-07 posee avec l'instance (structures annexes) : variante, origine absolue, rotation ;
     * null si la carte n'en declare pas ({@code quete.salle_n07}).
     */
    @Nullable
    public final ZombiesApi.PlanSalle salleBoss;
    public final List<BlocCarte> tasDeNeige;
    @Nullable
    public final BoundingBox tasDeNeigeVolume;
    @Nullable
    public final Vec3 teteRobot;
    public final String atoutSecret;
    public final List<String> alternativesSecret;
    /** Decors cuits a etats (generateurs, pylones, boitier du Fly Trap, lampe d'etabli). */
    public final List<EtatDecor> etatsDecor;

    private PlanGiant(Builder b) {
        courant = b.courant;
        fusiblesRequis = b.fusiblesRequis;
        fusiblesATirer = b.fusiblesATirer;
        cachettes = List.copyOf(b.cachettes);
        teleporteurs = List.copyOf(b.teleporteurs);
        mainframe = b.mainframe;
        rideau = b.rideau;
        faceRideau = b.faceRideau;
        zoneRideau = b.zoneRideau;
        passerelleRelevee = List.copyOf(b.passerelleRelevee);
        passerelleAbaissee = List.copyOf(b.passerelleAbaissee);
        passerelleVolume = b.passerelleVolume;
        sautsVers = Map.copyOf(b.sautsVers);
        pieges = List.copyOf(b.pieges);
        bowie = b.bowie;
        coutBowie = b.coutBowie;
        atoutsMelanges = b.atoutsMelanges;
        panneauFlyTrap = b.panneauFlyTrap;
        objetsFlyTrap = List.copyOf(b.objetsFlyTrap);
        recompenseFlyTrap = b.recompenseFlyTrap;
        armeRecompense = b.armeRecompense;
        List<List<Objet>> cf = new ArrayList<>();
        for (List<Objet> l : b.candidatsFlyTrap) cf.add(List.copyOf(l));
        candidatsFlyTrap = List.copyOf(cf);
        bocaux = List.copyOf(b.bocaux);
        bocauxATirer = b.bocauxATirer < 0 ? b.bocaux.size() : Math.min(b.bocauxATirer, b.bocaux.size());
        ciblesCachees = List.copyOf(b.ciblesCachees);
        carteAcces = b.carteAcces;
        porteConfinement = b.porteConfinement;
        areneBoss = b.areneBoss;
        salleBoss = b.salleBoss;
        tasDeNeige = List.copyOf(b.tasDeNeige);
        tasDeNeigeVolume = b.tasDeNeigeVolume;
        teteRobot = b.teteRobot;
        atoutSecret = b.atoutSecret;
        alternativesSecret = List.copyOf(b.alternativesSecret);
        etatsDecor = List.copyOf(b.etatsDecor);
    }

    /** Teleporteur de cette lettre, ou null. */
    @Nullable
    public Teleporteur teleporteur(char lettre) {
        for (Teleporteur t : teleporteurs) if (t.lettre() == lettre) return t;
        return null;
    }

    /** Volumes modifies pendant la partie (passerelle, rideau du Pack-a-Punch, tas de neige), a remettre comme la carte. */
    public List<BoundingBox> volumesARestaurer() {
        List<BoundingBox> l = new ArrayList<>(3);
        if (passerelleVolume != null) l.add(passerelleVolume);
        if (rideau != null) l.add(rideau);
        if (tasDeNeigeVolume != null) l.add(tasDeNeigeVolume);
        return l;
    }

    private static final class Builder {
        Machine courant;
        int fusiblesRequis = 3, fusiblesATirer = 3;
        final List<Cachette> cachettes = new ArrayList<>();
        final List<Teleporteur> teleporteurs = new ArrayList<>();
        Mainframe mainframe;
        BoundingBox rideau;
        Direction faceRideau = Direction.SOUTH;
        String zoneRideau = "pack_a_punch";
        final List<BlocCarte> passerelleRelevee = new ArrayList<>(), passerelleAbaissee = new ArrayList<>();
        BoundingBox passerelleVolume;
        final Map<String, List<String>> sautsVers = new HashMap<>();
        final List<Piege> pieges = new ArrayList<>();
        Machine bowie;
        int coutBowie = 3000;
        boolean atoutsMelanges;
        Objet panneauFlyTrap, recompenseFlyTrap, carteAcces;
        final List<Objet> objetsFlyTrap = new ArrayList<>(), bocaux = new ArrayList<>();
        final List<List<Objet>> candidatsFlyTrap = new ArrayList<>();
        int bocauxATirer = -1;
        final List<CibleCachee> ciblesCachees = new ArrayList<>();
        String armeRecompense = "annihilator";
        Machine porteConfinement;
        AABB areneBoss;
        ZombiesApi.PlanSalle salleBoss;
        final List<BlocCarte> tasDeNeige = new ArrayList<>();
        BoundingBox tasDeNeigeVolume;
        Vec3 teteRobot;
        String atoutSecret = "";
        final List<String> alternativesSecret = new ArrayList<>();
        final List<EtatDecor> etatsDecor = new ArrayList<>();
        boolean utile;
    }

    // ------------------------------------------------------------------ lecture
    /** Lit les champs v3 ; null si la carte n'en a aucun (ancienne carte). Jamais d'exception. */
    @Nullable
    public static PlanGiant lire(JsonObject m, BlockPos o, String carte) {
        long t0 = System.nanoTime();
        Builder b = new Builder();
        try {
            JsonObject cd = objet(m, "courant_details");
            if (cd != null) {
                b.courant = machine(cd, o, Direction.NORTH);
                b.fusiblesRequis = Math.max(0, Math.min(3, entier(cd, "fusibles_requis", 3)));
                b.utile = true;
            }
            JsonObject q = objet(m, "quete");
            if (q != null) {
                b.utile = true;
                JsonObject fu = objet(q, "fusibles");
                if (fu != null) {
                    b.fusiblesATirer = Math.max(0, entier(fu, "a_tirer", 3));
                    for (JsonElement e : tableau(fu, "cachettes")) {
                        if (!e.isJsonObject()) continue;
                        Machine mc = machine(e.getAsJsonObject(), o, Direction.NORTH);
                        if (mc != null) b.cachettes.add(new Cachette(b.cachettes.size(), mc.pos(), mc.face(), texte(e.getAsJsonObject(), "zone", "")));
                    }
                }
                JsonObject ft = objet(q, "fly_trap");
                if (ft != null) {
                    b.panneauFlyTrap = objetQuete(ft.get("panneau"), o, "panneau", 0);
                    // 0.2.3 : chaque objet peut avoir des « candidats » (un tire au hasard par partie) ; sans pos, le premier
                    // candidat sert de position par defaut
                    JsonElement objs = q.has("objets_fly_trap") ? q.get("objets_fly_trap") : ft.get("objets");
                    for (JsonElement e : objs != null && objs.isJsonArray() ? objs.getAsJsonArray() : new JsonArray()) {
                        List<Objet> cands = candidatsObjet(e, o, b.objetsFlyTrap.size());
                        if (cands.isEmpty()) continue;
                        b.objetsFlyTrap.add(cands.get(0));
                        b.candidatsFlyTrap.add(cands);
                    }
                    b.recompenseFlyTrap = objetQuete(ft.get("recompense"), o, "recompense", 0);
                    JsonObject r = objet(ft, "recompense");
                    if (r != null) b.armeRecompense = texte(r, "arme", "annihilator");
                }
                // bocaux : ancien format (liste fixe) ou 0.2.3 {a_tirer, candidats}
                JsonElement bm = q.get("bocaux_moelle");
                JsonArray lb = new JsonArray();
                if (bm != null && bm.isJsonArray()) lb = bm.getAsJsonArray();
                else if (bm != null && bm.isJsonObject()) {
                    JsonObject bo = bm.getAsJsonObject();
                    b.bocauxATirer = Math.max(0, entier(bo, "a_tirer", 3));
                    JsonElement c = bo.has("candidats") ? bo.get("candidats") : bo.get("cachettes");
                    if (c != null && c.isJsonArray()) lb = c.getAsJsonArray();
                }
                for (JsonElement e : lb) {
                    Objet ob = objetQuete(e, o, "bocal", b.bocaux.size());
                    if (ob != null) b.bocaux.add(ob);
                }
                for (JsonElement e : tableau(q, "cibles_cachees")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject c = e.getAsJsonObject();
                    BlockPos p = bloc(c.get("pos"), o);
                    if (p == null) continue;
                    String type = texte(c, "type", "tir").toLowerCase(Locale.ROOT);
                    b.ciblesCachees.add(new CibleCachee(b.ciblesCachees.size(), texte(c, "id", type + b.ciblesCachees.size()), p, type,
                            texte(c, "zone_visible_depuis", texte(c, "zone", "")), bloc(c.get("recompense_pos"), o)));
                }
                b.carteAcces = objetQuete(q.get("carte_acces"), o, "carte", 0);
                JsonObject pc = objet(q, "porte_confinement");
                if (pc != null) b.porteConfinement = machine(pc, o, Direction.WEST);
                JsonObject ab = objet(q, "arene_boss");
                if (ab != null) {
                    BoundingBox bb = boite(ab.get("volume"), o);
                    if (bb != null) b.areneBoss = PlanZombies.aabb(bb);
                }
                JsonObject sn = objet(q, "salle_n07");
                if (sn != null) {
                    BlockPos so = bloc(sn.get("origine"), o);
                    if (so != null) {
                        b.salleBoss = new ZombiesApi.PlanSalle(texte(sn, "variante", "n07_hall_zombies"), so,
                                fr.heralisz.matchs.moteur.api.Pose.rotationAnnexe(texte(sn, "rotation", "none")));
                    }
                }
                JsonObject ms = objet(q, "machine_secrete");
                if (ms != null) {
                    JsonObject tn = objet(ms, "tas_de_neige");
                    if (tn != null) {
                        b.tasDeNeigeVolume = boite(tn.get("volume"), o);
                        lireBlocs(tableau(tn, "blocs"), o, b.tasDeNeige);
                    }
                    b.atoutSecret = texte(ms, "atout", "");
                    for (JsonElement e : tableau(ms, "alternatives")) if (e.isJsonPrimitive()) b.alternativesSecret.add(e.getAsString());
                    BlockPos tr = bloc(ms.get("tete_robot"), o);
                    if (tr != null) b.teteRobot = Vec3.atCenterOf(tr).add(0, 6, 0);
                }
            }
            JsonObject tp = objet(m, "teleporteurs");
            if (tp != null) {
                b.utile = true;
                Map<Character, Integer> amesParLettre = new HashMap<>();
                if (q != null) {
                    for (JsonElement e : tableau(q, "cuves_ames")) {
                        if (!e.isJsonObject()) continue;
                        String l = texte(e.getAsJsonObject(), "teleporteur", "");
                        if (!l.isEmpty()) amesParLettre.put(Character.toUpperCase(l.charAt(0)), entier(e.getAsJsonObject(), "ames_requises", 20));
                    }
                }
                for (char l : new char[]{'A', 'B', 'C'}) {
                    JsonObject t = objet(tp, String.valueOf(l));
                    if (t == null) continue;
                    BlockPos pad = bloc(t.get("pad"), o);
                    if (pad == null) continue;
                    BoundingBox em = boite(t.get("pad_emprise"), o);
                    AABB emprise = em != null ? PlanZombies.aabb(em) : new AABB(pad).inflate(2, 0, 2).expandTowards(0, 2, 0);
                    Direction face = PlanZombies.face(texte(t, "face", ""), Direction.NORTH);
                    Machine pupitre = machineSouple(t.get("pupitre"), o, face);
                    BlockPos cuve = bloc(t.get("cuve_ames"), o);
                    Machine mc = cuve == null ? null : new Machine(cuve, faceVers(cuve, pad));
                    b.teleporteurs.add(new Teleporteur(b.teleporteurs.size(), l, pad, emprise, texte(t, "zone", ""), face, pupitre, mc,
                            amesParLettre.getOrDefault(l, 20), entier(t, "cout_teleportation", 1500), entier(t, "duree_liaison_s", 30) * 20));
                }
                JsonObject mf = objet(tp, "mainframe");
                if (mf != null) {
                    BlockPos pad = bloc(mf.get("pad"), o);
                    if (pad != null) {
                        BoundingBox em = boite(mf.get("pad_emprise"), o);
                        AABB emprise = em != null ? PlanZombies.aabb(em) : new AABB(pad).inflate(2, 0, 2).expandTowards(0, 2, 0);
                        BlockPos cpos = bloc(mf.get("console"), o);
                        Machine console = cpos == null ? null : new Machine(cpos, PlanZombies.face(texte(mf, "face_console", ""), Direction.SOUTH));
                        Machine tour = machineSouple(mf.get("tour"), o, Direction.SOUTH);
                        if (tour == null) {
                            BoundingBox te = boite(mf.get("tour_emprise"), o);
                            if (te != null) tour = new Machine(new BlockPos((te.minX() + te.maxX()) / 2, te.minY(), (te.minZ() + te.maxZ()) / 2),
                                    PlanZombies.face(texte(mf, "face_tour", "sud"), Direction.SOUTH));
                        }
                        Machine plat = machineSouple(mf.get("plateforme_modele"), o, Direction.SOUTH);
                        BlockPos bg = bloc(mf.get("bonus_gratuit"), o);
                        Vec3 bonus = bg != null ? Vec3.atBottomCenterOf(bg) : Vec3.atBottomCenterOf(pad);
                        b.mainframe = new Mainframe(pad, emprise, console, tour, plat, bonus);
                    }
                }
            }
            JsonObject pp = objet(m, "porte_pack_a_punch");
            if (pp != null) {
                b.rideau = boite(pp.get("blocs"), o);
                b.faceRideau = PlanZombies.face(texte(pp, "cote_achat", "sud"), Direction.SOUTH);
                b.zoneRideau = texte(pp, "zone_ouverte", "pack_a_punch");
                b.utile |= b.rideau != null;
            }
            JsonObject pa = objet(m, "passerelle");
            if (pa != null) {
                lireBlocs(tableau(pa, "blocs_releves"), o, b.passerelleRelevee);
                lireBlocs(tableau(pa, "blocs_abaisses"), o, b.passerelleAbaissee);
                BoundingBox v1 = boite(pa.get("volume_releve"), o), v2 = boite(pa.get("volume_abaisse"), o);
                b.passerelleVolume = v1 == null ? v2 : v2 == null ? v1 : BoundingBox.encapsulatingBoxes(List.of(v1, v2)).orElse(v1);
                b.utile = true;
            }
            for (JsonElement e : tableau(m, "sauts")) {
                if (!e.isJsonObject()) continue;
                String de = texte(e.getAsJsonObject(), "depuis", ""), vers = texte(e.getAsJsonObject(), "vers", "");
                if (!de.isEmpty() && !vers.isEmpty()) b.sautsVers.computeIfAbsent(vers, k -> new ArrayList<>()).add(de);
            }
            for (JsonElement ze : tableau(m, "zones")) {
                if (!ze.isJsonObject()) continue;
                String id = texte(ze.getAsJsonObject(), "id", "");
                for (JsonElement s : tableau(ze.getAsJsonObject(), "acces_par_saut_depuis")) {
                    if (s.isJsonPrimitive() && !id.isEmpty()) {
                        List<String> l = b.sautsVers.computeIfAbsent(id, k -> new ArrayList<>());
                        if (!l.contains(s.getAsString())) l.add(s.getAsString());
                    }
                }
            }
            for (JsonElement e : tableau(m, "pieges")) {
                if (!e.isJsonObject()) continue;
                JsonObject p = e.getAsJsonObject();
                BoundingBox v = boite(p.get("volume"), o);
                if (v == null) continue;
                List<Machine> pan = new ArrayList<>();
                for (JsonElement x : tableau(p, "panneaux")) {
                    if (!x.isJsonObject()) continue;
                    Machine mc = machine(x.getAsJsonObject(), o, Direction.NORTH);
                    if (mc != null) pan.add(mc);
                }
                b.pieges.add(new Piege(b.pieges.size(), texte(p, "id", "piege" + b.pieges.size()), texte(p, "zone", ""), PlanZombies.aabb(v), pan,
                        entier(p, "cout", 1000), Math.max(20, entier(p, "duree_s", 25) * 20), Math.max(20, entier(p, "recharge_s", 25) * 20)));
                b.utile = true;
            }
            JsonObject bw = objet(m, "couteau_bowie");
            if (bw != null) {
                b.bowie = machine(bw, o, Direction.NORTH);
                b.coutBowie = entier(bw, "cout", 3000);
                b.utile |= b.bowie != null;
            }
            for (JsonElement e : tableau(m, "decor_etats")) {
                if (!e.isJsonObject()) continue;
                JsonObject d = e.getAsJsonObject();
                BoundingBox em = boite(d.get("emprise"), o);
                String bloc = texte(d, "bloc", ""), pr = texte(d, "propriete", ""), de = texte(d, "declencheur", "");
                if (em != null && !bloc.isEmpty() && !pr.isEmpty() && !de.isEmpty()) b.etatsDecor.add(new EtatDecor(bloc, pr, de, em));
            }
            JsonElement am = m.get("atouts_aleatoires");
            b.atoutsMelanges = am != null && am.isJsonPrimitive() && am.getAsBoolean();
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[zombies] {} : champs The Giant illisibles ({}) : lus en partie", carte, ex.toString());
        }
        if (!b.utile) return null;
        PlanGiant p = new PlanGiant(b);
        HeraliszMatchs.LOG.debug("[zombies] {} : plan The Giant lu en {} us ({} cachettes, {} teleporteurs, mainframe {}, {} pieges, passerelle {}+{} blocs)",
                carte, (System.nanoTime() - t0) / 1000, p.cachettes.size(), p.teleporteurs.size(), p.mainframe != null, p.pieges.size(),
                p.passerelleRelevee.size(), p.passerelleAbaissee.size());
        return p;
    }

    /** Direction cardinale la plus proche de {@code depuis} vers {@code vers}. */
    private static Direction faceVers(BlockPos depuis, BlockPos vers) {
        int dx = vers.getX() - depuis.getX(), dz = vers.getZ() - depuis.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void lireBlocs(Iterable<JsonElement> l, BlockPos o, List<BlocCarte> out) {
        Map<String, BlockState> cache = new LinkedHashMap<>();
        for (JsonElement e : l) {
            if (!e.isJsonArray() || e.getAsJsonArray().size() < 4) continue;
            JsonArray a = e.getAsJsonArray();
            try {
                BlockPos p = o.offset(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
                String s = a.get(3).getAsString();
                BlockState st = cache.computeIfAbsent(s, PlanGiant::etat);
                out.add(new BlocCarte(p, st));
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Etat de bloc depuis {@code "minecraft:x[prop=val]"} ; air si illisible. */
    static BlockState etat(String s) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), s, false).blockState();
        } catch (Exception ex) {
            HeraliszMatchs.LOG.warn("[zombies] etat de bloc illisible : {}", s);
            return Blocks.AIR.defaultBlockState();
        }
    }

    /**
     * Candidats d'un objet du Fly Trap : {@code {type, candidats: [{pos, face, zone}...]}} (0.2.3), ou un objet a position
     * fixe {@code {type, pos, ...}} (un seul candidat). Le type et la zone de l'objet servent de defaut aux candidats.
     */
    private static List<Objet> candidatsObjet(JsonElement e, BlockPos o, int index) {
        List<Objet> l = new ArrayList<>();
        if (e == null || !e.isJsonObject()) return l;
        JsonObject j = e.getAsJsonObject();
        String type = texte(j, "type", "objet").toLowerCase(Locale.ROOT);
        if (j.has("pos")) {
            Objet ob = objetQuete(j, o, type, index);
            if (ob != null) l.add(ob);
        }
        for (JsonElement c : tableau(j, "candidats")) {
            if (!c.isJsonObject()) continue;
            JsonObject cj = c.getAsJsonObject();
            BlockPos p = bloc(cj.get("pos"), o);
            if (p == null) continue;
            Objet ob = new Objet(index, type, p, PlanZombies.face(texte(cj, "face", texte(j, "face", "")), Direction.SOUTH),
                    texte(cj, "zone", texte(j, "zone", "")), point(cj.get("ancre"), o));
            if (l.stream().noneMatch(x -> x.pos().equals(ob.pos()))) l.add(ob);
        }
        return l;
    }

    @Nullable
    private static Objet objetQuete(@Nullable JsonElement e, BlockPos o, @Nullable String type, int index) {
        if (e == null || !e.isJsonObject()) return null;
        JsonObject j = e.getAsJsonObject();
        BlockPos p = bloc(j.get("pos"), o);
        if (p == null) return null;
        return new Objet(index, (type != null ? type : texte(j, "type", "objet")).toLowerCase(Locale.ROOT), p,
                PlanZombies.face(texte(j, "face", ""), Direction.SOUTH), texte(j, "zone", ""), point(j.get("ancre"), o));
    }

    /** Point reel {@code [x, y, z]} (blocs) relatif a la carte, rendu absolu ; null si absent ou illisible. */
    @Nullable
    private static Vec3 point(@Nullable JsonElement e, BlockPos o) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 3) return null;
        try {
            JsonArray a = e.getAsJsonArray();
            return new Vec3(a.get(0).getAsDouble() + o.getX(), a.get(1).getAsDouble() + o.getY(), a.get(2).getAsDouble() + o.getZ());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Nullable
    private static Machine machine(JsonObject j, BlockPos o, Direction defaut) {
        BlockPos p = bloc(j.get("pos"), o);
        return p == null ? null : new Machine(p, PlanZombies.face(texte(j, "face", ""), defaut));
    }

    /** {@code [x,y,z]}, {@code [x,y,z,face]} ou {@code {pos, face}}. */
    @Nullable
    private static Machine machineSouple(@Nullable JsonElement e, BlockPos o, Direction defaut) {
        if (e == null) return null;
        if (e.isJsonObject()) return machine(e.getAsJsonObject(), o, defaut);
        BlockPos p = bloc(e, o);
        if (p == null) return null;
        Direction f = defaut;
        if (e.isJsonArray() && e.getAsJsonArray().size() > 3 && e.getAsJsonArray().get(3).isJsonPrimitive()) {
            JsonElement x = e.getAsJsonArray().get(3);
            f = x.getAsJsonPrimitive().isNumber() ? Direction.fromYRot(x.getAsFloat()) : PlanZombies.face(x.getAsString(), defaut);
        }
        return new Machine(p, f);
    }

    // ------------------------------------------------------------------ outils
    @Nullable
    private static JsonObject objet(JsonObject o, String cle) {
        JsonElement e = o.get(cle);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
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

    @Nullable
    private static BlockPos bloc(@Nullable JsonElement e, BlockPos o) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 3) return null;
        JsonArray a = e.getAsJsonArray();
        try {
            return o.offset((int) Math.floor(a.get(0).getAsDouble()), (int) Math.floor(a.get(1).getAsDouble()), (int) Math.floor(a.get(2).getAsDouble()));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Nullable
    private static BoundingBox boite(@Nullable JsonElement e, BlockPos o) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 6) return null;
        JsonArray a = e.getAsJsonArray();
        try {
            int x0 = a.get(0).getAsInt(), y0 = a.get(1).getAsInt(), z0 = a.get(2).getAsInt();
            int x1 = a.get(3).getAsInt(), y1 = a.get(4).getAsInt(), z1 = a.get(5).getAsInt();
            return new BoundingBox(Math.min(x0, x1) + o.getX(), Math.min(y0, y1) + o.getY(), Math.min(z0, z1) + o.getZ(),
                    Math.max(x0, x1) + o.getX(), Math.max(y0, y1) + o.getY(), Math.max(z0, z1) + o.getZ());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
