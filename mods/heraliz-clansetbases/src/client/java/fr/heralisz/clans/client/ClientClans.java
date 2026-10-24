package fr.heralisz.clans.client;

import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Calque;
import fr.heralisz.clans.model.ClanLigne;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.EtatZone;
import fr.heralisz.clans.model.Formes;
import fr.heralisz.clans.model.Evenement;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.InvitationVue;
import fr.heralisz.clans.model.Mouvement;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.model.QueteEtat;
import fr.heralisz.clans.model.ReglagesPerms;
import fr.heralisz.clans.model.Stats;
import fr.heralisz.clans.model.TagVue;
import fr.heralisz.clans.model.ZoneVue;
import fr.heralisz.clans.network.Packets;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Etat du client : ce que le serveur lui a envoye (snapshot de son clan, invitations, tags des joueurs, classement,
 * barre de zone, panneau de drapeau). Les ecrans lisent ici et ne gardent rien d'autre. Thread de rendu uniquement.
 * {@link #faux(String)} remplit un etat realiste pour le banc d'essai.
 */
public final class ClientClans {
    private ClientClans() {
    }

    @Nullable
    private static ClanSnapshot snapshot;
    /** Arbre d'ameliorations et saison du clan (envoye avec chaque snapshot), null avant le premier. */
    private static fr.heralisz.clans.network.Packets.ArbreSaisonS2C arbre;

    public static fr.heralisz.clans.network.Packets.ArbreSaisonS2C arbre() {
        return arbre;
    }

    public static void setArbre(fr.heralisz.clans.network.Packets.ArbreSaisonS2C a) {
        arbre = a;
    }
    private static List<InvitationVue> invitations = List.of();
    private static Map<UUID, TagVue> tags = Map.of();
    private static List<ClanLigne> classement = List.of();
    @Nullable
    private static Packets.TerritoireHudS2C hudZone;
    @Nullable
    private static ZoneVue panneauDrapeau;
    /** Horloge serveur estimee : maintenant(serveur) = maintenant(client) + decalage. */
    private static long decalageMs;
    /** Vrai quand l'etat vient du banc d'essai (faux(...)) : les demandes au serveur qui l'ecraseraient sont ignorees. */
    private static boolean faux;
    /** Vrai des qu'un classement (meme vide) est arrive du serveur. */
    private static boolean classementRecu;
    private static long invitationsRecuesLe;
    /**
     * Versions incrementees a chaque changement de l'etat correspondant : un ecran ne se reconstruit que sur celles
     * qu'il affiche (les tags des joueurs et la barre de zone, qui bougent sans cesse, ne touchent aucune version).
     */
    private static int versionSnapshot, versionInvitations, versionClassement;

    // ------------------------------------------------------------------ lecture
    @Nullable
    public static ClanSnapshot snapshot() {
        return snapshot;
    }

    public static boolean aUnClan() {
        return snapshot != null;
    }

    public static List<InvitationVue> invitations() {
        return invitations;
    }

    public static Map<UUID, TagVue> tags() {
        return tags;
    }

    @Nullable
    public static TagVue tagDe(UUID joueur) {
        return tags.get(joueur);
    }

    public static List<ClanLigne> classement() {
        return classement;
    }

    @Nullable
    public static Packets.TerritoireHudS2C hudZone() {
        return hudZone;
    }

    @Nullable
    public static ZoneVue panneauDrapeau() {
        return panneauDrapeau;
    }

    /** Version du snapshot du clan (change aussi a la deconnexion et sur un faux etat du banc). */
    public static int versionSnapshot() {
        return versionSnapshot;
    }

    public static int versionInvitations() {
        return versionInvitations;
    }

    public static int versionClassement() {
        return versionClassement;
    }

    private static void toutChange() {
        versionSnapshot++;
        versionInvitations++;
        versionClassement++;
    }

    /** Couleur d'accent : couleur principale de la banniere du clan, neutre sans clan. */
    public static int accent() {
        return snapshot != null ? snapshot.accent() : 0x6FA8FF;
    }

    /** Heure serveur estimee (ms epoch) : pour « il y a 3 h », « expire dans 3 min ». */
    public static long maintenantServeur() {
        return System.currentTimeMillis() + decalageMs;
    }

    /**
     * Delai restant d'un champ cd…Ms du snapshot courant (valeur figee a l'envoi) decompte depuis sa reception : un
     * ecran ouvert longtemps voit le delai finir sans attendre un nouveau snapshot. Jamais negatif.
     */
    public static long reste(long cdMs) {
        ClanSnapshot s = snapshot;
        if (cdMs <= 0 || s == null) return Math.max(0, cdMs);
        return Math.max(0, cdMs - Math.max(0, maintenantServeur() - s.maintenant()));
    }

    /** Prix de création d'un clan envoyé par le serveur avec le snapshot (-1 : pas encore reçu). */
    private static long coutCreation = -1L;

    /**
     * Prix de création d'un clan : celui du serveur s'il l'a envoyé, sinon le fichier de config du joueur (qui peut
     * garder un ancien prix : Forge ne réécrit pas une valeur déjà écrite), sinon 1 750.
     */
    public static long coutCreation() {
        if (coutCreation >= 0) return coutCreation;
        try {
            return fr.heralisz.clans.ClansConfig.COUT_CREATION.get();
        } catch (Exception ex) {
            return 1750L;
        }
    }

    public static void setCoutCreation(long cout) {
        if (cout >= 0) coutCreation = cout;
    }

    // ------------------------------------------------------------------ ecriture (ClientHandlers, harness)
    public static void setSnapshot(@Nullable ClanSnapshot s) {
        snapshot = s;
        if (s != null) decalageMs = s.maintenant() - System.currentTimeMillis();
        versionSnapshot++;
    }

    public static void setInvitations(List<InvitationVue> l) {
        invitations = l == null ? List.of() : List.copyOf(l);
        invitationsRecuesLe = System.currentTimeMillis();
        versionInvitations++;
    }

    /** Heure client de reception des invitations (pour decompter « expire dans 3 min »). */
    public static long invitationsRecuesLe() {
        return invitationsRecuesLe;
    }

    public static void setTags(Map<UUID, TagVue> t) {
        tags = t == null ? Map.of() : Map.copyOf(t);
    }

    public static void setClassement(List<ClanLigne> l) {
        classement = l == null ? List.of() : List.copyOf(l);
        classementRecu = true;
        versionClassement++;
    }

    public static void setHudZone(@Nullable Packets.TerritoireHudS2C h) {
        hudZone = h == null || !h.visible() ? null : h;
    }

    public static void setPanneauDrapeau(@Nullable ZoneVue z) {
        panneauDrapeau = z;
    }

    /** Tout oublier (deconnexion). */
    public static void vider() {
        faux = false;
        classementRecu = false;
        snapshot = null;
        invitations = List.of();
        tags = Map.of();
        classement = List.of();
        hudZone = null;
        panneauDrapeau = null;
        toutChange();
    }

    // ------------------------------------------------------------------ faux etat (banc d'essai)
    private static final String[] NOMS = {"Kaelith", "Vortex_77", "MarieLaFouine", "Dziga", "Oberon", "Nyx", "Rastaban", "Lou_Pratt",
            "Hexa", "Tomahawk", "Sable", "Yvann"};

    /**
     * Remplit l'etat avec un scenario : "clan" (clan de 12 membres, 5 en ligne, banque 12 450, 30 mouvements, quetes,
     * 2 territoires, journal, niveau 7), "invitations" (sans clan, 2 invitations), "aucun" (rien).
     */
    public static boolean estFaux() {
        return faux;
    }

    public static boolean classementRecu() {
        return classementRecu;
    }

    // ------------------------------------------------------------------ bannieres factices v2 (banc d'essai)
    /** Tete de creeper : fond vert uni, 2 yeux carres + bouche en 3 rectangles, sur la grille de 8 unites. */
    public static Banniere fauxCreeper() {
        int noir = 0x16210F;
        return new Banniere(0x4E9A3A, 0x4E9A3A, Banniere.Degrade.UNI, List.of(
                Calque.de(Formes.id("carre"), 16, 30, 16, 16, noir),
                Calque.de(Formes.id("carre"), 48, 30, 16, 16, noir),
                Calque.de(Formes.id("carre"), 32, 50, 16, 24, noir),
                Calque.de(Formes.id("carre"), 20, 58, 8, 24, noir),
                Calque.de(Formes.id("carre"), 44, 58, 8, 24, noir)));
    }

    /** NOVA : ecu sombre borde de creme, dragon par-dessus, chevron en pointe. */
    public static Banniere fauxNova() {
        return new Banniere(0xB8402E, 0x2A1B3D, Banniere.Degrade.DIAGONAL, List.of(
                new Calque(Formes.id("ecu"), 32, 38, 46, 56, 0, 0x2A1B3D, 3, 0xF4E6C8, false, false),
                Calque.de(Formes.id("dragon"), 32, 36, 34, 34, 0xF4E6C8),
                new Calque(Formes.id("chevron"), 32, 78, 28, 12, 0, 0xF4E6C8, 0, Calque.CONTOUR_DEFAUT, false, true)));
    }

    /** RVN : anneau clair, faucon au centre, deux etoiles4 de part et d'autre. */
    public static Banniere fauxRaven() {
        return new Banniere(0x1B1F2A, 0x8A2BE2, Banniere.Degrade.RADIAL, List.of(
                Calque.de(Formes.id("anneau"), 32, 38, 50, 50, 0xEDEDED),
                Calque.de(Formes.id("faucon"), 32, 38, 30, 30, 0xEDEDED),
                new Calque(Formes.id("etoile4"), 12, 72, 10, 10, 45, 0xEDEDED, 0, Calque.CONTOUR_DEFAUT, false, false),
                new Calque(Formes.id("etoile4"), 52, 72, 10, 10, 45, 0xEDEDED, 0, Calque.CONTOUR_DEFAUT, false, false)));
    }

    /** ASH : flamme double (miroir), noir et or. */
    public static Banniere fauxCendres() {
        return new Banniere(0xE0702A, 0x2B2B2B, Banniere.Degrade.VERTICAL, List.of(
                new Calque(Formes.id("flamme"), 25, 40, 34, 42, 350, 0x111111, 0, Calque.CONTOUR_DEFAUT, false, false),
                new Calque(Formes.id("flamme"), 39, 40, 34, 42, 10, 0xFFD27A, 2, 0x111111, true, false)));
    }

    /** Banniere factice variee : un ancien embleme migre, parfois une forme de fond en plus. */
    static Banniere fauxAleatoire(Random rnd, int graine) {
        int a = 0x101010 + rnd.nextInt(0xEFEFEF), b = 0x101010 + rnd.nextInt(0xEFEFEF);
        Banniere ban = Banniere.depuisV1(graine % Banniere.EMBLEMES, a, b, Banniere.Degrade.parOrdinal(graine % 5), 0xEEEEEE);
        if (graine % 3 == 0) {
            int forme = Formes.EMBLEMES + (graine * 5) % (Formes.NOMBRE - Formes.EMBLEMES);
            ban = ban.avecCalqueInsere(0, new Calque(forme, 32, 38, 54, 54, 0, b, 2, 0xEEEEEE, false, false));
        }
        return ban;
    }

    public static void faux(String scenario) {
        faux = true;
        classementRecu = true;
        Random rnd = new Random(42);
        long now = System.currentTimeMillis();
        UUID moi = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : new UUID(1, 1);
        switch (scenario) {
            case "clan" -> {
                UUID clanId = new UUID(7, 7);
                Banniere ban = fauxNova();
                List<ClanSnapshot.MembreVue> membres = new ArrayList<>();
                UUID chef = moi;
                for (int i = 0; i < 12; i++) {
                    UUID u = i == 0 ? moi : new UUID(100, i);
                    Grade g = i == 0 ? Grade.CHEF : i < 3 ? Grade.HAUT_GRADE : i < 9 ? Grade.MEMBRE : Grade.RECRUE;
                    Stats st = new Stats(20 + rnd.nextInt(400), 10 + rnd.nextInt(150), 300 + rnd.nextInt(3000), rnd.nextInt(6),
                            50 + rnd.nextInt(900), rnd.nextInt(5), rnd.nextInt(3), (long) (20 * 3600 * (2 + rnd.nextInt(80))),
                            5000 + rnd.nextInt(90000), 100 + rnd.nextInt(2000));
                    boolean enLigne = i < 5;
                    long vuLe = enLigne ? now : now - (long) (3600_000L * (1 + rnd.nextInt(120)));
                    membres.add(new ClanSnapshot.MembreVue(u, i == 0 ? "Vous" : NOMS[i], g, now - 86400_000L * (34 - i * 2), vuLe, st, enLigne,
                            500 + rnd.nextInt(15000)));
                }
                EnumMap<Grade, Integer> perms = new EnumMap<>(Grade.class);
                for (Grade g : Grade.modifiables()) perms.put(g, Perm.masque(g.permsParDefaut()));
                List<Mouvement> mouvements = new ArrayList<>();
                String[] motifs = {"depot", "retrait", "quete", "depot", "depot"};
                for (int i = 0; i < 30; i++) {
                    String motif = motifs[rnd.nextInt(motifs.length)];
                    long montant = motif.equals("retrait") ? -(100 + rnd.nextInt(900)) : 50 + rnd.nextInt(1500);
                    ClanSnapshot.MembreVue m = membres.get(rnd.nextInt(membres.size()));
                    mouvements.add(new Mouvement(now - i * 2_700_000L - rnd.nextInt(600_000), motif.equals("quete") ? null : m.uuid(),
                            motif.equals("quete") ? "Quete" : m.nom(), montant, motif));
                }
                List<QueteEtat> jour = List.of(
                        new QueteEtat("zombies_150", QueteEtat.Type.KILL_ZOMBIES, QueteEtat.Periode.JOUR, 150, 300, 400, "Abattre 150 rodeurs",
                                "Kill 150 walkers", 37, false, 0),
                        new QueteEtat("loot_60", QueteEtat.Type.LOOT_OUVERTS, QueteEtat.Periode.JOUR, 60, 250, 300, "Fouiller 60 caches",
                                "Loot 60 containers", 60, true, now - 3_600_000L),
                        new QueteEtat("temps_90", QueteEtat.Type.TEMPS_JEU_MIN, QueteEtat.Periode.JOUR, 90, 200, 250, "Jouer 90 minutes",
                                "Play 90 minutes", 52, false, 0));
                List<QueteEtat> semaine = List.of(
                        new QueteEtat("zombies_2000", QueteEtat.Type.KILL_ZOMBIES, QueteEtat.Periode.SEMAINE, 2000, 2500, 3000, "Abattre 2 000 rodeurs",
                                "Kill 2,000 walkers", 1240, false, 0),
                        new QueteEtat("captures_3", QueteEtat.Type.CAPTURES, QueteEtat.Periode.SEMAINE, 3, 1500, 2000, "Capturer 3 zones",
                                "Capture 3 zones", 2, false, 0));
                List<ClanSnapshot.TerritoireVue> terr = List.of(new ClanSnapshot.TerritoireVue("kamenka", "Kamenka", now - 8_040_000L),
                        new ClanSnapshot.TerritoireVue("lesnoi", "Lesnoi", now - 54_000_000L));
                List<Evenement> journal = List.of(
                        new Evenement(now - 720_000L, Evenement.Genre.CAPTURE, "Kamenka"),
                        new Evenement(now - 3_600_000L, Evenement.Genre.QUETE, "Fouiller 60 caches"),
                        new Evenement(now - 7_200_000L, Evenement.Genre.ARRIVEE, "Yvann"),
                        new Evenement(now - 20_000_000L, Evenement.Genre.NIVEAU, "7"),
                        new Evenement(now - 40_000_000L, Evenement.Genre.DEPART, "Grimm"),
                        new Evenement(now - 54_000_000L, Evenement.Genre.CAPTURE, "Lesnoi"));
                // reglages individuels : un haut grade prive de retrait, un membre qui peut inviter mais pas casser, une recrue
                // autorisee a construire et a ouvrir les coffres
                Map<UUID, ReglagesPerms> reglages = new HashMap<>();
                reglages.put(new UUID(100, 2), ReglagesPerms.AUCUN.avec(Perm.BANQUE_RETIRER, Perm.Etat.REFUSE));
                reglages.put(new UUID(100, 4), ReglagesPerms.AUCUN.avec(Perm.INVITER, Perm.Etat.AUTORISE).avec(Perm.BASE_CASSER, Perm.Etat.REFUSE));
                reglages.put(new UUID(100, 9), ReglagesPerms.AUCUN.avec(Perm.BASE_CONSTRUIRE, Perm.Etat.AUTORISE).avec(Perm.BASE_COFFRES, Perm.Etat.AUTORISE));
                journal = new ArrayList<>(journal);
                journal.add(1, new Evenement(now - 1_800_000L, Evenement.Genre.PERMISSION, NOMS[9], NOMS[1], Perm.BASE_CONSTRUIRE.name(),
                        Perm.Etat.AUTORISE.name()));
                snapshot = new ClanSnapshot(clanId, "NOVA", "Les Charognards", "Survivre ensemble, prendre les villes,\nne rien laisser derrière.",
                        ban, chef, now - 34L * 86400_000L, 12450, 7, 1240, 2350, 14, 3, membres, perms, moi, Grade.CHEF, 8320, "",
                        0, 2L * 86400_000L + 3L * 3600_000L, 0, 40L * 3600_000L, mouvements, jour, semaine,
                        7L * 3600_000L + 12L * 60_000L, 3L * 86400_000L + 4L * 3600_000L, terr, journal, now, reglages);
                invitations = List.of();
                Map<UUID, TagVue> t = new HashMap<>();
                for (ClanSnapshot.MembreVue m : membres) t.put(m.uuid(), new TagVue("NOVA", ban.accent()));
                tags = t;
                List<ClanLigne> top = new ArrayList<>();
                String[][] clans = {{"RVN", "Ravenhold"}, {"NOVA", "Les Charognards"}, {"KZ", "Kozaki"}, {"ASH", "Cendres"}, {"VLK", "Volki"},
                        {"HEX", "Hexagone"}, {"BRG", "Brigade 9"}, {"OMN", "Omen"}, {"TSR", "Tsar"}, {"DUNE", "Dune"}};
                for (int i = 0; i < clans.length; i++) {
                    Banniere b = switch (i) {
                        case 0 -> fauxRaven();
                        case 1 -> ban;
                        case 2 -> fauxCreeper();
                        case 3 -> fauxCendres();
                        default -> fauxAleatoire(rnd, i * 3);
                    };
                    top.add(new ClanLigne(i + 1, i == 1 ? clanId : new UUID(200, i), clans[i][0], clans[i][1], b, 9 - i / 2, 30000 - i * 2500,
                            6 + rnd.nextInt(14), 400 + rnd.nextInt(3000), Math.max(0, 3 - i / 3)));
                }
                classement = top;
                decalageMs = 0;
            }
            case "creeper", "nova", "raven", "cendres" -> {
                // meme clan, banniere composee choisie (captures de la banniere v2)
                faux("clan");
                ClanSnapshot c = snapshot;
                Banniere b = switch (scenario) {
                    case "creeper" -> fauxCreeper();
                    case "raven" -> fauxRaven();
                    case "cendres" -> fauxCendres();
                    default -> fauxNova();
                };
                if (c != null) {
                    snapshot = new ClanSnapshot(c.id(), c.tag(), c.nom(), c.description(), b, c.chef(), c.creeLe(), c.banque(), c.niveau(),
                            c.xp(), c.xpSuivant(), c.membresMax(), c.territoiresMax(), c.membres(), c.perms(), c.moi(), c.monGrade(), c.monSolde(),
                            c.symbole(), c.cdNomMs(), c.cdTagMs(), c.cdDescMs(), c.cdBanniereMs(), c.journalBanque(), c.quetesJour(),
                            c.quetesSemaine(), c.resetJourMs(), c.resetSemaineMs(), c.territoires(), c.journal(), c.maintenant(), c.reglages());
                    Map<UUID, TagVue> t = new HashMap<>();
                    for (ClanSnapshot.MembreVue m : c.membres()) t.put(m.uuid(), new TagVue(c.tag(), b.accent()));
                    tags = t;
                }
                return;
            }
            case "membre", "haut_grade" -> {
                // meme clan vu par un simple membre (actions limitees, pas d'onglet Permissions) ou par un haut grade (il
                // regle les membres et recrues, sauf ce qu'il n'a pas lui-meme : nom et tag) ; le premier haut grade
                // devient le chef
                Grade monGrade = scenario.equals("membre") ? Grade.MEMBRE : Grade.HAUT_GRADE;
                faux("clan");
                ClanSnapshot c = snapshot;
                if (c != null) {
                    List<ClanSnapshot.MembreVue> ms = new ArrayList<>();
                    for (ClanSnapshot.MembreVue m : c.membres()) {
                        if (m.uuid().equals(moi)) ms.add(new ClanSnapshot.MembreVue(m.uuid(), m.nom(), monGrade, m.rejointLe(), m.vuLe(), m.stats(), true, m.solde()));
                        else if (m.grade() == Grade.HAUT_GRADE && ms.stream().noneMatch(x -> x.grade() == Grade.CHEF))
                            ms.add(new ClanSnapshot.MembreVue(m.uuid(), m.nom(), Grade.CHEF, m.rejointLe(), m.vuLe(), m.stats(), m.enLigne(), m.solde()));
                        else ms.add(m);
                    }
                    UUID chef2 = ms.stream().filter(x -> x.grade() == Grade.CHEF).map(ClanSnapshot.MembreVue::uuid).findFirst().orElse(moi);
                    // un simple membre ne recoit que ses propres reglages (comme Sync cote serveur)
                    Map<UUID, ReglagesPerms> reglages = new HashMap<>(c.reglages());
                    reglages.remove(chef2);
                    if (monGrade == Grade.MEMBRE) reglages.keySet().retainAll(java.util.Set.of(moi));
                    snapshot = new ClanSnapshot(c.id(), c.tag(), c.nom(), c.description(), c.banniere(), chef2, c.creeLe(), c.banque(), c.niveau(),
                            c.xp(), c.xpSuivant(), c.membresMax(), c.territoiresMax(), ms, c.perms(), moi, monGrade, c.monSolde(), c.symbole(),
                            c.cdNomMs(), c.cdTagMs(), c.cdDescMs(), c.cdBanniereMs(), c.journalBanque(), c.quetesJour(), c.quetesSemaine(),
                            c.resetJourMs(), c.resetSemaineMs(), c.territoires(), c.journal(), c.maintenant(), reglages);
                }
                return;
            }
            case "drapeau", "drapeau_attente", "drapeau_libre", "drapeau_visiteur" -> {
                // panneau du drapeau : garde le reste de l'etat, pose seulement la vue de zone
                Banniere nova = snapshot != null ? snapshot.banniere() : fauxNova();
                Banniere rvn = fauxRaven();
                panneauDrapeau = switch (scenario) {
                    case "drapeau" -> new ZoneVue("kamenka", "Kamenka", "NOVA", "Les Charognards", nova, 2L * 3600_000L + 14L * 60_000L, 10, true, true, 0, EtatZone.TENUE);
                    case "drapeau_attente" -> new ZoneVue("kamenka", "Kamenka", "NOVA", "Les Charognards", nova, 25L * 60_000L, 10, true, true,
                            3600_000L + 3L * 60_000L, EtatZone.PROTEGEE);
                    case "drapeau_visiteur" -> new ZoneVue("lesnoi", "Lesnoi", "RVN", "Ravenhold", rvn, 5L * 3600_000L, 10, false, false, 0, EtatZone.CONTESTE);
                    default -> new ZoneVue("gorny", "Gorny", "", "", null, 0, 10, false, false, 0, EtatZone.NEUTRE);
                };
                return;
            }
            case "invitations", "invitations_6" -> {
                // sans clan : 2 invitations (panneau court), ou 6 (4 visibles + defilement)
                snapshot = null;
                List<InvitationVue> l = new ArrayList<>(List.of(
                        new InvitationVue(new UUID(200, 0), "RVN", "Ravenhold", fauxRaven(),
                                14, 16, "Kaelith", 3L * 60_000L + 12_000L),
                        new InvitationVue(new UUID(200, 3), "ASH", "Cendres", fauxCendres(),
                                5, 8, "Oberon", 4L * 60_000L + 40_000L)));
                if (scenario.equals("invitations_6")) {
                    String[][] autres = {{"KZ", "Kozaki", "Dziga"}, {"VLK", "Volki", "Nyx"}, {"HEX", "Hexagone", "Rastaban"}, {"OMN", "Omen", "Sable"}};
                    for (int i = 0; i < autres.length; i++) {
                        Banniere b = i == 0 ? fauxCreeper() : fauxAleatoire(rnd, i * 7 + 3);
                        l.add(new InvitationVue(new UUID(200, 10 + i), autres[i][0], autres[i][1], b, 3 + i * 2, 12, autres[i][2], (2L + i) * 60_000L));
                    }
                }
                invitations = List.copyOf(l);
                invitationsRecuesLe = System.currentTimeMillis();
                tags = Map.of();
                classement = List.of();
            }
            default -> {
                snapshot = null;
                invitations = List.of();
                tags = Map.of();
                classement = List.of();
            }
        }
        hudZone = null;
        panneauDrapeau = null;
        toutChange();
    }

    /** Banc d'essai : quitte l'etat factice (l'appelant redemande l'etat reel au serveur integre). */
    public static void reel() {
        faux = false;
        classementRecu = false;
        snapshot = null;
        invitations = List.of();
        classement = List.of();
        toutChange();
    }

    /** Etat factice (banc) : le « serveur » accepte le masque d'un grade (envoye par l'onglet Permissions). */
    public static void fauxPermsGrade(Grade g, int masque) {
        ClanSnapshot s = snapshot;
        if (!faux || s == null || g == null || g == Grade.CHEF) return;
        EnumMap<Grade, Integer> perms = new EnumMap<>(s.perms());
        perms.put(g, masque & Perm.TOUTES);
        snapshot = s.avecPerms(perms, s.reglages());
        versionSnapshot++;
    }

    /** Etat factice (banc) : le « serveur » applique des reglages individuels (memes masques que PermsJoueurC2S). */
    public static void fauxReglages(UUID membre, int masque, int autorise, int refuse) {
        ClanSnapshot s = snapshot;
        if (!faux || s == null || membre == null || s.membre(membre) == null) return;
        ReglagesPerms r = s.reglagesDe(membre);
        r = new ReglagesPerms((r.autorise() & ~masque) | (autorise & masque), (r.refuse() & ~masque) | (refuse & masque));
        Map<UUID, ReglagesPerms> m = new HashMap<>(s.reglages());
        if (r.vide()) m.remove(membre);
        else m.put(membre, r);
        snapshot = s.avecPerms(s.perms(), m);
        versionSnapshot++;
    }

    /** Faux HUD de zone pour le banc : etat par nom (neutre, capture, conteste, tenue, defense, protegee, limite). */
    public static void fauxHudZone(String etat, float progression) {
        EtatZone e = EtatZone.NEUTRE;
        try {
            e = EtatZone.valueOf(etat.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }
        boolean mienne = e == EtatZone.TENUE || e == EtatZone.DEFENSE;
        setHudZone(new Packets.TerritoireHudS2C("Kamenka", e == EtatZone.NEUTRE ? "" : mienne ? "NOVA" : "RVN",
                mienne ? accent() : 0x8A2BE2, progression, e, e == EtatZone.CAPTURE ? "NOVA" : "", e == EtatZone.PROTEGEE ? 8L * 60_000L : 0, -1));
    }
}
