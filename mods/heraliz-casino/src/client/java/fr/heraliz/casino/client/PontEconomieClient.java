package fr.heraliz.casino.client;

import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.EconomieServeur;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Cote client : le solde de jetons du serveur (mod HeraliZ Economie, facultatif) pour l'affichage de la caisse. Le
 * mod economie envoie lui-meme le solde au joueur ; on le lit dans sa classe ClientSolde, par {@link Lien} qui n'est
 * chargee que si le mod est present.
 */
public final class PontEconomieClient {
    private PontEconomieClient() {
    }

    /** Police du mod economie (glyphe « jeton » U+E000, petit jeton de laiton). */
    public static final ResourceLocation FONT_ECONOMIE = new ResourceLocation(EconomieServeur.MODID, "ui");
    public static final String GLYPHE_JETON = String.valueOf((char) 0xE000);

    private static boolean lisible = true;

    /** Solde de jetons du serveur recu du serveur (0 sans economie). */
    public static long solde() {
        if (!EconomieServeur.present() || !lisible) return 0;
        try {
            return Lien.solde();
        } catch (LinkageError e) {
            lisible = false;
            return 0;
        }
    }

    /** « 1 250 jetons » avec les noms recus du serveur (EconomieApi.format) ; sans economie : le nombre seul. */
    public static String format(long v) {
        if (EconomieServeur.present() && lisible) {
            try {
                return Lien.format(v);
            } catch (LinkageError e) {
                lisible = false;
            }
        }
        return Chips.format(v);
    }

    /** Le glyphe « jeton » seul (police du mod economie : la police du jeu ne l'a pas). */
    public static MutableComponent glyphe() {
        return Component.literal(GLYPHE_JETON).withStyle(s -> s.withFont(FONT_ECONOMIE));
    }

    /** Montant affiche : glyphe « jeton » puis « 1 250 jetons » (texte dans la police du casino). */
    public static MutableComponent montant(long v) {
        return Component.empty().append(glyphe()).append(Component.literal(" " + format(v)));
    }

    /** Les seuls appels au mod economie cote client (classe chargee a la demande). */
    private static final class Lien {
        static long solde() {
            return fr.heralisz.economie.client.ClientSolde.solde();
        }

        static String format(long v) {
            return fr.heralisz.economie.client.ClientSolde.format(v);
        }
    }
}
