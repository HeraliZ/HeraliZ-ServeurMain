package fr.heralisz.tactical.capability;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CustomItemCoolDownsProvider implements ICapabilityProvider {
   public static final Capability<CustomItemCoolDowns> CAPABILITY = CapabilityManager.get(new CapabilityToken<CustomItemCoolDowns>() {});
   private final CustomItemCoolDowns instance;

   public CustomItemCoolDownsProvider(Player player) {
      this.instance = new CustomItemCoolDowns(player);
   }

   @NotNull
   public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
      return CAPABILITY.orEmpty(cap, LazyOptional.of(() -> this.instance).cast());
   }
}
