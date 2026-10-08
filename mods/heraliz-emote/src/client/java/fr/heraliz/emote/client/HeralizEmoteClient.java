package fr.heraliz.emote.client;

import fr.heraliz.emote.HeralizEmote;
import fr.heraliz.emote.client.gui.EmoteBrowserScreen;
import fr.heraliz.emote.client.gui.EmotePreview;
import fr.heraliz.emote.client.render.LayerHooks;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class HeralizEmoteClient {
    private HeralizEmoteClient() {
    }

    public static void init(IEventBus modBus) {
        // Emotecraft reads its emotes folder during common setup, which comes after every mod
        // constructor: the files are in place before it looks.
        EmoteInstaller.install();

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HeralizConfig.SPEC);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new HeralizConfigScreen(parent)));

        modBus.addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent.class, HeralizKeys::register);
        modBus.addListener(EventPriority.NORMAL, false, EntityRenderersEvent.AddLayers.class, LayerHooks::addLayers);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, event -> {
            if (event.phase != TickEvent.Phase.END) return;
            BorrowController.tick();
            EmoteParticles.tick();
            EmoteSounds.tick();
            EmoteMotion.tick();
            ClientRules.tick();
            Minecraft mc = Minecraft.getInstance();
            while (HeralizKeys.OPEN_MENU.consumeClick()) {
                if (mc.screen == null && mc.player != null) mc.setScreen(new EmoteBrowserScreen());
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderNameTagEvent.class, EmotePreview::onNameTag);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlaySoundEvent.class, EmoteAudio::onPlaySound);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ScreenEvent.Init.Post.class, EmoteAudio::onScreenInit);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MovementInputUpdateEvent.class, EmoteMotion::onInput);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, InputEvent.InteractionKeyMappingTriggered.class,
                BorrowController::onInteraction);
        HeralizEmote.LOGGER.info("Heraliz Emote ready");
    }
}
