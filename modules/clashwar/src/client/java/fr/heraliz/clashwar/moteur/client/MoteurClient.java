package fr.heraliz.clashwar.moteur.client;

import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.moteur.api.Annonces;
import fr.heraliz.clashwar.moteur.api.Hud;
import fr.heraliz.clashwar.moteur.api.KillFeed;
import fr.heraliz.clashwar.moteur.net.PaquetsMoteur;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Etat client du moteur (recu par {@code PaquetsMoteur}) : bandeau, annonces, compte a rebours, fil des eliminations,
 * spectateur, choix de classe, etat « en match ». Dessine par {@link HudMoteur} ; ouvre {@link ChoixClasseScreen}.
 * Fil client.
 */
public final class MoteurClient {
    private MoteurClient() {
    }

    /** Lignes gardees dans le fil des eliminations. */
    public static final int FIL_MAX = 5;

    @Nullable
    private static Hud.Bandeau bandeau;
    private static long bandeauRecuMs;
    // etat en match (EtatMatchS2C)
    private static boolean enMatch;
    private static final Deque<Annonces.Annonce> ANNONCES = new ArrayDeque<>();
    private static int compteS = -1;
    private static long compteRecuMs;
    @Nullable
    private static Component compteTitre;
    private static final Deque<KillFeed.Ligne> FIL = new ArrayDeque<>();
    private static final Deque<Long> FIL_MS = new ArrayDeque<>();
    // spectateur
    private static boolean spectateur;
    private static String spectateurCible = "";
    private static int spectateurCouleur, spectateurNombre;
    // choix de classe
    @Nullable
    private static PaquetsMoteur.ChoixOuvrirS2C choix;
    private static long choixRecuMs;
    @Nullable
    private static PaquetsMoteur.ChoixEtatS2C choixEtat;
    private static int versionChoix;

    // ------------------------------------------------------------------ reception
    public static void bandeau(@Nullable Hud.Bandeau b) {
        bandeau = b;
        bandeauRecuMs = Util.getMillis();
    }

    /** Entree ou sortie d'un match (noté aussi dans le noyau : HUD du combat et emotes). */
    public static void etatMatch(boolean en) {
        enMatch = en;
        fr.heraliz.noyau.api.EtatClient.enMatch(en);
    }

    public static void annonce(Annonces.Annonce a) {
        if (ANNONCES.size() >= 4) ANNONCES.pollFirst();
        ANNONCES.addLast(a);
    }

    public static void compte(int secondes, Component titre) {
        compteS = secondes;
        compteTitre = titre;
        compteRecuMs = Util.getMillis();
    }

    public static void killFeed(KillFeed.Ligne l) {
        while (FIL.size() >= FIL_MAX) {
            FIL.pollFirst();
            FIL_MS.pollFirst();
        }
        FIL.addLast(l);
        FIL_MS.addLast(Util.getMillis());
        HudMoteur.sonElimination();
    }

    public static void spectateur(boolean actif, String cible, int couleur, int nombre) {
        spectateur = actif;
        spectateurCible = cible;
        spectateurCouleur = couleur;
        spectateurNombre = nombre;
    }

    public static void choixOuvrir(PaquetsMoteur.ChoixOuvrirS2C c) {
        choix = c;
        choixEtat = null;
        choixRecuMs = Util.getMillis();
        versionChoix++;
        Minecraft.getInstance().setScreen(new ChoixClasseScreen());
    }

    public static void choixEtat(PaquetsMoteur.ChoixEtatS2C e) {
        choixEtat = e;
        versionChoix++;
        if (e.fermer()) {
            choix = null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ChoixClasseScreen) mc.setScreen(null);
        }
    }

    // ------------------------------------------------------------------ lecture (pour le HUD et les ecrans)
    @Nullable
    public static Hud.Bandeau bandeau() {
        return bandeau;
    }

    /** Secondes restantes du bandeau, decomptees localement depuis la reception (-1 = pas de temps). */
    public static int bandeauSecondes() {
        if (bandeau == null || bandeau.secondes() < 0) return -1;
        return Math.max(0, bandeau.secondes() - (int) ((Util.getMillis() - bandeauRecuMs) / 1000));
    }

    /** Vrai si le serveur a signale que le joueur est en match. */
    public static boolean enMatch() {
        return enMatch;
    }

    /** Vrai si la mini-carte et la carte du monde de Xaero doivent etre masquees (en match). */
    /** Annonces en attente (la premiere est celle a afficher). */
    public static Deque<Annonces.Annonce> annonces() {
        return ANNONCES;
    }

    /** Secondes restantes du compte a rebours (-1 = aucun). */
    public static int compteSecondes() {
        if (compteS < 0) return -1;
        int r = compteS - (int) ((Util.getMillis() - compteRecuMs) / 1000);
        return r < 0 ? -1 : r;
    }

    /** Millisecondes ecoulees dans la seconde courante du compte (animation), 0..999. */
    public static int compteFractionMs() {
        return (int) ((Util.getMillis() - compteRecuMs) % 1000);
    }

    /** Millisecondes depuis la fin du compte (pour afficher « COMBAT ! » une seconde), -1 si pas de compte termine. */
    public static long depuisFinCompteMs() {
        if (compteS < 0 || compteTitre == null) return -1;
        long fin = compteRecuMs + (compteS + 1) * 1000L;
        long d = Util.getMillis() - fin;
        return d < 0 ? -1 : d;
    }

    @Nullable
    public static Component compteTitre() {
        return compteTitre;
    }

    /** Fil des eliminations (plus ancienne en tete) et heure de reception de chaque ligne. */
    public static Deque<KillFeed.Ligne> fil() {
        return FIL;
    }

    public static Deque<Long> filMs() {
        return FIL_MS;
    }

    public static boolean spectateur() {
        return spectateur;
    }

    public static String spectateurCible() {
        return spectateurCible;
    }

    public static int spectateurCouleur() {
        return spectateurCouleur;
    }

    public static int spectateurNombre() {
        return spectateurNombre;
    }

    @Nullable
    public static PaquetsMoteur.ChoixOuvrirS2C choix() {
        return choix;
    }

    @Nullable
    public static PaquetsMoteur.ChoixEtatS2C choixEtat() {
        return choixEtat;
    }

    public static int versionChoix() {
        return versionChoix;
    }

    /** Secondes restantes du choix de classe (decompte local). */
    public static float choixRestantS() {
        if (choix == null) return 0;
        return Math.max(0, choix.secondes() - (Util.getMillis() - choixRecuMs) / 1000f);
    }

    /** Vide tout (deconnexion). */
    public static void vider() {
        bandeau = null;
        enMatch = false;
        fr.heraliz.noyau.api.EtatClient.enMatch(false);
        ANNONCES.clear();
        compteS = -1;
        compteTitre = null;
        FIL.clear();
        FIL_MS.clear();
        spectateur = false;
        spectateurCible = "";
        choix = null;
        choixEtat = null;
        HudMoteur.oublier();
    }
}
