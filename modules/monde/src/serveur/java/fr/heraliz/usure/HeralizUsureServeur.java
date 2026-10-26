package fr.heraliz.usure;

import fr.heraliz.usure.regles.Regles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie SERVEUR de {@link HeralizUsure} (docs/METHODE_CLIENT_SERVEUR.md §5) : les ecouteurs serveur que le constructeur
 * du @Mod inscrivait (tirs, reglages du serveur, conversion Gun Durability, commandes), dans l'ordre d'origine, et le
 * traitement du paquet de l'ecran d'administration. Code d'origine deplace tel quel.
 */
public final class HeralizUsureServeur {
    private HeralizUsureServeur() {
    }

    /** Appele par serveur.PartieServeur, pendant le constructeur du @Mod (a la place d'origine des ecouteurs). */
    public static void init() {
        IEventBus forge = MinecraftForge.EVENT_BUS;
        TirsServeur.enregistrer(forge);
        forge.addListener(ReglesServeur::onDemarrage);
        forge.addListener(ReglesServeur::onArret);
        forge.addListener(ReglesServeur::onTick);
        forge.addListener(ReglesServeur::onDatapackSync);
        forge.addListener(HeralizUsureServeur::onConnexion);
        forge.addListener(HeralizUsureServeur::onEquipement);
        forge.addListener(Commandes::onRegister);
        // ecran d'administration (paquet EnregistrerRegles) : meme traitement qu'a l'origine (droits verifies)
        Reseau.RECEPTION_SERVEUR.brancher(r -> {
            if (r.paquet() instanceof Reseau.EnregistrerRegles m) ReglesServeur.enregistrerDepuisEcran(r.joueur(), m.regles());
        });
    }

    /** Connexion : les armes marquees par Gun Durability (inventaire, coffre de l'End) sont converties d'un coup. */
    private static void onConnexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        int n = migrer(p.getInventory()) + migrer(p.getEnderChestInventory());
        if (n > 0) HeralizUsure.LOG.info("[usure] {} arme(s) de Gun Durability converties pour {}", n, p.getGameProfile().getName());
    }

    private static int migrer(Container c) {
        Regles r = ReglesServeur.get();
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            String id = s.isEmpty() ? null : Usure.gunId(s);
            if (id != null && Usure.migrer(s, r.profil(id))) n++;
        }
        if (n > 0) c.setChanged();
        return n;
    }

    /** Arme prise en main (sortie d'un coffre, d'un sac...) : conversion gundb immediate. */
    private static void onEquipement(LivingEquipmentChangeEvent e) {
        if (e.getSlot() != EquipmentSlot.MAINHAND || !(e.getEntity() instanceof ServerPlayer)) return;
        ItemStack s = e.getTo();
        String id = s.isEmpty() ? null : Usure.gunId(s);
        if (id != null) Usure.migrer(s, ReglesServeur.get().profil(id));
    }
}
