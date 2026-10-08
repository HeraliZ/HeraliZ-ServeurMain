package fr.heraliz.casino.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Un videur arrete le joueur a l'entree du casino (serveur -> client) : le regard du joueur se tourne vers lui et la
 * fenetre de dialogue s'ouvre (payer l'entree, ou partir).
 * @param guard   identifiant de l'entite du videur (-1 : aucun a portee)
 * @param line    ce que dit le videur
 * @param price   prix de l'entree (jetons)
 * @param payWith ce qui sera pris au joueur ("100 jetons", "10 x Emeraude")
 * @param change  monnaie rendue en jetons (paiement en objets)
 * @param canPay  le joueur a de quoi payer
 * @param minutes duree du ticket (0 : une seule entree)
 */
public record ZoneDialogPacket(int guard, Component line, long price, Component payWith, long change, boolean canPay, int minutes) {
    public static void encode(ZoneDialogPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.guard);
        buf.writeComponent(p.line);
        buf.writeVarLong(p.price);
        buf.writeComponent(p.payWith);
        buf.writeVarLong(p.change);
        buf.writeBoolean(p.canPay);
        buf.writeVarInt(p.minutes);
    }

    public static ZoneDialogPacket decode(FriendlyByteBuf buf) {
        return new ZoneDialogPacket(buf.readVarInt(), buf.readComponent(), buf.readVarLong(), buf.readComponent(), buf.readVarLong(), buf.readBoolean(),
                buf.readVarInt());
    }

    public static void handle(ZoneDialogPacket p, Supplier<NetworkEvent.Context> ctx) {
        CasinoNet.RECEPTION_CLIENT.accept(p);
    }
}
