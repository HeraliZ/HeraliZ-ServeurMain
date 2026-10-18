package fr.heraliz.quetes.serveur;

import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.entite.Pnj;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arrivée d'un nouveau joueur avec le Commandant Varga (18/10, choix du propriétaire) :
 * <ol>
 *    <li>la cinématique d'arrivée finie (HeraliZ Utils), le suivi à droite dit « Va voir le Commandant Varga pour
 *    commencer le tutoriel », avec la distance jusqu'à lui et des lueurs sur lui quand on est près ;</li>
 *    <li>Varga ne propose alors QUE « Faire ma base » (et « Au revoir ») : ni mission, ni commerce ({@link Dialogues}) ;</li>
 *    <li>le bouton lance le tutoriel des bases de CRac ({@code TutorielApi.lancer}) ; dans le monde des bases, Varga donne
 *    les consignes une par une à la radio (en bas à gauche de l'écran, {@link Reseau.Radio}) et le suivi montre l'étape
 *    en cours de la cabane ;</li>
 *    <li>la cabane finie, Varga le laisse se débrouiller (« reviens me voir pour des missions ») : ses quêtes principales
 *    s'ouvrent comme avant.</li>
 * </ol>
 * Sans CRac (ou sans monde des bases), rien de tout cela : Varga donne ses quêtes comme avant. L'état du tutoriel est lu
 * chez CRac à chaque fois (rien n'est gardé ici, sauf la dernière étape vue, pour ne parler qu'aux changements).
 */
final class Accueil {
    /** Le PNJ qui accueille les nouveaux et guide le tutoriel des bases. */
    static final String PNJ = "varga";
    /** Secondes entre les deux mots de la fin de Varga (le premier pendant le tour de la base par la caméra). */
    private static final int FIN_ECART = 16;

    private record Etat(int tuto, int etape, boolean intro) {
    }

    private static final Map<UUID, Etat> DERNIER = new HashMap<>();
    /** Secondes avant le second mot de la fin. */
    private static final Map<UUID, Integer> FIN = new HashMap<>();
    /** Apparence de l'entité Varga vue en dernier (si son fichier n'en a pas : posée par /pnj apparence varga moi). */
    private static CompoundTag apparenceVue;

    private Accueil() {
    }

    static void oublier(UUID u) {
        DERNIER.remove(u);
        FIN.remove(u);
    }

    /** Le joueur doit d'abord faire le tutoriel des bases : Varga ne propose que « Faire ma base ». Jamais en créatif. */
    static boolean tutoAFaire(ServerPlayer p) {
        return !p.isCreative() && !p.isSpectator() && Ponts.tutoEtat(p) == 0;
    }

    /** « Va voir le Commandant Varga » : tutoriel à faire, cinématique finie, en survie. */
    private static boolean intro(ServerPlayer p, int tuto) {
        return tuto == 0 && !p.isCreative() && !p.isSpectator() && Ponts.cinematiqueFinie(p);
    }

    /** Bouton « Faire ma base » du dialogue de Varga. */
    static void lancer(ServerPlayer p) {
        if (Ponts.tutoLancer(p)) {
            Moteur.suiviBientot(p);
        }
    }

    /** Chaque seconde (ServeurQuetes) : suivi à jour pendant l'arrivée et le tutoriel, radio quand l'étape change. */
    static void seconde(ServerPlayer p) {
        int tuto = Ponts.tutoEtat(p);
        int etapes = Ponts.tutoEtapes();
        Integer fin = FIN.get(p.getUUID());
        if (fin != null) {
            if (fin <= 1) {
                FIN.remove(p.getUUID());
                radio(p, 0, "heraliz_quetes.tuto.radio.fin2");
                Moteur.notif(p, Texte.cle("heraliz_quetes.tuto.notif_fin"), 0x5FD08A);
            } else {
                FIN.put(p.getUUID(), fin - 1);
            }
        }
        if (tuto < 0) {
            DERNIER.remove(p.getUUID());
            return;
        }
        boolean intro = intro(p, tuto);
        int etape = tuto == 2 ? Ponts.tutoProgression(p)[0] : 0;
        Etat avant = DERNIER.put(p.getUUID(), new Etat(tuto, etape, intro));
        boolean guide = intro || tuto == 1 || tuto == 2;
        if (guide || avant != null && (avant.intro() || avant.tuto() == 1 || avant.tuto() == 2)) {
            Moteur.suiviBientot(p);   // distance et compteurs à jour, ou retour au suivi des quêtes
        }
        if (tuto == 2 && etape >= 1 && etape <= etapes && (avant == null || avant.tuto() != 2 || avant.etape() != etape)) {
            radio(p, etape, "heraliz_quetes.tuto.radio." + etape);
        } else if (tuto == 3 && avant != null && avant.tuto() == 2) {
            // le premier mot de la fin pendant que la caméra fait le tour de la base (CRac), le second après
            radio(p, 0, "heraliz_quetes.tuto.radio.fin", Texte.brut(Ponts.tutoJours()));
            FIN.put(p.getUUID(), FIN_ECART);
        } else if (tuto != 2 && avant != null && avant.tuto() == 2) {
            // tutoriel arrêté autrement (staff : /tuto finir, rejouer) : la radio se range
            Reseau.envoyer(p, new Reseau.Radio(Texte.VIDE, new CompoundTag(), "", false, 0, etapes, Texte.VIDE));
        }
    }

    /** Message radio de Varga : la consigne de l'étape (1 à 9), ou un mot de la fin (0). */
    private static void radio(ServerPlayer p, int etape, String cle, Texte... args) {
        Defs.Pnj def = Catalogue.pnj(PNJ);
        Texte nom = def != null && !def.nom.vide() ? def.nom : Texte.cle("heraliz_quetes.tuto.varga_nom");
        CompoundTag app = def != null && def.apparence != null ? def.apparence : apparenceVue != null ? apparenceVue : new CompoundTag();
        Reseau.envoyer(p, new Reseau.Radio(nom, app, def == null ? "" : def.skin, def != null && def.brasFins, etape,
                Ponts.tutoEtapes(), Texte.cle(cle, args)));
    }

    /** Le suivi à droite pendant l'arrivée et le tutoriel ; null le reste du temps (la quête suivie, comme avant). */
    static Reseau.Suivi suivi(ServerPlayer p) {
        int tuto = Ponts.tutoEtat(p);
        if (intro(p, tuto)) {
            Defs.Pnj def = Catalogue.pnj(PNJ);
            Texte nom = def != null && !def.nom.vide() ? def.nom : Texte.cle("heraliz_quetes.tuto.varga_nom");
            Pnj v = varga(p);
            int d = v == null ? -1 : (int) Math.sqrt(v.distanceToSqr(p));
            return new Reseau.Suivi(nom, Texte.cle("heraliz_quetes.tuto.intro_titre"),
                    List.of(new Reseau.SLigne(Texte.cle("heraliz_quetes.tuto.intro"), 0, 0, d, false, 1)));
        }
        Texte entete = Texte.cle("heraliz_quetes.tuto.suivi_pnj");
        Texte titre = Texte.cle("heraliz_quetes.tuto.titre");
        if (tuto == 1) {
            return new Reseau.Suivi(entete, titre, List.of(new Reseau.SLigne(Texte.cle("heraliz_quetes.tuto.kit"), 0, 0, -1, false, 1)));
        }
        if (tuto != 2) return null;
        int[] pr = Ponts.tutoProgression(p);
        if (pr[0] < 1) {
            return new Reseau.Suivi(entete, titre, List.of(new Reseau.SLigne(Texte.cle("heraliz_quetes.tuto.retour"), 0, 0, -1, false, 1)));
        }
        List<Reseau.SLigne> lignes = new ArrayList<>();
        int etapes = Ponts.tutoEtapes();
        int e = Math.min(pr[0], etapes);
        // l'étape d'avant (faite), puis celle en cours
        if (e > 1) lignes.add(new Reseau.SLigne(Texte.cle("heraliz_quetes.tuto.etape." + (e - 1)), 0, 0, -1, false, 2));
        lignes.add(new Reseau.SLigne(Texte.cle("heraliz_quetes.tuto.etape." + e), pr[1], pr[2], -1, false, pr[0] > etapes ? 2 : 1));
        return new Reseau.Suivi(entete, titre, lignes);
    }

    /** Lueurs sur Varga quand on doit aller le voir et qu'il est près. */
    static void lueurs(ServerPlayer p, List<BlockPos> l) {
        if (!intro(p, Ponts.tutoEtat(p))) return;
        Pnj v = varga(p);
        if (v != null && v.distanceToSqr(p) < 48 * 48) l.add(v.blockPosition().above(2));
    }

    /** Le Commandant Varga le plus proche dans le monde du joueur (chargé), ou null. */
    private static Pnj varga(ServerPlayer p) {
        Pnj best = null;
        double bd = Double.MAX_VALUE;
        for (Pnj e : p.serverLevel().getEntities(HeralizQuetes.PNJ.get(), x -> PNJ.equals(x.pnjId()))) {
            double d = e.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best != null && !best.apparence().isEmpty()) apparenceVue = best.apparence().copy();
        return best;
    }
}
