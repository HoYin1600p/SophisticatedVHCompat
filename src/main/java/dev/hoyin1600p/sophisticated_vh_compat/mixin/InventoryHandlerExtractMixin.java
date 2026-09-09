package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets stack-upgraded slots satisfy a large extraction in one call instead of
 * applying the vanilla stack-size cap.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler", remap = false)
public abstract class InventoryHandlerExtractMixin {
    @Shadow
    public abstract int getSlotLimit(int slot);

    @ModifyVariable(
            method = "extractItemInternal",
            at = @At(value = "STORE", ordinal = 0),
            require = 0,
            remap = false
    )
    private int sophisticatedVhCompat$capExtractionAtSlotLimit(
            int toExtract,
            @Local(argsOnly = true, ordinal = 0) int slot,
            @Local(argsOnly = true, ordinal = 1) int amount
    ) {
        return Math.min(amount, Math.max(toExtract, getSlotLimit(slot)));
    }
}
