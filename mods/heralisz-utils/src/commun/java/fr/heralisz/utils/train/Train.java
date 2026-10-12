package fr.heralisz.utils.train;

import fr.heralisz.utils.HeraliszUtils;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import fr.heralisz.utils.pont.Crochet;
import fr.heralisz.utils.pont.UsineEntite;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Le train de HeraliZ, côté commun : il ne roule que dans un monde qui le demande (données « heralisz_train » du
 * monde principal, écrites par la map : actif + décalage de l'horaire), et le serveur l'annonce à chaque joueur qui se
 * connecte. Le reste (position, portes, panneaux) se calcule des deux côtés à partir du temps du monde (Horaire).
 * Commandes (op) : /train info, /train actif <oui|non>, /train decalage <secondes> (avancer l'horaire, pour les essais),
 * /train voie ... (retouches de la voie dessinée, collision des ponts : VoieServeur).
 * Jar fusionné : ici les registres, le canal et ses paquets (numéros et ordre inchangés ; un paquet de plus et version 4
 * le 11/10 ; version 5 le 12/10) ; la logique serveur (données du monde, annonce, commandes, descentes forcées) est dans
 * TrainServeur et VoyageursServeur (src/serveur).
 */
public final class Train {
    private static final String PROTO = "5";                  // 3 : réseau choisi par le monde, deux rames (09/10) ;
                                                              // 4 : saut dans la voiture, retouches de la voie (11/10) ;
                                                              // 5 : course dans la voiture (12/10)
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeraliszUtils.MODID, "train"),
            () -> PROTO, PROTO::equals, PROTO::equals);

    public static final DeferredRegister<EntityType<?>> ENTITES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HeraliszUtils.MODID);
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, HeraliszUtils.MODID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> ENTITES_BLOC =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HeraliszUtils.MODID);
    public static final RegistryObject<net.minecraft.world.level.block.Block> TABLEAU = BLOCS.register("tableau_gare",
            () -> new TableauBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(-1.0f, 3600000.0f).noLootTable()
                    .sound(net.minecraft.world.level.block.SoundType.METAL).noOcclusion()));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<TableauBlockEntity>> TABLEAU_BE =
            ENTITES_BLOC.register("tableau_gare", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                    .of(TableauBlockEntity::new, TABLEAU.get()).build(null));
    /** Voyageur : version serveur (VoyageurServeur : tick, descente) dans un monde serveur, commune côté client. Suivi
     *  aussi loin qu'un joueur (32 tronçons, comme EntityType.PLAYER) : un joueur vu sans son voyageur resterait figé. */
    public static final UsineEntite<Voyageur> VOYAGEUR_USINE = new UsineEntite<>(Voyageur::new);
    public static final RegistryObject<EntityType<Voyageur>> VOYAGEUR = ENTITES.register("voyageur",
            () -> EntityType.Builder.<Voyageur>of(VOYAGEUR_USINE, MobCategory.MISC).sized(0.2f, 0.2f).noSummon().noSave().fireImmune()
                    .clientTrackingRange(32).updateInterval(1).build("voyageur"));
    /** Voie pleine : collision invisible sous la voie dessinée (ponts), posée par /train voie remplir. Sans objet. */
    public static final RegistryObject<net.minecraft.world.level.block.Block> VOIE_PLEINE = BLOCS.register("voie_pleine",
            () -> new VoiePleineBlock(VoiePleineBlock.proprietes()));

    /** Paquets serveur -> client reçus (Annonce, Recadrer, Voie) : traités par la partie client (TrainClient.recevoir). */
    public static final Crochet<Object> RECEPTION_CLIENT = new Crochet<>();
    /** Paquets client -> serveur reçus (Marche, Descente) : traités par la partie serveur (TrainServeur.recevoir). */
    public static final Crochet<Recu> RECEPTION_SERVEUR = new Crochet<>();

    /** Un paquet reçu par le serveur, avec le joueur qui l'envoie. */
    public record Recu(ServerPlayer joueur, Object paquet) {}

    private Train() {}

    /** Position d'un voyageur dans sa voiture (client -> serveur) : voiture, lx, lz, état (ASSIS, COURSE),
     *  hauteur du saut au-dessus du plancher (0 : les pieds au plancher ; vérifiée par le serveur, VoyageursServeur.marche). */
    public record Marche(int vehicule, float lx, float lz, byte etat, float hauteur) {
        public static final byte ASSIS = 1, COURSE = 4;

        public static byte etat(boolean assis, boolean course) {
            return (byte) ((assis ? ASSIS : 0) | (course ? COURSE : 0));
        }

        public boolean assis() {
            return (etat & ASSIS) != 0;
        }

        public boolean course() {
            return (etat & COURSE) != 0;
        }

        void ecrire(FriendlyByteBuf b) {
            b.writeVarInt(vehicule);
            b.writeFloat(lx);
            b.writeFloat(lz);
            b.writeByte(etat);
            b.writeFloat(hauteur);
        }

        static Marche lire(FriendlyByteBuf b) {
            return new Marche(b.readVarInt(), b.readFloat(), b.readFloat(), b.readByte(), b.readFloat());
        }
    }

    /** Le passager demande à descendre (Maj). */
    public record Descente() {
        void ecrire(FriendlyByteBuf b) {
        }

        static Descente lire(FriendlyByteBuf b) {
            return new Descente();
        }
    }

    public record Recadrer(int vehicule, float lx, float lz) {
        void ecrire(FriendlyByteBuf b) {
            b.writeVarInt(vehicule);
            b.writeFloat(lx);
            b.writeFloat(lz);
        }

        static Recadrer lire(FriendlyByteBuf b) {
            return new Recadrer(b.readVarInt(), b.readFloat(), b.readFloat());
        }
    }

    /** Retouches de la voie dessinée (/train voie) pour le réseau nommé : envoyées à la connexion et à chaque changement. */
    public record Voie(String reseau, java.util.List<Retouches.Retouche> retouches) {
        void ecrire(FriendlyByteBuf b) {
            b.writeUtf(reseau);
            Retouches.ecrire(b, retouches);
        }

        static Voie lire(FriendlyByteBuf b) {
            return new Voie(b.readUtf(), Retouches.lire(b));
        }
    }

    /** État annoncé aux clients : train actif, décalage de l'horaire, réseau du monde. */
    public record Annonce(boolean actif, long decalage, String reseau) {
        void ecrire(FriendlyByteBuf b) {
            b.writeBoolean(actif);
            b.writeLong(decalage);
            b.writeUtf(reseau);
        }

        static Annonce lire(FriendlyByteBuf b) {
            return new Annonce(b.readBoolean(), b.readLong(), b.readUtf());
        }
    }

    /** Registres (avant ceux des objets : le panneau y ajoute le sien). */
    public static void registres(IEventBus modBus) {
        ENTITES.register(modBus);
        TrainSons.SONS.register(modBus);
        fr.heralisz.utils.cine.CineSons.SONS.register(modBus);         // sons de la cinématique (tools/gen_cine_sons.py)
        BLOCS.register(modBus);
        ENTITES_BLOC.register(modBus);
        RegistryObject<net.minecraft.world.item.Item> it = HeraliszUtils.ITEMS.register("tableau_gare",
                () -> new net.minecraft.world.item.BlockItem(TABLEAU.get(), new net.minecraft.world.item.Item.Properties()));
        fr.heralisz.utils.deco.DecoBlocks.ITEMS.add(it);
    }

    public static void init(IEventBus modBus) {
        CANAL.registerMessage(0, Annonce.class, Annonce::ecrire, Annonce::lire, (m, ctx) -> {
            ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(1, Marche.class, Marche::ecrire, Marche::lire, (m, ctx) -> {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp != null) RECEPTION_SERVEUR.accept(new Recu(sp, m));
            });
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CANAL.registerMessage(2, Recadrer.class, Recadrer::ecrire, Recadrer::lire, (m, ctx) -> {
            ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(3, Descente.class, Descente::ecrire, Descente::lire, (m, ctx) -> {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp != null) RECEPTION_SERVEUR.accept(new Recu(sp, m));
            });
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CANAL.registerMessage(4, Voie.class, Voie::ecrire, Voie::lire, (m, ctx) -> {
            ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // écouteurs serveur (connexion, commandes, tick du monde, descentes forcées...) : TrainServeur.init (partie serveur).
        // Restent ici, des deux côtés comme à l'origine : à bord, rien n'est cassé, posé ni utilisé (le client n'essaie
        // même pas : pas de geste prédit qu'il faudrait corriger).
        MinecraftForge.EVENT_BUS.addListener(Train::clicDroit);
        MinecraftForge.EVENT_BUS.addListener(Train::clicGauche);
        MinecraftForge.EVENT_BUS.addListener(Train::casse);
        MinecraftForge.EVENT_BUS.addListener(Train::pose);
    }

    /** Vraiment dans le train : passager d'un voyageur du même monde, tout près (un passager « fantôme » resté accroché
     *  après une téléportation d'un autre mod ne perd plus le droit de construire ailleurs). */
    private static boolean aBord(net.minecraft.world.entity.Entity p) {
        return p != null && p.getVehicle() instanceof Voyageur v && !v.isRemoved() && v.level() == p.level() && v.distanceToSqr(p) < 16;
    }

    // à bord, les blocs du monde qui défilent derrière les parois ne sont ni cassés, ni posés, ni utilisés
    private static void clicDroit(PlayerInteractEvent.RightClickBlock e) {
        if (aBord(e.getEntity())) e.setCanceled(true);
    }

    private static void clicGauche(PlayerInteractEvent.LeftClickBlock e) {
        if (aBord(e.getEntity())) e.setCanceled(true);
    }

    private static void casse(BlockEvent.BreakEvent e) {
        if (aBord(e.getPlayer())) e.setCanceled(true);
    }

    private static void pose(BlockEvent.EntityPlaceEvent e) {
        if (aBord(e.getEntity())) e.setCanceled(true);
    }

}
