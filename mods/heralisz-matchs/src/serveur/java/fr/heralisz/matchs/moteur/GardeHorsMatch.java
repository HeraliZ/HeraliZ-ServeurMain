package fr.heralisz.matchs.moteur;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.Equipement;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.coffre.Coffres;
import fr.heralisz.matchs.moteur.spectateur.Spectateurs;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Personne dans un monde de match sans en faire partie (23/10, retour du propriétaire : après un {@code /zombies quitter}
 * pendant un lancement lent, un joueur s'est retrouvé sur la carte des zombies, « pas en partie »). Une fois par seconde,
 * tout joueur en survie ou en aventure (hors staff) qui se trouve dans un monde de HeraliZ Matchs (arènes, zombies) sans
 * partie ni rôle de spectateur : ses objets de match lui sont retirés, son Coffre (inventaire d'avant le match) lui est
 * rendu s'il en a un, puis il est renvoyé au spawn. Aucun objet d'un match ne peut ainsi sortir vers la carte.
 */
final class GardeHorsMatch {
    private static Method allerAuSpawn;
    private static boolean cherche;

    private GardeHorsMatch() {
    }

    static void tick(MinecraftServer s) {
        if (s.getTickCount() % 20 != 7) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!HeraliszMatchs.MODID.equals(p.level().dimension().location().getNamespace())) continue;
            if (p.isCreative() || p.isSpectator() || p.hasPermissions(2)) continue;
            if (MatchApi.partieDe(p.getUUID()) != null || Spectateurs.estSpectateur(p.getUUID())) continue;
            try {
                renvoyer(p);
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[matchs] renvoi hors match de {} en erreur", p.getGameProfile().getName(), ex);
            }
        }
    }

    private static void renvoyer(ServerPlayer p) {
        HeraliszMatchs.LOG.warn("[matchs] {} dans {} sans partie : objets de match retirés, renvoyé au spawn",
                p.getGameProfile().getName(), p.level().dimension().location());
        int n = Equipement.retirer(p);
        if (n > 0) HeraliszMatchs.LOG.warn("[matchs] {} : {} objet(s) de match retiré(s)", p.getGameProfile().getName(), n);
        boolean rendu = Coffre.aUnDepot(p.getUUID()) && Coffres.rendre(p);
        // le Coffre ramène là où le joueur était avant le match ; sinon, ou s'il ramène encore dans un monde de match : spawn
        if (!rendu || HeraliszMatchs.MODID.equals(p.level().dimension().location().getNamespace())) spawn(p);
        p.sendSystemMessage(Component.translatableWithFallback("matchs.hors_match.renvoye",
                "Tu n'es plus dans aucune partie : retour au spawn."));
    }

    private static void spawn(ServerPlayer p) {
        if (p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) p.setGameMode(GameType.SURVIVAL);
        if (!cherche) {
            cherche = true;
            try {
                allerAuSpawn = Class.forName("fr.heralisz.utils.api.SpawnApi").getMethod("allerAuSpawn", ServerPlayer.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                allerAuSpawn = null;
            }
        }
        if (allerAuSpawn != null) {
            try {
                if (Boolean.TRUE.equals(allerAuSpawn.invoke(null, p))) return;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                HeraliszMatchs.LOG.warn("[matchs] SpawnApi en erreur : {}", ex.toString());
            }
        }
        MinecraftServer s = p.getServer();
        if (s == null) return;
        ServerLevel l = s.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                new ResourceLocation("heralisz", "spawn")));
        if (l == null) l = s.overworld();
        BlockPos sp = l.getSharedSpawnPos();
        p.teleportTo(l, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, p.getYRot(), 0f);
    }
}
