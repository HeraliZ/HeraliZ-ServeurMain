package fr.heraliz.emote.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.emote.HeralizEmote;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class HeralizKeys {
    public static final String CATEGORY = "key.categories.heraliz_emote";

    /**
     * In game only, and never reported as a conflict: the Reverse sound key shares Left Ctrl with "Sprint" (it is only
     * read while borrowing).
     */
    private static final IKeyConflictContext SHARED = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return false;
        }
    };

    /**
     * Touche « Copier l'emote » : SANS touche par défaut (le joueur peut en choisir une dans Options > Commandes).
     * Maj + clic droit sur un joueur qui danse copie son emote sans elle ({@link BorrowController#onInteraction}).
     * <p>Avant la 2.7.1, elle valait Maj + clic droit. Avec Forge, une touche à modificateur posée sur un bouton prend
     * TOUT ce bouton tant que le modificateur est enfoncé ({@code KeyMappingLookup.getAll} ne rend alors qu'elle) :
     * accroupi, « Utiliser / Poser », la visée de TaCZ, les soins, la réanimation et la fouille ne recevaient plus le
     * clic droit. {@link #retirerTouchePiege} retire ce réglage s'il est resté dans options.txt.</p>
     */
    public static final KeyMapping BORROW = new KeyMapping("key.heraliz_emote.borrow", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, CATEGORY);
    public static final KeyMapping REVERSE_SOUND = new KeyMapping("key.heraliz_emote.reverse_sound", SHARED, KeyModifier.NONE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);
    /**
     * 0 (à on an AZERTY keyboard): H is already taken on HeraliZ (TaCZ inspect, voice chat icons, TCTcore). The emote
     * wheel of Emotecraft moves from B to ² ({@code mixin.EmotecraftKeysMixin}).
     */
    public static final KeyMapping OPEN_MENU = new KeyMapping("key.heraliz_emote.open_menu", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_0, CATEGORY);

    private HeralizKeys() {
    }

    static void register(RegisterKeyMappingsEvent event) {
        event.register(BORROW);
        event.register(REVERSE_SOUND);
        event.register(OPEN_MENU);
    }

    /** The key itself is held (whatever the modifier), read straight from the window. */
    static boolean keyHeld(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key.equals(InputConstants.UNKNOWN)) return false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, key.getValue());
    }

    /** The modifier of the mapping (Shift, Ctrl, Alt) is held, or it has none. */
    static boolean modifierHeld(KeyMapping mapping) {
        KeyModifier modifier = mapping.getKeyModifier();
        return modifier == KeyModifier.NONE || modifier.isActive(KeyConflictContext.IN_GAME);
    }

    /** Mise par le joueur sur le même bouton que « Utiliser / Poser » (une touche sans bouton ne compte pas). */
    static boolean sharesUseKey(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        return !key.equals(InputConstants.UNKNOWN) && key.equals(Minecraft.getInstance().options.keyUse.getKey());
    }

    /**
     * Retire (« sans touche ») la touche Copier si elle combine un modificateur (Maj, Ctrl, Alt) et le clic droit ou
     * gauche, ou le bouton d'« Utiliser » ou d'« Attaquer » : réglage par défaut avant la 2.7.1, gardé dans options.txt
     * chez les joueurs. Ce réglage volerait le clic à Minecraft et aux autres mods dès que le modificateur est enfoncé
     * (voir {@link #BORROW}) ; Maj + clic droit sur un joueur qui danse marche sans lui. Appelée à chaque tick de jeu
     * sans écran ouvert : une comparaison, et un enregistrement des options seulement quand il faut retirer.
     *
     * @return vrai si la touche a été retirée
     */
    static boolean retirerTouchePiege(Minecraft mc) {
        KeyModifier modifier = BORROW.getKeyModifier();
        InputConstants.Key key = BORROW.getKey();
        if (modifier == KeyModifier.NONE || key.equals(InputConstants.UNKNOWN)) return false;
        boolean clic = key.getType() == InputConstants.Type.MOUSE
                && (key.getValue() == GLFW.GLFW_MOUSE_BUTTON_RIGHT || key.getValue() == GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (!clic && !key.equals(mc.options.keyUse.getKey()) && !key.equals(mc.options.keyAttack.getKey())) return false;
        BORROW.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
        BORROW.setDown(false);
        KeyMapping.resetMapping();
        mc.options.save();
        HeralizEmote.LOGGER.info("Touche « Copier l'emote » ({} + {}) retirée : elle bloquait ce bouton", modifier, key.getName());
        return true;
    }
}
