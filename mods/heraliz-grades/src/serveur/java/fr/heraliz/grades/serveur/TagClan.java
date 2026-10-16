package fr.heraliz.grades.serveur;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

/**
 * Tag de clan du joueur (« [TAG] » dans la couleur du clan), lu par réflexion dans heraliz-clansetbases
 * ({@code ClanStore.get().clanDe(uuid)} puis {@code ClanService.prefixe(clan)}) : aucune dépendance de compilation ; sans
 * le mod des clans (ou si son API change), simplement pas de tag.
 */
final class TagClan {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean cherche, absent;
    private static Method get, clanDe, prefixe;

    private TagClan() {
    }

    private static boolean brancher() {
        if (cherche) return !absent;
        cherche = true;
        try {
            Class<?> store = Class.forName("fr.heralisz.clans.server.ClanStore");
            get = store.getMethod("get");
            clanDe = store.getMethod("clanDe", UUID.class);
            prefixe = Class.forName("fr.heralisz.clans.server.ClanService").getMethod("prefixe", clanDe.getReturnType());
        } catch (ReflectiveOperationException | LinkageError ex) {
            absent = true;
            LOG.info("[grades] tags de clan indisponibles ({}) : pas de tag de clan devant les pseudos", ex.toString());
        }
        return !absent;
    }

    /** « [TAG] » en couleur, ou null sans clan. */
    static MutableComponent prefixe(ServerPlayer p) {
        if (!brancher()) return null;
        try {
            Object store = get.invoke(null);
            if (store == null) return null;
            Object clan = clanDe.invoke(store, p.getUUID());
            return clan == null ? null : ((MutableComponent) prefixe.invoke(null, clan)).copy();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            absent = true;
            LOG.warn("[grades] lecture du tag de clan impossible, tags de clan coupés : {}", ex.toString());
            return null;
        }
    }

    /** Texte du tag (sert à voir s'il a changé), "" sans clan. */
    static String texte(MutableComponent c) {
        return c == null ? "" : c.getString() + "#" + (c.getStyle().getColor() == null ? "" : c.getStyle().getColor().serialize());
    }

    /** Le tag en codes couleur Bukkit (§x§R§R§G§G§B§B), "" sans clan. */
    static String bukkit(MutableComponent c) {
        if (c == null) return "";
        TextColor tc = c.getStyle().getColor();
        StringBuilder sb = new StringBuilder();
        if (tc != null) {
            sb.append("§x");
            for (char ch : String.format(Locale.ROOT, "%06X", tc.getValue() & 0xFFFFFF).toCharArray()) sb.append('§').append(ch);
        }
        return sb.append(c.getString().replace('§', ' ')).append("§r").toString();
    }
}
