package fr.heraliz.crac.bases;

import fr.heraliz.crac.Crac;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.data.SafeSavedData;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Objets à vie de HeraliZ Tactique (couteaux, skins à vie) dans les bases : jamais au sol ni détruits (02/11, audit point
 * 8, règle du propriétaire : un objet à vie n'est jamais perdu, sauf si un admin le supprime).
 * <ul>
 *   <li>Coffre ou caisse de CRac retiré (raid, chute faute d'appui, usure, démolition) : son contenu tombe comme avant,
 *   SAUF ses objets à vie, sortis avant et gardés pour le chef de la base (le propriétaire du groupe de son claim).</li>
 *   <li>Base effacée ({@code /base effacer}, fin du tutoriel sans place, {@code /wipe}) : les objets à vie de ses
 *   conteneurs et ceux restés au sol sont gardés pour son propriétaire au lieu de disparaître.</li>
 *   <li>Gardés avec le monde principal ({@code data/crac_objets_a_vie.dat}) et rendus dans l'inventaire du joueur tout
 *   de suite s'il est connecté, sinon à sa connexion ; inventaire plein : ils attendent (nouvel essai toutes les 30 s).</li>
 *   <li>Sans propriétaire connu (conteneur hors de tout claim) : gardés à part, avec une ligne au journal du serveur,
 *   pour que le staff les rende ; jamais détruits.</li>
 * </ul>
 * Fil du serveur seulement.
 */
@EventBusSubscriber(modid = "crac")
public final class ObjetsAVieBases extends SafeSavedData {
    public static final String ID = "crac_objets_a_vie";
    /** Objets sans propriétaire connu : gardés sous cette clé. */
    static final UUID SANS_PROPRIETAIRE = new UUID(0L, 0L);
    private static Method estAVie;
    private static boolean estAVieCherche;
    private static int ticks;

    /** Joueur -> objets à lui rendre. */
    private final Map<UUID, List<ItemStack>> aRendre = new LinkedHashMap<>();

    public static ObjetsAVieBases get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ObjetsAVieBases::load,
            () -> SafeSavedData.fresh(server.overworld(), ID, new ObjetsAVieBases()), ID);
    }

    /** Objet à vie de HeraliZ Tactique ({@code fr.heraliz.tactique.avie.ObjetsAVie.estAVie}, par réflexion) ; faux sans lui. */
    public static boolean estAVie(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (!estAVieCherche) {
            estAVieCherche = true;
            try {
                estAVie = Class.forName("fr.heraliz.tactique.avie.ObjetsAVie").getMethod("estAVie", ItemStack.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                estAVie = null;
            }
        }
        try {
            return estAVie != null && Boolean.TRUE.equals(estAVie.invoke(null, s));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Le chef de la base qui contient {@code pos} (propriétaire du groupe de son claim), ou null hors de tout claim. */
    @Nullable
    static UUID chefDe(ServerLevel level, BlockPos pos) {
        Claim c = ClaimManager.getAt(level, pos);
        Group g = c == null ? null : GroupData.get(level.getServer()).getById(c.groupId());
        return g == null ? null : g.owner();
    }

    /**
     * Un conteneur de CRac va être retiré et son contenu tomber : ses objets à vie en sont sortis et gardés pour le chef de
     * la base (appelé par CoffreBlock et MetalCrateBlock, juste avant de faire tomber le reste).
     */
    public static void avantChute(ServerLevel level, BlockPos pos, Container c) {
        UUID chef = null;
        boolean cherche = false;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (estAVie(s)) {
                if (!cherche) {
                    chef = chefDe(level, pos);
                    cherche = true;
                }
                garder(level.getServer(), chef, c.removeItemNoUpdate(i), "conteneur retiré en " + pos.toShortString());
            }
        }
    }

    /** Dernier refus montré à chaque joueur (ms), pour ne pas répéter le message à chaque clic. */
    private static final Map<UUID, Long> REFUS_MONTRE = new java.util.HashMap<>();

    /**
     * Un joueur peut-il sortir cet objet de ce conteneur (02/11, choix du propriétaire) ? Un objet à vie rangé dans un
     * conteneur d'une base (bloc dans un claim) ne sort qu'avec ceux qui peuvent ouvrir les coffres de cette base (son clan
     * avec le droit « Ouvrir les coffres », accès donnés dans l'armoire) : un pilleur le voit mais ne peut pas le prendre,
     * il reste dans le conteneur. Libres : créatif, spectateur, staff (comme {@link Protections#coffres}), conteneur hors
     * claim, claim sans groupe. Réglage {@code lifetimeItemsOwnerOnly} de crac-server.toml. Appelé par le mixin serveur
     * de {@code Slot.mayPickup} (seulement au clic) ; vrai pour tout le reste.
     */
    public static boolean peutPrendre(ServerPlayer p, net.minecraft.world.level.block.entity.BlockEntity be, ItemStack s) {
        if (s.isEmpty() || p.isCreative() || p.isSpectator() || p.hasPermissions(2) || !(be.getLevel() instanceof ServerLevel level)
            || !fr.heraliz.crac.Config.SPEC.isLoaded() || !fr.heraliz.crac.Config.lifetimeItemsOwnerOnly.get() || !estAVie(s)) {
            return true;
        }
        Claim c = ClaimManager.getAt(level, be.getBlockPos());
        Group g = c == null ? null : GroupData.get(level.getServer()).getById(c.groupId());
        if (g == null || fr.heraliz.crac.heraliz.ClanAccess.allows(g, p.getUUID(), fr.heraliz.crac.heraliz.ClanAccess.Action.CHESTS)) {
            return true;
        }
        long maintenant = System.currentTimeMillis();
        Long avant = REFUS_MONTRE.get(p.getUUID());
        if (avant == null || maintenant - avant > 2000) {
            REFUS_MONTRE.put(p.getUUID(), maintenant);
            T.barre(p, T.t("crac.avie.pilleur", "Objet à vie : seuls les membres de cette base peuvent le prendre."), ChatFormatting.YELLOW);
        }
        return false;
    }

    @SubscribeEvent
    public static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        REFUS_MONTRE.remove(e.getEntity().getUUID());
    }

    /** Garde un objet à vie pour {@code joueur} (null : sans propriétaire connu) et le lui rend s'il est connecté. */
    public static void garder(MinecraftServer server, @Nullable UUID joueur, ItemStack s, String pourquoi) {
        if (s.isEmpty()) {
            return;
        }
        UUID u = joueur == null ? SANS_PROPRIETAIRE : joueur;
        ObjetsAVieBases d = get(server);
        d.aRendre.computeIfAbsent(u, k -> new ArrayList<>()).add(s.copy());
        d.setDirty();
        Crac.LOGGER.info("[CRac] objet à vie gardé pour {} ({}) : {}", joueur == null ? "personne (staff)" : joueur, pourquoi,
            s.getHoverName().getString());
        ServerPlayer p = joueur == null ? null : server.getPlayerList().getPlayer(joueur);
        if (p != null) {
            rendre(p);
        }
    }

    /** Rend au joueur ce qui l'attend, autant que son inventaire le permet ; le reste attend. */
    static void rendre(ServerPlayer p) {
        ObjetsAVieBases d = get(p.server);
        List<ItemStack> l = d.aRendre.get(p.getUUID());
        if (l == null || l.isEmpty() || p.isCreative() || p.isSpectator() || !p.isAlive()) {
            return;
        }
        int rendus = 0;
        List<ItemStack> reste = new ArrayList<>();
        for (ItemStack s : l) {
            ItemStack copie = s.copy();
            p.getInventory().add(copie);
            if (copie.isEmpty()) {
                rendus++;
            } else {
                reste.add(copie);
            }
        }
        if (reste.isEmpty()) {
            d.aRendre.remove(p.getUUID());
        } else {
            d.aRendre.put(p.getUUID(), reste);
        }
        d.setDirty();
        if (rendus > 0) {
            p.inventoryMenu.broadcastChanges();
            T.chat(p, T.t("crac.avie.rendu", "%s objet(s) à vie de ta base (coffre détruit ou base effacée) rendu(s) dans ton inventaire.",
                String.valueOf(rendus)), ChatFormatting.GOLD);
        }
        if (!reste.isEmpty()) {
            T.chat(p, T.t("crac.avie.attente", "%s objet(s) à vie de ta base t'attendent : fais de la place dans ton inventaire.",
                String.valueOf(reste.size())), ChatFormatting.YELLOW);
        }
    }

    @SubscribeEvent
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            rendre(p);
        }
    }

    /** Toutes les 30 s, seulement s'il y a quelque chose à rendre : nouvel essai pour les joueurs connectés. */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer() == null || ++ticks % 600 != 0) {
            return;
        }
        ObjetsAVieBases d = get(e.getServer());
        if (d.aRendre.isEmpty()) {
            return;
        }
        for (UUID u : List.copyOf(d.aRendre.keySet())) {
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(u);
            if (p != null) {
                rendre(p);
            }
        }
    }

    public static ObjetsAVieBases load(CompoundTag tag) {
        ObjetsAVieBases d = new ObjetsAVieBases();
        ListTag joueurs = tag.getList("Joueurs", Tag.TAG_COMPOUND);
        for (int i = 0; i < joueurs.size(); i++) {
            CompoundTag j = joueurs.getCompound(i);
            if (!j.hasUUID("Joueur")) {
                continue;
            }
            List<ItemStack> l = new ArrayList<>();
            ListTag objets = j.getList("Objets", Tag.TAG_COMPOUND);
            for (int k = 0; k < objets.size(); k++) {
                ItemStack s = ItemStack.of(objets.getCompound(k));
                if (!s.isEmpty()) {
                    l.add(s);
                }
            }
            if (!l.isEmpty()) {
                d.aRendre.put(j.getUUID("Joueur"), l);
            }
        }
        return d;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag joueurs = new ListTag();
        for (Map.Entry<UUID, List<ItemStack>> e : this.aRendre.entrySet()) {
            CompoundTag j = new CompoundTag();
            j.putUUID("Joueur", e.getKey());
            ListTag objets = new ListTag();
            for (ItemStack s : e.getValue()) {
                objets.add(s.save(new CompoundTag()));
            }
            j.put("Objets", objets);
            joueurs.add(j);
        }
        tag.put("Joueurs", joueurs);
        return tag;
    }
}
