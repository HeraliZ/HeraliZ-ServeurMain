package fr.heraliz.usure;

import fr.heraliz.usure.regles.Etat;
import fr.heraliz.usure.regles.Profil;
import fr.heraliz.usure.regles.Regles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * L'etat d'une arme dans son NBT. Cles a nous (le reste du NBT, dont les camos {@code HeraliszCamo} et le
 * Pack-a-Punch {@code HeraliszZbPap*}, n'est jamais touche) :
 * <ul>
 *   <li>{@code HzUsureEtat} : etat en milliemes (1000 = 100 %) ; absent = arme neuve ;</li>
 *   <li>{@code HzUsureMax} : etat maximal en milliemes (baisse a chaque reparation) ; absent = 1000 ;</li>
 *   <li>{@code HzUsureRep} : nombre de reparations.</li>
 * </ul>
 * Les entiers restent entre 0 et 1000 : Minecraft garde ces petites valeurs en cache, un tir n'alloue rien.
 * <p><b>Gun Durability</b> (gundb) laissait {@code Damage} (points perdus sur sa durabilite maximale) et {@code Jammed} :
 * le serveur les convertit a la premiere lecture ({@link #migrer}) ; en attendant, le client affiche la valeur convertie.
 * Il n'y a plus d'enrayage (choix de l'utilisateur, 1.0.1) : {@code Jammed} et notre ancienne cle {@code HzUsureEnraye}
 * (1.0.0) sont simplement retirees, l'arme tire de nouveau.
 */
public final class Usure {
    private Usure() {
    }

    public static final String ETAT = "HzUsureEtat", MAX = "HzUsureMax", REPARATIONS = "HzUsureRep";
    /** Cle de la 1.0.0 (enrayage, retire en 1.0.1) : effacee a la lecture. */
    public static final String ANCIEN_ENRAYE = "HzUsureEnraye";
    public static final String GUN_ID = "GunId", PRET_MATCH = "HeraliszMatch";
    /** Cles laissees par Gun Durability. */
    public static final String GUNDB_DAMAGE = "Damage", GUNDB_JAMMED = "Jammed", GUNDB_REPAIR_COST = "RepairCost";
    public static final int PLEIN = 1000;

    /** Identifiant d'arme TaCZ (texte du NBT, sans allocation) ou null si ce n'est pas une arme. */
    @Nullable
    public static String gunId(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null) return null;
        Tag g = t.get(GUN_ID);
        return g != null && g.getId() == Tag.TAG_STRING ? g.getAsString() : null;
    }

    /** Etat en milliemes (0..1000) ; une arme encore marquee par gundb est convertie a la volee (sans ecrire). */
    public static int etat(ItemStack s, Profil p) {
        CompoundTag t = s.getTag();
        if (t == null) return PLEIN;
        if (t.contains(ETAT, Tag.TAG_ANY_NUMERIC)) return clamp(t.getInt(ETAT), 0, PLEIN);
        if (t.contains(GUNDB_DAMAGE, Tag.TAG_ANY_NUMERIC)) return depuisGundb(t.getInt(GUNDB_DAMAGE), p);
        return PLEIN;
    }

    public static int max(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.contains(MAX, Tag.TAG_ANY_NUMERIC) ? clamp(t.getInt(MAX), 1, PLEIN) : PLEIN;
    }

    public static int reparations(ItemStack s) {
        CompoundTag t = s.getTag();
        return t == null ? 0 : t.getInt(REPARATIONS);
    }

    /** Arme pretee pour un match (NBT {@code HeraliszMatch} de HeraliZ Matchs). */
    public static boolean pretee(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.contains(PRET_MATCH);
    }

    public static Etat etatDe(ItemStack s, Profil p, Regles r) {
        return Etat.de(etat(s, p), r);
    }

    // ---------------------------------------------------------------- ecriture (serveur)
    public static void ecrireEtat(ItemStack s, int milliemes) {
        s.getOrCreateTag().putInt(ETAT, clamp(milliemes, 0, PLEIN));
    }

    public static void ecrireMax(ItemStack s, int milliemes) {
        int m = clamp(milliemes, 1, PLEIN);
        if (m >= PLEIN) {
            CompoundTag t = s.getTag();
            if (t != null) t.remove(MAX);
        } else {
            s.getOrCreateTag().putInt(MAX, m);
        }
    }

    public static void ajouterReparation(ItemStack s) {
        CompoundTag t = s.getOrCreateTag();
        t.putInt(REPARATIONS, t.getInt(REPARATIONS) + 1);
    }

    /**
     * Convertit une arme marquee par Gun Durability : {@code Damage} sur la durabilite maximale de gundb pour cette arme
     * devient l'etat (meme proportion) ; {@code Damage} et {@code Jammed} disparaissent (l'arme n'est plus enrayee), comme
     * {@code RepairCost} s'il valait 0 (gundb le remettait a 0 a chaque tir) et notre cle d'enrayage de la 1.0.0. Une arme
     * exclue perd seulement ces cles.
     * Rend vrai si le NBT a change.
     */
    public static boolean migrer(ItemStack s, Profil p) {
        return migrer(s.getTag(), p);
    }

    /** Meme conversion sur le NBT de l'arme (null = rien a faire). */
    public static boolean migrer(@Nullable CompoundTag t, Profil p) {
        if (t == null || !(t.contains(GUNDB_DAMAGE) || t.contains(GUNDB_JAMMED) || t.contains(ANCIEN_ENRAYE))) return false;
        if (!t.contains(ETAT) && p.suivie && t.contains(GUNDB_DAMAGE, Tag.TAG_ANY_NUMERIC)) {
            int etat = depuisGundb(t.getInt(GUNDB_DAMAGE), p);
            if (etat < PLEIN) t.putInt(ETAT, etat);
        }
        t.remove(GUNDB_DAMAGE);
        t.remove(GUNDB_JAMMED);
        t.remove(ANCIEN_ENRAYE);
        if (t.contains(GUNDB_REPAIR_COST, Tag.TAG_ANY_NUMERIC) && t.getInt(GUNDB_REPAIR_COST) == 0) t.remove(GUNDB_REPAIR_COST);
        return true;
    }

    /** Etat (milliemes) equivalent a {@code damage} points perdus chez gundb. */
    public static int depuisGundb(int damage, Profil p) {
        if (damage <= 0) return PLEIN;
        return clamp(Math.round(PLEIN * (1f - damage / (float) p.maxGundb)), 0, PLEIN);
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }
}
