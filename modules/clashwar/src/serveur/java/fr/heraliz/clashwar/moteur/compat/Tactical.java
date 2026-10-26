package fr.heraliz.clashwar.moteur.compat;

import fr.heraliz.clashwar.HeralizClashWar;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * heralisz_tactical par reflexion tolerante : remise a zero des temps de recharge (capacite
 * {@code heralisz_tactical:custom_cooldown}, classe {@code CustomItemCoolDowns}) a l'entree en match. Absent = rien.
 * Fil serveur.
 */
public final class Tactical {
    private Tactical() {
    }

    private static boolean pret;
    private static Capability<?> cap;
    private static Method retirer;
    private static java.lang.reflect.Field cooldowns;

    private static boolean init() {
        if (pret) return cap != null;
        pret = true;
        try {
            Class<?> prov = Class.forName("fr.heralisz.tactical.capability.CustomItemCoolDownsProvider");
            Class<?> cd = Class.forName("fr.heralisz.tactical.capability.CustomItemCoolDowns");
            cap = (Capability<?>) prov.getField("CAPABILITY").get(null);
            retirer = cd.getMethod("removeCooldown", ResourceLocation.class);
            cooldowns = cd.getField("cooldowns");
        } catch (ClassNotFoundException e) {
            cap = null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            HeralizClashWar.LOG.warn("[matchs] heralisz_tactical present mais illisible : {}", e.toString());
            cap = null;
        }
        return cap != null;
    }

    /** Remet a zero tous les temps de recharge des objets tactiques du joueur ; rend leur nombre. */
    public static int zeroRecharges(ServerPlayer p) {
        if (!init()) return 0;
        Object c = p.getCapability(cap).resolve().orElse(null);
        if (c == null) return 0;
        try {
            Map<?, ?> m = (Map<?, ?>) cooldowns.get(c);
            List<Object> cles = new ArrayList<>(m.keySet());
            for (Object k : cles) retirer.invoke(c, k);
            return cles.size();
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeralizClashWar.LOG.warn("[matchs] recharges de {} non remises a zero : {}", p.getGameProfile().getName(), e.toString());
            return 0;
        }
    }
}
