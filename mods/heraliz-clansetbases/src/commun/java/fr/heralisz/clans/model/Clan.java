package fr.heralisz.clans.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Un clan (etat serveur, persiste dans clans.json). Les regles de validation des chaines sont ici pour etre les memes
 * cote client (erreurs en ligne) et cote serveur (verification finale).
 */
public final class Clan {
    public static final int TAG_MIN = 2, TAG_MAX = 5, NOM_MIN = 3, NOM_MAX = 24, DESC_MAX = 140, DESC_LIGNES = 3;
    private static final Pattern TAG = Pattern.compile("[A-Z0-9]{2,5}");
    private static final Pattern NOM = Pattern.compile("[\\p{L}\\p{N} '\\-]{3,24}");
    private static final Pattern CONTROLE = Pattern.compile("[\\p{Cntrl}§]");

    public final UUID id;
    public String tag;
    public String nom;
    public String description = "";
    public Banniere banniere = Banniere.DEFAUT;
    public UUID chef;
    public final Map<UUID, Membre> membres = new LinkedHashMap<>();
    public long creeLe;
    public long banque;
    public int niveau = 1;
    public long xp;
    public final EnumMap<Grade, EnumSet<Perm>> perms = new EnumMap<>(Grade.class);
    public long dernierChgtNom, dernierChgtTag, dernierChgtDesc, dernierChgtBanniere;
    public final List<Mouvement> journalBanque = new ArrayList<>();
    public final List<QueteEtat> quetesJour = new ArrayList<>();
    public final List<QueteEtat> quetesSemaine = new ArrayList<>();
    public long prochainResetJour, prochainResetSemaine;
    /**
     * Net depose en banque depuis le dernier tirage (jour / semaine) : depots - retraits, jamais negatif. C'est cette
     * valeur qui fait avancer les quetes DEPOTS_BANQUE (un cycle depot/retrait ne compte pas).
     */
    public long depotNetJour, depotNetSemaine;
    public final List<Invitation> invitations = new ArrayList<>();
    public final List<Evenement> journal = new ArrayList<>();
    /** ClashWar (HeraliZ Matchs, via ClansApi.ajouterClashWar) : participations et victoires du clan. */
    public int clashwarJoues, clashwarGagnes;
    /**
     * Territoires (serveur) : minutes-zones pas encore payees (revenu verse a chaque heure-zone complete) et
     * minutes-valeur pas encore converties en XP (valeur = niveau de loot de la zone).
     */
    public long minutesZonesAPayer, minutesValeurAConvertir;
    /** Points de territoire : somme, minute par minute, des valeurs des zones tenues (60 = une zone T1 tenue 1 h). */
    public long pointsTerritoire;
    /** Par zone (identifiant) : captures et minutes tenues par le clan. */
    public final Map<String, StatZone> statsZones = new LinkedHashMap<>();
    /** Arbre d'ameliorations (saison des clans) : palier achete par branche ({@link Arbre}). */
    public final EnumMap<Arbre.Branche, Integer> arbre = new EnumMap<>(Arbre.Branche.class);

    /** Captures et minutes tenues d'une zone par le clan. */
    public static final class StatZone {
        public int captures;
        public long minutes;
    }

    public Clan(UUID id, String tag, String nom, UUID chef, long creeLe) {
        this.id = id;
        this.tag = tag;
        this.nom = nom;
        this.chef = chef;
        this.creeLe = creeLe;
        for (Grade g : Grade.modifiables()) perms.put(g, g.permsParDefaut());
    }

    // ------------------------------------------------------------------ validation
    /** Retire les codes de couleur, caracteres de controle et espaces multiples ; coupe les bouts. */
    public static String nettoyer(String s) {
        if (s == null) return "";
        return CONTROLE.matcher(s).replaceAll("").replaceAll("[ \\t]{2,}", " ").trim();
    }

    /** Cle de traduction de l'erreur, ou null si le tag est valide (apres passage en majuscules). */
    @Nullable
    public static String validerTag(String tag) {
        if (tag == null) return "msg.tag_invalide";
        String t = nettoyer(tag).toUpperCase(Locale.ROOT);
        if (t.length() < TAG_MIN || t.length() > TAG_MAX) return "msg.tag_longueur";
        return TAG.matcher(t).matches() ? null : "msg.tag_invalide";
    }

    @Nullable
    public static String validerNom(String nom) {
        if (nom == null) return "msg.nom_invalide";
        String n = nettoyer(nom);
        if (n.length() < NOM_MIN || n.length() > NOM_MAX) return "msg.nom_longueur";
        return NOM.matcher(n).matches() ? null : "msg.nom_invalide";
    }

    /** Description : 140 caracteres et 3 lignes au plus (vide autorise). */
    @Nullable
    public static String validerDescription(String desc) {
        if (desc == null) return null;
        String d = desc.replace("\r", "");
        if (d.length() > DESC_MAX) return "msg.desc_longueur";
        if (d.split("\n", -1).length > DESC_LIGNES) return "msg.desc_lignes";
        return CONTROLE.matcher(d.replace("\n", "")).find() ? "msg.desc_invalide" : null;
    }

    /** Description normalisee : lignes nettoyees, au plus 3, au plus 140 caracteres. */
    public static String nettoyerDescription(String desc) {
        if (desc == null) return "";
        String[] lignes = desc.replace("\r", "").split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(DESC_LIGNES, lignes.length); i++) {
            if (i > 0) sb.append('\n');
            sb.append(nettoyer(lignes[i]));
        }
        String out = sb.toString().trim();
        return out.length() > DESC_MAX ? out.substring(0, DESC_MAX) : out;
    }

    // ------------------------------------------------------------------ acces
    @Nullable
    public Membre membre(UUID uuid) {
        return membres.get(uuid);
    }

    public boolean estMembre(UUID uuid) {
        return membres.containsKey(uuid);
    }

    public boolean estChef(UUID uuid) {
        return uuid != null && uuid.equals(chef);
    }

    /** Grade d'un joueur dans le clan (null s'il n'en fait pas partie). */
    @Nullable
    public Grade gradeDe(UUID uuid) {
        Membre m = membres.get(uuid);
        return m == null ? null : m.grade;
    }

    /** Permissions effectives d'un grade (le chef : toutes). */
    public EnumSet<Perm> permsDe(Grade grade) {
        if (grade == null) return EnumSet.noneOf(Perm.class);
        if (grade == Grade.CHEF) return EnumSet.allOf(Perm.class);
        EnumSet<Perm> p = perms.get(grade);
        return p == null ? grade.permsParDefaut() : p;
    }

    /**
     * Permission effective d'un membre : le chef a tout ; sinon le reglage individuel (AUTORISE / REFUSE) s'il y en a
     * un, sinon la permission de son grade. Faux pour un non-membre.
     */
    public boolean peut(UUID uuid, Perm perm) {
        Membre m = uuid == null || perm == null ? null : membres.get(uuid);
        if (m == null) return false;
        if (m.grade == Grade.CHEF || estChef(uuid)) return true;
        Perm.Etat e = m.etatPerm(perm);
        if (e == Perm.Etat.AUTORISE) return true;
        if (e == Perm.Etat.REFUSE) return false;
        return permsDe(m.grade).contains(perm);
    }

    /** Reglage individuel d'un membre (HERITE s'il n'en a pas, s'il n'est pas membre, ou pour le chef). */
    public Perm.Etat etatPerm(UUID uuid, Perm perm) {
        Membre m = uuid == null || perm == null ? null : membres.get(uuid);
        if (m == null || m.grade == Grade.CHEF || estChef(uuid)) return Perm.Etat.HERITE;
        return m.etatPerm(perm);
    }

    /** Permissions effectives d'un membre (vide pour un non-membre, toutes pour le chef). */
    public EnumSet<Perm> permsEffectives(UUID uuid) {
        EnumSet<Perm> s = EnumSet.noneOf(Perm.class);
        for (Perm p : Perm.values()) if (peut(uuid, p)) s.add(p);
        return s;
    }

    /** Alias de {@link #peut} (contrat avec le module territoires). */
    public boolean aPerm(UUID joueur, Perm perm) {
        return peut(joueur, perm);
    }

    /** Retire toute invitation adressee a ce joueur ; vrai si quelque chose a change. */
    public boolean retirerInvitation(UUID invite) {
        return invitations.removeIf(i -> i.invite().equals(invite));
    }

    /** Totaux des stats des membres. */
    public Stats totaux() {
        Stats t = new Stats();
        for (Membre m : membres.values()) t.ajouter(m.stats);
        return t;
    }

    // ------------------------------------------------------------------ arbre d'ameliorations
    public int palier(Arbre.Branche b) {
        return Arbre.palier(arbre, b);
    }

    public int membresMax() {
        return Arbre.membresMax(palier(Arbre.Branche.MEMBRES));
    }

    public int territoiresMax() {
        return Arbre.territoiresMax(palier(Arbre.Branche.TERRITOIRES));
    }

    /** Points de competence pas encore depenses. */
    public int pointsLibres() {
        return Math.max(0, Arbre.pointsGagnes(niveau) - Arbre.pointsDepenses(arbre));
    }

    /** XP totale (somme des paliers franchis + xp dans le niveau courant), pour le classement. */
    public long xpTotale() {
        return Niveaux.xpCumulee(niveau) + xp;
    }

    @Nullable
    public Invitation invitationDe(UUID invite) {
        for (Invitation i : invitations) if (i.invite().equals(invite)) return i;
        return null;
    }

    /** Retire les invitations expirees ; vrai si quelque chose a change. */
    public boolean purgerInvitations(long maintenant) {
        return invitations.removeIf(i -> i.expiree(maintenant));
    }

    /** Statistiques du clan sur une zone (creees au besoin). */
    public StatZone statZone(String zoneId) {
        return statsZones.computeIfAbsent(zoneId, k -> new StatZone());
    }

    public void ajouterMouvement(Mouvement m) {
        journalBanque.add(0, m);
        while (journalBanque.size() > Mouvement.MAX) journalBanque.remove(journalBanque.size() - 1);
    }

    public void ajouterEvenement(Evenement e) {
        journal.add(0, e);
        while (journal.size() > Evenement.MAX) journal.remove(journal.size() - 1);
    }

    // ------------------------------------------------------------------ JSON
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id.toString());
        o.addProperty("tag", tag);
        o.addProperty("nom", nom);
        o.addProperty("description", description);
        o.add("banniere", banniere.toJson());
        o.addProperty("chef", chef.toString());
        o.addProperty("creeLe", creeLe);
        o.addProperty("banque", banque);
        o.addProperty("niveau", niveau);
        o.addProperty("xp", xp);
        JsonArray am = new JsonArray();
        for (Membre m : membres.values()) am.add(m.toJson());
        o.add("membres", am);
        JsonObject ap = new JsonObject();
        for (Grade g : Grade.modifiables()) {
            JsonArray a = new JsonArray();
            for (Perm p : permsDe(g)) a.add(p.name());
            ap.add(g.name(), a);
        }
        o.add("perms", ap);
        // permissions connues a l'ecriture : au chargement, celles qui n'y sont pas recoivent les defauts (migration)
        JsonArray connues = new JsonArray();
        for (Perm p : Perm.values()) connues.add(p.name());
        o.add("permsConnues", connues);
        JsonObject cd = new JsonObject();
        cd.addProperty("nom", dernierChgtNom);
        cd.addProperty("tag", dernierChgtTag);
        cd.addProperty("description", dernierChgtDesc);
        cd.addProperty("banniere", dernierChgtBanniere);
        o.add("derniersChangements", cd);
        o.add("journalBanque", liste(journalBanque, Mouvement::toJson));
        o.add("quetesJour", liste(quetesJour, QueteEtat::toJson));
        o.add("quetesSemaine", liste(quetesSemaine, QueteEtat::toJson));
        o.addProperty("prochainResetJour", prochainResetJour);
        o.addProperty("prochainResetSemaine", prochainResetSemaine);
        o.addProperty("depotNetJour", depotNetJour);
        o.addProperty("depotNetSemaine", depotNetSemaine);
        o.add("invitations", liste(invitations, Invitation::toJson));
        o.add("journal", liste(journal, Evenement::toJson));
        o.addProperty("clashwarJoues", clashwarJoues);
        o.addProperty("clashwarGagnes", clashwarGagnes);
        o.addProperty("minutesZonesAPayer", minutesZonesAPayer);
        o.addProperty("minutesValeurAConvertir", minutesValeurAConvertir);
        o.addProperty("pointsTerritoire", pointsTerritoire);
        JsonObject sz = new JsonObject();
        for (Map.Entry<String, StatZone> e : statsZones.entrySet()) {
            JsonObject z = new JsonObject();
            z.addProperty("captures", e.getValue().captures);
            z.addProperty("minutes", e.getValue().minutes);
            sz.add(e.getKey(), z);
        }
        o.add("statsZones", sz);
        JsonObject ar = new JsonObject();
        for (Map.Entry<Arbre.Branche, Integer> e : arbre.entrySet()) ar.addProperty(e.getKey().id(), e.getValue());
        o.add("arbre", ar);
        return o;
    }

    private static <T> JsonArray liste(List<T> l, java.util.function.Function<T, JsonObject> f) {
        JsonArray a = new JsonArray();
        for (T t : l) a.add(f.apply(t));
        return a;
    }

    /** Null si l'entree est inutilisable (id ou chef manquant). */
    @Nullable
    public static Clan fromJson(JsonObject o) {
        UUID id = Json.uuid(o, "id"), chef = Json.uuid(o, "chef");
        if (id == null || chef == null) return null;
        Clan c = new Clan(id, Json.str(o, "tag", "????"), Json.str(o, "nom", "Sans nom"), chef, Json.lng(o, "creeLe", 0));
        c.description = Json.str(o, "description", "");
        c.banniere = Banniere.fromJson(Json.obj(o, "banniere"));
        c.banque = Json.lng(o, "banque", 0);
        c.niveau = Math.max(1, Math.min(Niveaux.MAX, Json.integer(o, "niveau", 1)));
        c.xp = Json.lng(o, "xp", 0);
        for (JsonElement e : Json.arr(o, "membres")) {
            if (!e.isJsonObject()) continue;
            Membre m = Membre.fromJson(e.getAsJsonObject());
            if (m != null) c.membres.put(m.uuid, m);
        }
        JsonObject ap = Json.obj(o, "perms");
        // permissions que le fichier connaissait (absent = ancien format : les 14 premieres) ; les autres sont nouvelles
        EnumSet<Perm> connues = EnumSet.noneOf(Perm.class);
        if (o.has("permsConnues")) {
            for (JsonElement e : Json.arr(o, "permsConnues")) {
                Perm p = e.isJsonPrimitive() ? Perm.parNom(e.getAsString()) : null;
                if (p != null) connues.add(p);
            }
        } else {
            connues.addAll(Perm.HISTORIQUE_V1);
        }
        for (Grade g : Grade.modifiables()) {
            if (!ap.has(g.name())) continue;
            EnumSet<Perm> s = EnumSet.noneOf(Perm.class);
            for (JsonElement e : Json.arr(ap, g.name())) {
                Perm p = e.isJsonPrimitive() ? Perm.parNom(e.getAsString()) : null;
                if (p != null) s.add(p);
            }
            // migration : une permission ajoutee depuis l'ecriture prend la valeur par defaut du grade
            for (Perm p : g.permsParDefaut()) if (!connues.contains(p)) s.add(p);
            c.perms.put(g, s);
        }
        JsonObject cd = Json.obj(o, "derniersChangements");
        c.dernierChgtNom = Json.lng(cd, "nom", 0);
        c.dernierChgtTag = Json.lng(cd, "tag", 0);
        c.dernierChgtDesc = Json.lng(cd, "description", 0);
        c.dernierChgtBanniere = Json.lng(cd, "banniere", 0);
        for (JsonElement e : Json.arr(o, "journalBanque")) if (e.isJsonObject()) c.journalBanque.add(Mouvement.fromJson(e.getAsJsonObject()));
        for (JsonElement e : Json.arr(o, "quetesJour")) if (e.isJsonObject()) c.quetesJour.add(QueteEtat.fromJson(e.getAsJsonObject()));
        for (JsonElement e : Json.arr(o, "quetesSemaine")) if (e.isJsonObject()) c.quetesSemaine.add(QueteEtat.fromJson(e.getAsJsonObject()));
        c.prochainResetJour = Json.lng(o, "prochainResetJour", 0);
        c.prochainResetSemaine = Json.lng(o, "prochainResetSemaine", 0);
        c.depotNetJour = Math.max(0, Json.lng(o, "depotNetJour", 0));
        c.depotNetSemaine = Math.max(0, Json.lng(o, "depotNetSemaine", 0));
        for (JsonElement e : Json.arr(o, "invitations")) {
            if (!e.isJsonObject()) continue;
            Invitation i = Invitation.fromJson(e.getAsJsonObject());
            if (i != null) c.invitations.add(i);
        }
        for (JsonElement e : Json.arr(o, "journal")) if (e.isJsonObject()) c.journal.add(Evenement.fromJson(e.getAsJsonObject()));
        c.clashwarJoues = Math.max(0, Json.integer(o, "clashwarJoues", 0));
        c.clashwarGagnes = Math.max(0, Math.min(c.clashwarJoues, Json.integer(o, "clashwarGagnes", 0)));
        c.minutesZonesAPayer = Math.max(0, Json.lng(o, "minutesZonesAPayer", 0));
        c.minutesValeurAConvertir = Math.max(0, Json.lng(o, "minutesValeurAConvertir", 0));
        c.pointsTerritoire = Math.max(0, Json.lng(o, "pointsTerritoire", 0));
        JsonObject sz = Json.obj(o, "statsZones");
        for (Map.Entry<String, JsonElement> e : sz.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            StatZone s = new StatZone();
            s.captures = Math.max(0, Json.integer(e.getValue().getAsJsonObject(), "captures", 0));
            s.minutes = Math.max(0, Json.lng(e.getValue().getAsJsonObject(), "minutes", 0));
            c.statsZones.put(e.getKey(), s);
        }
        JsonObject ar = Json.obj(o, "arbre");
        for (Map.Entry<String, JsonElement> e : ar.entrySet()) {
            Arbre.Branche b = Arbre.Branche.parId(e.getKey());
            if (b != null && e.getValue().isJsonPrimitive()) c.arbre.put(b, Math.max(0, Math.min(b.max, e.getValue().getAsInt())));
        }
        // le chef est toujours membre avec le grade CHEF (et n'a pas de reglages individuels : il a tout)
        Membre chefM = c.membres.get(chef);
        if (chefM != null) {
            chefM.grade = Grade.CHEF;
            chefM.permsJoueur.clear();
        }
        return c;
    }
}
