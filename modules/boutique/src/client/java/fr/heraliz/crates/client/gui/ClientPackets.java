package fr.heraliz.crates.client.gui;

import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.network.Packets;
import net.minecraft.client.Minecraft;

/**
 * Messages du serveur traites cote client (appeles seulement sur le client, via DistExecutor).
 */
public final class ClientPackets {
    private ClientPackets() {
    }

    public static void caseScreen(Packets.CaseScreen msg) {
        CrateType type = CrateType.byId(msg.crate());
        if (type == null) return;
        Minecraft.getInstance().setScreen(new CaseOpenScreen(type, msg.session(), msg.free(), msg.contents()));
    }

    public static void caseResult(Packets.CaseResult msg) {
        if (Minecraft.getInstance().screen instanceof CaseOpenScreen screen) screen.onResult(msg);
    }

    public static void editorOpen(Packets.EditorOpen msg) {
        CrateType type = CrateType.byId(msg.crate());
        if (type == null) return;
        Minecraft.getInstance().setScreen(new LootEditorScreen(type, msg.version(), msg.rows()));
    }
}
