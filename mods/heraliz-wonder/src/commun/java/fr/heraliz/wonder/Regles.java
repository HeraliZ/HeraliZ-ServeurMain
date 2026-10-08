package fr.heraliz.wonder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Regles du serveur pour les Wonder Weapons, sans dependre de HeraliZ Matchs (reflexion douce, cles du contrat du mode
 * zombies) :
 * <ul>
 *   <li>ces armes n'existent que dans le mode zombies : hors partie, elles ne tirent pas (sauf creatif, ou reglage
 *   {@code tirHorsZombies}) ;</li>
 *   <li>jamais de tir ami : les effets de zone (eclaboussure, foudre) ne touchent <b>jamais un autre joueur</b> ; en partie
 *   de zombies ils ne touchent que les creatures de la partie, ailleurs que les monstres ;</li>
 *   <li>les balles TaCZ elles-memes suivent les regles du mode zombies (pas de degats entre joueurs en partie).</li>
 * </ul>
 */
public final class Regles {
    private Regles() {
    }

    /** Cle de {@code persistentData} posee sur chaque creature d'une partie de zombies ({@code ZombiesApi.TAG_PARTIE}). */
    public static final String TAG_ZOMBIE = "heralisz:zombies";
    /** Dimension des parties de zombies de HeraliZ Matchs. */
    public static final ResourceLocation DIM_ZOMBIES = new ResourceLocation("heralisz_matchs", "zombies");

    private static boolean cherche;
    @Nullable
    private static Method partieDe;

    /** Vrai si le joueur est dans une partie de zombies (HeraliZ Matchs), sinon s'il est dans la dimension des zombies. */
    public static boolean enZombies(@Nullable Entity e) {
        if (e == null) return false;
        Method m = methode();
        if (m != null) {
            try {
                return m.invoke(null, e) != null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                partieDe = null;
            }
        }
        return dansDimensionZombies(e.level());
    }

    public static boolean dansDimensionZombies(Level l) {
        return l != null && DIM_ZOMBIES.equals(l.dimension().location());
    }

    @Nullable
    private static Method methode() {
        if (!cherche) {
            cherche = true;
            try {
                if (ModList.get() != null && ModList.get().isLoaded("heralisz_matchs")) {
                    partieDe = Class.forName("fr.heralisz.matchs.zombies.jeu.api.ZombiesApi").getMethod("partieDe", Entity.class);
                    HeralizWonder.LOG.info("[wonder] mode zombies de HeraliZ Matchs branche (ZombiesApi.partieDe)");
                }
            } catch (ReflectiveOperationException | LinkageError ex) {
                HeralizWonder.LOG.warn("[wonder] HeraliZ Matchs illisible ({}) : on se fie a la dimension des zombies", ex.toString());
                partieDe = null;
            }
        }
        return partieDe;
    }

    /** Vrai si l'entite est une creature d'une partie de zombies. */
    public static boolean estZombieDePartie(Entity e) {
        return e.getPersistentData().contains(TAG_ZOMBIE);
    }

    /**
     * Cible valable d'un effet de zone : vivante, pas le tireur, jamais un joueur ; en partie de zombies, seulement les
     * creatures de la partie ; ailleurs, seulement les monstres.
     */
    public static boolean estCible(Entity e, @Nullable Entity tireur, boolean zombies) {
        if (!(e instanceof LivingEntity le) || e == tireur || !le.isAlive() || e.isSpectator() || e instanceof Player || e instanceof ArmorStand) return false;
        if (le.isInvulnerable()) return false;
        return zombies ? estZombieDePartie(e) : e instanceof Enemy;
    }

    /** Le joueur peut-il tirer avec une Wonder Weapon ici ? (serveur : seul juge) */
    public static boolean peutTirer(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return true;
        if (WonderConfig.lire(WonderConfig.TIR_HORS_ZOMBIES)) return true;
        return enZombies(p);
    }
}
