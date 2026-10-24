package fr.heralisz.utils.combat;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

/**
 * Réglages du tag de combat et des commandes permises ({@code <monde>/serverconfig/heraliz_combat-server.toml}, modèle
 * dans {@code serveur/defaultconfigs/}) : mêmes sections et mêmes clés que l'ancien {@code heralisz_matchs-moteur.toml}
 * de HeraliZ Matchs (retiré le 24/10), sans la section {@code [matchs]}. Appliqués au chargement et à chaque
 * modification du fichier.
 */
public final class CombatConfig {
    private CombatConfig() {
    }

    /**
     * Seules commandes permises au joueur tagué en combat ou à terre (racines, sans « / » ni espace de noms) : toutes
     * les autres sont refusées (choix du propriétaire du 23/10 : aucune commande en combat).
     */
    public static final List<String> COMMANDES_COMBAT_DEFAUT = List.of("login", "register", "l", "reg", "msg", "tell", "w", "r",
            "me", "quetes", "pb", "saison", "extractions", "report");
    /** Seules commandes permises sur la carte (overworld) à un joueur en survie ou aventure qui n'est pas du staff. */
    public static final List<String> COMMANDES_CARTE_DEFAUT = List.of("tpa", "tpaccept", "tpdeny", "login", "register", "l", "reg",
            "msg", "tell", "w", "r", "me", "quetes", "pb", "saison", "extractions", "clan", "suicide", "report", "sethome", "delhome",
            "homes", "base", "purge", "coeur");

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue TAG_COMBAT_S;
    /** Déconnexion pendant le tag = mort (le corps reste). */
    public static final ForgeConfigSpec.BooleanValue MORT_DECONNEXION;
    /** Refus des commandes (hors liste permise) au joueur tagué ou à terre. */
    public static final ForgeConfigSpec.BooleanValue REFUS_COMMANDES;
    /** Opérateurs exemptés du refus des commandes en combat. */
    public static final ForgeConfigSpec.BooleanValue OPS_EXEMPTES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMMANDES_COMBAT;
    /** Filtre des commandes sur la carte (overworld) actif. */
    public static final ForgeConfigSpec.BooleanValue CARTE_ACTIF;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMMANDES_CARTE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("combat");
        TAG_COMBAT_S = b.comment("Duree du tag de combat en secondes (degats infliges ou recus d'un autre joueur)")
                .defineInRange("tag_secondes", 15, 1, 300);
        MORT_DECONNEXION = b.comment("Deconnexion pendant le tag de combat = mort (le corps reste ; jamais a l'arret du serveur)")
                .define("mort_deconnexion", true);
        REFUS_COMMANDES = b.comment("Refuser au joueur tague (ou a terre) toute commande absente de commandes_permises_combat")
                .define("refus_commandes", true);
        OPS_EXEMPTES = b.comment("Les operateurs (niveau 2) tagues gardent toutes leurs commandes")
                .define("operateurs_exemptes", false);
        COMMANDES_COMBAT = b.comment("Seules commandes permises au joueur tague ou a terre (racine en minuscules, sans / ni espace de noms : \"tpa\" couvre aussi /essentials:tpa)")
                .defineListAllowEmpty(List.of("commandes_permises_combat"), () -> COMMANDES_COMBAT_DEFAUT, o -> o instanceof String);
        b.pop();
        b.push("carte");
        CARTE_ACTIF = b.comment("Sur la carte (minecraft:overworld), en survie ou aventure, hors staff (niveau 2) : refuser toute commande absente de commandes_permises")
                .define("actif", true);
        COMMANDES_CARTE = b.comment("Seules commandes permises sur la carte (racine en minuscules, sans / ni espace de noms)")
                .defineListAllowEmpty(List.of("commandes_permises"), () -> COMMANDES_CARTE_DEFAUT, o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    public static void enregistrer(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "heraliz_combat-server.toml");
        modBus.addListener((ModConfigEvent.Loading e) -> appliquer(e.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading e) -> appliquer(e.getConfig()));
    }

    private static void appliquer(ModConfig c) {
        if (c.getSpec() != SPEC) return;
        CombatTag.dureeMs(TAG_COMBAT_S.get() * 1000L);
        CombatServeur.commandesPermises(COMMANDES_COMBAT.get(), COMMANDES_CARTE.get());
    }

    /** Valeur lue sans planter si la config n'est pas encore chargée (tout début du serveur). */
    public static boolean vrai(ForgeConfigSpec.BooleanValue v, boolean defaut) {
        try {
            return SPEC.isLoaded() ? v.get() : defaut;
        } catch (IllegalStateException e) {
            return defaut;
        }
    }
}
