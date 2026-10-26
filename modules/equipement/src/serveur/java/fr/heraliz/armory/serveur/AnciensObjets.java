package fr.heraliz.armory.serveur;

import fr.heraliz.armory.HeralizArmory;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

import java.util.Set;

/** Remappage des objets de l'ancien mod HeralisZ Armor (heralisz_armor) au chargement d'un monde (code d'origine de HeralizArmory). */
public final class AnciensObjets {
    private AnciensObjets() {}

    /**
     * Monde qui contenait HeralisZ Armor : les objets gardes deviennent heraliz_armory:<meme chemin> (ceux deja possedes
     * ne disparaissent pas) ; les objets supprimes (tenues, autres equipements, sets, stuffs) sont simplement ignores.
     */
    public static void remapOldItems(MissingMappingsEvent event) {
        Set<String> kept = HeralizArmory.keptIds();
        int remapped = 0, ignored = 0;
        for (MissingMappingsEvent.Mapping<Item> m : event.getMappings(ForgeRegistries.Keys.ITEMS, HeralizArmory.OLD_MOD)) {
            String path = m.getKey().getPath();
            Item item = kept.contains(path) ? ForgeRegistries.ITEMS.getValue(HeralizArmory.id(path)) : null;
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                m.remap(item);
                remapped++;
            } else {
                m.ignore();
                ignored++;
            }
        }
        if (remapped + ignored > 0) {
            HeralizArmory.LOGGER.info("[armurerie] anciens objets HeralisZ Armor : {} repris (heraliz_armory), {} supprimes (ignores)", remapped, ignored);
        }
    }
}
