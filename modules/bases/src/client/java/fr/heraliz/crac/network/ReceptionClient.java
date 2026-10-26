package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.client.ClientClaimCache;
import fr.heraliz.crac.client.RaidBlockOverlay;
import fr.heraliz.crac.client.admin.AdminClientState;
import fr.heraliz.crac.client.group.AddMemberScreen;
import fr.heraliz.crac.client.group.GroupCreateScreen;
import fr.heraliz.crac.client.group.GroupManageScreen;
import fr.heraliz.crac.client.group.InvitationsScreen;
import fr.heraliz.crac.group.GroupNames;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.NetworkEvent.Context;
import org.jetbrains.annotations.Nullable;

/**
 * Réception côté client des paquets serveur -> client (docs/METHODE_CLIENT_SERVEUR.md §6) : corps d'origine des
 * méthodes « handleClient » / « openScreen », branchés par client/PartieClient.
 */
public final class ReceptionClient {
    private ReceptionClient() {
    }

    /** Corps d'origine de S2CAdminData.handleClient (réception côté client). */
    public static void s2CAdminData(S2CAdminData msg) {
        AdminClientState.onData(msg);
    }

    /** Corps d'origine de S2CConfigResync.handleClient (réception côté client). */
    public static void s2CConfigResync(S2CConfigResync msg) {
        // the host of an integrated server shares the server's config objects (Forge skips the login sync too)
        if (Minecraft.getInstance().isLocalServer()) {
            return;
        }
        ModConfig config = ConfigTracker.INSTANCE.fileMap().get(msg.fileName());
        if (config == null || config.getType() != ModConfig.Type.SERVER || !Crac.MODID.equals(config.getModId())) {
            return;
        }
        try {
            config.acceptSyncedConfig(msg.data());
        } catch (RuntimeException e) {
            Crac.LOGGER.warn("Could not apply the server's {}", msg.fileName(), e);
        }
    }

    /** Corps d'origine de S2CNearbyClaims.handleClient (réception côté client). */
    public static void s2CNearbyClaims(S2CNearbyClaims msg) {
        ClientClaimCache.set(msg.claims());
    }

    /** Corps d'origine de S2COnlinePlayers.openScreen (réception côté client). */
    public static void s2COnlinePlayers(S2COnlinePlayers msg) {
        AddMemberScreen.open(msg.players());
    }

    /** Corps d'origine de S2COpenGroupCreate.openScreen (réception côté client). */
    public static void s2COpenGroupCreate(S2COpenGroupCreate msg) {
        Minecraft.getInstance().setScreen(new GroupCreateScreen());
    }

    /** Corps d'origine de S2COpenGroupManage.openScreen (réception côté client). */
    public static void s2COpenGroupManage(S2COpenGroupManage msg) {
        GroupManageScreen.openOrRefresh(msg);
    }

    /** Corps d'origine de S2COpenInvites.openScreen (réception côté client). */
    public static void s2COpenInvites(S2COpenInvites msg) {
        InvitationsScreen.open(msg.invites());
    }

    /** Corps d'origine de S2CRaidBlock.handleClient (réception côté client). */
    public static void s2CRaidBlock(S2CRaidBlock msg) {
        RaidBlockOverlay.setRemainingSeconds(msg.remainingSeconds, msg.ownSeconds);
    }
}
