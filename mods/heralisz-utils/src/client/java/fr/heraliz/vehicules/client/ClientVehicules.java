package fr.heraliz.vehicules.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.vehicules.HeralizVehicules;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;
import org.ywzj.vehicle.all.AllItems;
import org.ywzj.vehicle.all.AllKeys;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Touches de Limitless Vehicle (moteur des véhicules) rangées pour ne plus tomber sur celles des mods du serveur
 * (audit du 04/10 : toutes les lettres, les chiffres, ², =, ), Retour arrière, Verr. Maj, les flèches et Tab sont déjà
 * prises par le jeu, TaCZ, Xaero, Curios, Reanimation, les emotes, la voix, les tags, les clans, les bases, les sacs…).
 * <ul>
 *   <li>Les touches du moteur ne sont actives qu'à bord d'un véhicule (contexte {@link #A_BORD}) : à pied elles ne font
 *   rien. Seules les touches de conduite (Z Q S D, Espace, clics) restent volontairement les mêmes que pour marcher : l'écran
 *   des touches les montre en rouge, comme dans le mod d'origine (Forge compare les touches quel que soit le contexte).</li>
 *   <li>Nouvelles touches par défaut ({@link #TABLE}) : sur des touches libres ; le bouton « Réinitialiser » des
 *   options y ramène.</li>
 *   <li>Un joueur qui avait déjà choisi une autre touche la garde (on ne change que les touches restées à leur valeur
 *   d'origine).</li>
 * </ul>
 * Le parachute (Espace en chute) passe par la touche Sauter comme avant : actif seulement en chute avec le parachute au
 * torse, et jamais signalé en conflit.
 */
public final class ClientVehicules {
    private ClientVehicules() {
    }

    /** À bord d'un véhicule Limitless Vehicle, sans écran ouvert. */
    public static final IKeyConflictContext A_BORD = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.screen == null && mc.player != null && mc.player.getVehicle() instanceof AbstractVehicle;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    /** En chute avec le parachute dorsal au torse, sans écran ouvert. */
    public static final IKeyConflictContext EN_CHUTE = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.screen == null && mc.player != null && !mc.player.onGround() && !mc.player.isPassenger()
                    && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(AllItems.PARACHUTE_PACK.get());
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    private static InputConstants.Key clavier(int glfw) {
        return InputConstants.Type.KEYSYM.getOrCreate(glfw);
    }

    /** Nouvelles touches par défaut (null : touche d'origine gardée, seul le contexte change). */
    static Map<KeyMapping, InputConstants.Key> table() {
        Map<KeyMapping, InputConstants.Key> t = new LinkedHashMap<>();
        // conduite (les 4 véhicules du serveur)
        t.put(AllKeys.FORWARD, null);                                     // Z (W QWERTY), comme marcher
        t.put(AllKeys.BACKWARD, null);                                    // S
        t.put(AllKeys.LEFT, null);                                        // Q (A QWERTY)
        t.put(AllKeys.RIGHT, null);                                       // D
        t.put(AllKeys.UP, null);                                          // Espace : frein à main
        t.put(AllKeys.LEAVE_VEHICLE, clavier(GLFW.GLFW_KEY_LEFT_SHIFT));  // J (tags) -> Maj gauche
        t.put(AllKeys.OPEN_INVENTORY, InputConstants.UNKNOWN);            // B (sac) -> aucune : plus de coffre (CoffreRetire)
        t.put(AllKeys.TOGGLE_ENGINE, clavier(GLFW.GLFW_KEY_WORLD_2));     // I (Reanimation) -> <
        t.put(AllKeys.LEFT_YAW, clavier(GLFW.GLFW_KEY_PAGE_UP));          // A QWERTY (jeter) -> Page préc. : benne du camion
        t.put(AllKeys.RIGHT_YAW, clavier(GLFW.GLFW_KEY_PAGE_DOWN));       // E (inventaire) -> Page suiv. : benne du camion
        t.put(AllKeys.SWITCH_VIEW, clavier(GLFW.GLFW_KEY_F4));            // V (voix, TaCZ) -> F4
        t.put(AllKeys.FREE_CAMERA, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4)); // C (TaCZ) -> souris 4
        t.put(AllKeys.INSPECT_VEHICLE, clavier(GLFW.GLFW_KEY_APOSTROPHE)); // O (TaCZ) -> ù
        t.put(AllKeys.CHANGE_SEAT, clavier(GLFW.GLFW_KEY_LEFT_BRACKET));  // ² (emotes) -> ^ (+ chiffre)
        t.put(AllKeys.MAGNIFICATION_CHANGE, null);                        // clic droit : zoom de la vue
        t.put(AllKeys.MAIN_WEAPON_SHOOT, null);                           // clic gauche : arme du véhicule
        // avions, hélicoptères, armes (aucun sur le serveur) : pavé numérique et touches de navigation
        t.put(AllKeys.DOWN, clavier(GLFW.GLFW_KEY_KP_0));                 // Maj gauche (sortir) -> 0 pavé
        t.put(AllKeys.FUNCTIONAL_UP, clavier(GLFW.GLFW_KEY_KP_8));        // flèche haut (vision nocturne) -> 8 pavé
        t.put(AllKeys.FUNCTIONAL_DOWN, clavier(GLFW.GLFW_KEY_KP_2));      // flèche bas (bases) -> 2 pavé
        t.put(AllKeys.FUNCTIONAL_LEFT, clavier(GLFW.GLFW_KEY_KP_4));      // flèche gauche (frappe aérienne) -> 4 pavé
        t.put(AllKeys.FUNCTIONAL_RIGHT, clavier(GLFW.GLFW_KEY_KP_6));     // flèche droite (carte Xaero) -> 6 pavé
        t.put(AllKeys.TOGGLE_LANDING_GEAR, clavier(GLFW.GLFW_KEY_KP_1));  // G (Curios, TaCZ…) -> 1 pavé
        t.put(AllKeys.TOGGLE_AIRBRAKE, clavier(GLFW.GLFW_KEY_KP_3));      // Ctrl gauche (sprint, emotes) -> 3 pavé
        t.put(AllKeys.TOGGLE_HOVER_MODE, clavier(GLFW.GLFW_KEY_KP_5));    // W (Xaero, TaCZ) -> 5 pavé
        t.put(AllKeys.TOGGLE_RADAR, clavier(GLFW.GLFW_KEY_KP_7));         // 3 (barre) -> 7 pavé
        t.put(AllKeys.TOGGLE_THERMAL_IMAGING, clavier(GLFW.GLFW_KEY_KP_9)); // 4 (barre) -> 9 pavé
        t.put(AllKeys.SECONDARY_WEAPON_SHOOT, clavier(GLFW.GLFW_KEY_KP_DECIMAL)); // 1 (barre) -> . pavé
        t.put(AllKeys.SECONDARY_WEAPON_SWITCH, clavier(GLFW.GLFW_KEY_KP_DIVIDE)); // 2 (barre) -> / pavé
        t.put(AllKeys.MULTI_WEAPON_SWITCH, clavier(GLFW.GLFW_KEY_KP_MULTIPLY));   // F (main secondaire) -> * pavé
        t.put(AllKeys.FIRE_CONTROL_LOCK, clavier(GLFW.GLFW_KEY_KP_SUBTRACT));     // R (TaCZ) -> - pavé
        t.put(AllKeys.PRESET_COORDINATE_INPUT, clavier(GLFW.GLFW_KEY_KP_ENTER));  // N (bases, voix) -> Entrée pavé
        t.put(AllKeys.DECOY_FLARE_LAUNCH, clavier(GLFW.GLFW_KEY_INSERT)); // Alt gauche (Ping Wheel) -> Inser
        t.put(AllKeys.SMOKE_GRENADE_LAUNCH, clavier(GLFW.GLFW_KEY_DELETE)); // H (TaCZ) -> Suppr
        t.put(AllKeys.TOGGLE_SEEKER, clavier(GLFW.GLFW_KEY_HOME));        // clic molette (TaCZ, choisir bloc) -> Origine
        t.put(AllKeys.TOGGLE_WEAPON_BAY, clavier(GLFW.GLFW_KEY_END));     // U (Xaero, corps) -> Fin
        t.put(AllKeys.SWITCH_SCOPE, clavier(GLFW.GLFW_KEY_F6));           // X (barres d'outils) -> F6
        t.put(AllKeys.TOGGLE_AEROBATIC_SMOKE, clavier(GLFW.GLFW_KEY_F7)); // L (progrès) -> F7
        return t;
    }

    /**
     * Touches d'ApricityUI (bibliothèque d'interface embarquée par le moteur) : Alt gauche (Ping Wheel) -> Ctrl droit ; ses
     * trois outils de développement (recharger, outils, gestionnaire de ressources : Fin, F12, F10) -> sans touche.
     */
    static Map<String, InputConstants.Key> tableApricity() {
        Map<String, InputConstants.Key> t = new LinkedHashMap<>();
        t.put("key.apricityui.release_mouse", clavier(GLFW.GLFW_KEY_RIGHT_CONTROL));
        t.put("key.apricityui.reload", InputConstants.UNKNOWN);
        t.put("key.apricityui.dev_tools", InputConstants.UNKNOWN);
        t.put("key.apricityui.resource_manager", InputConstants.UNKNOWN);
        return t;
    }

    public static void init(IEventBus modBus) {
        // Forge relit options.txt (touches des mods) APRÈS la fin du chargement des mods : on range donc les touches au
        // premier tick sans écran de chargement, une fois ces valeurs relues.
        MinecraftForge.EVENT_BUS.addListener(ClientVehicules::tick);
    }

    private static boolean range;

    private static void tick(TickEvent.ClientTickEvent e) {
        if (range || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.options == null) return;
        range = true;
        ranger();
    }

    static void ranger() {
        Field defaut = null;
        try {
            defaut = ObfuscationReflectionHelper.findField(KeyMapping.class, "f_90814_"); // defaultKey
        } catch (RuntimeException ex) {
            HeralizVehicules.LOG.warn("[vehicules] touche par defaut non modifiable (Reinitialiser ramenera l'ancienne)", ex);
        }
        int changees = 0;
        for (Map.Entry<KeyMapping, InputConstants.Key> en : table().entrySet()) {
            KeyMapping k = en.getKey();
            InputConstants.Key nouvelle = en.getValue();
            k.setKeyConflictContext(A_BORD);
            if (nouvelle == null) continue;
            if (k.isDefault() && !k.getKey().equals(nouvelle)) {
                k.setKey(nouvelle);
                changees++;
            }
            if (defaut != null) {
                try {
                    defaut.set(k, nouvelle);
                } catch (IllegalAccessException ex) {
                    HeralizVehicules.LOG.warn("[vehicules] touche par defaut de {} non modifiable", k.getName(), ex);
                }
            }
        }
        Map<String, InputConstants.Key> apricity = tableApricity();
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
            InputConstants.Key nouvelle = apricity.get(k.getName());
            if (nouvelle == null) continue;
            if (k.isDefault() && !k.getKey().equals(nouvelle)) {
                k.setKey(nouvelle);
                changees++;
            }
            if (defaut != null) {
                try {
                    defaut.set(k, nouvelle);
                } catch (IllegalAccessException ex) {
                    HeralizVehicules.LOG.warn("[vehicules] touche par defaut de {} non modifiable", k.getName(), ex);
                }
            }
        }
        AllKeys.OPEN_PARACHUTE.setKeyConflictContext(EN_CHUTE);
        KeyMapping.resetMapping();
        HeralizVehicules.LOG.info("[vehicules] touches du moteur : {} deplacees, actives seulement a bord", changees);
    }
}
