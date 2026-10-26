package fr.heraliz.grades;

import fr.heraliz.grades.pont.Crochet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * « Registre des grades » (2.0.0) : l'objet des op qui ouvre l'éditeur des grades (clic droit). Le serveur décide de tout
 * (op niveau 4, connecté sur AuthMe) ; pour les autres, l'objet ne fait rien. Donné par {@code /grades registre}.
 */
public final class ObjetRegistre extends Item {
   public static final DeferredRegister<Item> OBJETS = DeferredRegister.create(ForgeRegistries.ITEMS, HeralizGrades.MODID);
   public static final RegistryObject<Item> REGISTRE = OBJETS.register("registre_grades", ObjetRegistre::new);
   /** Clic droit côté serveur : branché par la partie serveur (ouvre l'éditeur si le joueur en a le droit). */
   public static final Crochet<ServerPlayer> OUVRIR = new Crochet<>();

   private ObjetRegistre() {
      super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
   }

   @Override
   public boolean isFoil(ItemStack stack) {
      return true;
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player joueur, InteractionHand main) {
      if (joueur instanceof ServerPlayer p) OUVRIR.accept(p);
      return InteractionResultHolder.sidedSuccess(joueur.getItemInHand(main), level.isClientSide());
   }
}
