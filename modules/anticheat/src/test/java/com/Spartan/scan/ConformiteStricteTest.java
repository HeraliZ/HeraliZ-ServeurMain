package com.Spartan.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Spartan.integrity.ConformiteStricte;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mode « jeu identique au jeu officiel » : le jeu propre (Windows, Steam ouvert, agent autorisé, antivirus qui verrouille
 * un jar) n'est JAMAIS refusé ; chaque écart certain (jar en plus, modifié, manquant, Mixin inconnue, lancement modifié)
 * l'est. Les indices devinés (overlay, threads, DLL) ne refusent jamais.
 */
class ConformiteStricteTest {
    private static final List<String> AGENTS = List.of("authlib-injector");
    private static final List<String> INTERDITS = List.of("wurst", "xray");

    private static OfficialProfile official() {
        return OfficialProfile.fromJson("""
                {"genere": "2026-10-27T00:00:00Z", "empreinteDossier": "%s",
                 "mods": {"JEI-1.20.1.jar": "%s", "create-1.20.1.jar": "%s"},
                 "mixins": {"jei.mixins.json": "mezz.jei.mixin", "create.mixins.json": "com.simibubi.create.foundation.mixin"}}
                """.formatted("9".repeat(64), ScanTestData.JEI_SHA, ScanTestData.CREATE_SHA));
    }

    private static List<String> ecarts(ScanReport r, OfficialProfile off) {
        return ConformiteStricte.ecarts(r, off, AGENTS, INTERDITS);
    }

    private static List<String> ecarts(ScanReport r) {
        return ecarts(r, official());
    }

    @Test
    void jeuPropreJamaisRefuse() {
        assertEquals(List.of(), ecarts(ScanTestData.clean()));
    }

    @Test
    void indicesDevinesNeRefusentJamais() {
        ScanReport r = ScanTestData.clean();
        r.injection.add("hook|opengl32!wglSwapBuffers|0x7ff1234|");   // overlay qui saute vers de la mémoire sans module
        r.injection.add("thread|0x1234");
        r.unnamedThreads = 3;
        r.natives.add(new ScanReport.NativeEntry("inject.dll", "%TEMP%", "nom:inject"));
        r.processHits.add("cheatengine-x86_64.exe");
        r.origins.add(new ScanReport.OriginEntry("?", List.of("a.B"), List.of("(sans nom)")));
        assertEquals(List.of(), ecarts(r));
    }

    @Test
    void fichiersSansEffetEtJarVerrouilleNeRefusentPas() {
        ScanReport r = ScanTestData.clean();
        r.files.add(new ScanReport.FileEntry("mods", ".DS_Store", 6_148, "c".repeat(64)));      // fichier de Mac, jamais chargé
        r.files.add(new ScanReport.FileEntry("mods", "config/", 0, ""));                          // dossier
        r.files.set(0, new ScanReport.FileEntry("mods", "jei-1.20.1.jar", 1_000_000, ""));       // verrouillé : sans empreinte
        assertEquals(List.of(), ecarts(r));
    }

    @Test
    void jarEnPlusRefuse() {
        ScanReport r = ScanTestData.clean();
        r.files.add(new ScanReport.FileEntry("modlist", "optimisation.jar", 50_000, "a".repeat(64)));
        List<String> e = ecarts(r);
        assertEquals(1, e.size(), e.toString());
        assertTrue(e.get(0).contains("en plus") && e.get(0).contains("optimisation.jar"), e.toString());
    }

    @Test
    void jarOfficielModifieRefuse() {
        ScanReport r = ScanTestData.clean();
        r.files.set(0, new ScanReport.FileEntry("mods", "jei-1.20.1.jar", 1_000_123, "b".repeat(64)));
        List<String> e = ecarts(r);
        assertTrue(e.stream().anyMatch(s -> s.contains("MODIFIÉ")), e.toString());
    }

    @Test
    void jarOfficielManquantRefuse() {
        ScanReport r = ScanTestData.clean();
        r.files.remove(1);   // create retiré
        r.loadedFiles.remove("mods folder|create-1.20.1.jar|create");
        List<String> e = ecarts(r);
        assertTrue(e.stream().anyMatch(s -> s.contains("manquant") && s.contains("create-1.20.1.jar")), e.toString());
    }

    @Test
    void modpackPrecedentAccepteJusteApresUneMiseEnLigne() {
        // la mise en ligne vient de changer jei : le joueur lancé juste avant a encore l'ancien jar
        OfficialProfile nouveau = OfficialProfile.fromJson("""
                {"genere": "2026-10-28T00:00:00Z", "empreinteDossier": "%s",
                 "mods": {"jei-1.20.2.jar": "%s", "create-1.20.1.jar": "%s"},
                 "mixins": {"jei.mixins.json": "mezz.jei.mixin", "create.mixins.json": "com.simibubi.create.foundation.mixin"}}
                """.formatted("8".repeat(64), "5".repeat(64), ScanTestData.CREATE_SHA));
        nouveau.precedent = official();
        assertEquals(List.of(), ecarts(ScanTestData.clean(), nouveau));
        // sans le précédent, l'ancien jar n'est plus officiel
        nouveau.precedent = null;
        assertTrue(!ecarts(ScanTestData.clean(), nouveau).isEmpty());
    }

    @Test
    void mixinInconnueRefusee() {
        ScanReport r = ScanTestData.clean();
        r.mixins.add(new ScanReport.MixinEntry("esp.mixins.json", "net.cheat.mixin", "", 2,
                List.of("net.minecraft.client.renderer.LevelRenderer")));
        assertTrue(ecarts(r).stream().anyMatch(s -> s.contains("Mixin inconnue")), ecarts(r).toString());
    }

    @Test
    void mixinDeguiseeRefusee() {
        ScanReport r = ScanTestData.clean();
        r.mixins.set(0, new ScanReport.MixinEntry("jei.mixins.json", "net.cheat.mixin", "jei-1.20.1.jar", 3, List.of()));
        assertTrue(ecarts(r).stream().anyMatch(s -> s.contains("déguisée")), ecarts(r).toString());
    }

    @Test
    void mixinDUnJarOfficielHorsListeNeRefusePas() {
        // liste officielle incomplète (config ajoutée au lancement par un mod officiel) : alerte, pas de refus
        ScanReport r = ScanTestData.clean();
        r.mixins.add(new ScanReport.MixinEntry("create.compat.mixins.json", "com.simibubi.create.compat", "create-1.20.1.jar", 1, List.of()));
        assertEquals(List.of(), ecarts(r));
    }

    @Test
    void lancementModifieRefuse() {
        for (String flag : List.of("-javaagent:C:/x/cheat.jar", "-agentlib:jdwp=transport=dt_socket", "-agentpath:inject.dll",
                "-Xbootclasspath/a:cheat.jar", "-Djava.system.class.loader=x.Y", "-Djdk.attach.allowAttachSelf", "arg:--fml.coreMods")) {
            ScanReport r = ScanTestData.clean();
            r.jvmFlags.add(flag);
            assertTrue(!ecarts(r).isEmpty(), flag);
        }
        ScanReport r = ScanTestData.clean();
        r.jvmFlags.add("arg:--fml.mavenRoots");   // option du launcher : normale
        assertEquals(List.of(), ecarts(r));
    }

    @Test
    void sansProtectionsDuLauncherRefuse() {
        ScanReport r = ScanTestData.clean();
        r.attachDisabled = false;
        assertTrue(ecarts(r).stream().anyMatch(s -> s.contains("protections")), ecarts(r).toString());
    }

    @Test
    void autreVoieDeChargementOuModInterditOuSpartanModifieRefuse() {
        ScanReport r = ScanTestData.clean();
        r.loadedFiles.add("exploded directory|cheat|cheat");
        assertTrue(!ecarts(r).isEmpty());
        r = ScanTestData.clean();
        r.loadedFiles.add("mods folder|jei-1.20.1.jar|jei,wurst");
        assertTrue(ecarts(r).stream().anyMatch(s -> s.contains("interdit")));
        r = ScanTestData.clean();
        r.tamper.add("com.Spartan.Client.Gardien");
        assertTrue(ecarts(r).stream().anyMatch(s -> s.contains("modifié en mémoire")));
        r = ScanTestData.clean();
        r.classHits.add("net.wurstclient.WurstClient");
        assertTrue(ecarts(r).stream().anyMatch(s -> s.contains("cheat connu")));
    }

    @Test
    void sansModpackOfficielSeulsLesLancementsSontJuges() {
        ScanReport r = ScanTestData.clean();
        r.files.add(new ScanReport.FileEntry("mods", "x.jar", 1, "a".repeat(64)));
        assertEquals(List.of(), ecarts(r, new OfficialProfile()));
    }
}
