package fr.heraliz.emote.serveur;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Pair;
import fr.heraliz.emote.EmoteRules;
import io.github.kosmx.emotes.api.events.server.ServerEmoteAPI;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pas d'arme en main pendant une emote et {@link EmoteRules#SANS_ARME_MS} après (l'emote montre le joueur en vue à la
 * 3e personne : elle ne doit pas servir à regarder derrière un mur puis tirer). Décidé par le serveur à chaque tick : une
 * arme ({@link EmoteRules#estArme}) en main principale fait passer à la première case de la barre qui n'en est pas une
 * (sinon l'arme est rangée dans l'inventaire, échangée avec un objet qui n'en est pas une) ; une arme en main gauche est
 * rangée de même. Objets toujours déplacés, jamais copiés. Créatif et spectateur non concernés. En mémoire seulement.
 */
public final class SansArme {
    /** Joueur -> fin de l'interdiction (ms). */
    private static final Map<UUID, Long> JUSQUA = new HashMap<>();
    private static final Map<UUID, Long> DERNIER_MESSAGE = new HashMap<>();

    private SansArme() {
    }

    public static void tick(ServerPlayer p) {
        long now = System.currentTimeMillis();
        UUID id = p.getUUID();
        if (enEmote(id)) JUSQUA.put(id, now + EmoteRules.SANS_ARME_MS);
        Long fin = JUSQUA.get(id);
        if (fin == null) return;
        if (now >= fin) {
            JUSQUA.remove(id);
            return;
        }
        if (Deblocages.tout(p)) return;
        Inventory inv = p.getInventory();
        boolean bouge = false;
        if (EmoteRules.estArme(inv.getSelected())) bouge = lacher(p, inv);
        if (EmoteRules.estArme(inv.offhand.get(0)) && ranger(inv, Inventory.SLOT_OFFHAND)) bouge = true;
        if (bouge && now - DERNIER_MESSAGE.getOrDefault(id, 0L) > 1000) {
            DERNIER_MESSAGE.put(id, now);
            p.displayClientMessage(EmoteRules.sansArme(fin - now), true);
        }
    }

    public static void oublier(UUID id) {
        JUSQUA.remove(id);
        DERNIER_MESSAGE.remove(id);
    }

    private static boolean enEmote(UUID id) {
        try {
            Pair<KeyframeAnimation, Integer> joue = ServerEmoteAPI.getPlayedEmote(id);
            return joue != null && joue.getLeft() != null;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean lacher(ServerPlayer p, Inventory inv) {
        for (int i = 1; i <= Inventory.getSelectionSize(); i++) {
            int s = (inv.selected + i) % Inventory.getSelectionSize();
            if (!EmoteRules.estArme(inv.getItem(s))) {
                inv.selected = s;
                p.connection.send(new ClientboundSetCarriedItemPacket(s));
                return true;
            }
        }
        return ranger(inv, inv.selected);
    }

    /** Échange l'objet de cette case avec une case du sac de l'inventaire qui n'a pas d'arme. */
    private static boolean ranger(Inventory inv, int caseArme) {
        for (int i = Inventory.getSelectionSize(); i < inv.items.size(); i++) {
            ItemStack autre = inv.getItem(i);
            if (EmoteRules.estArme(autre)) continue;
            ItemStack arme = inv.getItem(caseArme);
            inv.setItem(i, arme);
            inv.setItem(caseArme, autre);
            return true;
        }
        return false;
    }
}
