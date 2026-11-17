package fr.heraliz.recolte.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.recolte.HeralizRecolte;
import fr.heraliz.recolte.client.modeles.Maillage;
import fr.heraliz.recolte.client.modeles.ModeleArbre;
import fr.heraliz.recolte.client.modeles.ModeleCuit;
import fr.heraliz.recolte.client.modeles.ModelesRecolte;
import fr.heraliz.recolte.entites.Arbre;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Arbre à abattre ({@code heraliz_recolte:arbre}) : le modèle cuit de son espèce, avec la lumière de l'endroit et le
 * lacet de l'entité ; une petite ombre ronde. La souche toujours ; tant que le tronc est là ({@code SOUCHE} faux) : le
 * morceau de coupe de l'entaille ({@code COUPS}), le fût, les branches et le feuillage.
 * <p><b>Côté de l'entaille et sens de la chute.</b> Les entailles des modèles sont creusées côté +x. L'arbre ne tourne
 * jamais sur lui-même (lacet fixé à l'apparition) : seul le morceau de coupe tourne, par quarts de tour autour de son
 * axe (tronc à 8 pans : il retombe sur sa forme, la coupe et le bas du fût restent alignés), pour que l'entaille soit
 * du côté du modèle le plus proche du joueur qui frappe ({@code DIRECTION} est à l'opposé de lui, remise par le serveur
 * à chaque coup). L'arbre tombe exactement vers {@code DIRECTION} (là où le serveur fait la poussière de l'impact),
 * en pivotant sur une charnière au bord de la coupe opposé au joueur (centre de la coupe décalé d'une demi-largeur
 * vers {@code DIRECTION}). Entaille et chute ont au plus 45° d'écart : ça ne se voit pas, et le serveur n'a rien à
 * arrondir.</p>
 * <p>Chute : {@link ChuteArbre} (lente puis de plus en plus vite, impact à 2,4 s, rebond), puis le tronc s'enfonce et
 * s'efface (rendu translucide) pendant les 2 s qui précèdent la souche seule.</p>
 * <p>Feuillage : ses plans sont cuits avec leurs deux faces (une par côté, chacune avec sa normale) ; ils sont donc
 * dessinés avec élimination des faces arrière (chaque côté voit sa face, éclairée de son côté), le reste sans
 * élimination comme demandé (A_FAIRE §4). Rien au-delà de 96 blocs ({@code Arbre.shouldRenderAtSqrDistance}). Aucune
 * allocation par image.</p>
 */
public final class RenduArbre extends EntityRenderer<Arbre> {
    private static final ResourceLocation SECOURS = new ResourceLocation(HeralizRecolte.MODID, "textures/entity/arbre_bouleau.png");
    private static final float DEG = (float) (Math.PI / 180.0), QUART = (float) (Math.PI / 2.0);

    private final Matrix4f m = new Matrix4f(), mt = new Matrix4f(), mc = new Matrix4f();
    private final Matrix3f n = new Matrix3f(), nt = new Matrix3f(), nc = new Matrix3f();
    private final Quaternionf chute = new Quaternionf(), coupe = new Quaternionf();

    public RenduArbre(EntityRendererProvider.Context contexte) {
        super(contexte);
        this.shadowRadius = 0.8F;
        this.shadowStrength = 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(Arbre a) {
        ModeleArbre ma = ModelesRecolte.arbre(a.espece());
        return ma != null ? ma.modele.texture : SECOURS;
    }

    @Override
    public void render(Arbre a, float lacetEntite, float partiel, PoseStack pose, MultiBufferSource tampons, int lumiere) {
        ModeleArbre ma = ModelesRecolte.arbre(a.espece());
        if (ma == null) {
            return;
        }
        ModeleCuit mod = ma.modele;
        int voile = OverlayTexture.NO_OVERLAY;
        float lacet = a.getYRot();
        m.set(pose.last().pose());
        n.set(pose.last().normal());
        m.rotateY(-lacet * DEG);
        n.rotateY(-lacet * DEG);
        Maillage.dessiner(ma.souche, m, n, tampons.getBuffer(mod.decoupe()), 1.0F, lumiere, voile);
        if (a.souche()) {
            return;
        }
        long debut = a.chute();
        float t = debut < 0L ? -1.0F : (float) (a.level().getGameTime() - debut) + partiel;
        if (t >= ChuteArbre.FIN) {
            return; // tronc effacé : la souche seule arrive du serveur
        }
        // sens de la chute dans le repère du modèle (DIRECTION est un lacet du monde, comme getYRot)
        float b = (lacet - a.direction()) * DEG;
        float dx = Mth.sin(b), dz = Mth.cos(b);
        mt.set(m);
        nt.set(n);
        float angle = t < 0.0F ? 0.0F : ChuteArbre.angle(t);
        float enfonce = t < 0.0F ? 0.0F : ChuteArbre.enfoncement(t);
        if (angle != 0.0F || enfonce != 0.0F) {
            // charnière : bord de la coupe du côté de la chute ; axe horizontal, le haut part vers la chute
            float hx = ma.cx + dx * ma.rayon, hy = ma.cy, hz = ma.cz + dz * ma.rayon;
            chute.rotationAxis(angle, dz, 0.0F, -dx);
            mt.translate(0.0F, -enfonce, 0.0F).translate(hx, hy, hz).rotate(chute).translate(-hx, -hy, -hz);
            nt.rotate(chute);
        }
        float opacite = t < 0.0F ? 1.0F : ChuteArbre.opacite(t);
        boolean efface = opacite < 0.999F;
        VertexConsumer tronc = tampons.getBuffer(efface ? mod.translucide() : mod.decoupe());
        Maillage.dessiner(ma.fut, mt, nt, tronc, opacite, lumiere, voile);
        Maillage.dessiner(ma.branches, mt, nt, tronc, opacite, lumiere, voile);
        // morceau de coupe : son entaille (+x) tournée vers le côté du modèle le plus proche du joueur
        int k = quart(-dx, -dz);
        mc.set(mt);
        nc.set(nt);
        if (k != 0) {
            coupe.rotationAxis(k * QUART, ma.ax, ma.ay, ma.az);
            mc.translate(ma.cx, ma.cy, ma.cz).rotate(coupe).translate(-ma.cx, -ma.cy, -ma.cz);
            nc.rotate(coupe);
        }
        Maillage.dessiner(ma.coupes[a.entaille()], mc, nc, tronc, opacite, lumiere, voile);
        Maillage.dessiner(ma.feuillage, mt, nt, tampons.getBuffer(efface ? mod.translucideFaces() : mod.decoupeFaces()),
                opacite, lumiere, voile);
    }

    /**
     * Quarts de tour (autour du tronc, sens de Minecraft) qui amènent le côté +x du modèle au plus près de la direction
     * (x, z) : 0 → +x, 1 → -z, 2 → -x, 3 → +z.
     */
    static int quart(float x, float z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x >= 0.0F ? 0 : 2;
        }
        return z < 0.0F ? 1 : 3;
    }
}
