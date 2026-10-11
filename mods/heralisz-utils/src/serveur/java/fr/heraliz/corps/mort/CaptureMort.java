package fr.heraliz.corps.mort;

import fr.heraliz.corps.CorpsConfig;
import fr.heraliz.corps.HeralizCorps;
import fr.heraliz.corps.compat.ReanimationCompat;
import fr.heraliz.corps.entite.CorpsContenu;
import fr.heraliz.corps.entite.CorpsEntity;
import fr.heraliz.corps.entite.CorpsEntityServeur;
import fr.heraliz.corps.registre.Registre;
import fr.heraliz.corps.registre.Registres;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La mort d'un joueur devient un corps.
 * <ol>
 *     <li>{@code LivingDeathEvent} LOWEST (mort non annulee : ni mise a terre Reanimation, ni match) : photo de ce que
 *     porte le joueur ({@link Instantane}) et cause de la mort ;</li>
 *     <li>{@code LivingDropsEvent} LOWEST (apres Curios qui ajoute ses objets en HIGHEST) : chaque objet tombe est range
 *     a sa place sur le corps, puis la liste des objets au sol est videe. Les memes objets passent du joueur au corps :
 *     rien n'est copie, rien n'est perdu. Si le corps ne peut pas etre cree (erreur, monde qui refuse l'entite), les objets
 *     tombent normalement au sol.</li>
 * </ol>
 * Pas de corps : joueur en match (drapeau {@code heralisz:en_match}), dimension exclue (arenes et zombies des matchs),
 * spectateur, faux joueur, mod desactive. Sous Mohist, l'ordre est le meme : {@code die} poste {@code LivingDeathEvent},
 * puis {@code dropAllDeathLoot} poste {@code LivingDropsEvent}.
 */
public final class CaptureMort {
    private CaptureMort() {
    }

    /** Drapeau de HeraliZ Matchs dans {@code persistentData} d'un joueur en match. */
    public static final String DRAPEAU_EN_MATCH = "heralisz:en_match";

    /** Photos en attente de la chute des objets (meme tick ; videes en fin de tick si rien n'est tombe). */
    private static final Map<UUID, Instantane> INSTANTANES = new HashMap<>();

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CaptureMort::mort);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CaptureMort::chute);
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && !INSTANTANES.isEmpty()) INSTANTANES.clear();
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> INSTANTANES.clear());
    }

    /** Vrai si la mort de ce joueur laisse un corps. */
    public static boolean eligible(ServerPlayer p) {
        if (p instanceof FakePlayer || p.isSpectator()) return false;
        if (!CorpsConfig.ACTIF.get()) return false;
        if (p.getPersistentData().contains(DRAPEAU_EN_MATCH)) return false;
        return !CorpsConfig.dimensionExclue(p.level().dimension().location().toString());
    }

    private static void mort(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level().isClientSide) return;
        INSTANTANES.remove(p.getUUID());
        if (!eligible(p)) return;
        try {
            INSTANTANES.put(p.getUUID(), Instantane.prendre(p, e.getSource()));
        } catch (RuntimeException ex) {
            HeralizCorps.LOG.error("[corps] photo de la mort de {} impossible : objets au sol", p.getGameProfile().getName(), ex);
        }
    }

    private static void chute(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level().isClientSide) return;
        Instantane s = INSTANTANES.remove(p.getUUID());
        if (s == null) return;
        Collection<ItemEntity> tombes = e.getDrops();
        try {
            if (creerCorps(p, s, tombes)) tombes.clear();
        } catch (RuntimeException ex) {
            HeralizCorps.LOG.error("[corps] corps de {} impossible : objets laisses au sol", p.getGameProfile().getName(), ex);
        }
    }

    /** Range les objets tombes sur un nouveau corps et l'ajoute au monde. Vrai si le corps existe (objets a retirer du sol). */
    static boolean creerCorps(ServerPlayer p, Instantane s, Collection<ItemEntity> tombes) {
        ServerLevel level = p.serverLevel();
        ItemStack[] fixes = new ItemStack[CorpsContenu.CURIOS];
        ItemStack[] curios = new ItemStack[s.curiosIds.size()];
        List<ItemStack> autres = new ArrayList<>();
        int total = 0;
        for (ItemEntity ie : tombes) {
            if (ie == null) continue;
            ItemStack st = ie.getItem();
            if (st.isEmpty()) continue;
            total++;
            Integer place = s.retrouver(st);
            if (place == null) {
                autres.add(st);
            } else if (place < CorpsContenu.CURIOS) {
                if (fixes[place] == null) fixes[place] = st;
                else autres.add(st);
            } else {
                int k = place - CorpsContenu.CURIOS;
                if (k < curios.length && curios[k] == null) curios[k] = st;
                else autres.add(st);
            }
        }
        // objet tenu au curseur et grille d'artisanat 2x2 : ils tomberaient au sol plus tard ; ils vont aussi sur le corps
        List<Runnable> rendre = new ArrayList<>();
        AbstractContainerMenu menu = p.containerMenu;
        ItemStack curseur = menu.getCarried();
        if (!curseur.isEmpty()) {
            autres.add(curseur);
            menu.setCarried(ItemStack.EMPTY);
            rendre.add(() -> menu.setCarried(curseur));
            total++;
        }
        CraftingContainer grille = p.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grille.getContainerSize(); i++) {
            ItemStack g = grille.getItem(i);
            if (g.isEmpty()) continue;
            grille.removeItemNoUpdate(i);
            autres.add(g);
            int j = i;
            rendre.add(() -> grille.setItem(j, g));
            total++;
        }
        if (total == 0) return false;
        try {
            if (poser(p, s, level, fixes, curios, autres, total)) return true;
        } catch (RuntimeException ex) {
            rendre.forEach(Runnable::run);
            throw ex;
        }
        rendre.forEach(Runnable::run);
        return false;
    }

    private static boolean poser(ServerPlayer p, Instantane s, ServerLevel level, ItemStack[] fixes, ItemStack[] curios, List<ItemStack> autres,
                                 int total) {
        int nbCurios = 0;
        for (ItemStack c : curios) if (c != null) nbCurios++;
        CorpsContenu contenu = new CorpsContenu(nbCurios, autres.size());
        for (int i = 0; i < fixes.length; i++) if (fixes[i] != null) contenu.poser(i, fixes[i]);
        int k = 0;
        for (int i = 0; i < curios.length; i++) {
            if (curios[i] == null) continue;
            contenu.definirIdCurios(k, s.curiosIds.get(i));
            contenu.poser(CorpsContenu.CURIOS + k, curios[i]);
            k++;
        }
        for (int i = 0; i < autres.size(); i++) contenu.poser(contenu.debutAutres() + i, autres.get(i));

        if (!(Registres.CORPS.get().create(level) instanceof CorpsEntityServeur corps)) return false;
        double x = p.getX(), y = Math.max(p.getY(), level.getMinBuildHeight() + 1), z = p.getZ();
        corps.moveTo(x, y, z, p.yBodyRot, 0);
        // Mort dans un bloc (étouffement, porte…) : seul le MILIEU du corps compte (un corps qui touche un mur ou un meuble
        // à côté reste où il est), et il ne remonte que d'un bloc au plus. Avant, toute la largeur (1,2) comptait et le
        // corps montait jusqu'à 4 blocs : il finissait sur le toit, à l'étage ou pris dans un plafond (« disparu »).
        net.minecraft.world.phys.AABB milieu = corps.getBoundingBox().deflate(0.45, 0, 0.45);
        if (level.getBlockCollisions(corps, milieu).iterator().hasNext()) {
            double haut = Math.floor(y) + 1;
            if (!level.getBlockCollisions(corps, milieu.move(0, haut - corps.getY(), 0)).iterator().hasNext()) {
                corps.setPos(x, haut, z);
            }
        }
        corps.preparer(p, contenu, s.cause, s.execution ? ReanimationCompat.SCENE_APRES_COUP : 0);

        plafonds(level, corps);
        if (!level.addFreshEntity(corps)) {
            HeralizCorps.LOG.warn("[corps] le monde a refuse le corps de {} : objets laisses au sol", p.getGameProfile().getName());
            return false;
        }
        Registre.Fiche f = Registre.de(level.getServer()).ajouter(corps);
        HeralizCorps.LOG.info("[corps] {} mort en {} {} {} {} : corps n{} ({} objet(s) dont {} accessoire(s), {} autre(s), sac {})",
                p.getGameProfile().getName(), level.dimension().location(), (int) Math.floor(x), (int) Math.floor(corps.getY()),
                (int) Math.floor(z), f.numero, total, nbCurios, autres.size(), fixes[CorpsContenu.SAC] != null ? "oui" : "non");
        return true;
    }

    /** Filets anti-lag : trop de corps dans ce chunk ou sur le serveur -> le plus ancien disparait (journalise). */
    private static void plafonds(ServerLevel level, CorpsEntity nouveau) {
        ChunkPos cp = new ChunkPos(nouveau.blockPosition());
        AABB zone = new AABB(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX() + 1, level.getMaxBuildHeight(),
                cp.getMaxBlockZ() + 1);
        List<CorpsEntity> ici = level.getEntities(Registres.CORPS.get(), zone, c -> !c.isRemoved());
        int max = CorpsConfig.MAX_PAR_CHUNK.get();
        while (ici.size() >= max) {
            CorpsEntityServeur vieux = null;
            for (CorpsEntity c : ici) if (c instanceof CorpsEntityServeur s && (vieux == null || s.creeLe() < vieux.creeLe())) vieux = s;
            if (vieux == null) break;
            vieux.disparaitre("plus de " + max + " corps dans ce chunk");
            ici.remove(vieux);
        }
        MinecraftServer serveur = level.getServer();
        Registre r = Registre.de(serveur);
        r.purger(level.getGameTime());
        int maxMonde = CorpsConfig.MAX_PAR_MONDE.get();
        int garde = 0;
        while (r.nbActifs() >= maxMonde && garde++ < 64) {
            Registre.Fiche f = r.plusAncienne(null);
            if (f == null) break;
            Entity e = null;
            ServerLevel ou = serveur.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(f.dimension)));
            if (ou != null) e = ou.getEntity(f.entite);
            if (e instanceof CorpsEntityServeur c) c.disparaitre("plus de " + maxMonde + " corps sur le serveur");
            else {
                r.programmer(f, Registre.Action.SUPPRIMER);
                HeralizCorps.LOG.info("[corps] corps n{} de {} (chunk non charge) sera retire : plus de {} corps sur le serveur", f.numero, f.nom,
                        maxMonde);
            }
        }
    }

    /** Pour le banc d'essai : niveau d'une fiche. */
    public static ServerLevel niveau(MinecraftServer serveur, String dimension) {
        ServerLevel l = serveur.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimension)));
        return l == null ? serveur.getLevel(Level.OVERWORLD) : l;
    }
}
