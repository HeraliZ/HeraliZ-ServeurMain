package fr.heralisz.clans.territory.client;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.banniere.AtlasSdf;
import fr.heralisz.clans.client.banniere.Rendu;
import fr.heralisz.clans.client.banniere.TextureBanniere;
import fr.heralisz.clans.model.Banniere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Texture du tissu du drapeau 3D (spec BANNIERE_V2 §3) : {@value #W} x {@value #H} paysage (u = 0 cote mat, v = 0 en
 * haut), composee par le meme composeur que les interfaces : degrade sur tout le tissu, composition (zone rectangulaire
 * 64 x 75 du canevas, sans la pointe) centree sur toute la hauteur, memes calques, ourlet plus sombre cote mat et bords
 * libres legerement assombris. Filtrage lineaire. Cache LRU par cle de banniere ({@value #CACHE_MAX} entrees, release()
 * + close() a l'eviction) ; neutre = textures/entity/drapeau_neutre.png. Rend directement le {@link RenderType} a passer
 * au maillage.
 */
@OnlyIn(Dist.CLIENT)
public final class TissuTexture {
    private TissuTexture() {
    }

    public static final int W = 256, H = 160;
    public static final int CACHE_MAX = 32;
    public static final ResourceLocation NEUTRE_PNG = HeraliszClans.id("textures/entity/drapeau_neutre.png");
    private static final RenderType NEUTRE = RenderType.entityCutoutNoCull(NEUTRE_PNG);

    private record Entree(int place, TextureBanniere texture) {
    }

    private static final Map<String, Entree> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    /**
     * Reservoir fixe d'identifiants tissu/0..{@value #CACHE_MAX}-1 (un par place du cache) : RenderType.entityCutoutNoCull
     * memorise un RenderType par identifiant pour toute la session ; un compteur sans fin en laissait un de plus a chaque
     * composition (eviction, F3+T, reconnexion). Une place liberee est reprise telle quelle : son RenderType reste valide.
     */
    private static final ResourceLocation[] IDS = new ResourceLocation[CACHE_MAX];
    private static final RenderType[] TYPES = new RenderType[CACHE_MAX];
    private static final boolean[] PRISES = new boolean[CACHE_MAX];

    /** Oublie toutes les textures (rechargement des ressources, deconnexion) ; les identifiants restent reserves. */
    public static void vider() {
        Minecraft mc = Minecraft.getInstance();
        for (Entree e : CACHE.values()) liberer(mc, e);
        CACHE.clear();
    }

    private static void liberer(Minecraft mc, Entree e) {
        mc.getTextureManager().release(IDS[e.place]);
        e.texture.close();
        PRISES[e.place] = false;
    }

    /** RenderType du tissu pour une banniere (null = tissu neutre « HeraliZ »). */
    public static RenderType type(@Nullable Banniere b) {
        if (b == null) return NEUTRE;
        Entree e = CACHE.get(b.cle());
        if (e != null) return TYPES[e.place];
        Minecraft mc = Minecraft.getInstance();
        // cache plein : la plus ancienne rend sa place avant la composition
        if (CACHE.size() >= CACHE_MAX) {
            Iterator<Entree> it = CACHE.values().iterator();
            liberer(mc, it.next());
            it.remove();
        }
        int place = 0;
        while (place < CACHE_MAX - 1 && PRISES[place]) place++;
        if (IDS[place] == null) {
            IDS[place] = HeraliszClans.id("tissu/" + place);
            TYPES[place] = RenderType.entityCutoutNoCull(IDS[place]);
        }
        int np = W * H;
        int[] px = Rendu.pixels(np);
        int n = Rendu.emballer(b);
        Rendu.COMPOSEUR.tissu(px, W, H, b.couleurA, b.couleurB, b.degrade.ordinal(), Rendu.calques(), n, AtlasSdf.octets());
        TextureBanniere tex = new TextureBanniere(true);
        mc.getTextureManager().register(IDS[place], tex);
        tex.envoyer(Rendu.versTampon(px, np), W, H);
        PRISES[place] = true;
        CACHE.put(b.cle(), new Entree(place, tex));
        return TYPES[place];
    }
}
