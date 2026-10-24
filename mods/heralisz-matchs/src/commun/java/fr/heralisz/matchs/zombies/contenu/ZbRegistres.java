package fr.heralisz.matchs.zombies.contenu;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.bloc.ArmeMurBE;
import fr.heralisz.matchs.zombies.contenu.bloc.AtelierBE;
import fr.heralisz.matchs.zombies.contenu.bloc.AtoutBE;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocAtelier;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocEasterEgg;
import fr.heralisz.matchs.zombies.contenu.bloc.EasterBE;
import fr.heralisz.matchs.zombies.contenu.entite.MateriauEntite;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocObstacle;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocPap;
import fr.heralisz.matchs.zombies.contenu.bloc.ObstacleBE;
import fr.heralisz.matchs.zombies.contenu.bloc.PapBE;
import fr.heralisz.matchs.zombies.contenu.entite.OuvertureEntite;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocArmeMur;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocAtout;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocBoite;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocFenetre;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocLevier;
import fr.heralisz.matchs.zombies.contenu.bloc.BoiteBE;
import fr.heralisz.matchs.zombies.contenu.bloc.FenetreBE;
import fr.heralisz.matchs.zombies.contenu.bloc.LevierBE;
import fr.heralisz.matchs.zombies.contenu.entite.BonusEntite;
import fr.heralisz.matchs.zombies.contenu.entite.Rodeur;
import fr.heralisz.matchs.zombies.contenu.entite.ZombieLabo;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocCollision;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocMachine;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocObjetQuete;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocPiece;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocDecoAnime;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocDecoGiant;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocNeige;
import fr.heralisz.matchs.zombies.contenu.bloc.DecoAnimeBE;
import fr.heralisz.matchs.zombies.contenu.bloc.MachineBE;
import fr.heralisz.matchs.zombies.contenu.bloc.ObjetQueteBE;
import fr.heralisz.matchs.zombies.contenu.objet.ObjetQuete;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registres du contenu zombies : entites (zombie « labo », rodeur, bonus au sol), blocs-entites du decor (fenetre
 * barricadee, arme au mur, boite mystere, machine d'atout, levier du courant) et particules maison. Les sons sont dans
 * {@link ZbSons}. Aucun objet ni onglet creatif : les blocs sont poses par le decor ({@link DecorContenu}) ou par
 * {@code /zbdev} (banc d'essai).
 */
public final class ZbRegistres {
    private ZbRegistres() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITES = DeferredRegister.create(Registries.ENTITY_TYPE, HeraliszMatchs.MODID);
    public static final DeferredRegister<Block> BLOCS = DeferredRegister.create(Registries.BLOCK, HeraliszMatchs.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BE = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HeraliszMatchs.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICULES = DeferredRegister.create(Registries.PARTICLE_TYPE, HeraliszMatchs.MODID);
    /** Objets de quete (fusible, noyau de 115) : icones du HUD et banc d'essai, jamais dans l'onglet creatif. */
    public static final DeferredRegister<Item> OBJETS = DeferredRegister.create(ForgeRegistries.ITEMS, HeraliszMatchs.MODID);

    // ------------------------------------------------------------------ entites
    /** Usines (docs/METHODE_CLIENT_SERVEUR.md §4) : versions serveur (IA, coups) branchées par la partie serveur. */
    public static final fr.heralisz.matchs.pont.UsineEntite<ZombieLabo> ZOMBIE_LABO_USINE = new fr.heralisz.matchs.pont.UsineEntite<>(ZombieLabo::new);
    public static final fr.heralisz.matchs.pont.UsineEntite<Rodeur> RODEUR_USINE = new fr.heralisz.matchs.pont.UsineEntite<>(Rodeur::new);
    public static final RegistryObject<EntityType<ZombieLabo>> ZOMBIE_LABO = ENTITES.register("zb_zombie",
            () -> EntityType.Builder.<ZombieLabo>of(ZOMBIE_LABO_USINE, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .clientTrackingRange(10).updateInterval(2).build(HeraliszMatchs.MODID + ":zb_zombie"));
    public static final RegistryObject<EntityType<Rodeur>> RODEUR = ENTITES.register("zb_rodeur",
            () -> EntityType.Builder.<Rodeur>of(RODEUR_USINE, MobCategory.MONSTER).sized(0.9F, 1.1F)
                    .clientTrackingRange(10).updateInterval(2).build(HeraliszMatchs.MODID + ":zb_rodeur"));
    public static final RegistryObject<EntityType<BonusEntite>> BONUS = ENTITES.register("zb_bonus",
            () -> EntityType.Builder.<BonusEntite>of(BonusEntite::new, MobCategory.MISC).sized(0.8F, 0.8F)
                    .clientTrackingRange(8).updateInterval(20).noSummon().build(HeraliszMatchs.MODID + ":zb_bonus"));
    /** Tas de materiaux de recuperation lache par un zombie (ramasse par le jeu). */
    public static final RegistryObject<EntityType<MateriauEntite>> MATERIAU = ENTITES.register("zb_materiau",
            () -> EntityType.Builder.<MateriauEntite>of(MateriauEntite::new, MobCategory.MISC).sized(0.5F, 0.3F)
                    .clientTrackingRange(8).updateInterval(20).noSummon().build(HeraliszMatchs.MODID + ":zb_materiau"));
    /** Animation d'ouverture d'une porte, grille ou tas de debris (2 s, purement visuelle). */
    public static final RegistryObject<EntityType<OuvertureEntite>> OUVERTURE = ENTITES.register("zb_ouverture",
            () -> EntityType.Builder.<OuvertureEntite>of(OuvertureEntite::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(8).updateInterval(40).noSummon().fireImmune().build(HeraliszMatchs.MODID + ":zb_ouverture"));

    // ------------------------------------------------------------------ blocs (decor, rendus par leur bloc-entite)
    private static BlockBehaviour.Properties decor() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).noLootTable()
                .noOcclusion().pushReaction(PushReaction.BLOCK).sound(SoundType.METAL).isValidSpawn((s, l, p, e) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false);
    }

    public static final RegistryObject<BlocFenetre> FENETRE = BLOCS.register("zb_fenetre", () -> new BlocFenetre(decor().sound(SoundType.WOOD)));
    public static final RegistryObject<BlocArmeMur> ARME_MUR = BLOCS.register("zb_arme_mur", () -> new BlocArmeMur(decor().noCollission()));
    public static final RegistryObject<BlocBoite> BOITE = BLOCS.register("zb_boite",
            () -> new BlocBoite(decor().sound(SoundType.WOOD).lightLevel(s -> s.getValue(BlocBoite.ALLUMEE) ? 8 : 0)));
    public static final RegistryObject<BlocAtout> ATOUT = BLOCS.register("zb_atout",
            () -> new BlocAtout(decor().lightLevel(s -> s.getValue(BlocAtout.ALIMENTE) ? 10 : 0)));
    public static final RegistryObject<BlocLevier> LEVIER = BLOCS.register("zb_levier",
            () -> new BlocLevier(decor().noCollission().lightLevel(s -> s.getValue(BlocLevier.ACTIF) ? 6 : 3)));
    /** Porte barree, grille cadenassee ou tas de debris payant (toutes les cases du volume de la porte). */
    public static final RegistryObject<BlocObstacle> OBSTACLE = BLOCS.register("zb_obstacle",
            () -> new BlocObstacle(decor().sound(SoundType.STONE)));
    /** Machine Pack-a-Punch (2 cases de large, 2 de haut ; case maitresse = bas gauche vu de face). */
    public static final RegistryObject<BlocPap> PAP = BLOCS.register("zb_pap",
            () -> new BlocPap(decor().lightLevel(s -> s.getValue(BlocPap.ALIMENTE) ? 11 : 2)));
    /** Etape d'easter egg (radio, interrupteur, fragment lumineux). */
    public static final RegistryObject<BlocEasterEgg> EASTER = BLOCS.register("zb_easter",
            () -> new BlocEasterEgg(decor().noCollission().lightLevel(s -> s.getValue(BlocEasterEgg.GENRE) == 2 ? 6 : 2)));
    /** Etabli de fabrication (2 cases de large). */
    public static final RegistryObject<BlocAtelier> ATELIER = BLOCS.register("zb_atelier",
            () -> new BlocAtelier(decor().sound(SoundType.WOOD).lightLevel(s -> s.getValue(BlocAtelier.PARTIE) == 0 ? 7 : 0)));

    // ------------------------------------------------------------------ machines v3 de The Giant (D-051)
    private static RegistryObject<BlocMachine> machine(String id, MachinesV3.Type t, int lumiere) {
        return BLOCS.register(id, () -> new BlocMachine(decor().dynamicShape().lightLevel(s -> s.getValue(BlocMachine.ALLUME) ? lumiere : 0), t));
    }

    /** Armoire du courant (3 fusibles, sectionneur, voltmetre). */
    public static final RegistryObject<BlocMachine> COURANT3 = machine("zb_courant3", MachinesV3.Type.COURANT, 7);
    public static final RegistryObject<BlocMachine> TP_PAD = machine("zb_tp_pad", MachinesV3.Type.TP_PAD, 9);
    public static final RegistryObject<BlocMachine> TP_PUPITRE = machine("zb_tp_pupitre", MachinesV3.Type.TP_PUPITRE, 5);
    public static final RegistryObject<BlocMachine> CUVE_AMES = machine("zb_cuve_ames", MachinesV3.Type.CUVE_AMES, 7);
    public static final RegistryObject<BlocMachine> MF_PUPITRE = machine("zb_mf_pupitre", MachinesV3.Type.MF_PUPITRE, 6);
    public static final RegistryObject<BlocMachine> MF_TOUR = machine("zb_mf_tour", MachinesV3.Type.MF_TOUR, 12);
    public static final RegistryObject<BlocMachine> MF_PLATEFORME = machine("zb_mf_plateforme", MachinesV3.Type.MF_PLATEFORME, 6);
    public static final RegistryObject<BlocMachine> PAP_RIDEAU = machine("zb_pap_rideau", MachinesV3.Type.PAP_RIDEAU, 6);
    /** Morceau cuit d'une tres grande machine (tour, plateforme). */
    public static final RegistryObject<BlocPiece> PIECE = BLOCS.register("zb_piece", () -> new BlocPiece(decor()));
    /** Collision invisible des machines v3. */
    public static final RegistryObject<BlocCollision> COLLISION = BLOCS.register("zb_collision", () -> new BlocCollision(decor()));
    /** Objet de quete (fusible, boitier du Fly Trap, singe, nounours, Annihilator, carte d'acces). */
    public static final RegistryObject<BlocObjetQuete> OBJET_QUETE = BLOCS.register("zb_objet_quete",
            () -> new BlocObjetQuete(decor().dynamicShape().lightLevel(s -> 3)));

    // ------------------------------------------------------------------ decors cuits de The Giant (D-053)
    /** Decors {@code zb_deco_<nom>} (un bloc par prop, une case par etat {@code part}), dans l'ordre de formes.json. */
    public static final java.util.Map<String, RegistryObject<Block>> DECORS = new java.util.LinkedHashMap<>();
    /** Meubles en bois (son de pas), le reste sonne le metal. */
    private static final java.util.Set<String> DECORS_BOIS = java.util.Set.of("zb_deco_banc", "zb_deco_bureau", "zb_deco_bureau_maxis",
            "zb_deco_etagere", "zb_deco_etagere_outils", "zb_deco_lit_camp", "zb_deco_panneau_affichage", "zb_deco_tableau_noir",
            "zb_deco_paillasse");
    public static final String ID_LEVIER_PIEGE = "zb_deco_levier_piege", ID_PORTE_CONFINEMENT = "zb_deco_porte_confinement";

    static {
        for (String id : FormesDecoGiant.ids()) {
            if (!id.startsWith("zb_deco_")) continue;
            SoundType son = DECORS_BOIS.contains(id) ? SoundType.WOOD : SoundType.METAL;
            if (id.equals(ID_LEVIER_PIEGE)) {
                DECORS.put(id, BLOCS.register(id, () -> BlocDecoAnime.creer(id, decor().sound(son), BlocDecoAnime.LEVIER)));
            } else if (id.equals(ID_PORTE_CONFINEMENT)) {
                DECORS.put(id, BLOCS.register(id, () -> BlocDecoAnime.creer(id, decor().sound(son).dynamicShape(), BlocDecoAnime.PORTE)));
            } else {
                DECORS.put(id, BLOCS.register(id, () -> BlocDecoGiant.creer(id, decor().sound(son))));
            }
        }
    }

    /** Plaque de neige (1/16, sans collision). */
    public static final RegistryObject<BlocNeige.Tache> NEIGE_TACHE = BLOCS.register("zb_neige_tache",
            () -> new BlocNeige.Tache(decor().mapColor(MapColor.SNOW).sound(SoundType.SNOW).noCollission()));
    /** Bourrelet de neige au bord des toits. */
    public static final RegistryObject<BlocNeige.Rebord> NEIGE_REBORD = BLOCS.register("zb_neige_rebord",
            () -> new BlocNeige.Rebord(decor().mapColor(MapColor.SNOW).sound(SoundType.SNOW).noCollission()));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<DecoAnimeBE>> DECO_ANIME_BE = BE.register("zb_deco_anime",
            () -> BlockEntityType.Builder.of(DecoAnimeBE::new, animes()).build(null));

    private static Block[] animes() {
        java.util.List<Block> l = new java.util.ArrayList<>(2);
        for (String id : new String[]{ID_LEVIER_PIEGE, ID_PORTE_CONFINEMENT}) {
            RegistryObject<Block> r = DECORS.get(id);
            if (r != null) l.add(r.get());
        }
        return l.toArray(new Block[0]);
    }

    /** Bloc de decor par id ({@code zb_deco_...}), ou null. */
    @javax.annotation.Nullable
    public static Block decor(String id) {
        RegistryObject<Block> r = DECORS.get(id);
        return r == null || !r.isPresent() ? null : r.get();
    }

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<MachineBE>> MACHINE_BE = BE.register("zb_machine",
            () -> BlockEntityType.Builder.of(MachineBE::new, COURANT3.get(), TP_PAD.get(), TP_PUPITRE.get(), CUVE_AMES.get(), MF_PUPITRE.get(),
                    MF_TOUR.get(), MF_PLATEFORME.get(), PAP_RIDEAU.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ObjetQueteBE>> OBJET_QUETE_BE = BE.register("zb_objet_quete",
            () -> BlockEntityType.Builder.of(ObjetQueteBE::new, OBJET_QUETE.get()).build(null));

    /** Objets : fusible de 25 A, noyau de 115 (dessines par leur modele zombies v3). */
    public static final RegistryObject<ObjetQuete> FUSIBLE = OBJETS.register("zb_fusible", () -> new ObjetQuete(new Item.Properties().stacksTo(3), "fusible"));
    public static final RegistryObject<ObjetQuete> NOYAU_115 = OBJETS.register("zb_noyau_115", () -> new ObjetQuete(new Item.Properties().stacksTo(3), "noyau_115"));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<FenetreBE>> FENETRE_BE = BE.register("zb_fenetre",
            () -> BlockEntityType.Builder.of(FenetreBE::new, FENETRE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ArmeMurBE>> ARME_MUR_BE = BE.register("zb_arme_mur",
            () -> BlockEntityType.Builder.of(ArmeMurBE::new, ARME_MUR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<BoiteBE>> BOITE_BE = BE.register("zb_boite",
            () -> BlockEntityType.Builder.of(BoiteBE::new, BOITE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<AtoutBE>> ATOUT_BE = BE.register("zb_atout",
            () -> BlockEntityType.Builder.of(AtoutBE::new, ATOUT.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<LevierBE>> LEVIER_BE = BE.register("zb_levier",
            () -> BlockEntityType.Builder.of(LevierBE::new, LEVIER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ObstacleBE>> OBSTACLE_BE = BE.register("zb_obstacle",
            () -> BlockEntityType.Builder.of(ObstacleBE::new, OBSTACLE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<PapBE>> PAP_BE = BE.register("zb_pap",
            () -> BlockEntityType.Builder.of(PapBE::new, PAP.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<AtelierBE>> ATELIER_BE = BE.register("zb_atelier",
            () -> BlockEntityType.Builder.of(AtelierBE::new, ATELIER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<EasterBE>> EASTER_BE = BE.register("zb_easter",
            () -> BlockEntityType.Builder.of(EasterBE::new, EASTER.get()).build(null));

    // ------------------------------------------------------------------ particules maison
    /** Copeaux de bois (planche arrachee). */
    public static final RegistryObject<SimpleParticleType> COPEAU = PARTICULES.register("zb_copeau", () -> new SimpleParticleType(false));
    /** Cendres sombres (mort d'un zombie, gravats). */
    public static final RegistryObject<SimpleParticleType> CENDRE = PARTICULES.register("zb_cendre", () -> new SimpleParticleType(false));
    /** Etincelles (courant, machines). */
    public static final RegistryObject<SimpleParticleType> ETINCELLE = PARTICULES.register("zb_etincelle", () -> new SimpleParticleType(false));
    /** Lueur douce teintee par la vitesse x (rouge), y (vert), z (bleu) : bonus, boite mystere. */
    public static final RegistryObject<SimpleParticleType> LUEUR = PARTICULES.register("zb_lueur", () -> new SimpleParticleType(false));
    /** Brume sombre (rodeurs, sortie de terre). */
    public static final RegistryObject<SimpleParticleType> BRUME = PARTICULES.register("zb_brume", () -> new SimpleParticleType(false));
    /**
     * Ame (bleu-vert) qui vole d'un zombie tue jusqu'au cornet d'une cuve : la vitesse envoyee est le vecteur jusqu'a la
     * cible (blocs), la particule fait le trajet en courbe en ~18 ticks.
     */
    public static final RegistryObject<SimpleParticleType> AME = PARTICULES.register("zb_ame", () -> new SimpleParticleType(true));
    /** Eclair d'apparition d'un chien (colonne brisee du ciel au sol, 6 ticks) ; vitesse x = hauteur (blocs), 0 = 18. */
    public static final RegistryObject<SimpleParticleType> ECLAIR = PARTICULES.register("zb_eclair", () -> new SimpleParticleType(true));

    public static void enregistrer(IEventBus modBus) {
        ENTITES.register(modBus);
        BLOCS.register(modBus);
        BE.register(modBus);
        PARTICULES.register(modBus);
        OBJETS.register(modBus);
        ZbSons.SONS.register(modBus);
    }
}
