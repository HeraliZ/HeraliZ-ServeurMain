package fr.heralisz.utils.coffres;

import fr.heralisz.utils.HeraliszUtils;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Coffres personnels (18/10, demande du propriétaire) : un clic droit sur un coffre-fort du mod de meubles ouvre le coffre
 * du joueur, le même quel que soit le bloc (comme le coffre de l'Ender) :
 * <ul>
 *    <li>« Safe Planque 1 » ({@code cfm:safe_planque_1}) : la planque, 1 ligne (9 cases), pour tout le monde ;</li>
 *    <li>« Safe Sentinel 1 » ({@code cfm:safe_sentinel_1}) : le coffre VIP, 1 ligne de plus, seulement pour les grades de
 *    la boutique Rescapé, Traqueur et Roi de la Zone (HeraliZ Grades, par réflexion ; sans Grades : fermé). Un joueur qui
 *    perd son grade garde ses objets, mais ne peut plus l'ouvrir tant qu'il n'a pas repris un grade.</li>
 * </ul>
 * Aucun sac à dos dedans (ni posé à la main, ni par Maj + clic). Objets toujours déplacés, jamais copiés. Gardés avec le
 * monde ({@code data/heraliz_coffres_perso.dat} du monde principal) : ils repartent de zéro avec une nouvelle carte, comme
 * les inventaires. Le staff (op) les voit et les modifie avec /inspecter (Inspecter).
 */
public final class CoffresPersonnels {
    public static final ResourceLocation BLOC_PLANQUE = new ResourceLocation("cfm", "safe_planque_1");
    public static final ResourceLocation BLOC_VIP = new ResourceLocation("cfm", "safe_sentinel_1");
    /** Cases d'une ligne. */
    public static final int CASES = 9;
    /** Cases gardées par coffre : 6 lignes au plus (taille montrée selon le grade, HeraliZ Grades 2.0.0). */
    public static final int CASES_MAX = 54;
    private static final String FICHIER = "heraliz_coffres_perso";

    public enum Sorte {
        PLANQUE("planque"), VIP("vip");

        final String cle;

        Sorte(String cle) {
            this.cle = cle;
        }
    }

    private CoffresPersonnels() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.RightClickBlock.class, CoffresPersonnels::clic);
    }

    private static void clic(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel l)) {
            return;
        }
        Block bloc = l.getBlockState(e.getPos()).getBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(bloc);
        Sorte sorte = BLOC_PLANQUE.equals(id) ? Sorte.PLANQUE : BLOC_VIP.equals(id) ? Sorte.VIP : null;
        if (sorte == null) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() != InteractionHand.MAIN_HAND || p.isSpectator()) {
            return;
        }
        int lignes = lignes(p.getUUID(), sorte);
        if (lignes <= 0) {
            p.displayClientMessage(Component.translatableWithFallback("heralisz_utils.coffre.vip_refus2",
                    "Coffre réservé à certains grades."), true);
            return;
        }
        Coffre c = coffre(p.server, p.getUUID(), sorte);
        BlockPos pos = e.getPos().immutable();
        p.openMenu(new SimpleMenuProvider((cid, inv, joueur) -> new MenuCoffre(cid, inv, c, l, pos, bloc, lignes), titre(sorte)));
    }

    public static Component titre(Sorte sorte) {
        return sorte == Sorte.PLANQUE
                ? Component.translatableWithFallback("heralisz_utils.coffre.planque", "Planque")
                : Component.translatableWithFallback("heralisz_utils.coffre.vip", "Coffre VIP");
    }

    /** Le coffre de ce joueur (créé vide au besoin). Fil serveur. */
    public static Coffre coffre(MinecraftServer srv, UUID joueur, Sorte sorte) {
        return donnees(srv).coffre(joueur, sorte);
    }

    /** Un sac à dos (HeraliZ Backpack) : jamais dans un coffre personnel. */
    public static boolean estSac(ItemStack s) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id != null && "backpacked".equals(id.getNamespace());
    }

    /**
     * Les {@code n} premières cases du menu acceptent seulement ce que le conteneur accepte ({@code canPlaceItem}) : la
     * case de Minecraft accepte tout, même par Maj + clic.
     */
    public static void casesSelonConteneur(AbstractContainerMenu menu, Container c, int n) {
        for (int i = 0; i < n && i < menu.slots.size(); i++) {
            Slot ancienne = menu.slots.get(i);
            Slot nouvelle = new Slot(c, ancienne.getContainerSlot(), ancienne.x, ancienne.y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return this.container.canPlaceItem(this.getContainerSlot(), stack);
                }
            };
            nouvelle.index = ancienne.index;
            menu.slots.set(i, nouvelle);
        }
    }

    private static Donnees donnees(MinecraftServer srv) {
        return srv.overworld().getDataStorage().computeIfAbsent(Donnees::charger, Donnees::new, FICHIER);
    }

    // ------------------------------------------------------------------ grade (HeraliZ Grades, par réflexion)
    private static Method perm;
    private static boolean permCherche;

    /**
     * Lignes montrées de ce coffre (1 à 6 ; 0 = fermé) : permissions « planque_lignes » et « coffre_vip_lignes » des grades
     * (HeraliZ Grades 2.0.0, réglées dans l'éditeur des grades). Avec une version plus ancienne : planque 1 ligne, coffre VIP
     * 1 ligne pour les grades de la boutique. Un joueur qui perd des lignes garde ses objets (cachés, pas perdus).
     */
    private static int lignes(UUID joueur, Sorte sorte) {
        if (!permCherche) {
            permCherche = true;
            try {
                perm = Class.forName("fr.heraliz.grades.serveur.GradesApi").getMethod("perm", UUID.class, String.class);
            } catch (Throwable t) {
                perm = null;
            }
        }
        if (perm != null) {
            try {
                int v = (int) perm.invoke(null, joueur, sorte == Sorte.PLANQUE ? "planque_lignes" : "coffre_vip_lignes");
                if (v >= 0) {
                    return Math.min(6, sorte == Sorte.PLANQUE ? Math.max(1, v) : v);
                }
            } catch (Throwable t) {
                // règle d'avant ci-dessous
            }
        }
        return sorte == Sorte.PLANQUE ? 1 : rangBoutique(joueur) > 0 ? 1 : 0;
    }

    private static Method rang;
    private static boolean rangCherche;

    private static int rangBoutique(UUID joueur) {
        if (!rangCherche) {
            rangCherche = true;
            try {
                rang = Class.forName("fr.heraliz.grades.serveur.GradesApi").getMethod("rangBoutique", UUID.class);
            } catch (Throwable t) {
                HeraliszUtils.LOG.warn("[coffres] HeraliZ Grades absent : le coffre VIP reste fermé ({})", t.toString());
            }
        }
        if (rang == null) {
            return 0;
        }
        try {
            return (int) rang.invoke(null, joueur);
        } catch (Throwable t) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ contenu

    /** Un coffre personnel : 54 cases gardées (6 lignes), sans sac à dos ; chaque changement est enregistré avec le monde. */
    public static final class Coffre extends SimpleContainer {
        private final Donnees donnees;

        Coffre(Donnees donnees) {
            super(CASES_MAX);
            this.donnees = donnees;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !estSac(stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            this.donnees.setDirty();
        }

        ListTag ecrire() {
            ListTag l = new ListTag();
            for (int i = 0; i < getContainerSize(); i++) {
                ItemStack s = getItem(i);
                if (!s.isEmpty()) {
                    CompoundTag t = s.save(new CompoundTag());
                    t.putByte("Slot", (byte) i);
                    l.add(t);
                }
            }
            return l;
        }

        void lire(ListTag l) {
            for (int i = 0; i < l.size(); i++) {
                CompoundTag t = l.getCompound(i);
                int slot = t.getByte("Slot") & 255;
                if (slot < getContainerSize()) {
                    super.setItem(slot, ItemStack.of(t));
                }
            }
        }
    }

    static final class Donnees extends SavedData {
        private final Map<UUID, Coffre[]> coffres = new HashMap<>();

        Coffre coffre(UUID joueur, Sorte sorte) {
            Coffre[] t = this.coffres.computeIfAbsent(joueur, u -> new Coffre[Sorte.values().length]);
            if (t[sorte.ordinal()] == null) {
                t[sorte.ordinal()] = new Coffre(this);
            }
            return t[sorte.ordinal()];
        }

        static Donnees charger(CompoundTag tag) {
            Donnees d = new Donnees();
            CompoundTag joueurs = tag.getCompound("Joueurs");
            for (String cle : joueurs.getAllKeys()) {
                UUID u;
                try {
                    u = UUID.fromString(cle);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                CompoundTag j = joueurs.getCompound(cle);
                for (Sorte s : Sorte.values()) {
                    if (j.contains(s.cle, Tag.TAG_LIST)) {
                        d.coffre(u, s).lire(j.getList(s.cle, Tag.TAG_COMPOUND));
                    }
                }
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag joueurs = new CompoundTag();
            this.coffres.forEach((u, t) -> {
                CompoundTag j = new CompoundTag();
                for (Sorte s : Sorte.values()) {
                    Coffre c = t[s.ordinal()];
                    if (c != null && !c.isEmpty()) {
                        j.put(s.cle, c.ecrire());
                    }
                }
                if (!j.isEmpty()) {
                    joueurs.put(u.toString(), j);
                }
            });
            tag.put("Joueurs", joueurs);
            return tag;
        }
    }

    /** Coffre ouvert par un joueur : fermé s'il s'éloigne (8 blocs) ou si le coffre-fort disparaît. */
    private static final class MenuCoffre extends ChestMenu {
        private final ServerLevel level;
        private final BlockPos pos;
        private final Block bloc;

        private static final MenuType<?>[] TYPES = {MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3, MenuType.GENERIC_9x4,
                MenuType.GENERIC_9x5, MenuType.GENERIC_9x6};

        MenuCoffre(int id, Inventory inv, Coffre c, ServerLevel level, BlockPos pos, Block bloc, int lignes) {
            super(TYPES[Math.max(1, Math.min(6, lignes)) - 1], id, inv, c, Math.max(1, Math.min(6, lignes)));
            casesSelonConteneur(this, c, CASES * Math.max(1, Math.min(6, lignes)));
            this.level = level;
            this.pos = pos;
            this.bloc = bloc;
        }

        @Override
        public boolean stillValid(Player p) {
            return p.isAlive() && p.level() == this.level && this.level.getBlockState(this.pos).is(this.bloc)
                    && p.distanceToSqr(Vec3.atCenterOf(this.pos)) <= 64.0D;
        }
    }
}
