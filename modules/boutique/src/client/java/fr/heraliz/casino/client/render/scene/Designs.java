package fr.heraliz.casino.client.render.scene;

import fr.heraliz.casino.client.render.scene.machines.PlaceholderDesign;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre des modeles de machines (par identifiant).
 */
public final class Designs {
    private Designs() {
    }

    private static final Map<String, MachineDesign<?>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, MachineDesign<?>> LOD = new ConcurrentHashMap<>();
    public static final float LOD_DETAIL = 0.5f;

    public static final String[] IDS = {"caisse", "rotten", "portes_olympe", "souffle_loup", "la_niche", "plinko", "blackjack", "roulette", "crash", "mines", "course",
            "pierre_feuille_ciseaux", "bataille_navale", "tableau_gagnants", "tableau_perdants", "tableau_parieurs"};

    public static MachineDesign<?> create(String id, float detail) {
        MachineDesign<?> d = create(id);
        d.detail = detail;
        return d;
    }

    public static MachineDesign<?> create(String id) {
        MachineDesign<?> decor = fr.heraliz.casino.client.render.scene.decor.DecorDesigns.create(id);
        if (decor != null) return decor;
        return switch (id) {
            case "rotten" -> new fr.heraliz.casino.client.render.scene.machines.RottenDesign();
            case "portes_olympe" -> new fr.heraliz.casino.client.render.scene.machines.OlympeDesign();
            case "souffle_loup" -> new fr.heraliz.casino.client.render.scene.machines.LoupDesign();
            case "la_niche" -> new fr.heraliz.casino.client.render.scene.machines.NicheDesign();
            case "tableau_gagnants" -> new fr.heraliz.casino.client.render.scene.machines.BoardDesign(0);
            case "tableau_perdants" -> new fr.heraliz.casino.client.render.scene.machines.BoardDesign(1);
            case "tableau_parieurs" -> new fr.heraliz.casino.client.render.scene.machines.BoardDesign(2);
            case "plinko" -> new fr.heraliz.casino.client.render.scene.machines.PlinkoDesign();
            case "blackjack" -> new fr.heraliz.casino.client.render.scene.machines.BlackjackDesign();
            case "roulette" -> new fr.heraliz.casino.client.render.scene.machines.RouletteDesign();
            case "crash" -> new fr.heraliz.casino.client.render.scene.machines.CrashDesign();
            case "mines" -> new fr.heraliz.casino.client.render.scene.machines.MinesDesign();
            case "course" -> new fr.heraliz.casino.client.render.scene.machines.RaceDesign();
            case "caisse" -> new fr.heraliz.casino.client.render.scene.machines.CashierDesign();
            case "pierre_feuille_ciseaux" -> new fr.heraliz.casino.client.render.scene.machines.RpsDesign();
            case "bataille_navale" -> new fr.heraliz.casino.client.render.scene.machines.NavalDesign();
            default -> new PlaceholderDesign(id);
        };
    }

    public static MachineDesign<?> get(String id) {
        return CACHE.computeIfAbsent(id, Designs::create);
    }

    public static MachineDesign<?> getLod(String id) {
        return LOD.computeIfAbsent(id, i -> create(i, LOD_DETAIL));
    }

    public static void clear() {
        CACHE.clear();
        LOD.clear();
    }
}
