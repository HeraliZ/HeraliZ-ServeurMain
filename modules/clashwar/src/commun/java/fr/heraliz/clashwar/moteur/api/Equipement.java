package fr.heraliz.clashwar.moteur.api;

import fr.heraliz.clashwar.pont.Services;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Objets pretes en match (MATCHS_SPEC §1.3) : tout objet donne porte le NBT {@code HeraliszMatch:<id de partie>}
 * ({@link MatchApi#NBT_PRET}) ; il est purge a la restauration, au login, quand il tombe ({@code ItemTossEvent},
 * {@code EntityJoinLevelEvent} d'un ItemEntity en arene). Armes TaCZ via {@code GunItemBuilder} (reserve virtuelle
 * {@code DummyAmmo}/{@code MaxDummyAmmo}, {@code AttachmentLock}), soins/grenades/melee via {@code heralisz_tactical:*},
 * armures via la logique de {@code HzKitItem.give()} ; mods absents = objet ignore avec un avertissement (reflexion
 * tolerante, jamais de plantage). Fil serveur uniquement.
 */
public final class Equipement {
    private Equipement() {
    }

    /**
     * Donne le kit au joueur (inventaire suppose vide : passe par le {@link Coffre} avant). Chaque objet est marque
     * {@link #marquer(ItemStack, String)}. Rend vrai si au moins l'arme principale (ou, a defaut, un objet) a ete donnee.
     * <p>Disposition : armure d'abord (emplacements vanilla, puis Curios, puis inventaire), arme principale en case 1,
     * secondaire en case 2, melee en case 3, grenades puis soins dans les cases suivantes ; la case 1 est selectionnee.
     * Les ids absents des packs installes sont ignores avec un avertissement (journal).
     *
     * @param matchId id de la partie ({@link Partie#id()})
     */
    public static boolean donner(ServerPlayer joueur, Kit kit, String matchId) {
        Services.Moteur m = Services.MOTEUR;   // moteur/kits/Distribution (serveur)
        return m != null && m.equipementDonner(joueur, kit, matchId);
    }

    /**
     * Construit les piles du kit sans les donner (aperçu de l'ecran de choix, killcam...) : principale, secondaire,
     * melee, grenades, soins, armure, dans cet ordre ; les ids inconnus sont omis. Piles non marquees.
     */
    public static java.util.List<ItemStack> piles(Kit kit) {
        Services.Moteur m = Services.MOTEUR;
        return m != null ? m.equipementPiles(kit) : java.util.List.of();
    }

    /** Retire du joueur tout l'equipement prete (inventaire et Curios) ; rend le nombre de piles retirees. */
    public static int retirer(ServerPlayer joueur) {
        int n = purger(joueur.getInventory());
        Services.Moteur m = Services.MOTEUR;   // moteur/compat/Compat (serveur)
        if (m != null) n += m.equipementPurgerCurios(joueur, Equipement::estPrete);
        if (n > 0) joueur.inventoryMenu.broadcastChanges();
        return n;
    }

    /** Marque une pile comme pretee pour ce match (sur place) et la rend. */
    public static ItemStack marquer(ItemStack pile, String matchId) {
        if (!pile.isEmpty()) pile.getOrCreateTag().putString(MatchApi.NBT_PRET, matchId);
        return pile;
    }

    /** Vrai si la pile est un objet prete de match. */
    public static boolean estPrete(ItemStack pile) {
        CompoundTag t = pile.getTag();
        return t != null && t.contains(MatchApi.NBT_PRET);
    }

    /** Id du match qui a prete la pile, ou null. */
    @Nullable
    public static String matchDe(ItemStack pile) {
        CompoundTag t = pile.getTag();
        return t != null && t.contains(MatchApi.NBT_PRET) ? t.getString(MatchApi.NBT_PRET) : null;
    }

    /** Vide de l'inventaire toutes les piles pretees (armure et main secondaire comprises) ; rend leur nombre. */
    public static int purger(Inventory inv) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (estPrete(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
                n++;
            }
        }
        return n;
    }
}
