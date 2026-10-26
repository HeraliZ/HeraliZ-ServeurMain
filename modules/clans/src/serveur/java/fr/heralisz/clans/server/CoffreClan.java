package fr.heralisz.clans.server;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Perm;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Coffre du clan (3.5.0, demande du propriétaire) : une ligne de 9 cases partagée par tout le clan, ouverte depuis
 * l'onglet Banque (bouton « Coffre ») ou par {@code /clan coffre}. Déposer = permission BANQUE_DEPOSER, prendre =
 * BANQUE_RETIRER (le chef a tout). Interdits : armes (armes TaCZ, couteaux compris, mêlée, Wonder Weapons, arcs,
 * épées), armures (toute pièce d'armure, Armurerie), explosifs (grenades, C4 et charges de CRac, TNT), et les
 * contenants (sacs à dos, boîtes de Shulker) qui pourraient en cacher ; liste à compléter par le tag d'objets
 * {@code heralisz_clans:coffre_interdit}. Écran de coffre de Minecraft (1 ligne), comme les coffres personnels.
 *
 * <p>Même conteneur pour tous les membres qui l'ont ouvert (aucune copie possible). Gardé hors du monde, à côté de
 * clans.json : {@code heralisz_clans/coffres.dat} (survit à un changement de carte). Clan dissous : le contenu va dans
 * l'inventaire du chef s'il est en ligne (le reste à ses pieds), sinon il est perdu (noté dans le journal).
 * Rempli aussi par CRac ({@code ClansApi.deposerCoffre}) : matériel de la première base du clan.
 */
public final class CoffreClan {
    public static final int CASES = 9;
    private static final TagKey<Item> INTERDIT = TagKey.create(Registries.ITEM, new ResourceLocation("heralisz_clans", "coffre_interdit"));
    /** Mods dont tout objet est refusé (armures, grenades, mêlée, Wonder Weapons, sacs à dos). */
    private static final Set<String> MODS_INTERDITS = Set.of("heraliz_armory", "heralisz_armor", "backpacked");
    /**
     * Objets refusés un par un. Grenades et mêlée de Tactique sont enregistrées sous {@code heralisz_tactical} (pas sous
     * les modIds {@code heralisz_grenade} / {@code heralisz_melee}, qui ne portent aucun objet) : 23/10, elles entraient.
     */
    private static final Set<String> OBJETS_INTERDITS = Set.of("tacz:modern_kinetic_gun", "crac:c4", "crac:explosive_charge",
            "heralisz_tactical:throwable", "heralisz_tactical:melee", "heralisz_tactical:flash_shield",
            "heralisz_tactical:detonator", "minecraft:tnt", "minecraft:tnt_minecart", "minecraft:end_crystal");
    /** Joueurs dont l'inventaire a changé par le coffre depuis la dernière écriture (sauvegardés avec lui). */
    private static final Set<UUID> TOUCHES = new java.util.HashSet<>();
    /** {@code PlayerList.save(ServerPlayer)} (protégée), comme les coffres de match de Matchs. */
    @Nullable
    private static java.lang.reflect.Method sauverJoueur;

    private static final Map<UUID, SimpleContainer> COFFRES = new HashMap<>();
    @Nullable
    private static Path chargeDe;
    private static boolean sale;

    private CoffreClan() {
    }

    // ------------------------------------------------------------------ règles
    /** Vrai si l'objet ne peut pas entrer dans le coffre du clan. */
    public static boolean interdit(ItemStack s) {
        if (s.isEmpty()) return false;
        Item it = s.getItem();
        if (it instanceof ArmorItem || it instanceof SwordItem || it instanceof BowItem || it instanceof CrossbowItem
                || it instanceof TridentItem || s.is(INTERDIT)) {
            return true;
        }
        if (EtatJoueur.pretOuAVie(s)) return true; // objet prêté par un match, couteau ou skin à vie
        if (it instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
        return id != null && (MODS_INTERDITS.contains(id.getNamespace()) || OBJETS_INTERDITS.contains(id.toString()));
    }

    // ------------------------------------------------------------------ ouverture
    /** Ouvre le coffre de son clan (membre avec le droit de déposer ou de prendre). */
    /**
     * Fichier des coffres illisible (25/10, audit) : mis de côté ({@code coffres.dat.illisible.<date>}), coffres fermés et
     * jamais réécrits tant qu'il n'est pas réparé (avant : partis vides, et le prochain changement écrasait le fichier :
     * tous les clans perdaient leur coffre).
     */
    private static boolean illisible;

    public static void ouvrir(ServerPlayer p) {
        charger();
        if (illisible) {
            refus(p, "msg.coffre.illisible", "Le coffre du clan est fermé pour maintenance : préviens le staff.");
            return;
        }
        ClanStore s = ClanStore.get();
        Clan c = s == null ? null : s.clanDe(p.getUUID());
        if (c == null) {
            refus(p, "msg.coffre.sans_clan", "Tu n'as pas de clan.");
            return;
        }
        if (!peut(p, Perm.BANQUE_DEPOSER) && !peut(p, Perm.BANQUE_RETIRER)) {
            refus(p, "msg.coffre.refus", "Tu n'as pas le droit d'ouvrir le coffre du clan.");
            return;
        }
        Component empeche = empechement(p);
        if (empeche != null) {
            p.sendSystemMessage(empeche.copy().withStyle(ChatFormatting.RED));
            return;
        }
        SimpleContainer coffre = coffre(c.id);
        UUID clanId = c.id;
        p.closeContainer();
        p.openMenu(new SimpleMenuProvider((id, inv, joueur) -> new Menu(id, inv, coffre, clanId),
                Component.translatableWithFallback("gui.coffre.titre", "Coffre du clan [%s]", c.tag)));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BARREL_OPEN, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    /**
     * Pourquoi le coffre est fermé à ce joueur maintenant (23/10, demande du propriétaire), null sinon : en combat, à
     * terre, en match, ou sur la carte (on n'y range rien par commande : seulement au spawn ou ailleurs hors carte).
     * Le créatif et le spectateur passent.
     */
    @Nullable
    static Component empechement(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return null;
        if (EtatJoueur.enMatch(p))
            return Component.translatableWithFallback("msg.coffre.match", "Le coffre du clan est fermé pendant un match.");
        if (EtatJoueur.aTerre(p))
            return Component.translatableWithFallback("msg.coffre.a_terre", "Impossible d'ouvrir le coffre du clan à terre.");
        if (EtatJoueur.enCombat(p))
            return Component.translatableWithFallback("msg.coffre.combat", "Impossible d'ouvrir le coffre du clan en combat.");
        if (EtatJoueur.surCarte(p))
            return Component.translatableWithFallback("msg.coffre.carte", "Le coffre du clan ne s'ouvre pas sur la carte : reviens au spawn.");
        return null;
    }

    private static boolean peut(Player p, Perm perm) {
        return fr.heralisz.clans.api.ClansApi.aPerm(p.getUUID(), perm);
    }

    private static void refus(ServerPlayer p, String cle, String texte) {
        p.sendSystemMessage(Component.translatableWithFallback(cle, texte).withStyle(ChatFormatting.RED));
    }

    /** Menu du coffre : écran de coffre de Minecraft d'une ligne, cases filtrées. */
    private static final class Menu extends ChestMenu {
        private final UUID clanId;

        Menu(int id, Inventory inv, Container coffre, UUID clanId) {
            super(MenuType.GENERIC_9x1, id, inv, coffre, 1);
            this.clanId = clanId;
            for (int i = 0; i < CASES; i++) {
                Slot ancien = slots.get(i);
                Slot nouveau = new Case(coffre, i, ancien.x, ancien.y, inv.player);
                nouveau.index = ancien.index;
                slots.set(i, nouveau);
            }
        }

        @Override
        public boolean stillValid(Player p) {
            ClanStore s = ClanStore.get();
            Clan c = s == null ? null : s.clanDe(p.getUUID());
            if (p instanceof ServerPlayer sp && empechement(sp) != null) return false;
            return c != null && c.id.equals(clanId) && (peut(p, Perm.BANQUE_DEPOSER) || peut(p, Perm.BANQUE_RETIRER));
        }

        @Override
        public void removed(Player p) {
            super.removed(p);
            sale = true;
            TOUCHES.add(p.getUUID());
            sauvegarder(); // joueur puis coffre, tout de suite : un plantage ne laisse jamais l'objet aux deux endroits
        }

        /**
         * Maj + clic depuis l'inventaire : Minecraft complète d'abord les piles déjà dans le coffre sans demander à la
         * case ; sans ce contrôle, un membre qui n'a que le droit de prendre pouvait quand même déposer.
         */
        @Override
        public ItemStack quickMoveStack(Player p, int index) {
            if (index >= CASES && index < slots.size()) {
                ItemStack st = slots.get(index).getItem();
                if (!st.isEmpty() && (interdit(st) || !peut(p, Perm.BANQUE_DEPOSER))) return ItemStack.EMPTY;
            }
            return super.quickMoveStack(p, index);
        }
    }

    /** Case du coffre : objets interdits refusés, dépôt et retrait selon les droits du clan. */
    private static final class Case extends Slot {
        private final Player joueur;

        Case(Container c, int i, int x, int y, Player joueur) {
            super(c, i, x, y);
            this.joueur = joueur;
        }

        @Override
        public boolean mayPlace(ItemStack s) {
            return !interdit(s) && peut(joueur, Perm.BANQUE_DEPOSER);
        }

        @Override
        public boolean mayPickup(Player p) {
            return peut(p, Perm.BANQUE_RETIRER);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            sale = true;
            TOUCHES.add(joueur.getUUID());
        }
    }

    // ------------------------------------------------------------------ dépôt sans joueur (CRac)
    /**
     * Range des objets dans le coffre du clan (objets interdits jamais rangés). Renvoie ce qui n'a pas trouvé de place
     * (ou était interdit), à la charge de l'appelant.
     */
    public static List<ItemStack> deposer(UUID clanId, List<ItemStack> objets) {
        List<ItemStack> reste = new ArrayList<>();
        ClanStore s = ClanStore.get();
        if (s == null || s.get(clanId) == null) {
            for (ItemStack o : objets) reste.add(o.copy());
            return reste;
        }
        charger();
        if (illisible) {
            for (ItemStack o : objets) reste.add(o.copy());
            return reste;
        }
        SimpleContainer coffre = coffre(clanId);
        for (ItemStack o : objets) {
            if (o.isEmpty()) continue;
            if (interdit(o)) {
                reste.add(o.copy());
                continue;
            }
            ItemStack r = coffre.addItem(o.copy());
            if (!r.isEmpty()) reste.add(r);
        }
        sale = true;
        sauvegarder();
        return reste;
    }

    /** Vrai si le coffre du clan ne contient rien (faux si le fichier des coffres est illisible : contenu inconnu). */
    static boolean vide(UUID clanId) {
        charger();
        if (illisible) return false;
        SimpleContainer c = COFFRES.get(clanId);
        return c == null || c.isEmpty();
    }

    /** Ecrit tout de suite la sauvegarde d'un joueur qui vient de recevoir des objets du coffre (dissolution). */
    static void sauverJoueurMaintenant(ServerPlayer p) {
        TOUCHES.add(p.getUUID());
        sauverJoueurs();
    }

    /** Vide le coffre d'un clan dissous ; renvoie son contenu. */
    public static List<ItemStack> vider(UUID clanId) {
        charger();
        if (illisible) return new ArrayList<>();   // contenu inconnu : rien n'est donné ni effacé
        SimpleContainer c = COFFRES.remove(clanId);
        List<ItemStack> out = new ArrayList<>();
        if (c != null) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack st = c.removeItemNoUpdate(i);
                if (!st.isEmpty()) out.add(st);
            }
            sale = true;
            sauvegarder();
        }
        return out;
    }

    // ------------------------------------------------------------------ fichier
    private static SimpleContainer coffre(UUID clanId) {
        charger();
        return COFFRES.computeIfAbsent(clanId, u -> nouveau());
    }

    private static SimpleContainer nouveau() {
        SimpleContainer c = new SimpleContainer(CASES);
        c.addListener(x -> sale = true);
        return c;
    }

    @Nullable
    private static Path fichier() {
        ClanStore s = ClanStore.get();
        return s == null ? null : s.dossier().resolve("coffres.dat");
    }

    private static void charger() {
        Path f = fichier();
        if (f == null || f.equals(chargeDe)) return;
        chargeDe = f;
        COFFRES.clear();
        sale = false;
        illisible = false;
        if (!Files.exists(f)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(f.toFile());
            ListTag l = root.getList("coffres", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag t = l.getCompound(i);
                SimpleContainer c = nouveau();
                ListTag objets = t.getList("objets", Tag.TAG_COMPOUND);
                for (int j = 0; j < objets.size(); j++) {
                    CompoundTag o = objets.getCompound(j);
                    int slot = o.getByte("Slot") & 255;
                    if (slot < CASES) c.setItem(slot, ItemStack.of(o));
                }
                COFFRES.put(t.getUUID("clan"), c);
            }
            sale = false;
        } catch (IOException | RuntimeException ex) {
            illisible = true;
            COFFRES.clear();
            Path copie = f.resolveSibling("coffres.dat.illisible." + System.currentTimeMillis());
            try {
                Files.copy(f, copie);
            } catch (IOException ignore) {
                // la copie a échoué : le fichier lui-même n'est de toute façon jamais réécrit tant qu'il est illisible
            }
            HeraliszClans.LOG.error("[clans] lecture de {} impossible : coffres des clans FERMÉS, fichier jamais réécrit (copie {})", f, copie, ex);
        }
    }

    /** Écrit les coffres s'ils ont changé (appelé par la sauvegarde des clans et après tout dépôt sans joueur). */
    public static void sauvegarder() {
        Path f = fichier();
        if (!sale || illisible || f == null || !f.equals(chargeDe)) return;
        ClanStore s = ClanStore.get();
        sauverJoueurs();
        CompoundTag root = new CompoundTag();
        ListTag l = new ListTag();
        for (Map.Entry<UUID, SimpleContainer> e : COFFRES.entrySet()) {
            if (s != null && s.get(e.getKey()) == null) continue; // clan disparu
            ListTag objets = new ListTag();
            SimpleContainer c = e.getValue();
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack st = c.getItem(i);
                if (st.isEmpty()) continue;
                CompoundTag o = new CompoundTag();
                o.putByte("Slot", (byte) i);
                st.save(o);
                objets.add(o);
            }
            if (objets.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putUUID("clan", e.getKey());
            t.put("objets", objets);
            l.add(t);
        }
        root.put("coffres", l);
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("coffres.dat.tmp");
            NbtIo.writeCompressed(root, tmp.toFile());
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            sale = false;
        } catch (IOException ex) {
            HeraliszClans.LOG.error("[clans] sauvegarde de {} impossible", f, ex);
        }
    }

    /**
     * Écrit d'abord la sauvegarde des joueurs qui ont déplacé des objets par le coffre (Minecraft ne les écrit que
     * toutes les 5 min) : après un plantage, l'objet est soit dans le coffre, soit dans l'inventaire, jamais les deux.
     */
    private static void sauverJoueurs() {
        if (TOUCHES.isEmpty()) return;
        net.minecraft.server.MinecraftServer srv = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (srv != null) {
            for (UUID u : TOUCHES) {
                ServerPlayer p = srv.getPlayerList().getPlayer(u);
                if (p == null) continue;
                try {
                    if (sauverJoueur == null)
                        sauverJoueur = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                                net.minecraft.server.players.PlayerList.class, "m_6765_", ServerPlayer.class);
                    sauverJoueur.invoke(srv.getPlayerList(), p);
                } catch (Exception ex) {
                    HeraliszClans.LOG.error("[clans] sauvegarde du joueur {} impossible", p.getGameProfile().getName(), ex);
                }
            }
        }
        TOUCHES.clear();
    }

    /** Fin du serveur : tout écrire, puis oublier (le prochain serveur relit le fichier). */
    public static void fermer() {
        sauvegarder();
        COFFRES.clear();
        chargeDe = null;
    }
}
