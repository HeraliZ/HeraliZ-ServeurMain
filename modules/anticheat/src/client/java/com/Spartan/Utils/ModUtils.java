package com.Spartan.Utils;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraftforge.fml.ModList;

public class ModUtils {
    public static List<String> getActiveMods() {
        return ModList.get().getMods().stream()
            .map(modInfo -> modInfo.getModId())
            .collect(Collectors.toList());
    }
}
