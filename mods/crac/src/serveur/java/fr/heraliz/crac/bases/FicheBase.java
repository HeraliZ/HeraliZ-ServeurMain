package fr.heraliz.crac.bases;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.build.world.Costs;
import fr.heraliz.crac.claim.UpkeepResource;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CFicheBase;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@code /base info} (CRac 1.13.0, demande du propriétaire du 19/10, maquette validée) : la fiche technique des bases,
 * pour tous les joueurs. Tout est lu à chaque fois dans les réglages du serveur ({@code crac-building.toml},
 * {@code crac-server.toml}) : un prix changé se voit aussitôt. Les prix en hérals sont ceux des marchands de HeraliZ
 * Quêtes (fichiers {@code config/heraliz_quetes/pnj/*.json}, type « achat », lus sans dépendance : sans eux, pas de prix).
 */
final class FicheBase {
    private static final Tier[] PALIERS = {Tier.TWIG, Tier.WOOD, Tier.STONE, Tier.METAL, Tier.ARMORED};

    private FicheBase() {
    }

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("base").then(Commands.literal("info").executes(c -> {
            envoyer(c.getSource().getPlayerOrException());
            return 1;
        })));
    }

    static void envoyer(ServerPlayer p) {
        List<S2CFicheBase.Pose> poses = new ArrayList<>();
        for (PieceKind k : PieceKind.values()) {
            poses.add(new S2CFicheBase.Pose(k.id, couts(Costs.place(k))));
        }
        List<S2CFicheBase.Palier> paliers = new ArrayList<>();
        for (Tier t : PALIERS) {
            List<S2CFicheBase.Cout> mur = t == Tier.TWIG ? List.of() : couts(Costs.upgrade(PieceKind.WALL, t));
            boolean meme = true;
            if (t != Tier.TWIG) {
                for (PieceKind k : PieceKind.values()) {
                    meme &= couts(Costs.upgrade(k, t)).equals(mur);
                }
            }
            int pv = BuildConfig.maxHp(t, PieceKind.WALL);
            UpkeepResource r = UpkeepResource.of(t);
            Item ent = Config.upkeepItem(r);
            S2CFicheBase.Cout entretien = new S2CFicheBase.Cout(ent == null || ent == net.minecraft.world.item.Items.AIR ? "" : id(ent), Config.upkeepPerPiece(r));
            paliers.add(new S2CFicheBase.Palier(t.id, mur, meme, pv, couts(Costs.repair(t)), entretien,
                combien(pv, Config.c4Damage.get()), combien(pv, Config.explosiveChargeDamage.get())));
        }
        S2CFicheBase m = new S2CFicheBase(poses, paliers, prix(), BuildConfig.repairPercent.get(),
            BuildConfig.repairSeconds.get().floatValue(), Config.upkeepHours.get().floatValue(), Config.c4Damage.get(),
            Config.explosiveChargeDamage.get());
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), m);
    }

    private static int combien(int pv, int degats) {
        return degats <= 0 ? 0 : (pv + degats - 1) / degats;
    }

    private static String id(Item i) {
        var k = ForgeRegistries.ITEMS.getKey(i);
        return k == null ? "" : k.toString();
    }

    /** Les objets d'une liste de coûts (pour un tag, son premier objet), sans les entrées invalides. */
    private static List<S2CFicheBase.Cout> couts(List<BuildCost> l) {
        List<S2CFicheBase.Cout> out = new ArrayList<>();
        for (BuildCost c : l) {
            ItemStack icone = c.icon();
            if (c.isValid() && !icone.isEmpty()) {
                out.add(new S2CFicheBase.Cout(id(icone.getItem()), c.count()));
            }
        }
        return out;
    }

    /** Prix de vente à l'unité des marchands (le moins cher si plusieurs le vendent). */
    private static Map<String, Integer> prix() {
        Map<String, Integer> out = new LinkedHashMap<>();
        Path dossier = FMLPaths.CONFIGDIR.get().resolve("heraliz_quetes").resolve("pnj");
        if (!Files.isDirectory(dossier)) {
            return out;
        }
        try (DirectoryStream<Path> fichiers = Files.newDirectoryStream(dossier, "*.json")) {
            for (Path f : fichiers) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    JsonElement e = JsonParser.parseReader(r);
                    if (!e.isJsonObject() || !e.getAsJsonObject().has("marchand")) {
                        continue;
                    }
                    JsonObject m = e.getAsJsonObject().getAsJsonObject("marchand");
                    if (!m.has("type") || !"achat".equals(m.get("type").getAsString()) || !m.has("articles")) {
                        continue;
                    }
                    for (JsonElement a : m.getAsJsonArray("articles")) {
                        JsonObject o = a.getAsJsonObject();
                        if (o.has("objet") && o.has("prix")) {
                            out.merge(o.get("objet").getAsString(), o.get("prix").getAsInt(), Math::min);
                        }
                    }
                } catch (IOException | RuntimeException ex) {
                    // fichier illisible : ses prix ne sont pas montrés
                }
            }
        } catch (IOException ex) {
            // dossier illisible : pas de prix
        }
        return out;
    }
}
