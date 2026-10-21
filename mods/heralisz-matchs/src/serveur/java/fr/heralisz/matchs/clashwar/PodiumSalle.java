package fr.heralisz.matchs.clashwar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.MatchApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ceremonie du podium dans la salle d'attente (maquette 3 validee le 21/10) : trois marches posees sur l'estrade, devant
 * le mur-tableau, face aux gradins (metadonnees {@code podium} de la carte de la salle : {@code [x, y, z, largeur,
 * hauteur]} par place, 1re puis 2e puis 3e), chiffre de la place en grand sur le devant, joueurs des trois premieres
 * equipes poses dessus, feux d'artifice aux couleurs des places. Tout est retire (blocs remis tels qu'ils etaient,
 * entites supprimees) avant la fermeture de la salle. Fil serveur.
 */
final class PodiumSalle {
    /** Marches par defaut (salle « Hangar du blocus ») : 1re au milieu, 2e a gauche, 3e a droite (vu des gradins). */
    private static final int[][] DEFAUT = {{22, 2, 6, 4, 3}, {18, 2, 6, 4, 2}, {26, 2, 6, 4, 1}};
    /** Couleurs des places : or, argent, bronze. */
    static final int[] COULEURS = {0xE8C36A, 0xB9C0CB, 0xD08A4E};
    private static final BlockState[] DESSUS = {Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState(),
            Blocks.WAXED_COPPER_BLOCK.defaultBlockState()};
    private static final BlockState CORPS = Blocks.POLISHED_DEEPSLATE.defaultBlockState();

    private record Bloc(BlockPos pos, BlockState avant) {
    }

    private final Instance inst;
    private final int[][] marches;
    private final List<Bloc> poses = new ArrayList<>();
    private final List<Entity> entites = new ArrayList<>();
    private int feux;

    private PodiumSalle(Instance inst, int[][] marches) {
        this.inst = inst;
        this.marches = marches;
    }

    /** Pose le podium des equipes classees ({@code podium[0..2]} = index d'equipe ou -1) ; rend null sans salle. */
    @Nullable
    static PodiumSalle poser(@Nullable Instance inst, Tableau tab, int[] podium, Tournoi t) {
        if (inst == null) return null;
        PodiumSalle p = new PodiumSalle(inst, lire(inst));
        try {
            p.construire(tab, podium, t);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[clashwar] podium non pose", ex);
            p.retirer();
            return null;
        }
        return p;
    }

    private static int[][] lire(Instance inst) {
        try {
            JsonObject m = inst.carte().meta();
            if (m.has("podium") && m.get("podium").isJsonArray()) {
                JsonArray a = m.getAsJsonArray("podium");
                int[][] r = new int[3][];
                for (int i = 0; i < 3; i++) {
                    JsonArray e = a.get(i).getAsJsonArray();
                    r[i] = new int[]{e.get(0).getAsInt(), e.get(1).getAsInt(), e.get(2).getAsInt(), e.get(3).getAsInt(), e.get(4).getAsInt()};
                }
                return r;
            }
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[clashwar] podium de {} illisible : {}", inst.carte().id(), ex.toString());
        }
        return DEFAUT;
    }

    private void construire(Tableau tab, int[] podium, Tournoi t) {
        ServerLevel lv = inst.niveau();
        BlockPos o = inst.origine();
        for (int k = 0; k < 3; k++) {
            int[] m = marches[k];
            int x = m[0], y = m[1], z = m[2], larg = m[3], haut = m[4];
            for (int i = 0; i < larg; i++) {
                for (int j = 0; j < haut; j++) {
                    BlockPos pos = o.offset(x + i, y + j, z);
                    poses.add(new Bloc(pos, lv.getBlockState(pos)));
                    lv.setBlock(pos, j == haut - 1 ? DESSUS[k] : CORPS, Block.UPDATE_CLIENTS);
                }
            }
            // chiffre de la place sur le devant de la marche (face aux gradins, +z)
            double cx = o.getX() + x + larg / 2.0, cz = o.getZ() + z + 1.02;
            // l'entite d'affichage part du bas du texte ; les chiffres font environ 7 / 40 de bloc de haut a l'echelle 1 :
            // on centre leur milieu sur le milieu du devant de la marche
            float echelle = haut >= 2 ? 3.2f : 2.2f;
            double cy = o.getY() + y + haut / 2.0 - (1 + 7 / 2.0) / 40.0 * echelle;
            // sur le corps sombre : couleur de la place ; marche d'un seul bloc (le dessus colore) : chiffre sombre
            final int couleur = haut >= 2 ? COULEURS[k] : 0x15181D;
            texte(lv, Component.literal(String.valueOf(k + 1)).withStyle(s -> s.withColor(couleur).withBold(true)), cx, cy, cz, echelle);
            // joueurs de l'equipe sur la marche, face aux gradins
            if (podium[k] < 0) continue;
            int n = 0;
            for (UUID u : tab.equipe(podium[k]).joueurs) {
                ServerPlayer p = t.serveur().getPlayerList().getPlayer(u);
                if (p == null || MatchApi.partieDe(p) != t.salle || n >= larg) continue;
                p.teleportTo(lv, o.getX() + x + n + 0.5, o.getY() + y + haut, o.getZ() + z + 0.5, 0f, -8f);
                n++;
            }
        }
        lancerFeux();
    }

    /** Texte affiche dans le monde (entite d'affichage), tourne vers +z, sans fond. */
    private void texte(ServerLevel lv, Component c, double x, double y, double z, float echelle) {
        Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(lv);
        if (d == null) return;
        CompoundTag t = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(x));
        pos.add(DoubleTag.valueOf(y));
        pos.add(DoubleTag.valueOf(z));
        t.put("Pos", pos);
        ListTag rot = new ListTag();
        rot.add(FloatTag.valueOf(0f));
        rot.add(FloatTag.valueOf(0f));
        t.put("Rotation", rot);
        t.putString("text", Component.Serializer.toJson(c));
        t.putString("billboard", "fixed");
        t.putInt("background", 0);
        t.putBoolean("shadow", false);
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", liste(0f, 0f, 0f, 1f));
        tr.put("right_rotation", liste(0f, 0f, 0f, 1f));
        tr.put("translation", liste(0f, 0f, 0f));
        tr.put("scale", liste(echelle, echelle, echelle));
        t.put("transformation", tr);
        d.load(t);
        if (lv.addFreshEntity(d)) entites.add(d);
    }

    private static ListTag liste(float... v) {
        ListTag l = new ListTag();
        for (float f : v) l.add(FloatTag.valueOf(f));
        return l;
    }

    /** Une salve de feux d'artifice au-dessus des trois marches (appelee regulierement pendant la ceremonie). */
    void lancerFeux() {
        ServerLevel lv = inst.niveau();
        BlockPos o = inst.origine();
        feux++;
        for (int k = 0; k < 3; k++) {
            int[] m = marches[k];
            ItemStack f = new ItemStack(Items.FIREWORK_ROCKET);
            CompoundTag fw = new CompoundTag();
            fw.putByte("Flight", (byte) 1);
            ListTag ex = new ListTag();
            CompoundTag e = new CompoundTag();
            e.putByte("Type", (byte) (k == 0 ? 1 : 4));
            e.put("Colors", new IntArrayTag(new int[]{COULEURS[k]}));
            e.put("FadeColors", new IntArrayTag(new int[]{0xFFFFFF}));
            e.putBoolean("Trail", k == 0);
            ex.add(e);
            fw.put("Explosions", ex);
            f.getOrCreateTag().put("Fireworks", fw);
            double x = o.getX() + m[0] + m[3] / 2.0 + (feux % 2 == 0 ? -1.2 : 1.2), y = o.getY() + m[1] + m[4] + 0.5, z = o.getZ() + m[2] + 2.5;
            lv.addFreshEntity(new FireworkRocketEntity(lv, x, y, z, f));
        }
    }

    /** Remet les blocs et retire les entites du podium. */
    void retirer() {
        ServerLevel lv = inst.niveau();
        for (int i = poses.size() - 1; i >= 0; i--) {
            Bloc b = poses.get(i);
            lv.setBlock(b.pos(), b.avant(), Block.UPDATE_CLIENTS);
        }
        poses.clear();
        for (Entity e : entites) if (!e.isRemoved()) e.discard();
        entites.clear();
    }
}
