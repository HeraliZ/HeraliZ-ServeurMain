package com.Spartan.integrity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Clés du Gardien déjà vues (19/11) : un launcher imité qui réutilise sa clé. Le vrai launcher tire une paire de clés
 * NEUVE à chaque lancement du jeu (gardien.js, nouvellesCles) ; le site la lie au jeton du compte. La même clé prouvée par
 * deux comptes, ou pour deux jeux différents (deux numéros de processus), ne vient donc jamais du launcher HeraliZ : c'est
 * un programme qui l'imite avec une clé fixe (l'aléa d'une clé Ed25519 rend toute coïncidence impossible). Une
 * reconnexion du même jeu (même processus) garde sa clé : rien à dire.
 *
 * <p>En mémoire seulement (24 h, 4096 clés au plus, les plus anciennes oubliées). Java pur (testé hors du jeu) ;
 * synchronisé (lié à la réception du jeton et aux rapports, fil du serveur).
 */
public final class GardienCles {
    /** Une clé est oubliée 24 h après son dernier usage. */
    public static final long DUREE_MS = 24L * 3600 * 1000;
    static final int MAX = 4096;

    private record Usage(UUID joueur, String nom, long pid, long vu) {
    }

    private final Map<String, Usage> usages = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Usage> eldest) {
            return size() > MAX;
        }
    };

    /**
     * Le jeton de ce joueur prouve cette clé. @return la raison si un AUTRE compte l'a prouvée dans les 24 h, sinon null
     */
    public synchronized String lier(String cle, UUID joueur, String nom, long maintenant) {
        if (cle == null || cle.isEmpty()) {
            return null;
        }
        Usage u = usages.get(cle);
        if (u != null && maintenant - u.vu() <= DUREE_MS && !u.joueur().equals(joueur)) {
            return "même clé du Gardien que le compte " + u.nom() + " : les deux jeux viennent d'un programme qui imite le"
                    + " launcher avec une clé fixe (le launcher HeraliZ en tire une neuve à chaque lancement)";
        }
        boolean memeJoueur = u != null && u.joueur().equals(joueur) && maintenant - u.vu() <= DUREE_MS;
        usages.put(cle, new Usage(joueur, nom, memeJoueur ? u.pid() : 0, maintenant));
        return null;
    }

    /**
     * Rapport prouvé (signé avec cette clé) pour le jeu {@code pid}. @return la raison si la clé a déjà servi à un AUTRE
     * jeu de ce joueur (autre processus), sinon null
     */
    public synchronized String jeu(String cle, UUID joueur, long pid, long maintenant) {
        if (cle == null || pid <= 0) {
            return null;
        }
        Usage u = usages.get(cle);
        if (u == null || !u.joueur().equals(joueur)) {
            return null; // clé jamais liée à ce joueur : jugée à la réception du jeton
        }
        if (u.pid() != 0 && u.pid() != pid) {
            return "clé du Gardien déjà utilisée pour un autre lancement du jeu (processus " + u.pid() + ", puis " + pid
                    + ") : le launcher HeraliZ en tire une neuve à chaque lancement (launcher imité avec une clé fixe)";
        }
        usages.put(cle, new Usage(joueur, u.nom(), pid, maintenant));
        return null;
    }
}
