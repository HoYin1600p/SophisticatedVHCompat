package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryPartitioner;
import net.p3pp3rf1y.sophisticatedstorage.upgrades.compression.CompressionInventoryPart;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.upgrades.compression.CompressionInventoryPart", remap = false)
public abstract class CompressionInventoryPartMixin {
    @Unique
    private static final Logger SOPHISTICATED_VH_COMPAT_LOGGER = LogUtils.getLogger();

    @Unique
    private static final String INVALID_STATE = "sophisticated_vh_compat:invalid-compression-state";

    @Shadow
    @Final
    private InventoryHandler parent;

    @Shadow
    @Final
    private InventoryPartitioner.SlotRange slotRange;

    @Shadow
    private Map<Integer, ?> slotDefinitions;

    @Shadow
    @Final
    private Map<Integer, ItemStack> calculatedStacks;

    @Unique
    private long sophisticatedVhCompat$lastInvalidStateWarning;

    @Unique
    private int sophisticatedVhCompat$invalidDefinitionSlot = -1;

    @Inject(
            method = "updateSlotLimits(Ljava/util/Map;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false
    )
    private void sophisticatedVhCompat$useMixedMultiplierSlotLimits(
            Map<Integer, ?> definitions,
            CallbackInfo ci
    ) {
        int totalLimit = 0;
        int endSlot = slotRange.firstSlot() + slotRange.numberOfSlots();
        for (int slot = slotRange.firstSlot(); slot < endSlot; slot++) {
            Object rawDefinition = definitions.get(slot);
            if (rawDefinition instanceof CompressionSlotDefinitionAccessor definition
                    && definition.sophisticatedVhCompat$isAccessible()) {
                int baseLimit = parent.getBaseStackLimit(
                        new ItemStack(definition.sophisticatedVhCompat$getItem())
                );
                totalLimit = sophisticatedVhCompat$cappedAdd(
                        baseLimit,
                        sophisticatedVhCompat$cappedMultiply(
                                definition.sophisticatedVhCompat$getPrevSlotMultiplier(),
                                totalLimit
                        )
                );
                definition.sophisticatedVhCompat$setSlotLimit(totalLimit);
            }
        }
        ci.cancel();
    }

    @WrapOperation(
            method = "insertIntoInternalAndCalculated(IJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/p3pp3rf1y/sophisticatedstorage/upgrades/compression/CompressionInventoryPart;getPrevSlotMultiplier(I)I"
            ),
            require = 1,
            remap = false
    )
    private int sophisticatedVhCompat$validateMultiplier(
            CompressionInventoryPart instance,
            int slot,
            Operation<Integer> original
    ) {
        Object rawDefinition = slotDefinitions.get(slot);
        if (!(rawDefinition instanceof CompressionSlotDefinitionAccessor definition)
                || !definition.sophisticatedVhCompat$isAccessible()
                || definition.sophisticatedVhCompat$getPrevSlotMultiplier() <= 0) {
            sophisticatedVhCompat$invalidDefinitionSlot = slot;
            throw new IllegalStateException(INVALID_STATE);
        }
        return original.call(instance, slot);
    }

    @WrapMethod(
            method = "insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",
            require = 1,
            remap = false
    )
    private ItemStack sophisticatedVhCompat$rejectInvalidInsertion(
            int slot,
            ItemStack stack,
            boolean simulate,
            Operation<ItemStack> original
    ) {
        try {
            ItemStack result = original.call(slot, stack, simulate);
            if (!simulate && result != stack) {
                sophisticatedVhCompat$refreshCalculatedSlots();
            }
            return result;
        } catch (IllegalStateException exception) {
            if (!INVALID_STATE.equals(exception.getMessage())) {
                throw exception;
            }
            sophisticatedVhCompat$warnInvalidState(slot, sophisticatedVhCompat$invalidDefinitionSlot);
            sophisticatedVhCompat$invalidDefinitionSlot = -1;
            return stack;
        }
    }

    @Inject(
            method = "extractItem(IIZLjava/util/function/ToIntFunction;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/p3pp3rf1y/sophisticatedstorage/upgrades/compression/CompressionInventoryPart;extractFromInternal(II)V",
                    shift = At.Shift.AFTER
            ),
            require = 1,
            remap = false
    )
    private void sophisticatedVhCompat$refreshCalculatedSlotTracking(
            int slot,
            int amount,
            boolean simulate,
            ToIntFunction<ItemStack> getLimit,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        sophisticatedVhCompat$refreshCalculatedSlots();
    }

    @Unique
    private void sophisticatedVhCompat$refreshCalculatedSlots() {
        calculatedStacks.forEach((calculatedSlot, stack) -> {
            parent.getSlotTracker().removeAndSetSlotIndexes(parent, calculatedSlot, stack);
            parent.triggerOnChangeListeners(calculatedSlot);
        });
    }

    @Unique
    private int sophisticatedVhCompat$cappedMultiply(int left, int right) {
        return (int) Math.min(Integer.MAX_VALUE, (long) left * right);
    }

    @Unique
    private int sophisticatedVhCompat$cappedAdd(int left, int right) {
        return (int) Math.min(Integer.MAX_VALUE, (long) left + right);
    }

    @Unique
    private void sophisticatedVhCompat$warnInvalidState(int requestedSlot, int invalidSlot) {
        long now = System.currentTimeMillis();
        if (now - sophisticatedVhCompat$lastInvalidStateWarning < 60_000L) {
            return;
        }
        sophisticatedVhCompat$lastInvalidStateWarning = now;
        SOPHISTICATED_VH_COMPAT_LOGGER.error(
                "Rejected a Compression insertion without consuming items: requested slot {}, "
                        + "invalid definition slot {}, range {}+{}, definitions {}",
                requestedSlot,
                invalidSlot,
                slotRange.firstSlot(),
                slotRange.numberOfSlots(),
                slotDefinitions.keySet()
        );
    }
}
