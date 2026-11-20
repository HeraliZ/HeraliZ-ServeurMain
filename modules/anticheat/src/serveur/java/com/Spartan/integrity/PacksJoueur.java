package com.Spartan.integrity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Packs de textures AJOUTÉS PAR LE JOUEUR (19/11, décision du propriétaire : interdits, connexion refusée). Java pur
 * (testé hors du jeu).
 *
 * <p>Minecraft nomme « file/&lt;nom&gt; » les packs qu'il trouve dans les dossiers du joueur ({@code resourcepacks}, et
 * {@code kiwipacks} pour Kiwi) : ce sont les seuls que le joueur ajoute lui-même, et le seul moyen de glisser des
 * textures de rayons X sans modifier le jeu. Tous les autres packs actifs viennent du jeu officiel et ne comptent
 * JAMAIS : le pack de base ({@code vanilla}), les ressources des mods ({@code mod_resources}, {@code mod:…}), nos packs
 * rangés dans nos jars ({@code heraliz_interface:textures}, {@code heraliz_interface:herbe},
 * {@code heralisz_utils:terrain}), ceux de TaCZ et des véhicules ({@code tacz_resources},
 * {@code ywzj_vehicle_resources_…}), le pack envoyé par le serveur ({@code server}). Les packs optionnels de Minecraft
 * ({@code programmer_art}, {@code high_contrast}) ne donnent aucun avantage : le mod d'interface les retire déjà.
 */
public final class PacksJoueur {
    /** Préfixe des packs trouvés dans les dossiers du joueur (FolderRepositorySource de Minecraft). */
    public static final String PREFIXE = "file/";
    /** Au plus autant de noms gardés (le reste ne dit rien de plus, et le message du joueur reste court). */
    static final int MAX = 10;

    private PacksJoueur() {
    }

    /**
     * Packs ajoutés par le joueur parmi les packs actifs : ceux de ses dossiers (« file/… »), hors de la liste
     * {@code autorises} (identifiants complets en minuscules, réglage {@code allowedResourcePacks}). Rendus sans le
     * préfixe, sans doublon, au plus {@link #MAX}.
     */
    public static List<String> ajoutes(List<String> actifs, List<String> autorises) {
        List<String> out = new ArrayList<>();
        if (actifs == null) {
            return out;
        }
        for (String p : actifs) {
            if (p == null) {
                continue;
            }
            String id = p.trim().toLowerCase(Locale.ROOT);
            if (!id.startsWith(PREFIXE) || id.length() == PREFIXE.length() || (autorises != null && autorises.contains(id))) {
                continue;
            }
            String nom = lisible(p.trim().substring(PREFIXE.length()));
            if (!nom.isEmpty() && !out.contains(nom) && out.size() < MAX) {
                out.add(nom);
            }
        }
        return out;
    }

    /** Liste à montrer au joueur et au staff : 5 noms au plus, puis « (+N) ». */
    public static String liste(List<String> noms) {
        int n = Math.min(5, noms.size());
        return String.join(", ", noms.subList(0, n)) + (noms.size() > n ? " (+" + (noms.size() - n) + ")" : "");
    }

    /**
     * Nom envoyé par le jeu, rendu sûr pour un message : sans caractère de contrôle ni code de couleur de Minecraft
     * (« § »), 48 caractères au plus.
     */
    static String lisible(String nom) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nom.length() && sb.length() < 48; i++) {
            char c = nom.charAt(i);
            if (c == '§') {
                i++; // le code de couleur qui suit part avec lui
            } else if (c >= 0x20 && (c < 0x7F || c > 0x9F)) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
