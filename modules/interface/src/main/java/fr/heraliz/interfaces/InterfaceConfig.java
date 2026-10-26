package fr.heraliz.interfaces;

import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Reglages du client : config/heraliz_interface-client.toml (ecrit au premier lancement, relu a chaud par Forge quand
 * le fichier change). Les libelles vides reprennent la traduction (fr_fr / en_us). Le texte de l'ecran Informations
 * est dans config/heraliz_interface/informations.json (voir {@link fr.heraliz.interfaces.client.Informations}).
 */
public final class InterfaceConfig {
    private InterfaceConfig() {
    }

    /** Lien du Discord du serveur (bouton Discord du menu Echap). */
    public static final String DISCORD_DEFAUT = "https://discord.gg/heraliz";
    /** Anciens liens par defaut : une config ecrite avec l'un d'eux prend le lien actuel (rien a changer chez les joueurs). */
    private static final List<String> ANCIENS_DISCORD = List.of("https://discord.gg/kYmtdKYXg");

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue MENU_ACTIF;
    public static final ForgeConfigSpec.ConfigValue<String> DISCORD_LIEN, DISCORD_LIBELLE, BOUTIQUE_LIEN, BOUTIQUE_LIBELLE,
            INFORMATIONS_LIBELLE, SOUS_TITRE, ACCENT;
    public static final ForgeConfigSpec.BooleanValue FLOU, ECHELLE_AUTO, PIED, LIENS_MOJANG;
    public static final ForgeConfigSpec.IntValue FLOU_FORCE;
    public static final ForgeConfigSpec.BooleanValue TITRE_ACTIF;
    public static final ForgeConfigSpec.ConfigValue<String> SERVEUR_ADRESSE, SERVEUR_NOM;
    /** Qualite graphique choisie dans le menu Echap ({@link fr.heraliz.interfaces.client.Qualite}). */
    public static final ForgeConfigSpec.ConfigValue<String> PROFIL_GRAPHIQUE;
    private static final List<String> PROFILS = List.of("performance", "equilibre", "qualite");

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Menu Echap HeraliZ (remplace le menu de pause vanilla)").push("menu_pause");
        MENU_ACTIF = b.comment("Remplacer le menu Echap vanilla par celui d'HeraliZ (false = menu vanilla)")
                .define("actif", true);
        DISCORD_LIEN = b.comment("Lien du bouton Discord (vide = bouton cache)")
                .define("discord_lien", DISCORD_DEFAUT, InterfaceConfig::lienValide);
        DISCORD_LIBELLE = b.comment("Libelle du bouton Discord (vide = traduction : « Discord »)")
                .define("discord_libelle", "");
        BOUTIQUE_LIEN = b.comment("Lien de la boutique (vide = bouton affiche mais grise avec « Bientot »)")
                .define("boutique_lien", "", InterfaceConfig::lienValide);
        BOUTIQUE_LIBELLE = b.comment("Libelle du bouton Boutique (vide = traduction : « Boutique »)")
                .define("boutique_libelle", "");
        INFORMATIONS_LIBELLE = b.comment("Libelle du bouton Informations (vide = traduction : « Informations »)")
                .define("informations_libelle", "");
        SOUS_TITRE = b.comment("Phrase sous le logo (vide = traduction : « Survie post-apocalyptique · 1950 » ; « - » = rien)")
                .define("sous_titre", "");
        ACCENT = b.comment("Couleur d'accent en hexadecimal (defaut : rouge du Z du logo, E3201B)")
                .define("accent", "E3201B", o -> o instanceof String s && s.trim().matches("#?[0-9a-fA-F]{6}"));
        FLOU = b.comment("Flouter le monde derriere le menu (false = simple voile sombre)")
                .define("flou", true);
        FLOU_FORCE = b.comment("Force du flou (rayon en pixels, 2 a 24)")
                .defineInRange("flou_force", 10, 2, 24);
        ECHELLE_AUTO = b.comment("Taille d'interface automatique pour ce menu (comme les ecrans des clans : 3 en 1080p, 4 en 1440p) ;"
                        + " false = taille d'interface choisie dans les options")
                .define("echelle_auto", true);
        LIENS_MOJANG = b.comment("Afficher les petits liens « Avis » et « Signaler un bug » de Mojang en bas a droite")
                .define("liens_mojang", true);
        PIED = b.comment("Afficher en bas de l'ecran le pseudo, le serveur et le ping")
                .define("pied", true);
        b.pop();
        b.comment("Ecran titre HeraliZ (remplace celui de Minecraft) : Jouer = connexion au serveur, ni solo ni liste de serveurs")
                .push("ecran_titre");
        TITRE_ACTIF = b.comment("Remplacer l'ecran titre de Minecraft (false = ecran vanilla, avec le solo : essais sur PC)")
                .define("actif", true);
        SERVEUR_ADRESSE = b.comment("Adresse du serveur rejoint par le bouton Jouer")
                .define("adresse", "play.heraliz.fr", o -> o instanceof String s && s.trim().matches("[A-Za-z0-9.-]+(:\\d{1,5})?"));
        SERVEUR_NOM = b.comment("Nom du serveur (liste des serveurs de Minecraft, ecran de connexion)")
                .define("nom", "HeraliZ");
        b.pop();
        b.comment("Qualite graphique (menu Echap) : se change en jeu, pas besoin de toucher ce fichier").push("graphismes");
        PROFIL_GRAPHIQUE = b.comment("Profil choisi : performance, equilibre ou qualite (filtrage anisotrope des blocs : aucun, x4, x8)")
                .define("profil", "performance", o ->o instanceof String s && PROFILS.contains(s.trim().toLowerCase(Locale.ROOT)));
        b.pop();
        SPEC = b.build();
    }

    private static boolean lienValide(Object o) {
        if (!(o instanceof String s)) return false;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() || t.startsWith("https://") || t.startsWith("http://");
    }

    /** Lien du bouton Discord (null : bouton cache). */
    @Nullable
    public static String discord() {
        String s = texte(DISCORD_LIEN, DISCORD_DEFAUT);
        if (s.isEmpty()) return null;
        return ANCIENS_DISCORD.contains(s) ? DISCORD_DEFAUT : s;
    }

    /** Valeur lue sans planter si la config n'est pas encore chargee (banc, ecran titre). */
    public static String texte(ForgeConfigSpec.ConfigValue<String> v, String defaut) {
        try {
            String s = v.get();
            return s == null ? defaut : s.trim();
        } catch (Exception ex) {
            return defaut;
        }
    }

    public static boolean oui(ForgeConfigSpec.BooleanValue v, boolean defaut) {
        try {
            return v.get();
        } catch (Exception ex) {
            return defaut;
        }
    }

    public static int entier(ForgeConfigSpec.IntValue v, int defaut) {
        try {
            return v.get();
        } catch (Exception ex) {
            return defaut;
        }
    }

    /** Couleur d'accent (rgb), rouge du logo par defaut. */
    public static int accent() {
        String s = texte(ACCENT, "E3201B").replace("#", "");
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            return 0xE3201B;
        }
    }
}
