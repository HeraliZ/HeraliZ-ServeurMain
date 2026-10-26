package fr.heralisz.clans.model;

import java.util.UUID;

/**
 * Ligne du classement des clans par niveau (top 20 de {@code /clan top}, dans le chat) : rang, identite, niveau, xp
 * totale, membres, kills, territoires. L'ecran du clan montre le classement de la saison ({@link SaisonLigne}).
 */
public record ClanLigne(int rang, UUID id, String tag, String nom, Banniere banniere, int niveau, long xpTotale, int membres,
                        int kills, int territoires) {
}
