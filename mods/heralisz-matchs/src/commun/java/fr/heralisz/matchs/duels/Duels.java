package fr.heralisz.matchs.duels;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.duels.classes.ClassePerso;
import fr.heralisz.matchs.duels.net.PaquetsDuels;
import fr.heralisz.matchs.duels.net.Vues;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Carte;
import fr.heralisz.matchs.moteur.api.Groupe;
import fr.heralisz.matchs.moteur.api.Groupes;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Partie commune (registres, paquets) du module d'entree des duels (appele par {@code Modules}) : registres, paquets, commandes, ecouteurs.
 * <p>Commandes : {@code /duel} (ecran DUEL), {@code /duel <joueur>} (ecran avec la cible choisie), {@code /duel
 * accepter|refuser} (dernier defi recu), {@code /duel classes} (ecran CLASSES), {@code /duel quitter} (abandon),
 * {@code /duel stats [joueur]}, {@code /duel classement} ; op : {@code /duel entrainement <rd|mam>} (contre la cible
 * immobile), {@code /duel etat}, {@code /duel annuler}.
 */
public final class Duels {
    private Duels() {
    }

    public static void construire(IEventBus modBus) {
        Registres.enregistrer(modBus);
        // écouteurs, commandes et logique : DuelsLogique (src/serveur), appelé par Modules juste après
    }

    public static void paquets(fr.heralisz.matchs.reseau.MatchsNet.Registre r) {
        PaquetsDuels.enregistrer(r);
    }
}
