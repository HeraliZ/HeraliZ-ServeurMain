package fr.heralisz.clans.api;

import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.EtatZone;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Territoires des clans pour la carte du monde (écran « Carte » de HeraliZ Utils, lu par réflexion). API stable : ne pas
 * renommer cette classe ni changer ses signatures (CLAUDE.md §5).
 * <p>
 * Côté joueur seulement. Tant que la carte est ouverte, elle appelle {@link #demander()} (au plus une fois par seconde) ;
 * le serveur répond avec ce que le joueur a le droit de voir : toutes les zones (centre, rayon, propriétaire, état
 * public, comme le drapeau et le panneau du drapeau le montrent à tous), le ravitaillement des zones de SON clan, et la
 * position des membres de son clan connectés dans le même monde (jamais en match, jamais d'autres joueurs).
 * Vide sur un serveur dédié et tant que rien n'est arrivé.
 */
public final class CarteApi {
    private CarteApi() {
    }

    /**
     * Une zone capturable telle que la carte la montre.
     *
     * @param etat              NEUTRE (libre), TENUE, CAPTURE, CONTESTE ou PROTEGEE (10 min après une capture)
     * @param capteurTag        clan qui capture (vide hors capture), avec sa couleur et sa bannière
     * @param progression       avancée de la capture, 0..1
     * @param tenueDepuisMs     depuis quand le propriétaire la tient (0 si libre)
     * @param protegeeMs        reste de la protection (0 si aucune)
     * @param valeur            niveau de loot de la ville (1 à 7)
     * @param bonusLoot         bonus de loot (%) des membres du propriétaire présents dans la zone
     * @param monClan           la zone est tenue par le clan du joueur
     * @param ravitaillementMs  pour une zone de son clan : délai avant le prochain ravitaillement (0 = prêt), -1 sinon
     * @param peutRavitailler   il a la permission et un drapeau est posé (le serveur revérifie tout, dont la distance)
     */
    public record Zone(String id, String nom, int x, int z, int rayon, boolean drapeau, int drapeauX, int drapeauZ,
                       String proprioTag, String proprioNom, int proprioCouleur, @Nullable Banniere proprioBanniere,
                       EtatZone etat, String capteurTag, String capteurNom, int capteurCouleur, @Nullable Banniere capteurBanniere,
                       float progression, long tenueDepuisMs, long protegeeMs, int valeur, int bonusLoot, boolean monClan,
                       long ravitaillementMs, boolean peutRavitailler) {
        public boolean libre() {
            return proprioTag.isEmpty();
        }
    }

    /** Un membre du clan du joueur, connecté dans le même monde que lui (lui exclu). */
    public record Allie(String nom, double x, double z) {
    }

    /** Le clan du joueur : nom, couleur, bannière, membres en ligne (lui compris), zones tenues. */
    public record MonClan(String tag, String nom, int couleur, @Nullable Banniere banniere, int enLigne, int territoires) {
    }

    /** Dessin d'une bannière de clan dans une interface (branché par la partie client du mod des clans). */
    @FunctionalInterface
    public interface DessinBanniere {
        void dessiner(Object guiGraphics, Banniere banniere, int x, int y, int w, int h);
    }

    private static volatile List<Zone> zones = List.of();
    private static volatile List<Allie> allies = List.of();
    @Nullable
    private static volatile MonClan monClan;
    private static volatile int version;
    private static volatile long recuLe;

    /** Branché par la partie client : envoie la demande au serveur (rien sur un serveur dédié). */
    public static volatile Runnable demande = () -> {
    };
    /** Branché par la partie client : demande de ravitaillement d'une zone (le serveur exige d'être au drapeau). */
    public static volatile java.util.function.Consumer<String> ravitaillement = id -> {
    };
    /** Branché par la partie client : dessin d'une bannière (BannerArt). */
    @Nullable
    public static volatile DessinBanniere dessin;

    /** Les zones (liste figée, à relire quand {@link #version()} change). */
    public static List<Zone> zones() {
        return zones;
    }

    /** Les membres de son clan visibles sur la carte. */
    public static List<Allie> allies() {
        return allies;
    }

    /** Son clan, null s'il n'en a pas. */
    @Nullable
    public static MonClan monClan() {
        return monClan;
    }

    /** Change à chaque réponse du serveur. */
    public static int version() {
        return version;
    }

    /** Heure (ms, horloge du joueur) de la dernière réponse, 0 si aucune. */
    public static long recuLe() {
        return recuLe;
    }

    /** Demande l'état des territoires au serveur (la carte l'appelle au plus une fois par seconde). */
    public static void demander() {
        demande.run();
    }

    /** Demande le ravitaillement de la zone (mêmes règles que le panneau du drapeau). */
    public static void ravitailler(String zoneId) {
        ravitaillement.accept(zoneId);
    }

    /**
     * Dessine la bannière (objet {@link Banniere} reçu dans une zone ou {@link MonClan}) dans la boîte (x, y, w, h) d'un
     * GuiGraphics ; rien si la partie client des clans est absente.
     */
    public static void dessinerBanniere(Object guiGraphics, @Nullable Object banniere, int x, int y, int w, int h) {
        DessinBanniere d = dessin;
        if (d != null && banniere instanceof Banniere b) d.dessiner(guiGraphics, b, x, y, w, h);
    }

    /** Réception de la réponse du serveur (paquet du mod). */
    public static void recevoir(List<Zone> z, List<Allie> a, @Nullable MonClan m) {
        zones = List.copyOf(z);
        allies = List.copyOf(a);
        monClan = m;
        recuLe = System.currentTimeMillis();
        version++;
    }

    /** Déconnexion : tout est oublié. */
    public static void vider() {
        zones = List.of();
        allies = List.of();
        monClan = null;
        recuLe = 0;
        version++;
    }
}
