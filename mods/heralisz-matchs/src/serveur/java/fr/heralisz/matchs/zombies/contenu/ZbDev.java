package fr.heralisz.matchs.zombies.contenu;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.matchs.moteur.api.Pose;
import fr.heralisz.matchs.zombies.contenu.bloc.ArmeMurBE;
import fr.heralisz.matchs.zombies.contenu.bloc.AtelierBE;
import fr.heralisz.matchs.zombies.contenu.bloc.AtoutBE;
import fr.heralisz.matchs.zombies.contenu.bloc.BoiteBE;
import fr.heralisz.matchs.zombies.contenu.bloc.EasterBE;
import fr.heralisz.matchs.zombies.contenu.bloc.FenetreBE;
import fr.heralisz.matchs.zombies.contenu.bloc.LevierBE;
import fr.heralisz.matchs.zombies.contenu.bloc.PapBE;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocMachine;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocObjetQuete;
import fr.heralisz.matchs.zombies.contenu.bloc.MachineBE;
import fr.heralisz.matchs.zombies.contenu.bloc.ObjetQueteBE;
import fr.heralisz.matchs.zombies.contenu.entite.BonusEntite;
import fr.heralisz.matchs.zombies.contenu.entite.MateriauEntite;
import fr.heralisz.matchs.zombies.contenu.entite.OuvertureEntite;
import fr.heralisz.matchs.zombies.contenu.entite.Rodeur;
import fr.heralisz.matchs.zombies.contenu.entite.ZombieLabo;
import fr.heralisz.matchs.zombies.jeu.api.Allure;
import fr.heralisz.matchs.zombies.jeu.api.PlanZombies;
import fr.heralisz.matchs.zombies.jeu.api.Rarete;
import fr.heralisz.matchs.zombies.jeu.api.TypeAtout;
import fr.heralisz.matchs.zombies.jeu.api.TypeBonus;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Commandes de developpement du contenu zombies ({@code /zbdev}, op niveau 2) pour le banc d'essai et les essais en
 * jeu, sans partie. Aucune regle du jeu ici (pas de points, pas d'achat) : seulement le decor et ses etats.
 * <ul>
 *     <li>{@code vitrine} : salle d'exposition devant le joueur (mur du fond avec fenetre barricadee, 5 machines
 *     d'atout, Pack-a-Punch, etabli, boite, arme a la craie, levier ; creatures, bonus, materiaux, objets d'easter
 *     egg) ;</li>
 *     <li>{@code portes} : mur perce de trois ouvertures (porte barree 4 x 3, grille 3 x 3, tas de debris 3 x 3) avec
 *     pancartes recto et verso ; {@code portes ouvrir} joue l'ouverture des trois ;</li>
 *     <li>{@code fenetre <n> [evenement]}, {@code fenetre demo} (zombie qui arrache les 6 planches puis enjambe),
 *     {@code boite <etat>}, {@code boite tirage <rarete>}, {@code courant on|off}, {@code servir}, {@code arme},
 *     {@code pap <etat>} (arme = objet en main), {@code atelier}, {@code easter <etat>}, {@code zombie <allure>
 *     [variante] [nombre]}, {@code sortie}, {@code rodeur}, {@code bonus <type>}, {@code materiau <n>},
 *     {@code nettoyer} ;</li>
 *     <li>D-053 : {@code decors [allumes]} : galerie des decors cuits de The Giant (tous les props, etats eteints ou allumes).</li>
 *     <li>v3 (The Giant) : {@code machines [etat]} : rangee des machines (armoire, pad A, pupitre, cuve, pupitre et tour du
 *     mainframe, rideau du Pack-a-Punch, fusible) dans un etat du storyboard valide (eteint, fusibles, courant, plein,
 *     liaison, lie, transport, recharge, tous, ouvert).</li>
 * </ul>
 * N'agit que sur les elements a moins de 28 blocs du joueur.
 */
public final class ZbDev {
    private ZbDev() {
    }

    private static final int RAYON = 28;

    public static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("zbdev").requires(s -> s.hasPermission(2))
                .then(Commands.literal("vitrine").executes(c -> vitrine(c.getSource().getPlayerOrException())))
                .then(Commands.literal("portes").executes(c -> portes(c.getSource().getPlayerOrException()))
                        .then(Commands.literal("ouvrir").executes(c -> ouvrirPortes(c.getSource().getPlayerOrException()))))
                .then(Commands.literal("fenetre")
                        .then(Commands.literal("demo").executes(ZbDev::demoFenetre))
                        .then(Commands.argument("planches", IntegerArgumentType.integer(0, 6))
                                .executes(c -> fenetre(c, "installee"))
                                .then(Commands.argument("evenement", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"installee", "arrachee", "reparee"}, b))
                                        .executes(c -> fenetre(c, StringArgumentType.getString(c, "evenement"))))))
                .then(Commands.literal("boite")
                        .then(Commands.literal("tirage").then(Commands.argument("rarete", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(noms(Rarete.values()), b))
                                .executes(ZbDev::tirage)))
                        .then(Commands.argument("etat", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(noms(ZombiesDecor.EtatBoite.values()), b))
                                .executes(ZbDev::boite)))
                .then(Commands.literal("courant").then(Commands.argument("actif", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"on", "off"}, b))
                        .executes(ZbDev::courant)))
                .then(Commands.literal("servir").executes(ZbDev::servir))
                .then(Commands.literal("arme").executes(ZbDev::arme))
                .then(Commands.literal("pap").then(Commands.argument("etat", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(noms(ZombiesDecor.EtatPap.values()), b))
                        .executes(ZbDev::pap)))
                .then(Commands.literal("atelier").executes(ZbDev::atelier))
                .then(Commands.literal("easter").then(Commands.argument("etat", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(noms(ZombiesDecor.EtatEtape.values()), b))
                        .executes(ZbDev::easter)))
                .then(Commands.literal("zombie").then(Commands.argument("allure", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(noms(Allure.values()), b))
                        .executes(c -> zombie(c, -1, 1, false))
                        .then(Commands.argument("variante", IntegerArgumentType.integer(-1, 3))
                                .executes(c -> zombie(c, IntegerArgumentType.getInteger(c, "variante"), 1, false))
                                .then(Commands.argument("nombre", IntegerArgumentType.integer(1, 24))
                                        .executes(c -> zombie(c, IntegerArgumentType.getInteger(c, "variante"),
                                                IntegerArgumentType.getInteger(c, "nombre"), false))))))
                .then(Commands.literal("sortie").executes(c -> zombie(c, -1, 1, true))
                        .then(Commands.argument("genre", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"sol", "coin", "conduit", "egout", "gravats", "tunnel"}, b))
                                .executes(ZbDev::sortieGenre)))
                .then(Commands.literal("rodeur").executes(ZbDev::rodeur))
                .then(Commands.literal("bonus").then(Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(noms(TypeBonus.values()), b))
                        .executes(ZbDev::bonus)))
                .then(Commands.literal("materiau").then(Commands.argument("quantite", IntegerArgumentType.integer(1, 20))
                        .executes(ZbDev::materiau)))
                .then(Commands.literal("nettoyer").executes(ZbDev::nettoyer))
                .then(Commands.literal("decors").executes(c -> decors(c.getSource().getPlayerOrException(), false))
                        .then(Commands.literal("allumes").executes(c -> decors(c.getSource().getPlayerOrException(), true))))
                .then(Commands.literal("machines").executes(c -> machines(c.getSource().getPlayerOrException(), "eteint"))
                        .then(Commands.argument("etat", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(ETATS_MACHINES, b))
                                .executes(c -> machines(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "etat"))))));
    }

    private static String[] noms(Enum<?>[] v) {
        return Arrays.stream(v).map(e -> e.name().toLowerCase(Locale.ROOT)).toArray(String[]::new);
    }

    /** Retour visible meme sous {@code sendCommandFeedback=false} (moteur : {@code Annonces.retour}). */
    private static void ok(CommandSourceStack s, String texte) {
        fr.heralisz.matchs.moteur.api.Annonces.retour(s, Component.literal("[zbdev] " + texte));
    }

    /** Blocs-entites de ce type a moins de RAYON blocs. */
    public static <T extends BlockEntity> List<T> proches(ServerLevel l, BlockPos centre, Class<T> type) {
        List<T> out = new ArrayList<>();
        int c0x = (centre.getX() - RAYON) >> 4, c1x = (centre.getX() + RAYON) >> 4;
        int c0z = (centre.getZ() - RAYON) >> 4, c1z = (centre.getZ() + RAYON) >> 4;
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                if (!l.hasChunk(cx, cz)) continue;
                LevelChunk ch = l.getChunk(cx, cz);
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (type.isInstance(be) && be.getBlockPos().distSqr(centre) <= RAYON * RAYON) out.add(type.cast(be));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ vitrine
    /**
     * Salle d'exposition devant le joueur (repere : u le long de sa droite, v devant lui) : sol de beton, mur du fond
     * en v = 10..11 ; fenetre 3 x 2 en u = -14..-12 (avec une embrasure dehors pour la demonstration), 5 machines
     * d'atout en u = -10, -8, -6, -4, -2, Pack-a-Punch en u = 1, etabli en u = 5, boite en u = 9, arme a la craie en
     * u = 12, levier en u = 14 (tous en v = 9, tournes vers le joueur). Derriere le joueur, tournes vers les machines :
     * zombies en v = -7 (u = -9..-3), rodeur en u = -1, objets d'easter egg en u = 3, 5, 7 ; bonus (u = -8..0) et
     * materiaux (u = 3, 5) en v = -4 ; mur arriere en v = -12. Salle fermee (plafond en y = 6, eclairage invisible
     * de niveau 11), ouverte au-dessus de l'embrasure de la fenetre.
     */
    public static int vitrine(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Direction f = p.getDirection();
        Direction r = f.getClockWise();
        Direction face = f.getOpposite();
        BlockPos o = p.blockPosition();
        nettoyer(l, o);
        ZbDemo.vider();
        for (int u = -17; u <= 17; u++) {
            for (int v = -13; v <= 15; v++) {
                l.setBlock(at(o, f, r, u, v, -1), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
                for (int y = 0; y <= 5; y++) l.setBlock(at(o, f, r, u, v, y), Blocks.AIR.defaultBlockState(), 2);
                // plafond (salle fermee) et eclairage invisible modere : les neons restent lisibles
                l.setBlock(at(o, f, r, u, v, 6), v <= 10 ? Blocks.GRAY_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                if (v <= 9 && Math.floorMod(u, 5) == 2 && Math.floorMod(v, 5) == 1) {
                    l.setBlock(at(o, f, r, u, v, 5), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 11), 2);
                }
            }
            for (int y = 0; y <= 5; y++) {
                l.setBlock(at(o, f, r, u, 10, y), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 2);
                l.setBlock(at(o, f, r, u, 11, y), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
                // mur arriere (fond des captures des creatures, bonus et easter egg)
                l.setBlock(at(o, f, r, u, -12, y), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 2);
            }
        }
        // fenetre 3 x 2 sur appui (u = -14..-12, y = 1..2) et son embrasure dehors (appui a y = 0, sol dehors)
        for (int u = -14; u <= -12; u++) {
            for (int y = 1; y <= 3; y++) l.setBlock(at(o, f, r, u, 11, y), Blocks.AIR.defaultBlockState(), 2);
            l.setBlock(at(o, f, r, u, 11, 0), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int v = 12; v <= 14; v++) l.setBlock(at(o, f, r, u, v, 0), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
        }
        DecorContenu.poserFenetre(l, BoundingBox.fromCorners(at(o, f, r, -14, 10, 1), at(o, f, r, -12, 10, 2)), f);
        // machines d'atout (5), Pack-a-Punch, etabli, boite, arme, levier
        TypeAtout[] types = TypeAtout.values();
        // 7 machines (v3 : Coup de mule, Tir d'elite) : 6 a gauche du Pack-a-Punch, la 7e a droite du levier
        int[] us = {-11, -9, -7, -5, -3, -1, 16};
        for (int i = 0; i < types.length && i < us.length; i++) DecorContenu.poserAtout(l, at(o, f, r, us[i], 9, 0), face, types[i], true);
        PapBE pap = DecorContenu.poserPap(l, at(o, f, r, 1, 9, 0), face);
        if (pap != null) pap.etat(ZombiesDecor.EtatPap.PRETE, ItemStack.EMPTY);
        DecorContenu.poserAtelier(l, at(o, f, r, 5, 9, 0), face);
        BoiteBE boite = DecorContenu.poserBoite(l, at(o, f, r, 9, 9, 0), face, ZombiesDecor.EtatBoite.FERMEE);
        if (boite != null) boite.etat(ZombiesDecor.EtatBoite.FERMEE);
        DecorContenu.poserArme(l, at(o, f, r, 12, 9, 1), face, "tacz:ak47");
        DecorContenu.poserLevier(l, at(o, f, r, 14, 9, 1), face);
        // derriere le joueur (tournes vers la rangee de machines), pour ne pas masquer les machines :
        // objets d'easter egg (radio et fragment sur un socle, interrupteur contre un pilier) en v = -7
        l.setBlock(at(o, f, r, 3, -7, 0), Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
        DecorContenu.poserEaster(l, at(o, f, r, 3, -7, 1), "radio", f);
        l.setBlock(at(o, f, r, 5, -7, 0), Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
        DecorContenu.poserEaster(l, at(o, f, r, 5, -7, 1), "objet", f);
        for (int y = 0; y <= 2; y++) l.setBlock(at(o, f, r, 7, -8, y), Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
        DecorContenu.poserEaster(l, at(o, f, r, 7, -7, 1), "interrupteur", f);
        // creatures sans IA en v = -7
        Allure[] allures = {Allure.MARCHEUR, Allure.COUREUR, Allure.SPRINTEUR, Allure.MARCHEUR};
        float yaw = f.toYRot();
        for (int i = 0; i < 4; i++) {
            ZombieLabo z = ZombieLabo.creer(l, allures[i], i, 5);
            placer(z, at(o, f, r, -9 + i * 2, -7, 0), yaw);
            z.setNoAi(true);
            l.addFreshEntity(z);
        }
        Rodeur rd = Rodeur.creer(l);
        placer(rd, at(o, f, r, -1, -7, 0), yaw);
        rd.setNoAi(true);
        l.addFreshEntity(rd);
        // bonus et materiaux en v = -4
        TypeBonus[] bonus = TypeBonus.values();
        for (int i = 0; i < bonus.length; i++) {
            BlockPos q = at(o, f, r, -8 + i * 2, -4, 0);
            l.addFreshEntity(BonusEntite.creer(l, q.getX() + 0.5, q.getY() + 0.6, q.getZ() + 0.5, bonus[i]));
        }
        for (int i = 0; i < 2; i++) {
            BlockPos q = at(o, f, r, 3 + i * 2, -4, 0);
            l.addFreshEntity(MateriauEntite.creer(l, q.getX() + 0.5, q.getY(), q.getZ() + 0.5, i == 0 ? 1 : 6));
        }
        ok(p.createCommandSourceStack(), "vitrine posee (face " + f.getName() + ")");
        return 1;
    }

    // ------------------------------------------------------------------ decors cuits de The Giant (D-053)
    /**
     * Galerie des decors de The Giant devant le joueur (sol de beton, tournes vers lui), a gauche puis a droite, sur deux
     * rangs ; le decor lointain (horloge, panneau, tete du robot) n'y est pas. {@code allumes} : generateurs, pylones,
     * lampe d'etabli allumes, boitier du Fly Trap ouvert, leviers en marche, porte ouverte.
     */
    private static int decors(ServerPlayer p, boolean allumes) {
        ServerLevel l = p.serverLevel();
        Direction f = p.getDirection();
        Direction r = f.getClockWise();
        Direction face = f.getOpposite();
        BlockPos o = p.blockPosition();
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String id : FormesDecoGiant.ids()) {
            if (!id.startsWith("zb_deco_") || id.equals("zb_deco_horloge") || id.equals("zb_deco_welcome") || id.equals("zb_deco_tete_robot")) continue;
            ids.add(id);
        }
        int largeurTotale = 0;
        for (String id : ids) largeurTotale += FormesDecoGiant.bloc(id).largeur() + 2;
        int demi = largeurTotale / 2 + 2;
        for (int u = -demi / 2 - 2; u <= demi / 2 + 2; u++) {
            for (int v = 3; v <= 40; v++) {
                l.setBlock(at(o, f, r, u, v, -1), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
                for (int y = 0; y <= 7; y++) l.setBlock(at(o, f, r, u, v, y), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        int u = -demi / 2, rang = 0, poses = 0;
        for (String id : ids) {
            FormesDecoGiant.Bloc b = FormesDecoGiant.bloc(id);
            if (!(ZbRegistres.decor(id) instanceof fr.heralisz.matchs.zombies.contenu.bloc.BlocDecoGiant bloc)) continue;
            if (u + b.largeur() > demi / 2) {
                u = -demi / 2;
                rang++;
            }
            // case maitresse (a = 0 : a gauche vu par le joueur, rang du fond) au fond du rang
            int v0 = 4 + rang * 12 + b.profondeur() - 1;
            BlockPos m = at(o, f, r, u, v0, 0);
            for (int pt = 0; pt < b.parts(); pt++) {
                int a = pt % b.largeur(), bb = (pt / b.largeur()) % b.hauteur(), c = pt / (b.largeur() * b.hauteur());
                Vec3 d = fr.heralisz.matchs.zombies.contenu.bloc.BlocDecoGiant.tourner(a, c, face);
                BlockPos q = m.offset((int) Math.round(d.x), bb, (int) Math.round(d.z));
                net.minecraft.world.level.block.state.BlockState s = bloc.defaultBlockState()
                        .setValue(fr.heralisz.matchs.zombies.contenu.bloc.BlocDecoGiant.FACING, face).setValue(bloc.part, pt);
                for (var bp : bloc.booleens) s = s.setValue(bp, allumes);
                l.setBlock(q, s, 2);
            }
            if (l.getBlockEntity(m) instanceof fr.heralisz.matchs.zombies.contenu.bloc.DecoAnimeBE be) {
                boolean porte = bloc instanceof fr.heralisz.matchs.zombies.contenu.bloc.BlocDecoAnime ba
                        && ba.genre == fr.heralisz.matchs.zombies.contenu.bloc.BlocDecoAnime.PORTE;
                if (porte) be.etat(allumes ? fr.heralisz.matchs.zombies.contenu.bloc.DecoAnimeBE.OUVERTE : fr.heralisz.matchs.zombies.contenu.bloc.DecoAnimeBE.FERMEE, 0, 0);
                else be.etat(allumes ? fr.heralisz.matchs.zombies.contenu.bloc.DecoAnimeBE.EN_MARCHE : fr.heralisz.matchs.zombies.contenu.bloc.DecoAnimeBE.DISPONIBLE,
                        allumes ? 400 : 0, allumes ? 400 : 0);
            }
            u += b.largeur() + 2;
            poses++;
        }
        ok(p.createCommandSourceStack(), "decors de The Giant : " + poses + " poses" + (allumes ? " (allumes)" : ""));
        return 1;
    }

    // ------------------------------------------------------------------ machines v3 de The Giant
    private static final String[] ETATS_MACHINES = {"eteint", "fusibles", "courant", "plein", "liaison", "lie", "transport", "recharge", "tous", "ouvert"};

    /**
     * Rangee des machines v3 devant le joueur (posee si absente, sur un sol de beton) : armoire du courant (u = -13), pad A
     * (u = -8) avec son pupitre (u = -5) et sa cuve (u = -3), pupitre du mainframe (u = 0), tour (u = 7), rideau du
     * Pack-a-Punch (u = 14), un fusible au sol (u = -11) ; puis met toutes ces machines dans l'etat demande (etats du
     * storyboard valide : eteint, fusibles, courant, plein, liaison, lie, transport, recharge, tous, ouvert).
     */
    private static int machines(ServerPlayer p, String etat) {
        ServerLevel l = p.serverLevel();
        Direction f = p.getDirection();
        Direction r = f.getClockWise();
        Direction face = f.getOpposite();
        BlockPos o = p.blockPosition();
        BlockPos armoire = at(o, f, r, -13, 8, 0), pad = at(o, f, r, -8, 8, 0), pupitre = at(o, f, r, -5, 7, 0), cuve = at(o, f, r, -3, 7, 0);
        BlockPos console = at(o, f, r, 0, 7, 0), tour = at(o, f, r, 7, 10, 0), rideau = at(o, f, r, 14, 8, 0), fusible = at(o, f, r, -11, 6, 0);
        if (!(l.getBlockEntity(pad) instanceof MachineBE)) {
            for (int u = -16; u <= 18; u++) {
                for (int v = 4; v <= 15; v++) {
                    l.setBlock(at(o, f, r, u, v, -1), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
                    for (int y = 0; y <= 14; y++) l.setBlock(at(o, f, r, u, v, y), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            poser(l, MachinesV3.Type.COURANT, ZbRegistres.COURANT3.get(), armoire, face);
            poser(l, MachinesV3.Type.TP_PAD, ZbRegistres.TP_PAD.get(), pad, face);
            poser(l, MachinesV3.Type.TP_PUPITRE, ZbRegistres.TP_PUPITRE.get(), pupitre, face);
            poser(l, MachinesV3.Type.CUVE_AMES, ZbRegistres.CUVE_AMES.get(), cuve, face);
            poser(l, MachinesV3.Type.MF_PUPITRE, ZbRegistres.MF_PUPITRE.get(), console, face);
            poser(l, MachinesV3.Type.MF_TOUR, ZbRegistres.MF_TOUR.get(), tour, face);
            l.setBlock(rideau, ZbRegistres.PAP_RIDEAU.get().defaultBlockState().setValue(BlocMachine.FACING, face), 2);
            l.setBlock(fusible, ZbRegistres.OBJET_QUETE.get().defaultBlockState().setValue(BlocObjetQuete.FACING, face), 2);
            if (l.getBlockEntity(tour) instanceof MachineBE t) t.arrivee(r.getStepX() * -3, 0, r.getStepZ() * -3);
        }
        boolean on = !etat.equals("eteint") && !etat.equals("fusibles");
        ZombiesDecor.EtatPad ep = switch (etat) {
            case "eteint", "fusibles" -> ZombiesDecor.EtatPad.ETEINT;
            case "courant" -> ZombiesDecor.EtatPad.ALIMENTE;
            case "plein" -> ZombiesDecor.EtatPad.CHARGE;
            case "liaison" -> ZombiesDecor.EtatPad.LIAISON;
            case "transport" -> ZombiesDecor.EtatPad.TRANSPORT;
            case "recharge" -> ZombiesDecor.EtatPad.RECHARGE;
            default -> ZombiesDecor.EtatPad.LIE;
        };
        int duree = switch (ep) {
            case LIAISON -> 600;
            case TRANSPORT -> 30;
            case RECHARGE -> 600;
            default -> 0;
        };
        int lies = switch (etat) {
            case "lie", "transport", "recharge" -> 1;
            case "tous", "ouvert" -> 7;
            default -> 0;
        };
        if (l.getBlockEntity(armoire) instanceof MachineBE b) {
            b.bits(etat.equals("eteint") ? 0 : 7);
            b.etat(on ? 1 : 0, 0);
        }
        for (BlockPos q : new BlockPos[]{pad, pupitre}) if (l.getBlockEntity(q) instanceof MachineBE b) b.etat(ep.ordinal(), duree);
        if (l.getBlockEntity(cuve) instanceof MachineBE b) b.niveau(switch (etat) {
            case "eteint", "fusibles" -> 0F;
            case "courant" -> 0.45F;
            default -> 1F;
        }, true);
        if (l.getBlockEntity(console) instanceof MachineBE b) {
            b.etat(on ? 1 : 0, 0);
            b.bits(lies | (ep == ZombiesDecor.EtatPad.LIAISON ? 1 << 3 : 0));
        }
        if (l.getBlockEntity(tour) instanceof MachineBE b) {
            b.etat(on ? 1 : 0, Integer.bitCount(lies));
            b.bits(Integer.bitCount(lies) | (ep == ZombiesDecor.EtatPad.LIAISON ? 0x10 : 0), false);
            if (ep == ZombiesDecor.EtatPad.TRANSPORT) b.impulsion();
        }
        if (l.getBlockEntity(rideau) instanceof MachineBE b) b.etat(etat.equals("ouvert") ? 1 : etat.equals("tous") ? 0 : 0, 0);
        if (l.getBlockEntity(fusible) instanceof ObjetQueteBE b) b.etat(etat.equals("eteint") ? ObjetQueteBE.VISIBLE : ObjetQueteBE.PRIS, "");
        ok(p.createCommandSourceStack(), "machines v3 : etat " + etat);
        return 1;
    }

    private static void poser(ServerLevel l, MachinesV3.Type t, BlocMachine b, BlockPos pos, Direction face) {
        MachinesV3.poser(l, t, pos, face, b.defaultBlockState().setValue(BlocMachine.FACING, face), 2);
        if (l.getBlockEntity(pos) instanceof MachineBE be) be.reinitialiser();
    }

    /** Case (u le long de la droite du joueur, v devant lui, y au-dessus de ses pieds). */
    private static BlockPos at(BlockPos o, Direction f, Direction r, int u, int v, int y) {
        return o.offset(r.getStepX() * u + f.getStepX() * v, y, r.getStepZ() * u + f.getStepZ() * v);
    }

    private static void placer(Entity e, BlockPos p, float yaw) {
        e.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
    }

    // ------------------------------------------------------------------ portes
    private static final String[] TYPES_PORTES = {"porte", "grille", "debris"};
    private static final int[][] OUVERTURES = {{-10, -7}, {-1, 1}, {7, 9}};
    private static final int[] COUTS = {750, 1000, 1250};

    /**
     * Mur perce de trois ouvertures en v = 6 (u = -10..-7 porte 4 x 3, u = -1..1 grille 3 x 3, u = 7..9 debris 3 x 3),
     * avec pancartes recto (cote joueur, a droite de l'ouverture, a hauteur des yeux) et verso.
     */
    public static int portes(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Direction f = p.getDirection();
        Direction r = f.getClockWise();
        BlockPos o = p.blockPosition();
        nettoyer(l, o);
        for (int u = -14; u <= 14; u++) {
            for (int v = -3; v <= 12; v++) {
                l.setBlock(at(o, f, r, u, v, -1), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
                for (int y = 0; y <= 5; y++) l.setBlock(at(o, f, r, u, v, y), Blocks.AIR.defaultBlockState(), 2);
                l.setBlock(at(o, f, r, u, v, 6), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
                if (v != 6 && Math.floorMod(u, 5) == 2 && Math.floorMod(v, 4) == 0) {
                    l.setBlock(at(o, f, r, u, v, 5), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 10), 2);
                }
            }
            for (int y = 0; y <= 4; y++) l.setBlock(at(o, f, r, u, 6, y), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 2);
        }
        for (int k = 0; k < 3; k++) {
            PlanZombies.Porte po = porteBanc(o, f, r, k);
            DecorContenu.poserObstacle(l, po, po.pancarteVerso(), true);
        }
        ok(p.createCommandSourceStack(), "portes posees (face " + f.getName() + ")");
        return 1;
    }

    private static PlanZombies.Porte porteBanc(BlockPos o, Direction f, Direction r, int k) {
        BlockPos a = at(o, f, r, OUVERTURES[k][0], 6, 0), b = at(o, f, r, OUVERTURES[k][1], 6, 2);
        BoundingBox bb = BoundingBox.fromCorners(a, b);
        BlockPos pc = at(o, f, r, OUVERTURES[k][1] + 1, 5, 1);
        Pose recto = new Pose(pc.getX() + 0.5, pc.getY(), pc.getZ() + 0.5, f.getOpposite().toYRot());
        BlockPos pv = at(o, f, r, OUVERTURES[k][0] - 1, 7, 1);
        Pose verso = new Pose(pv.getX() + 0.5, pv.getY(), pv.getZ() + 0.5, f.toYRot());
        return new PlanZombies.Porte("B" + k, "zone", List.of("zone"), COUTS[k], TYPES_PORTES[k].equals("debris") ? "eboulis" : "porte",
                PlanZombies.aabb(bb), bb, TYPES_PORTES[k], recto, "Banc " + TYPES_PORTES[k], verso, false, false);
    }

    private static int ouvrirPortes(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Direction f = p.getDirection();
        Direction r = f.getClockWise();
        BlockPos o = p.blockPosition();
        // la commande est lancee depuis la meme case que « portes » (memes reperes)
        for (int k = 0; k < 3; k++) {
            PlanZombies.Porte po = porteBanc(o, f, r, k);
            BoundingBox bb = po.boite();
            for (BlockPos q : BlockPos.betweenClosed(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ())) {
                if (!l.getBlockState(q).isAir()) l.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
            }
            DecorContenu.ouvrirEffet(l, po, true);
        }
        ok(p.createCommandSourceStack(), "portes ouvertes");
        return 1;
    }

    // ------------------------------------------------------------------ etats
    private static int fenetre(CommandContext<CommandSourceStack> c, String ev) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = IntegerArgumentType.getInteger(c, "planches");
        ZombiesDecor.EvenementFenetre e = switch (ev) {
            case "arrachee" -> ZombiesDecor.EvenementFenetre.ARRACHEE;
            case "reparee" -> ZombiesDecor.EvenementFenetre.REPAREE;
            default -> ZombiesDecor.EvenementFenetre.INSTALLEE;
        };
        List<FenetreBE> l = proches(p.serverLevel(), p.blockPosition(), FenetreBE.class);
        for (FenetreBE f : l) f.planches(n, e);
        ok(c.getSource(), l.size() + " fenetre(s) : " + n + " planches (" + ev + ")");
        return l.size();
    }

    /**
     * Demonstration : un zombie dans l'embrasure de la fenetre la plus proche arrache les 6 planches puis enjambe
     * (meme rythme et meme deplacement que le jeu).
     */
    private static int demoFenetre(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel l = p.serverLevel();
        FenetreBE best = null;
        double bd = Double.MAX_VALUE;
        for (FenetreBE f : proches(l, p.blockPosition(), FenetreBE.class)) {
            double d = f.getBlockPos().distSqr(p.blockPosition());
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        if (best == null) {
            c.getSource().sendFailure(Component.literal("aucune fenetre proche"));
            return 0;
        }
        Direction ext = best.exterieur();
        BlockPos m = best.getBlockPos();
        int lx = ext.getAxis() == Direction.Axis.Z ? best.largeur() : 1, lz = ext.getAxis() == Direction.Axis.X ? best.largeur() : 1;
        Vec3 centre = new Vec3(m.getX() + lx / 2.0, m.getY() + best.hauteur() / 2.0, m.getZ() + lz / 2.0);
        // dehors : la case de l'embrasure (pieds a hauteur de l'appui) ; dedans : devant la fenetre, au sol de la salle
        Vec3 dehors = new Vec3(centre.x + ext.getStepX() * 1.0, m.getY(), centre.z + ext.getStepZ() * 1.0);
        Vec3 dedans = new Vec3(centre.x - ext.getStepX() * 1.4, m.getY() - 1, centre.z - ext.getStepZ() * 1.4);
        for (ZombieLabo z : l.getEntitiesOfClass(ZombieLabo.class, new AABB(m).inflate(4))) z.discard();
        ZombieLabo z = ZombieLabo.creer(l, Allure.MARCHEUR, 0, 5);
        z.moveTo(dehors.x, dehors.y, dehors.z, ext.getOpposite().toYRot(), 0);
        z.setNoAi(true);
        l.addFreshEntity(z);
        ZbDemo.fenetre(l, z, best, dehors, dedans, centre, ext);
        ok(c.getSource(), "demonstration de fenetre lancee");
        return 1;
    }

    private static int boite(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ZombiesDecor.EtatBoite e;
        try {
            e = ZombiesDecor.EtatBoite.valueOf(StringArgumentType.getString(c, "etat").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            c.getSource().sendFailure(Component.literal("etat inconnu"));
            return 0;
        }
        List<BoiteBE> l = proches(p.serverLevel(), p.blockPosition(), BoiteBE.class);
        for (BoiteBE b : l) b.etat(e);
        ok(c.getSource(), l.size() + " boite(s) : " + e);
        return l.size();
    }

    private static int tirage(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Rarete r = Rarete.parId(StringArgumentType.getString(c, "rarete"), Rarete.RARE);
        List<BoiteBE> l = proches(p.serverLevel(), p.blockPosition(), BoiteBE.class);
        for (BoiteBE b : l) {
            b.etat(ZombiesDecor.EtatBoite.OUVERTE);
            b.tirage(r.couleur);
        }
        ok(c.getSource(), l.size() + " boite(s) : tirage " + r.id());
        return l.size();
    }

    private static int courant(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        boolean on = StringArgumentType.getString(c, "actif").equals("on");
        List<LevierBE> lv = proches(p.serverLevel(), p.blockPosition(), LevierBE.class);
        for (LevierBE b : lv) b.actif(on);
        List<AtoutBE> la = proches(p.serverLevel(), p.blockPosition(), AtoutBE.class);
        for (AtoutBE b : la) b.alimenter(on, true);
        List<PapBE> lp = proches(p.serverLevel(), p.blockPosition(), PapBE.class);
        for (PapBE b : lp) b.etat(on ? ZombiesDecor.EtatPap.PRETE : ZombiesDecor.EtatPap.ETEINTE, ItemStack.EMPTY);
        ok(c.getSource(), "courant " + (on ? "retabli" : "coupe") + " : " + lv.size() + " levier(s), " + la.size() + " machine(s), "
                + lp.size() + " pack-a-punch");
        return 1;
    }

    private static int servir(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<AtoutBE> la = proches(p.serverLevel(), p.blockPosition(), AtoutBE.class);
        for (AtoutBE b : la) b.servir();
        ok(c.getSource(), la.size() + " machine(s) servent");
        return la.size();
    }

    private static int arme(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<ArmeMurBE> l = proches(p.serverLevel(), p.blockPosition(), ArmeMurBE.class);
        for (ArmeMurBE b : l) b.achetee();
        ok(c.getSource(), l.size() + " arme(s) illuminee(s)");
        return l.size();
    }

    private static int pap(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ZombiesDecor.EtatPap e;
        try {
            e = ZombiesDecor.EtatPap.valueOf(StringArgumentType.getString(c, "etat").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            c.getSource().sendFailure(Component.literal("etat inconnu"));
            return 0;
        }
        ItemStack arme = p.getMainHandItem().copy();
        List<PapBE> l = proches(p.serverLevel(), p.blockPosition(), PapBE.class);
        for (PapBE b : l) b.etat(e, arme);
        ok(c.getSource(), l.size() + " pack-a-punch : " + e + " (" + (arme.isEmpty() ? "sans arme" : arme.getHoverName().getString()) + ")");
        return l.size();
    }

    private static int atelier(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<AtelierBE> l = proches(p.serverLevel(), p.blockPosition(), AtelierBE.class);
        for (AtelierBE b : l) {
            b.fabriquer("grenade");
            BlockPos q = b.getBlockPos();
            p.serverLevel().sendParticles(ZbRegistres.ETINCELLE.get(), q.getX() + 0.5, q.getY() + 1.05, q.getZ() + 0.5, 16, 0.4, 0.1, 0.3, 0.16);
        }
        ok(c.getSource(), l.size() + " etabli(s) : fabrication");
        return l.size();
    }

    private static int easter(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ZombiesDecor.EtatEtape e;
        try {
            e = ZombiesDecor.EtatEtape.valueOf(StringArgumentType.getString(c, "etat").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            c.getSource().sendFailure(Component.literal("etat inconnu"));
            return 0;
        }
        List<EasterBE> l = proches(p.serverLevel(), p.blockPosition(), EasterBE.class);
        for (EasterBE b : l) b.etat(e);
        ok(c.getSource(), l.size() + " etape(s) : " + e);
        return l.size();
    }

    // ------------------------------------------------------------------ creatures, bonus, materiaux
    private static int zombie(CommandContext<CommandSourceStack> c, int variante, int nombre, boolean sortie) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Allure a = sortie ? Allure.MARCHEUR : Allure.valueOf(StringArgumentType.getString(c, "allure").toUpperCase(Locale.ROOT));
        ServerLevel l = p.serverLevel();
        Direction f = p.getDirection();
        for (int i = 0; i < nombre; i++) {
            ZombieLabo z = ZombieLabo.creer(l, a, variante, 5);
            BlockPos q = p.blockPosition().relative(f, 8 + i % 4).relative(f.getClockWise(), i / 4 * 2 - 2);
            placer(z, q, f.getOpposite().toYRot());
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(a.vitesse);
            l.addFreshEntity(z);
            z.setTarget(p);
            if (sortie) z.sortirDeTerre();
        }
        ok(c.getSource(), nombre + " zombie(s) " + a);
        return nombre;
    }

    /**
     * Sortie d'un point de salle d'un genre donne (comme {@code ZombiesDecor.apparitionSalle}) : zombie sans IA pose 5
     * cases devant le joueur, 4 cases a sa droite (dans la scene {@code portes}, contre le mur : bouche d'aeration).
     */
    private static int sortieGenre(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel l = p.serverLevel();
        Direction f = p.getDirection();
        String genre = StringArgumentType.getString(c, "genre");
        ZombieLabo z = ZombieLabo.creer(l, Allure.MARCHEUR, -1, 5);
        placer(z, p.blockPosition().relative(f, 5).relative(f.getClockWise(), 4), f.getOpposite().toYRot());
        z.setNoAi(true);
        l.addFreshEntity(z);
        switch (genre) {
            case "conduit" -> z.sortirDUnConduit();
            case "egout" -> z.sortirDeLEgout();
            case "gravats" -> z.sortirDesGravats();
            case "tunnel" -> {
            }
            default -> z.sortirDeTerre();
        }
        ok(c.getSource(), "sortie " + genre);
        return 1;
    }

    private static int rodeur(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel l = p.serverLevel();
        Rodeur r = Rodeur.creer(l);
        placer(r, p.blockPosition().relative(p.getDirection(), 10), p.getDirection().getOpposite().toYRot());
        l.addFreshEntity(r);
        r.setTarget(p);
        r.animerApparition();
        ok(c.getSource(), "rodeur");
        return 1;
    }

    private static int bonus(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        TypeBonus t = TypeBonus.parId(StringArgumentType.getString(c, "type"));
        if (t == null) {
            c.getSource().sendFailure(Component.literal("bonus inconnu"));
            return 0;
        }
        BlockPos q = p.blockPosition().relative(p.getDirection(), 3);
        new DecorContenu().bonus(p.serverLevel(), new Vec3(q.getX() + 0.5, q.getY() + 0.6, q.getZ() + 0.5), t);
        ok(c.getSource(), "bonus " + t.id());
        return 1;
    }

    private static int materiau(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = IntegerArgumentType.getInteger(c, "quantite");
        BlockPos q = p.blockPosition().relative(p.getDirection(), 2);
        new DecorContenu().materiau(p.serverLevel(), new Vec3(q.getX() + 0.5, q.getY(), q.getZ() + 0.5), n);
        ok(c.getSource(), "materiaux x" + n);
        return 1;
    }

    private static int nettoyer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = nettoyer(p.serverLevel(), p.blockPosition());
        ZbDemo.vider();
        ok(c.getSource(), n + " entite(s) retiree(s)");
        return n;
    }

    private static int nettoyer(ServerLevel l, BlockPos o) {
        AABB zone = new AABB(o).inflate(RAYON);
        int n = 0;
        for (Entity e : l.getEntitiesOfClass(Entity.class, zone, e -> e instanceof ZombieLabo || e instanceof Rodeur || e instanceof BonusEntite
                || e instanceof MateriauEntite || e instanceof OuvertureEntite)) {
            e.discard();
            n++;
        }
        return n;
    }
}
