package fr.heraliz.usure;

import fr.heraliz.usure.regles.Profil;
import fr.heraliz.usure.regles.Regles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Partie SERVEUR de {@link Reparation} (docs/METHODE_CLIENT_SERVEUR.md §5) : la reparation d'une arme avec un kit, code
 * d'origine deplace tel quel (regles du serveur, NBT de l'arme, kit consomme, son et message). Appelee par
 * {@link KitReparationServeur} ; le calcul ({@link Reparation#calculer}) et l'affichage ({@link Reparation#pct}) restent
 * communs.
 */
public final class ReparationServeur {
    private ReparationServeur() {
    }

    /** Repare {@code arme} avec le kit de niveau {@code niveau} (0..2) ; rend le resultat et previent le joueur. */
    public static Reparation.Resultat appliquer(ServerPlayer p, ItemStack kit, ItemStack arme, int niveau) {
        if (Usure.gunId(arme) == null && KitReparation.estArmure(arme)) return armure(p, kit, arme, niveau);
        String id = Usure.gunId(arme);
        if (id == null) return message(p, Reparation.Resultat.PAS_UNE_ARME, null);
        Regles r = ReglesServeur.get();
        Profil pr = r.profil(id);
        if (!pr.suivie) return message(p, Reparation.Resultat.NE_S_USE_PAS, arme);
        Usure.migrer(arme, pr);
        int etat = Usure.etat(arme, pr), max = Usure.max(arme);
        Reparation.Calcul c = Reparation.calculer(etat, max, niveau, r);
        if (c.refus() != null) return message(p, c.refus(), arme);
        int cible = c.etat();
        Usure.ecrireMax(arme, c.max());
        Usure.ecrireEtat(arme, cible);
        Usure.ajouterReparation(arme);
        consommer(p, kit, r.kitUtilisations[niveau]);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.25F);
        p.displayClientMessage(Component.translatable("heraliz_usure.reparation.ok", arme.getHoverName(),
                Reparation.pct(etat), Reparation.pct(cible), Reparation.pct(Usure.max(arme))).withStyle(ChatFormatting.GREEN), true);
        return Reparation.Resultat.REPAREE;
    }

    /**
     * Piece de HeraliZ Armurerie (armure, masque a gaz, combinaison...) : le kit rend son gain en % de la durabilite
     * maximale, jusqu'a 100 % (demande du proprietaire, 24/10 : fortune +15 %, kit +35 %, armurier +60 %). Une piece usee
     * (barre vide, plus de protection contre les zones) se repare aussi. Une utilisation du kit.
     */
    static Reparation.Resultat armure(ServerPlayer p, ItemStack kit, ItemStack piece, int niveau) {
        Regles r = ReglesServeur.get();
        int max = piece.getMaxDamage();
        int avant = piece.getDamageValue();
        if (max <= 0 || avant <= 0) {
            p.displayClientMessage(Component.translatableWithFallback("heraliz_usure.reparation.armure_neuve",
                    "Cette pièce est déjà en parfait état.").withStyle(ChatFormatting.GOLD), true);
            p.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 1.2F);
            return Reparation.Resultat.DEJA_AU_PLAFOND;
        }
        int gain = Math.max(1, Math.round(max * r.kitGain[niveau] / 100f));
        int apres = Math.max(0, avant - gain);
        piece.setDamageValue(apres);
        consommer(p, kit, r.kitUtilisations[niveau]);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.7F, 1.1F);
        p.displayClientMessage(Component.translatableWithFallback("heraliz_usure.reparation.armure_ok", "%s réparée : %s → %s",
                piece.getHoverName(), Reparation.pct(Math.round(1000f * (max - avant) / max)),
                Reparation.pct(Math.round(1000f * (max - apres) / max))).withStyle(ChatFormatting.GREEN), true);
        return Reparation.Resultat.REPAREE;
    }

    /**
     * Une utilisation de moins ; le kit disparait apres la derniere. Pile de plusieurs kits (26/10, piles de 5) : un kit
     * est separe de la pile, garde son compte d'utilisations et va dans l'inventaire (au sol s'il n'y a pas de place),
     * pour que les autres kits de la pile restent neufs.
     */
    static void consommer(ServerPlayer p, ItemStack kit, int utilisations) {
        int faites = (kit.hasTag() ? kit.getTag().getInt(KitReparation.UTILISEES) : 0) + 1;
        if (faites >= utilisations) {
            kit.shrink(1);
        } else if (kit.getCount() > 1) {
            ItemStack un = kit.split(1);
            un.getOrCreateTag().putInt(KitReparation.UTILISEES, faites);
            p.getInventory().placeItemBackInInventory(un);
        } else {
            kit.getOrCreateTag().putInt(KitReparation.UTILISEES, faites);
        }
    }

    /**
     * Aperçu pour l'atelier, sans rien changer : {code, genre, etat avant, etat apres, max avant, max apres} (milliemes ;
     * codes et genres de {@link MenuReparation}).
     */
    static int[] apercu(ItemStack cible, int niveau) {
        if (cible.isEmpty()) return new int[]{MenuReparation.VIDE, MenuReparation.RIEN, 0, 0, 0, 0};
        Regles r = ReglesServeur.get();
        String id = Usure.gunId(cible);
        if (id == null) {
            if (!KitReparation.estArmure(cible)) return new int[]{MenuReparation.PAS_REPARABLE, MenuReparation.RIEN, 0, 0, 0, 0};
            int max = cible.getMaxDamage(), avant = cible.getDamageValue();
            if (max <= 0) return new int[]{MenuReparation.PAS_REPARABLE, MenuReparation.ARMURE, 0, 0, 0, 0};
            int etat = Math.round(1000f * (max - avant) / max);
            if (avant <= 0) return new int[]{MenuReparation.PLAFOND, MenuReparation.ARMURE, etat, etat, 1000, 1000};
            int apres = Math.max(0, avant - Math.max(1, Math.round(max * r.kitGain[niveau] / 100f)));
            return new int[]{MenuReparation.OK, MenuReparation.ARMURE, etat, Math.round(1000f * (max - apres) / max), 1000, 1000};
        }
        Profil pr = r.profil(id);
        if (!pr.suivie) return new int[]{MenuReparation.NE_S_USE_PAS, MenuReparation.ARME, 0, 0, 0, 0};
        int etat = Usure.etat(cible, pr), max = Usure.max(cible);
        Reparation.Calcul c = Reparation.calculer(etat, max, niveau, r);
        int code = c.refus() == null ? MenuReparation.OK : c.refus() == Reparation.Resultat.RUINEE_REFUSEE ? MenuReparation.RUINEE
                : MenuReparation.PLAFOND;
        return new int[]{code, MenuReparation.ARME, etat, c.etat(), max, c.max()};
    }

    private static Reparation.Resultat message(ServerPlayer p, Reparation.Resultat res, ItemStack arme) {
        String cle = switch (res) {
            case NE_S_USE_PAS -> "heraliz_usure.reparation.ne_s_use_pas";
            case DEJA_AU_PLAFOND -> "heraliz_usure.reparation.plafond";
            case RUINEE_REFUSEE -> "heraliz_usure.reparation.ruinee";
            default -> "heraliz_usure.reparation.pas_une_arme";
        };
        p.displayClientMessage(Component.translatable(cle).withStyle(ChatFormatting.GOLD), true);
        p.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 1.2F);
        return res;
    }
}
