package fr.heralisz.clans.client.banniere;

import com.mojang.blaze3d.platform.NativeImage;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.Optional;

/**
 * Atlas SDF des 64 formes ({@code textures/banniere/formes_sdf.png}, 2048 x 2048, niveaux de gris) en {@code byte[]}
 * pour le {@link Composeur}. Le tableau n'est jamais modifie apres lecture : il peut etre lu par le fil de fond de
 * l'atelier. Atlas absent ou illisible : null (bannieres sans calques, un avertissement).
 * <p>
 * Lecture hors du fil de rendu : {@link #RECHARGEMENT} (enregistre par {@code ClansClient}) decode le PNG dans la phase
 * « prepare » du rechargement des ressources (fil d'arriere-plan, au demarrage et a chaque F3+T), puis publie le tableau
 * et vide les textures de bannieres composees avec l'ancien. L'atlas ne depend pas du serveur : il survit aux
 * deconnexions. {@link #octets()} ne lit lui-meme (fil appelant) que si aucun rechargement n'a encore abouti.
 */
@OnlyIn(Dist.CLIENT)
public final class AtlasSdf {
    private AtlasSdf() {
    }

    public static final ResourceLocation CHEMIN = HeraliszClans.id("textures/banniere/formes_sdf.png");
    @Nullable
    private static volatile byte[] octets;
    private static volatile boolean cherche;

    /** Atlas publie par le dernier rechargement des ressources (lu ici en secours s'il n'y en a pas encore eu), ou null. */
    @Nullable
    public static byte[] octets() {
        if (cherche) return octets;
        octets = lire(Minecraft.getInstance().getResourceManager());
        cherche = true;
        return octets;
    }

    /** Decode l'atlas (n'importe quel fil) ; null s'il manque ou s'il est illisible. */
    @Nullable
    private static byte[] lire(ResourceManager rm) {
        long t0 = System.nanoTime();
        try {
            Optional<Resource> res = rm.getResource(CHEMIN);
            if (res.isEmpty()) {
                HeraliszClans.LOG.warn("[clans] atlas des formes absent ({}) : bannieres sans calques", CHEMIN);
                return null;
            }
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(NativeImage.Format.LUMINANCE, in)) {
                int n = Composeur.ATLAS;
                if (img.getWidth() != n || img.getHeight() != n) {
                    HeraliszClans.LOG.error("[clans] atlas des formes : {}x{} au lieu de {}x{}", img.getWidth(), img.getHeight(), n, n);
                    return null;
                }
                byte[] o = new byte[n * n];
                for (int y = 0; y < n; y++) {
                    int l = y * n;
                    for (int x = 0; x < n; x++) o[l + x] = img.getLuminanceOrAlpha(x, y);
                }
                HeraliszClans.LOG.info("[clans] atlas des formes lu en {} ms ({})", (System.nanoTime() - t0) / 1_000_000,
                        Thread.currentThread().getName());
                return o;
            }
        } catch (Exception ex) {
            HeraliszClans.LOG.error("[clans] atlas des formes illisible", ex);
            return null;
        }
    }

    /**
     * Rechargement des ressources : lecture de l'atlas sur le fil d'arriere-plan (prepare), publication et vidage des
     * textures de bannieres sur le fil principal (apply).
     */
    public static final SimplePreparableReloadListener<byte[]> RECHARGEMENT = new SimplePreparableReloadListener<>() {
        @Override
        @Nullable
        protected byte[] prepare(ResourceManager rm, ProfilerFiller profiler) {
            return lire(rm);
        }

        @Override
        protected void apply(@Nullable byte[] lu, ResourceManager rm, ProfilerFiller profiler) {
            octets = lu;
            cherche = true;
            BannerArt.vider();
        }
    };
}
