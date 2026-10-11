package fr.heraliz.loot;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Commandes (op) de la rotation du loot, sous /heralizloot :
 * <ul><li>zones — liste des zones : nom, nombre de conteneurs, actifs, réapparitions en attente ;</li>
 * <li>zone &lt;id&gt; info — détail d'une zone (et quelques actifs, cliquables pour s'y téléporter) ;</li>
 * <li>zone &lt;id&gt; retirer — re-tire au hasard les actifs de la zone (contenus en cours oubliés) ;</li>
 * <li>zone &lt;id&gt; part &lt;pourcent&gt;|auto — part des conteneurs de la zone remplis à la fois (0 : jamais rien ;
 * auto : celle du fichier ou de son tiers), gardée avec le monde ;</li>
 * <li>proche — zone et état du conteneur visé (ou du plus proche conteneur de zone).</li></ul>
 * L'identifiant d'une zone est le chemin de son fichier (ex. tcherno_q3, ou "tcherno/q3" entre guillemets).
 */
final class CommandesRotation {
    private CommandesRotation() {}

    private static final SuggestionProvider<CommandSourceStack> ZONES = (c, b) -> SharedSuggestionProvider.suggest(
            ZonesLoot.zones(c.getSource().getLevel().dimension()).keySet().stream().map(CommandesRotation::citer), b);

    static LiteralArgumentBuilder<CommandSourceStack> ajouter(LiteralArgumentBuilder<CommandSourceStack> racine) {
        return racine
                .then(Commands.literal("zones").executes(c -> zones(c.getSource())))
                .then(Commands.literal("zone").then(Commands.argument("zone", StringArgumentType.string()).suggests(ZONES)
                        .then(Commands.literal("info").executes(c -> info(c.getSource(), StringArgumentType.getString(c, "zone"))))
                        .then(Commands.literal("retirer").executes(c -> retirer(c.getSource(), StringArgumentType.getString(c, "zone"))))
                        .then(Commands.literal("part")
                                .then(Commands.literal("auto").executes(c -> part(c.getSource(), StringArgumentType.getString(c, "zone"), null)))
                                .then(Commands.argument("pourcent", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 100))
                                        .executes(c -> part(c.getSource(), StringArgumentType.getString(c, "zone"),
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "pourcent") / 100.0))))))
                .then(Commands.literal("proche").executes(c -> proche(c.getSource())));
    }

    private static String citer(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!StringReader.isAllowedInUnquotedString(s.charAt(i))) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return s;
    }

    /** Zone d'identifiant id : d'abord dans la dimension de la source, sinon dans les autres. */
    @Nullable
    private static ZonesLoot.Zone trouver(CommandSourceStack s, String id) {
        ZonesLoot.Zone z = ZonesLoot.zone(s.getLevel().dimension(), id);
        if (z != null) return z;
        for (Map<String, ZonesLoot.Zone> m : ZonesLoot.toutes().values()) {
            if (m.containsKey(id)) return m.get(id);
        }
        return null;
    }

    /** Comptes d'une zone : actifs, actifs ouverts, en attente, invalides, prochaine réapparition (ticks restants, -1). */
    private record Comptes(int actifs, int ouverts, int attente, int invalides, long prochaine) {}

    private static Map<String, Comptes> comptes(ServerLevel l) {
        RotationLoot e = RotationLoot.get(l);
        Object2IntOpenHashMap<String> a = new Object2IntOpenHashMap<>(), o = new Object2IntOpenHashMap<>(),
                p = new Object2IntOpenHashMap<>(), inv = new Object2IntOpenHashMap<>();
        Map<String, Long> prochaine = new java.util.HashMap<>();
        for (RotationLoot.Actif x : e.actifs.values()) {
            a.addTo(x.zone, 1);
            if (x.contenu != null) o.addTo(x.zone, 1);
        }
        long now = l.getGameTime();
        for (RotationLoot.Attente x : e.attentes) {
            p.addTo(x.zone(), 1);
            prochaine.merge(x.zone(), Math.max(0, x.heure() - now), Math::min);
        }
        for (long k : e.invalides.keySet()) {
            ZonesLoot.Zone z = ZonesLoot.zoneDe(l.dimension(), k);
            if (z != null) inv.addTo(z.id, 1);
        }
        Map<String, Comptes> m = new java.util.HashMap<>();
        for (String id : ZonesLoot.zones(l.dimension()).keySet()) {
            m.put(id, new Comptes(a.getInt(id), o.getInt(id), p.getInt(id), inv.getInt(id), prochaine.getOrDefault(id, -1L)));
        }
        return m;
    }

    private static Component duree(long ticks) {
        long s = ticks / 20;
        return s >= 60 ? Component.translatableWithFallback("heraliz_loot.duree.min", "%1$s min %2$s s", s / 60, s % 60)
                : Component.translatableWithFallback("heraliz_loot.duree.s", "%s s", s);
    }

    private static Component zoneInconnue(String id) {
        return Component.translatableWithFallback("heraliz_loot.cmd.zone.inconnue", "Zone inconnue : %s", id);
    }

    /** Pourcentage arrondi au dixième (« 20.0 »). */
    private static String pourcent(double part) {
        return String.valueOf(Math.round(part * 1000) / 10.0);
    }

    private static int zones(CommandSourceStack s) {
        int n = 0;
        for (Map.Entry<ResourceKey<Level>, Map<String, ZonesLoot.Zone>> d : ZonesLoot.toutes().entrySet()) {
            ServerLevel l = s.getServer().getLevel(d.getKey());
            if (l == null || d.getValue().isEmpty()) continue;
            Map<String, Comptes> c = comptes(l);
            s.sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.cmd.zones.titre", "%1$s zones de loot (%2$s)",
                    d.getValue().size(), d.getKey().location().toString()).withStyle(ChatFormatting.GOLD), false);
            for (ZonesLoot.Zone z : d.getValue().values()) {
                Comptes k = c.get(z.id);
                n++;
                MutableComponent ligne = Component.literal(z.id).withStyle(st -> st.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/heralizloot zone " + citer(z.id) + " info")));
                Component detail = k.invalides() > 0
                        ? Component.translatableWithFallback("heraliz_loot.cmd.zones.ligne_invalides",
                                "%1$s : %2$s conteneurs, %3$s actifs (%4$s ouverts), %5$s en attente, %6$s invalides",
                                z.nom, z.taille(), k.actifs(), k.ouverts(), k.attente(), k.invalides())
                        : Component.translatableWithFallback("heraliz_loot.cmd.zones.ligne",
                                "%1$s : %2$s conteneurs, %3$s actifs (%4$s ouverts), %5$s en attente",
                                z.nom, z.taille(), k.actifs(), k.ouverts(), k.attente());
                ligne.append(Component.literal(" — ").append(detail).withStyle(ChatFormatting.GRAY));
                s.sendSuccess(() -> ligne, false);
            }
        }
        if (n == 0) {
            s.sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.cmd.zones.aucune",
                    "Aucune zone de loot : fichiers data/heraliz_loot/zones/<zone>.json dans un pack de données du monde"), false);
        }
        return n;
    }

    private static int info(CommandSourceStack s, String id) {
        ZonesLoot.Zone z = trouver(s, id);
        ServerLevel l = z == null ? null : s.getServer().getLevel(z.dimension);
        if (z == null || l == null) {
            s.sendFailure(zoneInconnue(id));
            return 0;
        }
        Comptes k = comptes(l).get(z.id);
        int dispo = z.taille() - k.invalides();
        double part = z.proportion();
        s.sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.cmd.zone.titre", "Zone « %1$s » (%2$s, %3$s)",
                z.nom, z.id, z.dimension.location().toString()).withStyle(ChatFormatting.GOLD), false);
        Component origine = z.partForcee != null ? Component.translatableWithFallback("heraliz_loot.cmd.zone.origine.jeu", "réglée en jeu")
                : z.actifs == null ? Component.translatableWithFallback("heraliz_loot.cmd.zone.origine.config", "configuration")
                : Component.translatableWithFallback("heraliz_loot.cmd.zone.origine.zone", "zone");
        s.sendSuccess(() -> k.invalides() > 0
                ? Component.translatableWithFallback("heraliz_loot.cmd.zone.part_invalides",
                        "%1$s conteneurs (%2$s invalides) ; part d'actifs %3$s %% (%4$s) → %5$s voulus",
                        z.taille(), k.invalides(), pourcent(part), origine, z.cible(dispo))
                : Component.translatableWithFallback("heraliz_loot.cmd.zone.part", "%1$s conteneurs ; part d'actifs %2$s %% (%3$s) → %4$s voulus",
                        z.taille(), pourcent(part), origine, z.cible(dispo)), false);
        s.sendSuccess(() -> k.prochaine() >= 0
                ? Component.translatableWithFallback("heraliz_loot.cmd.zone.actifs_prochaine",
                        "%1$s actifs dont %2$s déjà ouverts ; %3$s en attente (prochaine dans %4$s)",
                        k.actifs(), k.ouverts(), k.attente(), duree(k.prochaine()))
                : Component.translatableWithFallback("heraliz_loot.cmd.zone.actifs", "%1$s actifs dont %2$s déjà ouverts ; %3$s en attente",
                        k.actifs(), k.ouverts(), k.attente()), false);
        RotationLoot e = RotationLoot.get(l);
        List<Component> exemples = new ArrayList<>();
        for (Long2ObjectMap.Entry<RotationLoot.Actif> x : e.actifs.long2ObjectEntrySet()) {
            if (!x.getValue().zone.equals(z.id)) continue;
            BlockPos b = BlockPos.of(x.getLongKey());
            RotationLoot.Actif a = x.getValue();
            Component etat = a.contenu == null ? Component.translatableWithFallback("heraliz_loot.cmd.etat.pas_ouvert", "pas encore ouvert")
                    : Component.translatableWithFallback("heraliz_loot.cmd.etat.objets", "%s objet(s)", a.contenu.nombre());
            exemples.add(Component.literal("[" + b.getX() + " " + b.getY() + " " + b.getZ() + "]").withStyle(st -> st.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + b.getX() + " " + (b.getY() + 1) + " " + b.getZ()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(a.nom + " — ").append(etat)))));
            if (exemples.size() >= 8) break;
        }
        if (!exemples.isEmpty()) {
            MutableComponent ligne = Component.translatableWithFallback("heraliz_loot.cmd.zone.exemples", "Actifs :").append(" ");
            for (Component c : exemples) ligne.append(c).append(" ");
            s.sendSuccess(() -> ligne, false);
        }
        return k.actifs();
    }

    private static int retirer(CommandSourceStack s, String id) {
        ZonesLoot.Zone z = trouver(s, id);
        ServerLevel l = z == null ? null : s.getServer().getLevel(z.dimension);
        if (z == null || l == null) {
            s.sendFailure(zoneInconnue(id));
            return 0;
        }
        int n = RotationLoot.get(l).retirerZone(l, z);
        s.sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.cmd.zone.retiree", "Zone « %1$s » : %2$s conteneurs actifs tirés au hasard",
                z.nom, n), true);
        return n;
    }

    private static int part(CommandSourceStack s, String id, @Nullable Double part) {
        ZonesLoot.Zone z = trouver(s, id);
        ServerLevel l = z == null ? null : s.getServer().getLevel(z.dimension);
        if (z == null || l == null) {
            s.sendFailure(zoneInconnue(id));
            return 0;
        }
        RotationLoot.get(l).regler(z, part);
        int voulus = z.cible(z.taille());
        s.sendSuccess(() -> part == null
                ? Component.translatableWithFallback("heraliz_loot.cmd.zone.part_auto",
                        "Zone « %1$s » : part %2$s %% (automatique) → %3$s conteneur(s) rempli(s) à la fois sur %4$s",
                        z.nom, pourcent(z.proportion()), voulus, z.taille())
                : Component.translatableWithFallback("heraliz_loot.cmd.zone.part_reglee",
                        "Zone « %1$s » : part %2$s %% → %3$s conteneur(s) rempli(s) à la fois sur %4$s",
                        z.nom, pourcent(z.proportion()), voulus, z.taille()), true);
        return voulus;
    }

    private static int proche(CommandSourceStack s) throws CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        ServerLevel l = s.getLevel();
        BlockPos cible = null;
        HitResult h = p.pick(8.0, 1.0f, false);
        if (h.getType() == HitResult.Type.BLOCK && h instanceof BlockHitResult bh) {
            BlockPos b = bh.getBlockPos();
            BlockState st = l.getBlockState(b);
            if (st.getBlock() instanceof PartieBlock) b = PartieBlock.principal(b, st);
            if (l.getBlockState(b).getBlock() instanceof ConteneurBlock) cible = b;
        }
        if (cible == null) {
            double mieux = 32 * 32;
            for (long k : ZonesLoot.index(l.dimension()).keySet()) {
                double d = p.distanceToSqr(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + 0.5, BlockPos.getZ(k) + 0.5);
                if (d < mieux) {
                    mieux = d;
                    cible = BlockPos.of(k);
                }
            }
        }
        if (cible == null) {
            s.sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.proche.aucun",
                    "Aucun conteneur visé, ni de conteneur de zone à moins de 32 blocs"));
            return 0;
        }
        BlockPos b = cible;
        long k = b.asLong();
        BlockState st = l.getBlockState(b);
        String enPlace = st.getBlock() instanceof ConteneurBlock cb ? cb.nom : BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
        ZonesLoot.Zone z = ZonesLoot.zoneDe(l.dimension(), k);
        RotationLoot e = RotationLoot.get(l);
        RotationLoot.Actif a = e.actifs.get(k);
        String ou = b.getX() + " " + b.getY() + " " + b.getZ();
        if (z == null) {
            s.sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.cmd.proche.hors",
                    "%1$s en %2$s : hors rotation (un conteneur y entre quand on le pose ou l'ouvre)", enPlace, ou)
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        String attendu = z.conteneur(k);
        Component etat;
        if (e.invalides.containsKey(k)) etat = Component.translatableWithFallback("heraliz_loot.cmd.proche.invalide",
                "INVALIDE (place vide : %s attendu, ou tout autre conteneur posé ici)", attendu);
        else if (a != null) etat = a.contenu == null ? Component.translatableWithFallback("heraliz_loot.cmd.proche.actif", "ACTIF, pas encore ouvert")
                : Component.translatableWithFallback("heraliz_loot.cmd.proche.actif_objets", "ACTIF, %s objet(s) dedans", a.contenu.nombre());
        else etat = Component.translatableWithFallback("heraliz_loot.cmd.proche.vide", "vide (inactif)");
        Comptes c = comptes(l).get(z.id);
        Component pose = e.places.containsKey(k) ? Component.translatableWithFallback("heraliz_loot.cmd.proche.pose", " [posé en jeu]") : Component.empty();
        Component autre = enPlace.equals(attendu) ? Component.empty()
                : Component.translatableWithFallback("heraliz_loot.cmd.proche.attendu", " (zone : %s)", attendu);
        s.sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.cmd.proche.ligne", "%1$s en %2$s%3$s%4$s : %5$s", enPlace, ou, pose, autre, etat)
                .withStyle(a != null ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        s.sendSuccess(() -> c.prochaine() >= 0
                ? Component.translatableWithFallback("heraliz_loot.cmd.proche.zone_prochaine",
                        "Zone « %1$s » (%2$s) : %3$s conteneurs, %4$s actifs, %5$s en attente (prochaine dans %6$s)",
                        z.nom, z.id, z.taille(), c.actifs(), c.attente(), duree(c.prochaine()))
                : Component.translatableWithFallback("heraliz_loot.cmd.proche.zone",
                        "Zone « %1$s » (%2$s) : %3$s conteneurs, %4$s actifs, %5$s en attente",
                        z.nom, z.id, z.taille(), c.actifs(), c.attente()), false);
        return a != null ? 1 : 0;
    }
}
