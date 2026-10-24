package fr.heralisz.utils.serveur;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Restes de HeraliZ Matchs (duels, ClashWar, zombies), retiré du serveur le 24/10 (focus DayZ) : un joueur sauvegardé
 * pendant un match pourrait garder des marques que plus personne ne retire. À chaque connexion :
 * <ul>
 *     <li>drapeau {@code heralisz:en_match} du {@code persistentData} retiré (sinon d'autres mods le croiraient en match
 *     pour toujours) ;</li>
 *     <li>étiquette {@code heraliz_reste_allonge} retirée (posée par les zombies et le casino, jamais utile à la
 *     connexion : le joueur resterait allongé) ;</li>
 *     <li>objets prêtés par un match (étiquette NBT {@code HeraliszMatch}) retirés de l'inventaire (comme le faisait le
 *     moteur des matchs) ; ligne dans le journal.</li>
 * </ul>
 * Le vrai inventaire d'un joueur resté en match (coffre de match, {@code heralisz_matchs/coffres/<uuid>.dat} à la racine
 * du serveur) n'est PAS rendu ici : à vérifier sur le serveur avant la mise en ligne. Fil serveur.
 */
public final class RestesMatchs {
    private static final String DRAPEAU_EN_MATCH = "heralisz:en_match";
    private static final String RESTE_ALLONGE = "heraliz_reste_allonge";
    private static final String NBT_PRET = "HeraliszMatch";

    private RestesMatchs() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(RestesMatchs::connexion);
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (p.getPersistentData().contains(DRAPEAU_EN_MATCH)) {
            p.getPersistentData().remove(DRAPEAU_EN_MATCH);
            HeraliszUtils.LOG.info("[utils] {} : ancien drapeau « en match » retiré (matchs retirés du serveur)", p.getGameProfile().getName());
        }
        p.removeTag(RESTE_ALLONGE);
        Inventory inv = p.getInventory();
        int retires = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.hasTag() && s.getTag().contains(NBT_PRET)) {
                inv.setItem(i, ItemStack.EMPTY);
                retires++;
            }
        }
        if (retires > 0) {
            inv.setChanged();
            HeraliszUtils.LOG.warn("[utils] {} : {} objet(s) prêté(s) par un ancien match retiré(s) de l'inventaire",
                    p.getGameProfile().getName(), retires);
        }
    }
}
