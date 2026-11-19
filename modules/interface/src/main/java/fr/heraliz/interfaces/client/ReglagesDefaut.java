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
 *     « Fabuleux », mélange des biomes à 1, trous de balle de TaCZ gardés 8 s au lieu de 20 ; version 3 : nuages
 *     rallumés, ceux du profil Équilibré de {@link Qualite} — sur la carte, ce sont nos nuages doux (un seul carré,
 *     {@code Ciel.nuages} de HeraliZ Utils) : Minecraft ne les dessine que si ce réglage n'est pas « Non »
 *     ({@code LevelRenderer.renderLevel} teste {@code Options.getCloudsType()} avant {@code renderClouds}, qui passe
 *     d'abord par {@code DimensionSpecialEffects.renderClouds} ; Embeddium n'intervient qu'après, si nos nuages ne se
 *     sont pas dessinés) ; hors de la carte, aucun nuage ; version 4, 25/10, choix du propriétaire : le profil
 *     Performance de {@link Qualite} chez tout le monde, même ceux qui avaient choisi un autre profil, synchronisation
 *     verticale coupée et images par seconde illimitées ; version 5, 19/11, choix du propriétaire : images par seconde
 *     plafonnées à {@link #PLAFOND_IPS} au lieu d'illimitées, carte graphique moins chargée, moins de chauffe sur les
 *     portables, temps entre deux images plus régulier). Le joueur peut les remettre : on n'y revient pas.</li>
 *     <li>à chaque lancement : le cran {@link #CRAN_PLAFOND} du réglage vaut {@link #PLAFOND_IPS} images par seconde
 *     ({@link #plafond}).</li>
 * </ul>
 */
final class ReglagesDefaut {
    /** À monter pour reposer de nouveaux réglages une fois chez tout le monde. */
    static final int VERSION = 5;
    /** Plafond d'images par seconde posé par HeraliZ (version 5). */
    static final int PLAFOND_IPS = 144;
    /**
     * Cran du réglage de Minecraft qui le porte : ce réglage ne garde que des multiples de 10 (curseur de 10 à 260 de
     * Minecraft et d'Embeddium ; {@code OptionInstance.set(144)} et options.txt donnent 140).
     */
    static final int CRAN_PLAFOND = 140;

    private ReglagesDefaut() {
    }

    static void appliquer(Minecraft mc) {
        Options o = mc.options;
        if (o.tutorialStep != TutorialSteps.NONE) {
            mc.getTutorial().setStep(TutorialSteps.NONE);
        }
        Path repere = FMLPaths.CONFIGDIR.get().resolve("heraliz_interface-reglages.txt");
        if (lu(repere) >= VERSION) {
            plafond(mc);
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
        if (avant < 3 && o.cloudStatus().get() == CloudStatus.OFF) {
            o.cloudStatus().set(Qualite.Profil.EQUILIBRE.nuages);
        }
        if (avant < 4) {
            Qualite.parDefaut(Qualite.Profil.PERFORMANCE);      // enregistre aussi le profil choisi
            o.enableVsync().set(false);
            o.framerateLimit().set(260);                     // 260 = illimité
        }
        if (avant < 5) {
            o.framerateLimit().set(CRAN_PLAFOND);            // 144 images par seconde (plafond), voir plafond()
        }
        o.save();
        plafond(mc);
        try {
            Files.writeString(repere, VERSION + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            HeralizInterface.LOG.warn("[interface] repère des réglages non écrit : {}", e.toString());
        }
        HeralizInterface.LOG.info("[interface] réglages HeraliZ posés (version {})", VERSION);
    }

    /**
     * Le cran {@link #CRAN_PLAFOND} vaut {@link #PLAFOND_IPS} images par seconde : posé à chaque lancement sur la fenêtre
     * (c'est elle qui limite, {@code Minecraft.getFramerateLimit}). Un joueur qui choisit un autre cran (150, 260 =
     * illimité…) l'a tel quel, tout de suite (Minecraft repose la fenêtre à chaque changement du réglage).
     */
    static void plafond(Minecraft mc) {
        if (mc.options.framerateLimit().get() == CRAN_PLAFOND) {
            mc.getWindow().setFramerateLimit(PLAFOND_IPS);
        }
    }

    /** Images par seconde que donne le réglage en cours (le cran du plafond vaut {@link #PLAFOND_IPS}). */
    static int ips(Options o) {
        int v = o.framerateLimit().get();
        return v == CRAN_PLAFOND ? PLAFOND_IPS : v;
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
