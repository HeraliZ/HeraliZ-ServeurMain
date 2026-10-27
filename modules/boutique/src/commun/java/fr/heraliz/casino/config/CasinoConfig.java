package fr.heraliz.casino.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.game.RtpCalc;
import fr.heraliz.casino.game.cards.BlackjackRules;
import fr.heraliz.casino.game.crash.CrashRules;
import fr.heraliz.casino.game.duel.DuelRules;
import fr.heraliz.casino.game.mines.MinesRules;
import fr.heraliz.casino.game.plinko.PlinkoRules;
import fr.heraliz.casino.game.race.RaceRules;
import fr.heraliz.casino.game.roulette.RouletteRules;
import fr.heraliz.casino.game.slot.RottenRules;
import fr.heraliz.casino.machine.MachineType;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration du casino (config/heralizcasino/casino.json) : limites de mise, tables de gains et reglages de
 * chaque machine, taux de change de la caisse. Lue au demarrage du serveur et par /casino reload, puis envoyee
 * aux joueurs (affichage des gains, des cases de la roue...).
 */
public final class CasinoConfig {
    private CasinoConfig() {
    }

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    private static volatile Settings current = new Settings().sanitized();
    private static volatile String currentJson = GSON.toJson(current);

    // =================================================================== contenu
    public static class Settings {
        public String aide = "Voir le README de Heraliz Casino. Gains = somme rendue (mise comprise) sauf mention contraire. "
                + "Apres modification : /casino reload";
        public General general = new General();
        public Cashier caisse = new Cashier();
        /** Compte de capsules des joueurs (27/10) : dépôts, retraits, paiement à la caisse et à l'entrée. */
        public Compte compte = new Compte();
        public RottenRules rotten = new RottenRules();
        public fr.heraliz.casino.game.slot.OlympeRules portesOlympe = new fr.heraliz.casino.game.slot.OlympeRules();
        public fr.heraliz.casino.game.slot.LoupRules souffleLoup = new fr.heraliz.casino.game.slot.LoupRules();
        public fr.heraliz.casino.game.slot.NicheRules laNiche = new fr.heraliz.casino.game.slot.NicheRules();
        public PlinkoRules plinko = new PlinkoRules();
        public BlackjackRules blackjack = new BlackjackRules();
        public RouletteRules roulette = new RouletteRules();
        public CrashRules crash = new CrashRules();
        public MinesRules mines = new MinesRules();
        public RaceRules course = new RaceRules();
        public DuelRules pierreFeuilleCiseaux = rps();
        public DuelRules batailleNavale = new DuelRules();
        /**
         * Lumiere emise par chaque machine posee, de 0 (aucune) a 15 (comme une lanterne), par identifiant de machine.
         * En dessous du reglage d'usine, la machine elle-meme s'assombrit aussi (neons, ecrans et halos plus doux).
         */
        public java.util.Map<String, Integer> lumieres = defaultLights();
        /** Version du fichier (mise a jour des anciens reglages par defaut) ; 0 = fichier d'avant la 1.4.1. */
        public int version;

        public static final int VERSION = 7;

        private static DuelRules rps() {
            DuelRules r = new DuelRules();
            r.winsNeeded = 3;
            return r;
        }

        /** Fichier d'une version precedente : les reglages par defaut qui ont change sont mis a jour. */
        void migrate() {
            if (version < 3) {
                // 1.4.1 : pierre-feuille-ciseaux en 3 manches gagnantes ; Olympe et Loup refaits (probabilites du vrai jeu)
                if (pierreFeuilleCiseaux != null && pierreFeuilleCiseaux.winsNeeded == 2) pierreFeuilleCiseaux.winsNeeded = 3;
                portesOlympe = new fr.heraliz.casino.game.slot.OlympeRules();
                souffleLoup = new fr.heraliz.casino.game.slot.LoupRules();
            }
            if (version < 4 && souffleLoup != null && Math.abs(souffleLoup.hat - 0.107) < 1e-9) {
                // 1.4.3 : chapeaux un peu plus rares (redistribution 87 %)
                souffleLoup.hat = 0.106;
            }
            if (version < 5) {
                // 1.7.0 : Souffle du Loup refait (243 facons, scie d'or, cadres, super roue et grille 5 x 6) ; seules
                // les limites de mise sont gardees
                fr.heraliz.casino.game.slot.LoupRules fresh = new fr.heraliz.casino.game.slot.LoupRules();
                if (souffleLoup != null) {
                    fresh.minBet = souffleLoup.minBet;
                    fresh.maxBet = souffleLoup.maxBet;
                }
                souffleLoup = fresh;
            }
            if (version < 6 && caisse != null) {
                // 2.5.0 (choix du proprietaire, 22/10) : plus aucun change herals <-> capsules, entree libre, et les
                // capsules trouvees sur la carte s'echangent contre des objets (catalogue de depart si l'ancienne liste
                // d'usine emeraude / or / diamant n'avait pas ete changee)
                if (caisse.economie != null) {
                    caisse.economie.actif = false;
                    caisse.economie.achat = false;
                    caisse.economie.vente = false;
                    caisse.economie.entree = false;
                }
                if (caisse.entree != null) caisse.entree.prix = 0;
                caisse.echangesObjets = true;
                if (caisse.echanges == null || anciensEchanges(caisse.echanges)) caisse.echanges = defaultExchanges();
            }
            if (version < 7 && caisse != null && caisse.entree != null) {
                // 2.5.0 (choix du proprietaire, 23/10) : entree de nouveau payante, 25 capsules (jamais en herals)
                caisse.entree.prix = 25;
            }
            version = VERSION;
        }

        public Settings sanitized() {
            if (general == null) general = new General();
            if (caisse == null) caisse = new Cashier();
            if (caisse.echanges == null) caisse.echanges = new ArrayList<>();
            if (caisse.encaissements == null) caisse.encaissements = new ArrayList<>();
            caisse.entree = (caisse.entree == null ? new Entree() : caisse.entree).sanitized();
            caisse.economie = (caisse.economie == null ? new JetonsServeur() : caisse.economie).sanitized();
            compte = (compte == null ? new Compte() : compte).sanitized();
            rotten =(rotten == null ? new RottenRules() : rotten).sanitized();
            portesOlympe = (portesOlympe == null ? new fr.heraliz.casino.game.slot.OlympeRules() : portesOlympe).sanitized();
            souffleLoup = (souffleLoup == null ? new fr.heraliz.casino.game.slot.LoupRules() : souffleLoup).sanitized();
            laNiche = (laNiche == null ? new fr.heraliz.casino.game.slot.NicheRules() : laNiche).sanitized();
            plinko = (plinko == null ? new PlinkoRules() : plinko).sanitized();
            blackjack = (blackjack == null ? new BlackjackRules() : blackjack).sanitized();
            roulette = (roulette == null ? new RouletteRules() : roulette).sanitized();
            crash = (crash == null ? new CrashRules() : crash).sanitized();
            mines = (mines == null ? new MinesRules() : mines).sanitized();
            course = (course == null ? new RaceRules() : course).sanitized();
            pierreFeuilleCiseaux = (pierreFeuilleCiseaux == null ? rps() : pierreFeuilleCiseaux).sanitized();
            batailleNavale = (batailleNavale == null ? new DuelRules() : batailleNavale).sanitized();
            general.jackpotSeed = Math.max(0, general.jackpotSeed);
            lumieres = sanitizedLights(lumieres);
            version = VERSION;
            return this;
        }

        /** Lumiere emise par une machine (0-15). */
        public int light(MachineType t) {
            Integer v = lumieres == null ? null : lumieres.get(t.id);
            return v == null ? t.light : Math.max(0, Math.min(15, v));
        }

        /** Plus petite mise acceptee par une machine du casino : en dessous, un joueur ne peut plus rien jouer. */
        public long minimalBet() {
            long m = Long.MAX_VALUE;
            for (MachineType t : MachineType.values()) if (t.game()) m = Math.min(m, limits(t)[0]);
            return m == Long.MAX_VALUE ? 1 : Math.max(1, m);
        }

        /** [mise min, mise max] d'une machine. */
        public long[] limits(MachineType t) {
            return switch (t) {
                case ROTTEN -> new long[]{rotten.minBet, rotten.maxBet};
                case OLYMPE -> new long[]{portesOlympe.minBet, portesOlympe.maxBet};
                case LOUP -> new long[]{souffleLoup.minBet, souffleLoup.maxBet};
                case NICHE -> new long[]{laNiche.minBet, laNiche.maxBet};
                case PLINKO -> new long[]{plinko.minBet, plinko.maxBet};
                case BLACKJACK -> new long[]{blackjack.minBet, blackjack.maxBet};
                case ROULETTE -> new long[]{roulette.minBet, roulette.maxBet};
                case CRASH -> new long[]{crash.minBet, crash.maxBet};
                case MINES -> new long[]{mines.minBet, mines.maxBet};
                case COURSE -> new long[]{course.minBet, course.maxBet};
                case PIERRE_FEUILLE_CISEAUX -> new long[]{pierreFeuilleCiseaux.minBet, pierreFeuilleCiseaux.maxBet};
                case BATAILLE_NAVALE -> new long[]{batailleNavale.minBet, batailleNavale.maxBet};
                default -> new long[]{1, 1000};
            };
        }
    }

    public static class General {
        /** Annonce a tout le serveur a partir de ce benefice (jetons)... */
        public long announceMinProfit = 5000;
        /** ...et de ce multiplicateur. */
        public double announceMinMultiplier = 10;
        /** Jackpot progressif des machines a sous : montant de depart (paye par la maison). */
        public long jackpotSeed = 5000;
        /** Mise a partir de laquelle un jackpot est gagne en entier (en dessous : au prorata de la mise). */
        public long jackpotFullBet = 100;
    }

    /**
     * Caisse : les capsules (jetons de casino, trouvees sur la carte) s'echangent contre des objets. Le change avec les
     * herals est ferme par le code (EconomieServeur.CHANGE_HERALS), quels que soient les reglages "economie".
     */
    public static class Cashier {
        /** Change avec les herals : FERME par le code (EconomieServeur.CHANGE_HERALS), garde pour l'ancien fichier. */
        public JetonsServeur economie = new JetonsServeur();
        /** false : les echanges d'objets sont fermes, la liste "echanges" reste dans le fichier. */
        public boolean echangesObjets = true;
        public List<Exchange> echanges = defaultExchanges();
        /** Encaissements par commande (ex. economie d'un plugin) : jetons -> commande console. Vide = desactive. */
        public List<CommandExchange> encaissements = new ArrayList<>();
        /** Entree du casino (zone delimitee par /casino zone, gardee par les videurs). */
        public Entree entree = new Entree();
    }

    /**
     * Change a la caisse avec la monnaie du serveur (mod HeraliZ Economie) : "jetonsServeur" jetons du compte du joueur
     * = "jetonsCasino" jetons de casino, dans les deux sens. Chaque operation passe par l'API de l'economie (atomique,
     * inscrite au journal du compte).
     */
    public static class JetonsServeur {
        public String aide = AIDE;
        /** Change ouvert (le mod economie doit aussi etre installe) ; sans effet tant que EconomieServeur.CHANGE_HERALS est faux. */
        public boolean actif = false;
        /** Taux : jetons du serveur... */
        public long jetonsServeur = 1;
        /** ...pour ce nombre de jetons de casino. */
        public long jetonsCasino = 1;
        /** Le joueur peut acheter des jetons de casino avec son compte. */
        public boolean achat = false;
        /** Le joueur peut revendre ses jetons de casino contre des jetons sur son compte. */
        public boolean vente = false;
        /** L'entree du casino peut se payer avec le compte (au meme taux), si le joueur n'a pas assez de jetons de casino. */
        public boolean entree = false;
        /**
         * Part gardee par le casino a la revente (0,05 = 5 %) : 100 jetons de casino revendus rendent 95 jetons du serveur
         * (arrondi en dessous). L'achat reste au taux plein.
         */
        public double commissionRevente = 0.05;

        static final String AIDE = "FERME par le code (choix du proprietaire : plus aucun echange herals <-> capsules). "
                + "Change avec les herals, la monnaie du serveur (HeraliZ Economie) : jetons_serveur herals du compte = "
                + "jetons_casino jetons du casino, dans les deux sens. achat / vente / entree : autoriser l'achat, la revente, le paiement de l'entree. "
                + "commission_revente : part gardee par le casino a la revente (0.05 = 5 %).";

        JetonsServeur sanitized() {
            aide = AIDE;
            jetonsServeur = Math.max(1, Math.min(1_000_000L, jetonsServeur));
            jetonsCasino = Math.max(1, Math.min(1_000_000L, jetonsCasino));
            commissionRevente = Double.isFinite(commissionRevente) ? Math.max(0, Math.min(0.5, commissionRevente)) : 0.05;
            return this;
        }

        /** Jetons du serveur rendus pour "units" fois le taux revendus, commission deduite (arrondi en dessous). */
        public long reventeServeur(long units) {
            return (long) Math.floor(units * jetonsServeur * (1 - commissionRevente) + 1e-9);
        }
    }

    /** Entree payante de la zone du casino et expulsion des joueurs fauches. */
    public static class Entree {
        /**
         * Prix d'entree (capsules, ou objets de la caisse pour la meme valeur) ; 0 = entree libre. 25 capsules depuis
         * la 2.5.0 (choix du proprietaire).
         */
        public long prix = 25;
        /**
         * Delai (minutes) pour entrer apres avoir paye. Le ticket ne vaut que pour une entree : qui ressort de la salle
         * repaie. 0 = juste le temps de passer la porte.
         */
        public int validiteMinutes = 60;
        /**
         * Les videurs sortent les joueurs fauches (moins de jetons que la plus petite mise, aucune partie en cours) ; celui
         * qui a encore des objets a changer a la caisse a 30 s pour y aller.
         */
        public boolean expulserFauches = true;
        /** ... y compris ceux qui n'ont rien mise pendant leur visite. */
        public boolean expulserSpectateurs = false;
        /** Delai entre le moment ou un joueur est fauche et l'arrivee des videurs (secondes). */
        public int delaiExpulsion = 4;

        Entree sanitized() {
            prix = Math.max(0, Math.min(1_000_000_000L, prix));
            validiteMinutes = Math.max(0, Math.min(100_000, validiteMinutes));
            delaiExpulsion = Math.max(1, Math.min(120, delaiExpulsion));
            return this;
        }
    }

    /**
     * Compte de capsules (27/10, demande du proprietaire) : chaque joueur range ses capsules sur un compte garde par le
     * serveur, hors du monde ({@code heralizcasino/comptes/<uuid>.json} a la racine du serveur), et les reprend quand il veut.
     */
    public static class Compte {
        public String aide = AIDE;
        /** false : compte ferme (ni depot, ni retrait, ni paiement) ; les soldes restent dans leurs fichiers. */
        public boolean actif = true;
        /** La caisse et l'entree du casino prennent sur le compte ce qui manque en capsules dans l'inventaire. */
        public boolean payerCaisse = true;
        /** /capsules : depots et retraits a moins de ce nombre de blocs d'un distributeur ou d'une caisse (0 = partout hors de la carte). */
        public double distance = 6;
        /** Plafond d'un compte (capsules). */
        public long max = 1_000_000_000L;

        static final String AIDE = "Compte de capsules : distributeur de capsules (ATM) du casino, ou /capsules (solde), /capsules deposer "
                + "[nombre] (rien = tout), /capsules retirer <nombre>, a moins de \"distance\" blocs d'un distributeur ou d'une caisse "
                + "(0 = partout hors de la carte). "
                + "payer_caisse : la caisse et l'entree prennent sur le compte ce qui manque dans l'inventaire. max : plafond d'un compte.";

        Compte sanitized() {
            aide = AIDE;
            distance = Double.isFinite(distance) ? Math.max(0, Math.min(64, distance)) : 6;
            max = Math.max(1, Math.min(1_000_000_000_000L, max));
            return this;
        }
    }

    public static class Exchange {
        /** Objet echange (identifiant Minecraft). */
        public String item = "minecraft:emerald";
        /**
         * NBT de l'objet donne par la caisse (vide : aucun), ex. {ConsumableId:"heralisz_heal:ibuprofen"} pour un soin.
         * Un echange avec NBT ne sert qu'a la revente des capsules (jamais d'achat de capsules avec cet objet).
         */
        public String nbt = "";
        /** Capsules (jetons de casino) pour UN objet. */
        public long jetons = 10;
        /** Le joueur peut acheter des capsules avec cet objet (ferme partout : les capsules se trouvent sur la carte). */
        public boolean achat = false;
        /** Le joueur peut echanger des capsules contre cet objet. */
        public boolean vente = true;

        public Exchange() {
        }

        Exchange(String item, long jetons, boolean achat, boolean vente) {
            this(item, "", jetons, achat, vente);
        }

        Exchange(String item, String nbt, long jetons, boolean achat, boolean vente) {
            this.item = item;
            this.nbt = nbt;
            this.jetons = jetons;
            this.achat = achat;
            this.vente = vente;
        }
    }

    public static class CommandExchange {
        /** Nom affiche a la caisse. */
        public String nom = "Argent";
        /** Commande executee par la console : {player} = pseudo, {amount} = montant. */
        public String commande = "eco give {player} {amount}";
        /** Jetons retires pour une unite... */
        public long jetons = 1;
        /** ...et montant ({amount}) verse pour cette unite. */
        public long montant = 1;
    }

    /** Lumiere d'usine de chaque machine, dans l'ordre de la liste des machines. */
    public static java.util.Map<String, Integer> defaultLights() {
        java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>();
        for (MachineType t : MachineType.values()) m.put(t.id, t.light);
        return m;
    }

    /** Toutes les machines presentes (reglage d'usine pour celles qui manquent), niveaux ramenes entre 0 et 15. */
    static java.util.Map<String, Integer> sanitizedLights(@javax.annotation.Nullable java.util.Map<String, Integer> in) {
        java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>();
        for (MachineType t : MachineType.values()) {
            Integer v = in == null ? null : in.get(t.id);
            m.put(t.id, v == null ? t.light : Math.max(0, Math.min(15, v)));
        }
        return m;
    }

    /**
     * Catalogue de depart (PROVISOIRE, 22/10) : capsules -> objets seulement, rien qui donne un avantage en combat ni
     * qui casse l'economie (materiaux de base, nourriture, soins Commun / Peu commun, munitions de 9 mm, Bonus de loot
     * et cle de la caisse des emotes, cheres). A regler dans casino.json (ou la Tablette du Directeur).
     */
    private static List<Exchange> defaultExchanges() {
        List<Exchange> l = new ArrayList<>();
        l.add(new Exchange("heraliz_composants:brindilles", 4, false, true));
        l.add(new Exchange("heraliz_composants:planches", 20, false, true));
        l.add(new Exchange("heraliz_composants:clou", 25, false, true));
        l.add(new Exchange("geckolib:food_mre", 15, false, true));
        l.add(new Exchange("heralisz_tactical:consumable", "{ConsumableId:\"heralisz_heal:ibuprofen\"}", 30, false, true));
        l.add(new Exchange("heralisz_tactical:consumable", "{ConsumableId:\"heralisz_heal:attelle_bandage\"}", 60, false, true));
        l.add(new Exchange("tacz:ammo", "{AmmoId:\"tacz:9mm\"}", 3, false, true));
        l.add(new Exchange("heralisz_utils:document_lootbonus", 2500, false, true));
        l.add(new Exchange("heralizcrates:spectacle_key", 5000, false, true));
        return l;
    }

    /** Ancienne liste d'usine (emeraude, or, diamant), remplacee par le catalogue des capsules a la mise a jour. */
    static boolean anciensEchanges(List<Exchange> l) {
        if (l.size() != 3) return false;
        for (Exchange e : l) {
            if (e == null || !java.util.Set.of("minecraft:emerald", "minecraft:gold_ingot", "minecraft:diamond").contains(e.item)) return false;
        }
        return true;
    }

    // =================================================================== acces
    public static Settings get() {
        return current;
    }

    /** JSON envoye aux joueurs. */
    public static String json() {
        return currentJson;
    }

    /** Reglages recus du serveur (cote client). */
    public static void applyJson(String json) {
        try {
            Settings s = GSON.fromJson(json, Settings.class);
            if (s != null) apply(s.sanitized());
        } catch (Exception e) {
            HeralizCasino.LOGGER.warn("[HeralizCasino] Reglages du serveur illisibles", e);
        }
    }

    static void apply(Settings s) {
        current = s;
        currentJson = GSON.toJson(s);
    }

    // =================================================================== reglage en jeu (Tablette du Directeur)
    /** Copie de travail des reglages actuels. */
    public static Settings copy() {
        return GSON.fromJson(currentJson, Settings.class).sanitized();
    }

    /** Section des reglages d'une machine. */
    public static Object section(MachineType t, Settings s) {
        return switch (t) {
            case CAISSE -> s.caisse;
            case TABLEAU, TABLEAU_PERDANTS, TABLEAU_PARIEURS, ATM -> null;
            case ROTTEN -> s.rotten;
            case OLYMPE -> s.portesOlympe;
            case LOUP -> s.souffleLoup;
            case NICHE -> s.laNiche;
            case PLINKO -> s.plinko;
            case BLACKJACK -> s.blackjack;
            case ROULETTE -> s.roulette;
            case CRASH -> s.crash;
            case MINES -> s.mines;
            case COURSE -> s.course;
            case PIERRE_FEUILLE_CISEAUX -> s.pierreFeuilleCiseaux;
            case BATAILLE_NAVALE -> s.batailleNavale;
        };
    }

    /** Section par defaut (reglages d'usine) d'une machine. */
    public static Object defaultSection(MachineType t) {
        return section(t, new Settings().sanitized());
    }

    /** Redistribution {min, max} d'une machine avec ces reglages (null pour la caisse). */
    public static double[] rtp(MachineType t, Settings s) {
        return switch (t) {
            case CAISSE, TABLEAU, TABLEAU_PERDANTS, TABLEAU_PARIEURS, ATM -> null;
            case ROTTEN -> RtpCalc.rotten(s.rotten);
            case OLYMPE -> RtpCalc.olympe(s.portesOlympe);
            case LOUP -> RtpCalc.loup(s.souffleLoup);
            case NICHE -> RtpCalc.niche(s.laNiche);
            case PLINKO -> RtpCalc.plinko(s.plinko);
            case BLACKJACK -> RtpCalc.blackjack(s.blackjack);
            case ROULETTE -> RtpCalc.roulette(s.roulette);
            case CRASH -> RtpCalc.crash(s.crash);
            case MINES -> RtpCalc.mines(s.mines);
            case COURSE -> RtpCalc.race(s.course);
            case PIERRE_FEUILLE_CISEAUX -> RtpCalc.duel(s.pierreFeuilleCiseaux);
            case BATAILLE_NAVALE -> RtpCalc.duel(s.batailleNavale);
        };
    }

    // =================================================================== simulation (/casino roll, page "Probabilites")
    /** Parties simulees par defaut (0 : les probabilites de cette machine se calculent sans tirage). */
    public static long rounds(MachineType t) {
        return switch (t) {
            case LOUP -> 300_000;
            case NICHE -> 300_000;
            case ROTTEN, OLYMPE, COURSE -> 200_000;
            case BLACKJACK -> 500_000;
            default -> 0;
        };
    }

    /** Frequence des bonus, gains moyens... d'une machine avec ces reglages (null : ni jeu ni mise). */
    public static fr.heraliz.casino.game.Simulator.Report simulate(MachineType t, Settings s, long n, long seed) {
        return switch (t) {
            case ROTTEN -> fr.heraliz.casino.game.Simulator.rotten(s.rotten, n, seed);
            case OLYMPE -> fr.heraliz.casino.game.Simulator.olympe(s.portesOlympe, n, seed);
            case LOUP -> fr.heraliz.casino.game.Simulator.loup(s.souffleLoup, n, seed);
            case NICHE -> fr.heraliz.casino.game.Simulator.niche(s.laNiche, n, seed);
            case PLINKO -> fr.heraliz.casino.game.Simulator.plinko(s.plinko);
            case BLACKJACK -> fr.heraliz.casino.game.Simulator.blackjack(s.blackjack, n, seed);
            case ROULETTE -> fr.heraliz.casino.game.Simulator.roulette(s.roulette);
            case CRASH -> fr.heraliz.casino.game.Simulator.crash(s.crash);
            case MINES -> fr.heraliz.casino.game.Simulator.mines(s.mines);
            case COURSE -> fr.heraliz.casino.game.Simulator.race(s.course, n, seed);
            case PIERRE_FEUILLE_CISEAUX -> fr.heraliz.casino.game.Simulator.duel(s.pierreFeuilleCiseaux);
            case BATAILLE_NAVALE -> fr.heraliz.casino.game.Simulator.duel(s.batailleNavale);
            case CAISSE, TABLEAU, TABLEAU_PERDANTS, TABLEAU_PARIEURS, ATM -> null;
        };
    }

    /**
     * Probabilites d'une machine avec ces reglages, pour la Tablette du Directeur : calculees en arriere-plan puis
     * gardees en memoire par reglages. null tant que rien n'est pret (redemander a l'image suivante) ; pour une machine
     * a sous, le rapport s'affine de tranche en tranche (voir oddsSettled) : c'est la simulation qui donne aussi sa
     * redistribution.
     */
    public static fr.heraliz.casino.game.Simulator.Report odds(MachineType t, Settings s) {
        return switch (t) {
            case ROTTEN -> RtpCalc.report(s.rotten);
            case OLYMPE -> RtpCalc.report(s.portesOlympe);
            case LOUP -> RtpCalc.report(s.souffleLoup);
            case NICHE -> RtpCalc.report(s.laNiche);
            case CAISSE, TABLEAU, TABLEAU_PERDANTS, TABLEAU_PARIEURS, ATM -> null;
            default -> fr.heraliz.casino.game.SimCache.get(oddsKey(t, s), () -> {
                Settings copy = GSON.fromJson(GSON.toJson(s), Settings.class).sanitized();
                return fr.heraliz.casino.game.Simulator.once(() -> simulate(t, copy, rounds(t), 4242));
            }, 1);
        };
    }

    /** Le rapport de odds() est-il definitif (simulation terminee) ? */
    public static boolean oddsSettled(MachineType t, Settings s) {
        return switch (t) {
            case ROTTEN -> RtpCalc.settled(s.rotten);
            case OLYMPE -> RtpCalc.settled(s.portesOlympe);
            case LOUP -> RtpCalc.settled(s.souffleLoup);
            case NICHE -> RtpCalc.settled(s.laNiche);
            case CAISSE, TABLEAU, TABLEAU_PERDANTS, TABLEAU_PARIEURS, ATM -> true;
            default -> fr.heraliz.casino.game.SimCache.done(oddsKey(t, s));
        };
    }

    private static String oddsKey(MachineType t, Settings s) {
        return t.id + GSON.toJson(section(t, s));
    }

    /** Cle de la page "Lumieres" de la Tablette du Directeur (a la place d'un identifiant de machine). */
    public static final String LIGHTS = "lumieres";
}
