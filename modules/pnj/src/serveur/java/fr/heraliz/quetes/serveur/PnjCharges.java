package fr.heraliz.quetes.serveur;

import fr.heraliz.quetes.entite.Pnj;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * PNJ des quêtes présents dans les mondes (20/11, audit du tick : {@code Accueil.pnj}, 0,18 ms par tick au banc à 150
 * joueurs). Les repères des nouveaux joueurs (lueur vers le guide puis Varga, toutes les 2 s), le rappel « tape /spawn » et la
 * présentation du Bâtisseur (chaque seconde) cherchaient leur PNJ parmi TOUTES les entités du monde du joueur (1 500 sur la
 * carte avec ses zombies) ; il y a une dizaine de PNJ. Ici, chaque PNJ s'inscrit quand il entre dans un monde (tronçon
 * chargé, apparition) et se retire quand il en sort (tronçon déchargé, mort, changement de monde) : mêmes PNJ que la
 * recherche de Minecraft. Fil serveur.
 */
final class PnjCharges {
    private PnjCharges() {
    }

    private static final List<Pnj> TOUS = new ArrayList<>();

    static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> TOUS.clear());
    }

    static void arrive(Pnj p) {
        if (!TOUS.contains(p)) TOUS.add(p);
    }

    static void part(Pnj p) {
        TOUS.remove(p);
    }

    /** Nombre de PNJ inscrits (tous mondes) ; avec {@link #pnj}, parcours sans rien créer. */
    static int nombre() {
        return TOUS.size();
    }

    /** Le i-ème PNJ inscrit s'il est dans ce monde (et pas retiré), sinon null. */
    static Pnj pnj(int i, ServerLevel monde) {
        Pnj p = TOUS.get(i);
        return p.level() == monde && !p.isRemoved() ? p : null;
    }
}
