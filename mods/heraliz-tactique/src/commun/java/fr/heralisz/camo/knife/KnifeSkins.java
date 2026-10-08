package fr.heralisz.camo.knife;

import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Reconnaît un skin de couteau à l'id de l'arme TaCZ : heralisz_skins:&lt;couteau&gt;__&lt;finition&gt;. */
public final class KnifeSkins {
   public static final String NAMESPACE = "heralisz_skins";

   public record Skin(KnifeType knife, KnifeFinish finish) {}

   @Nullable
   public static Skin of(ItemStack stack) {
      IGun gun = IGun.getIGunOrNull(stack);
      if (gun == null) {
         return null;
      }
      ResourceLocation id = gun.getGunId(stack);
      if (id == null || !NAMESPACE.equals(id.getNamespace())) {
         return null;
      }
      String[] parts = id.getPath().split("__", 2);
      if (parts.length != 2) {
         return null;
      }
      KnifeType knife = KnifeType.byId(parts[0]);
      KnifeFinish finish = KnifeFinish.byId(parts[1]);
      return knife == null || finish == null ? null : new Skin(knife, finish);
   }

   private KnifeSkins() {}
}
