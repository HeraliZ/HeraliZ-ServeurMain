package fr.heralisz.matchs.zombies.client.modele;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.entite.Rodeur;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Modele du rodeur (texture 128 x 64, cubes identiques a {@code tools/matchs/gen_zombies.py}) : quadrupede ecorche,
 * long cou tendu, machoire articulee, trois epines dorsales, queue. Galop (pattes avant et arriere en opposition,
 * dos qui ondule), machoire qui claque a l'attaque et grogne au repos, queue qui fouette, crispation a l'apparition.
 */
public class RodeurModele extends HierarchicalModel<Rodeur> {
    public static final ModelLayerLocation COUCHE = new ModelLayerLocation(HeraliszMatchs.id("zb_rodeur"), "main");

    private final ModelPart racine, corps, cou, tete, machoire, queue;
    private final ModelPart avG, avD, arG, arD;

    public RodeurModele(ModelPart racine) {
        this.racine = racine;
        this.corps = racine.getChild("corps");
        this.cou = corps.getChild("cou");
        this.tete = cou.getChild("tete");
        this.machoire = tete.getChild("machoire");
        this.queue = corps.getChild("queue");
        this.avG = racine.getChild("patte_av_g");
        this.avD = racine.getChild("patte_av_d");
        this.arG = racine.getChild("patte_ar_g");
        this.arD = racine.getChild("patte_ar_d");
    }

    public static LayerDefinition couche() {
        MeshDefinition m = new MeshDefinition();
        PartDefinition r = m.getRoot();
        PartDefinition corps = r.addOrReplaceChild("corps", CubeListBuilder.create().texOffs(0, 0).addBox(-5, -4.5F, -9, 10, 9, 18),
                PartPose.offset(0, 9.5F, 0));
        PartDefinition cou = corps.addOrReplaceChild("cou", CubeListBuilder.create().texOffs(56, 0).addBox(-3, -3, -7, 6, 6, 7),
                PartPose.offsetAndRotation(0, -1.5F, -8, 0.35F, 0, 0));
        PartDefinition tete = cou.addOrReplaceChild("tete", CubeListBuilder.create().texOffs(82, 0).addBox(-4, -3.5F, -9, 8, 7, 9),
                PartPose.offsetAndRotation(0, 0.5F, -6, -0.25F, 0, 0));
        tete.addOrReplaceChild("machoire", CubeListBuilder.create().texOffs(56, 16).addBox(-3.5F, 0, -8, 7, 2, 8),
                PartPose.offset(0, 3.0F, -0.5F));
        for (int i = 0; i < 3; i++) {
            corps.addOrReplaceChild("epine_" + i, CubeListBuilder.create().texOffs(60, 27).addBox(-1, -4, -1, 2, 4, 2),
                    PartPose.offsetAndRotation(0, -4.5F, -5 + i * 5, -0.3F, 0, 0));
        }
        corps.addOrReplaceChild("queue", CubeListBuilder.create().texOffs(32, 27).addBox(-1, -1, 0, 2, 2, 12),
                PartPose.offsetAndRotation(0, -2.5F, 9, -0.5F, 0, 0));
        r.addOrReplaceChild("patte_av_g", CubeListBuilder.create().texOffs(0, 27).addBox(-2, 0, -2, 4, 13, 4), PartPose.offset(3.5F, 11, -6));
        r.addOrReplaceChild("patte_av_d", CubeListBuilder.create().texOffs(0, 27).mirror().addBox(-2, 0, -2, 4, 13, 4), PartPose.offset(-3.5F, 11, -6));
        r.addOrReplaceChild("patte_ar_g", CubeListBuilder.create().texOffs(16, 27).addBox(-2, 0, -2, 4, 13, 4), PartPose.offset(3.5F, 11, 6.5F));
        r.addOrReplaceChild("patte_ar_d", CubeListBuilder.create().texOffs(16, 27).mirror().addBox(-2, 0, -2, 4, 13, 4), PartPose.offset(-3.5F, 11, 6.5F));
        return LayerDefinition.create(m, 128, 64);
    }

    @Override
    public ModelPart root() {
        return racine;
    }

    @Override
    public void setupAnim(Rodeur e, float marche, float ampl, float age, float lacetTete, float tangageTete) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        float t = age + e.phase * 20;
        ampl = Math.min(ampl, 1.0F);
        float g = marche * 0.85F;
        // galop : avant et arriere decales d'un quart de cycle, chaque paire en opposition
        avG.xRot = Mth.cos(g) * 1.1F * ampl;
        avD.xRot = Mth.cos(g + 0.6F) * 1.1F * ampl;
        arG.xRot = Mth.cos(g + Mth.PI) * 1.2F * ampl;
        arD.xRot = Mth.cos(g + Mth.PI + 0.6F) * 1.2F * ampl;
        corps.xRot = Mth.sin(g) * 0.08F * ampl;
        corps.y += Mth.sin(g * 2) * 0.6F * ampl;
        // tete : suit le regard, grondement au repos
        cou.yRot = lacetTete * Mth.DEG_TO_RAD * 0.6F;
        tete.yRot = lacetTete * Mth.DEG_TO_RAD * 0.4F;
        tete.xRot += tangageTete * Mth.DEG_TO_RAD * 0.5F;
        float gronde = 0.08F + 0.06F * Math.max(0, Mth.sin(t * 0.17F));
        machoire.xRot = gronde + 0.03F * Mth.sin(t * 2.3F);
        // attaque : gueule grande ouverte qui claque
        if (this.attackTime > 0) {
            float k = Mth.sin(this.attackTime * Mth.PI);
            machoire.xRot = 0.15F + 0.75F * k;
            cou.xRot += 0.25F * k;
        }
        // queue qui fouette
        queue.yRot = Mth.sin(t * 0.35F) * 0.35F + Mth.cos(g) * 0.3F * ampl;
        queue.xRot += Mth.sin(t * 0.21F) * 0.1F;
        // apparition : crispation puis detente (20 ticks)
        float pa = (e.tickCount - e.apparitionDebut + (age - e.tickCount)) / 20F;
        if (pa >= 0 && pa < 1) {
            float k = Mth.sin(pa * Mth.PI);
            cou.xRot -= 0.6F * k;
            machoire.xRot = 0.9F * k;
            corps.xRot -= 0.25F * k;
        }
    }
}
