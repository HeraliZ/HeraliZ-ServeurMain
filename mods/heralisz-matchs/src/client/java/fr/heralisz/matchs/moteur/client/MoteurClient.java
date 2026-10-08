package fr.heralisz.matchs.moteur.client;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.dev.MatchsHarness;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Hud;
import fr.heralisz.matchs.moteur.api.KillFeed;
import fr.heralisz.matchs.moteur.net.PaquetsMoteur;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Etat client du moteur (recu par {@code PaquetsMoteur}) : bandeau, tag de combat (secondes et duree pleine pour la
 * jauge), annonces, compte a rebours, fil des eliminations, spectateur, groupe et invitations, choix de classe, etat
 * « en match » (cartes de Xaero masquees par {@link CartesMasquees}). Dessine par {@link HudMoteur} ; ouvre
 * {@link ChoixClasseScreen} et {@link GroupeScreen}. Fil client.
 */
public final class MoteurClient {
    private MoteurClient() {
    }

    /** Lignes gardees dans le fil des eliminations. */
    public static final int FIL_MAX = 5;

    @Nullable
    private static Hud.Bandeau bandeau;
    private static long bandeauRecuMs;
    private static int combatS;
    private static long combatRecuMs;
    /** Duree pleine du tag en cours (s) : la plus grande valeur recue depuis son debut (jauge). */
    private static int combatTotalS;
    // etat en match (EtatMatchS2C)
    private static boolean enMatch, cartesMasquees;
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
    // groupe
    @Nullable
    private static PaquetsMoteur.GroupeS2C groupe;
    private static int versionGroupe;
    /** Invitations affichees en toast : de, secondes, recu (ms). */
    public record Toast(String de, int secondes, long recuMs) {
    }

    private static final Deque<Toast> TOASTS = new ArrayDeque<>();
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

    public static void combat(int secondes) {
        boolean actif = combatRestantMs() > 0;
        if (secondes <= 0) combatTotalS = 0;
        else combatTotalS = actif ? Math.max(combatTotalS, secondes) : secondes;
        combatS = Math.max(0, secondes);
        combatRecuMs = Util.getMillis();
    }

    /** Entree ou sortie d'un match : cartes de Xaero masquees ou rendues ({@link CartesMasquees}). */
    public static void etatMatch(boolean en, boolean cartes) {
        enMatch = en;
        cartesMasquees = en && cartes;
        if (en) combat(0);
        HeraliszMatchs.LOG.debug("[matchs] client : en match {}, cartes masquees {}", en, cartesMasquees);
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

    public static void groupe(PaquetsMoteur.GroupeS2C g) {
        groupe = g;
        versionGroupe++;
        Minecraft mc = Minecraft.getInstance();
        if (g.ouvrir() && !(mc.screen instanceof GroupeScreen)) mc.setScreen(new GroupeScreen());
    }

    public static void invitation(String de, int secondes) {
        while (TOASTS.size() >= 3) TOASTS.pollFirst();
        TOASTS.addLast(new Toast(de, secondes, Util.getMillis()));
        HudMoteur.sonInvitation();
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

    /** Secondes restantes du tag de combat (0 = aucun). */
    public static int combatSecondes() {
        if (combatS <= 0) return 0;
        return Math.max(0, combatS - (int) ((Util.getMillis() - combatRecuMs) / 1000));
    }

    /** Millisecondes restantes du tag de combat (0 = aucun), decomptees localement. */
    public static long combatRestantMs() {
        if (combatS <= 0) return 0;
        return Math.max(0, combatS * 1000L - (Util.getMillis() - combatRecuMs));
    }

    /** Part restante du tag (1 = tout juste tague, 0 = fini) pour la jauge. */
    public static float combatRatio() {
        if (combatTotalS <= 0) return 0;
        return Math.min(1f, combatRestantMs() / (combatTotalS * 1000f));
    }

    /** Vrai si le serveur a signale que le joueur est en match. */
    public static boolean enMatch() {
        return enMatch;
    }

    /** Vrai si la mini-carte et la carte du monde de Xaero doivent etre masquees (en match). */
    public static boolean cartesMasquees() {
        return cartesMasquees;
    }

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
    public static PaquetsMoteur.GroupeS2C groupe() {
        return groupe;
    }

    public static int versionGroupe() {
        return versionGroupe;
    }

    public static Deque<Toast> toasts() {
        return TOASTS;
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
        combatS = 0;
        combatTotalS = 0;
        enMatch = false;
        cartesMasquees = false;
        ANNONCES.clear();
        compteS = -1;
        compteTitre = null;
        FIL.clear();
        FIL_MS.clear();
        spectateur = false;
        spectateurCible = "";
        groupe = null;
        TOASTS.clear();
        choix = null;
        choixEtat = null;
        HudMoteur.oublier();
    }

    // ------------------------------------------------------------------ banc d'essai
    /**
     * Etapes du banc propres au moteur :
     * <ul>
     *     <li>{"moteurFaux": "bandeau|annonce|killfeed|combat|compte|spectateur|invitation|aucun"} : etat client factice ;</li>
     *     <li>{"moteurInventaire": true} : journalise l'inventaire vu par le client (synchronisation apres Coffre) ;</li>
     *     <li>{"moteurEcran": true} : journalise l'ecran ouvert et l'etat client (choix, groupe, spectateur).</li>
     * </ul>
     */
    public static void etapesBanc(MatchsHarness.Registre r) {
        r.ajouter("moteurFaux", (mc, valeur, etape) -> {
            String quoi = valeur.getAsString();
            switch (quoi) {
                case "bandeau" -> bandeau(new Hud.Bandeau(Component.literal("CLASHWAR · QUART DE FINALE"), 214,
                        List.of(new Hud.Pastille("NOVA", 0x6FA8FF, 3, 4), new Hud.Pastille("RVN", 0xE85D5D, 2, 4)), Component.literal("0 — 0")));
                case "annonce" -> annonce(new Annonces.Annonce(Component.literal("FINALE"), Component.literal("[NOVA] contre [RVN]"),
                        0xE8C36A, "", 100, Annonces.Style.FINALE));
                case "killfeed" -> killFeed(new KillFeed.Ligne("Vortex", 0x6FA8FF, "Dziga", 0xE85D5D, "AK-47", true));
                case "combat" -> combat(12);
                case "compte" -> compte(10, Component.literal("PREPARATION"));
                case "spectateur" -> spectateur(true, "Vortex", 0x6FA8FF, 2);
                case "invitation" -> invitation("Kayra", 60);
                default -> vider();
            }
            HeraliszMatchs.LOG.info("[matchs] moteurFaux : {}", quoi);
        });
        r.ajouter("moteurInventaire", (mc, valeur, etape) -> {
            if (mc.player == null) return;
            int n = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                ItemStack st = mc.player.getInventory().getItem(i);
                if (st.isEmpty()) continue;
                n++;
                if (sb.length() < 400) sb.append(i).append('=').append(st.getCount()).append('x').append(st.getItem()).append(' ');
            }
            HeraliszMatchs.LOG.info("[matchs] client inventaire : {} piles, mode {}, sante {}, effets {} : {}", n,
                    mc.gameMode == null ? "?" : mc.gameMode.getPlayerMode(), mc.player.getHealth(), mc.player.getActiveEffects().size(), sb);
        });
        r.ajouter("moteurEcran", (mc, valeur, etape) -> {
            HudMoteur.journal();
            HeraliszMatchs.LOG.info("[matchs] client ecran {} ; choix {} ; groupe {} membres ; spectateur {} '{}' ; toasts {}",
                mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName(), choix == null ? "-" : choix.kits().size() + " kits",
                groupe == null ? 0 : groupe.membres().size(), spectateur, spectateurCible, TOASTS.size());
        });
    }
}
