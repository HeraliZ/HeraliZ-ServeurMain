package fr.heralisz.matchs.moteur.arenes;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Arenes;
import fr.heralisz.matchs.moteur.api.Equipement;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.spectateur.Spectateurs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Regles des dimensions d'arenes et des objets pretes (MATCHS_SPEC §1.3, §1.5) :
 * <ul>
 *     <li>casse/pose refusees (sauf {@link Partie#peutCasser}/{@link Partie#peutPoser}, ou operateur en creatif hors
 *     match pour l'entretien) ; explosions sans degats aux blocs ;</li>
 *     <li>conteneurs (coffres, ender chest, tout bloc-entite conteneur d'un autre mod) refuses en arene ;</li>
 *     <li>objet prete lache : remis dans l'inventaire (on ne lache jamais son arme) ; objet prete qui apparait au sol :
 *     supprime ;</li>
 *     <li>commandes de stockage, d'echange et de teleportation refusees en match (operateurs exceptes) ;</li>
 *     <li>bornes : un joueur vivant de la partie hors des bornes de son instance (ou tombe sous le sol) =
 *     {@link Partie#surSortieBornes} (au plus une fois par seconde par joueur).</li>
 * </ul>
 * Fil serveur.
 */
public final class Protections {
    private Protections() {
    }

    /** Commandes refusees en match (racine en minuscules). */
    public static final Set<String> COMMANDES_BLOQUEES = Set.of("pv", "playervaults", "vault", "trade", "ec", "enderchest", "echest",
            "home", "homes", "sethome", "spawn", "warp", "warps", "back", "tpa", "tpahere", "tpaccept", "rtp", "wild", "kit", "kits",
            "hat", "craft", "workbench", "anvil", "disposal", "trash", "invsee", "suicide");
    private static final Map<UUID, Integer> DERNIERE_SORTIE = new HashMap<>();

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Protections::casse);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Protections::pose);
        MinecraftForge.EVENT_BUS.addListener(Protections::explosion);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Protections::interaction);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Protections::lache);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Protections::apparition);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Protections::commande);
    }

    private static boolean entretien(Player p) {
        return p instanceof ServerPlayer sp && !MatchApi.enMatch(sp) && sp.hasPermissions(2)
                && sp.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    private static void casse(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof Level l) || !Arenes.estArene(l)) return;
        Player p = e.getPlayer();
        if (entretien(p)) return;
        Partie partie = p instanceof ServerPlayer sp ? MatchApi.partieDe(sp) : null;
        if (partie != null && autorise(() -> partie.peutCasser((ServerPlayer) p, e.getPos(), e.getState()))) return;
        e.setCanceled(true);
    }

    private static void pose(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof Level l) || !Arenes.estArene(l)) return;
        if (e.getEntity() instanceof Player p) {
            if (entretien(p)) return;
            Partie partie = p instanceof ServerPlayer sp ? MatchApi.partieDe(sp) : null;
            BlockState etat = e.getPlacedBlock();
            if (partie != null && autorise(() -> partie.peutPoser((ServerPlayer) p, e.getPos(), etat))) return;
        }
        e.setCanceled(true);
    }

    private static boolean autorise(java.util.function.BooleanSupplier s) {
        try {
            return s.getAsBoolean();
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[matchs] regle de bloc du mode en erreur", ex);
            return false;
        }
    }

    private static void explosion(ExplosionEvent.Detonate e) {
        if (Arenes.estArene(e.getLevel())) e.getAffectedBlocks().clear();
    }

    private static void interaction(PlayerInteractEvent.RightClickBlock e) {
        Level l = e.getLevel();
        if (l.isClientSide || !Arenes.estArene(l) || entretien(e.getEntity())) return;
        BlockState etat = l.getBlockState(e.getPos());
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(etat.getBlock());
        if (id != null && id.getNamespace().equals(HeraliszMatchs.MODID)) return;
        BlockEntity be = l.getBlockEntity(e.getPos());
        if (etat.getBlock() instanceof EnderChestBlock || be instanceof Container) {
            e.setUseBlock(Event.Result.DENY);
            e.setCanceled(true);
        }
    }

    private static void lache(ItemTossEvent e) {
        ItemStack st = e.getEntity().getItem();
        if (!Equipement.estPrete(st)) return;
        e.setCanceled(true);
        Player p = e.getPlayer();
        if (p instanceof ServerPlayer sp && MatchApi.enMatch(sp)) {
            // on ne lache jamais son equipement de match : il revient dans l'inventaire
            if (!sp.getInventory().add(st.copy())) HeraliszMatchs.LOG.debug("[matchs] objet prete perdu (inventaire plein)");
            sp.inventoryMenu.broadcastChanges();
        }
    }

    private static void apparition(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof ItemEntity it)) return;
        if (Equipement.estPrete(it.getItem())) e.setCanceled(true);
    }

    private static void commande(CommandEvent e) {
        var ctx = e.getParseResults().getContext();
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer p) || !MatchApi.enMatch(p) || p.hasPermissions(2)) return;
        String brut = e.getParseResults().getReader().getString().trim();
        if (brut.startsWith("/")) brut = brut.substring(1);
        int sp = brut.indexOf(' ');
        String racine = (sp < 0 ? brut : brut.substring(0, sp)).toLowerCase(Locale.ROOT);
        int deux = racine.indexOf(':');
        if (deux >= 0) racine = racine.substring(deux + 1);
        if (COMMANDES_BLOQUEES.contains(racine)) {
            e.setCanceled(true);
            Annonces.chat(p, Component.translatable("matchs.commande_bloquee"));
        }
    }

    // ------------------------------------------------------------------ bornes
    /** Verifie les bornes des joueurs vivants de chaque partie (appele toutes les 5 ticks). */
    public static void bornes(MinecraftServer s) {
        int tick = s.getTickCount();
        for (Partie partie : MatchApi.parties()) {
            Instance inst = partie.instance();
            if (inst == null || !inst.prete()) continue;
            Partie.Phase ph = partie.phase();
            if (ph == Partie.Phase.ATTENTE || ph == Partie.Phase.FIN || ph == Partie.Phase.TERMINEE) continue;
            List<ServerPlayer> joueurs = partie.joueursEnLigne();
            for (int i = 0; i < joueurs.size(); i++) {
                ServerPlayer p = joueurs.get(i);
                if (p.level() != inst.niveau() || partie.spectateurs().contains(p.getUUID()) || Spectateurs.estSpectateur(p.getUUID())) continue;
                if (inst.contient(p.getX(), p.getY() + 0.1, p.getZ())) continue;
                Integer d = DERNIERE_SORTIE.get(p.getUUID());
                if (d != null && tick - d < 20) continue;
                DERNIERE_SORTIE.put(p.getUUID(), tick);
                try {
                    partie.surSortieBornes(p);
                } catch (RuntimeException ex) {
                    HeraliszMatchs.LOG.error("[matchs] surSortieBornes de {} en erreur", partie.id(), ex);
                }
            }
        }
    }

    public static void oublier(UUID u) {
        DERNIERE_SORTIE.remove(u);
    }

    public static void vider() {
        DERNIERE_SORTIE.clear();
    }
}
