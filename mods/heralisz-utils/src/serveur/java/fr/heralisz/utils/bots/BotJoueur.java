package fr.heralisz.utils.bots;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bot de charge : un vrai {@link ServerPlayer}, inscrit dans la liste des joueurs comme une connexion, relié à un canal
 * réseau factice qui jette tout ce qu'on lui envoie (voir {@link Bots#creer}). Le serveur le traite comme un joueur :
 * mêmes armes, mêmes balles, mêmes dégâts, même cadavre, mêmes morceaux de carte chargés. Sans jeu de joueur, il tique
 * lui-même ce que la connexion fait tiquer d'habitude ({@code doTick}) et son cerveau ({@link Cerveau}) remplit ses
 * touches (avancer, côté, saut, course, viser, tirer, lancer, se soigner).
 */
final class BotJoueur extends ServerPlayer {
    final Cerveau cerveau;

    BotJoueur(MinecraftServer s, ServerLevel l, GameProfile g) {
        super(s, l, g);
        this.cerveau = new Cerveau(this);
        addTag(Bots.TAG);
    }

    @Override
    public void tick() {
        // le joueur suit ses morceaux de carte quand ses paquets de position arrivent : ici, aucun paquet
        if (getServer() != null && getServer().getTickCount() % 10 == 0) serverLevel().getChunkSource().move(this);
        super.tick();
        if (isAlive() && !hasDisconnected()) {
            try {
                cerveau.tick();
            } catch (RuntimeException ex) {
                Bots.erreur(this, ex);
            }
        }
        doTick();
    }
}
