package fr.heraliz.usure;

import fr.heraliz.usure.regles.Regles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Les tirs cote serveur (seul juge) :
 * <ul>
 *   <li>arme ruinee (0 %) : le tir est refuse ({@code GunShootEvent}, priorite haute) et les balles suivantes d'une
 *   rafale aussi ({@code GunFireEvent}) ;</li>
 *   <li>chaque balle vraiment tiree ({@code GunFireEvent} non annule, priorite la plus basse) use l'arme : usure de la
 *   categorie (ou de l'arme), plus sous la pluie, dans l'eau, avec un silencieux ou un accessoire regle.</li>
 * </ul>
 * Rien d'autre : pas d'enrayage, ni degats ni precision en moins (choix de l'utilisateur, 1.0.1).
 * Aucune usure en match, sur une arme pretee, dans une dimension sans usure, en creatif (reglable). Aucune allocation
 * par tir : identifiant lu tel quel dans le NBT, profil en cache, entiers 0..1000 gardes en cache par Minecraft.
 * <p>Jar fusionne (docs/METHODE_CLIENT_SERVEUR.md §5) : ici, ce que lisent les deux cotes (exemptions, aussi lues par
 * le client pour le HUD et le clic a vide ; calcul de la perte) ; les ecouteurs des tirs sont dans
 * {@code TirsServeur} (partie serveur, inscrits par serveur.PartieServeur).
 */
public final class Tirs {
    private Tirs() {
    }

    /** Aucune usure : mod coupe, creatif, match en cours, arme pretee, dimension sans usure. */
    public static boolean exemptee(Player p, ItemStack arme, Regles r) {
        if (!r.actif) return true;
        if ((p.isCreative() || p.isSpectator()) && !r.usureCreatif) return true;
        if (r.sansUsureEnMatch) {
            if (Usure.pretee(arme)) return true;
            if (p instanceof ServerPlayer && p.getPersistentData().getBoolean(HeralizUsure.DRAPEAU_EN_MATCH)) return true;
        }
        return r.dimensionSansUsure(p.level().dimension().location());
    }

    /**
     * Milliemes perdus pour une usure de {@code milliemes} (fractionnaire) : arrondi au hasard ({@code hasard} dans
     * [0, 1[), 0,7 millieme = 1 millieme 7 fois sur 10 : exact en moyenne avec des entiers 0..1000.
     */
    public static int perte(float milliemes, float hasard) {
        if (milliemes <= 0) return 0;
        int perte = (int) milliemes;
        if (hasard < milliemes - perte) perte++;
        return perte;
    }
}
