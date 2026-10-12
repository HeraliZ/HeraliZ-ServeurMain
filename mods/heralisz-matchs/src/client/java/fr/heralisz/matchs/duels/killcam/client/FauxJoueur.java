package fr.heralisz.matchs.duels.killcam.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;

/**
 * Joueur factice de la killcam, cote client seulement : jamais ajoute au monde (ni tick, ni collision, ni reseau),
 * place et anime a la main par {@link Lecteur}, dessine par {@link KillcamClient}. Le skin vient du profil (UUID du
 * vrai joueur : meme skin que dans la liste des joueurs). Jamais en spectateur (sinon seul la tete serait dessinee),
 * jamais d'etiquette de nom vanilla.
 * <p>Allonge (« ramper » de TaCZ, nage) : Minecraft couche le modele selon {@code getSwimAmount}, qui monte de 0,09 par
 * tick... dans le tick de l'entite, que ce faux joueur n'a jamais : il restait debout. Le {@link Lecteur} donne ici
 * l'avancement calcule sur l'enregistrement ({@link #nageA} -> {@link #nageB}, interpole avec le partiel du rendu).
 */
public final class FauxJoueur extends RemotePlayer {
    /** Index de palette de l'objet tenu actuellement pose (-2 = rien encore). */
    int objetPose = -2;
    /** Dessine a cette image (present dans l'enregistrement). */
    boolean visible;
    /** Corps couche (0 debout, 1 a plat) a l'image courante et a la suivante. */
    float nageA, nageB;

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

    /** Corps couche, rejoue (voir la classe) : lu par le rendu du joueur et le modele (pose a plat, bras). */
    @Override
    public float getSwimAmount(float partiel) {
        return nageA + (nageB - nageA) * partiel;
    }

    /** Une image d'animation du bras (coup, utilisation) : l'equivalent du tick vanilla. */
    void avancerBras() {
        oAttackAnim = attackAnim;
        updateSwingTime();
    }
}
