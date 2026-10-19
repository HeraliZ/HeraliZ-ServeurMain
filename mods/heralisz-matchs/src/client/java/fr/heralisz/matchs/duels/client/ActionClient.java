package fr.heralisz.matchs.duels.client;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.DuelsConfig;
import fr.heralisz.matchs.duels.net.Vues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Chez le joueur, pendant qu'il amorce la bombe ou pirate l'ordinateur (16/10, Matchs 0.11.0 ; action annoncée par la vue
 * R&D du serveur) : ni tir, ni rafale, ni rechargement, ni coup de crosse (événements de TaCZ annulés côté joueur : pas
 * de faux tir à l'écran), visée de TaCZ coupée, molette et changement d'arme sans effet (la case en main est gardée).
 * L'immobilisation et les mêmes refus sont décidés par le serveur (duels.bombe.ReglesBombe) : ceci n'est que le
 * reflet chez le joueur. Champ de vision gardé tel quel (Matchs 0.11.2) : Minecraft le resserre quand la vitesse du joueur
 * baisse, et la vitesse x0 de l'immobilisation donnait un gros zoom pendant l'amorçage et le piratage. TaCZ par réflexion
 * (absent : rien). Fil client.
 */
final class ActionClient {
    private ActionClient() {
    }

    /** Case de la barre gardée en main pendant l'action ; -1 = aucune action. */
    private static int caseGardee = -1;
    @Nullable
    private static Method operateurDe, viser;

    static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(ActionClient::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ActionClient::molette);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ActionClient::champ);
        if (ModList.get() == null || !ModList.get().isLoaded("tacz")) return;
        refuser("com.tacz.guns.api.event.common.GunShootEvent", "getShooter");
        refuser("com.tacz.guns.api.event.common.GunFireEvent", "getShooter");
        refuser("com.tacz.guns.api.event.common.GunReloadEvent", "getEntity");
        refuser("com.tacz.guns.api.event.common.GunMeleeEvent", "getShooter");
        try {
            Class<?> op = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            operateurDe = op.getMethod("fromLocalPlayer", LocalPlayer.class);
            viser = op.getMethod("aim", boolean.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HeraliszMatchs.LOG.warn("[duels] visee TaCZ du joueur introuvable ({})", ex.toString());
        }
    }

    /** Vrai pendant l'amorçage ou le piratage du joueur. */
    static boolean bloque() {
        Vues.RdVue v = DuelHud.vue();
        return v != null && v.action() != Vues.ACTION_AUCUNE;
    }

    @SuppressWarnings("unchecked")
    private static void refuser(String classe, String auteur) {
        try {
            Class<?> c = Class.forName(classe);
            Method qui = c.getMethod(auteur), cote = c.getMethod("getLogicalSide");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, (Class<Event>) c, e -> {
                try {
                    if (!e.isCancelable() || !bloque() || !"CLIENT".equals(String.valueOf(cote.invoke(e)))) return;
                    LocalPlayer moi = Minecraft.getInstance().player;
                    if (moi != null && qui.invoke(e) == moi) e.setCanceled(true);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    // laissé à TaCZ (le serveur refuse de toute façon)
                }
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HeraliszMatchs.LOG.warn("[duels] evenement TaCZ {} introuvable chez le joueur ({})", classe, ex.toString());
        }
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !bloque()) {
            caseGardee = -1;
            return;
        }
        if (caseGardee < 0) caseGardee = p.getInventory().selected;
        else if (p.getInventory().selected != caseGardee) p.getInventory().selected = caseGardee;
        if (operateurDe != null && viser != null) {
            try {
                Object op = operateurDe.invoke(null, p);
                if (op != null) viser.invoke(op, false);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                viser = null;
            }
        }
    }

    /**
     * Champ de vision : pendant l'action (ou tant que le modificateur d'immobilisation du serveur est sur le joueur), aucun
     * zoom dû à la vitesse nulle ; le joueur ne court ni ne vole pendant l'action : champ de vision normal (1).
     */
    private static void champ(ComputeFovModifierEvent e) {
        LocalPlayer moi = Minecraft.getInstance().player;
        if (moi == null || e.getPlayer() != moi) return;
        AttributeInstance vitesse = moi.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean immobile = vitesse != null && vitesse.getModifier(DuelsConfig.IMMOBILE_VITESSE) != null;
        if (immobile || bloque()) e.setNewFovModifier(1.0f);
    }

    /** Molette : pas de changement d'arme pendant l'action. */
    private static void molette(InputEvent.MouseScrollingEvent e) {
        if (Minecraft.getInstance().screen == null && bloque()) e.setCanceled(true);
    }
}
