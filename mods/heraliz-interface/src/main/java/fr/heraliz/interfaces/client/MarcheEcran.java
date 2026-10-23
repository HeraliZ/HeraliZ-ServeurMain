package fr.heraliz.interfaces.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.interfaces.HeralizInterface;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Marcher avec un écran ouvert (23/10, demande du propriétaire, comme dans DayZ) : en survie et en aventure, avec
 * l'inventaire (écran de Curios), la fouille d'un conteneur de loot ou d'un coffre T7, d'un cadavre, d'un joueur à
 * terre, ou la carte du monde ouverts, les touches Avancer / Reculer / Gauche / Droite restent lues : on marche, sans
 * courir, sauter ni s'accroupir. Jamais quand un champ de texte a la main (recherche). Minecraft relâche toutes les
 * touches à l'ouverture d'un écran et ne les relit plus : on relit l'état réel du clavier à chaque tick, avant le
 * déplacement du joueur. Rien côté serveur : pour lui, c'est une marche normale (Spartan n'a pas de contrôle
 * « mouvement avec un écran ouvert »).
 */
@Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT)
public final class MarcheEcran {
    private MarcheEcran() {
    }

    /** Écrans où l'on peut marcher, par nom de classe (ils viennent d'autres mods : aucune dépendance). */
    private static final Set<String> ECRANS = Set.of(
            "top.theillusivec4.curios.client.gui.CuriosScreen",      // inventaire (écran de Curios)
            "top.theillusivec4.curios.client.gui.CuriosScreenV2",
            "fr.heraliz.loot.client.EcranConteneur",                 // conteneurs de loot T1 à T6, salles à carte
            "fr.heraliz.loot.client.CoffreEcran",                    // coffres T7
            "fr.heraliz.corps.client.CorpsEcran",                    // cadavres
            "fr.heraliz.reanimation.client.SearchScreen",            // fouille d'un joueur à terre
            "fr.heraliz.carte.client.EcranCarte");                   // carte du monde

    private static boolean marchait;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Screen s = mc.screen;
        if (p == null || s == null || !permis(s) || p.isCreative() || p.isSpectator()) {
            if (marchait && s != null) relacher(mc);
            marchait = false;
            return;
        }
        marchait = true;
        long fenetre = mc.getWindow().getWindow();
        lire(fenetre, mc.options.keyUp);
        lire(fenetre, mc.options.keyDown);
        lire(fenetre, mc.options.keyLeft);
        lire(fenetre, mc.options.keyRight);
        KeyMapping.set(mc.options.keySprint.getKey(), false);
        KeyMapping.set(mc.options.keyJump.getKey(), false);
        KeyMapping.set(mc.options.keyShift.getKey(), false);
        if (p.isSprinting()) p.setSprinting(false);
    }

    private static boolean permis(Screen s) {
        if (!(s instanceof InventoryScreen) && !ECRANS.contains(s.getClass().getName())) return false;
        return !(s.getFocused() instanceof EditBox b && b.isFocused());
    }

    private static void lire(long fenetre, KeyMapping k) {
        InputConstants.Key t = k.getKey();
        if (t.getType() != InputConstants.Type.KEYSYM || t.getValue() == InputConstants.UNKNOWN.getValue()) return;
        KeyMapping.set(t, InputConstants.isKeyDown(fenetre, t.getValue()));
    }

    /** Passage à un écran où l'on ne marche pas (champ de texte, autre écran) : plus aucune touche tenue. */
    private static void relacher(Minecraft mc) {
        for (KeyMapping k : new KeyMapping[]{mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight}) {
            if (k.getKey().getType() == InputConstants.Type.KEYSYM) KeyMapping.set(k.getKey(), false);
        }
    }
}
