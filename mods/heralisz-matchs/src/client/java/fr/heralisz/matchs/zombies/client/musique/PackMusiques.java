package fr.heralisz.matchs.zombies.client.musique;

import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pack de ressources « dossier » des musiques des zombies, charge par le mod cote client (toujours actif, en haut de
 * la liste) : l'utilisateur depose ses fichiers OGG dans {@code config/heralisz_matchs/musiques/} (dossier cree avec un
 * {@code LISEZMOI.txt}) ; chaque fichier reconnu ({@link #EMPLACEMENTS}) devient l'evenement sonore
 * {@code heralisz_matchs:zombies.<nom>} (en flux). Le {@code sounds.json} du pack est genere a chaque rechargement des
 * ressources d'apres les fichiers presents : un emplacement vide n'existe simplement pas (aucun avertissement, silence
 * propre). Rien n'est telecharge ni copie dans le jar du mod.
 */
@Mod.EventBusSubscriber(modid = HeraliszMatchs.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PackMusiques {
    private PackMusiques() {
    }

    public static final String ID = "heralisz_matchs_musiques";
    /** Noms de fichiers reconnus (sans .ogg) : 8 morceaux de playlist, ambiance, jingles et moments (v3 : The Giant). */
    public static final List<String> EMPLACEMENTS = List.of("musique_1", "musique_2", "musique_3", "musique_4", "musique_5", "musique_6",
            "musique_7", "musique_8", "ambiance", "manche_debut", "manche_fin", "speciale_debut", "speciale_fin", "partie_fin",
            "easter_egg", "pack_a_punch", "courant", "beauty_of_annihilation", "teleporteur", "annonce_chiens", "boss_n07");
    private static volatile Set<String> presents = Set.of();

    /** {@code <config>/heralisz_matchs/musiques}. */
    public static Path dossier() {
        return FMLPaths.CONFIGDIR.get().resolve("heralisz_matchs").resolve("musiques");
    }

    /** Vrai si le fichier de cet emplacement etait present au dernier chargement des ressources. */
    public static boolean present(String nom) {
        return presents.contains(nom);
    }

    public static Set<String> presents() {
        return presents;
    }

    /** Evenement sonore d'un emplacement : {@code heralisz_matchs:zombies.<nom>}. */
    public static ResourceLocation evenement(String nom) {
        return HeraliszMatchs.id("zombies." + nom);
    }

    @SubscribeEvent
    public static void ajouter(AddPackFindersEvent e) {
        if (e.getPackType() != PackType.CLIENT_RESOURCES) return;
        preparerDossier();
        e.addRepositorySource(consommateur -> {
            Pack p = Pack.readMetaAndCreate(ID, Component.translatable("heralisz_matchs.pack_musiques.titre"), true, Ressources::new,
                    PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
            if (p != null) consommateur.accept(p);
            else HeraliszMatchs.LOG.warn("[zombies] pack des musiques non cree");
        });
    }

    /** Cree le dossier et son LISEZMOI.txt (reecrit s'il manque ou s'il date d'une version precedente). */
    public static void preparerDossier() {
        try {
            Path d = dossier();
            Files.createDirectories(d);
            Path lisez = d.resolve("LISEZMOI.txt");
            if (!Files.exists(lisez) || !LISEZMOI.equals(Files.readString(lisez, StandardCharsets.UTF_8)))
                Files.writeString(lisez, LISEZMOI, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[zombies] dossier des musiques : {}", ex.toString());
        }
    }

    /** Fichiers presents dans le dossier (nom d'emplacement -> chemin). */
    static Map<String, Path> scruter() {
        Map<String, Path> out = new LinkedHashMap<>();
        Path d = dossier();
        for (String nom : EMPLACEMENTS) {
            Path f = d.resolve(nom + ".ogg");
            if (!Files.isRegularFile(f)) {
                // tolere les majuscules (Musique_1.OGG)
                try (var s = Files.list(d)) {
                    f = s.filter(x -> x.getFileName().toString().toLowerCase(Locale.ROOT).equals(nom + ".ogg")).findFirst().orElse(null);
                } catch (IOException | RuntimeException ex) {
                    f = null;
                }
            }
            try {
                if (f != null && Files.isRegularFile(f) && Files.size(f) > 64) out.put(nom, f);
            } catch (IOException ignored) {
            }
        }
        return out;
    }

    /** Ressources du pack : pack.mcmeta et sounds.json generes, fichiers OGG lus dans le dossier. */
    static final class Ressources extends AbstractPackResources {
        private final Map<String, Path> fichiers;
        private final byte[] sons;

        Ressources(String id) {
            super(id, true);
            fichiers = scruter();
            presents = Set.copyOf(fichiers.keySet());
            StringBuilder sb = new StringBuilder("{");
            boolean premier = true;
            for (String nom : fichiers.keySet()) {
                if (!premier) sb.append(',');
                premier = false;
                sb.append("\"zombies.").append(nom).append("\":{\"sounds\":[{\"name\":\"").append(HeraliszMatchs.MODID)
                        .append(":zbmusiques/").append(nom).append("\",\"stream\":true}]}");
            }
            sons = sb.append('}').toString().getBytes(StandardCharsets.UTF_8);
            HeraliszMatchs.LOG.info("[zombies] musiques du dossier {} : {}", dossier(), fichiers.isEmpty() ? "aucune" : fichiers.keySet());
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getRootResource(String... chemin) {
            if (chemin.length == 1 && "pack.mcmeta".equals(chemin[0])) {
                // description traduite chez le joueur (composant JSON {"translate": ...}, texte français de secours)
                byte[] b = ("{\"pack\":{\"pack_format\":15,\"description\":{\"translate\":\"heralisz_matchs.pack_musiques.description\","
                        + "\"fallback\":\"Vos musiques : config/heralisz_matchs/musiques\"}}}")
                        .getBytes(StandardCharsets.UTF_8);
                return () -> new ByteArrayInputStream(b);
            }
            return null;
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation rl) {
            if (type != PackType.CLIENT_RESOURCES || !HeraliszMatchs.MODID.equals(rl.getNamespace())) return null;
            String p = rl.getPath();
            if ("sounds.json".equals(p)) return () -> new ByteArrayInputStream(sons);
            if (p.startsWith("sounds/zbmusiques/") && p.endsWith(".ogg")) {
                Path f = fichiers.get(p.substring("sounds/zbmusiques/".length(), p.length() - 4));
                if (f != null) return () -> Files.newInputStream(f);
            }
            return null;
        }

        @Override
        public void listResources(PackType type, String espace, String chemin, PackResources.ResourceOutput sortie) {
            if (type != PackType.CLIENT_RESOURCES || !HeraliszMatchs.MODID.equals(espace)) return;
            if (!"sounds".equals(chemin) && !"sounds/zbmusiques".equals(chemin)) return;
            // appel par BiConsumer (methode Java, jamais obfusquee) plutot que par l'interface vanilla
            java.util.function.BiConsumer<net.minecraft.resources.ResourceLocation, net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream>> s = sortie;
            for (Map.Entry<String, Path> e : fichiers.entrySet()) {
                Path f = e.getValue();
                s.accept(HeraliszMatchs.id("sounds/zbmusiques/" + e.getKey() + ".ogg"), () -> Files.newInputStream(f));
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.CLIENT_RESOURCES ? Set.of(HeraliszMatchs.MODID) : Set.of();
        }

        @Override
        public void close() {
        }
    }

    private static final String LISEZMOI = """
            HeraliZ — musiques du mode Zombies
            ==================================

            Deposez ici vos fichiers OGG (Ogg Vorbis). Ils sont lus seulement sur votre ordinateur : rien n'est
            telecharge ni envoye au serveur, rien n'est copie dans le mod.

            Noms reconnus (minuscules, extension .ogg) :
              musique_1.ogg ... musique_8.ogg   playlist jouee pendant la partie (dans l'ordre, en boucle)
              ambiance.ogg                      fond sonore en boucle (remplace l'ambiance du laboratoire)
              manche_debut.ogg                  jingle de debut de manche
              manche_fin.ogg                    jingle de fin de manche
              speciale_debut.ogg                debut d'une manche des Rodeurs
              speciale_fin.ogg                  fin d'une manche des Rodeurs
              partie_fin.ogg                    fin de partie
              easter_egg.ogg                    morceau joue quand l'easter egg est termine
              pack_a_punch.ogg                  jingle quand une arme sort du Pack-a-Punch
              courant.ogg                       courant retabli
              beauty_of_annihilation.ogg        The Giant : musique des 3 bocaux a moelle (sinon « 115 »)
              teleporteur.ogg                   The Giant : son de la teleportation (remplace le son inclus)
              annonce_chiens.ogg                annonce d'une manche de chiens (si pas de speciale_debut.ogg)
              boss_n07.ogg                      The Giant : musique du combat contre N-07 (en boucle ; sinon celle du
                                                mod du boss, sinon « 115 »)

            Deja inclus dans le mod : la musique « 115 » (jouee en tete de la playlist, puis vos musique_1..8, et a
            l'easter egg) et des jingles classiques de debut/fin de manche et de boite mystere. Vos fichiers
            (manche_debut.ogg, easter_egg.ogg...) remplacent toujours les sons inclus. Pour retirer « 115 » ou les
            jingles classiques : musique_115 = false ou jingles_classiques = false dans
            config/heralisz_matchs-zombies-client.toml.

            Format : OGG Vorbis obligatoire (pas de MP3). Conversion avec ffmpeg par exemple :
              ffmpeg -i "mon morceau.mp3" -vn -c:a libvorbis -q:a 5 musique_1.ogg

            Apres avoir ajoute ou retire des fichiers : F3 + T en jeu (rechargement des ressources) ou relancer.

            Volume : curseur « Musique » des options du jeu (et « Ambiance » pour le fond),
            commande /zbmusique volume 0-100, ou volume_musique / volume_ambiance / mode dans
            config/heralisz_matchs-zombies-client.toml (mode : continue, manches ou aucune).
            /zbmusique liste : morceaux trouves ; /zbmusique suivante : passer au morceau suivant.
            """;
}
