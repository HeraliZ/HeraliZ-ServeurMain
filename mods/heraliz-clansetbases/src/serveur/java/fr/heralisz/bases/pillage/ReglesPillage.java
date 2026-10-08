package fr.heralisz.bases.pillage;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.heralisz.bases.api.BasesApi;
import fr.heralisz.bases.api.PillageHooks;
import fr.heralisz.bases.blindage.Blindage;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.model.PillableVue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Regles du module pillage branchees sur le coeur ({@link PillageHooks}) : droits d'un pillard dans la base qu'il pille
 * (BASES_SPEC §4) — il ouvre conteneurs et portes et utilise les blocs, casse a la main les blocs SANS niveau de
 * protection, ne pose aucun bloc (C4 et grenades sont des entites, pas des blocs) ; liste PILLER recalculee avec les
 * regles du jour ; {@code /base piller <tag>} ; attaquants / pillards de l'onglet BASE ; sortie d'un pillard.
 */
public final class ReglesPillage implements PillageHooks {
    @Override
    public boolean estPillard(ServerPlayer p, Base base) {
        Pillages.Session s = Pillages.session(p.getUUID());
        return s != null && s.base.equals(base.clan);
    }

    @Override
    public boolean peutCasser(ServerPlayer p, Base base, BlockPos pos, BlockState etat) {
        if (!estPillard(p, base)) return false;
        if (Blindage.niveauEn(p.level(), pos) > 0) {
            BasesApi.refus(p, "refus.heralisz_bases.blindage");
            return false;
        }
        return true;
    }

    @Override
    public boolean peutPoser(ServerPlayer p, Base base, BlockPos pos, BlockState etat) {
        return false;
    }

    @Override
    public boolean peutUtiliser(ServerPlayer p, Base base, BlockPos pos, BlockState etat, Usage usage) {
        return estPillard(p, base);
    }

    @Override
    public void piller(ServerPlayer p, @Nullable String tag) {
        if (tag != null) Pillages.piller(p, tag);
    }

    @Override
    public List<PillableVue> pillables(ServerPlayer p, List<PillableVue> parDefaut) {
        List<PillableVue> l = new ArrayList<>(parDefaut.size());
        for (PillableVue v : parDefaut) {
            Base b = BasesApi.base(v.clan());
            if (b == null) continue;
            String r = Pillages.refus(p, b);
            l.add(new PillableVue(v.clan(), v.tag(), v.nom(), v.banniere(), v.membres(), v.enLigne(), r == null, r == null ? "" : r,
                    Pillages.dejaPillee(p, b.clan), Pillages.pillards(b.clan)));
        }
        return l;
    }

    @Override
    public List<String> attaquants(Base base) {
        return Pillages.attaquants(base.clan);
    }

    @Override
    public int pillards(Base base) {
        return Pillages.pillards(base.clan);
    }

    @Override
    public long finPillageMs(ServerPlayer p) {
        return 0;
    }

    @Override
    public void sortie(ServerPlayer p, @Nullable Base base) {
        Pillages.sortie(p);
    }

    @Override
    public void commandes(LiteralArgumentBuilder<CommandSourceStack> base, LiteralArgumentBuilder<CommandSourceStack> admin) {
        CommandesPillage.ajouter(base, admin);
    }
}
