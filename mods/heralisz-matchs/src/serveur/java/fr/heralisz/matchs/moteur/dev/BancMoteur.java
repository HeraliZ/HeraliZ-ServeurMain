package fr.heralisz.matchs.moteur.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.Noms;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Arenes;
import fr.heralisz.matchs.moteur.api.ChoixClasse;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.CombatTag;
import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.Equipement;
import fr.heralisz.matchs.moteur.api.Groupe;
import fr.heralisz.matchs.moteur.api.Groupes;
import fr.heralisz.matchs.moteur.api.Hud;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.KillFeed;
import fr.heralisz.matchs.moteur.api.Kit;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.api.Pose;
import fr.heralisz.matchs.moteur.coffre.Coffres;
import fr.heralisz.matchs.moteur.combat.CombatEvents;
import fr.heralisz.matchs.moteur.combat.CombatService;
import fr.heralisz.matchs.moteur.combat.RestrictionsMatch;
import fr.heralisz.matchs.moteur.compat.Compat;
import fr.heralisz.matchs.moteur.compat.Soif;
import fr.heralisz.matchs.moteur.groupes.GroupesService;
import fr.heralisz.matchs.moteur.kits.ChoixService;
import fr.heralisz.matchs.moteur.spectateur.Spectateurs;
import fr.heralisz.matchs.moteur.MoteurSons;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Banc d'essai du moteur (client de dev seulement : {@code /matchs test ...}, enregistre hors production). Chaque
 * verification ecrit une ligne {@code [matchs] BANC ... OK|ECHEC} dans le journal et un message dans le chat.
 * <ul>
 *     <li>{@code coffre remplir} : inventaire complet (36 cases, armure, main gauche), sac a dos (Curios « back »
 *     avec contenu), autres Curios, effets, sante, faim, XP, soif, position et mode de jeu particuliers ;</li>
 *     <li>{@code coffre deposer} : empreinte puis depot + partie factice + objet prete, teleportation en arene ;</li>
 *     <li>{@code coffre verifier} : joueur vide (sauf objets pretes), Curios vides, sans effet, soif pleine et figee,
 *     fichier present, drapeau pose ;</li>
 *     <li>{@code coffre crash} : « redemarrage » simule (etat memoire du moteur efface, joueur sali) puis traitement de
 *     connexion (reprise apres crash) ;</li>
 *     <li>{@code coffre rendre} : restauration normale ; {@code coffre comparer} : empreinte identique, objets pretes
 *     purges, fichier supprime, drapeau retire ;</li>
 *     <li>{@code hud bandeau|annonce <style>|fil|compte|combat|fin}, {@code choix}, {@code groupe},
 *     {@code spectateur}, {@code elimination}, {@code fin} ;</li>
 *     <li>systeme de combat : {@code combat marquer|verifier|effacer} (tag et cle persistante), {@code combat commande}
 *     (/tp refuse au joueur tague puis accepte), {@code combat entrer|sortir} (partie factice : jamais de tag, etat
 *     « en match » pousse au client), {@code combat malus} (malus d'ArkaniaZ refuses en match, combat_log partout),
 *     {@code combat vehicule} (descente forcee d'un vehicule verrouille, pas de refus du Coffre),
 *     {@code combat deconnexion} (sanction simulee : le joueur meurt).</li>
 *     <li>Reanimation x moteur : {@code reanimation ...} ({@link BancReanimation}).</li>
 * </ul>
 */
public final class BancMoteur {
    private BancMoteur() {
    }

    private static CompoundTag empreinte, apres;
    private static PartieTest partie;
    private static final UUID VORTEX = UUID.fromString("00000000-0000-4000-8000-00000000a001");
    private static final UUID DZIGA = UUID.fromString("00000000-0000-4000-8000-00000000a002");
    private static final UUID KAYRA = UUID.fromString("00000000-0000-4000-8000-00000000a003");
    private static final UUID NYX = UUID.fromString("00000000-0000-4000-8000-00000000a004");

    static {
        Noms.FACTICES.put(VORTEX, "Vortex");
        Noms.FACTICES.put(DZIGA, "Dziga");
        Noms.FACTICES.put(KAYRA, "Kayra");
        Noms.FACTICES.put(NYX, "Nyx");
    }

    public static LiteralArgumentBuilder<CommandSourceStack> commandes() {
        return Commands.literal("test")
                .then(Commands.literal("coffre")
                        .then(Commands.literal("remplir").executes(x -> remplir(joueur(x))))
                        .then(Commands.literal("deposer").executes(x -> deposer(joueur(x))))
                        .then(Commands.literal("verifier").executes(x -> verifier(joueur(x))))
                        .then(Commands.literal("crash").executes(x -> crash(joueur(x))))
                        .then(Commands.literal("rendre").executes(x -> rendre(joueur(x))))
                        .then(Commands.literal("comparer").executes(x -> comparer(joueur(x))))
                        // depot fait par un vrai mode (ClashWar...) : empreinte avant, etat apres le retour, puis comparer
                        .then(Commands.literal("empreinte").executes(x -> {
                            empreinte = empreinteDe(joueur(x));
                            resultat(joueur(x), "coffre empreinte", true, empreinte.getList("inventaire", Tag.TAG_COMPOUND).size() + " piles");
                            return 1;
                        }).then(Commands.literal("depot").executes(x -> {
                            // l'empreinte de reference est le depot lui-meme (fichier sur disque ecrit par le mode)
                            CompoundTag d = Coffres.lire(joueur(x).getUUID());
                            if (d == null) {
                                resultat(joueur(x), "coffre empreinte depot", false, "aucun depot sur disque");
                                return 0;
                            }
                            d.remove("date");
                            d.remove("raison");
                            d.remove("brut");
                            empreinte = d;
                            resultat(joueur(x), "coffre empreinte depot", true, d.getList("inventaire", Tag.TAG_COMPOUND).size() + " piles, raison du depot lue sur disque");
                            return 1;
                        })))
                        .then(Commands.literal("apres").executes(x -> {
                            apres = empreinteDe(joueur(x));
                            resultat(joueur(x), "coffre apres", true, "depot " + (Coffre.aUnDepot(joueur(x).getUUID()) ? "TOUJOURS present" : "rendu"));
                            return 1;
                        })))
                .then(Commands.literal("hud")
                        .then(Commands.literal("bandeau").executes(x -> hudBandeau(joueur(x))))
                        .then(Commands.literal("annonce").then(Commands.argument("style", StringArgumentType.word())
                                .executes(x -> hudAnnonce(joueur(x), StringArgumentType.getString(x, "style")))))
                        .then(Commands.literal("fil").executes(x -> hudFil(joueur(x))))
                        .then(Commands.literal("compte").executes(x -> hudCompte(joueur(x))))
                        .then(Commands.literal("combat").executes(x -> hudCombat(joueur(x))))
                        .then(Commands.literal("fin").executes(x -> fin(joueur(x)))))
                .then(Commands.literal("combat")
                        .then(Commands.literal("marquer").executes(x -> combatMarquer(joueur(x))))
                        .then(Commands.literal("verifier").executes(x -> combatVerifier(joueur(x))))
                        .then(Commands.literal("effacer").executes(x -> {
                            CombatTag.effacer(joueur(x).getUUID());
                            return combatVerifier(joueur(x));
                        }))
                        .then(Commands.literal("commande").executes(x -> combatCommande(joueur(x))))
                        .then(Commands.literal("entrer").executes(x -> combatMatch(joueur(x), true)))
                        .then(Commands.literal("sortir").executes(x -> combatMatch(joueur(x), false)))
                        .then(Commands.literal("malus").executes(x -> combatMalus(joueur(x))))
                        .then(Commands.literal("vehicule").executes(x -> combatVehicule(joueur(x))))
                        .then(Commands.literal("deconnexion").executes(x -> combatDeconnexion(joueur(x)))))
                .then(Commands.literal("choix").executes(x -> choix(joueur(x))))
                .then(Commands.literal("groupe").executes(x -> groupe(joueur(x))))
                .then(Commands.literal("spectateur").executes(x -> spectateur(joueur(x))))
                .then(Commands.literal("elimination").executes(x -> elimination(joueur(x))))
                .then(BancReanimation.commandes())
                .then(Commands.literal("fin").executes(x -> fin(joueur(x))));
    }

    private static ServerPlayer joueur(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        return x.getSource().getPlayerOrException();
    }

    private static void resultat(ServerPlayer p, String test, boolean ok, String detail) {
        HeraliszMatchs.LOG.info("[matchs] BANC {} {} {}", test, ok ? "OK" : "ECHEC", detail);
        Annonces.chat(p, Component.literal(test.toUpperCase() + " · ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(ok ? "OK" : "ECHEC").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(detail.isEmpty() ? "" : "  " + detail).withStyle(ChatFormatting.GRAY)));
    }

    private static PartieTest partie(MinecraftServer s, ServerPlayer p) {
        if (partie != null && partie.phase() != Partie.Phase.TERMINEE) return partie;
        partie = new PartieTest("test-" + Long.toHexString(System.nanoTime() & 0xFFFFFF), s);
        Equipe a = new Equipe("a", "NOVA", 0x6FA8FF);
        a.ajouter(p.getUUID());
        a.ajouter(VORTEX);
        Equipe b = new Equipe("b", "RVN", 0xE85D5D);
        b.ajouter(DZIGA);
        b.ajouter(KAYRA);
        b.ajouter(NYX);
        partie.ajouterEquipe(a);
        partie.ajouterEquipe(b);
        MatchApi.enregistrer(partie);
        return partie;
    }

    // ------------------------------------------------------------------ coffre
    private static int remplir(ServerPlayer p) {
        MinecraftServer s = p.server;
        p.teleportTo(s.overworld(), 12.5, -60, 7.5, 33f, 12f);
        Inventory inv = p.getInventory();
        inv.clearContent();
        Compat.viderCurios(p);
        Item[] varies = {Items.COBBLESTONE, Items.OAK_LOG, Items.ARROW, Items.GOLDEN_APPLE, Items.PAPER, Items.IRON_INGOT, Items.BREAD, Items.TORCH};
        for (int i = 0; i < 36; i++) {
            ItemStack st;
            if (i % 9 == 0) {
                st = new ItemStack(Items.DIAMOND_SWORD);
                st.enchant(Enchantments.SHARPNESS, 1 + i / 9);
                st.setDamageValue(17 + i);
                st.setHoverName(Component.literal("Lame " + i));
            } else {
                Item it = varies[i % varies.length];
                st = new ItemStack(it, Math.max(1, Math.min(it.getMaxStackSize(), 64 - i)));
                if (i % 5 == 0) st.getOrCreateTag().putString("HeraliszBanc", "case-" + i);
            }
            inv.setItem(i, st);
        }
        ItemStack casque = new ItemStack(Items.IRON_HELMET);
        casque.setDamageValue(37);
        p.setItemSlot(EquipmentSlot.HEAD, casque);
        ItemStack plastron = new ItemStack(Items.DIAMOND_CHESTPLATE);
        plastron.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 3);
        p.setItemSlot(EquipmentSlot.CHEST, plastron);
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        inv.selected = 4;
        // sac a dos (backpacked) plein dans l'emplacement Curios « back »
        List<String> poses = new ArrayList<>();
        Item sac = ForgeRegistries.ITEMS.getValue(new ResourceLocation("backpacked", "backpack"));
        if (sac != null && sac != Items.AIR) {
            ItemStack bag = new ItemStack(sac);
            NonNullList<ItemStack> contenu = NonNullList.withSize(9, ItemStack.EMPTY);
            contenu.set(0, new ItemStack(Items.EMERALD, 23));
            contenu.set(3, new ItemStack(Items.DIAMOND, 7));
            ItemStack livre = new ItemStack(Items.BOOK);
            livre.setHoverName(Component.literal("Carnet du sac"));
            contenu.set(8, livre);
            CompoundTag t = bag.getOrCreateTag();
            ContainerHelper.saveAllItems(t, contenu);
            if (Compat.poserCurio(p, "back", 0, bag)) poses.add("back(sac)");
        }
        if (Compat.poserCurio(p, "belt", 0, new ItemStack(Items.LEAD, 2))) poses.add("belt");
        if (Compat.poserCurio(p, "necklace", 0, new ItemStack(Items.GOLD_NUGGET, 5))) poses.add("necklace");
        if (Compat.poserCurio(p, "ring", 0, new ItemStack(Items.IRON_NUGGET))) poses.add("ring");
        if (Compat.poserCurio(p, "hands", 0, new ItemStack(Items.RABBIT_HIDE))) poses.add("hands");
        // effets, sante, faim, XP, soif
        p.removeAllEffects();
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2400, 1));
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 6000, 0, true, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1800, 2));
        p.setGameMode(GameType.CREATIVE);
        p.setHealth(13.5f);
        p.setAbsorptionAmount(4f);
        p.getFoodData().setFoodLevel(9);
        p.getFoodData().setSaturation(2.5f);
        p.totalExperience = 700;
        p.experienceProgress = 0.42f;
        p.setExperienceLevels(17);
        Soif.definir(p, 7, 3);
        p.inventoryMenu.broadcastChanges();
        resultat(p, "coffre remplir", true, "36 cases + armure + main gauche, Curios " + poses + " (emplacements " + Compat.emplacementsCurios(p)
                + "), 3 effets, soif " + Soif.etat(p));
        return 1;
    }

    /** Empreinte comparable de l'etat du joueur (instantane sans date ni copie brute). */
    private static CompoundTag empreinteDe(ServerPlayer p) {
        CompoundTag t = Coffres.instantane(p, "", null);
        t.remove("date");
        t.remove("raison");
        return t;
    }

    private static int deposer(ServerPlayer p) {
        MinecraftServer s = p.server;
        CombatTag.effacer(p.getUUID());
        empreinte = empreinteDe(p);
        PartieTest pt = partie(s, p);
        boolean ok = Coffre.deposer(p, "banc");
        if (!ok) {
            resultat(p, "coffre deposer", false, "refus : " + Coffre.refus(p));
            return 0;
        }
        MatchApi.rejoindre(p, pt);
        // equipement de match prete (ids vanilla : TaCZ absent du client de dev)
        Kit kit = new Kit("banc", "Banc", "Bench", Kit.CLASSE_ASSAUT, List.of(), null, null, "minecraft:iron_sword",
                List.of(new Kit.Objet("minecraft:golden_apple", 2)), List.of(new Kit.Objet("minecraft:snowball", 4)), null,
                List.of("minecraft:iron_chestplate"), 0.5f, 0.5f, 0.5f);
        boolean donne = Equipement.donner(p, kit, pt.id());
        ServerLevel arene = s.getLevel(HeraliszMatchs.ARENES);
        if (arene != null) {
            // plateforme hors de la grille des instances (coordonnees negatives)
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) arene.setBlock(new BlockPos(-1000 + dx, 99, -1000 + dz), Blocks.GLASS.defaultBlockState(), 3);
            p.teleportTo(arene, -999.5, 100, -999.5, 0, 0);
        }
        resultat(p, "coffre deposer", true, "fichier " + Coffre.fichier(p.getUUID()).getFileName() + ", kit prete " + (donne ? "donne" : "NON donne"));
        return 1;
    }

    private static int verifier(ServerPlayer p) {
        List<String> pb = new ArrayList<>();
        Inventory inv = p.getInventory();
        int pretes = 0, autres = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            if (Equipement.estPrete(st)) pretes++;
            else autres++;
        }
        if (autres > 0) pb.add(autres + " objets non pretes");
        if (pretes == 0) pb.add("aucun objet prete");
        int curios = 0;
        for (ItemStack st : Compat.piecesCurios(p)) if (!Equipement.estPrete(st)) curios++;
        if (curios > 0) pb.add(curios + " Curios non vides");
        if (!p.getActiveEffects().isEmpty()) pb.add(p.getActiveEffects().size() + " effets");
        if (Soif.presente() && !Soif.etat(p).equals("soif 20, eau 20, figee")) pb.add("soif " + Soif.etat(p));
        if (!Coffre.aUnDepot(p.getUUID())) pb.add("fichier absent");
        if (!p.getPersistentData().getBoolean(MatchApi.DRAPEAU_EN_MATCH)) pb.add("drapeau absent");
        if (p.getHealth() < p.getMaxHealth()) pb.add("sante " + p.getHealth());
        if (p.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) pb.add("mode " + p.gameMode.getGameModeForPlayer());
        resultat(p, "coffre verifier", pb.isEmpty(), pb.isEmpty() ? "vide (" + pretes + " objets pretes), soif " + Soif.etat(p) : String.join(", ", pb));
        return pb.isEmpty() ? 1 : 0;
    }

    private static int crash(ServerPlayer p) {
        // le joueur se « sali t » pendant le match (objet prete supplementaire, effet), puis le serveur « redemarre »
        p.getInventory().add(Equipement.marquer(new ItemStack(Items.IRON_SHOVEL), partie == null ? "x" : partie.id()));
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
        MatchApi.vider();
        Spectateurs.vider();
        ChoixService.vider();
        CombatTag.vider();
        partie = null;
        HeraliszMatchs.LOG.info("[matchs] BANC crash simule : etat memoire du moteur efface, traitement de connexion");
        Coffres.surConnexion(p);
        // empreinte prise dans le meme tick que la restauration (sinon regeneration, faim et durees d'effets derivent)
        apres = empreinteDe(p);
        resultat(p, "coffre crash", !Coffre.aUnDepot(p.getUUID()), "reprise : depot " + (Coffre.aUnDepot(p.getUUID()) ? "TOUJOURS present" : "rendu"));
        return 1;
    }

    private static int rendre(ServerPlayer p) {
        boolean ok = Coffre.rendre(p);
        apres = empreinteDe(p);
        if (partie != null && partie.phase() != Partie.Phase.TERMINEE) {
            partie.fermer();
            partie = null;
        }
        resultat(p, "coffre rendre", ok, ok ? "" : "rien rendu");
        return ok ? 1 : 0;
    }

    private static int comparer(ServerPlayer p) {
        if (empreinte == null) {
            resultat(p, "coffre comparer", false, "aucune empreinte (deposer d'abord)");
            return 0;
        }
        if (apres == null) {
            resultat(p, "coffre comparer", false, "aucune restauration (rendre ou crash d'abord)");
            return 0;
        }
        CompoundTag now = apres;
        apres = null;
        List<String> diffs = new ArrayList<>();
        for (String k : empreinte.getAllKeys()) {
            Tag a = empreinte.get(k), b = now.get(k);
            if (k.equals("x") || k.equals("y") || k.equals("z")) {
                if (Math.abs(empreinte.getDouble(k) - now.getDouble(k)) > 0.01) diffs.add(k + " " + empreinte.getDouble(k) + " -> " + now.getDouble(k));
                continue;
            }
            if (k.equals("sante") || k.equals("absorption") || k.equals("yaw") || k.equals("pitch") || k.equals("xpProgres")) {
                if (Math.abs(empreinte.getFloat(k) - now.getFloat(k)) > 0.01) diffs.add(k + " " + empreinte.getFloat(k) + " -> " + now.getFloat(k));
                continue;
            }
            if (k.equals("effets") && a instanceof ListTag la && b instanceof ListTag lb) {
                // memes effets ; durees a quelques ticks pres (empreinte et depot ne sont pas pris dans le meme tick)
                String ecart = ecartEffets(la, lb);
                if (ecart != null) {
                    diffs.add("effets " + ecart);
                    HeraliszMatchs.LOG.info("[matchs] BANC diff effets :\n  avant {}\n  apres {}", a, b);
                }
                continue;
            }
            if (a == null ? b != null : !a.equals(b)) {
                diffs.add(k);
                HeraliszMatchs.LOG.info("[matchs] BANC diff {} :\n  avant {}\n  apres {}", k, a, b);
            }
        }
        for (String k : now.getAllKeys()) if (!empreinte.contains(k)) diffs.add("+" + k);
        int pretes = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (Equipement.estPrete(p.getInventory().getItem(i))) pretes++;
        for (ItemStack st : Compat.piecesCurios(p)) if (Equipement.estPrete(st)) pretes++;
        if (pretes > 0) diffs.add(pretes + " objets pretes restants");
        if (Coffre.aUnDepot(p.getUUID())) diffs.add("fichier encore present");
        if (p.getPersistentData().contains(MatchApi.DRAPEAU_EN_MATCH)) diffs.add("drapeau encore pose");
        ListTag inv = now.getList("inventaire", Tag.TAG_COMPOUND);
        String resume = inv.size() + " piles, Curios " + (now.contains("curios") ? Compat.piecesCurios(p).size() + " pieces" : "-")
                + ", " + p.getActiveEffects().size() + " effets, sante " + p.getHealth() + ", XP " + p.experienceLevel + ", soif " + Soif.etat(p)
                + ", " + p.level().dimension().location() + " " + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ())
                + ", mode " + p.gameMode.getGameModeForPlayer().getName();
        resultat(p, "coffre comparer", diffs.isEmpty(), diffs.isEmpty() ? "identique : " + resume : "differences : " + diffs);
        if (diffs.isEmpty()) empreinte = null;
        return diffs.isEmpty() ? 1 : 0;
    }

    /** Null si les deux listes d'effets sont identiques aux durees pres (ecart de 200 ticks au plus), sinon la raison. */
    @javax.annotation.Nullable
    private static String ecartEffets(ListTag a, ListTag b) {
        if (a.size() != b.size()) return a.size() + " -> " + b.size();
        for (int i = 0; i < a.size(); i++) {
            CompoundTag x = a.getCompound(i).copy(), y = b.getCompound(i).copy();
            int dx = x.getInt("Duration"), dy = y.getInt("Duration");
            x.remove("Duration");
            y.remove("Duration");
            if (!x.equals(y)) return "effet " + i + " different";
            if (Math.abs(dx - dy) > 200) return "duree " + dx + " -> " + dy;
        }
        return null;
    }

    // ------------------------------------------------------------------ HUD
    private static int hudBandeau(ServerPlayer p) {
        PartieTest pt = partie(p.server, p);
        MatchApi.rejoindre(p, pt);
        pt.equipes().get(1).eliminer(NYX);
        pt.equipes().get(0).score(2);
        pt.equipes().get(1).score(1);
        Hud.envoyer(pt, Component.translatable("matchs.banc.titre_bandeau"), 214, Component.literal("2 — 1"));
        resultat(p, "hud bandeau", true, "");
        return 1;
    }

    private static int hudAnnonce(ServerPlayer p, String style) {
        Annonces.Style st;
        try {
            st = Annonces.Style.valueOf(style.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            st = Annonces.Style.NORMALE;
        }
        Annonces.Annonce a = switch (st) {
            case VICTOIRE -> new Annonces.Annonce(Component.translatable("matchs.annonce.victoire"), Component.literal("[NOVA] Equipe 1"), 0xE8C36A,
                    Annonces.SON_VICTOIRE, 90, st);
            case DEFAITE -> new Annonces.Annonce(Component.translatable("matchs.annonce.defaite"), Component.literal("[RVN] avance en finale"), 0xE85D5D,
                    Annonces.SON_DEFAITE, 80, st);
            case FINALE -> new Annonces.Annonce(Component.literal("FINALE"), Component.literal("[NOVA] contre [RVN]"), 0xE8C36A,
                    Annonces.SON_FINALE, 120, st);
            case MANCHE -> new Annonces.Annonce(Component.literal("MANCHE 3"), Component.empty(), 0xC8322D, Annonces.SON_MANCHE, 90, st);
            default -> new Annonces.Annonce(Component.literal("ROUND 1"), Component.literal("Quart de finale"), 0x6FA8FF, Annonces.SON_ANNONCE, 70, st);
        };
        Annonces.grande(p, a);
        resultat(p, "hud annonce", true, st.name());
        return 1;
    }

    private static int hudFil(ServerPlayer p) {
        List<ServerPlayer> l = List.of(p);
        KillFeed.ajouter(l, new KillFeed.Ligne("Vortex", 0x6FA8FF, "Dziga", 0xE85D5D, "AK-47", true));
        KillFeed.ajouter(l, new KillFeed.Ligne("Kayra", 0xE85D5D, "Vortex", 0x6FA8FF, "Vector", false));
        KillFeed.ajouter(l, new KillFeed.Ligne("", 0xFFFFFF, "Nyx", 0xE85D5D, "", false));
        KillFeed.ajouter(l, new KillFeed.Ligne(p.getGameProfile().getName(), 0x6FA8FF, "Kayra", 0xE85D5D, "AWP", true));
        resultat(p, "hud fil", true, "");
        return 1;
    }

    private static int hudCompte(ServerPlayer p) {
        Annonces.compteARebours(List.of(p), 5, Component.translatable("matchs.compte.preparation"));
        resultat(p, "hud compte", true, "");
        return 1;
    }

    private static int hudCombat(ServerPlayer p) {
        CombatTag.marquer(p);
        CombatEvents.indicateur(p);
        // le tag court sa duree : l'indicateur reste visible (CombatTag.effacer le masquerait tout de suite)
        resultat(p, "hud combat", CombatTag.estTague(p), CombatTag.restantMs(p) + " ms");
        return 1;
    }

    // ------------------------------------------------------------------ systeme de combat (RETOURS du 01/10 §3)
    private static boolean verrouVehicule, ecouteVerrou;

    /** Banc : imite le train de heralisz_utils (descente annulee) tant que le verrou est pose. */
    private static void verrou(net.minecraftforge.event.entity.EntityMountEvent e) {
        if (verrouVehicule && e.isDismounting() && !e.getLevel().isClientSide()) e.setCanceled(true);
    }

    private static int combatMarquer(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        CombatTag.marquer(p);
        long cle = p.getPersistentData().getLong(CombatTag.CLE_JUSQUA);
        boolean ok = CombatTag.estTague(p) && cle == CombatTag.finMs(p.getUUID()) && cle > System.currentTimeMillis();
        resultat(p, "combat marquer", ok, CombatTag.restantMs(p) + " ms, cle " + CombatTag.CLE_JUSQUA + " = " + cle);
        return ok ? 1 : 0;
    }

    private static int combatVerifier(ServerPlayer p) {
        long cle = p.getPersistentData().getLong(CombatTag.CLE_JUSQUA);
        boolean tague = CombatTag.estTague(p);
        boolean coherent = tague ? cle == CombatTag.finMs(p.getUUID()) : !p.getPersistentData().contains(CombatTag.CLE_JUSQUA);
        resultat(p, "combat verifier", coherent, "tague " + tague + ", reste " + CombatTag.restantMs(p) + " ms, cle "
                + (p.getPersistentData().contains(CombatTag.CLE_JUSQUA) ? String.valueOf(cle) : "absente") + ", refus du Coffre " + Coffre.refus(p));
        return coherent ? 1 : 0;
    }

    /** Commande de teleportation tapee par le joueur tague : refusee ; puis acceptee une fois le tag efface. */
    private static int combatCommande(ServerPlayer p) {
        MinecraftServer s = p.server;
        if (!CombatTag.estTague(p)) CombatTag.marquer(p);
        double y0 = p.getY();
        s.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "tp @s ~ ~3 ~");
        boolean refusee = Math.abs(p.getY() - y0) < 0.01;
        CombatTag.effacer(p.getUUID());
        double y1 = p.getY();
        s.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "tp @s ~ ~3 ~");
        boolean acceptee = Math.abs(p.getY() - y1 - 3) < 0.01;
        String autre = CombatService.commandeBloquee("spawn") && CombatService.commandeBloquee("home") && CombatService.commandeBloquee("tpa")
                && CombatService.commandeBloquee("warp") ? "spawn/home/tpa/warp listees" : "LISTE INCOMPLETE";
        resultat(p, "combat commande", refusee && acceptee, "tague : /tp " + (refusee ? "refuse" : "EXECUTE") + " ; libre : /tp "
                + (acceptee ? "execute" : "REFUSE") + " ; " + autre + " ; racine(/essentials:spawn x) = " + CombatService.racine("/essentials:spawn x"));
        return refusee && acceptee ? 1 : 0;
    }

    /** Entree dans une partie factice (etat « en match » pousse au client) ou sortie. */
    private static int combatMatch(ServerPlayer p, boolean entrer) {
        if (entrer) {
            PartieTest pt = partie(p.server, p);
            CombatTag.effacer(p.getUUID());
            MatchApi.rejoindre(p, pt);
            // jamais de tag pendant un match : ce marquage doit etre ignore
            CombatTag.marquer(p);
            boolean ok = MatchApi.enMatch(p) && !CombatTag.estTague(p);
            resultat(p, "combat match entrer", ok, "en match " + MatchApi.enMatch(p) + ", tague en match " + CombatTag.estTague(p) + " (attendu : non)");
            return ok ? 1 : 0;
        }
        MatchApi.quitter(p);
        if (partie != null) {
            partie.fermer();
            partie = null;
        }
        resultat(p, "combat match sortir", !MatchApi.enMatch(p), "");
        return 1;
    }

    /** Malus d'ArkaniaZ : refuses en match, acceptes hors match (sauf combat_log, refuse partout). */
    private static int combatMalus(ServerPlayer p) {
        boolean enMatch = MatchApi.enMatch(p);
        List<String> l = new ArrayList<>();
        boolean ok = true;
        int presents = 0;
        for (ResourceLocation id : RestrictionsMatch.MALUS) {
            if (!ForgeRegistries.MOB_EFFECTS.containsKey(id)) continue;
            net.minecraft.world.effect.MobEffect m = ForgeRegistries.MOB_EFFECTS.getValue(id);
            if (m == null) continue;
            presents++;
            p.removeEffect(m);
            boolean pose = p.addEffect(new MobEffectInstance(m, 200, 0));
            boolean attendu = !enMatch && !id.getPath().equals("combat_log");
            if (pose != attendu) ok = false;
            l.add(id.getPath() + (pose ? "+" : "-"));
            p.removeEffect(m);
        }
        resultat(p, "combat malus", ok, (enMatch ? "en match" : "hors match") + " : " + (presents == 0 ? "geckolib222 absent (aucun effet a tester)" : l)
                + " (+ = pose, - = refuse)");
        return ok ? 1 : 0;
    }

    /**
     * Passager d'un vehicule dont la descente est interdite (comme le train) : pas de refus du Coffre, descente forcee,
     * position du joueur a pied.
     */
    private static int combatVehicule(ServerPlayer p) {
        if (!ecouteVerrou) {
            ecouteVerrou = true;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(BancMoteur::verrou);
        }
        ServerLevel l = p.serverLevel();
        net.minecraft.world.entity.vehicle.Boat bateau = new net.minecraft.world.entity.vehicle.Boat(l, p.getX() + 1, p.getY() + 0.5, p.getZ());
        l.addFreshEntity(bateau);
        boolean monte = p.startRiding(bateau, true);
        verrouVehicule = true;
        p.stopRiding();
        boolean bloque = p.isPassenger();
        Coffre.Refus refus = Coffre.refus(p);
        boolean descendu = fr.heralisz.matchs.moteur.coffre.Descente.forcer(p);
        verrouVehicule = false;
        bateau.discard();
        boolean ok = monte && bloque && refus != Coffre.Refus.VEHICULE && descendu && !p.isPassenger();
        resultat(p, "combat vehicule", ok, "monte " + monte + ", descente normale bloquee " + bloque + ", refus du Coffre " + refus
                + ", descente forcee " + descendu + String.format(java.util.Locale.ROOT, ", joueur en %.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()));
        return ok ? 1 : 0;
    }

    /** Deconnexion en combat (simulee : meme traitement que l'evenement de deconnexion) : le joueur meurt. */
    private static int combatDeconnexion(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        CombatTag.marquer(p);
        boolean tue = CombatService.surDeconnexion(p);
        boolean ok = tue && p.isDeadOrDying() && !p.getPersistentData().contains(CombatTag.CLE_JUSQUA) && !CombatTag.estTague(p);
        resultat(p, "combat deconnexion", ok, "tue " + tue + ", sante " + p.getHealth() + ", cle " + (p.getPersistentData().contains(CombatTag.CLE_JUSQUA) ? "PRESENTE" : "retiree")
                + ", source " + CombatService.source(p).type().msgId());
        return ok ? 1 : 0;
    }

    // ------------------------------------------------------------------ choix, groupe, spectateur, elimination
    /** Kits de demonstration (ids TaCZ reels : silhouettes dessinees si TaCZ est absent). */
    public static List<Kit> kitsDemo() {
        List<Kit> l = new ArrayList<>();
        l.add(demo("assaut", "Assaut", "Assault", Kit.CLASSE_ASSAUT, "tacz:ak47", "tacz:glock_17", 0.65f, 0.7f, 0.55f));
        l.add(demo("smg", "SMG", "SMG", Kit.CLASSE_SMG, "tacz:vector45", "tacz:glock_17", 0.35f, 0.95f, 0.8f));
        l.add(demo("pompe", "Pompe", "Shotgun", Kit.CLASSE_POMPE, "tacz:m870", "tacz:glock_17", 0.15f, 0.25f, 0.6f));
        l.add(demo("sniper", "Sniper", "Sniper", Kit.CLASSE_SNIPER, "tacz:ai_awp", "tacz:deagle", 1.0f, 0.1f, 0.35f));
        l.add(demo("tactique", "Tactique", "Tactical", Kit.CLASSE_TACTIQUE, "tacz:aug", "tacz:glock_17", 0.7f, 0.6f, 0.6f));
        return l;
    }

    private static Kit demo(String id, String fr, String en, String classe, String gun, String pistolet, float portee, float cadence, float mob) {
        return new Kit(id, fr, en, classe, List.of("clashwar"), new Kit.Arme(gun, Map.of(), "AUTO", 120), new Kit.Arme(pistolet, Map.of(), null, 48),
                "heralisz_melee:combat_knife", List.of(new Kit.Objet("heralisz_heal:carfak", 2), new Kit.Objet("heralisz_heal:blood_pack", 1)),
                List.of(new Kit.Objet("heralisz_grenade:smoke_grenade", 1)), "kit_trooper", List.of(), portee, cadence, mob);
    }

    private static int choix(ServerPlayer p) {
        PartieTest pt = partie(p.server, p);
        MatchApi.rejoindre(p, pt);
        List<Kit> kits = kitsDemo();
        ChoixClasse.ouvrir(pt, List.of(p), kits, Map.of(Kit.CLASSE_SNIPER, 1, Kit.CLASSE_POMPE, 1), 12,
                Component.translatable("matchs.banc.titre_choix"), res -> {
                    Map<String, String> r = new LinkedHashMap<>();
                    res.forEach((u, k) -> r.put(Noms.nom(p.server, u), k.id()));
                    resultat(p, "choix", !res.isEmpty(), "resultat " + r);
                });
        ChoixService.forcerFactice(pt, VORTEX, kits.get(3));
        resultat(p, "choix ouvert", true, "Vortex a pris le sniper");
        return 1;
    }

    private static int groupe(ServerPlayer p) {
        Groupe ancien = Groupes.de(p.getUUID());
        Groupes.definir(ancien, new Groupe(UUID.randomUUID(), p.getUUID(), List.of(p.getUUID(), VORTEX, DZIGA)));
        GroupesService.inviterFactice(p, KAYRA);
        GroupesService.envoyer(p, true);
        resultat(p, "groupe", Groupes.de(p.getUUID()) != null, "3 membres + invitation de Kayra");
        return 1;
    }

    private static int spectateur(ServerPlayer p) {
        Instance inst = null;
        for (Instance i : Arenes.instances()) if (i.carte().id().equals("clashwar_1v1")) inst = i;
        if (inst == null) inst = Arenes.reserver("clashwar_1v1");
        if (inst == null) {
            resultat(p, "spectateur", false, "carte clashwar_1v1 indisponible");
            return 0;
        }
        PartieTest pt = partie(p.server, p);
        pt.instance(inst);
        pt.phase(Partie.Phase.COMBAT);
        MatchApi.rejoindre(p, pt);
        final Instance fi = inst;
        inst.quandPrete(() -> {
            Pose sp = fi.point("spawn_a");
            if (sp != null) sp.teleporter(p, fi.niveau());
            Spectateurs.suivre(p, pt);
            resultat(p, "spectateur", Spectateurs.estSpectateur(p.getUUID()) && p.isSpectator(), "instance " + fi.id() + " "
                    + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()));
        });
        return 1;
    }

    private static int elimination(ServerPlayer p) {
        PartieTest pt = partie(p.server, p);
        if (!Coffre.aUnDepot(p.getUUID())) {
            CombatTag.effacer(p.getUUID());
            if (!Coffre.deposer(p, "banc-elimination")) {
                resultat(p, "elimination", false, "depot refuse : " + Coffre.refus(p));
                return 0;
            }
        }
        MatchApi.rejoindre(p, pt);
        pt.phase(Partie.Phase.COMBAT);
        int avant = pt.eliminations;
        p.hurt(p.damageSources().generic(), 1000f);
        p.hurt(p.damageSources().generic(), 1000f);
        boolean ok = p.isAlive() && p.getHealth() > 0 && pt.eliminations == avant + 1;
        resultat(p, "elimination", ok, "vivant " + p.isAlive() + ", sante " + p.getHealth() + ", eliminations " + (pt.eliminations - avant)
                + " (2 coups mortels, garde " + CombatEvents.GARDE_TICKS + " ticks), spectateur " + Spectateurs.estSpectateur(p.getUUID()));
        return ok ? 1 : 0;
    }

    private static int fin(ServerPlayer p) {
        ChoixService.vider();
        Spectateurs.arreter(p, true);
        if (Coffre.aUnDepot(p.getUUID())) Coffre.rendre(p);
        MatchApi.quitter(p);
        Hud.effacer(p);
        if (partie != null) {
            partie.fermer();
            partie = null;
        }
        p.playNotifySound(MoteurSons.FIN.get(), net.minecraft.sounds.SoundSource.MASTER, 0.6f, 1f);
        resultat(p, "fin", true, "");
        return 1;
    }
}
