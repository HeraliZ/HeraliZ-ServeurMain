package fr.heralisz.utils.cycle;

import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.pont.Crochet;
import java.util.Locale;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Cycle jour / nuit de la carte (25/10), partie commune : la courbe qui relie le temps réel à l'heure de Minecraft, son
 * inverse, et le canal {@code heralisz_utils:cycle} (version 1, facultatif des deux côtés comme celui de la carte).
 *
 * <p>La règle doDaylightCycle reste à false : seule l'horloge du serveur ({@code CycleServeur}, src/serveur) écrit l'heure
 * de la carte. Un tour complet (24 000 ticks de Minecraft) dure jour + nuit en temps réel (réglages
 * heraliz_cycle-server.toml, 20 min + 4 min par défaut) :
 * <ul>
 *   <li>jour : de 23 460 (lever du soleil) à 12 541 du tour suivant, soit 13 081 ticks de Minecraft en {@code jourTicks}
 *       ticks réels ;</li>
 *   <li>nuit : de 12 541 à 23 460, soit 10 919 ticks de Minecraft en {@code nuitTicks} ticks réels.</li>
 * </ul>
 * La position réelle {@code p} dans le tour (0 = lever du soleil, {@code jourTicks} = tombée de la nuit) se déduit de
 * l'heure par {@link #position} : rien à sauvegarder, l'heure du level.dat suffit. Le jeu du joueur
 * ({@code CycleClient}, src/client) fait avancer son heure avec la même courbe entre deux recalages du serveur.
 * L'heure n'est jamais ramenée entre 0 et 24 000 (la phase de la lune en dépend) : elle ne fait que monter.
 */
public final class Cycle {
    private Cycle() {
    }

    /** Un tour complet de Minecraft. */
    public static final long TOUR_MC = 24000L;
    /** Lever du soleil (début du jour du cycle). */
    public static final long DEBUT_JOUR = 23460L;
    /** Tombée de la nuit (début de la nuit du cycle). */
    public static final long DEBUT_NUIT = 12541L;
    /** Ticks de Minecraft du jour (23 460 -> 12 541 + 24 000) et de la nuit (12 541 -> 23 460). */
    public static final long DUREE_JOUR_MC = 13081L, DUREE_NUIT_MC = TOUR_MC - DUREE_JOUR_MC;

    public static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.ChannelBuilder.named(new ResourceLocation(HeraliszUtils.MODID, "cycle"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .simpleChannel();

    /** Réglages reçus du serveur : traités par la partie client (CycleClient.recevoir), neutre ailleurs. */
    public static final Crochet<Reglages> RECEPTION_CLIENT = new Crochet<>();

    /**
     * Ce que le jeu du joueur doit savoir pour prédire l'heure : le cycle tourne-t-il dans SON monde (carte, ou monde qui
     * suit l'heure de la carte sans heure fixe), durées réelles du jour et de la nuit (ticks), pause.
     */
    public record Reglages(boolean actif, int jourTicks, int nuitTicks, boolean pause) {
        void ecrire(FriendlyByteBuf b) {
            b.writeBoolean(actif);
            b.writeVarInt(jourTicks);
            b.writeVarInt(nuitTicks);
            b.writeBoolean(pause);
        }

        static Reglages lire(FriendlyByteBuf b) {
            boolean actif = b.readBoolean();
            int j = Math.max(20, b.readVarInt());
            int n = Math.max(20, b.readVarInt());
            return new Reglages(actif, j, n, b.readBoolean());
        }
    }

    /** Position dans le cycle : numéro du tour (compté depuis un lever du soleil) et ticks réels depuis ce lever. */
    public record Position(long tour, double p) {
    }

    private static boolean enregistre;

    /**
     * Enregistre le paquet du canal (une seule fois : appelé par la partie serveur ET par la partie client, toutes deux
     * présentes dans le jar -complet ; pendant le constructeur du @Mod, avant toute connexion).
     */
    public static synchronized void enregistrer() {
        if (enregistre) return;
        enregistre = true;
        CANAL.messageBuilder(Reglages.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Reglages::ecrire).decoder(Reglages::lire)
                .consumerMainThread((m, ctx) -> RECEPTION_CLIENT.accept(m))
                .add();
    }

    /** Ticks réels de tant de minutes. */
    public static int ticks(int minutes) {
        return minutes * 60 * 20;
    }

    /** Heure de Minecraft (absolue, en double) au tour {@code tour}, {@code p} ticks réels après le lever du soleil. */
    public static double temps(long tour, double p, int jourTicks, int nuitTicks) {
        double g = p < jourTicks ? DUREE_JOUR_MC * p / jourTicks
                : DUREE_JOUR_MC + DUREE_NUIT_MC * (p - jourTicks) / nuitTicks;
        return DEBUT_JOUR + tour * (double) TOUR_MC + g;
    }

    /** Inverse de {@link #temps} : où en est le cycle à cette heure de Minecraft. */
    public static Position position(long dayTime, int jourTicks, int nuitTicks) {
        long u = dayTime - DEBUT_JOUR;
        long tour = Math.floorDiv(u, TOUR_MC);
        long r = Math.floorMod(u, TOUR_MC);
        double p = r < DUREE_JOUR_MC ? r * (double) jourTicks / DUREE_JOUR_MC
                : jourTicks + (r - DUREE_JOUR_MC) * (double) nuitTicks / DUREE_NUIT_MC;
        return new Position(tour, p);
    }

    /** Prochaine heure (absolue, jamais en arrière) qui tombe sur {@code heureMc} modulo 24 000. */
    public static long prochain(long dayTime, long heureMc) {
        return dayTime + Math.floorMod(heureMc - dayTime, TOUR_MC);
    }

    /** Heure de Minecraft (0 .. 23 999) d'une heure d'horloge : 0 = 6 h 00, 6 000 = midi, 18 000 = minuit. */
    public static long heureMc(int heures, int minutes) {
        return Math.floorMod(heures * 1000L + Math.round(minutes * 1000 / 60.0) - 6000L, TOUR_MC);
    }

    /** Heure d'horloge « hh:mm » d'une heure de Minecraft. */
    public static String heure(long dayTime) {
        long min = Math.floorMod(dayTime + 6000L, TOUR_MC) * 60L / 1000L;     // minutes depuis minuit, 0 .. 1 439
        return String.format(Locale.ROOT, "%02d:%02d", min / 60, min % 60);
    }

    /**
     * Horloge du cycle, la même pour le serveur et le jeu du joueur : position = base (calée sur une heure) + nombre
     * entier de ticks réels écoulés, recalculée en double à chaque fois (jamais de flottants accumulés).
     */
    public static final class Horloge {
        public long tour;
        public double base;
        public long ticks;
        public int jourTicks = Cycle.ticks(20), nuitTicks = Cycle.ticks(4);

        /** Cale l'horloge sur cette heure de Minecraft, avec ces durées réelles. */
        public void caler(long dayTime, int jourTicks, int nuitTicks) {
            this.jourTicks = Math.max(20, jourTicks);
            this.nuitTicks = Math.max(20, nuitTicks);
            Position pos = Cycle.position(dayTime, this.jourTicks, this.nuitTicks);
            this.tour = pos.tour();
            this.base = pos.p();
            this.ticks = 0;
        }

        /** Ticks réels depuis le lever du soleil de ce tour. */
        public double p() {
            return base + ticks;
        }

        /** Un tick réel de plus ; au bout de la nuit, tour suivant. */
        public void avancer() {
            ticks++;
            int c = jourTicks + nuitTicks;
            double p = base + ticks;
            if (p >= c) {
                base = p - c;
                ticks = 0;
                tour++;
            }
        }

        /** Heure de Minecraft (en double) à la position de l'horloge. */
        public double temps() {
            return Cycle.temps(tour, p(), jourTicks, nuitTicks);
        }

        public boolean nuit() {
            return p() >= jourTicks;
        }

        /** Ticks réels avant le prochain changement (tombée de la nuit, ou lever du soleil). */
        public double restant() {
            double p = p();
            return p < jourTicks ? jourTicks - p : jourTicks + nuitTicks - p;
        }
    }
}
