package fr.heraliz.ping.dev;

import fr.heraliz.ping.HeralizPing;
import fr.heraliz.ping.PingConfig;
import fr.heraliz.ping.PingType;
import fr.heraliz.ping.net.Paquets;
import fr.heraliz.ping.server.Cercles;
import fr.heraliz.ping.server.Regles;
import fr.heraliz.ping.server.PingService;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Verifications cote serveur du banc d'essai (fil serveur, client de dev seulement). Faux joueurs ({@link FauxJoueur})
 * places autour du joueur du banc :
 * <ul>
 *     <li>Allie : membre du clan [PING] du joueur ; Etranger : sans clan ; Spectre : membre du clan en mode spectateur ;</li>
 *     <li>G1, G2 : un groupe (/groupe) sans clan ; G3 : seul ;</li>
 *     <li>A1, A2 : equipe A d'une partie, B1 : equipe B, Mort : equipe A eliminee (spectateur de la partie).</li>
 * </ul>
 * Chaque verification ecrit « [ping-banc] OK » ou « [ping-banc] ECHEC » dans run/logs/latest.log, et un bilan a la fin.
 */
final class BancServeur {
    private BancServeur() {
    }

    private static final Map<String, FauxJoueur> FAUX = new LinkedHashMap<>();
    private static BancLiens.PartieTest partie;
    private static int ok, echecs;
    private static final List<String> ECHECS = new ArrayList<>();

    static void log(String f, Object... a) {
        HeralizPing.LOG.info("[ping-banc] " + f, a);
    }

    private static void verifier(boolean cond, String quoi, Object... a) {
        String t = String.format(Locale.ROOT, quoi.replace("{}", "%s"), a);
        if (cond) {
            ok++;
            log("OK     {}", t);
        } else {
            echecs++;
            ECHECS.add(t);
            log("ECHEC  {}", t);
        }
    }

    static void etape(MinecraftServer s, String nom, UUID moi) {
        ServerPlayer dev = s.getPlayerList().getPlayer(moi);
        if (dev == null) return;
        try {
            switch (nom) {
                case "preparer" -> preparer(s, dev);
                case "zero" -> zero();
                case "verif_clan" -> verifClan();
                case "verif_groupe" -> verifGroupe(dev);
                case "verif_match" -> verifMatch(s, dev);
                case "verif_cadence" -> verifCadence(dev);
                case "verif_cadence_suite" -> verifCadenceSuite(dev);
                case "verif_vue" -> verifVue();
                case "verif_spectateur" -> verifSpectateur(dev);
                case "mesure" -> mesure(s, dev);
                case "nettoyer" -> nettoyer(s);
                case "bilan" -> bilan();
                default -> log("etape serveur inconnue : {}", nom);
            }
        } catch (Throwable t) {
            echecs++;
            ECHECS.add(nom + " : " + t);
            HeralizPing.LOG.error("[ping-banc] ECHEC  etape {} : exception", nom, t);
        }
    }

    private static FauxJoueur faux(String nom) {
        return FAUX.get(nom);
    }

    private static void preparer(MinecraftServer s, ServerPlayer dev) throws ReflectiveOperationException {
        ServerLevel niveau = dev.serverLevel();
        double y = dev.getY();
        Object[][] places = {
                {"Allie", -6.5, 4.5}, {"Etranger", 6.5, 4.5}, {"Spectre", -8.5, 2.5},
                {"G1", -20.5, 0.5}, {"G2", -22.5, 0.5}, {"G3", -24.5, 0.5},
                {"A1", 20.5, 0.5}, {"A2", 22.5, 0.5}, {"B1", 24.5, 0.5}, {"Mort", 26.5, 0.5}, {"Mur", 0.5, 10.5}};
        for (Object[] p : places) {
            String nom = (String) p[0];
            FAUX.put(nom, FauxJoueur.creer(s, niveau, nom, (double) p[1], y, (double) p[2]));
        }
        boolean clan = BancLiens.fonderClan(dev);
        verifier(clan, "clan [PING] fonde pour le joueur du banc");
        BancLiens.ajouterAuClan(dev, faux("Allie").joueur);
        BancLiens.ajouterAuClan(dev, faux("Spectre").joueur);
        faux("Spectre").joueur.setGameMode(GameType.SPECTATOR);
        BancLiens.groupe(List.of(faux("G1").joueur.getUUID(), faux("G2").joueur.getUUID()));
        zero();
        log("faux joueurs : {}", FAUX.keySet());
    }

    /** Vide les boites de reception des faux joueurs. */
    private static void zero() {
        for (FauxJoueur f : FAUX.values()) f.compter();
    }

    private static Map<String, Integer> recus() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Map.Entry<String, FauxJoueur> e : FAUX.entrySet()) m.put(e.getKey(), e.getValue().pings());
        return m;
    }

    /** Ping du joueur du banc (vrai client, vrai reseau) : le membre du clan le recoit, l'etranger et le spectateur non. */
    private static void verifClan() {
        Map<String, Integer> r = recus();
        log("reception apres le ping du joueur du banc : {} ; cercle {} : {}", r, Cercles.derniere(), PingService.derniersDestinataires());
        verifier(r.get("Allie") >= 1, "membre du clan (Allie) recoit le ping : {}", r.get("Allie"));
        verifier(r.get("Etranger") == 0, "joueur hors clan (Etranger) ne recoit rien : {}", r.get("Etranger"));
        verifier(r.get("Spectre") == 0, "membre du clan en mode spectateur (Spectre) ne recoit rien : {}", r.get("Spectre"));
        int autres = r.get("G1") + r.get("G2") + r.get("G3") + r.get("A1") + r.get("A2") + r.get("B1") + r.get("Mort") + r.get("Mur");
        verifier(autres == 0, "aucun autre joueur ne recoit le ping : {}", autres);
    }

    private static Paquets.PingC2S solDevant(ServerPlayer p, int type, double dz) {
        double x = p.getX(), z = p.getZ() + dz, y = Math.floor(p.getY());
        BlockPos b = BlockPos.containing(x, y - 0.5, z);
        return new Paquets.PingC2S(type, -1, b.asLong(), x, y, z);
    }

    /** Joueur sans clan dans un groupe : son groupe recoit, pas les autres. */
    private static void verifGroupe(ServerPlayer dev) {
        zero();
        FauxJoueur g1 = faux("G1");
        PingService.remplirSeau(g1.joueur.getUUID());
        long avant = PingService.relayes;
        PingService.recevoir(g1.joueur, solDevant(g1.joueur, PingType.ALLER.id(), 4));
        verifier(PingService.relayes == avant + 1, "ping de G1 (sans clan, en groupe) accepte");
        verifier(Cercles.derniere() == Regles.Origine.GROUPE, "cercle retenu pour G1 : {}", Cercles.derniere());
        Map<String, Integer> r = recus();
        log("reception apres le ping de G1 : {}", r);
        verifier(r.get("G2") == 1, "membre du groupe (G2) recoit : {}", r.get("G2"));
        verifier(r.get("G1") == 1, "l'auteur (G1) recoit son propre ping : {}", r.get("G1"));
        verifier(r.get("G3") == 0 && r.get("Allie") == 0 && r.get("Etranger") == 0, "hors groupe : G3 {}, Allie {}, Etranger {}", r.get("G3"),
                r.get("Allie"), r.get("Etranger"));
        // G3 : ni clan ni groupe -> lui seul
        zero();
        FauxJoueur g3 = faux("G3");
        PingService.remplirSeau(g3.joueur.getUUID());
        PingService.recevoir(g3.joueur, solDevant(g3.joueur, PingType.LOOT.id(), 4));
        r = recus();
        int total = r.values().stream().mapToInt(Integer::intValue).sum();
        verifier(Cercles.derniere() == Regles.Origine.SEUL && r.get("G3") == 1 && total == 1, "sans clan ni groupe : seul l'auteur recoit ({})",
                total);
    }

    /** En match : l'equipe seule (pas le clan, pas l'autre equipe, pas les spectateurs). */
    private static void verifMatch(MinecraftServer s, ServerPlayer dev) {
        FauxJoueur a1 = faux("A1"), a2 = faux("A2"), b1 = faux("B1"), mort = faux("Mort");
        // A1 est aussi membre du clan du joueur du banc : en match, c'est l'equipe qui compte
        BancLiens.ajouterAuClan(dev, a1.joueur);
        partie = BancLiens.ouvrirPartie(s, a1.joueur, a2.joueur, b1.joueur, mort.joueur);
        zero();
        PingService.remplirSeau(a1.joueur.getUUID());
        long avant = PingService.relayes;
        PingService.recevoir(a1.joueur, solDevant(a1.joueur, PingType.ENNEMI.id(), 4));
        verifier(PingService.relayes == avant + 1, "ping de A1 en match accepte");
        verifier(Cercles.derniere() == Regles.Origine.EQUIPE, "cercle retenu en match : {}", Cercles.derniere());
        Map<String, Integer> r = recus();
        log("reception apres le ping de A1 (match) : {}", r);
        verifier(r.get("A2") == 1, "coequipier (A2) recoit : {}", r.get("A2"));
        verifier(r.get("B1") == 0, "equipe adverse (B1) ne recoit rien : {}", r.get("B1"));
        verifier(r.get("Mort") == 0, "coequipier spectateur (Mort) ne recoit rien : {}", r.get("Mort"));
        verifier(r.get("Allie") == 0, "membre du clan hors match (Allie) ne recoit rien : {}", r.get("Allie"));
        // un spectateur de la partie ne pingue pas
        zero();
        long refus = PingService.refusSpectateur;
        PingService.remplirSeau(mort.joueur.getUUID());
        PingService.recevoir(mort.joueur, solDevant(mort.joueur, PingType.AIDE.id(), 4));
        verifier(PingService.refusSpectateur == refus + 1, "spectateur de la partie : ping refuse");
        // reglage : les spectateurs voient
        PingConfig.SPECTATEURS_VOIENT.set(true);
        zero();
        PingService.remplirSeau(a1.joueur.getUUID());
        PingService.recevoir(a1.joueur, solDevant(a1.joueur, PingType.DANGER.id(), 5));
        r = recus();
        verifier(r.get("Mort") == 1, "avec spectateurs_voient = oui, le spectateur de l'equipe recoit : {}", r.get("Mort"));
        PingConfig.SPECTATEURS_VOIENT.set(false);
        BancLiens.fermerPartie(partie, List.of(a1.joueur, a2.joueur, b1.joueur, mort.joueur));
        partie = null;
        BancLiens.retirerDuClan(dev, a1.joueur);
    }

    /** Anti-spam : rafale de 10 pings d'un coup, seuls « rafale » passent ; un de plus apres la recharge. */
    private static void verifCadence(ServerPlayer dev) {
        PingService.remplirSeau(dev.getUUID());
        long avant = PingService.relayes, refus = PingService.refusCadence;
        for (int i = 0; i < 10; i++) PingService.recevoir(dev, solDevant(dev, PingType.ALLER.id(), 3 + i * 0.5));
        long passes = PingService.relayes - avant;
        int rafale = PingConfig.RAFALE.get();
        verifier(passes == rafale, "rafale de 10 pings : {} acceptes (rafale = {}), {} refuses", passes, rafale,
                PingService.refusCadence - refus);
        avantCadence = PingService.relayes;
    }

    private static long avantCadence;

    private static void verifCadenceSuite(ServerPlayer dev) {
        PingService.recevoir(dev, solDevant(dev, PingType.ALLER.id(), 6));
        verifier(PingService.relayes == avantCadence + 1, "apres {} ms, un nouveau ping passe", PingConfig.RECHARGE_MS.get());
    }

    /** Ligne de vue et portee verifiees par le serveur. */
    private static void verifVue() {
        FauxJoueur mur = faux("Mur");
        ServerPlayer p = mur.joueur;
        // derriere le mur (z = 14) : refuse
        PingService.remplirSeau(p.getUUID());
        long refusVue = PingService.refusVue, relayes = PingService.relayes;
        PingService.recevoir(p, solDevant(p, PingType.ENNEMI.id(), 8));
        verifier(PingService.refusVue == refusVue + 1 && PingService.relayes == relayes, "cible derriere un mur : refusee (ligne de vue)");
        // devant le mur : accepte
        PingService.recevoir(p, solDevant(p, PingType.ENNEMI.id(), 2));
        verifier(PingService.relayes == relayes + 1, "cible en vue devant le mur : acceptee");
        // trop loin
        long refusPortee = PingService.refusPortee;
        PingService.remplirSeau(p.getUUID());
        PingService.recevoir(p, solDevant(p, PingType.ALLER.id(), -(PingConfig.PORTEE.get() + 40)));
        verifier(PingService.refusPortee == refusPortee + 1, "cible a {} blocs (portee {}) : refusee", PingConfig.PORTEE.get() + 40,
                PingConfig.PORTEE.get());
    }

    /** Un joueur en mode spectateur ne pingue pas. */
    private static void verifSpectateur(ServerPlayer dev) {
        FauxJoueur sp = faux("Spectre");
        long refus = PingService.refusSpectateur;
        PingService.remplirSeau(sp.joueur.getUUID());
        PingService.recevoir(sp.joueur, solDevant(sp.joueur, PingType.ALLER.id(), 3));
        verifier(PingService.refusSpectateur == refus + 1, "joueur en mode spectateur : ping refuse");
    }

    /** Cout serveur d'un ping, cout d'un tick au repos, taille des paquets. */
    private static void mesure(MinecraftServer s, ServerPlayer dev) {
        mesurer("Etranger", 2000, "seul");
        mesurer("Allie", 300, "clan de 2 joueurs en ligne");
        PingService.oublierActifs();
        int k = 1_000_000;
        long t1 = System.nanoTime();
        for (int i = 0; i < k; i++) PingService.tick(s);
        log("MESURE serveur au repos : {} ns par tick (aucun ping actif : un test de liste vide)",
                String.format(Locale.ROOT, "%.2f", (System.nanoTime() - t1) / (double) k));
        verifier(PingService.actifs() == 0, "au repos, aucun ping actif garde en memoire");
        // tailles des paquets (octets du message, index compris)
        BlockPos b = dev.blockPosition();
        log("MESURE paquets : PingC2S bloc {} o, entite {} o ; PingS2C {} o (pseudo de {} lettres) ; MajS2C {} o, fin {} o ; Annuler {} o ; Reglages {} o",
                taille(o -> new Paquets.PingC2S(7, -1, b.asLong(), b.getX() + 0.5, b.getY(), b.getZ() + 0.5).ecrire(o)),
                taille(o -> new Paquets.PingC2S(0, 1234, b.asLong(), b.getX() + 0.5, b.getY(), b.getZ() + 0.5).ecrire(o)),
                taille(o -> new Paquets.PingS2C(123456, 0, false, 1234, b.asLong(), 1, 2, 3, 0xF2A93B, 10, dev.getGameProfile().getName()).ecrire(o)),
                dev.getGameProfile().getName().length(),
                taille(o -> new Paquets.MajS2C(123456, false, b.asLong(), 1, 2, 3).ecrire(o)),
                taille(o -> Paquets.MajS2C.fin(123456).ecrire(o)),
                taille(o -> new Paquets.AnnulerC2S(123456).ecrire(o)),
                taille(o -> new Paquets.ReglagesS2C(300, true).ecrire(o)));
    }

    private static void mesurer(String qui, int n, String cercle) {
        FauxJoueur m = faux(qui);
        long n0 = PingService.nanosAcceptes, r0 = PingService.relayes;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            PingService.remplirSeau(m.joueur.getUUID());
            PingService.recevoir(m.joueur, solDevant(m.joueur, PingType.ALLER.id(), 2 + (i % 5)));
            if ((i & 63) == 0) zero();
        }
        long total = System.nanoTime() - t0;
        long acceptes = PingService.relayes - r0;
        zero();
        log("MESURE serveur ({}) : {} pings acceptes sur {}, {} us par ping (verifications, ligne de vue, cercle, envoi)", cercle, acceptes, n,
                String.format(Locale.ROOT, "%.1f", (PingService.nanosAcceptes - n0) / 1000.0 / Math.max(1, acceptes)));
        log("MESURE serveur ({}) : {} us par appel en tout", cercle, String.format(Locale.ROOT, "%.1f", total / 1000.0 / n));
    }

    /** Un faux joueur pingue un point (banc : pings d'un allie pour les captures). */
    static void fauxPing(String qui, int type, double x, double y, double z) {
        FauxJoueur f = faux(qui);
        if (f == null) return;
        PingService.remplirSeau(f.joueur.getUUID());
        BlockPos b = BlockPos.containing(x, y - 0.5, z);
        long avant = PingService.relayes;
        PingService.recevoir(f.joueur, new Paquets.PingC2S(type, -1, b.asLong(), x, y, z));
        log("ping de {} ({}) en {} {} {} : {} ; cercle {} : {}", qui, PingType.parId(type), x, y, z, PingService.relayes > avant ? "accepte" : "refuse",
                Cercles.derniere(), PingService.derniersDestinataires());
    }

    private static int taille(java.util.function.Consumer<FriendlyByteBuf> ecrire) {
        FriendlyByteBuf o = new FriendlyByteBuf(Unpooled.buffer());
        ecrire.accept(o);
        return o.readableBytes() + 1;
    }

    private static void nettoyer(MinecraftServer s) {
        if (partie != null) partie.fermer();
        for (FauxJoueur f : FAUX.values()) {
            BancLiens.finGroupe(f.joueur.getUUID());
            f.retirer(s);
        }
        FAUX.clear();
    }

    private static void bilan() {
        log("BILAN : {} verifications OK, {} en echec {}", ok, echecs, ECHECS);
    }
}
