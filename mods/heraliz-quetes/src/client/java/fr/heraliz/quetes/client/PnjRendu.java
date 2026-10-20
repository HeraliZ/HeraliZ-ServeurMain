package fr.heraliz.quetes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.entite.Pnj;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.HashMap;
import java.util.Map;

/**
 * Rendu d'un PNJ : un corps de joueur (bras larges ou fins), son apparence CaraCrea (skin peint par le créateur de
 * personnage du serveur, bras larges comme les joueurs) sinon son skin ({@code textures/pnj/<skin>.png} de ce jar, sinon
 * Steve / Alex), et son nom au-dessus de la tête quand on est près (plus aucune phrase au-dessus
 * de la tête depuis la 1.6.8 : elle cachait le repère du PNJ).
 */
public final class PnjRendu extends HumanoidMobRenderer<Pnj, PlayerModel<Pnj>> {
    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");
    private static final ResourceLocation ALEX = new ResourceLocation("textures/entity/alex.png");
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();
    private static Boolean caraCrea;

    private final PlayerModel<Pnj> large, fin;

    public PnjRendu(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        large = getModel();
        fin = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(Pnj e, float lacet, float pt, PoseStack ps, MultiBufferSource buf, int lumiere) {
        model = e.brasFins() && !caraCrea(e) ? fin : large;
        super.render(e, lacet, pt, ps, buf, lumiere);
    }

    @Override
    public ResourceLocation getTextureLocation(Pnj e) {
        if (caraCrea(e)) {
            ResourceLocation peinte = CaraCreaPnj.texture(e.apparence());
            if (peinte != null) return peinte;
        }
        String s = e.skin();
        if (s == null || s.isEmpty()) return e.brasFins() ? ALEX : STEVE;
        return SKINS.computeIfAbsent(s + (e.brasFins() ? "|f" : "|l"), k -> {
            ResourceLocation rl = ResourceLocation.tryParse(HeralizQuetes.MODID + ":textures/pnj/" + s + ".png");
            if (rl == null || Minecraft.getInstance().getResourceManager().getResource(rl).isEmpty()) return e.brasFins() ? ALEX : STEVE;
            return rl;
        });
    }

    /** Le PNJ porte une apparence CaraCrea et le mod est là. */
    private static boolean caraCrea(Pnj e) {
        if (e.apparence().isEmpty()) return false;
        if (caraCrea == null) caraCrea = ModList.get().isLoaded("caracrea");
        return caraCrea;
    }

    static void vider() {
        if (Boolean.TRUE.equals(caraCrea)) CaraCreaPnj.toutLiberer();
    }

    @Override
    protected boolean shouldShowName(Pnj e) {
        return false;   // 20/10 : plus de nom de Minecraft au-dessus ; seul le repère (client/Reperes) et son nom
    }
}
