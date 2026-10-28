package fr.heraliz.grades.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import fr.heralisz.economie.api.EconomieApi;
import fr.heralisz.economie.api.Transaction;
import fr.heraliz.grades.hdv.RegleHdv;
import fr.heraliz.grades.reseau.ReseauHdv;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hôtel des ventes (Grades 2.9.0, maquettes validées le 28/10 : {@code maquettes/hotel_ventes/}) : les joueurs mettent
 * en vente un objet de leur inventaire en hérals (sans frais) ou en Points Boutique (10 % de frais payés par l'acheteur,
 * brûlés ; prix plafonné selon la rareté de l'objet), pour 12, 24 ou 48 h ; 5 ventes en même temps par joueur. Ouvert au
 * spawn seulement ({@code /hdv} ou le PNJ commissaire-priseur), jamais en combat, à terre, en match, au tutoriel.
 * <p>
 * <b>Contre la duplication et l'argent perdu</b> (même méthode que {@code /trade}) :
 * <ul>
 * <li>Tout l'état tient dans UN fichier hors du monde ({@code heraliz_grades/hdv/hdv.json}), réécrit en une fois
 * (fichier temporaire puis remplacement) : une conclusion (offre vendue, objet de l'acheteur, argent du vendeur) est
 * écrite d'un seul coup ou pas du tout.</li>
 * <li>Mise en vente : l'offre est notée « en préparation », l'objet quitte l'inventaire et le joueur est sauvegardé avec
 * une marque (données persistantes {@code heraliz_hdv_marques}), puis l'offre passe « en vente ». Après un plantage, la
 * marque dit, à la connexion du vendeur, si l'objet avait quitté sa sauvegarde (offre gardée) ou non (offre oubliée).</li>
 * <li>Remise d'objets (achat, vente expirée, retirée) : même marque, dans des cases vides, au spawn seulement : jamais
 * deux fois, jamais par terre.</li>
 * <li>Achat : l'offre est réservée (personne d'autre ne peut l'acheter, le vendeur ne peut plus la retirer), puis le
 * paiement est fait en premier. Hérals : retirés et le compte écrit sur le disque avant la conclusion (motif
 * {@code hdv:<réf>}, relu dans le journal du compte après un plantage). PB : débités par le site avec une référence
 * unique jouée une seule fois ; refus clair = offre libérée ; site muet = l'achat attend (statut revérifié toutes les
 * 30 s, aussi après un redémarrage ; « pas fait » cru seulement 2 min après l'envoi).</li>
 * <li>Le vendeur PB reçoit le prix affiché par un crédit du site (référence fixe {@code <réf>-v}, rejouée jusqu'à ce que
 * le site l'accepte) ; ses hérals l'attendent dans « Mes ventes » (versés avec le motif {@code hdv:recup:<id>}, relu
 * après un plantage).</li>
 * </ul>
 * Le joueur n'envoie que des identifiants d'offre ou de ligne, une case d'inventaire, une monnaie, un prix et une durée :
 * tout est relu ici. Journal de chaque opération : {@code heraliz_grades/hdv/journal.log}. Fil serveur seulement.
 */
public final class HdvServeur {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String MARQUES = "heraliz_hdv_marques";
    private static final int CASES = 36;
    /** Plafond technique d'un montant de PB (celui du site). */
    private static final long PB_MAX = 100_000_000L;
    /** Un « pas fait » du site n'est cru que 2 min après l'envoi (une requête en retard ne passe plus). */
    private static final long ATTENTE_STATUT_MS = 120_000L;
    /** Lignes « vendu en PB » gardées pour « Mes ventes » : 7 jours, 10 par vendeur. */
    private static final long HISTORIQUE_MS = 7L * 24 * 3600_000L;
    private static final int HISTORIQUE_PAR_VENDEUR = 10;

    // ------------------------------------------------------------------ réglages (config/heraliz_grades/hdv.json)
    private static int ventesMax = 5, fraisPct = 10;
    private static List<Integer> durees = List.of(12, 24, 48);
    /** Plafond des prix en PB : index 0 sans rareté, puis Commun … Unique. */
    private static List<Long> plafondsPb = List.of(30L, 30L, 75L, 200L, 500L, 1200L, 2500L);
    private static long prixHeralsMax = 10_000_000L;
    private static Path fichierReglages;
    private static long dateReglages = -1;

    // ------------------------------------------------------------------ état (heraliz_grades/hdv/hdv.json)
    /** Une offre : « prepare » (objet en train de quitter l'inventaire), « vente » ; acheteur non nul = réservée. */
    static final class Offre {
        String id, vendeur, vendeurNom, objet, objetId, cles, etat;
        int devise, categorie;
        long prix, creeA, expireA;
        String acheteur;
        /** Vendeur remis à zéro (/wipe) pendant un achat : l'objet n'est pas rendu si l'achat échoue. */
        boolean efface;
        transient ItemStack cache;
    }

    /** Un achat en cours : « prepare » (hérals), « envoye » / « verifier » (PB), « passe » / « refuse » (conclusion à écrire). */
    static final class Achat {
        String ref, offre, acheteur, acheteurNom, etat;
        int devise;
        long total, envoyeA;
        boolean efface;
    }

    /** Ce qui attend un joueur : objet (acheté, expiré, retiré) ou hérals (vendu). */
    static final class Recup {
        String id, uuid, genre, motif, objet, autre;
        int devise;
        long montant, prix, quand;
        /** Objet en train d'être remis (marque posée sur le joueur) / hérals en train d'être versés. */
        boolean remise, paiement;
        transient ItemStack cache;
    }

    /** Prix d'une vente en PB à verser au vendeur par le site. */
    static final class Versement {
        String ref, vendeur, vendeurNom, etat;
        long montant;
    }

    /** Ligne « vendu en PB » de l'historique du vendeur. */
    static final class Vente {
        String ref, vendeur, objet, acheteurNom;
        long prix, quand;
        boolean verse;
        transient ItemStack cache;
    }

    private static final class Fichier {
        List<Offre> offres = new ArrayList<>();
        List<Achat> achats = new ArrayList<>();
        List<Recup> recups = new ArrayList<>();
        List<Versement> versements = new ArrayList<>();
        List<Vente> ventes = new ArrayList<>();
    }

    private static final Map<String, Offre> OFFRES = new LinkedHashMap<>();
    private static final Map<String, Achat> ACHATS = new LinkedHashMap<>();
    private static final List<Recup> RECUPS = new ArrayList<>();
    private static final Map<String, Versement> VERSEMENTS = new LinkedHashMap<>();
    private static final List<Vente> VENTES = new ArrayList<>();
    /** Références dont une requête au site est partie et pas encore revenue. */
    private static final Set<String> EN_VOL = new HashSet<>();
    /** Mises en vente en PB qui attendent le site (vérification du compte du vendeur). */
    private static final Set<UUID> MISES_EN_ATTENTE = new HashSet<>();
    private static final Map<UUID, Long> DERNIERE_ACTION = new HashMap<>(), DERNIERE_LISTE = new HashMap<>(), DERNIER_SOLDE = new HashMap<>();
    private static final Map<UUID, List<Long>> VENTES_RECENTES = new HashMap<>();
    private static final Map<UUID, Long> SOLDES_PB = new HashMap<>();
    private static final Set<UUID> OUVERTS = new HashSet<>();

    private static MinecraftServer serveur;
    private static Path dossier, fichier;
    /** Fichier illisible au démarrage : plus rien ne s'écrit (il n'est jamais écrasé), l'hôtel reste fermé. */
    private static boolean bloque;
    private static boolean repris;
    /** Dernière écriture ratée : l'état en mémoire est en avance sur le disque (réessayé toutes les 10 s). */
    private static boolean enRetard;
    private static int ticks;

    private HdvServeur() {
    }

    // ------------------------------------------------------------------ cycle de vie
    static void brancher() {
        ReseauHdv.RECEPTION_LISTE.brancher(r -> liste(r.joueur(), r.liste()));
        ReseauHdv.RECEPTION_ACTION.brancher(r -> action(r.joueur(), r.action()));
    }

    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("hdv").executes(c -> c.getSource().getEntity() instanceof ServerPlayer p ? ouvrir(p) : 0));
    }

    static void demarrer(MinecraftServer srv, Path dossierGrades) {
        serveur = srv;
        dossier = dossierGrades.resolve("hdv");
        fichier = dossier.resolve("hdv.json");
        fichierReglages = FMLPaths.CONFIGDIR.get().resolve("heraliz_grades").resolve("hdv.json");
        dateReglages = -1;
        relireReglages();
        OFFRES.clear();
        ACHATS.clear();
        RECUPS.clear();
        VERSEMENTS.clear();
        VENTES.clear();
        EN_VOL.clear();
        MISES_EN_ATTENTE.clear();
        OUVERTS.clear();
        bloque = false;
        repris = false;
        try {
            if (Files.exists(fichier)) {
                Fichier f = GSON.fromJson(Files.readString(fichier, StandardCharsets.UTF_8), Fichier.class);
                if (f != null) {
                    if (f.offres != null) for (Offre o : f.offres) if (o != null && o.id != null && o.objet != null) OFFRES.put(o.id, o);
                    if (f.achats != null) for (Achat a : f.achats) {
                        if (a == null || a.ref == null) continue;
                        // serveur arrêté pendant un débit de PB : le site dira s'il est passé
                        if ("envoye".equals(a.etat)) a.etat = "verifier";
                        ACHATS.put(a.ref, a);
                    }
                    if (f.recups != null) for (Recup r : f.recups) if (r != null && r.id != null && r.uuid != null) RECUPS.add(r);
                    if (f.versements != null) for (Versement v : f.versements) if (v != null && v.ref != null) VERSEMENTS.put(v.ref, v);
                    if (f.ventes != null) for (Vente v : f.ventes) if (v != null && v.vendeur != null) VENTES.add(v);
                }
            }
        } catch (Exception ex) {
            // jamais écrasé : copie mise de côté, hôtel fermé jusqu'à ce que le staff regarde
            bloque = true;
            LOG.error("[grades] hôtel des ventes : {} illisible ({}) : hôtel FERMÉ, fichier gardé tel quel", fichier, ex.toString());
            try {
                Files.copy(fichier, fichier.resolveSibling("hdv.illisible." + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignore) {
            }
        }
        LOG.info("[grades] hôtel des ventes : {} offre(s), {} achat(s) en cours, {} ligne(s) à récupérer, {} versement(s) de PB en attente",
                OFFRES.size(), ACHATS.size(), RECUPS.size(), VERSEMENTS.size());
    }

    static void arreter() {
        serveur = null;
        OUVERTS.clear();
        MISES_EN_ATTENTE.clear();
        EN_VOL.clear();
    }

    private static void relireReglages() {
        try {
            if (fichierReglages == null || !Files.exists(fichierReglages)) return;
            long d = Files.getLastModifiedTime(fichierReglages).toMillis();
            if (d == dateReglages) return;
            dateReglages = d;
            JsonObject o = JsonParser.parseString(Files.readString(fichierReglages, StandardCharsets.UTF_8)).getAsJsonObject();
            int vm = o.has("ventes_max") ? o.get("ventes_max").getAsInt() : 5;
            int fp = o.has("frais_pb_pourcent") ? o.get("frais_pb_pourcent").getAsInt() : 10;
            List<Integer> du = new ArrayList<>();
            if (o.has("durees_heures")) for (JsonElement e : o.getAsJsonArray("durees_heures")) {
                int h = e.getAsInt();
                if (h >= 1 && h <= 24 * 14 && du.size() < 4) du.add(h);
            }
            long phm = o.has("prix_herals_max") ? o.get("prix_herals_max").getAsLong() : 10_000_000L;
            List<Long> pl = new ArrayList<>(plafondsPb);
            if (o.has("plafond_pb")) {
                JsonObject p = o.getAsJsonObject("plafond_pb");
                String[] cles = {"aucune", "commun", "peu_commun", "rare", "epique", "legendaire", "unique"};
                for (int i = 0; i < cles.length; i++) if (p.has(cles[i])) pl.set(i, Math.max(1, Math.min(PB_MAX / 2, p.get(cles[i]).getAsLong())));
            }
            ventesMax = Math.max(1, Math.min(20, vm));
            fraisPct = Math.max(0, Math.min(50, fp));
            if (!du.isEmpty()) durees = List.copyOf(du);
            prixHeralsMax = Math.max(1, phm);
            plafondsPb = List.copyOf(pl);
            LOG.info("[grades] hôtel des ventes : {} ventes au plus, durées {} h, frais PB {} %, plafonds PB {}", ventesMax, durees, fraisPct, plafondsPb);
        } catch (Exception ex) {
            LOG.error("[grades] {} illisible ({}) : réglages de l'hôtel des ventes inchangés", fichierReglages, ex.toString());
        }
    }

    // ------------------------------------------------------------------ outils
    private static MutableComponent msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.hdv." + cle, fr, args);
    }

    private static ServerPlayer joueur(String uuid) {
        if (serveur == null || uuid == null) return null;
        try {
            return serveur.getPlayerList().getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String texte(ItemStack st) {
        return st.save(new CompoundTag()).toString();
    }

    private static ItemStack objet(String snbt) {
        try {
            return ItemStack.of(TagParser.parseTag(snbt));
        } catch (Exception ex) {
            LOG.error("[grades] hôtel des ventes : objet illisible {} ({})", snbt, ex.toString());
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack objet(Offre o) {
        if (o.cache == null) o.cache = objet(o.objet);
        return o.cache;
    }

    private static ItemStack objet(Recup r) {
        if (r.cache == null) r.cache = r.objet == null ? ItemStack.EMPTY : objet(r.objet);
        return r.cache;
    }

    private static ItemStack objet(Vente v) {
        if (v.cache == null) v.cache = v.objet == null ? ItemStack.EMPTY : objet(v.objet);
        return v.cache;
    }

    private static String resume(ItemStack st) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        CompoundTag t = st.getTag();
        String sous = t != null && t.contains("GunId", Tag.TAG_STRING) ? " (" + t.getString("GunId") + ")"
                : t != null && t.contains("AttachmentId", Tag.TAG_STRING) ? " (" + t.getString("AttachmentId") + ")" : "";
        return st.getCount() + "x " + id + sous;
    }

    private static String nouvelId(String type) {
        return Pb.ref(type);
    }

    static void journal(String ligne) {
        if (dossier == null) return;
        try {
            Files.createDirectories(dossier);
            Files.writeString(dossier.resolve("journal.log"), java.time.LocalDateTime.now().withNano(0) + " " + ligne + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            LOG.warn("[grades] hôtel des ventes : journal ({})", ex.toString());
        }
    }

    /** Écrit tout l'état en une fois ; faux (rien n'a changé sur le disque) si c'est impossible. */
    private static boolean sauver() {
        if (bloque || fichier == null) return false;
        try {
            Files.createDirectories(dossier);
            Fichier f = new Fichier();
            f.offres = new ArrayList<>(OFFRES.values());
            f.achats = new ArrayList<>(ACHATS.values());
            f.recups = new ArrayList<>(RECUPS);
            f.versements = new ArrayList<>(VERSEMENTS.values());
            f.ventes = new ArrayList<>(VENTES);
            Path tmp = fichier.resolveSibling("hdv.json.tmp");
            Files.writeString(tmp, GSON.toJson(f), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            enRetard = false;
            return true;
        } catch (Exception ex) {
            LOG.error("[grades] hôtel des ventes : {} impossible à écrire ({})", fichier, ex.toString());
            enRetard = true;
            return false;
        }
    }

    private static java.lang.reflect.Method sauverJoueur;

    /** Sauvegarde tout de suite ce joueur ({@code PlayerList.save}, protégée : par réflexion SRG, comme /trade). */
    private static void sauverJoueur(ServerPlayer p) {
        try {
            if (sauverJoueur == null) {
                sauverJoueur = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(net.minecraft.server.players.PlayerList.class,
                        "m_6765_", ServerPlayer.class);
            }
            sauverJoueur.invoke(serveur.getPlayerList(), p);
        } catch (Exception e) {
            LOG.warn("[grades] hôtel des ventes : sauvegarde du joueur impossible ({}), sauvegarde de tous", e.toString());
            serveur.getPlayerList().saveAll();
        }
    }

    // ------------------------------------------------------------------ marques sur le joueur
    private static ListTag marques(ServerPlayer p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return pers.getList(MARQUES, Tag.TAG_STRING);
    }

    private static void marquer(ServerPlayer p, List<String> ids, boolean ajouter) {
        CompoundTag data = p.getPersistentData();
        CompoundTag pers = data.getCompound(Player.PERSISTED_NBT_TAG);
        ListTag l = pers.getList(MARQUES, Tag.TAG_STRING);
        if (ajouter) {
            for (String id : ids) l.add(StringTag.valueOf(id));
        } else {
            l.removeIf(t -> ids.contains(t.getAsString()));
        }
        if (l.isEmpty()) pers.remove(MARQUES);
        else pers.put(MARQUES, l);
        data.put(Player.PERSISTED_NBT_TAG, pers);
    }

    private static boolean marque(ListTag l, String id) {
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equals(id)) return true;
        return false;
    }

    /**
     * Après un plantage : tranche ce qui était à moitié fait pour ce joueur, d'après les marques de sa sauvegarde. Offre
     * « en préparation » : marquée = l'objet avait quitté l'inventaire (offre mise en vente), sinon il y est encore (offre
     * oubliée). Objet « en cours de remise » : marqué = déjà dans l'inventaire (ligne retirée), sinon à remettre.
     */
    private static void resoudre(ServerPlayer p) {
        String u = p.getUUID().toString();
        ListTag l = marques(p);
        boolean change = false;
        List<String> journalLignes = new ArrayList<>();
        for (Iterator<Offre> it = OFFRES.values().iterator(); it.hasNext(); ) {
            Offre o = it.next();
            if (!u.equals(o.vendeur) || !"prepare".equals(o.etat)) continue;
            if (marque(l, o.id)) {
                o.etat = "vente";
                journalLignes.add("reprise : offre " + o.id + " de " + o.vendeurNom + " mise en vente (objet sorti de l'inventaire avant l'arrêt)");
            } else {
                it.remove();
                journalLignes.add("reprise : offre " + o.id + " de " + o.vendeurNom + " oubliée (objet resté dans l'inventaire)");
            }
            change = true;
        }
        for (Iterator<Recup> it = RECUPS.iterator(); it.hasNext(); ) {
            Recup r = it.next();
            if (!u.equals(r.uuid) || !r.remise) continue;
            if (marque(l, r.id)) {
                it.remove();
                journalLignes.add("reprise : " + r.id + " déjà remis à " + p.getGameProfile().getName());
            } else {
                r.remise = false;
            }
            change = true;
        }
        if (change && !sauver()) return;   // rien de tranché sur le disque : on recommencera
        for (String j : journalLignes) journal(j);
        // marques effacées seulement quand le disque est à jour (sinon elles trancheront après un arrêt)
        if (!l.isEmpty() && !enRetard) {
            List<String> tous = new ArrayList<>();
            for (int i = 0; i < l.size(); i++) tous.add(l.getString(i));
            marquer(p, tous, false);
        }
    }

    // ------------------------------------------------------------------ refus communs
    /** Raison qui empêche ce joueur d'utiliser l'hôtel maintenant, ou null. */
    private static Component refus(ServerPlayer p) {
        if (bloque) return msg("ferme", "L'hôtel des ventes est fermé pour le moment.");
        if (!PontBukkit.authentifie(p)) return Component.translatableWithFallback("message.heraliz_grades.kit.connexion", "Connecte-toi d'abord avec /login.");
        if (!p.isAlive() || p.isSpectator()) return msg("impossible", "Impossible pour le moment.");
        Component r = Restrictions.empechement(p);
        if (r != null) return r;
        if (!WarpsServeur.auSpawn(p)) return msg("spawn", "L'hôtel des ventes n'est ouvert qu'au spawn.");
        return null;
    }

    private static boolean tropTot(Map<UUID, Long> m, UUID u, long ms) {
        long now = System.currentTimeMillis();
        Long avant = m.get(u);
        if (avant != null && now - avant < ms) return true;
        m.put(u, now);
        return false;
    }

    // ------------------------------------------------------------------ ouverture et état
    /** /hdv et le PNJ commissaire-priseur. */
    static int ouvrir(ServerPlayer p) {
        if (serveur == null) return 0;
        Component non = refus(p);
        if (non != null) {
            p.sendSystemMessage(non.copy().withStyle(ChatFormatting.RED));
            return 0;
        }
        resoudre(p);
        OUVERTS.add(p.getUUID());
        etat(p, true, "", false);
        rafraichirSoldePb(p, true);
        livrerEnAttente(p, false);
        return 1;
    }

    /** Solde de PB demandé au site (au plus toutes les 5 s, sauf forcer), renvoyé avec l'état. */
    private static void rafraichirSoldePb(ServerPlayer p, boolean forcer) {
        if (!Pb.actif()) return;
        UUID u = p.getUUID();
        if (!forcer && tropTot(DERNIER_SOLDE, u, 5000)) return;
        if (forcer) DERNIER_SOLDE.put(u, System.currentTimeMillis());
        Pb.solde(p.getGameProfile().getName(), r -> {
            if (r != null && r.code() == 200) SOLDES_PB.put(u, r.nombre("solde"));
            ServerPlayer j = serveur == null ? null : serveur.getPlayerList().getPlayer(u);
            if (j != null && OUVERTS.contains(u)) etat(j, false, "", false);
        });
    }

    private static int mesEnVente(String u) {
        int n = 0;
        for (Offre o : OFFRES.values()) if (u.equals(o.vendeur)) n++;
        return n;
    }

    private static int aRecuperer(String u) {
        int n = 0;
        for (Recup r : RECUPS) if (u.equals(r.uuid)) n++;
        return n;
    }

    private static int offresVisibles() {
        int n = 0;
        for (Offre o : OFFRES.values()) if ("vente".equals(o.etat) && o.acheteur == null) n++;
        return n;
    }

    private static void etat(ServerPlayer p, boolean ouvrir, String message, boolean ok) {
        String u = p.getUUID().toString();
        long herals = EconomieApi.disponible() ? EconomieApi.solde(p.getUUID()) : 0;
        long pb = SOLDES_PB.getOrDefault(p.getUUID(), -1L);
        long hmax = EconomieApi.disponible() ? Math.min(prixHeralsMax, EconomieApi.maxSolde()) : prixHeralsMax;
        ReseauHdv.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new ReseauHdv.Etat(ouvrir, herals, pb, offresVisibles(), mesEnVente(u), ventesMax,
                aRecuperer(u), fraisPct, durees, plafondsPb, hmax, Pb.actif(), message, ok));
    }

    // ------------------------------------------------------------------ liste
    private static void liste(ServerPlayer p, ReseauHdv.Liste l) {
        if (serveur == null || !OUVERTS.contains(p.getUUID())) return;
        if (tropTot(DERNIERE_LISTE, p.getUUID(), 120)) return;
        String u = p.getUUID().toString();
        String q = RegleHdv.normaliser(l.recherche()).trim();
        Set<String> ids = new HashSet<>(l.ids());
        int devise = l.devise(), tri = l.tri(), cat = Math.max(0, Math.min(RegleHdv.CATEGORIES - 1, l.categorie()));
        long now = System.currentTimeMillis();
        int[] parCat = new int[RegleHdv.CATEGORIES];
        List<Offre> choisies = new ArrayList<>();
        for (Offre o : OFFRES.values()) {
            if (!"vente".equals(o.etat) || o.acheteur != null || o.expireA <= now) continue;
            if (devise == 1 && o.devise != RegleHdv.HERALS || devise == 2 && o.devise != RegleHdv.PB) continue;
            if (!q.isEmpty() && (o.cles == null || !o.cles.contains(q)) && !ids.contains(o.objetId)) continue;
            parCat[0]++;
            if (o.categorie > 0 && o.categorie < parCat.length) parCat[o.categorie]++;
            if (cat == 0 || o.categorie == cat) choisies.add(o);
        }
        Comparator<Offre> c = switch (tri) {
            case 1 -> Comparator.comparingLong((Offre o) -> o.prix).thenComparingLong(o -> -o.creeA);
            case 2 -> Comparator.comparingLong((Offre o) -> -o.prix).thenComparingLong(o -> -o.creeA);
            default -> Comparator.comparingLong((Offre o) -> -o.creeA);
        };
        choisies.sort(c);
        int pages = Math.max(1, (choisies.size() + ReseauHdv.PAR_PAGE - 1) / ReseauHdv.PAR_PAGE);
        int page = Math.max(0, Math.min(pages - 1, l.page()));
        List<ReseauHdv.LigneOffre> lignes = new ArrayList<>();
        for (int i = page * ReseauHdv.PAR_PAGE; i < Math.min(choisies.size(), (page + 1) * ReseauHdv.PAR_PAGE); i++) {
            Offre o = choisies.get(i);
            lignes.add(new ReseauHdv.LigneOffre(o.id, objet(o).copy(), o.vendeurNom, o.expireA - now, o.devise, o.prix,
                    RegleHdv.total(o.devise, o.prix, fraisPct), false, u.equals(o.vendeur)));
        }
        List<Integer> cats = new ArrayList<>();
        for (int x : parCat) cats.add(x);
        ReseauHdv.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new ReseauHdv.Page(page, pages, cats, lignes));
    }

    // ------------------------------------------------------------------ actions
    private static void action(ServerPlayer p, ReseauHdv.Action a) {
        if (serveur == null) return;
        if (a.type() == ReseauHdv.FERMER) {
            OUVERTS.remove(p.getUUID());
            return;
        }
        if (!OUVERTS.contains(p.getUUID())) return;
        if (a.type() == ReseauHdv.MES_VENTES) {
            if (!tropTot(DERNIERE_LISTE, p.getUUID(), 120)) mesVentes(p);
            return;
        }
        if (tropTot(DERNIERE_ACTION, p.getUUID(), 300)) return;
        Component non = refus(p);
        if (non != null) {
            p.sendSystemMessage(non.copy().withStyle(ChatFormatting.RED));
            etat(p, false, "refuse", false);
            return;
        }
        resoudre(p);
        switch (a.type()) {
            case ReseauHdv.ACHETER -> acheter(p, a.id(), a.devise(), a.prix());
            case ReseauHdv.VENDRE -> vendre(p, a.slot(), a.devise(), a.prix(), a.duree(), a.nombre(), a.id());
            case ReseauHdv.RETIRER -> retirer(p, a.id());
            case ReseauHdv.RECUPERER -> recuperer(p, a.id());
            default -> {
            }
        }
    }

    /** Réponse à une action : état, message, et « Mes ventes » à jour. */
    private static void repondre(ServerPlayer p, String message, boolean ok) {
        if (p == null || !OUVERTS.contains(p.getUUID())) return;
        etat(p, false, message, ok);
        mesVentes(p);
    }

    // ------------------------------------------------------------------ vendre
    private static void vendre(ServerPlayer p, int slot, int devise, long prix, int duree, int nombre, String attendu) {
        String u = p.getUUID().toString();
        if (slot < 0 || slot >= CASES || (devise != RegleHdv.HERALS && devise != RegleHdv.PB) || duree < 0 || duree >= durees.size()) return;
        ItemStack st = p.getInventory().getItem(slot);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        // la case a changé depuis le clic : rien n'est mis en vente
        if (st.isEmpty() || id == null || !id.toString().equals(attendu) || st.getCount() != nombre) {
            repondre(p, "case_changee", false);
            return;
        }
        int r = RegleHdv.refus(st);
        if (r != RegleHdv.OK) {
            repondre(p, "refus." + r, false);
            return;
        }
        if (mesEnVente(u) >= ventesMax) {
            repondre(p, "trop", false);
            return;
        }
        List<Long> rec = VENTES_RECENTES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        long now = System.currentTimeMillis();
        rec.removeIf(t -> now - t > 600_000L);
        if (rec.size() >= 15) {
            repondre(p, "lent", false);
            return;
        }
        if (prix < 1) {
            repondre(p, "prix_bas", false);
            return;
        }
        if (devise == RegleHdv.HERALS) {
            long hmax = EconomieApi.disponible() ? Math.min(prixHeralsMax, EconomieApi.maxSolde()) : prixHeralsMax;
            if (!EconomieApi.disponible()) {
                repondre(p, "economie", false);
                return;
            }
            if (prix > hmax) {
                repondre(p, "prix_haut", false);
                return;
            }
            mettreEnVente(p, slot, st.copy(), devise, prix, duree);
            return;
        }
        // PB : plafond selon la rareté (contre les PB passés d'un compte à l'autre avec un objet sans valeur)
        int rar = RegleHdv.rarete(st);
        long plafond = plafondsPb.get(Math.max(0, Math.min(plafondsPb.size() - 1, rar + 1)));
        if (prix > plafond || RegleHdv.total(devise, prix, fraisPct) > PB_MAX) {
            repondre(p, "prix_haut", false);
            return;
        }
        if (!Pb.actif()) {
            repondre(p, "pb_ferme", false);
            return;
        }
        // le vendeur doit avoir un compte sur le site (c'est là que ses PB seront versés)
        UUID uuid = p.getUUID();
        if (!MISES_EN_ATTENTE.add(uuid)) return;
        ItemStack modele = st.copy();
        Pb.solde(p.getGameProfile().getName(), rep -> {
            MISES_EN_ATTENTE.remove(uuid);
            ServerPlayer j = serveur == null ? null : serveur.getPlayerList().getPlayer(uuid);
            if (j == null) return;
            if (rep == null || rep.code() != 200) {
                repondre(j, rep != null && rep.code() == 404 ? "pas_de_compte" : "site", false);
                return;
            }
            SOLDES_PB.put(uuid, rep.nombre("solde"));
            // tout est relu : la case doit contenir exactement le même objet qu'au clic
            Component non = refus(j);
            if (non != null || !ItemStack.matches(j.getInventory().getItem(slot), modele) || mesEnVente(uuid.toString()) >= ventesMax) {
                repondre(j, "case_changee", false);
                return;
            }
            mettreEnVente(j, slot, modele, devise, prix, duree);
        });
    }

    /**
     * L'objet de la case quitte l'inventaire et devient une offre : offre notée « en préparation », objet retiré et joueur
     * sauvegardé avec la marque, puis offre « en vente ». Une arme TaCZ est déchargée d'abord (balles rendues).
     */
    private static void mettreEnVente(ServerPlayer p, int slot, ItemStack modele, int devise, long prix, int duree) {
        int dech = HdvTacz.decharger(p, slot);
        if (dech == HdvTacz.REFUS_RECHARGE) {
            repondre(p, "recharge", false);
            return;
        }
        ItemStack st = p.getInventory().getItem(slot);
        // après le déchargement, seule la quantité de munitions a pu changer : même objet, même nombre
        if (st.isEmpty() || st.getItem() != modele.getItem() || st.getCount() != modele.getCount() || RegleHdv.refus(st) != RegleHdv.OK) {
            repondre(p, "case_changee", false);
            return;
        }
        long now = System.currentTimeMillis();
        Offre o = new Offre();
        o.id = nouvelId("hdvo");
        o.vendeur = p.getUUID().toString();
        o.vendeurNom = p.getGameProfile().getName();
        ItemStack copie = st.copy();
        o.objet = texte(copie);
        ResourceLocation rid = ForgeRegistries.ITEMS.getKey(copie.getItem());
        o.objetId = rid == null ? "" : rid.toString();
        o.cles = RegleHdv.cles(copie);
        o.categorie = RegleHdv.categorie(copie);
        o.devise = devise;
        o.prix = prix;
        o.creeA = now;
        o.expireA = now + durees.get(duree) * 3600_000L;
        o.etat = "prepare";
        OFFRES.put(o.id, o);
        if (!sauver()) {
            OFFRES.remove(o.id);
            repondre(p, "erreur", false);
            return;
        }
        // l'objet quitte l'inventaire ; la sauvegarde du joueur porte la marque : un plantage ici ne copie rien
        p.getInventory().setItem(slot, ItemStack.EMPTY);
        p.getInventory().setChanged();
        marquer(p, List.of(o.id), true);
        sauverJoueur(p);
        o.etat = "vente";
        if (sauver()) marquer(p, List.of(o.id), false);
        // si l'écriture a échoué, l'offre reste « en vente » en mémoire et la marque tranchera après un arrêt
        p.inventoryMenu.broadcastChanges();
        VENTES_RECENTES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(now);
        journal("vente " + o.id + " : " + o.vendeurNom + " met en vente " + resume(copie) + " pour " + prix + (devise == RegleHdv.PB ? " PB" : " hérals")
                + ", " + durees.get(duree) + " h" + (dech == HdvTacz.DECHARGEE ? " (arme déchargée)" : ""));
        repondre(p, dech == HdvTacz.DECHARGEE ? "en_vente_decharge" : "en_vente", true);
    }

    // ------------------------------------------------------------------ retirer
    private static void retirer(ServerPlayer p, String id) {
        Offre o = OFFRES.get(id);
        String u = p.getUUID().toString();
        if (o == null || !u.equals(o.vendeur) || !"vente".equals(o.etat)) {
            repondre(p, "plus_dispo", false);
            return;
        }
        if (o.acheteur != null) {
            repondre(p, "en_achat", false);
            return;
        }
        Recup r = recupObjet(o, u, "retire", "", 0);
        OFFRES.remove(o.id);
        RECUPS.add(r);
        if (!sauver()) {
            RECUPS.remove(r);
            OFFRES.put(o.id, o);
            repondre(p, "erreur", false);
            return;
        }
        journal("retrait " + o.id + " : " + o.vendeurNom + " retire " + resume(objet(o)));
        livrer(p, List.of(r));
        repondre(p, "retire", true);
    }

    private static Recup recupObjet(Offre o, String uuid, String motif, String autre, int deviseVue) {
        Recup r = new Recup();
        r.id = nouvelId("hdvr");
        r.uuid = uuid;
        r.genre = "objet";
        r.motif = motif;
        r.objet = o.objet;
        r.autre = autre;
        r.devise = o.devise;
        r.prix = o.prix;
        r.quand = System.currentTimeMillis();
        return r;
    }

    // ------------------------------------------------------------------ acheter
    private static void acheter(ServerPlayer p, String id, int devise, long prixAttendu) {
        Offre o = OFFRES.get(id);
        String u = p.getUUID().toString();
        long now = System.currentTimeMillis();
        if (o == null || !"vente".equals(o.etat) || o.acheteur != null || o.expireA <= now) {
            repondre(p, "plus_dispo", false);
            return;
        }
        if (u.equals(o.vendeur)) {
            repondre(p, "la_tienne", false);
            return;
        }
        if (o.devise != devise || o.prix != prixAttendu) {
            repondre(p, "prix_change", false);
            return;
        }
        for (Achat a : ACHATS.values()) {
            if (u.equals(a.acheteur)) {
                repondre(p, "achat_en_cours", false);
                return;
            }
        }
        long total = RegleHdv.total(o.devise, o.prix, fraisPct);
        Achat a = new Achat();
        a.offre = o.id;
        a.acheteur = u;
        a.acheteurNom = p.getGameProfile().getName();
        a.devise = o.devise;
        a.total = total;
        if (o.devise == RegleHdv.HERALS) {
            if (!EconomieApi.disponible()) {
                repondre(p, "economie", false);
                return;
            }
            if (EconomieApi.solde(p.getUUID()) < total) {
                repondre(p, "solde", false);
                return;
            }
            a.ref = nouvelId("hdvh");
            a.etat = "prepare";
            o.acheteur = u;
            ACHATS.put(a.ref, a);
            if (!sauver()) {
                ACHATS.remove(a.ref);
                o.acheteur = null;
                repondre(p, "erreur", false);
                return;
            }
            if (!EconomieApi.retirer(p.getUUID(), total, "hdv:" + a.ref)) {
                ACHATS.remove(a.ref);
                o.acheteur = null;
                sauver();
                repondre(p, "solde", false);
                return;
            }
            // le débit sur le disque AVANT la conclusion : un plantage ne peut pas donner l'objet sans paiement
            if (!EconomieApi.sauvegarderMaintenant()) {
                EconomieApi.deposer(p.getUUID(), total, "hdv:rembourse:" + a.ref);
                ACHATS.remove(a.ref);
                o.acheteur = null;
                sauver();
                repondre(p, "erreur", false);
                return;
            }
            conclure(a, true, "");
            return;
        }
        // PB : débités par le site, référence notée sur le disque AVANT l'appel
        if (!Pb.actif()) {
            repondre(p, "pb_ferme", false);
            return;
        }
        a.ref = nouvelId("hdvp");
        a.etat = "envoye";
        a.envoyeA = now;
        o.acheteur = u;
        ACHATS.put(a.ref, a);
        if (!sauver()) {
            ACHATS.remove(a.ref);
            o.acheteur = null;
            repondre(p, "erreur", false);
            return;
        }
        journal("achat " + a.ref + " : " + a.acheteurNom + " achète l'offre " + o.id + " de " + o.vendeurNom + " pour " + total + " PB (débit demandé)");
        repondre(p, "attente_site", true);
        EN_VOL.add(a.ref);
        Pb.debiter(a.ref, a.acheteurNom, total, "hdv", rep -> {
            EN_VOL.remove(a.ref);
            reponseDebit(a, rep);
        });
    }

    /** Refus clair du site : il a lu la demande et l'a refusée (solde, compte inconnu, requête invalide). */
    private static boolean refusClair(Pb.Reponse r) {
        return r != null && !r.ok() && (r.code() == 404 || r.code() == 409 || r.code() == 422);
    }

    private static void reponseDebit(Achat a, Pb.Reponse r) {
        if (ACHATS.get(a.ref) != a) return;
        if (r != null && r.code() == 200 && r.ok()) {
            conclure(a, true, "");
        } else if (refusClair(r)) {
            conclure(a, false, "solde_insuffisant".equals(r.raison()) ? "solde" : "refuse_site");
        } else {
            // site muet ou en erreur : on ne sait pas si les PB sont partis ; l'offre reste réservée
            a.etat = "verifier";
            sauver();
            journal("achat " + a.ref + " : site injoignable, achat en attente de sa réponse");
            repondre(joueur(a.acheteur), "site_attente", false);
        }
    }

    /**
     * Fin d'un achat. Payé : l'offre disparaît, l'objet attend l'acheteur (remis tout de suite s'il a la place), le
     * vendeur a ses hérals à récupérer ou son versement de PB en route — en UNE écriture. Pas payé : l'offre est libérée.
     */
    private static void conclure(Achat a, boolean paye, String raison) {
        if (ACHATS.get(a.ref) != a) return;
        Offre o = OFFRES.get(a.offre);
        String etatAvant = a.etat;
        if (!paye) {
            ACHATS.remove(a.ref);
            boolean retireOffre = false;
            if (o != null) {
                o.acheteur = null;
                if (o.efface) {
                    OFFRES.remove(o.id);
                    retireOffre = true;
                }
            }
            if (!sauver()) {
                ACHATS.put(a.ref, a);
                if (o != null) {
                    o.acheteur = a.acheteur;
                    if (retireOffre) OFFRES.put(o.id, o);
                }
                a.etat = "refuse";
                return;
            }
            journal("achat " + a.ref + " annulé (" + (raison.isEmpty() ? "pas payé" : raison) + ") : offre " + a.offre + " libérée"
                    + (retireOffre ? " puis retirée (vendeur remis à zéro, objet non rendu)" : ""));
            ServerPlayer p = joueur(a.acheteur);
            if (p != null) {
                if (a.devise == RegleHdv.PB) rafraichirSoldePb(p, true);
                repondre(p, raison.isEmpty() ? "refuse_site" : raison, false);
            }
            return;
        }
        if (o == null) {
            // ne doit pas arriver (une offre réservée ne part jamais) : le paiement est noté pour le staff
            ACHATS.remove(a.ref);
            sauver();
            LOG.error("[grades] hôtel des ventes : achat {} payé mais offre {} introuvable, à régler à la main", a.ref, a.offre);
            journal("ERREUR achat " + a.ref + " payé (" + a.total + (a.devise == RegleHdv.PB ? " PB" : " hérals") + " par " + a.acheteurNom
                    + ") mais offre " + a.offre + " introuvable : à régler à la main");
            LienDiscord.evenement("⚠️ Hôtel des ventes : achat " + a.ref + " payé par " + a.acheteurNom + " mais offre introuvable, à régler à la main.");
            return;
        }
        long now = System.currentTimeMillis();
        List<Recup> nouvelles = new ArrayList<>();
        Recup pourAcheteur = null;
        if (!a.efface) {
            pourAcheteur = recupObjet(o, a.acheteur, "achete", o.vendeurNom, 0);
            nouvelles.add(pourAcheteur);
        }
        Versement v = null;
        Vente vente = null;
        if (o.devise == RegleHdv.HERALS) {
            if (!o.efface) {
                Recup h = new Recup();
                h.id = nouvelId("hdvr");
                h.uuid = o.vendeur;
                h.genre = "herals";
                h.motif = "vendu";
                h.objet = o.objet;
                h.autre = a.acheteurNom;
                h.devise = RegleHdv.HERALS;
                h.prix = o.prix;
                h.montant = o.prix;
                h.quand = now;
                nouvelles.add(h);
            }
        } else {
            v = new Versement();
            v.ref = a.ref + "-v";
            v.vendeur = o.vendeur;
            v.vendeurNom = o.vendeurNom;
            v.montant = o.prix;
            v.etat = "a_faire";
            vente = new Vente();
            vente.ref = v.ref;
            vente.vendeur = o.vendeur;
            vente.objet = o.objet;
            vente.acheteurNom = a.acheteurNom;
            vente.prix = o.prix;
            vente.quand = now;
        }
        OFFRES.remove(o.id);
        ACHATS.remove(a.ref);
        RECUPS.addAll(nouvelles);
        if (v != null) VERSEMENTS.put(v.ref, v);
        if (vente != null) VENTES.add(vente);
        if (!sauver()) {
            // rien n'a changé sur le disque : la décision est gardée, reprise par reprendre()
            OFFRES.put(o.id, o);
            RECUPS.removeAll(nouvelles);
            if (v != null) VERSEMENTS.remove(v.ref);
            if (vente != null) VENTES.remove(vente);
            ACHATS.put(a.ref, a);
            a.etat = "passe";
            LOG.error("[grades] hôtel des ventes : achat {} payé, conclusion impossible à écrire (avant : {}), reprise plus tard", a.ref, etatAvant);
            return;
        }
        journal("achat " + a.ref + " conclu : " + a.acheteurNom + " a payé " + a.total + (a.devise == RegleHdv.PB ? " PB" : " hérals") + " pour "
                + resume(objet(o)) + " (offre " + o.id + ") ; " + o.vendeurNom + " reçoit " + o.prix + (o.devise == RegleHdv.PB ? " PB" : " hérals")
                + (a.efface ? " ; acheteur remis à zéro : objet non remis" : "") + (o.efface ? " ; vendeur remis à zéro : hérals non versés" : ""));
        ServerPlayer acheteur = joueur(a.acheteur);
        if (acheteur != null) {
            acheteur.sendSystemMessage(msg("achete", "Achat réussi : %s.", objet(o).getHoverName()).withStyle(ChatFormatting.GREEN));
            if (pourAcheteur != null) livrer(acheteur, List.of(pourAcheteur));
            if (a.devise == RegleHdv.PB) rafraichirSoldePb(acheteur, true);
            repondre(acheteur, "achete", true);
        }
        ServerPlayer vendeur = joueur(o.vendeur);
        if (vendeur != null) {
            vendeur.sendSystemMessage(msg("vendu", "Hôtel des ventes : %s vendu à %s.", objet(o).getHoverName(), a.acheteurNom).withStyle(ChatFormatting.GOLD));
            repondre(vendeur, "", true);
        }
        if (v != null) verser(v);
    }

    /** Versement du prix d'une vente en PB au vendeur (référence fixe : le site ne la joue qu'une fois). */
    private static void verser(Versement v) {
        if (!"a_faire".equals(v.etat) || !EN_VOL.add(v.ref)) return;
        Pb.crediter(v.ref, v.vendeurNom, v.montant, "hdv", r -> {
            EN_VOL.remove(v.ref);
            if (VERSEMENTS.get(v.ref) != v) return;
            if (r != null && r.code() == 200 && r.ok()) {
                VERSEMENTS.remove(v.ref);
                for (Vente x : VENTES) if (v.ref.equals(x.ref)) x.verse = true;
                if (!sauver()) {
                    VERSEMENTS.put(v.ref, v);   // rejoué plus tard : le site répondra « déjà fait »
                    return;
                }
                journal("versement " + v.ref + " : " + v.montant + " PB versés à " + v.vendeurNom);
                ServerPlayer p = joueur(v.vendeur);
                if (p != null) {
                    rafraichirSoldePb(p, true);
                    repondre(p, "", true);
                }
            } else if (refusClair(r)) {
                v.etat = "bloque";
                sauver();
                LOG.error("[grades] hôtel des ventes : versement {} de {} PB à {} refusé par le site ({}), à régler à la main", v.ref, v.montant,
                        v.vendeurNom, r.raison());
                journal("ERREUR versement " + v.ref + " : " + v.montant + " PB à " + v.vendeurNom + " refusé par le site (" + r.raison() + "), à régler à la main");
                LienDiscord.evenement("⚠️ Hôtel des ventes : " + v.montant + " PB n'ont pas pu être versés à " + v.vendeurNom + " (" + r.raison()
                        + ", réf. " + v.ref + "), à régler à la main.");
            }
            // site muet : rejoué toutes les 30 s
        });
    }

    // ------------------------------------------------------------------ récupérer
    private static void recuperer(ServerPlayer p, String id) {
        String u = p.getUUID().toString();
        List<Recup> choix = new ArrayList<>();
        for (Recup r : RECUPS) if (u.equals(r.uuid) && !r.remise && !r.paiement && (id.isEmpty() || id.equals(r.id))) choix.add(r);
        if (choix.isEmpty()) {
            repondre(p, "rien", false);
            return;
        }
        int avant = choix.size();
        int remis = verserHerals(p, choix) + livrer(p, choix);
        repondre(p, remis == 0 ? "place" : remis < avant ? "place_reste" : "recupere", remis > 0);
    }

    /** Hérals des ventes : notés « en cours » sur le disque, versés (motif {@code hdv:recup:<id>}), compte écrit, puis lignes retirées. */
    private static int verserHerals(ServerPlayer p, List<Recup> choix) {
        List<Recup> h = new ArrayList<>();
        for (Recup r : choix) if ("herals".equals(r.genre)) h.add(r);
        if (h.isEmpty() || !EconomieApi.disponible()) return 0;
        // plafond du compte : ce qui dépasserait attend (rien n'est perdu)
        long place = EconomieApi.maxSolde() - EconomieApi.solde(p.getUUID()), somme = 0;
        for (Iterator<Recup> it = h.iterator(); it.hasNext(); ) {
            Recup r = it.next();
            if (somme + r.montant > place) it.remove();
            else somme += r.montant;
        }
        if (h.isEmpty()) {
            p.sendSystemMessage(msg("plafond", "Ton compte de hérals est plein : ces hérals t'attendent.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        for (Recup r : h) r.paiement = true;
        if (!sauver()) {
            for (Recup r : h) r.paiement = false;
            return 0;
        }
        long total = 0;
        for (Recup r : h) {
            EconomieApi.deposer(p.getUUID(), r.montant, "hdv:recup:" + r.id);
            total += r.montant;
        }
        EconomieApi.sauvegarderMaintenant();
        RECUPS.removeAll(h);
        if (!sauver()) {
            // lignes gardées « en cours » : au redémarrage, le journal du compte dira qu'elles sont versées
            RECUPS.addAll(h);
        }
        journal("hérals remis à " + p.getGameProfile().getName() + " : " + total + " (" + h.size() + " vente(s))");
        p.sendSystemMessage(msg("herals", "%s hérals récupérés.", EconomieApi.nombre(total)).withStyle(ChatFormatting.GREEN));
        return h.size();
    }

    /**
     * Remet des objets en attente, au spawn, vivant, dans des cases VIDES seulement (jamais par terre). Lignes notées « en
     * cours » sur le disque, objets donnés avec une marque, joueur sauvegardé, puis lignes retirées : jamais deux fois.
     */
    private static int livrer(ServerPlayer p, List<Recup> choix) {
        if (!p.isAlive() || p.isSpectator() || !WarpsServeur.auSpawn(p) || Restrictions.enMatch(p)) return 0;
        int libres = 0;
        for (int i = 0; i < CASES; i++) if (p.getInventory().items.get(i).isEmpty()) libres++;
        List<Recup> l = new ArrayList<>();
        for (Recup r : choix) {
            if (!"objet".equals(r.genre) || r.remise || l.size() >= libres) continue;
            if (objet(r).isEmpty()) continue;
            l.add(r);
        }
        if (l.isEmpty()) return 0;
        for (Recup r : l) r.remise = true;
        if (!sauver()) {
            for (Recup r : l) r.remise = false;
            return 0;
        }
        List<String> ids = new ArrayList<>();
        List<Recup> restes = new ArrayList<>();
        for (Recup r : l) {
            ItemStack st = objet(r).copy();
            p.getInventory().add(st);
            ids.add(r.id);
            if (!st.isEmpty()) {
                // ne devrait pas arriver (cases vides comptées) : le reste attend dans une nouvelle ligne
                Recup reste = new Recup();
                reste.id = nouvelId("hdvr");
                reste.uuid = r.uuid;
                reste.genre = "objet";
                reste.motif = r.motif;
                reste.objet = texte(st);
                reste.autre = r.autre;
                reste.devise = r.devise;
                reste.prix = r.prix;
                reste.quand = r.quand;
                restes.add(reste);
            }
        }
        p.getInventory().setChanged();
        p.inventoryMenu.broadcastChanges();
        marquer(p, ids, true);
        sauverJoueur(p);
        RECUPS.removeAll(l);
        RECUPS.addAll(restes);
        if (sauver()) {
            marquer(p, ids, false);
        } else {
            // lignes gardées « en cours » en mémoire : la marque tranchera (aucune remise en double)
            RECUPS.removeAll(restes);
            RECUPS.addAll(l);
        }
        StringBuilder b = new StringBuilder();
        for (Recup r : l) b.append(b.length() == 0 ? "" : ", ").append(resume(objet(r))).append(" [").append(r.motif).append(']');
        journal("objets remis à " + p.getGameProfile().getName() + " : " + b);
        return l.size() - restes.size();
    }

    /** À l'ouverture et à la connexion au spawn : ce qui attend le joueur (objets achetés) lui est remis s'il a la place. */
    private static void livrerEnAttente(ServerPlayer p, boolean message) {
        String u = p.getUUID().toString();
        List<Recup> achats = new ArrayList<>();
        int attente = 0;
        for (Recup r : RECUPS) {
            if (!u.equals(r.uuid)) continue;
            attente++;
            if ("achete".equals(r.motif)) achats.add(r);
        }
        if (attente == 0) return;
        if (!achats.isEmpty() && WarpsServeur.auSpawn(p)) livrer(p, achats);
        if (message && aRecuperer(u) > 0) {
            p.sendSystemMessage(msg("attente", "Hôtel des ventes : des objets ou des hérals t'attendent (/hdv, onglet « Mes ventes »).").withStyle(ChatFormatting.YELLOW));
        }
    }

    // ------------------------------------------------------------------ mes ventes
    private static void mesVentes(ServerPlayer p) {
        String u = p.getUUID().toString();
        long now = System.currentTimeMillis();
        List<ReseauHdv.LigneMv> l = new ArrayList<>();
        for (Recup r : RECUPS) {
            if (!u.equals(r.uuid)) continue;
            int genre = "herals".equals(r.genre) ? ReseauHdv.VENDU_HERALS : switch (r.motif == null ? "" : r.motif) {
                case "expire" -> ReseauHdv.EXPIRE;
                case "achete" -> ReseauHdv.ACHETE;
                default -> ReseauHdv.RETIRE;
            };
            ItemStack vu = objet(r);
            l.add(new ReseauHdv.LigneMv(genre, r.id, vu.copy(), r.autre == null ? "" : r.autre, now - r.quand, r.devise,
                    "herals".equals(r.genre) ? r.montant : r.prix, !r.remise && !r.paiement));
        }
        for (Offre o : OFFRES.values()) {
            if (!u.equals(o.vendeur) || !"vente".equals(o.etat)) continue;
            boolean res = o.acheteur != null;
            l.add(new ReseauHdv.LigneMv(res ? ReseauHdv.RESERVEE : ReseauHdv.EN_VENTE, o.id, objet(o).copy(), "", o.expireA - now, o.devise, o.prix, !res));
        }
        for (Vente v : VENTES) {
            if (!u.equals(v.vendeur)) continue;
            l.add(new ReseauHdv.LigneMv(v.verse ? ReseauHdv.VENDU_PB_VERSE : ReseauHdv.VENDU_PB_ATTENTE, v.ref, objet(v).copy(),
                    v.acheteurNom == null ? "" : v.acheteurNom, now - v.quand, RegleHdv.PB, v.prix, false));
        }
        if (l.size() > ReseauHdv.MES_MAX) l = new ArrayList<>(l.subList(0, ReseauHdv.MES_MAX));
        ReseauHdv.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new ReseauHdv.MesVentes(l));
    }

    // ------------------------------------------------------------------ chaque tick
    static void tick() {
        if (serveur == null || bloque) return;
        ticks++;
        if (!repris && EconomieApi.disponible()) {
            repris = true;
            reprendreHerals();
        }
        if (enRetard && ticks % 200 == 100) sauver();
        if (ticks % 200 == 0) {
            relireReglages();
            expirer();
            nettoyerHistorique();
        }
        if (ticks % 600 == 0) reprendre();
        if (ticks % 100 == 0 && !OUVERTS.isEmpty()) {
            OUVERTS.removeIf(u -> {
                ServerPlayer p = serveur.getPlayerList().getPlayer(u);
                return p == null || refus(p) != null;
            });
        }
    }

    /** Ventes arrivées au bout de leur durée (jamais pendant un achat) : l'objet revient au vendeur, dans « Mes ventes ». */
    private static void expirer() {
        long now = System.currentTimeMillis();
        List<Offre> finies = new ArrayList<>();
        for (Offre o : OFFRES.values()) if ("vente".equals(o.etat) && o.acheteur == null && o.expireA <= now) finies.add(o);
        if (finies.isEmpty()) return;
        List<Recup> nouvelles = new ArrayList<>();
        for (Offre o : finies) {
            OFFRES.remove(o.id);
            if (!o.efface) nouvelles.add(recupObjet(o, o.vendeur, "expire", "", 0));
        }
        RECUPS.addAll(nouvelles);
        if (!sauver()) {
            RECUPS.removeAll(nouvelles);
            for (Offre o : finies) OFFRES.put(o.id, o);
            return;
        }
        for (Offre o : finies) {
            journal("expirée " + o.id + " : " + resume(objet(o)) + " de " + o.vendeurNom + " revient dans « Mes ventes »");
            ServerPlayer p = joueur(o.vendeur);
            if (p != null) p.sendSystemMessage(msg("expire", "Hôtel des ventes : personne n'a acheté %s, il t'attend dans « Mes ventes ».",
                    objet(o).getHoverName()).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void nettoyerHistorique() {
        long now = System.currentTimeMillis();
        Map<String, Integer> parVendeur = new HashMap<>();
        boolean change = false;
        for (int i = VENTES.size() - 1; i >= 0; i--) {
            Vente v = VENTES.get(i);
            int n = parVendeur.merge(v.vendeur, 1, Integer::sum);
            // une vente pas encore versée reste visible
            if (v.verse && (now - v.quand > HISTORIQUE_MS || n > HISTORIQUE_PAR_VENDEUR)) {
                VENTES.remove(i);
                change = true;
            }
        }
        if (change) sauver();
    }

    /** Toutes les 30 s : achats en attente du site, conclusions pas encore écrites, versements de PB. */
    private static void reprendre() {
        long now = System.currentTimeMillis();
        for (Achat a : new ArrayList<>(ACHATS.values())) {
            if (EN_VOL.contains(a.ref)) continue;
            if ("passe".equals(a.etat) || "refuse".equals(a.etat)) {
                conclure(a, "passe".equals(a.etat), "");
            } else if ("verifier".equals(a.etat)) {
                EN_VOL.add(a.ref);
                Pb.statut(a.ref, r -> {
                    EN_VOL.remove(a.ref);
                    if (ACHATS.get(a.ref) != a || r == null || r.code() != 200) return;
                    boolean fait = r.corps().has("fait") && r.corps().get("fait").getAsBoolean();
                    if (fait) conclure(a, true, "");
                    else if (System.currentTimeMillis() - a.envoyeA >= ATTENTE_STATUT_MS) conclure(a, false, "refuse_site");
                });
            }
        }
        for (Versement v : new ArrayList<>(VERSEMENTS.values())) if ("a_faire".equals(v.etat)) verser(v);
    }

    /**
     * Au démarrage (économie prête) : achats en hérals et versements de hérals interrompus. Le journal du compte (motif
     * {@code hdv:<réf>} / {@code hdv:recup:<id>}, écrit sur le disque avant la suite) dit s'ils avaient eu lieu.
     */
    private static void reprendreHerals() {
        for (Achat a : new ArrayList<>(ACHATS.values())) {
            if (a.devise != RegleHdv.HERALS || !"prepare".equals(a.etat)) continue;
            UUID u = UUID.fromString(a.acheteur);
            boolean debite = false, rembourse = false;
            for (Transaction t : EconomieApi.journal(u, 50)) {
                if (t.motif().equals("hdv:" + a.ref) && t.delta() < 0) debite = true;
                if (t.motif().equals("hdv:rembourse:" + a.ref)) rembourse = true;
            }
            journal("reprise : achat en hérals " + a.ref + " " + (debite && !rembourse ? "payé avant l'arrêt, conclu" : "pas payé, offre libérée"));
            conclure(a, debite && !rembourse, debite && !rembourse ? "" : "erreur");
        }
        boolean change = false;
        for (Iterator<Recup> it = RECUPS.iterator(); it.hasNext(); ) {
            Recup r = it.next();
            if (!r.paiement) continue;
            boolean verse = false;
            try {
                for (Transaction t : EconomieApi.journal(UUID.fromString(r.uuid), 50)) if (t.motif().equals("hdv:recup:" + r.id)) verse = true;
            } catch (IllegalArgumentException ignore) {
            }
            if (verse) {
                it.remove();
                journal("reprise : hérals " + r.id + " déjà versés avant l'arrêt");
            } else {
                r.paiement = false;
            }
            change = true;
        }
        if (change) sauver();
    }

    // ------------------------------------------------------------------ connexion
    static void connexion(ServerPlayer p) {
        if (serveur == null || bloque) return;
        resoudre(p);
        if (WarpsServeur.auSpawn(p)) livrerEnAttente(p, true);
        else if (aRecuperer(p.getUUID().toString()) > 0) {
            p.sendSystemMessage(msg("attente_spawn", "Hôtel des ventes : des objets ou des hérals t'attendent, à récupérer au spawn (/hdv).")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    static void deconnexion(UUID u) {
        OUVERTS.remove(u);
        DERNIERE_ACTION.remove(u);
        DERNIERE_LISTE.remove(u);
        DERNIER_SOLDE.remove(u);
        SOLDES_PB.remove(u);
    }

    // ------------------------------------------------------------------ remise à zéro (/wipe de HeraliZ Quêtes)
    /**
     * Remise à zéro d'un joueur ({@link HdvApi#wipe}) : ses ventes en cours (pas pendant un achat), ce qui l'attend et son
     * historique partent ; les objets sont rendus à l'appelant (qui ne garde que les objets à vie). Une vente en cours
     * d'achat est marquée : si l'achat échoue, l'objet n'est pas rendu. Un achat qu'il a en cours est marqué : payé,
     * l'objet ne lui est pas remis. Les versements de PB ne sont pas touchés (compte du site).
     */
    static List<ItemStack> wipe(UUID id) {
        List<ItemStack> r = new ArrayList<>();
        if (fichier == null || bloque) return r;
        String u = id.toString();
        List<Offre> offres = new ArrayList<>();
        List<Recup> recups = new ArrayList<>();
        List<Vente> ventes = new ArrayList<>();
        List<Offre> marquees = new ArrayList<>();
        List<Achat> achats = new ArrayList<>();
        for (Offre o : OFFRES.values()) {
            if (!u.equals(o.vendeur)) continue;
            if (o.acheteur != null) marquees.add(o);
            else offres.add(o);
        }
        for (Recup x : RECUPS) if (u.equals(x.uuid) && !x.remise && !x.paiement) recups.add(x);
        for (Vente v : VENTES) if (u.equals(v.vendeur)) ventes.add(v);
        for (Achat a : ACHATS.values()) if (u.equals(a.acheteur)) achats.add(a);
        if (offres.isEmpty() && recups.isEmpty() && ventes.isEmpty() && marquees.isEmpty() && achats.isEmpty()) return r;
        for (Offre o : offres) OFFRES.remove(o.id);
        RECUPS.removeAll(recups);
        VENTES.removeAll(ventes);
        for (Offre o : marquees) o.efface = true;
        for (Achat a : achats) a.efface = true;
        if (!sauver()) {
            for (Offre o : offres) OFFRES.put(o.id, o);
            RECUPS.addAll(recups);
            VENTES.addAll(ventes);
            for (Offre o : marquees) o.efface = false;
            for (Achat a : achats) a.efface = false;
            LOG.error("[grades] hôtel des ventes : /wipe de {} impossible à écrire, ventes gardées", u);
            return r;
        }
        StringBuilder b = new StringBuilder();
        for (Offre o : offres) {
            r.add(objet(o).copy());
            b.append(resume(objet(o))).append(" ; ");
        }
        for (Recup x : recups) if ("objet".equals(x.genre)) {
            r.add(objet(x).copy());
            b.append(resume(objet(x))).append(" ; ");
        }
        journal("wipe " + u + " : ventes et objets en attente retirés : " + b + recups.size() + " ligne(s), " + marquees.size()
                + " vente(s) en cours d'achat marquée(s), " + achats.size() + " achat(s) marqué(s)");
        return r;
    }
}
