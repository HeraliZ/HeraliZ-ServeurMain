package fr.heralisz.matchs.moteur.arenes;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Arenes;
import fr.heralisz.matchs.moteur.api.Carte;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.api.Pose;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bordure du monde des dimensions de matchs ({@link HeraliszMatchs#ARENES}, {@link HeraliszMatchs#ZOMBIES}).
 * <p><b>Pourquoi</b> (bug du 11/10, « /duel cree hors de la bordure, on meurt en boucle ») : la grille des instances part
 * de (0, 0) et s'etend vers +x / +z par cases de 512 blocs ({@link ArenesService#origine}), alors que rien ne reglait la
 * bordure de ces dimensions : elles avaient celle de la carte (en Forge 1.20.1, la bordure de l'overworld est recopiee
 * dans chaque dimension, meme centre et meme taille avec {@code coordinate_scale} 1 ; sous Mohist, chaque monde peut
 * avoir la sienne mais ces dimensions partent des reglages de la carte). La carte du depot a une bordure de 3 000 blocs
 * centree sur (0, 0) (x et z de -1 500 a 1 500) : des la 4e case (x = 1 536) l'arene est dehors (6 cartes posees d'avance
 * dans la dimension des arenes : Nuketown tombe sur une des cases 0 a 5, et chaque duel en plus va au-dela), le joueur y
 * prend les degats de la bordure, est elimine, reapparait dans la meme arene, et ainsi de suite.
 * <p><b>Correction</b> :
 * <ul>
 *     <li>ces deux dimensions ont leur propre bordure, centree sur (0, 0) et aussi grande que le permet le jeu
 *     ({@link #TAILLE}, celle d'un monde neuf), independante de la carte : imposee au demarrage, puis verifiee chaque
 *     seconde (un changement de la bordure de la carte, recopie par le jeu, est defait) et envoyee aux joueurs qui y
 *     entrent (le jeu vanilla leur enverrait celle de l'overworld) ;</li>
 *     <li>une nouvelle instance n'est posee que si toute son emprise est dans la bordure, a {@link #MARGE} blocs au moins
 *     du bord ({@link ArenesService#reserver}) ; une instance deja posee qui serait dehors n'est plus prise ;</li>
 *     <li>filet : dans ces dimensions, les degats de la vraie bordure sont annules (jamais de mort en boucle), et un
 *     joueur hors de la bordure est ramene par une vraie teleportation (regle de Spartan) dans son instance, ou rendu
 *     a sa position d'origine (Coffre), ou a defaut au point d'apparition de la carte. Les degats de zone volontaires
 *     (mort subite du ClashWar, source « hors bordure » DANS la bordure) ne sont pas touches.</li>
 * </ul>
 * Aucune generation de tronçon : seulement des comparaisons de coordonnees. Fil serveur.
 */
public final class BordureArenes {
    private BordureArenes() {
    }

    /** Taille de la bordure des dimensions de matchs : la plus grande du jeu (59 999 968 blocs, monde neuf). */
    public static final double TAILLE = 5.9999968E7;
    /** Distance minimale (blocs) entre l'emprise d'une nouvelle instance et le bord de la bordure. */
    public static final int MARGE = 256;
    /** Dimensions concernees. */
    private static final List<ResourceKey<Level>> DIMENSIONS = List.of(HeraliszMatchs.ARENES, HeraliszMatchs.ZOMBIES);
    /** Tampon des joueurs a secourir (on ne teleporte pas en parcourant la liste des joueurs du niveau). */
    private static final List<ServerPlayer> TAMPON = new ArrayList<>();

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BordureArenes::attaque);
        MinecraftForge.EVENT_BUS.addListener(BordureArenes::changementDimension);
        MinecraftForge.EVENT_BUS.addListener(BordureArenes::connexion);
        MinecraftForge.EVENT_BUS.addListener(BordureArenes::reapparition);
    }

    // ------------------------------------------------------------------ bordure propre
    /** Demarrage du serveur (avant la reprise des arenes) : bordure propre des deux dimensions. */
    public static void demarrage(MinecraftServer s) {
        for (ResourceKey<Level> k : DIMENSIONS) {
            ServerLevel l = s.getLevel(k);
            if (l != null) imposer(l);
        }
    }

    /** Toutes les secondes : bordure remise si elle a change, joueurs hors de la bordure secourus. */
    public static void tick(MinecraftServer s) {
        for (ResourceKey<Level> k : DIMENSIONS) {
            ServerLevel l = s.getLevel(k);
            if (l == null) continue;
            imposer(l);
            secourir(s, l);
        }
    }

    /**
     * Impose la bordure propre (centre 0, 0 ; taille {@link #TAILLE}) au niveau s'il s'agit d'une dimension de matchs et
     * qu'elle n'y est pas deja ; la renvoie alors aux joueurs presents. Rend vrai si elle a ete changee.
     */
    public static boolean imposer(ServerLevel l) {
        if (!Arenes.estArene(l)) return false;
        WorldBorder b = l.getWorldBorder();
        if (b.getCenterX() == 0.0 && b.getCenterZ() == 0.0 && b.getSize() >= TAILLE - 1.0) return false;
        HeraliszMatchs.LOG.info("[matchs] bordure de {} : centre {} {}, taille {} -> centre 0 0, taille {} (propre aux matchs, independante de la carte)",
                l.dimension().location(), (long) b.getCenterX(), (long) b.getCenterZ(), (long) b.getSize(), (long) TAILLE);
        b.setCenter(0.0, 0.0);
        b.setSize(TAILLE);
        List<ServerPlayer> joueurs = l.players();
        if (!joueurs.isEmpty()) {
            ClientboundInitializeBorderPacket p = new ClientboundInitializeBorderPacket(b);
            for (int i = 0; i < joueurs.size(); i++) joueurs.get(i).connection.send(p);
        }
        return true;
    }

    // ------------------------------------------------------------------ emprise des instances
    /**
     * Emprise au sol (coordonnees absolues) d'une instance de la carte posee a cette origine : taille et bornes de la
     * carte, blocs de la structure et de ses annexes ({@code st} peut etre null : taille et bornes seulement).
     */
    public static AABB emprise(Carte c, @Nullable Structure st, BlockPos o) {
        double x0 = Math.min(0, c.bornes().minX), z0 = Math.min(0, c.bornes().minZ);
        double x1 = Math.max(c.taille().getX(), c.bornes().maxX), z1 = Math.max(c.taille().getZ(), c.bornes().maxZ);
        if (st != null && !st.blocs.isEmpty()) {
            x0 = Math.min(x0, st.minX);
            z0 = Math.min(z0, st.minZ);
            x1 = Math.max(x1, st.maxX + 1);
            z1 = Math.max(z1, st.maxZ + 1);
        }
        return new AABB(o.getX() + x0, o.getY(), o.getZ() + z0, o.getX() + x1, o.getY() + 1, o.getZ() + z1);
    }

    /** Vrai si la zone (absolue) est entierement dans la bordure du niveau, a au moins {@code marge} blocs du bord. */
    public static boolean dansBordure(ServerLevel l, AABB zone, double marge) {
        WorldBorder b = l.getWorldBorder();
        return zone.minX - marge >= b.getMinX() && zone.maxX + marge <= b.getMaxX() && zone.minZ - marge >= b.getMinZ()
                && zone.maxZ + marge <= b.getMaxZ();
    }

    /** Description courte de la bordure du niveau, pour le journal. */
    public static String decrire(ServerLevel l) {
        WorldBorder b = l.getWorldBorder();
        return String.format(Locale.ROOT, "centre %.0f %.0f, taille %.0f", b.getCenterX(), b.getCenterZ(), b.getSize());
    }

    // ------------------------------------------------------------------ filet
    /** Degats de la vraie bordure dans une dimension de matchs : annules (le joueur est ramene par {@link #tick}). */
    private static void attaque(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Arenes.estArene(p.level())) return;
        if (!e.getSource().is(DamageTypes.OUTSIDE_BORDER)) return;
        // la mort subite du ClashWar utilise la meme source sur des joueurs DANS la bordure : elle reste
        if (p.level().getWorldBorder().isWithinBounds(p.getBoundingBox())) return;
        e.setCanceled(true);
    }

    /** Entree dans une dimension de matchs : la bordure de CETTE dimension (le jeu vanilla envoie celle de l'overworld). */
    private static void changementDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) envoyer(p);
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) envoyer(p);
    }

    private static void reapparition(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) envoyer(p);
    }

    private static void envoyer(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        if (!Arenes.estArene(l)) return;
        imposer(l);
        p.connection.send(new ClientboundInitializeBorderPacket(l.getWorldBorder()));
    }

    /** Joueurs (ni spectateurs ni createurs, que la bordure ne blesse pas) hors de la bordure du niveau : ramenes. */
    private static void secourir(MinecraftServer s, ServerLevel l) {
        List<ServerPlayer> joueurs = l.players();
        if (joueurs.isEmpty()) return;
        WorldBorder b = l.getWorldBorder();
        TAMPON.clear();
        for (int i = 0; i < joueurs.size(); i++) {
            ServerPlayer p = joueurs.get(i);
            if (p.isSpectator() || p.isCreative() || b.isWithinBounds(p.getBoundingBox())) continue;
            TAMPON.add(p);
        }
        for (int i = 0; i < TAMPON.size(); i++) {
            ServerPlayer p = TAMPON.get(i);
            try {
                secourir(s, l, p);
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[matchs] secours de {} hors de la bordure en erreur", p.getGameProfile().getName(), ex);
            }
        }
        TAMPON.clear();
    }

    private static void secourir(MinecraftServer s, ServerLevel l, ServerPlayer p) {
        String nom = p.getGameProfile().getName();
        String ou = String.format(Locale.ROOT, "%s %.0f %.0f %.0f (%s)", l.dimension().location(), p.getX(), p.getY(), p.getZ(), decrire(l));
        Partie partie = MatchApi.partieDe(p);
        Instance inst = partie == null ? null : partie.instance();
        if (partie != null && inst != null && partie.phase() != Partie.Phase.TERMINEE) {
            if (dansBordure(inst.niveau(), inst.bornes(), 0)) {
                // instance encore en pose : le mode l'y teleportera quand elle sera prete (aucun degat de bordure d'ici la)
                if (!inst.prete()) return;
                // ramene dans son instance (le mode reprend la main : bornes, elimination...)
                Pose pose = pointSur(partie, inst, p);
                HeraliszMatchs.LOG.warn("[matchs] {} hors de la bordure en {} : ramene dans l'instance {}", nom, ou, inst.id());
                p.fallDistance = 0;
                p.clearFire();
                pose.teleporter(p, inst.niveau());
                return;
            }
            if (partie.phase() != Partie.Phase.FIN) {
                // instance inutilisable : la partie est annulee ; sa fin rend a chacun son Coffre (inventaire, position)
                HeraliszMatchs.LOG.error("[matchs] {} hors de la bordure en {} : l'instance {} est hors de la bordure, partie {} annulee", nom, ou,
                        inst.id(), partie.id());
                partie.terminer(null);
            }
            return;   // fin de partie : elle le rendra (aucun degat de bordure en attendant)
        }
        HeraliszMatchs.LOG.warn("[matchs] {} hors de la bordure en {}, hors de toute instance : renvoye", nom, ou);
        p.fallDistance = 0;
        p.clearFire();
        // depot en attente : inventaire et position d'origine rendus (et partie quittee)
        if (Coffre.aUnDepot(p.getUUID()) && Coffre.rendre(p)) return;
        if (partie != null) MatchApi.quitter(p);
        ServerLevel ow = s.overworld();
        BlockPos sp = ow.getSharedSpawnPos();
        p.teleportTo(ow, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, p.getYRot(), 0f);
    }

    /** Point sur de l'instance pour ce joueur : apparition de son equipe, sinon apparition commune, spectateur, centre. */
    private static Pose pointSur(Partie partie, Instance inst, ServerPlayer p) {
        Equipe e = partie.equipeDe(p.getUUID());
        if (e != null) {
            Pose q = inst.point("spawn_" + e.id());
            if (q != null) return q;
        }
        for (String nom : new String[]{"spawn", "spawn_joueurs", "spawn_a", "spectateur"}) {
            Pose q = inst.point(nom);
            if (q != null) return q;
        }
        AABB b = inst.bornes();
        return new Pose((b.minX + b.maxX) / 2, b.minY + 1, (b.minZ + b.maxZ) / 2, 0f);
    }
}
