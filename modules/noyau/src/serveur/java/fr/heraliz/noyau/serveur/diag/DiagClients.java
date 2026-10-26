package fr.heraliz.noyau.serveur.diag;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.net.NoyauNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /diag client <joueur> [secondes]} : le jeu du joueur enregistre ses images (FPS, images lentes, mod
 * responsable, temps de dessin par type d'entité et d'entité de bloc) puis renvoie son rapport, écrit dans
 * {@code diag/client-<pseudo>-<date>.txt}. Un rapport n'est accepté que s'il répond à une demande en cours (jeton,
 * délai), plafonné en taille ({@link NoyauNet#RAPPORT_MAX}). Fil serveur.
 */
public final class DiagClients {
    private DiagClients() {
    }

    private static final Logger LOG = LogUtils.getLogger();
    private static final DateTimeFormatter NOM = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private record Demande(int jeton, UUID demandeur, long limite) {
    }

    private static final Map<UUID, Demande> EN_COURS = new HashMap<>();

    public static void enregistrer() {
        NoyauNet.DIAG_RAPPORT.brancher(e -> recevoir(e.getKey(), e.getValue()));
    }

    /** Envoie la demande ; faux si le joueur a déjà une demande en cours. */
    static boolean demander(ServerPlayer joueur, int secondes, UUID demandeur) {
        long maintenant = System.currentTimeMillis();
        Demande d = EN_COURS.get(joueur.getUUID());
        if (d != null && d.limite > maintenant) return false;
        int jeton = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        EN_COURS.put(joueur.getUUID(), new Demande(jeton, demandeur, maintenant + (secondes + 30) * 1000L));
        NoyauNet.versJoueur(joueur, new NoyauNet.DiagDemandeS2C(secondes, jeton));
        return true;
    }

    private static void recevoir(ServerPlayer de, NoyauNet.DiagRapportC2S r) {
        Demande d = EN_COURS.get(de.getUUID());
        if (d == null || d.jeton != r.jeton() || System.currentTimeMillis() > d.limite) {
            LOG.debug("[diag] rapport de {} sans demande en cours : ignoré", de.getGameProfile().getName());
            return;
        }
        EN_COURS.remove(de.getUUID());
        MinecraftServer srv = de.server;
        String pseudo = de.getGameProfile().getName().replaceAll("[^A-Za-z0-9_]", "_");
        String nom = "client-" + pseudo + "-" + LocalDateTime.now().format(NOM) + ".txt";
        String texte = "HeraliZ — rapport du jeu de " + pseudo + " (envoyé par son jeu, non vérifié)\n" + r.texte();
        Diag.ecrire(srv, nom, texte);
        String resume = r.resume().replaceAll("[\\p{Cntrl}§]", " ");
        Component c = Component.translatableWithFallback("noyau.diag.client_recu", "[Diag] Jeu de %s : %s — rapport diag/%s",
                pseudo, resume, nom).withStyle(s -> s.withColor(0x8FD3FF));
        ServerPlayer qui = d.demandeur == null ? null : srv.getPlayerList().getPlayer(d.demandeur);
        if (qui != null) qui.sendSystemMessage(c);
        else srv.sendSystemMessage(c);
    }

    public static void oublier(UUID joueur) {
        EN_COURS.remove(joueur);
    }
}
