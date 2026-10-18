package fr.heralisz.utils.inspecter;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.coffres.CoffresPersonnels;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * /inspecter (18/10, demande du propriétaire) : réservé aux op (permission 4), après le /login d'AuthMe (HeraliZ Grades,
 * par réflexion ; AuthMe injoignable : refusé). Montre et laisse modifier, même joueur DÉCONNECTÉ (Cible) :
 * <ul>
 *    <li>{@code /inspecter <joueur>} : inventaire, armure, main gauche, emplacements Curios et ses hérals (pièce d'or) ;
 *    boutons vers le sac à dos, les coffres et la base ;</li>
 *    <li>{@code sac} : contenu du sac à dos porté ;</li>
 *    <li>{@code coffres} : planque, coffre VIP (CoffresPersonnels) et coffre de l'Ender ;</li>
 *    <li>{@code base} : tout ce que contiennent les conteneurs de sa base CRac (claim de son groupe), d'un coup, conteneur
 *    par conteneur (chaque groupe commence par le bloc du conteneur et ses coordonnées), page par page.</li>
 * </ul>
 * Écrans de coffre de Minecraft (rien à installer chez les joueurs). Les objets sont toujours ceux d'origine (déplacés,
 * jamais copiés). Rien testé en jeu.
 */
public final class Inspecter {
    private static final int PAGE = 45;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("heraliz_inspecter", Comparator.comparingLong(ChunkPos::toLong), 6000);

    private Inspecter() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(Inspecter::commandes);
        MinecraftForge.EVENT_BUS.addListener(Inspecter::connexion);
        MinecraftForge.EVENT_BUS.addListener(Inspecter::deconnexion);
    }

    // ------------------------------------------------------------------ commande

    private static void commandes(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("inspecter")
                .requires(s -> s.hasPermission(4) && s.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("joueur", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), b))
                        .executes(ctx -> lancer(ctx, "inventaire"))
                        .then(Commands.literal("inventaire").executes(ctx -> lancer(ctx, "inventaire")))
                        .then(Commands.literal("sac").executes(ctx -> lancer(ctx, "sac")))
                        .then(Commands.literal("coffres").executes(ctx -> lancer(ctx, "coffres")))
                        .then(Commands.literal("base").executes(ctx -> lancer(ctx, "base")))));
    }

    private static int lancer(CommandContext<CommandSourceStack> ctx, String vue) {
        ServerPlayer staff = (ServerPlayer) ctx.getSource().getEntity();
        if (staff == null) {
            return 0;
        }
        if (!authentifie(staff)) {
            dire(staff, Component.translatableWithFallback("heralisz_utils.inspecter.login",
                    "Connecte-toi d'abord avec /login.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String nom = StringArgumentType.getString(ctx, "joueur");
        GameProfile profil = profil(staff.server, nom);
        Cible c = profil == null ? null : Cible.obtenir(staff.server, profil);
        if (c == null) {
            dire(staff, Component.translatableWithFallback("heralisz_utils.inspecter.inconnu",
                    "Aucun joueur « %s » dans ce monde.", nom).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (c.uuid.equals(staff.getUUID()) && ("inventaire".equals(vue) || "sac".equals(vue))) {
            dire(staff, Component.translatableWithFallback("heralisz_utils.inspecter.soi",
                    "Ton propre inventaire : ouvre-le normalement.").withStyle(ChatFormatting.RED));
            return 0;
        }
        dire(staff, Component.translatableWithFallback(c.horsLigne ? "heralisz_utils.inspecter.fiche_hors" : "heralisz_utils.inspecter.fiche",
                c.horsLigne ? "%s (hors ligne) : %s." : "%s (en ligne) : %s.", c.nom, herals(c.uuid)).withStyle(ChatFormatting.GOLD));
        switch (vue) {
            case "sac" -> ouvrirSac(staff, c);
            case "coffres" -> ouvrirCoffres(staff, c);
            case "base" -> ouvrirBase(staff, c);
            default -> ouvrirInventaire(staff, c);
        }
        return 1;
    }

    /** Profil d'un joueur connecté, déjà venu (cache des profils) ou sauvegardé dans ce monde (serveur cracké). */
    private static GameProfile profil(MinecraftServer srv, String nom) {
        ServerPlayer en = srv.getPlayerList().getPlayerByName(nom);
        if (en != null) {
            return en.getGameProfile();
        }
        try {
            Optional<GameProfile> g = srv.getProfileCache() == null ? Optional.empty() : srv.getProfileCache().get(nom);
            if (g.isPresent() && Cible.fichier(srv, g.get().getId()).isFile()) {
                return g.get();
            }
        } catch (Exception ignored) {
        }
        UUID horsLigne = UUIDUtil.createOfflinePlayerUUID(nom);
        return Cible.fichier(srv, horsLigne).isFile() ? new GameProfile(horsLigne, nom) : null;
    }

    // ------------------------------------------------------------------ vues

    private static void ouvrir(ServerPlayer staff, Cible c, Vue vue, Component titre, Runnable entretien) {
        staff.openMenu(new SimpleMenuProvider((id, inv, p) -> new MenuInspection(id, inv, c, vue, entretien), titre));
    }

    static void ouvrirInventaire(ServerPlayer staff, Cible c) {
        if (!c.valide()) {
            return;
        }
        Vue vue = new Vue();
        Inventory inv = c.joueur.getInventory();
        for (int i = 0; i < 27; i++) {
            vue.cases[i] = Vue.de(inv, 9 + i);
        }
        for (int i = 0; i < 9; i++) {
            vue.cases[27 + i] = Vue.de(inv, i);
        }
        for (int k = 0; k < 4; k++) {                         // casque, plastron, jambières, bottes
            vue.cases[36 + k] = Vue.de(inv, 39 - k);
        }
        vue.cases[40] = Vue.de(inv, 40);                      // main gauche
        vue.cases[41] = Vue.decor(infos(c, null));
        Container sac = Cible.sac(c.joueur);
        vue.cases[42] = Vue.bouton(nomme(sac != null ? new ItemStack(Items.LEATHER) : new ItemStack(Items.BARRIER),
                Component.translatableWithFallback("heralisz_utils.inspecter.b_sac", "Sac à dos")), () -> ouvrirSac(staff, c));
        vue.cases[43] = Vue.bouton(nomme(new ItemStack(Items.ENDER_CHEST),
                Component.translatableWithFallback("heralisz_utils.inspecter.b_coffres", "Planque, coffre VIP et coffre de l'Ender")),
                () -> ouvrirCoffres(staff, c));
        vue.cases[44] = Vue.bouton(nomme(new ItemStack(Items.BRICKS),
                Component.translatableWithFallback("heralisz_utils.inspecter.b_base", "Contenu de la base")), () -> ouvrirBase(staff, c));
        List<Vue.Case> curios = CuriosVue.cases(c.joueur);
        for (int i = 0; i < 9; i++) {
            vue.cases[45 + i] = i < curios.size() ? curios.get(i) : Vue.decor(vitre());
        }
        ouvrir(staff, c, vue, Component.translatableWithFallback("heralisz_utils.inspecter.t_inventaire", "Inventaire de %s", c.nom), null);
    }

    static void ouvrirSac(ServerPlayer staff, Cible c) {
        if (!c.valide()) {
            return;
        }
        Container sac = Cible.sac(c.joueur);
        if (sac == null) {
            dire(staff, Component.translatableWithFallback("heralisz_utils.inspecter.sans_sac", "%s ne porte pas de sac à dos.", c.nom)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        List<Vue.Case> contenu = new ArrayList<>();
        for (int i = 0; i < sac.getContainerSize(); i++) {
            contenu.add(Vue.de(sac, i));
        }
        pages(staff, c, contenu, Component.translatableWithFallback("heralisz_utils.inspecter.t_sac", "Sac de %s", c.nom), null, null);
    }

    static void ouvrirCoffres(ServerPlayer staff, Cible c) {
        if (!c.valide()) {
            return;
        }
        List<Vue.Case> contenu = new ArrayList<>();
        Container planque = CoffresPersonnels.coffre(c.srv, c.uuid, CoffresPersonnels.Sorte.PLANQUE);
        Container vip = CoffresPersonnels.coffre(c.srv, c.uuid, CoffresPersonnels.Sorte.VIP);
        Container ender = c.joueur.getEnderChestInventory();
        for (int i = 0; i < CoffresPersonnels.CASES; i++) {
            contenu.add(Vue.de(planque, i));
        }
        for (int i = 0; i < CoffresPersonnels.CASES; i++) {
            contenu.add(Vue.de(vip, i));
        }
        for (int i = 0; i < ender.getContainerSize(); i++) {
            contenu.add(Vue.de(ender, i));
        }
        Vue.Case[] reperes = {
                Vue.decor(nomme(new ItemStack(Items.CHEST), Component.translatableWithFallback("heralisz_utils.inspecter.l_planque", "1re ligne : planque"))),
                Vue.decor(nomme(new ItemStack(Items.GOLD_BLOCK), Component.translatableWithFallback("heralisz_utils.inspecter.l_vip", "2e ligne : coffre VIP"))),
                Vue.decor(nomme(new ItemStack(Items.ENDER_CHEST), Component.translatableWithFallback("heralisz_utils.inspecter.l_ender", "Lignes 3 à 5 : coffre de l'Ender")))};
        pages(staff, c, contenu, Component.translatableWithFallback("heralisz_utils.inspecter.t_coffres", "Coffres de %s", c.nom), reperes, null);
    }

    static void ouvrirBase(ServerPlayer staff, Cible c) {
        if (!c.valide()) {
            return;
        }
        Object[] base = BaseCrac.base(c.srv, c.uuid);
        if (base == null) {
            dire(staff, Component.translatableWithFallback("heralisz_utils.inspecter.sans_base", "%s n'a pas de base (pas de groupe ou pas de Claim Block).", c.nom)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel l = (ServerLevel) base[0];
        BlockPos centre = (BlockPos) base[1];
        int r = (Integer) base[2], ry = (Integer) base[3];
        List<ChunkPos> morceaux = new ArrayList<>();
        for (int cx = (centre.getX() - r) >> 4; cx <= (centre.getX() + r) >> 4; cx++) {
            for (int cz = (centre.getZ() - r) >> 4; cz <= (centre.getZ() + r) >> 4; cz++) {
                morceaux.add(new ChunkPos(cx, cz));
            }
        }
        Runnable garder = () -> morceaux.forEach(m -> l.getChunkSource().addRegionTicket(TICKET, m, 1, m));
        garder.run();
        List<BlockEntity> conteneurs = new ArrayList<>();
        for (ChunkPos m : morceaux) {
            LevelChunk ch = l.getChunk(m.x, m.z);
            for (BlockEntity be : ch.getBlockEntities().values()) {
                BlockPos p = be.getBlockPos();
                if (be instanceof Container && Math.abs(p.getX() - centre.getX()) <= r && Math.abs(p.getZ() - centre.getZ()) <= r
                        && Math.abs(p.getY() - centre.getY()) <= ry) {
                    conteneurs.add(be);
                }
            }
        }
        conteneurs.sort(Comparator.comparingInt((BlockEntity b) -> b.getBlockPos().getY())
                .thenComparingInt(b -> b.getBlockPos().getX()).thenComparingInt(b -> b.getBlockPos().getZ()));
        List<Vue.Case> contenu = new ArrayList<>();
        for (BlockEntity be : conteneurs) {
            Container ct = (Container) be;
            BlockPos p = be.getBlockPos();
            ItemStack icone = new ItemStack(be.getBlockState().getBlock().asItem());
            if (icone.isEmpty()) {
                icone = new ItemStack(Items.CHEST);
            }
            contenu.add(Vue.decor(nomme(icone, Component.translatableWithFallback("heralisz_utils.inspecter.conteneur", "%s · %s %s %s",
                    be.getBlockState().getBlock().getName(), String.valueOf(p.getX()), String.valueOf(p.getY()), String.valueOf(p.getZ())))));
            for (int i = 0; i < ct.getContainerSize(); i++) {
                final int k = i;
                contenu.add(Vue.de(ct, k, s -> ct.canPlaceItem(k, s), () -> !be.isRemoved() && l.getBlockEntity(p) == be));
            }
            while (contenu.size() % 9 != 0) {
                contenu.add(null);
            }
        }
        if (conteneurs.isEmpty()) {
            dire(staff, Component.translatableWithFallback("heralisz_utils.inspecter.base_vide", "Aucun conteneur dans la base de %s.", c.nom)
                    .withStyle(ChatFormatting.GRAY));
        }
        pages(staff, c, contenu, Component.translatableWithFallback("heralisz_utils.inspecter.t_base", "Base de %s (%s conteneurs)",
                c.nom, String.valueOf(conteneurs.size())), null, garder);
    }

    /**
     * Vue page par page (45 cases par page) ; dernière ligne : page précédente, retour à l'inventaire, repères, hérals, page
     * suivante.
     */
    private static void pages(ServerPlayer staff, Cible c, List<Vue.Case> contenu, Component titre, Vue.Case[] reperes, Runnable entretien) {
        Vue vue = new Vue();
        int nbPages = Math.max(1, (contenu.size() + PAGE - 1) / PAGE);
        int[] page = {0};
        Runnable[] afficher = new Runnable[1];
        afficher[0] = () -> {
            for (int i = 0; i < PAGE; i++) {
                int k = page[0] * PAGE + i;
                Vue.Case cs = k < contenu.size() ? contenu.get(k) : null;
                vue.cases[i] = cs != null ? cs : Vue.decor(ItemStack.EMPTY);
            }
            for (int i = PAGE; i < Vue.TAILLE; i++) {
                vue.cases[i] = Vue.decor(vitre());
            }
            if (page[0] > 0) {
                vue.cases[45] = Vue.bouton(nomme(new ItemStack(Items.ARROW), Component.translatableWithFallback("heralisz_utils.inspecter.precedente",
                        "Page précédente")), () -> {
                    page[0]--;
                    afficher[0].run();
                });
            }
            vue.cases[46] = Vue.bouton(nomme(new ItemStack(Items.OAK_DOOR), Component.translatableWithFallback("heralisz_utils.inspecter.retour",
                    "Retour à l'inventaire")), () -> ouvrirInventaire(staff, c));
            if (reperes != null) {
                for (int i = 0; i < reperes.length && i < 3; i++) {
                    vue.cases[47 + i] = reperes[i];
                }
            }
            vue.cases[51] = Vue.decor(infos(c, nbPages > 1 ? (page[0] + 1) + " / " + nbPages : null));
            if (page[0] < nbPages - 1) {
                vue.cases[53] = Vue.bouton(nomme(new ItemStack(Items.SPECTRAL_ARROW), Component.translatableWithFallback("heralisz_utils.inspecter.suivante",
                        "Page suivante")), () -> {
                    page[0]++;
                    afficher[0].run();
                });
            }
            if (entretien != null) {
                entretien.run();
            }
        };
        afficher[0].run();
        ouvrir(staff, c, vue, titre, entretien);
    }

    // ------------------------------------------------------------------ connexions

    /** Le joueur inspecté hors ligne se connecte : vues fermées (sa sauvegarde, déjà à jour, est la sienne). */
    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        Cible c = Cible.ouverte(e.getEntity().getUUID());
        if (c != null && c.horsLigne) {
            fermerVues(c, true);
        }
    }

    /** Le joueur inspecté en ligne se déconnecte : vues fermées AVANT sa sauvegarde (rien ne bouge plus après). */
    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        Cible c = Cible.ouverte(e.getEntity().getUUID());
        if (c != null && !c.horsLigne) {
            fermerVues(c, true);
        }
    }

    static void fermerVues(Cible c, boolean prevenir) {
        for (ServerPlayer p : new ArrayList<>(c.srv.getPlayerList().getPlayers())) {
            if (p.containerMenu instanceof MenuInspection m && m.cible == c) {
                p.closeContainer();
                if (prevenir) {
                    dire(p, Component.translatableWithFallback("heralisz_utils.inspecter.ferme",
                            "%s vient de se connecter ou de se déconnecter : vue fermée, relance /inspecter.", c.nom).withStyle(ChatFormatting.YELLOW));
                }
            }
        }
    }

    // ------------------------------------------------------------------ outils

    private static void dire(ServerPlayer p, Component c) {
        p.sendSystemMessage(c);
    }

    private static ItemStack vitre() {
        return nomme(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Component.literal(" "));
    }

    private static ItemStack nomme(ItemStack s, Component nom) {
        s.setHoverName(nom.copy().withStyle(st -> st.withItalic(false)));
        return s;
    }

    /** Pièce d'or : hérals du joueur, en ligne ou non, et la page. */
    private static ItemStack infos(Cible c, String page) {
        ItemStack s = nomme(new ItemStack(Items.GOLD_INGOT), Component.literal(herals(c.uuid)).withStyle(ChatFormatting.GOLD));
        ListTag lore = new ListTag();
        Component etat = c.horsLigne
                ? Component.translatableWithFallback("heralisz_utils.inspecter.hors_ligne", "Hors ligne").withStyle(ChatFormatting.GRAY)
                : Component.translatableWithFallback("heralisz_utils.inspecter.en_ligne", "En ligne").withStyle(ChatFormatting.GREEN);
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(c.nom + " · ").withStyle(ChatFormatting.WHITE).append(etat))));
        if (page != null) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatableWithFallback("heralisz_utils.inspecter.page",
                    "Page %s", page).withStyle(ChatFormatting.GRAY))));
        }
        s.getOrCreateTagElement("display").put("Lore", lore);
        return s;
    }

    /** Solde en hérals (HeraliZ Clans et bases), « ? » sans l'économie. */
    private static String herals(UUID joueur) {
        try {
            return fr.heralisz.economie.api.EconomieApi.format(fr.heralisz.economie.api.EconomieApi.solde(joueur));
        } catch (Throwable t) {
            return "? hérals";
        }
    }

    // ------------------------------------------------------------------ AuthMe (HeraliZ Grades, par réflexion)
    private static Method authentifie;
    private static boolean authCherche;

    private static boolean authentifie(ServerPlayer p) {
        if (!p.server.isDedicatedServer() || p.server.usesAuthentication()) {
            return true;
        }
        if (!authCherche) {
            authCherche = true;
            try {
                authentifie = Class.forName("fr.heraliz.grades.serveur.GradesApi").getMethod("authentifie", ServerPlayer.class);
            } catch (Throwable t) {
                HeraliszUtils.LOG.error("[inspecter] HeraliZ Grades (AuthMe) injoignable : /inspecter refusé ({})", t.toString());
            }
        }
        if (authentifie == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(authentifie.invoke(null, p));
        } catch (Throwable t) {
            return false;
        }
    }
}
