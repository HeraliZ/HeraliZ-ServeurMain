package fr.heraliz.noyau.serveur.diag.profil;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * À qui appartient un cadre de pile du serveur (profil, 01/11) : modules du dépôt, mods des autres auteurs, plugins
 * Bukkit, Mohist, Forge, Minecraft ou Java. D'abord par le paquet Java de la classe (le plus long début connu de la table),
 * sinon par le module de la machine Java (Forge nomme le module d'un jar de mod d'après son premier identifiant de mod),
 * sinon par les trois premiers mots du paquet.
 * <p>
 * Les méthodes ajoutées par un mixin portent le nom de la classe de Minecraft visée (« ServerLevel.handler$zza000$… ») :
 * elles sont reconnues à leur nom, puis rendues à leur mod par {@code proprietaireMixin} (l'annotation que Mixin pose sur
 * chaque méthode fusionnée, lue côté serveur), sinon à « mixin d'un mod ».
 * <p>
 * Une instance par analyse (cache par classe), un seul fil à la fois. Aucune classe de Minecraft : vérifiable hors du jeu.
 */
public final class Origines {
    /** Genre d'un cadre ; {@link #rang()} sert à désigner le responsable d'un relevé. */
    public enum Genre {
        NOUS("modules du dépôt"), MOD("mods des autres auteurs"), PLUGIN("plugins Bukkit"), MOHIST("Mohist (Bukkit)"),
        FORGE("Forge"), MINECRAFT("Minecraft"), JAVA("Java et bibliothèques");

        public final String libelle;

        Genre(String libelle) {
            this.libelle = libelle;
        }

        /**
         * Le responsable d'un relevé est le cadre le plus haut de la pile (le plus près de ce qui tournait) parmi ceux du
         * rang le plus fort présent : le code d'un mod ou d'un plugin (3) passe devant Mohist et Forge (2), qui passent
         * devant Minecraft (1), puis Java (0). Une boucle de Minecraft qui appelle notre code compte pour nous ; notre code
         * qui appelle Minecraft (getEntities…) compte aussi pour nous, à la ligne de notre appel.
         */
        public int rang() {
            return switch (this) {
                case NOUS, MOD, PLUGIN -> 3;
                case MOHIST, FORGE -> 2;
                case MINECRAFT -> 1;
                case JAVA -> 0;
            };
        }
    }

    /**
     * {@code nom} : nom affiché (« monde · heralisz_utils », « TaCZ », « plugin AuthMe ») ; {@code module} : dossier
     * {@code modules/<module>} du dépôt pour le code qui s'y trouve, sinon null ; {@code mixin} : méthode fusionnée par un
     * mixin dans une classe d'un autre (son nom de classe Java est alors celui du mixin, s'il est connu).
     */
    public record Origine(String nom, Genre genre, String module, String mixin) {
    }

    /** début du paquet, nom affiché, genre, dossier du module (code du dépôt) */
    private static final String[][] TABLE = {
            // ---- modules du dépôt (modules/<module>) : notre code et celui d'autres auteurs que nous y gardons
            {"fr.heraliz.noyau", "noyau · heraliz_noyau", "NOUS", "noyau"},
            {"fr.heraliz.tactique", "armes · heraliz_tactique", "NOUS", "armes"},
            {"fr.heralisz.tactical", "armes · heralisz_tactical (LR Tactical)", "NOUS", "armes"},
            {"fr.heralisz.camo", "armes · heralisz_camo", "NOUS", "armes"},
            {"fr.heralisz.grenade", "armes · heralisz_grenade", "NOUS", "armes"},
            {"fr.heralisz.melee", "armes · heralisz_melee", "NOUS", "armes"},
            {"fr.heralisz.heal", "armes · heralisz_heal", "NOUS", "armes"},
            {"fr.heraliz.armory", "equipement · heraliz_armory", "NOUS", "equipement"},
            {"fr.heraliz.composants", "equipement · heraliz_composants", "NOUS", "equipement"},
            {"com.mrcrayfish.backpacked", "sacs · backpacked (Backpacked)", "NOUS", "sacs"},
            {"fr.heraliz.loot", "loot · heraliz_loot", "NOUS", "loot"},
            {"fr.heralisz.bunker", "bunker · heralisz_bunker", "NOUS", "bunker"},
            {"com.hbm_m", "bunker · HBM Modernized", "NOUS", "bunker"},
            {"fr.heralisz.utils", "monde · heralisz_utils", "NOUS", "monde"},
            {"fr.heraliz.airstrike", "monde · airstrike", "NOUS", "monde"},
            {"fr.heraliz.usure", "monde · heraliz_usure", "NOUS", "monde"},
            {"com.heraliz.extraction", "monde · heraliz_extraction", "NOUS", "monde"},
            {"fr.heraliz.ping", "monde · heraliz_ping", "NOUS", "monde"},
            {"fr.heraliz.corps", "monde · heraliz_corps", "NOUS", "monde"},
            {"fr.heraliz.decors", "monde · heraliz_decors", "NOUS", "monde"},
            {"fr.heraliz.vehicules", "monde · heraliz_vehicules", "NOUS", "monde"},
            {"fr.heraliz.carte", "monde · heraliz_carte", "NOUS", "monde"},
            {"fr.heraliz.reanimation", "survie · reanimation", "NOUS", "survie"},
            {"fr.heralisz.clans", "clans · heralisz_clans", "NOUS", "clans"},
            {"fr.heralisz.economie", "clans · heralisz_economie", "NOUS", "clans"},
            {"fr.heralisz.bases", "clans · heralisz_bases", "NOUS", "clans"},
            {"fr.heraliz.clansetbases", "clans · heraliz_clansetbases", "NOUS", "clans"},
            {"fr.heraliz.crac", "bases · crac", "NOUS", "bases"},
            {"fr.heraliz.boss", "events · heraliz_boss", "NOUS", "events"},
            {"fr.heralisz.n07", "events · heralisz_n07", "NOUS", "events"},
            {"fr.heralisz.mammoth", "events · heralisz_mammoth", "NOUS", "events"},
            {"fr.heralisz.sylvorn", "events · heralisz_sylvorn", "NOUS", "events"},
            {"fr.heralisz.plague", "events · heralisz_plague", "NOUS", "events"},
            {"fr.heralisz.mere", "events · heralisz_mere", "NOUS", "events"},
            {"fr.heraliz.clashwar", "clashwar · heraliz_clashwar", "NOUS", "clashwar"},
            {"fr.heraliz.evenements", "evenements · heraliz_evenements", "NOUS", "evenements"},
            {"fr.heraliz.quetes", "pnj · heraliz_quetes", "NOUS", "pnj"},
            {"fr.heraliz.grades", "boutique · heraliz_grades", "NOUS", "boutique"},
            {"fr.heraliz.casino", "boutique · heralizcasino", "NOUS", "boutique"},
            {"fr.heraliz.crates", "boutique · heralizcrates", "NOUS", "boutique"},
            {"fr.heraliz.emote", "emotes · heraliz_emote", "NOUS", "emotes"},
            {"com.caracrea", "voix · caracrea", "NOUS", "voix"},
            {"fr.heraliz.interfaces", "interface · heraliz_interface", "NOUS", "interface"},
            {"com.dayzhotbar", "interface · dayzhotbar", "NOUS", "interface"},
            {"com.Spartan", "anticheat · spartan", "NOUS", "anticheat"},
            {"fr.heraliz.banc", "banc · heraliz_banc", "NOUS", "banc"},
            {"fr.heraliz.capture", "capture · heraliz_capture", "NOUS", "capture"},
            // ---- mods des autres auteurs (serveur/mods-tiers, ou embarqués dans un de nos jars)
            {"com.tacz.guns", "TaCZ", "MOD", null},
            {"com.mafuyu404.taczaddon", "TaCZ Addon", "MOD", null},
            {"top.theillusivec4.curios", "Curios", "MOD", null},
            {"software.bernie", "GeckoLib", "MOD", null},
            {"net.petemc.mutantszombies", "Mutants & Zombies", "MOD", null},
            {"com.corosus.zombieawareness", "Zombie Awareness", "MOD", null},
            {"com.corosus", "CoroUtil", "MOD", null},
            {"org.ywzj", "ywzj (véhicules)", "MOD", null},
            {"com.mrcrayfish.furniture", "cfm (meubles de MrCrayfish)", "MOD", null},
            {"com.mrcrayfish.framework", "Framework (MrCrayfish)", "MOD", null},
            {"com.mrcrayfish.controllable", "Controllable", "MOD", null},
            {"snownee.passablefoliage", "Passable Foliage", "MOD", null},
            {"snownee.kiwi", "Kiwi", "MOD", null},
            {"net.tct.matmos", "MAtmos", "MOD", null},
            {"net.thetct.tctcore", "TCTcore", "MOD", null},
            {"me.shedaniel", "Cloth Config", "MOD", null},
            {"malte0811.ferritecore", "FerriteCore", "MOD", null},
            {"org.embeddedt.modernfix", "ModernFix", "MOD", null},
            {"ca.fxco.memoryleakfix", "MemoryLeakFix", "MOD", null},
            {"dev.tonimatas.packetfixer", "PacketFixer", "MOD", null},
            {"dev.kosmx.playerAnim", "Player Animator", "MOD", null},
            {"io.github.kosmx.emotes", "Emotecraft", "MOD", null},
            {"de.maxhenkel.voicechat", "Simple Voice Chat", "MOD", null},
            // ---- plugins Bukkit (serveur/plugins)
            {"fr.xephi.authme", "plugin AuthMe", "PLUGIN", null},
            {"me.lucko.luckperms", "plugin LuckPerms", "PLUGIN", null},
            {"net.luckperms", "plugin LuckPerms", "PLUGIN", null},
            {"litebans", "plugin LiteBans", "PLUGIN", null},
            {"com.sk89q.worldguard", "plugin WorldGuard", "PLUGIN", null},
            {"net.goldtreeservers.worldguardextraflags", "plugin WorldGuardExtraFlags", "PLUGIN", null},
            {"com.sk89q.worldedit", "plugin WorldEdit", "PLUGIN", null},
            {"org.enginehub", "plugin WorldEdit", "PLUGIN", null},
            {"me.lucko.spark", "plugin spark", "PLUGIN", null},
            {"dev.brighten.antivpn", "plugin KauriVPN", "PLUGIN", null},
            {"net.craftersland.consolefix", "plugin ConsoleSpamFix", "PLUGIN", null},
            {"com.azuriom", "plugin AzLink", "PLUGIN", null},
            // ---- le serveur
            {"com.mohistmc", "Mohist", "MOHIST", null},
            {"org.bukkit.craftbukkit", "Mohist (CraftBukkit)", "MOHIST", null},
            {"org.bukkit", "Mohist (API Bukkit)", "MOHIST", null},
            {"org.spigotmc", "Mohist (Spigot)", "MOHIST", null},
            {"io.papermc", "Mohist (Paper)", "MOHIST", null},
            {"com.destroystokyo.paper", "Mohist (Paper)", "MOHIST", null},
            {"net.md_5", "Mohist (BungeeCord)", "MOHIST", null},
            {"net.minecraftforge.eventbus", "Forge (bus d'événements)", "FORGE", null},
            {"net.minecraftforge", "Forge", "FORGE", null},
            {"cpw.mods", "Forge (chargeur)", "FORGE", null},
            {"org.spongepowered", "Mixin", "FORGE", null},
            {"com.llamalad7.mixinextras", "MixinExtras", "FORGE", null},
            {"net.minecraft", "Minecraft", "MINECRAFT", null},
            {"com.mojang.brigadier", "Minecraft (commandes)", "MINECRAFT", null},
            {"com.mojang.datafixers", "Minecraft (DataFixerUpper)", "MINECRAFT", null},
            {"com.mojang.serialization", "Minecraft (DataFixerUpper)", "MINECRAFT", null},
            {"com.mojang", "Minecraft", "MINECRAFT", null},
            // ---- Java et bibliothèques
            {"java", "Java", "JAVA", null},
            {"javax", "Java", "JAVA", null},
            {"jdk", "Java", "JAVA", null},
            {"sun", "Java", "JAVA", null},
            {"com.sun", "Java", "JAVA", null},
            {"io.netty", "Netty (réseau)", "JAVA", null},
            {"it.unimi.dsi.fastutil", "fastutil", "JAVA", null},
            {"com.google", "Guava / Gson", "JAVA", null},
            {"org.apache", "Apache (journal…)", "JAVA", null},
            {"org.slf4j", "journal (slf4j)", "JAVA", null},
            {"org.joml", "JOML", "JAVA", null},
            {"com.electronwill", "NightConfig (réglages)", "JAVA", null},
            {"org.objectweb.asm", "ASM", "JAVA", null},
    };
    /** Préfixes des méthodes que Mixin ajoute (injecteurs de Mixin et de MixinExtras). */
    private static final Set<String> PREFIXES_MIXIN = Set.of("handler", "redirect", "modify", "localvar", "constant", "args",
            "wrapOperation", "wrapWithCondition", "wrapMethod", "modifyExpressionValue", "modifyReturnValue", "modifyReceiver",
            "modifyVariable", "modifyArg", "modifyArgs", "inject");
    private static final String[][] TRIEE = Arrays.stream(TABLE).sorted(Comparator.comparingInt((String[] l) -> -l[0].length()))
            .toArray(String[][]::new);

    private final Map<String, Origine> parClasse = new HashMap<>();
    private final Map<String, Origine> parMixin = new HashMap<>();
    private final BiFunction<String, String, String> proprietaireMixin;

    /** {@code proprietaireMixin} : (classe, méthode) -> nom de la classe du mixin qui l'a ajoutée, ou null (peut être null). */
    public Origines(BiFunction<String, String, String> proprietaireMixin) {
        this.proprietaireMixin = proprietaireMixin;
    }

    /** Origine du cadre (classe Java, module de la machine Java ou null, méthode). */
    public Origine de(String classe, String module, String methode) {
        Origine o = deClasse(classe, module);
        if (o.genre.rang() < 3 && methodeDeMixin(methode)) return mixin(classe, methode, o);
        return o;
    }

    public Origine deClasse(String classe, String module) {
        Origine o = parClasse.get(classe);
        if (o == null) {
            o = chercher(classe, module);
            parClasse.put(classe, o);
        }
        return o;
    }

    private static Origine chercher(String classe, String module) {
        for (String[] l : TRIEE) {
            String p = l[0];
            if (classe.startsWith(p) && (classe.length() == p.length() || classe.charAt(p.length()) == '.' || classe.charAt(p.length()) == '$')) {
                return new Origine(l[1], Genre.valueOf(l[2]), l[3], null);
            }
        }
        if (module != null && !module.isEmpty() && !module.startsWith("java.") && !module.startsWith("jdk.")) {
            return new Origine("mod " + module, Genre.MOD, null, null);
        }
        String[] mots = classe.split("\\.");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(3, mots.length - 1); i++) b.append(i > 0 ? "." : "").append(mots[i]);
        return new Origine("inconnu : " + (b.length() == 0 ? classe : b), Genre.MOD, null, null);
    }

    private Origine mixin(String classe, String methode, Origine cible) {
        String cle = classe + '#' + methode;
        Origine o = parMixin.get(cle);
        if (o != null) return o;
        String m = null;
        try {
            m = proprietaireMixin == null ? null : proprietaireMixin.apply(classe, methode);
        } catch (RuntimeException | LinkageError e) {
            // classe illisible : mixin sans propriétaire connu
        }
        if (m != null) {
            Origine p = chercher(m, null);
            o = new Origine(p.nom, p.genre.rang() < 3 ? Genre.MOD : p.genre, p.module, m);
        } else if (methode.contains("heraliz$") || methode.contains("heralisz$")) {
            o = new Origine("mixin d'un module du dépôt (dans " + cible.nom + ")", Genre.NOUS, null, "?");
        } else {
            o = new Origine("mixin d'un mod (dans " + cible.nom + ")", Genre.MOD, null, "?");
        }
        parMixin.put(cle, o);
        return o;
    }

    /**
     * Méthode ajoutée par Mixin : « handler$zza000$nom », « redirect$zzb000$nom », « md1a2b3c$… » (méthode unique
     * renommée), ou nos conventions « heraliz$… » / « heralisz$… ». Les lambdas et accès de javac (« lambda$… »,
     * « access$… ») n'en sont pas.
     */
    public static boolean methodeDeMixin(String m) {
        int d = m.indexOf('$');
        if (d <= 0) return false;
        String p = m.substring(0, d);
        if (p.equals("lambda") || p.equals("access")) return false;
        if (p.equals("heraliz") || p.equals("heralisz")) return true;
        if (PREFIXES_MIXIN.contains(p)) return true;
        if (p.length() >= 6 && p.startsWith("md") && hexa(p, 2)) return true;
        // « prefixe$zza000$… » : identifiant unique de Mixin juste après le préfixe
        int d2 = m.indexOf('$', d + 1);
        return d2 == d + 7 && Character.isLowerCase(m.charAt(d + 1)) && Character.isDigit(m.charAt(d + 4))
                && Character.isDigit(m.charAt(d + 5)) && Character.isDigit(m.charAt(d + 6));
    }

    private static boolean hexa(String s, int depuis) {
        for (int i = depuis; i < s.length(); i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
        return true;
    }

    /** Cadre d'attente : le fil serveur dort (rien à faire, ou il attend un tronçon du disque, un verrou, un autre fil). */
    public static boolean attente(String classe, String methode) {
        return switch (classe) {
            case "jdk.internal.misc.Unsafe", "sun.misc.Unsafe" -> methode.equals("park");
            case "java.lang.Thread" -> methode.startsWith("sleep") || methode.startsWith("yield") || methode.equals("onSpinWait");
            case "java.lang.Object" -> methode.equals("wait") || methode.equals("wait0");
            case "java.util.concurrent.locks.LockSupport" -> methode.startsWith("park");
            default -> false;
        };
    }

    /**
     * Chemin du fichier source tel qu'il est dans le dépôt, sous {@code modules/<module>/src/<partie>/java/} :
     * « fr/heralisz/utils/zombies/IaZombies.java:212 » ; pour les autres : « IaZombies.java:212 ».
     */
    public static String fichier(String classe, String fichier, int ligne, boolean avecPaquet) {
        String f = fichier;
        if (f == null) {
            int p = classe.lastIndexOf('.');
            String simple = p >= 0 ? classe.substring(p + 1) : classe;
            int d = simple.indexOf('$');
            f = (d > 0 ? simple.substring(0, d) : simple) + ".java";
        }
        if (avecPaquet) {
            int p = classe.lastIndexOf('.');
            if (p > 0) f = classe.substring(0, p).replace('.', '/') + '/' + f;
        }
        return ligne >= 0 ? f + ":" + ligne : f;
    }

    /** « IaZombies.cibler » (classe sans paquet, classes internes gardées). */
    public static String court(String classe, String methode) {
        int p = classe.lastIndexOf('.');
        return (p >= 0 ? classe.substring(p + 1) : classe) + "." + methode;
    }
}
