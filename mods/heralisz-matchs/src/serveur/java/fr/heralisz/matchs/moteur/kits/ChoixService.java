package fr.heralisz.matchs.moteur.kits;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.Noms;
import fr.heralisz.matchs.moteur.api.ChoixClasse;
import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.Equipement;
import fr.heralisz.matchs.moteur.api.Kit;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.net.PaquetsMoteur;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Sessions de choix de classe (implementation de {@link ChoixClasse}). Un joueur n'est que dans une session a la fois.
 * Les choix des joueurs factices (banc d'essai) peuvent etre forces. Fil serveur.
 */
public final class ChoixService {
    private ChoixService() {
    }

    private static final class Session {
        final Partie partie;
        final List<UUID> joueurs = new ArrayList<>();
        final List<Kit> kits;
        final Map<String, Integer> limites;
        final Map<UUID, Kit> choix = new LinkedHashMap<>();
        final Consumer<Map<UUID, Kit>> quandFini;
        final long finMs;
        /** Dernier choix (ms) : quand tous ont choisi, la session finit 1,5 s apres (le choix reste visible). */
        long dernierChoixMs;
        /** Joueurs factices (banc) : comptent dans les limites mais ne recoivent rien. */
        final Map<UUID, Kit> factices = new LinkedHashMap<>();

        Session(Partie partie, List<Kit> kits, Map<String, Integer> limites, int secondes, Consumer<Map<UUID, Kit>> quandFini) {
            this.partie = partie;
            this.kits = List.copyOf(kits);
            this.limites = Map.copyOf(limites);
            this.quandFini = quandFini;
            this.finMs = System.currentTimeMillis() + secondes * 1000L;
        }
    }

    private static final List<Session> SESSIONS = new ArrayList<>();
    private static final Map<UUID, Session> PAR_JOUEUR = new HashMap<>();
    private static final Map<UUID, Long> DERNIER_CLIC = new HashMap<>();

    public static void ouvrir(Partie partie, Collection<ServerPlayer> joueurs, List<Kit> kits, Map<String, Integer> limites,
                              int secondes, Component titre, Consumer<Map<UUID, Kit>> quandFini) {
        if (kits.isEmpty()) {
            HeraliszMatchs.LOG.warn("[matchs] choix de classe sans kit pour {} : ignore", partie.id());
            quandFini.accept(Map.of());
            return;
        }
        int s = Math.max(1, Math.min(60, secondes));
        Session se = new Session(partie, kits, limites, s, quandFini);
        List<ItemStack> apercus = new ArrayList<>(kits.size());
        for (Kit k : kits) apercus.add(k.principale() == null ? ItemStack.EMPTY : fr.heralisz.matchs.moteur.compat.Tacz.arme(k.principale()));
        for (ServerPlayer p : joueurs) {
            Session ancienne = PAR_JOUEUR.remove(p.getUUID());
            if (ancienne != null) ancienne.joueurs.remove(p.getUUID());
            se.joueurs.add(p.getUUID());
            PAR_JOUEUR.put(p.getUUID(), se);
            Equipe eq = partie.equipeDe(p.getUUID());
            MatchsNet.versJoueur(p, new PaquetsMoteur.ChoixOuvrirS2C(titre, s, eq == null ? 0x6FA8FF : eq.couleur(), se.kits, apercus, se.limites));
        }
        SESSIONS.add(se);
        diffuser(se);
    }

    /** Banc d'essai : un joueur factice de l'equipe a choisi ce kit (compte dans les limites). */
    public static void forcerFactice(Partie partie, UUID factice, Kit kit) {
        for (Session se : SESSIONS) {
            if (se.partie == partie) {
                se.factices.put(factice, kit);
                diffuser(se);
            }
        }
    }

    public static boolean enCours(Partie partie) {
        for (Session se : SESSIONS) if (se.partie == partie) return true;
        return false;
    }

    public static void annuler(Partie partie) {
        Iterator<Session> it = SESSIONS.iterator();
        while (it.hasNext()) {
            Session se = it.next();
            if (se.partie != partie) continue;
            it.remove();
            fermer(se);
        }
    }

    @Nullable
    public static Kit choixDe(UUID u) {
        Session se = PAR_JOUEUR.get(u);
        return se == null ? null : se.choix.get(u);
    }

    /** Demande du client (revalidee). */
    public static void choisir(ServerPlayer p, String kitId) {
        Session se = PAR_JOUEUR.get(p.getUUID());
        if (se == null) return;
        long now = System.currentTimeMillis();
        Long d = DERNIER_CLIC.get(p.getUUID());
        if (d != null && now - d < 150) return;
        DERNIER_CLIC.put(p.getUUID(), now);
        Kit kit = null;
        for (Kit k : se.kits) if (k.id().equals(kitId)) kit = k;
        if (kit == null) return;
        if (!libre(se, p.getUUID(), kit.classe())) {
            diffuser(se);
            return;
        }
        se.choix.put(p.getUUID(), kit);
        se.dernierChoixMs = now;
        diffuser(se);
    }

    /** Vrai si la classe n'est pas pleine dans l'equipe du joueur (son propre choix exclu). */
    private static boolean libre(Session se, UUID u, String classe) {
        Integer max = se.limites.get(classe);
        if (max == null) return true;
        Equipe eq = se.partie.equipeDe(u);
        int n = 0;
        for (Map.Entry<UUID, Kit> e : se.choix.entrySet()) {
            if (e.getKey().equals(u) || !e.getValue().classe().equals(classe)) continue;
            if (eq == null || eq.contient(e.getKey())) n++;
        }
        for (Map.Entry<UUID, Kit> e : se.factices.entrySet()) {
            if (!e.getValue().classe().equals(classe)) continue;
            if (eq == null || eq.contient(e.getKey())) n++;
        }
        return n < max;
    }

    /** Envoie a chaque joueur son choix et ceux de ses coequipiers. */
    private static void diffuser(Session se) {
        MinecraftServer s = se.partie.serveur();
        for (UUID u : se.joueurs) {
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p == null) continue;
            MatchsNet.versJoueur(p, etat(se, u, false));
        }
    }

    private static PaquetsMoteur.ChoixEtatS2C etat(Session se, UUID u, boolean fermer) {
        MinecraftServer s = se.partie.serveur();
        Equipe eq = se.partie.equipeDe(u);
        List<PaquetsMoteur.Pris> pris = new ArrayList<>();
        for (Map.Entry<UUID, Kit> e : se.choix.entrySet()) {
            if (e.getKey().equals(u) || (eq != null && !eq.contient(e.getKey()))) continue;
            pris.add(new PaquetsMoteur.Pris(e.getValue().classe(), e.getValue().id(), Noms.nom(s, e.getKey())));
        }
        for (Map.Entry<UUID, Kit> e : se.factices.entrySet()) {
            if (eq != null && !eq.contient(e.getKey())) continue;
            pris.add(new PaquetsMoteur.Pris(e.getValue().classe(), e.getValue().id(), Noms.nom(s, e.getKey())));
        }
        Kit mon = se.choix.get(u);
        return new PaquetsMoteur.ChoixEtatS2C(mon == null ? "" : mon.id(), pris, fermer);
    }

    /** Echeances (appele a chaque tick serveur ; une session finit aussi quand tous ont choisi). */
    public static void tick() {
        if (SESSIONS.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (int i = SESSIONS.size() - 1; i >= 0; i--) {
            Session se = SESSIONS.get(i);
            boolean tous = !se.joueurs.isEmpty() && se.choix.keySet().containsAll(se.joueurs) && now - se.dernierChoixMs >= 1500;
            if (now < se.finMs && !tous) continue;
            SESSIONS.remove(i);
            finir(se);
        }
    }

    private static void finir(Session se) {
        MinecraftServer s = se.partie.serveur();
        Map<UUID, Kit> res = new LinkedHashMap<>();
        for (UUID u : se.joueurs) {
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p == null) continue;
            Kit k = se.choix.get(u);
            if (k == null) {
                for (Kit c : se.kits) {
                    if (libre(se, u, c.classe())) {
                        k = c;
                        break;
                    }
                }
                if (k == null) k = se.kits.get(0);
                se.choix.put(u, k);
            }
            res.put(u, k);
        }
        fermer(se);
        try {
            se.quandFini.accept(res);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[matchs] fin du choix de classe de {} en erreur", se.partie.id(), ex);
        }
    }

    private static void fermer(Session se) {
        MinecraftServer s = se.partie.serveur();
        for (UUID u : se.joueurs) {
            if (PAR_JOUEUR.get(u) == se) PAR_JOUEUR.remove(u);
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p != null) MatchsNet.versJoueur(p, etat(se, u, true));
        }
    }

    /** Donne a chacun son kit (aide pour les modes : {@code quandFini}). */
    public static void donnerTous(Partie partie, Map<UUID, Kit> choix) {
        for (Map.Entry<UUID, Kit> e : choix.entrySet()) {
            ServerPlayer p = partie.joueur(e.getKey());
            if (p != null) Equipement.donner(p, e.getValue(), partie.id());
        }
    }

    public static void oublier(UUID u) {
        DERNIER_CLIC.remove(u);
    }

    public static void vider() {
        SESSIONS.clear();
        PAR_JOUEUR.clear();
        DERNIER_CLIC.clear();
    }
}
