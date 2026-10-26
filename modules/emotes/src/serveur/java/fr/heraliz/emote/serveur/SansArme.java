package fr.heraliz.emote.serveur;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Pair;
import fr.heraliz.emote.EmoteRules;
import fr.heraliz.emote.reseau.ReseauEmotes;
import io.github.kosmx.emotes.api.events.server.ServerEmoteAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pas d'arme en main pendant une emote et {@link EmoteRules#SANS_ARME_MS} après (l'emote montre le joueur en vue à la
 * 3e personne : elle ne doit pas servir à regarder derrière un mur puis tirer). Décidé par le serveur à chaque tick : une
 * arme ({@link EmoteRules#estArme}) en main principale fait passer à la première case de la barre qui n'en est pas une
 * (sinon l'arme est rangée dans l'inventaire, échangée avec un objet qui n'en est pas une) ; une arme en main gauche est
 * rangée de même. Objets toujours déplacés, jamais copiés. Créatif et spectateur non concernés.
 * <p>Le délai part de la fin de l'emote vue par le serveur (arrêtée par le joueur, finie, refusée) : l'annuler ne fait
 * rien gagner. En plus (25/10) : tout tir et coup de TaCZ est refusé pendant ce temps ({@link TirsSansArme}, même si
 * l'arme n'a pas pu quitter la main ou si TaCZ tire avec l'arme sortie plus tôt), le jeu du joueur reçoit le temps
 * restant ({@link ReseauEmotes.SansArme} : pas de tir « fantôme » à l'écran), et le délai survit à une déconnexion
 * (clé {@link EmoteRules#SANS_ARME_JUSQUA} des données du joueur, relue à la connexion) et à la mort.
 */
public final class SansArme {
    /** Joueur -> fin de l'interdiction (ms). */
    private static final Map<UUID, Long> JUSQUA = new HashMap<>();
    private static final Map<UUID, Long> DERNIER_MESSAGE = new HashMap<>();
    /** Joueurs dont le serveur voyait l'emote au tick d'avant (fin de l'emote = envoi du délai au joueur). */
    private static final Set<UUID> EN_EMOTE = new HashSet<>();

    private SansArme() {
    }

    public static void tick(ServerPlayer p) {
        long now = System.currentTimeMillis();
        UUID id = p.getUUID();
        if (enEmote(id)) {
            JUSQUA.put(id, now + EmoteRules.SANS_ARME_MS);
            EN_EMOTE.add(id);
        } else if (EN_EMOTE.remove(id)) {
            envoyer(p, EmoteRules.SANS_ARME_MS);
        }
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
        if (bouge) prevenir(p, fin - now);
    }

    /** Temps restant sans arme (ms), 0 si rien n'est interdit (créatif et spectateur : toujours 0). Thread serveur. */
    public static long restant(ServerPlayer p) {
        if (Deblocages.tout(p)) return 0;
        UUID id = p.getUUID();
        if (enEmote(id)) return EmoteRules.SANS_ARME_MS;
        Long fin = JUSQUA.get(id);
        return fin == null ? 0 : Math.max(0, fin - System.currentTimeMillis());
    }

    /** Le message « pas d'arme » au-dessus de la barre, au plus une fois par seconde. */
    static void prevenir(ServerPlayer p, long restantMs) {
        long now = System.currentTimeMillis();
        if (now - DERNIER_MESSAGE.getOrDefault(p.getUUID(), 0L) <= 1000) return;
        DERNIER_MESSAGE.put(p.getUUID(), now);
        p.displayClientMessage(EmoteRules.sansArme(restantMs), true);
    }

    /** Connexion : le délai laissé à la déconnexion reprend (son jeu en est prévenu). */
    public static void connexion(ServerPlayer p) {
        CompoundTag data = p.getPersistentData();
        if (!data.contains(EmoteRules.SANS_ARME_JUSQUA)) return;
        long reste = data.getLong(EmoteRules.SANS_ARME_JUSQUA) - System.currentTimeMillis();
        data.remove(EmoteRules.SANS_ARME_JUSQUA);
        if (reste <= 0) return;
        // jamais plus que le délai normal (horloge du serveur changée entre-temps)
        reste = Math.min(reste, EmoteRules.SANS_ARME_MS);
        JUSQUA.put(p.getUUID(), System.currentTimeMillis() + reste);
        envoyer(p, reste);
    }

    /** Déconnexion (Forge la signale avant de sauvegarder le joueur) : un délai en cours est gardé dans ses données. */
    public static void deconnexion(ServerPlayer p) {
        UUID id = p.getUUID();
        long now = System.currentTimeMillis();
        long fin = enEmote(id) ? now + EmoteRules.SANS_ARME_MS : JUSQUA.getOrDefault(id, 0L);
        if (fin > now) p.getPersistentData().putLong(EmoteRules.SANS_ARME_JUSQUA, fin);
        JUSQUA.remove(id);
        DERNIER_MESSAGE.remove(id);
        EN_EMOTE.remove(id);
    }

    private static void envoyer(ServerPlayer p, long restantMs) {
        try {
            ReseauEmotes.envoyer(p, new ReseauEmotes.SansArme((int) Math.min(restantMs, EmoteRules.SANS_ARME_MS)));
        } catch (RuntimeException ignored) {
            // joueur sans le canal (jeu sans HeraliZ Emote) : le serveur décide seul
        }
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
