package fr.heralisz.n07.dev;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.api.DevControl;
import net.minecraft.world.entity.Entity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.Locale;

/**
 * /n07 : commande d'admin pour tester le boss (déclencher une attaque, sauter à une phase, régler ses PV, le rendre passif).
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID)
public final class DevCommand {
   private DevCommand() {}

   private static DevControl nearest(CommandSourceStack src) {
      Entity best = src.getLevel().getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(src.getPosition(), src.getPosition()).inflate(128),
            e -> e instanceof DevControl && e.isAlive())
         .stream().min(Comparator.comparingDouble(e -> e.position().distanceTo(src.getPosition()))).orElse(null);
      return (DevControl) best;
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent e) {
      for (String root : new String[] {"n07dev"}) e.getDispatcher().register(Commands.literal(root).requires(s -> s.hasPermission(2))
         .then(Commands.literal("action").then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
            DevControl b = nearest(c.getSource());
            if (b == null) return 0;
            if (!b.devAction(StringArgumentType.getString(c, "name").toUpperCase(Locale.ROOT))) {
               c.getSource().sendFailure(Component.translatableWithFallback("command.heralisz_n07.dev.unknown_action", "Action inconnue"));
               return 0;
            }
            return 1;
         })))
         .then(Commands.literal("phase").then(Commands.argument("n", IntegerArgumentType.integer(1, 3)).executes(c -> {
            DevControl b = nearest(c.getSource());
            if (b == null) return 0;
            b.devPhase(IntegerArgumentType.getInteger(c, "n"));
            return 1;
         })))
         .then(Commands.literal("hp").then(Commands.argument("fraction", FloatArgumentType.floatArg(0.01F, 1F)).executes(c -> {
            DevControl b = nearest(c.getSource());
            if (!(b instanceof net.minecraft.world.entity.LivingEntity le)) return 0;
            le.setHealth(le.getMaxHealth() * FloatArgumentType.getFloat(c, "fraction"));
            return 1;
         })))
         .then(Commands.literal("passive").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
            DevControl b = nearest(c.getSource());
            if (b == null) return 0;
            b.setDevPassive(BoolArgumentType.getBool(c, "on"));
            return 1;
         }))));
   }
}
