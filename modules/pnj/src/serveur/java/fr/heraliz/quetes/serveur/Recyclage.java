package fr.heraliz.quetes.serveur;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.entite.Pnj;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le recycleur (choix du propriétaire, 24/10) : bloc du spawn ou marchand de type « recyclage ». Règles dans
 * {@code config/heraliz_quetes/recyclage.json} (écrit par {@code outils/recyclage.py}, jamais à la main).
 *
 * <p>25/10 (maquette validée, maquettes/recycleur/) : plus de « coffre où l'on dépose puis ferme ». L'écran
 * ({@link MenuRecycleurServeur}) recycle les objets déposés un par un, en {@code secondes_par_objet} chacun, sous les yeux
 * du joueur ; ce qui sort va dans les cases « récupéré », les hérals sur son compte ; un aperçu montre pour chaque objet ce
 * qu'il peut donner ({@link #apercu}). Économie dure (demande du propriétaire) : chaque composant de la règle sort avec
 * {@code chance} (10 %), au plus {@code max_pieces} (2) pièces par objet, et un objet qui n'a rien rendu donne quelques
 * hérals. C'est un tri pour qui est plein, pas une source de richesse.
 *
 * <p>Sécurité : tout se décide sur le fil serveur, objet par objet ; à la fermeture, ce qui reste (à recycler ou récupéré)
 * est rendu, ou tombe aux pieds d'un joueur mort ou déconnecté. Objets à vie, prêtés par un match, à camo à vie, de
 * quête : jamais recyclés ({@link Echanges#interdit}) ; objets achetés chez un marchand, refusés s'ils rapporteraient
 * plus de la moitié de leur prix ({@link #tropRentable}).
 */
final class Recyclage {
    private static final Logger LOG = LogUtils.getLogger();

    /** États d'un objet pour le recycleur (même ordre que les textes gui.heraliz_quetes.recycleur.etat.N). */
    static final int OK = 0, NON = 1, REFUSE = 2, PROTEGE = 3, MANQUE = 4;

    private Recyclage() {
    }

    record Regle(String objet, boolean prefixe, String cle, String valeur, int par, List<String> rend, List<List<String>> rendRarete) {
        boolean correspond(String id, ItemStack s) {
            if (prefixe ? !id.startsWith(objet) : !id.equals(objet)) return false;
            return cle == null || (s.hasTag() && s.getTag().contains(cle));
        }

        List<String> rendPour(int rang) {
            if (rend != null) return rend;
            return rendRarete.get(Math.max(0, Math.min(rendRarete.size() - 1, rang)));
        }
    }

    private record Etat(double chance, int maxPieces, int ticksParObjet, long[] herals, List<String> jamais, Map<String, String> cles,
                        Map<String, Regle> exactes, List<Regle> larges) {
    }

    private static volatile Etat etat = vide();

    private static Etat vide() {
        return new Etat(0, 0, 60, new long[6], List.of(), Map.of(), Map.of(), List.of());
    }

    static Path fichier() {
        return FMLPaths.CONFIGDIR.get().resolve("heraliz_quetes").resolve("recyclage.json");
    }

    /** Relu avec les PNJ (démarrage, /quete recharger) ; un fichier absent ou illisible = recycleur fermé. */
    static void charger(List<String> erreurs) {
        Path f = fichier();
        if (!Files.exists(f)) {
            etat = vide();
            return;
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            double chance = Math.max(0, Math.min(1, o.has("chance") ? o.get("chance").getAsDouble() : 0.1));
            int maxPieces = Math.max(1, o.has("max_pieces") ? o.get("max_pieces").getAsInt() : 2);
            double secondes = Math.max(0.5, Math.min(30, o.has("secondes_par_objet") ? o.get("secondes_par_objet").getAsDouble() : 3));
            long[] herals = new long[6];
            if (o.has("herals_si_rien")) {
                JsonArray a = o.getAsJsonArray("herals_si_rien");
                for (int i = 0; i < 6 && i < a.size(); i++) herals[i] = Math.max(0, a.get(i).getAsLong());
            }
            List<String> jamais = new ArrayList<>();
            if (o.has("jamais")) for (JsonElement e : o.getAsJsonArray("jamais")) jamais.add(e.getAsString());
            Map<String, String> cles = new HashMap<>();
            Map<String, Regle> exactes = new HashMap<>();
            List<Regle> larges = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("regles")) {
                JsonObject r = e.getAsJsonObject();
                String objet = r.get("objet").getAsString();
                boolean prefixe = objet.endsWith("*");
                if (prefixe) objet = objet.substring(0, objet.length() - 1);
                String cle = r.has("cle") ? r.get("cle").getAsString() : null;
                String valeur = r.has("valeur") ? r.get("valeur").getAsString() : null;
                int par = r.has("par") ? Math.max(1, r.get("par").getAsInt()) : 1;
                List<String> rend = r.has("rend") ? liste(r.getAsJsonArray("rend")) : null;
                List<List<String>> rendRarete = null;
                if (rend == null) {
                    rendRarete = new ArrayList<>();
                    for (JsonElement l : r.getAsJsonArray("rend_rarete")) rendRarete.add(liste(l.getAsJsonArray()));
                    if (rendRarete.isEmpty()) continue;
                }
                Regle regle = new Regle(objet, prefixe, cle, valeur, par, rend, rendRarete);
                if (valeur != null && cle != null && !prefixe) {
                    cles.put(objet, cle);
                    exactes.putIfAbsent(objet + "|" + valeur, regle);
                } else {
                    larges.add(regle);
                }
            }
            etat = new Etat(chance, maxPieces, (int) Math.round(secondes * 20), herals, List.copyOf(jamais), Map.copyOf(cles),
                    Map.copyOf(exactes), List.copyOf(larges));
            LOG.info("[heraliz_quetes] recycleur : {} règles, {} % par composant, {} pièce(s) au plus, {} s par objet",
                    exactes.size() + larges.size(), Math.round(chance * 100), maxPieces, secondes);
        } catch (Exception ex) {
            erreurs.add("recyclage.json : " + ex.getMessage());
        }
    }

    private static List<String> liste(JsonArray a) {
        List<String> l = new ArrayList<>(a.size());
        for (JsonElement e : a) l.add(e.getAsString());
        return List.copyOf(l);
    }

    static float chance() {
        return (float) etat.chance;
    }

    static int maxPieces() {
        return etat.maxPieces;
    }

    static int ticksParObjet() {
        return etat.ticksParObjet;
    }

    /** La règle de l'objet, ou null s'il ne se recycle pas. */
    static Regle regle(ItemStack s) {
        if (s.isEmpty() || Echanges.interdit(s)) return null;
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (rl == null) return null;
        String id = rl.toString();
        Etat e = etat;
        for (String j : e.jamais) {
            if (j.endsWith("*") ? id.startsWith(j.substring(0, j.length() - 1)) : id.equals(j)) return null;
        }
        String cle = e.cles.get(id);
        if (cle != null && s.hasTag() && s.getTag().contains(cle)) {
            Regle r = e.exactes.get(id + "|" + s.getTag().getString(cle));
            if (r != null) return r;
        }
        for (Regle r : e.larges) if (r.correspond(id, s)) return r;
        return null;
    }

    /** Ce que le recycleur ferait de cet objet : état, composants possibles, taille du paquet, hérals si rien ne sort. */
    record Verdict(int etat, int rang, int par, List<String> rend, long herals, float facteur, int usure) {
    }

    /**
     * Part de la chance gardée selon l'état de l'objet (26/10, demande du propriétaire : « plus un objet est endommagé,
     * moins il rapporte ; vers 20 % presque plus rien ») : ((état - USURE_ZERO) / (100 - USURE_ZERO))^USURE_COURBE, entre 0
     * et 1. Neuf : 1 ; 75 % : 0,59 ; 50 % : 0,26 ; 30 % : 0,07 ; 20 % : 0,01. Armes TaCZ : usure HeraliZ ; armures, sacs et
     * tout objet à durabilité : durabilité restante ; le reste : 1.
     */
    static final float USURE_ZERO = 15f, USURE_COURBE = 1.5f;

    static int etatPct(ItemStack s) {
        if (Ponts.gunId(s) != null) return Ponts.usure(s);
        if (s.isDamageableItem() && s.getMaxDamage() > 0) {
            return Math.max(0, Math.min(100, Math.round(100f * (s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage())));
        }
        return 100;
    }

    static float facteurUsure(int pct) {
        if (pct >= 100) return 1f;
        float x = Math.max(0f, (pct - USURE_ZERO) / (100f - USURE_ZERO));
        return (float) Math.pow(x, USURE_COURBE);
    }

    /** {@code ventes} : {@link SaisonQuetes#ventes()} lu une fois pour plusieurs objets ; {@code valeurs} : cache partagé. */
    static Verdict verdict(ItemStack s, Map<String, Long> ventes, Map<String, Long> valeurs) {
        Etat e = etat;
        int rang = Math.max(0, Ponts.rarete(s));
        long herals = e.herals[Math.min(5, rang)];
        int u = etatPct(s);
        float f = facteurUsure(u);
        if (Echanges.interdit(s)) return new Verdict(PROTEGE, rang, 1, List.of(), herals, f, u);
        Regle r = regle(s);
        if (r == null) return new Verdict(NON, rang, 1, List.of(), herals, f, u);
        List<String> rend = r.rendPour(rang);
        long achat = SaisonQuetes.prixVente(ventes, s);
        if (achat >= 0 && tropRentable(e, rend, rang, achat * r.par(), ventes, valeurs)) return new Verdict(REFUSE, rang, r.par(), rend, herals, f, u);
        if (s.getCount() < r.par()) return new Verdict(MANQUE, rang, r.par(), rend, herals, f, u);
        return new Verdict(OK, rang, r.par(), rend, herals, f, u);
    }

    /** Aperçu d'une case pour l'écran : composants regroupés (« Ferraille III » x 4), dans l'ordre de la règle. */
    static Reseau.ApercuCase apercu(int index, Verdict v) {
        Map<String, Integer> parComposant = new LinkedHashMap<>();
        for (String c : v.rend()) parComposant.merge(c, 1, Integer::sum);
        return new Reseau.ApercuCase(index, v.etat(), v.rang(), v.par(), (int) Math.min(Integer.MAX_VALUE, v.herals()),
                new ArrayList<>(parComposant.keySet()), new ArrayList<>(parComposant.values()), Math.round(v.facteur() * 1000), v.usure());
    }

    /** Ce qui sort d'une unité recyclée : composants (au plus {@code max_pieces}) ou, si rien, des hérals. */
    record Tirage(List<ItemStack> pieces, long herals) {
    }

    static Tirage tirer(Verdict v) {
        Etat e = etat;
        List<String> sortis = new ArrayList<>();
        double chance = e.chance * v.facteur();   // objet abîmé : moins de chances (26/10)
        for (String c : v.rend()) if (Moteur.RANDOM.nextDouble() < chance) sortis.add(c);
        // plafond : au plus max_pieces pièces, choisies au hasard parmi celles qui sont sorties
        while (sortis.size() > e.maxPieces) sortis.remove(Moteur.RANDOM.nextInt(sortis.size()));
        List<ItemStack> pieces = new ArrayList<>();
        for (String c : sortis) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(c));
            if (item == null || item == Items.AIR) {
                LOG.warn("[heraliz_quetes] recycleur : composant inconnu {}", c);
                continue;
            }
            pieces.add(new ItemStack(item));
        }
        return new Tirage(pieces, pieces.isEmpty() ? v.herals() : 0);
    }

    static void ouvrir(ServerPlayer p, Pnj ent) {
        ouvrir(p, pl -> ent.isAlive() && pl.distanceToSqr(ent) < 64);
    }

    /**
     * Le bloc recycleur du spawn (25/10, {@code bloc/Recycleur}) : mêmes règles que le PNJ recycleur et que les marchands
     * (refusé en match, en combat, à terre, au tutoriel, sur la carte en survie) ; fermé si le joueur s'éloigne ou si le
     * bloc disparaît.
     */
    static void ouvrirBloc(ServerPlayer p, net.minecraft.core.BlockPos pos) {
        Texte refus = Marchands.empechement(p);
        if (refus != null) {
            Moteur.notif(p, refus, 0xFF6B6B);
            return;
        }
        net.minecraft.world.phys.Vec3 centre = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        ouvrir(p, pl -> pl.level().getBlockState(pos).is(fr.heraliz.quetes.HeralizQuetes.RECYCLEUR.get()) && pl.distanceToSqr(centre) < 64);
    }

    private static void ouvrir(ServerPlayer p, java.util.function.Predicate<Player> valide) {
        if (etat.exactes.isEmpty() && etat.larges.isEmpty()) {
            Moteur.notif(p, Texte.cle("heraliz_quetes.recycleur.ferme"), 0xFF6B6B);
            return;
        }
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new MenuRecycleurServeur(id, inv, valide),
                Component.translatableWithFallback("heraliz_quetes.recycleur.titre", "Recycleur")));
    }

    /**
     * Règle des propriétaires (25/10) : un objet vendu par un marchand au prix P (ou reçu par un échange, P = 0) ne doit
     * jamais rapporter plus au recycleur qu'à la revente, soit 50 % de P. Pour {@code cout} hérals d'objets (une unité de
     * recyclage), refusé si l'espérance (chaque composant revendu au meilleur racheteur, plafond des 50 % compris, ou les
     * hérals « rien ») dépasse la moitié du coût : refaire achat puis recyclage fait toujours perdre au moins la moitié
     * en moyenne. Pas de borne sur le meilleur tirage : elle refusait aussi les mêmes objets trouvés sur la carte pour une
     * chance sur des centaines de milliers. Même calcul dans {@code outils/marchands.py} (sans le plafond de pièces, qui ne
     * fait que baisser l'espérance : le refus reste du côté prudent).
     */
    private static boolean tropRentable(Etat e, List<String> rend, int rang, long cout, Map<String, Long> ventes, Map<String, Long> valeurs) {
        long somme = 0;
        for (String c : rend) {
            somme += valeurs.computeIfAbsent(c, k -> {
                ResourceLocation rl = ResourceLocation.tryParse(k);
                Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
                return item == null || item == Items.AIR ? 0L : Marchands.meilleurRachat(new ItemStack(item), ventes);
            });
        }
        long rien = e.herals[Math.min(5, rang)];
        double esperance = e.chance * somme + Math.pow(1 - e.chance, rend.size()) * rien;
        return esperance * 2 > cout;
    }

    /** Fin d'une session de recyclage : résumé au joueur et dans le journal du serveur. */
    static void bilan(ServerPlayer p, int recycles, int composants, long herals) {
        if (recycles <= 0) return;
        Journalieres.recyclage(p, recycles);
        Moteur.notif(p, Texte.cle("heraliz_quetes.recycleur.resultat", Texte.brut(recycles), Texte.brut(composants),
                Texte.brut(Ponts.nombre(herals))), 0xE8C36A);
        LOG.info("[heraliz_quetes] recycleur : {} a recyclé {} objet(s) : {} composant(s), {} hérals", p.getGameProfile().getName(),
                recycles, composants, herals);
    }
}
