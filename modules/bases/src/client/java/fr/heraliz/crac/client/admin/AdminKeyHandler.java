package fr.heraliz.crac.client.admin;

import fr.heraliz.crac.network.C2SAdminAction;
import fr.heraliz.crac.network.NetworkHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public final class AdminKeyHandler {
    // HeraliZ: no default key (² opens the emote wheel of HeraliZ Emote; the console is off by default anyway)
    public static final KeyMapping KEY_CONSOLE = new KeyMapping("key.crac.admin_console", com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), "key.categories.crac");

    private AdminKeyHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase == Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                while (KEY_CONSOLE.consumeClick()) {
                    if (AdminClientState.authed) {
                        NetworkHandler.CHANNEL.sendToServer(C2SAdminAction.of(0));
                    } else {
                        mc.setScreen(new AdminPasswordScreen());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(LoggingOut event) {
        AdminClientState.reset();
    }
}
