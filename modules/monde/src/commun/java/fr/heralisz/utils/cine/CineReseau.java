package fr.heralisz.utils.cine;

import fr.heralisz.utils.HeraliszUtils;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Zombie;
import fr.heralisz.utils.pont.Crochet;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Cinématique d'arrivée sur HeraliZ, côté serveur : à la première connexion (drapeau conservé même après la mort), le
 * joueur est posé au point de réveil et la cinématique est lancée chez lui ; /cinematique la rejoue depuis le point de
 * réveil ; /cinematique joueur <cibles> (op) la relance pour d'autres. Le point de réveil et l'épave du train se règlent
 * en jeu (CineDecor : /cinematique reveil, /cinematique epave ...) ; par défaut, le refuge de la map. Rien ne se passe si
 * le point de réveil n'est pas praticable (autre map) : personne n'est téléporté dans la roche. Enregistre aussi le
 * zombie de la cinématique (jamais présent sur le serveur, seulement créé côté client).
 * Jar fusionné : ici le canal, ses paquets (numéros, ordre et version inchangés) et le zombie ; le déclenchement, les
 * réglages et les commandes sont dans CineReseauServeur (src/serveur), code d'origine déplacé.
 */
public final class CineReseau {
    private static final String PROTO = "4";                  // 3 : le décor porte son monde (spawn à part) ; 4 : fin signalée
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeraliszUtils.MODID, "cinematique"),
            () -> PROTO, PROTO::equals, PROTO::equals);
    public static final DeferredRegister<EntityType<?>> ENTITES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HeraliszUtils.MODID);
    public static final RegistryObject<EntityType<ZombieCinematique>> ZOMBIE = ENTITES.register("zombie_cinematique",
            () -> EntityType.Builder.<ZombieCinematique>of(ZombieCinematique::new, MobCategory.MONSTER).sized(0.6f, 1.95f).noSummon()
                    .clientTrackingRange(8).build("zombie_cinematique"));

    /** Paquet Jouer reçu du serveur : la partie client lance la cinématique (Cinematique.demarrer). */
    public static final Crochet<Jouer> RECEPTION_CLIENT = new Crochet<>();
    /** Paquet Fin reçu d'un joueur : la partie serveur note que sa cinématique est finie (CineReseauServeur). */
    public static final Crochet<ServerPlayer> FIN_SERVEUR = new Crochet<>();

    private CineReseau() {}

    /** Paquet sans contenu : « joue la cinématique ». */
    public record Jouer() {}

    /** Le décor réglé sur le serveur (réveil, regard, épave). */
    public record Decor(CineDecor.Etat etat) {}

    /** Paquet sans contenu, du joueur au serveur : « ma cinématique est finie (vue jusqu'au bout ou passée) ». */
    public record Fin() {}

    public static void init(IEventBus modBus) {
        ENTITES.register(modBus);
        modBus.addListener(CineReseau::attributs);
        CANAL.registerMessage(0, Jouer.class, (m, buf) -> {}, buf -> new Jouer(), (m, ctx) -> {
            ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(1, Decor.class, (m, buf) -> m.etat.ecrire(buf), buf -> new Decor(CineDecor.Etat.lire(buf)), (m, ctx) -> {
            ctx.get().enqueueWork(() -> CineDecor.client = m.etat);
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(2, Fin.class, (m, buf) -> {}, buf -> new Fin(), (m, ctx) -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) {
                ctx.get().enqueueWork(() -> FIN_SERVEUR.accept(sp));
            }
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // écouteurs serveur (première connexion, réapparition, lancement, /cinematique) : CineReseauServeur.init
    }

    private static void attributs(EntityAttributeCreationEvent e) {
        e.put(ZOMBIE.get(), Zombie.createAttributes().build());
    }

}
