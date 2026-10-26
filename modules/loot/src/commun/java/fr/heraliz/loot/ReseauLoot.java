package fr.heraliz.loot;

import fr.heraliz.loot.pont.Crochet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal réseau de HeraliZ Loot (protocole 2) : le serveur envoie aux joueurs la table des raretés des objets
 * ({@link RaretesObjets}) à la connexion et à chaque changement ; le joueur demande à fouiller ce qu'il vise avec un
 * objet en main ({@link Fouiller}, 0.9.18). Un joueur avec un jar plus ancien est refusé à la connexion : il doit
 * prendre le nouveau jar.
 */
public final class ReseauLoot {
    private ReseauLoot() {
    }

    private static final String VERSION = "2";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizLoot.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    /** Table des raretés (serveur -> joueur). */
    public record Raretes(RaretesObjets.Table table) {
    }

    /**
     * Fouiller avec un objet en main (joueur -> serveur, 0.9.18) : bloc visé (conteneur, coffre, meuble de rangement) ou
     * entité visée (cadavre, caisse de largage, {@code entite} >= 0). Le serveur revérifie tout (portée, vue, règles des
     * autres mods) et ouvre comme une main vide : le clic droit ordinaire avec un objet en main pouvait être refusé par un
     * plugin du serveur (qui ne laisse passer que les op) avant d'arriver au conteneur.
     */
    public record Fouiller(BlockHitResult bloc, int entite) {
    }

    /** Demande reçue par le serveur, avec son joueur. */
    public record Demande(ServerPlayer joueur, Fouiller m) {
    }

    /** Branché par la partie serveur ; sans elle, la demande est ignorée. */
    public static final Crochet<Demande> FOUILLER_SERVEUR = new Crochet<>();

    static void enregistrer() {
        CANAL.messageBuilder(Fouiller.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> {
                    b.writeVarInt(m.entite());
                    if (m.entite() < 0) b.writeBlockHitResult(m.bloc());
                })
                .decoder(b -> {
                    int e = b.readVarInt();
                    return new Fouiller(e < 0 ? b.readBlockHitResult()
                            : BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO), e);
                })
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer sp = ctx.get().getSender();
                    if (sp != null) FOUILLER_SERVEUR.accept(new Demande(sp, m));
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CANAL.messageBuilder(Raretes.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, b) -> m.table().ecrire(b))
                .decoder(b -> new Raretes(RaretesObjets.Table.lire(b)))
                .consumerMainThread((m, ctx) -> {
                    // aucune classe client ici : la table est commune, le dessin la lit (client/RaretesClient)
                    RaretesObjets.remplacer(m.table());
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }
}
