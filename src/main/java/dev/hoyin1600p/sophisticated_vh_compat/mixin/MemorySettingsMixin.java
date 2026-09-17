package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Set;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.settings.memory.MemorySettingsCategory", remap = false)
public abstract class MemorySettingsMixin {
    @Shadow public abstract Set<Integer> getSlotIndexes();
    @Shadow private InventoryHandler getInventoryHandler() { throw new AssertionError(); }

    @WrapMethod(method = "unselectAllSlots")
    private void svhc$clearMemory(Operation<Void> original) {
        Set<Integer> slots = Set.copyOf(getSlotIndexes());
        original.call();
        slots.forEach(getInventoryHandler()::onSlotFilterChanged);
    }
}
