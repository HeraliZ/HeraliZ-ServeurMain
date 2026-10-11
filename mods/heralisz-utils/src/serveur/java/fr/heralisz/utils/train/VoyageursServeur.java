package fr.heralisz.utils.train;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Les voyageurs, côté serveur (jar fusionné : partie serveur de Voyageurs, code d'origine déplacé tel quel) : tick du
 * voyageur, marche vérifiée, montée par les portes ouvertes, descentes (Maj, quai, sortie sûre), joueurs et bêtes sur la
 * voie (repoussés, percutés), déconnexion à bord.
 */
public final class VoyageursServeur {
    private VoyageursServeur() {}

    static void tickServeur(Voyageur v) {
        if (!Voyageurs.SERVEUR.contains(v)) Voyageurs.SERVEUR.add(v);
        MinecraftServer srv = v.getServer();
        Entity rider = v.getFirstPassenger();
        if (rider == null || srv == null) {
            v.discard();
            return;
        }
        if (rider.isRemoved() || rider.level() != v.level()) {                // passager mort, parti, changé de monde
            liberer(v, rider, null);
            return;
        }
        Horaire h = Horaire.get();
        if (h == null || !TrainServeur.actif(srv)) {
            liberer(v, rider, rider instanceof net.minecraft.world.entity.LivingEntity le ? sortie(v, le) : null);
            return;
        }
        Horaire.Placement pl = new Horaire.Placement();
        h.placer(v.vehicule(), h.etatRame(TrainServeur.temps(srv), Horaire.rame(v.vehicule())).s(), pl);
        Vec3 w = Voyageurs.versMonde(pl, v.lx(), Reseau.PLANCHER, v.lz());
        v.setPos(w.x, w.y, w.z);
        // Maj : lu puis effacé avant le tick du passager (le véhicule est tické d'abord) -> plus de descente vanilla
        // n'importe où ; Maj vient du client (paquet Descente). Maj maintenue 2 s debout : descente de secours.
        if (rider instanceof ServerPlayer sp) {
            boolean maj = sp.isShiftKeyDown();
            sp.setShiftKeyDown(false);
            if (maj) {
                v.majAbsent = 0;
                if (v.majRelachee && !v.assis() && ++v.majTicks >= 40) {
                    v.majTicks = 0;
                    descenteDemandee(sp, "Maj maintenue");
                }
            } else if (++v.majAbsent > 3) {                                  // relâchée (un paquet peut manquer un tick)
                v.majRelachee = true;
                v.majTicks = 0;
            }
        }
    }

    /** Position voulue par le client du voyageur (paquet Marche) : vérifiée, appliquée ; descente par une porte
     *  ouverte ; sinon recadrage. La hauteur du saut est acceptée de 0 à Voyageurs.SAUT_MAX, debout seulement, et en
     *  suivant la courbe d'un vrai saut (sautVerifie : pas de lévitation dans la voiture). */
    static void marche(ServerPlayer p, int veh, float x, float z, boolean assis, float hauteur) {
        if (!(p.getVehicle() instanceof Voyageur v) || p.getServer() == null) return;
        Horaire h = Horaire.get();
        if (h == null) return;
        sautVerifie(v, assis, hauteur);
        int ov = v.getEntityData().get(Voyageur.VEHICULE);
        Horaire.Etat e = h.etatRame(TrainServeur.temps(p.getServer()), Horaire.rame(ov));
        int pq = Voyageurs.portesQuai(h, e);
        float ox = v.getEntityData().get(Voyageur.LX), oz = v.getEntityData().get(Voyageur.LZ);
        boolean ok = veh == ov ? Math.hypot(x - ox, z - oz) < 1.2 : Math.abs(veh - ov) == 1 && Voyageurs.voiture(veh)
                && Math.abs(ox) > Voyageurs.VL - 0.6f && Math.abs(x) > Voyageurs.VL - 0.6f;
        boolean place = assis && Voyageurs.sieges(veh) && Float.isFinite(Voyageurs.baie(x)) && Math.abs(Math.abs(z) - Voyageurs.ASSIS_Z) < 0.1f;
        ok &= Voyageurs.voiture(veh) && Horaire.rame(veh) == Horaire.rame(ov)
                && (place || Voyageurs.libre(Voyageurs.SERVEUR, veh, x, z, pq, true));
        if (!ok) {
            if (Boolean.getBoolean("heralisz.train.debug")) {
                HeraliszUtils.LOG.info("Train (debug) : marche refusée {} -> {} ({}, {}) depuis {} ({}, {}) écart {} libre {} pq {}", p.getGameProfile().getName(),
                        veh, x, z, ov, ox, oz, Math.hypot(x - ox, z - oz), Voyageurs.libre(Voyageurs.SERVEUR, veh, x, z, pq, true), pq);
            }
            TrainServeur.recadrer(p, ov, ox, oz);
            return;
        }
        if (veh != ov) HeraliszUtils.LOG.info("{} passe dans la voiture {}", p.getGameProfile().getName(), veh);
        v.placer(veh, x, z);
        v.getEntityData().set(Voyageur.ASSIS, place);
        if (pq != 0 && Math.signum(z) == pq && Math.abs(z) > Voyageurs.VW + 0.2f) {          // descente sur le quai
            Horaire.Placement pl = new Horaire.Placement();
            h.placer(veh, e.s(), pl);
            Vec3 q = posable(p.level(), p, Voyageurs.versMonde(pl, x, Reseau.PLANCHER + 0.02, pq * (Voyageurs.VW + 0.65)), 0.6, 2.0);
            liberer(v, p, q != null ? q : sortie(v, p));
        }
    }

    /** Hauteur du saut de Minecraft k ticks après le décollage (Voyageurs.SAUT, PESANTEUR, FREIN), 0 une fois retombé. */
    private static final float[] ARC = new float[20];

    static {
        float y = 0f, vy = Voyageurs.SAUT;
        for (int k = 1; k < ARC.length; k++) {
            y += vy;
            vy = (vy - Voyageurs.PESANTEUR) * Voyageurs.FREIN;
            ARC[k] = Math.max(0f, y);
            if (y <= 0f) y = vy = 0f;
        }
    }

    /** Hauteur du saut annoncée par le client : bornée (0 à SAUT_MAX), nulle assis, et jamais au-dessus de la courbe
     *  d'un vrai saut (3 ticks de marge pour le réseau). Un nouveau saut seulement après être retombé au plancher et au
     *  moins 10 ticks après le précédent (comme le saut de Minecraft) : un client trafiqué ne peut au mieux que sauter
     *  sans arrêt, comme avec Espace maintenue, jamais flotter. */
    private static void sautVerifie(Voyageur v, boolean assis, float hauteur) {
        float hs = assis || !Float.isFinite(hauteur) ? 0f : Math.max(0f, Math.min(Voyageurs.SAUT_MAX, hauteur));
        if (hs <= 0.02f) {
            hs = 0f;
            v.sautSol = true;
        } else {
            int dt = v.tickCount - v.debutSaut;
            if (v.sautSol && (dt < 0 || dt >= 10)) {                         // décollage
                v.debutSaut = v.tickCount;
                v.sautSol = false;
                dt = 0;
            }
            float permis = 0f;
            for (int k = Math.max(0, dt - 3); k <= Math.min(ARC.length - 1, dt + 3); k++) permis = Math.max(permis, ARC[k]);
            hs = permis <= 0f ? 0f : Math.min(hs, permis + 0.1f);
        }
        if (hs != v.getEntityData().get(Voyageur.HAUTEUR)) v.getEntityData().set(Voyageur.HAUTEUR, hs);
    }

    /** Joueurs qui viennent de descendre : pas remontés tant qu'ils n'ont pas quitté la bande de montée (sinon, posés
     *  sur le quai devant une porte ouverte, ils remontaient au tick suivant). */
    static final java.util.Set<java.util.UUID> DESCENDUS = new java.util.HashSet<>();

    static void descendu(ServerPlayer p) {
        DESCENDUS.add(p.getUUID());
    }

    /** Le serveur fait descendre le passager : descente, voyageur retiré, puis téléportation en `ou` (null : rien, une
     *  téléportation qui suit le place). Dans cet ordre, sinon un tick client entre les deux le remet dans la voiture. */
    static void liberer(Voyageur v, Entity rider, Vec3 ou) {
        v.libere = true;
        v.sortieImposee = ou;
        try {
            rider.stopRiding();
        } finally {
            v.libere = false;
            v.sortieImposee = null;
        }
        v.discard();
        Voyageurs.SERVEUR.remove(v);
        if (ou != null) rider.teleportTo(ou.x, ou.y, ou.z);
        if (rider instanceof ServerPlayer sp) descendu(sp);
    }

    private static final double[] ECARTS = {Voyageurs.VW + 1.25, Voyageurs.VW + 1.8, Voyageurs.VW + 2.5};      // hors caisse et hors bande de montée

    /** Sortie sûre (descente par Maj, autre mod, commande...) : à côté de la voiture, sinon sur le quai de la gare. */
    static Vec3 sortie(Voyageur v, net.minecraft.world.entity.LivingEntity e) {
        Vec3 q = sortiePres(v, e);
        if (q == null) q = quai(v, e);
        return q != null ? q : e.position();
    }

    /** À côté de la voiture : côté du quai (ou le plus proche), à hauteur du voyageur puis des portes, aux trois écarts ;
     *  un point où l'on tient debout, sans liquide ni bloc dangereux, chunk chargé, hors du gabarit. */
    static Vec3 sortiePres(Voyageur v, net.minecraft.world.entity.LivingEntity e) {
        MinecraftServer srv = v.getServer();
        Horaire h = Horaire.get();
        if (srv == null || h == null) return null;
        Horaire.Etat et = h.etatRame(TrainServeur.temps(srv), Horaire.rame(v.vehicule()));
        Horaire.Placement pl = new Horaire.Placement();
        h.placer(v.vehicule(), et.s(), pl);
        int cote = et.aQuai() >= 0 ? h.reseau.gares[et.aQuai()].cote() : (v.lz() >= 0 ? 1 : -1);
        float x0 = Math.max(-Voyageurs.VL + 0.6f, Math.min(Voyageurs.VL - 0.6f, v.lx()));
        float[] xs = {x0, (Voyageurs.PX0 + Voyageurs.PX1) / 2, -(Voyageurs.PX0 + Voyageurs.PX1) / 2, x0 + 3, x0 - 3};
        for (int c : new int[]{cote, -cote}) {
            for (float x : xs) {
                for (double lat : ECARTS) {
                    Vec3 q = posable(v.level(), e, Voyageurs.versMonde(pl, x, Reseau.PLANCHER, c * lat), 1.0, 5.5);
                    if (q != null) return q;
                }
            }
        }
        return null;
    }

    /** Gare où est la rame, sinon la prochaine. */
    static Reseau.Gare gare(MinecraftServer srv, int rame) {
        Horaire h = Horaire.get();
        Horaire.Etat et = h.etatRame(TrainServeur.temps(srv), rame);
        return h.reseau.gares[et.aQuai() >= 0 ? et.aQuai() : et.suivante()];
    }

    /** Sur le quai de la gare où est le train (sinon la suivante), hors de la bande de montée : un point libre près du
     *  milieu du quai, sinon le milieu lui-même (le quai est sûr par construction ; chunk pas chargé : on y va). */
    static Vec3 quai(Voyageur v, net.minecraft.world.entity.LivingEntity e) {
        MinecraftServer srv = v.getServer();
        Horaire h = Horaire.get();
        if (srv == null || h == null) return null;
        Reseau.Gare g = gare(srv, Horaire.rame(v.vehicule()));
        double cap = Math.toRadians(g.cap()), nx = -Math.sin(cap) * g.cote(), nz = Math.cos(cap) * g.cote();
        double[] c = new double[3];
        Vec3 milieu = null;
        for (double d : new double[]{0, 2, -2, 4, -4, 6, -6}) {
            h.reseau.point(g.s() + d, c);
            Vec3 q = new Vec3(c[0] + nx * (Voyageurs.VW + 1.25), g.yQuai() + 0.02, c[2] + nz * (Voyageurs.VW + 1.25));
            if (milieu == null) milieu = q;
            if (!v.level().hasChunkAt(net.minecraft.core.BlockPos.containing(q))) return milieu;
            Vec3 ok = posable(v.level(), e, q, 1.5, 3.0);
            if (ok != null) return ok;
        }
        return milieu;
    }

    /** Premier sol porteur de p.y + haut à p.y - bas où l'entité tient debout sans collision, sans liquide ni bloc
     *  dangereux (feu, lave, magma, cactus...), chunk chargé ; null sinon. */
    private static Vec3 posable(net.minecraft.world.level.Level lvl, net.minecraft.world.entity.LivingEntity e, Vec3 p, double haut,
                                double bas) {
        net.minecraft.core.BlockPos b0 = net.minecraft.core.BlockPos.containing(p);
        if (!lvl.hasChunkAt(b0)) return null;
        for (int y = (int) Math.floor(p.y + haut); y >= (int) Math.floor(p.y - bas); y--) {
            net.minecraft.core.BlockPos pied = new net.minecraft.core.BlockPos(b0.getX(), y, b0.getZ());
            net.minecraft.core.BlockPos sous = pied.below();
            var st = lvl.getBlockState(sous);
            if (st.getCollisionShape(lvl, sous).isEmpty()) continue;
            if (!st.getFluidState().isEmpty() || st.is(net.minecraft.tags.BlockTags.FIRE) || st.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                    || st.is(net.minecraft.world.level.block.Blocks.CACTUS) || st.is(net.minecraft.tags.BlockTags.CAMPFIRES)) return null;
            double top = st.getCollisionShape(lvl, sous).max(net.minecraft.core.Direction.Axis.Y) + sous.getY();
            Vec3 q = new Vec3(p.x, top + 0.01, p.z);
            var boite = e.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(q);
            if (lvl.noCollision(e, boite) && !lvl.containsAnyLiquid(boite)) return q;
            return null;                                                     // sol trouvé mais pas la place : ailleurs
        }
        return null;
    }

    /** Le train est-il arrêté à quai ? En marche, on ne descend pas (on ne saute pas d'un train qui roule). Sans train
     *  actif, rien n'empêche de descendre. */
    public static boolean aQuai(MinecraftServer srv) {
        return aQuai(srv, 0);
    }

    public static boolean aQuai(MinecraftServer srv, int rame) {
        Horaire h = Horaire.get();
        if (h == null || srv == null || !TrainServeur.actif(srv)) return true;
        return h.etatRame(TrainServeur.temps(srv), rame).aQuai() >= 0;
    }

    /** Descente demandée par le passager (Maj, Maj maintenue) : seulement train arrêté en gare ; en marche, un message
     *  dans la barre d'action et le passager reste à bord. Les autres descentes (mort, commande, train arrêté par un
     *  opérateur...) passent toujours. */
    static void descenteDemandee(ServerPlayer p, String pourquoi) {
        if (!(p.getVehicle() instanceof Voyageur vy)) return;
        if (!aQuai(p.getServer(), Horaire.rame(vy.vehicule()))) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.heralisz_utils.train.en_marche"), true);
            return;
        }
        descendre(p, pourquoi);
    }

    /** Fait descendre un joueur de la rame (Maj, filet de sécurité...) : à côté du train, sinon sur le quai (avec un
     *  message). false si le joueur n'est pas dans le train. */
    public static boolean descendre(ServerPlayer p, String pourquoi) {
        if (!(p.getVehicle() instanceof Voyageur v)) return false;
        Vec3 q = sortiePres(v, p);
        String gare = null;
        if (q == null) {
            q = quai(v, p);
            if (q != null && p.getServer() != null) gare = gare(p.getServer(), Horaire.rame(v.vehicule())).nom();
        }
        liberer(v, p, q);
        if (gare != null) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.heralisz_utils.train.descente_quai", gare), true);
        }
        HeraliszUtils.LOG.info("{} descend du train ({}){}", p.getGameProfile().getName(), pourquoi, gare != null ? " sur le quai de " + gare : "");
        return true;
    }

    /** Montée par les portes ouvertes, joueurs restés dans le gabarit de la rame (à chaque tick du serveur). */
    static void tickMonde(ServerLevel lvl) {
        MinecraftServer srv = lvl.getServer();
        Voyageurs.SERVEUR.removeIf(Entity::isRemoved);
        if (!TrainServeur.actif(srv)) return;
        Horaire h = Horaire.get();
        Horaire.Etat[] etats = new Horaire.Etat[Horaire.RAMES];
        double[][] centres = new double[Horaire.RAMES][3];
        Horaire.Placement pl = new Horaire.Placement();
        for (int r = 0; r < Horaire.RAMES; r++) {
            etats[r] = h.etatRame(TrainServeur.temps(srv), r);
            h.reseau.point(etats[r].s(), centres[r]);
        }
        for (int r = 0; r < Horaire.RAMES; r++) {
        Horaire.Etat e = etats[r];
        if (e.v() > 1.0 && lvl.getGameTime() % 2 == 0) {                         // zombies et bêtes sur la voie : écrasés
            for (int i = 0; i < Horaire.RAME.length; i++) {
                h.placer(i, e.s(), pl);
                double demi = Horaire.RAME[i].longueur / 2;
                net.minecraft.world.phys.AABB boite = new net.minecraft.world.phys.AABB(pl.x - demi - 1, pl.y - 2, pl.z - demi - 1,
                        pl.x + demi + 1, pl.y + 5, pl.z + demi + 1);
                for (net.minecraft.world.entity.LivingEntity m : lvl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, boite,
                        x -> !(x instanceof Player) && x.isAlive())) {
                    double[] l = Voyageurs.versLocal(pl, m.position());
                    if (Math.abs(l[0]) < demi + 0.3 && Math.abs(l[2]) < Voyageurs.VW + 0.3 && l[1] > SOL && l[1] < 5.2) {
                        m.hurt(ecrase(lvl), Float.MAX_VALUE);
                        double cote = l[2] >= 0 ? 1 : -1;
                        Vec3 lat = Voyageurs.versMonde(pl, l[0], 0, cote).subtract(Voyageurs.versMonde(pl, l[0], 0, 0)).normalize();
                        m.setDeltaMovement(lat.scale(1.2).add(0, 0.5, 0));
                    }
                }
            }
        }
        }
        for (ServerPlayer p : lvl.players()) {
            if (p.isSpectator() || p.isPassenger()) continue;
            boolean dansBande = false;
            rames:
            for (int r = 0; r < Horaire.RAMES; r++) {
            Horaire.Etat e = etats[r];
            double[] c = centres[r];
            if (p.distanceToSqr(c[0], c[1], c[2]) > Math.pow(Horaire.LONGUEUR / 2 + 20, 2)) continue;
            int pq = Voyageurs.portesQuai(h, e);
            for (int i = 0; i < Horaire.RAME.length; i++) {
                int veh = r * Horaire.N + i;
                h.placer(veh, e.s(), pl);
                double[] l = Voyageurs.versLocal(pl, p.position());
                double demi = Horaire.RAME[i].longueur / 2;
                if (Math.abs(l[0]) > demi + 0.4 || Math.abs(l[2]) > Voyageurs.VW + 1.0 || l[1] < SOL - 0.2 || l[1] > 5.4) continue;
                float ax = (float) Math.abs(l[0]);
                boolean seuil = Voyageurs.voiture(veh) && ax >= Voyageurs.PX0 + 0.05 && ax <= Voyageurs.PX1 - 0.05;
                if (pq != 0 && seuil && Math.signum(l[2]) == pq && Math.abs(l[1] - Reseau.PLANCHER) < 0.7
                        && Math.abs(l[2]) > Voyageurs.VW - 0.4 && Math.abs(l[2]) < Voyageurs.VW + 0.9) {
                    dansBande = true;
                    if (DESCENDUS.contains(p.getUUID())) break rames;               // vient de descendre : on attend
                    monter(lvl, p, veh, (float) l[0], pq * (Voyageurs.VWI - Voyageurs.R - 0.02f));
                    break rames;
                }
                if (Math.abs(l[2]) < Voyageurs.VW + 0.25 && Math.abs(l[0]) < demi + 0.2 && l[1] > SOL && l[1] < 5.2) {
                    heurt(p, pl, l, e.v(), i == 0 && l[0] > demi - 1.5);
                    break rames;
                }
            }
            }
            if (!dansBande) DESCENDUS.remove(p.getUUID());                    // ressorti de la bande : réarmé
        }
    }

    private static void monter(ServerLevel lvl, ServerPlayer p, int veh, float x, float z) {
        Voyageur v = Train.VOYAGEUR.get().create(lvl);
        if (v == null) return;
        v.placer(veh, x, z);
        v.setPos(p.getX(), p.getY(), p.getZ());
        lvl.addFreshEntity(v);
        Voyageurs.SERVEUR.add(v);
        p.startRiding(v, true);
        HeraliszUtils.LOG.info("{} monte dans la voiture {} ({}, {})", p.getGameProfile().getName(), veh, x, z);
        lvl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.4f, 1.4f);
    }

    /** Dégâts « écrasé par le train » (data/heralisz_utils/damage_type/train.json) : ni armure, ni effets, ni
     *  enchantements ne les réduisent. */
    static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> TRAIN_DEGATS =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    new net.minecraft.resources.ResourceLocation(HeraliszUtils.MODID, "train"));

    static net.minecraft.world.damagesource.DamageSource ecrase(ServerLevel lvl) {
        return new net.minecraft.world.damagesource.DamageSource(
                lvl.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(TRAIN_DEGATS));
    }

    /** Bas de la bande où le train percute (sous le dessus du rail) : le sol de la voie est 0,72 dessous (le ballast
     *  affleure 0,4 au-dessus du sol aplani), le tablier d'un pont jusqu'à 1,57. */
    static final double SOL = -1.6;

    /** Coup d'un train qui roule, en dehors des cas où il écrase : 5 points (2,5 cœurs), armure ou pas. */
    static final float HEURT = 5f;

    /** Joueur dans le gabarit : le train roule -> heurté (2,5 cœurs) et repoussé à côté ; tué net seulement s'il est
     *  devant la locomotive ou dans la voie, sous la caisse (écrasé). À l'arrêt -> repoussé à côté, du côté le plus
     *  proche puis de l'autre, là où l'on tient debout (jamais dans le mur du quai). */
    private static void heurt(ServerPlayer p, Horaire.Placement pl, double[] l, double v, boolean devant) {
        double cote = l[2] >= 0 ? 1 : -1;
        Vec3 dehors = null;
        for (double c : new double[]{cote, -cote}) {
            for (double lat : new double[]{Voyageurs.VW + 0.45, Voyageurs.VW + 1.25}) {
                dehors = posable(p.level(), p, Voyageurs.versMonde(pl, l[0], Math.max(l[1], 0), c * lat), 1.6, 2.5);
                if (dehors != null) break;
            }
            if (dehors != null) break;
        }
        if (v > 1.0) {
            boolean dessous = Math.abs(l[2]) < Voyageurs.VW - 0.5 && l[1] < Reseau.PLANCHER - 0.3;    // dans la voie
            p.hurt(ecrase(p.serverLevel()), devant || dessous ? Float.MAX_VALUE : HEURT);
            if (p.isAlive()) {                                              // heurté (ou créatif) : projeté à côté
                Vec3 lat = Voyageurs.versMonde(pl, l[0], 0, cote * 1.0).subtract(Voyageurs.versMonde(pl, l[0], 0, 0)).normalize();
                if (dehors != null) p.teleportTo(dehors.x, dehors.y, dehors.z);
                p.setDeltaMovement(lat.scale(1.5).add(0, 0.45, 0));
                p.hurtMarked = true;
            }
        } else if (dehors != null) {
            p.teleportTo(dehors.x, dehors.y, dehors.z);
        }
    }

    /** Déconnexion à bord (ou reconnexion d'un joueur resté passager d'une ancienne sauvegarde) : on se réveillera sur
     *  le quai de la gare où le train est (ou de la prochaine). */
    static void deconnexion(ServerPlayer p) {
        if (!(p.getVehicle() instanceof Voyageur v) || p.getServer() == null || Horaire.get() == null) return;
        liberer(v, p, quai(v, p));
        HeraliszUtils.LOG.info("{} déconnecté dans le train : posé sur le quai de {}", p.getGameProfile().getName(),
                gare(p.getServer(), Horaire.rame(v.vehicule())).nom());
    }
}
