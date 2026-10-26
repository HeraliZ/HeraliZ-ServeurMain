package fr.heralisz.clans.client;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.model.ReglagesPerms;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import net.minecraft.client.Minecraft;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Permissions modifiees a l'ecran et pas encore confirmees par le serveur (grades et reglages par joueur).
 * L'affichage est optimiste : un clic se voit tout de suite, par-dessus le snapshot. Les envois sont groupes (une
 * rafale de clics sur un meme grade ou un meme membre = un paquet, 300 ms apres le dernier clic) et espaces de 300 ms
 * (la cadence serveur est d'une action par 200 ms : plus de « trop rapide » en cliquant vite). Une entree envoyee
 * disparait quand le snapshot confirme la valeur, ou 3 s apres l'envoi (refus du serveur : l'affichage revient a
 * l'etat serveur, le message d'erreur est deja parti). Tic client : une verification de liste vide quand rien n'attend.
 * Hors connexion (banc d'essai, etat factice), l'envoi est applique directement a l'etat factice.
 */
public final class PermsLocales {
    private PermsLocales() {
    }

    private static final long DELAI_MS = 300, ESPACE_MS = 300, EXPIRATION_MS = 3000;

    /** Masque voulu pour un grade. */
    private static final class AttenteGrade {
        int voulu;
        long modifie, envoye;
        boolean dejaEnvoye;
    }

    /** Etats voulus pour les permissions touchees d'un membre. */
    private static final class AttenteMembre {
        int touche;
        ReglagesPerms voulu = ReglagesPerms.AUCUN;
        long modifie, envoye;
        boolean dejaEnvoye;
    }

    private static final EnumMap<Grade, AttenteGrade> GRADES = new EnumMap<>(Grade.class);
    private static final Map<UUID, AttenteMembre> MEMBRES = new LinkedHashMap<>();
    private static long dernierEnvoi;

    // ------------------------------------------------------------------ lecture (snapshot + modifications en attente)
    /** Permissions affichees d'un grade : celles du snapshot, ou la valeur voulue en attente. */
    public static EnumSet<Perm> permsGrade(ClanSnapshot s, Grade g) {
        AttenteGrade a = GRADES.get(g);
        return a == null ? s.permsDe(g) : Perm.depuisMasque(a.voulu);
    }

    /** Masque affiche d'un grade (sans allocation : rendu image par image). */
    public static int masqueGrade(ClanSnapshot s, Grade g) {
        AttenteGrade a = GRADES.get(g);
        if (a != null) return a.voulu;
        Integer m = s.perms().get(g);
        return m == null ? Perm.masque(g.permsParDefaut()) : m;
    }

    /** Reglages affiches d'un membre : ceux du snapshot, corriges par les etats voulus en attente. */
    public static ReglagesPerms reglages(ClanSnapshot s, UUID membre) {
        ReglagesPerms r = s.reglagesDe(membre);
        AttenteMembre a = MEMBRES.get(membre);
        if (a == null) return r;
        int au = (r.autorise() & ~a.touche) | (a.voulu.autorise() & a.touche);
        int re = (r.refuse() & ~a.touche) | (a.voulu.refuse() & a.touche);
        return new ReglagesPerms(au, re);
    }

    /** Vrai si des modifications de ce membre attendent la confirmation du serveur. */
    public static boolean enAttente(UUID membre) {
        return MEMBRES.containsKey(membre);
    }

    // ------------------------------------------------------------------ ecriture (clics)
    /** Bascule une permission d'un grade (chef). */
    public static void basculerGrade(ClanSnapshot s, Grade g, Perm p) {
        int courant = Perm.masque(permsGrade(s, g));
        AttenteGrade a = GRADES.computeIfAbsent(g, k -> new AttenteGrade());
        a.voulu = courant ^ p.bit();
        a.modifie = System.currentTimeMillis();
        if (a.envoye > 0) a.dejaEnvoye = true;
        a.envoye = 0;
    }

    /** Fixe l'etat voulu d'une permission d'un membre. */
    public static void definir(ClanSnapshot s, UUID membre, Perm p, Perm.Etat e) {
        AttenteMembre a = MEMBRES.computeIfAbsent(membre, k -> new AttenteMembre());
        a.touche |= p.bit();
        a.voulu = a.voulu.avec(p, e);
        a.modifie = System.currentTimeMillis();
        if (a.envoye > 0) a.dejaEnvoye = true;
        a.envoye = 0;
    }

    /** Oublie tout (deconnexion, plus de clan). */
    public static void vider() {
        GRADES.clear();
        MEMBRES.clear();
    }

    // ------------------------------------------------------------------ tic client (envois, confirmations, expirations)
    public static void tick() {
        if (GRADES.isEmpty() && MEMBRES.isEmpty()) return;
        ClanSnapshot s = ClientClans.snapshot();
        if (s == null) {
            vider();
            return;
        }
        long now = System.currentTimeMillis();
        boolean peutEnvoyer = now - dernierEnvoi >= ESPACE_MS;
        for (Iterator<Map.Entry<Grade, AttenteGrade>> it = GRADES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Grade, AttenteGrade> e = it.next();
            AttenteGrade a = e.getValue();
            boolean confirme = Perm.masque(s.permsDe(e.getKey())) == a.voulu;
            if (a.envoye > 0) {
                if (confirme || now - a.envoye > EXPIRATION_MS) it.remove();
                continue;
            }
            if (now - a.modifie < DELAI_MS) continue;
            if (confirme && !a.dejaEnvoye) {
                it.remove(); // revenu a la valeur du serveur avant tout envoi : rien a dire
                continue;
            }
            if (!peutEnvoyer) continue;
            envoyer(new Packets.PermsC2S(e.getKey(), a.voulu));
            a.envoye = now;
            peutEnvoyer = false;
        }
        for (Iterator<Map.Entry<UUID, AttenteMembre>> it = MEMBRES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, AttenteMembre> e = it.next();
            AttenteMembre a = e.getValue();
            ReglagesPerms r = s.reglagesDe(e.getKey());
            boolean confirme = (r.autorise() & a.touche) == (a.voulu.autorise() & a.touche)
                    && (r.refuse() & a.touche) == (a.voulu.refuse() & a.touche);
            if (s.membre(e.getKey()) == null) {
                it.remove(); // parti du clan
                continue;
            }
            if (a.envoye > 0) {
                if (confirme || now - a.envoye > EXPIRATION_MS) it.remove();
                continue;
            }
            if (now - a.modifie < DELAI_MS) continue;
            if (confirme && !a.dejaEnvoye) {
                it.remove();
                continue;
            }
            if (!peutEnvoyer) continue;
            envoyer(new Packets.PermsJoueurC2S(e.getKey(), a.touche, a.voulu.autorise() & a.touche, a.voulu.refuse() & a.touche));
            a.envoye = now;
            peutEnvoyer = false;
        }
    }

    private static void envoyer(Object paquet) {
        dernierEnvoi = System.currentTimeMillis();
        if (Minecraft.getInstance().getConnection() != null && !ClientClans.estFaux()) {
            ClansNet.toServer(paquet);
            return;
        }
        // banc d'essai : l'etat factice joue le role du serveur (l'editeur n'a envoye que des reglages permis)
        HeraliszClans.LOG.info("[clans] (etat factice) applique : {}", paquet);
        if (paquet instanceof Packets.PermsC2S p) ClientClans.fauxPermsGrade(p.grade(), p.masque());
        else if (paquet instanceof Packets.PermsJoueurC2S p) ClientClans.fauxReglages(p.membre(), p.masque(), p.autorise(), p.refuse());
    }
}
