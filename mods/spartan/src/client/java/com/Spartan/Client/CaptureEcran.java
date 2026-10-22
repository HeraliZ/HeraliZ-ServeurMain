package com.Spartan.Client;

import com.Spartan.Networks.CaptureChunkPacket;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Spartan;
import com.Spartan.scan.ScanTransfer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.sun.jna.Memory;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinNT;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Capture de l'écran du jeu demandée par le serveur (CaptureRequestPacket). Prise SEULEMENT si le joueur l'a autorisée
 * dans le launcher HeraliZ (variable d'environnement SPARTAN_CAPTURE_CONSENT=1) : sans accord, rien n'est pris ni
 * envoyé. Rien n'est écrit sur le disque du joueur, aucun message ne s'affiche.
 *
 * Prise 0,5 s après la demande : le menu d'un client de triche ouvert par la touche qui a déclenché la demande a le
 * temps de s'afficher. Sous Windows, c'est la zone de l'écran couverte par la fenêtre du jeu qui est copiée (comme une
 * capture d'écran du système) : un menu de triche dessiné dans une fenêtre posée par-dessus le jeu y apparaît. Image
 * du jeu lui-même (sans ce qui est par-dessus) si la copie de l'écran est impossible ou noire (plein écran exclusif),
 * si la fenêtre est réduite ou n'a pas le focus, et sur Mac et Linux.
 */
@Mod.EventBusSubscriber(modid = Spartan.MOD_ID, value = Dist.CLIENT)
public final class CaptureEcran {
    private static final int LARGEUR_MAX = 1280;
    private static final int OCTETS_MAX = ScanTransfer.CHUNK_BYTES * ScanTransfer.MAX_CHUNKS - 1024;

    private CaptureEcran() {
    }

    /** Accord donné dans le launcher. */
    static boolean autorisee() {
        String v = System.getenv("SPARTAN_CAPTURE_CONSENT");
        return v != null && v.trim().equals("1");
    }

    private static final int DELAI_TICKS = 10;
    private static final int CAPTUREBLT = 0x40000000;
    private static int attenteId;
    private static int attenteTicks = -1;

    /** Demande reçue du serveur : capture prise 0,5 s plus tard (tick du jeu). */
    static void prendre(int id) {
        if (!autorisee()) {
            return;
        }
        attenteId = id;
        attenteTicks = DELAI_TICKS;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || attenteTicks < 0 || attenteTicks-- > 0) {
            return;
        }
        attenteTicks = -1;
        capturer(attenteId);
    }

    /** Sur le thread du jeu (lecture de l'écran ou de l'image affichée), puis compression et envoi à côté. */
    private static void capturer(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }
        BufferedImage img = null;
        try {
            img = ecranWindows(mc.getWindow());
        } catch (Throwable t) {
            Spartan.LOGGER.debug("[Spartan] copie de l'écran impossible : {}", t.toString());
        }
        if (img == null) {
            try {
                img = imageDuJeu(mc);
            } catch (Throwable t) {
                Spartan.LOGGER.debug("[Spartan] capture impossible : {}", t.toString());
                return;
            }
        }
        BufferedImage source = img;
        Util.ioPool().execute(() -> {
            byte[] jpg = encoder(reduire(source));
            if (jpg == null) {
                return;
            }
            int total = Math.max(1, (jpg.length + ScanTransfer.CHUNK_BYTES - 1) / ScanTransfer.CHUNK_BYTES);
            for (int i = 0; i < total; i++) {
                int off = i * ScanTransfer.CHUNK_BYTES;
                byte[] part = new byte[Math.min(ScanTransfer.CHUNK_BYTES, jpg.length - off)];
                System.arraycopy(jpg, off, part, 0, part.length);
                int index = i;
                mc.execute(() -> {
                    if (mc.getConnection() != null) {
                        ModMessages.sendToServer(new CaptureChunkPacket(id, index, total, part));
                    }
                });
            }
        });
    }

    /** L'image rendue par le jeu (sans ce qu'une autre fenêtre dessine par-dessus). */
    private static BufferedImage imageDuJeu(Minecraft mc) {
        try (NativeImage ni = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int w = ni.getWidth();
            int h = ni.getHeight();
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = ni.getPixelRGBA(x, y); // ABGR
                    img.setRGB(x, y, (c & 0xFF) << 16 | (c >> 8 & 0xFF) << 8 | (c >> 16 & 0xFF));
                }
            }
            return img;
        }
    }

    /**
     * Windows : la zone de l'écran sous la zone de dessin de la fenêtre du jeu, telle que le joueur la voit (fenêtres
     * posées par-dessus comprises). null si ce n'est pas possible ou pas utile.
     */
    private static BufferedImage ecranWindows(Window fenetre) {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) {
            return null;
        }
        long glfw = fenetre.getWindow();
        if (GLFW.glfwGetWindowAttrib(glfw, GLFW.GLFW_FOCUSED) == 0 || GLFW.glfwGetWindowAttrib(glfw, GLFW.GLFW_ICONIFIED) != 0) {
            return null;
        }
        int[] px = new int[1];
        int[] py = new int[1];
        GLFW.glfwGetWindowPos(glfw, px, py);
        int w = fenetre.getWidth();
        int h = fenetre.getHeight();
        if (w <= 0 || h <= 0 || (long) w * h > 8_000L * 8_000L) {
            return null;
        }
        WinDef.HDC ecran = User32.INSTANCE.GetDC(null);
        if (ecran == null) {
            return null;
        }
        WinDef.HDC memoire = null;
        WinDef.HBITMAP bitmap = null;
        try {
            memoire = GDI32.INSTANCE.CreateCompatibleDC(ecran);
            bitmap = GDI32.INSTANCE.CreateCompatibleBitmap(ecran, w, h);
            if (memoire == null || bitmap == null) {
                return null;
            }
            WinNT.HANDLE avant = GDI32.INSTANCE.SelectObject(memoire, bitmap);
            boolean ok = GDI32.INSTANCE.BitBlt(memoire, 0, 0, w, h, ecran, px[0], py[0], GDI32.SRCCOPY | CAPTUREBLT);
            GDI32.INSTANCE.SelectObject(memoire, avant);
            if (!ok) {
                return null;
            }
            WinGDI.BITMAPINFO info = new WinGDI.BITMAPINFO();
            info.bmiHeader.biWidth = w;
            info.bmiHeader.biHeight = -h; // de haut en bas
            info.bmiHeader.biPlanes = 1;
            info.bmiHeader.biBitCount = 32;
            info.bmiHeader.biCompression = WinGDI.BI_RGB;
            Memory points = new Memory((long) w * h * 4);
            if (GDI32.INSTANCE.GetDIBits(ecran, bitmap, 0, h, points, info, WinGDI.DIB_RGB_COLORS) == 0) {
                return null;
            }
            int[] rgb = points.getIntArray(0, w * h);
            long somme = 0;
            for (int i = 0; i < rgb.length; i += 97) {
                int c = rgb[i];
                somme += (c >> 16 & 0xFF) + (c >> 8 & 0xFF) + (c & 0xFF);
            }
            if (somme == 0) {
                return null; // noire : plein écran exclusif, rien de lisible
            }
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, w, h, rgb, 0, w);
            return img;
        } finally {
            if (bitmap != null) {
                GDI32.INSTANCE.DeleteObject(bitmap);
            }
            if (memoire != null) {
                GDI32.INSTANCE.DeleteDC(memoire);
            }
            User32.INSTANCE.ReleaseDC(null, ecran);
        }
    }

    private static BufferedImage reduire(BufferedImage img) {
        if (img.getWidth() <= LARGEUR_MAX) {
            return img;
        }
        int w = LARGEUR_MAX;
        int h = Math.max(1, img.getHeight() * w / img.getWidth());
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** JPEG, qualité baissée jusqu'à tenir dans les morceaux permis. */
    private static byte[] encoder(BufferedImage img) {
        for (float q : new float[]{0.8f, 0.6f, 0.4f, 0.25f}) {
            try {
                Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpg");
                if (!it.hasNext()) {
                    return null;
                }
                ImageWriter w = it.next();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bos)) {
                    w.setOutput(out);
                    ImageWriteParam p = w.getDefaultWriteParam();
                    p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    p.setCompressionQuality(q);
                    w.write(null, new IIOImage(img, null, null), p);
                } finally {
                    w.dispose();
                }
                if (bos.size() <= OCTETS_MAX) {
                    return bos.toByteArray();
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
