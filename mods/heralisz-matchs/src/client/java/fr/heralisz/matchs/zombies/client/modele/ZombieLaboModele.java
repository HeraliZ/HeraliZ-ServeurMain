package fr.heralisz.matchs.zombies.client.modele;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.entite.ZombieLabo;
import fr.heralisz.matchs.zombies.jeu.api.Allure;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * Modele du zombie « labo » : corps humain au patron joueur 64 x 64 (avec surcouches : blouse, gilet, manches,
 * masque), entierement anime ici, sans aucune pose vanilla :
 * <ul>
 *     <li><b>marche</b> (marcheur) : bras tendus devant, qui flottent, tete penchee qui dodeline, jambe qui traine ;</li>
 *     <li><b>course</b> (coureur) : buste penche, bras tendus qui battent, foulees plus amples ;</li>
 *     <li><b>sprint</b> : buste tres penche, bras qui moulinent en arriere, tete en avant ;</li>
 *     <li><b>griffe</b> (attaque) : un bras se leve puis fauche en diagonale, le buste tourne ;</li>
 *     <li><b>arrachage</b> : les deux bras plongent vers la planche puis tirent en arriere ;</li>
 *     <li><b>enjambement</b> et <b>sortie de terre</b> : bras leves qui griffent l'air ;</li>
 *     <li><b>escalade</b> (0.2.3) : face au mur, bras qui se hissent tour a tour, jambes qui poussent, tete levee ;</li>
 *     <li><b>sortie d'egout</b> et <b>chute d'un conduit du plafond</b> (0.2.3) : mains sur le rebord puis appui, ou bras
 *     leves pendant la chute et reception accroupie.</li>
 * </ul>
 * Le buste se penche en deplacant tete, bras et jambes comme l'accroupissement vanilla (memes rapports), donc sans
 * deboitement.
 */
public class ZombieLaboModele extends PlayerModel<ZombieLabo> {
    public static final ModelLayerLocation COUCHE = new ModelLayerLocation(HeraliszMatchs.id("zb_zombie"), "main");

    public ZombieLaboModele(ModelPart racine) {
        super(racine, false);
    }

    public static LayerDefinition couche() {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
    }

    private static float doux(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    @Override
    public void setupAnim(ZombieLabo z, float marche, float ampl, float age, float lacetTete, float tangageTete) {
        // base : tete qui suit le regard, remise a zero des membres
        this.head.yRot = lacetTete * Mth.DEG_TO_RAD;
        this.head.xRot = tangageTete * Mth.DEG_TO_RAD;
        this.head.zRot = 0;
        this.body.xRot = 0;
        this.body.yRot = 0;
        this.body.zRot = 0;
        this.rightArm.xRot = this.leftArm.xRot = 0;
        this.rightArm.yRot = this.leftArm.yRot = 0;
        this.rightArm.zRot = this.leftArm.zRot = 0;
        this.rightLeg.xRot = this.leftLeg.xRot = 0;
        this.rightLeg.yRot = this.leftLeg.yRot = 0;
        this.rightLeg.zRot = this.leftLeg.zRot = 0;
        this.crouching = false;

        float t = age + z.phase * 20;
        float partiel = age - z.tickCount;
        Allure allure = z.allure();
        ampl = Math.min(ampl, 1.0F);
        float c = Mth.cos(marche * 0.6662F), s = Mth.sin(marche * 0.6662F);
        float penche;
        switch (allure) {
            case COUREUR -> {
                penche = 0.32F + 0.05F * Mth.sin(t * 0.25F);
                this.rightArm.xRot = -1.25F + c * 0.55F * ampl;
                this.leftArm.xRot = -1.25F - c * 0.55F * ampl;
                this.rightArm.zRot = 0.08F;
                this.leftArm.zRot = -0.08F;
                this.rightLeg.xRot = c * 1.25F * ampl;
                this.leftLeg.xRot = -c * 1.25F * ampl;
                this.head.zRot = 0.12F * Mth.sin(t * 0.11F);
            }
            case SPRINTEUR, RODEUR -> {
                penche = 0.55F;
                this.rightArm.xRot = 0.5F + c * 1.5F * ampl;
                this.leftArm.xRot = 0.5F - c * 1.5F * ampl;
                this.rightArm.zRot = 0.35F;
                this.leftArm.zRot = -0.35F;
                this.rightLeg.xRot = c * 1.45F * ampl;
                this.leftLeg.xRot = -c * 1.45F * ampl;
                this.head.xRot -= 0.45F;
                this.head.zRot = 0.18F * Mth.sin(t * 0.21F);
            }
            default -> {
                penche = 0.14F + 0.04F * Mth.sin(t * 0.07F);
                // bras tendus qui flottent, l'un un peu plus haut que l'autre
                this.rightArm.xRot = -1.45F + Mth.sin(t * 0.09F) * 0.08F + c * 0.12F * ampl;
                this.leftArm.xRot = -1.32F + Mth.sin(t * 0.09F + 1.7F) * 0.09F - c * 0.12F * ampl;
                this.rightArm.yRot = -0.1F;
                this.leftArm.yRot = 0.12F;
                this.rightArm.zRot = 0.06F + Mth.sin(t * 0.13F) * 0.04F;
                this.leftArm.zRot = -0.06F - Mth.sin(t * 0.11F) * 0.04F;
                // jambe droite qui traine
                this.rightLeg.xRot = c * 0.75F * ampl;
                this.leftLeg.xRot = -c * 1.05F * ampl;
                this.rightLeg.zRot = 0.04F;
                this.head.zRot = 0.22F * Mth.sin(t * 0.05F) + 0.1F;
                this.body.zRot = 0.04F * Mth.sin(t * 0.05F);
            }
        }

        // colle a la fenetre entre deux planches : penche dans l'embrasure, les bras griffent les planches tour a tour
        float pa = (z.tickCount - z.arracheDebut + partiel) / ZombieLabo.DUREE_ARRACHAGE;
        boolean aLaFenetre = z.aLaFenetre(partiel);
        if (aLaFenetre && (pa < 0 || pa >= 1)) {
            float g1 = Mth.sin(t * 0.42F), g2 = Mth.sin(t * 0.42F + 2.6F);
            this.rightArm.xRot = -1.62F + 0.38F * g1;
            this.leftArm.xRot = -1.55F + 0.38F * g2;
            this.rightArm.yRot = -0.18F;
            this.leftArm.yRot = 0.18F;
            this.rightArm.zRot = 0.12F + 0.08F * g1;
            this.leftArm.zRot = -0.12F - 0.08F * g2;
            this.head.xRot = Math.max(this.head.xRot, 0.15F) + 0.06F * g1;
            this.body.yRot = 0.06F * g1;
            penche = 0.32F + 0.04F * g2;
            this.rightLeg.xRot = 0.15F;
            this.leftLeg.xRot = -0.2F;
        }
        // arrachage d'une planche : les bras plongent dans l'embrasure (0-35 %), agrippent, puis tirent en arriere de
        // tout le corps (la planche part a ce moment-la, voir FenetreRendu)
        if (pa >= 0 && pa < 1) {
            float plonge = doux(pa / 0.35F), tire = doux((pa - 0.35F) / 0.5F);
            float bras = -1.45F - 0.55F * plonge + 1.45F * tire;
            this.rightArm.xRot = bras;
            this.leftArm.xRot = bras - 0.08F;
            this.rightArm.yRot = -0.38F * plonge * (1 - tire);
            this.leftArm.yRot = 0.38F * plonge * (1 - tire);
            this.rightArm.zRot = 0.12F * (1 - tire) + 0.25F * tire;
            this.leftArm.zRot = -0.12F * (1 - tire) - 0.25F * tire;
            this.body.yRot = 0.22F * Mth.sin(tire * Mth.PI);
            this.head.xRot = 0.25F * plonge - 0.45F * tire;
            this.rightLeg.xRot = -0.35F * tire;
            this.leftLeg.xRot = 0.25F * tire;
            penche = 0.3F + 0.35F * plonge - 0.72F * tire;
        }
        // enjambement (duree = ZombiesApi.ENJAMBEMENT_TICKS ; le jeu deplace le corps en arc) :
        // mains sur l'appui et jambe levee, passage plie en avant, puis reception jambes tendues, bras ecartes
        float pe = (z.tickCount - z.enjambeDebut + partiel) / ZombieLabo.DUREE_ENJAMBEMENT;
        if (pe >= 0 && pe < 1) {
            float p1 = doux(pe / 0.35F), p2 = doux((pe - 0.35F) / 0.35F), p3 = doux((pe - 0.7F) / 0.3F);
            float brasX = -1.5F + 0.5F * p1 + 0.5F * p2 - 0.3F * p3;
            this.rightArm.xRot = brasX;
            this.leftArm.xRot = brasX + 0.1F;
            this.rightArm.zRot = 0.1F + 0.55F * p3;
            this.leftArm.zRot = -0.1F - 0.55F * p3;
            this.rightLeg.xRot = -1.45F * p1 * (1 - p3) - 0.2F * p2 * (1 - p3);
            this.leftLeg.xRot = -1.15F * p2 * (1 - p3) + 0.15F * p3;
            this.head.xRot = 0.3F * p1 - 0.2F * p3;
            penche = Math.min(0.75F, 0.45F + 0.4F * p1 * (1 - p3) - 0.3F * p3);
        }
        // sortie d'un conduit : bras qui tirent tour a tour vers l'avant (reptation), jambes qui poussent
        float co = z.conduit(partiel);
        if (co < 0.75F) {
            float rp = Mth.sin(t * 0.55F);
            this.rightArm.xRot = -2.9F + 0.55F * rp;
            this.leftArm.xRot = -2.9F - 0.55F * rp;
            this.rightArm.zRot = 0.35F;
            this.leftArm.zRot = -0.35F;
            this.rightLeg.xRot = 0.25F * rp;
            this.leftLeg.xRot = -0.25F * rp;
            this.head.xRot = -0.9F;
            penche = 0;
        }
        // escalade (0.2.3) : face au mur, les mains se hissent tour a tour (~1,5 bloc/s), les pieds poussent contre la paroi
        if (z.grimpe() == ZombieLabo.GRIMPE_MUR) {
            float g = Mth.sin(t * 0.5F);
            this.rightArm.xRot = -2.85F + 0.5F * g;
            this.leftArm.xRot = -2.85F - 0.5F * g;
            this.rightArm.zRot = 0.22F;
            this.leftArm.zRot = -0.22F;
            this.rightArm.yRot = 0;
            this.leftArm.yRot = 0;
            this.rightLeg.xRot = -0.5F - 0.45F * g;
            this.leftLeg.xRot = -0.5F + 0.45F * g;
            this.rightLeg.zRot = 0.05F;
            this.leftLeg.zRot = -0.05F;
            this.head.xRot = -0.55F;
            this.head.zRot = 0;
            this.body.zRot = 0.05F * g;
            penche = 0.08F;
        }
        // sortie d'une bouche d'egout (0.2.3) : mains posees sur le rebord au-dessus de la tete, puis appui pour se hisser
        float eg = z.egout(partiel);
        if (eg < 1) {
            float hisse = doux((eg - 0.35F) / 0.5F);
            this.rightArm.xRot = -2.9F + 1.6F * hisse + 0.12F * Mth.sin(t * 0.6F) * (1 - hisse);
            this.leftArm.xRot = -2.9F + 1.6F * hisse - 0.12F * Mth.sin(t * 0.6F) * (1 - hisse);
            this.rightArm.zRot = 0.35F - 0.2F * hisse;
            this.leftArm.zRot = -0.35F + 0.2F * hisse;
            this.rightLeg.xRot = -0.9F * (1 - hisse);
            this.leftLeg.xRot = -0.4F * (1 - hisse);
            this.head.xRot = -0.6F + 0.8F * hisse;
            penche = 0.15F + 0.25F * hisse * (1 - doux((eg - 0.85F) / 0.15F));
        }
        // chute d'une bouche du plafond (0.2.3) : bras leves pendant la chute, reception accroupie
        float ch = z.chute(partiel);
        if (ch < 1) {
            float recu = doux((ch - 0.45F) / 0.35F);
            this.rightArm.xRot = -2.6F * (1 - recu) - 0.6F * recu;
            this.leftArm.xRot = -2.4F * (1 - recu) - 0.5F * recu;
            this.rightArm.zRot = 0.5F * (1 - recu) + 0.2F;
            this.leftArm.zRot = -0.5F * (1 - recu) - 0.2F;
            this.rightLeg.xRot = -0.35F * recu;
            this.leftLeg.xRot = -0.55F * recu;
            penche = 0.2F + 0.4F * recu * (1 - doux((ch - 0.8F) / 0.2F));
        }
        // sortie de terre : bras leves qui griffent
        float so = z.sortie(partiel);
        if (so < 1) {
            this.rightArm.xRot = -2.7F + Mth.sin(t * 0.7F) * 0.35F;
            this.leftArm.xRot = -2.5F + Mth.sin(t * 0.7F + 2) * 0.35F;
            this.rightArm.zRot = 0.25F;
            this.leftArm.zRot = -0.25F;
            this.head.xRot = -0.5F + so * 0.5F;
            penche = 0.2F;
        }
        // coup de griffe : bras qui monte puis fauche en diagonale (bras alterne selon l'entite)
        float at = this.attackTime;
        if (at > 0) {
            boolean droit = (z.getId() & 1) == 0;
            ModelPart bras = droit ? this.rightArm : this.leftArm;
            float leve = doux(at / 0.35F), fauche = doux((at - 0.35F) / 0.45F);
            bras.xRot = -1.4F - 1.5F * leve + 2.3F * fauche;
            bras.zRot = (droit ? 1 : -1) * (0.15F + 0.45F * leve - 0.75F * fauche);
            bras.yRot = (droit ? 1 : -1) * (-0.3F * fauche);
            this.body.yRot = (droit ? -1 : 1) * 0.35F * Mth.sin(Mth.clamp(at, 0, 1) * Mth.PI);
        }

        appliquerPenche(penche);
        // les bras suivent le buste
        this.rightArm.xRot -= penche * 0.4F;
        this.leftArm.xRot -= penche * 0.4F;
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftPants.copyFrom(this.leftLeg);
    }

    /** Penche le buste comme l'accroupissement vanilla (0,5 rad) : memes decalages, proportionnels. */
    private void appliquerPenche(float p) {
        float k = p / 0.5F;
        this.body.xRot = p;
        this.body.y = 3.2F * k;
        this.head.y = 4.2F * k;
        this.rightArm.y = 2.0F + 3.2F * k;
        this.leftArm.y = 2.0F + 3.2F * k;
        this.rightLeg.z = 4.0F * k;
        this.leftLeg.z = 4.0F * k;
        this.rightLeg.y = 12.0F + 0.2F * k;
        this.leftLeg.y = 12.0F + 0.2F * k;
    }
}
