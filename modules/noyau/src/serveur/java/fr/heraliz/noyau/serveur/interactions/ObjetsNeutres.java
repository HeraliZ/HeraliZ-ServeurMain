package fr.heraliz.noyau.serveur.interactions;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Objets de mod qui n'agissent jamais sur le bloc visé (armes de TaCZ, mêlée, soins, nourriture...) : un clic droit avec
 * eux sur un bloc est, pour le bloc, celui d'une main vide (voir {@link ClicObjetBukkit}).
 * <p>Pas neutres : les objets de Minecraft (WorldGuard les connaît tous), les objets qui posent quelque chose (blocs, seaux,
 * oeufs, cadres, bateaux, briquet...), ceux des mods de construction ({@link #CONSTRUCTION} : plan, marteau, C4 de CRac),
 * et tout objet dont la classe redéfinit l'usage sur un bloc ({@code useOn} ou {@code onItemUseFirst} de Forge : barricade,
 * mannequin...), même règle que les objets étrangers dans les bases de CRac. Toujours neutre : ce qui se mange ou se boit.
 * Réponse gardée par classe d'objet (le registre ne change pas en jeu).</p>
 */
public final class ObjetsNeutres {
   /** Mods dont les objets construisent ou raident : WorldGuard les juge toujours. */
   private static final Set<String> CONSTRUCTION = Set.of("minecraft", "crac");
   private static final Map<Class<?>, Boolean> NEUTRES = new ConcurrentHashMap<>();
   private static volatile String useOn;

   private ObjetsNeutres() {
   }

   public static boolean neutre(ItemStack s) {
      if (s.isEmpty()) return false;
      Item it = s.getItem();
      ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
      if (id == null || CONSTRUCTION.contains(id.getNamespace()) || posant(it)) return false;
      if (s.isEdible()) return true;
      return NEUTRES.computeIfAbsent(it.getClass(), c -> !agitSurBloc(c));
   }

   private static boolean posant(Item it) {
      return it instanceof BlockItem || it instanceof BucketItem || it instanceof SpawnEggItem || it instanceof HangingEntityItem
            || it instanceof ArmorStandItem || it instanceof BoatItem || it instanceof MinecartItem || it instanceof EndCrystalItem
            || it instanceof FireChargeItem || it instanceof FlintAndSteelItem;
   }

   /** La classe (ou une classe mère, avant {@code Item}) redéfinit-elle l'usage sur un bloc ? */
   private static boolean agitSurBloc(Class<?> c) {
      String nom = useOn;
      if (nom == null) {
         // Item.useOn : nom SRG en jeu, nom lisible dans l'environnement de développement
         nom = ObfuscationReflectionHelper.findMethod(Item.class, "m_6225_", UseOnContext.class).getName();
         useOn = nom;
      }
      for (Class<?> k = c; k != null && k != Item.class; k = k.getSuperclass()) {
         for (Method m : k.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0] == UseOnContext.class && m.getName().equals(nom)
                  || p.length == 2 && p[1] == UseOnContext.class && m.getName().equals("onItemUseFirst")) {
               return true;
            }
         }
      }
      return false;
   }
}
