package fr.heraliz.loot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * État de la nourriture ArkaniaZ (geckolib222 : {@code software.bernie.example.item.CustomFoodItem}, clé NBT
 * « FoodState » : 0 bon, 1 pourri, 2 périmé). Le mod d'origine ne le tire qu'à la première lecture : dans l'infobulle,
 * chez le joueur, puis de nouveau à chaque fois que le serveur renvoie l'objet (reconnexion, objet déplacé), d'où un état
 * qui change sans cesse, et jamais celui appliqué en mangeant (tiré à part par le serveur). Ici, le serveur le fixe une
 * fois pour toutes : à la création du loot ({@link TablesCoffres}), et pour la nourriture venue d'ailleurs, dès qu'elle est
 * dans l'inventaire d'un joueur ou dans le conteneur qu'il a ouvert. Même tirage que le mod d'origine (un tiers chacun).
 * Sans geckolib222 (jeu solo de test), rien ne se passe.
 */
final class EtatNourriture {
    private static final String CLE = "FoodState";
    private static final int ETATS = 3;
    private static final Class<?> CLASSE = classe();

    private EtatNourriture() {
    }

    private static Class<?> classe() {
        try {
            return Class.forName("software.bernie.example.item.CustomFoodItem");
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** Donne son état à une nourriture ArkaniaZ qui n'en a pas encore ; true si la pile a changé. */
    static boolean fixer(ItemStack st, RandomSource r) {
        if (CLASSE == null || st.isEmpty() || !CLASSE.isInstance(st.getItem())) return false;
        CompoundTag tag = st.getTag();
        if (tag != null && tag.contains(CLE)) return false;
        st.getOrCreateTag().putInt(CLE, r.nextInt(ETATS));
        return true;
    }

    /** Inventaire du joueur (mains, armure comprises) et conteneur ouvert : renvoyés au joueur au tick suivant. */
    static void joueur(ServerPlayer sp) {
        if (CLASSE == null) return;
        Inventory inv = sp.getInventory();
        boolean change = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            change |= fixer(inv.getItem(i), sp.getRandom());
        }
        if (change) inv.setChanged();
        if (sp.containerMenu != sp.inventoryMenu) {
            for (Slot slot : sp.containerMenu.slots) {
                if (slot.container != inv && fixer(slot.getItem(), sp.getRandom())) slot.setChanged();
            }
        }
    }
}
