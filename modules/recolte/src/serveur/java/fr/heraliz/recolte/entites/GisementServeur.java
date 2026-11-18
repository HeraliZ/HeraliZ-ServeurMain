package fr.heraliz.recolte.entites;

import fr.heraliz.recolte.objets.OutilRecolte;
import fr.heraliz.recolte.serveur.Apparition;
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

/**
 * Partie serveur d'un gisement (docs/METHODE_CLIENT_SERVEUR.md §5.1), créée par {@link EntitesRecolte#GISEMENT_USINE} :
 * le serveur juge chaque coup de pioche (attaque de Minecraft, {@link #skipAttackInteraction}).
 * <ul>
 *    <li>Le travail avance d'une fraction : 1 / coups nécessaires avec l'outil tenu (barème de recolte.json ; la
 *    hache-pioche de survie en demande 40 % de moins), partagé entre tous ceux qui frappent ; gardé avec l'entité.</li>
 *    <li>La matière est donnée au prorata du travail. 18/11 : son total est tiré au premier coup, entre les bornes du
 *    barème (pierre 10 à 14, métal 7 à 12, titane 4 à 8) ; la hache-pioche de survie tire deux fois et garde le meilleur
 *    (choix du propriétaire : « de meilleures chances avec la meilleure pioche, et plus vite »).</li>
 *    <li>{@link #ETAPE} = quarts du travail (le modèle suivant) ; {@link #SECOUSSE} = heure du coup (il tremble chez les
 *    joueurs) ; à la fin, il éclate et disparaît, sa place repart ailleurs après le délai de réapparition. La récolte ne
 *    rapporte aucun point de saison (décision du propriétaire du 16/11).</li>
 * </ul>
 */
public class GisementServeur extends Gisement {
    /** Fraction du travail faite, de 0 à 1. */
    private double avance;
    /** Matière déjà donnée. */
    private int donne;
    /** Matière que donne ce gisement en tout, tirée à son premier coup (0 : pas encore tirée). */
    private int total;

    public GisementServeur(EntityType<? extends Gisement> type, Level level) {
        super(type, level);
    }

    /** Nouveau gisement entier (apparition, /recolte placer) : 0 pierre, 1 métal, 2 titane. */
    public void preparer(int type) {
        this.entityData.set(TYPE, borne(type, 0, TYPES.length - 1));
        this.entityData.set(ETAPE, 0);
        this.avance = 0.0;
        this.donne = 0;
        this.total = 0;
    }

    /** Sorte de ressource du barème ({@link Reglages#PIERRE}, {@link Reglages#METAL}, {@link Reglages#RARE}). */
    public int sorte() {
        return Reglages.PIERRE + type();
    }

    /** Un coup d'un joueur : jamais de dégâts ; le serveur décide s'il compte. */
    @Override
    public boolean skipAttackInteraction(Entity attaquant) {
        if (attaquant instanceof ServerPlayer joueur && !this.isRemoved() && this.level() instanceof ServerLevel l) {
            frapper(l, joueur);
        }
        return true;
    }

    private void frapper(ServerLevel l, ServerPlayer joueur) {
        if (avance >= Coups.FINI) {
            return;
        }
        OutilRecolte outil = Coups.juger(joueur, this, false);
        if (outil == null) {
            return;
        }
        Reglages.Ressource r = Reglages.get().ressource(sorte());
        if (total <= 0) {
            total = r.tirer(l.getRandom(), outil.legendaire());
        }
        avance = Math.min(1.0, avance + 1.0 / r.coups(outil.legendaire()));
        boolean fini = avance >= Coups.FINI;
        if (fini) {
            avance = 1.0;
        }
        Vec3 point = Coups.pointTouche(joueur, this);
        Coups.user(joueur);
        int du = Coups.part(avance, total);
        if (du > donne) {
            Coups.donner(joueur, Coups.matiere(sorte()), du - donne);
            donne = du;
        }
        this.entityData.set(SECOUSSE, l.getGameTime());
        int etape = Math.min(ETAPES - 1, (int) Math.floor(avance * ETAPES + Coups.EPSILON));
        boolean nouvelle = !fini && etape != etape();
        if (nouvelle) {
            this.entityData.set(ETAPE, etape);
        }
        Effets.coupGisement(l, this, point, nouvelle, fini);
        if (fini) {
            Apparition.liberer(l, this.getUUID(), r.reapparitionMs());
            this.discard();
        }
    }

    /** Enlevé autrement que par la récolte ({@code /kill}...) : sa place repart ailleurs après le délai de réapparition. */
    @Override
    public void remove(Entity.RemovalReason raison) {
        boolean present = !this.isRemoved();
        super.remove(raison);
        if (present && raison.shouldDestroy() && this.level() instanceof ServerLevel l) {
            Apparition.retiree(l, this, sorte());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.avance = tag.contains("Avance") ? Mth.clamp(tag.getDouble("Avance"), 0.0, 1.0) : etape() / (double) ETAPES;
        this.donne = Math.max(0, tag.getInt("Donne"));
        this.total = Math.max(0, tag.getInt("Total"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Avance", this.avance);
        tag.putInt("Donne", this.donne);
        tag.putInt("Total", this.total);
    }
}
