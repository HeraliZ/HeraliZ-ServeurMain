package fr.heraliz.armory.client;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.GearDef;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.item.GearItem;
import fr.heraliz.armory.item.PieceItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Superposition realiste avec les ghillies modelisees (reprise de HeralisZ Armor, client.Layering ; 0.9.0 : adaptee aux
 * pieces de l'Armurerie). De l'interieur vers l'exterieur : vetements (haut, pantalon, genouilleres, chaussures) ->
 * ghillie -> equipement (gilet, brassard, casque, masque, sac).
 * <ul>
 * <li>ghillie integrale : le haut, le pantalon et les genouilleres de l'Armurerie ne sont plus dessines (recouverts) ;</li>
 * <li>ghillie incomplete : le haut est resserre pour rester sous la veste (le devant reste visible, veste ouverte) ;</li>
 * <li>l'equipement porte par-dessus passe au-dessus de la toile de la ghillie (casque grossi comme avant, gilet agrandi
 * juste de ce qu'il faut), et la ghillie cache ses meches la ou il appuie (poitrine et dos sous un gilet, dos sous un
 * sac, dessus de capuche sous un casque, voile sous un masque, bande du bras sous un brassard) ;</li>
 * <li>sans ghillie, le brassard passe au-dessus du haut porte (elargi selon son debord).</li>
 * </ul>
 * Seuls les emplacements dont le rendu est active (bouton oeil de Curios) comptent. Etat calcule une fois par image et
 * par entite.
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class GearLayering {
    private GearLayering() {}

    /** Grossissement de l'equipement porte par-dessus une ghillie (largeur, hauteur, profondeur). */
    static final float[] HEADGEAR = {1.14F, 1.03F, 1.14F}, ARMBAND = {1.1F, 1.0F, 1.1F};
    /** Toile des ghillies (la plus large des deux, + marge) : flancs et devant / dos, en px depuis l'axe du corps. */
    private static final float SHELL_X = 4.7F, SHELL_Z = 2.8F;

    /** Ce que porte l'entite, vu par le rendu. ghillie = null : pas de ghillie ; chestOv = debord du haut porte (ou null). */
    public record State(@Nullable GearDef ghillie, boolean head, boolean face, boolean vest, boolean armband, boolean back,
                        @Nullable float[] chestOv) {
        static final State NONE = new State(null, false, false, false, false, false, null);

        public boolean full() {
            return ghillie != null && "full".equals(ghillie.gh());
        }
    }

    private static final Map<Entity, State> CACHE = new IdentityHashMap<>();

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) CACHE.clear();
    }

    public static State of(@Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living)) return State.NONE;
        State s = CACHE.get(entity);
        if (s == null) {
            s = compute(living);
            CACHE.put(entity, s);
        }
        return s;
    }

    private static State compute(LivingEntity living) {
        var inv = CuriosApi.getCuriosInventory(living).resolve();
        if (inv.isEmpty()) return State.NONE;
        GearDef ghillie = null;
        boolean head = !living.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        float[] chestOv = null;
        if (living.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof PieceItem p && "chest".equals(p.def().piece())) {
            ArmorModels.Fit fit = ArmorModels.INSTANCE.fit(p.def());
            chestOv = fit == null ? null : fit.ov();
        }
        boolean face = false, vest = false, armband = false, back = false;
        for (Map.Entry<String, ICurioStacksHandler> entry : inv.get().getCurios().entrySet()) {
            ICurioStacksHandler handler = entry.getValue();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStacks().getStackInSlot(i);
                if (stack.isEmpty() || (i < handler.getRenders().size() && !handler.getRenders().get(i))) continue;
                switch (entry.getKey()) {
                    case "body" -> {
                        if (stack.getItem() instanceof GearItem g && g.gear().gh() != null) ghillie = g.gear();
                    }
                    case "head" -> head = true;
                    case "mask" -> face = true;
                    case "belt" -> vest = true;
                    case "bracelet" -> armband = true;
                    case "back" -> back = true;
                    default -> {
                    }
                }
            }
        }
        return new State(ghillie, head, face, vest, armband, back, chestOv);
    }

    /**
     * Piece de l'Armurerie portee avec une ghillie : ajuste son modele (resserre sous une ghillie incomplete, agrandi
     * par-dessus). Faux : la piece est recouverte par une ghillie integrale, ne pas la dessiner. Le modele doit etre
     * remis a zero apres le rendu (MeshArmorModel.clearFit).
     */
    public static boolean fitPiece(LivingEntity entity, PieceDef def, HumanoidModel<?> model) {
        if (!(model instanceof MeshArmorModel mesh)) return true;
        mesh.clearFit();
        State s = of(entity);
        if (s.ghillie() == null) return true;
        ArmorModels.Fit fit = ArmorModels.INSTANCE.fit(def);
        switch (def.piece()) {
            case "chest" -> {
                if (s.full()) return false;
                float[] sq = fit == null ? null : fit.sq();
                if (sq != null) {
                    centered(mesh, 1, sq[0], 1.0F, sq[1]);
                    centered(mesh, 2, sq[2], 1.0F, sq[3]);
                    centered(mesh, 3, sq[2], 1.0F, sq[3]);
                }
            }
            case "legs", "knees" -> {
                if (s.full()) return false;
            }
            // la tete grandit a partir du cou (le casque ne descend pas sur les yeux)
            case "head" -> mesh.fit(0, 0.0F, 0.0F, 0.0F, HEADGEAR[0], HEADGEAR[1], HEADGEAR[2]);
            case "vest" -> {
                float[] in = fit == null ? null : fit.vest();
                if (in != null) {
                    float sx = in[0] > 0 ? Math.max(1.0F, SHELL_X / in[0]) : 1.0F;
                    float sz = in[1] > 0 ? Math.max(1.0F, SHELL_Z / in[1]) : 1.0F;
                    mesh.fit(1, 0.0F, 6.0F, 0.0F, sx, 1.0F, sz);
                }
            }
            default -> {
            }
        }
        return true;
    }

    /** Ajustement autour du centre des cubes de base de la partie (la manche, le torse). */
    private static void centered(MeshArmorModel mesh, int part, float sx, float sy, float sz) {
        float[] b = mesh.base(part);
        if (b == null) b = mesh.bounds(part);
        if (b == null) return;
        mesh.fit(part, (b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2, sx, sy, sz);
    }
}
