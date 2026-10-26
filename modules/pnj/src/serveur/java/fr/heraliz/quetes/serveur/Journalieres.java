package fr.heraliz.quetes.serveur;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Quêtes journalières (26/10, demande du propriétaire) : système à part des quêtes de Varga. Règles et chiffres dans
 * {@code config/heraliz_quetes/journalieres.json} (écrit par {@code outils/quetes_journalieres.py}, relu par /quete
 * recharger).
 * <ul>
 *   <li>Chaque jour (minuit, heure de Paris), le joueur choisit UNE difficulté, définitive jusqu'à minuit, puis reçoit 3
 *   propositions tirées au hasard (familles différentes, une seule salle à carte) : il en accepte une, les autres
 *   disparaissent. Jamais de nouveau tirage dans la journée ; sans choix, pas de quête ce jour-là.</li>
 *   <li>Quête réussie : un gain à tirer (table de sa difficulté, chances affichées), gardé après minuit tant qu'il n'est
 *   pas tiré. Abandon (définitif) ou minuit avant la fin : rien.</li>
 *   <li>Tout est décidé ici, côté serveur : le joueur ne fait que choisir. Objectifs comptés sur des événements du serveur
 *   (vrais kills de zombies de HeraliZ Loot, première ouverture d'un conteneur rempli, extraction, vente à un marchand,
 *   recycleur, distance à pied, kill d'un joueur armé) ; jamais le même conteneur ni la même victime deux fois.</li>
 *   <li>État dans les données persistantes du joueur ({@code PlayerPersisted.heraliz_jour}) : écrit dans le même fichier
 *   que son inventaire. Le tirage est noté AVANT la remise (et le joueur sauvegardé), puis la remise efface la note dans
 *   la même sauvegarde que les objets : ni perte ni double, même après un plantage ou une déconnexion ; une clé de
 *   caisse passe par {@code ClesApi.crediterUneFois} (identifiant unique de la remise).</li>
 * </ul>
 * Journal : {@code heraliz_quetes/journalieres.log} à la racine du serveur. Fil serveur seulement.
 */
final class Journalieres {
    private Journalieres() {
    }

    private static final Logger LOG = LogUtils.getLogger();
    static final String CLE = "heraliz_jour";
    /** États du jour. */
    static final int CHOIX = 0, PROPOSITIONS = 1, EN_COURS = 2, REUSSIE = 3, FINIE = 4, ABANDON = 5;
    static final List<String> ORDRE = List.of("facile", "moyenne", "difficile");

    record Quete(String id, String famille, String type, int nombre, JsonObject p) {
        String s(String cle) {
            return p.has(cle) ? p.get(cle).getAsString() : "";
        }

        int i(String cle) {
            return p.has(cle) ? p.get(cle).getAsInt() : 0;
        }
    }

    record Recompense(String id, double chance, String objet, String nbt, int nombre, String cle, String fragment) {
    }

    record Diff(String id, int duree, List<Quete> quetes, List<Recompense> recompenses) {
        @Nullable
        Quete quete(String qid) {
            for (Quete q : quetes) if (q.id().equals(qid)) return q;
            return null;
        }

        @Nullable
        Recompense recompense(String rid) {
            for (Recompense r : recompenses) if (r.id().equals(rid)) return r;
            return null;
        }
    }

    private static final Map<String, Diff> DIFFS = new LinkedHashMap<>();
    /** Hasard des propositions et des tirages : indépendant du monde et des joueurs (rien de prévisible). */
    private static final java.util.Random HASARD = new java.security.SecureRandom();
    private static ZoneId zone = ZoneId.of("Europe/Paris");
    private static MinecraftServer serveur;

    // ------------------------------------------------------------------ réglages
    static void demarrer(MinecraftServer s) {
        serveur = s;
        charger();
        brancherLoot();
    }

    static void arreter() {
        serveur = null;
        ACTIFS.clear();
        DERNIERE_POS.clear();
    }

    /** Relit le fichier ; illisible : les anciennes règles restent. */
    static boolean charger() {
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_quetes").resolve("journalieres.json");
        if (!Files.exists(f)) {
            LOG.warn("[heraliz_quetes] {} absent : pas de quêtes du jour", f);
            return false;
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Diff> l = new LinkedHashMap<>();
            for (String d : ORDRE) {
                JsonObject od = o.getAsJsonObject("difficultes").getAsJsonObject(d);
                List<Quete> qs = new ArrayList<>();
                for (JsonElement e : od.getAsJsonArray("quetes")) {
                    JsonObject q = e.getAsJsonObject();
                    qs.add(new Quete(q.get("id").getAsString(), q.get("famille").getAsString(), q.get("type").getAsString(),
                            Math.max(1, q.get("nombre").getAsInt()), q));
                }
                List<Recompense> rs = new ArrayList<>();
                double total = 0;
                for (JsonElement e : od.getAsJsonArray("recompenses")) {
                    JsonObject r = e.getAsJsonObject();
                    Recompense rec = new Recompense(r.get("id").getAsString(), r.get("chance").getAsDouble(),
                            r.has("objet") ? r.get("objet").getAsString() : "", r.has("nbt") ? r.get("nbt").getAsString() : "",
                            r.has("nombre") ? Math.max(1, r.get("nombre").getAsInt()) : 1, r.has("cle") ? r.get("cle").getAsString() : "",
                            r.has("fragment") ? r.get("fragment").getAsString() : "");
                    if (!rec.objet().isEmpty() && !ForgeRegistries.ITEMS.containsKey(ResourceLocation.tryParse(rec.objet()))) {
                        throw new IllegalStateException("objet inconnu " + rec.objet());
                    }
                    rs.add(rec);
                    total += rec.chance();
                }
                if (Math.abs(total - 100) > 0.01) throw new IllegalStateException(d + " : chances = " + total + " %");
                if (qs.size() < 3) throw new IllegalStateException(d + " : moins de 3 quêtes");
                l.put(d, new Diff(d, od.has("duree_minutes") ? od.get("duree_minutes").getAsInt() : 0, List.copyOf(qs), List.copyOf(rs)));
            }
            if (o.has("fuseau")) zone = ZoneId.of(o.get("fuseau").getAsString());
            DIFFS.clear();
            DIFFS.putAll(l);
            LOG.info("[heraliz_quetes] quêtes du jour : {}", l.keySet());
            return true;
        } catch (Exception ex) {
            LOG.error("[heraliz_quetes] {} illisible ({}) : quêtes du jour inchangées", f, ex.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ jour (heure de Paris)
    /** Jour en cours ; jamais avant le dernier jour vu (horloge reculée : on ne rouvre pas une journée finie). */
    static String jour(CompoundTag t) {
        String auj = LocalDate.now(zone).toString();
        String vu = t.getString("jour");
        return !vu.isEmpty() && vu.compareTo(auj) > 0 ? vu : auj;
    }

    static long resteMs() {
        ZonedDateTime n = ZonedDateTime.now(zone);
        ZonedDateTime minuit = n.toLocalDate().plusDays(1).atStartOfDay(zone);
        return Math.max(0, Duration.between(n, minuit).toMillis());
    }

    // ------------------------------------------------------------------ état du joueur (données persistantes)
    static CompoundTag etat(ServerPlayer p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) pd.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag per = pd.getCompound(Player.PERSISTED_NBT_TAG);
        if (!per.contains(CLE, Tag.TAG_COMPOUND)) per.put(CLE, new CompoundTag());
        CompoundTag t = per.getCompound(CLE);
        nouveauJour(p, t);
        return t;
    }

    /** Minuit passé : la journée d'avant est close (quête pas finie = échec), les gains pas encore tirés restent. */
    private static void nouveauJour(ServerPlayer p, CompoundTag t) {
        String j = jour(t);
        if (j.equals(t.getString("jour"))) return;
        if (!t.getString("jour").isEmpty() && t.getInt("etat") == EN_COURS) {
            journal(p, "échec (minuit) " + t.getString("diff") + " " + t.getString("quete") + " " + t.getInt("fait"));
        }
        t.putString("jour", j);
        t.putInt("etat", CHOIX);
        for (String k : List.of("diff", "quete", "resultat")) t.putString(k, "");
        t.putInt("fait", 0);
        t.remove("props");
        t.remove("vus");
        t.remove("victimes");
        ACTIFS.remove(p.getUUID());
    }

    // ------------------------------------------------------------------ quête suivie (mémoire, pour les événements)
    private record Actif(Diff diff, Quete quete) {
    }

    private static final Map<UUID, Actif> ACTIFS = new HashMap<>();

    @Nullable
    private static Actif actif(ServerPlayer p) {
        if (serveur == null) return null;
        Actif a = ACTIFS.get(p.getUUID());
        if (a == null) return null;
        CompoundTag t = etat(p);   // minuit passé entre-temps : la quête n'est plus active
        return t.getInt("etat") == EN_COURS ? ACTIFS.get(p.getUUID()) : null;
    }

    private static void rafraichirActif(ServerPlayer p, CompoundTag t) {
        Diff d = DIFFS.get(t.getString("diff"));
        Quete q = d == null ? null : d.quete(t.getString("quete"));
        if (t.getInt("etat") == EN_COURS && q != null) ACTIFS.put(p.getUUID(), new Actif(d, q));
        else ACTIFS.remove(p.getUUID());
    }

    static void connexion(ServerPlayer p) {
        if (serveur == null) return;
        CompoundTag t = etat(p);
        rafraichirActif(p, t);
        // tirage fait mais pas encore remis (déconnexion, plantage) : remis 2 s après l'arrivée
        if (t.contains("remise", Tag.TAG_COMPOUND)) EN_ATTENTE.put(p.getUUID(), serveur.getTickCount() + 40);
    }

    static void deconnexion(UUID u) {
        ACTIFS.remove(u);
        DERNIERE_POS.remove(u);
        OUVERTS.remove(u);
        EN_ATTENTE.remove(u);
    }

    // ------------------------------------------------------------------ actions du joueur
    static void action(ServerPlayer p, Reseau.JourAction a) {
        if (serveur == null || DIFFS.isEmpty()) return;
        CompoundTag t = etat(p);
        switch (a.type()) {
            case 0 -> {
            }
            case 1 -> choisirDifficulte(p, t, a.cle());
            case 2 -> accepter(p, t, a.cle());
            case 3 -> abandonner(p, t);
            case 4 -> tirer(p, t);
            case 5 -> livrer(p, t);
            default -> {
                return;
            }
        }
        envoyer(p, a.type() == 0);
    }

    private static void choisirDifficulte(ServerPlayer p, CompoundTag t, String d) {
        if (t.getInt("etat") != CHOIX || !DIFFS.containsKey(d)) return;
        Diff diff = DIFFS.get(d);
        List<Quete> props = tirerPropositions(diff, p);
        ListTag l = new ListTag();
        for (Quete q : props) l.add(StringTag.valueOf(q.id()));
        t.putString("diff", d);
        t.put("props", l);
        t.putInt("etat", PROPOSITIONS);
        Ponts.sauverJoueur(p);   // choix définitif : écrit tout de suite (un plantage ne permet pas de rechoisir)
        journal(p, "difficulté " + d + " : " + props.stream().map(Quete::id).toList());
    }

    /** 3 quêtes de 3 familles différentes, au plus une salle à carte (elle demande une carte d'accès). */
    static List<Quete> tirerPropositions(Diff d, ServerPlayer p) {
        List<Quete> melange = new ArrayList<>(d.quetes());
        Collections.shuffle(melange, HASARD);
        List<Quete> out = new ArrayList<>();
        List<String> familles = new ArrayList<>();
        boolean salle = false;
        for (Quete q : melange) {
            if (out.size() == 3) break;
            if (familles.contains(q.famille()) || (salle && q.type().equals("salle"))) continue;
            out.add(q);
            familles.add(q.famille());
            salle |= q.type().equals("salle");
        }
        for (Quete q : melange) if (out.size() < 3 && !out.contains(q)) out.add(q);   // filet : moins de 3 familles
        return out;
    }

    private static void accepter(ServerPlayer p, CompoundTag t, String qid) {
        if (t.getInt("etat") != PROPOSITIONS) return;
        boolean propose = false;
        for (Tag x : t.getList("props", Tag.TAG_STRING)) propose |= x.getAsString().equals(qid);
        Diff d = DIFFS.get(t.getString("diff"));
        if (!propose || d == null || d.quete(qid) == null) return;
        t.putString("quete", qid);
        t.putInt("fait", 0);
        t.putInt("etat", EN_COURS);
        t.remove("props");
        rafraichirActif(p, t);
        DERNIERE_POS.remove(p.getUUID());
        Ponts.sauverJoueur(p);
        journal(p, "accepte " + d.id() + " " + qid);
    }

    private static void abandonner(ServerPlayer p, CompoundTag t) {
        if (t.getInt("etat") != EN_COURS && t.getInt("etat") != PROPOSITIONS) return;
        journal(p, "abandon " + t.getString("diff") + " " + t.getString("quete") + " " + t.getInt("fait"));
        t.putInt("etat", ABANDON);
        t.remove("props");
        rafraichirActif(p, t);
        Ponts.sauverJoueur(p);
    }

    // ------------------------------------------------------------------ progression
    private static void avancer(ServerPlayer p, Actif a, int n) {
        if (n <= 0) return;
        CompoundTag t = etat(p);
        if (t.getInt("etat") != EN_COURS) return;
        int fait = Math.min(a.quete().nombre(), t.getInt("fait") + n);
        t.putInt("fait", fait);
        if (fait >= a.quete().nombre()) reussir(p, t, a);
        else if (ouvert(p)) envoyer(p, false);
    }

    private static void reussir(ServerPlayer p, CompoundTag t, Actif a) {
        t.putInt("etat", REUSSIE);
        ListTag gains = t.getList("gains", Tag.TAG_COMPOUND);
        if (gains.size() < 7) {
            CompoundTag g = new CompoundTag();
            g.putString("id", p.getUUID() + ":" + t.getString("jour") + ":" + a.diff().id());
            g.putString("diff", a.diff().id());
            g.putString("quete", a.quete().id());
            gains.add(g);
            t.put("gains", gains);
        }
        ACTIFS.remove(p.getUUID());
        Ponts.sauverJoueur(p);
        journal(p, "réussite " + a.diff().id() + " " + a.quete().id());
        Moteur.notif(p, Texte.cle("heraliz_quetes.jour.notif.reussie"), 0x5FD08A);
        envoyer(p, false);
    }

    /** Vrai kill d'un zombie (HeraliZ Loot, {@code ButinZombies.ecouter}) ; tenue « MILITAIRE »... */
    static void zombie(ServerPlayer p, String tenue) {
        Actif a = actif(p);
        if (a == null || !a.quete().type().equals("zombies")) return;
        String voulu = a.quete().s("tenue");
        if (!voulu.isEmpty() && !voulu.equalsIgnoreCase(tenue)) return;
        if (a.quete().s("arme").equals("melee") && !Ponts.arme(p.getMainHandItem(), "melee")) return;
        avancer(p, a, 1);
    }

    /**
     * Première ouverture d'un conteneur rempli (HeraliZ Loot, {@code EvenementsLoot.ecouterFouille}) : palier, position,
     * couleur de salle à carte. Un même conteneur ne compte qu'une fois par jour.
     */
    static void conteneur(ServerPlayer p, CompoundTag e) {
        Actif a = actif(p);
        if (a == null) return;
        String type = a.quete().type();
        if (type.equals("salle")) {
            if (!a.quete().s("couleur").equals(e.getString("salle"))) return;
        } else if (!type.equals("conteneurs") || e.getInt("palier") < Math.max(1, a.quete().i("palier_min"))) {
            return;
        }
        CompoundTag t = etat(p);
        long cle = e.getLong("pos") ^ ((long) e.getString("monde").hashCode() << 1);
        ListTag vus = t.getList("vus", Tag.TAG_LONG);
        for (Tag x : vus) if (((LongTag) x).getAsLong() == cle) return;
        vus.add(LongTag.valueOf(cle));
        t.put("vus", vus);
        avancer(p, a, 1);
    }

    /** Arrivée au spawn depuis la carte (extraction). */
    static void extraction(ServerPlayer p) {
        Actif a = actif(p);
        if (a != null && a.quete().type().equals("extraction")) avancer(p, a, 1);
    }

    /** Vente à un marchand d'objets qu'aucun marchand ne vend (hérals). */
    static void vente(ServerPlayer p, long herals) {
        Actif a = actif(p);
        if (a != null && a.quete().type().equals("vendre")) avancer(p, a, (int) Math.min(Integer.MAX_VALUE, herals));
    }

    /** Objets recyclés au recycleur. */
    static void recyclage(ServerPlayer p, int n) {
        Actif a = actif(p);
        if (a != null && a.quete().type().equals("recycler")) avancer(p, a, n);
    }

    /**
     * Kill d'un joueur : armé (une arme à feu TaCZ sur lui), pas du même clan, pas la même adresse, une fois par victime
     * et par jour, ni bot ni en match.
     */
    static void kill(ServerPlayer tueur, ServerPlayer victime) {
        Actif a = actif(tueur);
        if (a == null || !a.quete().type().equals("pvp") || tueur == victime) return;
        if (victime.getTags().contains("heraliz_bot") || victime.getPersistentData().getBoolean("heralisz:en_match")) return;
        if (tueur.level().dimension() != Level.OVERWORLD) return;
        String ipT = ip(tueur), ipV = ip(victime);
        if (!ipT.isEmpty() && ipT.equals(ipV)) return;
        UUID cT = clanDe(tueur.getUUID()), cV = clanDe(victime.getUUID());
        if (cT != null && cT.equals(cV)) return;
        boolean armee = false;
        for (ItemStack s : victime.getInventory().items) armee |= Ponts.arme(s, "feu");
        if (!armee) return;
        CompoundTag t = etat(tueur);
        ListTag v = t.getList("victimes", Tag.TAG_STRING);
        for (Tag x : v) if (x.getAsString().equals(victime.getUUID().toString())) return;
        v.add(StringTag.valueOf(victime.getUUID().toString()));
        t.put("victimes", v);
        avancer(tueur, a, 1);
    }

    private static final Map<UUID, double[]> DERNIERE_POS = new HashMap<>();

    /** Toutes les secondes : mètres faits à pied sur la carte (ni véhicule, ni nage, ni vol ; 12 m/s au plus). */
    static void seconde(ServerPlayer p) {
        Actif a = ACTIFS.get(p.getUUID());
        if (a == null || !a.quete().type().equals("distance")) return;
        boolean aPied = p.level().dimension() == Level.OVERWORLD && !p.isSpectator() && !p.isCreative() && !p.isPassenger()
                && !p.isInWater() && !p.isFallFlying() && !p.getAbilities().flying;
        double[] prev = DERNIERE_POS.put(p.getUUID(), new double[]{p.getX(), p.getZ(), aPied ? 1 : 0, 0});
        if (prev == null || !aPied || prev[2] == 0) return;
        double d = Math.hypot(p.getX() - prev[0], p.getZ() - prev[1]);
        if (d <= 0.05 || d > 12) return;
        double cumul = prev[3] + d;
        int entier = (int) cumul;
        DERNIERE_POS.get(p.getUUID())[3] = cumul - entier;
        if (entier > 0) avancer(p, a, entier);
    }

    // ------------------------------------------------------------------ livraison (objets pris depuis le menu)
    private static void livrer(ServerPlayer p, CompoundTag t) {
        Actif a = actif(p);
        if (a == null || !a.quete().type().equals("livrer")) return;
        int reste = a.quete().nombre() - t.getInt("fait");
        int pris = 0;
        for (int i = 0; i < p.getInventory().items.size() && pris < reste; i++) {
            ItemStack s = p.getInventory().items.get(i);
            if (!livrable(a.quete(), s)) continue;
            int n = Math.min(s.getCount(), reste - pris);
            s.shrink(n);
            pris += n;
        }
        if (pris <= 0) {
            Moteur.notif(p, Texte.cle("heraliz_quetes.jour.notif.rien_a_livrer"), 0xFF6B6B);
            return;
        }
        journal(p, "livre " + pris + " pour " + a.quete().id());
        avancer(p, a, pris);
        Ponts.sauverJoueur(p);
    }

    /** L'objet compte-t-il pour cette livraison ? (objets d'événement, prêtés par un match ou marqués : jamais) */
    static boolean livrable(Quete q, ItemStack s) {
        if (s.isEmpty() || Echanges.interdit(s)) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id == null) return false;
        String tag = q.s("tag");
        if (!tag.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(tag);
            return rl != null && s.is(TagKey.create(net.minecraft.core.registries.Registries.ITEM, rl));
        }
        int niveau = q.i("niveau");
        if (niveau > 0 && id.getNamespace().equals("heraliz_composants")) {
            String suf = niveau == 2 ? "_ii" : niveau == 3 ? "_iii" : niveau == 4 ? "_iv" : "";
            return !suf.isEmpty() && id.getPath().endsWith(suf);
        }
        return false;
    }

    static int livrables(ServerPlayer p, Quete q) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (livrable(q, s)) n += s.getCount();
        return n;
    }

    // ------------------------------------------------------------------ tirage et remise
    private static void tirer(ServerPlayer p, CompoundTag t) {
        if (t.contains("remise", Tag.TAG_COMPOUND)) {
            remettre(p, t);   // tirage déjà fait (déconnexion, inventaire plein...) : on termine la remise
            return;
        }
        ListTag gains = t.getList("gains", Tag.TAG_COMPOUND);
        if (gains.isEmpty()) return;
        CompoundTag g = gains.getCompound(0);
        Diff d = DIFFS.get(g.getString("diff"));
        if (d == null) return;
        if (libres(p) < 1) {
            Moteur.notif(p, Texte.cle("heraliz_quetes.jour.notif.place"), 0xFF6B6B);
            return;
        }
        Recompense r = tirage(d, HASARD.nextDouble() * 100);
        CompoundTag remise = new CompoundTag();
        remise.putString("id", g.getString("id"));
        remise.putString("diff", d.id());
        remise.putString("rec", r.id());
        remise.putInt("numero", 1 + HASARD.nextInt(4));
        remise.putLong("quand", System.currentTimeMillis());
        // le tirage est noté et le gain retiré dans la même sauvegarde, AVANT de donner quoi que ce soit
        gains.remove(0);
        t.put("gains", gains);
        t.put("remise", remise);
        t.putString("resultat", d.id() + ":" + r.id());
        if (t.getInt("etat") == REUSSIE) t.putInt("etat", FINIE);
        Ponts.sauverJoueur(p);
        journal(p, "tirage " + d.id() + " -> " + r.id() + " (" + r.chance() + " %) #" + remise.getString("id"));
        Reseau.envoyer(p, bande(d, r, p));
        // remise à la fin de l'animation (5 s), à la fermeture de l'écran, ou à la reconnexion ; au plus tard ici
        EN_ATTENTE.put(p.getUUID(), serveur.getTickCount() + 100);
    }

    /** Le tirage : chances cumulées (somme 100). */
    static Recompense tirage(Diff d, double x) {
        double cumul = 0;
        for (Recompense r : d.recompenses()) {
            cumul += r.chance();
            if (x < cumul) return r;
        }
        return d.recompenses().get(d.recompenses().size() - 1);
    }

    private static final Map<UUID, Integer> EN_ATTENTE = new HashMap<>();

    /** Toutes les secondes : remises arrivées à leur heure. */
    static void tick(MinecraftServer s) {
        if (EN_ATTENTE.isEmpty()) return;
        int maintenant = s.getTickCount();
        EN_ATTENTE.entrySet().removeIf(en -> {
            if (en.getValue() > maintenant) return false;
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            if (p == null) return true;          // parti : remis à sa prochaine connexion
            if (!p.isAlive()) return false;      // mort : remis quand il est revenu
            remettre(p, etat(p));
            return true;
        });
    }

    /** Écran fermé : la remise en attente se fait tout de suite. */
    static void ecranFerme(ServerPlayer p) {
        if (EN_ATTENTE.remove(p.getUUID()) != null) remettre(p, etat(p));
    }

    /**
     * Donne ce que dit la note de remise, puis efface la note dans la même sauvegarde que l'inventaire. Inventaire plein :
     * la note reste (bouton « Récupérer », reconnexion).
     */
    static void remettre(ServerPlayer p, CompoundTag t) {
        if (!t.contains("remise", Tag.TAG_COMPOUND) || !p.isAlive()) return;
        CompoundTag remise = t.getCompound("remise");
        Diff d = DIFFS.get(remise.getString("diff"));
        Recompense r = d == null ? null : d.recompense(remise.getString("rec"));
        if (r == null) {
            journal(p, "remise impossible (récompense inconnue " + remise + ") : à régler par le staff");
            return;
        }
        if (!r.cle().isEmpty()) {
            if (!crediterCle(p.getUUID(), r.cle(), r.nombre(), remise.getString("id"))) {
                Moteur.notif(p, Texte.cle("heraliz_quetes.jour.notif.cle_erreur"), 0xFF6B6B);
                return;
            }
        } else {
            ItemStack st = objet(r, remise.getInt("numero"));
            if (st.isEmpty()) {
                journal(p, "remise impossible (objet absent " + r.id() + ")");
                return;
            }
            if (p.getInventory().getFreeSlot() < 0) {
                Moteur.notif(p, Texte.cle("heraliz_quetes.jour.notif.place"), 0xFF6B6B);
                return;
            }
            // une case libre suffit (une seule pile) ; un reste éventuel tombe aux pieds, jamais perdu
            p.getInventory().add(st);
            if (!st.isEmpty()) p.drop(st, false);
        }
        t.remove("remise");
        Ponts.sauverJoueur(p);
        journal(p, "remis " + r.id() + " #" + remise.getString("id"));
        Moteur.notif(p, Texte.cle("heraliz_quetes.jour.notif.remis", nom(r)), 0xE8C36A);
        if (ouvert(p)) envoyer(p, false);
    }

    /** L'objet d'une récompense (fragment : le numéro tiré avec la récompense). */
    static ItemStack objet(Recompense r, int numero) {
        String id = !r.fragment().isEmpty() ? "heralisz_bunker:keycard_fragment_" + r.fragment() + "_" + Math.max(1, Math.min(4, numero)) : r.objet();
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) return ItemStack.EMPTY;
        Item it = ForgeRegistries.ITEMS.getValue(rl);
        ItemStack st = new ItemStack(it, r.nombre());
        if (!r.nbt().isEmpty()) {
            try {
                st.setTag(TagParser.parseTag(r.nbt()));
            } catch (Exception ex) {
                LOG.warn("[heraliz_quetes] NBT illisible pour {} : {}", id, r.nbt());
            }
        }
        st.setCount(Math.min(r.nombre(), Math.max(1, st.getMaxStackSize())));
        return st;
    }

    private static int libres(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.isEmpty()) n++;
        return n;
    }

    // ------------------------------------------------------------------ envoi au joueur
    private static final java.util.Set<UUID> OUVERTS = new java.util.HashSet<>();

    private static boolean ouvert(ServerPlayer p) {
        return OUVERTS.contains(p.getUUID());
    }

    static void ferme(ServerPlayer p) {
        OUVERTS.remove(p.getUUID());
        ecranFerme(p);
    }

    static void envoyer(ServerPlayer p, boolean ouvrir) {
        if (serveur == null || DIFFS.isEmpty()) {
            if (ouvrir) Moteur.notif(p, Texte.cle("heraliz_quetes.jour.notif.ferme"), 0xFF6B6B);
            return;
        }
        if (ouvrir) OUVERTS.add(p.getUUID());
        CompoundTag t = etat(p);
        List<Reseau.JDiff> diffs = new ArrayList<>();
        for (Diff d : DIFFS.values()) {
            List<Texte> ex = new ArrayList<>();
            for (int i = 0; i < Math.min(3, d.quetes().size()); i++) ex.add(titre(d.quetes().get(i)));
            diffs.add(new Reseau.JDiff(d.id(), d.duree(), ex, recompenses(d)));
        }
        List<Reseau.JProp> props = new ArrayList<>();
        Diff d = DIFFS.get(t.getString("diff"));
        int etat = t.getInt("etat");
        if (d != null && etat == PROPOSITIONS) {
            for (Tag x : t.getList("props", Tag.TAG_STRING)) {
                Quete q = d.quete(x.getAsString());
                if (q != null) props.add(prop(q, 0));
            }
        } else if (d != null && (etat == EN_COURS || etat == REUSSIE || etat == FINIE || etat == ABANDON)) {
            Quete q = d.quete(t.getString("quete"));
            if (q != null) props.add(prop(q, etat == REUSSIE || etat == FINIE ? q.nombre() : t.getInt("fait")));
        }
        int livrables = 0;
        Actif a = ACTIFS.get(p.getUUID());
        if (a != null && a.quete().type().equals("livrer")) livrables = livrables(p, a.quete());
        ListTag gl = t.getList("gains", Tag.TAG_COMPOUND);
        String gainDiff = t.contains("remise", Tag.TAG_COMPOUND) ? t.getCompound("remise").getString("diff")
                : gl.isEmpty() ? "" : gl.getCompound(0).getString("diff");
        Reseau.envoyer(p, new Reseau.Jour(ouvrir, etat, t.getString("diff"), resteMs(), diffs, props, gl.size(), gainDiff,
                t.contains("remise", Tag.TAG_COMPOUND), t.getString("resultat"), livrables));
    }

    private static Reseau.JProp prop(Quete q, int fait) {
        int total = q.type().equals("distance") ? q.nombre() / 1000 : q.nombre();
        int f = q.type().equals("distance") ? fait / 1000 : fait;
        return new Reseau.JProp(q.id(), q.famille(), titre(q), aide(q), Math.min(f, total), total, q.type().equals("salle"),
                q.type().equals("livrer"));
    }

    static List<Reseau.JRec> recompenses(Diff d) {
        List<Reseau.JRec> l = new ArrayList<>();
        for (Recompense r : d.recompenses()) {
            ItemStack ic = icone(r);
            // nom : fragments et clés ont le leur ; les autres prennent le nom de l'objet chez le joueur (vide ici)
            Texte n = !r.fragment().isEmpty() || !r.cle().isEmpty() ? nom(r) : Texte.VIDE;
            l.add(new Reseau.JRec(r.id(), ic, n, r.nombre(), rarete(r, ic), (float) r.chance()));
        }
        return l;
    }

    private static ItemStack icone(Recompense r) {
        if (!r.cle().isEmpty()) {
            ResourceLocation rl = new ResourceLocation("heralizcrates", r.cle() + "_key");
            return ForgeRegistries.ITEMS.containsKey(rl) ? new ItemStack(ForgeRegistries.ITEMS.getValue(rl)) : ItemStack.EMPTY;
        }
        return objet(r, 2);
    }

    private static int rarete(Recompense r, ItemStack ic) {
        if (!r.cle().isEmpty()) return r.cle().equals("gemme") ? 4 : 3;
        return ic.isEmpty() ? -1 : Ponts.rarete(ic);
    }

    /** Nom d'une récompense : texte du mod (fragments, clés, quantités) ; sinon vide = nom de l'objet chez le joueur. */
    static Texte nom(Recompense r) {
        if (!r.fragment().isEmpty()) {
            return Texte.cle(r.nombre() > 1 ? "heraliz_quetes.jour.r.fragments_" + r.fragment() : "heraliz_quetes.jour.r.fragment_" + r.fragment(),
                    Texte.brut(r.nombre()));
        }
        if (!r.cle().isEmpty()) return Texte.cle("heraliz_quetes.jour.r.cle", Texte.cle("item.heralizcrates." + r.cle() + "_key"));
        ItemStack st = objet(r, 1);
        if (st.isEmpty()) return Texte.brut(r.id());
        Texte n = Texte.cle(st.getDescriptionId());
        if (r.objet().equals("tacz:modern_kinetic_gun") || r.objet().equals("tacz:ammo") || r.objet().equals("heralisz_tactical:consumable")) {
            n = Texte.cle("heraliz_quetes.jour.r.objet");   // nom lu sur l'objet chez le joueur (dépend de ses données)
        }
        return r.nombre() > 1 ? Texte.cle("heraliz_quetes.jour.r.nombre", n, Texte.brut(r.nombre())) : n;
    }

    static Texte titre(Quete q) {
        int n = q.nombre();
        return switch (q.type()) {
            case "zombies" -> q.s("tenue").equalsIgnoreCase("MILITAIRE") ? Texte.cle("heraliz_quetes.jour.q.zombies_mil", Texte.brut(n))
                    : q.s("arme").equals("melee") ? Texte.cle("heraliz_quetes.jour.q.zombies_melee", Texte.brut(n))
                    : Texte.cle("heraliz_quetes.jour.q.zombies", Texte.brut(n));
            case "conteneurs" -> q.i("palier_min") > 1 ? Texte.cle("heraliz_quetes.jour.q.conteneurs_t", Texte.brut(n), Texte.brut(q.i("palier_min")))
                    : Texte.cle("heraliz_quetes.jour.q.conteneurs", Texte.brut(n));
            case "salle" -> Texte.cle("heraliz_quetes.jour.q.salle_" + q.s("couleur"));
            case "extraction" -> n == 1 ? Texte.cle("heraliz_quetes.jour.q.extraction_1") : Texte.cle("heraliz_quetes.jour.q.extraction", Texte.brut(n));
            case "vendre" -> Texte.cle("heraliz_quetes.jour.q.vendre", Texte.brut(Ponts.nombre(n)));
            case "recycler" -> Texte.cle("heraliz_quetes.jour.q.recycler", Texte.brut(n));
            case "livrer" -> !q.s("tag").isEmpty() ? Texte.cle("heraliz_quetes.jour.q.livrer_recup", Texte.brut(n))
                    : Texte.cle("heraliz_quetes.jour.q.livrer_niveau", Texte.brut(n), Texte.brut(romain(q.i("niveau"))));
            case "distance" -> Texte.cle("heraliz_quetes.jour.q.distance", Texte.brut(n / 1000));
            case "pvp" -> Texte.cle("heraliz_quetes.jour.q.pvp", Texte.brut(n));
            default -> Texte.brut(q.id());
        };
    }

    static Texte aide(Quete q) {
        return switch (q.type()) {
            case "zombies" -> Texte.cle(q.s("tenue").equalsIgnoreCase("MILITAIRE") ? "heraliz_quetes.jour.a.zombies_mil"
                    : q.s("arme").equals("melee") ? "heraliz_quetes.jour.a.zombies_melee" : "heraliz_quetes.jour.a.zombies");
            case "salle" -> Texte.cle("heraliz_quetes.jour.a.salle_" + q.s("couleur"));
            default -> Texte.cle("heraliz_quetes.jour.a." + q.type());
        };
    }

    private static String romain(int n) {
        return switch (n) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "I";
        };
    }

    /** Bande du tirage pour l'animation (60 cartes tirées au hasard, le gagnant à la 50e) : index dans la table. */
    private static Reseau.JourTirage bande(Diff d, Recompense gagnant, ServerPlayer p) {
        List<Integer> l = new ArrayList<>();
        int g = d.recompenses().indexOf(gagnant);
        for (int i = 0; i < 60; i++) {
            l.add(i == 50 ? g : d.recompenses().indexOf(tirage(d, HASARD.nextDouble() * 100)));
        }
        return new Reseau.JourTirage(d.id(), l, 50);
    }

    // ------------------------------------------------------------------ ponts (réflexion)
    private static boolean crediterCle(UUID u, String caisse, int n, String id) {
        try {
            Object r = Class.forName("fr.heraliz.crates.api.ClesApi").getMethod("crediterUneFois", UUID.class, String.class, int.class,
                    String.class, String.class).invoke(null, u, caisse, n, "quete_jour", id);
            return r instanceof Boolean b && b;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOG.error("[heraliz_quetes] clé de caisse {} pour {} : {}", caisse, u, e.toString());
            return false;
        }
    }

    @Nullable
    private static UUID clanDe(UUID joueur) {
        try {
            Object r = Class.forName("fr.heralisz.clans.saison.SaisonApi").getMethod("clanDe", UUID.class).invoke(null, joueur);
            return r instanceof UUID u ? u : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    private static String ip(ServerPlayer p) {
        try {
            String s = p.getIpAddress();
            return s == null ? "" : s;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Écouteurs de HeraliZ Loot (zombies, conteneurs), par réflexion : sans Loot, ces objectifs n'avancent pas. */
    @SuppressWarnings("unchecked")
    private static void brancherLoot() {
        try {
            Class.forName("fr.heraliz.loot.ButinZombies").getMethod("ecouter", BiConsumer.class)
                    .invoke(null, (BiConsumer<ServerPlayer, String>) Journalieres::zombie);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.warn("[heraliz_quetes] HeraliZ Loot (zombies) absent : quêtes du jour « zombies » impossibles ({})", e.toString());
        }
        try {
            Class.forName("fr.heraliz.loot.EvenementsLoot").getMethod("ecouterFouille", BiConsumer.class)
                    .invoke(null, (BiConsumer<ServerPlayer, CompoundTag>) Journalieres::conteneur);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.warn("[heraliz_quetes] HeraliZ Loot (conteneurs) absent : quêtes du jour « conteneurs » impossibles ({})", e.toString());
        }
    }

    // ------------------------------------------------------------------ journal
    static void journal(ServerPlayer p, String ligne) {
        if (serveur == null) return;
        try {
            Path f = serveur.getServerDirectory().toPath().resolve("heraliz_quetes").resolve("journalieres.log");
            Files.createDirectories(f.getParent());
            Files.writeString(f, java.time.LocalDateTime.now().withNano(0) + " " + p.getGameProfile().getName() + " " + p.getUUID() + " "
                    + ligne + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            LOG.warn("[heraliz_quetes] journal des quêtes du jour : {}", e.toString());
        }
    }

    /**
     * Essais du staff (op 4, /journalieres essai ...), notés au journal : quête forcée, progression ajoutée, minuit simulé.
     * Rend faux si la demande est impossible.
     */
    static boolean essai(ServerPlayer p, String quoi, String diff, String quete, int n) {
        CompoundTag t = etat(p);
        switch (quoi) {
            case "forcer" -> {
                Diff d = DIFFS.get(diff);
                if (d == null || d.quete(quete) == null) return false;
                t.putString("diff", diff);
                t.putString("quete", quete);
                t.putInt("fait", 0);
                t.putInt("etat", EN_COURS);
                t.remove("props");
                t.remove("vus");
                t.remove("victimes");
                rafraichirActif(p, t);
            }
            case "avancer" -> {
                Actif a = actif(p);
                if (a == null) return false;
                avancer(p, a, n);
            }
            case "minuit" -> t.putString("jour", LocalDate.now(zone).minusDays(1).toString());
            default -> {
                return false;
            }
        }
        journal(p, "ESSAI du staff : " + quoi + " " + diff + " " + quete + " " + n);
        envoyer(p, false);
        return true;
    }

    /** Pour /journalieres voir (staff) : résumé de l'état d'un joueur. */
    static String resume(ServerPlayer p) {
        CompoundTag t = etat(p);
        return "jour " + t.getString("jour") + ", état " + t.getInt("etat") + ", difficulté " + t.getString("diff") + ", quête "
                + t.getString("quete") + " " + t.getInt("fait") + ", gains " + t.getList("gains", Tag.TAG_COMPOUND).size()
                + (t.contains("remise") ? ", remise en attente " + t.getCompound("remise") : "");
    }

    static Map<String, Diff> diffs() {
        return DIFFS;
    }
}
