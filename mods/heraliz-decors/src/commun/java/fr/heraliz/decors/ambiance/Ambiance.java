package fr.heraliz.decors.ambiance;

import fr.heraliz.decors.HeralizDecors;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Ambiance de la map (1.4.0, 09/10), côté commun : le canal et ses paquets (serveur -> client).
 * <ul>
 *   <li>Brume : profil de brouillard en cours et sa transition (brouillard dynamique : clair, léger, moyen, fort, purée
 *   de pois ; tiré par le serveur selon l'heure, la pluie et un peu de hasard ; longues périodes claires pour les
 *   tireurs) ;</li>
 *   <li>Zones : les zones dangereuses du monde (gaz, radiation, froid), envoyées à la connexion ;</li>
 *   <li>Exposition : où en est le joueur (0 à 1 par genre) et s'il est protégé, pour les effets à l'écran.</li>
 * </ul>
 * La logique serveur est dans serveur.MeteoServeur et serveur.ZonesServeur, le rendu dans client.AmbianceClient.
 */
public final class Ambiance {
    private static final String PROTO = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizDecors.MODID, "ambiance"),
            () -> PROTO, PROTO::equals, PROTO::equals);

    /** Paquets reçus côté client : traités par client.AmbianceClient (branché à son initialisation). */
    public static volatile Consumer<Object> RECEPTION_CLIENT = m -> {};

    private Ambiance() {}

    /** Brume : profil visé, transition (secondes) depuis l'état en cours. */
    public record Brume(int profil, float transition) {
        void ecrire(FriendlyByteBuf b) {
            b.writeVarInt(profil);
            b.writeFloat(transition);
        }

        static Brume lire(FriendlyByteBuf b) {
            return new Brume(b.readVarInt(), b.readFloat());
        }
    }

    /** Les zones du monde. */
    public record Zones(List<Zone> zones) {
        void ecrire(FriendlyByteBuf b) {
            b.writeVarInt(zones.size());
            for (Zone z : zones) z.ecrire(b);
        }

        static Zones lire(FriendlyByteBuf b) {
            int n = b.readVarInt();
            List<Zone> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(Zone.lire(b));
            return new Zones(l);
        }
    }

    /** Exposition du joueur : gaz, radiation, froid (0 à 1), protégé contre chacun. */
    public record Exposition(float gaz, float radiation, float froid, boolean pGaz, boolean pRad, boolean pFroid) {
        void ecrire(FriendlyByteBuf b) {
            b.writeFloat(gaz);
            b.writeFloat(radiation);
            b.writeFloat(froid);
            b.writeBoolean(pGaz);
            b.writeBoolean(pRad);
            b.writeBoolean(pFroid);
        }

        static Exposition lire(FriendlyByteBuf b) {
            return new Exposition(b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readBoolean(), b.readBoolean());
        }
    }

    public static void init() {
        CANAL.registerMessage(0, Brume.class, Brume::ecrire, Brume::lire, (m, ctx) -> {
            ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(1, Zones.class, Zones::ecrire, Zones::lire, (m, ctx) -> {
            ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(2, Exposition.class, Exposition::ecrire, Exposition::lire, (m, ctx) -> {
            ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Profils de brouillard : début et fin (en fraction de la distance d'affichage, ou en blocs si plus petit), teinte
     *  grise, nom. CLAIR = pas de brouillard ajouté (celui de Minecraft seul). */
    public enum Profil {
        // 10/10 (retour en jeu : « je vois même pas à 2 mètres ») : léger = on voit à 150-200 m, moyen = 100-150 m,
        // fort = 60-80 m (court) ; la purée de pois n'est plus jamais tirée au hasard (seulement /brume puree).
        CLAIR("clair", 1.0f, 1.0f, 999, 999),
        LEGER("leger", 0.6f, 1.0f, 120, 240),
        MOYEN("moyen", 0.35f, 1.0f, 60, 170),
        FORT("fort", 0.2f, 0.8f, 28, 110),
        PUREE("puree", 0.0f, 0.4f, 10, 60);

        public final String nom;
        public final float debut, fin;            // en fraction de la distance d'affichage
        public final float debutMax, finMax;      // plafonds en blocs

        Profil(String nom, float debut, float fin, float debutMax, float finMax) {
            this.nom = nom;
            this.debut = debut;
            this.fin = fin;
            this.debutMax = debutMax;
            this.finMax = finMax;
        }

        public static Profil de(String n) {
            for (Profil p : values()) if (p.nom.equalsIgnoreCase(n)) return p;
            return null;
        }
    }
}
