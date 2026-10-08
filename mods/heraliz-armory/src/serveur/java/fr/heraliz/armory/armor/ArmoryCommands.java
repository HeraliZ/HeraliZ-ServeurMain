package fr.heraliz.armory.armor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.entity.Mannequin;
import fr.heraliz.armory.menu.MannequinMenuServeur;
import fr.heraliz.armory.registry.ModEntities;
import fr.heraliz.armory.registry.ModItems;
import fr.heraliz.armory.suit.SuitRules;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * /armory equip <ensemble> [joueurs] : equipe l'ensemble complet (slots vanilla + Curios), remplace ce qui est porte.
 * /armory wear <ensemble> [joueurs]  : enfile les pieces de l'ensemble par-dessus la tenue actuelle (ex. un gilet seul).
 * /armory give <ensemble> [joueurs]  : donne toutes les pieces.
 * /armory strip [joueurs]            : retire les pieces du mod portees.
 * /armory mannequin <ensemble> [here] : pose un mannequin habille de l'ensemble (devant soi, ou a ses pieds avec "here").
 * /armory dress <ensemble>           : habille le mannequin le plus proche (par-dessus ce qu'il porte).
 * /armory mannequinmenu              : ouvre le menu du mannequin le plus proche.
 * /armory nvg                        : baisse / releve ses lunettes de vision nocturne (comme la touche N).
 * Reserve aux operateurs (niveau 2). Reponses en message systeme (le serveur coupe sendCommandFeedback).
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID)
public final class ArmoryCommands {
    private ArmoryCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("armory").requires(s -> s.hasPermission(2))
                .then(Commands.literal("equip").then(Commands.argument("set", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(sets(), b))
                        .executes(c -> equip(c, List.of(c.getSource().getPlayerOrException()), EQUIP))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> equip(c, EntityArgument.getPlayers(c, "targets"), EQUIP)))))
                .then(Commands.literal("wear").then(Commands.argument("set", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(sets(), b))
                        .executes(c -> equip(c, List.of(c.getSource().getPlayerOrException()), WEAR))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> equip(c, EntityArgument.getPlayers(c, "targets"), WEAR)))))
                .then(Commands.literal("give").then(Commands.argument("set", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(sets(), b))
                        .executes(c -> equip(c, List.of(c.getSource().getPlayerOrException()), GIVE))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> equip(c, EntityArgument.getPlayers(c, "targets"), GIVE)))))
                .then(Commands.literal("mannequin").then(Commands.argument("set", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(sets(), b))
                        .executes(c -> mannequin(c, false))
                        .then(Commands.literal("here").executes(c -> mannequin(c, true)))))
                .then(Commands.literal("dress").then(Commands.argument("set", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(sets(), b))
                        .executes(ArmoryCommands::dress)))
                .then(Commands.literal("nvg").executes(c -> {
                    fr.heraliz.armory.nvg.NightVisionServeur.toggle(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("mannequinmenu").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Mannequin m = nearest(p);
                    if (m == null) {
                        reply(c.getSource(), Component.translatable("commands.heraliz_armory.no_mannequin").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    MannequinMenuServeur.open(p, m);
                    return 1;
                }))
                .then(Commands.literal("strip").executes(c -> stripCommand(c.getSource(), List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> stripCommand(c.getSource(), EntityArgument.getPlayers(c, "targets"))))));
    }

    /** Reponse a celui qui tape la commande : message systeme (visible meme avec sendCommandFeedback=false). */
    private static void reply(CommandSourceStack src, Component msg) {
        if (src.getEntity() instanceof ServerPlayer p) p.sendSystemMessage(msg);
        else src.sendSystemMessage(msg);
    }

    private static Component setName(String set) {
        return Component.translatable("tooltip.heraliz_armory.set." + set);
    }

    private static int stripCommand(CommandSourceStack src, Collection<ServerPlayer> players) {
        int n = strip(players);
        reply(src, Component.translatable("commands.heraliz_armory.strip", n).withStyle(ChatFormatting.GRAY));
        return n;
    }

    public static Set<String> sets() {
        Set<String> s = new LinkedHashSet<>();
        for (PieceDef d : ArmorCatalog.PIECES) s.add(d.set());
        return s;
    }

    private static final int EQUIP = 0, WEAR = 1, GIVE = 2;

    private static int equip(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, int mode) throws CommandSyntaxException {
        boolean give = mode == GIVE;
        String set = StringArgumentType.getString(c, "set");
        List<PieceDef> pieces = ArmorCatalog.PIECES.stream().filter(p -> p.set().equals(set)).toList();
        if (pieces.isEmpty()) {
            reply(c.getSource(), Component.translatable("commands.heraliz_armory.unknown", set).withStyle(ChatFormatting.RED));
            return 0;
        }
        int refused = 0;
        for (ServerPlayer player : players) {
            if (mode == EQUIP) strip(List.of(player));
            for (PieceDef def : pieces) {
                ItemStack stack = new ItemStack(ModItems.get(def));
                if (give) {
                    if (!player.getInventory().add(stack)) player.drop(stack, false);
                } else if (!equipPiece(player, def, stack)) {
                    // combinaison portee (0.9.0) : la piece va dans l'inventaire
                    player.getInventory().placeItemBackInInventory(stack);
                    refused++;
                }
            }
        }
        reply(c.getSource(), Component.translatable(give ? "commands.heraliz_armory.give" : mode == WEAR ? "commands.heraliz_armory.wear"
                : "commands.heraliz_armory.equip", setName(set), pieces.size(), players.size()).withStyle(ChatFormatting.GRAY));
        if (refused > 0) reply(c.getSource(), Component.translatable("commands.heraliz_armory.suit_worn", refused).withStyle(ChatFormatting.RED));
        return pieces.size();
    }

    /**
     * Enfile la piece dans son slot. 0.9.0 : refuse (faux) si la regle des combinaisons l'interdit (une combinaison est
     * portee, ou c'est une combinaison et autre chose est porte) : voir suit.SuitRules.
     */
    public static boolean equipPiece(LivingEntity entity, PieceDef def, ItemStack stack) {
        if (def.curioSlot() != null) {
            if (!SuitRules.mayEquipCurio(entity, def.curioSlot(), stack)) return false;
            CuriosApi.getCuriosInventory(entity).ifPresent(inv -> inv.setEquippedCurio(def.curioSlot(), 0, stack));
        } else {
            if (!SuitRules.mayEquipArmor(entity, def.equipmentSlot(), stack)) return false;
            entity.setItemSlot(def.equipmentSlot(), stack);
        }
        return true;
    }

    private static List<PieceDef> piecesOf(CommandContext<CommandSourceStack> c) {
        String set = StringArgumentType.getString(c, "set");
        List<PieceDef> pieces = ArmorCatalog.PIECES.stream().filter(p -> p.set().equals(set)).toList();
        if (pieces.isEmpty()) reply(c.getSource(), Component.translatable("commands.heraliz_armory.unknown", set).withStyle(ChatFormatting.RED));
        return pieces;
    }

    private static int mannequin(CommandContext<CommandSourceStack> c, boolean here) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<PieceDef> pieces = piecesOf(c);
        if (pieces.isEmpty()) return 0;
        Mannequin m = ModEntities.MANNEQUIN.get().create(p.serverLevel());
        if (m == null) return 0;
        Vec3 at = p.position();
        float yaw = p.getYRot();
        if (!here) {
            Vec3 look = Vec3.directionFromRotation(0, yaw);
            at = at.add(look.scale(2.0));
            yaw += 180.0F;
        }
        m.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        m.setYBodyRot(yaw);
        m.setYHeadRot(yaw);
        p.serverLevel().addFreshEntity(m);
        for (PieceDef def : pieces) equipPiece(m, def, new ItemStack(ModItems.get(def)));
        reply(c.getSource(), Component.translatable("commands.heraliz_armory.mannequin", setName(StringArgumentType.getString(c, "set")))
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int dress(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Mannequin m = nearest(p);
        List<PieceDef> pieces = piecesOf(c);
        if (pieces.isEmpty()) return 0;
        if (m == null) {
            reply(c.getSource(), Component.translatable("commands.heraliz_armory.no_mannequin").withStyle(ChatFormatting.RED));
            return 0;
        }
        int refused = 0;
        for (PieceDef def : pieces) {
            if (!equipPiece(m, def, new ItemStack(ModItems.get(def)))) refused++;
        }
        reply(c.getSource(), Component.translatable("commands.heraliz_armory.dress", setName(StringArgumentType.getString(c, "set")))
                .withStyle(ChatFormatting.GRAY));
        if (refused > 0) reply(c.getSource(), Component.translatable("commands.heraliz_armory.suit_worn", refused).withStyle(ChatFormatting.RED));
        return pieces.size();
    }

    private static Mannequin nearest(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Mannequin.class, p.getBoundingBox().inflate(8.0)).stream()
                .min(java.util.Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
    }

    /** Retire les objets de l'Armurerie portes (pieces, combinaisons et, depuis 0.9.0, objets repris de HeralisZ Armor). */
    private static int strip(Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,
                    net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                if (player.getItemBySlot(slot).getItem() instanceof fr.heraliz.armory.item.ProtectiveItem) player.setItemSlot(slot, ItemStack.EMPTY);
            }
            CuriosApi.getCuriosInventory(player).ifPresent(inv -> {
                for (String id : new String[]{"mask", "belt", "hands", "knees", "body", "bracelet"}) {
                    inv.getStacksHandler(id).ifPresent(h -> {
                        for (int i = 0; i < h.getSlots(); i++) {
                            if (h.getStacks().getStackInSlot(i).getItem() instanceof fr.heraliz.armory.item.ProtectiveItem) {
                                h.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                            }
                        }
                    });
                }
            });
        }
        return players.size();
    }
}
