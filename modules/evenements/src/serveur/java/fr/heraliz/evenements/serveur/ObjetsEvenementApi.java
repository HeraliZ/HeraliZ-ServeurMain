package fr.heraliz.evenements.serveur;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Passage public vers les objets d'événement ({@link ObjetsServeur}) pour {@code fr.heraliz.evenements.api.EvenementsApi}. */
public final class ObjetsEvenementApi {
    private ObjetsEvenementApi() {
    }

    public static ItemStack creer(String evenement) {
        return ObjetsServeur.creer(evenement);
    }

    public static long prix(ItemStack s) {
        return ObjetsServeur.prix(s);
    }

    public static int points(ItemStack s) {
        String type = ObjetsServeur.type(s);
        return type == null || ObjetsServeur.essai(s) ? 0 : (int) Math.round(Outils.pointsBareme("objet_" + type));
    }

    public static int raisonSansPoints(ServerPlayer p) {
        return Outils.raisonSansPoints(p);
    }

    @Nullable
    public static long[] vendre(ServerPlayer p, ItemStack s) {
        return ObjetsServeur.vendre(p, s);
    }
}
