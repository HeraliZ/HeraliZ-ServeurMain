package fr.heraliz.quetes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Ce que contient un objet et qui disparaîtrait avec lui chez Mila, au recycleur ou dans un échange (15/11, audit de
 * l'économie n° 2 : balles chargées dans une arme, accessoires montés dessus, munitions rangées dans une boîte ; sac
 * plein, déjà refusé depuis le 25/10). Un tel objet est refusé et l'écran dit quoi vider d'abord (comme l'hôtel des ventes
 * qui décharge les armes : rien ne se perd). Lecture des données de l'objet seulement (formats de TaCZ 1.1.8 et de
 * Backpacked), sans rien créer : sert aussi chez le joueur, pour l'infobulle et l'aperçu du recycleur. Les deux côtés.
 */
public final class Contenu {
    /** Rien ; arme chargée ; accessoires montés ; boîte de munitions pleine ; sac plein (textes gui.heraliz_quetes.contenu.N). */
    public static final int RIEN = 0, BALLES = 1, ACCESSOIRES = 2, BOITE = 3, SAC = 4;

    private Contenu() {
    }

    /** Ce que contient l'objet ({@link #RIEN} : rien qui se perdrait avec lui). */
    public static int de(ItemStack s) {
        if (s.isEmpty() || !s.hasTag()) return RIEN;
        CompoundTag t = s.getTag();
        if (t.contains("GunId", Tag.TAG_STRING)) {
            // arme TaCZ : balles du chargeur (GunCurrentAmmoCount) et de la chambre (HasBulletInBarrel) ; accessoires posés
            // (« Attachment » + type, l'objet enregistré ; un emplacement retiré garde un objet vide)
            if (t.getInt("GunCurrentAmmoCount") > 0 || t.getBoolean("HasBulletInBarrel")) return BALLES;
            for (String k : t.getAllKeys()) {
                if (k.startsWith("Attachment") && t.contains(k, Tag.TAG_COMPOUND) && objet(t.getCompound(k))) return ACCESSOIRES;
            }
            return RIEN;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id == null) return RIEN;
        if (id.getNamespace().equals("tacz") && id.getPath().equals("ammo_box")) return t.getInt("AmmoCount") > 0 ? BOITE : RIEN;
        if (id.getNamespace().equals("backpacked")) {
            ListTag l = t.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) if (objet(l.getCompound(i))) return SAC;
        }
        return RIEN;
    }

    /** Données d'un objet enregistré (identifiant et nombre) qui n'est pas vide. */
    private static boolean objet(CompoundTag c) {
        if (c.isEmpty() || c.getByte("Count") <= 0) return false;
        String id = c.getString("id");
        return !id.isEmpty() && !id.equals("minecraft:air");
    }
}
