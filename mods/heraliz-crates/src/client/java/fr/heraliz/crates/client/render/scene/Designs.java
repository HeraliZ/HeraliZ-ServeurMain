package fr.heraliz.crates.client.render.scene;

import fr.heraliz.crates.client.render.scene.crates.Trophee;
import fr.heraliz.crates.client.render.scene.crates.Gemme;
import fr.heraliz.crates.client.render.scene.crates.MereDesRuines;
import fr.heraliz.crates.client.render.scene.crates.PorteurDePeste;
import fr.heraliz.crates.client.render.scene.crates.Quete;
import fr.heraliz.crates.client.render.scene.crates.SujetN07;
import fr.heraliz.crates.client.render.scene.crates.Sylvorn;
import fr.heraliz.crates.client.render.scene.crates.Urne;
import fr.heraliz.crates.client.render.scene.crates.Graffiti;
import fr.heraliz.crates.client.render.scene.crates.Booster;
import fr.heraliz.crates.client.render.scene.crates.Chantier;
import fr.heraliz.crates.client.render.scene.crates.Trauma;
import fr.heraliz.crates.client.render.scene.crates.Largage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre des designs de caisses (par identifiant).
 */
public final class Designs {
    private Designs() {
    }

    private static final Map<String, Design> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Design> LOD = new ConcurrentHashMap<>();
    /** Detail du modele eloigne. */
    public static final float LOD_DETAIL = 0.45f;

    public static Design create(String id, float detail) {
        Design d = create(id);
        d.detail = detail;
        return d;
    }

    public static Design create(String id) {
        return switch (id) {
            case "mere_des_ruines" -> new MereDesRuines();
            case "sylvorn" -> new Sylvorn();
            case "sujet_n07" -> new SujetN07();
            case "porteur_de_peste" -> new PorteurDePeste();
            case "vote" -> new Urne();
            case "quete" -> new Quete();
            case "event" -> new Trophee();
            case "gemme" -> new Gemme();
            case "camo" -> new Graffiti();
            case "sticker" -> new Booster();
            case "build" -> new Chantier();
            case "trauma" -> new Trauma();
            case "largage" -> new Largage();
            default -> throw new IllegalArgumentException("caisse inconnue : " + id);
        };
    }

    public static Design get(String id) {
        return CACHE.computeIfAbsent(id, Designs::create);
    }

    /** Version allegee (vue de loin, icones d'inventaire). */
    public static Design getLod(String id) {
        return LOD.computeIfAbsent(id, i -> create(i, LOD_DETAIL));
    }

    /** Vide les caches (rechargement des ressources). */
    public static void clear() {
        CACHE.clear();
        LOD.clear();
    }
}
