package fr.heralisz.matchs.duels.killcam;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Killcam des duels (MATCHS_SPEC §3), module d'entree appele par {@code Modules} : le serveur enregistre en continu,
 * pour chaque joueur d'une partie, les 5 dernieres secondes (anneau a 20 Hz : position, rotations, posture, objet tenu,
 * tirs TaCZ) ; a une elimination il envoie a la victime les 4 secondes precedentes ; le client les rejoue avec des
 * joueurs factices (skins reels), camera par-dessus l'epaule du tueur, sons de tir et traceurs, bandeau « KILLCAM »,
 * touche pour passer (voir {@code client.KillcamClient}).
 * <p><b>API pour l'agent des duels</b> (fil serveur) :
 * <ul>
 *     <li>{@link #enregistreur(Partie)} au debut de la partie (avant le premier combat) ; s'arrete seul en
 *     {@code TERMINEE}. {@link Enregistreur#suivre(LivingEntity)} pour un bot d'entrainement ;</li>
 *     <li>{@link #surElimination(Partie, ServerPlayer, LivingEntity)} dans {@code Partie.surElimination}, <b>avant</b>
 *     de deplacer la victime (spectateur, reapparition) : capture l'image du tick, envoie la killcam a la victime et la
 *     garde comme « derniere » ;</li>
 *     <li>{@link #rejouerDerniere(Partie, Collection)} en fin de manche (recherche et destruction) ;</li>
 *     <li>{@link #enCours(UUID)} pour attendre la fin de la killcam avant la reapparition (le client previent quand
 *     il a fini ou passe ; garde-fou {@link #DELAI_MAX_MS}) et {@link #arreter(ServerPlayer)} pour l'interrompre.</li>
 * </ul>
 * Sans TaCZ, les tirs ne sont pas enregistres (le reste fonctionne). Commandes op : {@code /killcam essai [rayon]}
 * (enregistrement factice qui tourne autour du joueur), {@code /killcam enregistrer} puis {@code /killcam rejouer}
 * (enregistrement reel de soi-meme), {@code /killcam etat}.
 */
public final class Killcam {
    private Killcam() {
    }

    public static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszMatchs.MODID);
    public static final RegistryObject<SoundEvent> SON_DEBUT = son("kc_debut");
    public static final RegistryObject<SoundEvent> SON_FIN = son("kc_fin");
    public static final RegistryObject<SoundEvent> SON_TIR = son("kc_tir");

    public static void construire(IEventBus modBus) {
        SONS.register(modBus);
        // écouteurs, commandes et logique : KillcamLogique (src/serveur), appelé par Modules juste après
    }

    public static void paquets(MatchsNet.Registre r) {
        PaquetsKillcam.enregistrer(r);
    }

    static RegistryObject<SoundEvent> son(String nom) {
        return Killcam.SONS.register(nom, () -> SoundEvent.createVariableRangeEvent(HeraliszMatchs.id(nom)));
    }
}
