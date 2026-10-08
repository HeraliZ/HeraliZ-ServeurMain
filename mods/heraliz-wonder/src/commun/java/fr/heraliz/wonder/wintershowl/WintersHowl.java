package fr.heraliz.wonder.wintershowl;

import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import javax.annotation.Nullable;

/**
 * Winter's Howl (Call of Duty : Black Ops, carte « Five ») : pistolet cryogenique du mode zombies. Le tir TaCZ est
 * remplace par un souffle d'azote liquide en cone court ({@link Givre}) : les zombies ralentissent, puis gelent sur place
 * (glace sur le corps), et volent en eclats de glace quand on les touche ou apres un court delai (points de kill du mode
 * zombies pour le tireur). Pack-a-Punch = « Winter's Fury » : cone plus large et plus long, gel plus rapide, chargeur de
 * 24, lueurs plus vives (le modele montre ses cristaux quand le grand chargeur est monte).
 * <p>Tout le code du Winter's Howl est dans ce paquet ; un seul appel depuis {@link HeralizWonder} : {@link #init}.
 * Le serveur seul decide (cibles, gel, eclatement) ; les clients recoivent de petits paquets sur leur propre canal
 * ({@link ReseauGivre}) et dessinent avec des reserves fixes. Jamais un joueur touche, aucun bloc casse.
 */
public final class WintersHowl {
    private WintersHowl() {
    }

    /** Id TaCZ de l'arme (gunpack heraliz_wonder). */
    public static final String ID = HeralizWonder.MODID + ":winters_howl";
    public static final String CHEMIN = "winters_howl";

    /**
     * Branchements communs (constructeur du mod) : sons, canal. Evenements serveur et TaCZ : partie serveur
     * (WintersHowlServeur.brancher) ; effets : partie client (ClientGivre.init).
     */
    public static void init(IEventBus mod, IEventBus forge) {
        SonsGivre.REGISTRE.register(mod);
        ReseauGivre.init();
    }

    /** La pile est-elle un Winter's Howl ? (compare l'id d'arme TaCZ, aucune allocation) */
    public static boolean est(@Nullable ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        CompoundTag t = s.getTag();
        return t != null && ID.equals(t.getString(Armes.NBT_GUN_ID));
    }

    /** Niveau de Pack-a-Punch du Winter's Howl tenu par l'entite (0 sinon). */
    public static int papTenu(@Nullable LivingEntity e) {
        if (e == null) return 0;
        ItemStack s = e.getMainHandItem();
        return est(s) ? Armes.pap(s) : 0;
    }
}
