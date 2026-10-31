package fr.heraliz.airstrike.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Horloge des frappes aériennes et des largages chez le joueur (31/10, « l'avion et les bombes avancent par à-coups »).
 * <p>
 * L'avion, les bombes, les obus, les fusées leurres et la caisse encore dans la soute ne sont pas des entités : chaque
 * joueur rejoue la frappe reçue du serveur ({@code StrikePacket}) sur des trajectoires calculées d'avance
 * ({@code FlightPath}, {@code Drop}). Leur position ne dépend que d'une heure. Avant, cette heure était le temps du monde
 * du client plus le partialTick. Ce temps avance d'un tick par tick du jeu, et le serveur le recale d'un coup chaque
 * seconde ({@code ClientboundSetTimePacket}) : dès que le serveur tient moins de 20 ticks par seconde (pics de la
 * sauvegarde, d'un impact...), ou que le réseau hésite, l'heure du joueur reculait ou sautait d'un ou plusieurs ticks une
 * fois par seconde. L'avion de chasse vole à plus de 4 blocs par tick : il reculait ou bondissait de 4 à 12 blocs, et
 * toutes les bombes avec lui. Même défaut déjà corrigé pour le train ({@code TrainClient}) et pour l'animation des
 * jumelles ({@code HorlogeJumelles}).
 * <p>
 * Cette horloge ne recule jamais : elle suit le temps du monde en accélérant ou en ralentissant doucement (de quelques
 * pour cent, invisible), et ne se recale d'un coup qu'après un écart de plus de {@value #RECALAGE} ticks (serveur gelé,
 * changement de monde). Elle avance une fois par tick (fin du tick du jeu) et l'image est placée entre les deux derniers
 * ticks : le mouvement est continu d'une image à l'autre. Même avance que l'ancien calcul (temps du monde + partialTick) :
 * les impacts envoyés par le serveur tombent au même moment que les bombes dessinées.
 * <p>
 * Le serveur ne change pas : il décide seul des largages et des impacts sur sa propre heure.
 */
public final class HorlogeFrappes {
    private HorlogeFrappes() {
    }

    /** Écart (ticks) au-delà duquel l'horloge se recale d'un coup plutôt que de rattraper (2 s). */
    static final double RECALAGE = 40.0;
    /** Part de l'écart rattrapée à chaque tick (3 % : un recalage de 3 ticks est absorbé en 1 à 2 s). */
    private static final double GAIN = 0.03;
    /** Correction lente du débit (le serveur qui tient un peu moins de 20 ticks par seconde sur la durée). */
    private static final double GAIN_DEBIT = 0.001;

    /** Heure lissée à la fin du tick précédent et du tick en cours (ticks du monde), NaN : pas encore calée. */
    private static double tPrec = Double.NaN, tTick = Double.NaN;
    private static double debit = 1.0;
    /** Monde dont l'heure est suivie (un autre monde : recalage). */
    private static ClientLevel niveau;

    /** Fin de chaque tick du jeu (ClientEvents), avant les frappes. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            vider();
            return;
        }
        double cible = level.getGameTime();
        if (level != niveau || Double.isNaN(tTick)) {
            caler(level, cible);
            return;
        }
        tPrec = tTick;
        if (mc.isPaused()) {
            return;                                                     // solo en pause : le monde ne tourne plus
        }
        double prevu = tTick + debit;
        double ecart = cible - prevu;
        if (Math.abs(ecart) > RECALAGE) {
            caler(level, cible);
            return;
        }
        debit = Math.max(0.5, Math.min(1.5, debit + GAIN_DEBIT * ecart));
        tTick = prevu + GAIN * ecart;
    }

    private static void caler(ClientLevel level, double cible) {
        niveau = level;
        debit = 1.0;
        tTick = cible;
        tPrec = cible - 1.0;
    }

    /** Déconnexion, plus de monde : l'horloge se recalera sur le prochain. */
    static void vider() {
        niveau = null;
        tPrec = tTick = Double.NaN;
        debit = 1.0;
    }

    /**
     * Heure (ticks du monde) pour l'image en cours : entre les deux derniers ticks de l'horloge, avec la même avance d'un
     * tick que le temps du monde lu juste après un tick (l'ancien {@code getGameTime() + partialTick}).
     */
    public static double temps(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (Double.isNaN(tTick) || mc.level != niveau) {
            // pas encore calée (premier tick, monde qui vient de changer) : le temps du monde, comme avant
            return mc.level == null ? Double.NaN : mc.level.getGameTime() + partialTick;
        }
        return tPrec + 1.0 + (tTick - tPrec) * partialTick;
    }
}
