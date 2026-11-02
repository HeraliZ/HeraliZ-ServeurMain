package com.Spartan.integrity;

import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Jugement des rapports du Gardien pour UNE connexion (audit du 02/11, point 2), côté serveur, Java pur (testé hors du
 * jeu). Ce que le jeu transmet n'est cru que s'il est prouvé : signature du launcher avec la clé liée au jeton, numéro
 * qui ne recule jamais, défi tiré par le serveur pour cette connexion. GardienServeur en tire les alertes.
 */
public final class GardienJuge {
    /** Comment la clé du Gardien de cette connexion est connue. */
    public enum Lien {
        /** Clé publique engagée dans le jeton signé par le site (launcher 1.16.0 ou plus récent) : rapports prouvés. */
        PROUVE,
        /** Serveur sans clé des jetons (serveur d'essai) : clé annoncée par le jeu, crue sans preuve. */
        NON_VERIFIE,
        /** Jeton valide sans clé du Gardien (launcher 1.15.x ou plus ancien) : rapports lus, jamais prouvés. */
        ANCIEN,
        /** Pas de jeton accepté : rien n'est prouvé. */
        SANS_JETON
    }

    /** Ce que vaut un paquet du jeu. */
    public enum Sorte {
        /** Le jeu dit avoir été lancé sans le Gardien. */
        ABSENT,
        /** Pas de rapport lisible dans le jeu (launcher arrêté, fichier verrouillé). */
        MUET,
        /** Rapport refusé (signature, format, rejoué, autre jeu) : {@link Verdict#raison}. */
        INVALIDE,
        /** Même rapport qu'au paquet précédent (le jeu relit plus souvent que le launcher n'écrit) : rien de neuf. */
        DOUBLON,
        /** Rapport lu : {@link Verdict#rapport}, {@link Verdict#prouve}, {@link Verdict#frais}. */
        RAPPORT
    }

    /**
     * @param prouve signature vérifiée avec la clé liée au jeton (sinon : contenu lu sans preuve)
     * @param frais  rapport écrit après le dernier ou l'avant-dernier défi de CETTE connexion
     */
    public record Verdict(Sorte sorte, String raison, GardienPreuve.Rapport rapport, boolean prouve, boolean frais) {
        static Verdict de(Sorte s) {
            return new Verdict(s, null, null, false, false);
        }

        static Verdict invalide(String raison) {
            return new Verdict(Sorte.INVALIDE, raison, null, false, false);
        }
    }

    private static final SecureRandom HASARD = new SecureRandom();

    private final Lien lien;
    private final PublicKey cle;
    private String defi;
    private String defiPrecedent;
    private long defiA;
    private long dernierN = -1;

    public GardienJuge(Lien lien, PublicKey cle) {
        this.lien = lien;
        this.cle = cle;
    }

    public Lien lien() {
        return lien;
    }

    /** Des rapports signés sont attendus (clé connue) : le serveur tire des défis. */
    public boolean avecCle() {
        return cle != null;
    }

    /** Nouveau défi à envoyer si le dernier a plus de {@code dureeMs} (ou s'il n'y en a pas) ; sinon null. */
    public String defiAEnvoyer(long maintenant, long dureeMs) {
        if (cle == null || (defi != null && maintenant - defiA < dureeMs)) {
            return null;
        }
        byte[] b = new byte[16];
        HASARD.nextBytes(b);
        defiPrecedent = defi;
        defi = HexFormat.of().formatHex(b);
        defiA = maintenant;
        return defi;
    }

    /** Pour les tests : impose le défi en cours. */
    void defi(String d, long maintenant) {
        defiPrecedent = defi;
        defi = d;
        defiA = maintenant;
    }

    /** Juge un paquet du jeu (état, ligne et signature tels que lus dans le fichier du launcher, numéro de processus). */
    public Verdict juger(String etat, String ligne, String signature, long pidJeu) {
        switch (etat) {
            case "absent":
                // jeton qui engage une clé du Gardien, jeu qui dit ne pas en avoir : le jeu ment
                return cle != null && lien == Lien.PROUVE ? Verdict.invalide("absent") : Verdict.de(Sorte.ABSENT);
            case "fichier":
            case "lecture":
                return Verdict.de(Sorte.MUET);
            case "ok":
                break;
            default:
                return Verdict.invalide("format");
        }
        GardienPreuve.Lu lu = cle != null ? GardienPreuve.verifier(cle, ligne, signature) : GardienPreuve.lireSansPreuve(ligne);
        if (!lu.ok()) {
            return Verdict.invalide(lu.refus());
        }
        GardienPreuve.Rapport r = lu.rapport();
        if (cle != null && r.pid() != pidJeu) {
            return Verdict.invalide("pid"); // rapport (bien signé) du launcher pour un autre jeu
        }
        if (r.n() < dernierN) {
            return Verdict.invalide("rejoue");
        }
        if (r.n() == dernierN) {
            return Verdict.de(Sorte.DOUBLON);
        }
        dernierN = r.n();
        boolean prouve = cle != null && lien == Lien.PROUVE;
        boolean frais = cle != null && !r.defi().equals("-") && (r.defi().equals(defi) || r.defi().equals(defiPrecedent));
        return new Verdict(Sorte.RAPPORT, null, r, prouve, frais);
    }
}
