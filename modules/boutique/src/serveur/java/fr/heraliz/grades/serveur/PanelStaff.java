package fr.heraliz.grades.serveur;

import com.mojang.logging.LogUtils;
import fr.heraliz.grades.Grade;
import fr.heraliz.grades.reseau.ReseauStaff;
import fr.heraliz.grades.staff.ActionStaff;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Panel du staff (touche P ou /staff panel, 25/10, maquettes validées) : partie serveur. Tout est décidé ici, rien chez le
 * joueur :
 * <ul>
 *     <li><b>qui</b> : un joueur du staff connecté sur AuthMe, rang = son grade du staff (1 Guide … 5 Fondateur) ; un op 4
 *     sans grade du staff compte comme un Admin, jamais comme un Fondateur ;</li>
 *     <li><b>quoi</b> : les actions cochées pour son rang ({@link PermsStaff}, onglet Permissions du Fondateur) ; donner un
 *     grade et régler les permissions : Fondateur ET op 4 seulement ; un membre du staff ne sanctionne jamais un membre du
 *     staff de rang égal ou plus haut (sauf le Fondateur) ;</li>
 *     <li><b>comment</b> : sanctions par LiteBans joué par la console au nom du staff ({@code --sender}), ban PC par Spartan,
 *     commandes de nos mods jouées au nom du staff avec la permission 4 (le résultat s'affiche dans son chat) ;</li>
 *     <li><b>trace</b> : chaque action réussie va dans le journal du staff ({@link JournalStaff}), sur le salon du staff
 *     Discord ({@link LienDiscord#evenement}) et dans l'onglet Historique.</li>
 * </ul>
 * Au plus une action toutes les 0,7 s par membre du staff, 10 changements de grade en 10 minutes. Fil serveur seulement.
 */
final class PanelStaff {
    private static final Logger LOG = LogUtils.getLogger();
    private static final long ECART_MS = 700, FENETRE_GRADES_MS = 600_000;
    private static final int MAX_GRADES = 10;
    private static final String CLE_COMBAT = "heralisz:combat_jusqua";
    private static final ResourceLocation CORPS = new ResourceLocation("heraliz_corps", "corpse");

    private static final Map<UUID, Long> DERNIERE = new HashMap<>();
    private static final Map<UUID, List<Long>> GRADES_DONNES = new HashMap<>();
    private static final Map<UUID, Long> CONNEXION = new HashMap<>();
    /** Joueurs rendus muets depuis le panel -> fin (ms ; Long.MAX_VALUE : définitif), pour l'étiquette « muet » de la liste. */
    private static final Map<UUID, Long> MUETS = new HashMap<>();
    /** Mode de jeu d'avant le mode spectateur du panel. */
    private static final Map<UUID, GameType> AVANT_SPECTATEUR = new HashMap<>();

    /** Noms des actions pour le salon Discord du staff (en français, comme la console). */
    private static final Map<ActionStaff, String> NOMS_FR = new HashMap<>();

    static {
        String[][] n = {{"avertir", "a averti"}, {"muet_court", "a rendu muet"}, {"muet_long", "a rendu muet"}, {"expulser", "a expulsé"},
                {"ban_temp", "a banni"}, {"ban_pc", "a banni (compte + PC)"}, {"message", "a écrit à"}, {"aller_voir", "est allé voir"},
                {"faire_venir", "a fait venir"}, {"liberer", "a ramené au spawn"}, {"inventaire_voir", "a regardé l'inventaire de"},
                {"inventaire_modifier", "a ouvert l'inventaire de"}, {"analyser_joueur", "a analysé le jeu de"}, {"donner_grade", "a changé le grade de"},
                {"vider_chat", "a vidé le chat"}, {"se_cacher", "s'est caché / montré"}, {"cacher_nom", "a caché / remis son nom"},
                {"spectateur", "est passé en spectateur / revenu"}, {"jour", "a mis le jour"}, {"nuit", "a mis la nuit"},
                {"meteo_claire", "a mis le beau temps"}, {"meteo_pluie", "a mis la pluie"}, {"extraction_vague", "a lancé la vague d'extractions suivante"},
                {"largage", "a lancé un largage"}, {"frappe", "a lancé une frappe"}, {"boss_eveil", "a réveillé un boss"},
                {"boss_reset", "a remis l'arène d'un boss"}, {"redemarrage", "a lancé un redémarrage doux"}, {"recharger", "a rechargé les données"},
                {"annonce", "a fait une annonce"}, {"etat", "a regardé l'état du serveur"}, {"analyser", "a lancé une analyse du serveur"},
                {"nettoyer_sol", "a nettoyé les objets au sol"}, {"nettoyer_corps", "a retiré des cadavres"}, {"nettoyer_zombies", "a retiré des zombies"}};
        for (String[] x : n) NOMS_FR.put(ActionStaff.parId(x[0]), x[1]);
    }

    private PanelStaff() {
    }

    static void brancher() {
        ReseauStaff.RECEPTION_OUVRIR.brancher(r -> ouvrir(r.joueur()));
        ReseauStaff.RECEPTION_DEMANDE_FICHE.brancher(r -> fiche(r.joueur(), r.message().joueur()));
        ReseauStaff.RECEPTION_AGIR.brancher(r -> agir(r.joueur(), r.message()));
        ReseauStaff.RECEPTION_PERMISSION.brancher(r -> permission(r.joueur(), r.message()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> CONNEXION.put(e.getEntity().getUUID(), System.currentTimeMillis()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            UUID u = e.getEntity().getUUID();
            CONNEXION.remove(u);
            DERNIERE.remove(u);
        });
    }

    static void demarrer(Path dossier) {
        PermsStaff.demarrer(dossier);
        JournalStaff.demarrer(dossier);
        DERNIERE.clear();
        GRADES_DONNES.clear();
        MUETS.clear();
        AVANT_SPECTATEUR.clear();
    }

    static void arreter() {
        PermsStaff.arreter();
        JournalStaff.arreter();
        CONNEXION.clear();
    }

    // ------------------------------------------------------------------ droits
    /** Rang du staff (1 Guide … 5 Fondateur), 0 : pas du staff ou pas connecté sur AuthMe. */
    static int rang(ServerPlayer p) {
        if (p == null || !PontBukkit.authentifie(p)) return 0;
        return rangGrade(p);
    }

    /**
     * Rang du staff d'après le grade seul, sans regarder AuthMe : sert aussi pour le joueur VISÉ (un membre du staff pas
     * encore connecté par /login reste protégé des sanctions d'un staff de rang plus bas). Seul le vrai grade Fondateur
     * vaut 5 : un grade du staff créé en jeu avec un rang plus haut compte comme un Admin.
     */
    private static int rangGrade(ServerPlayer p) {
        RegistreGrades r = ServeurGrades.registre;
        RegistreGrades.Fiche f = r == null ? null : r.fiche(p.getUUID());
        int rg = f != null && f.staff() != null ? f.staff().rang : 0;
        if (rg >= ActionStaff.RANG_MAX && f.staff() != Grade.FONDATEUR) rg = ActionStaff.RANG_MAX - 1;
        // op 4 sans grade du staff : comme un Admin (jamais Fondateur : donner un grade demande le vrai grade)
        if (rg <= 0 && p.hasPermissions(4)) rg = 4;
        return Math.max(0, Math.min(rg, ActionStaff.RANG_MAX));
    }

    /** Vrai Fondateur : grade Fondateur, op 4, connecté sur AuthMe (donner un grade, onglet Permissions). */
    static boolean fondateur(ServerPlayer p) {
        RegistreGrades r = ServeurGrades.registre;
        RegistreGrades.Fiche f = r == null || p == null ? null : r.fiche(p.getUUID());
        return f != null && f.staff() == Grade.FONDATEUR && p.hasPermissions(4) && PontBukkit.authentifie(p);
    }

    private static boolean permis(ServerPlayer p, ActionStaff a, int rang) {
        if (a.verrou) return fondateur(p);
        return PermsStaff.permis(a, rang);
    }

    private static String gradeAffiche(ServerPlayer p, int rang) {
        RegistreGrades r = ServeurGrades.registre;
        RegistreGrades.Fiche f = r == null ? null : r.fiche(p.getUUID());
        return f != null && f.staff() != null ? f.staff().id : PermsStaff.nomRang(rang);
    }

    // ------------------------------------------------------------------ état
    static void ouvrir(ServerPlayer p) {
        int rang = rang(p);
        if (rang == 0) {
            RegistreGrades r = ServeurGrades.registre;
            if (r != null && r.staffAuMoins(p.getUUID(), Grade.GUIDE) && !PontBukkit.authentifie(p)) {
                p.sendSystemMessage(Component.translatableWithFallback("message.heraliz_grades.kit.connexion", "Connecte-toi d'abord avec /login."));
            }
            return;
        }
        envoyer(p, rang, true, "", true);
    }

    private static void envoyer(ServerPlayer p, int rang, boolean ouvrir, String message, boolean ok) {
        List<String> permises = new ArrayList<>();
        for (ActionStaff a : ActionStaff.values()) if (permis(p, a, rang)) permises.add(a.id);
        boolean fonda = fondateur(p);
        List<String> grades = new ArrayList<>();
        if (fonda) {
            for (Grade g : Grade.values()) {
                if (g.attribuable() && g.type != Grade.Type.SOUTIEN && g != Grade.FONDATEUR) grades.add(g.id);
            }
        }
        long maintenant = System.currentTimeMillis();
        MUETS.values().removeIf(fin -> fin < maintenant);
        ReseauStaff.Etat e = new ReseauStaff.Etat(ouvrir, rang, gradeAffiche(p, rang), permises, fonda ? PermsStaff.tous() : Map.of(), grades,
                JournalStaff.recentes(60), new ArrayList<>(MUETS.keySet()), message, ok);
        ReseauStaff.CANAL.send(PacketDistributor.PLAYER.with(() -> p), e);
    }

    /** Réponse à une action : clé de texte {@code heraliz_grades.staff.msg.<cle>} et ses arguments (séparés par « | »). */
    private static void repondre(ServerPlayer p, boolean ok, String cle, Object... args) {
        StringBuilder b = new StringBuilder(cle);
        for (Object a : args) b.append('|').append(String.valueOf(a).replace('|', '/'));
        envoyer(p, rang(p), false, b.toString(), ok);
    }

    private static void fiche(ServerPlayer p, UUID cible) {
        if (rang(p) == 0) return;
        ServerPlayer t = p.server.getPlayerList().getPlayer(cible);
        if (t == null) return;
        RegistreGrades r = ServeurGrades.registre;
        RegistreGrades.Fiche f = r == null ? null : r.fiche(cible);
        String nom = t.getGameProfile().getName();
        long connexion = CONNEXION.getOrDefault(cible, 0L);
        long combat = t.getPersistentData().getLong(CLE_COMBAT) - System.currentTimeMillis();
        ReseauStaff.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new ReseauStaff.Fiche(cible, nom, t.level().dimension().location().toString(),
                t.getBlockX(), t.getBlockY(), t.getBlockZ(), connexion == 0 ? 0 : System.currentTimeMillis() - connexion, JournalStaff.nombreSanctions(nom),
                Math.max(0, combat), VanishServeur.cache(cible), f != null && f.staff() != null ? f.staff().id : "",
                f != null && f.boutique() != null ? f.boutique().id : "", JournalStaff.sanctionsDe(nom, 4)));
    }

    // ------------------------------------------------------------------ permissions (Fondateur)
    private static void permission(ServerPlayer p, ReseauStaff.Permission m) {
        if (!fondateur(p)) {
            repondre(p, false, "refuse");
            return;
        }
        ActionStaff a = ActionStaff.parId(m.action());
        if (a == null || a.verrou || m.rang() < 1 || m.rang() >= ActionStaff.RANG_MAX) {
            repondre(p, false, "verrou");
            return;
        }
        if (!PermsStaff.changer(a, m.rang(), m.permis())) {
            repondre(p, false, "erreur");
            return;
        }
        String detail = PermsStaff.nomRang(m.rang()) + (m.permis() ? " +" : " -");
        JournalStaff.noter(p.getGameProfile().getName(), Grade.FONDATEUR.id, a, "", "perm " + detail, "");
        LienDiscord.evenement("🛡️ Panel staff : **" + p.getGameProfile().getName() + "** (Fondateur) a " + (m.permis() ? "donné" : "retiré")
                + " « " + a.id + " » au grade " + PermsStaff.nomRang(m.rang()) + ".");
        repondre(p, true, "permission");
    }

    // ------------------------------------------------------------------ actions
    private static String nettoyer(String s, int max) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c < 32 || c == '§' || c == '|') continue;
            if (b.length() >= max) break;
            b.append(c);
        }
        return b.toString().strip();
    }

    private static void agir(ServerPlayer p, ReseauStaff.Agir m) {
        ActionStaff a = ActionStaff.parId(m.action());
        if (a == null) return;
        int rang = rang(p);
        if (rang == 0) return;
        if (!permis(p, a, rang)) {
            repondre(p, false, a.verrou ? "fondateur" : "refuse");
            return;
        }
        long maintenant = System.currentTimeMillis();
        Long der = DERNIERE.get(p.getUUID());
        if (der != null && maintenant - der < ECART_MS) {
            repondre(p, false, "trop_vite");
            return;
        }
        DERNIERE.put(p.getUUID(), maintenant);
        ServerPlayer cible = null;
        if (a.cible) {
            cible = m.cible() == null ? null : p.server.getPlayerList().getPlayer(m.cible());
            if (cible == null) {
                repondre(p, false, "absent");
                return;
            }
            boolean surSoi = cible == p;
            if (surSoi && a != ActionStaff.MESSAGE && a != ActionStaff.ANALYSER_JOUEUR && a != ActionStaff.DONNER_GRADE) {
                repondre(p, false, "soi");
                return;
            }
            if (!surSoi && (a.sanction() || a == ActionStaff.FAIRE_VENIR || a == ActionStaff.INVENTAIRE_MODIFIER || a == ActionStaff.INVENTAIRE_VOIR)
                    && rang < ActionStaff.RANG_MAX && rangGrade(cible) >= rang) {
                repondre(p, false, "staff_superieur");
                return;
            }
        }
        String option = a.options == null ? "" : a.options.contains(m.option()) ? m.option() : a.options.get(0);
        String texte = nettoyer(m.texte(), ReseauStaff.MAX_TEXTE);
        if ((a.sanction() || a == ActionStaff.MESSAGE || a == ActionStaff.ANNONCE) && texte.isEmpty()) {
            repondre(p, false, a.sanction() ? "raison_vide" : "texte_vide");
            return;
        }
        if ((a == ActionStaff.FAIRE_VENIR || a == ActionStaff.LIBERER) && Restrictions.enMatch(cible)) {
            repondre(p, false, "en_match");
            return;
        }
        String detail = option;
        try {
            Resultat res = executer(p, a, cible, option, texte, m);
            if (res == null) return;
            if (res.ok && !res.cle.isEmpty()) {
                // simple information (état du serveur) : rien au journal
                repondre(p, true, res.cle, res.args);
                return;
            }
            if (!res.ok) {
                repondre(p, false, res.cle, res.args);
                return;
            }
            if (!res.detail.isEmpty()) detail = res.detail;
        } catch (RuntimeException ex) {
            LOG.error("[grades] action « {} » du panel du staff impossible", a.id, ex);
            repondre(p, false, "erreur");
            return;
        }
        String nom = p.getGameProfile().getName(), grade = gradeAffiche(p, rang), nomCible = cible == null ? "" : cible.getGameProfile().getName();
        JournalStaff.noter(nom, grade, a, nomCible, detail, a == ActionStaff.MESSAGE ? "" : texte);
        Grade g = Grade.parId(grade);
        LienDiscord.evenement("🛡️ Panel staff : **" + nom + "** (" + (g != null ? g.nomFr : grade) + ") " + NOMS_FR.getOrDefault(a, a.id)
                + (nomCible.isEmpty() ? "" : " **" + nomCible + "**") + (detail.isEmpty() ? "" : " (" + detail + ")")
                + (texte.isEmpty() || a == ActionStaff.MESSAGE ? "" : " — « " + texte + " »"));
        repondre(p, true, "fait", a.id);
    }

    /** Résultat d'une action : réussie (détail noté au journal) ou refusée (clé de texte et arguments). */
    private record Resultat(boolean ok, String detail, String cle, Object[] args) {
        static Resultat ok(String detail) {
            return new Resultat(true, detail == null ? "" : detail, "", new Object[0]);
        }

        static Resultat non(String cle, Object... args) {
            return new Resultat(false, "", cle, args);
        }

        static Resultat info(String cle, Object... args) {
            return new Resultat(true, "", cle, args);
        }
    }

    private static Resultat executer(ServerPlayer p, ActionStaff a, ServerPlayer cible, String option, String texteBrut, ReseauStaff.Agir m) {
        MinecraftServer srv = p.server;
        // une raison ne porte jamais d'option de LiteBans (« -s », « --sender=… ») : l'auteur noté reste le vrai staff
        String texte = texteBrut.replaceAll("(^|\\s)-+", "$1").strip();
        String nom = p.getGameProfile().getName();
        String nc = cible == null ? "" : cible.getGameProfile().getName();
        switch (a) {
            case AVERTIR -> {
                if (!liteBans("warn " + nc + " " + texte + " --sender=" + nom)) {
                    cible.sendSystemMessage(Component.translatableWithFallback("message.heraliz_grades.staff.averti", "Avertissement du staff : %s",
                            texte).withStyle(ChatFormatting.GOLD));
                }
                return Resultat.ok("");
            }
            case MUET_COURT, MUET_LONG -> {
                boolean def = option.equals("def");
                if (!liteBans((def ? "mute " + nc : "tempmute " + nc + " " + option) + " " + texte + " --sender=" + nom)) {
                    return Resultat.non("litebans");
                }
                MUETS.put(cible.getUUID(), def ? Long.MAX_VALUE : System.currentTimeMillis() + duree(option));
                return Resultat.ok(option);
            }
            case EXPULSER -> {
                if (!liteBans("kick " + nc + " " + texte + " --sender=" + nom)) {
                    cible.connection.disconnect(Component.translatableWithFallback("message.heraliz_grades.staff.expulse", "Expulsé par le staff : %s", texte));
                }
                return Resultat.ok("");
            }
            case BAN_TEMP -> {
                if (!liteBans("tempban " + nc + " " + option + " " + texte + " --sender=" + nom)) return Resultat.non("litebans");
                return Resultat.ok(option);
            }
            case BAN_PC -> {
                return commandeStaff(p, "banpc " + nc + " " + option + " " + texte) > 0 ? Resultat.ok(option) : Resultat.non("echec");
            }
            case MESSAGE -> {
                cible.sendSystemMessage(Component.translatableWithFallback("message.heraliz_grades.staff.message", "[Staff] %1$s : %2$s", nom, texte)
                        .withStyle(ChatFormatting.AQUA));
                return Resultat.ok("");
            }
            case ALLER_VOIR -> {
                if (permis(p, ActionStaff.SE_CACHER, rang(p)) && !VanishServeur.cache(p.getUUID())) VanishServeur.basculerCache(p, true);
                p.teleportTo(cible.serverLevel(), cible.getX(), cible.getY(), cible.getZ(), cible.getYRot(), cible.getXRot());
                return Resultat.ok("");
            }
            case FAIRE_VENIR -> {
                cible.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), cible.getYRot(), cible.getXRot());
                return Resultat.ok("");
            }
            case LIBERER -> {
                return auSpawn(cible) ? Resultat.ok("") : Resultat.non("spawn");
            }
            case INVENTAIRE_VOIR, INVENTAIRE_MODIFIER -> {
                return inspecter(p, nc, a == ActionStaff.INVENTAIRE_VOIR) ? Resultat.ok("") : Resultat.non("inspecter");
            }
            case ANALYSER_JOUEUR -> {
                commandeStaff(p, "diag client " + nc + " 20");
                return Resultat.ok("");
            }
            case DONNER_GRADE -> {
                return grade(p, cible, option(m), m.retirer());
            }
            case VIDER_CHAT -> {
                for (ServerPlayer x : srv.getPlayerList().getPlayers()) {
                    ReseauStaff.CANAL.send(PacketDistributor.PLAYER.with(() -> x), new ReseauStaff.ViderChat());
                }
                return Resultat.ok("");
            }
            case SE_CACHER -> {
                boolean cache = !VanishServeur.cache(p.getUUID());
                VanishServeur.basculerCache(p, cache);
                return Resultat.ok(cache ? "on" : "off");
            }
            case CACHER_NOM -> {
                boolean cache = !VanishServeur.sansNom(p.getUUID());
                VanishServeur.basculerNom(p, cache);
                return Resultat.ok(cache ? "on" : "off");
            }
            case SPECTATEUR -> {
                if (p.isSpectator()) {
                    p.setGameMode(AVANT_SPECTATEUR.getOrDefault(p.getUUID(), GameType.SURVIVAL));
                    AVANT_SPECTATEUR.remove(p.getUUID());
                    return Resultat.ok("off");
                }
                AVANT_SPECTATEUR.put(p.getUUID(), p.gameMode.getGameModeForPlayer());
                p.setGameMode(GameType.SPECTATOR);
                return Resultat.ok("on");
            }
            case JOUR, NUIT -> {
                // par le cycle jour / nuit de HeraliZ Utils (/cycle) : prochain lever ou prochaine tombée de la nuit, en avançant
                return commandeStaff(p, a == ActionStaff.JOUR ? "cycle jour" : "cycle nuit") > 0 ? Resultat.ok("") : Resultat.non("echec");
            }
            case METEO_CLAIRE -> {
                srv.overworld().setWeatherParameters(12000, 0, false, false);
                return Resultat.ok("");
            }
            case METEO_PLUIE -> {
                srv.overworld().setWeatherParameters(0, 6000, true, false);
                return Resultat.ok("");
            }
            case EXTRACTION_VAGUE -> {
                return commandeStaff(p, "extraction rotation next") > 0 ? Resultat.ok("") : Resultat.non("echec");
            }
            case LARGAGE -> {
                // 26/10 (audit des largages) : le largage du panel passe par l'événement (annonce, caisse suivie, objet
                // d'événement à revendre, sans points de saison) ; avant, « airstrike call supply » posait une caisse sans
                // objet d'événement ni annonce
                return commandeStaff(p, "event lancer largage") > 0 ? Resultat.ok("") : Resultat.non("echec");
            }
            case FRAPPE -> {
                return commandeStaff(p, "airstrike call " + option) > 0 ? Resultat.ok(option) : Resultat.non("echec");
            }
            case BOSS_EVEIL, BOSS_RESET -> {
                return commandeStaff(p, "boss " + option + (a == ActionStaff.BOSS_EVEIL ? " eveil" : " reset")) > 0 ? Resultat.ok(option)
                        : Resultat.non("boss");
            }
            case REDEMARRAGE -> {
                return commandeStaff(p, "redemarrage " + option + (texte.isEmpty() ? "" : " " + texte)) > 0 ? Resultat.ok(option + " min")
                        : Resultat.non("echec");
            }
            case RECHARGER -> {
                commandeStaff(p, "recharger");
                return Resultat.ok("");
            }
            case ANNONCE -> {
                Component titre = Component.translatableWithFallback("message.heraliz_grades.staff.annonce_titre", "Annonce du staff")
                        .withStyle(ChatFormatting.RED);
                for (ServerPlayer x : srv.getPlayerList().getPlayers()) {
                    x.connection.send(new ClientboundSetTitlesAnimationPacket(10, 90, 20));
                    x.connection.send(new ClientboundSetTitleTextPacket(titre));
                    x.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(texte)));
                    x.sendSystemMessage(Component.empty().append(titre.copy().append(" : ")).append(Component.literal(texte).withStyle(ChatFormatting.WHITE)));
                }
                return Resultat.ok("");
            }
            case ETAT -> {
                float mspt = srv.getAverageTickTime();
                Runtime rt = Runtime.getRuntime();
                long utilisee = (rt.totalMemory() - rt.freeMemory()) / 1_048_576, max = rt.maxMemory() / 1_048_576;
                int entites = 0;
                for (ServerLevel l : srv.getAllLevels()) for (Entity ignore : l.getAllEntities()) entites++;
                return Resultat.info("etat_info", String.format(java.util.Locale.ROOT, "%.1f", Math.min(20, 1000 / Math.max(mspt, 1e-3F))),
                        String.format(java.util.Locale.ROOT, "%.1f", mspt), utilisee, max, srv.getPlayerCount(), entites);
            }
            case ANALYSER -> {
                commandeStaff(p, "diag mesure 60");
                return Resultat.ok("");
            }
            case NETTOYER_SOL, NETTOYER_CORPS, NETTOYER_ZOMBIES -> {
                double r = a == ActionStaff.NETTOYER_ZOMBIES ? 48 : 64;
                AABB zone = p.getBoundingBox().inflate(r);
                int n = 0;
                for (Entity e : p.serverLevel().getEntities((Entity) null, zone, e -> retirable(a, e))) {
                    e.discard();
                    n++;
                }
                return Resultat.ok(String.valueOf(n));
            }
            default -> {
                return Resultat.non("refuse");
            }
        }
    }

    private static String option(ReseauStaff.Agir m) {
        return m.option() == null ? "" : m.option();
    }

    private static boolean retirable(ActionStaff a, Entity e) {
        if (e instanceof ServerPlayer) return false;
        return switch (a) {
            case NETTOYER_SOL -> e instanceof ItemEntity;
            case NETTOYER_CORPS -> CORPS.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
            case NETTOYER_ZOMBIES -> e instanceof Zombie || "mutantszombies".equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
            default -> false;
        };
    }

    private static long duree(String code) {
        long n;
        try {
            n = Long.parseLong(code.substring(0, code.length() - 1));
        } catch (RuntimeException ex) {
            return 0;
        }
        return switch (code.charAt(code.length() - 1)) {
            case 'm' -> n * 60_000L;
            case 'h' -> n * 3_600_000L;
            case 'd' -> n * 86_400_000L;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ grade (Fondateur)
    private static Resultat grade(ServerPlayer p, ServerPlayer cible, String id, boolean retirer) {
        if (!fondateur(p)) return Resultat.non("fondateur");
        Grade g = Grade.parId(id);
        if (g == null || !g.attribuable() || g.type == Grade.Type.SOUTIEN || g == Grade.FONDATEUR) return Resultat.non("grade_refuse");
        RegistreGrades r = ServeurGrades.registre;
        if (r == null) return Resultat.non("erreur");
        RegistreGrades.Fiche f = r.fiche(cible.getUUID());
        // jamais de grade retiré ou remplacé à un Fondateur depuis le panel (console seulement)
        if (f != null && f.staff() == Grade.FONDATEUR && g.type == Grade.Type.STAFF) return Resultat.non("grade_refuse");
        long maintenant = System.currentTimeMillis();
        List<Long> faits = GRADES_DONNES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        faits.removeIf(t -> maintenant - t > FENETRE_GRADES_MS);
        if (faits.size() >= MAX_GRADES) return Resultat.non("trop_grades");
        String pseudo = cible.getGameProfile().getName();
        switch (r.changer(cible.getUUID(), pseudo, g, !retirer, "panel:" + p.getGameProfile().getName())) {
            case OK -> {
                faits.add(maintenant);
                ServeurGrades.rafraichir(cible);
                p.server.getCommands().sendCommands(cible);
                cible.sendSystemMessage(retirer
                        ? Component.translatableWithFallback("message.heraliz_grades.perdu", "Tu n'as plus le grade %s.", g.nom())
                        : Component.translatableWithFallback("message.heraliz_grades.recu", "Tu as reçu le grade %s.", g.nom()));
                TabServeur.maj(p.server, true);
                return Resultat.ok((retirer ? "-" : "+") + g.id);
            }
            case DEJA -> {
                return Resultat.non("grade_deja", pseudo);
            }
            case ABSENT -> {
                return Resultat.non("grade_absent", pseudo);
            }
            default -> {
                return Resultat.non("erreur");
            }
        }
    }

    // ------------------------------------------------------------------ ponts vers les autres mods
    /** Commande de nos mods jouée au nom du staff, avec la permission 4 : son résultat s'affiche dans son chat. */
    private static int commandeStaff(ServerPlayer p, String commande) {
        CommandSourceStack s = p.createCommandSourceStack().withPermission(4);
        return p.server.getCommands().performPrefixedCommand(s, commande);
    }

    private static boolean bukkitCherche;
    private static Method getConsole, dispatch, getPluginManager, getPlugin;

    /** Commande de LiteBans jouée par la console de Bukkit ; faux si Bukkit ou LiteBans manque, ou si elle échoue. */
    private static boolean liteBans(String commande) {
        if (!bukkitCherche) {
            bukkitCherche = true;
            try {
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
                getConsole = bukkit.getMethod("getConsoleSender");
                dispatch = bukkit.getMethod("dispatchCommand", Class.forName("org.bukkit.command.CommandSender"), String.class);
                getPluginManager = bukkit.getMethod("getPluginManager");
                getPlugin = Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin", String.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                dispatch = null;
            }
        }
        if (dispatch == null) return false;
        try {
            Object pm = getPluginManager.invoke(null);
            if (pm == null || getPlugin.invoke(pm, "LiteBans") == null) return false;
            return Boolean.TRUE.equals(dispatch.invoke(null, getConsole.invoke(null), commande));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            LOG.warn("[grades] LiteBans injoignable pour « {} » : {}", commande.split(" ")[0], ex.toString());
            return false;
        }
    }

    private static Method allerAuSpawn, spawnCrac, inspecter;
    private static boolean spawnCherche, inspecterCherche;

    private static boolean auSpawn(ServerPlayer p) {
        if (!spawnCherche) {
            spawnCherche = true;
            try {
                allerAuSpawn = Class.forName("fr.heralisz.utils.api.SpawnApi").getMethod("allerAuSpawn", ServerPlayer.class);
            } catch (ReflectiveOperationException | LinkageError ignore) {
                allerAuSpawn = null;
            }
            try {
                spawnCrac = Class.forName("fr.heraliz.crac.api.SpawnCracApi").getMethod("allerAuSpawnDefini", ServerPlayer.class);
            } catch (ReflectiveOperationException | LinkageError ignore) {
                spawnCrac = null;
            }
        }
        try {
            if (spawnCrac != null && Boolean.TRUE.equals(spawnCrac.invoke(null, p))) return true;
            return allerAuSpawn != null && Boolean.TRUE.equals(allerAuSpawn.invoke(null, p));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private static boolean inspecter(ServerPlayer staff, String nom, boolean lecture) {
        if (!inspecterCherche) {
            inspecterCherche = true;
            try {
                inspecter = Class.forName("fr.heralisz.utils.inspecter.Inspecter").getMethod("ouvrirPourPanel", ServerPlayer.class, String.class, boolean.class);
            } catch (ReflectiveOperationException | LinkageError ignore) {
                inspecter = null;
            }
        }
        try {
            return inspecter != null && Boolean.TRUE.equals(inspecter.invoke(null, staff, nom, lecture));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }
}
