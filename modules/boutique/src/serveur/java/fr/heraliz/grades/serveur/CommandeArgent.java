package fr.heraliz.grades.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.heralisz.economie.api.EconomieApi;
import fr.heraliz.casino.api.CapsulesApi;
import fr.heraliz.noyau.api.Notifs;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * {@code /money} et {@code /argent} (19/11, demande du propriétaire ; maquette validée) : tout son argent d'un coup d'œil,
 * dans le bandeau commun du noyau (à droite de l'écran, 6 s), jamais dans le chat : hérals du compte en titre, puis les
 * bourses qu'il a sur lui (à revendre à Mila), ses capsules du casino (compte et inventaire) et ses Points Boutique.
 * Une ligne à 0 n'est pas montrée ; tout à 0 : où gagner des hérals.
 * <p>Les PB sont sur le site : le bandeau attend sa réponse 2 s au plus (sans réponse : bandeau sans la ligne des PB) ; un
 * solde lu est gardé une minute (un échec, 10 s), le site n'est donc jamais interrogé à chaque commande. Permises partout,
 * même sur la carte et en combat (listes du noyau) : elles ne font que lire. Un bandeau toutes les 2 s au plus par joueur
 * (le précédent est encore à l'écran). Fil serveur.
 */
final class CommandeArgent {
    private CommandeArgent() {
    }

    private static final long ECART_MS = 2000, ATTENTE_PB_MS = 2000, PB_GARDE_MS = 60_000, PB_ECHEC_MS = 10_000;
    private static final int SECONDES = 6;
    private static final Map<UUID, Long> DERNIER = new HashMap<>();
    /** Dernier solde de PB lu sur le site : {solde (-1 : pas de réponse), heure}. */
    private static final Map<UUID, long[]> PB = new HashMap<>();
    /** Bandeau en attente de la réponse du site : numéro de la demande. */
    private static final Map<UUID, Long> ATTENTES = new HashMap<>();
    private static long numero;

    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(arbre(Commands.literal("money")));
        d.register(arbre(Commands.literal("argent")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> arbre(LiteralArgumentBuilder<CommandSourceStack> racine) {
        return racine.executes(c -> montrer(c.getSource().getPlayerOrException()));
    }

    /** Déconnexion. */
    static void oublier(UUID u) {
        DERNIER.remove(u);
        PB.remove(u);
        ATTENTES.remove(u);
    }

    private static Component msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.argent." + cle, fr, args);
    }

    /** « 12 450 » : espace insécable entre les milliers (la police des bandeaux l'a ; jamais coupé en fin de ligne). */
    static String nombre(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder(s.length() + 4);
        if (v < 0) b.append('-');
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    private static int montrer(ServerPlayer p) {
        UUID u = p.getUUID();
        long now = System.currentTimeMillis();
        Long avant = DERNIER.get(u);
        if (avant != null && now - avant < ECART_MS) return 1;   // le bandeau d'avant est encore à l'écran
        DERNIER.put(u, now);
        long[] connu = PB.get(u);
        if (connu != null && now - connu[1] < (connu[0] >= 0 ? PB_GARDE_MS : PB_ECHEC_MS)) {
            envoyer(p, connu[0]);
            return 1;
        }
        if (!Pb.actif()) {
            envoyer(p, -1);
            return 1;
        }
        long n = ++numero;
        ATTENTES.put(u, n);
        MinecraftServer srv = p.server;
        // réponse du site (rendue sur le fil serveur par Pb) : bandeau avec les PB, s'il n'est pas déjà parti sans eux
        Pb.solde(p.getGameProfile().getName(), r -> {
            long solde = r != null && r.code() == 200 ? r.nombre("solde") : -1;
            PB.put(u, new long[]{solde, System.currentTimeMillis()});
            if (!ATTENTES.remove(u, n)) return;
            ServerPlayer q = srv.getPlayerList().getPlayer(u);
            if (q != null) envoyer(q, solde);
        });
        // site trop lent : le bandeau part sans les PB au bout de 2 s
        CompletableFuture.delayedExecutor(ATTENTE_PB_MS, TimeUnit.MILLISECONDS).execute(() -> srv.execute(() -> {
            if (!ATTENTES.remove(u, n)) return;
            ServerPlayer q = srv.getPlayerList().getPlayer(u);
            if (q != null) envoyer(q, -1);
        }));
        return 1;
    }

    /** Le bandeau : montants relus maintenant ; {@code pb} : solde du site (-1 : inconnu, pas de ligne). */
    private static void envoyer(ServerPlayer p, long pb) {
        long solde = EconomieApi.solde(p.getUUID());
        long[] bourses = bourses(p);
        long capsulesCompte = CapsulesApi.solde(p.getUUID()), capsulesSurLui = CapsulesApi.surLui(p);
        List<Component> lignes = new ArrayList<>(4);
        lignes.add(msg("compte", "sur ton compte"));
        if (bourses != null && bourses[0] > 0) {
            lignes.add(msg("bourses", "Bourses sur toi : %s (à revendre à Mila)", nombre(bourses[0])));
        }
        if (capsulesCompte > 0 || capsulesSurLui > 0) {
            lignes.add(msg("capsules", "Capsules : %s au compte, %s sur toi", nombre(capsulesCompte), nombre(capsulesSurLui)));
        }
        if (pb > 0) lignes.add(msg("pb", "Points boutique : %s PB", nombre(pb)));
        if (lignes.size() == 1 && solde <= 0) lignes.add(msg("rien", "Les quêtes et Mila en rapportent, au spawn"));
        MutableComponent texte = Component.empty();
        for (int i = 0; i < lignes.size(); i++) {
            if (i > 0) texte.append("\n");
            texte.append(lignes.get(i));
        }
        Component titre = Math.abs(solde) < 2 ? msg("titre_un", "%s héral", nombre(solde)) : msg("titre", "%s hérals", nombre(solde));
        Notifs.envoyer(p, titre, texte, Notifs.OR, SECONDES);
    }

    // ------------------------------------------------------------------ bourses (HeraliZ Quêtes, par réflexion)
    private static Method bourses;
    private static boolean boursesAbsentes;

    /** {hérals des bourses que le joueur a sur lui, nombre de bourses} ; null sans HeraliZ Quêtes. */
    @Nullable
    private static long[] bourses(ServerPlayer p) {
        if (boursesAbsentes) return null;
        try {
            if (bourses == null) bourses = Class.forName("fr.heraliz.quetes.serveur.QuetesApi").getMethod("bourses", ServerPlayer.class);
            return bourses.invoke(null, p) instanceof long[] l && l.length >= 2 ? l : null;
        } catch (java.lang.reflect.InvocationTargetException ex) {
            return null;
        } catch (ReflectiveOperationException | LinkageError ex) {
            boursesAbsentes = true;
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
