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
 * mort a la deconnexion, seules commandes permises en combat), commandes permises sur la carte et matchs (cartes de Xaero masquees). Appliquee au
 * chargement et a chaque modification du fichier.
 */
public final class MoteurConfig {
    private MoteurConfig() {
    }

    /**
     * Seules commandes permises au joueur tague en combat ou a terre (racines, sans « / » ni espace de noms) : toutes les
     * autres sont refusees (choix du proprietaire du 23/10 : aucune commande en combat).
     */
    public static final List<String> COMMANDES_COMBAT_DEFAUT = List.of("login", "register", "l", "reg", "msg", "tell", "w", "r",
            "me", "quetes", "pb", "saison", "extractions", "report");
    /**
     * Seules commandes permises sur la carte (overworld) a un joueur en survie ou aventure qui n'est pas du staff (choix
     * du proprietaire du 23/10 : sur la carte, seul /tpa ; le coffre du clan est refuse par HeraliZ Clans lui-meme).
     */
    public static final List<String> COMMANDES_CARTE_DEFAUT = List.of("tpa", "tpaccept", "tpdeny", "login", "register", "l", "reg",
            "msg", "tell", "w", "r", "me", "quetes", "pb", "saison", "extractions", "clan", "suicide", "report");

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue TAG_COMBAT_S;
    /** Deconnexion pendant le tag = mort (le corps reste, mod corpse) (ajout). */
    public static final ForgeConfigSpec.BooleanValue MORT_DECONNEXION;
    /** Refus des commandes (hors liste permise) au joueur tague ou a terre (ajout). */
    public static final ForgeConfigSpec.BooleanValue REFUS_COMMANDES;
    /** Operateurs exemptes du refus des commandes en combat (ajout). */
    public static final ForgeConfigSpec.BooleanValue OPS_EXEMPTES;
    /** Racines des seules commandes permises au joueur tague (ajout). */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMMANDES_COMBAT;
    /** Filtre des commandes sur la carte (overworld) actif (ajout). */
    public static final ForgeConfigSpec.BooleanValue CARTE_ACTIF;
    /** Racines des seules commandes permises sur la carte (ajout). */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMMANDES_CARTE;
    /** Mini-carte et carte du monde de Xaero masquees pendant les matchs (ajout). */
    public static final ForgeConfigSpec.BooleanValue MASQUER_CARTES;

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
        if (m != null) m.combatCommandes(COMMANDES_COMBAT.get(), COMMANDES_CARTE.get());
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
