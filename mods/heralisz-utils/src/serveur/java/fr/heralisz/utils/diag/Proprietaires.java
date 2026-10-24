package fr.heralisz.utils.diag;

/**
 * À qui appartient un échantillon de pile du fil du serveur.
 * <ul>
 * <li>{@link #proprietaire} : le mod, le plugin ou la partie du serveur « responsable » : en partant du haut de la pile
 * (la méthode qui tournait), la première méthode d'un mod ou d'un plugin reconnu (les nôtres, ceux des autres auteurs,
 * les plugins Bukkit, Mohist, Forge). Les bibliothèques (Java, fastutil, Guava, Netty, bus d'évènements de Forge,
 * Mixin) sont sautées : elles travaillent pour celui qui les appelle. Un crochet Mixin posé dans une classe de Minecraft
 * (méthode « handler$… ») compte pour le mod du crochet. Rien de tout ça dans la pile : « Minecraft ».</li>
 * <li>{@link #famille} : la grande partie de Minecraft concernée (génération, chargement des tronçons, IA, réseau,
 * sauvegarde…), la plus précise trouvée dans toute la pile. Les noms de classes de Minecraft sont ceux de Mojang en jeu
 * (Forge 1.17+ : classes aux noms officiels, méthodes en noms SRG m_…_), ils se lisent tels quels.</li>
 * </ul>
 * Tables lues seulement (aucun état) : appelées depuis les fils d'échantillonnage, jamais depuis le fil du serveur.
 */
final class Proprietaires {
    private Proprietaires() {
    }

    /** Préfixe de paquet -> nom affiché ; nom null = bibliothèque ou mécanique, à sauter. Le premier préfixe qui va gagne. */
    private static final String[][] PAQUETS = {
            // le diagnostic lui-même (crochets des entités)
            {"fr.heralisz.utils.diag.", "HeraliZ Utils (diagnostic)"},
            // mécanique et bibliothèques : sautées
            {"java.", null}, {"javax.", null}, {"jdk.", null}, {"sun.", null}, {"com.sun.", null},
            {"net.minecraftforge.eventbus.", null}, {"net.minecraftforge.fml.", null}, {"cpw.mods.", null},
            {"org.spongepowered.", null}, {"com.llamalad7.mixinextras.", null},
            {"it.unimi.dsi.", null}, {"com.google.", null}, {"io.netty.", null}, {"org.apache.", null},
            {"org.slf4j.", null}, {"org.objectweb.", null}, {"org.joml.", null}, {"com.electronwill.", null},
            {"kotlin.", null}, {"org.jetbrains.", null}, {"org.checkerframework.", null},
            // nos mods (HeraliZ)
            {"fr.heraliz.crac.", "CRac"},
            {"fr.heralisz.utils.", "HeraliZ Utils"}, {"fr.heraliz.usure.", "HeraliZ Utils (usure)"},
            {"fr.heraliz.ping.", "HeraliZ Utils (ping)"}, {"fr.heraliz.corps.", "HeraliZ Utils (cadavres)"},
            {"fr.heraliz.vehicules.", "HeraliZ Utils (véhicules)"}, {"fr.heraliz.carte.", "HeraliZ Utils (carte)"},
            {"fr.heralisz.matchs.", "HeraliZ Matchs"},
            {"fr.heralisz.n07.", "HeraliZ Boss (N-07)"}, {"fr.heralisz.mammoth.", "HeraliZ Boss (Ymirak)"},
            {"fr.heralisz.sylvorn.", "HeraliZ Boss (Sylvorn)"}, {"fr.heralisz.plague.", "HeraliZ Boss (Porteur de Peste)"},
            {"fr.heralisz.mere.", "HeraliZ Boss (Mère des Ruines)"}, {"fr.heraliz.boss.", "HeraliZ Boss"},
            {"fr.heralisz.clans.", "HeraliZ Clans"}, {"fr.heralisz.economie.", "HeraliZ Clans (hérals)"},
            {"fr.heraliz.clansetbases.", "HeraliZ Clans"}, {"fr.heralisz.bases.", "HeraliZ Clans"},
            {"fr.heralisz.tactical.", "HeraliZ Tactique (moteur LR)"}, {"fr.heralisz.camo.", "HeraliZ Tactique (camos)"},
            {"fr.heraliz.tactique.", "HeraliZ Tactique"}, {"fr.heralisz.grenade.", "HeraliZ Tactique"},
            {"fr.heralisz.melee.", "HeraliZ Tactique"}, {"fr.heralisz.heal.", "HeraliZ Tactique"},
            {"fr.heraliz.casino.", "HeraliZ Casino"}, {"fr.heraliz.crates.", "HeraliZ Caisses"},
            {"fr.heraliz.loot.", "HeraliZ Loot"}, {"fr.heraliz.decors.", "HeraliZ Décors"},
            {"fr.heraliz.armory.", "HeraliZ Armurerie"}, {"fr.heraliz.composants.", "HeraliZ Composants"},
            {"fr.heraliz.wonder.", "HeraliZ Wonder"}, {"fr.heraliz.reanimation.", "Réanimation"},
            {"fr.heraliz.airstrike.", "Airstrike"}, {"fr.heraliz.emote.", "HeraliZ Emote"},
            {"fr.heraliz.interfaces.", "HeraliZ Interface"}, {"fr.heraliz.grades.", "HeraliZ Grades"},
            {"fr.heraliz.quetes.", "HeraliZ Quêtes"}, {"fr.heralisz.bunker.", "HeraliZ Bunker"},
            {"com.hbm_m.", "HeraliZ Bunker (portes HBM)"}, {"com.heraliz.extraction.", "HeraliZ Extraction"},
            {"com.caracrea.", "CaraCrea"}, {"org.concentus.", "CaraCrea (voix)"}, {"com.dayzhotbar.", "dayzhotbar"},
            {"com.mrcrayfish.backpacked.", "HeraliZ Sac à dos (Backpacked)"}, {"com.Spartan.", "Spartan"},
            {"fr.heraliz.", "HeraliZ (autre)"}, {"fr.heralisz.", "HeraliZ (autre)"}, {"com.heraliz.", "HeraliZ (autre)"},
            // mods des autres auteurs
            {"com.tacz.", "TaCZ"}, {"com.mafuyu404.taczaddon.", "TaCZ Addon"},
            {"net.petemc.", "Mutants & Zombies"}, {"com.corosus.zombieawareness.", "Zombie Awareness"},
            {"com.corosus.", "CoroUtil"}, {"software.bernie.", "GeckoLib"}, {"top.theillusivec4.curios.", "Curios"},
            {"com.mrcrayfish.furniture.", "cfm (meubles)"}, {"com.mrcrayfish.framework.", "Framework"},
            {"org.ywzj.", "ywzj (véhicules)"}, {"net.tct.matmos.", "MAtmos"}, {"net.thetct.tctcore.", "TCTcore"},
            {"snownee.passablefoliage.", "Passable Foliage"}, {"snownee.kiwi.", "Kiwi"}, {"me.shedaniel.", "Cloth Config"},
            {"malte0811.ferritecore.", "FerriteCore"}, {"org.embeddedt.modernfix.", "ModernFix"},
            {"ca.fxco.memoryleakfix.", "MemoryLeakFix"}, {"dev.tonimatas.packetfixer.", "PacketFixer"},
            {"dev.kosmx.", "Player Animator"}, {"io.github.kosmx.", "Emotecraft"},
            // plugins Bukkit
            {"fr.xephi.authme.", "AuthMe"}, {"dev.brighten.antivpn.", "KauriVPN"}, {"litebans.", "LiteBans"},
            {"me.lucko.luckperms.", "LuckPerms"}, {"net.luckperms.", "LuckPerms"}, {"me.lucko.spark.", "spark"},
            {"com.sk89q.worldguard.", "WorldGuard"}, {"net.goldtreeservers.worldguardextraflags.", "WorldGuardExtraFlags"},
            {"com.sk89q.", "WorldEdit"}, {"org.enginehub.", "WorldEdit"}, {"net.craftersland.consolefix.", "ConsoleSpamFix"},
            // le serveur lui-même
            {"com.mohistmc.", "Mohist (Bukkit / Spigot)"}, {"org.bukkit.", "Mohist (Bukkit / Spigot)"},
            {"org.spigotmc.", "Mohist (Bukkit / Spigot)"}, {"co.aikar.", "Mohist (Bukkit / Spigot)"},
            {"net.md_5.", "Mohist (Bukkit / Spigot)"}, {"io.papermc.", "Mohist (Bukkit / Spigot)"},
            {"net.minecraftforge.", "Forge"},
    };

    /** Familles de Minecraft : préfixe de classe, rang (plus petit = plus précis, gagne), nom. */
    private static final Object[][] FAMILLES = {
            {"net.minecraft.world.level.chunk.storage.", 1, "Sauvegarde et lecture sur le disque"},
            {"net.minecraft.world.level.storage.", 1, "Sauvegarde et lecture sur le disque"},
            {"net.minecraft.nbt.NbtIo", 1, "Sauvegarde et lecture sur le disque"},
            {"net.minecraft.world.level.levelgen.", 2, "Génération du terrain"},
            {"net.minecraft.world.level.biome.", 2, "Génération du terrain"},
            {"net.minecraft.server.level.WorldGenRegion", 2, "Génération du terrain"},
            {"net.minecraft.world.level.chunk.ChunkGenerator", 2, "Génération du terrain"},
            {"net.minecraft.world.level.chunk.ChunkStatus", 2, "Génération du terrain"},
            {"net.minecraft.world.level.chunk.ProtoChunk", 2, "Génération du terrain"},
            {"net.minecraft.world.level.lighting.", 3, "Lumière"},
            {"net.minecraft.server.level.ThreadedLevelLightEngine", 3, "Lumière"},
            {"net.minecraft.server.level.ChunkMap$TrackedEntity", 4, "Suivi des entités (envoi aux joueurs)"},
            {"net.minecraft.server.level.ServerEntity", 4, "Suivi des entités (envoi aux joueurs)"},
            {"net.minecraft.world.level.NaturalSpawner", 5, "Apparition naturelle des créatures"},
            {"net.minecraft.server.level.ChunkMap", 6, "Chargement et déchargement des tronçons"},
            {"net.minecraft.server.level.ChunkHolder", 6, "Chargement et déchargement des tronçons"},
            {"net.minecraft.server.level.DistanceManager", 6, "Chargement et déchargement des tronçons"},
            {"net.minecraft.server.level.ChunkTaskPriorityQueue", 6, "Chargement et déchargement des tronçons"},
            {"net.minecraft.server.level.ChunkTracker", 6, "Chargement et déchargement des tronçons"},
            {"net.minecraft.world.entity.ai.", 7, "IA et recherche de chemin"},
            {"net.minecraft.world.level.pathfinder.", 7, "IA et recherche de chemin"},
            {"net.minecraft.world.phys.shapes.", 8, "Collisions et déplacements"},
            {"net.minecraft.world.level.BlockCollisions", 8, "Collisions et déplacements"},
            {"net.minecraft.world.level.CollisionGetter", 8, "Collisions et déplacements"},
            {"net.minecraft.world.level.entity.", 9, "Recherche d'entités (sections)"},
            {"net.minecraft.util.ClassInstanceMultiMap", 9, "Recherche d'entités (sections)"},
            {"net.minecraft.world.level.EntityGetter", 9, "Recherche d'entités (sections)"},
            {"net.minecraft.network.", 10, "Réseau (paquets)"},
            {"net.minecraft.server.network.", 10, "Réseau (paquets)"},
            {"net.minecraft.world.level.block.entity.", 11, "Entités de bloc"},
            {"net.minecraft.world.level.redstone.", 12, "Blocs (ticks programmés, redstone)"},
            {"net.minecraft.world.ticks.", 12, "Blocs (ticks programmés, redstone)"},
            {"net.minecraft.world.level.material.", 12, "Blocs (ticks programmés, redstone)"},
            {"net.minecraft.world.level.block.", 12, "Blocs (ticks programmés, redstone)"},
            {"net.minecraft.commands.", 13, "Commandes"},
            {"com.mojang.brigadier.", 13, "Commandes"},
            {"net.minecraft.advancements.", 14, "Progrès (advancements)"},
            {"net.minecraft.server.PlayerAdvancements", 14, "Progrès (advancements)"},
            {"net.minecraft.world.item.", 15, "Objets, recettes et inventaires"},
            {"net.minecraft.world.inventory.", 15, "Objets, recettes et inventaires"},
            {"net.minecraft.server.level.ServerPlayer", 16, "Joueurs"},
            {"net.minecraft.server.players.", 16, "Joueurs"},
            {"net.minecraft.world.entity.", 17, "Entités (tick, dégâts, autre)"},
            {"net.minecraft.server.level.", 18, "Monde (autre)"},
            {"net.minecraft.world.level.", 18, "Monde (autre)"},
            {"net.minecraft.server.", 19, "Boucle du serveur"},
            {"net.minecraft.", 20, "Minecraft (autre)"},
            {"com.mojang.", 20, "Minecraft (autre)"},
    };

    static final String MINECRAFT = "Minecraft";
    static final String JVM = "Java (aucune méthode de jeu)";

    /** Nom du mod ou plugin d'une classe ; null = Minecraft, bibliothèque ou mécanique ; "autre : a.b.c" = inconnu. */
    static String mod(String classe) {
        if (classe.startsWith("net.minecraft.") || classe.startsWith("com.mojang.")) return null;
        for (String[] p : PAQUETS) {
            if (classe.startsWith(p[0])) return p[1];
        }
        return "autre : " + prefixe(classe);
    }

    /** Propriétaire d'une pile (en haut = élément 0). */
    static String proprietaire(StackTraceElement[] pile) {
        boolean minecraft = false;
        for (StackTraceElement f : pile) {
            String c = f.getClassName();
            if (c.startsWith("net.minecraft.") || c.startsWith("com.mojang.")) {
                String crochet = crochetMixin(f.getMethodName());
                if (crochet != null) return crochet;
                minecraft = true;
                continue;
            }
            String m = mod(c);
            if (m != null) return m;
        }
        return minecraft ? MINECRAFT : JVM;
    }

    /** Indice dans la pile de la méthode qui a donné le propriétaire (-1 : Minecraft ou Java seuls). */
    static int indiceProprietaire(StackTraceElement[] pile) {
        for (int i = 0; i < pile.length; i++) {
            String c = pile[i].getClassName();
            if (c.startsWith("net.minecraft.") || c.startsWith("com.mojang.")) {
                if (crochetMixin(pile[i].getMethodName()) != null) return i;
                continue;
            }
            if (mod(c) != null) return i;
        }
        return -1;
    }

    /** Famille de Minecraft la plus précise trouvée dans la pile, sinon null. */
    static String famille(StackTraceElement[] pile) {
        int meilleur = Integer.MAX_VALUE;
        String nom = null;
        for (StackTraceElement f : pile) {
            String c = f.getClassName();
            if (!c.startsWith("net.minecraft.") && !c.startsWith("com.mojang.")) continue;
            for (Object[] r : FAMILLES) {
                if (c.startsWith((String) r[0])) {
                    int rang = (Integer) r[1];
                    if (rang < meilleur) {
                        meilleur = rang;
                        nom = (String) r[2];
                    }
                    break;
                }
            }
            if (meilleur == 1) break;
        }
        return nom;
    }

    /** Vrai si la pile passe par le cache des tronçons du serveur (attente d'un tronçon chargé ou généré ailleurs). */
    static boolean attendTroncon(StackTraceElement[] pile) {
        for (StackTraceElement f : pile) {
            String c = f.getClassName();
            if (c.startsWith("net.minecraft.server.level.ServerChunkCache") || c.startsWith("net.minecraft.server.level.ChunkMap")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Crochet Mixin fondu dans une classe de Minecraft : « handler$zza000$modid$nom » (Mixin 0.8.5+, avec l'identifiant
     * du mod quand il est connu), « redirect$… », « wrapOperation$… » (MixinExtras)… Rend « Mixin : modid » ou null.
     */
    static String crochetMixin(String methode) {
        int d = methode.indexOf('$');
        if (d <= 0) return null;
        String genre = methode.substring(0, d);
        switch (genre) {
            case "handler", "redirect", "modify", "localvar", "constant", "wrapOperation", "wrapWithCondition",
                 "modifyExpressionValue", "modifyReturnValue", "modifyReceiver", "wrapMethod" -> {
            }
            default -> {
                return null;
            }
        }
        String[] parts = methode.split("\\$");
        if (parts.length >= 4) return "Mixin : " + parts[2];
        return "Mixin (mod inconnu) : " + (parts.length >= 3 ? parts[2] : methode);
    }

    private static String prefixe(String classe) {
        int n = 0;
        for (int i = 0; i < classe.length(); i++) {
            if (classe.charAt(i) == '.' && ++n == 3) return classe.substring(0, i);
        }
        int p = classe.lastIndexOf('.');
        return p > 0 ? classe.substring(0, p) : classe;
    }
}
