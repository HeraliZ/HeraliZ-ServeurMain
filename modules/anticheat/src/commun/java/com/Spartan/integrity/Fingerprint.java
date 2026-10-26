package com.Spartan.integrity;

import com.Spartan.Spartan;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Empreinte des classes du jar JOUEUR de Spartan, calculée par le joueur et par le serveur.
 *
 * <p>Où sont les classes :
 * <ul>
 *    <li>chez le joueur (jar -client) et en solo (jar -complet) : les classes du jar lui-même ;</li>
 *    <li>sur le serveur (jar -serveur) : la copie du jar joueur rangée dans {@code META-INF/spartan/client.bin} à la
 *    fabrication. Le serveur calcule dessus ce qu'un client non modifié doit répondre.</li>
 * </ul>
 *
 * <p>Un défi lie deux preuves :
 * <ol>
 *    <li>les octets des classes (le jar sur le disque n'a pas été modifié) ;</li>
 *    <li>la <b>forme vivante</b> de chaque classe : la liste exacte de ses champs, méthodes et constructeurs TELS QUE
 *    CHARGÉS dans le jeu (réflexion), comparée à celle lue dans le fichier par le serveur. Un client de triche qui
 *    neutralise Spartan en mémoire sans toucher au jar (Mixin, transformateur ASM, agent) ajoute ou retire forcément des
 *    membres : la réponse devient fausse. Garder une copie propre du jar ne suffit donc plus pour répondre.</li>
 * </ol>
 * Code commun : aucune classe client n'est référencée.
 */
public final class Fingerprint {
    /** Copie du jar joueur dans le jar serveur. */
    private static final String[] EMBARQUE = {"META-INF", "spartan", "client.bin"};
    private static final String RACINE = "com/Spartan/";

    private static List<byte[]> classes;
    private static List<String> names;
    private static List<byte[]> fileShapes;
    private static boolean embedded;
    private static String buildId;

    private Fingerprint() {
    }

    /** Charge (une seule fois) les octets de toutes les classes du jar joueur, dans un ordre stable. */
    private static synchronized List<byte[]> classes() {
        if (classes != null) {
            return classes;
        }
        TreeMap<String, byte[]> byRel = new TreeMap<>();
        try {
            IModFileInfo info = ModList.get().getModFileById(Spartan.MOD_ID);
            Path copy = info.getFile().findResource(EMBARQUE);
            if (copy != null && Files.isRegularFile(copy)) {
                embedded = true;
                try (InputStream in = Files.newInputStream(copy)) {
                    byRel.putAll(classesOf(in));
                }
            } else {
                Path root = info.getFile().findResource("com", "Spartan");
                try (Stream<Path> walk = Files.walk(root)) {
                    for (Path p : walk.filter(x -> x.toString().endsWith(".class")).toList()) {
                        byRel.put(root.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Spartan.LOGGER.error("[Spartan] Impossible de lire les classes du mod pour l'empreinte", e);
        }
        load(byRel);
        return classes;
    }

    /** Classes « com/Spartan/… » d'un jar, par chemin relatif à com/Spartan (Java pur, testé). */
    static TreeMap<String, byte[]> classesOf(InputStream jar) throws IOException {
        TreeMap<String, byte[]> out = new TreeMap<>();
        try (ZipInputStream zin = new ZipInputStream(jar)) {
            for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                String n = e.getName();
                if (!e.isDirectory() && n.startsWith(RACINE) && n.endsWith(".class")) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    zin.transferTo(b);
                    out.put(n.substring(RACINE.length()), b.toByteArray());
                }
            }
        }
        return out;
    }

    /** Range les classes dans l'ordre des chemins relatifs (même ordre chez le joueur et sur le serveur). */
    static synchronized void load(TreeMap<String, byte[]> byRel) {
        List<byte[]> out = new ArrayList<>();
        List<String> outNames = new ArrayList<>();
        byRel.forEach((rel, bytes) -> {
            out.add(bytes);
            outNames.add("com.Spartan." + rel.substring(0, rel.length() - ".class".length()).replace('/', '.'));
        });
        names = outNames;
        classes = out;
        fileShapes = null;
        buildId = null;
    }

    /** Noms des classes du jar joueur (même ordre que l'empreinte). */
    public static synchronized List<String> classNames() {
        classes();
        return names;
    }

    public static int classCount() {
        return classes().size();
    }

    /** Vrai sur le serveur quand l'empreinte vient de la copie du jar joueur (jar -serveur). */
    public static boolean fromEmbeddedCopy() {
        classes();
        return embedded;
    }

    /** Identifiant de build : empreinte de toutes les classes du jar joueur. */
    public static synchronized String buildId() {
        if (buildId == null) {
            MessageDigest sha = sha256();
            for (byte[] c : classes()) {
                sha.update(c);
            }
            buildId = HexFormat.of().formatHex(sha.digest()).substring(0, 32);
        }
        return buildId;
    }

    // ------------------------------------------------------------------ réponses au défi
    /** CÔTÉ JOUEUR : réponse au défi, avec la forme VIVANTE des classes (lue par réflexion dans le jeu). */
    public static byte[] answer(long nonce, int variant, int[] indices) {
        List<byte[]> all = classes();
        List<String> n = classNames();
        byte[][] shapes = new byte[all.size()][];
        for (int i : indices) {
            if (i >= 0 && i < shapes.length && shapes[i] == null) {
                shapes[i] = liveShape(n.get(i));
            }
        }
        return answer(all, shapes, nonce, variant, indices);
    }

    /** CÔTÉ SERVEUR : réponse attendue, avec la forme des classes lue dans les fichiers du jar joueur. */
    public static byte[] expected(long nonce, int variant, int[] indices) {
        return answer(classes(), fileShapes(), nonce, variant, indices);
    }

    private static synchronized byte[][] fileShapes() {
        if (fileShapes == null) {
            List<byte[]> out = new ArrayList<>();
            for (byte[] c : classes()) {
                out.add(fileShape(c));
            }
            fileShapes = out;
        }
        return fileShapes.toArray(new byte[0][]);
    }

    /**
     * Calcul commun (Java pur, testé). La variante change l'ordre de lecture, le découpage et un masque dérivé du
     * nonce : chaque défi demande un calcul différent. La forme de chaque classe est mêlée juste après ses octets.
     */
    static byte[] answer(List<byte[]> all, byte[][] shapes, long nonce, int variant, int[] indices) {
        MessageDigest sha = sha256();
        sha.update(ByteBuffer.allocate(8).putLong(nonce ^ (0x9E3779B97F4A7C15L * (variant + 1))).array());

        int n = indices.length;
        for (int k = 0; k < n; k++) {
            int idx = (variant & 1) != 0 ? indices[n - 1 - k] : indices[k];
            if (idx < 0 || idx >= all.size()) {
                sha.update((byte) 0x7F);
                continue;
            }
            byte[] c = all.get(idx);
            if ((variant & 4) != 0) {
                c = masked(c, nonce);
            }
            if ((variant & 2) != 0) {
                int half = c.length / 2;
                sha.update(c, half, c.length - half);
                sha.update(c, 0, half);
            } else {
                sha.update(c);
            }
            byte[] shape = shapes != null && idx < shapes.length ? shapes[idx] : null;
            sha.update(shape != null ? shape : new byte[] {0x7E});
            sha.update(ByteBuffer.allocate(4).putInt(idx * (variant + 3)).array());
        }
        return sha.digest();
    }

    private static byte[] masked(byte[] in, long nonce) {
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (byte) (in[i] ^ (nonce >>> ((i & 7) * 8)));
        }
        return out;
    }

    // ------------------------------------------------------------------ forme d'une classe
    /**
     * Forme d'une classe lue dans son fichier : ses membres déclarés (« F nom descripteur », « M nom descripteur »),
     * triés, hachés. L'initialiseur statique n'est pas un membre visible par réflexion : il est ignoré.
     */
    public static byte[] fileShape(byte[] classBytes) {
        List<String> members = new ArrayList<>();
        try {
            new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    members.add("F " + name + " " + descriptor);
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!"<clinit>".equals(name)) {
                        members.add("M " + name + descriptor);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (RuntimeException e) {
            return shapeOf(List.of("! illisible"));
        }
        return shapeOf(members);
    }

    /**
     * Forme d'une classe TELLE QUE CHARGÉE dans le jeu (réflexion, sans l'initialiser). Une classe impossible à charger
     * ou à lire donne une forme d'erreur : la réponse au défi sera fausse.
     */
    public static byte[] liveShape(String className) {
        try {
            return liveShape(Class.forName(className, false, Fingerprint.class.getClassLoader()));
        } catch (Throwable t) {
            return shapeOf(List.of("! " + t.getClass().getName()));
        }
    }

    static byte[] liveShape(Class<?> c) {
        List<String> members = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            members.add("F " + f.getName() + " " + descriptor(f.getType()));
        }
        for (Method m : c.getDeclaredMethods()) {
            members.add("M " + m.getName() + descriptor(m.getParameterTypes(), m.getReturnType()));
        }
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            members.add("M <init>" + descriptor(k.getParameterTypes(), void.class));
        }
        return shapeOf(members);
    }

    private static byte[] shapeOf(List<String> members) {
        List<String> sorted = new ArrayList<>(members);
        sorted.sort(null);
        MessageDigest sha = sha256();
        for (String m : sorted) {
            sha.update(m.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) '\n');
        }
        return sha.digest();
    }

    static String descriptor(Class<?>[] params, Class<?> ret) {
        StringBuilder b = new StringBuilder("(");
        for (Class<?> p : params) {
            b.append(descriptor(p));
        }
        return b.append(')').append(descriptor(ret)).toString();
    }

    static String descriptor(Class<?> c) {
        if (c.isArray()) {
            return c.getName().replace('.', '/');
        }
        if (!c.isPrimitive()) {
            return "L" + c.getName().replace('.', '/') + ";";
        }
        if (c == int.class) {
            return "I";
        }
        if (c == long.class) {
            return "J";
        }
        if (c == boolean.class) {
            return "Z";
        }
        if (c == byte.class) {
            return "B";
        }
        if (c == char.class) {
            return "C";
        }
        if (c == short.class) {
            return "S";
        }
        if (c == float.class) {
            return "F";
        }
        if (c == double.class) {
            return "D";
        }
        return "V";
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
