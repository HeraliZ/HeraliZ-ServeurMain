package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import fr.heraliz.crac.admin.AdminConfigAccess;
import fr.heraliz.crac.admin.AdminManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Traitement serveur du paquet C2SAdminConfig (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SAdminConfigTraitement {
    private C2SAdminConfigTraitement() {
    }

    public static void traiter(C2SAdminConfig msg, Supplier<Context> ctx) {
        ctx.get()
            .enqueueWork(
                () -> {
                    ServerPlayer player = ctx.get().getSender();
                    if (player != null && AdminManager.isAuthed(player)) {
                        int applied = 0;
                        int failed = 0;

                        for (int i = 0; i < msg.paths().size(); i++) {
                            String path = msg.paths().get(i);
                            ConfigValue<?> cv = AdminConfigAccess.values().get(path);
                            String before = cv == null ? null : AdminConfigAccess.asString(cv);
                            if (AdminConfigAccess.apply(path, msg.values().get(i))) {
                                applied++;
                                AdminManager.log(player, "set config " + path + ": '" + before + "' -> '" + AdminConfigAccess.asString(cv) + "'");
                            } else {
                                failed++;
                            }
                        }

                        if (applied > 0) {
                            AdminConfigAccess.save();
                            // clients only get SERVER configs at login: send the new file now
                            if (player.getServer() != null) {
                                ConfigResync.pushChanged(player.getServer());
                            }
                        }

                        player.displayClientMessage(
                            failed == 0
                                ? Component.translatableWithFallback("crac.admin.config_applied",
                                    "§6[Admin] %s changement(s) de config appliqué(s).", String.valueOf(applied))
                                : Component.translatableWithFallback("crac.admin.config_refused",
                                    "§6[Admin] %s appliqué(s), §c%s refusé(s) (clé inconnue, mauvais type ou hors limites).",
                                    String.valueOf(applied), String.valueOf(failed)),
                            false
                        );
                        AdminManager.sendSnapshot(player);
                    }
                }
            );
        ctx.get().setPacketHandled(true);
    }}
