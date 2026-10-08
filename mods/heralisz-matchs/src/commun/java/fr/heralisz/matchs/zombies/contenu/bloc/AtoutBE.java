package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.contenu.ZbSons;
import fr.heralisz.matchs.zombies.jeu.api.TypeAtout;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Machine d'atout facon distributeur de CoD (case basse ; la case haute est un {@link BlocAtout} sans bloc-entite) :
 * caisse de la couleur de l'atout, gros disque au logo en haut, enseigne au nom, vitrine retroeclairee de bouteilles,
 * distributeur, neons (modele et textures generes par {@code tools/matchs/zombies/gen_atouts.py}). Eteinte sans
 * courant ; a la mise sous tension les neons clignotent puis restent allumes, et la machine joue de temps en temps
 * son jingle original quand un joueur passe a proximite. {@link #servir()} marque un achat (eclat des neons,
 * bouteille qui tombe dans le distributeur, cote rendu).
 * <p>Le type est enregistre par son identifiant ({@link TypeAtout#id()}) : de nouveaux atouts ne decalent rien.
 */
public class AtoutBE extends ZbBE {
    private TypeAtout type = TypeAtout.BLINDAGE;
    private boolean alimente;
    private long prochainJingle;
    /** Temps de jeu du dernier achat (eclat de la machine), -100000 = jamais. */
    private long servi = -100000L;

    public AtoutBE(BlockPos pos, BlockState state) {
        super(ZbRegistres.ATOUT_BE.get(), pos, state);
    }

    /** Serveur : type de la machine. */
    public void type(TypeAtout t) {
        type = t == null ? TypeAtout.BLINDAGE : t;
        synchroniser();
    }

    /** Serveur : sous tension ou eteinte ({@code anime} = clignotement d'allumage et son). */
    public void alimenter(boolean oui, boolean anime) {
        if (oui == alimente && anime) return;
        alimente = oui;
        if (anime) marquer();
        else depuis = -100000L;
        if (level != null && !level.isClientSide) {
            for (BlockPos p : new BlockPos[]{worldPosition, worldPosition.above()}) {
                BlockState b = level.getBlockState(p);
                if (b.hasProperty(BlocAtout.ALIMENTE) && b.getValue(BlocAtout.ALIMENTE) != oui) level.setBlock(p, b.setValue(BlocAtout.ALIMENTE, oui), 2);
            }
            if (oui && anime) {
                level.playSound(null, worldPosition, ZbSons.MACHINE_ALLUMAGE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                prochainJingle = level.getGameTime() + 60 + level.random.nextInt(200);
            }
        }
        synchroniser();
    }

    /** Serveur : un joueur vient d'acheter l'atout (eclat, bouteille servie). */
    public void servir() {
        if (level == null) return;
        servi = level.getGameTime();
        if (!level.isClientSide) {
            level.playSound(null, worldPosition, ZbSons.MACHINE_SERVIR.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        }
        synchroniser();
    }

    /** Client : meme eclat qu'un achat, sans synchronisation (declenche par l'evenement « atout bu » du HUD). */
    public void servirClient() {
        if (level != null) servi = level.getGameTime();
    }

    public TypeAtout type() {
        return type;
    }

    public boolean alimente() {
        return alimente;
    }

    /** Ticks (avec fraction) depuis le dernier achat, cote rendu. */
    public float depuisServi(float partiel) {
        return level == null ? 1e6F : (float) (level.getGameTime() - servi) + partiel;
    }

    /** Son du jingle de cette machine (par identifiant d'atout ; un atout sans jingle propre prend celui d'allumage). */
    public SoundEvent jingle() {
        return switch (type.id()) {
            case "blindage" -> ZbSons.MACHINE_BLINDAGE.get();
            case "reanimation" -> ZbSons.MACHINE_REANIMATION.get();
            case "vigueur" -> ZbSons.MACHINE_VIGUEUR.get();
            case "double_impact" -> ZbSons.MACHINE_DOUBLE_IMPACT.get();
            case "rechargement" -> ZbSons.MACHINE_RECHARGEMENT.get();
            case "mule_kick" -> ZbSons.MACHINE_MULE_KICK.get();
            case "deadshot" -> ZbSons.MACHINE_DEADSHOT.get();
            default -> ZbSons.MACHINE_ALLUMAGE.get();
        };
    }

    /** Ticker serveur : jingle de temps en temps (45 a 90 s) si un joueur est a moins de 12 blocs. */
    public static void tickServeur(Level level, BlockPos pos, BlockState state, AtoutBE be) {
        if (!be.alimente || !(level instanceof ServerLevel sl)) return;
        long t = level.getGameTime();
        if (t < be.prochainJingle || t % 20 != 0) return;
        if (sl.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, false) == null) return;
        level.playSound(null, pos, be.jingle(), SoundSource.RECORDS, 0.8F, 1.0F);
        be.prochainJingle = t + 900 + level.random.nextInt(900);
    }

    @Override
    protected void ecrire(CompoundTag tag) {
        tag.putString("Type", type.id());
        tag.putBoolean("Alim", alimente);
        tag.putLong("Servi", servi);
    }

    @Override
    protected void lire(CompoundTag tag) {
        TypeAtout t = null;
        if (tag.contains("Type", Tag.TAG_STRING)) t = TypeAtout.parId(tag.getString("Type"));
        else if (tag.contains("Type")) t = TypeAtout.de(tag.getByte("Type"));
        type = t == null ? TypeAtout.BLINDAGE : t;
        alimente = tag.getBoolean("Alim");
        servi = tag.contains("Servi") ? tag.getLong("Servi") : -100000L;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 1.6, 0).inflate(0.6);
    }
}
