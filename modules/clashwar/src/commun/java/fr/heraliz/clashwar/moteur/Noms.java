package fr.heraliz.clashwar.moteur;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Noms de joueurs par UUID (connecte, sinon cache des profils du serveur, sinon noms factices du banc d'essai, sinon
 * debut de l'UUID). Fil serveur.
 */
public final class Noms {
    private Noms() {
    }

    /** Noms des joueurs factices du banc d'essai (jamais rempli en jeu). */
    public static final Map<UUID, String> FACTICES = new HashMap<>();

    public static String nom(MinecraftServer s, UUID u) {
        ServerPlayer p = s.getPlayerList().getPlayer(u);
        if (p != null) return p.getGameProfile().getName();
        String f = FACTICES.get(u);
        if (f != null) return f;
        if (s.getProfileCache() != null) {
            Optional<GameProfile> g = s.getProfileCache().get(u);
            if (g.isPresent() && g.get().getName() != null) return g.get().getName();
        }
        return u.toString().substring(0, 8);
    }
}
