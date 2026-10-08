package fr.heraliz.interfaces.client;

import fr.heraliz.interfaces.HeralizInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Pack de textures du serveur, imposé : les textures HeraliZ et l'herbe sont rangées dans le jar
 * ({@code resourcepacks/textures}, {@code resourcepacks/herbe}), toujours actives, juste au-dessus des textures des mods
 * (même place que l'ancien réglage d'options.txt). Les packs du dossier {@code resourcepacks} et les packs optionnels de
 * Minecraft sont ignorés ; le bouton « Packs de ressources » des options est retiré. Les packs fournis par les autres
 * mods (TaCZ, terrain, musiques…) ne changent pas.
 */
public final class PackImpose {
    private PackImpose() {
    }

    /** Nos packs, du bas vers le haut. */
    public static final String TEXTURES = HeralizInterface.MODID + ":textures";
    public static final String HERBE = HeralizInterface.MODID + ":herbe";
    private static final List<String> NOTRES = List.of(TEXTURES, HERBE);
    /** Packs optionnels de Minecraft (même rôle qu'un pack ajouté par le joueur). */
    private static final Set<String> REFUSES = Set.of("programmer_art", "high_contrast");
    /** Point d'ancrage : les textures des mods. */
    private static final String MODS = "mod_resources";

    /** Packs ajoutés par le joueur (dossier resourcepacks) ou optionnels : jamais sélectionnés. */
    public static boolean refuse(String id) {
        return id.startsWith("file/") || REFUSES.contains(id);
    }

    /** Sélection imposée : sans les packs refusés, nos deux packs juste au-dessus de {@link #MODS}. */
    public static List<String> imposer(Collection<String> selection, Collection<String> disponibles) {
        List<String> l = new ArrayList<>();
        for (String id : selection) {
            if (!refuse(id) && !NOTRES.contains(id)) l.add(id);
        }
        int i = l.indexOf(MODS);
        int pos = i >= 0 ? i + 1 : l.size();
        for (String id : NOTRES) {
            if (disponibles.contains(id)) l.add(pos++, id);
        }
        return l;
    }

    /** Appelé après la lecture des packs choisis dans options.txt (mixin OptionsMixin), avant le premier chargement. */
    public static void appliquer(PackRepository repo) {
        List<String> avant = new ArrayList<>(repo.getSelectedIds());
        List<String> apres = imposer(avant, repo.getAvailableIds());
        if (!apres.equals(avant)) {
            repo.setSelected(apres);
            HeralizInterface.LOG.info("[interface] packs de ressources imposés : {}", apres);
        }
    }

    // ------------------------------------------------------------------ enregistrement des deux packs (bus du mod)
    @Mod.EventBusSubscriber(modid = HeralizInterface.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Enregistrement {
        private Enregistrement() {
        }

        @SubscribeEvent
        public static void packs(AddPackFindersEvent e) {
            if (e.getPackType() != PackType.CLIENT_RESOURCES) return;
            ajouter(e, TEXTURES, "resourcepacks/textures", "HeraliZ : textures");
            ajouter(e, HERBE, "resourcepacks/herbe", "HeraliZ : herbe");
        }

        private static void ajouter(AddPackFindersEvent e, String id, String dossier, String titre) {
            Path chemin = ModList.get().getModFileById(HeralizInterface.MODID).getFile().findResource(dossier);
            if (!Files.isDirectory(chemin)) {
                HeralizInterface.LOG.error("[interface] pack {} absent du jar ({})", id, dossier);
                return;
            }
            Pack pack = Pack.readMetaAndCreate(id, Component.literal(titre), true, n -> new PathPackResources(n, chemin, true),
                    PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack != null) e.addRepositorySource(c -> c.accept(pack));
        }
    }

    // ------------------------------------------------------------------ plus d'écran de choix des packs (bus Forge)
    @Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT)
    public static final class Ecrans {
        private Ecrans() {
        }

        /** Bouton « Packs de ressources… » des options retiré. */
        @SubscribeEvent
        public static void options(ScreenEvent.Init.Post e) {
            if (!(e.getScreen() instanceof OptionsScreen)) return;
            for (var w : e.getListenersList()) {
                if (w instanceof AbstractWidget b && b.getMessage().getContents() instanceof TranslatableContents tc
                        && "options.resourcepack".equals(tc.getKey())) {
                    b.visible = false;
                    b.active = false;
                }
            }
        }

        /** L'écran des packs de ressources ne s'ouvre plus depuis les options (celui des packs de données d'un monde, si). */
        @SubscribeEvent
        public static void ouverture(ScreenEvent.Opening e) {
            if (e.getNewScreen() instanceof PackSelectionScreen && e.getCurrentScreen() instanceof OptionsScreen) {
                e.setCanceled(true);
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("heraliz_interface.pack_impose"), true);
                }
            }
        }
    }
}
