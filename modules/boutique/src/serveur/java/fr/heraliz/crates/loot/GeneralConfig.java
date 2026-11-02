package fr.heraliz.crates.loot;

/**
 * Reglages generaux (config/heralizcrates/general.json).
 */
public final class GeneralConfig {
    private GeneralConfig() {
    }

    /** Consommer la cle meme en creatif (02/11 : oui par defaut, sinon le staff ouvre des caisses Gemme sans cle). */
    public static boolean consumeKeyInCreative = true;
    /** Rarete minimale annoncee a tout le serveur. */
    public static LootRarity broadcastMinRarity = LootRarity.LEGENDARY;
    /** Rarete minimale affichee en grand titre au joueur. */
    public static LootRarity titleMinRarity = LootRarity.EPIC;
    /** Nombre d'objets dans la roue (4 a 12). */
    public static int ringSize = 8;
}
