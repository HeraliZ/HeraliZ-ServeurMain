package fr.heraliz.armory.menu;

import fr.heraliz.armory.entity.Mannequin;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ouverture du menu du mannequin, cote serveur (reprise telle quelle de MannequinMenu.open) : accroupi + clic droit sur
 * le mannequin, commande /armory mannequinmenu. Le menu lui-meme (cases, regles des cases) reste commun.
 */
public final class MannequinMenuServeur {
    private MannequinMenuServeur() {}

    /** Cote serveur. */
    public static void open(ServerPlayer player, Mannequin m) {
        Map<String, Integer> layout = layout(m);
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) -> new MannequinMenu(id, inv, m, layout),
                m.hasCustomName() ? m.getCustomName() : Component.translatable("container.heraliz_armory.mannequin")), buf -> {
            buf.writeVarInt(m.getId());
            buf.writeVarInt(layout.size());
            layout.forEach((k, v) -> {
                buf.writeUtf(k);
                buf.writeVarInt(v);
            });
        });
    }

    private static Map<String, Integer> layout(Mannequin m) {
        Map<String, Integer> out = new LinkedHashMap<>();
        CuriosApi.getCuriosInventory(m).ifPresent(inv -> inv.getCurios().forEach((k, h) -> out.put(k, h.getSlots())));
        return out;
    }
}
