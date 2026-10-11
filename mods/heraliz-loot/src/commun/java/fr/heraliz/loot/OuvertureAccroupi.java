package fr.heraliz.loot;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Fouille accroupi. Règle de Minecraft : accroupi avec un objet dans une main (arme, soin, nourriture...), un clic droit
 * sur un bloc n'utilise pas le bloc (c'est le geste pour poser un bloc contre un coffre). Accroupi, on ne pouvait donc
 * ouvrir ni les conteneurs de loot (T1 à T7), ni les coffres, caisses de ravitaillement et meubles de rangement. Ici,
 * accroupi avec un objet qui ne se pose pas, le clic droit sur un conteneur l'ouvre comme debout
 * ({@code PlayerInteractEvent.RightClickBlock#setUseBlock(ALLOW)}, comme le fait déjà heraliz_crates pour ses caisses).
 * <p>Code commun, pour les deux côtés : l'événement est lancé chez le joueur (prédiction du clic) ET sur le serveur
 * (décision), avec les mêmes informations, donc les deux décident pareil. Priorité la plus basse et seulement sur un
 * événement resté à DEFAULT : un refus posé par un autre mod (claims et plan / marteau de CRac, bases des clans...) est
 * gardé, et un événement annulé (ramassage de CRac, éditeur des caisses...) n'arrive pas jusqu'ici.</p>
 * <p>Inchangé (Maj + clic droit garde son sens) : les deux mains vides (Minecraft ouvre déjà), les objets qui se posent
 * (blocs, seaux, oeufs, cadres, barricade...), les objets de CRac (plan, marteau, C4, charges, fenêtres) et les objets
 * lancés de Tactique (grenades, C4 : lancer doux accroupi). Les entités (joueur à terre, cadavre) ne passent pas par
 * ici.</p>
 */
public final class OuvertureAccroupi {
    /** Objets qui se posent sans être des blocs (identifiants complets). */
    private static final Set<String> POSES = Set.of("heralisz_bunker:wooden_barricade");
    /** Mods dont les objets servent à construire ou à raider : jamais d'ouverture forcée. */
    private static final Set<String> MODS_CONSTRUCTION = Set.of("crac");
    /** Objets lancés de Tactique (grenades, C4), lus par réflexion : le mod peut manquer. */
    private static final String LANCER = "fr.heralisz.tactical.api.item.IThrowable";

    @Nullable
    private static volatile Class<?> lancer;
    private static volatile boolean lancerCherche;

    private OuvertureAccroupi() {
    }

    /** Branché par le constructeur du mod (les trois jars). */
    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PlayerInteractEvent.RightClickBlock.class,
                OuvertureAccroupi::clicDroit);
    }

    static void clicDroit(PlayerInteractEvent.RightClickBlock e) {
        if (e.getUseBlock() != Event.Result.DEFAULT) return;
        Player p = e.getEntity();
        // debout, Minecraft utilise déjà le bloc ; un spectateur passe par une autre règle
        if (p.isSpectator() || !p.isSecondaryUseActive()) return;
        ItemStack main = p.getMainHandItem();
        ItemStack seconde = p.getOffhandItem();
        if (main.isEmpty() && seconde.isEmpty()) return;
        // les deux mains : Minecraft regarde les deux (bloc dans l'autre main = Maj + clic droit pour le poser)
        if (sePose(main) || sePose(seconde)) return;
        Level l = e.getLevel();
        BlockPos pos = e.getPos();
        if (conteneur(l, pos, l.getBlockState(pos))) e.setUseBlock(Event.Result.ALLOW);
    }

    /** Objet qu'on pose (ou qu'on lance) avec Maj + clic droit : la règle de Minecraft reste. */
    static boolean sePose(ItemStack s) {
        if (s.isEmpty()) return false;
        Item it = s.getItem();
        if (it instanceof BlockItem || it instanceof BucketItem || it instanceof SpawnEggItem || it instanceof HangingEntityItem
                || it instanceof ArmorStandItem || it instanceof BoatItem || it instanceof MinecartItem
                || it instanceof EndCrystalItem || it instanceof FireChargeItem || it instanceof FlintAndSteelItem) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
        if (id != null && (MODS_CONSTRUCTION.contains(id.getNamespace()) || POSES.contains(id.toString()))) return true;
        Class<?> c = lancer();
        return c != null && c.isInstance(it);
    }

    /**
     * Bloc qu'on fouille : conteneur de loot (T1-T6, sauf véhicules de décor ; T7 ; leurs blocs « partie »), ou bloc dont
     * l'entité est un inventaire ou ouvre un menu (coffres, tonneaux, meubles cfm, caisses de ravitaillement...).
     */
    static boolean conteneur(Level l, BlockPos pos, BlockState st) {
        Block b = st.getBlock();
        if (b instanceof PartieBlock) {
            Block maitre = l.getBlockState(PartieBlock.principal(pos, st)).getBlock();
            return maitre instanceof ConteneurBlock cb ? !cb.vehicule : maitre instanceof CoffreBlock;
        }
        if (b instanceof ConteneurBlock cb) return !cb.vehicule;
        if (b instanceof CoffreBlock) return true;
        BlockEntity be = l.getBlockEntity(pos);
        return be instanceof Container || be instanceof MenuProvider;
    }

    @Nullable
    private static Class<?> lancer() {
        if (!lancerCherche) {
            try {
                // sans l'initialiser : seul le type compte
                lancer = Class.forName(LANCER, false, OuvertureAccroupi.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError ex) {
                lancer = null;
            }
            lancerCherche = true;
        }
        return lancer;
    }
}
