package fr.heraliz.interfaces.client;

import fr.heraliz.interfaces.HeralizInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Réglages du jeu posés par HeraliZ :
 * <ul>
 *     <li>toujours : plus de tutoriel de Minecraft (« Déplacez-vous avec Z Q S D »…) ;</li>
 *     <li>une seule fois par installation (repère config/heraliz_interface-reglages.txt, numéro {@link #VERSION}) :
 *     réglages qui libèrent des images par seconde sans rien enlever au jeu (synchronisation verticale coupée, images
 *     par seconde illimitées, nuages et ombres des entités coupés). Le joueur peut les remettre : on n'y revient pas.</li>
 * </ul>
 */
final class ReglagesDefaut {
    /** À monter pour reposer de nouveaux réglages une fois chez tout le monde. */
    static final int VERSION = 1;

    private ReglagesDefaut() {
    }

    static void appliquer(Minecraft mc) {
        Options o = mc.options;
        if (o.tutorialStep != TutorialSteps.NONE) {
            mc.getTutorial().setStep(TutorialSteps.NONE);
        }
        Path repere = FMLPaths.CONFIGDIR.get().resolve("heraliz_interface-reglages.txt");
        if (lu(repere) >= VERSION) {
            return;
        }
        o.enableVsync().set(false);
        o.framerateLimit().set(260);                 // 260 = illimité
        o.cloudStatus().set(CloudStatus.OFF);
        o.entityShadows().set(false);
        o.save();
        try {
            Files.writeString(repere, VERSION + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            HeralizInterface.LOG.warn("[interface] repère des réglages non écrit : {}", e.toString());
        }
        HeralizInterface.LOG.info("[interface] réglages HeraliZ posés (version {})", VERSION);
    }

    private static int lu(Path f) {
        try {
            return Files.exists(f) ? Integer.parseInt(Files.readString(f, StandardCharsets.UTF_8).trim()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
