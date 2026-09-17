package dev.hoyin1600p.sophisticated_vh_compat.compat;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BarrelDisplayAdjustmentsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void approvedPlantOffsetsScaleWithTheDisplay() {
        assertEquals(6.0 / 16, BarrelDisplayAdjustments.getOutwardOffset(new ItemStack(Items.BIG_DRIPLEAF), 1));
        assertEquals(4.0 / 16, BarrelDisplayAdjustments.getOutwardOffset(new ItemStack(Items.SMALL_DRIPLEAF), 1));
        assertEquals(3.0 / 16, BarrelDisplayAdjustments.getOutwardOffset(new ItemStack(Items.BIG_DRIPLEAF), 0.5f));
        assertEquals(2.0 / 16, BarrelDisplayAdjustments.getOutwardOffset(new ItemStack(Items.SMALL_DRIPLEAF), 0.5f));
        assertEquals(0, BarrelDisplayAdjustments.getOutwardOffset(new ItemStack(Items.STONE), 0.5f));
    }
}
