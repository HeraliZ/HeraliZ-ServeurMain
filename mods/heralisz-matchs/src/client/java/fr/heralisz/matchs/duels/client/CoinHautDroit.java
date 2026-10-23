package fr.heralisz.matchs.duels.client;

import fr.heralisz.matchs.clashwar.client.CwHud;
import fr.heralisz.matchs.moteur.client.HudMoteur;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Ce qui occupe deja le coin haut droit de l'ecran (echelle du joueur), pour y glisser les defis de duel DESSOUS :
 * <ul>
 *     <li>icones d'effets du jeu (bienfaits jusqu'a y 25, effets nefastes jusqu'a y 51 ; masquees par le HUD zombies) ;</li>
 *     <li>HUD du moteur des matchs : numero de manche des zombies, fil des eliminations, invitation de groupe ;</li>
 *     <li>toasts du ClashWar ;</li>
 *     <li>toasts des clans et des bases (heraliz_clansetbases, 3 au plus chacun, 4 a 6 s) et voyant « REC » du chat
 *     vocal (CaraCrea) : autres mods, lus par reflexion SANS dependance ; mod absent ou change = ignore pour de bon (les
 *     defis s'affichent alors en haut, sans rien casser).</li>
 * </ul>
 * Calcule seulement quand un defi est affiche ; aucune allocation hors des iterateurs des piles lues. Fil client.
 */
final class CoinHautDroit {
    private CoinHautDroit() {
    }

    private static final PileExterne CLANS = new PileExterne("fr.heralisz.clans.client.gui.hud.ClanHud");
    private static boolean voixEssayee, voixAbsente;
    @Nullable
    private static Method voixEnregistre;

    /** Bas (y) de ce qui occupe le coin haut droit, 0 si rien. */
    static int bas(Minecraft mc, int h, long now) {
        int bas = effets(mc);
        bas = Math.max(bas, HudMoteur.basHautDroit(h, now));
        bas = Math.max(bas, CwHud.basToasts(now));
        bas = Math.max(bas, CLANS.bas(now));
        if (voix()) bas = Math.max(bas, 18);
        return bas;
    }

    /** Icones d'effets dessinees par le jeu en haut a droite (memes regles que l'affichage vanilla). */
    private static int effets(Minecraft mc) {
        if (mc.player == null || HudZombies.actif()) return 0;
        Collection<MobEffectInstance> l = mc.player.getActiveEffects();
        if (l.isEmpty()) return 0;
        boolean bons = false;
        for (MobEffectInstance e : l) {
            if (!e.showIcon()) continue;
            if (!e.getEffect().isBeneficial()) return 51;
            bons = true;
        }
        return bons ? 25 : 0;
    }

    /** Voyant « REC » de CaraCrea (y 6 a 18, bord droit) quand le joueur parle. */
    private static boolean voix() {
        if (voixAbsente) return false;
        try {
            if (!voixEssayee) {
                voixEssayee = true;
                voixEnregistre = Class.forName("com.caracrea.client.voice.VoiceRecorder").getMethod("recording");
            }
            return voixEnregistre != null && Boolean.TRUE.equals(voixEnregistre.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            voixAbsente = true;
            return false;
        }
    }

    /**
     * Pile de toasts d'un HUD d'heraliz_clansetbases (champ statique {@code TOASTS} : toasts empiles depuis y = 8, ecart
     * de 6, methodes {@code hauteur()}, {@code depuis()}, {@code duree()}).
     */
    private static final class PileExterne {
        private final String classe;
        private boolean essayee, absente;
        @Nullable
        private Field champ;
        @Nullable
        private Method hauteur, depuis, duree;

        PileExterne(String classe) {
            this.classe = classe;
        }

        int bas(long now) {
            if (absente) return 0;
            try {
                if (!essayee) {
                    essayee = true;
                    champ = Class.forName(classe).getDeclaredField("TOASTS");
                    champ.setAccessible(true);
                }
                if (!(champ.get(null) instanceof Collection<?> pile) || pile.isEmpty()) return 0;
                int y = 8, n = 0;
                for (Object t : pile) {
                    if (hauteur == null) {
                        Class<?> c = t.getClass();
                        Method mh = c.getDeclaredMethod("hauteur"), md = c.getDeclaredMethod("depuis"), mu = c.getDeclaredMethod("duree");
                        mh.setAccessible(true);
                        md.setAccessible(true);
                        mu.setAccessible(true);
                        depuis = md;
                        duree = mu;
                        hauteur = mh;
                    }
                    if (now >= ((Number) depuis.invoke(t)).longValue() + ((Number) duree.invoke(t)).longValue()) continue;
                    y += ((Number) hauteur.invoke(t)).intValue() + 6;
                    n++;
                }
                return n == 0 ? 0 : y - 6;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                absente = true;
                return 0;
            }
        }
    }
}
