package fr.heraliz.armory.suit;

import fr.heraliz.armory.armor.ArmorConfig;
import fr.heraliz.armory.nvg.NightVision;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Eaux du labo sous-marin (17/11, demande du propriétaire : « sans la tenue de plongée, on ne doit pas pouvoir aller au
 * labo ; sans elle, il fait très sombre quand on descend, avec elle on voit correctement »). Dans les cercles de
 * {@code labo_zones} (carte principale), la tête sous l'eau :
 * <ul>
 *   <li>sans la tenue de plongée : {@code labo_apnee_secondes} d'air (les bulles se vident d'autant), puis
 *   {@code labo_degats_par_seconde} de noyade chaque seconde (la noyade de Minecraft, 1 cœur par seconde après 15 s, ne
 *   compte plus) ; sous {@code labo_noir_sous_y}, le noir (cécité, sans icône ni particules) ;</li>
 *   <li>avec la tenue : sa bouteille fait respirer (DivingServeur) et une vision de nuit fait voir correctement
 *   ({@code labo_vision_tenue}) ; elle est retirée en sortant de ces eaux (sauf si des lunettes de vision nocturne
 *   baissées la donnent).</li>
 * </ul>
 * Serveur seulement. Un joueur hors de l'eau ne coûte qu'un test par tick ; seuls les joueurs dans ces eaux sont suivis.
 */
public final class LaboSousMarin {
    private LaboSousMarin() {}

    /** Joueur dans les eaux du labo : ticks d'apnée, vision de nuit posée par nous. */
    private static final class Etat {
        int apnee;
        boolean vision;
    }

    private static final Map<UUID, Etat> ETATS = new HashMap<>();
    // labo_zones lu : recalculé seulement quand Forge rend une autre liste (rechargement de la config)
    private static List<? extends String> source;
    private static int[][] zones = new int[0][];

    public static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(LaboSousMarin::tick);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ETATS.remove(e.getEntity().getUUID()));
    }

    private static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp) || !ArmorConfig.SPEC.isLoaded()) return;
        Etat e = ETATS.get(sp.getUUID());
        if (!sp.isEyeInFluid(FluidTags.WATER) || sp.isCreative() || sp.isSpectator() || sp.level().dimension() != Level.OVERWORLD
                || !dansZone(sp.getBlockX(), sp.getBlockZ())) {
            if (e != null) sortir(sp, e);
            return;
        }
        if (e == null) {
            e = new Etat();
            ETATS.put(sp.getUUID(), e);
        }
        if (Diving.wears(sp)) {
            e.apnee = 0;
            if (ArmorConfig.LABO_VISION.get()) {
                MobEffectInstance nv = sp.getEffect(MobEffects.NIGHT_VISION);
                if (nv == null || nv.getDuration() < 300) {
                    sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
                }
                e.vision = true;
            }
            return;
        }
        if (e.vision) retirerVision(sp, e);
        int limite = Math.max(1, (int) Math.round(ArmorConfig.LABO_APNEE.get() * 20));
        e.apnee++;
        if (e.apnee < limite) {
            sp.setAirSupply(Math.min(sp.getAirSupply(), sp.getMaxAirSupply() * (limite - e.apnee) / limite));
        } else {
            // air tenu à 0 : la noyade de Minecraft (-20 d'air) n'arrive jamais, seuls ces dégâts comptent
            sp.setAirSupply(0);
            if ((e.apnee - limite) % 20 == 0) sp.hurt(sp.damageSources().drown(), ArmorConfig.LABO_DEGATS.get().floatValue());
        }
        if (sp.getY() < ArmorConfig.LABO_NOIR_Y.get() && sp.tickCount % 10 == 0) {
            sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false, false));
        }
    }

    private static void sortir(ServerPlayer sp, Etat e) {
        if (e.vision) retirerVision(sp, e);
        ETATS.remove(sp.getUUID());
    }

    /** Retire notre vision de nuit (ambiante, invisible, courte), jamais celle d'une potion ni des lunettes baissées. */
    private static void retirerVision(ServerPlayer sp, Etat e) {
        e.vision = false;
        if (NightVision.isDown(sp.getItemBySlot(EquipmentSlot.HEAD))) return;
        MobEffectInstance nv = sp.getEffect(MobEffects.NIGHT_VISION);
        if (nv != null && nv.isAmbient() && !nv.isVisible() && nv.getDuration() <= 400) sp.removeEffect(MobEffects.NIGHT_VISION);
    }

    /** Le point (x, z) est dans un cercle de labo_zones (une ligne illisible est sautée). */
    static boolean dansZone(int x, int z) {
        List<? extends String> l = ArmorConfig.LABO_ZONES.get();
        if (l != source) {
            List<int[]> c = new ArrayList<>();
            for (String s : l) {
                String[] p = s.trim().split("\\s+");
                try {
                    c.add(new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Math.abs(Integer.parseInt(p[2]))});
                } catch (RuntimeException ex) {
                    // ligne illisible (le réglage la refuse déjà) : sautée
                }
            }
            zones = c.toArray(new int[0][]);
            source = l;
        }
        for (int[] c : zones) {
            long dx = x - c[0], dz = z - c[1];
            if (dx * dx + dz * dz <= (long) c[2] * c[2]) return true;
        }
        return false;
    }
}
