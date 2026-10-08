package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import fr.heraliz.crac.admin.AdminManager;
import fr.heraliz.crac.placable.PlacableHpManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Traitement serveur du paquet C2SAdminPlacable (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SAdminPlacableTraitement {
    private C2SAdminPlacableTraitement() {
    }

    /** Highest HP the panel may give a placable block. */
    public static void traiter(C2SAdminPlacable msg, Supplier<Context> ctx) {
        ctx.get()
            .enqueueWork(
                () -> {
                    ServerPlayer player = ctx.get().getSender();
                    if (player != null && AdminManager.isAuthed(player)) {
                        Map<ResourceLocation, PlacableHpManager.Entry> entries = new LinkedHashMap<>();
                        int skipped = 0;

                        for (int i = 0; i < msg.ids().size(); i++) {
                            ResourceLocation id = ResourceLocation.tryParse(msg.ids().get(i).trim());
                            if (id != null && ForgeRegistries.BLOCKS.containsKey(id)) {
                                int hp = Math.max(1, Math.min(C2SAdminPlacable.MAX_HP, msg.hp().get(i)));
                                entries.put(id, new PlacableHpManager.Entry(hp, msg.enabled().get(i)));
                            } else {
                                skipped++;
                            }
                        }

                        PlacableHpManager.applyFromPanel(player.getServer(), entries);
                        AdminManager.log(player, "saved the placable register (" + entries.size() + " blocks, " + skipped + " unknown skipped)");
                        player.displayClientMessage(
                            Component.literal(
                                skipped == 0
                                    ? "§6[Admin] Placable register saved (" + entries.size() + " blocks)."
                                    : "§6[Admin] Placable register saved ("
                                        + entries.size()
                                        + "), §c"
                                        + skipped
                                        + " unknown block"
                                        + (skipped == 1 ? "" : "s")
                                        + " skipped."
                            ),
                            false
                        );
                        AdminManager.sendSnapshot(player);
                    }
                }
            );
        ctx.get().setPacketHandled(true);
    }}
