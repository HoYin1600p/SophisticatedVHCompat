package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Collection;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(value = net.p3pp3rf1y.sophisticatedcore.compat.jei.CraftingContainerRecipeTransferHandlerServer.class, remap = false)
public abstract class JeiTransferMixin {
    @WrapOperation(method = "putIntoInventory", at = @At(value = "INVOKE",
            target = "Lnet/p3pp3rf1y/sophisticatedcore/compat/jei/CraftingContainerRecipeTransferHandlerServer;addStack(Lnet/p3pp3rf1y/sophisticatedcore/common/gui/StorageContainerMenuBase;Ljava/util/Collection;Lnet/minecraft/world/item/ItemStack;)I"))
    private static int svhc$consumeAcceptedItems(StorageContainerMenuBase<?> menu, Collection<Integer> slots,
            ItemStack stack, Operation<Integer> original) {
        stack.shrink(original.call(menu, slots, stack));
        // The existing fallback now receives only items that storage did not accept.
        return 0;
    }

    @WrapOperation(method = "clearAndPutItemsIntoGrid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;remove(I)Lnet/minecraft/world/item/ItemStack;", remap = true))
    private static ItemStack svhc$removeActualCount(Slot slot, int amount, Operation<ItemStack> original) {
        return original.call(slot, slot.getItem().getCount());
    }

}
