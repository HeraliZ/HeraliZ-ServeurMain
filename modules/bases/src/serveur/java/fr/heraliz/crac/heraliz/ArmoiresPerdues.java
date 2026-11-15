package fr.heraliz.crac.heraliz;

import com.mojang.brigadier.CommandDispatcher;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.data.SafeSavedData;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.raid.RaidBlockManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * HeraliZ (15/11, demande du propriétaire) : armoire d'un clan détruite pendant un raid. Pendant {@link #DUREE_MS} (24 h),
 * la base reste à son clan :
 * <ul>
 * <li>lui seul peut y reposer une armoire ({@code ClaimBlockHandler.placementProblem}, même écart qu'entre deux bases) :
 * il la retrouve telle qu'elle était, à la taille que l'arbre du clan a payée, sans rien repayer (audit H2), et son home
 * revient ;</li>
 * <li>lui seul y construit et touche à ses pièces au marteau ({@code StructureService.privilegeProblem},
 * {@code hasPrivilege}) ;</li>
 * <li>elle ne s'use pas en attendant ({@code ClaimMaintenanceManager}).</li>
 * </ul>
 * Elle reste raidable comme toutes les bases. Le clan peut l'abandonner avant ({@code /base abandonner}, ou le bouton du
 * message ; chef ou droit « Gérer la base ») ; au bout de 24 h sans armoire, elle est libre : n'importe qui peut y poser
 * la sienne (règles d'avant). « Pendant un raid » : l'armoire part alors que la base est en raid block (explosions dans sa
 * zone ; son clan ne peut alors ni la casser, ni la déplacer, ni se dissoudre). Gardé avec le monde principal
 * ({@code data/crac_armoires_perdues.dat}) : un redémarrage n'efface rien. Fil du serveur seulement.
 */
@EventBusSubscriber(modid = "crac")
public final class ArmoiresPerdues extends SafeSavedData {
    public static final String ID = "crac_armoires_perdues";
    /** Temps pendant lequel la base reste à son clan (heure réelle : un arrêt du serveur compte). */
    static final long DUREE_MS = 24L * 3_600_000L;
    private static final long CONFIRMATION_MS = 30_000L;

    /** Une base réservée : son groupe, son monde, son ancien claim (zone, palier, home) et la fin de la réservation (ms). */
    record Reservation(UUID groupe, ResourceKey<Level> monde, Claim ancien, long fin) {
        boolean active(long now) {
            return now < this.fin;
        }
    }

    private final Map<UUID, Reservation> parGroupe = new HashMap<>();
    /** /base abandonner en attente de confirmation : joueur -> fin du délai (ms). */
    private static final Map<UUID, Long> A_CONFIRMER = new HashMap<>();

    public static ArmoiresPerdues get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ArmoiresPerdues::load,
            () -> SafeSavedData.fresh(server.overworld(), ID, new ArmoiresPerdues()), ID);
    }

    // ---------------------------------------------------------------------------------------------- lecture
    /** La réservation encore en cours du groupe {@code groupe} dans le monde {@code level}, ou null. */
    @Nullable
    static Reservation active(ServerLevel level, @Nullable UUID groupe) {
        if (groupe == null) {
            return null;
        }
        ArmoiresPerdues d = get(level.getServer());
        if (d.parGroupe.isEmpty()) {
            return null;
        }
        Reservation r = d.parGroupe.get(groupe);
        return r != null && r.monde() == level.dimension() && r.active(System.currentTimeMillis()) ? r : null;
    }

    /**
     * Le groupe à qui reste ce bâtiment sans armoire (réservation en cours, dans ce monde), ou null : seuls ses membres y
     * touchent au marteau, et il ne s'use pas.
     */
    @Nullable
    public static UUID groupeReserve(ServerLevel level, @Nullable Building b) {
        return b == null || active(level, b.ownerGroup) == null ? null : b.ownerGroup;
    }

    /** Le bloc est dans la zone d'une base réservée de ce monde (ses objets posés ne s'usent pas). */
    public static boolean dansZoneReservee(ServerLevel level, BlockPos pos) {
        ArmoiresPerdues d = get(level.getServer());
        if (d.parGroupe.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (Reservation r : d.parGroupe.values()) {
            if (r.monde() == level.dimension() && r.active(now) && r.ancien().contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pourquoi le groupe {@code groupe} ne peut pas poser d'armoire en {@code pos} (message encodé de {@link Txt#k}), ou null :
     * une base réservée d'un AUTRE groupe est trop près (même écart qu'entre deux bases, {@link ClaimManager#espacementMin}).
     */
    @Nullable
    public static String refusArmoire(ServerLevel level, BlockPos pos, @Nullable UUID groupe) {
        ArmoiresPerdues d = get(level.getServer());
        if (d.parGroupe.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        int ecart = ClaimManager.espacementMin();
        for (Reservation r : d.parGroupe.values()) {
            if (r.groupe().equals(groupe) || r.monde() != level.dimension() || !r.active(now)) {
                continue;
            }
            BlockPos a = r.ancien().anchor();
            if (Math.max(Math.abs(a.getX() - pos.getX()), Math.abs(a.getZ() - pos.getZ())) < ecart) {
                return Txt.k("crac.armoire.base_reservee", heures(r.fin() - now), minutes(r.fin() - now));
            }
        }
        return null;
    }

    /**
     * Pourquoi ce joueur ne peut pas construire sur ces blocs (message encodé), ou null : ils sont dans la zone d'une base
     * réservée dont il n'est pas un membre qui construit (droit « Construire »).
     */
    @Nullable
    public static String refusConstruction(ServerLevel level, Player player, List<BlockPos> blocs) {
        ArmoiresPerdues d = get(level.getServer());
        if (d.parGroupe.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (Reservation r : d.parGroupe.values()) {
            if (r.monde() != level.dimension() || !r.active(now)) {
                continue;
            }
            for (BlockPos b : blocs) {
                if (r.ancien().contains(b)) {
                    if (StructureService.isMember(level, player, r.groupe(), ClanAccess.Action.BUILD)) {
                        break;
                    }
                    return Txt.k("crac.armoire.zone_reservee", heures(r.fin() - now), minutes(r.fin() - now));
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------- vie
    /**
     * L'armoire du claim {@code ancien} vient de partir ({@code ClaimBlockLogique.retire}, claim déjà retiré) : base d'un
     * clan en raid block, hors du tutoriel : réservée à son clan pendant {@link #DUREE_MS}, et son clan est prévenu.
     */
    public static void armoireRetiree(ServerLevel level, Claim ancien) {
        MinecraftServer server = level.getServer();
        UUID g = ancien.groupId();
        if (fr.heraliz.crac.bases.MondeBases.estTuto(level) || !BasesClans.estGroupeClan(server, g)
            || !RaidBlockManager.isRaidBlocked(level, g)) {
            return;
        }
        long fin = System.currentTimeMillis() + DUREE_MS;
        ArmoiresPerdues d = get(server);
        d.parGroupe.put(g, new Reservation(g, level.dimension(), ancien, fin));
        d.setDirty();
        UUID clan = BasesClans.clanDuGroupe(server, g);
        BlockPos a = ancien.anchor();
        Crac.LOGGER.info("[CRac] armoire du clan [{}] détruite pendant un raid en {} {} {} ({}) : base réservée au clan pendant 24 h",
            clan == null ? "?" : PriseCoeur.tag(clan), a.getX(), a.getY(), a.getZ(), level.dimension().location());
        JournalClan.clan(clan, "crac.journal.base_reservee");
        if (clan != null) {
            for (UUID m : PriseCoeur.membres(clan)) {
                ServerPlayer p = server.getPlayerList().getPlayer(m);
                if (p != null) {
                    p.sendSystemMessage(t("crac.armoire.reservee",
                        "⚠ L'armoire de votre base a été détruite pendant un raid. Pendant 24 h, seul votre clan peut en reposer une sur la base : vous la retrouverez telle quelle, à la même taille, sans rien repayer. Ensuite, n'importe qui pourra la prendre.")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    boutonAbandon(p);
                }
            }
        }
    }

    /**
     * Une armoire vient d'être posée en {@code pos} par le groupe {@code groupe} (claim créé) : sa base réservée, s'il en
     * avait une, est reprise (même base : le home revient) ou laissée (armoire posée ailleurs : une base par clan). Vrai si
     * le home de la base a été rendu.
     */
    public static boolean armoirePosee(ServerLevel level, ServerPlayer poseur, UUID groupe, BlockPos pos) {
        MinecraftServer server = level.getServer();
        ArmoiresPerdues d = get(server);
        Reservation r = d.parGroupe.remove(groupe);
        if (r == null) {
            return false;
        }
        d.setDirty();
        UUID clan = BasesClans.clanDuGroupe(server, groupe);
        boolean memeBase = r.monde() == level.dimension() && r.ancien().contains(pos);
        Crac.LOGGER.info("[CRac] clan [{}] : armoire reposée par {} {}", clan == null ? "?" : PriseCoeur.tag(clan),
            poseur.getGameProfile().getName(), memeBase ? "sur sa base réservée (récupérée)" : "ailleurs (base réservée laissée)");
        if (!memeBase || clan == null) {
            return false;
        }
        boolean home = false;
        Claim nouveau = ClaimManager.getByAnchor(level, pos);
        if (nouveau != null && r.ancien().home() != null && nouveau.home() == null) {
            nouveau.setHome(r.ancien().home(), r.ancien().homeLacet(), r.ancien().homeNom());
            fr.heraliz.crac.claim.ClaimData.get(level).markDirty();
            home = true;
        }
        PriseCoeur.prevenir(server, clan, t("crac.armoire.recuperee", "%s a reposé l'armoire : votre base est récupérée.",
            poseur.getGameProfile().getName()).withStyle(ChatFormatting.GREEN));
        return home;
    }

    /** Clan dissous, base effacée par le staff : plus de réservation pour ce groupe. */
    public static void oublier(MinecraftServer server, UUID groupe) {
        ArmoiresPerdues d = get(server);
        if (d.parGroupe.remove(groupe) != null) {
            d.setDirty();
        }
    }

    /** Remise à zéro du serveur : plus aucune réservation. */
    public static void toutOublier(MinecraftServer server) {
        ArmoiresPerdues d = get(server);
        if (!d.parGroupe.isEmpty()) {
            d.parGroupe.clear();
            d.setDirty();
        }
        A_CONFIRMER.clear();
    }

    /** Toutes les 5 s (bases des clans) : réservations finies (clan prévenu) ou dont le groupe n'existe plus. */
    static void seconde(MinecraftServer server) {
        ArmoiresPerdues d = get(server);
        if (d.parGroupe.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        GroupData groupes = GroupData.get(server);
        for (Reservation r : new ArrayList<>(d.parGroupe.values())) {
            if (groupes.getById(r.groupe()) == null) {
                d.parGroupe.remove(r.groupe());
                d.setDirty();
                continue;
            }
            if (r.active(now)) {
                continue;
            }
            d.parGroupe.remove(r.groupe());
            d.setDirty();
            UUID clan = BasesClans.clanDuGroupe(server, r.groupe());
            Crac.LOGGER.info("[CRac] clan [{}] : base restée 24 h sans armoire, libre pour tous", clan == null ? "?" : PriseCoeur.tag(clan));
            JournalClan.clan(clan, "crac.journal.base_liberee");
            if (clan != null) {
                PriseCoeur.prevenir(server, clan, t("crac.armoire.expiree",
                    "Votre base est restée 24 h sans armoire : n'importe qui peut maintenant y poser la sienne.").withStyle(ChatFormatting.RED));
            }
        }
    }

    /** Un membre d'un clan dont la base attend son armoire se connecte : il le sait (et peut l'abandonner s'il la gère). */
    @SubscribeEvent
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !BasesClans.actif()) {
            return;
        }
        MinecraftServer server = p.server;
        ArmoiresPerdues d = get(server);
        if (d.parGroupe.isEmpty()) {
            return;
        }
        UUID clan = PriseCoeur.clanDe(p.getUUID());
        UUID g = clan == null ? null : BasesClans.groupeDuClan(server, clan);
        Reservation r = g == null ? null : d.parGroupe.get(g);
        long now = System.currentTimeMillis();
        if (r == null || !r.active(now)) {
            return;
        }
        p.sendSystemMessage(t("crac.armoire.rappel",
            "Votre base n'a plus d'armoire depuis un raid : reposez-en une sur la base dans les %s, sinon n'importe qui pourra la prendre.",
            duree(r.fin() - now)).withStyle(ChatFormatting.GOLD));
        boutonAbandon(p);
    }

    @SubscribeEvent
    public static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        A_CONFIRMER.remove(e.getEntity().getUUID());
    }

    // ---------------------------------------------------------------------------------------------- /base abandonner
    @SubscribeEvent
    public static void commandes(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("base").then(Commands.literal("abandonner")
            .executes(c -> abandonner(c.getSource().getPlayerOrException(), false))
            .then(Commands.literal("confirmer").executes(c -> abandonner(c.getSource().getPlayerOrException(), true)))));
    }

    /** Bouton « Abandonner la base » sous un message, pour qui gère la base (chef, droit « Gérer la base »). */
    private static void boutonAbandon(ServerPlayer p) {
        if (!BasesClans.peutGerer(p.getUUID())) {
            return;
        }
        p.sendSystemMessage(t("crac.armoire.bouton_abandonner", "[Abandonner la base]").withStyle(Style.EMPTY
            .withColor(ChatFormatting.RED).withBold(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/base abandonner"))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/base abandonner")))));
    }

    private static int abandonner(ServerPlayer p, boolean confirme) {
        MinecraftServer server = p.server;
        UUID clan = PriseCoeur.clanDe(p.getUUID());
        UUID g = clan == null ? null : BasesClans.groupeDuClan(server, clan);
        ArmoiresPerdues d = get(server);
        Reservation r = g == null ? null : d.parGroupe.get(g);
        long now = System.currentTimeMillis();
        if (r == null || !r.active(now)) {
            return refus(p, t("crac.armoire.aucune_reservee", "Ton clan n'a aucune base en attente d'armoire."));
        }
        if (!BasesClans.peutGerer(p.getUUID())) {
            return refus(p, t("crac.armoire.abandon_droit", "Seuls le chef et les membres qui gèrent la base peuvent l'abandonner."));
        }
        Long limite = A_CONFIRMER.remove(p.getUUID());
        if (!confirme || limite == null || now > limite) {
            if (confirme) {
                return refus(p, t("crac.armoire.abandon_trop_tard", "Tape d'abord /base abandonner, puis confirme dans les 30 s."));
            }
            A_CONFIRMER.put(p.getUUID(), now + CONFIRMATION_MS);
            p.sendSystemMessage(t("crac.armoire.abandon_confirmer",
                "Abandonner la base ? N'importe qui pourra y poser son armoire tout de suite. Confirme dans les 30 s :")
                .withStyle(ChatFormatting.GOLD));
            p.sendSystemMessage(t("crac.armoire.bouton_confirmer", "[Confirmer l'abandon]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/base abandonner confirmer"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/base abandonner confirmer")))));
            return 1;
        }
        d.parGroupe.remove(g);
        d.setDirty();
        String nom = p.getGameProfile().getName();
        Crac.LOGGER.info("[CRac] clan [{}] : base sans armoire abandonnée par {}", PriseCoeur.tag(clan), nom);
        JournalClan.clan(clan, "crac.journal.base_abandonnee", nom);
        PriseCoeur.prevenir(server, clan, t("crac.armoire.abandonnee", "%s a abandonné la base : n'importe qui peut maintenant y poser son armoire.",
            nom).withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int refus(ServerPlayer p, MutableComponent c) {
        p.sendSystemMessage(c.withStyle(ChatFormatting.RED));
        return 0;
    }

    private static MutableComponent t(String cle, String fr, Object... args) {
        return Component.translatableWithFallback(cle, fr, args);
    }

    /** Durée lisible (mêmes textes que les bases : « 23 h 12 min », « 42 min », « 15 s »). */
    /**
     * Heures puis minutes d'un temps restant, pour un message encodé ({@link Txt#k} : texte seulement) : les unités sont
     * dans le texte traduit chez le joueur (avant : « 23 h 12 min » écrit par le serveur, même pour un joueur russe).
     */
    private static long heures(long ms) {
        return Math.max(0L, ms) / 3_600_000L;
    }

    private static long minutes(long ms) {
        return Math.max(0L, ms) % 3_600_000L / 60_000L;
    }

    static MutableComponent duree(long ms) {
        long s = Math.max(0L, ms / 1000L);
        long h = s / 3600L;
        long m = s % 3600L / 60L;
        if (h > 0) {
            return t("crac.duree.hm", "%s h %s min", String.valueOf(h), String.valueOf(m));
        }
        if (m > 0) {
            return t("crac.duree.m", "%s min", String.valueOf(m));
        }
        return t("crac.duree.s", "%s s", String.valueOf(s));
    }

    // ---------------------------------------------------------------------------------------------- fichier
    public static ArmoiresPerdues load(CompoundTag tag) {
        ArmoiresPerdues d = new ArmoiresPerdues();
        ListTag l = tag.getList("Reservations", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            try {
                ResourceLocation monde = ResourceLocation.tryParse(t.getString("Monde"));
                if (!t.hasUUID("Groupe") || monde == null || !t.contains("Claim", Tag.TAG_COMPOUND)) {
                    continue;
                }
                UUID g = t.getUUID("Groupe");
                d.parGroupe.put(g, new Reservation(g, ResourceKey.create(Registries.DIMENSION, monde), Claim.load(t.getCompound("Claim")),
                    t.getLong("Fin")));
            } catch (RuntimeException ex) {
                Crac.LOGGER.warn("[CRac] armoires perdues : réservation illisible ignorée : {}", ex.toString());
            }
        }
        return d;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag l = new ListTag();
        for (Reservation r : this.parGroupe.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Groupe", r.groupe());
            t.putString("Monde", r.monde().location().toString());
            t.put("Claim", r.ancien().save());
            t.putLong("Fin", r.fin());
            l.add(t);
        }
        tag.put("Reservations", l);
        return tag;
    }
}
