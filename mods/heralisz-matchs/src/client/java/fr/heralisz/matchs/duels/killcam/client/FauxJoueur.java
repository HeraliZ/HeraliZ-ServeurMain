package fr.heralisz.matchs.duels.killcam.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;

/**
 * Joueur factice de la killcam, cote client seulement : jamais ajoute au monde (ni tick, ni collision, ni reseau),
 * place et anime a la main par {@link Lecteur}, dessine par {@link KillcamClient}. Le skin vient du profil (UUID du
 * vrai joueur : meme skin que dans la liste des joueurs). Jamais en spectateur (sinon seul la tete serait dessinee),
 * jamais d'etiquette de nom vanilla.
 */
public final class FauxJoueur extends RemotePlayer {
    /** Index de palette de l'objet tenu actuellement pose (-2 = rien encore). */
    int objetPose = -2;
    /** Dessine a cette image (present dans l'enregistrement). */
    boolean visible;

    /** Texture imposee (participant qui n'est pas un joueur : le mannequin d'entrainement), sinon le skin du profil. */
    @javax.annotation.Nullable
    private net.minecraft.resources.ResourceLocation peau;

    public FauxJoueur(ClientLevel niveau, GameProfile profil) {
        super(niveau, profil);
        noPhysics = true;
    }

    /** Impose une texture au modele de joueur (meme disposition 64 x 64 que celle du mannequin). */
    void peau(net.minecraft.resources.ResourceLocation texture) {
        this.peau = texture;
    }

    @Override
    public net.minecraft.resources.ResourceLocation getSkinTextureLocation() {
        return peau != null ? peau : super.getSkinTextureLocation();
    }

    @Override
    public String getModelName() {
        return peau != null ? "default" : super.getModelName();
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return false;
    }

    /** Une image d'animation du bras (coup, utilisation) : l'equivalent du tick vanilla. */
    void avancerBras() {
        oAttackAnim = attackAnim;
        updateSwingTime();
    }
}
