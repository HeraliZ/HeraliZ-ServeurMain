package fr.heraliz.wonder.jetgun;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.jetgun.net.JetReseau;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import javax.annotation.Nullable;

/**
 * Jet Gun (Call of Duty : Black Ops II, TranZit) : un turboréacteur tenu à la main. Tant qu'on tient la détente, il
 * <b>aspire</b> la horde vers sa bouche (cône d'aspiration) et <b>déchiquette</b> les créatures qui l'atteignent ; une
 * <b>jauge de chaleur</b> monte pendant le tir et redescend quand on relâche ; à 100 % l'arme <b>cale</b> (surchauffe) et
 * reste bloquée quelques secondes. La touche de rechargement fait une <b>purge</b> de vapeur qui vide la chaleur.
 * <p>Répartition :
 * <ul>
 *   <li>TaCZ + gunpack (script Lua {@code jetgun_logic}) : cadence (10 impulsions par seconde), chaleur, verrou de
 *   surchauffe, purge, aucune munition consommée ; machine à états {@code jetgun_state_machine} pour les animations
 *   (emballement, boucle de tir, surchauffe, jauge du thermomètre et incandescence réglées sur la chaleur) ;</li>
 *   <li>serveur ({@link Aspiration}) : la balle TaCZ est annulée, chaque impulsion entretient l'aspiration ; le serveur
 *   seul décide (cibles, force, dégâts) ; jamais de bloc cassé ni de joueur touché ;</li>
 *   <li>client ({@code client.JetClient}) : traits d'air en tourbillon, poussière aspirée, tourbillon à l'entrée d'air,
 *   lambeaux et brume quand un zombie est déchiqueté, vapeur de la purge, fumée et étincelles de la surchauffe,
 *   chaleur qui ondule, son du réacteur (boucle), jauge de chaleur à l'écran.</li>
 * </ul>
 * Pack-a-Punch : le Jet Gun de CoD ne passe pas au Pack-a-Punch. La machine du mode zombies (HeraliZ Matchs) accepte
 * toute arme TaCZ sans exception possible ; plutôt que de faire payer une amélioration vide, la version améliorée est
 * modeste : « surchauffe réduite » (chaleur x0,72 / x0,64 / x0,56 par niveau, refroidissement x1,3, verrou raccourci).
 * <p>Branché depuis {@link HeralizWonder} par une seule ligne : {@code JetGun.init(mod, forge)}.
 */
public final class JetGun {
    private JetGun() {
    }

    public static final String CHEMIN = "jetgun";
    /** Id TaCZ du Jet Gun (gunpack heraliz_wonder). */
    public static final String ID = HeralizWonder.MODID + ":" + CHEMIN;
    /** NBT de TaCZ (GunItemDataAccessor) : id d'arme, chaleur, verrou de surchauffe. */
    public static final String NBT_GUN_ID = "GunId", NBT_CHALEUR = "HeatAmount", NBT_VERROU = "OverHeated";
    /** Chaleur maximale (données « heat » du gunpack : max = 100). */
    public static final float CHALEUR_MAX = 100f;
    /** Seuil de chaleur à partir duquel la purge est possible (script Lua : seuil_purge = 0,08). */
    public static final float SEUIL_PURGE = 0.08f;

    public static boolean tacz;

    /**
     * Partie commune : TaCZ present ?, sons et canal reseau. La logique (aspiration, commandes, TaCZ) est branchee par la
     * partie serveur (JetGunServeur.brancher), les effets par la partie client (JetClient.init).
     */
    public static void init(IEventBus mod, IEventBus forge) {
        tacz = ModList.get().isLoaded("tacz");
        JetSons.REGISTRE.register(mod);
        JetReseau.init();
    }

    /** Vrai si la pile est un Jet Gun (id d'arme TaCZ lu dans le NBT, sans allocation). */
    public static boolean estJetGun(@Nullable ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        CompoundTag t = s.getTag();
        return t != null && ID.equals(t.getString(NBT_GUN_ID));
    }

    public static boolean tientJetGun(@Nullable LivingEntity e) {
        return e != null && estJetGun(e.getMainHandItem());
    }

    /** Chaleur de l'arme (0..1), lue dans le NBT de TaCZ. */
    public static float chaleur(@Nullable ItemStack s) {
        if (s == null) return 0f;
        CompoundTag t = s.getTag();
        return t == null ? 0f : Math.max(0f, Math.min(1f, t.getFloat(NBT_CHALEUR) / CHALEUR_MAX));
    }

    /** Vrai si l'arme est en surchauffe (verrou de TaCZ). */
    public static boolean surchauffe(@Nullable ItemStack s) {
        if (s == null) return false;
        CompoundTag t = s.getTag();
        return t != null && t.getBoolean(NBT_VERROU);
    }

    /** Niveau de Pack-a-Punch (NBT du mode zombies, comme {@code Armes.pap}). */
    public static int pap(@Nullable ItemStack s) {
        if (s == null || s.isEmpty()) return 0;
        CompoundTag t = s.getTag();
        if (t == null) return 0;
        int n = t.getInt("HeraliszZbPapNiveau");
        if (n > 0) return Math.min(n, 3);
        return t.getBoolean("HeraliszZbPap") ? 1 : 0;
    }
}
