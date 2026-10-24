package fr.heralisz.utils.zombies;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Reglages des zombies de la survie : heraliz_zombies-server.toml (voir {@link SpawnZombiesServeur}). */
public final class ZombiesConfig {
    private ZombiesConfig() {
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ACTIF;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ARCTIQUE, PRISON, MILITAIRE, CONTAMINEES, REMPLACES, MUTANTS, INTERDITS;
    public static final ForgeConfigSpec.DoubleValue PART_HAZMAT;
    public static final ForgeConfigSpec.IntValue PLAFOND_CARTE, PLAFOND_LOCAL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("zombies");
        ACTIF = b.comment("Remplacer les zombies qui apparaissent sur la carte par les zombies HeraliZ (tenue selon l'endroit).",
                "false : zombies de Minecraft comme avant.").define("actif", true);
        REMPLACES = b.comment("Zombies de Minecraft remplaces partout sur la carte.")
                .defineListAllowEmpty("remplaces", List.of("minecraft:zombie", "minecraft:husk", "minecraft:zombie_villager"), o -> o instanceof String);
        INTERDITS = b.comment("Jamais sur la carte, d'ou qu'ils viennent (apparition, commande, oeuf, deja dans le monde) :",
                        "retires des leur arrivee. Les zombies remplaces par les zombies HeraliZ ne passent pas par la.")
                .defineListAllowEmpty("interdits", List.of("minecraft:zombie", "minecraft:husk", "minecraft:zombie_villager",
                        "minecraft:drowned"), o -> o instanceof String);
        MUTANTS = b.comment("Remplaces en plus dans l'arctique, la prison et les zones militaires (\"que des zombies arctiques\").",
                        "Un nom qui finit par :* vaut pour tout le mod.")
                .defineListAllowEmpty("remplaces_zones_fermees", List.of("mutantszombies:*"), o -> o instanceof String);
        b.comment("Villes (identifiants de config/heralisz_clans/zones.json : centre et rayon de la ville).");
        ARCTIQUE = b.comment("Seulement des zombies arctiques (aussi dans les zones « froid » de HeraliZ Decors).")
                .defineListAllowEmpty("villes_arctique", List.of("arctique"), o -> o instanceof String);
        PRISON = b.comment("Seulement des prisonniers.").defineListAllowEmpty("villes_prison", List.of("prison"), o -> o instanceof String);
        MILITAIRE = b.comment("Seulement des militaires.")
                .defineListAllowEmpty("villes_militaire", List.of("aerodrome", "silo"), o -> o instanceof String);
        CONTAMINEES = b.comment("Surtout des hazmat (aussi dans les zones « gaz » et « radiation » de HeraliZ Decors).")
                .defineListAllowEmpty("villes_contaminees", List.of("gaz", "radio"), o -> o instanceof String);
        PART_HAZMAT = b.comment("Part de hazmat dans une zone contaminee (le reste : zombies de ville).")
                .defineInRange("part_hazmat", 0.75, 0.0, 1.0);
        PLAFOND_CARTE = b.comment("Monstres au plus sur toute la carte (24/10, pour 50 joueurs : le serveur ralentit au-dela) ;",
                        "au-dela, plus aucune apparition naturelle de monstre sur la carte. 0 : pas de plafond.",
                        "Vagues des drapeaux, /summon et oeufs : jamais concernes.")
                .defineInRange("plafond_carte", 1500, 0, 100000);
        PLAFOND_LOCAL = b.comment("Monstres au plus dans un carre de 5 x 5 troncons (80 blocs) autour du point d'apparition ;",
                        "au-dela, pas d'apparition naturelle ici (plusieurs joueurs au meme endroit n'empilent plus les hordes). 0 : pas de plafond.")
                .defineInRange("plafond_local", 0, 0, 10000);
        b.pop();
        SPEC = b.build();
    }
}
