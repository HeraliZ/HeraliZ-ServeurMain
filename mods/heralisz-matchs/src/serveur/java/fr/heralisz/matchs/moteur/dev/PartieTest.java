package fr.heralisz.matchs.moteur.dev;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.KillFeed;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.spectateur.Spectateurs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * Partie minimale du banc d'essai du moteur (jamais creee en jeu) : journalise les eliminations, fait du joueur un
 * spectateur et ajoute une ligne au fil.
 */
public final class PartieTest extends Partie {
    public int eliminations;
    public String derniereSource = "";

    public PartieTest(String id, MinecraftServer serveur) {
        super(id, "test", serveur);
    }

    @Override
    public void tick() {
        // rien : la partie de test est pilotee par les commandes du banc
    }

    @Override
    public void surElimination(ServerPlayer victime, @Nullable LivingEntity tueur, DamageSource source) {
        eliminations++;
        derniereSource = source.getMsgId();
        Equipe eq = equipeDe(victime.getUUID());
        if (eq != null) eq.eliminer(victime.getUUID());
        HeraliszMatchs.LOG.info("[matchs] BANC elimination de {} (source {}, tueur {}) : vivant={}, sante={}", victime.getGameProfile().getName(),
                derniereSource, tueur == null ? "-" : tueur.getName().getString(), victime.isAlive(), victime.getHealth());
        KillFeed.ajouter(this, new KillFeed.Ligne(tueur == null ? "" : tueur.getName().getString(), 0xE85D5D,
                victime.getGameProfile().getName(), eq == null ? 0xFFFFFF : eq.couleur(), "", false));
        Spectateurs.suivre(victime, this);
    }

    @Override
    public void surDeconnexion(ServerPlayer joueur) {
        HeraliszMatchs.LOG.info("[matchs] BANC deconnexion de {}", joueur.getGameProfile().getName());
    }
}
