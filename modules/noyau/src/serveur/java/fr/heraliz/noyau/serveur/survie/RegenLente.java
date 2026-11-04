package fr.heraliz.noyau.serveur.survie;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.api.ATerre;
import fr.heraliz.noyau.api.CombatTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vie qui remonte toute seule (04/11, choix du propriétaire : « on récupère nos PV très vite », réglage « lente »). Dans
 * Minecraft, ventre plein et saturation rendent 1 cœur par seconde (et la faim ralentie par {@code faim_facteur} rendait
 * chaque cœur trois fois moins cher). Réglage {@code [survie] regeneration} de {@code heraliz_noyau-server.toml}, relu sans
 * redémarrer :
 * <ul>
 * <li>« lente » : la règle {@code naturalRegeneration} est mise à false par le serveur, et la vie remonte ici :
 * {@code regen_pv} toutes les {@code regen_intervalle_s} secondes, barre de faim d'au moins {@code regen_faim_min}, jamais
 * en combat (tag de combat), à terre, ni moins de {@code regen_apres_degats_s} secondes après avoir pris des dégâts ; la
 * fatigue suit celle de Minecraft ({@code regen_fatigue_par_pv}, puis faim_facteur) ;</li>
 * <li>« coupee » : règle à false, rien ici (seulement les soins) ;</li>
 * <li>« minecraft » : règle remise à true, rien ici.</li>
 * </ul>
 * Une fois par seconde, tous les joueurs en une boucle ; rien d'autre ne tourne. Serveur seulement.
 */
public final class RegenLente {
    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();
    /** Secondes passées dans les conditions depuis le dernier palier, par joueur. */
    private static final Map<UUID, Integer> ATTENTE = new HashMap<>();
    /** Tick du serveur du dernier dégât reçu, par joueur. */
    private static final Map<UUID, Integer> DERNIER_DEGAT = new HashMap<>();
    /** Mode dont la règle du monde a été posée (null : pas encore). */
    private static String regle;

    private RegenLente() {
    }

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDamageEvent e) -> {
            if (e.getAmount() > 0 && e.getEntity() instanceof ServerPlayer p) DERNIER_DEGAT.put(p.getUUID(), p.server.getTickCount());
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && e.getServer().getTickCount() % 20 == 7) seconde(e.getServer());
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            ATTENTE.remove(e.getEntity().getUUID());
            DERNIER_DEGAT.remove(e.getEntity().getUUID());
        });
    }

    private static void seconde(MinecraftServer server) {
        String mode = NoyauConfig.regenMode;
        poserRegle(server, mode);
        if (!"lente".equals(mode)) {
            ATTENTE.clear();
            return;
        }
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID u = p.getUUID();
            Integer d = DERNIER_DEGAT.get(u);
            if (!p.isAlive() || p.isCreative() || p.isSpectator() || !p.isHurt() || ATerre.aTerre(p) || CombatTag.estTague(p)
                    || p.getFoodData().getFoodLevel() < NoyauConfig.regenFaimMin
                    || d != null && t - d < NoyauConfig.regenApresDegatsS * 20) {
                ATTENTE.remove(u);
                continue;
            }
            if (ATTENTE.merge(u, 1, Integer::sum) < NoyauConfig.regenIntervalleS) continue;
            ATTENTE.remove(u);
            float pv = NoyauConfig.regenPv;
            p.heal(pv);
            p.getFoodData().addExhaustion(NoyauConfig.regenFatigueParPv * pv);
        }
    }

    /**
     * Règle naturalRegeneration : false sauf en mode « minecraft », dans CHAQUE monde (sous Mohist, un monde peut avoir ses
     * propres règles ; un monde chargé plus tard, comme les arènes du ClashWar, la reçoit à la seconde suivante). Une
     * lecture par monde et par seconde ; écrite seulement si elle diffère. Journal à chaque changement de mode.
     */
    private static void poserRegle(MinecraftServer server, String mode) {
        boolean minecraft = "minecraft".equals(mode);
        for (net.minecraft.server.level.ServerLevel l : server.getAllLevels()) {
            GameRules.BooleanValue r = l.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION);
            if (r.get() != minecraft) r.set(minecraft, server);
        }
        if (!mode.equals(regle)) {
            regle = mode;
            LOG.info("[survie] vie qui remonte : {} (règle naturalRegeneration = {}){}", mode, minecraft,
                    "lente".equals(mode) ? " ; " + NoyauConfig.regenPv + " PV toutes les " + NoyauConfig.regenIntervalleS + " s" : "");
        }
    }
}
