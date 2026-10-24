package fr.heraliz.airstrike.helico;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.AirstrikeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Liens avec nos autres mods, par réflexion (aucune dépendance : un mod absent = la règle ne s'applique pas) :
 * <ul><li>HeraliZ Matchs : {@code MatchApi.enMatch(ServerPlayer)} (jamais de tir sur un joueur en match) ;</li>
 * <li>Réanimation : {@code Downed.isDowned(Entity)} (on n'achève pas un joueur à terre) ;</li>
 * <li>HeraliZ Grades : {@code GradesApi.cache(UUID)} (staff caché par /vanish) ;</li>
 * <li>HeraliZ Loot : {@code PointsT7.apparaitre(...)} (coffre T7 au crash) et {@code LootApi.tirer(...)} (contenu des
 * caisses T6).</li></ul>
 * Si une de ces API change de nom ou de signature, ce fichier aussi. Fil serveur.
 */
final class Ponts {
    private static boolean cherche;
    @Nullable
    private static Method enMatch, aTerre, cache, coffreT7, loot;

    private Ponts() {
    }

    private static void chercher() {
        if (cherche) return;
        cherche = true;
        enMatch = methode("fr.heralisz.matchs.moteur.api.MatchApi", "enMatch", ServerPlayer.class);
        aTerre = methode("fr.heraliz.reanimation.core.Downed", "isDowned", Entity.class);
        cache = methode("fr.heraliz.grades.serveur.GradesApi", "cache", UUID.class);
        coffreT7 = methode("fr.heraliz.loot.PointsT7", "apparaitre", ServerLevel.class, BlockPos.class, Direction.class,
                String.class, int.class, String.class);
        loot = methode("fr.heraliz.loot.LootApi", "tirer", ServerLevel.class, String.class, ServerPlayer.class, int.class);
        if (coffreT7 == null || loot == null) {
            AirstrikeMod.LOGGER.warn("[helico] HeraliZ Loot introuvable (ou trop ancien) : coffre T7 et caisses T6 vides");
        }
    }

    @Nullable
    private static Method methode(String classe, String nom, Class<?>... types) {
        try {
            return Class.forName(classe).getMethod(nom, types);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    private static boolean vrai(@Nullable Method m, Object... args) {
        chercher();
        if (m == null) return false;
        try {
            return Boolean.TRUE.equals(m.invoke(null, args));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    static boolean enMatch(ServerPlayer p) {
        chercher();
        return vrai(enMatch, p);
    }

    static boolean aTerre(Entity e) {
        chercher();
        return vrai(aTerre, e);
    }

    static boolean cache(ServerPlayer p) {
        chercher();
        return vrai(cache, p.getUUID());
    }

    /** Fait apparaître un coffre T7 de HeraliZ Loot pour presence ticks ; faux si impossible (place prise, mod absent). */
    static boolean coffreT7(ServerLevel l, BlockPos pos, Direction face, String type, int presence) {
        chercher();
        return vrai(coffreT7, l, pos, face, type, presence, "");
    }

    /** Contenu d'un conteneur de cette table de HeraliZ Loot (bonus de loot du joueur), au plus max objets. */
    @SuppressWarnings("unchecked")
    static List<ItemStack> loot(ServerLevel l, String table, @Nullable ServerPlayer joueur, int max) {
        chercher();
        if (loot != null) {
            try {
                Object r = loot.invoke(null, l, table, joueur, max);
                if (r instanceof List<?> liste) return (List<ItemStack>) liste;
            } catch (ReflectiveOperationException | RuntimeException e) {
                AirstrikeMod.LOGGER.warn("[helico] loot {} impossible : {}", table, e.toString());
            }
        }
        return List.of();
    }

    /**
     * Joueur que l'hélicoptère peut prendre pour cible : vivant, en survie ou aventure, dans le même monde, ni en match,
     * ni à terre, ni caché par le staff ; les bots de charge (/bots, étiquette heraliz_bot) seulement si le réglage le
     * permet. Le spawn, le monde des bases et le tutoriel sont d'autres mondes : l'hélicoptère n'y est jamais.
     */
    static boolean cible(ServerPlayer p, Entity helico) {
        if (!p.isAlive() || p.isSpectator() || p.isCreative() || p.level() != helico.level()) return false;
        if (!AirstrikeConfig.HELICO_TIRER_BOTS.get() && p.getTags().contains("heraliz_bot")) return false;
        return !enMatch(p) && !aTerre(p) && !cache(p);
    }
}
