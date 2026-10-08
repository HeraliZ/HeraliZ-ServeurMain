package fr.heralisz.matchs.moteur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.brigadier.CommandDispatcher;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Arenes;
import fr.heralisz.matchs.moteur.api.Carte;
import fr.heralisz.matchs.moteur.api.CombatTag;
import fr.heralisz.matchs.moteur.api.Groupes;
import fr.heralisz.matchs.moteur.api.Kit;
import fr.heralisz.matchs.moteur.api.Kits;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.arenes.ArenesService;
import fr.heralisz.matchs.moteur.arenes.Protections;
import fr.heralisz.matchs.moteur.coffre.Coffres;
import fr.heralisz.matchs.moteur.coffre.Descente;
import fr.heralisz.matchs.moteur.combat.CombatEvents;
import fr.heralisz.matchs.moteur.combat.CombatService;
import fr.heralisz.matchs.moteur.combat.RestrictionsMatch;
import fr.heralisz.matchs.moteur.groupes.GroupesService;
import fr.heralisz.matchs.moteur.kits.ChoixService;
import fr.heralisz.matchs.moteur.net.PaquetsMoteur;
import fr.heralisz.matchs.moteur.spectateur.Spectateurs;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Partie serveur (docs/METHODE_CLIENT_SERVEUR.md) du module d'entree du moteur commun (voir {@code Modules}) : sons, configuration, paquets, commandes ({@code /groupe},
 * {@code /matchs}), chargement des cartes et des kits, tick des parties, poses d'arenes, choix de classe, spectateurs,
 * bornes, connexions (reprise du Coffre apres crash), systeme de combat du serveur ({@code combat.CombatService}),
 * restrictions en match ({@code combat.RestrictionsMatch}) et remise a zero a l'arret. Le HUD client est branche par
 * {@code moteur.client.HudMoteur}.
 */
public final class MoteurLogique {
    private MoteurLogique() {
    }

    private static final Gson GSON = new GsonBuilder().create();
    /** Tampon reutilise pour iterer les parties sans allocation (une partie peut se fermer pendant son tick). */
    private static final List<Partie> TAMPON = new ArrayList<>();
    /** Connexions a traiter au tick suivant (le joueur est alors entierement place dans son niveau). */
    private static final List<UUID> CONNEXIONS = new ArrayList<>();

    /** Appele par Modules dans le constructeur du mod. */
    public static void construire(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(MoteurLogique::rechargement);
        MinecraftForge.EVENT_BUS.addListener(MoteurLogique::tick);
        MinecraftForge.EVENT_BUS.addListener(MoteurLogique::demarrage);
        MinecraftForge.EVENT_BUS.addListener(MoteurLogique::arret);
        MinecraftForge.EVENT_BUS.addListener(MoteurLogique::connexion);
        MinecraftForge.EVENT_BUS.addListener(MoteurLogique::deconnexion);
        CombatEvents.enregistrer();
        Descente.enregistrer();
        Protections.enregistrer();
    }


    /** Appele sur RegisterCommandsEvent. */
    public static void commandes(CommandDispatcher<CommandSourceStack> d, CommandBuildContext c) {
        CommandesMoteur.enregistrer(d, c);
    }

    private static void rechargement(AddReloadListenerEvent e) {
        e.addListener(new SimpleJsonResourceReloadListener(GSON, "cartes") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> fichiers, ResourceManager rm, ProfilerFiller pf) {
                Map<String, Carte> cartes = new LinkedHashMap<>();
                fichiers.forEach((rl, json) -> {
                    if (!rl.getNamespace().equals(HeraliszMatchs.MODID) || !json.isJsonObject()) return;
                    Carte c = Carte.lire(rl.getPath(), json.getAsJsonObject());
                    if (c != null) cartes.put(c.id(), c);
                });
                Arenes.definirCartes(cartes);
                ArenesService.oublierStructures();
                HeraliszMatchs.LOG.info("[matchs] {} cartes chargees : {}", cartes.size(), cartes.keySet());
            }
        });
        e.addListener(new SimpleJsonResourceReloadListener(GSON, "kits") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> fichiers, ResourceManager rm, ProfilerFiller pf) {
                Map<String, Kit> kits = new LinkedHashMap<>();
                fichiers.forEach((rl, json) -> {
                    if (!rl.getNamespace().equals(HeraliszMatchs.MODID) || !json.isJsonObject()) return;
                    Kit k = Kit.lire(rl.getPath(), json.getAsJsonObject());
                    if (k != null) kits.put(k.id(), k);
                });
                Kits.definir(kits);
                HeraliszMatchs.LOG.info("[matchs] {} kits charges : {}", kits.size(), kits.keySet());
            }
        });
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        if (!CONNEXIONS.isEmpty()) {
            for (int i = 0; i < CONNEXIONS.size(); i++) {
                ServerPlayer p = s.getPlayerList().getPlayer(CONNEXIONS.get(i));
                if (p == null) continue;
                try {
                    Coffres.surConnexion(p);
                } catch (RuntimeException ex) {
                    HeraliszMatchs.LOG.error("[matchs] traitement de connexion de {} en erreur", p.getGameProfile().getName(), ex);
                }
                GroupesService.presence(p, true);
            }
            CONNEXIONS.clear();
        }
        TAMPON.clear();
        TAMPON.addAll(MatchApi.parties());
        for (int i = 0; i < TAMPON.size(); i++) {
            Partie p = TAMPON.get(i);
            if (p.phase() == Partie.Phase.TERMINEE) continue;
            p.avancer();
            try {
                p.tick();
            } catch (RuntimeException ex) {
                // une partie qui plante ne doit pas faire tomber le serveur ni les autres parties
                HeraliszMatchs.LOG.error("[matchs] tick de {} en erreur", p.id(), ex);
            }
        }
        TAMPON.clear();
        ArenesService.tick(s);
        ChoixService.tick();
        Spectateurs.tick(s);
        int t = s.getTickCount();
        if (t % 5 == 0) Protections.bornes(s);
        if (t % 20 == 0) GroupesService.tick();
        if (t % 200 == 0) CombatTag.purger();
        CombatService.tick(s);
        RestrictionsMatch.tick(s);
    }

    private static void demarrage(ServerStartedEvent e) {
        ArenesService.demarrage(e.getServer());
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) CONNEXIONS.add(p.getUUID());
    }

    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        UUID u = p.getUUID();
        CONNEXIONS.remove(u);
        Partie partie = MatchApi.partieDe(u);
        if (partie != null && partie.phase() != Partie.Phase.TERMINEE) {
            try {
                partie.surDeconnexion(p);
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[matchs] surDeconnexion de {} en erreur", partie.id(), ex);
            }
        }
        Spectateurs.deconnexion(p);
        Protections.oublier(u);
        CombatEvents.oublier(u);
        ChoixService.oublier(u);
        GroupesService.oublier(u);
        GroupesService.presence(p, false);
    }

    private static void arret(ServerStoppedEvent e) {
        MatchApi.vider();
        Groupes.vider();
        Arenes.vider();
        CombatTag.vider();
        ArenesService.arret();
        Spectateurs.vider();
        ChoixService.vider();
        GroupesService.vider();
        CombatEvents.vider();
        Protections.vider();
        CONNEXIONS.clear();
    }
}
