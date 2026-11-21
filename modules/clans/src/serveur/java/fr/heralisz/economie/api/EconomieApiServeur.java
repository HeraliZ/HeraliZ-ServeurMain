package fr.heralisz.economie.api;

import fr.heralisz.economie.HeraliszEconomie;
import fr.heralisz.economie.network.EconomieNet;
import fr.heralisz.economie.network.Packets;
import fr.heralisz.economie.server.ComptesStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Partie serveur de {@link EconomieApi} : les comptes (store, journal, transactions, sauvegarde). Corps d'origine de
 * l'API, inchanges ; branchee par {@code economie.serveur.PartieServeur} (jars -serveur et -complet).
 */
public final class EconomieApiServeur implements EconomieApi.Moteur {
    private EconomieApiServeur() {
    }

    /** Branche les comptes sur l'API (appele une fois, pendant la construction du mod). */
    public static void installer() {
        EconomieApi.moteur = new EconomieApiServeur();
    }

    private static long maxSolde() {
        return EconomieApi.maxSolde();
    }

    // ------------------------------------------------------------------ etat
    /**
     * Vrai si le serveur tourne et que l'on est sur son fil : les autres methodes peuvent etre appelees. 21/11 (audit) :
     * faux aussi tant que comptes.json n'a pas pu etre lu au demarrage (sauvegarde bloquee, comptes partis de zero en
     * memoire) : rien de ce qui se paierait alors ne serait jamais ecrit, et la relecture apres reparation
     * ({@code /recharger herals}) l'effacerait. Avant, la vente a Mila, un retrait de la banque du clan ou une quete payee
     * passaient quand meme : objets ou banque partis pour de bon, herals perdus a la relecture. Maintenant ces operations
     * refusent (« economie indisponible »), comme sans le mod, jusqu'a la relecture ou {@code /eco sauvegarder --forcer}.
     */
    @Override
    public boolean disponible() {
        ComptesStore s = ComptesStore.get();
        return s != null && s.server().isSameThread() && !s.chargementEchoue();
    }

    // ------------------------------------------------------------------ lecture
    /** Solde du joueur (0 s'il n'a pas de compte). */
    @Override
    public long solde(UUID joueur) {
        Compte c = ComptesStore.verifierFil().get(joueur);
        return c == null ? 0 : c.solde();
    }

    /** Le compte (null si le joueur n'en a jamais eu). Lecture seule pour les autres mods. */
    @Override
    @Nullable
    public Compte compte(UUID joueur) {
        return ComptesStore.verifierFil().get(joueur);
    }

    /** Les n dernieres transactions du joueur, la plus recente en premier (liste vide sans compte). */
    @Override
    public List<Transaction> journal(UUID joueur, int n) {
        Compte c = ComptesStore.verifierFil().get(joueur);
        return c == null ? List.of() : c.journal(Math.max(0, n));
    }

    /** Les n comptes les plus riches (solde decroissant). */
    @Override
    public List<Compte> classement(int n) {
        return ComptesStore.verifierFil().classement(Math.max(0, n));
    }

    // ------------------------------------------------------------------ ecriture
    /** Retire le montant ; faux (et rien ne change) si montant <= 0 ou solde insuffisant. */
    @Override
    public boolean retirer(UUID joueur, long montant, String motif) {
        ComptesStore s = ComptesStore.verifierFil();
        if (montant <= 0) return false;
        Compte c = s.get(joueur);
        if (c == null || c.solde() < montant) return false;
        appliquer(s, c, -montant, motif, null);
        return true;
    }

    /** Crédite le montant (rien si <= 0) ; cree le compte au besoin ; tronque au plafond max_solde. */
    @Override
    public void deposer(UUID joueur, long montant, String motif) {
        ComptesStore s = ComptesStore.verifierFil();
        if (montant <= 0) return;
        Compte c = s.compte(joueur, null);
        long place = maxSolde() - c.solde();
        if (place <= 0) {
            HeraliszEconomie.LOG.warn("[economie] depot de {} pour {} ignore : plafond atteint", montant, joueur);
            return;
        }
        // 21/11 (validation de l'audit) : un depot coupe au plafond est dit au journal (le staff peut rendre la difference) ;
        // les appelants qui prennent quelque chose en echange (Mila, quetes, recycleurs) regardent la place avant
        if (montant > place) HeraliszEconomie.LOG.warn("[economie] depot de {} pour {} coupe a {} : plafond atteint", montant, joueur, place);
        appliquer(s, c, Math.min(montant, place), motif, null);
    }

    /**
     * Transfert atomique de « de » vers « vers » : faux (rien ne change) si montant <= 0, memes comptes, solde
     * insuffisant ou plafond du destinataire depasse. Les deux comptes sont mutes avant tout effet de bord
     * (evenement, paquet, sauvegarde) : une erreur en aval ne peut pas laisser un debit sans credit.
     */
    @Override
    public boolean transferer(UUID de, UUID vers, long montant, String motif) {
        ComptesStore s = ComptesStore.verifierFil();
        if (montant <= 0 || de == null || vers == null || de.equals(vers)) return false;
        Compte a = s.get(de);
        if (a == null || a.solde() < montant) return false;
        Compte b = s.compte(vers, null);
        if (b.solde() + montant > maxSolde()) return false;
        String m = normaliser(motif);
        long avantA = a.solde(), avantB = b.solde();
        s.appliquer(a, -montant, m, vers);
        s.appliquer(b, montant, m, de);
        s.marquerDirty(montant);
        notifier(s, a, avantA, -montant, m);
        notifier(s, b, avantB, montant, m);
        return true;
    }

    /** Fixe le solde (0 <= solde <= max_solde) ; faux si hors bornes. Journalise la difference. */
    @Override
    public boolean definir(UUID joueur, long solde, String motif) {
        ComptesStore s = ComptesStore.verifierFil();
        if (solde < 0 || solde > maxSolde()) return false;
        Compte c = s.compte(joueur, null);
        long delta = solde - c.solde();
        if (delta == 0) return true;
        appliquer(s, c, delta, motif, null);
        return true;
    }

    /** Mutation d'un seul compte puis effets de bord (sauvegarde, evenement, paquet). */
    private static void appliquer(ComptesStore s, Compte c, long delta, String motif, @Nullable UUID autre) {
        String m = normaliser(motif);
        long avant = c.solde();
        s.appliquer(c, delta, m, autre);
        s.marquerDirty(delta);
        notifier(s, c, avant, delta, m);
    }

    /** Evenement Forge puis paquet au joueur s'il est en ligne (motif deja normalise : jamais d'EncoderException). */
    private static void notifier(ComptesStore s, Compte c, long avant, long delta, String motif) {
        MinecraftForge.EVENT_BUS.post(new SoldeChangeEvent(c.uuid(), avant, c.solde(), motif));
        ServerPlayer p = s.server().getPlayerList().getPlayer(c.uuid());
        if (p != null) EconomieNet.toPlayer(p, new Packets.SoldeS2C(c.solde(), delta, motif));
    }

    /** Motif tel qu'il est journalise : non nul, coupe a {@link Transaction#MOTIF_MAX}. */
    private static String normaliser(@Nullable String motif) {
        if (motif == null) return "";
        return motif.length() > Transaction.MOTIF_MAX ? motif.substring(0, Transaction.MOTIF_MAX) : motif;
    }

    // ------------------------------------------------------------------ persistance
    /**
     * Ecrit comptes.json tout de suite et attend la fin de l'ecriture (de quelques ms a quelques centaines de ms
     * selon le nombre de comptes) : pour les operations critiques des autres mods (banque de clan, gros achats) qui
     * veulent le solde sur le disque avant de continuer. Fil serveur uniquement. Faux si rien n'a ete ecrit :
     * sauvegarde bloquee parce que le fichier etait illisible au demarrage, ou erreur disque (le journal du serveur
     * dit pourquoi) ; les soldes en memoire restent corrects dans tous les cas.
     */
    @Override
    public boolean sauvegarderMaintenant() {
        return ComptesStore.verifierFil().sauvegarderMaintenant();
    }

    /**
     * Demande une sauvegarde groupee : au plus une ecriture par 1,5 s, serialisation sur le fil serveur et ecriture
     * sur le fil IO du mod, sans bloquer le tick. Pour marquer un changement important sans attendre (c'est ce que
     * fait l'API elle-meme pour les montants >= sauvegarde_immediate). Fil serveur uniquement.
     */
    @Override
    public void sauvegarderBientot() {
        ComptesStore.verifierFil().sauvegarderBientot();
    }
}
