package com.aetherspellbooks.client.armor;

import com.aetherspellbooks.registry.ASArmorMaterials;
import net.minecraft.client.Minecraft;

/** Client-only helper: how many pieces of a set the local player is wearing (shown in the tooltip). */
public final class ArmorTooltipHelper {
    private ArmorTooltipHelper() {
    }

    public static int piecesWorn(ASArmorMaterials set) {
        var player = Minecraft.getInstance().player;
        return player == null ? 0 : set.countWornBy(player);
    }
}
