package com.heraliz.extraction.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Extractions ouvertes, lues par les autres mods (carte du monde). API stable : ne pas renommer cette classe ni changer
 * ses signatures (CLAUDE.md §5).
 * <p>
 * Côté joueur : la liste envoyée par le serveur (zones fixes ouvertes, rotation et ouvertures à la main comprises ; pas
 * les fusées des joueurs, qu'on repère à leur fumée), remplacée à chaque ouverture ou fermeture. Vide sur un serveur dédié.
 */
public final class ExtractionApi {
    /**
     * Une extraction ouverte.
     *
     * @param nom            identifiant de la zone (/extraction)
     * @param libelle        nom affiché (« Aérodrome nord »)
     * @param secondesATenir temps à tenir dans la zone pour être extrait
     * @param finTick        heure de jeu ({@code level.getGameTime()}) de la fermeture, -1 = zone permanente
     */
    public record Extraction(String nom, String libelle, ResourceLocation dimension, double x, double y, double z,
                             float rayon, int secondesATenir, long finTick) {
        public boolean permanente() {
            return this.finTick < 0;
        }

        /** Secondes avant la fermeture (-1 = permanente). */
        public int secondesRestantes(long heureDeJeu) {
            return this.finTick < 0 ? -1 : (int) Math.max(0L, (this.finTick - heureDeJeu) / 20L);
        }
    }

    private static volatile List<Extraction> ouvertes = List.of();
    private static volatile int version;

    private ExtractionApi() {
    }

    /** Côté joueur : les extractions ouvertes (liste figée, à relire à chaque image ou quand {@link #version()} change). */
    public static List<Extraction> ouvertes() {
        return ouvertes;
    }

    /** Change à chaque mise à jour de la liste (pour qu'une carte sache quand se redessiner). */
    public static int version() {
        return version;
    }

    /** Réception de la liste envoyée par le serveur (paquet du mod), ou liste vidée à la déconnexion. */
    public static void recevoir(List<Extraction> zones) {
        ouvertes = List.copyOf(zones);
        version++;
    }
}
