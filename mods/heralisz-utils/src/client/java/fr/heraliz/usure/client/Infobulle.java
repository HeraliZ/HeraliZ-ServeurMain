package fr.heraliz.usure.client;

import fr.heraliz.usure.Reparation;
import fr.heraliz.usure.Usure;
import fr.heraliz.usure.regles.Etat;
import fr.heraliz.usure.regles.Profil;
import fr.heraliz.usure.regles.Regles;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Lignes d'infobulle des armes : « Etat : Bon · 72 % » (couleur de l'etat, etat maximal s'il a baisse), ruinee en
 * rouge ; avec Maj : usure par tir, tirs restants avant la ruine, reparations.
 */
public final class Infobulle {
    private Infobulle() {
    }

    static void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        String id = Usure.gunId(s);
        if (id == null) return;
        Regles r = ReglesClient.get();
        if (!r.actif) return;
        Profil p = r.profil(id);
        if (!p.suivie) return;
        List<Component> l = e.getToolTip();
        int etat = Usure.etat(s, p), max = Usure.max(s);
        Etat st = Etat.de(etat, r);
        MutableComponent ligne = Component.translatable("heraliz_usure.infobulle.etat",
                Component.translatable(st.cleTraduction()).withStyle(x -> x.withColor(st.couleur)),
                Component.literal(Reparation.pct(etat)).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY);
        if (max < Usure.PLEIN) ligne.append(Component.translatable("heraliz_usure.infobulle.max", Reparation.pct(max)).withStyle(ChatFormatting.DARK_GRAY));
        l.add(ligne);
        if (etat <= 0) l.add(Component.translatable("heraliz_usure.infobulle.ruinee").withStyle(ChatFormatting.RED));
        if (Screen.hasShiftDown()) {
            int tirs = p.usureMilliemes <= 0 ? -1 : Math.round(etat / p.usureMilliemes);
            l.add(Component.translatable("heraliz_usure.infobulle.detail_usure", Component.translatable(p.categorie.cleTraduction()),
                    Regles.texte(p.usurePct), tirs < 0 ? "-" : String.valueOf(tirs)).withStyle(ChatFormatting.DARK_GRAY));
            l.add(Component.translatable("heraliz_usure.infobulle.detail_reparations", Usure.reparations(s)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
