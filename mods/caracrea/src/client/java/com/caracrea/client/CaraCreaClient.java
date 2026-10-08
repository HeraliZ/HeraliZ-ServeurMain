package com.caracrea.client;

import com.caracrea.CaraCreaConfig;
import com.caracrea.character.BodyType;
import com.caracrea.client.voice.VoiceClient;
import com.caracrea.network.CaraCreaNetwork;
import com.caracrea.voice.VoiceActivity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/** Client wiring: the 3D body, key binding, per-tick animation, cleanup on disconnect. */
public final class CaraCreaClient {
    /** Opens the creator (if the server allows it). Unbound by default to avoid conflicts; see the controls menu. */
    public static final KeyMapping OPEN_CREATOR = new KeyMapping("key.caracrea.open",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.caracrea");

    private CaraCreaClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(CaraCreaClient::registerKeys);
        modBus.addListener(CaraCreaClient::registerLayers);
        modBus.addListener(CaraCreaClient::addLayers);
        modBus.addListener(com.caracrea.client.voice.VoiceHud::registerOverlay);
        MinecraftForge.EVENT_BUS.addListener(com.caracrea.client.voice.VoiceHud::renderIcons);
        MinecraftForge.EVENT_BUS.addListener(CaraCreaClient::clientTick);
        MinecraftForge.EVENT_BUS.addListener(CaraCreaClient::loggingOut);
        MinecraftForge.EVENT_BUS.addListener(CaraCreaClient::hideCubicBody);
        VoiceActivity.localPlayer = () -> {
            var player = Minecraft.getInstance().player;
            return player == null ? null : player.getUUID();
        };
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CREATOR);
        VoiceClient.keys().forEach(event::register);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DetailModel.DEFAULT, () -> DetailModel.createLayer(BodyType.MALE));
        event.registerLayerDefinition(DetailModel.SLIM, () -> DetailModel.createLayer(BodyType.FEMALE));
    }

    /** Adds the 3D body layer to both player renderers (wide and slim arms). */
    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                boolean slim = "slim".equals(skin);
                DetailModel model = new DetailModel(event.getEntityModels().bakeLayer(slim ? DetailModel.SLIM : DetailModel.DEFAULT),
                        slim ? BodyType.FEMALE : BodyType.MALE);
                renderer.addLayer(new DetailLayer(renderer, model));
            }
        }
    }

    /**
     * The vanilla cubes and outer layers are hidden so the 3D body (head
     * included, with its high-definition face) takes their place. The
     * vanilla renderer makes them visible again before every render, so
     * first-person hands and other uses of the model are unaffected.
     */
    private static void hideCubicBody(RenderPlayerEvent.Pre event) {
        if (!CaraCreaConfig.CLIENT.detailedBody.get() || event.getEntity().isSpectator()) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.head.visible = false;
        model.hat.visible = false;
        model.body.visible = false;
        model.jacket.visible = false;
        model.rightArm.visible = false;
        model.leftArm.visible = false;
        model.rightSleeve.visible = false;
        model.leftSleeve.visible = false;
        model.rightLeg.visible = false;
        model.leftLeg.visible = false;
        model.rightPants.visible = false;
        model.leftPants.visible = false;
    }

    private static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_CREATOR.consumeClick()) {
            if (mc.player != null) {
                CaraCreaNetwork.CHANNEL.sendToServer(new CaraCreaNetwork.RequestCreator());
            }
        }
        VoiceClient.tick();
        if (mc.level != null) {
            FaceAnimator.tick();
        }
    }

    private static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FaceAnimator.clear();
        CompositeSkins.clear();
        ClientAppearances.clear();
        VoiceActivity.clear();
        VoiceClient.disconnect();
    }
}
