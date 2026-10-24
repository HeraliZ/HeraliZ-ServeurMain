package com.Spartan.Client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Rapports du Gardien du launcher : même format et même signature que launcher/app/assets/js/gardien.js. */
class GardienTest {
    private static final byte[] CLE = HexFormat.of().parseHex("ab".repeat(32));

    private static String fichier(String ligne, byte[] cle) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(cle, "HmacSHA256"));
        return ligne + "\n" + HexFormat.of().formatHex(mac.doFinal(ligne.getBytes(StandardCharsets.UTF_8))) + "\n";
    }

    @BeforeEach
    void oublier() {
        Gardien.oublier();
    }

    @Test
    void rapportValide() throws Exception {
        assertEquals("ok;lecture=ok;outils=cheatengine,xenos;jars=0",
                Gardien.juger(fichier("v1|1|1000|42|ok|cheatengine,xenos|0", CLE), CLE, 42, 2000));
        assertEquals("ok;lecture=impossible;outils=;jars=3",
                Gardien.juger(fichier("v1|2|1000|42|impossible||3", CLE), CLE, 42, 2000));
    }

    @Test
    void faussesSignaturesEtAutresParties() throws Exception {
        String bon = fichier("v1|5|1000|42|ok||0", CLE);
        assertEquals("invalide:signature", Gardien.juger(bon.replace("|ok||0", "|ok||9"), CLE, 42, 2000));
        assertEquals("invalide:signature", Gardien.juger(fichier("v1|5|1000|42|ok||0", HexFormat.of().parseHex("cd".repeat(32))), CLE, 42, 2000));
        assertEquals("invalide:pid", Gardien.juger(bon, CLE, 43, 2000));
        assertEquals("invalide:format", Gardien.juger("rien", CLE, 42, 2000));
    }

    @Test
    void rapportRejoueOuVieux() throws Exception {
        assertEquals("ok;lecture=ok;outils=;jars=0", Gardien.juger(fichier("v1|7|1000|42|ok||0", CLE), CLE, 42, 2000));
        assertEquals("invalide:rejoue", Gardien.juger(fichier("v1|6|1000|42|ok||0", CLE), CLE, 42, 2000));
        assertEquals("muet:vieux", Gardien.juger(fichier("v1|7|1000|42|ok||0", CLE), CLE, 42, 1000 + 60_000));
    }
}
