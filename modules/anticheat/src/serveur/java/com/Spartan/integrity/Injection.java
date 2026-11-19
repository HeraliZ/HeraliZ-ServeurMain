package com.Spartan.integrity;

import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.capture.CaptureManager;
import com.Spartan.check.CheckManager;
import com.Spartan.config.SpartanConfig;
import com.Spartan.scan.Finding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Injection = alerte Discord + expulsion directe (03/11, demande du propriétaire). Règle d'accès claire, réglable dans
 * {@code [injection]}, appliquée même en détection silencieuse et même si le mode strict est coupé.
 *
 * <p>Déclenchent l'expulsion (traces CERTAINES d'un code étranger dans le jeu, jamais d'un logiciel honnête) :
 * <ul>
 *   <li>contrôle d'intégrité, indices FORTS dont la clé commence par un motif de {@code indices} : programme attaché au
 *   jeu en cours de partie, anticheat modifié en mémoire, paquet d'un client de triche connu, agent Java, agent natif ou
 *   débogueur, -Xbootclasspath, chargeur de classes remplacé, allowAttachSelf, thread démarré hors de tout module (DLL
 *   cachée), affichage détourné vers du code sans module (ces deux derniers, code natif caché, doivent être revus au
 *   contrôle suivant, demandé aussitôt : une trace passagère d'un seul relevé n'expulse personne) ;</li>
 *   <li>réponse fausse au défi d'intégrité (Spartan du jeu modifié, dans son fichier ou en mémoire) ;</li>
 *   <li>Gardien du launcher : un programme de la liste {@code outils} ouvert sur le PC (injecteurs, éditeurs de mémoire).</li>
 *   <li>(19/11, règles « en rodage », voir {@link Rodage}) bibliothèque d'agent Java ou de débogueur chargée dans le jeu
 *   sans option de lancement (vue par le jeu ou, de l'extérieur, par le launcher), classe du jeu ou de l'anticheat
 *   modifiée en mémoire après son chargement.</li>
 * </ul>
 * Restent des ALERTES (un logiciel honnête peut les produire : antivirus, overlays Discord / Steam / OBS / NVIDIA,
 * pilotes) : DLL au nom ou à l'emplacement douteux, overlay inconnu, threads sans nom, DLL chargée en cours de partie,
 * débogueurs, décompilateurs et gestionnaires de processus ouverts, Gardien muet, jar changé pendant la partie.
 *
 * <p>Une capture d'écran est demandée d'abord (si le joueur l'a autorisée), l'expulsion suit {@value #DELAI_CAPTURE_MS}
 * ms plus tard pour qu'elle arrive ; sinon expulsion immédiate. Une seule fois par connexion. Fil du serveur.
 */
public final class Injection {
    static final long DELAI_CAPTURE_MS = 5_000L;
    private static final String NOM = "Injection";

    private record Attente(ServerPlayer joueur, long quand) {
    }

    /** Joueurs déjà traités pendant cette connexion. */
    private static final Set<UUID> FAITS = new HashSet<>();
    /** Expulsions qui attendent la capture d'écran. */
    private static final Map<UUID, Attente> EN_ATTENTE = new HashMap<>();

    private Injection() {
    }

    public static boolean actif() {
        return SpartanConfig.INJECTION_EXPULSER.get();
    }

    /** Le joueur va être expulsé pour injection (capture en route) : les autres règles le laissent à celle-ci. */
    public static boolean enAttente(ServerPlayer p) {
        return EN_ATTENTE.containsKey(p.getUUID());
    }

    /** Expulsé (ou sur le point de l'être) pour injection pendant cette connexion. */
    public static boolean expulse(UUID id) {
        return FAITS.contains(id);
    }

    /**
     * Verdict d'un contrôle d'intégrité : {@code expulser} (raison, ou null) ; {@code reverifier} : un indice « à
     * confirmer » vu pour la première fois, un nouveau contrôle est demandé tout de suite ; {@code suspendues} : règles en
     * rodage que ce contrôle vient de suspendre (le staff doit être prévenu : {@link #annoncerSuspension}).
     */
    public record Verdict(String expulser, boolean reverifier, List<String> suspendues) {
        public Verdict(String expulser, boolean reverifier) {
            this(expulser, reverifier, List.of());
        }
    }

    private static final Verdict RIEN = new Verdict(null, false);
    /** Indices « à confirmer » vus au contrôle précédent (motif → heure), par joueur. */
    private static final Map<UUID, Map<String, Long>> A_CONFIRMER = new HashMap<>();
    private static final long CONFIRMATION_MS = 10 * 60_000L;
    /** Règles en rodage (19/11) : coupées toutes seules si trop de joueurs différents les déclenchent (fausse alerte). */
    private static final Rodage RODAGE = new Rodage();

    /**
     * Indices FORTS du contrôle qui valent une expulsion (liste {@code indices}). Ceux de {@code aConfirmer} (code natif
     * caché : une trace passagère est concevable, par exemple un module déchargé pendant le relevé) doivent être revus au
     * contrôle suivant, demandé aussitôt. Ceux de {@code rodage} (règles du 19/11, jamais essayées sur de vrais PC)
     * passent par le disjoncteur {@link Rodage} ; une règle établie qui expulse déjà les emporte avec elle.
     */
    public static Verdict juger(UUID id, List<Finding> findings) {
        if (!actif()) {
            return RIEN;
        }
        List<? extends String> motifs = SpartanConfig.INJECTION_INDICES.get();
        List<? extends String> confirmer = SpartanConfig.INJECTION_A_CONFIRMER.get();
        List<? extends String> rodage = SpartanConfig.INJECTION_RODAGE.get();
        List<String> certains = new ArrayList<>();
        Map<String, String> vus = new HashMap<>(); // motif à confirmer → texte
        Map<String, String> enRodage = new LinkedHashMap<>(); // motif en rodage → texte
        for (Finding f : findings) {
            if (f.severity() != Finding.Severity.STRONG) {
                continue;
            }
            String cle = f.key().toLowerCase(Locale.ROOT);
            String motif = motif(cle, motifs);
            if (motif == null) {
                continue;
            }
            String regle = Rodage.motif(cle, rodage);
            if (motif(cle, confirmer) != null) {
                vus.putIfAbsent(motif, f.text());
            } else if (regle != null) {
                enRodage.putIfAbsent(regle, f.text());
            } else {
                certains.add(f.text());
            }
        }
        long now = System.currentTimeMillis();
        Map<String, Long> avant = A_CONFIRMER.getOrDefault(id, Map.of());
        for (Map.Entry<String, String> v : vus.entrySet()) {
            Long quand = avant.get(v.getKey());
            if (quand != null && now - quand <= CONFIRMATION_MS) {
                certains.add(v.getValue() + " (revu au contrôle suivant)");
            }
        }
        List<String> suspendues = new ArrayList<>();
        if (!certains.isEmpty()) {
            certains.addAll(enRodage.values()); // expulsé de toute façon : le staff lit tout
        } else {
            for (Map.Entry<String, String> e : enRodage.entrySet()) {
                switch (RODAGE.noter(e.getKey(), id, now, SpartanConfig.INJECTION_RODAGE_JOUEURS.get(),
                        SpartanConfig.INJECTION_RODAGE_MINUTES.get() * 60_000L)) {
                    case APPLIQUER -> certains.add(e.getValue() + " (règle en rodage)");
                    case SUSPENDRE -> suspendues.add(e.getKey());
                    case SUSPENDUE -> {
                        // règle suspendue depuis le démarrage : l'alerte du contrôle suffit
                    }
                }
            }
        }
        if (!certains.isEmpty()) {
            A_CONFIRMER.remove(id);
            return new Verdict(String.join(" ; ", certains.subList(0, Math.min(3, certains.size())))
                    + (certains.size() > 3 ? " (+" + (certains.size() - 3) + ")" : ""), false, suspendues);
        }
        if (vus.isEmpty()) {
            A_CONFIRMER.remove(id);
            return suspendues.isEmpty() ? RIEN : new Verdict(null, false, suspendues);
        }
        Map<String, Long> nouveau = new HashMap<>();
        for (String m : vus.keySet()) {
            nouveau.put(m, avant.getOrDefault(m, now));
        }
        A_CONFIRMER.put(id, nouveau);
        return new Verdict(null, true, suspendues);
    }

    /**
     * Une règle hors du contrôle d'intégrité (Gardien du launcher : bibliothèque vue dans le jeu, clé réutilisée) peut-elle
     * agir sur ce joueur ? Vrai si elle n'est pas en rodage, ou si le disjoncteur la laisse passer. Le staff est prévenu
     * quand ce joueur la suspend. Fil du serveur.
     *
     * @param cle clé de l'indice (« gardien:dll:instrument »…), comparée à la liste {@code rodage}
     */
    public static boolean rodageAutorise(ServerPlayer p, String cle) {
        String regle = Rodage.motif(cle, SpartanConfig.INJECTION_RODAGE.get());
        if (regle == null) {
            return true;
        }
        Rodage.Decision d = RODAGE.noter(regle, p.getUUID(), System.currentTimeMillis(),
                SpartanConfig.INJECTION_RODAGE_JOUEURS.get(), SpartanConfig.INJECTION_RODAGE_MINUTES.get() * 60_000L);
        if (d == Rodage.Decision.SUSPENDRE) {
            annoncerSuspension(p, List.of(regle));
        }
        return d == Rodage.Decision.APPLIQUER;
    }

    /** Règles en rodage suspendues depuis le démarrage du serveur (/spartan status). */
    public static List<String> reglesSuspendues() {
        return RODAGE.suspendues();
    }

    /**
     * Le staff est prévenu qu'une règle en rodage vient d'être suspendue (fil du serveur) : trop de joueurs différents l'ont
     * déclenchée, c'est probablement une fausse alerte. {@code p} : le joueur qui l'a déclenchée en dernier (pas expulsé).
     */
    public static void annoncerSuspension(ServerPlayer p, List<String> regles) {
        for (String regle : regles) {
            String texte = "la règle « " + regle + " » (nouvelle, en rodage) a été déclenchée par "
                    + SpartanConfig.INJECTION_RODAGE_JOUEURS.get() + " joueurs différents en moins de "
                    + SpartanConfig.INJECTION_RODAGE_MINUTES.get() + " min (dernier : " + p.getName().getString()
                    + ", pas expulsé) : sans doute une fausse alerte. Elle ne fait plus qu'alerter jusqu'au prochain redémarrage."
                    + " Regarder les dernières alertes « Injection » ; pour la couper pour de bon : retirer « " + regle
                    + " » de [injection] indices dans spartan-common.toml.";
            Spartan.LOGGER.warn("[Spartan] Règle suspendue : {}", texte);
            AlertManager.info(p.server, "Règle suspendue", texte, AlertManager.COLOR_INFO);
        }
    }

    /** Motif de la liste par lequel commence la clé (minuscules), ou null. */
    private static String motif(String cle, List<? extends String> motifs) {
        for (String m : motifs) {
            String t = m.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty() && cle.startsWith(t)) {
                return t;
            }
        }
        return null;
    }

    /** Programmes vus par le Gardien qui valent une expulsion (liste {@code outils}). */
    public static List<String> outils(List<String> vus) {
        List<String> out = new ArrayList<>();
        if (!actif()) {
            return out;
        }
        List<? extends String> liste = SpartanConfig.INJECTION_OUTILS.get();
        for (String v : vus) {
            for (String o : liste) {
                if (!o.isBlank() && v.equalsIgnoreCase(o.trim())) {
                    out.add(v);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Alerte au staff (Discord, en jeu, alerts.log) puis expulsion. @param source d'où vient la trace (contrôle, défi,
     * Gardien…)
     */
    public static void expulser(ServerPlayer p, String raison, String source) {
        if (!actif() || p.hasDisconnected() || !FAITS.add(p.getUUID())) {
            return;
        }
        String nom = p.getName().getString();
        Spartan.LOGGER.warn("[Spartan] {} expulsé pour injection ({}) : {}", nom, source, raison);
        CheckManager.flag(NOM, p, "EXPULSÉ : " + raison + " [" + source + "]");
        boolean capture = false;
        if (SpartanConfig.INJECTION_CAPTURE.get()) {
            String refus = CaptureManager.demander(p, "injection : " + raison);
            capture = refus == null || CaptureManager.enCours(p.getUUID());
        }
        if (capture) {
            EN_ATTENTE.put(p.getUUID(), new Attente(p, System.currentTimeMillis() + DELAI_CAPTURE_MS));
        } else {
            deconnecter(p);
        }
    }

    /** Fin de chaque tick serveur : expulsions dont la capture a eu le temps d'arriver. Rien à faire presque toujours. */
    public static void tick(MinecraftServer server) {
        if (EN_ATTENTE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Attente> it = EN_ATTENTE.values().iterator();
        List<ServerPlayer> partir = null;
        while (it.hasNext()) {
            Attente a = it.next();
            if (a.joueur().hasDisconnected()) {
                it.remove();
            } else if (now >= a.quand()) {
                it.remove();
                if (partir == null) {
                    partir = new ArrayList<>();
                }
                partir.add(a.joueur());
            }
        }
        if (partir != null) {
            partir.forEach(Injection::deconnecter);
        }
    }

    private static void deconnecter(ServerPlayer p) {
        if (p.hasDisconnected()) {
            return;
        }
        p.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.injection",
                "Connexion fermée : un programme modifie le jeu ou s'y injecte (outil de triche, injecteur, débogueur).\n"
                        + "Ferme-le, puis relance le jeu depuis le launcher HeraliZ."));
    }

    /** Déconnexion : la prochaine connexion repart de zéro. */
    public static void oublier(UUID id) {
        FAITS.remove(id);
        EN_ATTENTE.remove(id);
        A_CONFIRMER.remove(id);
    }
}
