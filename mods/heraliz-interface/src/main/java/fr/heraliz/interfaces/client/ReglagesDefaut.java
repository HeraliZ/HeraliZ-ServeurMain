package fr.heraliz.interfaces.client;

import fr.heraliz.interfaces.HeralizInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
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
 *     par seconde illimitées, nuages et ombres des entités coupés ; version 2 : graphismes « Détaillés » au lieu de
 *     « Fabuleux », mélange des biomes à 1, trous de balle de TaCZ gardés 8 s au lieu de 20). Le joueur peut les
 *     remettre : on n'y revient pas.</li>
 * </ul>
 */
final class ReglagesDefaut {
    /** À monter pour reposer de nouveaux réglages une fois chez tout le monde. */
    static final int VERSION = 2;

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
        int avant = lu(repere);
        if (avant < 1) {
            o.enableVsync().set(false);
            o.framerateLimit().set(260);                 // 260 = illimité
            o.cloudStatus().set(CloudStatus.OFF);
            o.entityShadows().set(false);
        }
        if (avant < 2) {
            if (o.graphicsMode().get() == GraphicsStatus.FABULOUS) {
                o.graphicsMode().set(GraphicsStatus.FANCY);
            }
            if (o.biomeBlendRadius().get() > 1) {
                o.biomeBlendRadius().set(1);
            }
            trousDeBalle(160);
        }
        o.save();
        try {
            Files.writeString(repere, VERSION + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            HeralizInterface.LOG.warn("[interface] repère des réglages non écrit : {}", e.toString());
        }
        HeralizInterface.LOG.info("[interface] réglages HeraliZ posés (version {})", VERSION);
    }

    /** Durée de vie des trous de balle de TaCZ (réglage joueur de son tacz-client.toml), lue par réflexion. */
    private static void trousDeBalle(int ticks) {
        try {
            Object valeur = Class.forName("com.tacz.guns.config.client.RenderConfig").getField("BULLET_HOLE_PARTICLE_LIFE").get(null);
            if (valeur instanceof net.minecraftforge.common.ForgeConfigSpec.IntValue v && v.get() > ticks) {
                v.set(ticks);
                v.save();
            }
        } catch (Throwable e) {
            HeralizInterface.LOG.warn("[interface] réglage des trous de balle de TaCZ non posé : {}", e.toString());
        }
    }

    private static int lu(Path f) {
        try {
            return Files.exists(f) ? Integer.parseInt(Files.readString(f, StandardCharsets.UTF_8).trim()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
