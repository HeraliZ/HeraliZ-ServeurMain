package fr.heralisz.bunker.barricade;

import fr.heralisz.bunker.HeraliszBunker;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Barricade en bois : entité + objet de pose (stack de 3). */
public final class BarricadeRegistry {
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HeraliszBunker.MODID);
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HeraliszBunker.MODID);

   public static final RegistryObject<EntityType<BarricadeEntity>> BARRICADE = ENTITIES.register("wooden_barricade",
      () -> EntityType.Builder.<BarricadeEntity>of(BarricadeEntity::new, MobCategory.MISC).sized(0.4F, 0.4F)
         .clientTrackingRange(10).updateInterval(20).build("wooden_barricade"));

   public static final RegistryObject<Item> BARRICADE_ITEM = ITEMS.register("wooden_barricade", () -> new Item(new Item.Properties().stacksTo(3)) {
      /**
       * Posée sur le dessus d'un bloc, au point exact cliqué, face au joueur (angle libre). Refusée si elle entre dans un
       * bloc, un prop, une entité ou une autre barricade (voir BarricadePlacement, aussi utilisé par l'aperçu).
       */
      @Override
      public InteractionResult useOn(UseOnContext ctx) {
         if (ctx.getPlayer() == null) {
            return InteractionResult.PASS;
         }
         BlockHitResult hit = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), ctx.getClickedPos(), false);
         BarricadePlacement.Result where = BarricadePlacement.compute(ctx.getLevel(), ctx.getPlayer(), hit);
         if (!where.valid()) {
            if (ctx.getLevel().isClientSide) {
               ctx.getPlayer().displayClientMessage(Component.translatable("message.heralisz_bunker.barricade_blocked"), true);
            }
            return InteractionResult.FAIL;
         }
         if (!ctx.getLevel().isClientSide) {
            BarricadeEntity b = BARRICADE.get().create(ctx.getLevel());
            if (b == null) return InteractionResult.FAIL;
            b.moveTo(where.pos().x, where.pos().y, where.pos().z, where.yaw(), 0.0F);
            ctx.getLevel().addFreshEntity(b);
            ctx.getLevel().playSound(null, b.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
            if (!ctx.getPlayer().getAbilities().instabuild) {
               ctx.getItemInHand().shrink(1);
            }
         }
         return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
      }
   });

   public static void register(IEventBus bus) {
      ENTITIES.register(bus);
      ITEMS.register(bus);
   }

   private BarricadeRegistry() {}
}
