package fr.heraliz.clashwar.moteur.arenes;

import fr.heraliz.clashwar.HeralizClashWar;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heraliz.clashwar.moteur.api.Arenes;
import fr.heraliz.clashwar.moteur.api.Carte;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.CRC32;

/**
 * Structure d'une carte lue directement dans {@code data/heraliz_clashwar/structures/<carte>.nbt} (format des
 * structures vanilla : palette + blocs + entites), pour une pose par tranches (le placement vanilla pose tout d'un
 * coup). Les blocs d'air sont omis (les cases de la grille sont vides). Les blocs sont ranges par tronçon (chunk)
 * puis par hauteur : chaque tronçon n'est charge qu'une fois pendant la pose. Immuable apres lecture.
 * <p><b>Structures annexes</b> (champ {@code structures_annexes} du JSON de la carte) : gabarits d'autres mods poses avec
 * l'instance, dans la meme file et le meme budget (ex. la salle du boss N-07 sous The Giant, D-057) :
 * <pre>
 * "structures_annexes": [{"structure": "heralisz_n07:n07_hall_zombies", "origine": [127, -26, 74],
 *                         "rotation": "counterclockwise_90", "mod": "heralisz_n07"}]
 * </pre>
 * Origine relative a celle de la carte (y negatif permis : sous la carte), rotation comme {@code StructureTemplate}
 * (pivot 0,0,0). Mod absent ou gabarit introuvable = annexe ignoree (la carte reste jouable). Une annexe gagne sur la
 * carte a la meme case, air compris dans le volume de la carte (ex. le mur du tunnel ouvert sur le monte-charge) ; ailleurs
 * son air est omis comme celui de la carte. L'empreinte compte les annexes : une instance posee sans elles (mod ajoute
 * plus tard) est reposee.
 */
public final class Structure {
    /** Bloc a poser : position relative, etat, NBT de bloc-entite eventuel. */
    public record Bloc(int x, int y, int z, BlockState etat, @Nullable CompoundTag nbt) {
    }

    /** Entite de decor : position relative et NBT complet. */
    public record Ent(double x, double y, double z, CompoundTag nbt) {
    }

    public final String carte;
    public final List<Bloc> blocs;
    public final List<Ent> entites;
    /**
     * CRC32 du fichier .nbt suivi du nombre de blocs poses (une instance posee d'une autre version de la carte, ou
     * avec d'autres mods de blocs presents, est abandonnee).
     */
    public final long empreinte;
    /**
     * Emprise au sol des blocs et entites poses (relative a l'origine, max inclus), annexes comprises : sert a verifier
     * qu'une instance tombe entierement dans la bordure du monde ({@link BordureArenes}). 0 si la structure est vide.
     */
    public final int minX, minZ, maxX, maxZ;

    private Structure(String carte, List<Bloc> blocs, List<Ent> entites, long empreinte) {
        this.carte = carte;
        this.blocs = blocs;
        this.entites = entites;
        this.empreinte = empreinte;
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (Bloc b : blocs) {
            x0 = Math.min(x0, b.x());
            z0 = Math.min(z0, b.z());
            x1 = Math.max(x1, b.x());
            z1 = Math.max(z1, b.z());
        }
        for (Ent e : entites) {
            x0 = Math.min(x0, (int) Math.floor(e.x()));
            z0 = Math.min(z0, (int) Math.floor(e.z()));
            x1 = Math.max(x1, (int) Math.floor(e.x()));
            z1 = Math.max(z1, (int) Math.floor(e.z()));
        }
        boolean vide = x0 > x1;
        this.minX = vide ? 0 : x0;
        this.minZ = vide ? 0 : z0;
        this.maxX = vide ? 0 : x1;
        this.maxZ = vide ? 0 : z1;
    }

    /**
     * Cartes retouchées en jeu par le staff ({@code /clashwar carte sauver}, 23/11) : {@code <serveur>/heraliz_clashwar/
     * cartes_retouchees/<carte>.nbt} passe devant la carte du jar. Le dossier n'est jamais effacé par la mise en ligne.
     */
    public static java.nio.file.Path dossierRetouches(MinecraftServer serveur) {
        return serveur.getServerDirectory().toPath().resolve("heraliz_clashwar").resolve("cartes_retouchees");
    }

    /** Clé gardée dans une carte retouchée : empreinte de la carte du jar au moment de la sauvegarde. */
    public static final String CLE_CRC_JAR = "heraliz_crc_jar";

    /** Empreinte (CRC) du fichier de la carte dans le jar, ou null s'il est absent ou illisible. */
    @Nullable
    public static Long crcJar(MinecraftServer serveur, String carte) {
        Optional<Resource> res = serveur.getResourceManager().getResource(HeralizClashWar.id("structures/" + carte + ".nbt"));
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open()) {
            CRC32 crc = new CRC32();
            crc.update(in.readAllBytes());
            return crc.getValue();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Lit la structure de la carte, ou null (avertissement) si elle est absente ou illisible. Une version retouchée en
     * jeu passe devant celle du jar, sauf si elle est illisible, d'une autre taille que la carte, ou faite d'après une
     * carte du jar qui a changé depuis (mise à jour du mod) : alors la carte du jar sert, avec une erreur au journal.
     */
    @Nullable
    public static Structure lire(MinecraftServer serveur, String carte) {
        java.nio.file.Path retouche = dossierRetouches(serveur).resolve(carte + ".nbt");
        if (java.nio.file.Files.isRegularFile(retouche)) {
            try {
                byte[] octets = java.nio.file.Files.readAllBytes(retouche);
                CompoundTag t = NbtIo.readCompressed(new ByteArrayInputStream(octets));
                String refus = refusRetouche(serveur, carte, t);
                if (refus == null) {
                    HeralizClashWar.LOG.info("[matchs] structure {} : version retouchee en jeu ({})", carte, retouche);
                    return lire(serveur, carte, octets, t);
                }
                HeralizClashWar.LOG.error("[matchs] carte retouchee {} ignoree ({}) : carte du jar utilisee ; supprimer ou refaire {}",
                        carte, refus, retouche);
            } catch (Exception ex) {
                HeralizClashWar.LOG.error("[matchs] carte retouchee {} illisible : carte du jar utilisee ; supprimer ou refaire {}",
                        carte, retouche, ex);
            }
        }
        ResourceLocation rl = HeralizClashWar.id("structures/" + carte + ".nbt");
        Optional<Resource> res = serveur.getResourceManager().getResource(rl);
        if (res.isEmpty()) {
            HeralizClashWar.LOG.warn("[matchs] structure absente : {}", rl);
            return null;
        }
        try (InputStream in = res.get().open()) {
            byte[] octets = in.readAllBytes();
            return lire(serveur, carte, octets, NbtIo.readCompressed(new ByteArrayInputStream(octets)));
        } catch (Exception ex) {
            HeralizClashWar.LOG.error("[matchs] structure {} illisible", rl, ex);
            return null;
        }
    }

    /** Raison de ne pas prendre une carte retouchée, ou null si elle convient. */
    @Nullable
    private static String refusRetouche(MinecraftServer serveur, String carte, CompoundTag t) {
        if (t.getList("blocks", Tag.TAG_COMPOUND).isEmpty()) return "aucun bloc";
        Carte c = Arenes.carte(carte);
        ListTag taille = t.getList("size", Tag.TAG_INT);
        if (c != null && (taille.size() != 3 || taille.getInt(0) != c.taille().getX() || taille.getInt(1) != c.taille().getY()
                || taille.getInt(2) != c.taille().getZ())) {
            return "taille differente de la carte";
        }
        Long jar = crcJar(serveur, carte);
        if (jar != null && t.contains(CLE_CRC_JAR) && t.getLong(CLE_CRC_JAR) != jar) return "la carte du mod a change depuis";
        return null;
    }

    /** Structure tirée du fichier lu ; une erreur remonte à l'appelant (retouche : retour à la carte du jar). */
    private static Structure lire(MinecraftServer serveur, String carte, byte[] octets, CompoundTag t) throws Exception {
        {
            CRC32 crc = new CRC32();
            crc.update(octets);
            HolderGetter<Block> registre = serveur.registryAccess().lookupOrThrow(Registries.BLOCK);
            ListTag palette = t.getList("palette", Tag.TAG_COMPOUND);
            if (palette.isEmpty() && t.contains("palettes")) palette = t.getList("palettes", Tag.TAG_LIST).getList(0);
            BlockState[] etats = new BlockState[palette.size()];
            // bloc d'un mod absent = air : il manque au nombre de blocs poses, donc a l'empreinte (une instance posee
            // sans ce mod est reposee quand il est la, et inversement) ; signale dans le journal
            int manquants = 0;
            StringBuilder absents = new StringBuilder();
            for (int i = 0; i < etats.length; i++) {
                etats[i] = NbtUtils.readBlockState(registre, palette.getCompound(i));
                String nom = palette.getCompound(i).getString("Name");
                if (etats[i].isAir() && !nom.endsWith(":air") && !nom.equals("air")) {
                    manquants++;
                    if (absents.length() < 200) absents.append(absents.length() == 0 ? "" : ", ").append(nom);
                }
            }
            if (manquants > 0) HeralizClashWar.LOG.warn("[matchs] structure {} : {} bloc(s) inconnu(s) poses en air ({})", carte, manquants, absents);
            ListTag lb = t.getList("blocks", Tag.TAG_COMPOUND);
            List<Bloc> blocs = new ArrayList<>(lb.size());
            for (int i = 0; i < lb.size(); i++) {
                CompoundTag b = lb.getCompound(i);
                int s = b.getInt("state");
                if (s < 0 || s >= etats.length) continue;
                BlockState e = etats[s];
                if (e.isAir() || e.is(Blocks.STRUCTURE_VOID)) continue;
                ListTag pos = b.getList("pos", Tag.TAG_INT);
                blocs.add(new Bloc(pos.getInt(0), pos.getInt(1), pos.getInt(2), e, b.contains("nbt") ? b.getCompound("nbt") : null));
            }
            // structures annexes (autres mods) : elles gagnent sur la carte a la meme case
            int annexes = annexes(serveur, carte, registre, t, blocs, crc);
            int murs = mursInvisibles(carte, t, blocs);
            // tri : tronçon (x >> 4, z >> 4), puis hauteur, puis position
            blocs.sort((a, b) -> {
                int c = Integer.compare(a.x >> 4, b.x >> 4);
                if (c != 0) return c;
                c = Integer.compare(a.z >> 4, b.z >> 4);
                if (c != 0) return c;
                c = Integer.compare(a.y, b.y);
                if (c != 0) return c;
                c = Integer.compare(a.x, b.x);
                return c != 0 ? c : Integer.compare(a.z, b.z);
            });
            ListTag le = t.getList("entities", Tag.TAG_COMPOUND);
            List<Ent> ents = new ArrayList<>(le.size());
            for (int i = 0; i < le.size(); i++) {
                CompoundTag e = le.getCompound(i);
                ListTag pos = e.getList("pos", Tag.TAG_DOUBLE);
                if (pos.size() < 3 || !e.contains("nbt")) continue;
                ents.add(new Ent(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2), e.getCompound("nbt")));
            }
            // empreinte = fichier + nombre de blocs reellement poses (mods presents)
            int n = blocs.size();
            crc.update(new byte[]{(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n});
            if (annexes > 0) HeralizClashWar.LOG.info("[matchs] structure {} : {} annexe(s), {} blocs a poser", carte, annexes, n);
            if (murs > 0) HeralizClashWar.LOG.info("[matchs] structure {} : {} barrieres autour de la carte", carte, murs);
            return new Structure(carte, List.copyOf(blocs), List.copyOf(ents), crc.getValue());
        }
    }
    /** Hauteur des murs invisibles au-dessus du haut de la structure (on ne les saute pas). */
    private static final int MURS_AU_DESSUS = 4;

    /**
     * Murs invisibles (18/11, retour du propriétaire : « on peut sauter dans le vide, ça nous tue : mettre des blocs
     * invisibles autour pour empêcher d'aller dans le vide ») : des barrières de Minecraft tout autour du volume de la
     * structure, juste à l'extérieur, du bas jusqu'à {@value #MURS_AU_DESSUS} blocs au-dessus de son haut. Comptées dans
     * les blocs posés, donc dans l'empreinte : une instance posée sans elles est reposée. Réglage {@code murs_invisibles}
     * du JSON de la carte (vrai si absent). Rend le nombre de barrières ajoutées.
     */
    private static int mursInvisibles(String carte, CompoundTag principale, List<Bloc> blocs) {
        Carte c = Arenes.carte(carte);
        if (c != null && c.meta().has("murs_invisibles") && !c.meta().get("murs_invisibles").getAsBoolean()) return 0;
        ListTag taille = principale.getList("size", Tag.TAG_INT);
        if (taille.size() != 3) return 0;
        int sx = taille.getInt(0), sy = taille.getInt(1), sz = taille.getInt(2);
        if (sx <= 0 || sy <= 0 || sz <= 0) return 0;
        BlockState barriere = Blocks.BARRIER.defaultBlockState();
        int avant = blocs.size();
        for (int y = 0; y < sy + MURS_AU_DESSUS; y++) {
            for (int x = -1; x <= sx; x++) {
                blocs.add(new Bloc(x, y, -1, barriere, null));
                blocs.add(new Bloc(x, y, sz, barriere, null));
            }
            for (int z = 0; z < sz; z++) {
                blocs.add(new Bloc(-1, y, z, barriere, null));
                blocs.add(new Bloc(sx, y, z, barriere, null));
            }
        }
        return blocs.size() - avant;
    }

    /**
     * Ajoute les structures annexes de la carte a la liste des blocs (et a l'empreinte). Rend le nombre d'annexes posees.
     * Lecture des ressources seulement, aucun acces au monde.
     */
    private static int annexes(MinecraftServer serveur, String carte, HolderGetter<Block> registre, CompoundTag principale, List<Bloc> blocs, CRC32 crc) {
        Carte c = Arenes.carte(carte);
        if (c == null || !c.meta().has("structures_annexes") || !c.meta().get("structures_annexes").isJsonArray()) return 0;
        ListTag taille = principale.getList("size", Tag.TAG_INT);
        int sx = taille.size() == 3 ? taille.getInt(0) : 0, sy = taille.size() == 3 ? taille.getInt(1) : 0, sz = taille.size() == 3 ? taille.getInt(2) : 0;
        int nb = 0;
        for (JsonElement el : c.meta().getAsJsonArray("structures_annexes")) {
            if (!el.isJsonObject()) continue;
            JsonObject a = el.getAsJsonObject();
            try {
                String mod = a.has("mod") ? a.get("mod").getAsString() : "";
                if (!mod.isEmpty() && !ModList.get().isLoaded(mod)) {
                    HeralizClashWar.LOG.info("[matchs] structure {} : annexe {} ignoree (mod {} absent)", carte, a.get("structure"), mod);
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(a.get("structure").getAsString());
                JsonArray o = a.getAsJsonArray("origine");
                if (id == null || o == null || o.size() < 3) continue;
                BlockPos origine = new BlockPos(o.get(0).getAsInt(), o.get(1).getAsInt(), o.get(2).getAsInt());
                Rotation rot = rotation(a.has("rotation") ? a.get("rotation").getAsString() : "none");
                ResourceLocation rl = new ResourceLocation(id.getNamespace(), "structures/" + id.getPath() + ".nbt");
                Optional<Resource> res = serveur.getResourceManager().getResource(rl);
                if (res.isEmpty()) {
                    HeralizClashWar.LOG.warn("[matchs] structure {} : annexe {} introuvable, ignoree", carte, rl);
                    continue;
                }
                byte[] octets;
                try (InputStream in = res.get().open()) {
                    octets = in.readAllBytes();
                }
                crc.update(octets);
                crc.update(origine.toShortString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                crc.update(String.valueOf(rot).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                CompoundTag t = NbtIo.readCompressed(new ByteArrayInputStream(octets));
                ListTag palette = t.getList("palette", Tag.TAG_COMPOUND);
                if (palette.isEmpty() && t.contains("palettes")) palette = t.getList("palettes", Tag.TAG_LIST).getList(0);
                BlockState[] etats = new BlockState[palette.size()];
                for (int i = 0; i < etats.length; i++) etats[i] = NbtUtils.readBlockState(registre, palette.getCompound(i)).rotate(rot);
                // cases de l'annexe (position relative a la carte -> bloc), air compris
                Long2ObjectOpenHashMap<Bloc> parCase = new Long2ObjectOpenHashMap<>();
                ListTag lb = t.getList("blocks", Tag.TAG_COMPOUND);
                for (int i = 0; i < lb.size(); i++) {
                    CompoundTag b = lb.getCompound(i);
                    int s = b.getInt("state");
                    if (s < 0 || s >= etats.length || etats[s].is(Blocks.STRUCTURE_VOID)) continue;
                    ListTag pos = b.getList("pos", Tag.TAG_INT);
                    BlockPos p = origine.offset(StructureTemplate.transform(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), Mirror.NONE, rot,
                            BlockPos.ZERO));
                    parCase.put(p.asLong(), new Bloc(p.getX(), p.getY(), p.getZ(), etats[s], b.contains("nbt") ? b.getCompound("nbt") : null));
                }
                // la carte cede ses cases a l'annexe
                blocs.removeIf(b -> parCase.containsKey(BlockPos.asLong(b.x(), b.y(), b.z())));
                for (Bloc b : parCase.values()) {
                    boolean dansCarte = b.x() >= 0 && b.y() >= 0 && b.z() >= 0 && b.x() < sx && b.y() < sy && b.z() < sz;
                    if (b.etat().isAir() && !dansCarte) continue;
                    blocs.add(b);
                }
                nb++;
            } catch (Exception ex) {
                HeralizClashWar.LOG.error("[matchs] structure {} : annexe {} illisible, ignoree", carte, a.get("structure"), ex);
            }
        }
        return nb;
    }

    /** Rotation d'une annexe : none, clockwise_90, clockwise_180, counterclockwise_90 (noms de StructureTemplate). */
    public static Rotation rotation(String nom) {
        String n = switch (nom.toLowerCase(Locale.ROOT)) {
            case "90" -> "clockwise_90";
            case "180" -> "clockwise_180";
            case "270", "-90" -> "counterclockwise_90";
            default -> nom;
        };
        // par le nom de la constante (aucune reference directe aux constantes de l'enum dans le jar reobfusque)
        for (Rotation r : Rotation.values()) if (String.valueOf(r).equalsIgnoreCase(n)) return r;
        return Rotation.NONE;
    }
}
