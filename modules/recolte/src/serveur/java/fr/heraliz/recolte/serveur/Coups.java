package fr.heraliz.recolte.serveur;

import fr.heraliz.recolte.objets.ObjetsRecolte;
import fr.heraliz.recolte.objets.OutilRecolte;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que le serveur juge à chaque coup d'outil sur une ressource (A_FAIRE §6, « Coups ») : joueur vivant et pas
 * spectateur, bon outil en main (sinon un message au-dessus de la barre), portée de la mêlée, cadence par joueur ; puis
 * l'usure de l'outil et la matière donnée (dans l'inventaire, au sol à ses pieds s'il est plein).
 * <p>Cadence comptée comme les coups de couteau de HeraliZ Tactique ({@code CouteauxServeur}) : en temps réel (un serveur
 * qui rame ne refuse pas les coups d'un joueur honnête), à {@link #TOLERANCE_MS} près pour le décalage du réseau ; le coup
 * suivant est compté depuis l'heure où celui-ci était dû, si bien qu'un jeu modifié ne frappe jamais plus vite. Une
 * entrée par joueur qui a frappé, oubliée à sa déconnexion. Fil du serveur seulement.
 */
public final class Coups {
    private Coups() {
    }

    /** Avance pardonnée sur la cadence (ms) : deux coups honnêtes peuvent arriver plus rapprochés qu'envoyés. */
    private static final long TOLERANCE_MS = 100L;
    /** Marge des calculs en fraction de travail (1/12 + ... + 1/12 ne fait pas toujours 1 en virgule flottante). */
    public static final double EPSILON = 1.0E-6;
    /** Travail fini (toute la matière donnée). */
    public static final double FINI = 1.0 - EPSILON;

    /** Joueur -> heure (ms, horloge monotone) où son coup suivant est dû. */
    private static final Map<UUID, long[]> PRET = new HashMap<>();

    /** Déconnexion : sa cadence est oubliée. */
    static void oublier(UUID joueur) {
        PRET.remove(joueur);
    }

    /** Arrêt du serveur. */
    static void vider() {
        PRET.clear();
    }

    /**
     * Juge un coup porté par {@code joueur} sur une ressource : arbre ({@code arbre} vrai) ou gisement. Rend l'outil tenu si
     * le coup compte (sa cadence est alors prise), null sinon : rien ne se passe, sauf avec un mauvais outil, où le joueur
     * lit au-dessus de la barre quel outil il faut ({@code heraliz_recolte.refus.outil_arbre} ou {@code outil_gisement}).
     */
    @Nullable
    public static OutilRecolte juger(ServerPlayer joueur, Entity cible, boolean arbre) {
        if (joueur instanceof FakePlayer || !joueur.isAlive() || joueur.isSpectator()) {
            return null;
        }
        ItemStack main = joueur.getMainHandItem();
        if (!(main.getItem() instanceof OutilRecolte outil) || !(arbre ? outil.coupeLesArbres() : outil.casseLesGisements())) {
            joueur.displayClientMessage(arbre
                    ? Component.translatableWithFallback("heraliz_recolte.refus.outil_arbre", "Il faut une hache pour abattre cet arbre.")
                    : Component.translatableWithFallback("heraliz_recolte.refus.outil_gisement", "Il faut une pioche pour casser ce gisement."), true);
            return null;
        }
        Reglages r = Reglages.get();
        if (distance(joueur, cible) > r.portee + r.margePortee) {
            return null;
        }
        long maintenant = System.nanoTime() / 1_000_000L;
        long[] pret = PRET.computeIfAbsent(joueur.getUUID(), u -> new long[]{Long.MIN_VALUE / 4});
        if (maintenant < pret[0] - TOLERANCE_MS) {
            return null;
        }
        // la cadence repart de l'heure où ce coup était dû (jamais d'avance gagnée), ou de maintenant après une pause
        pret[0] = Math.max(maintenant, pret[0]) + r.cadenceMs;
        return outil;
    }

    /** Usure d'1 de l'outil tenu (rien en créatif) ; usé jusqu'au bout, il casse (son et éclats de Minecraft). */
    public static void user(ServerPlayer joueur) {
        ItemStack main = joueur.getMainHandItem();
        if (main.getItem() instanceof OutilRecolte) {
            main.hurtAndBreak(1, joueur, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
    }

    /** Matière due pour une avance (0 à 1) sur un total : arrondie au-dessous, jamais plus que le total. */
    public static int part(double avance, int total) {
        return Mth.clamp((int) Math.floor(avance * total + EPSILON), 0, total);
    }

    /** Matière récoltée sur une sorte de ressource ({@link Reglages#ARBRE}...). */
    public static Item matiere(int sorte) {
        return switch (sorte) {
            case Reglages.PIERRE -> ObjetsRecolte.PIERRE_BRUTE.get();
            case Reglages.METAL -> ObjetsRecolte.MINERAI_FER.get();
            case Reglages.RARE -> ObjetsRecolte.MINERAI_RARE.get();
            default -> ObjetsRecolte.BUCHE.get();
        };
    }

    /**
     * Donne {@code n} {@code objet} au joueur : dans son inventaire, au sol à ses pieds s'il est plein ; « +2 Bûche »
     * au-dessus de sa barre.
     */
    public static void donner(ServerPlayer joueur, Item objet, int n) {
        if (n <= 0) {
            return;
        }
        int pile = Math.max(1, new ItemStack(objet).getMaxStackSize());
        for (int reste = n; reste > 0; ) {
            int k = Math.min(reste, pile);
            ItemHandlerHelper.giveItemToPlayer(joueur, new ItemStack(objet, k));
            reste -= k;
        }
        joueur.displayClientMessage(Component.translatableWithFallback("heraliz_recolte.recu", "+%1$s %2$s", n, objet.getDescription()), true);
    }

    /** Distance des yeux du joueur au point le plus proche de la boîte de la cible (0 s'ils sont dedans), comme la mêlée. */
    public static double distance(Entity joueur, Entity cible) {
        Vec3 oeil = joueur.getEyePosition();
        return oeil.distanceTo(plusProche(oeil, cible.getBoundingBox()));
    }

    /** Point de la ressource que le joueur vise (là où partent les éclats), sinon son point le plus proche. */
    public static Vec3 pointTouche(ServerPlayer joueur, Entity cible) {
        Vec3 oeil = joueur.getEyePosition();
        AABB boite = cible.getBoundingBox();
        return boite.clip(oeil, oeil.add(joueur.getLookAngle().scale(8.0))).orElseGet(() -> plusProche(oeil, boite));
    }

    private static Vec3 plusProche(Vec3 p, AABB b) {
        return new Vec3(Mth.clamp(p.x, b.minX, b.maxX), Mth.clamp(p.y, b.minY, b.maxY), Mth.clamp(p.z, b.minZ, b.maxZ));
    }

    /** Lacet (degrés, comme {@code getYRot}) qui part du joueur et traverse la cible : un arbre tombe loin de lui. */
    public static float lacetLoinDe(Entity joueur, Entity cible) {
        double dx = cible.getX() - joueur.getX(), dz = cible.getZ() - joueur.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return joueur.getYRot();
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
