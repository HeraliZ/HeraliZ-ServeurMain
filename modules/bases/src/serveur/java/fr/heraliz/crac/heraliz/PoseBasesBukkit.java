package fr.heraliz.crac.heraliz;

import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.placable.PlacableSet;
import fr.heraliz.crac.registry.ModBlocks;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Objets de base posés malgré les régions de WorldGuard (25/11, retour du propriétaire : « dé-op, en survie, j'ai posé une
 * fondation, mais l'armoire se pose puis s'enlève tout de suite, avec des particules noires et grises, comme un bloc de
 * terre sur la carte ; aucun message »).
 * <p><b>Cause</b> : sur le serveur hybride (Mohist), poser un bloc passe aussi par les événements Bukkit
 * {@code PlayerInteractEvent} et {@code BlockPlaceEvent}. Les régions de WorldGuard de la carte refusent de CONSTRUIRE à
 * qui n'est pas op (fumée de WorldGuard, {@code use-particle-effects}) : l'armoire, les coffres et tous les objets de base
 * étaient refusés à tous les joueurs (les fondations passaient : CRac les pose lui-même, sans ces événements). Le staff,
 * op, ne le voyait pas.</p>
 * <p><b>Correction</b> : pour les objets de base seulement (armoire, objets posables en base de {@link PlacableSet}, cœur de
 * clan, échelle de raid), le refus de WorldGuard est levé en dernier (priorité HIGHEST) ; CRac les juge ensuite avec ses
 * propres règles (événements de Forge : armoire seulement dans un clan, hors des zones interdites, objets seulement dans la
 * base de son clan, sur une fondation...). Tout autre bloc (terre, blocs de Minecraft) reste refusé par WorldGuard. Le bloc
 * visé, lui, reste jugé par WorldGuard (un coffre protégé ne s'ouvre pas plus qu'avant).</p>
 * <p>Écouteurs Bukkit créés par réflexion (aucune dépendance de compilation), rattachés à un plugin déjà chargé, comme
 * {@code fr.heraliz.noyau.serveur.interactions.ClicObjetBukkit}. Sans Bukkit (solo) : rien.</p>
 */
@EventBusSubscriber(modid = "crac")
public final class PoseBasesBukkit {
    private static final String[] PORTEURS = {"WorldGuard", "LuckPerms", "AuthMe"};
    private static boolean fini;
    private static MinecraftServer serveur;
    private static Method getPlayer, getUniqueId, getAction, getHand, useItemInHand, setUseItemInHand;
    private static Method getBlockPlaced, getX, getY, getZ, setCancelled, setBuild, isCancelled;
    private static Object resultatDefaut, resultatRefus;

    private PoseBasesBukkit() {
    }

    @SubscribeEvent
    public static void demarrage(ServerStartedEvent e) {
        brancher(e.getServer());
    }

    /** Plugins pas encore prêts au démarrage : nouvel essai à la première connexion. */
    @SubscribeEvent
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (!fini && e.getEntity() instanceof ServerPlayer p) brancher(p.server);
    }

    private static synchronized void brancher(MinecraftServer srv) {
        if (fini) return;
        Class<?> bukkit;
        try {
            bukkit = Class.forName("org.bukkit.Bukkit", false, MinecraftServer.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ex) {
            fini = true;   // pas de partie Bukkit : pas de WorldGuard
            return;
        }
        try {
            ClassLoader cl = bukkit.getClassLoader();
            Class<?> ecouteur = Class.forName("org.bukkit.event.Listener", false, cl);
            Class<?> executeur = Class.forName("org.bukkit.plugin.EventExecutor", false, cl);
            Class<?> priorite = Class.forName("org.bukkit.event.EventPriority", false, cl);
            Class<?> plugin = Class.forName("org.bukkit.plugin.Plugin", false, cl);
            Class<?> interaction = Class.forName("org.bukkit.event.player.PlayerInteractEvent", false, cl);
            Class<?> pose = Class.forName("org.bukkit.event.block.BlockPlaceEvent", false, cl);
            Class<?> resultat = Class.forName("org.bukkit.event.Event$Result", false, cl);
            Class<?> bloc = Class.forName("org.bukkit.block.Block", false, cl);
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object proprio = porteur(pm);
            if (proprio == null) return;   // nouvel essai plus tard
            getUniqueId = Class.forName("org.bukkit.entity.Entity", false, cl).getMethod("getUniqueId");
            getPlayer = interaction.getMethod("getPlayer");
            getAction = interaction.getMethod("getAction");
            getHand = interaction.getMethod("getHand");
            useItemInHand = interaction.getMethod("useItemInHand");
            setUseItemInHand = interaction.getMethod("setUseItemInHand", resultat);
            getBlockPlaced = pose.getMethod("getBlockPlaced");
            setCancelled = pose.getMethod("setCancelled", boolean.class);
            setBuild = pose.getMethod("setBuild", boolean.class);
            isCancelled = pose.getMethod("isCancelled");
            getX = bloc.getMethod("getX");
            getY = bloc.getMethod("getY");
            getZ = bloc.getMethod("getZ");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object defaut = Enum.valueOf((Class) resultat, "DEFAULT");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object refus = Enum.valueOf((Class) resultat, "DENY");
            resultatDefaut = defaut;
            resultatRefus = refus;
            serveur = srv;
            Object e = Proxy.newProxyInstance(cl, new Class<?>[]{ecouteur}, (px, m, a) -> objet(px, m, a));
            Object exec = Proxy.newProxyInstance(cl, new Class<?>[]{executeur}, (px, m, a) -> {
                if (m.getName().equals("execute") && a != null && a.length == 2) {
                    if (pose.isInstance(a[1])) pose(a[1]);
                    else if (interaction.isInstance(a[1])) interaction(a[1]);
                    return null;
                }
                return objet(px, m, a);
            });
            Method enregistrer = pm.getClass().getMethod("registerEvent", Class.class, ecouteur, priorite, executeur, plugin, boolean.class);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object haute = Enum.valueOf((Class) priorite, "HIGHEST");
            // ignoreCancelled = false : c'est justement l'événement refusé par WorldGuard qu'on veut voir
            enregistrer.invoke(pm, interaction, e, haute, exec, proprio, false);
            enregistrer.invoke(pm, pose, e, haute, exec, proprio, false);
            fini = true;
            Crac.LOGGER.info("[CRac] objets de base : poses jugées par CRac, pas par les régions de WorldGuard (armoire, objets de base)");
        } catch (Throwable ex) {
            fini = true;
            Crac.LOGGER.warn("[CRac] objets de base : correctif WorldGuard impossible à brancher ({})", ex.toString());
        }
    }

    /** Objet de base : l'armoire, un objet posable en base, le cœur de clan, l'échelle de raid. */
    static boolean objetDeBase(ServerLevel sl, Block b) {
        return b instanceof ClaimBlock || b == ModBlocks.CLAIM_BLOCK.get() || b instanceof fr.heraliz.crac.block.CoeurClanBlock
                || b instanceof fr.heraliz.crac.block.EchelleBlock || PlacableSet.contains(sl, b);
    }

    /** HIGHEST : clic droit avec un objet de base en main refusé par une région : l'objet peut servir (CRac jugera la pose). */
    private static void interaction(Object ev) {
        try {
            if (!(getAction.invoke(ev) instanceof Enum<?> action) || !"RIGHT_CLICK_BLOCK".equals(action.name())) return;
            if (useItemInHand.invoke(ev) != resultatRefus) return;
            ServerPlayer sp = joueur(getPlayer.invoke(ev));
            if (sp == null || sp.isSpectator()) return;
            InteractionHand main = getHand.invoke(ev) instanceof Enum<?> h && "OFF_HAND".equals(h.name())
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack st = sp.getItemInHand(main);
            if (st.getItem() instanceof BlockItem bi && objetDeBase(sp.serverLevel(), bi.getBlock())) {
                setUseItemInHand.invoke(ev, resultatDefaut);
            }
        } catch (Throwable ex) {
            Crac.LOGGER.debug("[CRac] objets de base : clic Bukkit illisible ({})", ex.toString());
        }
    }

    /** HIGHEST : pose d'un objet de base refusée par une région : levée, CRac la juge ensuite (événements de Forge). */
    private static void pose(Object ev) {
        try {
            if (!Boolean.TRUE.equals(isCancelled.invoke(ev))) return;
            ServerPlayer sp = joueur(getPlayer.invoke(ev));
            if (sp == null || sp.isSpectator()) return;
            Object b = getBlockPlaced.invoke(ev);
            BlockPos pos = new BlockPos((int) getX.invoke(b), (int) getY.invoke(b), (int) getZ.invoke(b));
            ServerLevel sl = sp.serverLevel();
            if (objetDeBase(sl, sl.getBlockState(pos).getBlock())) {
                setCancelled.invoke(ev, false);
                setBuild.invoke(ev, true);
            }
        } catch (Throwable ex) {
            Crac.LOGGER.debug("[CRac] objets de base : pose Bukkit illisible ({})", ex.toString());
        }
    }

    @Nullable
    private static ServerPlayer joueur(Object bp) throws ReflectiveOperationException {
        if (bp == null || serveur == null || !(getUniqueId.invoke(bp) instanceof UUID u)) return null;
        return serveur.getPlayerList().getPlayer(u);
    }

    @Nullable
    private static Object porteur(Object pm) throws ReflectiveOperationException {
        Method getPlugin = pm.getClass().getMethod("getPlugin", String.class);
        for (String nom : PORTEURS) {
            Object p = getPlugin.invoke(pm, nom);
            if (p != null && Boolean.TRUE.equals(p.getClass().getMethod("isEnabled").invoke(p))) return p;
        }
        return null;
    }

    private static Object objet(Object px, Method m, Object[] a) {
        return switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(px);
            case "equals" -> a != null && a.length == 1 && px == a[0];
            case "toString" -> "HeraliZ CRac (objets de base et régions de WorldGuard)";
            default -> null;
        };
    }
}
