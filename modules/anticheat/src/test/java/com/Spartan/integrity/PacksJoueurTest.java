package com.Spartan.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Packs de textures ajoutés par le joueur (19/11, refusés) : seuls ceux de ses dossiers (« file/… ») comptent ; le jeu
 * officiel (pack de base, ressources des mods, nos packs, TaCZ, véhicules, pack du serveur) ne compte JAMAIS.
 */
class PacksJoueurTest {
    /** Packs actifs d'un jeu HeraliZ honnête (identifiants relevés dans le code de Minecraft, Forge, nos mods et les mods). */
    private static final List<String> JEU_OFFICIEL = List.of("vanilla", "mod_resources", "mod:tacz", "tacz_resources",
            "ywzj_vehicle_resources_default_vehicle", "heraliz_interface:textures", "heraliz_interface:herbe",
            "heralisz_utils:terrain", "server", "world", "programmer_art", "high_contrast");

    @Test
    void leJeuOfficielNeCompteJamais() {
        assertEquals(List.of(), PacksJoueur.ajoutes(JEU_OFFICIEL, List.of()));
        assertEquals(List.of(), PacksJoueur.ajoutes(List.of(), List.of()));
        assertEquals(List.of(), PacksJoueur.ajoutes(null, List.of()));
        assertEquals(List.of(), PacksJoueur.ajoutes(Arrays.asList("vanilla", null, "file/"), null));
    }

    @Test
    void lesPacksDesDossiersDuJoueurComptent() {
        List<String> actifs = new java.util.ArrayList<>(JEU_OFFICIEL);
        actifs.addAll(List.of("file/Xray Ultimate 1.20.zip", "FILE/Faithful 32x", "file/Xray Ultimate 1.20.zip"));
        assertEquals(List.of("Xray Ultimate 1.20.zip", "Faithful 32x"), PacksJoueur.ajoutes(actifs, List.of()));
        // un pack autorisé par le staff (allowedResourcePacks, identifiant complet en minuscules) ne compte plus
        assertEquals(List.of("Faithful 32x"), PacksJoueur.ajoutes(actifs, List.of("file/xray ultimate 1.20.zip")));
    }

    @Test
    void nomsSursEtListeCourte() {
        // codes de couleur et caractères de contrôle retirés (le nom vient du jeu du joueur), 48 caractères au plus
        assertEquals(List.of("Rouge pack"), PacksJoueur.ajoutes(List.of("file/§cRouge\u0007 pack"), List.of()));
        assertEquals(48, PacksJoueur.ajoutes(List.of("file/" + "a".repeat(100)), List.of()).get(0).length());
        List<String> beaucoup = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            beaucoup.add("file/p" + i + ".zip");
        }
        List<String> noms = PacksJoueur.ajoutes(beaucoup, List.of());
        assertEquals(PacksJoueur.MAX, noms.size());
        assertEquals("p0.zip, p1.zip, p2.zip, p3.zip, p4.zip (+5)", PacksJoueur.liste(noms));
        assertEquals("seul.zip", PacksJoueur.liste(List.of("seul.zip")));
    }
}
