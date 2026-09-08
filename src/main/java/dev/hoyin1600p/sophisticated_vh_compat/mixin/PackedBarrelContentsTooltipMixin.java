package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import dev.hoyin1600p.sophisticated_vh_compat.compat.PackedBarrelTooltip;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.render.ClientStorageContentsTooltip", remap = false)
public abstract class PackedBarrelContentsTooltipMixin {
    @Shadow
    private static List<ItemStack> sortedContents;

    @Shadow
    @Final
    private static List<Component> tooltipLines;

    @ModifyVariable(
            method = "refreshContents(Lnet/p3pp3rf1y/sophisticatedcore/api/IStorageWrapper;)V",
            at = @At(value = "INVOKE", target = "Lnet/p3pp3rf1y/sophisticatedcore/api/IStorageWrapper;onContentsNbtUpdated()V", shift = At.Shift.AFTER),
            argsOnly = true,
            ordinal = 0,
            require = 1
    )
    private IStorageWrapper sophisticated_vh_compat$useCompleteBarrelPreview(IStorageWrapper wrapper) {
        // Substitute only inside a refresh and after the original wrapper is invalidated, so the
        // initialized preview feeds contents, upgrade icons, multiplier, fluids and energy together.
        if ((Object) this instanceof StorageContentsTooltipAccessor tooltip) {
            return PackedBarrelTooltip.getPreview(tooltip.sophisticated_vh_compat$getStorageItem()).orElse(wrapper);
        }
        return wrapper;
    }

    @Inject(
            method = "refreshContents(Lnet/p3pp3rf1y/sophisticatedcore/api/IStorageWrapper;)V",
            at = @At(value = "INVOKE", target = "Lnet/p3pp3rf1y/sophisticatedcore/api/IStorageWrapper;getUpgradeHandler()Lnet/p3pp3rf1y/sophisticatedcore/upgrades/UpgradeHandler;"),
            require = 1
    )
    private void sophisticated_vh_compat$readCalculatedBarrelSlots(IStorageWrapper wrapper, CallbackInfo ci) {
        // Limited barrel denominations retain slot order; regular barrels keep native aggregation.
        if (PackedBarrelTooltip.usesBarrelSlotOrder(wrapper)) {
            sortedContents = PackedBarrelTooltip.getContents(wrapper);
        }
        if ((Object) this instanceof StorageContentsTooltipAccessor tooltip) {
            PackedBarrelTooltip.getTierLine(tooltip.sophisticated_vh_compat$getStorageItem()).ifPresent(tooltipLines::add);
        }
    }
}
