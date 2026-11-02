package com.Spartan.check.combat;

import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.check.CheckManager;
import com.Spartan.check.MovementCheck;
import com.Spartan.check.movement.MovePacket;
import com.Spartan.config.SpartanConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;

/**
 * Tirs TaCZ analysés par le serveur (audit du 02/11, point 14 ; A326) : avant, seuls les coups de mêlée étaient
 * contrôlés. Trois indices, réglés dans {@code [tirs]}, toujours des ALERTES au staff (aucune sanction, aucun coup
 * annulé) avec les chiffres :
 * <ul>
 *   <li>taux de tirs à la tête anormal sur les dernières touches de JOUEURS à distance (aimbot) ;</li>
 *   <li>tirs à la tête juste après un grand mouvement de visée en moins de 100 ms (« coup sec » d'un aimbot) ; ignoré en
 *   véhicule, en train et juste après une téléportation (la visée y tourne sans le joueur) ;</li>
 *   <li>tirs refusés par TaCZ lui-même (cadence de l'arme dépassée, horloge des tirs trafiquée : « rapid fire »).</li>
 * </ul>
 * Les zombies et les PNJ ne comptent pas, ni les bots de charge. Un résumé de chaque session (tirs, touches, têtes,
 * refus) est écrit dans {@code config/spartan/tirs.log} pour régler les seuils.
 *
 * <p>TaCZ est lu par ses événements, sans dépendance de compilation (comme Noise) ; les refus viennent d'un Mixin
 * facultatif sur TaCZ ({@code TaczTirMixin}). TaCZ absent ou d'une autre version : rien ne se passe, le journal le dit.
 */
public class TirsTacz extends MovementCheck {
    private static final Map<UUID, Etat> ETATS = new ConcurrentHashMap<>();
    private static TirsTacz instance;
    private static boolean branche;
    private static Field champTireur;

    /** Rotations récentes du joueur (paquets de mouvement) et dernier tir. */
    private static final class Etat {
        final String nom;
        final StatsTirs stats;
        final long[] nanos = new long[8];
        final float[] yaw = new float[8];
        final float[] pitch = new float[8];
        int pos;
        boolean dernierTirSec;
        long dernierTirNanos;

        Etat(String nom) {
            this.nom = nom;
            this.stats = new StatsTirs(SpartanConfig.TIRS_HISTORIQUE.get());
        }
    }

    public TirsTacz() {
        super("Tirs", "tir"); // catégorie sans correction : jamais de coup annulé ni de renvoi
        instance = this;
    }

    private static boolean ignore(Entity e) {
        return !(e instanceof ServerPlayer p) || p instanceof FakePlayer || p.getTags().contains("heraliz_bot");
    }

    private static Etat etat(ServerPlayer p) {
        return ETATS.computeIfAbsent(p.getUUID(), u -> new Etat(p.getName().getString()));
    }

    @Override
    public void onMovePacket(ServerPlayer player, MovePacket packet) {
        if (!packet.hasRot() || !SpartanConfig.TIRS_ENABLED.get()) {
            return;
        }
        Etat e = ETATS.get(player.getUUID());
        if (e == null) {
            return; // jamais tiré : rien à garder
        }
        e.nanos[e.pos] = packet.nanos();
        e.yaw[e.pos] = packet.yRot();
        e.pitch[e.pos] = packet.xRot();
        e.pos = (e.pos + 1) % e.nanos.length;
    }

    /** Plus grand mouvement de visée (degrés) entre la visée actuelle et celles des 100 dernières ms. */
    private static float mouvementRecent(Etat e, ServerPlayer p, long maintenant) {
        float y = p.getYRot();
        float x = p.getXRot();
        float max = 0;
        for (int i = 0; i < e.nanos.length; i++) {
            if (e.nanos[i] == 0 || maintenant - e.nanos[i] > 100_000_000L) {
                continue;
            }
            float dy = Mth.wrapDegrees(y - e.yaw[i]);
            float dx = x - e.pitch[i];
            max = Math.max(max, (float) Math.sqrt(dy * dy + dx * dx));
        }
        return max;
    }

    // ------------------------------------------------------------------ événements de TaCZ

    private static void tir(ServerPlayer p) {
        if (!SpartanConfig.TIRS_ENABLED.get()) {
            return;
        }
        Etat e = etat(p);
        e.stats.tir();
        long now = System.nanoTime();
        boolean neutre = p.isPassenger() || CheckManager.isInGrace(p.getUUID());
        e.dernierTirSec = !neutre && mouvementRecent(e, p, now) >= SpartanConfig.TIRS_COUP_SEC_DEGRES.get();
        e.dernierTirNanos = now;
    }

    private static void touche(ServerPlayer tireur, Player cible, boolean tete) {
        if (!SpartanConfig.TIRS_ENABLED.get() || cible == tireur) {
            return;
        }
        Etat e = etat(tireur);
        boolean sec = tete && e.dernierTirSec && System.nanoTime() - e.dernierTirNanos < 2_000_000_000L;
        double distance = tireur.distanceTo(cible);
        e.stats.touche(tete, distance, sec);
        StatsTirs s = e.stats;
        double distMin = SpartanConfig.TIRS_DISTANCE_MIN.get();
        int loin = s.touchesLoin(distMin);
        int tetes = s.tetesLoin(distMin);
        if (loin >= SpartanConfig.TIRS_TOUCHES_MIN.get() && tetes >= loin * SpartanConfig.TIRS_RATIO_TETE.get()) {
            instance.flag(tireur, String.format("%d tirs à la tête sur les %d dernières touches de joueurs à plus de %.0f blocs"
                            + " (%d %%, seuil %d %%) : aimbot ? Session : %s", tetes, loin, distMin, Math.round(100.0 * tetes / loin),
                    Math.round(100 * SpartanConfig.TIRS_RATIO_TETE.get()), s.resume()));
            s.oublierTouches();
            return;
        }
        int secs = s.coupsSecs();
        if (secs >= SpartanConfig.TIRS_COUPS_SECS_MAX.get()) {
            instance.flag(tireur, String.format("%d tirs à la tête juste après un mouvement de visée de plus de %.0f° en moins"
                            + " de 100 ms, sur les %d dernières touches de joueurs : aimbot ? Session : %s", secs,
                    SpartanConfig.TIRS_COUP_SEC_DEGRES.get(), s.touches(), s.resume()));
            s.oublierTouches();
        }
    }

    /** Résultat d'un tir chez TaCZ (TaczTirMixin, fil du serveur). */
    public static void resultat(Object tir, Object resultat) {
        if (!(resultat instanceof Enum<?> r) || !SpartanConfig.TIRS_ENABLED.get() || instance == null) {
            return;
        }
        String nom = r.name();
        boolean cadence = nom.equals("COOL_DOWN");
        if (!cadence && !nom.equals("NETWORK_FAIL")) {
            return;
        }
        Object tireur;
        try {
            if (champTireur == null) {
                champTireur = tir.getClass().getDeclaredField("shooter");
                champTireur.setAccessible(true);
            }
            tireur = champTireur.get(tir);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return;
        }
        if (ignore((Entity) tireur)) {
            return;
        }
        ServerPlayer p = (ServerPlayer) tireur;
        Etat e = etat(p);
        long fenetre = SpartanConfig.TIRS_REFUS_SECONDES.get() * 1000L;
        int n = e.stats.refus(System.currentTimeMillis(), fenetre);
        int seuil = cadence ? SpartanConfig.TIRS_REFUS_CADENCE.get() : SpartanConfig.TIRS_REFUS_HORLOGE.get();
        if (n >= seuil) {
            instance.flag(p, String.format("%d tirs refusés par TaCZ en %d s (dernier : %s) : %s", n, fenetre / 1000, nom,
                    cadence ? "cadence plus rapide que celle de l'arme (rapid fire) ?" : "horloge des tirs trafiquée ?"));
            e.stats.oublierRefus();
        }
    }

    /** Démarrage du serveur : branche les événements de TaCZ (une fois). */
    @SuppressWarnings("unchecked")
    public static void brancher() {
        if (branche || !ModList.get().isLoaded("tacz")) {
            return;
        }
        branche = true;
        try {
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            Class<? extends Event> tirEv = (Class<? extends Event>) Class.forName("com.tacz.guns.api.event.common.GunShootEvent");
            MethodHandle tireur = l.findVirtual(tirEv, "getShooter", MethodType.methodType(LivingEntity.class));
            MethodHandle coteTir = l.findVirtual(tirEv, "getLogicalSide", MethodType.methodType(LogicalSide.class));
            Class<? extends Event> toucheEv = (Class<? extends Event>) Class.forName(
                    "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Post");
            MethodHandle attaquant = l.findVirtual(toucheEv, "getAttacker", MethodType.methodType(LivingEntity.class));
            MethodHandle cible = l.findVirtual(toucheEv, "getHurtEntity", MethodType.methodType(Entity.class));
            MethodHandle tete = l.findVirtual(toucheEv, "isHeadShot", MethodType.methodType(boolean.class));
            MethodHandle coteTouche = l.findVirtual(toucheEv, "getLogicalSide", MethodType.methodType(LogicalSide.class));
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class<Event>) tirEv, ev -> {
                try {
                    if (coteTir.invoke(ev) == LogicalSide.SERVER && tireur.invoke(ev) instanceof ServerPlayer p && !ignore(p)) {
                        tir(p);
                    }
                } catch (Throwable t) {
                    // tir illisible : ignoré
                }
            });
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class<Event>) toucheEv, ev -> {
                try {
                    if (coteTouche.invoke(ev) == LogicalSide.SERVER && attaquant.invoke(ev) instanceof ServerPlayer p && !ignore(p)
                            && cible.invoke(ev) instanceof Player c && !ignore(c)) {
                        touche(p, c, (boolean) tete.invoke(ev));
                    }
                } catch (Throwable t) {
                    // touche illisible : ignorée
                }
            });
            Spartan.LOGGER.info("[Spartan] Tirs TaCZ analysés (taux de tirs à la tête, coups secs, tirs refusés par TaCZ).");
        } catch (Throwable t) {
            Spartan.LOGGER.warn("[Spartan] Tirs TaCZ non analysés : TaCZ non reconnu ({}).", t.toString());
        }
    }

    /** Déconnexion : une ligne dans tirs.log si le joueur a tiré. */
    @Override
    public void clear(UUID uuid) {
        Etat e = ETATS.remove(uuid);
        if (e != null && e.stats.tirs() > 0 && SpartanConfig.TIRS_JOURNAL.get()) {
            AlertManager.journal("tirs.log", e.nom + " (" + uuid + ") : " + e.stats.resume());
        }
    }

    /** Une grâce (téléportation…) ne remet pas les statistiques à zéro. */
    @Override
    public void onGrace(UUID uuid) {
    }
}
