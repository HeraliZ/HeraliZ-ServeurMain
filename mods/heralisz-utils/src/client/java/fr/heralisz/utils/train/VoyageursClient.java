package fr.heralisz.utils.train;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Tick client d'un voyageur (repris tel quel de Voyageurs.tickClient) : le passager calcule lui-même sa marche dans la
 * voiture et l'envoie au serveur ; les autres voyageurs sont placés d'après l'horaire. Branché par client.PartieClient
 * sur {@link Voyageur#TICK_CLIENT}.
 */
public final class VoyageursClient {
    private VoyageursClient() {}

    public static void tick(Voyageur v) {
        tickClient(v);
    }


    static void tickClient(Voyageur v) {
        if (!Voyageurs.CLIENT.contains(v)) Voyageurs.CLIENT.add(v);
        Voyageurs.CLIENT.removeIf(Entity::isRemoved);
        Horaire h = Horaire.get();
        if (h == null) return;
        Horaire.Etat e = h.etat(TrainClient.tTick);
        Entity rider = v.getFirstPassenger();
        Horaire.Placement pl = new Horaire.Placement();
        if (rider != null && TrainClient.estMoi(rider)) {
            if (v.pv < 0) {
                v.pv = v.getEntityData().get(Voyageur.VEHICULE);
                v.px = v.getEntityData().get(Voyageur.LX);
                v.pz = v.getEntityData().get(Voyageur.LZ);
                v.accroupiPrec = TrainClient.accroupi();                         // Maj tenu en montant : pas un appui
                TrainClient.aide(false);
            }
            h.placer(v.pv, e.s(), pl);
            Player p = (Player) rider;
            boolean acc = TrainClient.accroupi();
            boolean appui = acc && !v.accroupiPrec;
            if (v.passis) {                                                   // assis : Espace se lève, Maj descend
                if (appui) {
                    TrainClient.descendre();
                } else if (TrainClient.saut()) {
                    float f = Voyageurs.baie(v.px);
                    v.passis = false;
                    v.px = f;
                    v.pz = Math.signum(v.pz) * Voyageurs.DEBOUT_Z;
                    TrainClient.envoyer(v.pv, v.px, v.pz, false);
                    TrainClient.aide(false);
                }
            } else if (appui && !(Voyageurs.sieges(v.pv) && Float.isFinite(Voyageurs.baie(v.px)) && Math.abs(v.pz) > Voyageurs.SIEGE_Z - 0.75f
                    && Math.abs(v.pz) < Voyageurs.SIEGE_Z - Voyageurs.R + 0.03f)) {
                TrainClient.descendre();                                      // couloir, vestibule, soufflet, porte
            } else if (appui) {                                               // devant une banquette : on s'assoit
                // accroupi dans une baie : assis sur la banquette tournée dans le sens du regard
                double yawLocal = Math.toRadians(p.getYRot()) ;
                double regx = -Math.sin(yawLocal), regz = Math.cos(yawLocal);
                double lxDir = regx * Math.cos(pl.cap) + regz * Math.sin(pl.cap);
                float f = Voyageurs.baie(v.px);
                v.passis = true;
                v.px = f + (lxDir >= 0 ? -0.78f : 0.78f);
                v.pz = Math.signum(v.pz) * Voyageurs.ASSIS_Z;
                TrainClient.envoyer(v.pv, v.px, v.pz, true);
                TrainClient.aide(true);
            }
            v.accroupiPrec = acc;
            float[] in = v.passis ? new float[]{0, 0, 0} : TrainClient.entree();   // gauche, avant, vitesse
            double yaw = Math.toRadians(p.getYRot());
            double s = in[2];
            double ix = in[0], iz = in[1], l = Math.hypot(ix, iz);
            if (l > 1) {
                ix /= l;
                iz /= l;
            }
            double wx = (ix * Math.cos(yaw) - iz * Math.sin(yaw)) * s, wz = (iz * Math.cos(yaw) + ix * Math.sin(yaw)) * s;
            float dlx = (float) (wx * Math.cos(pl.cap) + wz * Math.sin(pl.cap));
            float dlz = (float) (-wx * Math.sin(pl.cap) + wz * Math.cos(pl.cap));
            float[] r = Voyageurs.deplacer(Voyageurs.CLIENT, v.pv, v.px, v.pz, dlx, dlz, Voyageurs.portesQuai(h, e), false);
            if (Boolean.getBoolean("heralisz.train.debug") && (dlx != 0 || dlz != 0) && r[1] == v.px && r[2] == v.pz) {
                HeraliszUtils.LOG.info("Train (debug) : bloqué en {} ({}, {}) vers ({}, {}) assis {} client {}", v.pv, v.px, v.pz, dlx, dlz, v.passis, Voyageurs.CLIENT.size());
            }
            boolean bouge = r[0] != v.pv || r[1] != v.px || r[2] != v.pz;
            v.pv = (int) r[0];
            v.px = r[1];
            v.pz = r[2];
            if (bouge || v.tickCount % 10 == 0) TrainClient.envoyer(v.pv, v.px, v.pz, v.passis);
            h.placer(v.pv, e.s(), pl);
            if (!Double.isNaN(v.capPrec)) {                                   // le regard tourne avec la voiture
                double d = Math.toDegrees(pl.cap - v.capPrec);
                d = ((d + 540) % 360) - 180;
                p.setYRot(p.getYRot() + (float) d);
                p.yRotO += (float) d;
                p.setYHeadRot(p.getYHeadRot() + (float) d);
            }
            v.capPrec = pl.cap;
        } else {
            v.pv = -1;
            h.placer(v.vehicule(), e.s(), pl);
        }
        if (v.pv >= 0 && rider == null) v.passis = false;
        Vec3 w = Voyageurs.versMonde(pl, v.lx(), Reseau.PLANCHER, v.lz());
        v.setPos(w.x, w.y, w.z);
    }
}
