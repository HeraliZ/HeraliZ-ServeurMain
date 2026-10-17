package fr.heraliz.crac.bases;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CProteges;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Protections des nouveaux joueurs.
 * <ul>
 *   <li><b>Joueur</b> (étape PHASE du tutoriel, dans le monde des bases seulement) : personne ne peut le blesser (ni joueur
 *   ni créature) et il ne peut blesser aucun joueur ; son modèle est entouré de vert chez les autres
 *   ({@link S2CProteges}, rendu client {@code client.ContourProtege}). Elle finit quand sa base est posée (Claim Block
 *   sur au moins {@code [tuto] fondations_min} fondations), au bout de {@code [tuto] phase_max_min} minutes de jeu, ou
 *   quand il va sur la carte (tout monde autre que le monde des bases et le spawn).</li>
 *   <li><b>Base</b> (posée par un joueur qui a fait le tutoriel, une seule fois) : {@code [protection] jours} jours sans
 *   raid : aucun dégât aux pièces et objets posés de son claim (C4, charges, grenades), aucun bloc cassé par un explosif,
 *   rien posé par un autre (échelles, trappes de pillards) ; pas d'usure non plus ({@code pause_usure}). Elle tombe si le
 *   groupe dépasse {@code membres_max} membres. Texte flottant au-dessus de la base, visible de tous.</li>
 * </ul>
 * Staff : {@code /protection voir|retirer|donner <joueur> [jours]}.
 */
@EventBusSubscriber(modid = "crac")
public final class Protections {
    /** Donnée persistante (ForgeData) des textes flottants : l'identifiant du groupe protégé. */
    static final String MARQUE = "crac_protection";

    /** Joueurs protégés en ce moment (étape PHASE et dans le monde des bases), refait chaque seconde. */
    private static Set<UUID> actifs = Set.of();
    private static int compteur;

    private Protections() {
    }

    // ---------------------------------------------------------------------------------------------- lectures

    static boolean joueurProtege(Entity e) {
        return e instanceof ServerPlayer && actifs.contains(e.getUUID());
    }

    /** La base de ce groupe est-elle protégée en ce moment ? */
    public static boolean groupeProtege(@Nullable MinecraftServer server, @Nullable UUID groupe) {
        if (server == null || groupe == null) {
            return false;
        }
        long fin = BasesDonnees.get(server).finProtection(groupe);
        if (fin <= System.currentTimeMillis()) {
            return false;
        }
        Group g = GroupData.get(server).getById(groupe);
        return g == null || g.size() <= BasesReglages.i(BasesReglages.MEMBRES_MAX, 2);
    }

    /** Ce bloc est-il dans la zone d'une base protégée ? (explosifs : RaidBlocks, PlacableHpManager, poses) */
    public static boolean baseProtegee(ServerLevel level, BlockPos pos) {
        if (BasesDonnees.get(level.getServer()).protections().isEmpty()) {
            return false;
        }
        Claim c = ClaimManager.getAt(level, pos);
        return c != null && groupeProtege(level.getServer(), c.groupId());
    }

    /**
     * Une pièce de construction ne perd pas de vie (StructureService.damage) : base protégée, sauf démolition par son
     * groupe et commandes du staff ; l'usure seulement avec {@code pause_usure}.
     */
    public static boolean pieceProtegee(ServerLevel level, Piece p, StructureService.Cause cause) {
        if (cause == StructureService.Cause.ADMIN || cause == StructureService.Cause.DEMOLISH
            || BasesDonnees.get(level.getServer()).protections().isEmpty()) {
            return false;
        }
        if (cause == StructureService.Cause.DECAY && !BasesReglages.b(BasesReglages.PAUSE_USURE, true)) {
            return false;
        }
        Building b = StructureService.buildingOf(level, p);
        Claim c = StructureService.claimOf(level, b);
        return c != null && groupeProtege(level.getServer(), c.groupId());
    }

    // ---------------------------------------------------------------------------------------------- combats

    /** Le protégé ne prend aucun coup d'un autre (joueur, créature, projectile) et ne blesse aucun joueur. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attaque(LivingAttackEvent e) {
        if (e.getEntity().level().isClientSide()) {
            return;
        }
        Entity auteur = e.getSource().getEntity();
        if (auteur == null || auteur == e.getEntity()) {
            return;
        }
        if (joueurProtege(e.getEntity())) {
            e.setCanceled(true);
            return;
        }
        if (joueurProtege(auteur) && e.getEntity() instanceof Player) {
            e.setCanceled(true);
            if (auteur instanceof ServerPlayer sp) {
                T.barre(sp, T.t("crac.protection.pas_de_combat", "Protégé : tu ne peux pas blesser les autres joueurs."), ChatFormatting.GREEN);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- suivi

    /** Toutes les secondes (MondeBasesServeur). */
    static void seconde(MinecraftServer server) {
        BasesDonnees d = BasesDonnees.get(server);
        long phaseMax = BasesReglages.i(BasesReglages.PHASE_MAX_MIN, 120) * 1200L;
        compteur++;
        Set<UUID> nouveaux = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BasesDonnees.Joueur j = d.siConnu(p.getUUID());
            if (j == null) {
                continue;
            }
            if (j.etape == BasesDonnees.Etape.PHASE) {
                boolean dansBases = MondeBases.estBases(p.level());
                if (!dansBases && !MondeBases.estSpawn(p.level())) {
                    finirPhase(d, j);
                    T.chat(p, T.t("crac.protection.perdue_carte", "Tu as quitté le monde des bases : ta protection de nouveau joueur est terminée."),
                        ChatFormatting.YELLOW);
                } else if (basePosee(p)) {
                    finirPhase(d, j);
                } else {
                    if (dansBases) {
                        j.phaseTicks += 20;
                        d.marquer();
                    }
                    if (j.phaseTicks >= phaseMax) {
                        finirPhase(d, j);
                        T.chat(p, T.t("crac.protection.temps_ecoule", "Ta protection de nouveau joueur est terminée (temps écoulé). Pose vite ta base !"),
                            ChatFormatting.YELLOW);
                    } else if (dansBases) {
                        nouveaux.add(p.getUUID());
                        if (compteur % 30 == 0) {
                            T.barre(p, T.t("crac.protection.barre", "Protégé : base non posée · %s restantes",
                                T.duree((phaseMax - j.phaseTicks) * 50L)), ChatFormatting.GREEN);
                        }
                    }
                }
            }
            if (j.protectionDue && basePosee(p)) {
                accorder(p, d, j);
            }
        }
        if (!nouveaux.equals(actifs)) {
            actifs = Set.copyOf(nouveaux);
            NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new S2CProteges(actifs));
        }
        if (compteur % 60 == 1) {
            minute(server, d);
        }
    }

    private static void finirPhase(BasesDonnees d, BasesDonnees.Joueur j) {
        j.etape = BasesDonnees.Etape.FINI;
        d.marquer();
    }

    /** Le Claim Block du groupe du joueur est posé dans le monde des bases, sur assez de fondations. */
    static boolean basePosee(ServerPlayer p) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        Claim c = Deplacements.baseDe(p);
        if (bases == null || c == null) {
            return false;
        }
        Building b = StructureService.buildingOfClaim(bases, c.anchor());
        if (b == null) {
            return false;
        }
        int n = 0;
        for (Piece piece : StructureService.store(bases).piecesOf(b)) {
            if (piece.kind() == PieceKind.FOUNDATION || piece.kind() == PieceKind.TRI_FOUNDATION) {
                n++;
            }
        }
        return n >= BasesReglages.i(BasesReglages.FONDATIONS_MIN, 2);
    }

    private static void accorder(ServerPlayer p, BasesDonnees d, BasesDonnees.Joueur j) {
        j.protectionDue = false;
        d.marquer();
        Group g = GroupData.get(p.server).getByMember(p.getUUID());
        double jours = BasesReglages.d(BasesReglages.PROTECTION_JOURS, 4.0);
        if (g == null || jours <= 0) {
            return;
        }
        if (g.size() > BasesReglages.i(BasesReglages.MEMBRES_MAX, 2)) {
            T.chat(p, T.t("crac.protection.groupe_trop_grand", "Ta base n'est pas protégée : ton groupe a trop de membres."), ChatFormatting.YELLOW);
            return;
        }
        long fin = System.currentTimeMillis() + (long) (jours * 86_400_000L);
        d.proteger(g.id(), Math.max(fin, d.finProtection(g.id())));
        String nb = jours == Math.rint(jours) ? String.valueOf((long) jours) : String.valueOf(jours);
        // plus de grand titre (1.7.0) : pendant le tutoriel, son étape « Ta base est à toi » le dit déjà
        if (!Tutoriel.enCours(p.getUUID())) {
            T.chat(p, T.t("crac.protection.accordee", "Ta base est protégée %s jours : personne ne peut la raider. Le monde des bases reste en PvP.",
                nb), ChatFormatting.GREEN);
        }
        Crac.LOGGER.info("[CRac] base du groupe {} ({}) protégée {} jours", g.name(), p.getGameProfile().getName(), nb);
        majTextes(p.server);
    }

    /** Une fois par minute : protections finies retirées (membres prévenus), textes flottants mis à jour. */
    private static void minute(MinecraftServer server, BasesDonnees d) {
        long now = System.currentTimeMillis();
        List<UUID> finies = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : d.protections().entrySet()) {
            if (e.getValue() <= now) {
                finies.add(e.getKey());
            }
        }
        for (UUID g : finies) {
            d.retirerProtection(g);
            Group grp = GroupData.get(server).getById(g);
            if (grp != null) {
                for (UUID m : grp.members()) {
                    ServerPlayer sp = server.getPlayerList().getPlayer(m);
                    if (sp != null) {
                        T.chat(sp, T.t("crac.protection.finie", "La protection de ta base est terminée : elle peut être raidée."), ChatFormatting.GOLD);
                    }
                }
            }
        }
        majTextes(server);
    }

    // ---------------------------------------------------------------------------------------------- texte flottant

    /** Texte « Base protégée — nouveau joueur · 3 j 12 h » au-dessus de chaque base protégée dont le tronçon est chargé. */
    static void majTextes(MinecraftServer server) {
        ServerLevel bases = server.getLevel(MondeBases.BASES);
        if (bases == null) {
            return;
        }
        boolean panneau = BasesReglages.b(BasesReglages.PANNEAU, true);
        long now = System.currentTimeMillis();
        for (Claim c : new ArrayList<>(ClaimData.get(bases).all())) {
            BlockPos a = c.anchor();
            if (!bases.isLoaded(a)) {
                continue;
            }
            List<Display.TextDisplay> ex = bases.getEntitiesOfClass(Display.TextDisplay.class, new AABB(a).inflate(4, 64, 4),
                e -> e.getPersistentData().contains(MARQUE));
            boolean actif = panneau && groupeProtege(server, c.groupId());
            if (!actif) {
                ex.forEach(Entity::discard);
                continue;
            }
            double y = haut(bases, c) + 1.6;
            MutableComponent texte = Component.empty()
                .append(T.t("crac.protection.panneau", "Base protégée — nouveau joueur").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                .append("\n")
                .append(T.duree(BasesDonnees.get(server).finProtection(c.groupId()) - now).withStyle(ChatFormatting.WHITE));
            Display.TextDisplay d = ex.isEmpty() ? null : ex.get(0);
            for (int i = 1; i < ex.size(); i++) {
                ex.get(i).discard();
            }
            if (d == null) {
                d = EntityType.TEXT_DISPLAY.create(bases);
                if (d == null) {
                    continue;
                }
                d.moveTo(a.getX() + 0.5, y, a.getZ() + 0.5, 0f, 0f);
                ecrire(d, texte);
                d.getPersistentData().putString(MARQUE, c.groupId().toString());
                bases.addFreshEntity(d);
            } else {
                d.setPos(a.getX() + 0.5, y, a.getZ() + 0.5);
                ecrire(d, texte);
            }
        }
    }

    /** Texte, face au joueur, fond sombre léger, ombre, jamais à travers les murs (comme /data merge). */
    private static void ecrire(Display.TextDisplay d, Component texte) {
        try {
            CompoundTag t = d.saveWithoutId(new CompoundTag());
            t.putString("text", Component.Serializer.toJson(texte));
            t.putString("billboard", "center");
            t.putInt("background", 0x60000000);
            t.putBoolean("shadow", true);
            t.putBoolean("see_through", false);
            t.putInt("line_width", 260);
            d.load(t);
        } catch (RuntimeException ex) {
            Crac.LOGGER.debug("[CRac] texte de base protégée : {}", ex.toString());
        }
    }

    /** Point le plus haut de la base (pièces de son bâtiment), au moins le Claim Block. */
    private static int haut(ServerLevel level, Claim c) {
        int y = c.anchor().getY();
        Building b = StructureService.buildingOfClaim(level, c.anchor());
        if (b != null) {
            for (Piece p : StructureService.store(level).piecesOf(b)) {
                for (BlockPos q : PieceIndex.blocksOf(p)) {
                    y = Math.max(y, q.getY());
                }
            }
        }
        return y;
    }

    /** Un texte flottant rechargé d'un tronçon alors que sa protection est finie : retiré. */
    @SubscribeEvent
    public static void arrivee(EntityJoinLevelEvent e) {
        if (!(e.getEntity() instanceof Display.TextDisplay d) || !(e.getLevel() instanceof ServerLevel sl)
            || !d.getPersistentData().contains(MARQUE)) {
            return;
        }
        UUID g;
        try {
            g = UUID.fromString(d.getPersistentData().getString(MARQUE));
        } catch (IllegalArgumentException ex) {
            g = null;
        }
        if (!BasesReglages.b(BasesReglages.PANNEAU, true) || !groupeProtege(sl.getServer(), g)) {
            e.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------------------------------------- connexion

    @SubscribeEvent
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new S2CProteges(actifs));
        }
    }

    // ---------------------------------------------------------------------------------------------- commandes

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("protection").requires(s -> s.hasPermission(2))
            .then(Commands.literal("voir").then(Commands.argument("joueur", EntityArgument.player())
                .executes(c -> voir(c.getSource(), EntityArgument.getPlayer(c, "joueur")))))
            .then(Commands.literal("retirer").then(Commands.argument("joueur", EntityArgument.player())
                .executes(c -> retirer(c.getSource(), EntityArgument.getPlayer(c, "joueur")))))
            .then(Commands.literal("donner").then(Commands.argument("joueur", EntityArgument.player())
                .executes(c -> donner(c.getSource(), EntityArgument.getPlayer(c, "joueur"), BasesReglages.d(BasesReglages.PROTECTION_JOURS, 4.0)))
                .then(Commands.argument("jours", DoubleArgumentType.doubleArg(0.01, 365))
                    .executes(c -> donner(c.getSource(), EntityArgument.getPlayer(c, "joueur"), DoubleArgumentType.getDouble(c, "jours")))))));
    }

    private static int voir(CommandSourceStack src, ServerPlayer p) {
        BasesDonnees d = BasesDonnees.get(src.getServer());
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        long phaseMax = BasesReglages.i(BasesReglages.PHASE_MAX_MIN, 120) * 1200L;
        Group g = GroupData.get(src.getServer()).getByMember(p.getUUID());
        long fin = g == null ? 0 : d.finProtection(g.id());
        long reste = fin - System.currentTimeMillis();
        fr.heraliz.crac.heraliz.Reply.ok(src, T.t("crac.protection.voir",
            "%s : tuto %s, protection joueur %s (reste %s), base %s, protection de base %s.",
            p.getGameProfile().getName(), j.etape.name(), actifs.contains(p.getUUID()) ? T.t("crac.oui", "oui") : T.t("crac.non", "non"),
            T.duree(Math.max(0, phaseMax - j.phaseTicks) * 50L), basePosee(p) ? T.t("crac.protection.posee", "posée") : T.t("crac.protection.non_posee", "non posée"),
            reste > 0 && g != null ? T.duree(reste) : Component.literal("-")));
        return 1;
    }

    private static int retirer(CommandSourceStack src, ServerPlayer p) {
        BasesDonnees d = BasesDonnees.get(src.getServer());
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        if (j.etape == BasesDonnees.Etape.PHASE) {
            j.etape = BasesDonnees.Etape.FINI;
        }
        j.protectionDue = false;
        Group g = GroupData.get(src.getServer()).getByMember(p.getUUID());
        if (g != null) {
            d.retirerProtection(g.id());
        }
        d.marquer();
        majTextes(src.getServer());
        fr.heraliz.crac.heraliz.Reply.ok(src, T.t("crac.protection.retiree", "Protections de %s retirées (joueur et base).",
            p.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int donner(CommandSourceStack src, ServerPlayer p, double jours) {
        Group g = GroupData.get(src.getServer()).getByMember(p.getUUID());
        if (g == null) {
            fr.heraliz.crac.heraliz.Reply.fail(src, T.t("crac.protection.sans_groupe", "%s n'a pas de groupe (donc pas de base).",
                p.getGameProfile().getName()));
            return 0;
        }
        BasesDonnees.get(src.getServer()).proteger(g.id(), System.currentTimeMillis() + (long) (jours * 86_400_000L));
        majTextes(src.getServer());
        fr.heraliz.crac.heraliz.Reply.ok(src, T.t("crac.protection.donnee", "Base du groupe %s protégée %s.", g.name(),
            T.duree((long) (jours * 86_400_000L))).withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
