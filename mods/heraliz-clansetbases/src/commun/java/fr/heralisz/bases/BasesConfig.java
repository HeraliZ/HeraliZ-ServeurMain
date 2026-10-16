package fr.heralisz.bases;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Reglages communs (config/heralisz_bases-common.toml), lus cote serveur. Le client recoit les valeurs utiles dans les
 * paquets (cout, fenetre de pillage...).
 */
public final class BasesConfig {
    private BasesConfig() {
    }

    public static final ForgeConfigSpec SPEC;

    // ---- bases (§2, §3)
    public static final ForgeConfigSpec.BooleanValue CREATION;
    public static final ForgeConfigSpec.LongValue COUT_CREATION;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Long>> COUTS_SALLES;
    public static final ForgeConfigSpec.IntValue DELAI_ENTREE_S;
    public static final ForgeConfigSpec.IntValue BUDGET_BLOCS_TICK;
    public static final ForgeConfigSpec.IntValue PAS_GRILLE;
    public static final ForgeConfigSpec.IntValue Y_BUNKER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COQUE_PROVISOIRE;
    // ---- combat (tag local en attendant le moteur de matchs)
    public static final ForgeConfigSpec.IntValue COMBAT_TAG_S;
    // ---- pillage (§4 : lu par le module pillage et par les ecrans)
    public static final ForgeConfigSpec.IntValue FENETRE_DEBUT;
    public static final ForgeConfigSpec.IntValue FENETRE_FIN;
    public static final ForgeConfigSpec.IntValue RESET_HEURE;
    public static final ForgeConfigSpec.IntValue PILLARDS_MAX;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Bases de clan HeraliZ (bunkers)").push("bases");
        CREATION = b.comment("Creation de nouveaux bunkers de clan (joueurs et /base admin creer). Faux depuis la 2.5.0 :",
                        "remplace par les bases CRac du monde des bases. Les bunkers deja crees restent (donnees, entree, API).")
                .define("creation", false);
        COUT_CREATION = b.comment("Cout de creation d'une base (hérals pris dans la banque du clan)")
                .defineInRange("cout_creation", 10_000L, 0L, 1_000_000_000L);
        COUTS_SALLES = b.comment("Cout de deblocage des salles fermees au depart, dans l'ordre du modele (salles 2 a 6) ;",
                        "une salle au-dela de la liste garde le cout ecrit dans bunker.json")
                .defineList("couts_salles", List.of(5_000L, 10_000L, 20_000L, 35_000L, 50_000L),
                        o -> o instanceof Number n && n.longValue() >= 0);
        DELAI_ENTREE_S = b.comment("Secondes immobile avant la teleportation d'entree (annulee si mouvement ou degat)")
                .defineInRange("delai_entree_s", 10, 0, 120);
        BUDGET_BLOCS_TICK = b.comment("Blocs poses ou effaces au plus par tick serveur (pose du bunker par tranches)")
                .defineInRange("budget_blocs_tick", 20_000, 500, 200_000);
        PAS_GRILLE = b.comment("Pas de la grille des parcelles (blocs). Ne JAMAIS changer une fois des bases posees.")
                .defineInRange("pas_grille", 192, 128, 1024);
        Y_BUNKER = b.comment("Altitude du coin bas du bunker dans la dimension (0 a 200)").defineInRange("y_bunker", 64, 0, 200);
        COQUE_PROVISOIRE = b.comment("Blocs du modele remplaces par heralisz_bases:coque a la pose (coque provisoire du dessin) ;",
                        "s'ajoute a la liste « coque_provisoire » de bunker.json")
                .defineList("coque_provisoire", List.of("minecraft:obsidian"), o -> o instanceof String);
        b.pop();

        b.comment("Tag de combat local (remplace par celui du moteur de matchs quand il est present)").push("combat");
        COMBAT_TAG_S = b.comment("Secondes de tag apres avoir inflige ou subi des degats d'un autre joueur")
                .defineInRange("combat_tag_s", 15, 1, 300);
        b.pop();

        b.comment("Pillage (heure du serveur)").push("pillage");
        FENETRE_DEBUT = b.comment("Heure d'ouverture des pillages (0-23)").defineInRange("fenetre_debut", 18, 0, 23);
        FENETRE_FIN = b.comment("Heure de fin des pillages (1-24, apres fenetre_debut)").defineInRange("fenetre_fin", 23, 1, 24);
        RESET_HEURE = b.comment("Heure de remise a zero des entrees de pillage du jour").defineInRange("reset_heure", 6, 0, 23);
        PILLARDS_MAX = b.comment("Pillards au plus en meme temps dans une base").defineInRange("pillards_max", 8, 1, 64);
        b.pop();
        SPEC = b.build();
    }
}
