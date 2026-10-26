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

public class CombatPropertiesProvider implements ICapabilityProvider {
   public static final Capability<CombatProperties> CAPABILITY = CapabilityManager.get(new CapabilityToken<CombatProperties>() {});
   private final CombatProperties instance;

   public CombatPropertiesProvider(Player entity) {
      this.instance = new CombatProperties(entity);
   }

   @NotNull
   public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
      return CAPABILITY.orEmpty(cap, LazyOptional.of(() -> this.instance).cast());
   }
}
