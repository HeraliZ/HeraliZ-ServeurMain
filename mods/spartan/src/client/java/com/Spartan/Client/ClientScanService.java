package com.Spartan.Client;

import com.Spartan.Networks.ModMessages;
import com.Spartan.Networks.ScanChunkPacket;
import com.Spartan.Networks.ScanRequestPacket;
import com.Spartan.integrity.ModsHasher;
import com.Spartan.scan.ScanReport;
import com.Spartan.scan.ScanTransfer;
import com.Spartan.scan.collect.ScanCollector;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Côté joueur : exécute les contrôles demandés par le serveur, hors du thread
 * du jeu, et renvoie le rapport. Rien n'est affiché au joueur.
 */
public final class ClientScanService {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Spartan-Scan");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private ClientScanService() {
    }

    /** Appelé sur le thread du jeu (c'est aussi le thread de rendu, échantillonné pendant le contrôle). */
    public static void onRequest(ScanRequestPacket req) {
        Thread render = Thread.currentThread();
        Set<Long> known = new HashSet<>();
        for (long fp : req.knownMixins()) {
            known.add(fp);
        }
        ScanCollector.Context ctx = new ScanCollector.Context(req.scanId(), FMLPaths.MODSDIR.get(), FMLPaths.GAMEDIR.get(),
                ModsHasher.inventoryIfReady(), render, req.samples(), req.referenceKnown(), known, req.watchClasses(),
                req.classProbes(), req.processPatterns(), req.nativePatterns());
        EXEC.submit(() -> {
            List<byte[]> chunks;
            try {
                chunks = pack(ScanCollector.collect(ctx));
            } catch (Throwable t) {
                ScanReport failed = new ScanReport();
                failed.scanId = req.scanId();
                failed.errors.add(ScanReport.cut("contrôle : " + t, 250));
                chunks = pack(failed);
            }
            List<byte[]> toSend = chunks;
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().getConnection() == null) {
                    return; // déconnecté entre-temps
                }
                for (int i = 0; i < toSend.size(); i++) {
                    ModMessages.sendToServer(new ScanChunkPacket(req.scanId(), i, toSend.size(), toSend.get(i)));
                }
            });
        });
    }

    /** Compresse ; si le rapport est exceptionnellement gros, on retire d'abord le moins utile. */
    private static List<byte[]> pack(ScanReport r) {
        List<byte[]> chunks = ScanTransfer.pack(r);
        if (chunks.size() > ScanTransfer.MAX_CHUNKS) {
            r.threadNames.clear();
            r.natives.removeIf(n -> n.reason().isEmpty());
            chunks = ScanTransfer.pack(r);
        }
        if (chunks.size() > ScanTransfer.MAX_CHUNKS) {
            r.loadedFiles.clear();
            r.modules.removeIf(m -> m.startsWith("GAME/"));
            chunks = ScanTransfer.pack(r);
        }
        if (chunks.size() > ScanTransfer.MAX_CHUNKS) {
            ScanReport minimal = new ScanReport();
            minimal.scanId = r.scanId;
            minimal.errors.add("rapport trop volumineux pour être envoyé");
            chunks = ScanTransfer.pack(minimal);
        }
        return chunks;
    }
}
