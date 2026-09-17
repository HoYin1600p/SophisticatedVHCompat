package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.compat.jei.CraftingContainerRecipeTransferHandlerServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CraftingContainerRecipeTransferHandlerServer.class, remap = false)
public abstract class JeiRemainderPersistenceMixin {
    @WrapOperation(method = "addStack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;grow(I)V", remap = true))
    private static void svhc$saveGrownRemainder(ItemStack stack, int count, Operation<Void> original, @Local Slot slot) {
        original.call(stack, count);
        slot.setChanged();
    }

    @WrapOperation(method = "addStack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;setCount(I)V", ordinal = 0, remap = true))
    private static void svhc$saveFilledRemainder(ItemStack stack, int count, Operation<Void> original, @Local Slot slot) {
        original.call(stack, count);
        slot.setChanged();
    }
}
