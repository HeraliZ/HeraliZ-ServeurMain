package fr.heraliz.casino.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Debut de l'expulsion du joueur (serveur -> client) : l'ecran de jeu se ferme, la camera passe en plan de cinema
 * (elle suit le joueur et les deux videurs jusqu'a la porte), bandes noires et titres.
 * @param escort entite que monte le joueur
 * @param g1     premier videur
 * @param g2     second videur
 * @param reason 0 : fauche ; 1 : sorti par un operateur
 * @param doorX  porte (point au sol)
 * @param nx     direction de l'exterieur
 */
public record ZoneCutscenePacket(int escort, int g1, int g2, int reason, double doorX, double doorY, double doorZ, double nx, double nz) {
    public static void encode(ZoneCutscenePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.escort);
        buf.writeVarInt(p.g1);
        buf.writeVarInt(p.g2);
        buf.writeVarInt(p.reason);
        buf.writeDouble(p.doorX);
        buf.writeDouble(p.doorY);
        buf.writeDouble(p.doorZ);
        buf.writeDouble(p.nx);
        buf.writeDouble(p.nz);
    }

    public static ZoneCutscenePacket decode(FriendlyByteBuf buf) {
        return new ZoneCutscenePacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble());
    }

    public static void handle(ZoneCutscenePacket p, Supplier<NetworkEvent.Context> ctx) {
        CasinoNet.RECEPTION_CLIENT.accept(p);
    }
}
