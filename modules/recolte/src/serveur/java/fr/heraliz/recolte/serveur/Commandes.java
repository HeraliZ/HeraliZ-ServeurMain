package fr.heraliz.recolte.serveur;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heraliz.recolte.HeralizRecolte;
import fr.heraliz.recolte.entites.Arbre;
import fr.heraliz.recolte.entites.GisementServeur;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Commandes du staff (permission 2) : {@code /recolte info} (places, attentes, contrôles depuis le démarrage),
 * {@code /recolte placer <type>} (une ressource en plus, à l'endroit où l'on se tient : arbre, bouleau, epicea, pierre,
 * metal, rare), {@code /recolte retirer} (la ressource chargée la plus proche ; sa place repart ailleurs),
 * {@code /recolte vider} (toutes les places tirées de nouveau). Réglages relus par {@code /recharger recolte} (noyau).
 */
final class Commandes {
    private Commandes() {
    }

    /** Types de /recolte placer. */
    private static final List<String> TYPES = List.of("arbre", "bouleau", "epicea", "pierre", "metal", "rare");
    /** Rayon de /recolte retirer (blocs). */
    private static final double RAYON_RETIRER = 32.0;

    static void enregistrer(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("recolte").requires(s -> s.hasPermission(2))
                .then(Commands.literal("info").executes(Commandes::info))
                .then(Commands.literal("placer").then(Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(TYPES, b))
                        .executes(Commandes::placer)))
                .then(Commands.literal("retirer").executes(Commandes::retirer))
                .then(Commands.literal("vider").executes(Commandes::vider)));
        inscrireRechargement("recolte", Commandes::recharger);
    }

    /** Inscrit le rechargement dans /recharger du noyau ({@code fr.heraliz.noyau.api.Rechargements}), par réflexion. */
    private static void inscrireRechargement(String nom, ToIntFunction<CommandSourceStack> action) {
        try {
            Class.forName("fr.heraliz.noyau.api.Rechargements").getMethod("ajouter", String.class, ToIntFunction.class).invoke(null, nom, action);
        } catch (ReflectiveOperationException | LinkageError ex) {
            HeralizRecolte.LOG.warn("[recolte] /recharger du noyau introuvable : recolte.json n'est relu que quand il change ({})", ex.toString());
        }
    }

    private static MutableComponent t(String cle, String fr, Object... args) {
        return Component.translatableWithFallback(cle, fr, args);
    }

    private static void ok(CommandSourceStack s, Component c) {
        s.sendSuccess(() -> c, false);
    }

    /** Nom d'une sorte, au singulier (« Gisement de métal ») ou au pluriel (« Gisements de métal »). */
    private static MutableComponent nom(int sorte, boolean pluriel) {
        String[] fr = pluriel ? new String[]{"Arbres à abattre", "Gisements de pierre", "Gisements de métal", "Gisements de titane"}
                : new String[]{"Arbre à abattre", "Gisement de pierre", "Gisement de métal", "Gisement de titane"};
        return t("heraliz_recolte." + (pluriel ? "sortes." : "sorte.") + Reglages.SORTES[sorte], fr[sorte]);
    }

    // ------------------------------------------------------------------ /recolte info
    private static int info(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        MinecraftServer srv = s.getServer();
        Reglages r = Reglages.get();
        Places pl = Places.de(srv);
        long now = System.currentTimeMillis();
        ok(s, t("heraliz_recolte.cmd.info.titre", "Récolte : %1$s place(s) dans %2$s.", pl.liste().size(),
                Apparition.monde(srv).dimension().location().toString()).withStyle(ChatFormatting.GOLD));
        for (int sorte = 0; sorte < Reglages.SORTES.length; sorte++) {
            int posees = 0, attente = 0, pretes = 0, staff = 0;
            long prochaine = Long.MAX_VALUE;
            for (Places.Place p : pl.liste()) {
                if (p.sorte != sorte) {
                    continue;
                }
                if (p.bonus) {
                    staff++;
                }
                if (p.etat == Places.POSEE) {
                    posees++;
                } else {
                    attente++;
                    if (p.prete <= now) {
                        pretes++;
                    } else {
                        prochaine = Math.min(prochaine, p.prete);
                    }
                }
            }
            MutableComponent ligne = t("heraliz_recolte.cmd.info.sorte", "%1$s : %2$s posé(s), %3$s en attente dont %4$s prêt(s) (objectif %5$s).",
                    nom(sorte, true), posees, attente, pretes, r.ressource(sorte).aLaFois());
            if (staff > 0) {
                ligne.append(t("heraliz_recolte.cmd.info.staff", " Dont %s posé(s) par le staff.", staff));
            }
            if (prochaine != Long.MAX_VALUE) {
                ligne.append(t("heraliz_recolte.cmd.info.prochaine", " Prochain retour dans %s min.", (prochaine - now + 59_999L) / 60_000L));
            }
            ok(s, ligne);
        }
        ok(s, t("heraliz_recolte.cmd.info.attente", "Une ressource prête apparaît quand un joueur charge son coin de carte, sans personne à moins de %s blocs.",
                r.distanceJoueurs).withStyle(ChatFormatting.GRAY));
        ok(s, t("heraliz_recolte.cmd.info.controles", "Depuis le démarrage : %1$s place(s) contrôlée(s), %2$s ressource(s) posée(s).",
                Apparition.controles, Apparition.posees));
        MutableComponent refus = Component.empty();
        boolean un = false;
        for (int i = 0; i < Terrain.RAISONS.length; i++) {
            if (Apparition.REFUS[i] == 0) {
                continue;
            }
            if (un) {
                refus.append(Component.literal(", "));
            }
            un = true;
            refus.append(t("heraliz_recolte.cmd.info.raison", "%1$s (%2$s)", t("heraliz_recolte.raison." + Terrain.RAISONS[i], Terrain.RAISONS_FR[i]),
                    Apparition.REFUS[i]));
        }
        if (un) {
            ok(s, t("heraliz_recolte.cmd.info.refus", "Places refusées puis tirées ailleurs : %s.", refus).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    // ------------------------------------------------------------------ /recolte placer <type>
    private static int placer(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        String type = StringArgumentType.getString(c, "type").toLowerCase(Locale.ROOT);
        int sorte, variante = -1;
        switch (type) {
            case "arbre" -> sorte = Reglages.ARBRE;
            case "bouleau" -> {
                sorte = Reglages.ARBRE;
                variante = Arbre.BOULEAU;
            }
            case "epicea" -> {
                sorte = Reglages.ARBRE;
                variante = Arbre.EPICEA;
            }
            case "pierre" -> sorte = Reglages.PIERRE;
            case "metal" -> sorte = Reglages.METAL;
            case "rare" -> sorte = Reglages.RARE;
            default -> {
                s.sendFailure(t("heraliz_recolte.cmd.placer.inconnu", "Type inconnu : %s (arbre, bouleau, epicea, pierre, metal, rare).", type));
                return 0;
            }
        }
        ServerLevel l = s.getLevel();
        if (l != Apparition.monde(s.getServer())) {
            s.sendFailure(t("heraliz_recolte.cmd.placer.monde", "Les ressources ne se posent que dans %s.",
                    Apparition.monde(s.getServer()).dimension().location().toString()));
            return 0;
        }
        Entity e = Apparition.placer(l, sorte, variante, s.getPosition());
        if (e == null) {
            s.sendFailure(t("heraliz_recolte.cmd.placer.echec", "Ressource pas posée : refusée par le serveur ou un autre mod."));
            return 0;
        }
        ok(s, t("heraliz_recolte.cmd.placer.ok", "%s posé ici (en plus du compte : il ne revient pas une fois récolté).", nom(sorte, false)));
        return 1;
    }

    // ------------------------------------------------------------------ /recolte retirer
    private static int retirer(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        Entity e = Apparition.retirerProche(s.getLevel(), s.getPosition(), RAYON_RETIRER);
        if (e == null) {
            s.sendFailure(t("heraliz_recolte.cmd.retirer.aucune", "Aucune ressource chargée à moins de %s blocs.", (int) RAYON_RETIRER));
            return 0;
        }
        int sorte = e instanceof GisementServeur g ? g.sorte() : Reglages.ARBRE;
        ok(s, t("heraliz_recolte.cmd.retirer.ok", "%1$s retiré à %2$s bloc(s) : sa place repart ailleurs.", nom(sorte, false),
                (int) Math.round(Math.sqrt(e.distanceToSqr(s.getPosition())))));
        return 1;
    }

    // ------------------------------------------------------------------ /recolte vider
    private static int vider(CommandContext<CommandSourceStack> c) {
        int[] n = Apparition.vider(c.getSource().getServer());
        ok(c.getSource(), t("heraliz_recolte.cmd.vider.ok", "Récolte vidée : %1$s ressource(s) retirée(s), %2$s place(s) tirée(s) de nouveau ; "
                + "celles des coins de carte déchargés partiront à leur chargement.", n[0], n[1]));
        return 1;
    }

    // ------------------------------------------------------------------ /recharger recolte
    private static int recharger(CommandSourceStack s) {
        int lu = Reglages.charger();
        if (lu < 0) {
            s.sendFailure(t("heraliz_recolte.cmd.recharger.erreur", "recolte.json illisible, réglages en cours gardés : %s", String.valueOf(Reglages.derniereErreur())));
            return 0;
        }
        Apparition.reglagesChanges(s.getServer());
        Reglages r = Reglages.get();
        ok(s, lu == 0 ? t("heraliz_recolte.cmd.recharger.absent", "recolte.json absent : barème par défaut.")
                : t("heraliz_recolte.cmd.recharger.ok", "recolte.json relu : %1$s arbres, %2$s gisements de pierre, %3$s de métal et %4$s de titane à la fois.",
                r.ressource(Reglages.ARBRE).aLaFois(), r.ressource(Reglages.PIERRE).aLaFois(), r.ressource(Reglages.METAL).aLaFois(),
                r.ressource(Reglages.RARE).aLaFois()));
        return 1;
    }
}
