package dev.hoyin1600p.sophisticated_vh_compat.compat;

import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

public final class StorageStackUpdates {
    private StorageStackUpdates() {
    }

    public static void replace(ItemStack requested, Runnable refresh, Consumer<ItemStack> setter) {
        ItemStack replacement = requested.copy();
        // Rebuild the virtual inventory from stored contents before the native setter compares counts.
        refresh.run();
        setter.accept(replacement);
    }
}
