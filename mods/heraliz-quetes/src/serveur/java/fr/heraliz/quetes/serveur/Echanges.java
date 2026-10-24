package fr.heraliz.quetes.serveur;

import com.mojang.logging.LogUtils;
import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.objet.ObjetQuete;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Échanges des marchands (troc, Quêtes 1.11.0) : X objets donnés contre Y objets reçus, réglés en jeu par un op dans
 * l'éditeur du marchand. Le serveur décide de tout :
 * <ul>
 * <li>l'objet donné doit être le même objet que celui de l'échange, avec AU MOINS les mêmes données (NBT : un objet de
 * quête, une arme TaCZ précise...), sans les données qui changent à l'usage (munitions, accessoires, marque anti-copie) ;
 * un objet abîmé (usure) ne compte pas : il ne passe jamais pour un neuf ;</li>
 * <li>objets à vie, objets prêtés par un match, armes à camo à vie et objets de quête : jamais donnés ni reçus ;</li>
 * <li>l'inventaire est calculé sur une copie (objets retirés, puis objets reçus rangés) ; si tout ne tient pas : refus,
 * rien n'a bougé ; sinon la copie remplace l'inventaire en une fois, puis le joueur est sauvegardé ;</li>
 * <li>pas de boucle : un objet reçu par un échange n'est jamais donné dans un autre échange (ni racheté contre des
 * hérals, {@link SaisonQuetes#vendus()}) ; un échange qui le ferait est coupé.</li>
 * </ul>
 */
final class Echanges {
    private static final Logger LOG = LogUtils.getLogger();
    /** Maj + clic : 5 fois de suite au plus. */
    private static final int FOIS_MAX = 5;
    /** Données ignorées pour reconnaître l'objet donné : elles changent à l'usage ou ne sont qu'une marque. */
    private static final List<String> VOLATILES = List.of("spartan_uid", "Damage", "RepairCost", "GunCurrentAmmoCount",
            "HasBulletInBarrel", "GunFireMode", "HeralizTirAuto", "HeralizSacOk");

    private Echanges() {
    }

    // ------------------------------------------------------------------ objets
    static ItemStack donne(Defs.EchangeDef e) {
        return Marchands.objet(new Defs.ArticleDef(e.donne(), e.nbtDonne(), 0), 1);
    }

    static ItemStack recu(Defs.EchangeDef e) {
        return Marchands.objet(new Defs.ArticleDef(e.recu(), e.nbtRecu(), 0), 1);
    }

    /** Objet qui ne s'échange jamais : à vie, prêté par un match, camo à vie, objet de quête, registre du marchand. */
    static boolean interdit(ItemStack s) {
        if (s.isEmpty()) return false;
        if (s.getItem() instanceof ObjetQuete || s.is(HeralizQuetes.REGISTRE.get())) return true;
        if (s.hasTag() && (s.getTag().contains("HeraliszMatch") || s.getTag().contains("HeraliszCamoProprio"))) return true;
        return Commandes.estAVie(s);
    }

    /** Données d'un objet à garder dans un article ou un échange : sans l'usure ni la marque anti-copie ; "" sans données. */
    static String nbtPropre(ItemStack s) {
        if (!s.hasTag()) return "";
        CompoundTag tag = s.getTag().copy();
        tag.remove("spartan_uid");
        tag.remove("Damage");
        return tag.isEmpty() ? "" : tag.toString();
    }

    /** Données que l'objet donné doit avoir (celles de l'échange, sans les données qui changent à l'usage), ou null. */
    private static CompoundTag requis(ItemStack modele) {
        if (!modele.hasTag()) return null;
        CompoundTag t = modele.getTag().copy();
        for (String k : VOLATILES) t.remove(k);
        for (String k : new ArrayList<>(t.getAllKeys())) if (k.startsWith("Attachment")) t.remove(k);
        return t.isEmpty() ? null : t;
    }

    /** Cet objet de l'inventaire peut-il être donné pour cet échange ? */
    private static boolean convient(ItemStack s, ItemStack modele, CompoundTag requis) {
        if (s.isEmpty() || s.getItem() != modele.getItem() || s.isDamaged() || interdit(s)) return false;
        return requis == null || NbtUtils.compareNbt(requis, s.getTag(), true);
    }

    /** Combien d'objets qui conviennent le joueur a dans son inventaire (36 cases). */
    static int possede(ServerPlayer p, Defs.EchangeDef e) {
        ItemStack modele = donne(e);
        if (modele.isEmpty()) return 0;
        CompoundTag req = requis(modele);
        int n = 0;
        for (int i = 0; i < Reseau.CASES; i++) {
            ItemStack s = p.getInventory().items.get(i);
            if (convient(s, modele, req)) n += s.getCount();
        }
        return n;
    }

    // ------------------------------------------------------------------ boucles
    /** Objets reçus par au moins un échange (tous les marchands). */
    static Set<String> sorties() {
        Set<String> s = new HashSet<>();
        for (Defs.Pnj d : Catalogue.tous()) if (d.marchand != null) for (Defs.EchangeDef e : d.marchand.echanges) s.add(e.recu());
        return s;
    }

    /** Objets donnés dans au moins un échange (tous les marchands). */
    static Set<String> entrees() {
        Set<String> s = new HashSet<>();
        for (Defs.Pnj d : Catalogue.tous()) if (d.marchand != null) for (Defs.EchangeDef e : d.marchand.echanges) s.add(e.donne());
        return s;
    }

    /** Échange coupé : il donnerait un objet reçu par un échange (chaîne ou boucle), ou rend ce qu'on lui donne. */
    static boolean coupe(Defs.EchangeDef e, Set<String> sorties) {
        return e.donne().equals(e.recu()) || sorties.contains(e.donne());
    }

    /** Raison de refuser un nouvel échange (clé de langue), ou null. */
    static String refusAjout(String donne, String recu) {
        if (donne.equals(recu)) return "heraliz_quetes.echange.meme_objet";
        if (sorties().contains(donne)) return "heraliz_quetes.echange.donne_recu";
        if (entrees().contains(recu)) return "heraliz_quetes.echange.recu_donne";
        return null;
    }

    static String id(ItemStack s) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(s.getItem());
        return rl == null ? "" : rl.toString();
    }

    // ------------------------------------------------------------------ écran du joueur
    static List<Reseau.Echange> vue(ServerPlayer p, Defs.Marchand m) {
        List<Reseau.Echange> l = new ArrayList<>();
        Set<String> sorties = sorties();
        for (int i = 0; i < m.echanges.size() && l.size() < Reseau.MAX_ECHANGES; i++) {
            Defs.EchangeDef e = m.echanges.get(i);
            ItemStack d = donne(e), r = recu(e);
            if (d.isEmpty() || r.isEmpty()) continue;
            l.add(new Reseau.Echange(i, d, e.nDonne(), r, e.nRecu(), Ponts.rarete(r), possede(p, e), !coupe(e, sorties)));
        }
        return l;
    }

    // ------------------------------------------------------------------ faire un échange
    static void troc(ServerPlayer p, Reseau.Troc t) {
        Defs.Pnj def = Marchands.verifier(p, t.entite());
        if (def == null || t.index() < 0 || t.index() >= def.marchand.echanges.size()) return;
        Texte refus = Marchands.empechement(p);
        if (refus != null) {
            Moteur.notif(p, refus, 0xFF6B6B);
            return;
        }
        Defs.EchangeDef e = def.marchand.echanges.get(t.index());
        if (coupe(e, sorties())) {
            Moteur.notif(p, Texte.cle("heraliz_quetes.echange.coupe"), 0xFF6B6B);
            return;
        }
        ItemStack modele = donne(e), recu = recu(e);
        if (modele.isEmpty() || recu.isEmpty() || interdit(modele) || interdit(recu)) {
            Moteur.notif(p, Texte.cle("heraliz_quetes.echange.coupe"), 0xFF6B6B);
            return;
        }
        int fois = Math.max(1, Math.min(FOIS_MAX, t.fois())), faits = 0;
        String raison = null;
        for (int i = 0; i < fois; i++) {
            raison = unEchange(p, e, modele, recu);
            if (raison != null) break;
            faits++;
        }
        if (faits > 0) {
            // objets partis et reçus écrits tout de suite (un arrêt brutal ne rend pas les deux)
            Ponts.sauverJoueur(p);
            p.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7F, 1.0F);
            Moteur.notif(p, Texte.cle("heraliz_quetes.echange.fait", Texte.brut(e.nRecu() * faits), Texte.cle(recu.getDescriptionId()),
                    Texte.brut(e.nDonne() * faits), Texte.cle(modele.getDescriptionId())), 0xE8C36A);
            LOG.info("[heraliz_quetes] {} échange {} x{} contre {} x{} chez {} ({} fois)", p.getGameProfile().getName(), e.donne(),
                    e.nDonne(), e.recu(), e.nRecu(), def.id, faits);
        }
        if (raison != null && (faits == 0 || raison.endsWith("plein"))) {
            Moteur.notif(p, raison.endsWith("pas_assez")
                    ? Texte.cle(raison, Texte.brut(e.nDonne()), Texte.cle(modele.getDescriptionId()))
                    : Texte.cle(raison), 0xFF6B6B);
        }
        Marchands.envoyerA(p, t.entite(), def);
    }

    /**
     * Un échange, tout ou rien : calculé sur une copie des 36 cases, puis la copie remplace l'inventaire. Rend null si
     * c'est fait, sinon la clé de la raison (« pas assez », « inventaire plein »).
     */
    private static String unEchange(ServerPlayer p, Defs.EchangeDef e, ItemStack modele, ItemStack recu) {
        Inventory inv = p.getInventory();
        CompoundTag req = requis(modele);
        List<ItemStack> copie = new ArrayList<>(Reseau.CASES);
        for (int i = 0; i < Reseau.CASES; i++) copie.add(inv.items.get(i).copy());
        // 1. retirer : le sac d'abord (cases 9 à 35), la barre d'objets ensuite
        int reste = e.nDonne();
        for (int k = 0; k < Reseau.CASES && reste > 0; k++) {
            int i = (k + 9) % Reseau.CASES;
            ItemStack s = copie.get(i);
            if (!convient(s, modele, req)) continue;
            int pris = Math.min(reste, s.getCount());
            s.shrink(pris);
            reste -= pris;
            if (s.isEmpty()) copie.set(i, ItemStack.EMPTY);
        }
        if (reste > 0) return "heraliz_quetes.echange.pas_assez";
        // 2. ranger ce qui est reçu : d'abord sur les piles du même objet, puis dans les cases vides
        int max = Math.max(1, recu.getMaxStackSize()), aDonner = e.nRecu();
        for (int i = 0; i < Reseau.CASES && aDonner > 0; i++) {
            ItemStack s = copie.get(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameTags(s, recu) || s.getCount() >= max) continue;
            int n = Math.min(aDonner, max - s.getCount());
            s.grow(n);
            aDonner -= n;
        }
        for (int i = 0; i < Reseau.CASES && aDonner > 0; i++) {
            if (!copie.get(i).isEmpty()) continue;
            int n = Math.min(aDonner, max);
            ItemStack neuf = recu.copy();
            neuf.setCount(n);
            copie.set(i, neuf);
            aDonner -= n;
        }
        if (aDonner > 0) return "heraliz_quetes.echange.plein";
        // 3. tout d'un coup
        for (int i = 0; i < Reseau.CASES; i++) inv.items.set(i, copie.get(i));
        inv.setChanged();
        return null;
    }
}
