package dev.hoyin1600p.sophisticated_vh_compat.compat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BarrelDisplayAdjustments {
    // Block-space distances, applied after item sizing. Keep separate for visual fine tuning.
    private static final double BIG_DRIPLEAF_OUTSET = 6.0 / 16.0;
    private static final double SMALL_DRIPLEAF_OUTSET = 6.0 / 16.0;

    private BarrelDisplayAdjustments() {
    }

    public static double getOutwardOffset(ItemStack stack) {
        if (stack.is(Items.BIG_DRIPLEAF)) {
            return BIG_DRIPLEAF_OUTSET;
        }
        return stack.is(Items.SMALL_DRIPLEAF) ? SMALL_DRIPLEAF_OUTSET : 0.0;
    }
}
