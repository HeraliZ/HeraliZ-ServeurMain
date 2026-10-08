package fr.heralisz.utils.deco;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sur un pont de corde, la caméra suit un peu le roulis du tablier (au plus ~2,5 degrés, rien ailleurs). */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, value = Dist.CLIENT)
public final class PontClient {
    private PontClient() {}

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles ev) {
        Player p = Minecraft.getInstance().player;
        if (p == null || PontBlockEntity.CHARGES.isEmpty()) {
            return;
        }
        float partial = (float) ev.getPartialTick();
        for (PontBlockEntity be : PontBlockEntity.CHARGES) {
            if (be.getLevel() != p.level() || be.isRemoved()) {
                continue;
            }
            float s = be.surLePont(p.getX(), p.getY(), p.getZ());
            if (!Float.isNaN(s)) {
                float t = (p.level().getGameTime() % 72000L) + partial;
                ev.setRoll(ev.getRoll() + (float) Math.toDegrees(be.roulisCamera(s, t, be.energie(partial))) * 0.4f);
                return;
            }
        }
    }
}
