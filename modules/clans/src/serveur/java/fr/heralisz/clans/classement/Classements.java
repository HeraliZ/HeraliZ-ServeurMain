package fr.heralisz.clans.classement;

import com.mojang.authlib.GameProfile;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.saison.Saison;
import fr.heralisz.clans.server.ClanStore;
import fr.heralisz.economie.api.Compte;
import fr.heralisz.economie.server.ComptesStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Calcul des classements des hologrammes du spawn (03/11), {@value #N} premiers :
 * <ul>
 * <li><b>argent</b> : solde du compte de hérals (la seule fortune d'un joueur ; la banque d'un clan est au clan) ;</li>
 * <li><b>kills</b> : {@link KillsJoueurs} (kills de joueurs, regles anti-triche des clans, jamais de zombies) ;</li>
 * <li><b>clans</b> : classement general de la saison ({@link Saison#classement()}, comme l'onglet Saison).</li>
 * </ul>
 * Le staff cache (vanish du panneau du staff, {@code GradesApi.cache}, lu par reflexion) n'apparait pas dans les deux
 * classements de joueurs. Pseudos : celui du joueur connecte, sinon le dernier connu (compte de hérals, compteur de
 * kills, cache des profils du serveur) ; un joueur sans aucun pseudo connu n'est pas affiche (jamais un identifiant).
 * Fil serveur.
 */
public final class Classements {
    private Classements() {
    }

    /** Lignes affichees. */
    public static final int N = 8;

    /** Ce que montre un hologramme (identifiant garde dans l'etiquette de l'entite : ne jamais le changer). */
    public enum Genre {
        ARGENT, KILLS, CLANS;

        public final String id = name().toLowerCase(Locale.ROOT);

        @Nullable
        public static Genre parId(String s) {
            for (Genre g : values()) if (g.id.equals(s)) return g;
            return null;
        }
    }

    /**
     * Une ligne : nom (joueur ou clan), tag et couleur du clan (vides / 0 sans clan), valeur (hérals, kills, ou points de
     * saison au total pour un clan).
     */
    public record Ligne(String nom, String tag, int couleur, long valeur) {
    }

    static List<Ligne> calculer(Genre g) {
        try {
            return switch (g) {
                case ARGENT -> argent();
                case KILLS -> kills();
                case CLANS -> clans();
            };
        } catch (RuntimeException ex) {
            HeraliszClans.LOG.error("[classements] calcul du classement {} en erreur", g.id, ex);
            return List.of();
        }
    }

    /** Les plus riches : solde du compte de hérals, sans le staff cache ni les comptes vides. */
    private static List<Ligne> argent() {
        ComptesStore cs = ComptesStore.get();
        if (cs == null) return List.of();
        List<Ligne> out = new ArrayList<>(N);
        // classement trie et garde en cache par l'economie (retrie au plus une fois par seconde, seulement s'il a change)
        for (Compte c : cs.classement(Integer.MAX_VALUE)) {
            if (out.size() >= N || c.solde() <= 0) break;
            if (cache(c.uuid())) continue;
            String nom = nom(c.uuid(), c.dernierNom());
            if (nom != null) out.add(ligneJoueur(c.uuid(), nom, c.solde()));
        }
        return out;
    }

    private static List<Ligne> kills() {
        List<Ligne> out = new ArrayList<>(N);
        // un peu plus que N : un joueur sans pseudo connu est saute
        for (KillsJoueurs.Rang r : KillsJoueurs.top(N + 5, Classements::cache)) {
            if (out.size() >= N) break;
            String nom = nom(r.joueur(), r.nom());
            if (nom != null) out.add(ligneJoueur(r.joueur(), nom, r.kills()));
        }
        return out;
    }

    /** Classement general de la saison (somme des places) ; valeur = points des trois categories, arrondis. */
    private static List<Ligne> clans() {
        ClanStore s = ClanStore.get();
        List<Ligne> out = new ArrayList<>(N);
        for (Saison.Ligne l : Saison.classement()) {
            if (out.size() >= N) break;
            Clan c = s == null ? null : s.get(l.clan());
            int couleur = c == null || c.banniere == null ? 0x6FA8FF : c.banniere.couleurA;
            out.add(new Ligne(l.nom(), l.tag(), couleur, Saison.entiers(l.pve()) + Saison.entiers(l.pvp()) + Saison.entiers(l.events())));
        }
        return out;
    }

    /** Ligne d'un joueur : tag et couleur de son clan s'il en a un. */
    private static Ligne ligneJoueur(UUID joueur, String nom, long valeur) {
        ClanStore s = ClanStore.get();
        Clan c = s == null ? null : s.clanDe(joueur);
        if (c == null) return new Ligne(nom, "", 0, valeur);
        return new Ligne(nom, c.tag, c.banniere == null ? 0x6FA8FF : c.banniere.couleurA, valeur);
    }

    /** Pseudo a afficher : connecte, sinon dernier connu, sinon cache des profils du serveur ; null si aucun. */
    @Nullable
    private static String nom(UUID joueur, @Nullable String connu) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(joueur);
            if (p != null) return p.getGameProfile().getName();
        }
        ComptesStore cs = ComptesStore.get();
        Compte c = cs == null ? null : cs.get(joueur);
        if (c != null && c.dernierNom() != null && !c.dernierNom().isEmpty()) return c.dernierNom();
        if (connu != null && !connu.isEmpty()) return connu;
        if (server != null && server.getProfileCache() != null) {
            // lecture du cache local seulement (par identifiant : jamais de demande a Mojang)
            return server.getProfileCache().get(joueur).map(GameProfile::getName).filter(n -> !n.isEmpty()).orElse(null);
        }
        return null;
    }

    // ------------------------------------------------------------------ staff cache (HeraliZ Grades, par reflexion)
    private static Method gradesCache;
    private static boolean gradesAbsent;

    /** Vrai si le joueur est cache par le vanish du staff (faux sans HeraliZ Grades). */
    private static boolean cache(UUID joueur) {
        if (gradesAbsent) return false;
        try {
            if (gradesCache == null) gradesCache = Class.forName("fr.heraliz.grades.serveur.GradesApi").getMethod("cache", UUID.class);
            return Boolean.TRUE.equals(gradesCache.invoke(null, joueur));
        } catch (ReflectiveOperationException | LinkageError ex) {
            gradesAbsent = true;
            HeraliszClans.LOG.info("[classements] HeraliZ Grades absent : le staff cache n'est pas retire des classements");
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
