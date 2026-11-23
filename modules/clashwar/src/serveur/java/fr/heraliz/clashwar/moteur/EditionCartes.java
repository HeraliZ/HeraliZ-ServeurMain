package fr.heraliz.clashwar.moteur;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.moteur.api.Annonces;
import fr.heraliz.clashwar.moteur.api.Arenes;
import fr.heraliz.clashwar.moteur.api.Carte;
import fr.heraliz.clashwar.moteur.api.Instance;
import fr.heraliz.clashwar.moteur.api.Pose;
import fr.heraliz.clashwar.moteur.arenes.ArenesService;
import fr.heraliz.clashwar.moteur.arenes.RegistreArenes;
import fr.heraliz.clashwar.moteur.arenes.Structure;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Retouche des cartes du ClashWar en jeu par le staff (23/11, demande du propriétaire : « simple pour faire spawn et
 * despawn » une carte, la modifier et la ré-enregistrer). {@code /clashwar carte poser <carte>} pose une instance de la
 * carte dans le monde des arènes (réservée à l'édition, jamais prise par un match) et y emmène le joueur ;
 * {@code /clashwar carte sauver} enregistre l'arène où il se trouve dans
 * {@code <serveur>/heraliz_clashwar/cartes_retouchees/<carte>.nbt}, qui passe devant la carte du jar
 * ({@link Structure#lire}) ; {@code /clashwar carte quitter} le ramène au spawn. Toute instance retouchée est marquée
 * obsolète (registre des arènes) : au redémarrage suivant, chaque match reprend une arène neuve, posée d'après le
 * fichier sauvé (ou d'après le jar si rien n'a été sauvé) ; d'ici là elle reste réservée. Opérateurs (niveau 2).
 */
final class EditionCartes {
    /** Occupant d'une instance réservée à l'édition. */
    static final String EDITION = "edition";

    private EditionCartes() {
    }

    static int poser(CommandContext<CommandSourceStack> x) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = x.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(x, "carte");
        if (Arenes.carte(id) == null) {
            x.getSource().sendFailure(Component.translatableWithFallback("heraliz_clashwar.commande.carte.inconnue",
                    "Carte inconnue : %s (/clashwar arenes liste).", id));
            return 0;
        }
        Instance i = Arenes.reserver(id);
        if (i == null) {
            x.getSource().sendFailure(Component.translatableWithFallback("heraliz_clashwar.commande.arenes.reservation_impossible",
                    "Réservation impossible (voir le journal)."));
            return 0;
        }
        i.occuper(EDITION);
        obsolete(p.getServer(), i);
        UUID u = p.getUUID();
        MinecraftServer s = p.getServer();
        if (i.prete()) {
            aller(p, i);
        } else {
            Annonces.retour(x.getSource(), Component.translatableWithFallback("heraliz_clashwar.commande.carte.pose",
                    "Carte %s en cours de pose (%s) : tu y seras emmené dès qu'elle est prête.", id, ArenesService.progression(i)));
            i.quandPrete(() -> {
                ServerPlayer q = s.getPlayerList().getPlayer(u);
                if (q != null) aller(q, i);
            });
        }
        return 1;
    }

    private static void aller(ServerPlayer p, Instance i) {
        Pose pose = i.point("spectateur");
        if (pose == null) pose = i.point("spawn_a");
        if (pose == null) pose = new Pose(i.bornes().getCenter().x, i.bornes().minY + 2, i.bornes().getCenter().z, 0);
        pose.teleporter(p, i.niveau());
        p.sendSystemMessage(Component.translatableWithFallback("heraliz_clashwar.commande.carte.arrivee",
                "Carte %s : retouche-la (en créatif), puis /clashwar carte sauver ; /clashwar carte quitter pour revenir au spawn.",
                i.carte().id()));
    }

    static int sauver(CommandContext<CommandSourceStack> x) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = x.getSource().getPlayerOrException();
        Instance i = Arenes.instanceA(p.level(), p.getX(), p.getY(), p.getZ());
        if (i == null || !EDITION.equals(i.occupant())) {
            x.getSource().sendFailure(Component.translatableWithFallback("heraliz_clashwar.commande.carte.pas_dedans",
                    "Place-toi dans une carte posée par /clashwar carte poser."));
            return 0;
        }
        Carte c = i.carte();
        try {
            StructureTemplate t = new StructureTemplate();
            t.fillFromWorld(i.niveau(), i.origine(), c.taille(), false, Blocks.STRUCTURE_VOID);
            CompoundTag tag = t.save(new CompoundTag());
            int gardes = sansAirNiBarrieres(tag);
            Path dossier = Structure.dossierRetouches(p.getServer());
            Files.createDirectories(dossier);
            Path fichier = dossier.resolve(c.id() + ".nbt"), tmp = dossier.resolve(c.id() + ".nbt.tmp");
            try (OutputStream o = Files.newOutputStream(tmp)) {
                NbtIo.writeCompressed(tag, o);
            }
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            ArenesService.oublierStructures();
            obsoletesDeLaCarte(p.getServer(), c.id());
            HeralizClashWar.LOG.info("[matchs] carte {} retouchee et sauvee par {} : {} blocs -> {}", c.id(), p.getGameProfile().getName(),
                    gardes, fichier);
            Annonces.retour(x.getSource(), Component.translatableWithFallback("heraliz_clashwar.commande.carte.sauvee",
                    "Carte %s sauvée (%s blocs). Les matchs la prendront après le prochain redémarrage.", c.id(), String.valueOf(gardes)));
            return 1;
        } catch (Exception ex) {
            HeralizClashWar.LOG.error("[matchs] sauvegarde de la carte {} impossible", c.id(), ex);
            x.getSource().sendFailure(Component.translatableWithFallback("heraliz_clashwar.commande.carte.echec",
                    "Sauvegarde impossible (voir le journal) : rien n'a changé."));
            return 0;
        }
    }

    static int quitter(CommandContext<CommandSourceStack> x) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = x.getSource().getPlayerOrException();
        GardeHorsMatch.spawn(p);
        return 1;
    }

    /**
     * Garde seulement les blocs posés : l'air (la pose ne le pose pas) et les barrières (murs invisibles ajoutés à chaque
     * pose autour de la carte) sont retirés. Rend le nombre de blocs gardés.
     */
    private static int sansAirNiBarrieres(CompoundTag tag) {
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        Set<Integer> retires = new HashSet<>();
        for (int k = 0; k < palette.size(); k++) {
            String nom = palette.getCompound(k).getString("Name");
            if (nom.equals("minecraft:air") || nom.equals("minecraft:cave_air") || nom.equals("minecraft:void_air")
                    || nom.equals("minecraft:barrier") || nom.equals("minecraft:structure_void")) {
                retires.add(k);
            }
        }
        ListTag blocs = tag.getList("blocks", Tag.TAG_COMPOUND), gardes = new ListTag();
        for (int k = 0; k < blocs.size(); k++) {
            CompoundTag b = blocs.getCompound(k);
            if (!retires.contains(b.getInt("state"))) gardes.add(b);
        }
        tag.put("blocks", gardes);
        return gardes.size();
    }

    /** L'instance retouchée ne servira plus après le redémarrage (une neuve sera posée d'après le bon fichier). */
    private static void obsolete(MinecraftServer s, Instance i) {
        RegistreArenes reg = RegistreArenes.de(s);
        RegistreArenes.Entree e = reg.entree(i.niveau().dimension().location().toString(), i.index());
        if (e != null && !e.obsolete) {
            e.obsolete = true;
            reg.setDirty();
        }
    }

    /** Toutes les instances de la carte sauvée : reposées d'après le nouveau fichier au prochain redémarrage. */
    private static void obsoletesDeLaCarte(MinecraftServer s, String carte) {
        RegistreArenes reg = RegistreArenes.de(s);
        for (RegistreArenes.Entree e : reg.entrees) {
            if (carte.equals(e.carte) && !e.obsolete) {
                e.obsolete = true;
                reg.setDirty();
            }
        }
    }
}
