package fr.heralisz.clans.client;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.gui.AtelierScreen;
import fr.heralisz.clans.client.gui.ClanCreateScreen;
import fr.heralisz.clans.client.gui.FlagPanelScreen;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;

import java.util.List;

/**
 * Reception des messages serveur -> client : met a jour {@link ClientClans} et declenche ce qu'il faut (ecrans,
 * toasts, sons). Classe client seulement (appelee via DistExecutor depuis Packets, sur le fil de rendu).
 */
public final class ClientHandlers {
    private ClientHandlers() {
    }

    /** Snapshot : etat a jour ; si l'ecran de fondation est ouvert et que le clan existe maintenant, on passe a l'ecran du clan. */
    public static void snapshot(Packets.ClanSnapshotS2C m) {
        boolean avait = ClientClans.aUnClan();
        ClientClans.setCoutCreation(m.coutCreation());
        ClientClans.setSnapshot(m.snapshot());
        Minecraft mc = Minecraft.getInstance();
        if (m.snapshot() != null && !avait && mc.screen instanceof ClanCreateScreen cs && !cs.banniereSeule()) {
            ClansClient.ouvrir("clan");
        }
        if (m.snapshot() == null && avait && mc.screen instanceof FlagPanelScreen) {
            mc.setScreen(null);
        }
    }

    public static void invitations(Packets.InvitesS2C m) {
        ClientClans.setInvitations(m.invitations());
    }

    public static void tags(Packets.TagsS2C m) {
        ClientClans.setTags(m.tags());
    }

    public static void classement(Packets.ClassementS2C m) {
        ClientClans.setClassement(m.lignes());
    }

    /** Notice : son (si le serveur en demande un) + toast ; une ERREUR pendant la fondation va dans le bandeau de l'ecran. */
    public static void notice(Packets.NoticeS2C m) {
        HeraliszClans.LOG.debug("[clans] notice {} {}", m.genre(), m.args());
        SoundEvent son = Sons.parChemin(m.son());
        if (son != null) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(son, 1.0F));
        if (m.genre() == Packets.NoticeS2C.Genre.ERREUR && Minecraft.getInstance().screen instanceof ClanCreateScreen cs) {
            cs.erreurServeur(texteNotice(m.args()));
            return;
        }
        // refus d'ENREGISTRER pendant que l'atelier attend la reponse : bandeau dans l'atelier, qui reste ouvert
        if (m.genre() == Packets.NoticeS2C.Genre.ERREUR && Minecraft.getInstance().screen instanceof AtelierScreen at && at.enAttente()) {
            at.erreurServeur(texteNotice(m.args()));
            return;
        }
        ClansClient.toast(m);
    }

    /**
     * Texte d'une notice ERREUR / INFO, dans la langue du joueur : le serveur envoie la cle de traduction puis ses
     * arguments ; un argument qui est lui-meme une cle connue (libelle de permission, nom d'entite) est traduit aussi.
     * Un premier element qui n'est pas une cle est un texte deja ecrit, affiche tel quel.
     */
    public static String texteNotice(List<String> args) {
        if (args.isEmpty()) return "";
        String cle = args.get(0);
        if (!I18n.exists(cle)) return cle;
        Object[] a = new Object[args.size() - 1];
        for (int i = 1; i < args.size(); i++) {
            String s = args.get(i);
            a[i - 1] = s.indexOf('.') > 0 && I18n.exists(s) ? Component.translatable(s) : s;
        }
        return Component.translatable(cle, a).getString();
    }

    public static void ouvrir(Packets.OuvrirS2C m) {
        ClansClient.ouvrir(m.ecran());
    }

    public static void hudZone(Packets.TerritoireHudS2C m) {
        ClientClans.setHudZone(m);
    }

    public static void panneauDrapeau(Packets.PanneauDrapeauS2C m) {
        ClientClans.setPanneauDrapeau(m.zone());
        ClansClient.ouvrir("drapeau");
    }
}
