package fr.heraliz.crac.bases;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CCarrieres;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Carrières du monde des bases, côté serveur.
 * <ul>
 *   <li>Posées par le staff à l'endroit où il se tient : {@code /carriere creer <nom> <rayon> <type>} (cylindre de ce
 *   rayon, autant vers le haut et le bas), {@code liste}, {@code retirer <nom>}, {@code tp <nom>},
 *   {@code regenerer [nom]}.</li>
 *   <li>Un joueur en survie y casse les blocs naturels du type de la carrière ({@code #crac:carriere/<type>}) ; chaque bloc
 *   cassé est noté et repousse au bout de {@code [carrieres] repousse_min} minutes, jamais sous un joueur (ni une autre
 *   créature) ; si quelqu'un a mis autre chose à sa place entre-temps, la main l'emporte (il ne repousse pas).</li>
 *   <li>En survie, on ne pose rien dans une carrière (sinon on pourrait poser de la pierre, la casser et la voir
 *   repousser : copie de matière).</li>
 *   <li>Un Claim Block ne se pose pas si sa zone toucherait une carrière.</li>
 * </ul>
 * Les blocs de la carrière ne sont jamais dessinés ici : le staff la bâtit (WorldEdit, à la main) puis la déclare ; ce qui
 * repousse est ce qui était là quand le bloc a été cassé.
 */
@EventBusSubscriber(modid = "crac")
public final class CarrieresServeur {
    /** Blocs repoussés au plus par seconde. */
    private static final int REPOUSSES_PAR_SECONDE = 400;

    private CarrieresServeur() {
    }

    /** Branché par la partie serveur : la décision de {@link Carrieres#permet} au serveur. */
    public static void brancher() {
        Carrieres.brancherServeur((level, pos, state) -> level instanceof ServerLevel sl && permet(sl, pos, state));
    }

    static boolean permet(ServerLevel level, BlockPos pos, BlockState state) {
        if (!MondeBases.estBases(level)) {
            return false;
        }
        for (Carrieres.Zone z : CarrieresDonnees.get(level).zones()) {
            if (z.permet(pos, state)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------- casse et pose

    /** Après toutes les règles (rien n'a annulé) : le bloc cassé dans une carrière est noté pour repousser. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void casse(BlockEvent.BreakEvent e) {
        if (e.isCanceled() || !(e.getLevel() instanceof ServerLevel sl) || !MondeBases.estBases(sl)) {
            return;
        }
        BlockState s = e.getState();
        if (!permet(sl, e.getPos(), s)) {
            return;
        }
        long delai = BasesReglages.i(BasesReglages.REPOUSSE_MIN, 10) * 60_000L;
        CarrieresDonnees.get(sl).noter(e.getPos(), s, System.currentTimeMillis() + delai);
    }

    /** En survie, rien ne se pose dans une carrière. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void pose(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel sl) || !MondeBases.estBases(sl)
            || p.isCreative() || p.isSpectator() || p.hasPermissions(2)) {
            return;
        }
        if (CarrieresDonnees.get(sl).zoneA(e.getPos()) != null) {
            e.setCanceled(true);
            T.barre(p, T.t("crac.carriere.pose_interdite", "On ne pose rien dans une carrière."), ChatFormatting.RED);
        }
    }

    /**
     * Refus d'un Claim Block dont la zone (rayon {@code rayon}) toucherait une carrière, ou null. Appelé par
     * {@code ClaimBlockHandler.placementProblem}.
     */
    @Nullable
    public static String probleme(ServerLevel level, BlockPos claim, int rayon) {
        if (!MondeBases.estBases(level)) {
            return null;
        }
        for (Carrieres.Zone z : CarrieresDonnees.get(level).zones()) {
            double dx = claim.getX() - z.centre().getX();
            double dz = claim.getZ() - z.centre().getZ();
            // carré du claim contre cercle de la carrière : distance du centre de la carrière au carré
            double ex = Math.max(0, Math.abs(dx) - rayon);
            double ez = Math.max(0, Math.abs(dz) - rayon);
            if (ex * ex + ez * ez <= (double) z.rayon() * z.rayon()) {
                return fr.heraliz.crac.heraliz.Txt.k("crac.carriere.claim_trop_pres", z.nom());
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------- repousse

    /** Toutes les secondes (MondeBasesServeur) : les blocs dus repoussent s'ils le peuvent. */
    static void seconde(MinecraftServer server) {
        ServerLevel level = server.getLevel(MondeBases.BASES);
        if (level == null) {
            return;
        }
        CarrieresDonnees d = CarrieresDonnees.get(level);
        long now = System.currentTimeMillis();
        int vus = 0;
        int max = Math.min(d.enAttente(), REPOUSSES_PAR_SECONDE);
        while (vus < max) {
            CarrieresDonnees.Repousse r = d.premier();
            if (r == null || r.quand() > now) {
                break;
            }
            vus++;
            if (!level.isLoaded(r.pos())) {
                // tronçon pas chargé : on attend qu'il le soit (jamais de chargement pour ça)
                d.repousserPremier(now + 30_000L);
                continue;
            }
            BlockState ici = level.getBlockState(r.pos());
            if (!ici.isAir() && !ici.canBeReplaced()) {
                d.oublierPremier();
                continue;
            }
            if (occupe(level, r.pos())) {
                d.repousserPremier(now + 30_000L);
                continue;
            }
            level.setBlock(r.pos(), r.etat(), Block.UPDATE_ALL);
            d.oublierPremier();
        }
    }

    private static boolean occupe(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(0.05), LivingEntity::isAlive).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------- envoi aux joueurs

    static S2CCarrieres paquet(MinecraftServer server) {
        ServerLevel level = server.getLevel(MondeBases.BASES);
        return new S2CCarrieres(level == null ? List.of() : new ArrayList<>(CarrieresDonnees.get(level).zones()));
    }

    static void envoyer(ServerPlayer p) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), paquet(p.server));
    }

    static void envoyerATous(MinecraftServer server) {
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), paquet(server));
    }

    // ---------------------------------------------------------------------------------------------- commandes

    private static final SuggestionProvider<CommandSourceStack> NOMS = (ctx, b) -> {
        ServerLevel l = ctx.getSource().getServer().getLevel(MondeBases.BASES);
        List<String> noms = new ArrayList<>();
        if (l != null) {
            CarrieresDonnees.get(l).zones().forEach(z -> noms.add(z.nom()));
        }
        return SharedSuggestionProvider.suggest(noms, b);
    };

    private static final SuggestionProvider<CommandSourceStack> TYPES = (ctx, b) ->
        SharedSuggestionProvider.suggest(Arrays.stream(CarriereType.values()).map(t -> t.id), b);

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("carriere").requires(s -> s.hasPermission(2))
            .then(Commands.literal("creer")
                .then(Commands.argument("nom", StringArgumentType.word())
                    .then(Commands.argument("rayon", IntegerArgumentType.integer(2, 256))
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                            .executes(c -> creer(c.getSource(), StringArgumentType.getString(c, "nom"),
                                IntegerArgumentType.getInteger(c, "rayon"), StringArgumentType.getString(c, "type")))))))
            .then(Commands.literal("liste").executes(c -> liste(c.getSource())))
            .then(Commands.literal("retirer")
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(NOMS)
                    .executes(c -> retirer(c.getSource(), StringArgumentType.getString(c, "nom")))))
            .then(Commands.literal("tp")
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(NOMS)
                    .executes(c -> tp(c.getSource(), StringArgumentType.getString(c, "nom")))))
            .then(Commands.literal("regenerer")
                .executes(c -> regenerer(c.getSource(), null))
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(NOMS)
                    .executes(c -> regenerer(c.getSource(), StringArgumentType.getString(c, "nom"))))));
    }

    private static void ok(CommandSourceStack src, Component c) {
        fr.heraliz.crac.heraliz.Reply.ok(src, c);
    }

    private static void ko(CommandSourceStack src, Component c) {
        fr.heraliz.crac.heraliz.Reply.fail(src, c);
    }

    @Nullable
    private static ServerLevel monde(CommandSourceStack src) {
        ServerLevel l = src.getServer().getLevel(MondeBases.BASES);
        if (l == null) {
            ko(src, T.t("crac.bases.absent", "La carte n'est pas chargée sur ce serveur : préviens le staff."));
        }
        return l;
    }

    private static int creer(CommandSourceStack src, String nom, int rayon, String typeId) {
        CarriereType type = CarriereType.parId(typeId);
        if (type == null) {
            ko(src, T.t("crac.carriere.type_inconnu", "Type inconnu : %s (pierre, minerais, fer, bois, sable, argile, terre).", typeId));
            return 0;
        }
        if (!MondeBases.estBases(src.getLevel())) {
            ko(src, T.t("crac.carriere.hors_monde", "Une carrière se pose sur la carte, à l'endroit où tu te tiens."));
            return 0;
        }
        int max = BasesReglages.i(BasesReglages.RAYON_MAX, 64);
        if (rayon > max) {
            ko(src, T.t("crac.carriere.trop_grande", "Rayon au plus %s (réglage [carrieres] rayon_max).", String.valueOf(max)));
            return 0;
        }
        if (nom.length() > 32) {
            ko(src, T.t("crac.carriere.nom_long", "Nom trop long (32 caractères au plus)."));
            return 0;
        }
        ServerLevel l = src.getLevel();
        CarrieresDonnees d = CarrieresDonnees.get(l);
        boolean remplace = d.zone(nom) != null;
        BlockPos centre = BlockPos.containing(src.getPosition());
        d.ajouter(new Carrieres.Zone(nom, centre, rayon, type));
        envoyerATous(src.getServer());
        Crac.LOGGER.info("[CRac] carrière {} ({}, rayon {}) en {} par {}", nom, type.id, rayon, centre.toShortString(), src.getTextName());
        ok(src, T.t(remplace ? "crac.carriere.remplacee" : "crac.carriere.creee", remplace
                ? "Carrière %s remplacée : %s, rayon %s, centre %s." : "Carrière %s créée : %s, rayon %s, centre %s.",
            nom, type.id, String.valueOf(rayon), centre.toShortString()).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int liste(CommandSourceStack src) {
        ServerLevel l = monde(src);
        if (l == null) {
            return 0;
        }
        CarrieresDonnees d = CarrieresDonnees.get(l);
        if (d.zones().isEmpty()) {
            ok(src, T.t("crac.carriere.aucune", "Aucune carrière sur la carte."));
            return 0;
        }
        for (Carrieres.Zone z : d.zones()) {
            ok(src, T.t("crac.carriere.ligne", "%s · %s · rayon %s · centre %s", z.nom(), z.type().id, String.valueOf(z.rayon()),
                z.centre().toShortString()));
        }
        ok(src, T.t("crac.carriere.en_attente", "%s bloc(s) en attente de repousse.", String.valueOf(d.enAttente())).withStyle(ChatFormatting.GRAY));
        return d.zones().size();
    }

    private static int retirer(CommandSourceStack src, String nom) {
        ServerLevel l = monde(src);
        if (l == null) {
            return 0;
        }
        Carrieres.Zone z = CarrieresDonnees.get(l).retirer(nom);
        if (z == null) {
            ko(src, T.t("crac.carriere.inconnue", "Aucune carrière « %s ».", nom));
            return 0;
        }
        envoyerATous(src.getServer());
        ok(src, T.t("crac.carriere.retiree", "Carrière %s retirée (ses blocs en attente repoussent quand même).", z.nom())
            .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int tp(CommandSourceStack src, String nom) {
        ServerLevel l = monde(src);
        if (l == null || !(src.getEntity() instanceof ServerPlayer p)) {
            return 0;
        }
        Carrieres.Zone z = CarrieresDonnees.get(l).zone(nom);
        if (z == null) {
            ko(src, T.t("crac.carriere.inconnue", "Aucune carrière « %s ».", nom));
            return 0;
        }
        p.teleportTo(l, z.centre().getX() + 0.5, z.centre().getY(), z.centre().getZ() + 0.5, p.getYRot(), p.getXRot());
        return 1;
    }

    private static int regenerer(CommandSourceStack src, @Nullable String nom) {
        ServerLevel l = monde(src);
        if (l == null) {
            return 0;
        }
        CarrieresDonnees d = CarrieresDonnees.get(l);
        Carrieres.Zone z = null;
        if (nom != null) {
            z = d.zone(nom);
            if (z == null) {
                ko(src, T.t("crac.carriere.inconnue", "Aucune carrière « %s ».", nom));
                return 0;
            }
        }
        int n = d.avancer(z);
        ok(src, T.t("crac.carriere.regeneree", "%s bloc(s) repoussent maintenant (sauf sous un joueur).", String.valueOf(n))
            .withStyle(ChatFormatting.GREEN));
        return n;
    }
}
