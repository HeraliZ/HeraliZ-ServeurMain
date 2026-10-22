package fr.heraliz.quetes.serveur;

import com.mojang.logging.LogUtils;
import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.entite.Pnj;
import fr.heraliz.quetes.objet.ObjetQuete;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Marchands (clé « marchand » du fichier d'un PNJ) : vendeurs (soins, nourriture, accessoires) et racheteurs (objets de
 * valeur, composants). Tout est décidé ici : prix, solde, objets pris et donnés ; l'écran du joueur ne fait que montrer
 * et demander. Paiements par HeraliZ Économie ({@link Ponts}).
 */
final class Marchands {
    private static final Logger LOG = LogUtils.getLogger();
    /** Achat avec Maj : 5 d'un coup au plus. */
    private static final int LOT_MAX = 5;
    /** Marchand ouvert par chaque joueur (identifiant de l'entité PNJ). */
    private static final Map<UUID, Integer> OUVERTS = new HashMap<>();

    private Marchands() {
    }

    static void oublier(UUID u) {
        OUVERTS.remove(u);
    }

    static void vider() {
        OUVERTS.clear();
    }

    /** Ouvre (ouvrir = vrai) ou met à jour l'écran du marchand. */
    static void envoyer(ServerPlayer p, Pnj ent, Defs.Pnj def, boolean ouvrir) {
        Defs.Marchand m = def.marchand;
        if (m == null) return;
        OUVERTS.put(p.getUUID(), ent.getId());
        List<Reseau.Article> articles = new ArrayList<>();
        int[] prix = new int[Reseau.CASES], rar = new int[Reseau.CASES];
        if (m.rachat) {
            for (int i = 0; i < Reseau.CASES; i++) {
                ItemStack s = p.getInventory().items.get(i);
                prix[i] = (int) Math.min(Integer.MAX_VALUE, prixRachat(m, s));
                rar[i] = s.isEmpty() ? -1 : Ponts.rarete(s);
            }
        } else {
            for (int i = 0; i < m.articles.size(); i++) {
                ItemStack s = objet(m.articles.get(i), 1);
                if (s.isEmpty()) continue;
                articles.add(new Reseau.Article(i, s, m.articles.get(i).prix(), Ponts.rarete(s)));
            }
        }
        Reseau.envoyer(p, new Reseau.Boutique(ent.getId(), ouvrir, def.nom.vide() ? ent.nom() : def.nom, def.role,
                Ponts.solde(p.getUUID()), m.rachat, articles, prix, rar));
    }

    /** Le marchand que le joueur a ouvert, s'il est toujours là, à portée, et marchand ; sinon null. */
    private static Defs.Pnj verifier(ServerPlayer p, int entite) {
        Integer ouvert = OUVERTS.get(p.getUUID());
        if (ouvert == null || ouvert != entite || !p.isAlive()) return null;
        Entity e = p.level().getEntity(entite);
        if (!(e instanceof Pnj pnj) || e.distanceToSqr(p) > 8 * 8) return null;
        Defs.Pnj def = Catalogue.pnj(pnj.pnjId());
        return def == null || def.marchand == null ? null : def;
    }

    static void achat(ServerPlayer p, Reseau.Achat a) {
        Defs.Pnj def = verifier(p, a.entite());
        if (def == null || def.marchand.rachat || a.index() < 0 || a.index() >= def.marchand.articles.size()) return;
        Defs.ArticleDef art = def.marchand.articles.get(a.index());
        int q = Math.max(1, Math.min(LOT_MAX, a.quantite()));
        ItemStack modele = objet(art, 1);
        if (modele.isEmpty()) return;
        long total = art.prix() * q;
        if (!Ponts.retirer(p.getUUID(), total, "Achat : " + art.objet() + " x" + q)) {
            Moteur.notif(p, Texte.cle("heraliz_quetes.notif.pas_assez", Texte.brut(Ponts.nombre(total))), 0xFF6B6B);
            return;
        }
        for (int i = 0; i < q; i++) {
            ItemStack s = modele.copy();
            if (!p.getInventory().add(s)) p.drop(s, false);
        }
        p.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7F, 1.0F);
        Moteur.notif(p, Texte.cle("heraliz_quetes.notif.achete", Texte.brut(q), Texte.cle(modele.getDescriptionId()),
                Texte.brut(Ponts.nombre(total))), 0xE8C36A);
        LOG.info("[heraliz_quetes] {} achète {} x{} à {} pour {} hérals", p.getGameProfile().getName(), art.objet(), q, def.id, total);
        envoyerA(p, a.entite(), def);
    }

    static void vente(ServerPlayer p, Reseau.Vente v) {
        Defs.Pnj def = verifier(p, v.entite());
        if (def == null || !def.marchand.rachat) return;
        long total = 0, horsBoutique = 0;
        int nombre = 0;
        java.util.Set<String> vendus = SaisonQuetes.vendus();
        for (int c : new LinkedHashSet<>(v.cases())) {
            if (c < 0 || c >= Reseau.CASES) continue;
            ItemStack s = p.getInventory().items.get(c);
            long u = prixRachat(def.marchand, s);
            if (u <= 0) continue;
            total += u * s.getCount();
            if (!SaisonQuetes.vendu(vendus, s)) horsBoutique += u * s.getCount();
            nombre += s.getCount();
            p.getInventory().items.set(c, ItemStack.EMPTY);
        }
        if (nombre == 0) return;
        if (!Ponts.deposer(p.getUUID(), total, "vente:" + def.id)) {
            LOG.error("[heraliz_quetes] vente de {} : {} hérals NON versés (économie absente)", p.getGameProfile().getName(), total);
        } else {
            SaisonQuetes.vente(p, horsBoutique);
        }
        p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        Moteur.notif(p, Texte.cle("heraliz_quetes.notif.vendu", Texte.brut(nombre), Texte.brut(Ponts.nombre(total))), 0xE8C36A);
        LOG.info("[heraliz_quetes] {} vend {} objets à {} pour {} hérals", p.getGameProfile().getName(), nombre, def.id, total);
        envoyerA(p, v.entite(), def);
    }

    private static void envoyerA(ServerPlayer p, int entite, Defs.Pnj def) {
        if (p.level().getEntity(entite) instanceof Pnj ent) envoyer(p, ent, def, false);
    }

    /** Prix de rachat à l'unité (0 : il ne le prend pas). La première règle dont le tag correspond fixe le prix. */
    static long prixRachat(Defs.Marchand m, ItemStack s) {
        if (s.isEmpty() || s.getItem() instanceof ObjetQuete) return 0;
        for (Defs.Regle r : m.regles) {
            ResourceLocation rl = ResourceLocation.tryParse(r.tag().startsWith("#") ? r.tag().substring(1) : r.tag());
            if (rl == null || !s.is(TagKey.create(Registries.ITEM, rl))) continue;
            int rang = Ponts.rarete(s);
            String nom = rang < 0 ? "commun" : rang == 5 ? "mythique" : Ponts.RARETES.get(rang);
            Long v = r.prix().get(nom);
            if (v == null && rang == 5) v = r.prix().get("unique");
            return v == null ? 0 : Math.max(0, v);
        }
        return 0;
    }

    private static final Map<Defs.ArticleDef, ItemStack> CACHE = new HashMap<>();

    static void oublierCache() {
        CACHE.clear();
    }

    /** L'objet d'un article (vide si l'identifiant est inconnu). */
    static ItemStack objet(Defs.ArticleDef a, int n) {
        ItemStack s = CACHE.computeIfAbsent(a, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(k.objet());
            Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            if (it == null || it == Items.AIR) {
                LOG.warn("[heraliz_quetes] marchand : objet inconnu {}", k.objet());
                return ItemStack.EMPTY;
            }
            ItemStack st = new ItemStack(it);
            if (!k.nbt().isEmpty()) {
                try {
                    st.setTag(TagParser.parseTag(k.nbt()));
                } catch (Exception ex) {
                    LOG.warn("[heraliz_quetes] marchand : données illisibles pour {} : {}", k.objet(), ex.getMessage());
                }
            }
            return st;
        });
        if (s.isEmpty()) return ItemStack.EMPTY;
        ItemStack c = s.copy();
        c.setCount(n);
        return c;
    }
}
