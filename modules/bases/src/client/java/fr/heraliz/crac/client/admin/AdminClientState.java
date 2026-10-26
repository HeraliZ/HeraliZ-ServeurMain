package fr.heraliz.crac.client.admin;

import fr.heraliz.crac.network.S2CAdminData;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AdminClientState {
    public static boolean authed = false;
    public static S2CAdminData data = null;

    private AdminClientState() {
    }

    public static void onData(S2CAdminData msg) {
        Minecraft mc = Minecraft.getInstance();
        if (!msg.ok()) {
            authed = false;
            data = null;
            if (mc.screen instanceof AdminPasswordScreen pw) {
                pw.onWrongPassword();
            } else if (mc.screen instanceof AdminPanelScreen) {
                // session ended on the server (password changed, operator level lost)
                mc.setScreen(null);
            }
        } else {
            authed = true;
            data = msg;
            if (mc.screen instanceof AdminPanelScreen panel) {
                panel.onDataRefreshed();
            } else if (mc.screen == null || mc.screen instanceof AdminPasswordScreen) {
                mc.setScreen(new AdminPanelScreen());
            }
            // any other screen (a confirmation over the panel, chat...) keeps focus; the panel reads the new data later
        }
    }

    public static void reset() {
        authed = false;
        data = null;
    }
}
