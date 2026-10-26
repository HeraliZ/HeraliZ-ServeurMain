package fr.heraliz.evenements.api;

import fr.heraliz.evenements.serveur.ObjetsEvenementApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * API de HeraliZ Événements pour les autres mods, côté serveur, appelée PAR RÉFLEXION (Class.forName) : ne jamais
 * renommer cette classe ni changer ces signatures. Fil serveur seulement.
 */
public final class EvenementsApi {
    private EvenementsApi() {
    }

    /**
     * Un coffre d'événement ({@code evenement} : « largage#12 ») est ouvert pour la première fois par ce joueur (caisse de
     * largage des frappes aériennes) : l'événement donne ses points et l'annonce, une seule fois.
     */
    public static void coffreOuvert(ServerPlayer joueur, String evenement) {
        fr.heraliz.evenements.serveur.Directeur.coffreOuvert(joueur, evenement);
    }

    /**
     * L'objet d'événement à revendre à mettre dans le coffre de l'événement {@code evenement} (« largage#12 ») à sa
     * première ouverture : un exemplaire neuf, numéroté et signé (vide si les objets sont coupés).
     */
    public static ItemStack objetDuCoffre(String evenement) {
        return ObjetsEvenementApi.creer(evenement);
    }

    /** Prix de rachat d'un objet d'événement : -1 si ce n'en est pas un, 0 s'il ne se vend pas (copie, déjà vendu). */
    public static long prixObjet(ItemStack objet) {
        return ObjetsEvenementApi.prix(objet);
    }

    /** Points de saison que l'objet rapporte au clan du vendeur, d'après le barème (avant plafond du jour), 0 sinon. */
    public static int pointsObjet(ItemStack objet) {
        return ObjetsEvenementApi.points(objet);
    }

    /** Pourquoi le clan du joueur ne gagnerait aucun point : 0 il en gagne, 1 pas de saison, 2 sans clan, 3 clan sans cœur. */
    public static int raisonSansPoints(ServerPlayer joueur) {
        return ObjetsEvenementApi.raisonSansPoints(joueur);
    }

    /**
     * Vend l'objet d'événement au marchand : noté vendu sur le disque, puis points de saison au clan du joueur. Rend
     * {hérals à verser, points donnés}, ou null s'il ne se vend pas (rien n'est fait). Le marchand retire l'objet et paie.
     */
    public static long[] vendreObjet(ServerPlayer joueur, ItemStack objet) {
        return ObjetsEvenementApi.vendre(joueur, objet);
    }
}
