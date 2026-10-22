package fr.heraliz.grades.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heraliz.grades.reseau.ReseauBoutique;
import fr.heraliz.grades.reseau.ReseauBoutique.ActionEchange;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Échanges entre joueurs (/trade), au spawn seulement : objets et Points Boutique (PB).
 * <p>
 * Pour qu'aucun objet ne puisse être copié ni perdu, rien ne quitte l'inventaire avant la fin : chaque joueur propose
 * des CASES de son inventaire, le serveur note une copie de ce qu'elles contiennent et la recompare à chaque tick (case
 * changée, objet jeté, mort, coffre ouvert… : l'objet sort de l'échange et les deux « Prêt » sautent). Quand les deux
 * sont prêts : 3 s de compte à rebours (tout changement l'arrête), puis exécution en une seule fois, dans un seul tick,
 * suivie d'une sauvegarde de tous les joueurs (sinon une déconnexion puis un plantage pourrait laisser l'objet des deux
 * côtés). Déconnexion, mort, téléportation hors du spawn, plantage avant la fin : rien n'a bougé.
 * <p>
 * PB : un seul des deux en donne (des PB contre des PB n'a pas de sens). Le site les fait passer (taxe de 10 % brûlée)
 * AVANT les objets ; l'envoi est noté sur le disque avant l'appel ({@code heraliz_grades/echanges_en_cours.json}). Si
 * les objets ne peuvent plus passer ensuite, ou si le serveur s'arrête entre les deux, le transfert est annulé : PB
 * rendus à celui qui les a donnés, repris à celui qui les a reçus.
 */
final class EchangeServeur {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long DEMANDE_MS = 60_000, MAX_PB = 100_000_000L;
    private static final int COMPTE_TICKS = 60, CASES_INVENTAIRE = 36;

    private static final class Cote {
        final UUID uuid;
        final String nom;
        final List<Integer> cases = new ArrayList<>();
        final List<ItemStack> copies = new ArrayList<>();
        long pb, solde = -1;
        boolean pret;

        Cote(ServerPlayer p) {
            uuid = p.getUUID();
            nom = p.getGameProfile().getName();
        }
    }

    private static final class Session {
        final Cote a, b;
        int phase, reste;
        boolean invalide;
        String message = "";

        Session(Cote a, Cote b) {
            this.a = a;
            this.b = b;
        }

        Cote de(UUID u) {
            return a.uuid.equals(u) ? a : b;
        }

        Cote autre(UUID u) {
            return a.uuid.equals(u) ? b : a;
        }
    }

    /** Transfert de PB envoyé au site, pas encore conclu. */
    private static final class Envoi {
        String ref, de, vers;
        long montant, recu;
        String etat; // « envoye », puis « annuler » si les PB doivent revenir
    }

    private record Demande(UUID de, long expire) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Demandes reçues : cible -> (demandeur -> demande). */
    private static final Map<UUID, Map<UUID, Demande>> DEMANDES = new HashMap<>();
    private static final Map<String, Envoi> ENVOIS = new LinkedHashMap<>();
    private static final java.util.Set<String> EN_REPRISE = new java.util.HashSet<>();
    private static MinecraftServer serveur;
    private static Path fichier;
    private static int ticks;

    private EchangeServeur() {
    }

    static void brancher() {
        ReseauBoutique.RECEPTION_ACTION.brancher(r -> action(r.joueur(), r.action()));
    }

    static void demarrer(MinecraftServer srv, Path dossier) {
        serveur = srv;
        SESSIONS.clear();
        DEMANDES.clear();
        ENVOIS.clear();
        EN_REPRISE.clear();
        fichier = dossier.resolve("echanges_en_cours.json");
        try {
            if (Files.exists(fichier)) {
                for (JsonElement e : JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    Envoi v = GSON.fromJson(e, Envoi.class);
                    // serveur arrêté pendant un échange : les objets n'ont pas bougé, les PB doivent revenir s'ils sont partis
                    if (v != null && v.ref != null) {
                        if ("envoye".equals(v.etat)) v.etat = "verifier";
                        ENVOIS.put(v.ref, v);
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("[grades] {} illisible ({}) : échanges en cours NON repris", fichier, ex.toString());
            try {
                Files.copy(fichier, fichier.resolveSibling("echanges_en_cours.illisible." + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignore) {
            }
        }
        if (!ENVOIS.isEmpty()) LOG.warn("[grades] trade : {} transfert(s) de PB à vérifier", ENVOIS.size());
    }

    static void arreter() {
        SESSIONS.clear();
        DEMANDES.clear();
        serveur = null;
    }

    private static MutableComponent msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.trade." + cle, fr, args);
    }

    private static ServerPlayer joueur(UUID u) {
        return serveur == null ? null : serveur.getPlayerList().getPlayer(u);
    }

    private static boolean dansEchange(UUID u) {
        return SESSIONS.containsKey(u);
    }

    // ------------------------------------------------------------------ demandes
    /** Raison du refus (null : peut échanger) : connecté sur AuthMe, au spawn, vivant, en survie ou aventure. */
    private static Component refus(ServerPlayer p) {
        if (!PontBukkit.authentifie(p)) return Component.translatableWithFallback("message.heraliz_grades.kit.connexion", "Connecte-toi d'abord avec /login.");
        if (!WarpsServeur.auSpawn(p)) return msg("spawn", "Les échanges ne se font qu'au spawn.");
        if (!p.isAlive() || p.isSpectator()) return msg("impossible", "Impossible pour le moment.");
        return null;
    }

    static int demander(ServerPlayer p, ServerPlayer cible) {
        if (serveur == null) return 0;
        if (cible == p) return refuse(p, msg("soi", "Tu ne peux pas échanger avec toi-même."));
        Component r = refus(p);
        if (r != null) return refuse(p, r);
        if (dansEchange(p.getUUID())) return refuse(p, msg("deja", "Tu es déjà dans un échange."));
        Component rc = refus(cible);
        if (rc != null || dansEchange(cible.getUUID())) return refuse(p, msg("cible_indispo", "%s ne peut pas échanger pour le moment (au spawn, connecté, libre).", cible.getDisplayName()));
        // il m'avait déjà demandé : on accepte directement
        Map<UUID, Demande> miennes = DEMANDES.get(p.getUUID());
        Demande sa = miennes == null ? null : miennes.get(cible.getUUID());
        if (sa != null && sa.expire() > System.currentTimeMillis()) return accepter(p, cible);
        DEMANDES.computeIfAbsent(cible.getUUID(), k -> new HashMap<>()).put(p.getUUID(), new Demande(p.getUUID(), System.currentTimeMillis() + DEMANDE_MS));
        String nom = p.getGameProfile().getName();
        MutableComponent oui = Component.literal("[").append(msg("accepter", "Accepter")).append("]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accepter " + nom)));
        MutableComponent non = Component.literal("[").append(msg("refuser", "Refuser")).append("]").withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade refuser " + nom)));
        cible.sendSystemMessage(msg("recue", "%s te propose un échange (60 s).", p.getDisplayName()).withStyle(ChatFormatting.GOLD)
                .append(" ").append(oui).append(" ").append(non));
        p.sendSystemMessage(msg("envoyee", "Demande d'échange envoyée à %s.", cible.getDisplayName()).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    static int accepter(ServerPlayer p, ServerPlayer demandeur) {
        Map<UUID, Demande> miennes = DEMANDES.get(p.getUUID());
        Demande d = miennes == null ? null : miennes.remove(demandeur.getUUID());
        if (d == null || d.expire() < System.currentTimeMillis()) return refuse(p, msg("aucune", "Aucune demande d'échange de ce joueur."));
        Component r = refus(p), rd = refus(demandeur);
        if (r != null) return refuse(p, r);
        if (rd != null || dansEchange(demandeur.getUUID()) || dansEchange(p.getUUID())) return refuse(p, msg("cible_indispo", "%s ne peut pas échanger pour le moment (au spawn, connecté, libre).", demandeur.getDisplayName()));
        DEMANDES.remove(p.getUUID());
        DEMANDES.remove(demandeur.getUUID());
        Session s = new Session(new Cote(demandeur), new Cote(p));
        SESSIONS.put(demandeur.getUUID(), s);
        SESSIONS.put(p.getUUID(), s);
        for (Cote c : new Cote[]{s.a, s.b}) {
            Pb.solde(c.nom, rep -> {
                if (rep != null && rep.code() == 200) c.solde = rep.nombre("solde");
                if (SESSIONS.get(c.uuid) == s) envoyer(s);
            });
        }
        envoyer(s);
        return 1;
    }

    static int refuser(ServerPlayer p, ServerPlayer demandeur) {
        Map<UUID, Demande> miennes = DEMANDES.get(p.getUUID());
        if (miennes == null || miennes.remove(demandeur.getUUID()) == null) return refuse(p, msg("aucune", "Aucune demande d'échange de ce joueur."));
        demandeur.sendSystemMessage(msg("refusee", "%s a refusé l'échange.", p.getDisplayName()).withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(msg("tu_refuses", "Échange refusé.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    static int annulerCommande(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return refuse(p, msg("aucun", "Tu n'es dans aucun échange."));
        annuler(s, "annule_par:" + s.de(p.getUUID()).nom, p.getUUID());
        return 1;
    }

    private static int refuse(ServerPlayer p, Component c) {
        p.sendSystemMessage(c.copy().withStyle(ChatFormatting.RED));
        return 0;
    }

    // ------------------------------------------------------------------ actions de l'écran
    private static void action(ServerPlayer p, ActionEchange a) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        if (a.genre() == ActionEchange.Genre.ANNULER) {
            if (s.phase < 2) annuler(s, "annule_par:" + s.de(p.getUUID()).nom, p.getUUID());
            return;
        }
        if (s.phase >= 2) return;
        Cote moi = s.de(p.getUUID()), lui = s.autre(p.getUUID());
        switch (a.genre()) {
            case PROPOSER -> {
                int c = (int) a.valeur();
                if (c < 0 || c >= CASES_INVENTAIRE || moi.cases.contains(c) || moi.cases.size() >= ReseauBoutique.CASES_ECHANGE) return;
                ItemStack st = p.getInventory().getItem(c);
                if (st.isEmpty()) return;
                if (st.hasTag() && st.getTag().contains("HeraliszCamoProprio")) {
                    s.message = "camo";
                    envoyer(s);
                    return;
                }
                moi.cases.add(c);
                moi.copies.add(st.copy());
                change(s);
            }
            case RETIRER -> {
                int i = (int) a.valeur();
                if (i < 0 || i >= moi.cases.size()) return;
                moi.cases.remove(i);
                moi.copies.remove(i);
                change(s);
            }
            case PB -> {
                long v = a.valeur();
                if (v < 0 || v > MAX_PB || v == moi.pb) return;
                if (v > 0 && lui.pb > 0) {
                    s.message = "pb_un_seul";
                    envoyer(s);
                    return;
                }
                if (v > 0 && moi.solde >= 0 && v > moi.solde) {
                    s.message = "pb_solde";
                    envoyer(s);
                    return;
                }
                moi.pb = v;
                change(s);
            }
            case PRET -> {
                if (!moi.pret && moi.cases.isEmpty() && lui.cases.isEmpty() && moi.pb == 0 && lui.pb == 0) return;
                if (!moi.pret && !place(s, moi, lui, p)) {
                    s.message = "plein";
                    envoyer(s);
                    return;
                }
                moi.pret = !moi.pret;
                s.message = "";
                if (moi.pret && lui.pret) {
                    s.phase = 1;
                    s.reste = COMPTE_TICKS;
                } else {
                    s.phase = 0;
                }
                envoyer(s);
            }
            default -> {
            }
        }
    }

    /** Quelque chose a changé : les deux « Prêt » sautent, le compte à rebours s'arrête. */
    private static void change(Session s) {
        s.a.pret = false;
        s.b.pret = false;
        s.phase = 0;
        s.message = "";
        envoyer(s);
    }

    /** Le joueur aura-t-il la place pour ce qu'il reçoit (cases vides + cases qu'il donne) ? */
    private static boolean place(Session s, Cote moi, Cote lui, ServerPlayer p) {
        int libres = moi.cases.size();
        Inventory inv = p.getInventory();
        for (int i = 0; i < CASES_INVENTAIRE; i++) if (inv.getItem(i).isEmpty()) libres++;
        return libres >= lui.cases.size();
    }

    // ------------------------------------------------------------------ chaque tick
    static void tick() {
        if (serveur == null) return;
        ticks++;
        if (ticks % 200 == 0) {
            long now = System.currentTimeMillis();
            DEMANDES.values().forEach(m -> m.values().removeIf(d -> d.expire() < now));
            DEMANDES.values().removeIf(Map::isEmpty);
        }
        if (ticks % 600 == 0) reprendre();
        for (Session s : new java.util.HashSet<>(SESSIONS.values())) {
            if (SESSIONS.get(s.a.uuid) != s) continue;
            ServerPlayer pa = joueur(s.a.uuid), pb = joueur(s.b.uuid);
            if (pa == null || pb == null || refus(pa) != null || refus(pb) != null) {
                if (s.phase == 2) s.invalide = true;
                else annuler(s, "annule", null);
                continue;
            }
            boolean change = verifier(s.a, pa) | verifier(s.b, pb);
            if (change) {
                if (s.phase == 2) s.invalide = true;
                else change(s);
                continue;
            }
            if (s.phase == 1) {
                if (--s.reste <= 0) executer(s, pa, pb);
                else if (s.reste % 20 == 0) envoyer(s);
            }
        }
    }

    /** Les cases proposées contiennent-elles toujours la même chose ? Une case changée sort de l'échange. */
    private static boolean verifier(Cote c, ServerPlayer p) {
        boolean change = false;
        for (int i = c.cases.size() - 1; i >= 0; i--) {
            if (!ItemStack.matches(p.getInventory().getItem(c.cases.get(i)), c.copies.get(i))) {
                c.cases.remove(i);
                c.copies.remove(i);
                change = true;
            }
        }
        return change;
    }

    // ------------------------------------------------------------------ exécution
    private static void executer(Session s, ServerPlayer pa, ServerPlayer pb) {
        s.phase = 2;
        envoyer(s);
        Cote donneur = s.a.pb > 0 ? s.a : s.b.pb > 0 ? s.b : null;
        if (donneur == null) {
            finir(s, null);
            return;
        }
        Cote receveur = donneur == s.a ? s.b : s.a;
        Envoi v = new Envoi();
        v.ref = Pb.ref("trade");
        v.de = donneur.nom;
        v.vers = receveur.nom;
        v.montant = donneur.pb;
        v.etat = "envoye";
        ENVOIS.put(v.ref, v);
        if (!sauver()) {
            ENVOIS.remove(v.ref);
            annuler(s, "erreur", null);
            return;
        }
        Pb.transfert(v.ref, v.de, v.vers, v.montant, r -> {
            if (r == null) {
                // on ne sait pas si les PB sont partis : vérifié plus tard, rendus s'ils sont partis
                v.etat = "verifier";
                sauver();
                annuler(s, "injoignable", null);
                return;
            }
            if (!r.ok()) {
                ENVOIS.remove(v.ref);
                sauver();
                annuler(s, "solde_insuffisant".equals(r.raison())
                        ? "solde:" + v.de
                        : "refuse_site", null);
                return;
            }
            v.recu = r.nombre("recu");
            ServerPlayer a2 = joueur(s.a.uuid), b2 = joueur(s.b.uuid);
            boolean ok = !s.invalide && SESSIONS.get(s.a.uuid) == s && a2 != null && b2 != null && refus(a2) == null && refus(b2) == null
                    && !verifier(s.a, a2) && !verifier(s.b, b2);
            if (ok) {
                finir(s, v);
                ENVOIS.remove(v.ref);
                sauver();
            } else {
                v.etat = "annuler";
                sauver();
                annulerEnvoi(v);
                annuler(s, "rendu", null);
            }
        });
    }

    /** Fait passer les objets dans les deux sens, en une fois, puis sauvegarde les joueurs. */
    private static void finir(Session s, Envoi v) {
        ServerPlayer pa = joueur(s.a.uuid), pb = joueur(s.b.uuid);
        if (pa == null || pb == null || verifier(s.a, pa) || verifier(s.b, pb)) {
            annuler(s, "annule", null);
            return;
        }
        List<ItemStack> versB = retirer(s.a, pa), versA = retirer(s.b, pb);
        donner(pa, versA);
        donner(pb, versB);
        serveur.getPlayerList().saveAll();
        String recu = v == null ? "" : " ; " + v.de + " -> " + v.vers + " " + v.montant + " PB (reçu " + v.recu + ")";
        BoutiqueServeur.journal("trade " + s.a.nom + " <-> " + s.b.nom + " : " + resume(versB) + " <-> " + resume(versA) + recu);
        s.phase = 3;
        s.message = "fini";
        envoyer(s);
        SESSIONS.remove(s.a.uuid);
        SESSIONS.remove(s.b.uuid);
        for (ServerPlayer p : new ServerPlayer[]{pa, pb}) p.sendSystemMessage(msg("reussi", "Échange réussi !").withStyle(ChatFormatting.GREEN));
    }

    private static List<ItemStack> retirer(Cote c, ServerPlayer p) {
        List<ItemStack> l = new ArrayList<>();
        for (int slot : c.cases) {
            l.add(p.getInventory().getItem(slot));
            p.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        c.cases.clear();
        c.copies.clear();
        p.getInventory().setChanged();
        return l;
    }

    private static void donner(ServerPlayer p, List<ItemStack> l) {
        for (ItemStack st : l) {
            if (!p.getInventory().add(st) && !st.isEmpty()) p.drop(st, false);
        }
        p.inventoryMenu.broadcastChanges();
    }

    private static String resume(List<ItemStack> l) {
        if (l.isEmpty()) return "rien";
        StringBuilder b = new StringBuilder();
        for (ItemStack st : l) b.append(b.length() == 0 ? "" : ", ").append(st.getCount()).append("x ").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()));
        return b.toString();
    }

    private static void annuler(Session s, String message, UUID auteur) {
        SESSIONS.remove(s.a.uuid);
        SESSIONS.remove(s.b.uuid);
        s.phase = 4;
        s.message = message;
        envoyer(s);
    }

    // ------------------------------------------------------------------ PB à rendre
    /** Rend les PB d'un transfert qui ne doit pas compter (références fixes : jamais rendus deux fois). */
    private static void annulerEnvoi(Envoi v) {
        if (!EN_REPRISE.add(v.ref)) return;
        long reprendre = v.recu > 0 ? v.recu : v.montant - v.montant / 10;
        Pb.debiter(v.ref + "-rb", v.vers, reprendre, "trade annulé", r1 -> {
            if (r1 != null && !r1.ok()) LOG.error("[grades] trade {} : {} PB impossibles à reprendre à {} ({}) : à régler à la main", v.ref, reprendre, v.vers, r1.raison());
            if (r1 == null) {
                EN_REPRISE.remove(v.ref);
                return;
            }
            Pb.crediter(v.ref + "-ra", v.de, v.montant, "trade annulé", r2 -> {
                EN_REPRISE.remove(v.ref);
                if (r2 != null && r2.ok()) {
                    ENVOIS.remove(v.ref);
                    sauver();
                    BoutiqueServeur.journal("trade annulé " + v.ref + " : " + v.montant + " PB rendus à " + v.de + (r1.ok() ? ", " + reprendre + " repris à " + v.vers : ", RIEN repris à " + v.vers));
                    if (!r1.ok()) LienDiscord.evenement("⚠️ Trade annulé " + v.ref + " : " + reprendre + " PB n'ont pas pu être repris à " + v.vers + " (à régler à la main).");
                }
            });
        });
    }

    private static void reprendre() {
        for (Envoi v : new ArrayList<>(ENVOIS.values())) {
            if (EN_REPRISE.contains(v.ref)) continue;
            if ("annuler".equals(v.etat)) {
                annulerEnvoi(v);
            } else if ("verifier".equals(v.etat)) {
                EN_REPRISE.add(v.ref);
                Pb.statut(v.ref, r -> {
                    EN_REPRISE.remove(v.ref);
                    if (r == null || r.code() != 200) return;
                    if (r.corps().has("fait") && r.corps().get("fait").getAsBoolean()) {
                        v.etat = "annuler";
                        sauver();
                        annulerEnvoi(v);
                    } else {
                        ENVOIS.remove(v.ref);
                        sauver();
                    }
                });
            }
        }
    }

    private static boolean sauver() {
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling("echanges_en_cours.json.tmp");
            Files.writeString(tmp, GSON.toJson(new ArrayList<>(ENVOIS.values())), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception ex) {
            LOG.error("[grades] {} impossible à écrire ({})", fichier, ex.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ envoi de l'état
    private static void envoyer(Session s) {
        envoyer(s, s.a, s.b);
        envoyer(s, s.b, s.a);
        s.message = s.phase >= 3 ? s.message : "";
    }

    private static void envoyer(Session s, Cote moi, Cote lui) {
        ServerPlayer p = joueur(moi.uuid);
        if (p == null) return;
        List<ItemStack> mes = new ArrayList<>();
        for (ItemStack st : moi.copies) mes.add(st.copy());
        List<ItemStack> ses = new ArrayList<>();
        for (ItemStack st : lui.copies) ses.add(st.copy());
        ReseauBoutique.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new ReseauBoutique.EtatEchange(lui.nom, s.phase, s.reste, mes,
                new ArrayList<>(moi.cases), moi.pb, moi.pret, ses, lui.pb, lui.pret, moi.solde, s.message == null ? "" : s.message));
    }

    static void deconnexion(UUID u) {
        DEMANDES.remove(u);
        Session s = SESSIONS.get(u);
        if (s == null) return;
        if (s.phase == 2) s.invalide = true;
        else annuler(s, "annule", null);
    }
}
