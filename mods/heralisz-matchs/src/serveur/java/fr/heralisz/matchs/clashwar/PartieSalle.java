package fr.heralisz.matchs.clashwar;

import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.api.Pose;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Salle d'attente d'une edition (carte {@code lobby_clashwar}, MATCHS_SPEC §2.4) : partie « clashwar » qui garde tous
 * les participants entre leurs matchs (drapeau « en match » pose, Coffre deja depose). Aucun degat (phase ATTENTE),
 * sortie des bornes ou chute = retour au point d'apparition, sante et faim entretenues. Le mur « tableau du tournoi »
 * y est pose a l'emplacement {@code tableau_mur} de la carte.
 */
public final class PartieSalle extends Partie {
    private final Tournoi tournoi;
    /** Une seule equipe : tous les participants (les pastilles du bandeau ne servent pas ici). */
    private final Equipe tous = new Equipe("salle", "HeraliZ", 0x6FA8FF);
    private int prochainSpawn;

    PartieSalle(Tournoi tournoi, String id) {
        super(id, "clashwar", tournoi.serveur());
        this.tournoi = tournoi;
        ajouterEquipe(tous);
    }

    Tournoi tournoi() {
        return tournoi;
    }

    void ajouter(UUID u) {
        tous.ajouter(u);
    }

    void retirer(UUID u) {
        tous.retirer(u);
    }

    /** Point d'apparition suivant de la salle (rotation sur les points « spawn »), ou le centre des bornes. */
    Pose spawn() {
        Instance i = instance();
        if (i == null) return Pose.ZERO;
        List<Pose> l = i.points("spawn");
        if (l.isEmpty()) {
            AABB b = i.bornes();
            return new Pose(b.getCenter().x, b.minY + 1, b.getCenter().z, 0);
        }
        Pose p = l.get(prochainSpawn % l.size());
        prochainSpawn++;
        return p;
    }

    /**
     * Teleporte le joueur dans la salle (rien si l'instance n'existe plus), le regard leve vers le centre du mur-tableau
     * ({@code tableau_mur} de la carte) : a l'horizontale, depuis les points d'apparition, l'ecran etait presque hors champ.
     */
    void teleporter(ServerPlayer p) {
        Instance i = instance();
        if (i == null) return;
        Pose s = spawn();
        p.teleportTo(i.niveau(), s.x(), s.y(), s.z(), s.yaw(), tangageVersMur(i, s));
    }

    /** Tangage (degres, negatif = vers le haut) du regard des yeux vers le centre du mur, 0 sans mur dans la carte. */
    private static float tangageVersMur(Instance i, Pose s) {
        try {
            com.google.gson.JsonObject m = i.carte().meta();
            if (!m.has("tableau_mur")) return 0f;
            com.google.gson.JsonObject t = m.getAsJsonObject("tableau_mur");
            com.google.gson.JsonArray c = t.getAsJsonArray("coin_bas_gauche");
            int l = t.has("largeur") ? t.get("largeur").getAsInt() : 24, h = t.has("hauteur") ? t.get("hauteur").getAsInt() : 12;
            double mx = i.origine().getX() + c.get(0).getAsDouble() + l / 2.0;
            double my = i.origine().getY() + c.get(1).getAsDouble() + h / 2.0;
            double mz = i.origine().getZ() + c.get(2).getAsDouble() + 1;
            double dx = mx - s.x(), dz = mz - s.z(), dy = my - (s.y() + 1.62);
            float tangage = (float) -Math.toDegrees(Math.atan2(dy, Math.max(1, Math.sqrt(dx * dx + dz * dz))));
            return Math.max(-45f, Math.min(10f, tangage));
        } catch (RuntimeException e) {
            return 0f;
        }
    }

    @Override
    public void tick() {
        if (ticks() % 100 != 0) return;
        // sante et faim entretenues (personne ne se bat ici) ; aucune allocation : on parcourt les inscrits
        for (UUID u : tous.joueurs()) {
            ServerPlayer p = joueur(u);
            if (p == null || MatchApi.partieDe(p) != this) continue;
            if (p.getHealth() < p.getMaxHealth()) p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
        }
    }

    @Override
    public void surElimination(ServerPlayer victime, @Nullable LivingEntity tueur, DamageSource source) {
        victime.setHealth(victime.getMaxHealth());
        victime.clearFire();
        teleporter(victime);
    }

    @Override
    public void surDeconnexion(ServerPlayer joueur) {
        // le joueur garde sa place : s'il n'est pas revenu quand son match l'appelle, il le rate
    }

    @Override
    public boolean surReconnexion(ServerPlayer joueur) {
        if (!tournoi.actif() || !tournoi.participant(joueur.getUUID())) return false;
        tournoi.placerEnSalle(joueur);
        return true;
    }

    @Override
    public void surSortieBornes(ServerPlayer joueur) {
        teleporter(joueur);
    }
}
