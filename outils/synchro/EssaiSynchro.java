import fr.heraliz.grades.reseau.Synchro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Essai hors du jeu des listes envoyées par différences (Synchro, 21/11), lancé par outils/verif_synchro.py : des
 * centaines de milliers d'arrivées, départs, changements de grade ou de tag, joueurs qui se cachent (vanish) et staff qui
 * entre ou sort de sa vue, avec ou sans paquets perdus. Le serveur est fait comme TabServeur (deux vues : le staff voit
 * tout, les autres pas les cachés) ; chaque joueur simulé applique ce qu'il reçoit comme TabHeraliz. Vérifie :
 * 1. sans perte, la liste de chaque joueur est TOUJOURS exactement celle de sa vue, sans aucune demande de liste complète ;
 * 2. un joueur hors du staff ne reçoit JAMAIS une ligne cachée (ni dans une liste complète, ni dans une différence) ;
 * 3. avec des pertes, une liste fausse est repérée à la différence suivante, redemandée et réparée : après une période
 *    sans perte, tout le monde a de nouveau la bonne liste ;
 * 4. chaque arrivée ne coûte qu'une liste complète (au joueur qui arrive) et une petite différence aux autres.
 * Code de sortie 1 au moindre écart.
 */
public final class EssaiSynchro {
    private EssaiSynchro() {
    }

    /** Comme ReseauGrades.LigneTab : une ligne du tab. */
    record Ligne(UUID joueur, String grade, String clan, int couleur, String surnom, boolean cache) {
    }

    /** Ce que reçoit un joueur : liste complète, ou différence. */
    record Message(boolean complet, int vue, int version, Map<UUID, Ligne> table, List<UUID> retires, Map<UUID, Ligne> changes) {
    }

    /** Un joueur simulé : comme TabHeraliz (table, suite des numéros). */
    static final class Client {
        final UUID id;
        final Map<UUID, Ligne> table = new HashMap<>();
        final Synchro.Suite suite = new Synchro.Suite();
        final ArrayDeque<Message> boite = new ArrayDeque<>();
        int vuePubliee = -1;
        boolean perte;
        int recus;

        Client(UUID id) {
            this.id = id;
        }
    }

    private static final String[] GRADES = {"survivant", "rescape", "traqueur", "roi", "guide", "moderateur", "admin"};
    private static final String[] CLANS = {"", "", "ZK", "NOVA", "WOLF", "ABC"};
    private static int erreurs;

    private static void erreur(String texte) {
        if (erreurs++ < 20) System.out.println("[synchro] ERREUR : " + texte);
    }

    /** Un essai : {@code ticks} ticks, au plus {@code max} joueurs, pertes de différences avec la probabilité {@code perte}. */
    static void essai(long graine, int ticks, int max, double perte, boolean verifierPendant) {
        Random r = new Random(graine);
        Synchro.Diffuseur<UUID, UUID, Ligne> tab = new Synchro.Diffuseur<>(l -> true, l -> !l.cache());
        Map<UUID, Ligne> etat = new LinkedHashMap<>();
        Map<UUID, Client> clients = new LinkedHashMap<>();
        Set<UUID> staff = new HashSet<>();
        List<UUID> demandes = new ArrayList<>();
        long maintenant = 0;
        Map<UUID, Ligne> dernier = Map.of();
        long complets = 0, differences = 0, arrivees = 0;
        for (int t = 0; t < ticks; t++) {
            maintenant += 50;
            boolean sale = false;
            boolean sansPerte = t >= ticks - 400;   // fin de l'essai : plus de perte, tout doit se réparer
            // demandes reçues pendant le tick précédent
            for (UUID u : demandes) if (clients.containsKey(u) && tab.demande(u, maintenant)) sale = true;
            demandes.clear();
            int evenements = r.nextInt(4);
            for (int e = 0; e < evenements; e++) {
                int quoi = r.nextInt(100);
                List<UUID> la = new ArrayList<>(etat.keySet());
                if (quoi < 25 && etat.size() < max) {
                    UUID u = new UUID(r.nextLong(), r.nextLong());
                    etat.put(u, new Ligne(u, GRADES[r.nextInt(4)], CLANS[r.nextInt(CLANS.length)], r.nextInt(0xFFFFFF), "", r.nextInt(30) == 0));
                    clients.put(u, new Client(u));
                    if (r.nextInt(15) == 0) staff.add(u);
                    tab.complet(u);
                    arrivees++;
                    sale = true;
                } else if (quoi < 45 && !la.isEmpty()) {
                    UUID u = la.get(r.nextInt(la.size()));
                    etat.remove(u);
                    clients.remove(u);
                    staff.remove(u);
                    tab.depart(u);
                    sale = true;
                } else if (quoi < 70 && !la.isEmpty()) {
                    UUID u = la.get(r.nextInt(la.size()));
                    Ligne l = etat.get(u);
                    etat.put(u, new Ligne(u, GRADES[r.nextInt(GRADES.length)], CLANS[r.nextInt(CLANS.length)], l.couleur(),
                            r.nextInt(5) == 0 ? "Surnom" + r.nextInt(9) : l.surnom(), l.cache()));
                    sale = r.nextBoolean();   // parfois signalé, parfois vu seulement au contrôle des 2 s
                } else if (quoi < 85 && !la.isEmpty()) {
                    UUID u = la.get(r.nextInt(la.size()));
                    Ligne l = etat.get(u);
                    etat.put(u, new Ligne(u, l.grade(), l.clan(), l.couleur(), l.surnom(), !l.cache()));
                    sale = true;
                } else if (!la.isEmpty()) {
                    UUID u = la.get(r.nextInt(la.size()));
                    if (!staff.remove(u)) staff.add(u);   // vu au contrôle des 2 s (connexion AuthMe, grade du staff)
                }
            }
            boolean publie = sale || t % 40 == 0;
            if (publie) {
                Map<UUID, Ligne> table = new LinkedHashMap<>(etat);
                dernier = table;
                final boolean sansPerteIci = sansPerte;
                List<Client> enLigne = new ArrayList<>(clients.values());
                long[] envoyes = new long[2];
                tab.<Client>publier(table, enLigne, c -> c.id, c -> staff.contains(c.id) ? 0 : 1,
                        new Synchro.Envoi<Client, UUID, Ligne>() {
                            @Override
                            public void complet(Client c, int vue, int version, Map<UUID, Ligne> t2) {
                                envoyes[0]++;
                                if (vue == 1) for (Ligne l : t2.values()) if (l.cache()) erreur("ligne cachée dans une liste complète publique");
                                c.vuePubliee = vue;
                                c.boite.add(new Message(true, vue, version, t2, List.of(), Map.of()));
                            }

                            @Override
                            public void difference(Client c, int vue, int version, Synchro.Diff<UUID, Ligne> d) {
                                envoyes[1]++;
                                if (vue == 1) for (Ligne l : d.changes().values()) if (l.cache()) erreur("ligne cachée dans une différence publique");
                                c.vuePubliee = vue;
                                if (!sansPerteIci && perte > 0 && r.nextDouble() < perte) {
                                    c.perte = true;   // paquet perdu (n'arrive jamais)
                                    return;
                                }
                                c.boite.add(new Message(false, vue, version, Map.of(), d.retires(), d.changes()));
                            }
                        });
                complets += envoyes[0];
                differences += envoyes[1];
            }
            // les joueurs lisent ce qu'ils ont reçu (dans l'ordre), comme TabHeraliz ; les listes attendues (une par vue)
            // ne sont refaites qu'après un envoi (rien ne change chez les joueurs entre deux envois)
            Map<UUID, Ligne> attenduStaff = publie && verifierPendant ? Synchro.vue(dernier, l -> true) : null;
            Map<UUID, Ligne> attenduPublic = publie && verifierPendant ? Synchro.vue(dernier, l -> !l.cache()) : null;
            for (Client c : clients.values()) {
                while (!c.boite.isEmpty()) {
                    Message m = c.boite.poll();
                    c.recus++;
                    if (m.complet()) {
                        c.table.clear();
                        c.table.putAll(m.table());
                        c.suite.complet(m.version());
                        c.perte = false;
                    } else if (c.suite.suivante(m.version())) {
                        Synchro.appliquer(c.table, m.retires(), m.changes());
                    } else if (c.suite.demander(maintenant)) {
                        if (perte == 0) erreur("demande de liste complète sans aucune perte (tick " + t + ")");
                        demandes.add(c.id);
                    }
                }
                boolean doitEtreJuste = perte == 0 || !c.perte && c.suite.aJour();
                if (attenduStaff != null && doitEtreJuste && c.vuePubliee >= 0) {
                    if (!(c.vuePubliee == 0 ? attenduStaff : attenduPublic).equals(c.table)) {
                        erreur("liste fausse chez un joueur (tick " + t + ", vue " + c.vuePubliee + ")");
                    }
                }
            }
        }
        // fin : sans perte depuis 400 ticks et avec des changements, tout le monde doit avoir la bonne liste
        int fausses = 0;
        for (Client c : clients.values()) {
            if (c.vuePubliee < 0) continue;
            Map<UUID, Ligne> attendu = Synchro.vue(dernier, c.vuePubliee == 0 ? (Ligne l) -> true : (Ligne l) -> !l.cache());
            if (!attendu.equals(c.table)) fausses++;
        }
        if (fausses > 0) erreur(fausses + " liste(s) fausse(s) à la fin (graine " + graine + ", pertes " + perte + ")");
        System.out.printf(java.util.Locale.ROOT, "[synchro] graine %d : %d ticks, %d arrivées, %d listes complètes, %d différences, pertes %.0f %%, %d joueurs à la fin%n",
                graine, ticks, arrivees, complets, differences, perte * 100, clients.size());
    }

    /** Petits cas écrits à la main (diff, vues, suite des numéros, demandes). */
    static void cas() {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        Ligne la = new Ligne(a, "survivant", "", 0, "", false), lb = new Ligne(b, "roi", "ZK", 1, "", false), lbc = new Ligne(b, "roi", "ZK", 1, "", true);
        Map<UUID, Ligne> avant = new LinkedHashMap<>(Map.of(a, la)), apres = new LinkedHashMap<>();
        apres.put(a, la);
        apres.put(b, lbc);
        Synchro.Diff<UUID, Ligne> pub = Synchro.diff(avant, apres, l -> !l.cache()), tout = Synchro.diff(avant, apres, l -> true);
        if (!pub.vide()) erreur("un joueur caché qui arrive ne doit rien envoyer aux autres");
        if (tout.changes().size() != 1 || !tout.changes().containsKey(b)) erreur("le staff doit recevoir le joueur caché qui arrive");
        Map<UUID, Ligne> ensuite = new LinkedHashMap<>(apres);
        ensuite.put(b, lb);
        if (!Synchro.diff(apres, ensuite, l -> !l.cache()).changes().containsKey(b)) erreur("joueur qui se montre : ajouté chez les autres");
        if (!Synchro.diff(ensuite, apres, l -> !l.cache()).retires().contains(b)) erreur("joueur qui se cache : retiré chez les autres");
        if (!Synchro.diff(apres, Map.of(a, la), l -> !l.cache()).vide()) erreur("départ d'un caché : rien chez les autres");
        if (!Synchro.diff(apres, apres, l -> true).vide()) erreur("rien n'a changé : rien ne part");
        Synchro.Suite s = new Synchro.Suite();
        if (s.suivante(1)) erreur("une différence avant toute liste complète ne s'applique pas");
        if (!s.demander(0) || s.demander(1000) || !s.demander(6000)) erreur("demandes : une toutes les 5 s");
        s.complet(7);
        if (!s.suivante(8) || s.suivante(10) || s.suivante(11) || s.aJour()) erreur("numéros : 8 suit 7, 10 ne suit pas 8");
        s.complet(11);
        if (!s.suivante(12)) erreur("après une liste complète, la suite repart");
        Synchro.Demandes<UUID> d = new Synchro.Demandes<>();
        if (!d.accepter(c, 0) || d.accepter(c, 4999) || !d.accepter(c, 5000)) erreur("serveur : une demande toutes les 5 s par joueur");
        // liste vocale : groupes (clé : numéro) et joueurs (clé : UUID) dans une seule liste
        Map<Object, Object> v1 = new LinkedHashMap<>(), v2 = new LinkedHashMap<>();
        v1.put(3, "groupe 3");
        v1.put(a, "joueur a");
        v2.put(a, "joueur a");
        v2.put(4, "groupe 4");
        Synchro.Diff<Object, Object> dv = Synchro.diff(v1, v2, x -> true);
        if (!dv.retires().equals(List.of(3)) || !dv.changes().equals(Map.of(4, "groupe 4"))) erreur("liste vocale : groupes et joueurs mêlés");
    }

    /** Taille des envois d'une arrivée dans un serveur de n joueurs (comme l'essai, sans perte) : complètes et différences. */
    static void cout(int n) {
        Synchro.Diffuseur<UUID, UUID, Ligne> tab = new Synchro.Diffuseur<>(l -> true, l -> !l.cache());
        Map<UUID, Ligne> etat = new LinkedHashMap<>();
        List<UUID> joueurs = new ArrayList<>();
        Random r = new Random(n);
        for (int i = 0; i < n; i++) {
            UUID u = new UUID(r.nextLong(), r.nextLong());
            etat.put(u, new Ligne(u, "survivant", "", 0, "", false));
            joueurs.add(u);
        }
        int[] compte = new int[4];
        Synchro.Envoi<UUID, UUID, Ligne> envoi = new Synchro.Envoi<>() {
            @Override
            public void complet(UUID j, int vue, int version, Map<UUID, Ligne> table) {
                compte[0]++;
                compte[1] += table.size();
            }

            @Override
            public void difference(UUID j, int vue, int version, Synchro.Diff<UUID, Ligne> d) {
                compte[2]++;
                compte[3] += d.changes().size() + d.retires().size();
            }
        };
        tab.<UUID>publier(new LinkedHashMap<>(etat), joueurs, u -> u, u -> 1, envoi);
        UUID nouveau = new UUID(1, 1);
        etat.put(nouveau, new Ligne(nouveau, "survivant", "", 0, "", false));
        joueurs.add(nouveau);
        tab.complet(nouveau);
        java.util.Arrays.fill(compte, 0);
        tab.<UUID>publier(new LinkedHashMap<>(etat), joueurs, u -> u, u -> 1, envoi);
        if (compte[0] != 1 || compte[1] != n + 1 || compte[2] != n || compte[3] != n) {
            erreur("arrivée à " + n + " joueurs : " + compte[0] + " liste(s) complète(s) de " + compte[1] + " lignes, " + compte[2] + " différence(s) de " + compte[3] + " lignes");
        }
        etat.remove(nouveau);
        joueurs.remove(nouveau);
        tab.depart(nouveau);
        java.util.Arrays.fill(compte, 0);
        tab.<UUID>publier(new LinkedHashMap<>(etat), joueurs, u -> u, u -> 1, envoi);
        if (compte[0] != 0 || compte[2] != n || compte[3] != n) erreur("départ à " + n + " joueurs : " + compte[2] + " différence(s)");
        java.util.Arrays.fill(compte, 0);
        tab.<UUID>publier(new LinkedHashMap<>(etat), joueurs, u -> u, u -> 1, envoi);
        if (compte[0] + compte[2] != 0) erreur("rien n'a changé : " + (compte[0] + compte[2]) + " envoi(s) au lieu de 0");
    }

    public static void main(String[] args) {
        cas();
        for (int n : new int[]{50, 100, 150}) cout(n);
        essai(1, 4_000, 150, 0, true);
        essai(2, 4_000, 60, 0, true);
        essai(3, 4_000, 150, 0.02, true);
        essai(4, 4_000, 150, 0.2, true);
        System.out.println("[synchro] " + erreurs + " erreur(s)");
        System.exit(erreurs == 0 ? 0 : 1);
    }
}
