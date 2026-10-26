package fr.heraliz.quetes.serveur;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.objet.ObjetQuete;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le moteur des quêtes (serveur) : accepter, faire avancer chaque étape d'après ce que fait le joueur, terminer et payer.
 * Tout est décidé ici ; le jeu du joueur reçoit seulement ce qu'il faut afficher (journal, suivi, lueurs, notes).
 */
final class Moteur {
    private static final Logger LOG = LogUtils.getLogger();
    static final String SPAWN = "heralisz:spawn";
    /** Un objet de quête perdu (mort, volé) se retrouve au même point au bout de 30 minutes. */
    static final long REPRISE_MS = 30 * 60_000L;
    private static final Pattern PALIER = Pattern.compile("^t(\\d)_");
    static final Random RANDOM = new Random();

    static MinecraftServer serveur;
    /** Quêtes tirées des contrats, relues une fois (clé de la quête -> définition). */
    private static final Map<String, Defs.Quete> CONTRATS = new HashMap<>();

    private Moteur() {
    }

    static void demarrer(MinecraftServer s) {
        serveur = s;
        CONTRATS.clear();
    }

    static void arreter() {
        serveur = null;
        CONTRATS.clear();
        DERNIER_SUIVI.clear();
    }

    static void oublierContrats() {
        CONTRATS.clear();
    }

    static Points points() {
        return Points.de(serveur);
    }

    // ------------------------------------------------------------------ définitions
    static Defs.Quete def(Donnees.DActive a) {
        if (!a.suite.isEmpty()) {
            Defs.Pnj p = Catalogue.pnj(a.pnj);
            return p == null ? null : p.quete(a.suite);
        }
        return CONTRATS.computeIfAbsent(a.cle, k -> {
            try {
                return Defs.quete(JsonParser.parseString(a.json).getAsJsonObject(), a.pnj, new ArrayList<>());
            } catch (RuntimeException ex) {
                LOG.warn("[heraliz_quetes] contrat illisible {} : {}", a.cle, ex.getMessage());
                return null;
            }
        });
    }

    static Defs.Etape etape(Donnees.DActive a, Defs.Quete q) {
        return q == null || a.aRendre || a.etape < 0 || a.etape >= q.etapes.size() ? null : q.etapes.get(a.etape);
    }

    /** Le PNJ à qui l'étape se fait (donner, montrer, payer, parler) : celui de l'étape, sinon celui de la quête. */
    static String pnjEtape(Defs.Etape e, Donnees.DActive a) {
        return e.texte("pnj", a.pnj);
    }

    static boolean auPnj(Defs.Etape e) {
        return switch (e.type) {
            case "donner", "montrer", "payer", "parler" -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ accepter, avancer, terminer
    /** Vrai si les points dont la quête a besoin existent (sinon : « pas encore prête »). */
    static boolean prete(Defs.Quete q) {
        Points pts = points();
        for (Defs.Etape e : q.etapes) {
            if (e.p.has("groupe")) {
                int taille = pts.groupe(e.texte("groupe", "")).size();
                // 24/10 : assez de points pour l'étape (avant, 3 insignes posées pour 5 demandées : étape finie à 3,
                // la suivante en voulait 5, quête bloquée pour toujours) ; « un_seul » : un seul point suffit
                if (taille == 0 || (!e.oui("un_seul") && taille < e.nombre())) return false;
            }
            for (String id : Defs.liste(e.p, "point")) if (pts.point(id) == null) return false;
        }
        return true;
    }

    static Donnees.DActive accepter(ServerPlayer p, Defs.Pnj pnj, Defs.Quete q, String json) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        Donnees.DActive a = new Donnees.DActive();
        a.pnj = pnj.id;
        if (json == null) {
            a.suite = q.id;
            a.cle = "s:" + pnj.id + ":" + q.id;
        } else {
            a.json = json;
            a.cle = "c:" + pnj.id + ":" + Long.toHexString(RANDOM.nextLong() & 0xFFFFFFFFFFL);
            CONTRATS.put(a.cle, q);
        }
        a.debut = System.currentTimeMillis();
        a.etape = 0;
        j.actives.put(a.cle, a);
        if (j.suivie.isEmpty() || !j.actives.containsKey(j.suivie)) j.suivie = a.cle;
        for (Defs.Don d : q.donne) {
            for (int i = 0; i < d.nombre(); i++) donnerObjet(p, d.modele(), a.cle, d.ref(), d.nom(), d.desc());
        }
        demarrerEtape(p, a, q);
        Donnees.sale(p.getUUID());
        notif(p, Texte.cle("heraliz_quetes.notif.nouvelle", q.titre), 0xE8C36A);
        envoyerSuivi(p);
        return a;
    }

    static void demarrerEtape(ServerPlayer p, Donnees.DActive a, Defs.Quete q) {
        List<String> avant = new ArrayList<>(a.cibles);
        a.progres = 0;
        a.tenu = 0;
        a.faits.clear();
        a.cibles.clear();
        a.debutEtape = System.currentTimeMillis();
        Defs.Etape e = etape(a, q);
        if (e == null) return;
        // « meme_point » : les mêmes points que l'étape d'avant (tenir puis ramasser au même endroit...)
        if (e.oui("meme_point") && a.etape > 0 && !avant.isEmpty()) {
            a.cibles.addAll(avant);
            return;
        }
        if (e.avecPoints()) {
            Points pts = points();
            List<String> explicites = Defs.liste(e.p, "point");
            if (!explicites.isEmpty()) {
                for (String id : explicites) if (pts.point(id) != null) a.cibles.add(id);
            } else {
                List<Points.Point> g = new ArrayList<>(pts.groupe(e.texte("groupe", "")));
                Collections.shuffle(g, RANDOM);
                // d'abord les points pas encore faits dans cette quête (trois livraisons = trois adresses)
                g.sort(java.util.Comparator.comparing(pt -> a.vus.contains("p:" + pt.id())));
                // « un_seul » : tous les points du groupe, le premier atteint suffit (n'importe quelle salle verte)
                int n = e.oui("un_seul") ? g.size() : e.nombre();
                for (int i = 0; i < Math.min(n, g.size()); i++) a.cibles.add(g.get(i).id());
            }
        }
    }

    /** L'étape en cours est faite : étape suivante, ou retour au PNJ, ou fin immédiate si elle se faisait au PNJ. */
    static void avancer(ServerPlayer p, Donnees.DActive a, Defs.Quete q, boolean auPnj) {
        a.etape++;
        Donnees.sale(p.getUUID());
        if (a.etape >= q.etapes.size()) {
            if (auPnj) {
                terminer(p, a, q);
                return;
            }
            a.aRendre = true;
            Defs.Pnj pnj = Catalogue.pnj(a.pnj);
            notif(p, Texte.cle("heraliz_quetes.notif.a_rendre", pnj == null ? Texte.brut(a.pnj) : pnj.nom), 0x5FD08A);
            son(p, true);
        } else {
            demarrerEtape(p, a, q);
            notif(p, Texte.cle("heraliz_quetes.notif.etape", libelle(q.etapes.get(a.etape - 1))), 0x5FD08A);
            son(p, false);
        }
        // étape faite au PNJ (donner, payer…) : objets ou hérals déjà pris, progression écrite tout de suite (23/10)
        if (auPnj) sauver(p);
        envoyerSuivi(p);
    }

    /** Progression et joueur écrits tout de suite (après un paiement ou des objets pris). */
    static void sauver(ServerPlayer p) {
        Donnees.ecrireMaintenant(p.getUUID());
        Ponts.sauverJoueur(p);
    }

    static void terminer(ServerPlayer p, Donnees.DActive a, Defs.Quete q) {
        // 25/10 : économie injoignable = la quête attend, à rendre plus tard (avant : finie pour toujours, hérals perdus)
        if (q.recompense.herals > 0 && !Ponts.economie()) {
            a.aRendre = true;
            a.etape = q.etapes.size();
            Donnees.sale(p.getUUID());
            notif(p, Texte.cle("heraliz_quetes.notif.recompense_attente"), 0xFF6B6B);
            sauver(p);
            envoyerSuivi(p);
            return;
        }
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        Donnees.DPnj dp = j.pnj(a.pnj);
        j.actives.remove(a.cle);
        CONTRATS.remove(a.cle);
        retirerObjets(p, a.cle);
        Defs.Recompense r = q.recompense;
        boolean paye = r.herals <= 0 || Ponts.deposer(p.getUUID(), r.herals, "quete:" + q.titre.francais());
        dp.rep = Math.max(0, dp.rep + r.reputation);
        r.autres.forEach((pnj, v) -> j.pnj(pnj).rep = Math.max(0, j.pnj(pnj).rep + v));
        if (!r.titre.vide() && !j.titres.contains(r.titre.toString())) j.titres.add(r.titre.toString());
        r.objets.forEach((id, n) -> donnerRecompense(p, id, n, q));
        if (a.suite.isEmpty()) {
            dp.contrats++;
            j.contratsFinis++;
        } else {
            // quête principale (suite d'un PNJ) : jamais refaite, points de saison du clan
            if (!j.finies.contains(a.cle)) SaisonQuetes.pve(p, "quete_principale", 1);
            j.finies.add(a.cle);
            Defs.Pnj def = Catalogue.pnj(a.pnj);
            if (def != null) {
                int i = indexSuite(def, a.suite);
                if (i >= dp.suite) dp.suite = i + 1;
            }
        }
        if (a.cle.equals(j.suivie)) j.suivie = j.actives.isEmpty() ? "" : j.actives.keySet().iterator().next();
        Donnees.sale(p.getUUID());
        Texte gain = r.herals > 0 ? Texte.cle(paye ? "heraliz_quetes.notif.finie_herals" : "heraliz_quetes.notif.finie_sans_herals",
                q.titre, Texte.brut(Ponts.nombre(r.herals))) : Texte.cle("heraliz_quetes.notif.finie", q.titre);
        notif(p, gain, 0xE8C36A);
        if (!r.titre.vide()) notif(p, Texte.cle("heraliz_quetes.notif.titre", r.titre), 0xE8C36A);
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.3F);
        LOG.info("[heraliz_quetes] {} a fini « {} » ({}) : {} hérals{}, réputation {} {}", p.getGameProfile().getName(),
                q.titre.francais(), a.cle, r.herals, paye ? "" : " (NON versés : économie absente)", a.pnj, dp.rep);
        // 23/10 : quête payée = progression et joueur écrits tout de suite (sinon rendue et payée deux fois après un plantage)
        sauver(p);
        envoyerSuivi(p);
    }

    private static java.lang.reflect.Method crediterCle;
    private static boolean crediterCleCherche;

    /** Clé de caisse créditée sur le compte du joueur ({@code fr.heraliz.crates.api.ClesApi}, par réflexion) ; faux sinon. */
    private static boolean crediterCle(ServerPlayer p, String id, int n) {
        if (!id.startsWith("heralizcrates:") || !id.endsWith("_key")) return false;
        if (!crediterCleCherche) {
            crediterCleCherche = true;
            try {
                crediterCle = Class.forName("fr.heraliz.crates.api.ClesApi").getMethod("crediter", java.util.UUID.class, String.class, int.class, String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                LOG.warn("[heraliz_quetes] HeraliZ Caisses sans compte de clés : clés données en objet ({})", e.toString());
            }
        }
        if (crediterCle == null) return false;
        try {
            return Boolean.TRUE.equals(crediterCle.invoke(null, p.getUUID(), id, n, "quete"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("[heraliz_quetes] clé {} non créditée à {} : donnée en objet ({})", id, p.getGameProfile().getName(), e.toString());
            return false;
        }
    }

    /** Objet de récompense : dans l'inventaire, sinon au sol à ses pieds ; une clé de caisse va sur le compte de clés. */
    static void donnerRecompense(ServerPlayer p, String id, int n, Defs.Quete q) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        var item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            LOG.warn("[heraliz_quetes] récompense de « {} » : objet inconnu {}", q.titre.francais(), id);
            return;
        }
        ItemStack s = new ItemStack(item, n);
        // 25/10 : une clé de caisse (Clé de Quêtes...) va sur le compte de clés du joueur (HeraliZ Caisses, ClesApi),
        // jamais en objet ; sans HeraliZ Caisses ou compte indisponible : l'objet, converti plus tard (rien n'est perdu)
        if (crediterCle(p, id, n)) {
            notif(p, Texte.cle("heraliz_quetes.notif.cle_recue", Texte.cle(s.getDescriptionId())), 0xE8C36A);
            return;
        }
        notif(p, Texte.cle("heraliz_quetes.notif.objet_recu", Texte.cle(s.getDescriptionId())), 0xE8C36A);
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    static int indexSuite(Defs.Pnj def, String id) {
        for (int i = 0; i < def.suite.size(); i++) if (def.suite.get(i).id.equals(id)) return i;
        return -1;
    }

    static void abandonner(ServerPlayer p, String cle, boolean echec) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        Donnees.DActive a = j.actives.remove(cle);
        if (a == null) return;
        Defs.Quete q = def(a);
        CONTRATS.remove(cle);
        retirerObjets(p, cle);
        if (cle.equals(j.suivie)) j.suivie = j.actives.isEmpty() ? "" : j.actives.keySet().iterator().next();
        Donnees.sale(p.getUUID());
        Texte titre = q == null ? Texte.brut(cle) : q.titre;
        notif(p, Texte.cle(echec ? "heraliz_quetes.notif.echec" : "heraliz_quetes.notif.abandon", titre), 0xFF6B6B);
        envoyerSuivi(p);
    }

    /**
     * Retire les quêtes en cours qui n'existent plus dans les fichiers (PNJ retiré, contrats compris, ou quête retirée,
     * comme l'ancienne suite de Varga) : sinon elles bloquent la suite du PNJ sans pouvoir avancer. Rend vrai si quelque chose a été retiré.
     */
    static boolean nettoyer(ServerPlayer p) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        boolean retire = false;
        for (Donnees.DActive a : new ArrayList<>(j.actives.values())) {
            Defs.Pnj pnj = Catalogue.pnj(a.pnj);
            boolean perdue = pnj == null || (a.suite.isEmpty() ? pnj.contrats.isEmpty() : def(a) == null);
            if (perdue) {
                j.actives.remove(a.cle);
                retirerObjets(p, a.cle);
                if (a.cle.equals(j.suivie)) j.suivie = "";
                LOG.info("[heraliz_quetes] {} : quête disparue retirée ({})", p.getGameProfile().getName(), a.cle);
                retire = true;
            }
        }
        if (retire) {
            if (j.suivie.isEmpty() && !j.actives.isEmpty()) j.suivie = j.actives.keySet().iterator().next();
            Donnees.sale(p.getUUID());
        }
        return retire;
    }

    /** Recommence la quête à sa première étape (mort avec une quête « sans mourir »). */
    static void recommencer(ServerPlayer p, Donnees.DActive a, Defs.Quete q) {
        a.etape = 0;
        a.aRendre = false;
        a.vus.clear();
        demarrerEtape(p, a, q);
        Donnees.sale(p.getUUID());
        notif(p, Texte.cle("heraliz_quetes.notif.recommence", q.titre), 0xFF6B6B);
    }

    // ------------------------------------------------------------------ objets de quête
    static void donnerObjet(ServerPlayer p, String modele, String quete, String ref, Texte nom, Texte desc) {
        ItemStack s = ObjetQuete.marquer(new ItemStack(HeralizQuetes.modele(modele)), p.getUUID(), quete, ref, nom, desc);
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    static boolean possede(ServerPlayer p, String quete, String ref) {
        for (ItemStack s : inventaire(p)) if (ObjetQuete.est(s, p.getUUID(), quete, ref)) return true;
        return false;
    }

    static void retirerObjets(ServerPlayer p, String quete) {
        for (ItemStack s : inventaire(p)) {
            if (!(s.getItem() instanceof ObjetQuete)) continue;
            var t = ObjetQuete.etiquette(s);
            if (t != null && t.hasUUID("Joueur") && t.getUUID("Joueur").equals(p.getUUID()) && quete.equals(t.getString("Quete"))) s.setCount(0);
        }
    }

    /** Inventaire principal + main secondaire (pas l'armure ni les Curios : ce qu'on « tend » au PNJ). */
    static List<ItemStack> inventaire(Player p) {
        List<ItemStack> l = new ArrayList<>(p.getInventory().items);
        l.addAll(p.getInventory().offhand);
        return l;
    }

    /** Ce que le joueur porte et tient, armure et emplacements Curios compris (ce qu'il peut « montrer »). */
    static List<ItemStack> surLui(Player p) {
        List<ItemStack> l = inventaire(p);
        l.addAll(p.getInventory().armor);
        l.addAll(Ponts.curios(p));
        return l;
    }

    // ------------------------------------------------------------------ objets demandés (donner, montrer, déposer)
    static boolean correspond(ItemStack s, Defs.Etape e, ServerPlayer p, Donnees.DActive a) {
        if (s.isEmpty()) return false;
        String ref = e.texte("objet_quete", "");
        if (!ref.isEmpty()) return ObjetQuete.est(s, p.getUUID(), a.cle, ref);
        if (s.getItem() instanceof ObjetQuete) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        List<String> objets = Defs.liste(e.p, "objet");
        String idTac = Ponts.idTactique(s);
        if (!objets.isEmpty() && (id == null || !objets.contains(id.toString())) && (idTac == null || !objets.contains(idTac))) return false;
        String tag = e.texte("tag", "");
        if (!tag.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(tag.startsWith("#") ? tag.substring(1) : tag);
            if (rl == null || !s.is(TagKey.create(Registries.ITEM, rl))) return false;
        }
        List<String> armes = Defs.liste(e.p, "arme_id");
        if (!armes.isEmpty() && !armes.contains(Ponts.gunId(s))) return false;
        String arme = e.texte("arme", "");
        if (!arme.isEmpty() && (Ponts.gunId(s) == null || !Ponts.arme(s, arme))) return false;
        String armure = e.texte("armure", "");
        if (!armure.isEmpty() && !Ponts.armure(s, armure)) return false;
        String rar = e.texte("rarete_min", "");
        if (!rar.isEmpty() && Ponts.rarete(s) < Ponts.rangRarete(rar)) return false;
        int usureMax = e.entier("usure_max", -1);
        if (usureMax >= 0 && (Ponts.gunId(s) == null || Ponts.usure(s) > usureMax)) return false;
        if (e.oui("silencieux") && !Ponts.silencieux(s)) return false;
        if (objets.isEmpty() && tag.isEmpty() && armes.isEmpty() && arme.isEmpty() && armure.isEmpty()) return !rar.isEmpty() || usureMax >= 0;
        return true;
    }

    /** Clé d'un objet pour « differents » : l'arme TaCZ, le soin ou la grenade du mod tactique, sinon l'objet. */
    static String sorte(ItemStack s) {
        String g = Ponts.gunId(s);
        if (g != null) return g;
        String t = Ponts.idTactique(s);
        if (t != null) return t;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id == null ? "?" : id.toString();
    }

    /** Combien d'objets demandés le joueur a sur lui (« differents » : une sorte compte une fois, déjà données exclues). */
    static int compter(ServerPlayer p, Defs.Etape e, Donnees.DActive a) {
        boolean diff = e.oui("differents");
        java.util.Set<String> vus = new java.util.HashSet<>(diff ? a.vus : java.util.Set.of());
        int n = 0;
        for (ItemStack s : e.type.equals("montrer") ? surLui(p) : inventaire(p)) {
            if (!correspond(s, e, p, a)) continue;
            if (diff) {
                if (vus.add(sorte(s))) n++;
            } else n += s.getCount();
        }
        return n;
    }

    /** Donne au PNJ ce que le joueur a (jusqu'au nombre demandé) ; rend combien d'objets ont été pris. */
    static int prendre(ServerPlayer p, Defs.Etape e, Donnees.DActive a, int voulu) {
        boolean diff = e.oui("differents");
        int pris = 0;
        for (ItemStack s : inventaire(p)) {
            if (pris >= voulu) break;
            if (!correspond(s, e, p, a)) continue;
            if (diff) {
                String k = sorte(s);
                if (a.vus.contains(k)) continue;
                a.vus.add(k);
                s.shrink(1);
                pris++;
            } else {
                int n = Math.min(s.getCount(), voulu - pris);
                s.shrink(n);
                pris += n;
            }
        }
        return pris;
    }

    /** Étape au PNJ (donner, montrer, payer, parler) : rend vrai si l'étape est finie. */
    static boolean faireAuPnj(ServerPlayer p, Donnees.DActive a, Defs.Quete q, Defs.Etape e) {
        int voulu = e.nombre();
        switch (e.type) {
            case "donner" -> {
                int pris = prendre(p, e, a, voulu - a.progres);
                a.progres += pris;
                Donnees.sale(p.getUUID());
                if (a.progres < voulu) {
                    if (pris == 0) notif(p, Texte.cle("heraliz_quetes.notif.rien_a_donner", libelle(e)), 0xFF6B6B);
                    else notif(p, Texte.cle("heraliz_quetes.notif.donne", Texte.brut(a.progres), Texte.brut(voulu)), 0xE8C36A);
                    envoyerSuivi(p);
                    return false;
                }
                return true;
            }
            case "montrer" -> {
                if (compter(p, e, a) >= voulu) return true;
                notif(p, Texte.cle("heraliz_quetes.notif.rien_a_montrer", libelle(e)), 0xFF6B6B);
                return false;
            }
            case "payer" -> {
                long v = e.entier("herals", 0);
                if (v <= 0 || Ponts.retirer(p.getUUID(), v, "quete:" + q.titre.francais())) return true;
                notif(p, Texte.cle("heraliz_quetes.notif.pas_assez", Texte.brut(Ponts.nombre(v))), 0xFF6B6B);
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    // ------------------------------------------------------------------ événements
    /** Une créature meurt : étapes « tuer » du tueur et, pour un boss, des joueurs proches (participation). */
    static void mort(LivingEntity victime, ServerPlayer tueur) {
        if (!(victime.level() instanceof ServerLevel lvl) || victime instanceof Player) return;
        for (ServerPlayer p : lvl.players()) {
            boolean estTueur = p == tueur;
            if (!estTueur && p.distanceToSqr(victime) > 128 * 128) continue;
            Donnees.DJoueur j = Donnees.de(p.getUUID());
            for (Donnees.DActive a : new ArrayList<>(j.actives.values())) {
                Defs.Quete q = def(a);
                Defs.Etape e = etape(a, q);
                if (e == null || !e.type.equals("tuer")) continue;
                int part = e.entier("participation", 0);
                if (!estTueur && (part <= 0 || p.distanceTo(victime) > part)) continue;
                if (!cible(e, victime)) continue;
                if (estTueur && part <= 0) {
                    ItemStack main = p.getMainHandItem();
                    if (!Ponts.arme(main, e.texte("arme", ""))) continue;
                    List<String> ids = Defs.liste(e.p, "arme_id");
                    if (!ids.isEmpty() && !ids.contains(Ponts.gunId(main))) continue;
                    if (e.oui("silencieux") && !Ponts.silencieux(main)) continue;
                    if (p.distanceTo(victime) < e.entier("distance_min", 0)) continue;
                }
                if (e.oui("nuit") && !lvl.isNight()) continue;
                if (!a.cibles.isEmpty() && !dansZone(victime.level().dimension().location(), victime.blockPosition(), a, e.rayon())) continue;
                a.progres++;
                Donnees.sale(p.getUUID());
                if (a.progres >= e.nombre()) avancer(p, a, q, false);
                else suiviBientot(p);
            }
        }
    }

    /** Un joueur à terre est achevé (exécution ou coup de grâce) par un autre joueur : étapes « executer » du tueur. */
    static void execution(ServerPlayer victime, ServerPlayer tueur) {
        if (tueur == null || tueur == victime) return;
        avancerType(tueur, "executer");
    }

    /** Fait avancer d'un pas toutes les étapes de ce type du joueur. */
    static void avancerType(ServerPlayer p, String type) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        for (Donnees.DActive a : new ArrayList<>(j.actives.values())) {
            Defs.Quete q = def(a);
            Defs.Etape e = etape(a, q);
            if (e == null || !e.type.equals(type)) continue;
            a.progres++;
            Donnees.sale(p.getUUID());
            if (a.progres >= e.nombre()) avancer(p, a, q, false);
            else suiviBientot(p);
        }
    }

    /** Dernier relevé de chaque joueur : armes dans l'inventaire (table d'armurier ouverte, -1 sinon), frappes en cours. */
    private static final Map<UUID, Integer> ARMES = new HashMap<>(), FRAPPES = new HashMap<>();

    static void oublier(UUID u) {
        ARMES.remove(u);
        FRAPPES.remove(u);
        DERNIER_SUIVI.remove(u);
    }

    /** Étape « fabriquer » : une arme de plus dans l'inventaire pendant que la Table d'armurier (TaCZ) est ouverte. */
    private static boolean fabrication(ServerPlayer p) {
        int n = Ponts.tableArmes(p) ? Ponts.armes(p) : -1;
        Integer avant = ARMES.put(p.getUUID(), n);
        return avant != null && avant >= 0 && n > avant;
    }

    /** Étape « frappe » : une frappe aérienne de plus appelée par le joueur. */
    private static boolean frappe(ServerPlayer p) {
        int n = Ponts.frappes(p.getUUID());
        Integer avant = FRAPPES.put(p.getUUID(), n);
        return avant != null && avant >= 0 && n > avant;
    }

    static boolean cible(Defs.Etape e, LivingEntity v) {
        List<String> l = Defs.liste(e.p, "cible");
        if (l.isEmpty()) l = List.of("zombies");
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(v.getType());
        for (String c : l) {
            String s = c.toLowerCase(Locale.ROOT);
            if (s.equals("zombies") || s.equals("monstres")) {
                if (v instanceof Enemy) return true;
            } else if (s.equals("*")) {
                return true;
            } else if (s.startsWith("#")) {
                ResourceLocation rl = ResourceLocation.tryParse(s.substring(1));
                if (rl != null && v.getType().is(TagKey.create(Registries.ENTITY_TYPE, rl))) return true;
            } else if (s.endsWith(":*")) {
                if (id != null && id.getNamespace().equals(s.substring(0, s.length() - 2))) return true;
            } else if (id != null && id.toString().equals(s)) return true;
        }
        return false;
    }

    static boolean dansZone(ResourceLocation monde, BlockPos pos, Donnees.DActive a, int rayon) {
        Points pts = points();
        for (String id : a.cibles) {
            Points.Point pt = pts.point(id);
            if (pt != null && pt.monde().equals(monde.toString()) && pt.pos().distSqr(pos) <= (double) rayon * rayon) return true;
        }
        return false;
    }

    /** Le joueur est mort : quêtes « sans mourir » recommencées, étapes « survivre » et « tenir » remises à zéro. */
    static void mortJoueur(ServerPlayer p) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        for (Donnees.DActive a : new ArrayList<>(j.actives.values())) {
            Defs.Quete q = def(a);
            if (q == null) continue;
            if (q.sansMourir && (a.etape > 0 || a.progres > 0 || !a.faits.isEmpty() || a.aRendre)) {
                recommencer(p, a, q);
                continue;
            }
            Defs.Etape e = etape(a, q);
            if (e != null && (e.type.equals("survivre") || e.type.equals("tenir") || e.oui("sans_mourir"))) {
                a.progres = 0;
                a.tenu = 0;
                Donnees.sale(p.getUUID());
            }
        }
        envoyerSuivi(p);
    }

    /** Le joueur est touché : étapes « sans_degats » remises à zéro. */
    static void blesse(ServerPlayer p) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        for (Donnees.DActive a : j.actives.values()) {
            Defs.Etape e = etape(a, def(a));
            if (e != null && e.oui("sans_degats") && a.progres > 0) {
                a.progres = 0;
                Donnees.sale(p.getUUID());
                notif(p, Texte.cle("heraliz_quetes.notif.touche", libelle(e)), 0xFF6B6B);
                suiviBientot(p);
            }
        }
    }

    /** Arrivée au spawn depuis la carte (extraction réussie). */
    static void arriveeSpawn(ServerPlayer p) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        for (Donnees.DActive a : new ArrayList<>(j.actives.values())) {
            Defs.Quete q = def(a);
            Defs.Etape e = etape(a, q);
            if (e == null || !e.type.equals("extraction")) continue;
            a.progres++;
            Donnees.sale(p.getUUID());
            if (a.progres >= e.nombre()) avancer(p, a, q, false);
        }
        envoyerSuivi(p);
    }

    /** Clic droit sur un bloc : points de quête, et conteneurs de HeraliZ Loot (étapes « fouiller »). Vrai = pris. */
    static boolean clic(ServerPlayer p, BlockPos pos, BlockState etat) {
        ResourceLocation monde = p.level().dimension().location();
        ResourceLocation bloc = ForgeRegistries.BLOCKS.getKey(etat.getBlock());
        if (bloc != null && bloc.getNamespace().equals("heraliz_loot")) {
            // 25/10 : une « partie » d'un grand conteneur (heraliz_loot:partie, décalage dx/dy/dz) compte pour son bloc
            // principal ; avant, cliquer les 4 parties d'un même meuble comptait 4 conteneurs fouillés
            BlockPos principal = principalLoot(pos, etat);
            if (principal != pos) {
                ResourceLocation b2 = ForgeRegistries.BLOCKS.getKey(p.level().getBlockState(principal).getBlock());
                if (b2 != null && b2.getNamespace().equals("heraliz_loot")) fouille(p, monde, principal, b2);
            } else fouille(p, monde, pos, bloc);
        }
        Points.Point pt = points().auBloc(monde, pos);
        if (pt == null) return false;
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        long maintenant = System.currentTimeMillis();
        for (Donnees.DActive a : new ArrayList<>(j.actives.values())) {
            Defs.Quete q = def(a);
            Defs.Etape e = etape(a, q);
            if (e == null || !a.cibles.contains(pt.id()) || a.faits.contains(pt.id())) continue;
            switch (e.type) {
                case "ramasser" -> {
                    String ref = e.texte("ref", e.texte("objet", "document"));
                    for (int i = 0; i < Math.max(1, e.entier("quantite", 1)); i++) {
                        donnerObjet(p, e.texte("objet", "document"), a.cle, ref, e.t("nom"), e.t("desc"));
                    }
                    a.refPoint.put(ref, pt.id());
                    a.refQuand.put(ref, maintenant);
                    notif(p, Texte.cle("heraliz_quetes.notif.trouve", e.t("nom")), 0xE8C36A);
                    p.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 0.9F);
                    pointFait(p, a, q, pt);
                    return true;
                }
                case "indice" -> {
                    Reseau.envoyer(p, new Reseau.Note(e.t("titre"), e.t("texte")));
                    pointFait(p, a, q, pt);
                    return true;
                }
                case "code" -> {
                    Reseau.envoyer(p, new Reseau.Clavier(pt.id(), e.t("titre")));
                    return true;
                }
                case "deposer" -> {
                    ItemStack main = p.getMainHandItem();
                    if (correspond(main, e, p, a)) {
                        main.shrink(1);
                        notif(p, Texte.cle("heraliz_quetes.notif.depose", libelle(e)), 0xE8C36A);
                        p.playNotifySound(SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 0.8F, 1.0F);
                        pointFait(p, a, q, pt);
                    } else notif(p, Texte.cle("heraliz_quetes.notif.il_faut", libelle(e)), 0xFF6B6B);
                    return true;
                }
                default -> {
                }
            }
        }
        // objet de quête perdu : il revient au même point au bout de 30 minutes
        for (Donnees.DActive a : j.actives.values()) {
            for (Map.Entry<String, String> r : a.refPoint.entrySet()) {
                if (!r.getValue().equals(pt.id()) || possede(p, a.cle, r.getKey()) || !encoreUtile(a, r.getKey())) continue;
                long depuis = maintenant - a.refQuand.getOrDefault(r.getKey(), 0L);
                if (depuis < REPRISE_MS) {
                    notif(p, Texte.cle("heraliz_quetes.notif.revenir", Texte.brut((REPRISE_MS - depuis) / 60_000L + 1)), 0xFF6B6B);
                    return true;
                }
                Defs.Etape src = ramassage(def(a), r.getKey());
                donnerObjet(p, src == null ? "document" : src.texte("objet", "document"), a.cle, r.getKey(),
                        src == null ? Texte.brut(r.getKey()) : src.t("nom"), src == null ? Texte.VIDE : src.t("desc"));
                a.refQuand.put(r.getKey(), maintenant);
                Donnees.sale(p.getUUID());
                notif(p, Texte.cle("heraliz_quetes.notif.retrouve", src == null ? Texte.brut(r.getKey()) : src.t("nom")), 0xE8C36A);
                envoyerSuivi(p);
                return true;
            }
        }
        return false;
    }

    /**
     * Bloc principal d'une partie de conteneur de HeraliZ Loot ({@code PartieBlock} : propriétés dx, dy, dz, décalées de
     * PORTEE = 3 en largeur), sinon pos elle-même. Lu par le nom des propriétés : Quêtes ne dépend pas de Loot.
     */
    private static BlockPos principalLoot(BlockPos pos, BlockState etat) {
        Integer dx = null, dy = null, dz = null;
        for (net.minecraft.world.level.block.state.properties.Property<?> pr : etat.getProperties()) {
            if (!(pr instanceof net.minecraft.world.level.block.state.properties.IntegerProperty ip)) continue;
            switch (pr.getName()) {
                case "dx" -> dx = etat.getValue(ip);
                case "dy" -> dy = etat.getValue(ip);
                case "dz" -> dz = etat.getValue(ip);
                default -> {
                }
            }
        }
        if (dx == null || dy == null || dz == null) return pos;
        return pos.offset(-(dx - 3), -dy, -(dz - 3));
    }

    private static void pointFait(ServerPlayer p, Donnees.DActive a, Defs.Quete q, Points.Point pt) {
        a.faits.add(pt.id());
        a.vus.add("p:" + pt.id());
        Donnees.sale(p.getUUID());
        Defs.Etape e = etape(a, q);
        if (a.faits.size() >= a.cibles.size() || e != null && e.oui("un_seul")) avancer(p, a, q, false);
        else suiviBientot(p);
    }

    /** L'étape « ramasser » qui donne cet objet. */
    static Defs.Etape ramassage(Defs.Quete q, String ref) {
        if (q == null) return null;
        for (Defs.Etape e : q.etapes) if (e.type.equals("ramasser") && e.texte("ref", e.texte("objet", "document")).equals(ref)) return e;
        return null;
    }

    /** L'objet sert-il encore (une étape en cours ou à venir le demande) ? */
    static boolean encoreUtile(Donnees.DActive a, String ref) {
        Defs.Quete q = def(a);
        if (q == null || a.aRendre) return false;
        for (int i = a.etape; i < q.etapes.size(); i++) if (ref.equals(q.etapes.get(i).texte("objet_quete", ""))) return true;
        return false;
    }

    private static void fouille(ServerPlayer p, ResourceLocation monde, BlockPos pos, ResourceLocation bloc) {
        Matcher m = PALIER.matcher(bloc.getPath());
        int palier = m.find() ? Integer.parseInt(m.group(1)) : 0;
        String cle = Points.cleBloc(monde.toString(), pos);
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        for (Donnees.DActive a : new ArrayList<>(j.actives.values())) {
            Defs.Quete q = def(a);
            Defs.Etape e = etape(a, q);
            if (e == null || !e.type.equals("fouiller") || palier < e.entier("palier_min", 0)) continue;
            String modele = e.texte("modele", "");
            if (!modele.isEmpty() && !bloc.getPath().contains(modele)) continue;
            if (!a.cibles.isEmpty() && !dansZone(monde, pos, a, e.rayon())) continue;
            if (!a.vus.add(cle)) continue;
            a.progres++;
            Donnees.sale(p.getUUID());
            if (a.progres >= e.nombre()) avancer(p, a, q, false);
            else suiviBientot(p);
        }
    }

    /** Dernier essai de code par joueur (un toutes les 2 s : pas de recherche du code par force brute). */
    private static final java.util.Map<java.util.UUID, Long> ESSAI_CODE = new java.util.HashMap<>();

    /** Code tapé sur un coffre à code. */
    static void code(ServerPlayer p, String point, String code) {
        long now = System.currentTimeMillis();
        Long dernier = ESSAI_CODE.get(p.getUUID());
        if (dernier != null && now - dernier < 2000L) return;
        ESSAI_CODE.put(p.getUUID(), now);
        if (ESSAI_CODE.size() > 512) ESSAI_CODE.values().removeIf(t -> now - t > 60_000L);
        Points.Point pt = points().point(point);
        if (pt == null || !pt.monde().equals(p.level().dimension().location().toString())
                || pt.pos().distToCenterSqr(p.position()) > 8 * 8) return;
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        for (Donnees.DActive a : new ArrayList<>(j.actives.values())) {
            Defs.Quete q = def(a);
            Defs.Etape e = etape(a, q);
            if (e == null || !e.type.equals("code") || !a.cibles.contains(point) || a.faits.contains(point)) continue;
            if (normaliser(e.texte("code", "")).equals(normaliser(code))) {
                notif(p, Texte.cle("heraliz_quetes.notif.code_bon"), 0x5FD08A);
                p.playNotifySound(SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.2F);
                pointFait(p, a, q, pt);
            } else {
                notif(p, Texte.cle("heraliz_quetes.notif.code_faux"), 0xFF6B6B);
                p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
            return;
        }
    }

    private static String normaliser(String s) {
        return s.replace(" ", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    /** Toutes les 10 ticks : endroits, tenir, attendre, survivre, délais. */
    static void tick(ServerPlayer p) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        if (j.actives.isEmpty()) return;
        ResourceLocation monde = p.level().dimension().location();
        long maintenant = System.currentTimeMillis();
        Points pts = points();
        Boolean fabrique = null, frappe = null;
        for (Donnees.DActive a : new ArrayList<>(j.actives.values())) {
            Defs.Quete q = def(a);
            Defs.Etape e = etape(a, q);
            if (e == null) continue;
            if (e.type.equals("fabriquer")) {
                if (fabrique == null) fabrique = fabrication(p);
                if (fabrique) {
                    a.progres++;
                    Donnees.sale(p.getUUID());
                    if (a.progres >= e.nombre()) avancer(p, a, q, false);
                    else suiviBientot(p);
                }
                continue;
            }
            if (e.type.equals("frappe")) {
                if (frappe == null) frappe = frappe(p);
                if (frappe) {
                    a.progres++;
                    Donnees.sale(p.getUUID());
                    if (a.progres >= e.nombre()) avancer(p, a, q, false);
                    else suiviBientot(p);
                }
                continue;
            }
            int delai = e.entier("delai", 0);
            if (delai > 0 && maintenant - a.debutEtape > delai * 1000L) {
                abandonner(p, a.cle, true);
                continue;
            }
            switch (e.type) {
                case "endroit" -> {
                    for (String id : a.cibles) {
                        if (a.faits.contains(id)) continue;
                        Points.Point pt = pts.point(id);
                        if (pt == null || !pt.monde().equals(monde.toString())) continue;
                        if (pt.pos().distToCenterSqr(p.position()) <= (double) e.rayon() * e.rayon()) {
                            notif(p, Texte.cle("heraliz_quetes.notif.lieu", Texte.fr(pt.nomOuId())), 0x5FD08A);
                            pointFait(p, a, q, pt);
                            break;
                        }
                    }
                }
                case "tenir" -> {
                    boolean dedans = false;
                    for (String id : a.cibles) {
                        Points.Point pt = pts.point(id);
                        if (pt != null && pt.monde().equals(monde.toString())
                                && pt.pos().distToCenterSqr(p.position()) <= (double) e.rayon() * e.rayon()) dedans = true;
                    }
                    if (dedans) {
                        a.tenu += 10;
                        if (a.tenu >= e.entier("secondes", 30) * 20) avancer(p, a, q, false);
                        else suiviBientot(p);
                    } else if (a.tenu > 0) {
                        a.tenu = 0;
                        notif(p, Texte.cle("heraliz_quetes.notif.sorti"), 0xFF6B6B);
                        suiviBientot(p);
                    }
                }
                case "attendre" -> {
                    if (maintenant - a.debutEtape >= e.entier("secondes", 60) * 1000L) avancer(p, a, q, false);
                }
                case "survivre" -> {
                    int vieMax = e.entier("vie_max", 0);
                    // 25/10 : seulement sur la carte (« Reste 15 minutes sur la carte ») ; avant, partout sauf au spawn :
                    // le monde du tutoriel des bases ou les arènes du ClashWar comptaient aussi
                    if (monde.equals(net.minecraft.world.level.Level.OVERWORLD.location()) && (vieMax <= 0 || p.getHealth() <= vieMax)) {
                        a.progres += 10;
                        if (a.progres >= e.entier("secondes", 600) * 20) avancer(p, a, q, false);
                    }
                }
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------ textes
    static Texte libelle(Defs.Etape e) {
        return e.libelle.vide() ? Texte.cle("heraliz_quetes.etape." + e.type) : e.libelle;
    }

    /** Où en est l'étape : [fait, total]. */
    static int[] progression(Donnees.DActive a, Defs.Etape e) {
        long maintenant = System.currentTimeMillis();
        return switch (e.type) {
            case "ramasser", "indice", "code", "deposer", "endroit" -> e.oui("un_seul") ? new int[]{a.faits.size(), 1}
                    : new int[]{a.faits.size(), Math.max(1, a.cibles.isEmpty() ? e.nombre() : a.cibles.size())};
            case "tenir" -> new int[]{a.tenu / 20, e.entier("secondes", 30)};
            case "attendre" -> new int[]{(int) Math.min(e.entier("secondes", 60), (maintenant - a.debutEtape) / 1000L), e.entier("secondes", 60)};
            case "survivre" -> new int[]{a.progres / 20, e.entier("secondes", 600)};
            case "parler", "payer" -> new int[]{0, 1};
            default -> new int[]{a.progres, e.nombre()};
        };
    }

    static Texte recompense(Defs.Recompense r) {
        Texte t = recompenseBase(r);
        for (Map.Entry<String, Integer> o : r.objets.entrySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(o.getKey());
            var item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            Texte nom = item == null ? Texte.brut(o.getKey()) : Texte.cle(item.getDescriptionId());
            if (o.getValue() > 1) nom = Texte.cle("heraliz_quetes.recompense.objet_nb", Texte.brut(o.getValue()), nom);
            t = t.vide() ? nom : Texte.cle("heraliz_quetes.recompense.et", t, nom);
        }
        return t;
    }

    private static Texte recompenseBase(Defs.Recompense r) {
        if (r.herals > 0 && r.reputation > 0) return Texte.cle("heraliz_quetes.recompense.herals_rep", Texte.brut(Ponts.nombre(r.herals)), Texte.brut(r.reputation));
        if (r.herals > 0) return Texte.cle("heraliz_quetes.recompense.herals", Texte.brut(Ponts.nombre(r.herals)));
        if (r.reputation > 0) return Texte.cle("heraliz_quetes.recompense.rep", Texte.brut(r.reputation));
        return Texte.VIDE;
    }

    // ------------------------------------------------------------------ envois
    static void notif(ServerPlayer p, Texte t, int couleur) {
        Reseau.envoyer(p, new Reseau.Notif(t, couleur));
    }

    static void son(ServerPlayer p, boolean fort) {
        p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, fort ? 0.7F : 0.4F, fort ? 1.4F : 1.1F);
    }

    /** Joueurs dont le suivi doit être renvoyé au prochain tick (évite un envoi par zombie tué). */
    private static final java.util.Set<UUID> SUIVI_A_ENVOYER = new java.util.HashSet<>();

    static void suiviBientot(ServerPlayer p) {
        SUIVI_A_ENVOYER.add(p.getUUID());
    }

    static void envoyerSuivisEnAttente() {
        if (SUIVI_A_ENVOYER.isEmpty() || serveur == null) return;
        for (UUID u : new ArrayList<>(SUIVI_A_ENVOYER)) {
            ServerPlayer p = serveur.getPlayerList().getPlayer(u);
            if (p != null) envoyerSuivi(p);
        }
        SUIVI_A_ENVOYER.clear();
    }

    /**
     * Dernier suivi envoyé à chaque joueur (ses octets, oublié à la déconnexion) : le renvoi de chaque seconde (distance à
     * jour) ne part plus que s'il a changé (25/10 : avant, un paquet par seconde et par joueur qui suit une quête, même
     * identique). Le jeu du joueur garde le dernier suivi reçu jusqu'à sa déconnexion.
     */
    private static final Map<UUID, byte[]> DERNIER_SUIVI = new HashMap<>();

    static void envoyerSuivi(ServerPlayer p) {
        SUIVI_A_ENVOYER.remove(p.getUUID());
        Reseau.Suivi s = Vues.suivi(p);
        byte[] octets = octets(s);
        if (octets != null && java.util.Arrays.equals(octets, DERNIER_SUIVI.get(p.getUUID()))) return;
        if (octets != null) DERNIER_SUIVI.put(p.getUUID(), octets);
        else DERNIER_SUIVI.remove(p.getUUID());
        Reseau.envoyer(p, s);
    }

    /** Le suivi tel qu'il part sur le réseau (pour le comparer au dernier envoyé), null si illisible. */
    private static byte[] octets(Reseau.Suivi s) {
        net.minecraft.network.FriendlyByteBuf b = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer(256));
        try {
            s.pnj().ecrire(b);
            s.titre().ecrire(b);
            b.writeVarInt(s.lignes().size());
            for (Reseau.SLigne l : s.lignes()) {
                l.libelle().ecrire(b);
                b.writeVarInt(l.fait());
                b.writeVarInt(l.total());
                b.writeVarInt(l.distance());
                b.writeBoolean(l.approx());
                b.writeVarInt(l.etat());
            }
            byte[] out = new byte[b.readableBytes()];
            b.readBytes(out);
            return out;
        } catch (RuntimeException ex) {
            return null;
        } finally {
            b.release();
        }
    }

    static void envoyerJournal(ServerPlayer p, boolean ouvrir) {
        Reseau.envoyer(p, Vues.journal(p, ouvrir));
    }

    /** Lueurs des points de la quête près du joueur, et de tous les points pour le staff qui tient la baguette. */
    static List<BlockPos> lueursQuete(ServerPlayer p) {
        List<BlockPos> l = new ArrayList<>();
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        if (j.actives.isEmpty()) return l;
        String monde = p.level().dimension().location().toString();
        Points pts = points();
        for (Donnees.DActive a : j.actives.values()) {
            Defs.Etape e = etape(a, def(a));
            if (e != null && (e.type.equals("ramasser") || e.type.equals("indice") || e.type.equals("code") || e.type.equals("deposer"))) {
                for (String id : a.cibles) {
                    if (a.faits.contains(id)) continue;
                    Points.Point pt = pts.point(id);
                    if (pt != null && pt.monde().equals(monde) && pt.pos().distToCenterSqr(p.position()) < 32 * 32) l.add(pt.pos());
                }
            }
            for (Map.Entry<String, String> r : a.refPoint.entrySet()) {
                if (possede(p, a.cle, r.getKey()) || !encoreUtile(a, r.getKey())) continue;
                Points.Point pt = pts.point(r.getValue());
                if (pt != null && pt.monde().equals(monde) && pt.pos().distToCenterSqr(p.position()) < 32 * 32) l.add(pt.pos());
            }
        }
        return l;
    }

    static boolean estUnPnjValide(EntityType<?> t) {
        return t == HeralizQuetes.PNJ.get();
    }
}
