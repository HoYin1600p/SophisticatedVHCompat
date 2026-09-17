package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryPartitioner;
import dev.hoyin1600p.sophisticated_vh_compat.compat.CompressionContentsListener;
import net.minecraft.world.item.ItemStack;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler", remap = false)
public abstract class InventoryHandlerMixin {
    @Shadow private InventoryPartitioner inventoryPartitioner;
    @Unique private int svhc$slotWriteDepth;

    @WrapMethod(method = "setSlotStack")
    private void svhc$writeStoredSlot(int slot, ItemStack stack, Operation<Void> original) {
        svhc$slotWriteDepth++;
        try {
            original.call(slot, stack);
        } finally {
            svhc$slotWriteDepth--;
        }
    }

    @Inject(method = "onContentsChanged", at = @At("HEAD"))
    private void svhc$reconcile(int slot, CallbackInfo ci) {
        if (svhc$slotWriteDepth == 0 && inventoryPartitioner != null && inventoryPartitioner.getPartBySlot(slot) instanceof CompressionContentsListener listener) {
            listener.svhc$commitStackMutation(slot);
        }
    }

    @Inject(method = "onFilterItemsChanged", at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private void svhc$refreshFilters(CallbackInfo ci) {
        InventoryHandler handler = (InventoryHandler) (Object) this;
        handler.getSlotTracker().refreshSlotIndexesFrom(handler);
    }

}
