package fr.heraliz.clashwar.moteur.compat;

import fr.heraliz.clashwar.HeralizClashWar;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * Facade des mods optionnels utilises par le moteur : Curios (API, classe {@link CuriosCompat} chargee seulement si le
 * mod est present), etat a terre (noyau, {@code ATerre}), heralisz_tactical ({@link Tactical}), TaCZ
 * ({@link Tacz}), HeraliZ Armurerie ({@link Armures}). Un mod absent = fonction ignoree, jamais de plantage.
 * Fil serveur.
 */
public final class Compat {
    private Compat() {
    }

    private static Boolean curios;

    /** Vrai si Curios est charge. */
    public static boolean curios() {
        if (curios == null) curios = ModList.get() != null && ModList.get().isLoaded("curios");
        return curios;
    }

    /** Instantane Curios ({@code saveInventory(false)}), ou null si Curios est absent ou en erreur. */
    @Nullable
    public static ListTag sauverCurios(ServerPlayer p) {
        if (!curios()) return null;
        try {
            return CuriosCompat.sauver(p);
        } catch (Throwable t) {
            HeralizClashWar.LOG.error("[matchs] Curios : sauvegarde de {} impossible", p.getGameProfile().getName(), t);
            return null;
        }
    }

    /** Vide tous les emplacements Curios (cosmetiques compris) ; rend le nombre de piles retirees. */
    public static int viderCurios(ServerPlayer p) {
        if (!curios()) return 0;
        try {
            return CuriosCompat.vider(p);
        } catch (Throwable t) {
            HeralizClashWar.LOG.error("[matchs] Curios : vidage de {} impossible", p.getGameProfile().getName(), t);
            return 0;
        }
    }

    /** Vide puis recharge les Curios depuis un instantane. Rend faux en cas d'erreur (ou Curios absent). */
    public static boolean chargerCurios(ServerPlayer p, ListTag t) {
        if (!curios()) return false;
        try {
            CuriosCompat.charger(p, t);
            return true;
        } catch (Throwable ex) {
            HeralizClashWar.LOG.error("[matchs] Curios : rechargement de {} impossible", p.getGameProfile().getName(), ex);
            return false;
        }
    }

    /** Pose la pile dans un emplacement Curios libre qui l'accepte ; rend vrai si c'est fait. */
    public static boolean equiperCurio(ServerPlayer p, ItemStack pile) {
        if (!curios() || pile.isEmpty()) return false;
        try {
            return CuriosCompat.equiper(p, pile);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Retire des Curios les piles qui verifient le filtre ; rend leur nombre. */
    public static int purgerCurios(ServerPlayer p, Predicate<ItemStack> filtre) {
        if (!curios()) return 0;
        try {
            return CuriosCompat.purger(p, filtre);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Toutes les piles non vides des Curios (banc d'essai), liste vide sans Curios. */
    public static List<ItemStack> piecesCurios(ServerPlayer p) {
        if (!curios()) return List.of();
        try {
            return CuriosCompat.pieces(p);
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** Pose une pile dans l'emplacement {@code slot} d'indice {@code index} sans controle (banc d'essai). */
    public static boolean poserCurio(ServerPlayer p, String slot, int index, ItemStack pile) {
        if (!curios()) return false;
        try {
            return CuriosCompat.poser(p, slot, index, pile);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Identifiants des emplacements Curios du joueur (banc d'essai), vide sans Curios. */
    public static List<String> emplacementsCurios(ServerPlayer p) {
        if (!curios()) return List.of();
        try {
            return CuriosCompat.emplacements(p);
        } catch (Throwable t) {
            return List.of();
        }
    }
}
