package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.p3pp3rf1y.sophisticatedcore.settings.memory.MemorySettingsCategory;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandlerSlotTracker", remap = false)
public abstract class InventorySlotTrackerMixin {
    @Shadow private Map<Item, Set<Integer>> filterItemSlots;

    @WrapOperation(method = "insertIntoEmptySlots", at = @At(value = "INVOKE",
            target = "Lnet/p3pp3rf1y/sophisticatedcore/settings/memory/MemorySettingsCategory;isSlotSelected(I)Z"))
    private boolean svhc$skipReservedSlots(MemorySettingsCategory memory, int slot, Operation<Boolean> original) {
        return original.call(memory, slot) || filterItemSlots.values().stream().anyMatch(slots -> slots.contains(slot));
    }
}
