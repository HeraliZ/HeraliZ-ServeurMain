package fr.heralisz.clans.saison;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heralisz.clans.model.Arbre;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.server.ClanService;
import fr.heralisz.clans.server.ClanStore;
import fr.heralisz.clans.server.Reponses;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * /saison (tous) : la saison, les points et les places de son clan ; /saison classement [pve|pvp|events|general].
 * Staff (permission 4) : /saison reset (puis confirmer : tous les clans a 0, nouvelle saison tout de suite), /saison fin
 * [jj/mm/aaaa hh:mm | annuler] (date de fin, heure de Paris), /saison points &lt;tag&gt;
 * &lt;pve|pvp|events&gt; &lt;+/-n&gt; &lt;motif&gt;, /saison journal &lt;tag&gt;, /saison recharger. Et /clan ameliorer
 * &lt;branche&gt; (arbre du clan, chef).
 */
public final class SaisonCommandes {
    private SaisonCommandes() {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(Saison.PARIS);

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        enregistrer(e.getDispatcher());
    }

    private static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("saison")
                .executes(SaisonCommandes::info)
                .then(Commands.literal("classement").executes(c -> classement(c, "general"))
                        .then(Commands.argument("categorie", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("general", "pve", "pvp", "events"), b))
                                .executes(c -> classement(c, StringArgumentType.getString(c, "categorie")))))
                // 25/10 : plus de « commencer » / « terminer » ; la saison tourne seule, deux commandes seulement
                .then(Commands.literal("reset").requires(s -> s.hasPermission(4))
                        .executes(SaisonCommandes::reset)
                        .then(Commands.literal("confirmer").executes(SaisonCommandes::resetConfirmer)))
                .then(Commands.literal("fin").requires(s -> s.hasPermission(4))
                        .executes(SaisonCommandes::finVoir)
                        .then(Commands.literal("annuler").executes(c -> {
                            Saison.programmerFin(0);
                            Reponses.ok(c.getSource(), Component.literal("Date de fin retirée : la saison continue sans fin prévue."));
                            return 1;
                        }))
                        .then(Commands.argument("date", StringArgumentType.greedyString()).executes(SaisonCommandes::finProgrammer)))
                .then(Commands.literal("points").requires(s -> s.hasPermission(4))
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .then(Commands.argument("categorie", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("pve", "pvp", "events"), b))
                                        .then(Commands.argument("delta", DoubleArgumentType.doubleArg(-1_000_000, 1_000_000))
                                                .then(Commands.argument("motif", StringArgumentType.greedyString())
                                                        .executes(SaisonCommandes::points))))))
                .then(Commands.literal("journal").requires(s -> s.hasPermission(4))
                        .then(Commands.argument("tag", StringArgumentType.word()).executes(c -> {
                            String tag = StringArgumentType.getString(c, "tag").toUpperCase(Locale.ROOT);
                            var src = c.getSource();
                            Saison.journalDe(tag, 15, l -> {
                                Reponses.ok(src, Component.literal("Journal de [" + tag + "] (" + l.size() + " dernières lignes) :"));
                                for (String x : l) Reponses.ligne(src, Component.literal(x));
                            });
                            return 1;
                        })))
                .then(Commands.literal("recharger").requires(s -> s.hasPermission(4)).executes(c -> {
                    Saison.recharger();
                    Reponses.ok(c.getSource(), Component.literal("Barème de la saison relu (config/heralisz_clans/saison.json)."));
                    return 1;
                })));
        d.register(Commands.literal("clan").then(Commands.literal("don").executes(c -> don(c, false))
                .then(Commands.literal("confirmer").executes(c -> don(c, true)))));
        d.register(Commands.literal("clan").then(Commands.literal("ameliorer")
                .then(Commands.argument("branche", StringArgumentType.word())
                        .suggests((c, b) -> {
                            List<String> l = new ArrayList<>();
                            for (Arbre.Branche x : Arbre.Branche.values()) l.add(x.id());
                            return SharedSuggestionProvider.suggest(l, b);
                        })
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Arbre.Branche b = Arbre.Branche.parId(StringArgumentType.getString(c, "branche"));
                            if (b == null) return Reponses.echec(c.getSource(), Component.translatable("msg.arbre.inconnue"));
                            return ClanService.ameliorer(p, b) ? 1 : 0;
                        }))));
    }

    /** /saison reset : demande de confirmation par celui qui tape la commande (30 s). */
    private static final java.util.Map<String, Long> RESET_DEMANDE = new java.util.HashMap<>();
    private static final DateTimeFormatter SAISIE = DateTimeFormatter.ofPattern("d/M/uuuu H:mm");

    private static int reset(CommandContext<CommandSourceStack> c) {
        RESET_DEMANDE.put(c.getSource().getTextName(), System.currentTimeMillis() + 30_000L);
        Reponses.ok(c.getSource(), Component.literal("ATTENTION : tous les clans vont repartir de 0 point (la saison " + Saison.numero()
                + " est archivée dans heralisz_clans/saisons/, une nouvelle commence tout de suite). Tape /saison reset confirmer dans les 30 s."));
        return 1;
    }

    private static int resetConfirmer(CommandContext<CommandSourceStack> c) {
        Long limite = RESET_DEMANDE.remove(c.getSource().getTextName());
        if (limite == null || System.currentTimeMillis() > limite) {
            return Reponses.echec(c.getSource(), Component.literal("Tape d'abord /saison reset, puis /saison reset confirmer dans les 30 s."));
        }
        Saison.reset(c.getSource().getTextName());
        Reponses.ok(c.getSource(), Component.literal("Points remis à 0 : la saison " + Saison.numero() + " commence."));
        return 1;
    }

    private static int finVoir(CommandContext<CommandSourceStack> c) {
        if (!Saison.active()) return Reponses.echec(c.getSource(), Component.literal("Aucune saison en cours (/saison reset pour en lancer une)."));
        Reponses.ok(c.getSource(), Component.literal(Saison.fin() > 0
                ? "La saison " + Saison.numero() + " se termine le " + DATE.format(Instant.ofEpochMilli(Saison.fin())) + " (heure de Paris)."
                : "La saison " + Saison.numero() + " n'a pas de date de fin."));
        Reponses.ligne(c.getSource(), Component.literal("Programmer : /saison fin 25/12/2026 20:00 · retirer : /saison fin annuler"));
        return 1;
    }

    private static int finProgrammer(CommandContext<CommandSourceStack> c) {
        String saisie = StringArgumentType.getString(c, "date").trim();
        long ms;
        try {
            ms = java.time.LocalDateTime.parse(saisie, SAISIE).atZone(Saison.PARIS).toInstant().toEpochMilli();
        } catch (java.time.format.DateTimeParseException ex) {
            return Reponses.echec(c.getSource(), Component.literal("Date illisible : écris par exemple /saison fin 25/12/2026 20:00 (heure de Paris)."));
        }
        if (ms <= System.currentTimeMillis()) return Reponses.echec(c.getSource(), Component.literal("Cette date est déjà passée."));
        if (!Saison.active()) return Reponses.echec(c.getSource(), Component.literal("Aucune saison en cours (/saison reset pour en lancer une)."));
        Saison.programmerFin(ms);
        Reponses.ok(c.getSource(), Component.literal("La saison " + Saison.numero() + " se terminera le " + DATE.format(Instant.ofEpochMilli(ms))
                + " : le classement sera figé et archivé."));
        return 1;
    }

    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> VENTE = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, new net.minecraft.resources.ResourceLocation("heraliz_composants", "vente"));

    /** Etiquette anti-copie posee par Spartan sur les objets de valeur suivis (com.Spartan.check.item.AntiDupe.CLE). */
    private static final String UID_SPARTAN = "spartan_uid";
    /** Don en attente de confirmation (plafond du jour des dons depasse) : joueur -> fin du delai (ms). */
    private static final java.util.Map<java.util.UUID, Long> DON_A_CONFIRMER = new java.util.HashMap<>();

    /**
     * Don au clan de l'objet en main : il disparait, le clan gagne de l'XP (31/10 : bareme par rarete et plafond du jour
     * dans niveaux.json, {@link fr.heralisz.clans.server.XpClan}). Seuls les objets de valeur a revendre (tag
     * heraliz_composants:vente) : aucun marchand ne les vend, aucune recette ne les fabrique (rien a acheter ou a fabriquer
     * pour creer de l'XP). Refuse en creatif (copie au bouton du milieu) et pour une copie d'un objet deja donne (numero
     * anti-copie de Spartan). Au-dela du plafond du jour, le joueur confirme ({@code /clan don confirmer}, 30 s). Seulement
     * hors de la carte, comme le coffre du clan (02/11, audit point 22 : avant, un objet Legendaire trouve sur la carte
     * etait mis a l'abri sans extraction) ; pas en match.
     */
    private static int don(CommandContext<CommandSourceStack> c, boolean confirme) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ClanStore s = ClanStore.get();
        Clan clan = s == null ? null : s.clanDe(p.getUUID());
        if (clan == null) return Reponses.echec(c.getSource(), Component.translatable("msg.pas_de_clan"));
        if (p.isCreative() || p.isSpectator()) {
            return Reponses.echec(c.getSource(), Component.translatableWithFallback("msg.don.creatif",
                    "Pas de don au clan en mode créatif ou spectateur."));
        }
        if (fr.heralisz.clans.server.EtatJoueur.surCarte(p) || fr.heralisz.clans.server.EtatJoueur.enMatch(p)) {
            return Reponses.echec(c.getSource(), Component.translatableWithFallback("msg.don.carte",
                    "Le don au clan se fait au spawn : rapporte d'abord l'objet (extraction)."));
        }
        net.minecraft.world.item.ItemStack st = p.getMainHandItem();
        int r = st.isEmpty() || !st.is(VENTE) ? -1 : Ponts.rarete(st);
        net.minecraft.resources.ResourceLocation id = st.isEmpty() ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(st.getItem());
        long parObjet = r < 0 || id == null ? 0 : fr.heralisz.clans.server.XpClan.donXp(id.toString(), r);
        if (parObjet <= 0) {
            return Reponses.echec(c.getSource(), Component.translatableWithFallback("msg.don.refus",
                    "Seuls les objets de valeur à revendre Épiques, Légendaires ou Uniques se donnent au clan (en main) : %s, %s et %s XP chacun.",
                    fr.heralisz.clans.server.XpClan.donRarete(3), fr.heralisz.clans.server.XpClan.donRarete(4), fr.heralisz.clans.server.XpClan.donRarete(5)));
        }
        long uid = st.getTag() != null && st.getTag().contains(UID_SPARTAN, net.minecraft.nbt.Tag.TAG_LONG) ? st.getTag().getLong(UID_SPARTAN) : 0L;
        if (s.donDejaFait(uid)) {
            fr.heralisz.clans.HeraliszClans.LOG.warn("[clans] [{}] don refuse : {} tient une copie d'un objet deja donne ({}, numero {})",
                    clan.tag, p.getGameProfile().getName(), id, uid);
            return Reponses.echec(c.getSource(), Component.translatableWithFallback("msg.don.copie",
                    "Cet objet a déjà été donné à un clan : une copie ne rapporte rien. Le staff est prévenu."));
        }
        int n = st.getCount();
        long propose = parObjet * n;
        long now = System.currentTimeMillis();
        long donne = fr.heralisz.clans.server.XpClan.apercu(clan, fr.heralisz.clans.model.SourceXp.DON, propose, now);
        Long attente = DON_A_CONFIRMER.remove(p.getUUID());
        if (donne < propose && (!confirme || attente == null || now > attente)) {
            DON_A_CONFIRMER.put(p.getUUID(), now + 30_000L);
            return Reponses.echec(c.getSource(), Component.translatableWithFallback("msg.don.plafond",
                    "Plafond du jour des dons atteint (%s XP) : cet objet ne rapporterait que %s XP au lieu de %s. Gardez-le pour demain, ou tapez /clan don confirmer dans les 30 s pour le donner quand même.",
                    fr.heralisz.clans.server.XpClan.plafond(fr.heralisz.clans.model.SourceXp.DON), donne, propose));
        }
        String nom = st.getHoverName().getString();
        p.getInventory().setItem(p.getInventory().selected, net.minecraft.world.item.ItemStack.EMPTY);
        s.noterDon(uid);
        long gagne = ClanService.ajouterXp(clan, fr.heralisz.clans.model.SourceXp.DON, propose, p.getUUID());
        ClanService.diffuserChat(clan, "msg.don.fait", p.getGameProfile().getName(), n, nom, gagne);
        fr.heralisz.clans.HeraliszClans.LOG.info("[clans] [{}] don de {} x {} ({}, numero {}) par {} (+{} XP sur {})", clan.tag, n, nom, id, uid,
                p.getGameProfile().getName(), gagne, propose);
        // l'objet n'existe plus : le fichier des clans est ecrit tout de suite (un plantage ne rend pas l'objet ET l'XP)
        p.getInventory().setChanged();
        s.sauvegarderMaintenant();
        if (DON_A_CONFIRMER.size() > 64) DON_A_CONFIRMER.values().removeIf(t -> t < now);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        if (!Saison.active()) {
            Reponses.ok(src, Component.translatableWithFallback("msg.saison.aucune", "Aucune saison en cours."));
            return 1;
        }
        if (Saison.fin() > 0) {
            Reponses.ok(src, Component.translatableWithFallback("msg.saison.info", "Saison %s · fin le %s", Saison.numero(),
                    DATE.format(Instant.ofEpochMilli(Saison.fin()))));
        } else {
            Reponses.ok(src, Component.translatableWithFallback("msg.saison.info_sans_fin", "Saison %s · pas de date de fin", Saison.numero()));
        }
        if (Saison.verrou()) Reponses.ligne(src, Saison.refusRejoindre());
        ClanStore s = ClanStore.get();
        Clan clan = s == null || !(src.getEntity() instanceof ServerPlayer p) ? null : s.clanDe(p.getUUID());
        if (clan == null) {
            Reponses.ligne(src, Component.translatableWithFallback("msg.saison.sans_clan", "Tu n'as pas de clan : pas de points de saison."));
            return 1;
        }
        if (!Ponts.aUnCoeur(clan.id)) {
            Reponses.ligne(src, Component.translatableWithFallback("msg.saison.sans_coeur",
                    "Ton clan n'a pas de cœur posé dans sa base : il ne gagne aucun point."));
            return 1;
        }
        double[] sc = Saison.scores(clan.id);
        int[] r = Saison.rangs(clan.id);
        Reponses.ligne(src, Component.translatableWithFallback("msg.saison.mon_clan", "[%s] PVE %s (%s) · PVP %s (%s) · Events %s (%s) · Général %s",
                clan.tag, Saison.entiers(sc[0]), place(r[0]), Saison.entiers(sc[1]), place(r[1]), Saison.entiers(sc[2]), place(r[2]), place(r[3])));
        return 1;
    }

    private static String place(int r) {
        return r <= 0 ? "-" : r + "e";
    }

    private static int classement(CommandContext<CommandSourceStack> c, String cat) {
        CommandSourceStack src = c.getSource();
        List<Saison.Ligne> l = new ArrayList<>(Saison.classement());
        switch (cat) {
            case "pve" -> l.sort(Comparator.comparingInt(Saison.Ligne::rPve));
            case "pvp" -> l.sort(Comparator.comparingInt(Saison.Ligne::rPvp));
            case "events" -> l.sort(Comparator.comparingInt(Saison.Ligne::rEvents));
            default -> cat = "general";
        }
        Reponses.ok(src, Component.translatableWithFallback("msg.saison.classement." + cat, "Classement " + cat));
        if (l.isEmpty()) Reponses.ligne(src, Component.translatableWithFallback("msg.saison.vide", "Aucun clan classé."));
        int n = 0;
        for (Saison.Ligne x : l) {
            if (++n > 10) break;
            int place = switch (cat) {
                case "pve" -> x.rPve();
                case "pvp" -> x.rPvp();
                case "events" -> x.rEvents();
                default -> x.rGeneral();
            };
            double v = switch (cat) {
                case "pve" -> x.pve();
                case "pvp" -> x.pvp();
                case "events" -> x.events();
                default -> x.total();
            };
            Reponses.ligne(src, Component.literal(place + ". [" + x.tag() + "] " + x.nom() + " · " + Saison.entiers(v)));
        }
        return 1;
    }

    private static int points(CommandContext<CommandSourceStack> c) {
        ClanStore s = ClanStore.get();
        String tag = StringArgumentType.getString(c, "tag");
        Clan clan = null;
        if (s != null) for (Clan x : s.tous()) if (x.tag.equalsIgnoreCase(tag)) clan = x;
        if (clan == null) return Reponses.echec(c.getSource(), Component.literal("Clan inconnu : " + tag));
        int cat = List.of(Saison.CATEGORIES).indexOf(StringArgumentType.getString(c, "categorie"));
        if (cat < 0) return Reponses.echec(c.getSource(), Component.literal("Catégorie : pve, pvp ou events."));
        double delta = DoubleArgumentType.getDouble(c, "delta");
        Saison.corriger(clan, cat, delta, StringArgumentType.getString(c, "motif"), c.getSource().getTextName());
        Reponses.ok(c.getSource(), Component.literal("[" + clan.tag + "] " + Saison.CATEGORIES[cat] + " " + (delta >= 0 ? "+" : "") + delta
                + " (noté dans le journal de la saison)."));
        return 1;
    }
}
