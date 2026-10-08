package fr.heraliz.wonder.sliquifier;

import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.sliquifier.net.SliqReseau;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import javax.annotation.Nullable;

/**
 * Sliquifier (Call of Duty : Black Ops II, Die Rise ; {@code heraliz_wonder:sliquifier}, modele et sons faits maison) :
 * l'arme tire des boules de gelee rose (projectile du mod, la balle TaCZ est annulee) qui s'ecrasent en <b>flaques</b>.
 * <ul>
 *   <li>coup direct : le zombie fond (mort, points comme pour les autres Wonder Weapons) et laisse une flaque ;</li>
 *   <li>un zombie qui marche dans une flaque glisse, tombe a la renverse et fond (et laisse une petite flaque : reaction
 *   en chaine, comme dans CoD) ;</li>
 *   <li>un joueur qui marche dans une flaque glisse vite (aucun degat, jamais) ;</li>
 *   <li>flaques bornees (nombre par dimension, duree), liste cote serveur (pas de blocs, pas d'entites), aucun bloc
 *   casse ;</li>
 *   <li>Pack-a-Punch « Sliquidator » (nom maison : dans CoD, le Sliquifier n'a pas de version Pack-a-Punch connue) :
 *   flaques plus grandes et plus longues, chargeur de 20, gelee plus vive.</li>
 * </ul>
 * Tout est dans ce paquet (nouveaux fichiers seulement) ; le mod l'allume par {@link #init} (une ligne dans
 * {@code HeralizWonder}) et ses reglages par {@link SliqConfig#definir} (une ligne dans {@code WonderConfig}).
 * Le serveur seul decide (flaques, glissades, morts) ; les clients recoivent de petits paquets sur leur propre canal.
 */
public final class Sliquifier {
    private Sliquifier() {
    }

    public static final String CHEMIN = "sliquifier";
    public static final String ID = HeralizWonder.MODID + ":" + CHEMIN;

    /** Branche le Sliquifier : sons, canal reseau, evenements serveur et client. A appeler depuis le constructeur du mod. */
    public static void init(IEventBus mod, IEventBus forge) {
        if (SliqConfig.VITESSE == null) HeralizWonder.LOG.error("[sliquifier] SliqConfig.definir n'a pas ete appele par WonderConfig : reglages absents");
        SliqSons.REGISTRE.register(mod);
        SliqReseau.init();
        // evenements serveur et TaCZ : partie serveur (SliquifierServeur.brancher) ; effets : partie client (SliqClient.init)
    }

    /** Vrai si l'id d'arme TaCZ est celui du Sliquifier. */
    public static boolean est(@Nullable ResourceLocation gunId) {
        return gunId != null && HeralizWonder.MODID.equals(gunId.getNamespace()) && CHEMIN.equals(gunId.getPath());
    }

    /** Vrai si la pile est un Sliquifier (arme TaCZ, NBT {@code GunId}). Aucune allocation. */
    public static boolean est(@Nullable ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        CompoundTag t = s.getTag();
        return t != null && ID.equals(t.getString(Armes.NBT_GUN_ID));
    }

    /** Niveau de Pack-a-Punch du Sliquifier tenu en main (0 s'il n'est pas tenu). */
    public static int papTenu(@Nullable net.minecraft.world.entity.LivingEntity e) {
        if (e == null) return 0;
        ItemStack s = e.getMainHandItem();
        return est(s) ? Armes.pap(s) : 0;
    }
}
