package fr.heralisz.matchs.zombies.jeu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Records du mode zombies (donnees globales du serveur, JSON atomique {@code <serveur>/heralisz_matchs/zombies_records.json},
 * .tmp puis ATOMIC_MOVE) : par carte, record du serveur et record de chaque groupe (cle = UUID tries) ; par joueur,
 * meilleure manche, parties, kills, tetes. Fil serveur ; ecriture seulement en fin de partie.
 * <pre>
 * {"cartes": {"zombies_labo_n07": {"serveur": {"manche": 12, "noms": ["Vortex"], "kills": 230, "date": 1759...},
 *                                 "groupes": {"uuidA,uuidB": {"manche": 9, "noms": [...], "date": ...}}}},
 *  "joueurs": {"uuid": {"nom": "Vortex", "meilleure": 12, "parties": 4, "kills": 610, "tetes": 140}}}
 * </pre>
 */
public final class Records {
    private Records() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    @Nullable
    private static JsonObject donnees;
    @Nullable
    private static MinecraftServer serveurCharge;

    /** Participant d'une partie terminee. */
    record Participant(UUID id, String nom, int kills, int tetes) {
    }

    /** Resultat de l'enregistrement : records battus et ancien record du serveur. */
    record Resultat(boolean groupe, boolean serveur, int recordServeur) {
    }

    @Nullable
    private static Path fichier() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return null;
        return s.getServerDirectory().toPath().resolve("heralisz_matchs").resolve("zombies_records.json");
    }

    private static JsonObject donnees() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (donnees != null && serveurCharge == s) return donnees;
        serveurCharge = s;
        donnees = new JsonObject();
        Path f = fichier();
        if (f != null && Files.isRegularFile(f)) {
            try {
                JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
                if (e.isJsonObject()) donnees = e.getAsJsonObject();
            } catch (IOException | RuntimeException ex) {
                HeraliszMatchs.LOG.error("[zombies] records illisibles ({}), fichier garde tel quel", f, ex);
            }
        }
        return donnees;
    }

    private static JsonObject objet(JsonObject parent, String cle) {
        JsonElement e = parent.get(cle);
        if (e != null && e.isJsonObject()) return e.getAsJsonObject();
        JsonObject o = new JsonObject();
        parent.add(cle, o);
        return o;
    }

    private static int entier(@Nullable JsonObject o, String cle) {
        if (o == null) return 0;
        JsonElement e = o.get(cle);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : 0;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** Cle d'un groupe : UUID tries, separes par des virgules. */
    static String cleGroupe(Collection<UUID> ids) {
        List<String> l = new ArrayList<>(ids.size());
        for (UUID u : ids) l.add(u.toString());
        l.sort(null);
        return String.join(",", l);
    }

    /** Record du serveur sur cette carte (manche, 0 = aucun). */
    public static int recordServeur(String carte) {
        JsonObject c = objetOuNull(objetOuNull(donnees(), "cartes"), carte);
        return entier(objetOuNull(c, "serveur"), "manche");
    }

    /** Noms des detenteurs du record du serveur (« Vortex, Dziga »), vide si aucun. */
    public static String recordServeurNoms(String carte) {
        JsonObject s = objetOuNull(objetOuNull(objetOuNull(donnees(), "cartes"), carte), "serveur");
        if (s == null || !s.has("noms") || !s.get("noms").isJsonArray()) return "";
        List<String> n = new ArrayList<>();
        for (JsonElement e : s.getAsJsonArray("noms")) n.add(e.getAsString());
        return String.join(", ", n);
    }

    /** Record de ce groupe exact sur cette carte (0 = aucun). */
    public static int recordGroupe(String carte, Collection<UUID> groupe) {
        JsonObject g = objetOuNull(objetOuNull(objetOuNull(objetOuNull(donnees(), "cartes"), carte), "groupes"), cleGroupe(groupe));
        return entier(g, "manche");
    }

    /** Meilleure manche d'un joueur (toutes cartes). */
    public static int meilleureJoueur(UUID joueur) {
        return entier(objetOuNull(objetOuNull(donnees(), "joueurs"), joueur.toString()), "meilleure");
    }

    @Nullable
    private static JsonObject objetOuNull(@Nullable JsonObject parent, String cle) {
        if (parent == null) return null;
        JsonElement e = parent.get(cle);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /** Enregistre une partie terminee (manche atteinte = derniere manche commencee) et sauvegarde. */
    static Resultat enregistrer(String carte, int manche, List<Participant> participants) {
        JsonObject d = donnees();
        JsonObject c = objet(objet(d, "cartes"), carte);
        JsonObject s = objetOuNull(c, "serveur");
        int ancien = entier(s, "manche");
        long maintenant = System.currentTimeMillis();
        int kills = 0;
        JsonArray noms = new JsonArray();
        List<UUID> ids = new ArrayList<>();
        for (Participant p : participants) {
            noms.add(p.nom());
            ids.add(p.id());
            kills += p.kills();
        }
        boolean recServeur = manche > ancien && manche > 0;
        if (recServeur) {
            JsonObject n = new JsonObject();
            n.addProperty("manche", manche);
            n.add("noms", noms.deepCopy());
            n.addProperty("kills", kills);
            n.addProperty("date", maintenant);
            c.add("serveur", n);
        }
        JsonObject groupes = objet(c, "groupes");
        String cle = cleGroupe(ids);
        boolean recGroupe = manche > entier(objetOuNull(groupes, cle), "manche") && manche > 0;
        if (recGroupe) {
            JsonObject n = new JsonObject();
            n.addProperty("manche", manche);
            n.add("noms", noms.deepCopy());
            n.addProperty("kills", kills);
            n.addProperty("date", maintenant);
            groupes.add(cle, n);
        }
        JsonObject joueurs = objet(d, "joueurs");
        for (Participant p : participants) {
            JsonObject j = objet(joueurs, p.id().toString());
            j.addProperty("nom", p.nom());
            j.addProperty("meilleure", Math.max(entier(j, "meilleure"), manche));
            j.addProperty("parties", entier(j, "parties") + 1);
            j.addProperty("kills", entier(j, "kills") + p.kills());
            j.addProperty("tetes", entier(j, "tetes") + p.tetes());
        }
        sauver();
        return new Resultat(recGroupe, recServeur, ancien);
    }

    /**
     * Quete de la carte terminee (The Giant : boss N-07 vaincu, D-057) a la manche {@code manche} : meilleure quete du serveur
     * et du groupe = le moins de manches ({@code cartes.<id>.quete}), compteur {@code quetes} par joueur. Rend vrai si c'est
     * le record du serveur.
     */
    static boolean enregistrerQuete(String carte, int manche, List<Participant> participants) {
        JsonObject d = donnees();
        JsonObject q = objet(objet(objet(d, "cartes"), carte), "quete");
        long maintenant = System.currentTimeMillis();
        JsonArray noms = new JsonArray();
        List<UUID> ids = new ArrayList<>();
        for (Participant p : participants) {
            noms.add(p.nom());
            ids.add(p.id());
        }
        int ancien = entier(objetOuNull(q, "serveur"), "manche");
        boolean rec = manche > 0 && (ancien <= 0 || manche < ancien);
        if (rec) {
            JsonObject n = new JsonObject();
            n.addProperty("manche", manche);
            n.add("noms", noms.deepCopy());
            n.addProperty("date", maintenant);
            q.add("serveur", n);
        }
        JsonObject groupes = objet(q, "groupes");
        String cle = cleGroupe(ids);
        int ancienGroupe = entier(objetOuNull(groupes, cle), "manche");
        if (manche > 0 && (ancienGroupe <= 0 || manche < ancienGroupe)) {
            JsonObject n = new JsonObject();
            n.addProperty("manche", manche);
            n.add("noms", noms.deepCopy());
            n.addProperty("date", maintenant);
            groupes.add(cle, n);
        }
        JsonObject joueurs = objet(d, "joueurs");
        for (Participant p : participants) {
            JsonObject j = objet(joueurs, p.id().toString());
            j.addProperty("nom", p.nom());
            j.addProperty("quetes", entier(j, "quetes") + 1);
        }
        sauver();
        return rec;
    }

    /** Meilleure quete du serveur sur cette carte (manches, 0 = jamais terminee) et ses noms. */
    public static int recordQuete(String carte) {
        return entier(objetOuNull(objetOuNull(objetOuNull(objetOuNull(donnees(), "cartes"), carte), "quete"), "serveur"), "manche");
    }

    public static String recordQueteNoms(String carte) {
        JsonObject s = objetOuNull(objetOuNull(objetOuNull(objetOuNull(donnees(), "cartes"), carte), "quete"), "serveur");
        if (s == null || !s.has("noms") || !s.get("noms").isJsonArray()) return "";
        List<String> n = new ArrayList<>();
        for (JsonElement e : s.getAsJsonArray("noms")) n.add(e.getAsString());
        return String.join(", ", n);
    }

    private static void sauver() {
        Path f = fichier();
        if (f == null || donnees == null) return;
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(donnees), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            HeraliszMatchs.LOG.error("[zombies] ecriture des records impossible", ex);
        }
    }

    /** Oublie le cache (arret du serveur). */
    static void vider() {
        donnees = null;
        serveurCharge = null;
    }
}
