package fr.heraliz.crac.bases;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Types de carrière. Les blocs cassables de chaque type sont la liste {@code #crac:carriere/<id>}
 * (data/crac/tags/blocks/carriere/, envoyée aux joueurs par le serveur avec les autres listes de blocs).
 */
public enum CarriereType {
    PIERRE("pierre"),
    MINERAIS("minerais"),
    FER("fer"),
    BOIS("bois"),
    SABLE("sable"),
    ARGILE("argile"),
    TERRE("terre");

    private static final CarriereType[] VALEURS = values();

    public final String id;
    public final TagKey<Block> tag;

    CarriereType(String id) {
        this.id = id;
        this.tag = BlockTags.create(new ResourceLocation("crac", "carriere/" + id));
    }

    @Nullable
    public static CarriereType parId(String id) {
        String s = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (CarriereType t : VALEURS) {
            if (t.id.equals(s)) {
                return t;
            }
        }
        return null;
    }

    @Nullable
    public static CarriereType parRang(int rang) {
        return rang >= 0 && rang < VALEURS.length ? VALEURS[rang] : null;
    }
}
