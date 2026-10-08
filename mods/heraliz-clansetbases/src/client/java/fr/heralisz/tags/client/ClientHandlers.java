package fr.heralisz.tags.client;

import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.client.gui.IndexScreen;
import fr.heralisz.tags.client.render.DecalBaker;
import fr.heralisz.tags.network.Packets;
import fr.heralisz.tags.registry.Sons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/**
 * Reception des messages du serveur (thread client).
 */
public final class ClientHandlers {
    private ClientHandlers() {
    }

    private static int dernierNombre = -1;

    /** Etat recu : un deblocage de plus qu'avant → son « tag debloque » (joue en local, jamais par le serveur). */
    public static void etat(Packets.EtatS2C msg) {
        int avant = dernierNombre;
        ClientTags.etat(msg.debloques(), msg.roue());
        ClientTags.porteeServeur(msg.portee());
        int apres = ClientTags.nombreDebloques();
        dernierNombre = apres;
        Minecraft mc = Minecraft.getInstance();
        if (avant >= 0 && apres > avant && mc.player != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(Sons.TAG_DEBLOQUE.get(), 1f, 0.8f));
        }
        if (mc.screen instanceof IndexScreen s) s.rafraichir();
    }

    /** Tag recu : enregistre puis cuit immediatement (thread de rendu) ; retourne l'entree creee. */
    public static ClientTags.TagActif tag(Packets.TagS2C msg) {
        ClientTags.TagActif t = ClientTags.ajouter(msg);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            try {
                t.rendu = DecalBaker.cuire(mc.level, msg);
                ClientTags.modifie();
            } catch (Exception e) {
                HeraliszTags.LOGGER.warn("[HeraliZ Tags] cuisson du tag #{} impossible", msg.id(), e);
            }
        }
        return t;
    }

    public static void tagRetire(Packets.TagRetireS2C msg) {
        ClientTags.retirer(msg.id());
    }

    public static void ouvrirIndex() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new IndexScreen());
    }

    /** Deconnexion : plus d'etat, plus de tags (tampons fermes), portee du serveur oubliee (le prochain serveur
     *  annoncera la sienne ; un changement de dimension, lui, la garde). */
    public static void deconnexion() {
        ClientTags.vider();
        ClientTags.porteeServeur(-1f);
        dernierNombre = -1;
    }
}
