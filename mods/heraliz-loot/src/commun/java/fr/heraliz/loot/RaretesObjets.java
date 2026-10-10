package fr.heraliz.loot;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Rareté de chaque sorte d'objet : la couleur légère derrière l'objet dans les cases (inventaire, coffres, sacs, barre
 * d'objets) et la ligne « ★ Rare » de l'infobulle. Par identifiant d'objet, et pour les objets de TaCZ (une seule sorte
 * d'objet pour toutes les armes) par identifiant d'arme, d'accessoire ou de munition. Les objets sans rareté ont celle
 * par défaut (Commun, ou aucune). Une rareté inscrite sur l'objet lui-même ({@link Rarete#CLE}, coffres T7) passe avant.
 * <p>Rempli côté serveur depuis {@code config/heraliz_loot/raretes.json} (commande /rarete pour le staff) et envoyé aux
 * joueurs à la connexion et à chaque changement ({@link ReseauLoot}). Lectures sans allocation : appelé pour chaque case
 * à chaque image.
 */
public final class RaretesObjets {
    private RaretesObjets() {
    }

    /** Ce qui porte la rareté : l'objet, ou l'arme / l'accessoire / la munition de TaCZ (étiquette de l'objet). */
    public enum Sorte {
        OBJET("objets", null), ARME("armes", "GunId"), ACCESSOIRE("accessoires", "AttachmentId"), MUNITION("munitions", "AmmoId");

        /** Clé dans raretes.json. */
        public final String cle;
        /** Étiquette NBT de TaCZ qui porte l'identifiant (null : identifiant de l'objet). */
        @Nullable
        public final String etiquette;

        Sorte(String cle, @Nullable String etiquette) {
            this.cle = cle;
            this.etiquette = etiquette;
        }
    }

    /** La sorte et l'identifiant qui portent la rareté d'un objet. */
    public record Cle(Sorte sorte, String id) {
    }

    /** Toutes les raretés connues, par sorte : identifiant -> rareté. */
    public record Table(@Nullable Rarete defaut, Map<Sorte, Map<String, Rarete>> parSorte) {
        public static Table vide() {
            Map<Sorte, Map<String, Rarete>> m = new EnumMap<>(Sorte.class);
            for (Sorte s : Sorte.values()) m.put(s, new HashMap<>());
            return new Table(Rarete.COMMUN, m);
        }

        public void ecrire(FriendlyByteBuf b) {
            b.writeByte(defaut == null ? -1 : defaut.ordinal());
            for (Sorte s : Sorte.values()) {
                Map<String, Rarete> m = parSorte.getOrDefault(s, Map.of());
                b.writeVarInt(m.size());
                for (Map.Entry<String, Rarete> e : m.entrySet()) {
                    b.writeUtf(e.getKey(), 256);
                    b.writeByte(e.getValue().ordinal());
                }
            }
        }

        public static Table lire(FriendlyByteBuf b) {
            Rarete[] r = Rarete.values();
            int d = b.readByte();
            Map<Sorte, Map<String, Rarete>> m = new EnumMap<>(Sorte.class);
            for (Sorte s : Sorte.values()) {
                int n = b.readVarInt();
                Map<String, Rarete> ms = new HashMap<>(Math.max(4, n * 2));
                for (int i = 0; i < n; i++) {
                    String id = b.readUtf(256);
                    int o = b.readByte();
                    if (o >= 0 && o < r.length) ms.put(id, r[o]);
                }
                m.put(s, ms);
            }
            return new Table(d >= 0 && d < r.length ? r[d] : null, m);
        }
    }

    private static volatile Table table = Table.vide();
    /** Objets (hors TaCZ) déjà résolus : objet -> rareté, refait à chaque nouvelle table. */
    private static volatile Map<Item, Rarete> parObjet = new IdentityHashMap<>();

    /** Remplace toutes les raretés (serveur : fichier chargé ou modifié ; client : table reçue). */
    public static void remplacer(Table t) {
        Map<Item, Rarete> po = new IdentityHashMap<>();
        for (Map.Entry<String, Rarete> e : t.parSorte().getOrDefault(Sorte.OBJET, Map.of()).entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id != null && ForgeRegistries.ITEMS.containsKey(id)) po.put(ForgeRegistries.ITEMS.getValue(id), e.getValue());
        }
        table = t;
        parObjet = po;
    }

    public static Table table() {
        return table;
    }

    /** Ce qui porte la rareté de cet objet (null : objet vide). */
    @Nullable
    public static Cle cle(ItemStack st) {
        if (st.isEmpty()) return null;
        CompoundTag t = st.getTag();
        if (t != null) {
            for (Sorte s : Sorte.values()) {
                if (s.etiquette != null && t.contains(s.etiquette, Tag.TAG_STRING)) return new Cle(s, t.getString(s.etiquette));
            }
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        return id == null ? null : new Cle(Sorte.OBJET, id.toString());
    }

    /** Rareté de cet objet : inscrite sur l'objet, sinon celle de sa sorte, sinon celle par défaut (null : aucune). */
    @Nullable
    public static Rarete de(ItemStack st) {
        if (st.isEmpty()) return null;
        Rarete r = Rarete.de(st);
        if (r != null) return r;
        Table t = table;
        CompoundTag tag = st.getTag();
        if (tag != null) {
            for (Sorte s : Sorte.values()) {
                if (s.etiquette != null && tag.contains(s.etiquette, Tag.TAG_STRING)) {
                    r = t.parSorte().getOrDefault(s, Map.of()).get(tag.getString(s.etiquette));
                    return r != null ? r : t.defaut();
                }
            }
        }
        r = parObjet.get(st.getItem());
        return r != null ? r : t.defaut();
    }

    /** Rareté propre à la sorte de cet objet (sans la rareté par défaut ni celle inscrite sur l'objet), null : aucune. */
    @Nullable
    public static Rarete propre(ItemStack st) {
        Cle c = cle(st);
        return c == null ? null : table.parSorte().getOrDefault(c.sorte(), Map.of()).get(c.id());
    }
}
