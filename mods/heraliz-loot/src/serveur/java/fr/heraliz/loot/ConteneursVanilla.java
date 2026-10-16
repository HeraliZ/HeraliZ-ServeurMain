package fr.heraliz.loot;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/**
 * Blocs de Minecraft fermés aux joueurs en survie ou en aventure (choix du propriétaire). Libres : créatif, spectateur,
 * staff (permission 2). Seulement le namespace {@code minecraft} : jamais nos blocs ni ceux de cfm.
 * <ul>
 *   <li><b>Rangements</b> ({@code [conteneurs_vanilla]} de {@code heraliz_loot-server.toml}, {@link ConfigLoot}) : coffre,
 *   baril, four, alambic, entonnoir, distributeur, boîte de Shulker... ne s'ouvrent pas (« Ce conteneur est vide et
 *   scellé. ») ; le loot est dans les conteneurs de HeraliZ Loot. Seule l'ouverture par un joueur est refusée (entonnoirs
 *   et distributeurs marchent toujours entre eux). Wagonnets à coffre ou à entonnoir et coffre des bateaux à coffre :
 *   refusés aussi ({@code entites}), on peut toujours monter dans le bateau. Dans un claim de CRac (rangements des bases) :
 *   permis par défaut ({@code permis_claims_crac}).</li>
 *   <li><b>Postes de travail et blocs à usage</b> ({@code [postes_vanilla]}, Loot 0.9.0) : établi, enclumes, table
 *   d'enchantement, métier, coffre de l'Ender, cartographie, tailleur de pierre, table de forge, balise, meule, pupitre,
 *   composteur, chaudrons, pots de fleurs, feux de camp, bougies, bloc musical, répéteur, comparateur, blocs de
 *   commande... sont hors service (« Ce poste est hors service. »), et tout autre bloc de Minecraft qui ouvre un menu
 *   ({@code menus_auto}). Aussi dans les claims de CRac par défaut ({@code permis_claims_crac = false} : le propriétaire
 *   préfère bloquer en trop ; une base n'a pas besoin d'un poste de Minecraft, et le coffre de l'Ender y serait une
 *   réserve que le raid ne peut pas prendre). Portes, trappes, portillons, boutons, leviers, plaques, lits, échelles et
 *   cloches ne sont jamais dans la liste.</li>
 * </ul>
 * Claims de CRac lus par réflexion (CRac facultatif).
 */
final class ConteneursVanilla {
    private ConteneursVanilla() {
    }

    private static final String MINECRAFT = "minecraft";
    private static volatile Regles regles;

    /** Ce que fait un bloc de Minecraft pour la règle : rien, rangement (conteneur) ou poste (usage, menu). */
    enum Genre { AUCUN, RANGEMENT, POSTE }

    record Liste(Set<Block> blocs, List<TagKey<Block>> tags) {
        boolean contient(BlockState st) {
            if (blocs.contains(st.getBlock())) return true;
            for (TagKey<Block> k : tags) if (st.is(k)) return true;
            return false;
        }
    }

    record Regles(Liste rangements, Liste rangementsSauf, Liste postes, Liste postesSauf) {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ConteneursVanilla::bloc);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ConteneursVanilla::entite);
    }

    /** La configuration a changé : listes relues au prochain clic. */
    static void invalider() {
        regles = null;
    }

    /** Joueur que ces règles ne concernent pas : créatif, spectateur, staff (permission 2). */
    static boolean libre(ServerPlayer sp) {
        return sp.isCreative() || sp.isSpectator() || sp.hasPermissions(2);
    }

    private static void bloc(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer sp) || !ConfigLoot.SPEC.isLoaded() || libre(sp)) return;
        Level l = e.getLevel();
        BlockPos pos = e.getPos();
        BlockState st = l.getBlockState(pos);
        Genre g = genre(l, pos, st, l.getBlockEntity(pos));
        if (g == Genre.AUCUN) return;
        boolean permisClaim = g == Genre.RANGEMENT ? ConfigLoot.VANILLA_CLAIMS.get() : ConfigLoot.POSTES_CLAIMS.get();
        if (permisClaim && l instanceof ServerLevel sl && dansClaimCrac(sl, pos)) return;
        boolean objet = !sp.getMainHandItem().isEmpty() || !sp.getOffhandItem().isEmpty();
        if (sp.isSecondaryUseActive() && objet) {
            // accroupi avec un objet : Minecraft n'utilise pas le bloc, l'objet sert (poser un bloc contre le coffre...) ;
            // le bloc reste refusé au cas où l'objet laisserait passer l'ouverture
            e.setUseBlock(Event.Result.DENY);
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
        if (e.getHand() == InteractionHand.MAIN_HAND) prevenir(sp, g);
    }

    /** Clic sur une entité (wagonnet, bateau) : EntityInteractSpecific passe toujours par ici ensuite s'il ne fait rien. */
    private static void entite(PlayerInteractEvent.EntityInteract e) {
        if (refuserEntite(e.getTarget(), e)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static boolean refuserEntite(Entity cible, PlayerInteractEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer sp) || !ConfigLoot.SPEC.isLoaded()
                || !ConfigLoot.VANILLA_ACTIF.get() || libre(sp)) return false;
        if (!ConfigLoot.VANILLA_ENTITES.get() || !(cible instanceof ContainerEntity)) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(cible.getType());
        if (!MINECRAFT.equals(id.getNamespace())) return false;
        // bateau à coffre : clic simple = monter, accroupi = ouvrir le coffre ; wagonnet : le clic ouvre toujours
        if (!(cible instanceof AbstractMinecartContainer) && !sp.isSecondaryUseActive()) return false;
        if (e.getHand() == InteractionHand.MAIN_HAND) prevenir(sp, Genre.RANGEMENT);
        return true;
    }

    /**
     * Rangement scellé, poste hors service, ou rien. Un conteneur (entité de bloc à inventaire) ne suit que
     * {@code [conteneurs_vanilla]} : mis dans ses exceptions, il est rouvert, sauf s'il est écrit en toutes lettres dans la
     * liste des postes. {@code menus_auto} ne vise donc jamais un conteneur.
     */
    static Genre genre(Level l, BlockPos pos, BlockState st, BlockEntity be) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
        if (!MINECRAFT.equals(id.getNamespace())) return Genre.AUCUN;
        Regles r = regles();
        boolean conteneur = be instanceof Container;
        if (ConfigLoot.VANILLA_ACTIF.get() && !r.rangementsSauf().contient(st)
                && (r.rangements().contient(st) || ConfigLoot.VANILLA_AUTO.get() && conteneur)) return Genre.RANGEMENT;
        if (ConfigLoot.POSTES_ACTIF.get() && !r.postesSauf().contient(st)) {
            if (r.postes().contient(st)) return Genre.POSTE;
            if (ConfigLoot.POSTES_MENUS_AUTO.get() && !conteneur && st.getMenuProvider(l, pos) != null) return Genre.POSTE;
        }
        return Genre.AUCUN;
    }

    // ------------------------------------------------------------------ CRac (facultatif, par réflexion)
    private static final String CRAC_CLAIMS = "fr.heraliz.crac.claim.ClaimManager";
    private static boolean cracCherche;
    private static Method cracClaimA;

    /**
     * Un claim de CRac couvre-t-il cet endroit ? (CRac absent ou changé : non, avec un avertissement une seule fois.)
     * Aussi lu par {@link Presentoirs} et {@link Nuisances}.
     */
    static boolean dansClaimCrac(ServerLevel l, BlockPos pos) {
        if (!cracCherche) {
            cracCherche = true;
            if (ModList.get().isLoaded("crac")) {
                try {
                    cracClaimA = Class.forName(CRAC_CLAIMS).getMethod("getAt", ServerLevel.class, BlockPos.class);
                } catch (ReflectiveOperationException | LinkageError ex) {
                    HeralizLoot.LOG.warn("[conteneurs_vanilla] {}.getAt introuvable : règles appliquées aussi dans les claims", CRAC_CLAIMS);
                }
            }
        }
        Method m = cracClaimA;
        if (m == null) return false;
        try {
            return m.invoke(null, l, pos) != null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HeralizLoot.LOG.warn("[conteneurs_vanilla] claims de CRac illisibles : règles appliquées aussi dans les claims ({})", ex.toString());
            cracClaimA = null;
            return false;
        }
    }

    private static void prevenir(ServerPlayer sp, Genre g) {
        Component c = g == Genre.POSTE
                ? Component.translatableWithFallback("heraliz_loot.vanilla.poste", "Ce poste est hors service.")
                : Component.translatableWithFallback("heraliz_loot.vanilla.scelle", "Ce conteneur est vide et scellé.");
        sp.displayClientMessage(c.copy().withStyle(ChatFormatting.GRAY), true);
    }

    private static Regles regles() {
        Regles r = regles;
        if (r == null) {
            regles = r = new Regles(lire(ConfigLoot.VANILLA_BLOQUES.get()), lire(ConfigLoot.VANILLA_EXCEPTIONS.get()),
                    lire(ConfigLoot.POSTES_BLOQUES.get()), lire(ConfigLoot.POSTES_EXCEPTIONS.get()));
        }
        return r;
    }

    private static Liste lire(List<? extends String> liste) {
        Set<Block> blocs = new HashSet<>();
        List<TagKey<Block>> tags = new ArrayList<>();
        for (String brut : liste) {
            String x = brut.trim();
            boolean tag = x.startsWith("#");
            ResourceLocation rl = ResourceLocation.tryParse(tag ? x.substring(1) : x);
            if (rl == null) {
                HeralizLoot.LOG.warn("[conteneurs_vanilla] identifiant illisible : {}", brut);
            } else if (tag) {
                tags.add(TagKey.create(Registries.BLOCK, rl));
            } else if (BuiltInRegistries.BLOCK.containsKey(rl)) {
                blocs.add(BuiltInRegistries.BLOCK.get(rl));
            } else {
                HeralizLoot.LOG.warn("[conteneurs_vanilla] bloc inconnu : {}", brut);
            }
        }
        return new Liste(blocs, tags);
    }
}
