package fr.heralisz.matchs.moteur.client;

import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Cartes de Xaero pendant les matchs (RETOURS du 01/10 §4.14), cote client, tant que le serveur signale le joueur « en
 * match » ({@code EtatMatchS2C}, {@link MoteurClient#cartesMasquees()}) :
 * <ul>
 *     <li>mini-carte (Xaero's Minimap, edition normale {@code xaerominimap} ou « Fair Play » {@code xaerominimapfair}) :
 *     son calque Forge ({@code <modid>:xaero}, enregistre au-dessus de {@code minecraft:portal}, qui dessine la
 *     mini-carte, le radar et les points de passage du HUD) n'est pas rendu ;</li>
 *     <li>carte du monde (Xaero's World Map) : l'ouverture de son ecran ({@code xaero.map.gui.GuiMap}, touche M ou
 *     depuis la mini-carte) est annulee, avec une ligne discrete au-dessus de la barre d'objets.</li>
 * </ul>
 * Methode retenue apres decompilation (Xaeros_Minimap_FP24.2.0, XaerosWorldMap_1.39.12) : la mini-carte accepte bien un
 * code de format envoye dans le chat ({@code §n§o§m§i§n§i§m§a§p}, retabli par {@code §r§e§s§e§t§x§a§e§r§o}) mais il
 * laisse une ligne vide dans le chat et la carte du monde n'a aucun code equivalent (seulement un effet, qui exigerait le
 * mod sur le serveur) ; notre mod etant obligatoire cote client, le masquage local couvre les deux sans rien afficher.
 * A la sortie du match, rien a retablir : les evenements ne sont simplement plus annules.
 */
public final class CartesMasquees {
    private CartesMasquees() {
    }

    private static long dernierAvis;
    private static boolean journalCalque, journalEcran;

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CartesMasquees::calque);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CartesMasquees::ecran);
    }

    /** Vrai si ce calque est celui de la mini-carte de Xaero ({@code xaerominimap:xaero} ou {@code xaerominimapfair:xaero}). */
    public static boolean calqueXaero(ResourceLocation id) {
        return id.getNamespace().startsWith("xaerominimap") || id.getPath().equals("xaero");
    }

    /** Vrai si cet ecran est la carte du monde de Xaero. */
    public static boolean ecranCarte(Screen s) {
        return s != null && s.getClass().getName().startsWith("xaero.map.gui.GuiMap");
    }

    private static void calque(RenderGuiOverlayEvent.Pre e) {
        if (!MoteurClient.cartesMasquees() || !calqueXaero(e.getOverlay().id())) return;
        e.setCanceled(true);
        if (!journalCalque) {
            journalCalque = true;
            HeraliszMatchs.LOG.info("[matchs] mini-carte de Xaero masquee pendant le match (calque {})", e.getOverlay().id());
        }
    }

    private static void ecran(ScreenEvent.Opening e) {
        if (!MoteurClient.cartesMasquees() || !ecranCarte(e.getNewScreen())) return;
        e.setCanceled(true);
        if (!journalEcran) {
            journalEcran = true;
            HeraliszMatchs.LOG.info("[matchs] carte du monde de Xaero refusee pendant le match ({})", e.getNewScreen().getClass().getName());
        }
        long now = Util.getMillis();
        if (now - dernierAvis > 1500) {
            dernierAvis = now;
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("matchs.carte_indisponible"), false);
        }
    }
}
