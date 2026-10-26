package fr.heralisz.utils.cycle;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cycle jour / nuit, côté joueur (25/10) : prédit l'heure entre deux envois du serveur. La règle doDaylightCycle est à
 * false sur le serveur : sans ce prédicteur, le jeu du joueur garderait l'heure figée et la recevrait par sauts, une fois
 * par seconde (soleil et lumière qui avancent par à-coups, surtout la nuit, plus rapide).
 *
 * <p>À la fin de chaque tick, dans un monde où le serveur dit que le cycle tourne (réglages {@link Cycle.Reglages}, monde
 * sans heure fixe) et dont l'heure est figée chez le joueur (doDaylightCycle reçue à false) : un tick réel de plus sur la
 * même courbe que le serveur ({@link Cycle.Horloge}), écrit dans les données du monde du joueur
 * ({@code ClientLevelData.setDayTime}, jamais {@code ClientLevel.setDayTime}, qui remettrait la règle du joueur à true).
 * L'heure envoyée chaque seconde par le serveur recale l'horloge : un petit écart (40 ticks de Minecraft au plus) ou un
 * vrai saut (/cycle, /time : plus de 2 400) est repris d'un coup ; entre les deux, il est rattrapé en douceur (10 % par
 * tick), sans jamais faire reculer le soleil : en avance, on ralentit.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, value = Dist.CLIENT)
public final class CycleClient {
    private CycleClient() {
    }

    /** Écart (ticks de Minecraft) repris d'un coup ; au-delà de SAUT, c'est un vrai saut d'heure, repris d'un coup aussi. */
    private static final double ECART_DIRECT = 40.0, SAUT = 2400.0;
    /** Part de l'écart rattrapée à chaque tick pendant un lissage. */
    private static final double LISSAGE = 0.1;

    private static Cycle.Reglages reglages;
    private static final Cycle.Horloge H = new Cycle.Horloge();
    /** Monde sur lequel l'horloge est calée (null : à recaler au prochain tick). */
    private static ClientLevel niveau;
    /** Heure prédite du serveur, heure montrée au joueur (en double) et dernière heure écrite dans le monde. */
    private static double reference, affiche;
    private static long ecrit;

    /** Réglages reçus du serveur (branché par client.PartieClient) : l'horloge repart de l'heure du monde, sans lissage. */
    public static void recevoir(Cycle.Reglages r) {
        reglages = r;
        niveau = null;
    }

    @SubscribeEvent
    public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
        reglages = null;
        niveau = null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel l = mc.level;
        Cycle.Reglages r = reglages;
        if (l == null || r == null || !r.actif() || l.dimensionType().hasFixedTime()
                || l.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            niveau = null;                                       // le jeu (ou personne) fait avancer l'heure : on ne touche à rien
            return;
        }
        if (mc.isPaused()) return;                               // solo en pause : le serveur intégré l'est aussi
        ClientLevel.ClientLevelData donnees = l.getLevelData();
        long d = donnees.getDayTime();
        if (l != niveau) {
            niveau = l;
            caler(d, r);
            affiche = d;
        } else if (d != ecrit) {                                 // heure reçue du serveur (envoi de chaque seconde)
            double ecart = d - affiche;
            caler(d, r);
            if (Math.abs(ecart) <= ECART_DIRECT || Math.abs(ecart) > SAUT) affiche = d;
        }
        double avant = reference;
        if (!r.pause()) {
            H.avancer();
            reference = H.temps();
        }
        double pas = (reference - avant) + (reference - affiche) * LISSAGE;
        if (pas > 0) affiche += pas;                             // jamais en arrière
        long v = (long) Math.floor(affiche + 1e-6);
        if (v != d) donnees.setDayTime(v);
        ecrit = v;
    }

    private static void caler(long d, Cycle.Reglages r) {
        H.caler(d, r.jourTicks(), r.nuitTicks());
        reference = d;
    }
}
