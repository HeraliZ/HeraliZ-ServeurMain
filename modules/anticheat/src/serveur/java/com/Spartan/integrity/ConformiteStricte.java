package com.Spartan.integrity;

import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.check.CheckManager;
import com.Spartan.config.SpartanConfig;
import com.Spartan.scan.OfficialProfile;
import com.Spartan.scan.ScanReport;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Mode « jeu identique au jeu officiel » (27/10, demande du propriétaire : « refuser toute connexion avec un client pas
 * identique 1:1 ») : règle d'ACCÈS, comme le jeton du launcher, appliquée même en détection silencieuse.
 *
 * <p>Seuls les écarts CERTAINS refusent : ce qui se compare exactement au modpack officiel (officiel.json, fabriqué avec
 * le serveur) ou à la copie du jar joueur gardée par le serveur. Les indices devinés (overlays qui détournent
 * l'affichage, threads sans nom, DLL, programmes ouverts, code d'origine inconnue) restent des alertes : un joueur
 * honnête peut en avoir (Discord, Steam, NVIDIA…).
 *
 * <p>Le joueur ne lit qu'une consigne courte (relancer depuis le launcher, qui remet les fichiers officiels) ; la raison
 * exacte va au staff (Discord, fil du joueur, et en jeu). {@code strictOfficial = false} coupe tout sans redémarrer.
 */
public final class ConformiteStricte {
    /** Fournisseurs de mods d'un jeu officiel : dossier mods, Minecraft / Forge, jars imbriqués, dossier du launcher. */
    private static final Set<String> FOURNISSEURS = Set.of("mods folder", "minecraft", "JarInJar", "maven libs");
    /** Options de lancement du launcher (Helios) qui chargent les mods depuis son propre dossier. */
    private static final Set<String> OPTIONS_LAUNCHER = Set.of("arg:--fml.mavenroots", "arg:--fml.modlists", "arg:--fml.mods");
    /** Joueurs déjà refusés (une seule alerte et une seule expulsion par connexion). */
    private static final Set<ServerPlayer> REFUSES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private ConformiteStricte() {
    }

    public static boolean actif() {
        return SpartanConfig.STRICT_OFFICIAL.get();
    }

    /**
     * Refuse la connexion (fil serveur). La raison exacte part au staff, le joueur ne lit qu'une consigne.
     *
     * @param consigne message court pour le joueur (null : consigne générale « jeu non identique »)
     */
    public static void refuser(ServerPlayer p, String raison, Component consigne) {
        if (!actif() || p.hasDisconnected() || !REFUSES.add(p)) {
            return;
        }
        String name = p.getName().getString();
        Spartan.LOGGER.warn("[Spartan] {} refusé (jeu non identique au jeu officiel) : {}", name, raison);
        CheckManager.flag("Conformité", p, "connexion refusée : " + raison);
        AlertManager.info(p.server, "Conformité", name + " refusé (jeu non identique au jeu officiel) : " + raison,
                AlertManager.COLOR_INFO);
        p.connection.disconnect(consigne != null ? consigne : Component.translatableWithFallback("spartan.expulsion.conformite",
                "Connexion refusée : ton jeu n'est pas identique au jeu officiel HeraliZ.\n"
                        + "Ferme le jeu et relance-le depuis le launcher HeraliZ (il remet les fichiers officiels tout seul)."));
    }

    public static void refuser(ServerPlayer p, String raison) {
        refuser(p, raison, null);
    }

    /** Joueur déjà refusé pendant cette connexion (le staff a reçu la raison : rien d'autre à lui dire). */
    public static boolean estRefuse(ServerPlayer p) {
        return REFUSES.contains(p);
    }

    /** Consigne pour un Spartan d'une autre version (mise à jour pas encore faite d'un côté ou de l'autre). */
    public static Component consigneVersion() {
        return Component.translatableWithFallback("spartan.expulsion.version_stricte",
                "Connexion refusée : ton jeu n'est pas à la version du serveur.\n"
                        + "Ferme le jeu et relance-le depuis le launcher HeraliZ. Si tu viens de le faire, le serveur se met à jour : "
                        + "réessaie dans quelques minutes.");
    }

    /**
     * Écarts certains entre un rapport de contrôle et le jeu officiel (vide : conforme). Jamais d'indice deviné.
     *
     * @param off            modpack officiel (sans lui, rien n'est jugé sur les jars)
     * @param agentsPermis   fragments de chemin d'agents Java autorisés (config)
     * @param modsInterdits  identifiants de mods interdits (config, minuscules)
     */
    public static List<String> ecarts(ScanReport r, OfficialProfile off, List<String> agentsPermis, List<String> modsInterdits) {
        List<String> out = new ArrayList<>();
        // Lancement : agents, débogueur, code ajouté au démarrage, options qui chargent des mods hors du launcher
        for (String flag : r.jvmFlags) {
            String f = flag.toLowerCase(Locale.ROOT);
            if (f.startsWith("-javaagent:")) {
                String nom = f.substring("-javaagent:".length());
                if (agentsPermis.stream().noneMatch(a -> !a.isBlank() && nom.contains(a.toLowerCase(Locale.ROOT)))) {
                    out.add("agent Java au lancement : " + flag.substring("-javaagent:".length()));
                }
            } else if (f.startsWith("-agentlib:") || f.startsWith("-agentpath:")) {
                out.add("agent natif ou débogueur au lancement : " + flag);
            } else if (f.startsWith("-xbootclasspath")) {
                out.add("code ajouté au démarrage de Java (-Xbootclasspath)");
            } else if (f.startsWith("-djava.system.class.loader")) {
                out.add("chargeur de classes remplacé : " + flag);
            } else if (f.equals("-djdk.attach.allowattachself")) {
                out.add("le jeu s'autorise à s'injecter du code (allowAttachSelf)");
            } else if (f.startsWith("arg:--fml.") && !OPTIONS_LAUNCHER.contains(f)) {
                out.add("option de lancement qui charge du code hors du launcher : " + flag.substring(4));
            }
        }
        if (!r.attachDisabled) {
            out.add("jeu lancé sans les protections du launcher HeraliZ (DisableAttachMechanism absent)");
        }
        for (String t : r.tamper) {
            out.add("anticheat modifié en mémoire : " + t);
        }
        for (String c : r.classHits) {
            out.add("classe d'un cheat connu présente : " + c);
        }
        // Code aux noms de classes impossibles en Java compilé (caractères de contrôle) : chargé en mémoire sans fichier,
        // trace laissée par le client de triche Doomsday le 11/10. Aucun mod honnête n'en a.
        for (ScanReport.OriginEntry o : r.origins) {
            if (com.Spartan.scan.ScanAnalyzer.nomImpossible(o.key())
                    || o.classes().stream().anyMatch(com.Spartan.scan.ScanAnalyzer::nomImpossible)) {
                out.add("code brouillé chargé en mémoire sans fichier (" + com.Spartan.scan.ScanAnalyzer.lisible(o.key()) + ")");
                break;
            }
        }
        // Mods vus par Forge : fournisseur inconnu, mod interdit
        Set<String> nomsCharges = new HashSet<>();
        for (String lf : r.loadedFiles) {
            String[] parts = lf.split("\\|", -1);
            String fournisseur = parts[0];
            String fichier = parts.length > 1 ? parts[1] : "?";
            nomsCharges.add(fichier.toLowerCase(Locale.ROOT));
            if (!FOURNISSEURS.contains(fournisseur)) {
                out.add("mod chargé par une autre voie que le launcher (" + fournisseur + ") : " + fichier);
            }
            for (String id : (parts.length > 2 ? parts[2] : "").split(",")) {
                if (!id.isBlank() && modsInterdits.contains(id.toLowerCase(Locale.ROOT))) {
                    out.add("mod interdit chargé : " + id + " (" + fichier + ")");
                }
            }
        }
        if (off == null || off.isEmpty()) {
            return out; // sans modpack officiel, les jars et les configs Mixin ne sont pas jugés
        }
        // Jars : tout jar du dossier mods ou du dossier du launcher doit être un jar officiel (par empreinte)
        Set<String> empreintes = new HashSet<>();
        for (ScanReport.FileEntry f : r.files) {
            boolean dossierMods = f.origin().equals("mods");
            if (!dossierMods && !f.origin().equals("modlist")) {
                continue; // bibliothèques de Minecraft / Forge, agents : jugés ailleurs
            }
            nomsCharges.add(f.name().toLowerCase(Locale.ROOT));
            if (f.sha256().isEmpty()) {
                continue; // dossier, jar trop gros ou verrouillé par un antivirus : rien de certain
            }
            if (dossierMods && !f.name().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                continue; // Forge ne charge que les .jar (fichiers .DS_Store, .disabled… sans effet)
            }
            empreintes.add(f.sha256());
            if (!off.knowsFile(f.sha256())) {
                out.add((off.knowsName(f.name()) ? "jar officiel MODIFIÉ : " : "jar en plus du jeu officiel : ") + f.name()
                        + (dossierMods ? " (dossier mods)" : " (dossier du launcher)"));
            }
        }
        // Jar officiel manquant (mod retiré : vue forcée, contours des blocs…) : le modpack actuel ou le précédent doit
        // être complet. Un jar présent mais sans empreinte (antivirus) compte comme présent.
        List<String> manquants = manquants(off, empreintes, nomsCharges);
        if (!manquants.isEmpty() && (off.precedent == null || !manquants(off.precedent, empreintes, nomsCharges).isEmpty())) {
            out.add("jar(s) officiel(s) manquant(s) : " + String.join(", ", manquants.subList(0, Math.min(5, manquants.size())))
                    + (manquants.size() > 5 ? " (+" + (manquants.size() - 5) + ")" : ""));
        }
        // Configs Mixin : inconnue (hors d'un jar officiel) ou déguisée sous le nom d'une config officielle
        Map<String, Boolean> jarsOfficiels = null;
        for (ScanReport.MixinEntry m : r.mixins) {
            String fp = ScanReport.hex(ScanReport.mixinFingerprint(m.name()));
            if (off.knowsMixin(fp)) {
                String pkg = off.mixinPackage(fp);
                if (!m.pkg().isEmpty() && !pkg.isEmpty() && !pkg.equals(m.pkg())) {
                    out.add("config Mixin déguisée : " + m.name() + " (" + m.pkg() + " au lieu de " + pkg + ")");
                }
                continue;
            }
            if (com.Spartan.scan.ScanAnalyzer.isSpartanOwn(m)) {
                continue;
            }
            // déclarée par un jar officiel mais absente de la liste : liste incomplète, pas une triche (alerte seulement).
            // Jar reconnu par son nom ou, rangé par le launcher sous un autre nom, par son empreinte (27/10).
            if (!m.source().isEmpty()) {
                if (jarsOfficiels == null) {
                    jarsOfficiels = com.Spartan.scan.ScanAnalyzer.jarsOfficiels(r, off);
                }
                if (off.knowsName(m.source()) || jarsOfficiels.getOrDefault(m.source().toLowerCase(Locale.ROOT), false)) {
                    continue;
                }
            }
            out.add("config Mixin inconnue : " + m.name() + (m.pkg().isEmpty() ? "" : " (" + m.pkg() + ")"));
        }
        return out;
    }

    /** Jars officiels absents du jeu (ni leur empreinte, ni leur nom). */
    private static List<String> manquants(OfficialProfile off, Set<String> empreintes, Set<String> noms) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : off.mods.entrySet()) {
            if (!empreintes.contains(e.getValue()) && !noms.contains(e.getKey())) {
                out.add(e.getKey());
            }
        }
        return out;
    }
}
