package fr.heraliz.armory.client.cible;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.client.gui.Ui;
import fr.heraliz.armory.network.PaquetsCible;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Chiffres de dégâts au-dessus de la cible d'essai (03/11, demande du propriétaire) : à chaque touche, le nombre de dégâts
 * reçus (ce qu'aurait perdu un joueur) s'écrit juste au-dessus de sa tête, monte d'un demi-bloc et s'efface en 1 s. Tête :
 * doré ; corps, mêlée, explosion : blanc. Les plombs d'un même tir de fusil à pompe s'additionnent en un seul chiffre. Vu
 * du tireur seulement (seul lui reçoit les coups). DM Sans gras du mod (ui_chiffre), à l'échelle des noms au-dessus des
 * entités. Aucun coût sans chiffre en cours : l'écouteur sort tout de suite ; au plus {@link #MAX} chiffres à la fois.
 * <p>08/11 : aussi les dégâts infligés à un AUTRE JOUEUR (PaquetsCible.Degats, décidés par le serveur), même dessin en plus
 * petit (70 %) et plus bas, juste au-dessus de sa tête ; doré à la tête ; plombs d'un même tir additionnés.</p>
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class ChiffresCible {
    private ChiffresCible() {}

    private static final ResourceLocation CHIFFRE = HeralizArmory.id("ui_chiffre");
    private static final long DUREE_MS = 1000L;
    private static final double MONTEE = 0.5D, AU_DESSUS = 0.35D;
    private static final int MAX = 16;
    private static final int OR = 0xFFC94A, BLANC = 0xFFFFFF;
    /** Taille des chiffres : cible d'essai (taille des noms au-dessus des entités), joueurs touchés (plus discrets). */
    private static final float TAILLE = 0.025F, TAILLE_JOUEUR = 0.0175F;
    private static final double AU_DESSUS_JOUEUR = 0.15D;

    private static final class Chiffre {
        final double x, y, z;
        final long tir;
        final int entite;
        float valeur;
        long debut;
        int couleur;
        final float taille;

        Chiffre(double x, double y, double z, int entite, long tir, float valeur, long debut, int couleur, float taille) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.entite = entite;
            this.tir = tir;
            this.valeur = valeur;
            this.debut = debut;
            this.couleur = couleur;
            this.taille = taille;
        }
    }

    private static final List<Chiffre> CHIFFRES = new ArrayList<>();

    /** Un coup reçu du serveur (fil du jeu). */
    static void ajouter(PaquetsCible.Touche t) {
        if (t.plafond() && t.recu() <= 0.0F && !memeTir(t.entite(), t.tir())) return;
        ajouter(t.entite(), t.tir(), t.recu(), t.zone() == PaquetsCible.TETE, TAILLE, AU_DESSUS);
    }

    /** Dégâts infligés à un autre joueur (08/11), fil du jeu. */
    static void ajouter(PaquetsCible.Degats d) {
        if (d.recu() <= 0.0F) return;
        ajouter(d.entite(), d.tir(), d.recu(), d.tete(), TAILLE_JOUEUR, AU_DESSUS_JOUEUR);
    }

    private static boolean memeTir(int entite, long tir) {
        if (tir < 0 || CHIFFRES.isEmpty()) return false;
        Chiffre d = CHIFFRES.get(CHIFFRES.size() - 1);
        return d.entite == entite && d.tir == tir;
    }

    private static void ajouter(int entite, long tir, float recu, boolean tete, float taille, double auDessus) {
        long maintenant = System.currentTimeMillis();
        int couleur = tete ? OR : BLANC;
        // plomb d'un tir déjà affiché : même chiffre, additionné (plafond atteint : rien de plus)
        if (memeTir(entite, tir)) {
            Chiffre d = CHIFFRES.get(CHIFFRES.size() - 1);
            d.valeur += recu;
            d.debut = maintenant;
            if (couleur == OR) d.couleur = OR;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity cible = mc.level == null ? null : mc.level.getEntity(entite);
        if (cible == null) return;
        // un peu de côté au hasard, pour que deux chiffres de suite ne se recouvrent pas
        double dx = (mc.level.random.nextDouble() - 0.5D) * 0.4D, dz = (mc.level.random.nextDouble() - 0.5D) * 0.4D;
        if (CHIFFRES.size() >= MAX) CHIFFRES.remove(0);
        CHIFFRES.add(new Chiffre(cible.getX() + dx, cible.getY() + cible.getBbHeight() + auDessus, cible.getZ() + dz, entite, tir,
                recu, maintenant, couleur, taille));
    }

    @SubscribeEvent
    public static void dessiner(RenderLevelStageEvent e) {
        if (CHIFFRES.isEmpty() || e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        long maintenant = System.currentTimeMillis();
        CHIFFRES.removeIf(c -> maintenant - c.debut > DUREE_MS);
        if (CHIFFRES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = e.getPoseStack();
        Font font = mc.font;
        MultiBufferSource.BufferSource tampon = mc.renderBuffers().bufferSource();
        for (Chiffre c : CHIFFRES) {
            float t = (maintenant - c.debut) / (float) DUREE_MS;
            int alpha = Math.max(8, Math.round(255.0F * (1.0F - t * t)));
            MutableComponent texte = Ui.lit(TextesCible.nombre(c.valeur), CHIFFRE);
            pose.pushPose();
            pose.translate(c.x - cam.x, c.y + MONTEE * t - cam.y, c.z - cam.z);
            pose.mulPose(camera.rotation());
            pose.scale(-c.taille, -c.taille, c.taille);
            font.drawInBatch(texte, -font.width(texte) / 2.0F, 0.0F, Ui.argb(c.couleur, alpha), true, pose.last().pose(), tampon,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        tampon.endBatch();
    }
}
