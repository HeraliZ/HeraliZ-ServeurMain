package fr.heralisz.matchs.duels.classes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.duels.DuelsConfig;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.moteur.api.Kit;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Classe personnalisee de duel (MATCHS_SPEC §3) : 5 emplacements par joueur, sauvegardes dans les donnees globales du
 * serveur ({@code DuelsStore}). Arme principale (toute arme TaCZ du {@link Catalogue}) et ses accessoires (ceux permis
 * par TaCZ pour cette arme, un par emplacement), arme secondaire (arme de poing), melee, 2 soins, 1 grenade letale + 1
 * tactique, tenue (tenue complete de HeraliZ Armurerie). <b>Jamais de camo, skin, sticker ni pendentif</b> : la classe ne peut pas en
 * porter, et les objets donnes sont nettoyes ({@code DuelPartie}).
 * <p>Chaine vide = emplacement vide. Toute classe recue d'un client passe par {@link #assainir(Catalogue)} : un id
 * inconnu ou interdit est simplement retire ; seule exception, une tenue inconnue (ancien kit heralisz_armor sauvegarde
 * avant le passage a l'Armurerie) est remplacee par la tenue par defaut ({@link Catalogue#tenueParDefaut()}, Siberie).
 *
 * @param nom         nom choisi par le joueur (16 caracteres au plus)
 * @param principale  id TaCZ de l'arme principale
 * @param accessoires emplacement TaCZ en minuscules -> id d'accessoire
 * @param secondaire  id TaCZ de l'arme secondaire
 * @param melee       id d'index heralisz_tactical de l'arme de melee
 * @param soins       ids de consommables (2 au plus, doublons permis)
 * @param letale      id de grenade letale
 * @param tactique    id de grenade tactique
 * @param tenue       tenue de HeraliZ Armurerie : chemin de l'objet {@code heraliz_armory:kit_<ensemble>} ({@code kit_siberie}),
 *                    vide = aucune tenue
 */
public record ClassePerso(String nom, String principale, Map<String, String> accessoires, String secondaire, String melee,
                          List<String> soins, String letale, String tactique, String tenue) {
    /** Nombre d'emplacements de classes par joueur. */
    public static final int EMPLACEMENTS = 5;
    /** Longueur maximale du nom. */
    public static final int NOM_MAX = 16;
    /** Nombre de soins par classe. */
    public static final int SOINS = 2;

    public ClassePerso {
        nom = nom == null ? "" : nom;
        principale = nn(principale);
        accessoires = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(accessoires));
        secondaire = nn(secondaire);
        melee = nn(melee);
        soins = List.copyOf(soins.size() > SOINS ? soins.subList(0, SOINS) : soins);
        letale = nn(letale);
        tactique = nn(tactique);
        tenue = nn(tenue);
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    /** Classe vide nommee « CLASSE n » (n a partir de 1). */
    public static ClassePerso vide(int n) {
        return new ClassePerso("CLASSE " + n, "", Map.of(), "", "", List.of(), "", "", "");
    }

    /**
     * Classe par defaut de l'emplacement n (0..4) construite depuis le catalogue : une categorie d'arme par emplacement
     * (fusil, mitraillette, pompe, precision, mitrailleuse), premiere arme de poing, premiere melee, deux premiers soins,
     * premieres grenades, tenue par defaut (Siberie).
     */
    public static ClassePerso parDefaut(int n, Catalogue c) {
        String[][] types = {{"rifle"}, {"smg"}, {"shotgun"}, {"sniper"}, {"mg", "rifle"}};
        String[] noms = {"ASSAUT", "SMG", "POMPE", "PRÉCISION", "SOUTIEN"};
        int k = Math.max(0, Math.min(EMPLACEMENTS - 1, n));
        String principale = "";
        for (String t : types[k]) {
            for (Catalogue.Arme a : c.armes()) {
                if (a.type().equals(t)) {
                    principale = a.id();
                    break;
                }
            }
            if (!principale.isEmpty()) break;
        }
        if (principale.isEmpty() && !c.armes().isEmpty()) principale = c.armes().get(0).id();
        String secondaire = "";
        for (Catalogue.Arme a : c.armes()) {
            if (a.pistolet()) {
                secondaire = a.id();
                break;
            }
        }
        List<String> soins = new ArrayList<>();
        for (int i = 0; i < SOINS && !c.soins().isEmpty(); i++) soins.add(c.soins().get(Math.min(i, c.soins().size() - 1)).id());
        return new ClassePerso(noms[k], principale, Map.of(), secondaire, premier(c.melees()), soins, premier(c.letales()),
                premier(c.tactiques()), c.tenueParDefaut());
    }

    private static String premier(List<Catalogue.Objet> l) {
        return l.isEmpty() ? "" : l.get(0).id();
    }

    /** Copie avec un autre nom (borne, capitales conservees telles que saisies). */
    public ClassePerso avecNom(String n) {
        return new ClassePerso(nettoyerNom(n), principale, accessoires, secondaire, melee, soins, letale, tactique, tenue);
    }

    /** Nom borne a {@link #NOM_MAX} caracteres imprimables. */
    public static String nettoyerNom(String n) {
        if (n == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length() && sb.length() < NOM_MAX; i++) {
            char ch = n.charAt(i);
            if (ch >= 32 && ch != 127 && ch != '§') sb.append(ch);
        }
        return sb.toString().trim();
    }

    /**
     * Copie ou chaque element absent du catalogue (ou interdit a cette place) est retire : principale et secondaire
     * verifiees, accessoires re-verifies contre la principale retenue, soins/grenades verifies. Tenue absente du catalogue
     * (ancien kit heralisz_armor) = tenue par defaut ; tenue vide (« aucune ») gardee. Le nom est borne.
     */
    public ClassePerso assainir(Catalogue c) {
        String p = c.principale(principale) ? principale : "";
        Map<String, String> acc = new LinkedHashMap<>();
        if (!p.isEmpty()) {
            for (String emp : Catalogue.EMPLACEMENTS) {
                String id = accessoires.get(emp);
                if (id != null && !id.isEmpty() && c.accessoirePermis(p, emp, id)) acc.put(emp, id);
            }
        }
        String s = c.secondaire(secondaire) ? secondaire : "";
        List<String> so = new ArrayList<>();
        for (String id : soins) if (Catalogue.chercher(c.soins(), id) != null && so.size() < SOINS) so.add(id);
        String n = nettoyerNom(nom);
        return new ClassePerso(n.isEmpty() ? "CLASSE" : n, p, acc, s, Catalogue.chercher(c.melees(), melee) != null ? melee : "", so,
                Catalogue.chercher(c.letales(), letale) != null ? letale : "", Catalogue.chercher(c.tactiques(), tactique) != null ? tactique : "",
                tenue.isEmpty() || Catalogue.chercher(c.tenues(), tenue) != null ? tenue : c.tenueParDefaut());
    }

    /** Vrai si la classe ne contient aucune arme (emplacement jamais rempli). */
    public boolean sansArme() {
        return principale.isEmpty() && secondaire.isEmpty() && melee.isEmpty();
    }

    /**
     * Kit de match equivalent (classe {@link Kit#CLASSE_PERSO}, mode "duel"), avec munitions en reserve virtuelle :
     * {@link DuelsConfig#CHARGEURS_RESERVE} chargeurs pour chaque arme a feu. Les soins identiques sont regroupes.
     *
     * @param joueur proprietaire (pour l'id du kit « perso:uuid:n »)
     * @param n      emplacement 0..4
     */
    public Kit versKit(UUID joueur, int n, Catalogue c) {
        Kit.Arme p = null, s = null;
        Catalogue.Arme ap = c.arme(principale);
        if (ap != null) p = new Kit.Arme(principale, accessoires, null, Math.max(30, ap.chargeur() * DuelsConfig.CHARGEURS_RESERVE));
        Catalogue.Arme as = c.arme(secondaire);
        if (as != null) s = new Kit.Arme(secondaire, Map.of(), null, Math.max(14, as.chargeur() * DuelsConfig.CHARGEURS_RESERVE));
        Map<String, Integer> regroupe = new LinkedHashMap<>();
        for (String id : soins) regroupe.merge(id, 1, Integer::sum);
        List<Kit.Objet> so = new ArrayList<>();
        regroupe.forEach((id, nb) -> so.add(new Kit.Objet(id, nb)));
        List<Kit.Objet> gr = new ArrayList<>();
        if (!letale.isEmpty()) gr.add(new Kit.Objet(letale, 1));
        if (!tactique.isEmpty()) gr.add(new Kit.Objet(tactique, 1));
        return new Kit("perso:" + joueur + ":" + n, nom, nom, Kit.CLASSE_PERSO, List.of("duel"), p, s, melee.isEmpty() ? null : melee, so, gr,
                tenue.isEmpty() ? null : tenue, List.of(), 0.5f, 0.5f, 0.5f);
    }

    // ------------------------------------------------------------------ JSON
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("nom", nom);
        o.addProperty("principale", principale);
        JsonObject a = new JsonObject();
        accessoires.forEach(a::addProperty);
        o.add("accessoires", a);
        o.addProperty("secondaire", secondaire);
        o.addProperty("melee", melee);
        JsonArray so = new JsonArray();
        soins.forEach(so::add);
        o.add("soins", so);
        o.addProperty("letale", letale);
        o.addProperty("tactique", tactique);
        o.addProperty("tenue", tenue);
        return o;
    }

    /** Lecture tolerante (champ absent = vide). */
    public static ClassePerso lire(JsonObject o, int n) {
        Map<String, String> acc = new LinkedHashMap<>();
        if (o.has("accessoires") && o.get("accessoires").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("accessoires").entrySet()) {
                if (e.getValue().isJsonPrimitive()) acc.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsString());
            }
        }
        List<String> so = new ArrayList<>();
        if (o.has("soins") && o.get("soins").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("soins")) if (e.isJsonPrimitive() && so.size() < SOINS) so.add(e.getAsString());
        }
        return new ClassePerso(chaine(o, "nom", "CLASSE " + (n + 1)), chaine(o, "principale", ""), acc, chaine(o, "secondaire", ""),
                chaine(o, "melee", ""), so, chaine(o, "letale", ""), chaine(o, "tactique", ""), chaine(o, "tenue", ""));
    }

    private static String chaine(JsonObject o, String cle, String defaut) {
        return o.has(cle) && o.get(cle).isJsonPrimitive() ? o.get(cle).getAsString() : defaut;
    }

    // ------------------------------------------------------------------ reseau
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(nom, 64);
        buf.writeUtf(principale, 128);
        buf.writeVarInt(accessoires.size());
        accessoires.forEach((k, v) -> {
            buf.writeUtf(k, 32);
            buf.writeUtf(v, 128);
        });
        buf.writeUtf(secondaire, 128);
        buf.writeUtf(melee, 128);
        buf.writeVarInt(soins.size());
        for (String s : soins) buf.writeUtf(s, 128);
        buf.writeUtf(letale, 128);
        buf.writeUtf(tactique, 128);
        buf.writeUtf(tenue, 128);
    }

    public static ClassePerso read(FriendlyByteBuf buf) {
        String nom = buf.readUtf(64), principale = buf.readUtf(128);
        int na = Math.min(8, buf.readVarInt());
        Map<String, String> acc = new LinkedHashMap<>();
        for (int i = 0; i < na; i++) acc.put(buf.readUtf(32), buf.readUtf(128));
        String secondaire = buf.readUtf(128), melee = buf.readUtf(128);
        int ns = Math.min(SOINS, buf.readVarInt());
        List<String> soins = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) soins.add(buf.readUtf(128));
        return new ClassePerso(nom, principale, acc, secondaire, melee, soins, buf.readUtf(128), buf.readUtf(128), buf.readUtf(128));
    }

    /** Ecrit une liste de classes (5). */
    public static void ecrireListe(FriendlyByteBuf buf, List<ClassePerso> l) {
        buf.writeVarInt(l.size());
        for (ClassePerso c : l) c.write(buf);
    }

    public static List<ClassePerso> lireListe(FriendlyByteBuf buf) {
        int n = Math.min(EMPLACEMENTS, buf.readVarInt());
        List<ClassePerso> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(read(buf));
        return l;
    }
}
