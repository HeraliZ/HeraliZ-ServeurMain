package fr.heralisz.clans.territory;

import fr.heraliz.clansetbases.pont.Crochet;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.EtatZone;
import fr.heralisz.clans.registry.ClansRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Bloc-entite du drapeau : identifiant et nom de la zone, tag et banniere du proprietaire, etat discret (NEUTRE,
 * TENUE, CAPTURE, CONTESTE : mi-mat pour les deux derniers). Tout est synchronise par le serveur (NBT : paquet a
 * l'entree du chunk + {@link #synchroniser()} a chaque changement). Aucun tick, ni client ni serveur ; au chargement
 * cote serveur, {@code Territoires} (partie serveur, par {@link #CHARGEMENT_SERVEUR}) lui redonne l'etat courant de sa zone.
 */
public class DrapeauBlockEntity extends BlockEntity {
    public String zoneId = "";
    public String zoneNom = "";
    public String proprioTag = "";
    @Nullable
    public Banniere banniere;
    public EtatZone etat = EtatZone.NEUTRE;
    /**
     * Cercle de capture (ClansConfig.RAYON_CAPTURE / HAUTEUR_CAPTURE, config COMMON non envoyee aux joueurs) : recopie
     * par le serveur dans le NBT synchronise pour que le rendu dessine la zone de capture ; 10 / 6 par defaut.
     */
    public int rayonCapture = RAYON_DEFAUT, hauteurCapture = HAUTEUR_DEFAUT;
    public static final int RAYON_DEFAUT = 10, HAUTEUR_DEFAUT = 6;
    /** Cache du rendu client (etiquette) : cle et objet, geres par DrapeauRenderer. */
    public transient String clientCle;
    public transient Object clientCache;
    /** Cache du rendu client (mur de la zone de capture) : hauteur du sol par segment, gere par DrapeauRenderer. */
    public transient float[] clientSol;
    public transient long clientSolTick = Long.MIN_VALUE;
    public transient int clientSolCle;

    /** Chargement cote serveur (Territoires.rafraichirDrapeau) : branche par la partie serveur, neutre sans elle. */
    public static final Crochet<DrapeauBlockEntity> CHARGEMENT_SERVEUR = new Crochet<>();

    public DrapeauBlockEntity(BlockPos pos, BlockState state) {
        super(ClansRegistry.DRAPEAU_BE.get(), pos, state);
    }

    /** Applique un etat ; vrai si quelque chose a change (a synchroniser). */
    public boolean appliquer(String zoneId, String zoneNom, String proprioTag, @Nullable Banniere banniere, EtatZone etat) {
        zoneId = zoneId == null ? "" : zoneId;
        zoneNom = zoneNom == null ? "" : zoneNom;
        proprioTag = proprioTag == null ? "" : proprioTag;
        etat = etat == null ? EtatZone.NEUTRE : etat;
        boolean cercle = lireCercle();
        if (!cercle && this.zoneId.equals(zoneId) && this.zoneNom.equals(zoneNom) && this.proprioTag.equals(proprioTag)
                && Objects.equals(this.banniere, banniere) && this.etat == etat) return false;
        this.zoneId = zoneId;
        this.zoneNom = zoneNom;
        this.proprioTag = proprioTag;
        this.banniere = banniere;
        this.etat = etat;
        return true;
    }

    /** Cote serveur : relit le cercle de capture dans la config ; vrai s'il a change. */
    private boolean lireCercle() {
        if (level == null || level.isClientSide) return false;
        int r, h;
        try {
            r = fr.heralisz.clans.ClansConfig.RAYON_CAPTURE.get();
            h = fr.heralisz.clans.ClansConfig.HAUTEUR_CAPTURE.get();
        } catch (RuntimeException e) {
            return false; // config pas encore chargee : on garde les valeurs actuelles
        }
        if (r == rayonCapture && h == hauteurCapture) return false;
        rayonCapture = r;
        hauteurCapture = h;
        return true;
    }

    /** Tissu a mi-mat : capture en cours ou zone contestee. */
    public boolean miMat() {
        return etat == EtatZone.CAPTURE || etat == EtatZone.CONTESTE;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            CHARGEMENT_SERVEUR.accept(this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("zone", zoneId);
        tag.putString("zoneNom", zoneNom);
        tag.putString("proprio", proprioTag);
        tag.putInt("etat", etat.ordinal());
        tag.putInt("rayonCapture", rayonCapture);
        tag.putInt("hauteurCapture", hauteurCapture);
        // banniere v2 en JSON compact (calques, formes par cle) ; voir load() pour l'ancien format
        if (banniere != null) tag.putString("banniere", banniere.toJson().toString());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        zoneId = tag.getString("zone");
        zoneNom = tag.getString("zoneNom");
        proprioTag = tag.getString("proprio");
        etat = EtatZone.parOrdinal(tag.getInt("etat"));
        rayonCapture = tag.contains("rayonCapture") ? Math.max(1, Math.min(64, tag.getInt("rayonCapture"))) : RAYON_DEFAUT;
        hauteurCapture = tag.contains("hauteurCapture") ? Math.max(1, Math.min(64, tag.getInt("hauteurCapture"))) : HAUTEUR_DEFAUT;
        banniere = null;
        if (tag.contains("banniere")) {
            // migration : un ancien drapeau (v1 : embleme + couleurEmbleme, sans calques) devient une banniere v2 a un
            // calque embleme centre ; Banniere.fromJson s'en charge et borne tout
            try {
                banniere = Banniere.fromJson(com.google.gson.JsonParser.parseString(tag.getString("banniere")).getAsJsonObject());
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        lireCercle(); // serveur : toujours les valeurs de la config dans ce qui part aux joueurs
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Boite de rendu : le mat fait 5 blocs, le tissu depasse de 2 blocs, l'etiquette est a 5,7 ; le mur de la zone de
     * capture va jusqu'a rayonCapture (+1) autour et hauteurCapture (+2, hauteur du mur) au-dessus et en dessous.
     */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        double r = Math.max(3, rayonCapture + 1);
        int h = hauteurCapture;
        return new AABB(p.getX() + 0.5 - r, p.getY() - h, p.getZ() + 0.5 - r,
                p.getX() + 0.5 + r, p.getY() + Math.max(7, h + 2), p.getZ() + 0.5 + r);
    }

    /** A appeler cote serveur apres un changement d'etat : sauvegarde + envoi aux clients. */
    public void synchroniser() {
        setChanged();
        if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }
}
