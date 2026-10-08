package fr.heralisz.bases.monde;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.bases.BasesConfig;
import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.model.Plan;
import fr.heralisz.bases.registry.BasesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Modele du bunker charge depuis le datapack du mod : {@code data/heralisz_bases/structures/bunker.nbt} (structure
 * vanilla, dessinee par l'agent « carte bunker ») et {@code bunker.json} (metadonnees, {@link Plan}). Charge au demarrage
 * du serveur et par {@code /base admin recharger} ; s'il manque, la creation de base est refusee (message + journal).
 * <p>Les blocs sont gardes en tableaux plats (aucun objet par bloc) : positions relatives emballees, index de palette,
 * NBT des BlockEntity a part. L'air et les structure_void sont ignores (la parcelle est vide avant la pose). Les blocs de
 * « coque provisoire » (config {@code coque_provisoire} + champ du JSON, obsidienne par defaut) deviennent
 * {@code heralisz_bases:coque}. Ordre de pose : blocs pleins d'abord, puis les autres (torches, portes, lampes...).
 */
public final class Modele {
    public static final ResourceLocation NBT = HeraliszBases.id("structures/bunker.nbt");
    public static final ResourceLocation JSON = HeraliszBases.id("structures/bunker.json");

    private static Modele actuel;
    private static String erreur = "non charge";

    public final Plan plan;
    public final BlockState[] palette;
    /** Position relative emballee : x | y << 10 | z << 20 (chaque coordonnee < 1024). */
    public final int[] positions;
    public final int[] etats;
    /** Index dans {@link #positions} -> NBT de BlockEntity (sans x/y/z). */
    public final Map<Integer, CompoundTag> nbt;
    /** Positions relatives des blocs de sortie a poser en plus (entrees sans bloc de sortie dans le dessin). */
    public final List<int[]> sortiesAjoutees;
    public final int coqueRemplacee;
    public final int empreinte;

    private Modele(Plan plan, BlockState[] palette, int[] positions, int[] etats, Map<Integer, CompoundTag> nbt,
                   List<int[]> sortiesAjoutees, int coqueRemplacee) {
        this.plan = plan;
        this.palette = palette;
        this.positions = positions;
        this.etats = etats;
        this.nbt = nbt;
        this.sortiesAjoutees = sortiesAjoutees;
        this.coqueRemplacee = coqueRemplacee;
        this.empreinte = plan.empreinte();
    }

    /** Le modele charge, ou null (voir {@link #erreur()}). */
    @Nullable
    public static Modele get() {
        return actuel;
    }

    /** Raison de l'absence de modele (journal, message admin). */
    public static String erreur() {
        return erreur;
    }

    public int nombre() {
        return positions.length;
    }

    public static int x(int p) {
        return p & 1023;
    }

    public static int y(int p) {
        return (p >> 10) & 1023;
    }

    public static int z(int p) {
        return (p >> 20) & 1023;
    }

    /** (Re)charge le modele depuis les ressources du serveur. Vrai si reussi ; l'ancien modele est garde sinon. */
    public static boolean charger(MinecraftServer server) {
        try {
            Optional<Resource> rj = server.getResourceManager().getResource(JSON);
            Optional<Resource> rn = server.getResourceManager().getResource(NBT);
            if (rj.isEmpty() || rn.isEmpty()) {
                erreur = (rj.isEmpty() ? "bunker.json" : "") + (rj.isEmpty() && rn.isEmpty() ? " et " : "") + (rn.isEmpty() ? "bunker.nbt" : "")
                        + " absent(s) de data/heralisz_bases/structures";
                HeraliszBases.LOG.warn("[bases] modele du bunker indisponible : {}", erreur);
                return false;
            }
            JsonObject jo;
            try (Reader r = rj.get().openAsReader()) {
                jo = JsonParser.parseReader(r).getAsJsonObject();
            }
            CompoundTag racine;
            try (InputStream in = rn.get().open()) {
                racine = NbtIo.readCompressed(in);
            }
            Modele m = construire(server, Plan.lire(jo), racine);
            actuel = m;
            erreur = "";
            HeraliszBases.LOG.info("[bases] modele du bunker charge : {} x {} x {}, {} blocs, {} salles, {} entrees, {} blocs de coque provisoire remplaces",
                    m.plan.tx, m.plan.ty, m.plan.tz, m.nombre(), m.plan.salles.size(), m.plan.entrees.size(), m.coqueRemplacee);
            return true;
        } catch (Exception ex) {
            erreur = "lecture impossible : " + ex;
            HeraliszBases.LOG.error("[bases] modele du bunker illisible", ex);
            return false;
        }
    }

    private static Modele construire(MinecraftServer server, Plan planJson, CompoundTag racine) {
        // taille : celle du NBT si le JSON n'en donne pas
        Plan plan = planJson;
        ListTag taille = racine.getList("size", Tag.TAG_INT);
        if (taille.size() == 3 && (plan.tx <= 0 || plan.ty <= 0 || plan.tz <= 0)) {
            plan = new Plan(taille.getInt(0), taille.getInt(1), taille.getInt(2), plan.salles, plan.entrees, plan.interieur,
                    plan.hall, plan.couloirs, plan.coqueProvisoire);
        }
        if (plan.tx > 1023 || plan.ty > 1023 || plan.tz > 1023) throw new IllegalStateException("structure trop grande");
        if (plan.tx > Grille.pas() || plan.tz > Grille.pas()) {
            HeraliszBases.LOG.warn("[bases] le bunker ({} x {}) depasse le pas de la grille ({})", plan.tx, plan.tz, Grille.pas());
        }
        // palette (premiere palette si la structure en a plusieurs)
        ListTag pal = racine.contains("palettes", Tag.TAG_LIST) ? racine.getList("palettes", Tag.TAG_LIST).getList(0)
                : racine.getList("palette", Tag.TAG_COMPOUND);
        Set<String> provisoire = new HashSet<>(plan.coqueProvisoire);
        for (Object o : BasesConfig.COQUE_PROVISOIRE.get()) provisoire.add(String.valueOf(o));
        var blocs = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        BlockState coque = BasesRegistry.COQUE.get().defaultBlockState();
        BlockState[] palette = new BlockState[pal.size()];
        boolean[] provisoireIdx = new boolean[pal.size()];
        boolean sortieDansDessin = false;
        for (int i = 0; i < pal.size(); i++) {
            CompoundTag t = pal.getCompound(i);
            String nom = t.getString("Name");
            BlockState s = NbtUtils.readBlockState(blocs, t);
            if (provisoire.contains(nom)) {
                s = coque;
                provisoireIdx[i] = true;
            }
            if (s.is(BasesRegistry.SORTIE.get())) sortieDansDessin = true;
            palette[i] = s;
        }
        // blocs : pleins d'abord, puis les autres
        ListTag lb = racine.getList("blocks", Tag.TAG_COMPOUND);
        List<Integer> pleins = new ArrayList<>(lb.size()), autres = new ArrayList<>();
        boolean[] pleinIdx = new boolean[palette.length];
        for (int i = 0; i < palette.length; i++) {
            pleinIdx[i] = palette[i].isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        }
        for (int i = 0; i < lb.size(); i++) {
            CompoundTag b = lb.getCompound(i);
            int st = b.getInt("state");
            if (st < 0 || st >= palette.length) continue;
            BlockState s = palette[st];
            if (s.isAir() || s.is(Blocks.STRUCTURE_VOID)) continue;
            (pleinIdx[st] ? pleins : autres).add(i);
        }
        int n = pleins.size() + autres.size(), k = 0, remplaces = 0;
        int[] positions = new int[n], etats = new int[n];
        Map<Integer, CompoundTag> nbt = new HashMap<>();
        for (List<Integer> l : List.of(pleins, autres)) {
            for (int i : l) {
                CompoundTag b = lb.getCompound(i);
                ListTag p = b.getList("pos", Tag.TAG_INT);
                int x = p.getInt(0), y = p.getInt(1), z = p.getInt(2);
                if (x < 0 || y < 0 || z < 0 || x > 1023 || y > 1023 || z > 1023) continue;
                positions[k] = x | (y << 10) | (z << 20);
                etats[k] = b.getInt("state");
                if (provisoireIdx[etats[k]]) remplaces++;
                if (b.contains("nbt", Tag.TAG_COMPOUND) && !provisoireIdx[etats[k]]) nbt.put(k, b.getCompound("nbt"));
                k++;
            }
        }
        if (k < n) {
            positions = java.util.Arrays.copyOf(positions, k);
            etats = java.util.Arrays.copyOf(etats, k);
        }
        // blocs de sortie : ceux du JSON, sinon un bloc derriere chaque point d'arrivee (si le dessin n'en a pas)
        List<int[]> sorties = new ArrayList<>();
        for (Plan.Entree e : plan.entrees.values()) {
            if (e.sortie() != null) sorties.add(e.sortie());
            else if (!sortieDansDessin) {
                double r = Math.toRadians(e.yaw());
                int dx = (int) Math.round(Math.sin(r)), dz = (int) Math.round(-Math.cos(r));
                sorties.add(new int[]{(int) Math.floor(e.x()) + dx, (int) Math.floor(e.y()), (int) Math.floor(e.z()) + dz});
            }
        }
        String ids = BuiltInRegistries.BLOCK.getKey(coque.getBlock()).toString();
        HeraliszBases.LOG.debug("[bases] coque = {}, provisoire = {}", ids, provisoire);
        return new Modele(plan, palette, positions, etats, nbt, sorties, remplaces);
    }
}
