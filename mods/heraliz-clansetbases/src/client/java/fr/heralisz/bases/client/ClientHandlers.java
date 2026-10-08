package fr.heralisz.bases.client;

import fr.heralisz.bases.network.Packets;

/**
 * Reception des paquets serveur -> client (fil client) : l'etat va dans {@link ClientBases}, puis l'affichage est
 * delegue a {@link BasesClient} (ecrans, toasts, HUD : agent interfaces).
 */
public final class ClientHandlers {
    private ClientHandlers() {
    }

    public static void etat(Packets.EtatBaseS2C m) {
        ClientBases.setFaux(false);
        ClientBases.setEtat(m.vue());
        // plan perime (modele recharge sur le serveur) : on le redemande
        if (m.vue() != null && m.vue().planEmpreinte() != 0 && (ClientBases.plan() == null
                || ClientBases.plan().empreinte() != m.vue().planEmpreinte())) {
            BasesClient.demander(Packets.ActionC2S.Genre.PLAN, "");
        }
    }

    public static void plan(Packets.PlanS2C m) {
        ClientBases.setPlan(m.plan());
    }

    public static void pillables(Packets.PillablesS2C m) {
        ClientBases.setPillables(m.bases(), m.fenetre());
    }

    public static void ouvrir(Packets.OuvrirS2C m) {
        BasesClient.ouvrir(m.ecran());
    }

    public static void notice(Packets.NoticeS2C m) {
        BasesClient.notice(m);
    }

    public static void hud(Packets.HudS2C m) {
        ClientBases.setHud(m);
    }

    public static void teleport(Packets.TeleportS2C m) {
        fr.heralisz.bases.HeraliszBases.LOG.debug("[bases] teleportation recue : {} {} {} ticks {}", m.genre(), m.etat(), m.totalTicks(), m.raison());
        ClientBases.setTeleport(m);
        BasesClient.teleport(m);
    }
}
