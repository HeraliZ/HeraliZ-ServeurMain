package fr.heraliz.carte.client;

import fr.heraliz.carte.CarteNet;
import fr.heraliz.carte.Lieu;

import java.util.List;

/** Lieux nommés et nom de la carte reçus du serveur (connexion, puis à chaque changement du staff). Fil du client. */
final class LieuxClient {
    private LieuxClient() {
    }

    private static List<Lieu> lieux = List.of();
    private static String nomCarte = "";

    static void recevoir(CarteNet.LieuxS2C m) {
        lieux = List.copyOf(m.lieux());
        nomCarte = m.nomCarte();
    }

    static List<Lieu> lieux() {
        return lieux;
    }

    /** Nom de la carte (vide tant que le serveur n'a rien envoyé). */
    static String nomCarte() {
        return nomCarte;
    }

    static void vider() {
        lieux = List.of();
        nomCarte = "";
    }
}
