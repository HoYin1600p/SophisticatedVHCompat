package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.IInventoryPartHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper", remap = false)
public abstract class InventoryHelperMixin {
    @Inject(method = "getAndRemove", at = @At("HEAD"), cancellable = true)
    private static void svhc$removeWholeStack(IItemHandler inventory, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (inventory instanceof InventoryHandler handler && slot >= 0 && slot < handler.getSlots()
                && handler.getInventoryPartitioner().getPartBySlot(slot) instanceof IInventoryPartHandler.Default) {
            ItemStack stack = handler.getSlotStack(slot);
            handler.setSlotStack(slot, ItemStack.EMPTY);
            cir.setReturnValue(stack);
        }
    }

}
