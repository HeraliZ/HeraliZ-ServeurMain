package fr.heraliz.usure.client;

import fr.heraliz.usure.Tirs;
import fr.heraliz.usure.Usure;
import fr.heraliz.usure.regles.Profil;
import fr.heraliz.usure.regles.Regles;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Regles recues du serveur (valeurs par defaut tant qu'il n'a rien envoye). Fil du rendu. */
public final class ReglesClient {
    private ReglesClient() {
    }

    private static Regles actives = Regles.defauts();

    public static Regles get() {
        return actives;
    }

    static void set(Regles r) {
        actives = r == null ? Regles.defauts() : r;
    }

    /**
     * Profil de l'arme si elle est suivie (s'use) et que le joueur n'en est pas exempte (match, creatif,
     * dimension sans usure, arme pretee) ; null sinon. Sans allocation.
     */
    @Nullable
    public static Profil suivie(Player p, ItemStack arme) {
        String id = Usure.gunId(arme);
        if (id == null) return null;
        Regles r = actives;
        if (!r.actif) return null;
        Profil pr = r.profil(id);
        if (!pr.suivie || (p != null && Tirs.exemptee(p, arme, r))) return null;
        return pr;
    }
}
