package fr.heraliz.interfaces.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.interfaces.HeralizInterface;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Bouger avec un écran ouvert (demande du propriétaire, comme dans DayZ) : en survie et en aventure, avec l'inventaire
 * (écran de Curios), la fouille d'un conteneur de loot ou d'un coffre T7, d'un cadavre, d'un joueur à terre, ou la carte
 * du monde ouverts, on avance, recule, va à gauche et à droite, on court et on saute ; la tête ne bouge pas (la souris
 * est libre pour l'écran). S'allonger et se relever : touche « Ramper » lue par le mod tactique ({@link #actif()}, par
 * réflexion). Jamais quand un champ de texte a la main (recherche). Les conteneurs se ferment seuls quand on s'éloigne
 * (règle de chaque menu, côté serveur).
 *
 * <p>Pourquoi la 1.9.0 ne marchait pas : Forge rattache les touches de déplacement au contexte « en jeu » (aucun écran
 * ouvert) : {@code isDown()} y rend toujours faux dès qu'un écran est ouvert, quoi qu'on y écrive. On ne passe donc plus
 * par les touches : on lit l'état réel du clavier et on remplit directement l'entrée de déplacement du joueur, au moment
 * où Minecraft la calcule ({@link MovementInputUpdateEvent}, en premier : les autres mods la retouchent ensuite). Rien
 * côté serveur : pour lui, c'est une marche normale.
 */
@Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT)
public final class MarcheEcran {
    private MarcheEcran() {
    }

    /** Écrans où l'on peut bouger, par nom de classe (ils viennent d'autres mods : aucune dépendance). */
    private static final Set<String> ECRANS = Set.of(
            "top.theillusivec4.curios.client.gui.CuriosScreen",      // inventaire (écran de Curios)
            "top.theillusivec4.curios.client.gui.CuriosScreenV2",
            "fr.heraliz.loot.client.EcranConteneur",                 // conteneurs de loot T1 à T6, salles à carte
            "fr.heraliz.loot.client.CoffreEcran",                    // coffres T7
            "fr.heraliz.corps.client.CorpsEcran",                    // cadavres
            "fr.heraliz.reanimation.client.SearchScreen",            // fouille d'un joueur à terre
            "fr.heraliz.carte.client.EcranCarte");                   // carte du monde

    /** Course voulue pendant l'écran (reprise de la course en cours à l'ouverture ; touche « Basculer » comprise). */
    private static boolean courseVoulue;
    private static boolean sprintAvant;
    private static Screen ecranVu;

    /** Vrai tant qu'on peut bouger avec l'écran ouvert (lu par réflexion par le mod tactique : ramper). */
    public static boolean actif() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Screen s = mc.screen;
        return p != null && s != null && !p.isCreative() && !p.isSpectator() && permis(s);
    }

    /** Course : suivie à chaque tick, avant le déplacement du joueur. */
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (!actif()) {
            ecranVu = null;
            return;
        }
        LocalPlayer p = mc.player;
        boolean sprint = enfoncee(mc, mc.options.keySprint);
        if (ecranVu != mc.screen) {
            ecranVu = mc.screen;
            courseVoulue = p.isSprinting();
            sprintAvant = sprint;
        }
        if (Boolean.TRUE.equals(mc.options.toggleSprint().get())) {
            if (sprint && !sprintAvant) courseVoulue = !courseVoulue;
        } else {
            courseVoulue = sprint || (courseVoulue && p.isSprinting());
        }
        sprintAvant = sprint;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void entree(MovementInputUpdateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player || !actif()) return;
        LocalPlayer p = mc.player;
        Input in = e.getInput();
        in.up = enfoncee(mc, mc.options.keyUp);
        in.down = enfoncee(mc, mc.options.keyDown);
        in.left = enfoncee(mc, mc.options.keyLeft);
        in.right = enfoncee(mc, mc.options.keyRight);
        in.jumping = enfoncee(mc, mc.options.keyJump);
        in.shiftKeyDown = false;
        float avant = in.up == in.down ? 0f : (in.up ? 1f : -1f);
        float cote = in.left == in.right ? 0f : (in.left ? 1f : -1f);
        if (p.isMovingSlowly()) { // allongé (ramper), comme Minecraft
            float f = Mth.clamp(0.3F + EnchantmentHelper.getSneakingSpeedBonus(p), 0f, 1f);
            avant *= f;
            cote *= f;
        }
        in.forwardImpulse = avant;
        in.leftImpulse = cote;
        boolean peutCourir = avant > 0.8f && courseVoulue && !p.isMovingSlowly() && !p.isUsingItem()
                && !p.isPassenger() && (p.getFoodData().getFoodLevel() > 6 || p.getAbilities().mayfly);
        if (peutCourir && !p.isSprinting()) p.setSprinting(true);
        else if (!peutCourir && p.isSprinting()) p.setSprinting(false);
    }

    private static boolean permis(Screen s) {
        if (!(s instanceof InventoryScreen) && !ECRANS.contains(s.getClass().getName())) return false;
        return !(s.getFocused() instanceof EditBox b && b.isFocused());
    }

    /** État réel de la touche (clavier ou bouton de souris), sans le contexte « en jeu » de Forge. */
    static boolean enfoncee(Minecraft mc, KeyMapping k) {
        InputConstants.Key t = k.getKey();
        long fenetre = mc.getWindow().getWindow();
        if (t.getType() == InputConstants.Type.KEYSYM) {
            return t.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(fenetre, t.getValue());
        }
        if (t.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(fenetre, t.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }
}
