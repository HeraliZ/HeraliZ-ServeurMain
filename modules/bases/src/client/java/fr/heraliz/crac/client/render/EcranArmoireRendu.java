package fr.heraliz.crac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.crac.block.Adosse;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

/**
 * Écran rouillé de l'armoire (saison des clans, modèle validé le 22/10) : entretien demandé par période et temps avant
 * que la base ne se dégrade, écrits en direct sur le fond d'écran du modèle (lignes, fêlure, coin rouillé), en ambre,
 * avec ses défauts : une ligne qui saute de temps en temps, une image fantôme décalée, un écran qui baisse d'un coup.
 * Rien au-delà de {@link #PORTEE} blocs ; tout vient du serveur ({@link ClaimBlockEntity#majEcran()}).
 */
public class EcranArmoireRendu implements BlockEntityRenderer<ClaimBlockEntity> {
    private static final double PORTEE = 16.0;
    private static final int PLEIN_JOUR = 0xF000F0;
    private static final int AMBRE = 0xFFB038, AMBRE_PALE = 0xC07A24, ROUGE = 0xFF3A24, FANTOME = 0x5A1E08;
    /** Largeur de l'écran en unités de texte (une ligne de police fait 9 unités de haut). */
    private static final float LARGEUR = 100F;

    private final Font font;

    public EcranArmoireRendu(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(ClaimBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (be.getLevel() == null || mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > PORTEE * PORTEE) {
            return;
        }
        CompoundTag e = be.ecran();
        if (!e.contains("Min")) {
            return;
        }
        BlockState s = be.getBlockState();
        if (!(s.getBlock() instanceof ClaimBlock cb)) {
            return;
        }
        Direction f = s.getValue(ClaimBlock.FACING);
        double[] d = Adosse.decalage(Adosse.tourner(cb.boiteNord(), f), s.getValue(Adosse.ADOSSE));
        long t = be.getLevel().getGameTime();

        ps.pushPose();
        ps.translate(d[0] / 16.0, 0, d[1] / 16.0);
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-angle(f)));
        ps.translate(-0.5, 0, -0.5);
        // coin haut gauche de l'écran vu de face (l'est du modèle), texte tourné vers le nord du modèle
        float k = (EcranArmoireDonnees.X1 - EcranArmoireDonnees.X0) / 16F / LARGEUR;
        ps.translate(EcranArmoireDonnees.X1 / 16F, EcranArmoireDonnees.Y1 / 16F, (EcranArmoireDonnees.Z - 0.03F) / 16F);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        ps.scale(k, -k, k);
        float hauteur = (EcranArmoireDonnees.Y1 - EcranArmoireDonnees.Y0) / 16F / k;

        // l'écran baisse d'un coup, parfois (environ 1 tick sur 40)
        boolean baisse = bruit(be.getBlockPos().asLong(), t / 2) < 0.025F;
        double heures = e.contains("Heures") ? e.getDouble("Heures") : 24.0;
        float y = 3F;
        ligne(Component.translatable("crac.ecran.titre", (int) Math.round(heures)).getString(), 4F, y, 0.75F, AMBRE_PALE, baisse, be, t, 0, buffers, ps);
        y += 9F;
        int[] besoin = e.getIntArray("Besoin");
        var objets = e.getList("Objets", Tag.TAG_STRING);
        int lignes = 0;
        for (int i = 0; i < besoin.length && lignes < 3; i++) {
            if (besoin[i] <= 0) {
                continue;
            }
            String nom = nomObjet(i < objets.size() ? objets.getString(i) : "");
            String txt = this.font.plainSubstrByWidth(besoin[i] + " " + nom, (int) (LARGEUR - 8));
            ligne(txt, 4F, y, 1F, AMBRE, baisse, be, t, 1 + i, buffers, ps);
            y += 11F;
            lignes++;
        }
        if (lignes == 0) {
            ligne(Component.translatable("crac.ecran.aucun").getString(), 4F, y, 1F, AMBRE, baisse, be, t, 1, buffers, ps);
        }
        // temps avant dégradation, décompté seconde par seconde depuis la dernière réception
        int min = e.getInt("Min");
        String bas;
        int couleur = AMBRE;
        if (min < 0) {
            bas = Component.translatable("crac.ecran.depop_aucun").getString();
        } else {
            long reste = Math.max(0L, min * 60L - (t - be.ecranRecuA()) / 20L);
            if (reste <= 0L) {
                bas = Component.translatable("crac.ecran.depop_imminent").getString();
                couleur = (t / 10) % 2 == 0 ? ROUGE : FANTOME;
            } else {
                bas = Component.translatable("crac.ecran.depop", String.format(Locale.ROOT, "%d:%02d:%02d", reste / 3600, reste / 60 % 60, reste % 60)).getString();
            }
        }
        ligne(bas, 4F, hauteur - 12F, 1F, couleur, baisse, be, t, 9, buffers, ps);
        ps.popPose();
    }

    /** Une ligne : parfois décalée de quelques unités (le signal saute), avec son image fantôme. */
    private void ligne(String txt, float x, float y, float echelle, int couleur, boolean baisse, ClaimBlockEntity be, long t, int n,
                       MultiBufferSource buffers, PoseStack ps) {
        float saut = bruit(be.getBlockPos().asLong() * 31 + n, t / 3) < 0.04F ? 2F + 3F * bruit(n, t) : 0F;
        int c = baisse ? assombrir(couleur) : couleur;
        ps.pushPose();
        ps.translate(x + saut, y, 0);
        ps.scale(echelle, echelle, 1F);
        Matrix4f m = ps.last().pose();
        this.font.drawInBatch(txt, 1.2F, 0.6F, 0xFF000000 | FANTOME, false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, PLEIN_JOUR);
        ps.translate(0, 0, -0.01F);
        this.font.drawInBatch(txt, 0F, 0F, 0xFF000000 | c, false, ps.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, PLEIN_JOUR);
        ps.popPose();
    }

    private static int assombrir(int c) {
        return ((c >> 16 & 0xFF) * 2 / 5) << 16 | ((c >> 8 & 0xFF) * 2 / 5) << 8 | (c & 0xFF) * 2 / 5;
    }

    private static String nomObjet(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? Items.AIR : ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) {
            return "?";
        }
        return item.getDescription().getString().toUpperCase(Locale.ROOT);
    }

    private static float angle(Direction f) {
        return switch (f) {
            case EAST -> 90F;
            case SOUTH -> 180F;
            case WEST -> 270F;
            default -> 0F;
        };
    }

    /** Nombre entre 0 et 1 qui ne dépend que de (a, b) : les défauts de l'écran changent tout seuls, sans état. */
    private static float bruit(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    @Override
    public int getViewDistance() {
        return (int) PORTEE;
    }
}
