package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.ArriveesServeur;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * {@code /tp} de Minecraft (19/11, audit des lenteurs, point C1) : l'événement de Forge ({@code EntityTeleportCommand}) ne
 * dit pas vers quel monde part l'entité. Ici, au début de {@code performTeleport} (avant l'événement), le monde d'arrivée,
 * le lacet, le tangage, les axes relatifs et la source de la commande sont notés pour {@link ArriveesServeur} ; oubliés à
 * la fin. Le regard imposé ({@code facing}, type de Minecraft non public) n'est noté que présent ou absent. Mixin absent
 * (require = 0) : rien n'est noté, la commande reste celle de Minecraft.
 */
@Mixin(TeleportCommand.class)
public abstract class CommandeTpMixin {
   @Inject(method = "performTeleport", at = @At("HEAD"))
   private static void heraliz$noter(CommandSourceStack source, Entity entite, ServerLevel niveau, double x, double y, double z,
                                     Set<RelativeMovement> relatifs, float lacet, float tangage, @Coerce Object regard, CallbackInfo ci) {
      ArriveesServeur.noterTp(source, entite, niveau, relatifs, lacet, tangage, regard != null);
   }

   @Inject(method = "performTeleport", at = @At("RETURN"))
   private static void heraliz$oublier(CommandSourceStack source, Entity entite, ServerLevel niveau, double x, double y, double z,
                                       Set<RelativeMovement> relatifs, float lacet, float tangage, @Coerce Object regard, CallbackInfo ci) {
      ArriveesServeur.oublierTp();
   }
}
