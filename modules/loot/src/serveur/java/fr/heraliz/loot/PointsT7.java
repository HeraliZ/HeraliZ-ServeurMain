package fr.heraliz.loot;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Points d'apparition des coffres T7 (un fichier par dimension). Un coffre T7 n'est jamais là en permanence : il
 * apparaît (animation), reste un temps (présence), puis disparaît — plus tôt s'il a été vidé.
 * <ul><li>mode « événement » : il n'apparaît que sur déclenchement (lecteur de carte, mort d'un boss, script, plugin) ;
 * un nouveau déclenchement est refusé tant que le coffre est là ;</li>
 * <li>mode « auto » : il réapparaît tout seul, un délai tiré entre min et max après sa disparition (quand le secteur
 * est chargé, donc quand des joueurs sont près).</li></ul>
 * API pour les autres mods : {@link #declencher(ServerLevel, String)}, {@link #apparaitre}.
 */
public final class PointsT7 extends SavedData {
    private static final String FICHIER = "heraliz_loot_points_t7";

    public static final class Point {
        public String nom, type;
        public BlockPos pos;
        public Direction face;
        public boolean auto;
        /** Durées en ticks. */
        public int presence, delaiMin, delaiMax;
        long prochain;
        boolean present;

        CompoundTag sauver() {
            CompoundTag t = new CompoundTag();
            t.putString("Nom", nom);
            t.putString("Type", type);
            t.putLong("Pos", pos.asLong());
            t.putString("Face", face.getName());
            t.putBoolean("Auto", auto);
            t.putInt("Presence", presence);
            t.putInt("DelaiMin", delaiMin);
            t.putInt("DelaiMax", delaiMax);
            t.putLong("Prochain", prochain);
            t.putBoolean("Present", present);
            return t;
        }

        static Point lire(CompoundTag t) {
            Point p = new Point();
            p.nom = t.getString("Nom");
            p.type = t.getString("Type");
            p.pos = BlockPos.of(t.getLong("Pos"));
            Direction d = Direction.byName(t.getString("Face"));
            p.face = d == null || d.getAxis().isVertical() ? Direction.NORTH : d;
            p.auto = t.getBoolean("Auto");
            p.presence = t.getInt("Presence");
            p.delaiMin = t.getInt("DelaiMin");
            p.delaiMax = t.getInt("DelaiMax");
            p.prochain = t.getLong("Prochain");
            p.present = t.getBoolean("Present");
            return p;
        }

        public boolean present() {
            return present;
        }
    }

    final Map<String, Point> points = new LinkedHashMap<>();

    public static PointsT7 get(ServerLevel l) {
        return l.getDataStorage().computeIfAbsent(PointsT7::charger, PointsT7::new, FICHIER);
    }

    static PointsT7 charger(CompoundTag t) {
        PointsT7 p = new PointsT7();
        for (Tag e : t.getList("Points", Tag.TAG_COMPOUND)) {
            Point q = Point.lire((CompoundTag) e);
            p.points.put(q.nom, q);
        }
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        points.values().forEach(p -> l.add(p.sauver()));
        t.put("Points", l);
        return t;
    }

    /** Présence par défaut : 10 min pour un largage, 5 min pour les autres. */
    public static int presenceDefaut(String type) {
        return type.equals("t7_largage") ? 12000 : 6000;
    }

    /** Nom complet d'un type (« largage » ou « t7_largage »), ou null s'il est inconnu. */
    public static String type(String nom) {
        String n = nom.startsWith("t7_") ? nom : "t7_" + nom;
        return TypeCoffre.TOUS.containsKey(n) ? n : null;
    }

    // ------------------------------------------------------------------------------------------ apparitions
    /**
     * Pose un coffre T7 qui apparaît (animation) en pos, face avant vers face, pour presence ticks. Refusé si la place
     * est prise par un bloc solide. point : nom du point d'origine (ou "").
     */
    public static boolean apparaitre(ServerLevel l, BlockPos pos, Direction face, String type, int presence, String point) {
        return apparaitre(l, pos, face, type, presence, point, "");
    }

    /**
     * Comme {@link #apparaitre(ServerLevel, BlockPos, Direction, String, int, String)}, pour le coffre d'un événement de
     * HeraliZ Événements ({@code evenement} : « helico#12 », ou "") : à sa première ouverture, il reçoit l'objet
     * d'événement à revendre et prévient l'événement (EvenementsApi, par réflexion). Appelé par réflexion : ne pas renommer.
     */
    public static boolean apparaitre(ServerLevel l, BlockPos pos, Direction face, String type, int presence, String point, String evenement) {
        Block b = HeralizLoot.COFFRES.stream().map(r -> r.get()).filter(x -> x instanceof CoffreBlock cb && cb.type.nom.equals(type))
                .findFirst().orElse(null);
        if (b == null) return false;
        BlockState ici = l.getBlockState(pos);
        if (!ici.canBeReplaced() && !(ici.getBlock() instanceof CoffreBlock)) return false;
        if (!((CoffreBlock) b).placeLibre(l, pos, face, null)) return false;
        l.setBlock(pos, b.defaultBlockState().setValue(CoffreBlock.FACING, face), 3);
        if (l.getBlockEntity(pos) instanceof CoffreBlockEntityServeur be) {
            be.apparaitre(presence, point);
            be.evenement = evenement == null ? "" : evenement;
            be.setChanged();
            return true;
        }
        return false;
    }

    /** Déclenche le point nom (carte, boss...) : 1 si le coffre apparaît, 0 s'il est déjà là ou si c'est impossible. */
    public static int declencher(ServerLevel l, String nom) {
        PointsT7 d = get(l);
        Point p = d.points.get(nom);
        if (p == null) return 0;
        d.verifier(l, p);
        if (p.present) return 0;
        if (!apparaitre(l, p.pos, p.face, p.type, p.presence, p.nom)) return 0;
        p.present = true;
        d.setDirty();
        return 1;
    }

    /** Appelé quand un coffre disparaît : son point (s'il en a un) programme la prochaine apparition. */
    static void libere(Level l, String nom, long now) {
        if (nom == null || nom.isEmpty() || !(l instanceof ServerLevel sl)) return;
        PointsT7 d = get(sl);
        Point p = d.points.get(nom);
        if (p == null) return;
        p.present = false;
        p.prochain = now + p.delaiMin + (p.delaiMax > p.delaiMin ? sl.random.nextInt(p.delaiMax - p.delaiMin + 1) : 0);
        d.setDirty();
    }

    /** Le coffre d'un point a pu être cassé ou remplacé : on remet le point à jour. */
    private void verifier(ServerLevel l, Point p) {
        if (p.present && l.isLoaded(p.pos) && !(l.getBlockEntity(p.pos) instanceof CoffreBlockEntityServeur be && p.nom.equals(be.point))) {
            libere(l, p.nom, l.getGameTime());
        }
    }

    /** Toutes les secondes : points automatiques à faire réapparaître. */
    void tick(ServerLevel l) {
        long now = l.getGameTime();
        for (Point p : points.values()) {
            verifier(l, p);
            if (p.auto && !p.present && now >= p.prochain && l.isLoaded(p.pos)) {
                if (apparaitre(l, p.pos, p.face, p.type, p.presence, p.nom)) {
                    p.present = true;
                    setDirty();
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ gestion (commandes)
    public boolean ajouter(Point p) {
        if (points.containsKey(p.nom)) return false;
        points.put(p.nom, p);
        setDirty();
        return true;
    }

    public Point retirer(String nom) {
        Point p = points.remove(nom);
        if (p != null) setDirty();
        return p;
    }

    public Iterable<Point> tous() {
        return points.values();
    }

    public Point point(String nom) {
        return points.get(nom);
    }
}
