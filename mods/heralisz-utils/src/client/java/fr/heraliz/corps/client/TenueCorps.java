package fr.heraliz.corps.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.corps.HeralizCorps;
import fr.heraliz.corps.entite.CorpsEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tenue d'un corps (21/10) : l'armure et les objets Curios encore sur le corps (gilet, masque, gants, ghillie, sac...)
 * dessines par-dessus le corps allonge, exactement comme sur un joueur vivant.
 * <p>Chaque corps habille a son « mannequin » : un joueur factice du client (jamais ajoute au monde, jamais envoye au
 * serveur) qui porte la tenue recue du serveur ({@link CorpsEntity#tenue()}) dans ses cases d'armure et ses emplacements
 * Curios. On lui applique les couches du rendu des joueurs qui dessinent l'equipement (armure de Minecraft, armures et
 * equipement de l'Armurerie, Curios, sac a dos de Backpacked), le modele du joueur etant pose comme le corps
 * ({@link Maillage#poser}). Rien d'autre (objets en main, cape, nom...). Mannequin rempli seulement quand la tenue change
 * (prise dans la fouille : la piece disparait du rendu) ; corps sans tenue : rien du tout.
 */
final class TenueCorps {
    private TenueCorps() {
    }

    /** Couches retenues par rendu de joueur (« default », « slim »). */
    private static final Map<LivingEntityRenderer<?, ?>, List<RenderLayer<?, ?>>> COUCHES = new HashMap<>();
    private static boolean panne;

    /** Mannequin d'un corps et version de tenue qu'il porte. */
    private static final class Mannequin {
        final RemotePlayer joueur;
        int version = Integer.MIN_VALUE;
        boolean habille;

        Mannequin(RemotePlayer joueur) {
            this.joueur = joueur;
        }
    }

    /**
     * Dessine la tenue du corps. Pose : celle du rendu du corps, avant la rotation de lacet (meme repere que
     * {@link Maillage#cuire}).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void rendre(CorpsEntity e, float lacet, float pt, PoseStack ps, MultiBufferSource buffers, int lumiere) {
        if (panne || e.tenue().isEmpty()) return;
        try {
            Mannequin m = mannequin(e);
            if (m == null) return;
            if (m.version != e.tenueVersion) habiller(m, e.tenue(), e.tenueVersion);
            if (!m.habille) return;
            RemotePlayer j = m.joueur;
            j.setPos(e.getX(), e.getY(), e.getZ());
            j.xo = j.getX();
            j.yo = j.getY();
            j.zo = j.getZ();
            j.setYRot(e.getYRot());
            j.yBodyRot = j.yBodyRotO = j.yHeadRot = j.yHeadRotO = e.getYRot();
            EntityRenderer<?> r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(j);
            if (!(r instanceof LivingEntityRenderer lr) || !(lr.getModel() instanceof PlayerModel modele)) return;
            List<RenderLayer<?, ?>> couches = couches(lr);
            if (couches.isEmpty()) return;
            modele.attackTime = 0.0F;
            modele.riding = false;
            modele.young = false;
            modele.crouching = false;
            Maillage.poser(modele);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - e.getYRot()));
            ps.translate(0.0F, Maillage.LEVE, -Maillage.DEMI);
            ps.mulPose(Axis.XP.rotationDegrees(90.0F));
            ps.scale(-0.9375F, -0.9375F, 0.9375F);
            ps.translate(0.0F, -1.501F, 0.0F);
            float age = j.tickCount + pt;
            for (RenderLayer c : couches) {
                c.render(ps, buffers, lumiere, j, 0.0F, 0.0F, pt, age, 0.0F, 0.0F);
            }
            ps.popPose();
        } catch (Throwable t) {
            // un autre mod qui refuse un joueur factice : plus de tenue sur les corps jusqu'au redemarrage, jamais de plantage
            panne = true;
            HeralizCorps.LOG.warn("[corps] tenue des corps coupee (rendu impossible) : {}", t.toString());
        }
    }

    private static Mannequin mannequin(CorpsEntity e) {
        if (e.tenueCache instanceof Mannequin m && m.joueur.level() == e.level()) return m;
        if (!(e.level() instanceof ClientLevel niveau)) return null;
        String nom = e.nom().isEmpty() ? "corps" : e.nom();
        RemotePlayer j = new RemotePlayer(niveau, new GameProfile(UUID.randomUUID(), nom.length() > 16 ? nom.substring(0, 16) : nom));
        j.setSilent(true);
        Mannequin m = new Mannequin(j);
        e.tenueCache = m;
        return m;
    }

    /** Remplit le mannequin avec la tenue recue : tout est d'abord retire (piece prise dans la fouille = plus dessinee). */
    private static void habiller(Mannequin m, CompoundTag tenue, int version) {
        m.version = version;
        RemotePlayer j = m.joueur;
        boolean quelqueChose = false;
        ListTag armure = tenue.getList("A", Tag.TAG_COMPOUND);
        for (int i = 0; i < 4; i++) {
            ItemStack s = i < armure.size() ? ItemStack.of(armure.getCompound(i)) : ItemStack.EMPTY;
            j.getInventory().armor.set(i, s);
            quelqueChose |= !s.isEmpty();
        }
        ListTag curios = tenue.getList("C", Tag.TAG_COMPOUND);
        if (ModList.get().isLoaded("curios")) quelqueChose |= CuriosTenue.remplir(j, curios);
        m.habille = quelqueChose;
    }

    /** Couches du rendu des joueurs qui dessinent l'equipement porte (lues une fois par rendu de joueur). */
    @SuppressWarnings("unchecked")
    private static List<RenderLayer<?, ?>> couches(LivingEntityRenderer<?, ?> r) {
        List<RenderLayer<?, ?>> l = COUCHES.get(r);
        if (l != null) return l;
        l = new ArrayList<>();
        try {
            Field f = ObfuscationReflectionHelper.findField(LivingEntityRenderer.class, "f_115291_");
            for (RenderLayer<?, ?> c : (List<RenderLayer<?, ?>>) f.get(r)) {
                if (retenue(c)) l.add(c);
            }
        } catch (RuntimeException | IllegalAccessException ex) {
            HeralizCorps.LOG.warn("[corps] couches du rendu des joueurs illisibles : {}", ex.toString());
        }
        COUCHES.put(r, l);
        return l;
    }

    private static boolean retenue(RenderLayer<?, ?> c) {
        if (c instanceof HumanoidArmorLayer || c instanceof CustomHeadLayer || c instanceof ElytraLayer) return true;
        String n = c.getClass().getName();
        return n.startsWith("top.theillusivec4.curios.") || n.startsWith("fr.heraliz.armory.")
                || n.startsWith("com.mrcrayfish.backpacked.client.renderer.entity.layers.BackpackLayer");
    }

    /** Rendus des joueurs changes (rechargement des ressources) : couches relues. */
    static void vider() {
        COUCHES.clear();
    }
}
