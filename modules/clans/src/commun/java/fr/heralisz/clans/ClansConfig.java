package fr.heralisz.clans;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages communs (config/heralisz_clans-common.toml). Tout ce qui est lu cote serveur pour appliquer les regles ;
 * le client n'en a pas besoin (le snapshot lui donne les valeurs utiles).
 */
public final class ClansConfig {
    private ClansConfig() {
    }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.LongValue COUT_CREATION;
    public static final ForgeConfigSpec.IntValue DELAI_REJOINDRE_H;
    public static final ForgeConfigSpec.IntValue COOLDOWN_MODIF_H;
    public static final ForgeConfigSpec.IntValue LOOT_BONUS;
    public static final ForgeConfigSpec.LongValue REVENU_HEURE;
    public static final ForgeConfigSpec.IntValue XP_HEURE_VALEUR;
    public static final ForgeConfigSpec.IntValue DUREE_CAPTURE_S;
    public static final ForgeConfigSpec.IntValue RAYON_CAPTURE;
    public static final ForgeConfigSpec.IntValue HAUTEUR_CAPTURE;
    public static final ForgeConfigSpec.IntValue DUREE_CAPTURE_NEUTRE_S;
    public static final ForgeConfigSpec.IntValue PROTECTION_MIN;
    public static final ForgeConfigSpec.IntValue TENUE_MIN;
    public static final ForgeConfigSpec.IntValue RAVITAILLEMENT_MIN;
    public static final ForgeConfigSpec.BooleanValue CAISSE_VERROUILLEE_CLAN;
    public static final ForgeConfigSpec.IntValue XP_CAPTURE;
    public static final ForgeConfigSpec.IntValue XP_RAVITAILLEMENT;
    public static final ForgeConfigSpec.IntValue XP_KILL_JOUEUR;
    public static final ForgeConfigSpec.IntValue XP_KILL_ZOMBIE;
    public static final ForgeConfigSpec.IntValue XP_BOSS;
    public static final ForgeConfigSpec.IntValue QUETES_HEURE;
    public static final ForgeConfigSpec.IntValue QUETES_JOUR_SEMAINE;
    public static final ForgeConfigSpec.IntValue QUETES_PAR_JOUR;
    public static final ForgeConfigSpec.IntValue QUETES_PAR_SEMAINE;
    public static final ForgeConfigSpec.IntValue LOOT_DELAI_MIN;
    public static final ForgeConfigSpec.IntValue BOOST_COUPLE_MIN;
    public static final ForgeConfigSpec.IntValue BOOST_REAPPARITION_S;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Regles des clans HeraliZ").push("clans");
        COUT_CREATION = b.comment("Cout de creation d'un clan (hérals, HeraliZ Economie)").defineInRange("cout_creation", 1750L, 0L, Long.MAX_VALUE);
        DELAI_REJOINDRE_H = b.comment("Heures a attendre apres avoir quitte (ou ete exclu d') un clan avant d'en rejoindre un autre")
                .defineInRange("delai_rejoindre_h", 12, 0, 24 * 30);
        COOLDOWN_MODIF_H = b.comment("Heures entre deux modifications d'un meme champ (nom, tag, description, banniere)")
                .defineInRange("cooldown_modif_h", 72, 0, 24 * 365);
        b.pop();

        // l'economie est celle du mod heralisz_economie (dependance obligatoire) : plus de choix de fournisseur ici

        b.comment("Territoires").push("territoires");
        LOOT_BONUS = b.comment("Bonus de loot (%) des membres presents dans une zone tenue (HeraliZ Loot : objets plus rares, quantites)")
                .defineInRange("loot_bonus", 10, 0, 100);
        REVENU_HEURE = b.comment("Herals verses a la banque du clan par heure et par zone tenue (a chaque heure complete)")
                .defineInRange("revenu_heure", 100L, 0L, 1_000_000L);
        XP_HEURE_VALEUR = b.comment("XP du clan par heure tenue et par point de valeur de la zone (valeur = niveau de loot de la ville,",
                        "1 a 7, cle « valeur » de zones.json : l'aerodrome T6 rapporte 6 fois plus qu'un village T1)")
                .defineInRange("xp_heure_valeur", 10, 0, 100000);
        RAYON_CAPTURE = b.comment("Rayon (blocs) autour du drapeau ou l'on capture et conteste la zone ; une zone sans drapeau ne se capture pas")
                .defineInRange("rayon_capture", 10, 2, 64);
        HAUTEUR_CAPTURE = b.comment("Ecart de hauteur maximal (blocs) avec le drapeau pour compter dans le cercle de capture")
                .defineInRange("hauteur_capture", 6, 1, 64);
        DUREE_CAPTURE_S = b.comment("Secondes pour capturer une zone tenue par un autre clan (un seul capteur)").defineInRange("duree_capture_s", 180, 5, 3600);
        DUREE_CAPTURE_NEUTRE_S = b.comment("Secondes pour capturer une zone libre").defineInRange("duree_capture_neutre_s", 120, 5, 3600);
        // 27/10 (regle du proprietaire) : « on le cap, pendant 2 h on n'est pas attaquable, et 2 h apres le territoire
        // redevient neutre, il faut le recap »
        TENUE_MIN = b.comment("Minutes pendant lesquelles un clan tient une zone apres l'avoir capturee : protegee tout ce temps (personne",
                        "ne peut la prendre), puis elle redevient libre toute seule (revenu, bonus de loot et minutes tenues",
                        "s'arretent) : il faut la recapturer. Une zone capturee avant cette regle redevient libre au demarrage si sa",
                        "capture date de plus longtemps. 0 = ancienne regle : zone gardee jusqu'a ce qu'un autre clan la prenne")
                .defineInRange("tenue_min", 120, 0, 7 * 24 * 60);
        PROTECTION_MIN = b.comment("Minutes de protection apres une capture, seulement avec tenue_min = 0 ; avec une tenue, la zone est",
                        "protegee pendant toute la tenue").defineInRange("protection_min", 10, 0, 24 * 60);
        RAVITAILLEMENT_MIN = b.comment("Minutes entre deux signaux de ravitaillement sur une meme zone").defineInRange("ravitaillement_min", 90, 1, 24 * 60);
        CAISSE_VERROUILLEE_CLAN = b.comment("true : seule l'equipe qui a lance le signal ouvre la caisse larguee").define("caisse_verrouillee_clan", false);
        b.pop();

        b.comment("Sources d'XP (les quetes sont dans config/heralisz_clans/quetes.json, les paliers dans niveaux.json)").push("xp");
        XP_CAPTURE = b.defineInRange("capture", 150, 0, 100000);
        XP_RAVITAILLEMENT = b.defineInRange("ravitaillement", 100, 0, 100000);
        XP_KILL_JOUEUR = b.defineInRange("kill_joueur", 5, 0, 100000);
        XP_KILL_ZOMBIE = b.defineInRange("kill_zombie", 1, 0, 100000);
        XP_BOSS = b.defineInRange("boss", 50, 0, 100000);
        b.pop();

        b.comment("Quetes de clan (catalogue dans config/heralisz_clans/quetes.json)").push("quetes");
        QUETES_HEURE = b.comment("Heure du serveur (0-23) du tirage quotidien et hebdomadaire").defineInRange("heure_tirage", 6, 0, 23);
        QUETES_JOUR_SEMAINE = b.comment("Jour du tirage hebdomadaire (1 = lundi ... 7 = dimanche)").defineInRange("jour_tirage_semaine", 1, 1, 7);
        QUETES_PAR_JOUR = b.comment("Nombre de quetes « jour » tirees").defineInRange("par_jour", 3, 1, 8);
        QUETES_PAR_SEMAINE = b.comment("Nombre de quetes « semaine » tirees").defineInRange("par_semaine", 2, 1, 8);
        b.pop();

        b.comment("Statistiques").push("stats");
        LOOT_DELAI_MIN = b.comment("Minutes avant qu'un meme bloc de loot (cfm:loot_*) compte a nouveau pour un joueur")
                .defineInRange("loot_delai_min", 5, 0, 24 * 60);
        BOOST_COUPLE_MIN = b.comment("Anti-boost : minutes avant qu'un kill du meme tueur sur la meme victime compte a nouveau (stats, XP, quetes)")
                .defineInRange("anti_boost_couple_min", 10, 0, 24 * 60);
        BOOST_REAPPARITION_S = b.comment("Anti-boost : un joueur tue moins de N secondes apres sa reapparition (ou sa connexion) ne compte pas")
                .defineInRange("anti_boost_reapparition_s", 60, 0, 3600);
        b.pop();
        SPEC = b.build();
    }
}
