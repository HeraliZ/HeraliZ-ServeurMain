package fr.heraliz.usure;

import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Rareté d'un objet telle que HeraliZ Loot la donne : couleur légère derrière l'objet dans les cases et ligne « Rare » de
 * l'infobulle. Même source que le reste du serveur ({@code fr.heraliz.loot.RaretesObjets.de(ItemStack)}, lue par réflexion
 * comme le font les clans et les quêtes : aucune dépendance de compilation) : {@code config/heraliz_loot/raretes.json},
 * choix du staff ({@code /rarete}), rareté inscrite sur l'objet (coffres T7). Côté joueur, la table envoyée par le
 * serveur à la connexion. Sert aux réparations (11/11) : nombre de réparations d'une arme et gain d'un kit selon leur
 * rareté. Rang 0 Commun (gris), 1 Peu commun (vert), 2 Rare (bleu), 3 Épique (violet), 4 Légendaire, 5 Unique ; -1 :
 * inconnue (HeraliZ Loot absent, ou objet sans rareté).
 */
public final class RaretesLoot {
    private RaretesLoot() {
    }

    /** Couleurs (RGB) des raretés, par rang : celles de HeraliZ Loot ({@code fr.heraliz.loot.Rarete}), pour les écrans. */
    public static final int[] COULEURS = {0x9A9A9A, 0x5CBF4A, 0x3F8CFF, 0xA64DFF, 0xF2B83A, 0xFF3333};

    private static volatile Method de;
    private static volatile boolean cherchee;

    /** Rang de rareté de l'objet (0 Commun ... 5 Unique), -1 si inconnue. Sans allocation hors de HeraliZ Loot. */
    public static int rang(ItemStack s) {
        if (s.isEmpty()) return -1;
        Method m = methode();
        if (m == null) return -1;
        try {
            return m.invoke(null, s) instanceof Enum<?> e ? e.ordinal() : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static Method methode() {
        if (!cherchee) {
            Method m = null;
            try {
                m = Class.forName("fr.heraliz.loot.RaretesObjets").getMethod("de", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                HeralizUsure.LOG.warn("[usure] HeraliZ Loot absent : raretés inconnues (armes comptées Communes, kits à leur rareté du loot)");
            }
            de = m;
            cherchee = true;
        }
        return de;
    }
}
