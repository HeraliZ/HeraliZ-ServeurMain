package com.Spartan.Utils;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;

public class ResourcePackUtil {
    public static List<String> getActiveResourcePacks() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getResourcePackRepository().getSelectedIds().stream().collect(Collectors.toList());
    }
}
