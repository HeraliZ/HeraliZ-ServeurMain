package fr.heralisz.matchs.duels.killcam;

import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.Partie;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enregistreur en anneau d'une partie (fil serveur) : pour chaque joueur de la partie (et chaque entite suivie, un bot
 * d'entrainement par exemple), les {@link #CAPACITE} dernieres images a 20 Hz (position, rotations, posture, objet
 * tenu) et les derniers tirs (evenement de tir TaCZ). Aucune allocation par tick : les tableaux de chaque piste sont
 * crees une fois ; un objet tenu n'est copie que lorsqu'il change.
 * <p>Obtenu par {@link Killcam#enregistreur(Partie)} ; s'arrete seul quand la partie passe en
 * {@link Partie.Phase#TERMINEE} (ou par {@link #arreter()}).
 */
public final class Enregistreur {
    /** Images gardees : 5 s a 20 Hz. */
    public static final int CAPACITE = 100;
    /** Tirs gardes par piste. */
    public static final int TIRS = 48;

    @Nullable
    private final Partie partie;
    private final Map<UUID, Piste> pistes = new HashMap<>();
    private final List<LivingEntity> suivis = new ArrayList<>();
    /** Numero de la derniere image capturee (-1 = aucune). */
    private long courant = -1;
    /** Tick serveur de la derniere capture (une capture de plus dans le meme tick remplace l'image). */
    private int tickServeurCapture = Integer.MIN_VALUE;
    private boolean arrete;
    @Nullable
    private Clip derniere;

    Enregistreur(@Nullable Partie partie) {
        this.partie = partie;
    }

    /** Partie enregistree (null pour un enregistreur d'essai). */
    @Nullable
    public Partie partie() {
        return partie;
    }

    /** Suit aussi cette entite (bot d'entrainement, mannequin) ; les joueurs de la partie sont suivis d'office. */
    public void suivre(LivingEntity entite) {
        if (!suivis.contains(entite)) suivis.add(entite);
    }

    /** Oublie la piste de cette entite (et cesse de la suivre si c'etait une entite suivie). */
    public void oublier(UUID uuid) {
        suivis.removeIf(e -> e.getUUID().equals(uuid));
        pistes.remove(uuid);
    }

    /** Arrete l'enregistrement (retire au tick suivant). */
    public void arreter() {
        arrete = true;
    }

    public boolean actif() {
        return !arrete && (partie == null || partie.phase() != Partie.Phase.TERMINEE);
    }

    /** Derniere killcam extraite par {@link Killcam#surElimination} (pour la rejouer en fin de manche), ou null. */
    @Nullable
    public Clip derniere() {
        return derniere;
    }

    void derniere(@Nullable Clip c) {
        derniere = c;
    }

    /** Oublie la derniere killcam (nouvelle manche : rien a rejouer tant que personne n'est elimine). */
    public void oublierDerniere() {
        derniere = null;
    }

    /** Nombre d'images capturees depuis le debut. */
    public long images() {
        return courant + 1;
    }

    // ------------------------------------------------------------------ capture
    /**
     * Capture une image de tous les participants. Appele a la fin de chaque tick serveur, et juste avant une
     * extraction (l'image du tick de la mort) : dans ce cas, la capture de fin de tick est sautee.
     */
    void capturer(MinecraftServer serveur) {
        int tickServeur = serveur.getTickCount();
        if (tickServeur == tickServeurCapture) return;
        tickServeurCapture = tickServeur;
        long t = ++courant;
        if (partie != null) {
            for (Equipe e : partie.equipes()) {
                for (UUID u : e.joueurs()) {
                    ServerPlayer p = serveur.getPlayerList().getPlayer(u);
                    if (p != null) capturer(p, t, couleur(e));
                }
            }
        }
        for (int i = suivis.size() - 1; i >= 0; i--) {
            LivingEntity e = suivis.get(i);
            if (e.isRemoved()) {
                suivis.remove(i);
                continue;
            }
            capturer(e, t, 0xBFC5CF);
        }
    }

    private static int couleur(Equipe e) {
        return e.couleur() & 0xFFFFFF;
    }

    private void capturer(LivingEntity e, long t, int couleur) {
        Piste p = pistes.get(e.getUUID());
        if (p == null) {
            p = new Piste(e.getUUID(), nom(e), e instanceof Player, couleur);
            pistes.put(e.getUUID(), p);
        }
        p.couleur = couleur;
        int drapeaux = Clip.PRESENT;
        if (e.isCrouching()) drapeaux |= Clip.ACCROUPI;
        if (e.isSprinting()) drapeaux |= Clip.SPRINT;
        if (e.getPose() == Pose.SWIMMING) drapeaux |= Clip.NAGE;
        if (e.hurtTime > 0) drapeaux |= Clip.BLESSE;
        if (e.swinging) drapeaux |= Clip.FRAPPE;
        if (e.isDeadOrDying()) drapeaux |= Clip.MORT;
        if (vise(e)) drapeaux |= Clip.VISE;
        p.ecrire(t, e.getX(), e.getY(), e.getZ(), e.yBodyRot, e.getYHeadRot(), e.getXRot(), drapeaux, e.getMainHandItem());
    }

    // ------------------------------------------------------------------ visee TaCZ (reflexion : TaCZ facultatif)
    private static boolean viseCherche;
    @Nullable
    private static java.lang.reflect.Method operateur, enVisee;

    /** Vrai si l'entite vise avec son arme TaCZ ({@code IGunOperator.getSynIsAiming}) ; faux sans TaCZ ou en cas d'erreur. */
    private static boolean vise(LivingEntity e) {
        if (!viseCherche) {
            viseCherche = true;
            try {
                if (net.minecraftforge.fml.ModList.get() != null && net.minecraftforge.fml.ModList.get().isLoaded("tacz")) {
                    Class<?> c = Class.forName("com.tacz.guns.api.entity.IGunOperator");
                    operateur = c.getMethod("fromLivingEntity", LivingEntity.class);
                    enVisee = c.getMethod("getSynIsAiming");
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                operateur = null;
                enVisee = null;
            }
        }
        if (operateur == null || enVisee == null) return false;
        try {
            Object op = operateur.invoke(null, e);
            return op != null && Boolean.TRUE.equals(enVisee.invoke(op));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            operateur = null;
            return false;
        }
    }

    private static String nom(LivingEntity e) {
        return e instanceof Player pl ? pl.getGameProfile().getName() : e.getName().getString();
    }

    /** Enregistre un tir de cette entite (appele par l'ecouteur TaCZ, fil serveur). */
    void tir(LivingEntity tireur) {
        Piste p = pistes.get(tireur.getUUID());
        if (p == null) return;
        // le tir a lieu pendant le tick : il appartient a l'image qui sera capturee en fin de tick
        p.tir(courant + 1, tireur.getYHeadRot(), tireur.getXRot());
    }

    // ------------------------------------------------------------------ essai (enregistrement factice)
    /** Ecrit une image a la main (enregistrement factice du banc / de {@code /killcam essai}). */
    void injecter(UUID uuid, String nom, boolean joueur, int couleur, long t, double x, double y, double z, float corps, float tete,
                  float tangage, int drapeaux, ItemStack objet) {
        Piste p = pistes.computeIfAbsent(uuid, u -> new Piste(u, nom, joueur, couleur));
        p.ecrire(t, x, y, z, corps, tete, tangage, drapeaux, objet);
        if (t > courant) courant = t;
    }

    void injecterTir(UUID uuid, long t, float lacet, float tangage) {
        Piste p = pistes.get(uuid);
        if (p != null) p.tir(t, lacet, tangage);
    }

    // ------------------------------------------------------------------ extraction
    /**
     * Extrait les {@code images} dernieres images autour d'une elimination : tueur et victime d'abord, puis les autres
     * pistes presentes a moins de {@code rayon} blocs du tueur (au plus {@link Clip#MAX_PARTICIPANTS}). Null si le tueur
     * ou la victime n'a pas de piste.
     *
     * @param tenueTueur entite vivante du tueur pour sa tenue (armure, main gauche) ; null = aucune
     */
    @Nullable
    Clip extraire(UUID victime, UUID tueur, int images, double rayon, @Nullable LivingEntity tenueTueur,
                  @Nullable LivingEntity tenueVictime, byte raison) {
        Piste pt = pistes.get(tueur), pv = pistes.get(victime);
        if (pt == null || pv == null || courant < 0) return null;
        int n = (int) Math.min(images, Math.min(courant + 1, CAPACITE));
        long debut = courant - n + 1;
        int dernierT = pt.slotValide(courant) ? pt.slot(courant) : -1;
        if (dernierT < 0) {
            // le tueur n'est plus la a la derniere image : sa derniere position connue sert d'origine
            for (long t = courant; t >= debut && dernierT < 0; t--) if (pt.slotValide(t)) dernierT = pt.slot(t);
            if (dernierT < 0) return null;
        }
        Clip c = new Clip();
        c.raison = raison;
        c.images = n;
        c.ox = pt.x[dernierT];
        c.oy = pt.y[dernierT];
        c.oz = pt.z[dernierT];
        List<Piste> choisies = new ArrayList<>();
        choisies.add(pt);
        if (pv != pt) choisies.add(pv);
        double r2 = rayon * rayon;
        for (Piste p : pistes.values()) {
            if (choisies.size() >= Clip.MAX_PARTICIPANTS) break;
            if (choisies.contains(p)) continue;
            boolean proche = false;
            for (long t = debut; t <= courant && !proche; t++) {
                if (!p.slotValide(t)) continue;
                int s = p.slot(t);
                double dx = p.x[s] - c.ox, dy = p.y[s] - c.oy, dz = p.z[s] - c.oz;
                proche = dx * dx + dy * dy + dz * dz <= r2;
            }
            if (proche) choisies.add(p);
        }
        Map<ItemStack, Integer> palette = new IdentityHashMap<>();
        for (int k = 0; k < choisies.size(); k++) {
            Piste p = choisies.get(k);
            Clip.Participant cp = new Clip.Participant(p.uuid, p.nom, p.joueur, p.couleur, n);
            for (int i = 0; i < n; i++) {
                long t = debut + i;
                if (!p.slotValide(t)) {
                    cp.objet[i] = -1;
                    continue;
                }
                int s = p.slot(t);
                cp.x[i] = p.x[s];
                cp.y[i] = p.y[s];
                cp.z[i] = p.z[s];
                cp.corps[i] = p.corps[s];
                cp.tete[i] = p.tete[s];
                cp.tangage[i] = p.tangage[s];
                cp.drapeaux[i] = p.drapeaux[s];
                cp.objet[i] = (byte) indice(c, palette, p.objet[s]);
            }
            for (int ti = 0; ti < p.nbTirs; ti++) {
                int s = (p.tetetir - 1 - ti + TIRS) % TIRS;
                long tt = p.tirT[s];
                if (tt < debut || tt > courant) continue;
                if (c.tirs.size() < Clip.MAX_TIRS) c.tirs.add(new Clip.Tir(k, tt - debut, p.tirLacet[s], p.tirTangage[s]));
            }
            c.participants.add(cp);
        }
        c.tirs.sort((a, b) -> Float.compare(a.temps(), b.temps()));
        c.tueur = 0;
        c.victime = pv == pt ? 0 : 1;
        Clip.Participant ct = c.participants.get(c.tueur), cv = c.participants.get(c.victime);
        if (tenueTueur != null) tenue(c, palette, ct, tenueTueur);
        if (tenueVictime != null) tenue(c, palette, cv, tenueVictime);
        // arme : objet tenu par le tueur a sa derniere image presente
        for (int i = n - 1; i >= 0; i--) {
            if (ct.present(i)) {
                c.arme = ct.objet[i];
                break;
            }
        }
        int iv = n - 1, it = n - 1;
        while (iv > 0 && !cv.present(iv)) iv--;
        while (it > 0 && !ct.present(it)) it--;
        double dx = cv.x[iv] - ct.x[it], dy = cv.y[iv] - ct.y[it], dz = cv.z[iv] - ct.z[it];
        c.distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return c;
    }

    private static void tenue(Clip c, Map<ItemStack, Integer> palette, Clip.Participant cp, LivingEntity e) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
        for (int i = 0; i < slots.length; i++) {
            ItemStack s = e.getItemBySlot(slots[i]);
            cp.tenue[i] = s.isEmpty() ? -1 : indice(c, palette, s.copy());
        }
    }

    private static int indice(Clip c, Map<ItemStack, Integer> palette, @Nullable ItemStack s) {
        if (s == null || s.isEmpty()) return -1;
        Integer i = palette.get(s);
        if (i != null) return i;
        if (c.objets.size() >= Clip.MAX_OBJETS) return -1;
        c.objets.add(s);
        palette.put(s, c.objets.size() - 1);
        return c.objets.size() - 1;
    }

    /** Pistes connues (lecture seule, journal). */
    public int nbPistes() {
        return pistes.size();
    }

    // ------------------------------------------------------------------ piste
    /** Anneau d'une entite : l'image du tick t est dans la case t % CAPACITE (valide si temps[case] == t). */
    private static final class Piste {
        final UUID uuid;
        final String nom;
        final boolean joueur;
        int couleur;
        final long[] temps = new long[CAPACITE];
        final double[] x = new double[CAPACITE], y = new double[CAPACITE], z = new double[CAPACITE];
        final float[] corps = new float[CAPACITE], tete = new float[CAPACITE], tangage = new float[CAPACITE];
        final byte[] drapeaux = new byte[CAPACITE];
        final ItemStack[] objet = new ItemStack[CAPACITE];
        @Nullable
        ItemStack dernierObjet;
        final long[] tirT = new long[TIRS];
        final float[] tirLacet = new float[TIRS], tirTangage = new float[TIRS];
        int tetetir, nbTirs;

        Piste(UUID uuid, String nom, boolean joueur, int couleur) {
            this.uuid = uuid;
            this.nom = nom;
            this.joueur = joueur;
            this.couleur = couleur;
            java.util.Arrays.fill(temps, Long.MIN_VALUE);
        }

        int slot(long t) {
            return (int) Math.floorMod(t, (long) CAPACITE);
        }

        boolean slotValide(long t) {
            return t >= 0 && temps[slot(t)] == t;
        }

        void ecrire(long t, double px, double py, double pz, float pc, float ptete, float ptang, int dr, ItemStack tenu) {
            int s = slot(t);
            temps[s] = t;
            x[s] = px;
            y[s] = py;
            z[s] = pz;
            corps[s] = pc;
            tete[s] = ptete;
            tangage[s] = ptang;
            drapeaux[s] = (byte) dr;
            if (tenu.isEmpty()) {
                dernierObjet = null;
            } else if (dernierObjet == null || !memeObjet(dernierObjet, tenu)) {
                dernierObjet = tenu.copy();
            }
            objet[s] = dernierObjet;
        }

        /** Meme objet pour l'affichage : meme item et meme arme TaCZ (les munitions, qui changent a chaque tir, sont ignorees). */
        private static boolean memeObjet(ItemStack a, ItemStack b) {
            if (a.getItem() != b.getItem()) return false;
            CompoundTag ta = a.getTag(), tb = b.getTag();
            String ga = ta == null ? "" : ta.getString("GunId"), gb = tb == null ? "" : tb.getString("GunId");
            return ga.equals(gb);
        }

        void tir(long t, float lacet, float tang) {
            tirT[tetetir] = t;
            tirLacet[tetetir] = lacet;
            tirTangage[tetetir] = tang;
            tetetir = (tetetir + 1) % TIRS;
            if (nbTirs < TIRS) nbTirs++;
        }
    }
}
