package fr.heraliz.armory.client.cible;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.cible.CibleEssai;
import fr.heraliz.armory.network.PaquetsCible;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cible d'essai, côté joueur (03/11) : paquets reçus du serveur (écran du choix de l'armure, coups mesurés).
 * <ul>
 *    <li>chaque coup : le chiffre des dégâts reçus juste au-dessus de la cible (ChiffresCible), et une ligne au-dessus
 *    de la barre (zone, dégâts reçus, dégâts de l'arme, part prise par l'armure,
 *    distance ; tir de fusil à pompe : tous ses plombs ensemble) ;</li>
 *    <li>fin d'une série (4 s sans coup) : un récapitulatif dans le chat (tirs, temps, total, « mort au tir n° N » sur les
 *    20 PV d'un joueur, arme, armure de la cible), qu'on peut relire pour équilibrer.</li>
 * </ul>
 * Rien ne tourne sans série en cours (le tick sort tout de suite).
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class CibleClient {
    private CibleClient() {}

    /** Branché par la partie client sur PaquetsCible.RECEPTION_CLIENT. */
    public static void recevoir(Object paquet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (paquet instanceof PaquetsCible.Ouvrir o) {
            Entity e = mc.level.getEntity(o.entite());
            if (e instanceof CibleEssai) mc.setScreen(new EcranCible(o));
        } else if (paquet instanceof PaquetsCible.Touche t) {
            touche(mc, t);
        } else if (paquet instanceof PaquetsCible.Degats d) {
            ChiffresCible.ajouter(d);   // 08/11 : dégâts infligés à un autre joueur (petit chiffre)
        }
    }

    private static void touche(Minecraft mc, PaquetsCible.Touche t) {
        boolean finie = SerieCible.active && System.currentTimeMillis() - SerieCible.dernierMs > SerieCible.FIN_MS;
        if (finie || SerieCible.active && (t.entite() != SerieCible.entite || !t.tenue().equals(SerieCible.tenue))) {
            recapitulatif(mc);
        }
        SerieCible.ajouter(t, System.currentTimeMillis());
        mc.gui.setOverlayMessage(ligne(), false);
        ChiffresCible.ajouter(t);
    }

    /** « Tête · 7,5 reçus (arme 10,2, armure −26 %) · 12,4 m », tir entier pour un fusil à pompe. */
    static MutableComponent ligne() {
        PaquetsCible.Touche t = SerieCible.derniere;
        MutableComponent zone = TextesCible.zone(t.zone()).copy();
        if (SerieCible.plombs > 1) zone = Component.translatable("gui.heraliz_armory.cible.plombs", SerieCible.plombs);
        MutableComponent c = Component.translatable("gui.heraliz_armory.cible.ligne", zone, TextesCible.nombre(SerieCible.tirRecu),
                TextesCible.nombre(SerieCible.tirArme), TextesCible.pourcent(SerieCible.tirArme, SerieCible.tirRecu),
                TextesCible.nombre(t.distance()));
        if (SerieCible.tirPlafond) c.append(" · ").append(Component.translatable("gui.heraliz_armory.cible.plafond"));
        if (SerieCible.mortAu > 0) {
            c.append(" · ").append(Component.translatable("gui.heraliz_armory.cible.mort_au", SerieCible.mortAu).withStyle(ChatFormatting.RED));
        }
        return c.withStyle(t.zone() == PaquetsCible.TETE ? ChatFormatting.GOLD : ChatFormatting.WHITE);
    }

    /** Fin de la série : récapitulatif dans le chat. */
    private static void recapitulatif(Minecraft mc) {
        if (!SerieCible.active) return;
        SerieCible.active = false;
        PaquetsCible.Touche t = SerieCible.derniere;
        if (t == null || mc.player == null) return;
        float secondes = Math.max(0.0F, (SerieCible.dernierMs - SerieCible.debutMs) / 1000.0F);
        Component fin = SerieCible.mortAu > 0
                ? Component.translatable("gui.heraliz_armory.cible.mort_au", SerieCible.mortAu).withStyle(ChatFormatting.RED)
                : Component.translatable("gui.heraliz_armory.cible.pas_mort", TextesCible.nombre(SerieCible.recu),
                TextesCible.nombre(CibleEssai.PV_JOUEUR), SerieCible.tirsPourTuer()).withStyle(ChatFormatting.YELLOW);
        MutableComponent c = Component.translatable("gui.heraliz_armory.cible.recap", SerieCible.tirs, TextesCible.nombre(secondes),
                TextesCible.nombre(SerieCible.recu), TextesCible.nombre(SerieCible.arme),
                TextesCible.pourcent(SerieCible.arme, SerieCible.recu)).withStyle(ChatFormatting.GRAY);
        c.append(" · ").append(fin).append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(t.nomArme().copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                .append(TextesCible.tenue(SerieCible.tenue).withStyle(ChatFormatting.WHITE));
        mc.player.displayClientMessage(c, false);
    }

    /** Fin d'une série après 4 s sans coup : rien à faire sans série en cours. */
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !SerieCible.active) return;
        if (System.currentTimeMillis() - SerieCible.dernierMs > SerieCible.FIN_MS) recapitulatif(Minecraft.getInstance());
    }
}
