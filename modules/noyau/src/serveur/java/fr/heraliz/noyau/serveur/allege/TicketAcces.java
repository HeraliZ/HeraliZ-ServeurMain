package fr.heraliz.noyau.serveur.allege;

import net.minecraft.server.level.Ticket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Tickets à échéance (20/11, {@link TicketsEcheanceMixin}) : la question de Minecraft « ce ticket a-t-il fini son temps ? »
 * et le tick de sa pose (temps compté par la carte des tickets). À part des autres accès : un accès introuvable ici ne coupe
 * que ce point.
 */
@Mixin(Ticket.class)
public interface TicketAcces {
   @Invoker("timedOut")
   boolean heraliz$finiAu(long tick);

   @Accessor("createdTick")
   long heraliz$pose();
}
