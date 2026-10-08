package fr.heralisz.matchs.moteur.spectateur;

import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.api.Pose;
import fr.heralisz.matchs.moteur.net.PaquetsMoteur;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spectateur borne (MATCHS_SPEC §1.9) : un elimine passe en mode spectateur, camera sur un coequipier vivant (sinon
 * un autre joueur vivant de la partie, sinon point {@code spectateur} de la carte), fleches gauche/droite pour changer
 * (paquet {@code SpectateurC2S}), bornes de l'instance imposees (toute sortie, teleportation du menu vanilla ou
 * changement de dimension ramene au point spectateur), retour sur la cible si le joueur quitte la camera (Maj).
 * Le mode de jeu d'avant est rendu par {@link #arreter(ServerPlayer, boolean)}. Fil serveur.
 */
public final class Spectateurs {
    private Spectateurs() {
    }

    private static final class Etat {
        final Partie partie;
        final GameType avant;
        @Nullable
        UUID cible;
        int envoye = -1;
        String nomEnvoye = "";

        Etat(Partie partie, GameType avant) {
            this.partie = partie;
            this.avant = avant;
        }
    }

    private static final Map<UUID, Etat> ETATS = new HashMap<>();
    private static final List<UUID> TAMPON = new ArrayList<>();

    /** Vrai si le joueur est spectateur d'une partie. */
    public static boolean estSpectateur(UUID u) {
        return ETATS.containsKey(u);
    }

    /**
     * Fait du joueur un spectateur de la partie (ajoute a {@link Partie#spectateurs()}) : mode spectateur, camera sur
     * un coequipier vivant. Sans effet sur l'equipe (le mode appelle {@link Equipe#eliminer} lui-meme).
     */
    public static void suivre(ServerPlayer p, Partie partie) {
        Etat e = ETATS.get(p.getUUID());
        if (e == null || e.partie != partie) {
            e = new Etat(partie, e != null ? e.avant : p.gameMode.getGameModeForPlayer());
            ETATS.put(p.getUUID(), e);
        }
        partie.spectateurs().add(p.getUUID());
        p.setGameMode(GameType.SPECTATOR);
        e.cible = null;
        choisir(p, e, 0);
    }

    /**
     * Fin du mode spectateur : camera rendue, retrait de {@link Partie#spectateurs()}, bandeau client efface ; si
     * {@code restaurerMode}, le mode de jeu d'avant est rendu (sinon le mode le fixe lui-meme : reapparition...).
     */
    public static void arreter(ServerPlayer p, boolean restaurerMode) {
        Etat e = ETATS.remove(p.getUUID());
        if (e == null) return;
        e.partie.spectateurs().remove(p.getUUID());
        p.setCamera(p);
        if (restaurerMode) p.setGameMode(e.avant);
        MatchsNet.versJoueur(p, new PaquetsMoteur.SpectateurS2C(false, "", 0, 0));
    }

    /** Joueur suivant (+1) ou precedent (-1) ; demande du client (revalidee : le joueur doit etre spectateur). */
    public static void changer(ServerPlayer p, int sens) {
        Etat e = ETATS.get(p.getUUID());
        if (e == null) return;
        choisir(p, e, sens >= 0 ? 1 : -1);
    }

    /** Candidats : coequipiers vivants connectes dans le niveau de l'instance, sinon tout joueur vivant de la partie. */
    private static void candidats(ServerPlayer p, Etat e, List<UUID> sortie) {
        sortie.clear();
        Partie partie = e.partie;
        Instance inst = partie.instance();
        Equipe eq = partie.equipeDe(p.getUUID());
        if (eq != null) for (UUID u : eq.vivants()) ajouterSiValide(p, partie, inst, u, sortie);
        if (!sortie.isEmpty()) return;
        for (Equipe x : partie.equipes()) for (UUID u : x.vivants()) ajouterSiValide(p, partie, inst, u, sortie);
    }

    private static void ajouterSiValide(ServerPlayer p, Partie partie, @Nullable Instance inst, UUID u, List<UUID> sortie) {
        if (u.equals(p.getUUID()) || partie.spectateurs().contains(u)) return;
        ServerPlayer c = partie.joueur(u);
        if (c == null || c.isSpectator() || (inst != null && c.level() != inst.niveau())) return;
        sortie.add(u);
    }

    private static void choisir(ServerPlayer p, Etat e, int pas) {
        candidats(p, e, TAMPON);
        UUID avant = e.cible;
        if (TAMPON.isEmpty()) {
            e.cible = null;
        } else {
            int i = avant == null ? -1 : TAMPON.indexOf(avant);
            if (i < 0) i = pas >= 0 ? 0 : TAMPON.size() - 1;
            else i = Math.floorMod(i + pas, TAMPON.size());
            e.cible = TAMPON.get(i);
        }
        ServerPlayer c = e.cible == null ? null : e.partie.joueur(e.cible);
        if (c != null) {
            p.setCamera(c);
        } else {
            p.setCamera(p);
            ramener(p, e.partie);
        }
        informer(p, e, c, TAMPON.size());
    }

    private static void informer(ServerPlayer p, Etat e, @Nullable ServerPlayer c, int nb) {
        String nom = c == null ? "" : c.getGameProfile().getName();
        if (nb == e.envoye && nom.equals(e.nomEnvoye)) return;
        e.envoye = nb;
        e.nomEnvoye = nom;
        Equipe eq = c == null ? null : e.partie.equipeDe(c.getUUID());
        MatchsNet.versJoueur(p, new PaquetsMoteur.SpectateurS2C(true, nom, eq == null ? 0xFFFFFF : eq.couleur(), nb));
    }

    /** Ramene le spectateur au point « spectateur » de la carte (ou au centre des bornes). */
    private static void ramener(ServerPlayer p, Partie partie) {
        Instance inst = partie.instance();
        if (inst == null) return;
        Pose pose = inst.point("spectateur");
        if (pose != null) {
            // regard plongeant vers le centre de l'arene (la pose ne donne que le lacet : a l'horizontale, on ne voyait que le ciel)
            AABB b = inst.bornes();
            double dx = b.getCenter().x - pose.x(), dz = b.getCenter().z - pose.z(), dy = pose.y() - (b.minY + 2);
            float tangage = (float) Math.toDegrees(Math.atan2(dy, Math.max(1, Math.sqrt(dx * dx + dz * dz))));
            p.teleportTo(inst.niveau(), pose.x(), pose.y(), pose.z(), pose.yaw(), Math.max(0f, Math.min(60f, tangage)));
        } else {
            AABB b = inst.bornes();
            p.teleportTo(inst.niveau(), (b.minX + b.maxX) / 2, Math.min(b.maxY - 2, b.minY + 12), (b.minZ + b.maxZ) / 2, p.getYRot(), 30f);
        }
    }

    /** A chaque tick : cible toujours valide, camera reprise, bornes imposees. */
    public static void tick(MinecraftServer s) {
        if (ETATS.isEmpty()) return;
        for (Map.Entry<UUID, Etat> en : ETATS.entrySet()) {
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            if (p == null) continue;
            Etat e = en.getValue();
            Partie partie = e.partie;
            if (partie.phase() == Partie.Phase.TERMINEE || MatchApi.partieDe(p) != partie) continue;
            ServerPlayer c = e.cible == null ? null : partie.joueur(e.cible);
            boolean valide = c != null && !c.isSpectator() && !partie.spectateurs().contains(e.cible)
                    && partie.equipeDe(e.cible) != null && partie.equipeDe(e.cible).estVivant(e.cible)
                    && (partie.instance() == null || c.level() == partie.instance().niveau());
            if (!valide) {
                // la cible est morte ou partie : suivante (toutes les 10 ticks au plus pour ne pas reconstruire sans cesse)
                if (s.getTickCount() % 10 == 0 || c != null) choisir(p, e, 1);
                continue;
            }
            if (p.getCamera() != c) p.setCamera(c);
            if (s.getTickCount() % 10 == 0) {
                candidats(p, e, TAMPON);
                informer(p, e, c, TAMPON.size());
            }
            Instance inst = partie.instance();
            if (inst != null && p.getCamera() == p && (p.level() != inst.niveau() || !inst.contient(p.getX(), p.getY(), p.getZ()))) {
                ramener(p, partie);
            }
        }
        // spectateur libre (sans cible) : bornes
        for (Map.Entry<UUID, Etat> en : ETATS.entrySet()) {
            Etat e = en.getValue();
            if (e.cible != null) continue;
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            Instance inst = e.partie.instance();
            if (p == null || inst == null) continue;
            if (p.level() != inst.niveau() || !inst.contient(p.getX(), p.getY(), p.getZ())) ramener(p, e.partie);
        }
    }

    /** Deconnexion : l'etat est garde (la partie decide) mais la camera est rendue. */
    public static void deconnexion(ServerPlayer p) {
        if (ETATS.containsKey(p.getUUID())) p.setCamera(p);
    }

    public static void vider() {
        ETATS.clear();
    }
}
