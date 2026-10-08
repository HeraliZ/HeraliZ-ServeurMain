package fr.heralisz.bases.client;

import fr.heralisz.bases.model.BaseVue;
import fr.heralisz.bases.model.FenetreVue;
import fr.heralisz.bases.model.PillableVue;
import fr.heralisz.bases.model.Plan;
import fr.heralisz.bases.network.Packets;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Etat client du mod, rempli par {@link ClientHandlers} (paquets du serveur) et lu par les ecrans et le HUD (agent
 * interfaces). Chaque partie a sa version : un ecran ne se reconstruit que si la version de ce qu'il affiche change.
 * Les heures sont celles du client a la reception ({@link System#currentTimeMillis()}).
 */
public final class ClientBases {
    private ClientBases() {
    }

    /** Etat factice pose par le banc (dev/FauxBases) : les ecrans ne redemandent alors rien au serveur. */
    private static boolean faux;

    public static boolean estFaux() {
        return faux;
    }

    public static void setFaux(boolean f) {
        faux = f;
    }

    // ---- etat de ma base (EtatBaseS2C)
    @Nullable
    private static BaseVue etat;
    private static int versionEtat;
    private static long etatRecuMs;

    // ---- plan du bunker (PlanS2C)
    @Nullable
    private static Plan plan;
    private static int versionPlan;

    // ---- liste PILLER (PillablesS2C)
    private static List<PillableVue> pillables = List.of();
    @Nullable
    private static FenetreVue fenetre;
    private static int versionPillables;
    private static long pillablesRecuMs;

    // ---- HUD de base (HudS2C)
    private static Packets.HudS2C hud = Packets.HudS2C.AUCUN;
    private static int versionHud;

    // ---- teleportation en attente (TeleportS2C DEBUT ; null sinon)
    @Nullable
    private static Packets.TeleportS2C teleport;
    private static long teleportDebutMs;

    @Nullable
    public static BaseVue etat() {
        return etat;
    }

    public static int versionEtat() {
        return versionEtat;
    }

    public static long etatRecuMs() {
        return etatRecuMs;
    }

    @Nullable
    public static Plan plan() {
        return plan;
    }

    public static int versionPlan() {
        return versionPlan;
    }

    public static List<PillableVue> pillables() {
        return pillables;
    }

    /** Fenetre recue avec la liste PILLER (ou, a defaut, avec l'etat de ma base). */
    @Nullable
    public static FenetreVue fenetre() {
        return fenetre != null ? fenetre : etat != null ? etat.fenetre() : null;
    }

    public static int versionPillables() {
        return versionPillables;
    }

    public static long pillablesRecuMs() {
        return pillablesRecuMs;
    }

    public static Packets.HudS2C hud() {
        return hud;
    }

    public static int versionHud() {
        return versionHud;
    }

    /** Teleportation en attente (null sinon). */
    @Nullable
    public static Packets.TeleportS2C teleport() {
        return teleport;
    }

    /** Fraction ecoulee de la teleportation en attente (0..1), 0 sans attente. */
    public static float teleportProgression() {
        if (teleport == null || teleport.totalTicks() <= 0) return 0f;
        float ms = System.currentTimeMillis() - teleportDebutMs;
        return Math.min(1f, ms / (teleport.totalTicks() * 50f));
    }

    /** Secondes restantes de la teleportation en attente (arrondies au-dessus). */
    public static int teleportRestantS() {
        if (teleport == null) return 0;
        long reste = teleport.totalTicks() * 50L - (System.currentTimeMillis() - teleportDebutMs);
        return (int) Math.max(0, (reste + 999) / 1000);
    }

    // ------------------------------------------------------------------ ecritures (ClientHandlers, banc)
    public static void setEtat(@Nullable BaseVue v) {
        etat = v;
        etatRecuMs = System.currentTimeMillis();
        versionEtat++;
    }

    public static void setPlan(@Nullable Plan p) {
        plan = p;
        versionPlan++;
    }

    public static void setPillables(List<PillableVue> l, FenetreVue f) {
        pillables = List.copyOf(l);
        fenetre = f;
        pillablesRecuMs = System.currentTimeMillis();
        versionPillables++;
    }

    public static void setHud(Packets.HudS2C h) {
        hud = h == null ? Packets.HudS2C.AUCUN : h;
        versionHud++;
    }

    public static void setTeleport(Packets.TeleportS2C t) {
        if (t == null || t.etat() != Packets.TeleportS2C.Etat.DEBUT) {
            teleport = null;
        } else {
            teleport = t;
            teleportDebutMs = System.currentTimeMillis();
        }
    }

    /** Deconnexion : tout est oublie. */
    public static void vider() {
        etat = null;
        plan = null;
        pillables = List.of();
        fenetre = null;
        hud = Packets.HudS2C.AUCUN;
        teleport = null;
        versionEtat++;
        versionPlan++;
        versionPillables++;
        versionHud++;
    }
}
