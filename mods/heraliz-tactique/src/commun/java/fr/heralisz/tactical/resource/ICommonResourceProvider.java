package fr.heralisz.tactical.resource;

import java.util.Collection;
import fr.heralisz.tactical.item.index.ConsumableIndex;
import fr.heralisz.tactical.item.index.MeleeWeaponIndex;
import fr.heralisz.tactical.item.index.ThrowableIndex;
import net.minecraft.resources.ResourceLocation;

public interface ICommonResourceProvider {
   ConsumableIndex getConsumableIndex(ResourceLocation var1);

   Collection<ConsumableIndex> getConsumableIndexes();

   ThrowableIndex<?, ?> getThrowableIndex(ResourceLocation var1);

   Collection<ThrowableIndex<?, ?>> getThrowableIndexes();

   MeleeWeaponIndex<?> getMeleeIndex(ResourceLocation var1);

   Collection<MeleeWeaponIndex<?>> getMeleeIndexes();
}
