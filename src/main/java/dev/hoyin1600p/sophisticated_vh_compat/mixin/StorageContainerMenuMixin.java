package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

@Mixin(value = StorageContainerMenuBase.class, remap = false)
public abstract class StorageContainerMenuMixin {
    @Shadow protected abstract boolean isUpgradeSettingsSlot(int index);

    @WrapOperation(method = "doClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;", remap = true), remap = true)
    private ItemStack svhc$detachHotbarSelection(Slot slot, Operation<ItemStack> original,
            @Local(argsOnly = true) ClickType clickType, @Share("hotbarSelection") LocalRef<ItemStack> selection) {
        ItemStack stack = original.call(slot);
        if (clickType == ClickType.SWAP) {
            stack = stack.copy();
            selection.set(stack);
        }
        return stack;
    }

    @WrapOperation(method = "doClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;setChanged()V", remap = true), remap = true)
    private void svhc$writeSplitSelection(Slot slot, Operation<Void> original,
            @Local(argsOnly = true) ClickType clickType, @Share("hotbarSelection") LocalRef<ItemStack> selection) {
        if (clickType == ClickType.SWAP && selection.get() != null) {
            // The native hotbar branch split our detached stack; persist its remainder.
            slot.set(selection.get());
        } else {
            original.call(slot);
        }
    }

    @WrapMethod(method = "triggerSlotListeners")
    private void svhc$notifySharedUpgrade(int index, ItemStack stack, Supplier<ItemStack> copy,
            NonNullList<ItemStack> lastSlots, int offset, Operation<Void> original) {
        boolean changed = !ItemStack.matches(lastSlots.get(index), stack);
        original.call(index, stack, copy, lastSlots, offset);
        if (changed && isUpgradeSettingsSlot(index + offset)) {
            ((StorageContainerMenuBase<?>) (Object) this).getSlot(index + offset).setChanged();
        }
    }

    @Inject(method = {"sendSlotUpdates", "setSlotStackToUpdate"}, at = @At("HEAD"), cancellable = true)
    private void svhc$useContainerSynchronization(CallbackInfo ci) {
        ci.cancel();
    }

}
