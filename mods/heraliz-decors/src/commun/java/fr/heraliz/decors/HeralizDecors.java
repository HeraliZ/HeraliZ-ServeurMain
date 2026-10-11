package fr.heraliz.decors;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * HeraliZ Décors : les décors 3D de la map (la cité cachée de la faille d'abord). Tout est en modèles fixes (calculés une
 * fois avec le chunk, aucun rendu à chaque image) ; les faces lumineuses (ampoules, projecteurs, cristaux) passent par les
 * données de face de Forge ; les effets (brume, poussière, embruns) ne tournent que près du joueur (animateTick).
 */
@Mod(HeralizDecors.MODID)
public final class HeralizDecors {
    public static final String MODID = "heraliz_decors";
    /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
    public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";
    public static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> ONGLETS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final RegistryObject<CreativeModeTab> ONGLET = ONGLETS.register("decors", () -> CreativeModeTab.builder()
            .title(Component.translatable("onglet.heraliz_decors"))
            .icon(() -> new ItemStack(Blocs.LAMPE_SUSPENDUE.get()))
            .displayItems((p, out) -> Blocs.OBJETS.forEach(o -> out.accept(o.get())))
            .build());

    public HeralizDecors() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Blocs.BLOCS.register(bus);
        Blocs.OBJETS_REG.register(bus);
        Particules.TYPES.register(bus);
        EntitesBloc.TYPES.register(bus);
        ONGLETS.register(bus);
        fr.heraliz.decors.ambiance.Ambiance.init();                   // brouillard dynamique et zones dangereuses (1.4.0)
    }
}
