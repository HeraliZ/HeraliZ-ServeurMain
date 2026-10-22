package fr.heraliz.grades;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Les grades HeraliZ. Depuis la 2.0.0 (22/10, demande du propriétaire), ils se créent et se règlent EN JEU (éditeur des op,
 * « Registre des grades ») : nom en 3 langues, couleur, famille, rang d'affichage, permissions ({@link PermsGrades}) et kits.
 * Le serveur garde ces définitions hors du monde ({@code heraliz_grades/definitions.json}) et les envoie aux joueurs
 * ({@code ReseauGrades.Definitions}) : chaque grade garde le même objet (on le met à jour sur place), donc les constantes
 * ci-dessous restent valables.
 * <p>
 * Un joueur a au plus un grade du staff, au plus un grade de la boutique, et autant de grades spéciaux (Streamer…) qu'on
 * veut ; Survivant est le grade de tout le monde, Soutien vient du tag HeraliZ sur Discord. Les grades « internes »
 * (ceux d'origine) ne se suppriment pas et ne changent pas de famille : d'autres mods en dépendent.
 */
public final class Grade {
   /** Familles : un grade de chaque, sauf SPÉCIAL (autant qu'on veut). */
   public enum Type { STAFF, BOUTIQUE, SPECIAL, BASE, SOUTIEN }

   private static final Map<String, Grade> TOUS = new LinkedHashMap<>();
   /** Langue du jeu chez le joueur (branchée par la partie client) ; null côté serveur : français. */
   public static volatile Supplier<String> LANGUE;

   public static final Grade FONDATEUR = interne("fondateur", "Fondateur", "Founder", "Основатель", 0xE5484D, Type.STAFF, 3, 100);
   public static final Grade MODERATEUR = interne("moderateur", "Modérateur", "Moderator", "Модератор", 0xB48CFF, Type.STAFF, 2, 90);
   public static final Grade HELPER = interne("helper", "Helper", "Helper", "Хелпер", 0x5FD08A, Type.STAFF, 1, 80);
   public static final Grade STREAMER = interne("streamer", "Streamer", "Streamer", "Стример", 0x9146FF, Type.SPECIAL, 1, 50);
   public static final Grade ROI_DE_LA_ZONE = interne("roi_de_la_zone", "Roi de la Zone", "King of the Zone", "Король Зоны", 0xFFC23A, Type.BOUTIQUE, 3, 30);
   public static final Grade TRAQUEUR = interne("traqueur", "Traqueur", "Tracker", "Следопыт", 0x8FD3FF, Type.BOUTIQUE, 2, 20);
   public static final Grade RESCAPE = interne("rescape", "Rescapé", "Survivor+", "Уцелевший", 0xE39B5E, Type.BOUTIQUE, 1, 10);
   public static final Grade SURVIVANT = interne("survivant", "Survivant", "Survivor", "Выживший", 0xA0A6B0, Type.BASE, 0, 0);
   /** Pour ceux qui portent le tag HeraliZ sur Discord (compte lié) : ses kits, jamais de préfixe. */
   public static final Grade SOUTIEN = interne("soutien", "Soutien Discord", "Discord Supporter", "Поддержка Discord", 0xF5C842, Type.SOUTIEN, 0, 0);

   static {
      STREAMER.colonneStaff = false;
      SURVIVANT.gras = false;
      SOUTIEN.prefixe = false;
      PermsGrades.defauts();
   }

   public final String id;
   /** Grade d'origine : ni supprimé, ni changé de famille. */
   public final boolean interne;
   public String nomFr, nomEn, nomRu;
   public int couleur;
   public Type type;
   /** Niveau dans sa famille (staff : 3 Fondateur, 2 Modérateur, 1 Helper ; boutique : 1 à 3), lu par les autres mods. */
   public int rang;
   /** Rang d'affichage (tab, préfixe gardé quand on a plusieurs grades) : plus grand = devant. */
   public int ordre;
   public boolean gras = true, prefixe = true, colonneStaff;
   /** Permissions réglées pour ce grade (clé de {@link PermsGrades}) ; une clé absente ne compte pas. */
   public final Map<String, Integer> perms = new LinkedHashMap<>();

   private Grade(String id, boolean interne) {
      this.id = id;
      this.interne = interne;
   }

   private static Grade interne(String id, String fr, String en, String ru, int couleur, Type type, int rang, int ordre) {
      Grade g = new Grade(id, true);
      g.nomFr = fr;
      g.nomEn = en;
      g.nomRu = ru;
      g.couleur = couleur;
      g.type = type;
      g.rang = rang;
      g.ordre = ordre;
      g.colonneStaff = type == Type.STAFF;
      TOUS.put(id, g);
      return g;
   }

   /** Nouveau grade créé en jeu (identifiant libre et valide : voir {@link #idValide}). */
   public static synchronized Grade creer(String id) {
      Grade g = new Grade(id, false);
      g.nomFr = g.nomEn = g.nomRu = id;
      g.couleur = 0xA0A6B0;
      g.type = Type.SPECIAL;
      g.rang = 1;
      g.ordre = 40;
      TOUS.put(id, g);
      return g;
   }

   /** Retire un grade créé en jeu (jamais un grade interne). */
   public static synchronized boolean supprimer(String id) {
      Grade g = TOUS.get(id);
      if (g == null || g.interne) return false;
      TOUS.remove(id);
      return true;
   }

   /** Identifiant d'un nouveau grade : 2 à 24 lettres minuscules, chiffres ou _. */
   public static boolean idValide(String id) {
      return id != null && id.matches("[a-z0-9_]{2,24}");
   }

   /** Grade par identifiant (« traqueur »...), null si inconnu. */
   public static synchronized Grade parId(String id) {
      return id == null ? null : TOUS.get(id.toLowerCase(Locale.ROOT));
   }

   /** Tous les grades, du plus haut au plus bas (rang d'affichage). */
   public static synchronized List<Grade> values() {
      List<Grade> l = new ArrayList<>(TOUS.values());
      l.sort(Comparator.comparingInt((Grade g) -> -g.ordre).thenComparing(g -> g.id));
      return Collections.unmodifiableList(l);
   }

   /** Vrai si ce grade se donne à la console (staff, boutique, spécial) : Survivant et Soutien ne se donnent pas. */
   public boolean attribuable() {
      return type == Type.STAFF || type == Type.BOUTIQUE || type == Type.SPECIAL;
   }

   /** Nom dans la langue du jeu (joueur) ; français côté serveur. */
   public String nomLocal() {
      Supplier<String> l = LANGUE;
      String code = l == null ? "fr_fr" : l.get();
      if (code != null && code.startsWith("ru") && nomRu != null && !nomRu.isBlank()) return nomRu;
      if (code != null && !code.startsWith("fr") && nomEn != null && !nomEn.isBlank()) return nomEn;
      return nomFr;
   }

   /** Nom du grade, en couleur. */
   public MutableComponent nom() {
      return Component.literal(nomLocal()).withStyle(st -> st.withColor(TextColor.fromRgb(couleur)));
   }

   /** Nom du grade, sans couleur. */
   public MutableComponent nomSansCouleur() {
      return Component.literal(nomLocal());
   }

   /** Préfixe placé devant le pseudo : le nom en capitales, en couleur, en gras si réglé (« TRAQUEUR Pseudo »). */
   public MutableComponent prefixe() {
      return Component.literal(nomFr.toUpperCase(Locale.ROOT)).withStyle(st -> st.withColor(TextColor.fromRgb(couleur)).withBold(gras))
            .append(Component.literal(" ").withStyle(st -> st.withBold(false)));
   }

   /** Le même préfixe en codes couleur Bukkit (§x§R§R§G§G§B§B), pour le nom affiché dans le chat des serveurs hybrides. */
   public String prefixeBukkit() {
      return couleurBukkit(couleur) + (gras ? "§l" : "") + nomFr.toUpperCase(Locale.ROOT) + "§r ";
   }

   /** Code couleur Bukkit d'une couleur RGB (§x§R§R§G§G§B§B). */
   public static String couleurBukkit(int rgb) {
      StringBuilder sb = new StringBuilder("§x");
      for (char c : String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF).toCharArray()) sb.append('§').append(c);
      return sb.toString();
   }

   // ------------------------------------------------------------------ réseau (définitions envoyées aux joueurs)
   private static final int MAX_NOM = 32, MAX_GRADES = 64, MAX_PERMS = 32;

   /** Écrit l'apparence et les permissions de tous les grades. */
   public static synchronized void ecrireTous(FriendlyByteBuf b) {
      List<Grade> l = values();
      int n = Math.min(l.size(), MAX_GRADES);
      b.writeVarInt(n);
      for (int i = 0; i < n; i++) l.get(i).ecrire(b);
   }

   private void ecrire(FriendlyByteBuf b) {
      ecrireDef(b, def());
   }

   /** Écrit une définition. */
   public static void ecrireDef(FriendlyByteBuf b, Def d) {
      b.writeUtf(d.id(), 32);
      b.writeBoolean(d.interne());
      b.writeUtf(d.fr(), MAX_NOM);
      b.writeUtf(d.en(), MAX_NOM);
      b.writeUtf(d.ru(), MAX_NOM);
      b.writeInt(d.couleur());
      b.writeEnum(d.type());
      b.writeVarInt(d.rang());
      b.writeVarInt(d.ordre());
      b.writeBoolean(d.gras());
      b.writeBoolean(d.prefixe());
      b.writeBoolean(d.colonneStaff());
      int np = Math.min(d.perms().size(), MAX_PERMS);
      b.writeVarInt(np);
      d.perms().entrySet().stream().limit(np).forEach(e -> {
         b.writeUtf(e.getKey(), 32);
         b.writeVarInt(e.getValue());
      });
   }

   /** Lit une définition. */
   public static Def lireDef(FriendlyByteBuf b) {
      String id = b.readUtf(32);
      boolean interne = b.readBoolean();
      String fr = b.readUtf(MAX_NOM), en = b.readUtf(MAX_NOM), ru = b.readUtf(MAX_NOM);
      int couleur = b.readInt();
      Type type = b.readEnum(Type.class);
      int rang = b.readVarInt(), ordre = b.readVarInt();
      boolean gras = b.readBoolean(), prefixe = b.readBoolean(), colonne = b.readBoolean();
      int np = Math.min(b.readVarInt(), MAX_PERMS);
      Map<String, Integer> perms = new LinkedHashMap<>();
      for (int j = 0; j < np; j++) perms.put(b.readUtf(32), b.readVarInt());
      return new Def(id, interne, fr, en, ru, couleur, type, rang, ordre, gras, prefixe, colonne, perms);
   }

   /** Une définition lue (pas encore appliquée). */
   public record Def(String id, boolean interne, String fr, String en, String ru, int couleur, Type type, int rang, int ordre, boolean gras,
                     boolean prefixe, boolean colonneStaff, Map<String, Integer> perms) {
   }

   public static List<Def> lireTous(FriendlyByteBuf b) {
      int n = Math.min(b.readVarInt(), MAX_GRADES);
      List<Def> l = new ArrayList<>(n);
      for (int i = 0; i < n; i++) l.add(lireDef(b));
      return l;
   }

   /** Applique des définitions (venues du serveur ou de son fichier) : grades mis à jour sur place, créés, retirés. */
   public static synchronized void appliquer(List<Def> defs, boolean retirerAbsents) {
      java.util.Set<String> vus = new java.util.HashSet<>();
      for (Def d : defs) {
         if (!idValide(d.id())) continue;
         Grade g = TOUS.get(d.id());
         if (g == null) g = creer(d.id());
         vus.add(g.id);
         g.appliquer(d);
      }
      if (retirerAbsents) TOUS.values().removeIf(g -> !g.interne && !vus.contains(g.id));
   }

   /** Met cette définition sur ce grade (la famille d'un grade interne ne change jamais). */
   public void appliquer(Def d) {
      nomFr = propre(d.fr(), nomFr);
      nomEn = propre(d.en(), nomFr);
      nomRu = propre(d.ru(), nomFr);
      couleur = d.couleur() & 0xFFFFFF;
      if (!interne && d.type() != null && d.type() != Type.BASE && d.type() != Type.SOUTIEN) type = d.type();
      if (!interne) rang = Math.max(0, Math.min(9, d.rang()));
      ordre = Math.max(0, Math.min(999, d.ordre()));
      gras = d.gras();
      prefixe = d.prefixe();
      colonneStaff = d.colonneStaff();
      perms.clear();
      d.perms().forEach((k, v) -> {
         PermsGrades.Perm p = PermsGrades.parCle(k);
         if (p != null) perms.put(k, p.borner(v));
      });
      // Survivant porte la valeur de base de chaque permission : jamais de trou
      if (this == SURVIVANT) for (PermsGrades.Perm p : PermsGrades.TOUTES) perms.putIfAbsent(p.cle(), p.defaut());
   }

   private static String propre(String s, String secours) {
      if (s == null) return secours;
      String t = s.replace('§', ' ').strip();
      if (t.length() > MAX_NOM) t = t.substring(0, MAX_NOM);
      return t.isEmpty() ? secours : t;
   }

   /** Définition de ce grade (copie). */
   public Def def() {
      return new Def(id, interne, nomFr, nomEn, nomRu, couleur, type, rang, ordre, gras, prefixe, colonneStaff, new LinkedHashMap<>(perms));
   }
}
