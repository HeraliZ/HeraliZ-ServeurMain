package fr.heraliz.recolte.client.enmain;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.recolte.client.modeles.Maillage;
import fr.heraliz.recolte.client.modeles.ModeleCuit;
import fr.heraliz.recolte.client.modeles.ModelesRecolte;
import fr.heraliz.recolte.objets.OutilRecolte;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Le modèle en cubes de l'outil ({@code modeles/hache.json}, {@code pioche.json}, {@code hache_pioche.json} : groupes
 * {@code manche} et {@code tete}) dessiné en un envoi là où Minecraft dessine l'objet tenu : modèle d'objet
 * {@code forge:separate_transforms} dont la base est {@code builtin/entity} (l'inventaire, le sol et les cadres gardent
 * l'icône {@code textures/item/<id>.png}). À la première personne avec l'outil en main principale, c'est
 * {@link PremierePersonne} qui dessine.
 * <p>Vu des autres : l'outil est fixé dans la main (prise à 6 cm du bas du manche, manche vers la main du haut,
 * {@link TenueOutil}) ; ce que Minecraft pose entre la main et l'objet (ItemInHandLayer) est d'abord défait, comme pour
 * les jumelles d'Airstrike ({@code DesignatorHold}). Main gauche : en miroir, le tranchant toujours devant.</p>
 */
final class RenduOutilObjet extends BlockEntityWithoutLevelRenderer {
    /** Repère de l'objet vers celui du modèle de l'outil (mètres), et sa rotation seule (normales). */
    private record Attache(Matrix4f m, Matrix3f n) {
        static Attache de(Matrix4f m, float echelle) {
            return new Attache(m, new Matrix3f().set(m).scale(1.0F / echelle));
        }
    }

    private static final float K = TenueOutil.PX / 16.0F;
    private static final Attache DROITE = tiers(false, false), DROITE_PIC = tiers(false, true);
    private static final Attache GAUCHE = tiers(true, false), GAUCHE_PIC = tiers(true, true);
    /** Outil seul dans la main gauche à la première personne (rare) : debout, le milieu du manche dans la main. */
    private static final Attache PREMIERE_DROITE = premiere(false), PREMIERE_GAUCHE = premiere(true);
    /** Ailleurs (sur la tête, présentoirs d'autres mods) : debout, centré. */
    private static final Attache AUTRE = Attache.de(new Matrix4f().translation(0.5F, 0.14F, 0.5F), 1.0F);

    private final Matrix4f m = new Matrix4f();
    private final Matrix3f n = new Matrix3f();

    RenduOutilObjet() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack pile, ItemDisplayContext contexte, PoseStack pose, MultiBufferSource tampons, int lumiere,
                             int voile) {
        if (!(pile.getItem() instanceof OutilRecolte outil)) {
            return;
        }
        ModeleCuit modele = ModelesRecolte.outil(outil.id());
        if (modele == null) {
            return;
        }
        boolean pic = outil.casseLesGisements() && !outil.legendaire();
        Attache a = switch (contexte) {
            case THIRD_PERSON_RIGHT_HAND -> pic ? DROITE_PIC : DROITE;
            case THIRD_PERSON_LEFT_HAND -> pic ? GAUCHE_PIC : GAUCHE;
            case FIRST_PERSON_RIGHT_HAND -> PREMIERE_DROITE;
            case FIRST_PERSON_LEFT_HAND -> PREMIERE_GAUCHE;
            default -> AUTRE;
        };
        m.set(pose.last().pose()).mul(a.m());
        n.set(pose.last().normal()).mul(a.n());
        Maillage.dessiner(modele.tout, m, n, tampons.getBuffer(modele.decoupe()), 1.0F, lumiere, voile);
    }

    /**
     * Vu des autres : ItemInHandLayer.renderArmWithItem et le -0,5 d'ItemRenderer défaits (retour au repère de la main,
     * px du modèle / 16), puis l'outil fixé dans la main ; main gauche en miroir (x opposé, outil retourné sur son
     * épaisseur pour garder le tranchant devant).
     */
    private static Attache tiers(boolean gauche, boolean pic) {
        float s = pic ? -1.0F : 1.0F;
        float[] u = TenueOutil.MANCHE, f = TenueOutil.TRANCHANT, p = TenueOutil.PRISE_MAIN;
        Matrix3f r = Repere.outil(u[0], u[1], u[2], f[0] * s, f[1] * s, f[2] * s);
        Matrix4f main = new Matrix4f().translation(p[0] / 16.0F, p[1] / 16.0F, p[2] / 16.0F)
                .mul(new Matrix4f().set3x3(r))
                .scale(K)
                .translate(0.0F, -TenueOutil.PRISE_BAS, 0.0F);
        if (gauche) {
            main = new Matrix4f().scaling(-1.0F, 1.0F, 1.0F).mul(main).scale(1.0F, 1.0F, -1.0F);
        }
        Matrix4f m = new Matrix4f().translation(0.5F, 0.5F, 0.5F)
                .translate((gauche ? 1.0F : -1.0F) / 16.0F, -0.125F, 0.625F)
                .rotateY((float) -Math.PI)
                .rotateX((float) (Math.PI / 2.0))
                .mul(main);
        return Attache.de(m, K);
    }

    private static Attache premiere(boolean gauche) {
        Matrix4f outil = new Matrix4f().rotateX((float) Math.toRadians(-20.0)).translate(0.0F, -0.30F, 0.0F);
        if (gauche) {
            outil = new Matrix4f().scaling(-1.0F, 1.0F, 1.0F).mul(outil).scale(1.0F, 1.0F, -1.0F);
        }
        return Attache.de(new Matrix4f().translation(0.5F, 0.5F, 0.5F).mul(outil), 1.0F);
    }
}
