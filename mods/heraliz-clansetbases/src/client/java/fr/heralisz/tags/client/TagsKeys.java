package fr.heralisz.tags.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.client.gui.WheelScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Touche J (configurable, clavier ou bouton de souris) : appui bref (&lt; 200 ms) = taguer avec le quartier haut ;
 * maintenue = roue radiale (ouverte au franchissement du seuil, fermee au relachement : l'ecran recoit keyReleased /
 * mouseReleased, et ce tick sert de filet de securite si l'evenement s'est perdu).
 * <p>Le maintien est mesure par sondage a chaque tick ; un appui-relachement entier entre deux ticks (&lt; 50 ms)
 * echapperait a {@code isDown()}, il est rattrape par le compteur de clics ({@code consumeClick}).
 */
@Mod.EventBusSubscriber(modid = HeraliszTags.MODID, value = Dist.CLIENT)
public final class TagsKeys {
    private TagsKeys() {
    }

    /** Seuil entre appui bref et maintien (ms). */
    public static final long SEUIL_MAINTIEN_MS = 200;

    public static final KeyMapping TAG = new KeyMapping("key.heralisz_tags.tag", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.heralisz_tags");

    private static long appuyeDepuis = -1;
    private static boolean maintienSignale;

    /** Code GLFW de la touche (GLFW_KEY_UNKNOWN si elle n'est pas une touche clavier). */
    public static int code() {
        InputConstants.Key k = TAG.getKey();
        return k.getType() == InputConstants.Type.KEYSYM ? k.getValue() : GLFW.GLFW_KEY_UNKNOWN;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            appuyeDepuis = -1;
            drainer();
            return;
        }
        // hors de nos ecrans, la touche ne compte que sans ecran ouvert (chat, inventaire...)
        boolean notre = mc.screen instanceof fr.heralisz.tags.client.gui.EcranTags;
        boolean down = (mc.screen == null && TAG.isDown()) || (notre && isPhysicallyDown(mc));
        long now = System.currentTimeMillis();
        if (down) {
            // les clics comptes pendant le maintien (repetition clavier) ne doivent pas declencher un bref ensuite
            drainer();
            if (appuyeDepuis < 0) {
                appuyeDepuis = now;
                maintienSignale = false;
            } else if (!maintienSignale && now - appuyeDepuis >= SEUIL_MAINTIEN_MS) {
                maintienSignale = true;
                maintenu();
            }
        } else if (appuyeDepuis >= 0) {
            long duree = now - appuyeDepuis;
            appuyeDepuis = -1;
            drainer();
            if (duree < SEUIL_MAINTIEN_MS && !maintienSignale) bref();
            else relache();
        } else if (TAG.consumeClick()) {
            // appui-relachement entier entre deux ticks : jamais vu par isDown(), mais compte par le clic
            drainer();
            if (mc.screen == null) bref();
        }
    }

    /** Vide le compteur de clics de la touche. */
    private static void drainer() {
        while (TAG.consumeClick()) {
            // rien : on consomme
        }
    }

    /**
     * La touche reste « enfoncee » meme quand un de nos ecrans a le focus (la roue est un ecran) : lue directement
     * dans GLFW, clavier ou bouton de souris. Une liaison par code materiel (scancode) n'est pas interrogeable : on la
     * considere maintenue, la roue se ferme alors par keyReleased.
     */
    private static boolean isPhysicallyDown(Minecraft mc) {
        InputConstants.Key k = TAG.getKey();
        long fenetre = mc.getWindow().getWindow();
        return switch (k.getType()) {
            case KEYSYM -> k.getValue() != GLFW.GLFW_KEY_UNKNOWN && InputConstants.isKeyDown(fenetre, k.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(fenetre, k.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> true;
        };
    }

    /** Appui bref : taguer avec le quartier haut, sans ouvrir la roue. */
    private static void bref() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        TagPlacer.taguer(ClientTags.roue(0));
    }

    /** Maintien franchi : ouvrir la roue (seulement sans autre ecran ouvert). */
    private static void maintenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        mc.setScreen(new WheelScreen());
    }

    /** Relachement apres maintien : la roue valide le quartier sous la souris et se ferme (filet de securite). */
    private static void relache() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WheelScreen w) w.valider();
    }
}
