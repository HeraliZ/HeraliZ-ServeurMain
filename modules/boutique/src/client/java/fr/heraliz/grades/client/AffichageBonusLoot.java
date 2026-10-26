package fr.heraliz.grades.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

/**
 * Effet « Bonus de loot » dans l'inventaire : « Bonus de loot +10 % » au lieu du niveau en chiffres romains, puis le temps
 * qui reste (comme les autres effets).
 */
final class AffichageBonusLoot implements IClientMobEffectExtensions {
    @Override
    public boolean renderInventoryText(MobEffectInstance effet, EffectRenderingInventoryScreen<?> ecran, GuiGraphics g, int x, int y, int blitOffset) {
        Font f = Minecraft.getInstance().font;
        Component nom = Component.translatable("effect.heraliz_grades.bonus_loot.niveau", String.valueOf(effet.getAmplifier() + 1));
        g.drawString(f, nom, x + 28, y + 6, 0xFFFFFF);
        g.drawString(f, MobEffectUtil.formatDuration(effet, 1.0F), x + 28, y + 16, 0x7F7F7F);
        return true;
    }
}
