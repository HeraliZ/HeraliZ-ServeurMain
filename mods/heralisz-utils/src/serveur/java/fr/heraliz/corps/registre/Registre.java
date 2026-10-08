package fr.heraliz.corps.registre;

import fr.heraliz.corps.entite.CorpsEntity;
import fr.heraliz.corps.entite.CorpsEntityServeur;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Index des corps du serveur ({@code data/heraliz_corps.dat} du monde principal, une centaine d'octets par corps) : sert aux
 * commandes d'administration et au plafond par monde, y compris pour les corps de chunks non charges. Le contenu des corps
 * n'y est pas : il reste dans l'entite (chunk). Les actions demandees sur un corps non charge (vider, supprimer) sont
 * appliquees quand son chunk se charge ; une fiche dont le minuteur est passe est oubliee (le corps disparaitra seul a
 * son chargement). Fil serveur.
 */
public final class Registre extends SavedData {
    private static final String NOM = "heraliz_corps";

    public enum Action { AUCUNE, VIDER, SUPPRIMER }

    /** Fiche d'un corps. */
    public static final class Fiche {
        public int numero;
        public UUID entite;
        public UUID proprietaire;
        public String nom = "";
        public String dimension = "";
        public int x, y, z;
        public long creeLeMs;
        /** Temps de jeu de disparition. */
        public long expire;
        public int objets;
        public Action action = Action.AUCUNE;

        CompoundTag sauver() {
            CompoundTag t = new CompoundTag();
            t.putInt("N", numero);
            t.putUUID("E", entite);
            t.putUUID("P", proprietaire);
            t.putString("Nom", nom);
            t.putString("Dim", dimension);
            t.putIntArray("Pos", new int[]{x, y, z});
            t.putLong("Cree", creeLeMs);
            t.putLong("Exp", expire);
            t.putInt("Obj", objets);
            if (action != Action.AUCUNE) t.putByte("Act", (byte) action.ordinal());
            return t;
        }

        static Fiche charger(CompoundTag t) {
            Fiche f = new Fiche();
            f.numero = t.getInt("N");
            f.entite = t.getUUID("E");
            f.proprietaire = t.getUUID("P");
            f.nom = t.getString("Nom");
            f.dimension = t.getString("Dim");
            int[] p = t.getIntArray("Pos");
            if (p.length == 3) {
                f.x = p[0];
                f.y = p[1];
                f.z = p[2];
            }
            f.creeLeMs = t.getLong("Cree");
            f.expire = t.getLong("Exp");
            f.objets = t.getInt("Obj");
            int a = t.getByte("Act");
            f.action = a >= 0 && a < Action.values().length ? Action.values()[a] : Action.AUCUNE;
            return f;
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    private final Map<UUID, Fiche> fiches = new LinkedHashMap<>();
    private int prochain = 1;

    public static Registre de(MinecraftServer serveur) {
        return serveur.overworld().getDataStorage().computeIfAbsent(Registre::charger, Registre::new, NOM);
    }

    private static Registre charger(CompoundTag t) {
        Registre r = new Registre();
        r.prochain = Math.max(1, t.getInt("Prochain"));
        ListTag l = t.getList("Corps", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            try {
                Fiche f = Fiche.charger(l.getCompound(i));
                r.fiches.put(f.entite, f);
            } catch (RuntimeException ignore) {
                // fiche abimee : oubliee
            }
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.putInt("Prochain", prochain);
        ListTag l = new ListTag();
        for (Fiche f : fiches.values()) l.add(f.sauver());
        t.put("Corps", l);
        return t;
    }

    // ------------------------------------------------------------------ ecritures
    /** Nouveau corps : numero attribue (court, pour les commandes). */
    public Fiche ajouter(CorpsEntityServeur c) {
        Fiche f = new Fiche();
        f.numero = prochain++;
        if (prochain > 999_999) prochain = 1;
        f.entite = c.getUUID();
        f.proprietaire = c.proprietaire();
        f.nom = c.nom();
        f.dimension = c.level().dimension().location().toString();
        remplir(f, c);
        fiches.put(f.entite, f);
        c.definirNumero(f.numero);
        setDirty();
        return f;
    }

    private static void remplir(Fiche f, CorpsEntityServeur c) {
        BlockPos p = c.blockPosition();
        f.x = p.getX();
        f.y = p.getY();
        f.z = p.getZ();
        f.creeLeMs = c.creeLeMs();
        f.expire = c.expire();
        f.objets = c.contenu() == null ? 0 : c.contenu().nbObjets();
    }

    /**
     * Corps qui se charge (premier tick) : rend l'action en attente et la retire. Une fiche manquante (registre perdu) est
     * recreee, avec le numero du corps s'il en a un.
     */
    public Action accueillir(CorpsEntityServeur c) {
        Fiche f = fiches.get(c.getUUID());
        if (f == null) {
            f = new Fiche();
            f.numero = c.numero() > 0 ? c.numero() : prochain++;
            f.entite = c.getUUID();
            f.proprietaire = c.proprietaire();
            f.nom = c.nom();
            f.dimension = c.level().dimension().location().toString();
            remplir(f, c);
            fiches.put(f.entite, f);
            c.definirNumero(f.numero);
            setDirty();
            return Action.AUCUNE;
        }
        c.definirNumero(f.numero);
        Action a = f.action;
        if (a != Action.AUCUNE) {
            f.action = Action.AUCUNE;
            setDirty();
        }
        return a;
    }

    public void objets(UUID entite, int n) {
        Fiche f = fiches.get(entite);
        if (f != null && f.objets != n) {
            f.objets = n;
            setDirty();
        }
    }

    /** Nouvelle position apres une chute (sauvegarde seulement si le bloc a change). */
    public void position(CorpsEntity c) {
        Fiche f = fiches.get(c.getUUID());
        if (f == null) return;
        BlockPos p = c.blockPosition();
        if (p.getX() == f.x && p.getY() == f.y && p.getZ() == f.z) return;
        f.x = p.getX();
        f.y = p.getY();
        f.z = p.getZ();
        setDirty();
    }

    public void expire(UUID entite, long expire) {
        Fiche f = fiches.get(entite);
        if (f != null && f.expire != expire) {
            f.expire = expire;
            setDirty();
        }
    }

    public void retirer(UUID entite) {
        if (fiches.remove(entite) != null) setDirty();
    }

    /** Action a faire au prochain chargement d'un corps non charge. */
    public void programmer(Fiche f, Action a) {
        f.action = a;
        setDirty();
    }

    // ------------------------------------------------------------------ lectures
    /** Oublie les fiches dont le minuteur est passe (ces corps disparaitront seuls a leur chargement). */
    public void purger(long maintenant) {
        if (fiches.values().removeIf(f -> f.expire > 0 && f.expire <= maintenant)) setDirty();
    }

    @Nullable
    public Fiche parNumero(int numero) {
        for (Fiche f : fiches.values()) if (f.numero == numero) return f;
        return null;
    }

    /** Fiches triees de la plus recente a la plus ancienne. */
    public List<Fiche> toutes() {
        List<Fiche> l = new ArrayList<>(fiches.values());
        l.sort(Comparator.comparingLong((Fiche f) -> f.creeLeMs).reversed());
        return l;
    }

    public int taille() {
        return fiches.size();
    }

    /** La plus ancienne fiche (plafond par monde), hors {@code sauf}. */
    @Nullable
    public Fiche plusAncienne(@Nullable UUID sauf) {
        Fiche r = null;
        for (Fiche f : fiches.values()) {
            if (f.entite.equals(sauf) || f.action == Action.SUPPRIMER) continue;
            if (r == null || f.creeLeMs < r.creeLeMs) r = f;
        }
        return r;
    }

    public int nbActifs() {
        int n = 0;
        for (Fiche f : fiches.values()) if (f.action != Action.SUPPRIMER) n++;
        return n;
    }
}
