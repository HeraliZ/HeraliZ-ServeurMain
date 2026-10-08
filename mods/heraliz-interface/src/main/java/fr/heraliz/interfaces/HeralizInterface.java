package fr.heraliz.interfaces;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * HeraliZ Interface (client seulement) : interfaces du serveur au style des clans. Version 1.0.0 : menu Echap
 * ({@link fr.heraliz.interfaces.client.EcranPause}) et ecran Informations, kit d'interface commun
 * ({@code fr.heraliz.interfaces.kit}) reutilisable par les prochains ecrans. Sur un serveur dedie le mod ne fait rien.
 */
@Mod(HeralizInterface.MODID)
public final class HeralizInterface {
    public static final String MODID = "heraliz_interface";
    public static final Logger LOG = LogUtils.getLogger();

    public HeralizInterface() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            LOG.info("[interface] serveur dedie : rien a faire (mod client)");
            return;
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, InterfaceConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
