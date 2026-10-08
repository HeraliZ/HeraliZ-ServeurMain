package fr.heralisz.clans.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heralisz.clans.api.ClansApi;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.Evenement;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.model.ReglagesPerms;
import fr.heralisz.clans.server.ClanService;
import fr.heralisz.clans.server.ClanStore;
import fr.heralisz.clans.server.Sync;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Essais des permissions par joueur, banc d'essai seulement (etape « testPerms » de {@link ClansHarness}), sur le fil du
 * serveur integre :
 * <ul>
 * <li>« regles » : regles du serveur, journal regroupe, JSON (aller-retour, lecture tolerante, migration de
 * GERER_PERMISSIONS), reseau (snapshot avec reglages, ancien format sans le champ) et ClansApi, sur un clan
 * synthetique ; une ligne « [clans] test perms : n ok, m echec(s) » ;</li>
 * <li>« preparer » / « nettoyer » : vrai clan de test PRMS ou le joueur de dev est haut grade (essai de bout en bout par
 * l'interface : paquets reels, snapshot reel) ; le joueur retrouve ensuite son clan et son grade d'origine.</li>
 * </ul>
 */
public final class TestPerms {
    private static final Logger LOG = LogUtils.getLogger();
    private static int ok, ko;
    @Nullable
    private static UUID clanTest, clanOrigine;
    @Nullable
    private static Membre membreOrigine;

    private TestPerms() {
    }

    /** Lance l'essai demande sur le fil du serveur integre. */
    public static void lancer(@Nullable MinecraftServer server, String quoi) {
        if (server == null) {
            LOG.warn("[clans] test perms {} : pas de serveur integre", quoi);
            return;
        }
        server.execute(() -> {
            try {
                switch (quoi) {
                    case "regles" -> regles();
                    case "preparer" -> preparer(server);
                    case "nettoyer" -> nettoyer(server);
                    default -> LOG.warn("[clans] test perms inconnu : {}", quoi);
                }
            } catch (Exception ex) {
                LOG.error("[clans] test perms {} : exception", quoi, ex);
            }
        });
    }

    private static void verifier(String nom, boolean cond) {
        if (cond) ok++;
        else {
            ko++;
            LOG.warn("[clans] test perms ECHEC : {}", nom);
        }
    }

    @Nullable
    private static String refus(Clan c, @Nullable UUID acteur, UUID cible, Perm p, Perm.Etat e) {
        return ClanService.refusPermJoueur(c, acteur, cible, p, e);
    }

    private static Membre membre(Clan c, UUID u, String nom, Grade g, long now) {
        Membre m = new Membre(u, nom, g, now, now, null);
        c.membres.put(u, m);
        return m;
    }

    // ------------------------------------------------------------------ regles
    static void regles() {
        ok = ko = 0;
        long now = System.currentTimeMillis();
        UUID chef = new UUID(9, 1), h = new UUID(9, 2), h2 = new UUID(9, 3), m = new UUID(9, 4), m2 = new UUID(9, 6), r = new UUID(9, 5);
        Clan c = new Clan(UUID.randomUUID(), "ZTPRM", "Essai regles", chef, now);
        membre(c, chef, "ChefT", Grade.CHEF, now);
        membre(c, h, "HautT", Grade.HAUT_GRADE, now);
        membre(c, h2, "Haut2T", Grade.HAUT_GRADE, now);
        membre(c, m, "MembreT", Grade.MEMBRE, now);
        membre(c, m2, "Membre2T", Grade.MEMBRE, now);
        membre(c, r, "RecrueT", Grade.RECRUE, now);

        // defauts : GERER_PERMISSIONS au chef et aux hauts grades
        verifier("haut grade gere par defaut", c.peut(h, Perm.GERER_PERMISSIONS));
        verifier("membre ne gere pas par defaut", !c.peut(m, Perm.GERER_PERMISSIONS));
        verifier("recrue sans construire", !c.peut(r, Perm.BASE_CONSTRUIRE));
        verifier("chef a tout", c.peut(chef, Perm.MODIFIER_TAG) && c.peut(chef, Perm.GERER_PERMISSIONS));
        // accorder ce qu'on a
        verifier("haut grade autorise construire a une recrue (refus nul)", refus(c, h, r, Perm.BASE_CONSTRUIRE, Perm.Etat.AUTORISE) == null);
        verifier("... applique", ClanService.definirPermJoueur(h, c, r, Perm.BASE_CONSTRUIRE, Perm.Etat.AUTORISE) && c.peut(r, Perm.BASE_CONSTRUIRE));
        verifier("etat lu", c.etatPerm(r, Perm.BASE_CONSTRUIRE) == Perm.Etat.AUTORISE);
        // pas ce qu'on n'a pas
        verifier("pas d'accord de MODIFIER_NOM par un haut grade", "msg.perm_joueur_accorder".equals(refus(c, h, m, Perm.MODIFIER_NOM, Perm.Etat.AUTORISE)));
        verifier("... rien d'applique", !ClanService.definirPermJoueur(h, c, m, Perm.MODIFIER_NOM, Perm.Etat.AUTORISE) && !c.peut(m, Perm.MODIFIER_NOM));
        verifier("refuser ce qu'on n'a pas reste permis", refus(c, h, m, Perm.MODIFIER_NOM, Perm.Etat.REFUSE) == null);
        // hierarchie
        verifier("haut grade ne regle pas un haut grade", "msg.perm_joueur_hierarchie".equals(refus(c, h, h2, Perm.CHAT_CLAN, Perm.Etat.REFUSE)));
        verifier("ni lui-meme", "msg.perm_joueur_hierarchie".equals(refus(c, h, h, Perm.CHAT_CLAN, Perm.Etat.REFUSE)));
        // droit de gerer
        verifier("membre sans GERER_PERMISSIONS refuse", "msg.permission".equals(refus(c, m, r, Perm.CHAT_CLAN, Perm.Etat.REFUSE)));
        verifier("non-membre refuse", refus(c, new UUID(9, 99), r, Perm.CHAT_CLAN, Perm.Etat.REFUSE) != null);
        // chef non reglable, meme par le systeme
        verifier("chef non reglable (systeme)", "msg.perm_joueur_chef".equals(refus(c, null, chef, Perm.CHAT_CLAN, Perm.Etat.REFUSE)));
        verifier("chef non reglable (par lui-meme)", "msg.perm_joueur_chef".equals(refus(c, chef, chef, Perm.CHAT_CLAN, Perm.Etat.REFUSE)));
        // le chef accorde tout
        verifier("chef accorde MODIFIER_TAG a un membre", ClanService.definirPermJoueur(chef, c, m, Perm.MODIFIER_TAG, Perm.Etat.AUTORISE) && c.peut(m, Perm.MODIFIER_TAG));
        // refus individuel plus fort que le grade
        ClanService.definirPermJoueur(chef, c, m, Perm.BANQUE_DEPOSER, Perm.Etat.REFUSE);
        verifier("refus l'emporte sur le grade", !c.peut(m, Perm.BANQUE_DEPOSER));
        // retour au grade depuis un refus = accorder : un haut grade prive de BANQUE_DEPOSER ne peut pas
        ClanService.definirPermJoueur(chef, c, h, Perm.BANQUE_DEPOSER, Perm.Etat.REFUSE);
        verifier("retour au grade d'un refus = accorder", "msg.perm_joueur_accorder".equals(refus(c, h, m, Perm.BANQUE_DEPOSER, Perm.Etat.HERITE)));
        verifier("... le chef le peut", refus(c, chef, m, Perm.BANQUE_DEPOSER, Perm.Etat.HERITE) == null);
        // GERER_PERMISSIONS individuel : un membre autorise regle une recrue, pas un autre membre
        ClanService.definirPermJoueur(chef, c, m, Perm.GERER_PERMISSIONS, Perm.Etat.AUTORISE);
        verifier("membre autorise a gerer : recrue reglable", refus(c, m, r, Perm.CHAT_CLAN, Perm.Etat.REFUSE) == null);
        verifier("membre autorise a gerer : pas un autre membre", "msg.perm_joueur_hierarchie".equals(refus(c, m, m2, Perm.CHAT_CLAN, Perm.Etat.REFUSE)));
        // journal : deux changements rapproches du meme auteur sur la meme cible = une ligne
        ClanService.definirPermJoueur(h, c, r, Perm.BASE_COFFRES, Perm.Etat.AUTORISE);
        Evenement e1 = c.journal.get(0);
        verifier("journal : une ligne par changement", e1.genre() == Evenement.Genre.PERMISSION && e1.args().get(2).equals("BASE_COFFRES"));
        verifier("journal : noms de la cible et de l'auteur", e1.args().get(0).equals("RecrueT") && e1.args().get(1).equals("HautT"));
        ClanService.definirPermJoueur(h, c, r, Perm.BASE_PORTES, Perm.Etat.REFUSE);
        ClanService.definirPermJoueur(h, c, r, Perm.CHAT_CLAN, Perm.Etat.REFUSE);
        Evenement e0 = c.journal.get(0);
        verifier("journal regroupe (3 changements)", e0.genre() == Evenement.Genre.PERMISSION && "*".equals(e0.args().get(2))
                && "3".equals(e0.args().get(3)) && c.journal.get(1) != e1 && !c.journal.contains(e1));
        // transfert : le nouveau chef perd ses reglages (il a tout)
        // (teste sur une copie : la regle vit dans ClanService.transferer, qui demande un joueur connecte)

        // JSON aller-retour
        Clan c2 = Clan.fromJson(c.toJson());
        verifier("json relu", c2 != null);
        if (c2 != null) {
            verifier("json : reglages relus", c2.etatPerm(r, Perm.BASE_CONSTRUIRE) == Perm.Etat.AUTORISE
                    && c2.etatPerm(m, Perm.BANQUE_DEPOSER) == Perm.Etat.REFUSE && c2.etatPerm(r, Perm.BASE_PORTES) == Perm.Etat.REFUSE);
            boolean memes = true;
            for (UUID u : c.membres.keySet()) for (Perm p : Perm.values()) memes &= c.peut(u, p) == c2.peut(u, p);
            verifier("json : memes permissions effectives", memes);
            verifier("json : journal PERMISSION relu", c2.journal.get(0).genre() == Evenement.Genre.PERMISSION);
        }
        // lecture tolerante d'un membre
        JsonObject jm = JsonParser.parseString("{\"uuid\":\"" + r + "\",\"nom\":\"X\",\"grade\":\"RECRUE\",\"permsJoueur\":"
                + "{\"FOO\":\"AUTORISE\",\"BASE_CASSER\":\"bidule\",\"base_entrer\":\"refuse\",\"CHAT_CLAN\":{},\"INVITER\":\"herite\"}}").getAsJsonObject();
        Membre mt = Membre.fromJson(jm);
        verifier("json tolerant : seul BASE_ENTRER garde", mt != null && mt.permsJoueur.size() == 1 && mt.etatPerm(Perm.BASE_ENTRER) == Perm.Etat.REFUSE);
        jm.add("permsJoueur", new JsonArray());
        Membre mt2 = Membre.fromJson(jm);
        verifier("json tolerant : mauvais type ignore", mt2 != null && mt2.permsJoueur.isEmpty());
        // migration : un fichier qui ne connaissait pas GERER_PERMISSIONS le donne aux hauts grades (defaut du grade)
        JsonObject jc = c.toJson();
        JsonArray connues = new JsonArray();
        for (JsonElement el : jc.getAsJsonArray("permsConnues")) if (!el.getAsString().equals("GERER_PERMISSIONS")) connues.add(el);
        jc.add("permsConnues", connues);
        JsonObject perms = jc.getAsJsonObject("perms");
        for (String g : List.of("HAUT_GRADE", "MEMBRE", "RECRUE")) {
            JsonArray a = new JsonArray();
            for (JsonElement el : perms.getAsJsonArray(g)) if (!el.getAsString().equals("GERER_PERMISSIONS")) a.add(el);
            perms.add(g, a);
        }
        Clan c3 = Clan.fromJson(jc);
        verifier("migration : haut grade recoit GERER_PERMISSIONS", c3 != null && c3.permsDe(Grade.HAUT_GRADE).contains(Perm.GERER_PERMISSIONS)
                && !c3.permsDe(Grade.MEMBRE).contains(Perm.GERER_PERMISSIONS));

        // reseau : snapshot avec reglages, puis sans le champ (paquet d'une version precedente)
        EnumMap<Grade, Integer> pm = new EnumMap<>(Grade.class);
        for (Grade g : Grade.modifiables()) pm.put(g, Perm.masque(c.permsDe(g)));
        ClanSnapshot.MembreVue mv = ClanSnapshot.MembreVue.de(c.membre(r), false, 0);
        ClanSnapshot snap = new ClanSnapshot(c.id, c.tag, c.nom, "", Banniere.DEFAUT, chef, now, 0, 1, 0, 100, 6, 1, List.of(mv), pm, r,
                Grade.RECRUE, 0, "", 0, 0, 0, 0, List.of(), List.of(), List.of(), 0, 0, List.of(), List.of(), now,
                Map.of(r, c.membre(r).reglages()));
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        snap.write(b);
        ClanSnapshot lu = ClanSnapshot.read(b);
        verifier("reseau : reglages relus", lu.reglagesDe(r).equals(snap.reglagesDe(r)) && !lu.reglagesDe(r).vide());
        verifier("reseau : permission vue par le client", lu.jePeux(Perm.BASE_CONSTRUIRE) && !lu.jePeux(Perm.BASE_PORTES));
        FriendlyByteBuf b2 = new FriendlyByteBuf(Unpooled.buffer());
        snap.avecPerms(pm, Map.of()).write(b2);
        b2.writerIndex(b2.writerIndex() - 1); // retire le compteur (0) : format d'avant les reglages
        ClanSnapshot ancien = ClanSnapshot.read(b2);
        verifier("reseau : ancien format lu sans erreur", ancien.reglages().isEmpty() && ancien.id().equals(c.id));
        // masques
        ReglagesPerms rp = ReglagesPerms.AUCUN.avec(Perm.GERER_PERMISSIONS, Perm.Etat.AUTORISE).avec(Perm.INVITER, Perm.Etat.REFUSE);
        verifier("masques : bit 22 et disjonction", rp.etat(Perm.GERER_PERMISSIONS) == Perm.Etat.AUTORISE && rp.etat(Perm.INVITER) == Perm.Etat.REFUSE
                && new ReglagesPerms(0b11, 0b11).refuse() == 0);

        // API (clan dans le store le temps de l'essai)
        ClanStore s = ClanStore.get();
        if (s != null) {
            s.ajouter(c);
            try {
                verifier("api aPerm (clan donne)", ClansApi.aPerm(r, c.id, Perm.BASE_CONSTRUIRE));
                verifier("api aPerm (son clan)", ClansApi.aPerm(r, Perm.BASE_CONSTRUIRE) && !ClansApi.aPerm(m, Perm.BANQUE_DEPOSER));
                verifier("api etatPermJoueur", ClansApi.etatPermJoueur(c.id, r, Perm.BASE_CONSTRUIRE) == Perm.Etat.AUTORISE
                        && ClansApi.etatPermJoueur(c.id, chef, Perm.BASE_CONSTRUIRE) == Perm.Etat.HERITE);
                verifier("api definir (systeme)", ClansApi.definirPermJoueur(null, c.id, r, Perm.BASE_CONSTRUIRE, Perm.Etat.HERITE)
                        && !ClansApi.aPerm(r, Perm.BASE_CONSTRUIRE));
                verifier("api definir refuse (hierarchie)", !ClansApi.definirPermJoueur(h, c.id, h2, Perm.CHAT_CLAN, Perm.Etat.REFUSE)
                        && ClansApi.aPerm(h2, Perm.CHAT_CLAN));
                verifier("api definir refuse (chef)", !ClansApi.definirPermJoueur(null, c.id, chef, Perm.CHAT_CLAN, Perm.Etat.REFUSE));
            } finally {
                s.supprimer(c);
            }
        } else {
            LOG.warn("[clans] test perms : pas de store, essais de l'API sautes");
        }
        LOG.info("[clans] test perms : {} ok, {} echec(s)", ok, ko);
    }

    // ------------------------------------------------------------------ essai de bout en bout
    /** Clan PRMS : chef fictif, le joueur de dev en haut grade, un haut grade, deux membres et une recrue fictifs. */
    static void preparer(MinecraftServer server) {
        ClanStore s = ClanStore.get();
        List<ServerPlayer> joueurs = server.getPlayerList().getPlayers();
        if (s == null || joueurs.isEmpty()) return;
        ServerPlayer dev = joueurs.get(0);
        UUID u = dev.getUUID();
        long now = System.currentTimeMillis();
        Clan reste = s.parTag("PRMS");
        if (reste != null) s.supprimer(reste); // essai precedent interrompu
        Clan actuel = s.clanDe(u);
        if (actuel != null && actuel.estChef(u)) {
            LOG.warn("[clans] test perms : le joueur de dev est chef de [{}], essai annule", actuel.tag);
            return;
        }
        clanOrigine = actuel == null ? null : actuel.id;
        membreOrigine = actuel == null ? null : actuel.membres.remove(u);
        if (actuel != null) Sync.marquer(actuel);
        UUID chef = new UUID(8, 1);
        Clan c = new Clan(UUID.randomUUID(), "PRMS", "Essai permissions", chef, now);
        c.banniere = Banniere.DEFAUT;
        membre(c, chef, "Sentinelle", Grade.CHEF, now);
        membre(c, u, dev.getGameProfile().getName(), Grade.HAUT_GRADE, now);
        membre(c, new UUID(8, 2), "Falaise", Grade.HAUT_GRADE, now);
        membre(c, new UUID(8, 3), "Caillou", Grade.MEMBRE, now);
        membre(c, new UUID(8, 4), "Mousse", Grade.MEMBRE, now).definirPerm(Perm.CHAT_CLAN, Perm.Etat.REFUSE);
        membre(c, new UUID(8, 5), "Brindille", Grade.RECRUE, now);
        s.ajouter(c);
        clanTest = c.id;
        Sync.envoyerEtat(dev);
        Sync.diffuserTags(c, null);
        LOG.info("[clans] test perms : clan [PRMS] pret ({} haut grade)", dev.getGameProfile().getName());
    }

    /** Journal des reglages obtenus, puis le joueur retrouve son clan et son grade d'origine. */
    static void nettoyer(MinecraftServer server) {
        ClanStore s = ClanStore.get();
        if (s == null) return;
        Clan c = clanTest == null ? null : s.get(clanTest);
        if (c != null) {
            for (Membre m : c.membres.values()) {
                if (!m.permsJoueur.isEmpty()) LOG.info("[clans] test perms : [PRMS] {} -> {}", m.dernierNom, m.permsJoueur);
            }
            for (int i = 0; i < Math.min(4, c.journal.size()); i++) LOG.info("[clans] test perms : journal {} {}", c.journal.get(i).genre(), c.journal.get(i).args());
            s.supprimer(c);
        }
        List<ServerPlayer> joueurs = server.getPlayerList().getPlayers();
        if (!joueurs.isEmpty()) {
            ServerPlayer dev = joueurs.get(0);
            Clan o = clanOrigine == null ? null : s.get(clanOrigine);
            if (o != null && membreOrigine != null) {
                o.membres.put(dev.getUUID(), membreOrigine);
                s.lien(dev.getUUID()).clan = o.id;
                Sync.marquer(o);
            }
            Sync.envoyerEtat(dev);
            if (o != null) Sync.diffuserTags(o, null);
        }
        s.marquerDirty();
        s.marquerDirtyJoueurs();
        clanTest = null;
        LOG.info("[clans] test perms : clan de test retire, joueur rendu a son clan d'origine");
    }
}
