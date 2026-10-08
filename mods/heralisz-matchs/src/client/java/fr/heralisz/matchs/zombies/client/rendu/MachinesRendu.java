package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.matchs.zombies.contenu.MachinesV3;
import fr.heralisz.matchs.zombies.contenu.bloc.MachineBE;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor.EtatPad;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

/**
 * Pieces animees des machines v3 de The Giant ({@link MachineBE}) : modeles {@code zbmodeles/<machine>.json} valides le
 * 05/10 ({@code work/zombies_v3/machines/storyboard.png}), dessines par-dessus le corps fixe cuit dans les troncons.
 * Etats et intensites repris des scripts de validation ({@code gen_courant_v2.py}, {@code gen_teleporteurs.py},
 * {@code gen_mainframe.py} : memes regles que les rendus valides). Les groupes {@code bb_*} (halos) sont tournes vers la
 * camera autour de leur pivot. Trois passes par machine (textures, lueurs additives pleine lumiere, verre translucide),
 * jamais entrelacees ; aucune allocation par image (noms de groupes et quaternions statiques). Petites machines : rien au-dela
 * de 48 blocs ; la tour (eclairs vers le ciel) jusqu'a 96.
 */
public class MachinesRendu implements BlockEntityRenderer<MachineBE> {
    private static final int PLEIN = 0xF000F0;
    private static final int BLEU = 0xBFE6FF, ORANGE = 0xFF6B14, BLANC = 0xFFFFFF;
    private static final float PX = 1 / 16F;
    private static final Quaternionf Q = new Quaternionf(), QC = new Quaternionf();
    // normales des pupitres inclines (axe des aiguilles) : gen_teleporteurs.py, ns = nz(0, zf - zb, yb - yf)
    private static final float NS_TP_Y = 0.93870F, NS_TP_Z = 0.34490F, NS_MF_Y = 0.93981F, NS_MF_Z = 0.34175F;

    // noms de groupes (aucune concatenation par image)
    private static final String[] FUSIBLE = {"fusible_1", "fusible_2", "fusible_3"}, FIL = {"fil_1", "fil_2", "fil_3"};
    private static final String[] LF_R = {"lampe_fus_1_r", "lampe_fus_2_r", "lampe_fus_3_r"}, LF_V = {"lampe_fus_1_v", "lampe_fus_2_v", "lampe_fus_3_v"};
    private static final String[] HF_R = {"bb_halo_fus_1_r", "bb_halo_fus_2_r", "bb_halo_fus_3_r"}, HF_V = {"bb_halo_fus_1_v", "bb_halo_fus_2_v", "bb_halo_fus_3_v"};
    private static final String[] BOBINE = {"bobine_1", "bobine_2", "bobine_3", "bobine_4"};
    private static final String[] HALO_SPH = {"bb_halo_sphere_1", "bb_halo_sphere_2", "bb_halo_sphere_3", "bb_halo_sphere_4"};
    private static final String[] ARC_GLOBE = {"arc_globe_1", "arc_globe_2", "arc_globe_3", "arc_globe_4"};
    private static final String[] ARC_PYL = {"arc_pyl_1", "arc_pyl_2", "arc_pyl_3", "arc_pyl_4"};
    private static final String[] ARC_SOL = {"arc_sol_1", "arc_sol_2", "arc_sol_3"};
    private static final String[] LETTRE = {"lettre_A", "lettre_B", "lettre_C"};
    private static final String[] VOYANT = {"voyant_1", "voyant_2", "voyant_3"}, HALO_VOY = {"bb_halo_voyant_1", "bb_halo_voyant_2", "bb_halo_voyant_3"};
    private static final String[] MF_R = {"lampe_A_r", "lampe_B_r", "lampe_C_r"}, MF_V = {"lampe_A_v", "lampe_B_v", "lampe_C_v"};
    private static final String[] MF_HR = {"bb_halo_A_r", "bb_halo_B_r", "bb_halo_C_r"}, MF_HV = {"bb_halo_A_v", "bb_halo_B_v", "bb_halo_C_v"};
    private static final String[] ISOL = {"isol_1", "isol_2", "isol_3", "isol_4", "isol_5", "isol_6"};
    private static final String[] ARC_C = {"arc_c1", "arc_c2", "arc_c3", "arc_c4", "arc_c5", "arc_c6"};
    private static final String[] ARC_CIEL = {"arc_ciel_1", "arc_ciel_2", "arc_ciel_3"};
    private static final String[] LAME = {"lame_1", "lame_2", "lame_3", "lame_4", "lame_5", "lame_6", "lame_7", "lame_8", "lame_9", "lame_10",
            "lame_11", "lame_12"};

    public MachinesRendu(BlockEntityRendererProvider.Context ctx) {
    }

    // ------------------------------------------------------------------ outils
    @Nullable
    private static ModeleZb modele(String nom) {
        return ModelesZb.get(nom);
    }

    private static float k01(float v) {
        return Mth.clamp(v, 0, 1);
    }

    private static float doux(float u) {
        u = k01(u);
        return u * u * (3 - 2 * u);
    }

    /** Scintillement deterministe (0..1) d'un arc electrique : change toutes les 2 ticks, propre a chaque arc. */
    private static float crepite(float age, int graine) {
        int k = (int) (age * 0.5F) * 31 + graine * 17;
        k ^= k << 13;
        k ^= k >> 17;
        k ^= k << 5;
        return (k & 1023) / 1023F;
    }

    private static void base(ModeleZb m, String g, PoseStack ps, VertexConsumer vb, int l, int o) {
        ModeleZb.dessiner(m.groupe(g), ModeleZb.BASE, ps, vb, l, o, 0xFFFFFFFF);
    }

    private static void lueur(ModeleZb m, String g, PoseStack ps, VertexConsumer vl, float k, int rgb) {
        if (k > 0.01F) ModeleZb.dessiner(m.groupe(g), ModeleZb.LUEUR, ps, vl, PLEIN, OverlayTexture.NO_OVERLAY, Geo.additif(k, rgb));
    }

    /** Halo {@code bb_*} tourne vers la camera autour de son pivot (repere courant = celui de la machine tournee). */
    private static void halo(ModeleZb m, String nom, PoseStack ps, VertexConsumer vl, float k, int rgb, Direction f) {
        if (k <= 0.01F) return;
        ModeleZb.Groupe g = m.groupe(nom);
        if (g == null) return;
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        ps.pushPose();
        ps.translate(g.px, g.py, g.pz);
        ps.mulPose(Q.rotationY(f.toYRot() * Mth.DEG_TO_RAD));
        ps.mulPose(QC.set(cam.rotation()));
        ps.translate(-g.px, -g.py, -g.pz);
        ModeleZb.dessiner(g, ModeleZb.LUEUR, ps, vl, PLEIN, OverlayTexture.NO_OVERLAY, Geo.additif(k, rgb));
        ps.popPose();
    }

    /** Rotation (degres) autour d'un axe passant par le pivot du groupe. */
    private static void tourner(PoseStack ps, @Nullable ModeleZb.Groupe g, float ax, float ay, float az, float degres) {
        if (g == null || degres == 0) return;
        ps.translate(g.px, g.py, g.pz);
        ps.mulPose(Q.rotationAxis(degres * Mth.DEG_TO_RAD, ax, ay, az));
        ps.translate(-g.px, -g.py, -g.pz);
    }

    private static void echelleY(PoseStack ps, @Nullable ModeleZb.Groupe g, float s) {
        if (g == null) return;
        ps.translate(g.px, g.py, g.pz);
        ps.scale(1, Math.max(0.02F, s), 1);
        ps.translate(-g.px, -g.py, -g.pz);
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(MachineBE be, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        if (be.getLevel() == null) return;
        MachinesV3.Type type = be.type();
        if (type != MachinesV3.Type.MF_TOUR && type != MachinesV3.Type.MF_PLATEFORME) {
            Vec3 c = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (c.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 1, be.getBlockPos().getZ() + 0.5) > 48 * 48) return;
        }
        Direction f = be.face();
        float age = (be.getLevel().getGameTime() % 24000L) + partiel;
        float t = be.ecoule(partiel);
        ps.pushPose();
        ps.translate(0.5F, 0, 0.5F);
        if (type == MachinesV3.Type.MF_TOUR && be.arrivee()) arriveeMonde(be, ps, buf, partiel, age);
        ps.mulPose(Q.rotationY(-f.toYRot() * Mth.DEG_TO_RAD));
        switch (type) {
            case COURANT -> courant(be, ps, buf, lumiere, overlay, f, t, age, partiel);
            case TP_PAD -> pad(be, ps, buf, lumiere, overlay, f, t, age);
            case TP_PUPITRE -> pupitre(be, ps, buf, lumiere, overlay, f, t, age);
            case CUVE_AMES -> cuve(be, ps, buf, lumiere, overlay, f, age, partiel);
            case MF_PUPITRE -> pupitreMf(be, ps, buf, lumiere, overlay, f, age, partiel);
            case MF_TOUR -> tour(be, ps, buf, lumiere, overlay, f, t, age);
            case MF_PLATEFORME -> plateforme(be, ps, buf, f, age, partiel);
            case PAP_RIDEAU -> rideau(be, ps, buf, lumiere, overlay, f, t);
        }
        ps.popPose();
    }

    // ------------------------------------------------------------------ armoire du courant
    private void courant(MachineBE be, PoseStack ps, MultiBufferSource buf, int l, int o, Direction f, float t, float age, float partiel) {
        ModeleZb m = modele("courant");
        if (m == null) return;
        ModelesZb.Tex tx = ModelesZb.tex(m.texture);
        boolean on = be.etat() == 1;
        // levier : leve (-85 deg) ; abaisse lourdement en 0,6 s, contact a 12 ticks, petit rebond
        float levier;
        if (!on) levier = -85F;
        else if (t < 12) {
            float p = t / 12F;
            levier = -85F + 81F * p * p;
        } else if (t < 18) levier = -4F * (1 - (t - 12) / 6F) + 1.5F * Mth.sin((t - 12) / 6F * Mth.PI);
        else levier = 0;
        float contact = on && t >= 11 && t < 30 ? 1 - (t - 11) / 19F : 0;
        float eclair = on && t >= 11 && t < 21 ? 1 - (t - 11) / 10F : 0;
        // voltmetre : 0 V, puis 380 V avec depassement ; petit tremblement
        float volts = 0;
        if (on && t >= 12) {
            float p = k01((t - 12) / 28F);
            volts = 380F * (1 + 0.12F * Mth.sin(p * Mth.PI) * (1 - p)) * doux(p) + 4F * Mth.sin(age * 0.7F) * p;
        }
        float rouge = on ? k01(1 - (t - 12) / 8F) : 1;
        float vert = on ? k01((t - 12) / 6F) : 0;
        // fusibles : le dernier pose glisse dans ses pinces (8 ticks)
        int bits = be.bits();
        int dernier = 31 - Integer.numberOfLeadingZeros(bits);
        float fDernier = k01(be.depuisBits(partiel) / 8F);
        ModeleZb.Groupe gl = m.groupe("levier");
        VertexConsumer vb = buf.getBuffer(tx.base());
        ps.pushPose();
        tourner(ps, gl, 1, 0, 0, levier);
        base(m, "levier", ps, vb, l, o);
        ps.popPose();
        for (int i = 0; i < 3; i++) {
            if ((bits & (1 << i)) == 0) continue;
            float fi = i == dernier ? fDernier : 1;
            ps.pushPose();
            ps.translate(0, (1 - fi) * 1F * PX, (1 - fi) * 7F * PX);
            base(m, FUSIBLE[i], ps, vb, l, o);
            base(m, FIL[i], ps, vb, l, o);
            ps.popPose();
        }
        ModeleZb.Groupe ga = m.groupe("aiguille");
        ps.pushPose();
        tourner(ps, ga, 0, 0, 1, -(-60 + 120 * volts / 500F));
        base(m, "aiguille", ps, vb, l, o);
        ps.popPose();
        // lueurs
        VertexConsumer vl = buf.getBuffer(tx.lueur());
        lueur(m, "lampe_rouge", ps, vl, rouge, BLANC);
        lueur(m, "lampe_verte", ps, vl, vert, BLANC);
        for (int i = 0; i < 3; i++) {
            boolean pose = (bits & (1 << i)) != 0;
            float fi = pose ? (i == dernier ? fDernier : 1) : 0;
            boolean vertF = pose && fi >= 1;
            if (pose) {
                ps.pushPose();
                ps.translate(0, (1 - fi) * 1F * PX, (1 - fi) * 7F * PX);
                lueur(m, FIL[i], ps, vl, on ? Math.max(0.85F, contact) : 0, BLANC);
                ps.popPose();
            }
            lueur(m, vertF ? LF_V[i] : LF_R[i], ps, vl, 1, BLANC);
        }
        lueur(m, "arcs", ps, vl, contact * (0.5F + 0.5F * crepite(age, 3)), BLANC);
        halo(m, "bb_halo_rouge", ps, vl, rouge * 0.85F, BLANC, f);
        halo(m, "bb_halo_verte", ps, vl, vert * 0.85F, BLANC, f);
        for (int i = 0; i < 3; i++) {
            boolean pose = (bits & (1 << i)) != 0;
            boolean vertF = pose && (i != dernier || fDernier >= 1);
            halo(m, vertF ? HF_V[i] : HF_R[i], ps, vl, 0.9F, BLANC, f);
        }
        halo(m, "bb_eclair", ps, vl, eclair, BLANC, f);
    }

    // ------------------------------------------------------------------ pad du teleporteur
    private void pad(MachineBE be, PoseStack ps, MultiBufferSource buf, int l, int o, Direction f, float t, float age) {
        ModeleZb m = modele("tp_pad");
        if (m == null) return;
        ModelesZb.Tex tx = ModelesZb.tex(m.texture);
        EtatPad e = be.etatPad();
        int lettre = Mth.clamp(be.lettre(), 0, 2);
        VertexConsumer vb = buf.getBuffer(tx.base());
        base(m, "globe", ps, vb, l, o);
        for (String b : BOBINE) base(m, b, ps, vb, l, o);
        base(m, LETTRE[lettre], ps, vb, l, o);
        VertexConsumer vl = buf.getBuffer(tx.lueur());
        lueur(m, LETTRE[lettre], ps, vl, e == EtatPad.ETEINT ? 0.15F : 0.9F, BLANC);
        float duree = Math.max(1, be.duree());
        switch (e) {
            case ALIMENTE, CHARGE -> {
                float k = e == EtatPad.CHARGE ? 0.32F + 0.08F * Mth.sin(age * 0.12F) : 0.18F;
                lueur(m, "globe", ps, vl, k, BLANC);
                halo(m, "bb_halo_globe", ps, vl, k * 0.7F, BLANC, f);
                if (e == EtatPad.CHARGE) lueur(m, "anneau_lueur", ps, vl, 0.12F, BLEU);
            }
            case LIAISON -> {
                // bobines allumees tour a tour, arcs du globe et des pylones qui crepitent, chrono qui accelere a la fin
                float fin = k01(t / duree);
                float vitesse = 5 - 3 * fin;
                int tour = (int) (t / vitesse) % 4;
                lueur(m, "globe", ps, vl, 0.75F + 0.15F * Mth.sin(age * 0.6F), BLANC);
                halo(m, "bb_halo_globe", ps, vl, 0.7F, BLANC, f);
                lueur(m, "anneau_lueur", ps, vl, 0.45F, BLEU);
                lueur(m, "sol_lueur", ps, vl, 0.25F + 0.1F * fin, BLANC);
                for (int k = 0; k < 4; k++) {
                    lueur(m, BOBINE[k], ps, vl, 0.25F + (k == tour ? 0.75F : 0), BLEU);
                    halo(m, HALO_SPH[k], ps, vl, k == tour ? 0.6F : 0.25F, BLANC, f);
                    lueur(m, ARC_GLOBE[k], ps, vl, crepite(age, k) > 0.55F ? 1 : 0, BLANC);
                    lueur(m, ARC_PYL[k], ps, vl, crepite(age, k + 7) > 0.7F ? 0.8F : 0, BLANC);
                }
            }
            case LIE -> {
                lueur(m, "globe", ps, vl, 0.5F + 0.05F * Mth.sin(age * 0.08F), BLANC);
                halo(m, "bb_halo_globe", ps, vl, 0.4F, BLANC, f);
                lueur(m, "sol_lueur", ps, vl, 0.35F, BLANC);
                lueur(m, "anneau_lueur", ps, vl, 0.2F, BLEU);
                for (int k = 0; k < 4; k++) lueur(m, BOBINE[k], ps, vl, 0.3F, BLEU);
            }
            case TRANSPORT -> {
                // charge (1,5 s) : tout monte, colonne et eclair au depart
                float p = k01(t / duree);
                float coup = p > 0.8F ? (1 - p) / 0.2F : p / 0.8F;
                lueur(m, "globe", ps, vl, 0.6F + 0.4F * p, BLANC);
                halo(m, "bb_halo_globe", ps, vl, 0.6F + 0.4F * p, BLANC, f);
                lueur(m, "sol_lueur", ps, vl, 0.4F + 0.6F * p, BLANC);
                lueur(m, "anneau_lueur", ps, vl, 0.5F + 0.4F * p, BLEU);
                lueur(m, "colonne", ps, vl, 0.85F * p, BLANC);
                halo(m, "bb_flash", ps, vl, p > 0.8F ? 0.75F * coup + 0.25F : 0.1F * p, BLANC, f);
                for (int k = 0; k < 4; k++) {
                    lueur(m, BOBINE[k], ps, vl, 0.5F + 0.5F * p, BLEU);
                    halo(m, HALO_SPH[k], ps, vl, 0.8F * p, BLANC, f);
                    lueur(m, ARC_GLOBE[k], ps, vl, crepite(age, k) > 0.35F ? 1 : 0, BLANC);
                    lueur(m, ARC_PYL[k], ps, vl, k % 2 == 0 && crepite(age, k + 5) > 0.4F ? 0.7F : 0, BLANC);
                }
                for (int k = 0; k < 3; k++) lueur(m, ARC_SOL[k], ps, vl, p > 0.3F && crepite(age, k + 11) > 0.4F ? 1 : 0, BLANC);
            }
            case RECHARGE -> {
                // bobines orange qui refroidissent
                float froid = k01(t / duree);
                float k = 1 - 0.75F * froid;
                lueur(m, "globe", ps, vl, 0.45F * k, ORANGE);
                halo(m, "bb_halo_globe", ps, vl, 0.35F * k, ORANGE, f);
                lueur(m, "anneau_lueur", ps, vl, 0.45F * k, ORANGE);
                for (int i = 0; i < 4; i++) {
                    lueur(m, BOBINE[i], ps, vl, k, ORANGE);
                    halo(m, HALO_SPH[i], ps, vl, 0.45F * k, ORANGE, f);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ pupitre du pad
    private void pupitre(MachineBE be, PoseStack ps, MultiBufferSource buf, int l, int o, Direction f, float t, float age) {
        ModeleZb m = modele("tp_pupitre");
        if (m == null) return;
        ModelesZb.Tex tx = ModelesZb.tex(m.texture);
        EtatPad e = be.etatPad();
        int lettre = Mth.clamp(be.lettre(), 0, 2);
        boolean liaison = e == EtatPad.LIAISON;
        boolean charge = e.ordinal() >= EtatPad.CHARGE.ordinal();
        boolean lie = e == EtatPad.LIE || e == EtatPad.TRANSPORT || e == EtatPad.RECHARGE;
        // bouton SENDEN : enfonce pendant la liaison (le long de la normale du pupitre)
        float enfonce = liaison ? 0.55F * k01(t / 4F) : 0;
        // minuterie : 30 s en haut, l'aiguille tourne vers 0
        float secondes = liaison ? Math.max(0, (be.duree() - t) / 20F) : 30F;
        float angle = -(secondes / Math.max(1, be.duree() / 20F)) * 360F;
        VertexConsumer vb = buf.getBuffer(tx.base());
        base(m, "noyau", ps, vb, l, o);
        base(m, LETTRE[lettre], ps, vb, l, o);
        ps.pushPose();
        ps.translate(0, -NS_TP_Y * enfonce * PX, -NS_TP_Z * enfonce * PX);
        base(m, "bouton", ps, vb, l, o);
        ps.popPose();
        ps.pushPose();
        tourner(ps, m.groupe("aiguille"), 0, NS_TP_Y, NS_TP_Z, angle);
        base(m, "aiguille", ps, vb, l, o);
        ps.popPose();
        VertexConsumer vl = buf.getBuffer(tx.lueur());
        lueur(m, LETTRE[lettre], ps, vl, e == EtatPad.ETEINT ? 0.1F : 0.8F, BLANC);
        if (e != EtatPad.ETEINT) {
            lueur(m, VOYANT[0], ps, vl, 0.9F, BLANC);
            halo(m, HALO_VOY[0], ps, vl, 0.6F, BLANC, f);
        }
        if (charge) {
            float puls = 0.85F + 0.15F * Mth.sin(age * 0.15F);
            lueur(m, "noyau_lueur", ps, vl, puls, BLANC);
            halo(m, "bb_halo_noyau", ps, vl, 0.7F * puls, BLANC, f);
            lueur(m, VOYANT[1], ps, vl, 0.9F, BLANC);
            halo(m, HALO_VOY[1], ps, vl, 0.6F, BLANC, f);
        } else if (e == EtatPad.ALIMENTE) {
            lueur(m, "noyau_lueur", ps, vl, 0.12F, BLANC);
        }
        if (liaison || lie) {
            float k = lie ? 0.9F : (Mth.sin(age * 0.5F) > 0 ? 0.9F : 0.2F);
            lueur(m, VOYANT[2], ps, vl, k, BLANC);
            halo(m, HALO_VOY[2], ps, vl, 0.6F * k, BLANC, f);
        }
        if (liaison) {
            ps.pushPose();
            ps.translate(0, -NS_TP_Y * enfonce * PX, -NS_TP_Z * enfonce * PX);
            lueur(m, "bouton_lueur", ps, vl, 0.9F, BLANC);
            ps.popPose();
        }
        ModeleZb.dessiner(m.groupe("verre_douille"), ModeleZb.VERRE, ps, buf.getBuffer(tx.verre()), l, o, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ cuve a ames
    private void cuve(MachineBE be, PoseStack ps, MultiBufferSource buf, int l, int o, Direction f, float age, float partiel) {
        ModeleZb m = modele("cuve_ames");
        if (m == null) return;
        ModelesZb.Tex tx = ModelesZb.tex(m.texture);
        float n = be.niveau();
        boolean pleine = n >= 0.999F;
        // eclat quand l'ame arrive dans le cornet (l'instant est decale du temps de vol de la particule)
        float db = be.depuisBits(partiel);
        float ame = db < 0 ? 0 : k01(1 - db / 12F);
        VertexConsumer vb = buf.getBuffer(tx.base());
        if (n > 0.001F) {
            ps.pushPose();
            echelleY(ps, m.groupe("ames"), n);
            base(m, "ames", ps, vb, l, o);
            ps.popPose();
        }
        VertexConsumer vl = buf.getBuffer(tx.lueur());
        if (n > 0.001F) {
            ps.pushPose();
            echelleY(ps, m.groupe("ames"), n);
            lueur(m, "ames", ps, vl, 0.55F + 0.4F * n + 0.25F * ame, BLANC);
            ps.popPose();
            ps.pushPose();
            echelleY(ps, m.groupe("volutes"), n);
            tourner(ps, m.groupe("volutes"), 0, 1, 0, age * 2.2F);
            lueur(m, "volutes", ps, vl, 0.6F * Math.min(1, n * 1.5F) + 0.3F * ame, BLANC);
            ps.popPose();
            halo(m, "bb_halo_cuve", ps, vl, 0.6F * n + 0.3F * ame, BLANC, f);
            halo(m, "bb_halo_cornet", ps, vl, 0.5F + 0.4F * ame, BLANC, f);
        }
        if (pleine) lueur(m, "coeur_115", ps, vl, 0.6F + 0.2F * Mth.sin(age * 0.2F), BLANC);
        ModeleZb.dessiner(m.groupe("verre"), ModeleZb.VERRE, ps, buf.getBuffer(tx.verre()), l, o, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ pupitre A / B / C du mainframe
    private void pupitreMf(MachineBE be, PoseStack ps, MultiBufferSource buf, int l, int o, Direction f, float age, float partiel) {
        ModeleZb m = modele("mf_pupitre");
        if (m == null) return;
        ModelesZb.Tex tx = ModelesZb.tex(m.texture);
        boolean on = be.etat() >= 1;
        int lies = be.bits() & 7, liaison = (be.bits() >> 3) & 7;
        // grand levier VERBINDEN : tire a -35 deg a chaque liaison (10 ticks), tenu, puis relache
        float dl = be.depuisBits(partiel);
        float levier = dl < 10 ? -35 * doux(dl / 10F) : dl < 20 ? -35 : dl < 32 ? -35 * (1 - doux((dl - 20) / 12F)) : 0;
        if (liaison != 0) levier = Math.min(levier, -6F + 3F * Mth.sin(age * 0.4F));
        VertexConsumer vb = buf.getBuffer(tx.base());
        ps.pushPose();
        tourner(ps, m.groupe("levier"), 1, 0, 0, levier);
        base(m, "levier", ps, vb, l, o);
        ps.popPose();
        ps.pushPose();
        tourner(ps, m.groupe("aiguille_1"), 0, NS_MF_Y, NS_MF_Z, liaison != 0 ? -(age * 4F) % 360 : 0);
        base(m, "aiguille_1", ps, vb, l, o);
        ps.popPose();
        ps.pushPose();
        tourner(ps, m.groupe("aiguille_2"), 0, NS_MF_Y, NS_MF_Z, on ? -(150 + 25 * Integer.bitCount(lies) + 3 * Mth.sin(age * 0.5F)) : 0);
        base(m, "aiguille_2", ps, vb, l, o);
        ps.popPose();
        if (!on) return;
        VertexConsumer vl = buf.getBuffer(tx.lueur());
        for (int i = 0; i < 3; i++) {
            boolean lie = (lies & (1 << i)) != 0;
            boolean enCours = (liaison & (1 << i)) != 0;
            if (lie) {
                lueur(m, MF_V[i], ps, vl, 1, BLANC);
                halo(m, MF_HV[i], ps, vl, 0.8F, BLANC, f);
            } else if (enCours) {
                boolean v = Mth.sin(age * 0.6F) > 0;
                lueur(m, v ? MF_V[i] : MF_R[i], ps, vl, 0.95F, BLANC);
                halo(m, v ? MF_HV[i] : MF_HR[i], ps, vl, 0.7F, BLANC, f);
            } else {
                lueur(m, MF_R[i], ps, vl, 0.9F, BLANC);
                halo(m, MF_HR[i], ps, vl, 0.6F, BLANC, f);
            }
        }
    }

    // ------------------------------------------------------------------ tour du mainframe
    private void tour(MachineBE be, PoseStack ps, MultiBufferSource buf, int l, int o, Direction f, float t, float age) {
        ModeleZb m = modele("mf_tour");
        if (m == null) return;
        ModelesZb.Tex tx = ModelesZb.tex(m.texture);
        boolean on = be.etat() >= 1;
        int lies = be.bits() & 7;
        boolean liaison = (be.bits() & 0x10) != 0;
        VertexConsumer vb = buf.getBuffer(tx.base());
        for (String i : ISOL) base(m, i, ps, vb, l, o);
        base(m, "coeur", ps, vb, l, o);
        if (!on) return;
        VertexConsumer vl = buf.getBuffer(tx.lueur());
        lueur(m, "hublot", ps, vl, 0.7F, BLANC);
        float puls = 0.5F + 0.5F * Mth.sin(age * 0.1F);
        if (lies == 0 && !liaison) {
            lueur(m, "coeur", ps, vl, 0.35F + 0.05F * puls, BLANC);
            halo(m, "bb_halo_coeur", ps, vl, 0.25F, BLANC, f);
            return;
        }
        float kc = Math.min(1, 0.55F + 0.15F * lies + (liaison ? 0.2F : 0));
        lueur(m, "coeur", ps, vl, kc * (0.9F + 0.1F * puls), BLANC);
        halo(m, "bb_halo_coeur", ps, vl, kc, BLANC, f);
        halo(m, "bb_halo_tete", ps, vl, liaison ? 0.4F + 0.2F * puls : 0.8F, BLANC, f);
        int decal = (int) (age / 4) % 6;
        for (int k = 0; k < 6; k++) {
            float ki = liaison && lies < 3 ? 0.3F + 0.7F * ((k + decal) % 2) : 0.75F;
            lueur(m, ISOL[k], ps, vl, ki, BLEU);
        }
        boolean tous = lies >= 3;
        // 3/3 : grand spectacle 5 s (eclairs vers le ciel), puis une decharge toutes les ~4 s
        float spectacle = tous ? k01(1 - (t - 100) / 40F) : 0;
        boolean decharge = tous && (age % 80) < 14;
        for (int k = 0; k < 6; k++) {
            float ka = liaison ? (k % 2 == 0 && crepite(age, k) > 0.5F ? 1 : 0) : 0;
            if (tous) ka = Math.max(ka, (spectacle > 0 || decharge) && crepite(age, k + 20) > 0.35F ? 1 : 0);
            lueur(m, ARC_C[k], ps, vl, ka, BLANC);
        }
        if (tous && (spectacle > 0 || decharge)) {
            for (int k = 0; k < 3; k++) lueur(m, ARC_CIEL[k], ps, vl, crepite(age, k + 40) > 0.45F ? 1 : 0, BLANC);
            halo(m, "bb_flash_tour", ps, vl, 0.35F + 0.4F * spectacle * crepite(age, 50), BLANC, f);
        }
    }

    /**
     * Effet d'arrivee des teleportes dessine par la tour quand la plateforme validee n'est pas posee : disque de lumiere,
     * colonne et eclair de {@code mf_plateforme.json}, ramenes sur le pad d'arrivee de la carte (repere du monde).
     */
    private void arriveeMonde(MachineBE be, PoseStack ps, MultiBufferSource buf, float partiel, float age) {
        float da = be.depuisBits(partiel);
        boolean lie = (be.bits() & 7) != 0;
        if (da >= 36 && !lie) return;
        ModeleZb m = modele("mf_plateforme");
        if (m == null) return;
        ModeleZb.Groupe sol = m.groupe("sol_lueur");
        if (sol == null) return;
        ModelesZb.Tex tx = ModelesZb.tex(m.texture);
        ps.pushPose();
        ps.translate(be.arriveeX() - sol.px, be.arriveeY() - sol.py + 0.02F, be.arriveeZ() - sol.pz);
        arrivee(m, ps, buf.getBuffer(tx.lueur()), da, lie, Direction.SOUTH);
        ps.popPose();
    }

    private static void arrivee(ModeleZb m, PoseStack ps, VertexConsumer vl, float da, boolean lie, Direction f) {
        float p = da < 36 ? 1 - da / 36F : 0;
        lueur(m, "sol_lueur", ps, vl, Math.max(lie ? 0.35F : 0, p), BLANC);
        lueur(m, "colonne", ps, vl, 0.85F * p, BLANC);
        halo(m, "bb_flash", ps, vl, da < 10 ? 0.7F * (1 - da / 10F) : 0, BLANC, f);
    }

    // ------------------------------------------------------------------ plateforme validee
    private void plateforme(MachineBE be, PoseStack ps, MultiBufferSource buf, Direction f, float age, float partiel) {
        ModeleZb m = modele("mf_plateforme");
        if (m == null) return;
        ModelesZb.Tex tx = ModelesZb.tex(m.texture);
        // repere du modele : origine au pied de la tour (exporter_machines_v3.py : plateforme decalee de +8 px en z)
        ps.translate(0, 0, 0.5F);
        VertexConsumer vl = buf.getBuffer(tx.lueur());
        boolean on = be.etat() >= 1;
        if (on) {
            lueur(m, "lampe_niche", ps, vl, 1, BLANC);
            halo(m, "bb_halo_niche", ps, vl, 0.8F, BLANC, f);
        }
        arrivee(m, ps, vl, be.depuisBits(partiel), be.bits() != 0, f);
    }

    // ------------------------------------------------------------------ rideau du Pack-a-Punch
    private void rideau(MachineBE be, PoseStack ps, MultiBufferSource buf, int l, int o, Direction f, float t) {
        ModeleZb m = modele("pap_rideau");
        float sx = be.echelleX(), sy = be.echelleY();
        ps.scale(sx, sy, 1);
        // corps (cadre, caisson, voyant) : le modele de bloc valide, relu comme modele zombies (ModelesZb), dessine ici
        // pour suivre l'echelle de l'ouverture
        ModeleZb corps = modele("zb_pap_rideau_corps");
        if (corps != null) ModeleZb.dessiner(corps.groupe("corps"), ModeleZb.BASE, ps, buf.getBuffer(ModelesZb.tex(corps.texture).base()), l, o, 0xFFFFFFFF);
        if (m == null) return;
        ModelesZb.Tex tx = ModelesZb.tex(m.texture);
        int etat = be.etat();
        float ouverture = etat == 0 ? 0 : etat >= 2 ? 1 : doux(t / 60F);
        float dy = ouverture * 48F * PX;
        VertexConsumer vb = buf.getBuffer(tx.base());
        for (int k = 0; k < LAME.length; k++) {
            float haut = ((k + 1) * 4F) * PX + dy;
            if (haut > (48F + 2.6F) * PX) continue;
            ps.pushPose();
            ps.translate(0, dy, 0);
            // petit tremblement des lames pendant la montee
            if (etat == 1 && ouverture < 1) ps.translate(0, 0, 0.12F * PX * Mth.sin(t * 2.1F + k));
            base(m, LAME[k], ps, vb, l, o);
            ps.popPose();
        }
        VertexConsumer vl = buf.getBuffer(tx.lueur());
        boolean vert = etat >= 1;
        lueur(m, vert ? "lampe_v" : "lampe_r", ps, vl, 1, BLANC);
        halo(m, vert ? "bb_halo_v" : "bb_halo_r", ps, vl, vert ? 0.8F : 0.7F, BLANC, f);
    }

    @Override
    public boolean shouldRenderOffScreen(MachineBE be) {
        return false;
    }

    /** La tour (et ses eclairs) se voit de loin ; les autres machines s'arretent a 48 blocs dans {@link #render}. */
    @Override
    public int getViewDistance() {
        return 96;
    }
}
