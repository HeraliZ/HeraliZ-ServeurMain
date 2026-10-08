package fr.heraliz.ping.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.ping.HeralizPing;
import fr.heraliz.ping.PingType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * L'atlas du HUD (tools/gen_atlas.py) : icones, formes, secteurs de la roue et glyphes DM Sans dans une seule texture
 * blanche, teintee par la couleur des sommets. Charge avec 3 niveaux de mipmap en filtrage trilineaire (les formes et
 * les lettres restent lisses quelle que soit l'echelle d'interface). Les rectangles sont lus dans atlas.json a chaque
 * rechargement des ressources ; les objets {@link Sprite} sont crees une fois et remplis sur place.
 */
public final class Atlas {
    private Atlas() {
    }

    public static final ResourceLocation PNG = HeralizPing.id("textures/gui/atlas.png");
    public static final ResourceLocation JSON = HeralizPing.id("textures/gui/atlas.json");
    /** Emplacement de la texture mipmappee dans le TextureManager (pas un fichier). */
    public static final ResourceLocation TEXTURE = HeralizPing.id("hud_atlas_mipmap");
    public static final int NIVEAUX = 3;

    /** Rectangle de l'atlas : coordonnees de texture et taille en pixels. */
    public static final class Sprite {
        public float u0, v0, u1, v1, l, h;
    }

    public static final Sprite[] ICONES = new Sprite[PingType.nombre()];
    public static final Sprite DISQUE = new Sprite(), ANNEAU = new Sprite(), ANNEAU_FIN = new Sprite(), HALO = new Sprite(),
            FLECHE = new Sprite(), BLANC = new Sprite(), SECTEUR = new Sprite(), SECTEUR_BORD = new Sprite();
    /** Boite du secteur du haut dans la roue, en rayons exterieurs, y vers le bas : x0, y0, x1, y1. */
    public static final float[] GEO_SECTEUR = new float[4], GEO_BORD = new float[4];
    /** Rayon interieur des secteurs (part du rayon exterieur). */
    public static float rayonInterieur = 0.42f;
    public static final Police GRAS = new Police();

    private static boolean pret;

    static {
        for (int i = 0; i < ICONES.length; i++) ICONES[i] = new Sprite();
    }

    /** Vrai une fois l'atlas lu (sinon le HUD ne dessine rien). */
    public static boolean pret() {
        return pret;
    }

    /** (Re)lit atlas.json et enregistre la texture mipmappee (rechargement des ressources, fil de rendu). */
    public static void charger(ResourceManager rm) {
        pret = false;
        try {
            Resource res = rm.getResource(JSON).orElseThrow(() -> new FileNotFoundException(JSON.toString()));
            JsonObject racine;
            try (Reader r = new InputStreamReader(res.open(), StandardCharsets.UTF_8)) {
                racine = JsonParser.parseReader(r).getAsJsonObject();
            }
            JsonArray taille = racine.getAsJsonArray("taille");
            float tw = taille.get(0).getAsFloat(), th = taille.get(1).getAsFloat();
            JsonObject formes = racine.getAsJsonObject("formes");
            for (PingType t : PingType.values()) lire(formes, t.nom, ICONES[t.ordinal()], tw, th);
            lire(formes, "disque", DISQUE, tw, th);
            lire(formes, "anneau", ANNEAU, tw, th);
            lire(formes, "anneau_fin", ANNEAU_FIN, tw, th);
            lire(formes, "halo", HALO, tw, th);
            lire(formes, "fleche", FLECHE, tw, th);
            lire(formes, "secteur", SECTEUR, tw, th);
            lire(formes, "secteur_bord", SECTEUR_BORD, tw, th);
            lire(formes, "blanc", BLANC, tw, th);
            // le blanc : seulement le centre de la case (aucun texel voisin au filtrage)
            float du = (BLANC.u1 - BLANC.u0) * 0.375f, dv = (BLANC.v1 - BLANC.v0) * 0.375f;
            BLANC.u0 += du;
            BLANC.u1 -= du;
            BLANC.v0 += dv;
            BLANC.v1 -= dv;
            JsonObject roue = racine.getAsJsonObject("roue");
            rayonInterieur = roue.get("interieur").getAsFloat();
            geo(roue.getAsJsonArray("secteur"), GEO_SECTEUR);
            geo(roue.getAsJsonArray("secteur_bord"), GEO_BORD);
            JsonObject police = racine.getAsJsonObject("polices").getAsJsonObject("bold");
            GRAS.lire(police, tw, th);
            TextureManager tm = Minecraft.getInstance().getTextureManager();
            if (tm.getTexture(TEXTURE, null) == null) tm.register(TEXTURE, new Texture());
            pret = true;
        } catch (Exception ex) {
            HeralizPing.LOG.error("[ping] atlas du HUD illisible : le HUD des pings est coupe", ex);
        }
    }

    private static void lire(JsonObject formes, String nom, Sprite s, float tw, float th) {
        JsonArray a = formes.getAsJsonArray(nom);
        float x = a.get(0).getAsFloat(), y = a.get(1).getAsFloat(), l = a.get(2).getAsFloat(), h = a.get(3).getAsFloat();
        s.u0 = x / tw;
        s.v0 = y / th;
        s.u1 = (x + l) / tw;
        s.v1 = (y + h) / th;
        s.l = l;
        s.h = h;
    }

    private static void geo(JsonArray a, float[] dest) {
        for (int i = 0; i < 4; i++) dest[i] = a.get(i).getAsFloat();
    }

    /** Glyphes d'une police de l'atlas, indexes par caractere (Latin-1 + 4 signes). */
    public static final class Police {
        static final int N = 260;
        public final float[] u0 = new float[N], v0 = new float[N], u1 = new float[N], v1 = new float[N];
        public final float[] l = new float[N], h = new float[N], ox = new float[N], oy = new float[N], avance = new float[N];
        public final boolean[] present = new boolean[N];
        /** Hauteur des capitales, en pixels de l'atlas. */
        public float cap = 28f;

        public static int index(char c) {
            if (c < 256) return c;
            return switch (c) {
                case 'œ' -> 256; // oe
                case 'Œ' -> 257; // OE
                case '’' -> 258; // apostrophe typographique
                case '…' -> 259; // points de suspension
                default -> -1;
            };
        }

        void lire(JsonObject p, float tw, float th) {
            java.util.Arrays.fill(present, false);
            cap = p.get("cap").getAsFloat();
            for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("glyphes").entrySet()) {
                int i = index((char) Integer.parseInt(e.getKey()));
                if (i < 0) continue;
                JsonArray g = e.getValue().getAsJsonArray();
                float x = g.get(0).getAsFloat(), y = g.get(1).getAsFloat(), gl = g.get(2).getAsFloat(), gh = g.get(3).getAsFloat();
                u0[i] = x / tw;
                v0[i] = y / th;
                u1[i] = (x + gl) / tw;
                v1[i] = (y + gh) / th;
                l[i] = gl;
                h[i] = gh;
                ox[i] = g.get(4).getAsFloat();
                oy[i] = g.get(5).getAsFloat();
                avance[i] = g.get(6).getAsFloat();
                present[i] = true;
            }
        }
    }

    /** La texture de l'atlas avec ses mipmaps (calcules ici : alpha moyen sans seuil, contrairement au jeu). */
    static final class Texture extends AbstractTexture {
        @Override
        public void load(ResourceManager rm) throws IOException {
            Resource res = rm.getResource(PNG).orElseThrow(() -> new FileNotFoundException(PNG.toString()));
            NativeImage image;
            try (InputStream in = res.open()) {
                image = NativeImage.read(in);
            }
            NativeImage[] niveaux = new NativeImage[NIVEAUX + 1];
            niveaux[0] = image;
            for (int l = 1; l <= NIVEAUX; l++) niveaux[l] = reduire(niveaux[l - 1]);
            if (RenderSystem.isOnRenderThread()) televerser(niveaux);
            else RenderSystem.recordRenderCall(() -> televerser(niveaux));
        }

        /** Niveau suivant : moitie de cote, alpha moyen des 4 texels (l'atlas est blanc partout : couleur inchangee). */
        private static NativeImage reduire(NativeImage src) {
            int w = Math.max(1, src.getWidth() >> 1), h = Math.max(1, src.getHeight() >> 1);
            NativeImage dst = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int a = 0;
                    for (int k = 0; k < 4; k++) a += src.getPixelRGBA(2 * x + (k & 1), 2 * y + (k >> 1)) >>> 24;
                    dst.setPixelRGBA(x, y, ((a + 2) / 4) << 24 | 0xFFFFFF);
                }
            }
            return dst;
        }

        private void televerser(NativeImage[] niveaux) {
            int w = niveaux[0].getWidth(), h = niveaux[0].getHeight();
            TextureUtil.prepareImage(getId(), NIVEAUX, w, h);
            for (int l = 0; l < niveaux.length; l++) niveaux[l].upload(l, 0, 0, 0, 0, w >> l, h >> l, true, true, true, true);
        }
    }
}
