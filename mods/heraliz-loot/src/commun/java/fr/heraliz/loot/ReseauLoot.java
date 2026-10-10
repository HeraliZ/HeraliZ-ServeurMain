package fr.heraliz.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal réseau de HeraliZ Loot (protocole 1) : le serveur envoie aux joueurs la table des raretés des objets
 * ({@link RaretesObjets}) à la connexion et à chaque changement. Un joueur avec un jar plus ancien (sans ce canal) est
 * refusé à la connexion : il doit prendre le nouveau jar.
 */
public final class ReseauLoot {
    private ReseauLoot() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizLoot.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    /** Table des raretés (serveur -> joueur). */
    public record Raretes(RaretesObjets.Table table) {
    }

    static void enregistrer() {
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
