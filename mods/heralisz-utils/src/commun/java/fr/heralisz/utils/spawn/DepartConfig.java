package fr.heralisz.utils.spawn;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages du monde du spawn et de la borne de départ (fichier {@code heraliz_spawn-server.toml}, dans
 * {@code serverconfig/} du monde ; copié de {@code defaultconfigs/} pour un monde neuf). Lus par le serveur seulement.
 */
public final class DepartConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue DELAI, ATTENTE, PROTECTION;
    public static final ForgeConfigSpec.DoubleValue BOUGER;
    public static final ForgeConfigSpec.BooleanValue SEULEMENT_AU_SPAWN, AVEUGLEMENT;
    public static final ForgeConfigSpec.ConfigValue<String> MONDE;
    public static final ForgeConfigSpec.IntValue DISTANCE_JOUEURS, ESSAIS, MARGE_BORDURE;
    public static final ForgeConfigSpec.BooleanValue CARTE_ENTIERE, EVITER_ZONES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SOLS;
    public static final ForgeConfigSpec.BooleanValue MONSTRES, PVP;
    public static final ForgeConfigSpec.IntValue BORDURE;
    public static final ForgeConfigSpec.BooleanValue PROTEGER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITES_PERMISES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Borne de départ (bloc heralisz_utils:borne_depart) : clic droit = départ vers la carte.").push("borne");
        DELAI = b.comment("Délai avant le départ (secondes). Le joueur doit rester près de la borne : s'il s'éloigne, est",
                "blessé ou change de monde, le départ est annulé.").defineInRange("delai", 5, 0, 60);
        BOUGER = b.comment("Distance (blocs) dont le joueur peut s'écarter de sa place pendant le délai.")
                .defineInRange("distance_annulation", 1.5, 0.2, 16.0);
        ATTENTE = b.comment("Attente entre deux départs demandés par un même joueur (secondes).").defineInRange("attente", 10, 0, 3600);
        SEULEMENT_AU_SPAWN = b.comment("La borne ne fonctionne que dans le monde du spawn (heralisz:spawn).")
                .define("seulement_au_spawn", true);
        PROTECTION = b.comment("Protection à l'arrivée (secondes) : aucun dégât reçu tant que le joueur n'attaque pas. 0 : aucune.")
                .defineInRange("protection_arrivee", 5, 0, 60);
        AVEUGLEMENT = b.comment("Court fondu au noir à l'arrivée (le temps que la carte s'affiche).").define("fondu_arrivee", true);
        b.pop();
        b.comment("Points d'arrivée sur la carte : points et zones posés par le staff (/depart point ..., /depart zone ...),",
                "gardés avec le monde de la carte (data/heraliz_depart.dat).").push("arrivee");
        MONDE = b.comment("Monde de la carte (dimension) où mène la borne.").define("monde", "minecraft:overworld");
        DISTANCE_JOUEURS = b.comment("Aucun autre joueur à moins de tant de blocs du point d'arrivée.")
                .defineInRange("distance_joueurs", 64, 0, 1024);
        ESSAIS = b.comment("Points essayés au plus pour un départ (un par tick, pendant le délai puis après).")
                .defineInRange("essais", 60, 1, 400);
        CARTE_ENTIERE = b.comment("Sans aucun point ni zone posés : chercher un sol sûr n'importe où dans la bordure de la carte.")
                .define("carte_entiere_si_vide", true);
        MARGE_BORDURE = b.comment("Distance minimale (blocs) entre le point d'arrivée et la bordure de la carte.")
                .defineInRange("marge_bordure", 64, 0, 4096);
        EVITER_ZONES = b.comment("Jamais dans une zone dangereuse de HeraliZ Décors (gaz, radiation, froid : data/heraliz_zones.json).")
                .define("eviter_zones_dangereuses", true);
        SOLS = b.comment("Zones et carte entière : blocs (ou #tags) sur lesquels on peut arriver. Un sol de la liste avec du ciel",
                "au-dessus : jamais sur un toit, dans l'eau ni dans un bâtiment. Les points posés à la main ne sont pas concernés.")
                .defineListAllowEmpty(List.of("sols"), () -> List.of("#minecraft:dirt", "#minecraft:sand", "#minecraft:base_stone_overworld",
                        "minecraft:gravel", "minecraft:dirt_path", "minecraft:snow_block", "minecraft:snow", "minecraft:packed_mud",
                        "#minecraft:terracotta", "heraliz_decors:sable_tasse", "heralisz_utils:sable_desert",
                        "heralisz_utils:sable_desert_ombre", "heraliz_decors:dallage_sable"), o -> o instanceof String);
        b.pop();
        b.comment("Monde du spawn (heralisz:spawn).").push("spawn");
        MONSTRES = b.comment("Monstres permis dans le monde du spawn (false : tout monstre qui y entre est retiré).")
                .define("monstres", false);
        PVP = b.comment("Combat entre joueurs dans le monde du spawn.").define("pvp", false);
        BORDURE = b.comment("Taille de la bordure du monde du spawn (blocs), centrée sur le point de réveil. 0 : celle de la carte.")
                .defineInRange("bordure", 256, 0, 60000000);
        b.pop();
        b.comment("Protection du monde du spawn (heralisz:spawn).").push("protection");
        PROTEGER = b.comment("Aucun dégât pour les joueurs (feu, chute, lave, noyade, faim, gel, vide, explosions, autres joueurs...) :",
                "feu éteint, faim et air gardés pleins, effets négatifs retirés et refusés, chute dans le vide = retour au point de",
                "réveil ; aucune créature (monstres, animaux, PNJ : retirées) ; explosions sans effet sur les blocs et les entités ;",
                "/suicide refusé (sauf staff). Seul /kill passe. false : seulement les réglages de la section [spawn].")
                .define("actif", true);
        ENTITES_PERMISES = b.comment("Créatures gardées malgré tout dans le monde du spawn (identifiants, ex. \"minecraft:villager\").")
                .defineListAllowEmpty(List.of("entites_permises"), () -> List.<String>of(), o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private DepartConfig() {}

    /** La valeur, ou le défaut tant que le fichier n'est pas chargé (avant l'ouverture du monde). */
    public static <T> T lire(ForgeConfigSpec.ConfigValue<T> v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }
}
