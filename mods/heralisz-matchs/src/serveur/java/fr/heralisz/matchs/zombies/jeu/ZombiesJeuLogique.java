package fr.heralisz.matchs.zombies.jeu;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Arenes;
import fr.heralisz.matchs.moteur.api.Carte;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.Groupe;
import fr.heralisz.matchs.moteur.api.Groupes;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.reseau.MatchsNet;
import fr.heralisz.matchs.zombies.jeu.api.Rarete;
import fr.heralisz.matchs.zombies.jeu.api.TypeBonus;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import fr.heralisz.matchs.zombies.jeu.net.PaquetsZombies;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Partie serveur (docs/METHODE_CLIENT_SERVEUR.md) du module d'entree du jeu zombies (appele par {@code Modules}) : evenements serveur (degats et morts des creatures,
 * butin coupe, touche d'echange de main interceptee, tirs et impacts TaCZ), donnees de jeu
 * ({@link ConfigZombies}), paquets, commandes {@code /zombies}, lancement d'une partie (groupe, Coffre, instance). Les
 * regles sont dans {@link PartieZombies}.
 * <p>Commandes :
 * <ul>
 *     <li>{@code /zombies} : ecran de lancement (groupe, carte, records, LANCER pour le chef) ;</li>
 *     <li>{@code /zombies lancer [carte]} : meme chose sans l'ecran ;</li>
 *     <li>{@code /zombies quitter} : abandonner la partie (Coffre rendu tout de suite) ;</li>
 *     <li>{@code /zombies records} : records du serveur ;</li>
 *     <li>{@code /zombies dev ...} (op 2, banc d'essai) : {@code etat}, {@code manche <n>}, {@code points <n>},
 *     {@code materiaux <n>}, {@code bonus <type>}, {@code courant}, {@code portes}, {@code tuer}, {@code aterre},
 *     {@code fin}, {@code annuler}, {@code speciale}, {@code appui} (comme F), {@code maintien <true|false>},
 *     {@code couteau} (comme un coup de melee), {@code tp <porte|fenetre|contre|dehors|arme|atout|atelier|ee|boite|
 *     courant|pap|depart> [index]}, {@code vue x y z lacet tangage}, {@code fenetre <i> <planches>}, {@code zombie <i>}
 *     (un zombie derriere la fenetre i), {@code sanskill <s>} (garde-fou), {@code boite <rarete>} (prochain tirage),
 *     {@code pap} / {@code atelier} / {@code ee} (poses de banc si la carte n'en a pas), {@code materiau [n]} (tas au
 *     sol), {@code arme} (arme tenue dans le journal).</li>
 * </ul>
 */
public final class ZombiesJeuLogique {
    private ZombiesJeuLogique() {
    }

    /** Anti-spam du bouton LANCER (ms). */
    private static final Map<UUID, Long> DERNIER_LANCER = new HashMap<>();

    /** Appele par Modules dans le constructeur du mod. */
    public static void construire(IEventBus modBus) {
        modBus.addListener(ZombiesJeuLogique::setup);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, ZombiesJeuLogique::blessure);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ZombiesJeuLogique::mort);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ZombiesJeuLogique::butin);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ZombiesJeuLogique::experience);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ZombiesJeuLogique::echangeMains);
        MinecraftForge.EVENT_BUS.addListener(ConfigZombies::rechargement);
        MinecraftForge.EVENT_BUS.addListener(ZombiesJeuLogique::arret);
    }


    private static void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            Tacz.ecouter((touchee, tireur, gunId, tete, base, multTete) -> {
                PartieZombies p = PartiesZombies.partieDe(touchee);
                return p != null ? p.surTir(touchee, tireur, gunId, tete, base, multTete) : base;
            });
            Tacz.ecouterImpacts((niveau, pos, face, tireur) -> {
                PartieZombies p = PartieZombies.a(niveau, pos);
                // 0.2.3 : relais 935 caches hors de la carte : la partie du tireur (meme niveau)
                if (p == null && tireur != null) {
                    PartieZombies q = PartiesZombies.partieDe(tireur);
                    if (q != null && q.niveau() == niveau) p = q;
                }
                if (p != null) p.impact(pos, face, tireur);
            });
            // acte IV de The Giant : boss N-07 (dependance douce, la classe n'est chargee que si le mod est la)
            if (net.minecraftforge.fml.ModList.get().isLoaded("heralisz_n07")) {
                try {
                    fr.heralisz.matchs.zombies.compat.N07Final.enregistrer();
                } catch (LinkageError | RuntimeException ex) {
                    HeraliszMatchs.LOG.error("[zombies] heralisz_n07 incompatible : acte IV sans boss", ex);
                }
            }
        });
    }

    // ------------------------------------------------------------------ evenements
    private static void blessure(LivingHurtEvent e) {
        if (e.getEntity().level().isClientSide || !ZombiesApi.estZombie(e.getEntity())) return;
        PartieZombies p = PartiesZombies.partieDe(e.getEntity());
        if (p != null) p.surZombieTouche(e);
    }

    private static void mort(LivingDeathEvent e) {
        if (e.isCanceled() || e.getEntity().level().isClientSide || !ZombiesApi.estZombie(e.getEntity())) return;
        PartieZombies p = PartiesZombies.partieDe(e.getEntity());
        if (p != null) p.surZombieMort(e.getEntity(), e.getSource());
    }

    private static void butin(LivingDropsEvent e) {
        if (ZombiesApi.estZombie(e.getEntity())) e.setCanceled(true);
    }

    private static void experience(LivingExperienceDropEvent e) {
        if (ZombiesApi.estZombie(e.getEntity())) e.setCanceled(true);
    }

    /** L'echange de main (F) est la touche d'interaction en partie : jamais d'echange, appui = interaction. */
    private static void echangeMains(LivingSwapItemsEvent.Hands e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PartieZombies p = PartiesZombies.partieDe(sp);
        if (p == null) return;
        e.setCanceled(true);
        p.interagir(sp);
    }

    private static void arret(ServerStoppedEvent e) {
        Records.vider();
        PartieZombies.viderActives();
        DERNIER_LANCER.clear();
    }

    // ------------------------------------------------------------------ reseau
    /** Touche d'interaction (paquet C2S, revalide). */
    public static void touche(ServerPlayer sp, boolean enfoncee) {
        PartieZombies p = PartiesZombies.partieDe(sp);
        if (p != null) p.touche(sp, enfoncee);
    }

    /** Coup de melee signale par le client (paquet C2S, revalide par la partie). */
    public static void coup(ServerPlayer sp) {
        PartieZombies p = PartiesZombies.partieDe(sp);
        if (p != null) p.coup(sp);
    }

    /** Fabrication a l'etabli (paquet C2S, revalide par la partie). */
    public static void fabriquer(ServerPlayer sp, int atelier, String recette) {
        PartieZombies p = PartiesZombies.partieDe(sp);
        if (p != null) p.fabriquer(sp, atelier, recette);
    }

    /** Ouvre l'ecran /zombies chez le joueur. */
    static void ouvrir(ServerPlayer sp) {
        Groupe g = Groupes.deOuSolo(sp);
        MinecraftServer s = sp.server;
        List<PaquetsZombies.Membre> membres = new ArrayList<>();
        for (UUID u : g.membres()) {
            ServerPlayer m = s.getPlayerList().getPlayer(u);
            String nom = m != null ? m.getGameProfile().getName() : nomHorsLigne(s, u);
            String refus = "";
            if (m == null) refus = "zombies.refus.hors_ligne";
            else if (MatchApi.enMatch(m)) refus = "zombies.refus.en_partie";
            else {
                Coffre.Refus r = Coffre.refus(m);
                if (r != null) refus = "matchs.coffre.refus." + r.name().toLowerCase(Locale.ROOT);
            }
            membres.add(new PaquetsZombies.Membre(u, nom, g.estChef(u), m != null, refus));
        }
        List<PaquetsZombies.CarteInfo> cartes = new ArrayList<>();
        for (Carte c : Arenes.cartesDeType(Carte.TYPE_ZOMBIES)) {
            cartes.add(new PaquetsZombies.CarteInfo(c.id(), c.nomFr(), c.nomEn(), Records.recordServeur(c.id()),
                    Records.recordServeurNoms(c.id()), Records.recordGroupe(c.id(), g.membres())));
        }
        MatchsNet.versJoueur(sp, new PaquetsZombies.OuvrirS2C(membres, g.estChef(sp.getUUID()), cartes));
    }

    private static String nomHorsLigne(MinecraftServer s, UUID u) {
        var prof = s.getProfileCache() == null ? null : s.getProfileCache().get(u).orElse(null);
        return prof != null ? prof.getName() : u.toString().substring(0, 8);
    }

    /**
     * LANCER : verifie le chef, le groupe et la carte, reserve l'instance, passe chaque membre par le Coffre, cree et
     * enregistre la partie. Tout refus est explique au joueur ; rien n'est garde a moitie (Coffres rendus si un depot
     * echoue).
     */
    public static void lancer(ServerPlayer sp, String carteId) {
        long now = System.currentTimeMillis();
        Long der = DERNIER_LANCER.get(sp.getUUID());
        if (der != null && now - der < 1500) return;
        DERNIER_LANCER.put(sp.getUUID(), now);
        if (MatchApi.enMatch(sp)) {
            refus(sp, "zombies.refus.en_partie");
            return;
        }
        Groupe g = Groupes.deOuSolo(sp);
        if (!g.estChef(sp.getUUID())) {
            refus(sp, "zombies.refus.chef");
            return;
        }
        Carte c = carteId == null || carteId.isEmpty() ? null : Arenes.carte(carteId);
        if (c == null || !Carte.TYPE_ZOMBIES.equals(c.type())) {
            List<Carte> l = Arenes.cartesDeType(Carte.TYPE_ZOMBIES);
            c = l.isEmpty() ? null : l.get(0);
        }
        if (c == null) {
            refus(sp, "zombies.refus.carte");
            return;
        }
        List<ServerPlayer> membres = g.enLigne(sp.server);
        for (ServerPlayer m : membres) {
            if (MatchApi.enMatch(m)) {
                refusMembre(sp, m, "zombies.refus.en_partie");
                return;
            }
            Coffre.Refus r = Coffre.refus(m);
            if (r != null) {
                refusMembre(sp, m, "matchs.coffre.refus." + r.name().toLowerCase(Locale.ROOT));
                return;
            }
        }
        Instance inst = Arenes.reserver(c.id());
        if (inst == null) {
            refus(sp, "zombies.refus.instance");
            return;
        }
        String id = "zombies-" + UUID.randomUUID().toString().substring(0, 6);
        List<ServerPlayer> deposes = new ArrayList<>();
        boolean banc = System.getProperty("heralisz.matchs.harness") != null;
        for (ServerPlayer m : membres) {
            boolean ok = Coffre.deposer(m, "zombies");
            if (!ok && banc && Coffre.refus(m) == null) {
                HeraliszMatchs.LOG.warn("[zombies] banc d'essai : Coffre provisoire ignore pour {}", m.getGameProfile().getName());
                ok = true;
            }
            if (!ok) {
                for (ServerPlayer d : deposes) Coffre.rendre(d);
                Arenes.liberer(inst);
                refusMembre(sp, m, "zombies.refus.coffre");
                return;
            }
            deposes.add(m);
        }
        PartieZombies partie = new PartieZombies(id, sp.server, c, membres);
        MatchApi.enregistrer(partie);
        for (ServerPlayer m : membres) {
            MatchApi.rejoindre(m, partie);
            Annonces.chat(m, Component.translatable(inst.prete() ? "zombies.lancement" : "zombies.lancement.attente", c.nom(true)));
        }
        partie.demarrer(inst);
        HeraliszMatchs.LOG.info("[zombies] {} lance par {} sur {} ({} joueurs)", id, sp.getGameProfile().getName(), c.id(), membres.size());
    }

    private static void refus(ServerPlayer sp, String cle) {
        Annonces.chat(sp, Component.translatable(cle));
    }

    private static void refusMembre(ServerPlayer chef, ServerPlayer membre, String cle) {
        Annonces.chat(chef, Component.translatable("zombies.refus.membre", membre.getGameProfile().getName(), Component.translatable(cle)));
    }

    // ------------------------------------------------------------------ commandes
    /** Appele sur RegisterCommandsEvent. */
    public static void commandes(CommandDispatcher<CommandSourceStack> d, CommandBuildContext ctx) {
        d.register(Commands.literal("zombies")
                .executes(c -> {
                    ouvrir(c.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("lancer")
                        .executes(c -> {
                            lancer(c.getSource().getPlayerOrException(), "");
                            return 1;
                        })
                        .then(Commands.argument("carte", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(idsCartes(), b))
                                .executes(c -> {
                                    lancer(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "carte"));
                                    return 1;
                                })))
                .then(Commands.literal("quitter").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    PartieZombies p = PartiesZombies.partieDe(sp);
                    if (p == null) {
                        refus(sp, "zombies.refus.pas_en_partie");
                        return 0;
                    }
                    p.abandonner(sp);
                    return 1;
                }))
                .then(Commands.literal("records").executes(c -> {
                    for (Carte ca : Arenes.cartesDeType(Carte.TYPE_ZOMBIES)) {
                        int r = Records.recordServeur(ca.id());
                        Annonces.retour(c.getSource(), Component.translatable("zombies.chat.records", ca.nom(true),
                                r, r > 0 ? Records.recordServeurNoms(ca.id()) : "-"));
                        int q = Records.recordQuete(ca.id());
                        if (q > 0) Annonces.retour(c.getSource(), Component.translatable("zombies.chat.records.quete", q, Records.recordQueteNoms(ca.id())));
                    }
                    return 1;
                }))
                .then(Commands.literal("dev").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("etat").executes(c -> {
                            int n = 0;
                            for (Partie p : MatchApi.parties()) {
                                if (p instanceof PartieZombies z) {
                                    String r = z.resume();
                                    Annonces.retour(c.getSource(), Component.literal(r));
                                    HeraliszMatchs.LOG.info("[zombies] etat {}", r);
                                    n++;
                                }
                            }
                            if (n == 0) Annonces.retour(c.getSource(), Component.literal("aucune partie de zombies"));
                            return n;
                        }))
                        .then(Commands.literal("manche").then(Commands.argument("n", IntegerArgumentType.integer(1, 200)).executes(c -> {
                            partie(c).devManche(IntegerArgumentType.getInteger(c, "n"));
                            return 1;
                        })))
                        .then(Commands.literal("points").then(Commands.argument("n", IntegerArgumentType.integer(0, 1_000_000)).executes(c -> {
                            partie(c).devPoints(c.getSource().getPlayerOrException().getUUID(), IntegerArgumentType.getInteger(c, "n"));
                            return 1;
                        })))
                        .then(Commands.literal("materiaux").then(Commands.argument("n", IntegerArgumentType.integer(0, 1000)).executes(c -> {
                            partie(c).devMateriaux(c.getSource().getPlayerOrException().getUUID(), IntegerArgumentType.getInteger(c, "n"));
                            return 1;
                        })))
                        .then(Commands.literal("materiau")
                                .executes(c -> {
                                    partie(c).devMateriauSol(c.getSource().getPlayerOrException(), 1);
                                    return 1;
                                })
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 20)).executes(c -> {
                                    partie(c).devMateriauSol(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n"));
                                    return 1;
                                })))
                        .then(Commands.literal("bonus").then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(idsBonus(), b))
                                .executes(c -> {
                                    TypeBonus t = TypeBonus.parId(StringArgumentType.getString(c, "type"));
                                    if (t == null) return 0;
                                    partie(c).devBonus(t, c.getSource().getPlayerOrException());
                                    return 1;
                                })))
                        .then(Commands.literal("courant").executes(c -> {
                            partie(c).devCourant();
                            return 1;
                        }))
                        .then(Commands.literal("giant").then(Commands.argument("action", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("fusibles", "courant", "ames", "lier", "flytrap", "relais"), b))
                                .executes(c -> {
                                    String r = partie(c).devGiant(StringArgumentType.getString(c, "action"));
                                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("[zombies] " + r), false);
                                    return 1;
                                })))
                        .then(Commands.literal("portes").executes(c -> {
                            partie(c).devPortes();
                            return 1;
                        }))
                        .then(Commands.literal("tp").then(Commands.argument("cible", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("porte", "fenetre", "contre", "dehors", "arme", "atout", "atelier",
                                        "ee", "boite", "courant", "pap", "depart"), b))
                                .executes(c -> partie(c).devTp(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "cible"), 0) ? 1 : 0)
                                .then(Commands.argument("index", IntegerArgumentType.integer(0, 99)).executes(c ->
                                        partie(c).devTp(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "cible"),
                                                IntegerArgumentType.getInteger(c, "index")) ? 1 : 0))))
                        .then(Commands.literal("tpmur").then(Commands.argument("i", IntegerArgumentType.integer(0, 99))
                                .then(Commands.argument("distance", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.3, 6))
                                        .executes(c -> partie(c).devTpMur(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "i"),
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "distance"), 0f) ? 1 : 0)
                                        .then(Commands.argument("lacet", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(-180, 180)).executes(c ->
                                                partie(c).devTpMur(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "i"),
                                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "distance"),
                                                        com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "lacet")) ? 1 : 0)))))
                        .then(Commands.literal("vue")
                                .then(Commands.argument("x", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                .then(Commands.argument("lacet", com.mojang.brigadier.arguments.FloatArgumentType.floatArg())
                                .then(Commands.argument("tangage", com.mojang.brigadier.arguments.FloatArgumentType.floatArg()).executes(c ->
                                        partie(c).devVue(c.getSource().getPlayerOrException(),
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "x"),
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "y"),
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "z"),
                                                com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "lacet"),
                                                com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "tangage")) ? 1 : 0)))))))
                        .then(Commands.literal("speciale").executes(c -> {
                            partie(c).devSpeciale();
                            return 1;
                        }))
                        .then(Commands.literal("appui").executes(c -> partie(c).interagir(c.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(Commands.literal("couteau").executes(c -> {
                            partie(c).coup(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("maintien").then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c -> {
                            partie(c).touche(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "on"));
                            return 1;
                        })))
                        .then(Commands.literal("fenetre").then(Commands.argument("i", IntegerArgumentType.integer(0, 99))
                                .then(Commands.argument("planches", IntegerArgumentType.integer(0, 6)).executes(c -> {
                                    partie(c).devFenetre(IntegerArgumentType.getInteger(c, "i"), IntegerArgumentType.getInteger(c, "planches"));
                                    return 1;
                                }))))
                        .then(Commands.literal("zombie").then(Commands.argument("i", IntegerArgumentType.integer(0, 99)).executes(c ->
                                partie(c).devZombieFenetre(IntegerArgumentType.getInteger(c, "i")) ? 1 : 0)))
                        .then(Commands.literal("pause").then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c -> {
                            partie(c).devPause(com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "on"));
                            return 1;
                        })))
                        .then(Commands.literal("effets").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            String r = partie(c).devEffets(sp);
                            HeraliszMatchs.LOG.info("[zombies] effets de {} : {}", sp.getGameProfile().getName(), r);
                            Annonces.retour(c.getSource(), Component.literal(r));
                            return 1;
                        }))
                        .then(Commands.literal("sanskill").then(Commands.argument("s", IntegerArgumentType.integer(0, 600)).executes(c -> {
                            partie(c).devSansKill(IntegerArgumentType.getInteger(c, "s"));
                            return 1;
                        })))
                        .then(Commands.literal("boite").then(Commands.argument("rarete", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(idsRaretes(), b))
                                .executes(c -> {
                                    partie(c).devBoite(Rarete.parId(StringArgumentType.getString(c, "rarete"), Rarete.RARE));
                                    return 1;
                                })))
                        .then(Commands.literal("pap").executes(c -> {
                            partie(c).devPap(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        // arme tenue marquee comme une ancienne arme amelioree (marqueur seul, sans niveau)
                        .then(Commands.literal("papancien").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            Annonces.retour(c.getSource(), Component.literal(partie(c).devPapAncien(sp)));
                            return 1;
                        }))
                        // banc : camos du Pack-a-Punch simules (heralisz_camo n'est pas charge au banc)
                        .then(Commands.literal("camopap").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            PackAPunch.camoForce = BoolArgumentType.getBool(c, "on");
                            HeraliszMatchs.LOG.info("[zombies] banc : camos du Pack-a-Punch simules {}", PackAPunch.camoForce);
                            return 1;
                        })))
                        .then(Commands.literal("atelier").executes(c -> {
                            partie(c).devAtelier(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("ee").executes(c -> {
                            partie(c).devEasterEgg(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("fabriquer").then(Commands.argument("recette", StringArgumentType.word()).executes(c -> {
                            partie(c).fabriquer(c.getSource().getPlayerOrException(), 0, StringArgumentType.getString(c, "recette"));
                            return 1;
                        })))
                        .then(Commands.literal("arme").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            String r = partie(c).resumeArme(sp);
                            HeraliszMatchs.LOG.info("[zombies] arme tenue par {} : {}", sp.getGameProfile().getName(), r);
                            Annonces.retour(c.getSource(), Component.literal(r));
                            return 1;
                        }))
                        .then(Commands.literal("aterre").executes(c -> {
                            partie(c).devATerre(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("tuer").executes(c -> {
                            partie(c).devTuer();
                            return 1;
                        }))
                        .then(Commands.literal("fin").executes(c -> {
                            partie(c).terminer(null);
                            return 1;
                        }))
                        .then(Commands.literal("annuler").executes(c -> {
                            partie(c).annuler();
                            return 1;
                        }))));
    }

    /** Partie de l'executant (ou la premiere partie de zombies s'il n'en a pas). */
    private static PartieZombies partie(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayer();
        PartieZombies p = sp == null ? null : PartiesZombies.partieDe(sp);
        if (p == null) p = premiere();
        if (p == null) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("aucune partie de zombies")).create();
        return p;
    }

    @Nullable
    private static PartieZombies premiere() {
        for (Partie p : MatchApi.parties()) if (p instanceof PartieZombies z) return z;
        return null;
    }

    private static List<String> idsCartes() {
        List<String> l = new ArrayList<>();
        for (Carte c : Arenes.cartesDeType(Carte.TYPE_ZOMBIES)) l.add(c.id());
        return l;
    }

    private static List<String> idsBonus() {
        List<String> l = new ArrayList<>();
        for (TypeBonus b : TypeBonus.values()) l.add(b.id());
        return l;
    }

    private static List<String> idsRaretes() {
        List<String> l = new ArrayList<>();
        for (Rarete r : Rarete.values()) l.add(r.id());
        return l;
    }
}
