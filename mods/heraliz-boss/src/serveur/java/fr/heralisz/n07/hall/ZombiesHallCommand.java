package fr.heralisz.n07.hall;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.api.Encounter;
import fr.heralisz.n07.api.EncounterSpec;
import fr.heralisz.n07.api.N07Api;
import fr.heralisz.n07.api.N07Events;
import fr.heralisz.n07.api.PlacedHall;
import fr.heralisz.n07.api.ZombiesBalance;
import fr.heralisz.n07.api.scene.SkipMode;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Banc d'essai de la salle Zombies (op 2), sans le mode Zombies :
 * /n07 zombies pose | intro [joueurs] [manche] | combat [joueurs] [manche] | reset | evacuer | vider.
 * « pose » met le haut du monte-charge devant soi (on est a la place de la porte de confinement), le hall s'etend dans la
 * direction du regard, 28 blocs plus bas. Le retour du teleporteur ramene en haut du monte-charge.
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID)
public final class ZombiesHallCommand {
   private ZombiesHallCommand() {}

   @SubscribeEvent
   public static void register(RegisterCommandsEvent e) {
      e.getDispatcher().register(Commands.literal("n07").requires(s -> s.hasPermission(2))
         .then(Commands.literal("zombies")
            .then(Commands.literal("pose").executes(c -> pose(c.getSource())))
            .then(Commands.literal("intro").executes(c -> fight(c.getSource(), true, 1, 20))
               .then(Commands.argument("joueurs", IntegerArgumentType.integer(1, 8)).executes(c -> fight(c.getSource(), true, IntegerArgumentType.getInteger(c, "joueurs"), 20))
                  .then(Commands.argument("manche", IntegerArgumentType.integer(1, 200))
                     .executes(c -> fight(c.getSource(), true, IntegerArgumentType.getInteger(c, "joueurs"), IntegerArgumentType.getInteger(c, "manche"))))))
            .then(Commands.literal("combat").executes(c -> fight(c.getSource(), false, 1, 20))
               .then(Commands.argument("joueurs", IntegerArgumentType.integer(1, 8)).executes(c -> fight(c.getSource(), false, IntegerArgumentType.getInteger(c, "joueurs"), 20))
                  .then(Commands.argument("manche", IntegerArgumentType.integer(1, 200))
                     .executes(c -> fight(c.getSource(), false, IntegerArgumentType.getInteger(c, "joueurs"), IntegerArgumentType.getInteger(c, "manche"))))))
            .then(Commands.literal("reset").executes(c -> reset(c.getSource())))
            .then(Commands.literal("evacuer").executes(c -> evacuate(c.getSource())))
            .then(Commands.literal("vider").executes(c -> clear(c.getSource())))));
   }

   private static Rotation rotationFor(Direction face) {
      return switch (face) {
         case WEST -> Rotation.CLOCKWISE_90;
         case NORTH -> Rotation.CLOCKWISE_180;
         case EAST -> Rotation.COUNTERCLOCKWISE_90;
         default -> Rotation.NONE;
      };
   }

   private static int pose(CommandSourceStack src) {
      ServerLevel level = src.getLevel();
      var layout = N07Api.layout(N07Api.HALL_ZOMBIES);
      if (layout == null) return 0;
      Rotation rot = rotationFor(Direction.fromYRot(src.getRotation().y));
      // la case devant l'ouverture du haut du puits (local hall) tombe sur les pieds du joueur
      BlockPos door = layout.liftOffset.offset(2, layout.liftDrop + 1, -1);
      BlockPos at = BlockPos.containing(src.getPosition());
      BlockPos origin = at.subtract(StructureTemplate.transform(door, Mirror.NONE, rot, BlockPos.ZERO));
      PlacedHall h = N07Api.placeHall(level, N07Api.HALL_ZOMBIES, origin, rot, 6000,
         ph -> src.sendSuccess(() -> Component.translatable("command.heralisz_n07.zombies.placed", origin.getX(), origin.getY(), origin.getZ()), true));
      if (h != null) h.events(testEvents(h));
      return h != null ? 1 : 0;
   }

   private static PlacedHall nearest(CommandSourceStack src) {
      PlacedHall best = null;
      double bd = 160 * 160;
      for (PlacedHall h : HallRegistry.all()) {
         if (h.level != src.getLevel()) continue;
         double d = h.bounds().getCenter().distanceToSqr(src.getPosition());
         if (d < bd) {
            bd = d;
            best = h;
         }
      }
      if (best == null) src.sendFailure(Component.translatable("command.heralisz_n07.zombies.none"));
      return best;
   }

   /** Ecouteur du banc d'essai : journal dans le chat, outro a la victoire, retour en haut du monte-charge. */
   private static N07Events testEvents(PlacedHall h) {
      return new N07Events() {
         @Override
         public void onHook(Encounter e, String hook) {
            HeraliszN07.LOGGER.info("[n07] crochet {}", hook);
         }

         @Override
         public void onDeath(Encounter e) {
            N07Api.playGiantOutro(e, SkipMode.MAJORITY, r -> HeraliszN07.LOGGER.info("[n07] outro : {}", r));
         }

         @Override
         public void onReturnRequested(ServerPlayer p, ReturnReason reason) {
            Vec3 top = h.liftTop();
            p.teleportTo(h.level, top.x, top.y, top.z, h.yaw(180), 0);
            p.displayClientMessage(Component.translatable("command.heralisz_n07.zombies.return", p.getGameProfile().getName(), reason.name()), false);
         }
      };
   }

   private static int fight(CommandSourceStack src, boolean intro, int players, int round) {
      PlacedHall h = nearest(src);
      if (h == null) return 0;
      if (h.encounter() != null && h.encounter().active()) {
         src.sendFailure(Component.translatable("command.heralisz_n07.zombies.busy"));
         return 0;
      }
      ServerLevel level = src.getLevel();
      N07Events ev = testEvents(h);
      EncounterSpec spec = EncounterSpec.builder(level).hall(h).players(players, round)
         .players(() -> {
            List<ServerPlayer> ps = new java.util.ArrayList<>(h.playersInside());
            if (src.getEntity() instanceof ServerPlayer sp && !ps.contains(sp)) ps.add(sp);
            return ps;
         })
         .zombiesDamage(ZombiesBalance.damageScaleForRound(round), ZombiesBalance.MAX_HIT)
         .targetable(p -> !p.isCreative() && !p.isSpectator()).events(ev).build();
      Encounter e = N07Api.prepare(spec);
      if (e == null) return 0;
      if (intro) N07Api.playGiantIntro(e, null, SkipMode.MAJORITY, r -> HeraliszN07.LOGGER.info("[n07] intro : {}", r));
      else e.wake();
      src.sendSuccess(() -> Component.translatable("command.heralisz_n07.zombies.started", e.virtualHp()), true);
      return 1;
   }

   private static int reset(CommandSourceStack src) {
      PlacedHall h = nearest(src);
      if (h == null) return 0;
      h.reset(6000, () -> src.sendSuccess(() -> Component.translatable("command.heralisz_n07.zombies.reset"), true));
      return 1;
   }

   private static int evacuate(CommandSourceStack src) {
      PlacedHall h = nearest(src);
      if (h == null) return 0;
      return h.evacuate().size();
   }

   private static int clear(CommandSourceStack src) {
      PlacedHall h = nearest(src);
      if (h == null) return 0;
      N07Api.clearHall(h, 6000, null);
      return 1;
   }
}
