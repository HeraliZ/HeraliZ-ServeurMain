package fr.heralisz.utils.ambiance;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.heralisz.utils.HeraliszUtils;
import java.nio.file.Path;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Côté client : effets du monde principal (ciel, nuages et éclairage de l'ambiance), shaders du ciel, et pack de
 * ressources « terrain » intégré au mod (herbe réaliste) : obligatoire et placé tout en haut de la liste, il passe
 * au-dessus des packs du modpack (arkaniaz_pack7 reprend l'herbe vanilla) sans que les joueurs aient rien à régler.
 * Un shader qui ne se charge pas ne bloque rien : le ciel retombe alors sur le ciel vanilla.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AmbianceClient {
    private AmbianceClient() {}

    @SubscribeEvent
    public static void effets(RegisterDimensionSpecialEffectsEvent e) {
        e.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS, new CielHeraliz());
    }

    @SubscribeEvent
    public static void shaders(RegisterShadersEvent e) {
        try {
            e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(HeraliszUtils.MODID, "etoiles"),
                    DefaultVertexFormat.POSITION_COLOR_TEX), s -> Ciel.shEtoiles = s);
            e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(HeraliszUtils.MODID, "voie_lactee"),
                    DefaultVertexFormat.POSITION_COLOR_TEX), s -> Ciel.shVoie = s);
            e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(HeraliszUtils.MODID, "nuages_doux"),
                    DefaultVertexFormat.POSITION), s -> Ciel.shNuages = s);
        } catch (Exception ex) {
            HeraliszUtils.LOG.error("Shaders du ciel HeraliZ illisibles : ciel vanilla", ex);
            Ciel.shEtoiles = null;
            Ciel.shVoie = null;
            Ciel.shNuages = null;
        }
    }

    @SubscribeEvent
    public static void packs(AddPackFindersEvent e) {
        if (e.getPackType() != PackType.CLIENT_RESOURCES) return;
        try {
            Path chemin = ModList.get().getModFileById(HeraliszUtils.MODID).getFile().findResource("resourcepacks/heraliz_terrain");
            Pack pack = Pack.readMetaAndCreate("heralisz_utils:terrain", Component.literal("HeraliZ : terrain"), true,
                    id -> new PathPackResources(id, chemin, true), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack != null) {
                e.addRepositorySource(c -> c.accept(pack));
            }
        } catch (Exception ex) {
            HeraliszUtils.LOG.error("Pack terrain HeraliZ indisponible", ex);
        }
    }
}
