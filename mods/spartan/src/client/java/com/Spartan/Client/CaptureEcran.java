package com.Spartan.Client;

import com.Spartan.Networks.CaptureChunkPacket;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Spartan;
import com.Spartan.scan.ScanTransfer;
import com.mojang.blaze3d.platform.NativeImage;
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

/**
 * Capture de l'écran du jeu demandée par le serveur (CaptureRequestPacket). Prise SEULEMENT si le joueur l'a autorisée
 * dans le launcher HeraliZ (variable d'environnement SPARTAN_CAPTURE_CONSENT=1) : sans accord, rien n'est pris ni
 * envoyé. Rien n'est écrit sur le disque du joueur, aucun message ne s'affiche.
 */
final class CaptureEcran {
    private static final int LARGEUR_MAX = 1280;
    private static final int OCTETS_MAX = ScanTransfer.CHUNK_BYTES * ScanTransfer.MAX_CHUNKS - 1024;

    private CaptureEcran() {
    }

    /** Accord donné dans le launcher. */
    static boolean autorisee() {
        String v = System.getenv("SPARTAN_CAPTURE_CONSENT");
        return v != null && v.trim().equals("1");
    }

    /** Sur le thread du jeu (lecture de l'image affichée), puis compression et envoi à côté. */
    static void prendre(int id) {
        if (!autorisee()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }
        BufferedImage img;
        try (NativeImage ni = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int w = ni.getWidth();
            int h = ni.getHeight();
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = ni.getPixelRGBA(x, y); // ABGR
                    img.setRGB(x, y, (c & 0xFF) << 16 | (c >> 8 & 0xFF) << 8 | (c >> 16 & 0xFF));
                }
            }
        } catch (Throwable t) {
            Spartan.LOGGER.debug("[Spartan] capture impossible : {}", t.toString());
            return;
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
