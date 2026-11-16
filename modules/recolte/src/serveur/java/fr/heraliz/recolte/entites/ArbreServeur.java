package fr.heraliz.recolte.entites;

import fr.heraliz.recolte.objets.ObjetsRecolte;
import fr.heraliz.recolte.objets.OutilRecolte;
import fr.heraliz.recolte.serveur.Coups;
import fr.heraliz.recolte.serveur.Effets;
import fr.heraliz.recolte.serveur.Reglages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Partie serveur d'un arbre à abattre (docs/METHODE_CLIENT_SERVEUR.md §5.1), créée par {@link EntitesRecolte#ARBRE_USINE} :
 * le serveur juge chaque coup de hache (attaque de Minecraft, {@link #skipAttackInteraction}).
 * <ul>
 *    <li>Travail et matière comme un gisement : avance d'1 / coups nécessaires avec l'outil tenu, partagée entre les
 *    joueurs et gardée avec l'entité ; bûches au prorata (2 par coup à la hache), même total quel que soit l'outil.</li>
 *    <li>{@link #COUPS} (entaille) : 0, 1 puis 2 aux tiers du travail, 3 au dernier coup ; {@link #DIRECTION} : à
 *    l'opposé du joueur à chaque coup, figée au dernier.</li>
 *    <li>Au dernier coup, {@link #CHUTE} = heure de jeu : il craque et tombe chez les joueurs ; à l'impact (≈ 2,4 s),
 *    poussière et feuilles le long de la chute ; 2 s plus tard, les bûches de la chute vont au joueur qui l'a abattu
 *    (au sol s'il n'est plus là) et {@link #SOUCHE} = vrai ; la souche reste 10 minutes (réglage
 *    {@code souche_minutes}) puis disparaît. L'arbre qui tombe ne blesse personne (dès la chute, sa boîte est celle de
 *    la souche).</li>
 * </ul>
 */
public class ArbreServeur extends Arbre {
    /** Ticks entre le dernier coup et l'impact (≈ 2,4 s, comme le rendu), puis jusqu'aux dernières bûches (2 s de plus). */
    public static final int IMPACT = 48, FIN = IMPACT + 40;
    /** Longueur du tronc couché (m) : bouleau 9,2 m, épicéa 12 m. */
    private static final double[] LONGUEUR = {9.2, 12.0};

    /** Fraction du travail faite, de 0 à 1. */
    private double avance;
    /** Bûches déjà données. */
    private int donne;
    /** Celui qui a porté le dernier coup (il reçoit les bûches de la chute). */
    @Nullable
    private UUID abatteur;
    /** Effets de l'impact déjà joués (pas sauvegardé : rechargé pendant la chute, l'impact est passé). */
    private boolean impactJoue;

    public ArbreServeur(EntityType<? extends Arbre> type, Level level) {
        super(type, level);
    }

    /** Un coup d'un joueur : jamais de dégâts ; seul un arbre debout se coupe. */
    @Override
    public boolean skipAttackInteraction(Entity attaquant) {
        if (attaquant instanceof ServerPlayer joueur && !this.isRemoved() && debout() && this.level() instanceof ServerLevel l) {
            frapper(l, joueur);
        }
        return true;
    }

    private void frapper(ServerLevel l, ServerPlayer joueur) {
        OutilRecolte outil = Coups.juger(joueur, this, true);
        if (outil == null) {
            return;
        }
        Reglages.Ressource r = Reglages.get().ressource(Reglages.ARBRE);
        avance = Math.min(1.0, avance + 1.0 / r.coups(outil.legendaire()));
        boolean fini = avance >= Coups.FINI;
        if (fini) {
            avance = 1.0;
        }
        Vec3 point = Coups.pointTouche(joueur, this);
        Coups.user(joueur);
        int du = Coups.part(avance, r.matiereCoups());
        if (du > donne) {
            Coups.donner(joueur, ObjetsRecolte.BUCHE.get(), du - donne);
            donne = du;
        }
        // il tombera à l'opposé du dernier joueur qui l'a frappé
        this.entityData.set(DIRECTION, Coups.lacetLoinDe(joueur, this));
        int entaille = fini ? ENTAILLES - 1 : Math.min(ENTAILLES - 2, (int) Math.floor(avance * 3.0 + Coups.EPSILON));
        if (entaille != entaille()) {
            this.entityData.set(COUPS, entaille);
        }
        Effets.coupArbre(l, this, point);
        if (fini) {
            this.abatteur = joueur.getUUID();
            this.impactJoue = false;
            this.entityData.set(CHUTE, l.getGameTime());
            Effets.craque(l, this);
        }
    }

    /** Debout : rien à faire. Abattu : suit sa chute (heure de jeu), puis la vie de la souche. */
    @Override
    public void tick() {
        super.tick();
        long debut = chute();
        if (debut < 0L || !(this.level() instanceof ServerLevel l)) {
            return;
        }
        long t = l.getGameTime() - debut;
        if (!souche()) {
            if (!impactJoue && t >= IMPACT) {
                impactJoue = true;
                if (t < IMPACT + 20) {
                    Effets.impact(l, this, longueur());
                }
            }
            if (t >= FIN) {
                abattu(l);
            }
        } else if (t >= FIN + soucheTicks()) {
            this.discard();
        }
    }

    /** Le tronc est parti : les dernières bûches à l'abatteur (au sol s'il n'est plus là), la souche reste. */
    private void abattu(ServerLevel l) {
        Reglages.Ressource r = Reglages.get().ressource(Reglages.ARBRE);
        // les bûches de la chute : ce qui manque pour le total du barème (6 avec le barème de la proposition)
        int reste = Math.max(0, r.matiere() - donne);
        donne += reste;
        if (reste > 0) {
            ServerPlayer joueur = abatteur == null ? null : l.getServer().getPlayerList().getPlayer(abatteur);
            if (joueur != null && joueur.isAlive() && joueur.level() == l && joueur.distanceToSqr(this) < 64.0 * 64.0) {
                Coups.donner(joueur, ObjetsRecolte.BUCHE.get(), reste);
            } else {
                Coups.lacher(l, this.getX(), this.getY() + 0.5, this.getZ(), ObjetsRecolte.BUCHE.get(), reste);
            }
        }
        this.entityData.set(SOUCHE, true);
        Effets.tronc(l, this, longueur());
    }

    private double longueur() {
        return LONGUEUR[espece()];
    }

    /** Ticks où la souche reste (réglage {@code souche_minutes}). */
    private static long soucheTicks() {
        return Reglages.get().soucheMinutes * 1200L;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.avance = tag.contains("Avance") ? Mth.clamp(tag.getDouble("Avance"), 0.0, 1.0) : entaille() / 3.0;
        this.donne = Math.max(0, tag.getInt("Donne"));
        this.abatteur = tag.hasUUID("Abatteur") ? tag.getUUID("Abatteur") : null;
        this.impactJoue = chute() >= 0L;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Avance", this.avance);
        tag.putInt("Donne", this.donne);
        if (this.abatteur != null) {
            tag.putUUID("Abatteur", this.abatteur);
        }
    }
}
