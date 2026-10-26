package com.Spartan.Networks;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

import io.netty.buffer.Unpooled;
import java.time.Duration;
import java.util.Random;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * Injection de données aléatoires dans chaque décodeur de paquet Spartan.
 *
 * Un client peut envoyer n'importe quels octets sur le canal du mod. Exigence :
 * le décodeur produit un résultat borné ou refuse proprement (exception
 * ordinaire → seul ce client est déconnecté). Jamais d'Error (mémoire épuisée,
 * pile), jamais de blocage.
 */
class PacketFuzzTest {
    private static final int ROUNDS = 4000;

    private static void fuzz(String name, Function<FriendlyByteBuf, Object> decoder) {
        Random rnd = new Random(name.hashCode());
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            for (int i = 0; i < ROUNDS; i++) {
                byte[] data = payload(rnd, i);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                try {
                    decoder.apply(buf);
                } catch (RuntimeException expected) {
                    // refus propre
                } catch (Error e) {
                    fail(name + " : " + e + " sur l'entrée n°" + i);
                }
            }
        });
    }

    /** Entrées aléatoires, plus des cas construits : tailles annoncées énormes, négatives, coupées. */
    private static byte[] payload(Random rnd, int i) {
        switch (i % 5) {
            case 0 -> {
                return varInt(Integer.MAX_VALUE); // taille annoncée géante
            }
            case 1 -> {
                byte[] b = varInt(-1 - rnd.nextInt(1000)); // taille négative
                return concat(b, randomBytes(rnd, rnd.nextInt(16)));
            }
            case 2 -> {
                byte[] b = varInt(100 + rnd.nextInt(100000)); // taille annoncée, contenu absent
                return concat(b, randomBytes(rnd, rnd.nextInt(8)));
            }
            case 3 -> {
                return new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}; // VarInt invalide
            }
            default -> {
                return randomBytes(rnd, rnd.nextInt(2048));
            }
        }
    }

    private static byte[] randomBytes(Random rnd, int n) {
        byte[] b = new byte[n];
        rnd.nextBytes(b);
        return b;
    }

    private static byte[] varInt(int v) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(v);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    @Test
    void stringListIsBounded() {
        fuzz("readStringList", PacketUtil::readStringList);
    }

    @Test
    void modsPacket() {
        fuzz("ModsPacket", ModsPacket::decode);
    }

    @Test
    void resourcePackPacket() {
        fuzz("ResourcePackPacket", ResourcePackPacket::decode);
    }

    @Test
    void launchTokenPacket() {
        fuzz("LaunchTokenPacket", LaunchTokenPacket::decode);
    }

    @Test
    void buildPacket() {
        fuzz("BuildPacket", BuildPacket::decode);
    }

    @Test
    void challengeResponsePacket() {
        fuzz("ChallengeResponsePacket", ChallengeResponsePacket::decode);
    }

    @Test
    void challengePacket() {
        fuzz("ChallengePacket", ChallengePacket::decode);
    }

    @Test
    void clientReportPacket() {
        fuzz("ClientReportPacket", ClientReportPacket::decode);
    }

    @Test
    void hardwarePacket() {
        fuzz("TestPacket", TestPacket::decode);
    }

    @Test
    void requestModsPacket() {
        fuzz("RequestModsPacket", RequestModsPacket::decode);
    }

    @Test
    void scanChunkPacket() {
        fuzz("ScanChunkPacket", ScanChunkPacket::decode);
    }

    @Test
    void scanRequestPacket() {
        fuzz("ScanRequestPacket", ScanRequestPacket::decode);
    }

    @Test
    void scanReportDecoder() {
        fuzz("ScanReport", com.Spartan.scan.ScanReport::read);
    }

    @Test
    void compressedScanReport() {
        // Données compressées aléatoires ou plausibles : refus propre, jamais d'Error.
        fuzz("ScanTransfer.unpack", buf -> {
            byte[] b = new byte[buf.readableBytes()];
            buf.readBytes(b);
            return com.Spartan.scan.ScanTransfer.unpack(b);
        });
    }
}
