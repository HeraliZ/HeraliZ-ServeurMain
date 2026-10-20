package fr.heraliz.interfaces.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.interfaces.HeralizInterface;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Table UNIQUE des touches par défaut du serveur (demande du propriétaire : des touches les plus normales possible dès la
 * 1re connexion, aucune touche en double, seulement l'essentiel de chaque mod). Elle règle les touches des autres mods
 * chez le joueur, SANS toucher à leurs jars : chaque touche est retrouvée par son identifiant dans
 * {@code options.keyMappings} (une touche absente, mod retiré, est ignorée).
 * <ul>
 *     <li><b>Nouvelle valeur par défaut</b>, posée à CHAQUE lancement (le mod d'origine remet la sienne à chaque
 *     démarrage) : les boutons « Réinitialiser » et « Tout réinitialiser » d'Options &gt; Commandes y ramènent.</li>
 *     <li><b>Touche du joueur déplacée</b> seulement si elle vaut encore l'ancienne valeur par défaut (touche ET
 *     modificateur Ctrl / Maj / Alt) : un joueur qui avait choisi une autre touche la garde. Chaque touche n'est déplacée
 *     qu'une fois (repère {@code config/heraliz_interface-touches.txt}) : un joueur qui remet ensuite l'ancienne touche
 *     la garde aussi. Si la table change de valeur pour une touche, elle n'est redéplacée que si le joueur avait encore
 *     notre ancienne valeur.</li>
 *     <li><b>Touches cachées</b> ({@link #cachee}) : sans touche et retirées de l'écran Options &gt; Commandes
 *     ({@code mixin.KeyBindsListMixin}), mais laissées dans {@code options.keyMappings} (leurs mods les lisent toujours).
 *     Une touche cachée à laquelle le joueur avait donné sa propre touche reste affichée, pour qu'il puisse la voir et la
 *     changer.</li>
 * </ul>
 * Appliquée au premier tick sans écran de chargement (Forge relit les touches des mods dans options.txt à la fin du
 * chargement), après les autres mods : {@code InterfaceClient.touches}, priorité la plus basse, donc après le
 * rangement des touches des véhicules de HeraliZ Utils ({@code ClientVehicules}) fait au même tick.
 * <p>
 * Ne changent pas (choix du propriétaire) : Réanimation (Retour arrière, I, =), emotes (0, ²), TaCZ, voix (Verr. Maj,
 * « ) »), roue de ping (souris 5), clan K, base N, sac B, lunettes (flèche haut).
 * <p>
 * Selon le clavier ({@link #selonClavier}) : la carte du monde (HeraliZ Utils) sur la touche MARQUÉE « M » du clavier du
 * joueur (les codes de touche suivent les places d'un clavier QWERTY : sur AZERTY, le « M » est la touche du « ; »).
 * Sur AZERTY, cette touche était « Couper mon micro » de CaraCrea : le micro passe sur la touche voisine, marquée
 * « , » (libre). Sur QWERTY, la carte est sur M et le micro reste sur « ; ».
 */
public final class Touches {
    private Touches() {
    }

    /** Repère des touches déjà déplacées chez ce joueur : une ligne « identifiant=touche posée ». */
    private static final String REPERE = "heraliz_interface-touches.txt";

    /** Une ligne de la table : identifiant de la touche, nouvelle touche par défaut, cachée de l'écran des commandes. */
    private record Ligne(String nom, InputConstants.Key touche, boolean cachee) {
    }

    private static Ligne vers(String nom, int glfw) {
        return new Ligne(nom, InputConstants.Type.KEYSYM.getOrCreate(glfw), false);
    }

    private static Ligne aucune(String nom) {
        return new Ligne(nom, InputConstants.UNKNOWN, false);
    }

    private static Ligne cachee(String nom) {
        return new Ligne(nom, InputConstants.UNKNOWN, true);
    }

    private static final List<Ligne> TABLE = List.of(
            // ---------- touches déplacées (restent dans l'écran des commandes) ----------
            // Curios « Ouvrir l'inventaire Curios » (G) : G est déjà « Mode de tir » de TaCZ. U, libre à pied ; Curios
            // (masque, gants, ceinture, sac au dos de l'Armurerie) s'ouvre aussi par son bouton dans l'inventaire.
            vers("key.curios.open.desc", GLFW.GLFW_KEY_U),
            // CRac « Menu des pièces » (G) : G = mode de tir de TaCZ ; le clic droit maintenu avec l'outil de
            // construction ouvre déjà la même roue.
            aucune("key.crac.build_menu"),
            // TaCZ Addon « Changement rapide d'arme » (Tab) : Tab = liste des joueurs ; la barre d'objets suffit.
            aucune("key.taczaddon.switch_gun.desc"),
            // Minecraft « Progrès » (L) : pas de progrès sur le serveur (bouton Succès déjà retiré du menu Échap).
            aucune("key.advancements"),
            // Minecraft « Sauvegarder la barre » (C) : C = s'allonger de TaCZ ; barres d'outils utiles en créatif seulement.
            aucune("key.saveToolbarActivator"),
            // Minecraft « Charger la barre » (X) : même raison ; libère X pour la voix (ligne suivante).
            aucune("key.loadToolbarActivator"),
            // HeraliZ Emote « Inverser le son » (Ctrl gauche) : Ctrl gauche = Courir.
            aucune("key.heraliz_emote.reverse_sound"),
            // CaraCrea « Changer de mode » (chuchoter, parler, crier ; aucune touche) : X, libéré par « Charger la barre ».
            vers("key.caracrea.voice.mode", GLFW.GLFW_KEY_X),
            // Emotecraft « Roue des emotes » (² posé par HeraliZ Emote) : ² et la touche sous Entrée (*) sont celles des
            // menus de clients de triche, laissées libres pour l'alerte de Spartan. Y, libre.
            vers("key.emotecraft.fastchoose", GLFW.GLFW_KEY_Y),

            // ---------- touches cachées (sans touche, aucune utilité pour nos joueurs) ----------
            // TCTcore (H, base de MAtmos) : H = Inspecter de TaCZ ; elle n'ouvre que le menu de son auteur (liens vers
            // ses autres mods et sites).
            cachee("key.tctcore.tc_tcore_key"),
            // ModernFix « Ouvrir l'écran de configuration » : réglage technique, déjà sans touche.
            cachee("key.modernfix.config"),
            // EntityCulling : deux touches de débogage (activer / boîtes de calcul), déjà sans touche.
            cachee("key.entityculling.toggle"),
            cachee("key.entityculling.toggleBoxes"),
            // ApricityUI (interface du moteur des véhicules) : ses trois outils de développeur (Fin, F12, F10 d'origine,
            // déjà retirés par HeraliZ Utils). « Libérer la souris » reste visible.
            cachee("key.apricityui.reload"),
            cachee("key.apricityui.dev_tools"),
            cachee("key.apricityui.resource_manager"),
            // Emotecraft « Arrêter l'animation » : une emote s'arrête déjà en bougeant ou par Arrêter dans le menu des emotes.
            cachee("key.emotecraft.stop"),
            // CRac « Console admin » : réservée au staff, console coupée par défaut (sans touche d'origine).
            cachee("key.crac.admin_console"),
            // Moteur des véhicules (ywzj) : avions, hélicoptères et armes de véhicule, absents du serveur. HeraliZ Utils
            // les avait rangées sur le pavé numérique, Inser, Suppr, Origine, Fin, F6 et F7 : plus de touche du tout.
            // Gardées : les 16 touches de conduite et le parachute.
            cachee("key.ywzj_vehicle.down.desc"),                    // 0 du pavé (descendre)
            cachee("key.ywzj_vehicle.functional_up.desc"),           // 8 du pavé
            cachee("key.ywzj_vehicle.functional_down.desc"),         // 2 du pavé
            cachee("key.ywzj_vehicle.functional_left.desc"),         // 4 du pavé
            cachee("key.ywzj_vehicle.functional_right.desc"),        // 6 du pavé
            cachee("key.ywzj_vehicle.toggle_landing_gear.desc"),     // 1 du pavé (train d'atterrissage)
            cachee("key.ywzj_vehicle.toggle_airbrake.desc"),         // 3 du pavé (aérofrein)
            cachee("key.ywzj_vehicle.toggle_hover_mode.desc"),       // 5 du pavé (vol stationnaire)
            cachee("key.ywzj_vehicle.toggle_radar.desc"),            // 7 du pavé
            cachee("key.ywzj_vehicle.toggle_thermal_imaging.desc"),  // 9 du pavé
            cachee("key.ywzj_vehicle.secondary_weapon_shoot.desc"),  // . du pavé
            cachee("key.ywzj_vehicle.secondary_weapon_switch.desc"), // / du pavé
            cachee("key.ywzj_vehicle.multi_weapon_switch.desc"),     // * du pavé
            cachee("key.ywzj_vehicle.fire_control_lock.desc"),       // - du pavé
            cachee("key.ywzj_vehicle.preset_coordinate_input.desc"), // Entrée du pavé
            cachee("key.ywzj_vehicle.decoy_flare_launch.desc"),      // Inser (leurres)
            cachee("key.ywzj_vehicle.smoke_grenade_launch.desc"),    // Suppr (fumigènes)
            cachee("key.ywzj_vehicle.toggle_seeker.desc"),           // Origine (autodirecteur)
            cachee("key.ywzj_vehicle.toggle_weapon_bay.desc"),       // Fin (soute)
            cachee("key.ywzj_vehicle.switch_scope.desc"),            // F6 (viseur)
            cachee("key.ywzj_vehicle.toggle_aerobatic_smoke.desc"),  // F7 (fumée de voltige)
            // Minecraft « Caméra cinématique » et « Contours en spectateur » : sans touche d'origine, inutiles en jeu.
            cachee("key.smoothCamera"),
            cachee("key.spectatorOutlines"));

    /** Identifiants des touches cachées de l'écran Options &gt; Commandes. */
    private static final Set<String> CACHEES = TABLE.stream().filter(Ligne::cachee).map(Ligne::nom)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * Touche à ne pas montrer dans Options &gt; Commandes : dans la table des touches cachées ET sans touche (une touche
     * cachée que le joueur avait réglée lui-même reste visible, pour qu'il puisse la changer).
     */
    public static boolean cachee(KeyMapping k) {
        return k != null && CACHEES.contains(k.getName()) && k.isUnbound();
    }

    /** Les touches de l'écran des commandes, sans les touches cachées (en cas de souci : toutes, rien de perdu). */
    public static KeyMapping[] visibles(KeyMapping[] toutes) {
        try {
            List<KeyMapping> l = new ArrayList<>(toutes.length);
            for (KeyMapping k : toutes) {
                if (!cachee(k)) l.add(k);
            }
            return l.toArray(new KeyMapping[0]);
        } catch (RuntimeException e) {
            HeralizInterface.LOG.warn("[interface] touches cachées non retirées de l'écran des commandes", e);
            return toutes;
        }
    }

    /** Pose la table (voir la description de la classe). Appelée une fois par lancement, après les autres mods. */
    static void appliquer(Minecraft mc) {
        Field defaut;
        try {
            defaut = ObfuscationReflectionHelper.findField(KeyMapping.class, "f_90814_"); // defaultKey
        } catch (RuntimeException e) {
            HeralizInterface.LOG.warn("[interface] touches par défaut non modifiables : table des touches non posée", e);
            return;
        }
        Map<String, KeyMapping> parNom = new HashMap<>();
        for (KeyMapping k : mc.options.keyMappings) {
            if (k != null) parNom.put(k.getName(), k);
        }
        Path repere = FMLPaths.CONFIGDIR.get().resolve(REPERE);
        Map<String, String> deja = lire(repere);
        int deplacees = 0, absentes = 0;
        boolean repereChange = false;
        List<Ligne> table = new ArrayList<>(TABLE);
        table.addAll(selonClavier());
        for (Ligne l : table) {
            KeyMapping k = parNom.get(l.nom());
            if (k == null) {
                absentes++; // mod absent : rien à faire, la ligne servira s'il revient
                continue;
            }
            // ancienne valeur par défaut vue par le joueur : celle que nous avions posée, sinon celle du mod (à ce tick,
            // après le passage des autres mods, dont HeraliZ Utils pour les véhicules)
            String vu = deja.get(l.nom());
            InputConstants.Key ancienne;
            KeyModifier ancienModif;
            if (vu != null) {
                ancienne = touche(vu);
                ancienModif = KeyModifier.NONE;
            } else {
                ancienne = k.getDefaultKey();
                ancienModif = k.getDefaultKeyModifier();
            }
            if (!poserDefaut(defaut, k, l.touche())) continue;
            String pose = l.touche().getName();
            if (pose.equals(vu)) continue; // déjà déplacée une fois : le choix du joueur est respecté
            if (ancienne != null && k.getKey().equals(ancienne) && k.getKeyModifier() == ancienModif
                    && !(k.getKey().equals(l.touche()) && k.getKeyModifier() == KeyModifier.NONE)) {
                k.setKeyModifierAndCode(KeyModifier.NONE, l.touche());
                deplacees++;
            }
            deja.put(l.nom(), pose);
            repereChange = true;
        }
        // touche posée d'office par un mod sur ² ou sous Entrée (son constructeur ne passe pas par setKey) : retirée
        for (KeyMapping k : mc.options.keyMappings) {
            if (k != null && TouchesInterdites.interdite(k.getKey())) {
                k.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
                deplacees++;
            }
        }
        if (deplacees > 0) {
            KeyMapping.resetMapping();
            mc.options.save();
        }
        if (repereChange) ecrire(repere, deja);
        HeralizInterface.LOG.info("[interface] touches HeraliZ : {} lignes, {} touches déplacées, {} absentes (mod absent)",
                table.size(), deplacees, absentes);
    }

    /**
     * Lignes qui dépendent du clavier du joueur, calculées au lancement : la carte du monde sur la touche marquée « M » ;
     * si c'est celle de « Couper mon micro » de CaraCrea (clavier AZERTY : touche du « ; »), le micro passe sur la touche
     * marquée « , » (touche M d'un clavier QWERTY), libre : jamais deux touches sur la même.
     */
    private static List<Ligne> selonClavier() {
        int m = toucheMarquee("m", GLFW.GLFW_KEY_M);
        List<Ligne> l = new ArrayList<>(2);
        l.add(vers("key.heraliz_carte.ouvrir", m));
        if (m == GLFW.GLFW_KEY_SEMICOLON) l.add(vers("key.caracrea.voice.mute", GLFW.GLFW_KEY_M));
        return l;
    }

    /** Code de la touche qui porte cette lettre sur le clavier du joueur (sinon {@code defaut}, la place QWERTY). */
    private static int toucheMarquee(String lettre, int defaut) {
        int[] candidates = new int[2 + 26 + 1];
        candidates[0] = GLFW.GLFW_KEY_SEMICOLON;
        candidates[1] = GLFW.GLFW_KEY_COMMA;
        for (int i = 0; i < 26; i++) candidates[2 + i] = GLFW.GLFW_KEY_A + i;
        candidates[28] = GLFW.GLFW_KEY_APOSTROPHE;
        try {
            for (int k : candidates) {
                String nom = GLFW.glfwGetKeyName(k, 0);
                if (nom != null && nom.equalsIgnoreCase(lettre)) return k;
            }
        } catch (RuntimeException | LinkageError e) {
            HeralizInterface.LOG.warn("[interface] disposition du clavier illisible : touche « {} » laissée à sa place QWERTY", lettre);
        }
        return defaut;
    }

    /** Nouvelle touche par défaut (sans modificateur) ; faux si elle n'a pas pu être posée. */
    private static boolean poserDefaut(Field defaut, KeyMapping k, InputConstants.Key touche) {
        try {
            defaut.set(k, touche);
            if (k.getDefaultKeyModifier() != KeyModifier.NONE) {
                Field modif = KeyMapping.class.getDeclaredField("keyModifierDefault"); // champ ajouté par Forge
                modif.setAccessible(true);
                modif.set(k, KeyModifier.NONE);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeralizInterface.LOG.warn("[interface] touche par défaut de {} non modifiable", k.getName(), e);
            return false;
        }
    }

    private static InputConstants.Key touche(String nom) {
        try {
            return InputConstants.getKey(nom);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Map<String, String> lire(Path f) {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            if (!Files.exists(f)) return m;
            for (String ligne : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                int i = ligne.indexOf('=');
                if (ligne.startsWith("#") || i <= 0) continue;
                m.put(ligne.substring(0, i).trim(), ligne.substring(i + 1).trim());
            }
        } catch (Exception e) {
            HeralizInterface.LOG.warn("[interface] repère des touches illisible : {}", e.toString());
        }
        return m;
    }

    private static void ecrire(Path f, Map<String, String> m) {
        StringBuilder sb = new StringBuilder("# Touches déjà rangées par HeraliZ Interface (identifiant=touche posée). "
                + "Effacer ce fichier les range à nouveau au prochain lancement.\n");
        m.forEach((nom, touche) -> sb.append(nom).append('=').append(touche).append('\n'));
        try {
            Files.writeString(f, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            HeralizInterface.LOG.warn("[interface] repère des touches non écrit : {}", e.toString());
        }
    }
}
