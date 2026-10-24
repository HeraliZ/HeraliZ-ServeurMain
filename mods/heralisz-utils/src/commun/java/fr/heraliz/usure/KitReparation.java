package fr.heraliz.usure;

import fr.heraliz.usure.regles.Regles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Kit de reparation (3 niveaux : fortune, standard, armurier). S'utilise dans l'inventaire comme on range un objet dans
 * un sac : prendre le kit, puis clic droit sur l'arme. Le serveur repare ({@link Reparation}) ; le client ne fait que
 * consommer le clic. Les utilisations restantes s'affichent en barre, comme une durabilite. Aucun kit n'est mis dans
 * le loot T1 a T7 (outils/loot_armes.py). Ils reparent aussi les pieces de HeraliZ Armurerie (meme gain, jusqu'a 100 %).
 * <p>Jar fusionne (docs/METHODE_CLIENT_SERVEUR.md §5.3) : ici, l'objet, sa barre et son infobulle, et le clic consomme ;
 * la reparation est dans {@code KitReparationServeur}, cree par l'usine {@link Contenu#KIT_USINE}.
 */
public class KitReparation extends Item {
    /** NBT : utilisations deja faites. */
    public static final String UTILISEES = "HzKitUtilisees";

    public final int niveau;

    /** Arguments du constructeur, pour l'usine Contenu.KIT_USINE (version serveur : KitReparationServeur). */
    public record Params(int niveau, Rarity rarete) {
    }

    public KitReparation(int niveau, Rarity rarete) {
        super(new Item.Properties().stacksTo(1).rarity(rarete));
        this.niveau = niveau;
    }

    /** Pour l'usine (Contenu.KIT_USINE). */
    public KitReparation(Params a) {
        this(a.niveau(), a.rarete());
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack kit, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        ItemStack cible = slot.getItem();
        if (!reparable(cible) || !slot.allowModification(player)) return false;
        // reparation (joueur du serveur) : KitReparationServeur ; ici, le clic est seulement consomme
        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack kit, ItemStack autre, Slot slot, ClickAction action, Player player, SlotAccess porte) {
        // arme tenue au curseur, clic droit sur le kit : meme reparation (l'arme est l'objet porte)
        if (action != ClickAction.SECONDARY || !reparable(autre) || !slot.allowModification(player)) return false;
        // reparation (joueur du serveur) : KitReparationServeur ; ici, le clic est seulement consomme
        return true;
    }

    /** Arme suivie par l'usure, ou piece de HeraliZ Armurerie (armure, masque, combinaison...) qui a une durabilite. */
    public static boolean reparable(ItemStack st) {
        return Usure.gunId(st) != null || estArmure(st);
    }

    /** Piece de HeraliZ Armurerie avec une durabilite (lue par son identifiant : aucune dependance vers l'Armurerie). */
    public static boolean estArmure(ItemStack st) {
        if (st.isEmpty() || !st.isDamageableItem()) return false;
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(st.getItem());
        return id != null && ARMURERIE.equals(id.getNamespace());
    }

    static final String ARMURERIE = "heraliz_armory";

    private static Regles regles() {
        return HeralizUsure.reglesClient();
    }

    private int restantes(ItemStack kit) {
        int total = regles().kitUtilisations[niveau];
        return Math.max(0, total - (kit.hasTag() ? kit.getTag().getInt(UTILISEES) : 0));
    }

    @Override
    public boolean isBarVisible(ItemStack kit) {
        return kit.hasTag() && kit.getTag().getInt(UTILISEES) > 0;
    }

    @Override
    public int getBarWidth(ItemStack kit) {
        int total = Math.max(1, regles().kitUtilisations[niveau]);
        return Math.round(13f * restantes(kit) / total);
    }

    @Override
    public int getBarColor(ItemStack kit) {
        int total = Math.max(1, regles().kitUtilisations[niveau]);
        return Mth.hsvToRgb(Math.max(0f, (float) restantes(kit) / total) / 3f, 1f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack kit, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
        Regles r = regles();
        lignes.add(Component.translatable("heraliz_usure.kit.effet", Regles.texte(r.kitGain[niveau]), Regles.texte(r.kitPlafond[niveau]))
                .withStyle(ChatFormatting.GRAY));
        lignes.add(Component.translatable("heraliz_usure.kit.utilisations", restantes(kit), r.kitUtilisations[niveau]).withStyle(ChatFormatting.GRAY));
        lignes.add(Component.translatable("heraliz_usure.kit.armures", Regles.texte(r.kitGain[niveau])).withStyle(ChatFormatting.GRAY));
        if (!r.kitRuinee[niveau]) lignes.add(Component.translatable("heraliz_usure.kit.pas_ruinee").withStyle(ChatFormatting.DARK_GRAY));
        lignes.add(Component.translatable("heraliz_usure.kit.mode_emploi").withStyle(ChatFormatting.DARK_GRAY));
    }
}
