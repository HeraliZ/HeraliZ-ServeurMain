package fr.heraliz.clashwar.serveur;

import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.moteur.api.Coffre;
import fr.heraliz.clashwar.moteur.coffre.Coffres;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pour {@code /wipe} (HeraliZ Quetes, par reflexion : ne jamais renommer la classe ni changer la signature de
 * {@link #vider}) : un joueur en match a son vrai inventaire dans son coffre de match
 * ({@code heraliz_clashwar/coffres/<uuid>.dat}), qui lui serait rendu apres la remise a zero. {@link #vider} rend tous
 * les objets de ce depot (inventaire et Curios, sans descendre dans le contenu des objets : un sac garde le sien) et
 * supprime le fichier : a la reconnexion, plus rien n'est rendu (objets pretes purges comme d'habitude). Fil serveur.
 */
public final class WipeClashWar {
    private WipeClashWar() {
    }

    /** Objets du coffre de match en attente du joueur (liste vide s'il n'en a pas) ; le fichier est supprime. */
    public static List<ItemStack> vider(MinecraftServer srv, UUID id) {
        List<ItemStack> objets = new ArrayList<>();
        Path f = Coffre.fichier(id);
        if (f == null || !Files.isRegularFile(f)) return objets;
        CompoundTag t = Coffres.lire(f);
        if (t != null) {
            collecter(t.get("inventaire"), objets);
            collecter(t.get("curios"), objets);
        }
        try {
            Files.deleteIfExists(f);
            HeralizClashWar.LOG.info("[matchs] wipe : coffre de match de {} vide ({} objet(s))", id, objets.size());
        } catch (IOException e) {
            HeralizClashWar.LOG.error("[matchs] wipe : coffre de match de {} non supprime", id, e);
        }
        return objets;
    }

    /** Toute pile d'objets trouvee dans ce morceau de NBT (compose avec « id » et « Count »), sans descendre dedans. */
    private static void collecter(Tag tag, List<ItemStack> out) {
        if (tag instanceof ListTag l) {
            for (Tag e : l) collecter(e, out);
        } else if (tag instanceof CompoundTag c) {
            if (c.contains("id", Tag.TAG_STRING) && c.contains("Count")) {
                ItemStack s = ItemStack.of(c);
                if (!s.isEmpty()) out.add(s);
                return;
            }
            for (String k : c.getAllKeys()) collecter(c.get(k), out);
        }
    }
}
