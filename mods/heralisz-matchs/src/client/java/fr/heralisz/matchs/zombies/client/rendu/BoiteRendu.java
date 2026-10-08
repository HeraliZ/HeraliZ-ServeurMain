package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocBoite;
import fr.heralisz.matchs.zombies.contenu.bloc.BoiteBE;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor.EtatBoite;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

/**
 * Boite mystere v2 (D-048, rendus valides le 05/10 : {@code work/zombies_v3/boite/}) : la caisse classique de CoD
 * (planches sombres, fer, deux « ? » lumineux, paille et lumiere), modele {@code zbmodeles/boite_v2.json} genere par
 * {@code tools/matchs/zombies/gen_boite_v2.py}. La table et les parpaings (et l'emplacement vide : bidons + nounours)
 * sont des modeles de bloc cuits dans les troncons ({@link BlocBoite}) : rien a dessiner ici pour eux.
 * <p>Groupes animes : {@code caisse} (pivot centre bas : tremblement, envol, chute), {@code couvercle},
 * {@code couvercle_lueur}, {@code couvercle_reflet} (axe des charnieres, ouverture a 92 deg), {@code lumiere},
 * {@code rayons} (eclat a l'ouverture), {@code faisceau} (colonne bleue, hauteur variable) ; nounours
 * ({@code peluche.json} : corps, tete, bras_g, bras_d) ; bidons ({@code emplacement_vide.json}) pendant l'arrivee. L'arme
 * qui defile est la vraie arme (ItemDisplay du jeu). Poussiere, eclat et trainees : particules ({@link BoiteBE#tickClient}).
 * <p>Temps (storyboard) : voir {@link BoiteBE}. Aucune allocation par image hors pile de poses ; repere : origine au
 * centre bas de la case centrale, +z vers la salle.
 */
public class BoiteRendu implements BlockEntityRenderer<BoiteBE> {
    private static final float PX = 1 / 16F;
    /** Angle d'ouverture du couvercle (valide : le haut reste a un demi-pixel du mur). */
    private static final float OUVERT = 92F;
    /** Nounours dans la caisse : hauteur basse / haute (px), decalage en z (px) ; sur les bidons (px). */
    private static final float PEL_BAS = 9F, PEL_HAUT = 17.5F, PEL_Z = 0.1F, VIDE_Y = 5.0F + 4.2F + 0.36F, VIDE_Z = 0.8F;
    /** Hauteur de l'envol (blocs). */
    private static final float ENVOL = 8F;
    private static final Quaternionf Q = new Quaternionf();

    // modeles (recharges avec les ressources : on suit l'instance)
    @Nullable
    private static ModeleZb mBoite, mPeluche, mVide;
    @Nullable
    private static ModeleZb.Groupe gCaisse, gCouv, gCouvLueur, gReflet, gLumiere, gRayons, gFaisceau, gCorps, gTete, gBrasG, gBrasD, gBidons;
    @Nullable
    private static ModelesZb.Tex tBoite, tPeluche, tVide;

    public BoiteRendu(BlockEntityRendererProvider.Context ctx) {
    }

    private static boolean charger() {
        ModeleZb b = ModelesZb.get("boite_v2");
        if (b == null) return false;
        if (b != mBoite) {
            mBoite = b;
            tBoite = ModelesZb.tex(b.texture);
            gCaisse = b.groupe("caisse");
            gCouv = b.groupe("couvercle");
            gCouvLueur = b.groupe("couvercle_lueur");
            gReflet = b.groupe("couvercle_reflet");
            gLumiere = b.groupe("lumiere");
            gRayons = b.groupe("rayons");
            gFaisceau = b.groupe("faisceau");
        }
        ModeleZb p = ModelesZb.get("peluche");
        if (p != mPeluche) {
            mPeluche = p;
            tPeluche = p == null ? null : ModelesZb.tex(p.texture);
            gCorps = p == null ? null : p.groupe("corps");
            gTete = p == null ? null : p.groupe("tete");
            gBrasG = p == null ? null : p.groupe("bras_g");
            gBrasD = p == null ? null : p.groupe("bras_d");
        }
        ModeleZb v = ModelesZb.get("emplacement_vide");
        if (v != mVide) {
            mVide = v;
            tVide = v == null ? null : ModelesZb.tex(v.texture);
            gBidons = v == null ? null : v.groupe("bidons");
        }
        return gCaisse != null;
    }

    private static float doux(float u) {
        u = Mth.clamp(u, 0, 1);
        return u * u * (3 - 2 * u);
    }

    private static float k01(float u) {
        return Mth.clamp(u, 0, 1);
    }

    @Override
    public void render(BoiteBE be, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        EtatBoite etat = be.etat();
        if (etat == EtatBoite.ABSENTE || be.getLevel() == null || !charger()) return;
        Direction f = be.getBlockState().hasProperty(BlocBoite.FACING) ? be.getBlockState().getValue(BlocBoite.FACING) : Direction.NORTH;
        float t = be.ecoule(partiel);
        float age = (be.getLevel().getGameTime() % 24000L) + partiel;
        // repos : les « ? » respirent de 0,55 a 1 en 40 ticks
        float respire = 0.775F + 0.225F * Mth.sin(age * Mth.TWO_PI / 40F);

        // --- parametres d'animation (degres, blocs, intensites 0..1)
        float ouverture = 0, kLum = 0, kRayons = 0, kReflet = 0, kQ = respire;
        float kFaisceau = 0.9F + 0.1F * Mth.sin(age * 0.08F), hFaisceau = 1;
        float dy = 0, lacet = 0, tremble = 0, echelle = 1;
        float pelY = -1, pelS = 1;
        boolean caisse = true, vide = false;
        int teinteRayons = 0xFFFFFF;
        switch (etat) {
            case FERMEE -> {
                EtatBoite avant = be.precedent();
                if ((avant == EtatBoite.ROULETTE || avant == EtatBoite.OUVERTE) && t < BoiteBE.FERMETURE + 16) {
                    // fermeture : le couvercle retombe en accelerant (0,4 s), petit rebond, la lumiere s'eteint
                    if (t < BoiteBE.FERMETURE) {
                        float p = t / BoiteBE.FERMETURE;
                        ouverture = OUVERT * (1 - p * p);
                    } else if (t < BoiteBE.FERMETURE + 5) {
                        ouverture = 5F * Mth.sin((t - BoiteBE.FERMETURE) / 5F * Mth.PI);
                    }
                    float p = k01(t / BoiteBE.FERMETURE);
                    kLum = 0.5F * (1 - p);
                    kReflet = 0.5F * (1 - p);
                    kQ = Mth.lerp(k01((t - BoiteBE.FERMETURE) / 8F), 0.4F, respire);
                } else if (avant == EtatBoite.ARRIVEE) {
                    // les « ? » s'allument apres l'arrivee
                    kQ = Mth.lerp(k01(t / 10F), 0.5F, respire);
                }
            }
            case ROULETTE, OUVERTE -> {
                float o = k01(t / BoiteBE.OUVERTURE);
                ouverture = OUVERT * doux(o);
                kQ = Mth.lerp(o, respire, 0.4F);
                kReflet = 0.85F * o;
                if (etat == EtatBoite.ROULETTE) {
                    kLum = t < BoiteBE.OUVERTURE ? o : 0.9F;
                    // eclat : pic a 6 ticks, puis voile qui faiblit pendant le defilement
                    kRayons = t < 6 ? t / 6F : t < 20 ? 1 - 0.6F * (t - 6) / 14F : 0.4F - 0.15F * k01((t - 20) / 80F);
                } else {
                    // arme offerte : petit eclat a l'arret (teinte de la rarete), puis la lumiere baisse sur 10 s
                    float dt = be.depuisTirage(partiel);
                    float p = k01(dt / 200F);
                    float eclat = dt < 10 ? 1 - dt / 10F : 0;
                    kLum = 1 - 0.45F * p;
                    kReflet = 0.9F - 0.25F * p;
                    kRayons = 0.5F - 0.4F * p + 0.5F * eclat;
                    teinteRayons = melange(be.couleur(), 0.5F);
                }
            }
            case DEPART -> {
                if (t < BoiteBE.P_MONTEE) {
                    // le nounours sort de la paille
                    float p = doux(t / BoiteBE.P_MONTEE);
                    ouverture = OUVERT;
                    pelY = Mth.lerp(p, PEL_BAS, PEL_HAUT);
                    pelS = 0.25F + 0.75F * p;
                    kLum = 0.4F;
                    kReflet = 0.35F;
                    kRayons = 0.08F;
                    kQ = 0.25F;
                } else if (t < BoiteBE.P_CLAQUE) {
                    // le couvercle claque, le nounours retombe dedans
                    float p = (t - BoiteBE.P_MONTEE) / (BoiteBE.P_CLAQUE - BoiteBE.P_MONTEE);
                    ouverture = OUVERT * (1 - p * p);
                    if (p < 0.75F) {
                        pelY = PEL_HAUT - 10F * p;
                        pelS = 1 - 0.8F * p;
                    }
                    kLum = 0.4F * (1 - p);
                    kReflet = 0.35F * (1 - p);
                    kQ = 0.25F;
                } else if (t < BoiteBE.P_ENVOL) {
                    // secousses de plus en plus fortes
                    float a = (t - BoiteBE.P_CLAQUE) / (BoiteBE.P_ENVOL - BoiteBE.P_CLAQUE);
                    tremble = Mth.sin(age * 2.3F) * 5F * a;
                    dy = 1.2F * PX * a * Math.abs(Mth.sin(age * 1.7F));
                    kQ = 0.25F;
                } else if (t < BoiteBE.P_FIN) {
                    // envol en tournant et en retrecissant ; le faisceau baisse puis s'eteint
                    float p = (t - BoiteBE.P_ENVOL) / (BoiteBE.P_FIN - BoiteBE.P_ENVOL);
                    dy = 1.2F * PX + ENVOL * p * p;
                    lacet = 200F * p * p;
                    echelle = 1 - (float) Math.pow(p, 1.5);
                    tremble = 3F * (1 - p) * Mth.sin(age * 2.3F);
                    kFaisceau *= 1 - p;
                    hFaisceau = 1 - p;
                    kQ = 0.25F * (1 - p);
                } else {
                    // disparue : le nounours est deja sur ses bidons (relais du modele cuit de l'emplacement vide)
                    caisse = false;
                    kFaisceau = 0;
                    vide = true;
                }
            }
            case ARRIVEE -> {
                // le faisceau s'allume, la caisse tombe du ciel, rebond ; le nounours et ses bidons partent a l'impact
                kFaisceau *= k01(t / BoiteBE.A_CHUTE);
                vide = t < BoiteBE.A_IMPACT;
                if (t < BoiteBE.A_CHUTE) {
                    caisse = false;
                } else if (t < BoiteBE.A_IMPACT) {
                    float p = (t - BoiteBE.A_CHUTE) / (BoiteBE.A_IMPACT - BoiteBE.A_CHUTE);
                    dy = BoiteBE.HAUTEUR_CHUTE * (1 - p * p);
                    kQ = 0.3F;
                } else {
                    float p = k01((t - BoiteBE.A_IMPACT) / (BoiteBE.DUREE_ARRIVEE - BoiteBE.A_IMPACT));
                    dy = 2F * PX * Mth.sin(p * Mth.PI);
                    tremble = 1.5F * (1 - p) * Mth.sin(age * 2.3F);
                    kQ = 0.3F + 0.2F * p;
                }
            }
            default -> {
            }
        }
        if (echelle < 0.02F) caisse = false;

        ps.pushPose();
        ps.translate(0.5F, 0, 0.5F);
        ps.mulPose(Q.rotationY(-f.toYRot() * Mth.DEG_TO_RAD));

        // ---------------------------------------------------------------- passe 1 : textures (decoupe)
        if (caisse) {
            ps.pushPose();
            placerCaisse(ps, dy, lacet, tremble, echelle);
            VertexConsumer vb = buf.getBuffer(tBoite.base());
            ModeleZb.dessiner(gCaisse, ModeleZb.BASE, ps, vb, lumiere, overlay, 0xFFFFFFFF);
            ps.pushPose();
            tourner(ps, gCouv, 'x', -ouverture);
            ModeleZb.dessiner(gCouv, ModeleZb.BASE, ps, vb, lumiere, overlay, 0xFFFFFFFF);
            ps.popPose();
            if (pelY >= 0 && tPeluche != null) {
                ps.pushPose();
                ps.translate(0, pelY * PX, PEL_Z * PX);
                ps.scale(pelS, pelS, pelS);
                peluche(ps, buf.getBuffer(tPeluche.base()), lumiere, overlay, age, true);
                ps.popPose();
            }
            ps.popPose();
        }
        if (vide) {
            if (tVide != null) ModeleZb.dessiner(gBidons, ModeleZb.BASE, ps, buf.getBuffer(tVide.base()), lumiere, overlay, 0xFFFFFFFF);
            if (tPeluche != null) {
                ps.pushPose();
                ps.translate(0, VIDE_Y * PX, VIDE_Z * PX);
                peluche(ps, buf.getBuffer(tPeluche.base()), lumiere, overlay, age, false);
                ps.popPose();
            }
        }

        // ---------------------------------------------------------------- passe 2 : lueurs additives (pleine lumiere)
        VertexConsumer vl = buf.getBuffer(tBoite.lueur());
        if (kFaisceau > 0.01F && hFaisceau > 0.01F && gFaisceau != null) {
            ps.pushPose();
            ps.translate(gFaisceau.px, gFaisceau.py, gFaisceau.pz);
            ps.scale(1, hFaisceau, 1);
            ps.translate(-gFaisceau.px, -gFaisceau.py, -gFaisceau.pz);
            ModeleZb.dessiner(gFaisceau, ModeleZb.LUEUR, ps, vl, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(kFaisceau, 0xFFFFFF));
            ps.popPose();
        }
        if (caisse) {
            ps.pushPose();
            placerCaisse(ps, dy, lacet, tremble, echelle);
            if (kLum > 0.01F) ModeleZb.dessiner(gLumiere, ModeleZb.LUEUR, ps, vl, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(kLum, 0xFFFFFF));
            if (kRayons > 0.01F) ModeleZb.dessiner(gRayons, ModeleZb.LUEUR, ps, vl, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(kRayons, teinteRayons));
            tourner(ps, gCouv, 'x', -ouverture);
            if (kQ > 0.01F) ModeleZb.dessiner(gCouvLueur, ModeleZb.LUEUR, ps, vl, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(kQ, 0xFFFFFF));
            if (kReflet > 0.01F) ModeleZb.dessiner(gReflet, ModeleZb.LUEUR, ps, vl, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(kReflet, 0xFFFFFF));
            ps.popPose();
        }
        ps.popPose();
    }

    /** Transformation de la caisse autour de son pivot (centre bas) : hauteur, lacet, tremblement, echelle. */
    private static void placerCaisse(PoseStack ps, float dy, float lacet, float tremble, float echelle) {
        if (dy != 0) ps.translate(0, dy, 0);
        if (lacet == 0 && tremble == 0 && echelle == 1) return;
        ModeleZb.Groupe g = gCaisse;
        ps.translate(g.px, g.py, g.pz);
        if (lacet != 0) ps.mulPose(Q.rotationY(lacet * Mth.DEG_TO_RAD));
        if (tremble != 0) ps.mulPose(Q.rotationZ(tremble * Mth.DEG_TO_RAD));
        if (echelle != 1) ps.scale(echelle, echelle, echelle);
        ps.translate(-g.px, -g.py, -g.pz);
    }

    /** Rotation (degres) autour du pivot d'un groupe, sans allocation (quaternion reutilise). */
    private static void tourner(PoseStack ps, @Nullable ModeleZb.Groupe g, char axe, float degres) {
        if (g == null || degres == 0) return;
        ps.translate(g.px, g.py, g.pz);
        float a = degres * Mth.DEG_TO_RAD;
        ps.mulPose(axe == 'x' ? Q.rotationX(a) : axe == 'y' ? Q.rotationY(a) : Q.rotationZ(a));
        ps.translate(-g.px, -g.py, -g.pz);
    }

    /** Nounours : corps, tete et bras ; {@code moqueur} = il agite les bras et penche la tete (sortie de la boite). */
    private static void peluche(PoseStack ps, VertexConsumer vc, int lumiere, int overlay, float age, boolean moqueur) {
        ModeleZb.dessiner(gCorps, ModeleZb.BASE, ps, vc, lumiere, overlay, 0xFFFFFFFF);
        float tete = moqueur ? Mth.sin(age * 0.5F) * 12F : 0;
        float bras = moqueur ? 20F + Mth.sin(age * 0.9F) * 25F : 0;
        ps.pushPose();
        tourner(ps, gTete, 'z', tete);
        ModeleZb.dessiner(gTete, ModeleZb.BASE, ps, vc, lumiere, overlay, 0xFFFFFFFF);
        ps.popPose();
        ps.pushPose();
        tourner(ps, gBrasG, 'z', -bras);
        ModeleZb.dessiner(gBrasG, ModeleZb.BASE, ps, vc, lumiere, overlay, 0xFFFFFFFF);
        ps.popPose();
        ps.pushPose();
        tourner(ps, gBrasD, 'z', bras);
        ModeleZb.dessiner(gBrasD, ModeleZb.BASE, ps, vc, lumiere, overlay, 0xFFFFFFFF);
        ps.popPose();
    }

    /** Blanc melange a une couleur (part {@code k}) : teinte douce des rayons a l'arret de la roulette. */
    private static int melange(int rgb, float k) {
        int r = (int) Mth.lerp(k, 255, (rgb >> 16) & 255), g = (int) Mth.lerp(k, 255, (rgb >> 8) & 255), b = (int) Mth.lerp(k, 255, rgb & 255);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Pas de rendu hors ecran force (l'elimination par occlusion reste active) : le volume de rendu de {@link BoiteBE}
     * couvre la caisse, l'envol, la chute et les 17 blocs du faisceau.
     */
    @Override
    public boolean shouldRenderOffScreen(BoiteBE be) {
        return false;
    }

    /** Le faisceau doit se voir de loin. */
    @Override
    public int getViewDistance() {
        return 96;
    }
}
