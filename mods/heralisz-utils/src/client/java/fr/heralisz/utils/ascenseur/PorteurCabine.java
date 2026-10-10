package fr.heralisz.utils.ascenseur;

import fr.heralisz.utils.HeraliszUtils;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Côté client, son propre joueur debout sur la cabine est porté tout de suite (sans attendre le serveur) : il monte et
 * descend avec le plancher, garde toute sa liberté de mouvement, ne s'enfonce jamais dans le plancher ; en marche, il
 * ne peut pas sortir du bord (garde-corps invisibles). Le serveur porte de la même façon (CabineServeur) : les deux
 * restent d'accord, pas de saccades.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, value = Dist.CLIENT)
public final class PorteurCabine {
    /** Dernier mouvement de chaque cabine déjà appliqué (id de la cabine -> tick de la cabine). */
    private static final Map<Integer, Integer> APPLIQUE = new HashMap<>();

    private PorteurCabine() {}

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        Player p = e.player;
        if (e.phase != TickEvent.Phase.START || !p.level().isClientSide || p != Minecraft.getInstance().player) {
            return;
        }
        for (Cabine c : p.level().getEntitiesOfClass(Cabine.class, p.getBoundingBox().inflate(Cabine.DEMI + 1, 3, Cabine.DEMI + 1))) {
            if (!c.porte(p)) {
                continue;
            }
            double y = p.getY();
            if (c.tickDelta >= 0 && APPLIQUE.getOrDefault(c.getId(), -1) != c.tickDelta) {
                APPLIQUE.put(c.getId(), c.tickDelta);
                y += c.deltaClient;
            }
            y = Math.max(y, c.plancher());
            double x = p.getX(), z = p.getZ();
            if (c.enMarche()) {
                double m = Cabine.DEMI - 0.35;
                x = c.getX() + Mth.clamp(x - c.getX(), -m, m);
                z = c.getZ() + Mth.clamp(z - c.getZ(), -m, m);
            }
            p.setPos(x, y, z);
            p.fallDistance = 0;
            if (p.getDeltaMovement().y < 0) {
                p.setDeltaMovement(p.getDeltaMovement().multiply(1, 0, 1));
            }
            p.setOnGround(true);
            return;
        }
    }
}
