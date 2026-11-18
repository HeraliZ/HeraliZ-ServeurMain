package fr.heraliz.quetes.objet;

import fr.heraliz.quetes.Langue;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Bourse de hérals (18/11, demande du propriétaire ; maquette validée : {@code maquettes/notifications/3_bourse.jpg}) : les
 * hérals gagnés sur la carte (aujourd'hui : le recycleur de terrain) ne vont plus sur le compte, ils vont dans une bourse,
 * un objet qu'on peut perdre (mort, vol) et qu'on revend à Mila, au spawn, pour ce qu'elle contient. Les gains s'ajoutent
 * à la bourse qui est déjà là (3 puis 60 : une bourse de 63) ; une bourse posée sur une autre s'y vide.
 *
 * <p>Étiquette {@value #CLE} : {@code Id} (numéro unique) et {@code Herals} (le montant affiché). Ce montant n'est jamais
 * cru seul : la partie serveur ({@code serveur.Bourses}) tient un registre des bourses (numéro → plafond) écrit sur le
 * disque AVANT que la bourse existe ou change dans le monde ; à la revente, Mila paie le plus petit des deux et efface le
 * numéro. Une bourse copiée (bug de duplication) ne paie donc qu'une seule fois, et jamais plus que ce qui a été gagné.
 * Non empilable : chaque bourse a son numéro.
 */
public class Bourse extends Item {
    public static final String CLE = "HeralizBourse";

    /**
     * Fusion d'une bourse dans une autre, décidée par la partie serveur (registre) ; null chez le joueur, où l'écran ne
     * fait que la prévoir (le serveur corrige s'il refuse).
     */
    public interface Fusion {
        boolean fusionner(Player joueur, ItemStack dans, ItemStack versee);
    }

    public static volatile Fusion FUSION;

    public Bourse(Properties p) {
        super(p);
    }

    @Nullable
    private static CompoundTag etiquette(ItemStack s) {
        if (!(s.getItem() instanceof Bourse)) return null;
        CompoundTag t = s.getTag();
        return t != null && t.contains(CLE, Tag.TAG_COMPOUND) ? t.getCompound(CLE) : null;
    }

    /** Hérals affichés (0 : bourse vide, ou sans étiquette). */
    public static long herals(ItemStack s) {
        CompoundTag t = etiquette(s);
        return t == null ? 0 : Math.max(0, t.getLong("Herals"));
    }

    /** Numéro de la bourse (null : sans étiquette, elle ne vaut rien). */
    @Nullable
    public static UUID id(ItemStack s) {
        CompoundTag t = etiquette(s);
        return t != null && t.hasUUID("Id") ? t.getUUID("Id") : null;
    }

    /** Pose le numéro et le montant (partie serveur, APRÈS l'écriture du registre). */
    public static ItemStack marquer(ItemStack s, UUID id, long herals) {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", id);
        t.putLong("Herals", Math.max(0, herals));
        s.getOrCreateTag().put(CLE, t);
        return s;
    }

    /** Change le montant affiché (le numéro reste). */
    public static void montant(ItemStack s, long herals) {
        CompoundTag t = etiquette(s);
        if (t != null) t.putLong("Herals", Math.max(0, herals));
    }

    /** « 1 250 » : espace fine insécable entre les milliers. */
    public static String nombre(long v) {
        String s = Long.toString(Math.max(0, v));
        StringBuilder b = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    /** Montant court pour le coin de la case : « 63 », « 1,2k », « 12k », « 1,5M » (point en anglais). */
    public static String court(long v) {
        if (v < 1000) return Long.toString(Math.max(0, v));
        String sep = Langue.code().startsWith("en") ? "." : ",";
        if (v < 10_000) return dixiemes(v, 1000, sep) + "k";
        if (v < 1_000_000) return (v / 1000) + "k";
        if (v < 10_000_000) return dixiemes(v, 1_000_000, sep) + "M";
        return (v / 1_000_000) + "M";
    }

    private static String dixiemes(long v, long unite, String sep) {
        long d = v * 10 / unite;   // tronqué : jamais plus que ce qu'il y a
        return d % 10 == 0 ? Long.toString(d / 10) : (d / 10) + sep + (d % 10);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
        long h = herals(s);
        if (id(s) == null || h <= 0) {
            lignes.add(Component.translatable("heraliz_quetes.bourse.vide").withStyle(ChatFormatting.GRAY));
            return;
        }
        // maquette validée (18/11) : le montant en or et en gras, puis où la revendre ; pas de ligne « tombe au sol si tu
        // meurs » (choix du propriétaire)
        lignes.add(Component.translatable(h == 1 ? "heraliz_quetes.bourse.montant_un" : "heraliz_quetes.bourse.montant", nombre(h))
                .withStyle(st -> st.withColor(0xFFD678).withBold(true)));
        lignes.add(Component.translatable("heraliz_quetes.bourse.mila").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Une bourse (au curseur) posée sur une autre (dans la case) : elle s'y vide, il n'en reste qu'une. Jamais dans une case
     * où l'on ne peut rien poser (sorties d'une machine).
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack moi, ItemStack autre, Slot slot, ClickAction action, Player joueur, SlotAccess curseur) {
        if (!(autre.getItem() instanceof Bourse) || autre == moi || !slot.allowModification(joueur)) return false;
        UUID a = id(moi), b = id(autre);
        if (a == null || b == null || a.equals(b)) return false;
        if (joueur.level().isClientSide) {
            // prévision de l'écran : le serveur fait la vraie fusion (registre) et corrige s'il la refuse
            montant(moi, herals(moi) + herals(autre));
            curseur.set(ItemStack.EMPTY);
            return true;
        }
        Fusion f = FUSION;
        if (f == null || !f.fusionner(joueur, moi, autre)) return false;
        curseur.set(ItemStack.EMPTY);
        slot.setChanged();
        return true;
    }
}
