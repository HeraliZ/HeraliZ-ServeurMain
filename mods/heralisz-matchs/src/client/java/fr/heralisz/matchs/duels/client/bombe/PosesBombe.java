package fr.heralisz.matchs.duels.client.bombe;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.bombe.OrdinateurDuel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Le joueur qui utilise l'ordinateur (attaquant qui tape les codes, défenseur qui le pirate) :
 * <ul>
 *     <li>en vue à la 1re personne, ses mains (et l'arme tenue) sont cachées : on voit l'ordinateur ;</li>
 *     <li>si Player Animator est présent (dépendance FACULTATIVE, par réflexion : le mod est embarqué dans heraliz_emote
 *     et existe aussi seul), ses bras visent le clavier (accroupi qui tape, à petits coups) ou tiennent l'ordinateur
 *     (accroupi qui pirate), tête penchée vers l'écran ; sans lui, la pose accroupie normale.</li>
 * </ul>
 * Chaque ordinateur en cours d'usage se signale à chaque tick ({@link OrdinateurDuel#TICK_CLIENT}).
 */
public final class PosesBombe {
    private PosesBombe() {
    }

    private record Suivi(OrdinateurDuel ordinateur, long tick) {
    }

    /** Id d'entité du joueur -> ordinateur qu'il utilise (mis à jour à chaque tick tant que dure l'action). */
    private static final Map<Integer, Suivi> SUIVIS = new HashMap<>();

    /** Appelé par chaque ordinateur du monde du joueur, à chaque tick. */
    public static void suivre(OrdinateurDuel o) {
        try {
            int e = o.etat();
            int u = o.utilisateurId();
            if (u < 0 || (e != OrdinateurDuel.AMORCAGE && e != OrdinateurDuel.PIRATAGE)) return;
            SUIVIS.put(u, new Suivi(o, o.level().getGameTime()));
            if (o.level().getEntity(u) instanceof AbstractClientPlayer p) installer(p);
        } catch (RuntimeException | LinkageError ex) {
            desactiver(ex);
        }
    }

    /** Ordinateur que ce joueur tape ou tient en ce moment, ou null. */
    @Nullable
    static OrdinateurDuel utilise(Player p) {
        Suivi s = SUIVIS.get(p.getId());
        if (s == null) return null;
        OrdinateurDuel o = s.ordinateur();
        if (o.isRemoved() || o.level() != p.level() || p.level().getGameTime() - s.tick() > 2 || o.utilisateurId() != p.getId()) {
            SUIVIS.remove(p.getId());
            return null;
        }
        int e = o.etat();
        return e == OrdinateurDuel.AMORCAGE || e == OrdinateurDuel.PIRATAGE ? o : null;
    }

    /** 1re personne : pas de mains ni d'arme pendant qu'on tape ou qu'on pirate. */
    public static void mains(RenderHandEvent e) {
        Player p = Minecraft.getInstance().player;
        if (p != null && !SUIVIS.isEmpty() && utilise(p) != null) e.setCanceled(true);
    }

    public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
        SUIVIS.clear();
    }

    // ------------------------------------------------------------------ Player Animator (réflexion)
    private static boolean essaye, dispo;
    private static Method couches, ajouter, lireX, lireY, lireZ;
    private static Constructor<?> vecteur;
    private static Class<?> animation;
    private static final Set<AbstractClientPlayer> INSTALLES = Collections.newSetFromMap(new WeakHashMap<>());

    private static boolean pret() {
        if (essaye) return dispo;
        essaye = true;
        if (!ModList.get().isLoaded("playeranimator")) return false;
        try {
            ClassLoader cl = PosesBombe.class.getClassLoader();
            Class<?> acces = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess", true, cl);
            Class<?> pile = Class.forName("dev.kosmx.playerAnim.api.layered.AnimationStack", true, cl);
            animation = Class.forName("dev.kosmx.playerAnim.api.layered.IAnimation", true, cl);
            Class<?> vec3f = Class.forName("dev.kosmx.playerAnim.core.util.Vec3f", true, cl);
            Class<?> vector3 = Class.forName("dev.kosmx.playerAnim.core.util.Vector3", true, cl);
            couches = acces.getMethod("getPlayerAnimLayer", AbstractClientPlayer.class);
            ajouter = pile.getMethod("addAnimLayer", int.class, animation);
            vecteur = vec3f.getConstructor(float.class, float.class, float.class);
            lireX = vector3.getMethod("getX");
            lireY = vector3.getMethod("getY");
            lireZ = vector3.getMethod("getZ");
            dispo = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[duels] Player Animator illisible, poses de la bombe sans animation : {}", ex.toString());
        }
        return dispo;
    }

    /** Une erreur pendant le rendu : plus de poses de la bombe jusqu'au redémarrage du jeu (journal une fois). */
    private static void desactiver(Throwable ex) {
        if (!dispo) return;
        dispo = false;
        HeraliszMatchs.LOG.warn("[duels] poses de la bombe coupées (Player Animator) : {}", ex.toString());
    }

    private static void installer(AbstractClientPlayer p) {
        if (INSTALLES.contains(p) || !pret()) return;
        INSTALLES.add(p);
        try {
            Object couche = Proxy.newProxyInstance(animation.getClassLoader(), new Class<?>[]{animation}, new Couche(p));
            ajouter.invoke(couches.invoke(null, p), 1400, couche);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            dispo = false;
            HeraliszMatchs.LOG.warn("[duels] couche Player Animator refusée, poses de la bombe sans animation : {}", ex.toString());
        }
    }

    /**
     * Couche d'animation d'un joueur (interface IAnimation de Player Animator, par procuration) : active seulement quand
     * il utilise un ordinateur. Rotations des bras (visée du clavier ou des côtés de l'ordinateur tenu, comme le passage
     * de carte des bunkers) et de la tête ; le reste garde la pose accroupie de Minecraft.
     */
    private static final class Couche implements InvocationHandler {
        private final AbstractClientPlayer joueur;
        private float partial;

        Couche(AbstractClientPlayer joueur) {
            this.joueur = joueur;
        }

        /**
         * Appelé par Player Animator pendant le rendu du joueur : JAMAIS d'exception qui en sorte (elle ferait planter le
         * jeu au rendu du personnage). Toute erreur coupe les poses de la bombe pour la session (journal une fois) et la
         * couche rend la pose reçue telle quelle.
         */
        @Override
        public Object invoke(Object proxy, Method m, Object[] args) {
            String nom = m.getName();
            try {
                switch (nom) {
                    case "equals":
                        return args != null && args.length == 1 && proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "heralisz_matchs:bombe";
                    default:
                        break;
                }
                if (!dispo) return neutre(proxy, m, args);
                switch (nom) {
                    case "isActive":
                        return utilise(this.joueur) != null;
                    case "setupAnim":
                        if (args != null && args.length > 0 && args[0] instanceof Number n) this.partial = n.floatValue();
                        return null;
                    case "tick":
                        return null;
                    case "get3DTransform":
                        if (args == null || args.length < 4) return args != null && args.length > 0 ? args[args.length - 1] : null;
                        if (!(args[0] instanceof String partie) || !(args[1] instanceof Enum<?> type)) return args[3];
                        return transformer(partie, type, args[3]);
                    default:
                        return neutre(proxy, m, args);
                }
            } catch (Throwable ex) {
                desactiver(ex);
                return neutre(proxy, m, args);
            }
        }

        /** Réponse sans effet : méthode par défaut de l'interface, sinon une valeur neutre du bon type. */
        private static Object neutre(Object proxy, Method m, Object[] args) {
            String nom = m.getName();
            if ("isActive".equals(nom)) return false;
            if ("get3DTransform".equals(nom) && args != null && args.length >= 4) return args[3];
            if (m.isDefault()) {
                try {
                    return InvocationHandler.invokeDefault(proxy, m, args);
                } catch (Throwable ignore) {
                    // repli ci-dessous
                }
            }
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r == int.class) return 0;
            if (r == float.class) return 0f;
            if (r == double.class) return 0d;
            if (r == long.class) return 0L;
            if (r.isEnum()) {
                Object[] valeurs = r.getEnumConstants();
                for (Object v : valeurs) if ("NONE".equals(((Enum<?>) v).name())) return v;
                return valeurs.length > 0 ? valeurs[0] : null;
            }
            return null;
        }

        private Object transformer(String partie, Enum<?> type, Object v0) throws ReflectiveOperationException {
            OrdinateurDuel o = utilise(this.joueur);
            if (o == null || !"ROTATION".equals(type.name())) return v0;
            boolean droit = partie.equals("rightArm");
            if (!droit && !partie.equals("leftArm") && !partie.equals("head")) return v0;
            float x0 = ((Number) lireX.invoke(v0)).floatValue(), y0 = ((Number) lireY.invoke(v0)).floatValue(), z0 = ((Number) lireZ.invoke(v0)).floatValue();
            float t = this.joueur.tickCount + this.partial;
            // entrée en douceur (4 ticks) à partir du début de l'action
            float w = Mth.clamp(o.progression(this.partial) * o.dureeAction() / 4f, 0f, 1f);
            PoseOrdinateur pose = PoseOrdinateur.nouvelle().calculer(o, this.partial);
            float corps = Mth.rotLerp(this.partial, this.joueur.yBodyRotO, this.joueur.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 avant = new Vec3(-Mth.sin(corps), 0, Mth.cos(corps)), droite = new Vec3(-Mth.cos(corps), 0, -Mth.sin(corps));
            Vec3 base = this.joueur.getPosition(this.partial);
            boolean pirate = o.etat() == OrdinateurDuel.PIRATAGE;
            if (partie.equals("head")) {
                // tête penchée vers l'écran (le lacet reste celui du joueur)
                Vec3 oeil = base.add(0, this.joueur.getEyeHeight(), 0);
                Vec3 cible = pose.point(0, pirate ? 4 : 2, pirate ? 2 : 0);
                Vec3 d = cible.subtract(oeil);
                float tangage = (float) Mth.clamp(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)), -0.2, 1.1);
                return vecteur.newInstance(Mth.lerp(w, x0, tangage), y0, z0);
            }
            // bras : de l'épaule (accroupi) vers le clavier (tape) ou vers le bord de l'ordinateur tenu (pirate)
            double cote = droit ? 1 : -1;
            Vec3 epaule = base.add(0, this.joueur.isCrouching() ? 0.98 : 1.29, 0).add(droite.scale(0.31 * cote));
            Vec3 cible = pirate ? pose.point(-5.2 * cote, 0.6, -1.0) : pose.point(-2.4 * cote, 1.0, -1.6);
            Vec3 d = cible.subtract(epaule);
            double f = d.dot(avant), u = d.y, l = d.dot(droite);
            float tangage = (float) -Math.atan2(Math.sqrt(f * f + l * l), -u);
            float lacet = (float) Math.atan2(l, Math.max(f, 0.05));
            if (!pirate) tangage += 0.06f * Mth.sin(t * 1.7f + (droit ? 0f : 1.9f));
            return vecteur.newInstance(Mth.lerp(w, x0, tangage), Mth.lerp(w, y0, lacet), Mth.lerp(w, z0, 0f));
        }
    }
}
