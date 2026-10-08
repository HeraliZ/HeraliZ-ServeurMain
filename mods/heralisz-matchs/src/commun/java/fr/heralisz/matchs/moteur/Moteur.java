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
import fr.heralisz.matchs.moteur.net.PaquetsMoteur;
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
 * Partie commune (registres, paquets) du module d'entree du moteur commun (voir {@code Modules}) : sons, configuration, paquets, commandes ({@code /groupe},
 * {@code /matchs}), chargement des cartes et des kits, tick des parties, poses d'arenes, choix de classe, spectateurs,
 * bornes, connexions (reprise du Coffre apres crash), systeme de combat du serveur ({@code combat.CombatService}),
 * restrictions en match ({@code combat.RestrictionsMatch}) et remise a zero a l'arret. Le HUD client est branche par
 * {@code moteur.client.HudMoteur}.
 */
public final class Moteur {
    private Moteur() {
    }

    public static void construire(IEventBus modBus) {
        MoteurSons.enregistrer(modBus);
        MoteurConfig.enregistrer(modBus);
        // Reanimation : pas de tir ni de coup de crosse TaCZ a terre (les deux cotes)
        fr.heralisz.matchs.moteur.compat.Revive.enregistrer();
        // écouteurs, commandes et logique : MoteurLogique (src/serveur), appelé par Modules juste après
    }

    /** Appele par MatchsNet.init. */
    public static void paquets(MatchsNet.Registre r) {
        PaquetsMoteur.enregistrer(r);
    }
}
