package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashSet;
import java.util.Set;
import net.p3pp3rf1y.sophisticatedcore.inventory.IItemHandlerSimpleInserter;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

/**
 * Serializes reentrant compacting callbacks and leaves Compression-controlled
 * virtual slots to the Compression upgrade.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.upgrades.compacting.CompactingUpgradeWrapper", remap = false)
public abstract class CompactingUpgradeWrapperMixin {
    @Unique
    private final Set<Integer> sophisticatedVhCompat$queuedSlots = new HashSet<>();

    @Unique
    private boolean sophisticatedVhCompat$compacting;

    @WrapMethod(
            method = "compactSlot(Lnet/p3pp3rf1y/sophisticatedcore/inventory/IItemHandlerSimpleInserter;I)V",
            require = 1,
            remap = false
    )
    private void sophisticatedVhCompat$compactSlotAndQueued(
            IItemHandlerSimpleInserter inventoryHandler,
            int slot,
            Operation<Void> original
    ) {
        if (sophisticatedVhCompat$isCompressionSlot(inventoryHandler, slot)) {
            return;
        }

        if (sophisticatedVhCompat$compacting) {
            sophisticatedVhCompat$queuedSlots.add(slot);
            return;
        }

        sophisticatedVhCompat$compacting = true;
        try {
            original.call(inventoryHandler, slot);
            while (!sophisticatedVhCompat$queuedSlots.isEmpty()) {
                Set<Integer> queuedSlots = new HashSet<>(sophisticatedVhCompat$queuedSlots);
                sophisticatedVhCompat$queuedSlots.clear();
                for (int queuedSlot : queuedSlots) {
                    if (!sophisticatedVhCompat$isCompressionSlot(inventoryHandler, queuedSlot)) {
                        original.call(inventoryHandler, queuedSlot);
                    }
                }
            }
        } finally {
            sophisticatedVhCompat$queuedSlots.clear();
            sophisticatedVhCompat$compacting = false;
        }
    }

    @Unique
    private boolean sophisticatedVhCompat$isCompressionSlot(
            IItemHandlerSimpleInserter inventoryHandler,
            int slot
    ) {
        return inventoryHandler instanceof InventoryHandler handler
                && "compression".equals(handler.getInventoryPartitioner().getPartBySlot(slot).getName());
    }
}
