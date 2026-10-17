package fr.heraliz.quetes.objet;

import fr.heraliz.quetes.Langue;
import fr.heraliz.quetes.Texte;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Objet de quête (document, carnet, lettre, photo, clé, clé USB, colis, échantillon, plaque, pièce) : un modèle, et un
 * nom donné par la quête (« Carnet de tir de Jack »), en or. Étiquette {@value #CLE} : joueur, quête et repère de l'objet
 * dans la quête. Il ne sert qu'au joueur qui a la quête ; il tombe sur le cadavre comme tout le reste.
 */
public class ObjetQuete extends Item {
    public static final String CLE = "HeralizQuete";
    private static final Map<String, Texte> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Texte> e) {
            return size() > 64;
        }
    };

    public ObjetQuete(Properties p) {
        super(p);
    }

    /** Pose l'étiquette de quête sur un objet tout neuf. */
    public static ItemStack marquer(ItemStack s, UUID joueur, String quete, String ref, Texte nom, Texte desc) {
        CompoundTag t = new CompoundTag();
        t.putUUID("Joueur", joueur);
        t.putString("Quete", quete);
        t.putString("Ref", ref);
        t.putString("Nom", nom.toString());
        t.putString("Desc", desc.toString());
        s.getOrCreateTag().put(CLE, t);
        return s;
    }

    @Nullable
    public static CompoundTag etiquette(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.contains(CLE) ? t.getCompound(CLE) : null;
    }

    /** Vrai si c'est l'objet « ref » de la quête « quete » de ce joueur. */
    public static boolean est(ItemStack s, UUID joueur, String quete, String ref) {
        if (!(s.getItem() instanceof ObjetQuete)) return false;
        CompoundTag t = etiquette(s);
        return t != null && t.hasUUID("Joueur") && t.getUUID("Joueur").equals(joueur) && quete.equals(t.getString("Quete"))
                && ref.equals(t.getString("Ref"));
    }

    private static Texte lire(String s) {
        if (s == null || s.isEmpty()) return Texte.VIDE;
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(s, Texte::json);
        }
    }

    @Override
    public Component getName(ItemStack s) {
        CompoundTag t = etiquette(s);
        Texte n = t == null ? Texte.VIDE : lire(t.getString("Nom"));
        if (n.vide()) return super.getName(s);
        return Component.literal(n.choisir(Langue.code())).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
        CompoundTag t = etiquette(s);
        Texte d = t == null ? Texte.VIDE : lire(t.getString("Desc"));
        if (!d.vide()) lignes.add(Component.literal(d.choisir(Langue.code())).withStyle(ChatFormatting.GRAY));
        lignes.add(Component.translatable("item.heraliz_quetes.objet_quete").withStyle(ChatFormatting.DARK_GRAY));
    }
}
