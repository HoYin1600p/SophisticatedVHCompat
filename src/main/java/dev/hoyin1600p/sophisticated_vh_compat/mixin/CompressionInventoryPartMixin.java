package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryPartitioner;
import dev.hoyin1600p.sophisticated_vh_compat.compat.CompressionContentsListener;
import dev.hoyin1600p.sophisticated_vh_compat.compat.StorageStackUpdates;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import static net.p3pp3rf1y.sophisticatedcore.util.MathHelper.intMaxCappedAddition;
import static net.p3pp3rf1y.sophisticatedcore.util.MathHelper.intMaxCappedMultiply;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.upgrades.compression.CompressionInventoryPart", remap = false)
public abstract class CompressionInventoryPartMixin implements CompressionContentsListener {

    @Shadow @Final private InventoryHandler parent;
    @Shadow @Final private InventoryPartitioner.SlotRange slotRange;
    @Shadow private Map<Integer, CompressionSlotDefinitionAccessor> slotDefinitions;
    @Shadow @Final private Map<Integer, ItemStack> calculatedStacks;
    @Shadow private Map<Integer, CompressionSlotDefinitionAccessor> getSlotDefinitions(Item item, int slot, Map<Integer, ItemStack> stacks) { throw new AssertionError(); }
    @Shadow private void setSlotDefinitions(Map<Integer, CompressionSlotDefinitionAccessor> definitions, boolean initial) { throw new AssertionError(); }
    @Shadow private void compactInternalSlots() { throw new AssertionError(); }
    @Shadow private void updateCalculatedStacks() { throw new AssertionError(); }
    @Shadow private void extractFromCalculated(int slot, int count) { throw new AssertionError(); }
    @Shadow private void extractFromInternal(int slot, int count) { throw new AssertionError(); }
    @Shadow private void removeDefinitionsIfEmpty(int slot) { throw new AssertionError(); }
    @Shadow private int getPrevSlotMultiplier(int slot) { throw new AssertionError(); }
    @Shadow private void updateInternalStacksWithCounts(Map<Integer, Integer> counts) { throw new AssertionError(); }
    @Shadow private void addToCalculatedStack(int slot, int count) { throw new AssertionError(); }

    @Inject(method = "updateSlotLimits", at = @At("HEAD"), cancellable = true)
    private void svhc$limits(Map<Integer, CompressionSlotDefinitionAccessor> definitions, CallbackInfo ci) {
        svhc$updateSlotLimits(definitions);
        ci.cancel();
    }

    @Inject(method = "extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void svhc$extract(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(svhc$extractItem(slot, amount, simulate, stack -> Integer.MAX_VALUE));
    }

    @Inject(method = "extractItem(IIZLjava/util/function/ToIntFunction;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void svhc$extractWithLimit(int slot, int amount, boolean simulate, ToIntFunction<ItemStack> limit, CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(svhc$extractItem(slot, amount, simulate, limit));
    }

    @Inject(method = "insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void svhc$insert(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(svhc$insertItem(slot, stack, simulate));
    }

    @WrapMethod(method = "setStackInSlot")
    private void svhc$replaceFromStoredContents(int slot, ItemStack stack,
            BiConsumer<Integer, ItemStack> setter, Operation<Void> original) {
        if (!stack.isEmpty() && svhc$canNotBeInserted(slot, stack)) {
            return;
        }
        StorageStackUpdates.replace(stack, () -> {
            if (!slotDefinitions.isEmpty()) {
                updateCalculatedStacks();
            }
        }, replacement -> original.call(slot, replacement, setter));
    }

    @Unique
    private void svhc$updateSlotLimits(Map<Integer, CompressionSlotDefinitionAccessor> definitions) {
		int totalLimit = 0;
		for (int slot = slotRange.firstSlot(); slot < slotRange.firstSlot() + slotRange.numberOfSlots(); slot++) {
			if (definitions.containsKey(slot) && definitions.get(slot).sophisticatedVhCompat$isAccessible()) {
				CompressionSlotDefinitionAccessor definition = definitions.get(slot);
				totalLimit = intMaxCappedAddition(parent.getBaseStackLimit(new ItemStack(definition.sophisticatedVhCompat$getItem())), intMaxCappedMultiply(definition.sophisticatedVhCompat$getPrevSlotMultiplier(), totalLimit));

				definitions.get(slot).sophisticatedVhCompat$setSlotLimit(totalLimit);
			}
		}
	}

    @Unique
    private ItemStack svhc$extractItem(int slot, int amount, boolean simulate, ToIntFunction<ItemStack> getLimit) {
		if (!slotDefinitions.containsKey(slot) || !slotDefinitions.get(slot).sophisticatedVhCompat$isAccessible()) {
			return ItemStack.EMPTY;
		}
		int toExtract = Math.min(calculatedStacks.get(slot).getCount(), amount);

		if (toExtract > 0) {
			CompressionSlotDefinitionAccessor slotDefinition = slotDefinitions.get(slot);
			ItemStack slotStack = parent.getSlotStack(slot);
			toExtract = Math.min(toExtract, getLimit.applyAsInt(calculatedStacks.get(slot)));
			ItemStack result = slotDefinition.sophisticatedVhCompat$isCompressible() ? new ItemStack(slotDefinition.sophisticatedVhCompat$getItem(), toExtract) : ItemHandlerHelper.copyStackWithSize(slotStack, toExtract);

			if (!simulate) {
				if (slotDefinition.sophisticatedVhCompat$isCompressible()) {
					extractFromCalculated(slot, toExtract);
					extractFromInternal(slot, toExtract);
				} else {
					slotStack.shrink(toExtract);
					parent.setSlotStack(slot, slotStack);
					calculatedStacks.put(slot, slotStack.copy());
				}
				removeDefinitionsIfEmpty(slot);
				svhc$refreshCalculatedSlots();
			}

			return result;
		}

		return ItemStack.EMPTY;
	}

    @Unique
    private ItemStack svhc$insertItem(int slot, ItemStack stack, boolean simulate) {
		if (svhc$canNotBeInserted(slot, stack)) {
			return stack;
		}

		int limit = 0;

		Map<Integer, CompressionSlotDefinitionAccessor> definitions = slotDefinitions;

		if (definitions.isEmpty()) {
			definitions = getSlotDefinitions(stack.getItem(), slot, Map.of());
		}

		if (!svhc$hasValidSlotDefinitions(definitions, slot)) {
			return stack;
		}
		limit = definitions.get(slot).sophisticatedVhCompat$getSlotLimit();

		int currentCalculatedCount = calculatedStacks.containsKey(slot) ? calculatedStacks.get(slot).getCount() : 0;
		int inserted = Math.min(Math.max(parent.getBaseStackLimit(stack) - parent.getSlotStack(slot).getCount(), limit - currentCalculatedCount), stack.getCount());

		if (inserted == 0) {
			return stack;
		}

		ItemStack result = ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - inserted);

		if (simulate) {
			return result;
		}

		if (!slotDefinitions.containsKey(slot)) {
			setSlotDefinitions(definitions, false);
			compactInternalSlots();
			updateCalculatedStacks();
		}

		if (slotDefinitions.get(slot).sophisticatedVhCompat$isCompressible()) {
			if (!svhc$insertIntoInternalAndCalculated(slot, inserted)) {
				return stack;
			}
		} else if (inserted > 0) {
			calculatedStacks.compute(slot, (s, st) -> {
				if (st ==null || st.isEmpty()) {
					ItemStack copy = stack.copy();
					copy.setCount(inserted);
					return copy;
				}
				st.grow(inserted);
				return st;
			});
			ItemStack slotStack = parent.getSlotStack(slot);
			if (slotStack.isEmpty()) {
				ItemStack copy = stack.copy();
				copy.setCount(inserted);
				parent.setSlotStack(slot, copy);
			} else {
				slotStack.grow(inserted);
				parent.setSlotStack(slot, slotStack);
			}
		}

		svhc$refreshCalculatedSlots();
		return result;
	}

    @Unique
    private boolean svhc$canNotBeInserted(int slot, ItemStack stack) {
		if (stack.isEmpty() || slot < slotRange.firstSlot() || slot >= slotRange.firstSlot() + slotRange.numberOfSlots()) {
			return true;
		}

		if (!slotDefinitions.containsKey(slot)) {
			return false;
		}

		CompressionSlotDefinitionAccessor slotDefinition = slotDefinitions.get(slot);
		return !slotDefinition.sophisticatedVhCompat$isAccessible() || slotDefinition.sophisticatedVhCompat$getItem() != stack.getItem();
	}

    @Unique
    private boolean svhc$hasValidSlotDefinitions(Map<Integer, CompressionSlotDefinitionAccessor> definitions, int slot) {
		CompressionSlotDefinitionAccessor definition = definitions.get(slot);
		return definition != null && definition.sophisticatedVhCompat$isAccessible() && definitions.values().stream().noneMatch(d -> d.sophisticatedVhCompat$isAccessible() && d.sophisticatedVhCompat$getPrevSlotMultiplier() <= 0);
	}

    @Unique
    private void svhc$refreshCalculatedSlots() {
		for (int slot = slotRange.firstSlot(); slot < slotRange.firstSlot() + slotRange.numberOfSlots(); slot++) {
			parent.getSlotTracker().removeAndSetSlotIndexes(parent, slot, calculatedStacks.getOrDefault(slot, ItemStack.EMPTY));
		}
		for (int slot = slotRange.firstSlot(); slot < slotRange.firstSlot() + slotRange.numberOfSlots(); slot++) {
			parent.triggerOnChangeListeners(slot);
		}
	}

    @Unique
    private boolean svhc$insertIntoInternalAndCalculated(int slotToStartFrom, long amountToInsert) {
		Map<Integer, Integer> toUpdate = new LinkedHashMap<>();
		Map<Integer, Integer> calculatedAdditions = new LinkedHashMap<>();
		int totalMultiplier = 1;
		int slot = slotToStartFrom;

		long amountToSet = amountToInsert + parent.getSlotStack(slot).getCount();

		while (amountToSet / ((long) totalMultiplier * getPrevSlotMultiplier(slot)) > 0 && slotDefinitions.containsKey(slot - 1) && slotDefinitions.get(slot - 1).sophisticatedVhCompat$isAccessible()) {
			totalMultiplier *= getPrevSlotMultiplier(slot);
			slot--;
			amountToSet += (long) parent.getSlotStack(slot).getCount() * totalMultiplier;
		}

		long calculatedAddition = 0;
		while (slot <= slotToStartFrom) {
			calculatedAddition *= getPrevSlotMultiplier(slot);
			ItemStack slotStack = parent.getSlotStack(slot);
			int toSet = (int) Math.min(amountToSet / totalMultiplier, parent.getBaseStackLimit(slotStack));
			calculatedAddition += (toSet - slotStack.getCount());
			calculatedAdditions.put(slot, (int) Math.min(calculatedAddition, Integer.MAX_VALUE));
			if (toSet > 0) {
				toUpdate.put(slot, toSet);
				amountToSet -= (long) toSet * totalMultiplier;
			} else {
				toUpdate.put(slot, 0);
			}

			if (amountToSet != 0 && slot < slotToStartFrom) {
				totalMultiplier /= getPrevSlotMultiplier(slot + 1);
			}
			slot++;
		}

		if (amountToSet != 0) {
			return false;
		}

		//finish calculation of calculated addition to the follow up slots even though they are not getting their internal stack changed
		while (slot < slotRange.firstSlot() + slotRange.numberOfSlots()) {
			if (!slotDefinitions.containsKey(slot) || !slotDefinitions.get(slot).sophisticatedVhCompat$isAccessible()) {
				break;
			}

			calculatedAddition *= getPrevSlotMultiplier(slot);
			calculatedAdditions.put(slot, (int) Math.min(calculatedAddition, Integer.MAX_VALUE));

			slot++;
		}

		updateInternalStacksWithCounts(toUpdate);

		calculatedAdditions.forEach(this::addToCalculatedStack);
		return true;
	}

    @Override
    @Unique
    public void svhc$commitStackMutation(int slot) {
        if (calculatedStacks.containsKey(slot)) {
            parent.setStackInSlot(slot, calculatedStacks.get(slot));
        }
    }
}
