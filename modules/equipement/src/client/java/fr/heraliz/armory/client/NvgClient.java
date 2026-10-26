package fr.heraliz.armory.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.network.ArmoryNetwork;
import fr.heraliz.armory.nvg.NightVision;
import fr.heraliz.armory.registry.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/** Cote client : touche des lunettes, animation (baisser / relever) par entite, filtre vert a l'ecran, gresillement des
 *  lunettes en service autour de leur porteur (audible a quelques blocs : un joueur sans jumelles peut les entendre). */
public final class NvgClient {
    private NvgClient() {}

    /**
     * Touche des lunettes. 0.8.2 : fleche du haut par defaut, car N est deja prise sur le serveur (Simple Voice Chat
     * « couper le chat vocal », et ouverture du menu de base de HeralisZ Bases) ; reglable dans Options > Commandes.
     */
    public static final KeyMapping KEY = new KeyMapping("key.heraliz_armory.nvg", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UP, "key.categories.heraliz_armory");
    private static final ResourceLocation OVERLAY = HeralizArmory.id("textures/misc/nvg_overlay.png");
    private static final float ANIM_MS = 450.0F;
    /** entite -> {position 0..1 (1 = baissees), horodatage ms} */
    private static final Map<Integer, float[]> STATE = new HashMap<>();
    private static final Map<Integer, Long> TIME = new HashMap<>();
    /** Horloge de l'animation (ms) : le temps reel, ou l'horloge a pas fixe du tournage video (outils de dev). */
    public static java.util.function.LongSupplier clock = Util::getMillis;

    /** Position des lunettes de cette entite (0 = relevees, 1 = baissees), animee en douceur. */
    public static float progress(LivingEntity entity, ItemStack stack) {
        float target = NightVision.isDown(stack) ? 1.0F : 0.0F;
        long now = clock.getAsLong();
        int id = entity.getId();
        float[] st = STATE.get(id);
        if (st == null) {
            if (STATE.size() > 512) {
                STATE.clear();
                TIME.clear();
            }
            st = new float[]{target};
            STATE.put(id, st);
            TIME.put(id, now);
        }
        long last = TIME.getOrDefault(id, now);
        float step = (now - last) / ANIM_MS;
        TIME.put(id, now);
        if (st[0] < target) st[0] = Math.min(target, st[0] + step);
        else if (st[0] > target) st[0] = Math.max(target, st[0] - step);
        float t = st[0];
        return t * t * (3.0F - 2.0F * t);   // depart et arrivee amortis
    }

    @Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(KEY);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("nvg", OVERLAY_RENDER);
        }
    }

    @Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) ArmoryNetwork.CHANNEL.sendToServer(new ArmoryNetwork.ToggleNvg());
            }
            hums(mc);
        }
    }

    /** Gresillements en cours, par entite. */
    private static final Map<Integer, Hum> HUMS = new HashMap<>();

    /** Lance le gresillement des joueurs proches dont les lunettes sont baissees (y compris le sien, plus faible). */
    private static void hums(Minecraft mc) {
        HUMS.values().removeIf(Hum::isStopped);
        if (mc.level == null || mc.player == null) {
            HUMS.clear();
            return;
        }
        for (Player p : mc.level.players()) {
            if (HUMS.containsKey(p.getId()) || p.distanceToSqr(mc.player) > 24 * 24) continue;
            if (!NightVision.isDown(p.getItemBySlot(EquipmentSlot.HEAD))) continue;
            Hum h = new Hum(p, p == mc.player ? 0.12F : 0.3F);
            HUMS.put(p.getId(), h);
            mc.getSoundManager().play(h);
        }
    }

    /** Gresillement electronique en boucle, attache a la tete d'un porteur de lunettes en service ; s'arrete quand elles
     *  remontent, quand le casque est retire ou quand l'entite disparait. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final LivingEntity entity;

        Hum(LivingEntity entity, float volume) {
            super(ModSounds.NVG_HUM.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.entity = entity;
            this.looping = true;
            this.delay = 0;
            this.volume = volume;
            this.pitch = 0.97F + entity.getRandom().nextFloat() * 0.06F;
            this.attenuation = Attenuation.LINEAR;
            move();
        }

        private void move() {
            this.x = entity.getX();
            this.y = entity.getEyeY();
            this.z = entity.getZ();
        }

        @Override
        public void tick() {
            if (entity.isRemoved() || !entity.isAlive() || !NightVision.isDown(entity.getItemBySlot(EquipmentSlot.HEAD))) {
                stop();
                return;
            }
            move();
        }
    }

    /** Filtre a deux tubes, vert phosphore, quand les lunettes sont baissees (vue a la premiere personne). */
    private static final IGuiOverlay OVERLAY_RENDER = (gui, graphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        ItemStack head = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        if (!NightVision.hasNvg(head)) return;
        float p = progress(mc.player, head);
        if (p < 0.9F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (p - 0.9F) / 0.1F);
        graphics.blit(OVERLAY, 0, 0, 0.0F, 0.0F, width, height, width, height);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    };
}
