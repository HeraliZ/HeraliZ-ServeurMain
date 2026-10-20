package fr.heralisz.bases.client;

import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.client.gui.BaseScreen;
import fr.heralisz.bases.client.gui.PillerScreen;
import fr.heralisz.bases.client.gui.hud.BasesHud;
import fr.heralisz.bases.client.gui.kit.Ui;
import fr.heralisz.bases.network.BasesNet;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.bases.registry.BasesRegistry;
import fr.heralisz.bases.registry.Sons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Cote client des bases : points d'entree des ecrans, reception des notices et des teleportations, touche N.
 * <ul>
 * <li>{@link #ouvrir()} : POINT D'ENTREE STATIQUE pour l'ecran du clan (agent d'integration) : demande l'etat au serveur
 * puis ouvre l'ecran BASE ; {@link #ouvrir(Screen)} fait de meme et y revient a la fermeture ;</li>
 * <li>{@link #ouvrirPiller(Screen)} : ecran PILLER ;</li>
 * <li>{@link #ouvrir(Packets.OuvrirS2C.Ecran)} : ordre du serveur (apres l'etat qu'il affiche ; {@code /base},
 * {@code /base piller}, touche N) ;</li>
 * <li>{@link #notice} : toasts et refus ({@link BasesHud}), {@link #teleport} : compte a rebours (l'ecran ouvert se ferme
 * pour laisser voir le monde : il faut rester immobile 10 s).</li>
 * </ul>
 * Envois au serveur : {@link #demander}. Aucune decision n'est prise ici : le serveur verifie tout.
 */
public final class BasesClient {
    private BasesClient() {
    }

    /** Ecran auquel revenir quand l'ecran demande s'ouvrira (ouverture depuis l'ecran du clan). */
    @Nullable
    private static Screen retourEnAttente;
    private static long demandeLe;

    /** Envoie une action au serveur (ouvrir, creer, entrer « nord », debloquer « 2 », piller « TAG »...). */
    public static void demander(Packets.ActionC2S.Genre genre, String arg) {
        if (Minecraft.getInstance().getConnection() == null) return;
        BasesNet.toServer(new Packets.ActionC2S(genre, arg == null ? "" : arg));
    }

    /** Ouvre l'ecran BASE (etat demande au serveur, l'ecran s'ouvre a la reponse). Point d'entree de l'ecran du clan. */
    public static void ouvrir() {
        ouvrir((Screen) null);
    }

    /** Ouvre l'ecran BASE ; a la fermeture, on revient a {@code retour} (l'ecran du clan par exemple). */
    public static void ouvrir(@Nullable Screen retour) {
        retourEnAttente = retour;
        demandeLe = System.currentTimeMillis();
        demander(Packets.ActionC2S.Genre.OUVRIR, "");
    }

    /** Ouvre l'ecran PILLER (liste demandee au serveur) ; a la fermeture, on revient a {@code retour}. */
    public static void ouvrirPiller(@Nullable Screen retour) {
        retourEnAttente = retour;
        demandeLe = System.currentTimeMillis();
        demander(Packets.ActionC2S.Genre.OUVRIR, "piller");
    }

    /** Ouvre un ecran (l'etat est deja dans {@link ClientBases}) ; si le meme ecran est deja ouvert, il se met a jour seul. */
    public static void ouvrir(Packets.OuvrirS2C.Ecran ecran) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // retour demande il y a moins de 10 s seulement (sinon : ouverture par commande, sans retour)
        Screen retour = System.currentTimeMillis() - demandeLe < 10_000 ? retourEnAttente : null;
        retourEnAttente = null;
        if (ecran == Packets.OuvrirS2C.Ecran.PILLER) {
            if (!(mc.screen instanceof PillerScreen)) mc.setScreen(new PillerScreen(retour));
        } else {
            if (!(mc.screen instanceof BaseScreen)) mc.setScreen(new BaseScreen(retour));
        }
    }

    /** Notification du serveur : son du paquet, puis toast (ou refus discret) dans le HUD. */
    public static void notice(Packets.NoticeS2C n) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        jouer(n.son());
        BasesHud.notice(n);
    }

    /** Teleportation differee (DEBUT / ANNULEE / FAITE) : compte a rebours du HUD ; au DEBUT, nos ecrans se ferment. */
    public static void teleport(Packets.TeleportS2C t) {
        BasesHud.teleport(t);
        Minecraft mc = Minecraft.getInstance();
        if (t.etat() == Packets.TeleportS2C.Etat.DEBUT && (mc.screen instanceof BaseScreen || mc.screen instanceof PillerScreen)) {
            mc.setScreen(null);
        }
    }

    /** Joue un son du mod par son chemin (« » = rien). */
    public static void jouer(String chemin) {
        SoundEvent s = Sons.parChemin(chemin);
        if (s != null) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, 1.0f, 0.8f));
    }

    /** Bus du mod : particules maison, touche N, cache de glyphes vide au rechargement des ressources. */
    @Mod.EventBusSubscriber(modid = HeraliszBases.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Mod_ {
        private Mod_() {
        }

        @SubscribeEvent
        public static void particules(RegisterParticleProvidersEvent e) {
            e.registerSpriteSet(BasesRegistry.POUSSIERE_COQUE.get(), PoussiereParticle.Fabrique::new);
        }

        @SubscribeEvent
        public static void touches(RegisterKeyMappingsEvent e) {
            // 20/10 : plus de touche N pour l'ancien écran des bunkers (bases CRac à la place, choix du propriétaire)
        }

        @SubscribeEvent
        public static void rechargement(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> Ui.viderCache());
        }
    }

    /** Bus Forge : touche N, deconnexion = etat vide. */
    @Mod.EventBusSubscriber(modid = HeraliszBases.MODID, value = Dist.CLIENT)
    public static final class Forge_ {
        private Forge_() {
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            // touche N retirée le 20/10 (ancien système des bunkers)
        }

        @SubscribeEvent
        public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
            ClientBases.vider();
            BasesHud.vider();
        }
    }
}
