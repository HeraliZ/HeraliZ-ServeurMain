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

    private static String duree(long ticks) {
        long s = ticks / 20;
        return s >= 60 ? (s / 60) + " min " + (s % 60) + " s" : s + " s";
    }

    private static int zones(CommandSourceStack s) {
        int n = 0;
        for (Map.Entry<ResourceKey<Level>, Map<String, ZonesLoot.Zone>> d : ZonesLoot.toutes().entrySet()) {
            ServerLevel l = s.getServer().getLevel(d.getKey());
            if (l == null || d.getValue().isEmpty()) continue;
            Map<String, Comptes> c = comptes(l);
            s.sendSuccess(() -> Component.literal(d.getValue().size() + " zones de loot (" + d.getKey().location() + ")").withStyle(ChatFormatting.GOLD), false);
            for (ZonesLoot.Zone z : d.getValue().values()) {
                Comptes k = c.get(z.id);
                n++;
                MutableComponent ligne = Component.literal(z.id).withStyle(st -> st.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/heralizloot zone " + citer(z.id) + " info")));
                ligne.append(Component.literal(" — " + z.nom + " : " + z.taille() + " conteneurs, " + k.actifs() + " actifs ("
                        + k.ouverts() + " ouverts), " + k.attente() + " en attente" + (k.invalides() > 0 ? ", " + k.invalides() + " invalides" : ""))
                        .withStyle(ChatFormatting.GRAY));
                s.sendSuccess(() -> ligne, false);
            }
        }
        if (n == 0) {
            s.sendSuccess(() -> Component.literal("Aucune zone de loot : fichiers data/heraliz_loot/zones/<zone>.json dans un pack de données du monde"), false);
        }
        return n;
    }

    private static int info(CommandSourceStack s, String id) {
        ZonesLoot.Zone z = trouver(s, id);
        ServerLevel l = z == null ? null : s.getServer().getLevel(z.dimension);
        if (z == null || l == null) {
            s.sendFailure(Component.literal("Zone inconnue : " + id));
            return 0;
        }
        Comptes k = comptes(l).get(z.id);
        int dispo = z.taille() - k.invalides();
        double part = z.proportion();
        s.sendSuccess(() -> Component.literal("Zone « " + z.nom + " » (" + z.id + ", " + z.dimension.location() + ")").withStyle(ChatFormatting.GOLD), false);
        s.sendSuccess(() -> Component.literal(z.taille() + " conteneurs" + (k.invalides() > 0 ? " (" + k.invalides() + " invalides)" : "")
                + " ; part d'actifs " + Math.round(part * 1000) / 10.0 + " %"
                + (z.partForcee != null ? " (réglée en jeu)" : z.actifs == null ? " (configuration)" : " (zone)")
                + " → " + z.cible(dispo) + " voulus"), false);
        s.sendSuccess(() -> Component.literal(k.actifs() + " actifs dont " + k.ouverts() + " déjà ouverts ; " + k.attente() + " en attente"
                + (k.prochaine() >= 0 ? " (prochaine dans " + duree(k.prochaine()) + ")" : "")), false);
        RotationLoot e = RotationLoot.get(l);
        List<Component> exemples = new ArrayList<>();
        for (Long2ObjectMap.Entry<RotationLoot.Actif> x : e.actifs.long2ObjectEntrySet()) {
            if (!x.getValue().zone.equals(z.id)) continue;
            BlockPos b = BlockPos.of(x.getLongKey());
            RotationLoot.Actif a = x.getValue();
            String etat = a.contenu == null ? "pas encore ouvert" : a.contenu.nombre() + " objet(s)";
            exemples.add(Component.literal("[" + b.getX() + " " + b.getY() + " " + b.getZ() + "]").withStyle(st -> st.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + b.getX() + " " + (b.getY() + 1) + " " + b.getZ()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(a.nom + " — " + etat)))));
            if (exemples.size() >= 8) break;
        }
        if (!exemples.isEmpty()) {
            MutableComponent ligne = Component.literal("Actifs : ");
            for (Component c : exemples) ligne.append(c).append(" ");
            s.sendSuccess(() -> ligne, false);
        }
        return k.actifs();
    }

    private static int retirer(CommandSourceStack s, String id) {
        ZonesLoot.Zone z = trouver(s, id);
        ServerLevel l = z == null ? null : s.getServer().getLevel(z.dimension);
        if (z == null || l == null) {
            s.sendFailure(Component.literal("Zone inconnue : " + id));
            return 0;
        }
        int n = RotationLoot.get(l).retirerZone(l, z);
        s.sendSuccess(() -> Component.literal("Zone « " + z.nom + " » : " + n + " conteneurs actifs tirés au hasard"), true);
        return n;
    }

    private static int part(CommandSourceStack s, String id, @Nullable Double part) {
        ZonesLoot.Zone z = trouver(s, id);
        ServerLevel l = z == null ? null : s.getServer().getLevel(z.dimension);
        if (z == null || l == null) {
            s.sendFailure(Component.literal("Zone inconnue : " + id));
            return 0;
        }
        RotationLoot.get(l).regler(z, part);
        int voulus = z.cible(z.taille());
        s.sendSuccess(() -> Component.literal("Zone « " + z.nom + " » : part " + Math.round(z.proportion() * 1000) / 10.0 + " %"
                + (part == null ? " (automatique)" : "") + " → " + voulus + " conteneur(s) rempli(s) à la fois sur " + z.taille()), true);
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
            s.sendFailure(Component.literal("Aucun conteneur visé, ni de conteneur de zone à moins de 32 blocs"));
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
            s.sendSuccess(() -> Component.literal(enPlace + " en " + ou + " : hors rotation (un conteneur y entre quand on le pose ou l'ouvre)")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        String attendu = z.conteneur(k);
        String etat;
        if (e.invalides.containsKey(k)) etat = "INVALIDE (place vide : " + attendu + " attendu, ou tout autre conteneur posé ici)";
        else if (a != null) etat = "ACTIF, " + (a.contenu == null ? "pas encore ouvert" : a.contenu.nombre() + " objet(s) dedans");
        else etat = "vide (inactif)";
        Comptes c = comptes(l).get(z.id);
        String pose = e.places.containsKey(k) ? " [posé en jeu]" : "";
        s.sendSuccess(() -> Component.literal(enPlace + " en " + ou + pose + (enPlace.equals(attendu) ? "" : " (zone : " + attendu + ")") + " : " + etat)
                .withStyle(a != null ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        s.sendSuccess(() -> Component.literal("Zone « " + z.nom + " » (" + z.id + ") : " + z.taille() + " conteneurs, " + c.actifs() + " actifs, "
                + c.attente() + " en attente" + (c.prochaine() >= 0 ? " (prochaine dans " + duree(c.prochaine()) + ")" : "")), false);
        return a != null ? 1 : 0;
    }
}
