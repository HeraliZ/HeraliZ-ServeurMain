package fr.heraliz.decors.client;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import com.mojang.blaze3d.platform.NativeImage;
import fr.heraliz.decors.HeralizDecors;
import fr.heraliz.decors.bloc.Clignotement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Teinte rouge de la lumière près des lampes qui clignotent, et la seconde noire.
 * <p>
 * 19/11 (audit des images par seconde, point 2) : les lampes ne changent plus d'état ni de lumière (toujours 15, voir
 * {@link Clignotement}) ; leur verre clignote par sa texture animée. Le moment de la seconde noire vient donc de
 * l'horloge des textures animées de la grande texture des blocs ({@link #horloge} : ticks depuis son dernier
 * rechargement, comme leur compteur d'images), plus de l'état du bloc. Et comme la lumière des lampes reste là pendant
 * la seconde noire, la teinte la retire elle-même près du joueur : chaque niveau de lumière de bloc y est ramené vers
 * « sans lumière de bloc », d'autant plus que la lampe est proche (le même poids que l'assombrissement d'avant) et que le
 * ciel est loin (comme lui : rien en plein ciel, ni sur les faces qui brillent d'elles-mêmes) ; sous terre et à moins de
 * 6 blocs, c'est exactement la lumière d'avant, lampes éteintes. Une coupure demandée par un autre mod
 * ({@code fr.heraliz.noyau.client.Eclairage}, réglette du casino) passe par le même chemin.
 */
public final class AlarmeRouge {
    private static final boolean ACTIF = true;
    private static final int VUE = 20;
    private static final int PRES = 6;
    private static final int RAYON = 48;
    private static final int HAUT = 32;
    private static final int PLEIN = 40;
    private static final List<BlockPos> LAMPES = new ArrayList<>();
    private static volatile float niveau = 0.0F;
    private static volatile float noir = 0.0F;
    private static volatile float coupure = 0.0F;
    private static int compteur = 0;
    /** Ticks des textures animées depuis le dernier rechargement des ressources (même compteur que leurs images). */
    private static long horloge = 0;
    private static LightTexture lt;
    private static NativeImage pixels;
    private static DynamicTexture texture;
    private static boolean echec = false;
    private static boolean aSortie = false;
    private static final int[] BASE = new int[256];
    private static final int[] SORTIE = new int[256];

    private AlarmeRouge() {
    }

    /** Mélange de deux couleurs de la lightmap (ABGR), t de 0 (c1) à 1 (c2). */
    private static int melange(int c1, int c2, float t) {
        int out = 0;
        for (int k = 0; k < 32; k += 8) {
            int v1 = c1 >>> k & 0xFF;
            int v2 = c2 >>> k & 0xFF;
            out |= Math.min(255, Math.round(v1 + (v2 - v1) * t)) << k;
        }
        return out;
    }

    private static int teinte(int c, int x, int y, float a, float n) {
        float jour = y / 15.0F;
        float ombre = n * (1.0F - jour) * (1.0F - jour);
        int r = c & 0xFF;
        int g = c >> 8 & 0xFF;
        int b = c >> 16 & 0xFF;
        int al = c >>> 24;
        if (x > 0) {
            float t = Math.min(1.0F, a * (0.75F + 0.25F * x / 15.0F) * (1.0F - 0.7F * jour) * 1.3F);
            if (t > 0.005F) {
                r = Math.min(255, (int)(r * (1.0F + 0.6F * t) + 40.0F * t * (x / 15.0F)));
                g = (int)(g * (1.0F - 0.97F * t));
                b = (int)(b * (1.0F - 0.98F * t));
            }
        }

        if (ombre > 0.005F) {
            r = (int)(r * (1.0F - ombre));
            g = (int)(g * (1.0F - ombre));
            b = (int)(b * (1.0F - ombre));
        }

        return al << 24 | b << 16 | g << 8 | r;
    }

    private static boolean trouverChamps(LightTexture l) {
        pixels = null;
        texture = null;

        try {
            for (Field f : LightTexture.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    if (f.getType() == NativeImage.class) {
                        f.setAccessible(true);
                        pixels = (NativeImage)f.get(l);
                    } else if (f.getType() == DynamicTexture.class) {
                        f.setAccessible(true);
                        texture = (DynamicTexture)f.get(l);
                    }
                }
            }
        } catch (RuntimeException | ReflectiveOperationException var5) {
            HeralizDecors.LOG.warn("Lumière rouge : lightmap inaccessible ({})", var5.toString());
        }

        lt = l;
        aSortie = false;
        return pixels != null && texture != null;
    }

    static void appliquer(Minecraft mc) {
        if (!echec) {
            LightTexture l = mc.gameRenderer.lightTexture();
            if (l != lt && !trouverChamps(l)) {
                echec = true;
                HeralizDecors.LOG.warn("Lumière rouge : champs de la lightmap introuvables, effet désactivé");
            } else {
                boolean frais = !aSortie;

                for (int i = 0; i < 256 && !frais; i++) {
                    if (pixels.getPixelRGBA(i & 15, i >> 4) != SORTIE[i]) {
                        frais = true;
                    }
                }

                if (frais) {
                    for (int ix = 0; ix < 256; ix++) {
                        BASE[ix] = pixels.getPixelRGBA(ix & 15, ix >> 4);
                    }
                }

                float a = niveau;
                float n = noir;
                // lumière de bloc retirée : lampes dans leur seconde noire, ou coupure demandée (réglette du casino)
                float k = Math.max(n, coupure);
                boolean change = false;

                for (int ix = 0; ix < 256; ix++) {
                    int c = a <= 0.01F && k <= 0.01F ? BASE[ix] : teinte(BASE[ix], ix & 15, ix >> 4, a, n);
                    if (k > 0.01F && (ix & 15) > 0) {
                        // comme l'assombrissement : rien sous le ciel (ni les faces qui brillent d'elles-mêmes, 15 / 15)
                        int y = ix >> 4;
                        float sous = 1.0F - y / 15.0F;
                        c = melange(c, teinte(BASE[y << 4], 0, y, a, n), k * sous * sous);
                    }
                    SORTIE[ix] = c;
                    if (pixels.getPixelRGBA(ix & 15, ix >> 4) != c) {
                        pixels.setPixelRGBA(ix & 15, ix >> 4, c);
                        change = true;
                    }
                }

                aSortie = true;
                if (change) {
                    texture.upload();
                }
            }
        }
    }

    private static boolean rouge(BlockState s) {
        return Clignotement.clignote(s.getBlock());
    }

    private static void relever(ClientLevel lv, BlockPos c) {
        LAMPES.clear();
        int ymin = Math.max(lv.getMinBuildHeight(), c.getY() - 32);
        int ymax = Math.min(lv.getMaxBuildHeight() - 1, c.getY() + 32);

        for (int cx = c.getX() - 48 >> 4; cx <= c.getX() + 48 >> 4; cx++) {
            for (int cz = c.getZ() - 48 >> 4; cz <= c.getZ() + 48 >> 4; cz++) {
                LevelChunk ch = lv.getChunkSource().getChunk(cx, cz, false);
                if (ch != null) {
                    for (int sy = ymin >> 4; sy <= ymax >> 4; sy++) {
                        int i = ch.getSectionIndexFromSectionY(sy);
                        if (i >= 0 && i < ch.getSections().length) {
                            LevelChunkSection s = ch.getSection(i);
                            if (s != null && !s.hasOnlyAir() && s.maybeHas(AlarmeRouge::rouge)) {
                                for (int y = 0; y < 16; y++) {
                                    for (int z = 0; z < 16; z++) {
                                        for (int x = 0; x < 16; x++) {
                                            if (rouge(s.getBlockState(x, y, z))) {
                                                LAMPES.add(new BlockPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventBusSubscriber(
        modid = HeralizDecors.MODID,
        bus = Bus.FORGE,
        value = {Dist.CLIENT}
    )
    public static final class Forge_ {
        private Forge_() {
        }

        @SubscribeEvent
        public static void alarmeRougeLightmap(RenderLevelStageEvent e) {
            if (e.getStage() == Stage.AFTER_SKY) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    AlarmeRouge.appliquer(mc);
                }
            }
        }

        @SubscribeEvent
        public static void alarmeRougeTick(ClientTickEvent e) {
            if (e.phase == Phase.END) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    AlarmeRouge.horloge++;               // Minecraft.tick : les textures animées avancent s'il y a un monde
                }
                AlarmeRouge.coupure = fr.heraliz.noyau.client.Eclairage.lire();
                Player p = mc.player;
                if (p != null && mc.level != null) {
                    if (AlarmeRouge.compteur++ % 10 == 0) {
                        AlarmeRouge.relever(mc.level, p.blockPosition());
                    }

                    double ox = p.getX();
                    double oy = p.getEyeY();
                    double oz = p.getZ();
                    double d2 = Double.MAX_VALUE;
                    boolean allumee = Clignotement.allumeA(AlarmeRouge.horloge);
                    Vec3 oeil = p.getEyePosition();

                    for (BlockPos q : AlarmeRouge.LAMPES) {
                        BlockState s = mc.level.getBlockState(q);
                        if (AlarmeRouge.rouge(s)) {
                            double dx = q.getX() + 0.5 - ox;
                            double dy = q.getY() + 0.5 - oy;
                            double dz = q.getZ() + 0.5 - oz;
                            double dd = dx * dx + dy * dy + dz * dz;
                            if (!(dd > 400.0) && !(dd >= d2)) {
                                Vec3 cible3 = new Vec3(q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5);
                                BlockHitResult hr = mc.level.clip(new ClipContext(oeil, cible3, Block.VISUAL, Fluid.NONE, p));
                                if (hr.getType() == Type.MISS || hr.getBlockPos().equals(q)) {
                                    d2 = dd;
                                }
                            }
                        }
                    }

                    float cible = 0.0F;
                    if (d2 < Double.MAX_VALUE) {
                        double d = Math.sqrt(d2);
                        cible = d <= 6.0 ? 1.0F : (float)Math.max(0.0, 1.0 - (d - 6.0) / 14.0);
                    }

                    AlarmeRouge.niveau = AlarmeRouge.niveau + (cible - AlarmeRouge.niveau) * 0.25F;
                    if (Math.abs(cible - AlarmeRouge.niveau) < 0.02F) {
                        AlarmeRouge.niveau = cible;
                    }

                    AlarmeRouge.noir = !allumee && cible > 0.0F ? cible : 0.0F;
                } else {
                    AlarmeRouge.LAMPES.clear();
                    AlarmeRouge.niveau = 0.0F;
                    AlarmeRouge.noir = 0.0F;
                }
            }
        }
    }

    /** Horloge remise à zéro à chaque rechargement des ressources (les textures animées repartent de leur 1re image). */
    @EventBusSubscriber(
        modid = HeralizDecors.MODID,
        bus = Bus.MOD,
        value = {Dist.CLIENT}
    )
    public static final class Mod_ {
        private Mod_() {
        }

        @SubscribeEvent
        public static void alarmeRougeRechargement(RegisterClientReloadListenersEvent e) {
            // après celui des modèles de Minecraft (inscrit avant les mods), qui refait la grande texture des blocs
            e.registerReloadListener((ResourceManagerReloadListener) rm -> AlarmeRouge.horloge = 0);
        }
    }
}
