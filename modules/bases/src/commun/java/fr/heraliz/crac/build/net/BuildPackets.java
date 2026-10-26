package fr.heraliz.crac.build.net;

import java.util.Optional;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

/** Registers the building packets on the mod channel, each restricted to its direction. */
public final class BuildPackets {
    private BuildPackets() {
    }

    /** Registers from message index {@code id} on; returns the next free index. */
    public static int register(SimpleChannel channel, int id) {
        Optional<NetworkDirection> toServer = Optional.of(NetworkDirection.PLAY_TO_SERVER);
        Optional<NetworkDirection> toClient = Optional.of(NetworkDirection.PLAY_TO_CLIENT);
        channel.registerMessage(id++, C2SPlacePiece.class, C2SPlacePiece::encode, C2SPlacePiece::decode, C2SPlacePiece::handle, toServer);
        channel.registerMessage(id++, C2SHammer.class, C2SHammer::encode, C2SHammer::decode, C2SHammer::handle, toServer);
        channel.registerMessage(id++, C2SPieceInfo.class, C2SPieceInfo::encode, C2SPieceInfo::decode, C2SPieceInfo::handle, toServer);
        channel.registerMessage(id++, S2CPieceInfo.class, S2CPieceInfo::encode, S2CPieceInfo::decode, S2CPieceInfo::handle, toClient);
        return id;
    }
}
