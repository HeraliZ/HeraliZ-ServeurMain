package fr.heralisz.matchs.moteur;

import fr.heralisz.matchs.pont.Services;

import fr.heralisz.matchs.moteur.api.CombatTag;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

/**
 * Configuration serveur du moteur ({@code <monde>/serverconfig/heralisz_matchs-moteur.toml}) : tag de combat (duree,
 * mort a la deconnexion, commandes de teleportation refusees) et matchs (cartes de Xaero masquees). Appliquee au
 * chargement et a chaque modification du fichier.
 */
public final class MoteurConfig {
    private MoteurConfig() {
    }

    /** Commandes de teleportation refusees par defaut au joueur tague (racines, sans « / » ni espace de noms). */
    public static final List<String> COMMANDES_COMBAT_DEFAUT = List.of("spawn", "home", "homes", "warp", "warps", "tpa", "tpahere",
            "tpask", "tpaccept", "tpyes", "back", "rtp", "wild", "randomtp", "tp", "teleport", "tphere", "tpo", "top", "hub",
            "lobby", "ehome", "espawn", "ewarp", "etpa", "etpahere", "etpaccept", "eback", "etop", "etp");

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue TAG_COMBAT_S;
    /** Deconnexion pendant le tag = mort (le corps reste, mod corpse) (ajout). */
    public static final ForgeConfigSpec.BooleanValue MORT_DECONNEXION;
    /** Refus des commandes de teleportation au joueur tague (ajout). */
    public static final ForgeConfigSpec.BooleanValue REFUS_COMMANDES;
    /** Operateurs exemptes du refus des commandes (ajout). */
    public static final ForgeConfigSpec.BooleanValue OPS_EXEMPTES;
    /** Racines des commandes refusees au joueur tague (ajout). */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMMANDES_COMBAT;
    /** Mini-carte et carte du monde de Xaero masquees pendant les matchs (ajout). */
    public static final ForgeConfigSpec.BooleanValue MASQUER_CARTES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("combat");
        TAG_COMBAT_S = b.comment("Duree du tag de combat en secondes (degats infliges ou recus d'un autre joueur)")
                .defineInRange("tag_secondes", 15, 1, 300);
        MORT_DECONNEXION = b.comment("Deconnexion pendant le tag de combat = mort (le corps reste ; jamais a l'arret du serveur)")
                .define("mort_deconnexion", true);
        REFUS_COMMANDES = b.comment("Refuser au joueur tague les commandes de teleportation de la liste commandes_bloquees")
                .define("refus_commandes", true);
        OPS_EXEMPTES = b.comment("Les operateurs (niveau 2) tagues gardent leurs commandes de teleportation")
                .define("operateurs_exemptes", false);
        COMMANDES_COMBAT = b.comment("Commandes refusees au joueur tague (racine en minuscules, sans / ni espace de noms : \"spawn\" couvre aussi /essentials:spawn)")
                .defineListAllowEmpty(List.of("commandes_bloquees"), () -> COMMANDES_COMBAT_DEFAUT, o -> o instanceof String);
        b.pop();
        b.push("matchs");
        MASQUER_CARTES = b.comment("Masquer la mini-carte de Xaero et refuser l'ouverture de sa carte du monde pendant les matchs")
                .define("masquer_cartes", true);
        b.pop();
        SPEC = b.build();
    }

    public static void enregistrer(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "heralisz_matchs-moteur.toml");
        modBus.addListener((ModConfigEvent.Loading e) -> appliquer(e.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading e) -> appliquer(e.getConfig()));
    }

    private static void appliquer(ModConfig c) {
        if (c.getSpec() != SPEC) return;
        CombatTag.dureeMs(TAG_COMBAT_S.get() * 1000L);
        Services.Moteur m = Services.MOTEUR;   // moteur/combat/CombatService (serveur)
        if (m != null) m.combatCommandesBloquees(COMMANDES_COMBAT.get());
    }

    /** Valeur lue sans planter si la config n'est pas encore chargee (client, tout debut du serveur). */
    public static boolean vrai(ForgeConfigSpec.BooleanValue v, boolean defaut) {
        try {
            return SPEC.isLoaded() ? v.get() : defaut;
        } catch (IllegalStateException e) {
            return defaut;
        }
    }
}
