package dev.hoyin1600p.sophisticated_vh_compat.compat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BarrelDisplayAdjustments {
    private BarrelDisplayAdjustments() {}

    public static double getOutwardOffset(ItemStack stack, float displayScale) {
        if (stack.is(Items.BIG_DRIPLEAF)) {
            return 6.0 / 16.0 * displayScale;
        }
        return stack.is(Items.SMALL_DRIPLEAF) ? 4.0 / 16.0 * displayScale : 0;
    }
}
