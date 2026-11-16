package fr.heraliz.recolte.objets;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import fr.heraliz.recolte.pont.Crochet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Outil de récolte : la hache (arbres), la pioche (gisements), la hache-pioche de survie (les deux, 40 % de coups en
 * moins ; légendaire, seulement dans le loot T6-T7 et les coffres des événements). Pour le serveur, un outil n'est qu'un
 * objet et son usure (400, 400 et 1 000 coups) : c'est lui qui décide des coups (cadence, portée, matière). Au combat, un
 * outil frappe faiblement : 2 cœurs, un coup toutes les 1,2 s. Il ne casse aucun bloc (ce n'est pas un outil de
 * Minecraft). En main, le client dessine le modèle en cubes tenu à deux mains avec son coup ({@link #RENDU_CLIENT}).
 */
public class OutilRecolte extends Item {
    /** Ce que l'outil récolte. */
    public enum Genre {
        HACHE(true, false), PIOCHE(false, true), HACHE_PIOCHE(true, true);

        public final boolean arbres, gisements;

        Genre(boolean arbres, boolean gisements) {
            this.arbres = arbres;
            this.gisements = gisements;
        }
    }

    /** Demande de rendu en main : l'outil et à qui donner ses extensions client. */
    public record DemandeClient(OutilRecolte outil, Consumer<IClientItemExtensions> sortie) {
    }

    /** Rendu en main (modèle en cubes, deux mains, coup animé) : branché par {@code fr.heraliz.recolte.client.PartieClient}. */
    public static final Crochet<DemandeClient> RENDU_CLIENT = new Crochet<>();

    /** Un coup toutes les 1,2 s (vitesse d'attaque = 1 / 1,2 par seconde, celle d'un joueur à mains nues étant 4). */
    public static final double VITESSE = 1.0 / 1.2;

    private final String id;
    private final Genre genre;
    private final Multimap<Attribute, AttributeModifier> attributs;

    public OutilRecolte(String id, Genre genre, int usure) {
        super(new Item.Properties().durability(usure));
        this.id = id;
        this.genre = genre;
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        // 2 cœurs (4 points) : 1 du joueur + 3 ; vitesse : 4 du joueur + (VITESSE - 4)
        b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Outil de récolte", 3.0, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Outil de récolte", VITESSE - 4.0, AttributeModifier.Operation.ADDITION));
        this.attributs = b.build();
    }

    /** Identifiant dans le mod ({@code hache}, {@code pioche}, {@code hache_pioche}). */
    public String id() {
        return id;
    }

    public Genre genre() {
        return genre;
    }

    /** Abat les arbres à abattre (hache, hache-pioche). */
    public boolean coupeLesArbres() {
        return genre.arbres;
    }

    /** Casse les gisements (pioche, hache-pioche). */
    public boolean casseLesGisements() {
        return genre.gisements;
    }

    /** La hache-pioche de survie : 40 % de coups en moins (les nombres de coups sont des réglages du serveur). */
    public boolean legendaire() {
        return genre == Genre.HACHE_PIOCHE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? attributs : super.getDefaultAttributeModifiers(slot);
    }

    /** Jamais un outil de Minecraft : aucun bloc ne se casse avec (on ne récolte que les arbres et gisements du mod). */
    @Override
    public boolean canAttackBlock(BlockState etat, Level level, BlockPos pos, Player joueur) {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack livre) {
        return false;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack reparation) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
        lignes.add(Component.translatable("heraliz_recolte.info." + id).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> sortie) {
        RENDU_CLIENT.accept(new DemandeClient(this, sortie));
    }
}
