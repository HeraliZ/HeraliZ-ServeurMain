package fr.heralisz.utils.train;

import fr.heralisz.utils.HeraliszUtils;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;

/**
 * Tick client d'un voyageur : le passager calcule lui-même sa marche dans la voiture (et son saut, Espace debout, et son
 * allongé, touche « Ramper » de TaCZ) et l'envoie au serveur ; les autres voyageurs sont placés d'après l'horaire, à leur
 * place dans la voiture lissée sur 3 ticks (comme Minecraft pour toute entité : sinon ils avançaient par à-coups au gré
 * du réseau). Branché par client.PartieClient sur {@link Voyageur#TICK_CLIENT}.
 * <p>
 * La marche est celle de Minecraft au sol (Voyageurs.FROTTEMENT_SOL...) : vitesse du joueur (attribut : course, lenteur,
 * armes de TaCZ...), départ et arrêt progressifs, saut en courant avec son élan ; avant, la vitesse était fixe et
 * immédiate, et on « glissait ». Le pas fait dans la voiture à chaque tick anime les jambes (mixin LivingEntityTrainMixin,
 * qui tourne aussi le corps dans le sens de la marche), le balancement de la vue et l'arme de TaCZ (passager « au sol » :
 * LocalPlayerTrainMixin), et fait entendre les pas sur le plancher (bois) ou la passerelle du soufflet (tôle), pour
 * soi comme pour les autres passagers.
 */
public final class VoyageursClient {
    private VoyageursClient() {}

    /** Bonus de vitesse de la course de Minecraft (LivingEntity.SPEED_MODIFIER_SPRINTING_UUID, privé) : +30 %. */
    private static final UUID COURSE_UUID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");

    public static void tick(Voyageur v) {
        tickClient(v);
    }

    static void tickClient(Voyageur v) {
        if (!Voyageurs.CLIENT.contains(v)) Voyageurs.CLIENT.add(v);
        Voyageurs.CLIENT.removeIf(Entity::isRemoved);
        Horaire h = Horaire.get();
        if (h == null) return;
        Entity rider = v.getFirstPassenger();
        boolean moi = rider instanceof LocalPlayer && TrainClient.estMoi(rider);
        if (!moi) {
            v.pv = -1;
            lisser(v);
        }
        int rame = Horaire.rame(moi && v.pv >= 0 ? v.pv : v.vehicule());
        Horaire.Etat e = h.etatRame(TrainClient.tTick, rame);
        Horaire.Placement pl = new Horaire.Placement();
        if (moi) {
            marcher(v, (LocalPlayer) rider, h, e, pl);
        } else {
            h.placer(v.vehicule(), e.s(), pl);
        }
        if (v.pv >= 0 && rider == null) v.passis = false;
        Vec3 w = Voyageurs.versMonde(pl, v.lx(), Reseau.PLANCHER, v.lz());
        v.setPos(w.x, w.y, w.z);
        v.capAnim = pl.cap;
        compterPas(v, moi, rider);
        if (rider != null && !moi) rider.setOnGround(v.surLePlancher() || v.allonge());
    }

    // ------------------------------------------------------------------------------------------------ le passager local

    /** Devant une banquette, dans une baie, assez près pour s'y asseoir. */
    private static boolean devantBanquette(Voyageur v) {
        return Voyageurs.sieges(v.pv) && Float.isFinite(Voyageurs.baie(v.px)) && Math.abs(v.pz) > Voyageurs.SIEGE_Z - 0.75f
                && Math.abs(v.pz) < Voyageurs.SIEGE_Z - Voyageurs.R + 0.03f;
    }

    private static void marcher(Voyageur v, LocalPlayer p, Horaire h, Horaire.Etat e, Horaire.Placement pl) {
        if (v.pv < 0) {
            v.pv = v.getEntityData().get(Voyageur.VEHICULE);
            v.px = v.getEntityData().get(Voyageur.LX);
            v.pz = v.getEntityData().get(Voyageur.LZ);
            v.accroupiPrec = TrainClient.accroupi();                         // Maj tenu en montant : pas un appui
            v.ramperPrec = TrainClient.ramper();
            v.ph = 0f;
            v.pvy = 0f;
            v.vx = 0f;
            v.vz = 0f;
            v.passis = false;
            v.pallonge = false;
            v.pcourse = false;
            v.sautBloque = TrainClient.saut();                               // Espace tenue en montant : pas un saut
            TrainClient.aide(false);
        }
        h.placer(v.pv, e.s(), pl);
        boolean acc = TrainClient.accroupi();
        boolean appui = acc && !v.accroupiPrec;
        boolean rp = TrainClient.ramper();
        boolean appuiRamper = rp && !v.ramperPrec;
        boolean change = false;
        if (v.passis) {                                                   // assis : Espace se lève, Maj descend
            if (appui) {
                TrainClient.descendre();
            } else if (TrainClient.saut()) {
                float f = Voyageurs.baie(v.px);
                v.passis = false;
                v.px = f;
                v.pz = Math.signum(v.pz) * Voyageurs.DEBOUT_Z;
                v.sautBloque = true;                                      // Espace a servi à se lever : pas de saut
                change = true;
                TrainClient.aide(false);
            }
        } else if (v.pallonge) {                                          // allongé : Ramper ou Espace relève
            if (appuiRamper || TrainClient.saut()) {
                v.pallonge = false;
                v.sautBloque = TrainClient.saut();                        // Espace a servi à se relever : pas de saut
                change = true;
                TrainClient.aide(false);
            }
        } else if (appuiRamper && v.ph <= 0f && v.pvy <= 0f && TrainClient.ramperPermis()) {
            v.pallonge = true;                                            // debout, les pieds au plancher : on s'allonge
            change = true;
            TrainClient.aide(false, true);
        } else if (appui && !devantBanquette(v)) {
            TrainClient.descendre();                                      // couloir, vestibule, soufflet, porte
        } else if (appui) {                                               // devant une banquette : on s'assoit
            // accroupi dans une baie : assis sur la banquette tournée dans le sens du regard
            double yawLocal = Math.toRadians(p.getYRot());
            double regx = -Math.sin(yawLocal), regz = Math.cos(yawLocal);
            double lxDir = regx * Math.cos(pl.cap) + regz * Math.sin(pl.cap);
            float f = Voyageurs.baie(v.px);
            v.passis = true;
            v.px = f + (lxDir >= 0 ? -0.78f : 0.78f);
            v.pz = Math.signum(v.pz) * Voyageurs.ASSIS_Z;
            v.ph = 0f;
            v.pvy = 0f;
            v.vx = 0f;
            v.vz = 0f;
            change = true;
            TrainClient.aide(true);
        }
        v.accroupiPrec = acc;
        v.ramperPrec = rp;
        boolean coursePrec = v.pcourse;
        boolean bouge = marche(v, p, h, e, pl);
        change |= v.pcourse != coursePrec;
        if (bouge || change || v.tickCount % 10 == 0) {
            TrainClient.envoyer(v.pv, v.px, v.pz, Train.Marche.etat(v.passis, v.pallonge, v.pcourse), v.ph);
        }
        h.placer(v.pv, e.s(), pl);
        if (!Double.isNaN(v.capPrec)) {                                   // le regard tourne avec la voiture
            double d = Math.toDegrees(pl.cap - v.capPrec);
            d = ((d + 540) % 360) - 180;
            p.setYRot(p.getYRot() + (float) d);
            p.yRotO += (float) d;
            p.setYHeadRot(p.getYHeadRot() + (float) d);
        }
        v.capPrec = pl.cap;
    }

    /** Vitesse de marche du joueur (attribut de Minecraft, sans le bonus de course), comme LivingEntity.getSpeed :
     *  0,1 à pied ; lenteur, rapidité et ralentissements des armes de TaCZ compris. */
    private static double vitesseMarche(Player p) {
        AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return 0.1;
        double v = a.getValue();
        if (a.getModifier(COURSE_UUID) != null) v /= 1.3;
        return v;
    }

    /** Marche (et saut) du passager pendant ce tick, comme Minecraft au sol (LivingEntity.travel) : la vitesse du joueur
     *  s'ajoute dans la direction voulue, le passager se déplace (collisions de la voiture, axe par axe : un axe bloqué
     *  perd sa vitesse), puis le frottement du sol ou de l'air ralentit. Renvoie vrai s'il a bougé (à envoyer). */
    private static boolean marche(Voyageur v, LocalPlayer p, Horaire h, Horaire.Etat e, Horaire.Placement pl) {
        Minecraft mc = Minecraft.getInstance();
        boolean libre = mc.screen == null;
        boolean espace = TrainClient.saut();
        if (!espace) v.sautBloque = false;
        if (v.passis) {
            v.vx = 0f;
            v.vz = 0f;
            course(v, p, false);
            boolean avant = v.ph != 0f;
            v.ph = 0f;
            v.pvy = 0f;
            return avant;
        }
        float ix = libre ? p.input.leftImpulse * 0.98f : 0f, iz = libre ? p.input.forwardImpulse * 0.98f : 0f;
        if (p.isUsingItem()) {                                            // manger, boire, bander un arc : au pas (LocalPlayer.aiStep)
            ix *= 0.2f;
            iz *= 0.2f;
        }
        boolean auSol = v.ph <= 0f && v.pvy <= 0f;
        // course : comme Minecraft (touche de course en avançant franchement ; elle dure tant qu'on avance), debout,
        // assez nourri, sans objet en cours d'utilisation ni cécité
        boolean nourri = p.getFoodData().getFoodLevel() > 6 || p.getAbilities().mayfly;
        boolean course = libre && !v.pallonge && !p.input.shiftKeyDown && nourri && !p.isUsingItem() && !p.hasEffect(MobEffects.BLINDNESS)
                && (v.pcourse ? p.input.forwardImpulse > 1e-5f : mc.options.keySprint.isDown() && p.input.forwardImpulse >= 0.8f);
        course(v, p, course);
        double yaw = Math.toRadians(p.getYRot());
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cc = Math.cos(pl.cap), sc = Math.sin(pl.cap);
        // saut (debout, les pieds au plancher) : en courant, élan de 0,2 vers l'avant (Player.jumpFromGround)
        if (!v.pallonge && auSol && espace && !v.sautBloque) {
            v.pvy = Voyageurs.SAUT;
            if (course) {
                double wx = -sy * Voyageurs.ELAN_SAUT, wz = cy * Voyageurs.ELAN_SAUT;
                v.vx += (float) (wx * cc + wz * sc);
                v.vz += (float) (-wx * sc + wz * cc);
            }
        }
        float accel = auSol ? (float) (vitesseMarche(p) * (course ? 1.3 : 1.0)) : Voyageurs.ACCEL_AIR * (course ? 1.3f : 1f);
        double l = ix * ix + iz * iz;
        if (l > 1) {
            l = Math.sqrt(l);
            ix /= l;
            iz /= l;
        }
        if (l > 1e-7) {
            double wx = (ix * cy - iz * sy) * accel, wz = (iz * cy + ix * sy) * accel;
            v.vx += (float) (wx * cc + wz * sc);
            v.vz += (float) (-wx * sc + wz * cc);
        }
        float[] r = Voyageurs.deplacer(Voyageurs.CLIENT, v.pv, v.px, v.pz, v.vx, v.vz, Voyageurs.portesQuai(h, e), false);
        if (Boolean.getBoolean("heralisz.train.debug") && (v.vx != 0 || v.vz != 0) && r[1] == v.px && r[2] == v.pz) {
            HeraliszUtils.LOG.info("Train (debug) : bloqué en {} ({}, {}) vers ({}, {}) assis {} client {}", v.pv, v.px, v.pz, v.vx, v.vz, v.passis,
                    Voyageurs.CLIENT.size());
        }
        boolean bouge = r[0] != v.pv || r[1] != v.px || r[2] != v.pz;
        if (r[3] != 0) v.vx = 0f;
        if (r[4] != 0) v.vz = 0f;
        v.pv = (int) r[0];
        v.px = r[1];
        v.pz = r[2];
        float frottement = auSol ? Voyageurs.FROTTEMENT_SOL : Voyageurs.FROTTEMENT_AIR;
        v.vx *= frottement;
        v.vz *= frottement;
        if (Math.abs(v.vx) < 0.003f) v.vx = 0f;
        if (Math.abs(v.vz) < 0.003f) v.vz = 0f;
        bouge |= sauter(v);
        return bouge;
    }

    /** Course du passager : gardée pour l'envoyer au serveur (animations chez les autres) et posée sur le joueur (champ
     *  de vision et bonus de vitesse de Minecraft, comme hors du train). */
    private static void course(Voyageur v, LocalPlayer p, boolean course) {
        v.pcourse = course;
        if (p.isSprinting() != course) p.setSprinting(course);
    }

    /** Hauteur du saut en cours (Espace, départ décidé par marche) : courbe du saut de Minecraft, prédite ici (le
     *  serveur la vérifie), plafonnée sous le linteau des portes. Renvoie vrai si la hauteur a changé (à envoyer). */
    private static boolean sauter(Voyageur v) {
        float avant = v.ph;
        if (v.passis || v.pallonge) {
            v.ph = 0f;
            v.pvy = 0f;
        } else if (v.ph > 0f || v.pvy > 0f) {
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
        return v.ph != avant;
    }

    // ------------------------------------------------------------------------------------------------ les autres passagers

    /** Place affichée d'un autre passager : celle reçue du serveur, rejointe en 3 ticks à chaque nouvelle valeur (comme
     *  Minecraft fait pour la position de toute entité) ; d'un coup en s'asseyant, en s'allongeant, en se relevant ou
     *  après un grand écart. Le passage d'une voiture à la voisine garde le même point. */
    private static void lisser(Voyageur v) {
        var d = v.getEntityData();
        int tv = d.get(Voyageur.VEHICULE);
        float tx = d.get(Voyageur.LX), tz = d.get(Voyageur.LZ);
        boolean assis = d.get(Voyageur.ASSIS), allonge = !assis && d.get(Voyageur.ALLONGE);
        float th = assis || allonge ? 0f : d.get(Voyageur.HAUTEUR);
        boolean net = v.rv < 0 || assis != v.rAssis || allonge != v.rAllonge || Math.abs(tv - v.rv) > 1;
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
        v.rAllonge = allonge;
    }

    // ------------------------------------------------------------------------------------------------ pas, jambes, corps

    /** Pas fait dans la voiture depuis le tick précédent (coordonnées de la voiture : le roulement du train n'y est pour
     *  rien), pour les jambes, le corps et les bruits de pas (un pas tous les 1/0,6 bloc, comme Entity.move ; un bruit
     *  aussi en retombant d'un saut). */
    private static void compterPas(Voyageur v, boolean moi, Entity rider) {
        float x = v.lx(), z = v.lz();
        int veh = v.vehicule();
        float dx = 0f, dz = 0f;
        if (veh == v.animVeh) {
            dx = x - v.animX;
            dz = z - v.animZ;
        } else if (v.animVeh >= 0 && Math.abs(veh - v.animVeh) == 1) {     // passage dans la voiture voisine
            dx = x - Voyageurs.versVoisine(v.animVeh, veh, v.animX);
            dz = z - v.animZ;
        }
        float d = (float) Math.hypot(dx, dz);
        if (d > 1.0f) {                                                    // recadrage par le serveur, pas un pas
            d = 0f;
            dx = 0f;
            dz = 0f;
        }
        v.pas = moi ? d : v.pas * 0.3f + d * 0.7f;
        v.dirX = dx;
        v.dirZ = dz;
        v.animVeh = veh;
        v.animX = x;
        v.animZ = z;
        if (rider == null || v.assis()) {
            v.enLair = false;
            return;
        }
        boolean sol = v.hauteur() <= 0.02f;
        if (sol && v.enLair) pas(v, rider, 1.4f);                          // retombée d'un saut
        v.enLair = !sol;
        if (sol && d > 0f) {
            v.distPas += d * 0.6f;
            if (v.distPas > v.prochainPas) {
                v.prochainPas = (int) v.distPas + 1;
                pas(v, rider, v.allonge() ? 0.6f : 1.0f);
            }
        }
    }

    /** Bruit d'un pas : plancher des voitures (bois), passerelle du soufflet (tôle striée). */
    private static void pas(Voyageur v, Entity rider, float force) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || rider.isSilent()) return;
        SoundType t = Math.abs(v.lx()) > Voyageurs.VL - 0.3f ? SoundType.METAL : SoundType.WOOD;
        mc.level.playLocalSound(rider.getX(), rider.getY(), rider.getZ(), t.getStepSound(), SoundSource.PLAYERS, t.getVolume() * 0.15f * force,
                t.getPitch(), false);
    }

    /** Vitesse de marche (blocs par tick) d'un passager debout ou allongé du train, pour l'animation de ses jambes ; -1
     *  s'il n'est pas sur le plancher du train (le jeu calcule alors l'animation comme d'habitude). Appelé par le mixin
     *  LivingEntityTrainMixin, aussi pour le joueur du serveur intégré en solo : seulement côté client. */
    public static float marcheAnimee(LivingEntity e) {
        if (!e.level().isClientSide() || !(e.getVehicle() instanceof Voyageur v) || v.isRemoved() || v.assis()) return -1f;
        return v.pas;
    }

    /** Corps tourné dans le sens de la marche, comme LivingEntity.tick le fait d'après le déplacement dans le monde (nul
     *  pour un passager) : en reculant, le corps reste face au regard ; en marchant de côté, il se tourne à moitié. Sinon
     *  un passager qui marche de côté « glissait » chez les autres. Appelé par LivingEntityTrainMixin. */
    public static void orienterCorps(LivingEntity e) {
        if (!(e.getVehicle() instanceof Voyageur v) || v.isRemoved() || v.assis()) return;
        float dx = v.dirX, dz = v.dirZ;
        if (dx * dx + dz * dz <= 0.0025f) return;
        double c = Math.cos(v.capAnim), s = Math.sin(v.capAnim);
        double wx = dx * c - dz * s, wz = dx * s + dz * c;
        float f4 = (float) (Mth.atan2(wz, wx) * (180.0 / Math.PI)) - 90.0f;
        float f5 = Mth.abs(Mth.wrapDegrees(e.getYRot()) - f4);
        float cible = 95.0f < f5 && f5 < 265.0f ? f4 - 180.0f : f4;
        e.yBodyRot += Mth.wrapDegrees(cible - e.yBodyRot) * 0.3f;
    }

    /** Fin du tick du joueur local passager (mixin LocalPlayerTrainMixin) : balancement de la vue (Player.rideTick le remet
     *  à zéro pour tout passager ; même calcul que Player.aiStep, au plus 0,1, approché à 40 % par tick, nul en l'air) et
     *  « au sol » quand il tient sur le plancher : l'arme de TaCZ joue alors son balancement de marche ou de course (elle
     *  restait immobile, comme en l'air, et l'on avait l'impression de glisser). */
    public static void apresTick(LocalPlayer p) {
        if (!(p.getVehicle() instanceof Voyageur v) || v.isRemoved()) return;
        p.setOnGround(v.surLePlancher() || v.allonge());
        if (v.assis()) return;
        float cible = v.hauteur() > 0.02f ? 0f : Math.min(0.1f, v.pas);
        p.oBob = v.bobPrec;
        p.bob = v.bobPrec + (cible - v.bobPrec) * 0.4f;
        v.bobPrec = p.bob;
        p.walkDist = p.walkDistO + v.pas * 0.6f;
    }

    /** Véhicule vu par l'animation à la troisième personne de TaCZ (mixins TaczAnimationTrainMixin et
     *  TaczAjustementTrainMixin) : aucun pour un passager debout ou allongé du train (sinon TaCZ lui met les jambes en pose
     *  de monture, « assis sur une selle », et ne tourne plus le haut du corps vers le regard) ; le vrai véhicule sinon. */
    public static Entity vehiculePourAnimation(Player p) {
        Entity veh = p.getVehicle();
        return veh instanceof Voyageur v && !v.assis() ? null : veh;
    }
}
