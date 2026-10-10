package fr.heraliz.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

/**
 * Raretés des objets côté serveur, en deux fichiers de {@code config/heraliz_loot/} :
 * <ul><li>{@code raretes.json} : la base, livrée avec le dépôt (refaite avec la table de loot) ; le jeu ne l'écrit
 * jamais (il la crée seulement si elle manque) ;</li>
 * <li>{@code raretes_jeu.json} : les choix faits en jeu par le staff (/rarete, {@link CommandesRarete}), qui passent
 * avant la base ; jamais dans le dépôt, donc jamais écrasés par la mise en ligne.</li></ul>
 * Lus au démarrage et par /rarete recharger ; la table fusionnée est envoyée à chaque joueur à la connexion et à tous
 * après un changement. Un fichier illisible n'est jamais écrasé : il est gardé en {@code .bad} et ignoré.
 */
public final class RaretesServeur {
    private RaretesServeur() {
    }

    /** Valeur des choix en jeu qui retire la rareté d'un objet (la rareté par défaut s'applique). */
    static final String AUCUNE = "aucune";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String AIDE = "Rareté de chaque objet : couleur légère derrière l'objet dans les cases. Niveaux : commun, peu_commun, "
            + "rare, epique, legendaire, unique (« aucune » = pas de rareté). « objets » : identifiant de l'objet ; « armes », "
            + "« accessoires », « munitions » : identifiants de TaCZ. Les choix faits en jeu (/rarete) vont dans raretes_jeu.json "
            + "et passent avant ce fichier.";

    /** Base (fichier du dépôt) et choix faits en jeu (valeurs : identifiant de rareté ou « aucune »). */
    private static Fichier base = Fichier.vide(), jeu = Fichier.vide();

    /** Contenu d'un fichier : rareté par défaut (null = absente) et, par sorte, identifiant -> rareté ou « aucune ». */
    private record Fichier(@Nullable String defaut, Map<RaretesObjets.Sorte, Map<String, String>> parSorte) {
        static Fichier vide() {
            Map<RaretesObjets.Sorte, Map<String, String>> m = new EnumMap<>(RaretesObjets.Sorte.class);
            for (RaretesObjets.Sorte s : RaretesObjets.Sorte.values()) m.put(s, new HashMap<>());
            return new Fichier(null, m);
        }
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> charger());
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) envoyer(sp);
        });
    }

    private static Path dossier() {
        return FMLPaths.CONFIGDIR.get().resolve(HeralizLoot.MODID);
    }

    /** Relit les deux fichiers (crée la base si elle manque). Rend false si l'un d'eux est illisible. */
    static synchronized boolean charger() {
        Path fb = dossier().resolve("raretes.json"), fj = dossier().resolve("raretes_jeu.json");
        if (!Files.exists(fb)) {
            Fichier f = new Fichier(Rarete.COMMUN.id, Fichier.vide().parSorte());
            ecrire(fb, f, AIDE);
        }
        Fichier b = lire(fb), j = Files.exists(fj) ? lire(fj) : Fichier.vide();
        base = b == null ? Fichier.vide() : b;
        jeu = j == null ? Fichier.vide() : j;
        RaretesObjets.remplacer(fusion());
        return b != null && j != null;
    }

    @Nullable
    private static Fichier lire(Path f) {
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            Fichier fi = Fichier.vide();
            String defaut = o.has("defaut") ? o.get("defaut").getAsString() : null;
            for (RaretesObjets.Sorte s : RaretesObjets.Sorte.values()) {
                if (!o.has(s.cle) || !o.get(s.cle).isJsonObject()) continue;
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(s.cle).entrySet()) {
                    String v = e.getValue().getAsString();
                    if (v.equalsIgnoreCase(AUCUNE) || Rarete.lire(v) != null) fi.parSorte().get(s).put(e.getKey(), v.toLowerCase());
                    else HeralizLoot.LOG.warn("{} : rareté inconnue « {} » pour {}", f.getFileName(), v, e.getKey());
                }
            }
            return new Fichier(defaut, fi.parSorte());
        } catch (Exception ex) {
            HeralizLoot.LOG.error("{} illisible, gardé en .bad et ignoré : {}", f.getFileName(), ex.toString());
            try {
                Files.copy(f, f.resolveSibling(f.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignore) {
                // la copie de secours est un plus
            }
            return null;
        }
    }

    /** La base, puis les choix faits en jeu par-dessus. */
    private static RaretesObjets.Table fusion() {
        String d = jeu.defaut() != null ? jeu.defaut() : base.defaut();
        Rarete defaut = d == null ? Rarete.COMMUN : d.equalsIgnoreCase(AUCUNE) ? null : Rarete.lire(d);
        if (d != null && defaut == null && !d.equalsIgnoreCase(AUCUNE)) defaut = Rarete.COMMUN;
        Map<RaretesObjets.Sorte, Map<String, Rarete>> m = new EnumMap<>(RaretesObjets.Sorte.class);
        for (RaretesObjets.Sorte s : RaretesObjets.Sorte.values()) {
            Map<String, Rarete> ms = new HashMap<>();
            for (Fichier f : new Fichier[]{base, jeu}) {
                for (Map.Entry<String, String> e : f.parSorte().getOrDefault(s, Map.of()).entrySet()) {
                    Rarete r = Rarete.lire(e.getValue());
                    if (r == null) ms.remove(e.getKey());
                    else ms.put(e.getKey(), r);
                }
            }
            m.put(s, ms);
        }
        return new RaretesObjets.Table(defaut, m);
    }

    private static void ecrire(Path f, Fichier fi, String aide) {
        JsonObject o = new JsonObject();
        o.addProperty("_aide", aide);
        if (fi.defaut() != null) o.addProperty("defaut", fi.defaut());
        for (RaretesObjets.Sorte s : RaretesObjets.Sorte.values()) {
            JsonObject js = new JsonObject();
            for (Map.Entry<String, String> e : new TreeMap<>(fi.parSorte().getOrDefault(s, Map.of())).entrySet()) js.addProperty(e.getKey(), e.getValue());
            o.add(s.cle, js);
        }
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(o, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            HeralizLoot.LOG.error("{} : écriture impossible : {}", f.getFileName(), ex.toString());
        }
    }

    /** Choix en jeu : cette rareté pour cette sorte d'objet (null : aucune rareté propre, la rareté par défaut s'applique). */
    static synchronized void definir(RaretesObjets.Cle cle, @Nullable Rarete r) {
        jeu.parSorte().get(cle.sorte()).put(cle.id(), r == null ? AUCUNE : r.id);
        appliquer();
    }

    /** Efface le choix fait en jeu pour cette sorte d'objet : la base s'applique de nouveau. Rend false s'il n'y en avait pas. */
    static synchronized boolean effacer(RaretesObjets.Cle cle) {
        boolean la = jeu.parSorte().get(cle.sorte()).remove(cle.id()) != null;
        if (la) appliquer();
        return la;
    }

    /** Choix en jeu de la rareté par défaut (null : aucune couleur pour les objets sans rareté). */
    static synchronized void definirDefaut(@Nullable Rarete r) {
        jeu = new Fichier(r == null ? AUCUNE : r.id, jeu.parSorte());
        appliquer();
    }

    /** La rareté vient-elle d'un choix fait en jeu ? */
    static synchronized boolean choisieEnJeu(RaretesObjets.Cle cle) {
        return jeu.parSorte().getOrDefault(cle.sorte(), Map.of()).containsKey(cle.id());
    }

    private static void appliquer() {
        ecrire(dossier().resolve("raretes_jeu.json"), jeu, "Choix faits en jeu avec /rarete (passent avant raretes.json). Ne pas mettre dans le dépôt.");
        RaretesObjets.remplacer(fusion());
        envoyerATous();
    }

    static void envoyer(ServerPlayer sp) {
        ReseauLoot.CANAL.send(PacketDistributor.PLAYER.with(() -> sp), new ReseauLoot.Raretes(RaretesObjets.table()));
    }

    static void envoyerATous() {
        ReseauLoot.CANAL.send(PacketDistributor.ALL.noArg(), new ReseauLoot.Raretes(RaretesObjets.table()));
    }
}
