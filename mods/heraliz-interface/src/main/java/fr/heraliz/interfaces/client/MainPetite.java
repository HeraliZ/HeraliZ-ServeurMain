package fr.heraliz.interfaces.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.interfaces.InterfaceConfig;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import java.util.List;

/**
 * Main et objets tenus plus petits en 1re personne (Interface 1.10.0, demande du propriétaire du 25/10 : « les mains
 * paraissent trop grosses ») : la main vide et les objets qui ne sont pas des armes sont dessinés à
 * {@code [main] echelle} de leur taille (0,85 par défaut, config/heraliz_interface-client.toml), rapetissés vers le
 * bas de l'écran, sous la main (le bras reste attaché hors de l'écran). Les armes de TaCZ ont leur propre rendu et ne
 * passent pas par ici ; les armes de mêlée, Wonder Weapons, épées, arcs, arbalètes, tridents et boucliers gardent leur
 * taille. Rien ne change en 3e personne ni pour les autres joueurs. Appelé par MainPetiteMixin (ItemInHandRenderer).
 */
public final class MainPetite {
    /** Objets jamais rapetissés : identifiants qui commencent ainsi (armes). */
    private static final List<String> ARMES = List.of("tacz:", "heralisz_melee:", "heraliz_wonder:", "geckolib:item_");
    /** Point fixe du rapetissement (repère de la vue, blocs) : sous la main, hors de l'écran. */
    private static final float PIVOT_X = 0.56F, PIVOT_Y = -0.9F, PIVOT_Z = -0.72F;

    private MainPetite() {
    }

    /** Échelle à appliquer à ce dessin de main (1 : rien à faire). */
    public static float echelle(ItemStack objet) {
        float e = (float) InterfaceConfig.decimal(InterfaceConfig.MAIN_ECHELLE, 0.85);
        if (e >= 0.999F || !objet.isEmpty() && arme(objet)) {
            return 1.0F;
        }
        return Math.max(0.5F, e);
    }

    private static boolean arme(ItemStack objet) {
        if (objet.getItem() instanceof SwordItem || objet.getItem() instanceof BowItem || objet.getItem() instanceof CrossbowItem
                || objet.getItem() instanceof TridentItem || objet.getItem() instanceof ShieldItem) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(objet.getItem());
        String s = id.toString();
        for (String a : ARMES) {
            if (s.startsWith(a)) {
                return true;
            }
        }
        return false;
    }

    /** Pose la pile rapetissée (à défaire par {@link PoseStack#popPose()} après le dessin). */
    public static void poser(PoseStack pile, AbstractClientPlayer joueur, InteractionHand main, float e) {
        HumanoidArm bras = main == InteractionHand.MAIN_HAND ? joueur.getMainArm() : joueur.getMainArm().getOpposite();
        float cote = bras == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        pile.pushPose();
        pile.translate(cote * PIVOT_X, PIVOT_Y, PIVOT_Z);
        pile.scale(e, e, e);
        pile.translate(-cote * PIVOT_X, -PIVOT_Y, -PIVOT_Z);
    }
}
