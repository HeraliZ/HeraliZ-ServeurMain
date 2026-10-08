package fr.heralisz.utils.cine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.utils.HeraliszUtils;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Décor de la cinématique réglé en jeu : point de réveil (et direction du regard au réveil), épave 3D du train couchée
 * derrière, et les blocs barrière invisibles qui la rendent solide. Tant que rien n'est réglé : le refuge de la map
 * (heralisz_utils_cinematique.json). Réglages des ops : /cinematique reveil, /cinematique epave poser|devant|decaler|
 * tourner|retirer, /cinematique info (CineReseau). Gardé dans le monde (data/heraliz_cinematique.dat) et envoyé aux
 * joueurs à la connexion et à chaque réglage : la cinématique et l'épave se règlent sans recompiler le mod, là où sera
 * posé le spawn du serveur.
 * Jar fusionné : ici ce que lisent le client et le paquet Decor (Etat, Piece, décor du jar, décor reçu) ; les données du
 * monde, les réglages et les barrières de l'épave sont dans CineDecorServeur (src/serveur), code d'origine déplacé.
 */
public final class CineDecor {
    private CineDecor() {}

    /** Une pièce de l'épave : modèle du train (loco, voiture_b, bogie_voiture...), origine (dessus du rail, milieu),
     *  lacet (degrés, axe de la rame depuis +x vers +z) et roulis (degrés, 90 = couchée sur le flanc). */
    public record Piece(String modele, double x, double y, double z, float lacet, float roulis) {
        Piece deplacee(double dx, double dy, double dz) {
            return new Piece(modele, x + dx, y + dy, z + dz, lacet, roulis);
        }
    }

    /** Monde du spawn de HeraliZ (monde à part : la grotte du serveur, le train couché ; datapack heralisz_map). */
    public static final ResourceLocation SPAWN = new ResourceLocation("heralisz", "spawn");
    public static final ResourceLocation MONDE_PRINCIPAL = new ResourceLocation("minecraft", "overworld");

    /** Réveil (pieds du joueur), regard au réveil (lacet Minecraft), épave, et le monde où ils sont. */
    public record Etat(Vec3 reveil, float cap, List<Piece> epave, ResourceLocation monde) {
        public Etat(Vec3 reveil, float cap, List<Piece> epave) {
            this(reveil, cap, epave, MONDE_PRINCIPAL);
        }

        /** Le client est-il dans le monde du décor ? */
        public boolean dans(net.minecraft.world.level.Level l) {
            return l != null && l.dimension().location().equals(monde);
        }
        public Vec3 regard() {
            float a = cap * Mth.DEG_TO_RAD;
            return new Vec3(-Mth.sin(a), 0, Mth.cos(a));
        }

        void ecrire(FriendlyByteBuf b) {
            b.writeDouble(reveil.x).writeDouble(reveil.y).writeDouble(reveil.z);
            b.writeFloat(cap);
            b.writeVarInt(epave.size());
            for (Piece p : epave) {
                b.writeUtf(p.modele, 64);
                b.writeDouble(p.x).writeDouble(p.y).writeDouble(p.z);
                b.writeFloat(p.lacet).writeFloat(p.roulis);
            }
            b.writeResourceLocation(monde);
        }

        static Etat lire(FriendlyByteBuf b) {
            Vec3 r = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
            float cap = b.readFloat();
            int n = Math.min(b.readVarInt(), 64);
            List<Piece> l = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                l.add(new Piece(b.readUtf(64), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat()));
            }
            return new Etat(r, cap, List.copyOf(l), b.readResourceLocation());
        }
    }

    private static Etat defaut;
    /** Côté client : le dernier décor reçu du serveur (null : pas encore reçu). */
    static Etat client;

    // ---------------------------------------------------------------------------------------------- valeurs par défaut
    /** Le spawn de HeraliZ (fichier du jar) : réveil, regard (« cap », ou vers le « puits »), épave, monde. */
    public static Etat defaut() {
        if (defaut == null) {
            Vec3 r = Vec3.ZERO;
            float cap = 0;
            List<Piece> ep = new ArrayList<>();
            ResourceLocation monde = MONDE_PRINCIPAL;
            try (Reader in = new InputStreamReader(CineDecor.class.getResourceAsStream("/heralisz_utils_cinematique.json"), StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                JsonArray a = o.getAsJsonArray("reveil"), w = o.getAsJsonArray("puits");
                r = new Vec3(a.get(0).getAsInt() + 0.5, a.get(1).getAsInt(), a.get(2).getAsInt() + 0.5);
                if (o.has("cap")) {
                    cap = o.get("cap").getAsFloat();
                } else if (w != null) {
                    cap = (float) (Mth.atan2(w.get(2).getAsInt() + 0.5 - r.z, w.get(0).getAsInt() + 0.5 - r.x) * Mth.RAD_TO_DEG) - 90f;
                }
                if (o.has("monde") && ResourceLocation.tryParse(o.get("monde").getAsString()) != null) {
                    monde = ResourceLocation.tryParse(o.get("monde").getAsString());
                }
                if (o.has("epave")) {
                    for (var e : o.getAsJsonArray("epave")) {
                        JsonArray p = e.getAsJsonArray();
                        ep.add(new Piece(p.get(0).getAsString(), p.get(1).getAsDouble(), p.get(2).getAsDouble(), p.get(3).getAsDouble(),
                                p.get(4).getAsFloat(), p.get(5).getAsFloat()));
                    }
                }
            } catch (Exception ex) {
                HeraliszUtils.LOG.error("cinématique : coordonnées illisibles", ex);
            }
            defaut = new Etat(r, cap, List.copyOf(ep), monde);
        }
        return defaut;
    }

    /** Déconnexion : on oublie le décor de ce serveur. */
    public static void oublier() {
        client = null;
    }

    /** Le décor connu du client (reçu du serveur, sinon celui du jar). */
    public static Etat client() {
        return client != null ? client : defaut();
    }

}
