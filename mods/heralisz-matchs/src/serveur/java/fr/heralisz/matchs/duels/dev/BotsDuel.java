package fr.heralisz.matchs.duels.dev;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.Defis;
import fr.heralisz.matchs.duels.net.Vues;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.api.Pose;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Joueurs simules du banc d'essai des duels (client de dev seulement, jamais en production) : de vrais
 * {@link ServerPlayer} inscrits dans la liste des joueurs (comme une connexion), relies a un canal reseau factice qui
 * jette tout ce qu'on leur envoie. Ils permettent de tester un vrai defi (lance ou accepte par le bot), un duel a deux
 * joueurs, et une vraie elimination du joueur (tirs TaCZ du bot, par reflexion) pour la killcam.
 * <p>Commande op {@code /duelbanc} : {@code bot <nom>} (apparait a cote du joueur), {@code defier <bot> <mode>}
 * (le bot defie le joueur), {@code accepter <bot>} (le bot accepte son dernier defi recu), {@code tirer <tireur> <cible> <ticks>}
 * (un bot ou un vrai joueur vise la cible et tire avec l'arme en main, par l'API TaCZ cote serveur), {@code accroupi <bot> <oui>} (touche s'accroupir maintenue : pose
 * ou desamorcage de la charge), {@code aller <bot> <point>} (point nomme de la carte de son match : site_a, charge...),
 * {@code retirer <bot>}.
 * <p>Fil serveur.
 */
public final class BotsDuel {
    private BotsDuel() {
    }

    /** Joueur simule : tique comme un joueur connecte (le canal factice ne fait pas tourner l'ecouteur reseau). */
    static final class Bot extends ServerPlayer {
        Bot(MinecraftServer s, ServerLevel l, GameProfile g) {
            super(s, l, g);
        }

        @Override
        public void tick() {
            if (getServer() != null && getServer().getTickCount() % 10 == 0) serverLevel().getChunkSource().move(this);
            super.tick();
            doTick();
        }
    }

    /** Rafale en cours : un tireur (bot ou vrai joueur) vise une cible et tire a chaque tick qu'il peut. */
    static final class Tir {
        final ServerPlayer tireur;
        final LivingEntity cible;
        int restant, tirs, refus;
        String dernierRefus = "";

        Tir(ServerPlayer tireur, LivingEntity cible, int ticks) {
            this.tireur = tireur;
            this.cible = cible;
            this.restant = ticks;
        }
    }

    private static final Map<String, Bot> BOTS = new LinkedHashMap<>();
    private static final Map<java.util.UUID, Tir> TIRS = new LinkedHashMap<>();
    private static final List<Tir> TAMPON = new ArrayList<>();
    private static boolean ecoute;

    // ------------------------------------------------------------------ commandes
    public static void commandes(CommandDispatcher<CommandSourceStack> d) {
        if (!ecoute) {
            ecoute = true;
            MinecraftForge.EVENT_BUS.addListener(BotsDuel::tick);
            MinecraftForge.EVENT_BUS.addListener(BotsDuel::arret);
        }
        d.register(Commands.literal("duelbanc").requires(s -> s.hasPermission(2))
                .then(Commands.literal("bot").then(Commands.argument("nom", StringArgumentType.word()).executes(BotsDuel::cmdBot)))
                .then(Commands.literal("defier").then(argBot().then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of(Vues.MODE_RD, Vues.MODE_MAM), b))
                        .executes(BotsDuel::cmdDefier))))
                .then(Commands.literal("accepter").then(argBot().executes(x -> {
                    Bot b = bot(x);
                    if (b == null) return 0;
                    Defis.repondreDernier(b, true);
                    HeraliszMatchs.LOG.info("[duels] banc : {} accepte son dernier defi", b.getGameProfile().getName());
                    return 1;
                })))
                .then(Commands.literal("tirer").then(Commands.argument("tireur", EntityArgument.player())
                        .then(Commands.argument("cible", EntityArgument.entity()).then(Commands.argument("ticks", IntegerArgumentType.integer(1, 400))
                                .executes(BotsDuel::cmdTirer)))))
                .then(Commands.literal("accroupi").then(argBot().then(Commands.argument("oui", BoolArgumentType.bool()).executes(x -> {
                    Bot b = bot(x);
                    if (b == null) return 0;
                    b.setShiftKeyDown(BoolArgumentType.getBool(x, "oui"));
                    return 1;
                }))))
                .then(Commands.literal("aller").then(argBot().then(Commands.argument("point", StringArgumentType.word()).executes(x -> {
                    Bot b = bot(x);
                    if (b == null) return 0;
                    Partie pa = MatchApi.partieDe(b);
                    Instance inst = pa == null ? null : pa.instance();
                    Pose p = inst == null ? null : inst.point(StringArgumentType.getString(x, "point"));
                    if (p == null) {
                        x.getSource().sendFailure(Component.literal("Point introuvable (le bot doit etre en match)."));
                        return 0;
                    }
                    p.teleporter(b, inst.niveau());
                    return 1;
                }))))
                .then(Commands.literal("inventaire").then(Commands.argument("joueur", EntityArgument.player()).executes(x -> {
                    inventaire(EntityArgument.getPlayer(x, "joueur"));
                    return 1;
                })))
                .then(Commands.literal("retirer").then(argBot().executes(x -> {
                    Bot b = bot(x);
                    if (b == null) return 0;
                    retirer(b);
                    return 1;
                }))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> argBot() {
        return Commands.argument("bot", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(BOTS.keySet(), b));
    }

    @Nullable
    private static Bot bot(CommandContext<CommandSourceStack> x) {
        Bot b = BOTS.get(StringArgumentType.getString(x, "bot"));
        if (b == null || b.hasDisconnected()) {
            x.getSource().sendFailure(Component.literal("Bot inconnu."));
            return null;
        }
        return b;
    }

    private static int cmdBot(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        ServerPlayer p = x.getSource().getPlayerOrException();
        String nom = StringArgumentType.getString(x, "nom");
        Bot ancien = BOTS.get(nom);
        if (ancien != null && !ancien.hasDisconnected()) return 1;
        Bot b = creer(p.server, nom);
        if (b == null) {
            x.getSource().sendFailure(Component.literal("Bot impossible (voir le journal)."));
            return 0;
        }
        float yaw = p.getYRot() + 180f;
        double ang = Math.toRadians(p.getYRot());
        b.teleportTo(p.serverLevel(), p.getX() - Math.sin(ang) * 3, p.getY(), p.getZ() + Math.cos(ang) * 3, yaw, 0f);
        BOTS.put(nom, b);
        HeraliszMatchs.LOG.info("[duels] banc : bot {} connecte ({})", nom, b.getUUID());
        return 1;
    }

    private static int cmdDefier(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        ServerPlayer p = x.getSource().getPlayerOrException();
        Bot b = bot(x);
        if (b == null) return 0;
        String mode = StringArgumentType.getString(x, "mode");
        Defis.defier(b, p.getUUID(), Vues.FORMAT_1V1, mode, "");
        HeraliszMatchs.LOG.info("[duels] banc : {} defie {} ({})", b.getGameProfile().getName(), p.getGameProfile().getName(), mode);
        return 1;
    }

    private static int cmdTirer(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        ServerPlayer t = EntityArgument.getPlayer(x, "tireur");
        Entity ce = EntityArgument.getEntity(x, "cible");
        if (!(ce instanceof LivingEntity c) || c == t) {
            x.getSource().sendFailure(Component.literal("Cible invalide."));
            return 0;
        }
        Tir tir = new Tir(t, c, IntegerArgumentType.getInteger(x, "ticks"));
        TIRS.put(t.getUUID(), tir);
        // un vrai joueur est oriente par son client : il vise une fois (teleportation sur place), un bot a chaque tick
        float[] a = angles(t, c);
        if (t instanceof Bot) orienter(t, a);
        else t.connection.teleport(t.getX(), t.getY(), t.getZ(), a[0], a[1]);
        Object op = operateur(t);
        if (op != null) {
            try {
                Supplier<ItemStack> main = t::getMainHandItem;
                Objects.requireNonNull(draw).invoke(op, main);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                HeraliszMatchs.LOG.warn("[duels] banc : draw TaCZ impossible", ex);
            }
        }
        HeraliszMatchs.LOG.info("[duels] banc : {} tire sur {} pendant {} ticks (arme {})", t.getGameProfile().getName(), c.getName().getString(),
                tir.restant, t.getMainHandItem().getItem());
        return 1;
    }

    /**
     * Journal de l'inventaire cote serveur : chaque pile avec les cles de son NBT, et le compte des marques de skin
     * interdites en duel ({@code DuelsConfig.NBT_INTERDITS}, cherchees a toute profondeur).
     */
    private static void inventaire(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        int interdits = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            net.minecraft.nbt.CompoundTag t = s.getTag();
            sb.append(i).append('=').append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()));
            if (t != null) {
                sb.append(t.getAllKeys());
                if (t.contains("GunId")) sb.append('<').append(t.getString("GunId")).append('>');
                interdits += interdits(t);
            }
            sb.append(' ');
        }
        HeraliszMatchs.LOG.info("[duels] banc inventaire {} : marques de skin {} ; {}", p.getGameProfile().getName(), interdits, sb);
    }

    private static int interdits(net.minecraft.nbt.Tag t) {
        int n = 0;
        if (t instanceof net.minecraft.nbt.CompoundTag c) {
            for (String k : c.getAllKeys()) {
                if (fr.heralisz.matchs.duels.DuelsConfig.NBT_INTERDITS.contains(k)) n++;
                n += interdits(c.get(k));
            }
        } else if (t instanceof net.minecraft.nbt.ListTag l) {
            for (net.minecraft.nbt.Tag e : l) n += interdits(e);
        }
        return n;
    }

    // ------------------------------------------------------------------ creation
    @Nullable
    private static Bot creer(MinecraftServer s, String nom) {
        try {
            GameProfile g = new GameProfile(UUIDUtil.createOfflinePlayerUUID(nom), nom);
            Bot b = new Bot(s, s.overworld(), g);
            Connection c = new Connection(PacketFlow.SERVERBOUND);
            // canal factice : tout paquet sortant est jete
            EmbeddedChannel canal = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    ReferenceCountUtil.release(msg);
                    promise.setSuccess();
                }
            });
            for (Field f : Connection.class.getDeclaredFields()) {
                if (f.getType() == Channel.class) {
                    f.setAccessible(true);
                    f.set(c, canal);
                } else if (f.getType() == SocketAddress.class) {
                    f.setAccessible(true);
                    f.set(c, new InetSocketAddress(InetAddress.getLoopbackAddress(), 25565));
                }
            }
            s.getPlayerList().placeNewPlayer(c, b);
            return b;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeraliszMatchs.LOG.error("[duels] banc : creation du bot {} impossible", nom, ex);
            return null;
        }
    }

    private static void retirer(Bot b) {
        BOTS.values().remove(b);
        if (!b.hasDisconnected()) b.connection.onDisconnect(Component.literal("banc"));
        HeraliszMatchs.LOG.info("[duels] banc : bot {} deconnecte", b.getGameProfile().getName());
    }

    // ------------------------------------------------------------------ tir
    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || TIRS.isEmpty()) return;
        TAMPON.clear();
        TAMPON.addAll(TIRS.values());
        for (Tir tir : TAMPON) {
            ServerPlayer t = tir.tireur;
            LivingEntity c = tir.cible;
            tir.restant--;
            boolean fini = tir.restant <= 0 || c.isRemoved() || t.hasDisconnected();
            if (!fini) {
                float[] a = angles(t, c);
                if (t instanceof Bot) orienter(t, a);
                Object op = operateur(t);
                if (op != null) {
                    try {
                        Supplier<Float> st = () -> a[1], sl = () -> a[0];
                        String res = String.valueOf(Objects.requireNonNull(shoot).invoke(op, st, sl));
                        if ("SUCCESS".equals(res)) tir.tirs++;
                        else {
                            tir.refus++;
                            tir.dernierRefus = res;
                        }
                    } catch (ReflectiveOperationException | RuntimeException ex) {
                        HeraliszMatchs.LOG.warn("[duels] banc : tir TaCZ impossible", ex);
                        fini = true;
                    }
                } else fini = true;
            }
            if (fini) {
                TIRS.remove(t.getUUID());
                HeraliszMatchs.LOG.info("[duels] banc : {} a tire {} balle(s) ({} refus, dernier {})", t.getGameProfile().getName(), tir.tirs, tir.refus,
                        tir.dernierRefus);
            }
        }
        TAMPON.clear();
    }

    /** Lacet et tangage (degres) pour viser les yeux de la cible depuis ceux du tireur. */
    private static float[] angles(LivingEntity t, LivingEntity c) {
        double dx = c.getX() - t.getX(), dy = c.getEyeY() - 0.15 - t.getEyeY(), dz = c.getZ() - t.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        float lacet = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f);
        float tangage = (float) -(Mth.atan2(dy, h) * Mth.RAD_TO_DEG);
        return new float[]{lacet, tangage};
    }

    /** Oriente un bot (corps, tete, tangage). */
    private static void orienter(ServerPlayer b, float[] a) {
        b.setYRot(a[0]);
        b.setXRot(a[1]);
        b.setYHeadRot(a[0]);
        b.setYBodyRot(a[0]);
    }

    /** {@code IGunOperator} TaCZ du bot (reflexion), ou null si TaCZ est absent. */
    @Nullable
    private static Object operateur(LivingEntity e) {
        if (!resolu) {
            resolu = true;
            try {
                Class<?> k = Class.forName("com.tacz.guns.api.entity.IGunOperator");
                depuis = k.getMethod("fromLivingEntity", LivingEntity.class);
                draw = k.getMethod("draw", Supplier.class);
                shoot = k.getMethod("shoot", Supplier.class, Supplier.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                depuis = null;
                HeraliszMatchs.LOG.info("[duels] banc : TaCZ absent, les bots ne tirent pas");
            }
        }
        if (depuis == null) return null;
        try {
            return depuis.invoke(null, e);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static boolean resolu;
    @Nullable
    private static Method depuis, draw, shoot;

    private static void arret(ServerStoppingEvent e) {
        for (Bot b : new ArrayList<>(BOTS.values())) {
            try {
                retirer(b);
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.debug("[duels] banc : bot deja parti", ex);
            }
        }
        BOTS.clear();
        TIRS.clear();
    }
}
