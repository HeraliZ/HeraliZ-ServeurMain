package fr.heraliz.clashwar.moteur.combat;

import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.noyau.api.CombatTag;
import fr.heraliz.clashwar.moteur.api.MatchApi;
import fr.heraliz.noyau.api.ATerre;
import fr.heraliz.clashwar.moteur.net.PaquetsMoteur;
import fr.heraliz.clashwar.reseau.MatchsNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Regles propres aux joueurs en match ({@link MatchApi#enMatch}) (RETOURS du 01/10 §3 et §4.14) :
 * <ul>
 *     <li>malus d'ArkaniaZ (effets de geckolib222 : saignement, os casse, irradiation, infections, intoxication
 *     alimentaire, ancien tag de combat) retires a l'entree puis toutes les secondes (leur refus est dans le noyau) ;</li>
 *     <li>etat « en match » pousse au client ({@code EtatMatchS2C}) a chaque changement (HUD, emotes refusees).</li>
 * </ul>
 * L'etat est pousse tout de suite par {@link MatchApi#rejoindre}/{@link MatchApi#quitter} et reconcilie toutes les
 * 10 ticks (parties fermees, joueurs detaches par UUID, reconnexions). Fil serveur.
 */
public final class RestrictionsMatch {
    private RestrictionsMatch() {
    }

    /** Effets d'ArkaniaZ (geckolib222) refuses et retires en match. */
    public static final Set<ResourceLocation> MALUS = Set.of(
            new ResourceLocation("geckolib", "bleeding"),
            new ResourceLocation("geckolib", "broken_bone"),
            new ResourceLocation("geckolib", "irradiated"),
            new ResourceLocation("geckolib", "parasitic_infection"),
            new ResourceLocation("geckolib", "bacterial_infection"),
            new ResourceLocation("geckolib", "food_poisoning"),
            new ResourceLocation("geckolib", "combat_log"));

    /** Etat envoye a chaque joueur (en match, cartes masquees). */
    private static final Map<UUID, Integer> ENVOYE = new HashMap<>();
    /** Effets du registre correspondant a {@link #MALUS} (resolus a la premiere utilisation ; geckolib222 absent = vide). */
    private static List<MobEffect> malus;

    // ------------------------------------------------------------------ effets
    private static List<MobEffect> malus() {
        if (malus == null) {
            List<MobEffect> l = new ArrayList<>();
            for (ResourceLocation id : MALUS) {
                if (!ForgeRegistries.MOB_EFFECTS.containsKey(id)) continue;
                MobEffect m = ForgeRegistries.MOB_EFFECTS.getValue(id);
                if (m != null) l.add(m);
            }
            malus = List.copyOf(l);
            HeralizClashWar.LOG.info("[matchs] malus d'ArkaniaZ surveilles en match : {} / {}", malus.size(), MALUS.size());
        }
        return malus;
    }

    /** Retire les malus d'ArkaniaZ du joueur ; rend leur nombre. */
    public static int retirerMalus(ServerPlayer p) {
        List<MobEffect> l = malus();
        int n = 0;
        for (int i = 0; i < l.size(); i++) {
            MobEffect m = l.get(i);
            if (p.hasEffect(m)) {
                p.removeEffect(m);
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ etat en match pousse au client
    /** Pousse l'etat « en match » au client s'il a change (entree : malus retires). */
    public static void synchroniser(ServerPlayer p) {
        boolean en = MatchApi.enMatch(p);
        int code = en ? 1 : 0;
        Integer avant = en ? ENVOYE.put(p.getUUID(), code) : ENVOYE.remove(p.getUUID());
        if (avant == null && !en) return;   // jamais en match depuis la connexion : rien a envoyer
        if (avant != null && avant == code) return;
        if (en) {
            int n = retirerMalus(p);
            if (n > 0) HeralizClashWar.LOG.info("[matchs] {} : {} malus d'ArkaniaZ retire(s) a l'entree en match", p.getGameProfile().getName(), n);
            // jamais de tag de combat en match (le Coffre l'efface deja ; ici pour toute autre entree)
            if (CombatTag.estTague(p)) CombatTag.effacer(p.getUUID());
        }
        MatchsNet.versJoueur(p, new PaquetsMoteur.EtatMatchS2C(en));
    }

    /**
     * Tick du moteur : reconciliation de l'etat (10 ticks), malus retires aux joueurs en match (20 ticks), filet
     * Reanimation (10 ticks) : un joueur en match n'est jamais « a terre » au sens de Reanimation (le moteur decide :
     * elimination, ou etat a terre propre aux zombies) ; s'il l'est quand meme (commande /reanimation down, etat restaure
     * a la connexion), il est releve tout de suite.
     */
    public static void tick(MinecraftServer s) {
        int t = s.getTickCount();
        if (t % 10 != 0) return;
        // hors tournoi : personne en match et aucun etat a retirer, rien a faire (aucun parcours des joueurs)
        if (ENVOYE.isEmpty() && MatchApi.parties().isEmpty()) return;
        List<ServerPlayer> joueurs = s.getPlayerList().getPlayers();
        for (int i = 0; i < joueurs.size(); i++) {
            ServerPlayer p = joueurs.get(i);
            synchroniser(p);
            if (!MatchApi.enMatch(p)) continue;
            if (t % 20 == 0) retirerMalus(p);
            if (ATerre.aTerreReanimation(p) && ATerre.relever(p)) {
                HeralizClashWar.LOG.warn("[matchs] {} etait a terre (Reanimation) en match : releve, le moteur garde la main",
                        p.getGameProfile().getName());
            }
        }
    }

    /** Joueur deconnecte : l'etat sera renvoye a la reconnexion. */
    public static void oublier(UUID u) {
        ENVOYE.remove(u);
    }

    /** Arret du serveur. */
    public static void vider() {
        ENVOYE.clear();
        malus = null;
    }
}
