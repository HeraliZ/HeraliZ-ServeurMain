package fr.heraliz.reanimation.client.hud;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.noyau.client.kit.Anim;
import fr.heraliz.noyau.client.kit.AnneauProgression;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.Palette;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.TexteHud;
import fr.heraliz.reanimation.client.ClientDowned;
import fr.heraliz.reanimation.network.SyncPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

/**
 * HUD de celui qui réanime (refonte du 25/10, docs/HUD_REFONTE.md, maquettes {@code maquettes/hud_refonte/reanimation_*})
 * : le même anneau que les soins autour du réticule ({@link AnneauProgression}), le temps restant au centre, « Réanimation
 * de PSEUDO » dessous, puis « Bouger annule ». La progression est celle que prévoit le jeu du joueur, recalée sur le
 * serveur en cours de route ({@link ClientDowned}) ; arrivée à 100 % avant la réponse du serveur, l'anneau passe en attente
 * (arc clair qui tourne, « Confirmation… ») au lieu de rester figé. Réponse du serveur (paquet de la victime,
 * {@link SyncPacket#EVENT_REVIVED} ou {@link SyncPacket#EVENT_INTERRUPTED}) : coche verte
 * {@value AnneauProgression#REUSSITE_MS} ms, ou anneau rouge qui se vide {@value AnneauProgression#INTERRUPTION_MS} ms.
 * Le temps restant reste au centre jusqu'à cette réponse (27/10, demande du propriétaire : il était caché sous 2 s alors
 * que la réanimation n'était pas finie), et disparaît avec elle (finie, interrompue, annulée). Rien ne change côté serveur
 * (durée, distance, interruptions).
 */
public final class HudReanimateur {
    private HudReanimateur() {
    }

    /** Lignes sous l'anneau (milieu des capitales, sous le centre de l'anneau), largeur maximale d'une ligne. */
    static final float LIGNE1_CY = 22f, LIGNE2_CY = 32f, LIGNE_MAX = 150f;
    /** Réponse du serveur attendue au plus ce temps après la fin chez le joueur (ms) ; ensuite le HUD s'efface. */
    static final int VERDICT_MS = 1500;

    private static final AnneauProgression ANNEAU = new AnneauProgression();
    private static final Anim.Fondu FONDU = new Anim.Fondu();
    private static final TexteHud NOM = new TexteHud(PoliceHud.TEXTE);
    private static final TexteHud LIGNE2 = new TexteHud(PoliceHud.MINI);
    private static final TexteHud CENTRE = new TexteHud(PoliceHud.CHIFFRE_PETIT);

    /** Joueur réanimé (identifiant d'entité) ; fin de la réanimation chez le joueur en attente de la réponse du serveur. */
    private static int partenaire = -1;
    private static boolean verdictAttendu;
    private static long finAction;
    /** Clés des textes en cache. */
    private static String langue;
    private static int partenaireNom = Integer.MIN_VALUE, etatLigne2 = -1, dixiemes = -1;
    private static boolean centre;

    /**
     * Paquet d'état reçu (ClientDowned.receive) : fin de la réanimation chez le joueur, puis verdict du serveur sur la
     * victime (relevée ou interrompue).
     */
    public static void onSync(Entity e, int ancien, int nouveau, int evenement) {
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player) {
            if ((ancien & 16) != 0 && (nouveau & 16) == 0) {
                verdictAttendu = true;
                finAction = Util.getMillis();
            }
            return;
        }
        if (e.getId() != partenaire || (!verdictAttendu && (ClientDowned.localFlags() & 16) == 0)) return;
        if (evenement == SyncPacket.EVENT_REVIVED) {
            ANNEAU.reussir(Palette.OK);
            verdictAttendu = false;
        } else if (evenement == SyncPacket.EVENT_INTERRUPTED) {
            ANNEAU.interrompre();
            verdictAttendu = false;
        }
    }

    /** Le réticule est remplacé par l'anneau (ClientEvents). */
    public static boolean masqueReticule() {
        return FONDU.visible();
    }

    /** Appelé à chaque image par DownedHud. */
    public static void rendre(Minecraft mc, GuiGraphics g, ClientDowned.Info me, float pt, int w, int h) {
        boolean aTerre = me != null && me.has(1);
        boolean reanime = me != null && me.has(16) && !aTerre && !me.has(128) && !me.has(256);
        float restant = 0f;
        if (reanime) {
            if (me.partner != partenaire) ANNEAU.arreter();
            partenaire = me.partner;
            verdictAttendu = false;
            float p = me.reviveProgress(pt);
            // temps restant d'après le compteur recalé sur le serveur ; 0 en attente de sa réponse
            restant = Math.max(0f, (me.reviveMax - me.reviveTicks(pt)) / 20f);
            if (p >= 1f) {
                ANNEAU.attente(Palette.OK);
            } else {
                ANNEAU.normal(p, Palette.OK);
            }
        } else if (verdictAttendu) {
            if (Util.getMillis() - finAction > VERDICT_MS) {
                verdictAttendu = false;
            } else if (ANNEAU.etat() == AnneauProgression.Etat.NORMAL) {
                // fin reçue, verdict du serveur pas encore là : l'anneau tourne au lieu de rester figé
                ANNEAU.attente(Palette.OK);
            }
        }
        boolean visible = !aTerre && (reanime || verdictAttendu || ANNEAU.enFin());
        float a = FONDU.maj(visible);
        if (!FONDU.visible()) {
            ANNEAU.arreter();
            return;
        }
        if (a <= 0f || mc.options.hideGui) return;
        textes(mc, reanime, restant);

        float cx = w / 2f, cy = h / 2f;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = Formes.tampon(g);
        ANNEAU.ajouter(vc, m, cx, cy, a);
        g.flush();

        if (centre) CENTRE.ajouter(g, cx, cy, Palette.argb(Palette.TEXTE, a), TexteHud.CENTRE, true);
        NOM.ajouter(g, cx, cy + LIGNE1_CY, Palette.argb(Palette.TEXTE, a), TexteHud.CENTRE, true);
        int c2 = switch (etatLigne2) {
            case 2 -> Palette.OK;
            case 3 -> Palette.clair(Palette.DANGER, 0.3f);
            default -> Palette.ATTENUE;
        };
        LIGNE2.ajouter(g, cx, cy + LIGNE2_CY, Palette.argb(c2, a), TexteHud.CENTRE, true);
        g.flush();
    }

    /** Textes recalculés seulement si la langue, le joueur réanimé, l'état ou le dixième de seconde affiché changent. */
    private static void textes(Minecraft mc, boolean reanime, float restant) {
        String l = mc.getLanguageManager().getSelected();
        boolean nouvelleLangue = !l.equals(langue);
        langue = l;
        if (nouvelleLangue || partenaire != partenaireNom) {
            partenaireNom = partenaire;
            Entity p = partenaire >= 0 && mc.level != null ? mc.level.getEntity(partenaire) : null;
            String pseudo = p != null ? p.getName().getString() : "?";
            NOM.composant(Component.translatable("hud.reanimation.reviving",
                    Component.literal(pseudo).withStyle(st -> st.withFont(PoliceHud.TEXTE_GRAS.id))));
        }
        AnneauProgression.Etat e = ANNEAU.etat();
        int etat = e == AnneauProgression.Etat.ATTENTE ? 1 : e == AnneauProgression.Etat.REUSSI ? 2 : e == AnneauProgression.Etat.INTERROMPU ? 3 : 0;
        // anneau éteint (fondu de sortie) : la dernière ligne reste
        if (e != AnneauProgression.Etat.RIEN && (etat != etatLigne2 || nouvelleLangue)) {
            etatLigne2 = etat;
            LIGNE2.texte(Language.getInstance().getOrDefault(switch (etat) {
                case 1 -> "hud.reanimation.attente";
                case 2 -> "hud.reanimation.reussie";
                case 3 -> "hud.reanimation.interrompue";
                default -> "hud.reanimation.move_to_cancel";
            }));
        }
        // temps restant au centre, au dixième de seconde (virgule en français et en russe), jusqu'à la fin réelle : tant que
        // le serveur dit que la réanimation est en cours (anneau normal ou en attente de sa réponse) ; retiré dès qu'elle
        // est finie, interrompue ou annulée (coche, anneau rouge, fondu de sortie). Avant le 27/10 : caché sous 2 s.
        centre = reanime && (e == AnneauProgression.Etat.NORMAL || e == AnneauProgression.Etat.ATTENTE);
        if (centre) {
            int d = (int) Math.ceil(restant * 10f);
            if (d != dixiemes || nouvelleLangue) {
                dixiemes = d;
                CENTRE.texte(d / 10 + (virgule(l) ? "," : ".") + d % 10);
            }
        }
    }

    static boolean virgule(String langue) {
        return langue.startsWith("fr") || langue.startsWith("ru");
    }
}
