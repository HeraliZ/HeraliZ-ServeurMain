package fr.heraliz.usure.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.client.sound.SoundPlayManager;
import fr.heraliz.usure.Usure;
import fr.heraliz.usure.regles.Profil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;

/**
 * Cote client, pour le joueur local seulement (TaCZ envoie aussi ces evenements pour les autres joueurs) : une arme
 * ruinee (d'apres son NBT, ecrit par le serveur) ne tire pas — le tir est annule avant de partir vers le serveur, avec un
 * clic a vide. Le serveur refuse aussi le tir de son cote : le client ne decide de rien. Pas d'enrayage (1.0.1).
 */
public final class ClientTirs {
    private ClientTirs() {
    }

    /** Dernier clic a vide (ms), pour ne pas le repeter a chaque image en tir automatique, et pour le flash du HUD. */
    private static long dernierClicMs;

    static void enregistrer(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, false, GunShootEvent.class, ClientTirs::avantTir);
        bus.addListener(EventPriority.HIGH, false, GunFireEvent.class, ClientTirs::avantBalle);
    }

    private static LocalPlayer joueur() {
        return Minecraft.getInstance().player;
    }

    /** Arme suivie et ruinee, d'apres son NBT. */
    private static boolean ruinee(LocalPlayer p, ItemStack arme) {
        Profil pr = ReglesClient.suivie(p, arme);
        return pr != null && Usure.etat(arme, pr) <= 0;
    }

    private static void avantTir(GunShootEvent e) {
        LocalPlayer p = joueur();
        if (e.getLogicalSide() != LogicalSide.CLIENT || p == null || e.getShooter() != p) return;
        ItemStack arme = e.getGunItemStack();
        if (!ruinee(p, arme)) return;
        e.setCanceled(true);
        long now = Util.getMillis();
        if (now - dernierClicMs > 350) {
            dernierClicMs = now;
            try {
                TimelessAPI.getGunDisplay(arme).ifPresent(d -> SoundPlayManager.playDryFireSound(p, d));
            } catch (RuntimeException | LinkageError ignored) {
                // TaCZ different : pas de clic
            }
        }
    }

    private static void avantBalle(GunFireEvent e) {
        LocalPlayer p = joueur();
        if (e.getLogicalSide() == LogicalSide.CLIENT && p != null && e.getShooter() == p && ruinee(p, e.getGunItemStack())) e.setCanceled(true);
    }

    /** ms depuis le dernier clic a vide sur une arme ruinee (flash du HUD). */
    public static long depuisClic() {
        return Util.getMillis() - dernierClicMs;
    }
}
