package fr.heralisz.utils.train;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Tick client d'un voyageur (repris tel quel de Voyageurs.tickClient) : le passager calcule lui-même sa marche dans la
 * voiture (et son saut, Espace debout) et l'envoie au serveur ; les autres voyageurs sont placés d'après l'horaire.
 * Branché par client.PartieClient sur {@link Voyageur#TICK_CLIENT}. Le pas fait dans la voiture à chaque tick anime les
 * jambes des passagers debout (mixin LivingEntityTrainMixin) et le balancement de la vue (LocalPlayerTrainMixin) : le
 * jeu les calcule d'après le déplacement dans le monde, nul pour un passager.
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
        int rv = Horaire.rame(v.pv >= 0 ? v.pv : v.getEntityData().get(Voyageur.VEHICULE));
        Horaire.Etat e = h.etatRame(TrainClient.tTick, rv);
        Entity rider = v.getFirstPassenger();
        Horaire.Placement pl = new Horaire.Placement();
        if (rider != null && TrainClient.estMoi(rider)) {
            if (v.pv < 0) {
                v.pv = v.getEntityData().get(Voyageur.VEHICULE);
                v.px = v.getEntityData().get(Voyageur.LX);
                v.pz = v.getEntityData().get(Voyageur.LZ);
                v.accroupiPrec = TrainClient.accroupi();                         // Maj tenu en montant : pas un appui
                v.ph = 0f;
                v.pvy = 0f;
                v.sautBloque = TrainClient.saut();                               // Espace tenue en montant : pas un saut
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
                    v.sautBloque = true;                                      // Espace a servi à se lever : pas de saut
                    TrainClient.envoyer(v.pv, v.px, v.pz, false, 0f);
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
                v.ph = 0f;
                v.pvy = 0f;
                TrainClient.envoyer(v.pv, v.px, v.pz, true, 0f);
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
            bouge |= sauter(v);
            if (bouge || v.tickCount % 10 == 0) TrainClient.envoyer(v.pv, v.px, v.pz, v.passis, v.ph);
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
            lisser(v);
            h.placer(v.vehicule(), e.s(), pl);
        }
        if (v.pv >= 0 && rider == null) v.passis = false;
        Vec3 w = Voyageurs.versMonde(pl, v.lx(), Reseau.PLANCHER, v.lz());
        v.setPos(w.x, w.y, w.z);
        compterPas(v, rider != null && TrainClient.estMoi(rider));
    }

    // ------------------------------------------------------------------------------------------------ les autres passagers

    /** Place affichée d'un autre passager : celle reçue du serveur, rejointe en 3 ticks à chaque nouvelle valeur (comme
     *  Minecraft fait pour la position de toute entité) ; d'un coup en s'asseyant, en se relevant ou après un
     *  grand écart. Le passage d'une voiture à la voisine garde le même point. */
    private static void lisser(Voyageur v) {
        var d = v.getEntityData();
        int tv = d.get(Voyageur.VEHICULE);
        float tx = d.get(Voyageur.LX), tz = d.get(Voyageur.LZ);
        boolean assis = d.get(Voyageur.ASSIS);
        float th = assis ? 0f : d.get(Voyageur.HAUTEUR);
        boolean net = v.rv < 0 || assis != v.rAssis || Math.abs(tv - v.rv) > 1;
        if (!net && tv != v.rv) {
            v.rx = Voyageurs.versVoisine(v.rv, tv, v.rx);
            v.cx = Voyageurs.versVoisine(v.rv, tv, v.cx);
            v.rv = tv;
        }
        if (net || Math.hypot(tx - v.rx, tz - v.rz) > 3.0) {
            v.rv = tv;
            v.rx = v.cx = tx;
            v.rz = v.cz = tz;
            v.rh = v.ch = th;
            v.rPas = 0;
        } else {
            if (tx != v.cx || tz != v.cz || th != v.ch) {
                v.cx = tx;
                v.cz = tz;
                v.ch = th;
                v.rPas = 3;
            }
            if (v.rPas > 0) {
                v.rx += (tx - v.rx) / v.rPas;
                v.rz += (tz - v.rz) / v.rPas;
                v.rh += (th - v.rh) / v.rPas;
                v.rPas--;
            }
        }
        v.rAssis = assis;
    }

    /** Saut du passager debout (Espace) : courbe du saut de Minecraft, prédite ici (le serveur la vérifie), plafonnée
     *  sous le linteau des portes. Pas assis, pas avec un écran ouvert, pas avec la touche qui vient de servir à se lever.
     *  Renvoie vrai si la hauteur a changé (à envoyer). */
    private static boolean sauter(Voyageur v) {
        float avant = v.ph;
        boolean espace = TrainClient.saut();
        if (!espace) v.sautBloque = false;
        if (v.passis) {
            v.ph = 0f;
            v.pvy = 0f;
        } else {
            if (v.ph <= 0f && v.pvy <= 0f && espace && !v.sautBloque) v.pvy = Voyageurs.SAUT;
            if (v.ph > 0f || v.pvy > 0f) {
                v.ph += v.pvy;
                v.pvy = (v.pvy - Voyageurs.PESANTEUR) * Voyageurs.FREIN;
                float plafond = Voyageurs.plafondSaut(v.px);
                if (v.ph > plafond) {                                         // la tête touche : on retombe
                    v.ph = plafond;
                    v.pvy = Math.min(v.pvy, 0f);
                }
                if (v.ph <= 0f) {
                    v.ph = 0f;
                    v.pvy = 0f;
                }
            }
        }
        return v.ph != avant;
    }

    /** Pas fait dans la voiture depuis le tick précédent (coordonnées de la voiture : le roulement du train n'y est
     *  pour rien). Les positions des autres arrivent du serveur par à-coups : moyenne glissante pour eux. */
    private static void compterPas(Voyageur v, boolean moi) {
        float x = v.lx(), z = v.lz();
        int veh = v.vehicule();
        float d;
        if (veh == v.animVeh) {
            d = (float) Math.hypot(x - v.animX, z - v.animZ);
            if (d > 1.0f) d = 0f;                                              // recadrage par le serveur, pas un pas
        } else {
            d = v.animVeh >= 0 ? v.pas : 0f;                                   // passage dans la voiture voisine
        }
        v.pas = moi ? d : v.pas * 0.5f + d * 0.5f;
        v.animVeh = veh;
        v.animX = x;
        v.animZ = z;
    }

    /** Vitesse de marche (blocs par tick) d'un passager debout du train, pour l'animation de ses jambes ; -1 s'il n'est
     *  pas debout dans le train (le jeu calcule alors l'animation comme d'habitude). Appelé par le mixin
     *  LivingEntityTrainMixin, aussi pour le joueur du serveur intégré en solo : seulement côté client. */
    public static float marcheAnimee(LivingEntity e) {
        if (!e.level().isClientSide() || !(e.getVehicle() instanceof Voyageur v) || v.isRemoved() || v.assis()) return -1f;
        return v.pas;
    }

    /** Balancement de la vue du joueur qui marche dans la voiture (option « Balancement de la vue ») : Player.rideTick le
     *  remet à zéro pour tout passager. Même calcul que Player.aiStep (au plus 0,1, approché à 40 % par tick), nul en
     *  l'air ; la distance de marche fait avancer le balancement. Appelé par le mixin LocalPlayerTrainMixin. */
    public static void balancement(LocalPlayer p) {
        if (!(p.getVehicle() instanceof Voyageur v) || v.isRemoved() || v.assis()) return;
        float cible = v.hauteur() > 0.02f ? 0f : Math.min(0.1f, v.pas);
        p.oBob = v.bobPrec;
        p.bob = v.bobPrec + (cible - v.bobPrec) * 0.4f;
        v.bobPrec = p.bob;
        p.walkDist = p.walkDistO + v.pas * 0.6f;
    }
}
