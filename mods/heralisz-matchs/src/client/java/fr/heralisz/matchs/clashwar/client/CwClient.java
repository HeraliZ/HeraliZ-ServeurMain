package fr.heralisz.matchs.clashwar.client;

import fr.heralisz.matchs.clashwar.net.CwPaquets;
import fr.heralisz.matchs.clashwar.net.VueTableau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Etat client du ClashWar (recu du serveur) : vue du tableau (mur-ecran, ecran {@code /clashwar tableau}), ecran
 * d'inscription, toasts, zone de mort subite. Chaque changement de la vue incremente {@link #version()} : la texture du
 * tableau ({@link TextureTableau}) n'est redessinee qu'a ce moment-la. Fil client.
 */
public final class CwClient {
    private CwClient() {
    }

    private static VueTableau vue = VueTableau.VIDE;
    private static int version;
    /** Classement des clans (panneau du spawn) et sa version. */
    private static CwPaquets.ClassementS2C classement = new CwPaquets.ClassementS2C(java.util.List.of(), 0, "", 0);
    private static int versionClassement;
    /** Heure locale de reception de la vue (pour les comptes a rebours). */
    private static long recueMs;

    public static VueTableau vue() {
        return vue;
    }

    /** Incremente a chaque changement de la vue. */
    public static int version() {
        return version;
    }

    public static CwPaquets.ClassementS2C classement() {
        return classement;
    }

    /** Incremente a chaque nouveau classement. */
    public static int versionClassement() {
        return versionClassement;
    }

    public static void classement(CwPaquets.ClassementS2C p) {
        classement = p;
        versionClassement++;
    }

    public static long recueMs() {
        return recueMs;
    }

    // ------------------------------------------------------------------ paquets
    public static void tableau(VueTableau v) {
        vue = v;
        version++;
        recueMs = System.currentTimeMillis();
    }

    public static void rencontre(CwPaquets.RencontreS2C p) {
        VueTableau.Rencontre r = vue.rencontre(p.tour(), p.index());
        if (r == null) return;
        r.statut = p.statut();
        r.gagnant = p.gagnant();
        r.vivantsA = p.vivantsA();
        r.totalA = p.totalA();
        r.vivantsB = p.vivantsB();
        r.totalB = p.totalB();
        r.forfait = p.forfait();
        version++;
    }

    public static void inscription(CwPaquets.InscriptionS2C p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranInscription e) {
            e.recevoir(p);
        } else if (p.ouvrir()) {
            mc.setScreen(new EcranInscription(p));
        }
    }

    public static void toast(CwPaquets.ToastS2C p) {
        CwHud.toast(p.titre(), p.texte(), p.couleur());
        jouer(p.son());
    }

    public static void zone(CwPaquets.ZoneS2C p) {
        CwHud.zone(p);
    }

    public static void ouvrir(String ecran) {
        if ("tableau".equals(ecran)) Minecraft.getInstance().setScreen(new EcranTableau());
    }

    /** Joue un son du mod par son id (source interface), rien s'il est inconnu. */
    public static void jouer(@Nullable String id) {
        if (id == null || id.isEmpty()) return;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        SoundEvent s = rl == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(rl);
        if (s != null) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, 1f, 0.9f));
    }

    /** Deconnexion : on oublie tout. */
    static void vider() {
        vue = VueTableau.VIDE;
        version++;
        versionClassement++;
        CwHud.vider();
        HudMatchCw.vider();
        HudPodium.vider();
    }
}
