package fr.heraliz.airstrike.registry;

import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.strike.CartridgeTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AirstrikeMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.airstrike"))
            .icon(() -> new ItemStack(ModItems.DESIGNATOR.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.DESIGNATOR.get());
                for (CartridgeTier t : CartridgeTier.values()) out.accept(ModItems.cartridge(t));
            })
            .build());

    private ModCreativeTab() {
    }
}
