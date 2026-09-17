package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.IItemHandlerSimpleInserter;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.upgrades.compacting.CompactingUpgradeWrapper", remap = false)
public abstract class CompactingUpgradeWrapperMixin {
    @Unique private final Map<IItemHandlerSimpleInserter, Set<Integer>> svhc$pending = new IdentityHashMap<>();
    @Unique private boolean svhc$compacting;

    @WrapMethod(method = "compactSlot")
    private void svhc$compact(IItemHandlerSimpleInserter inventory, int slot, Operation<Void> original) {
        if (!svhc$canCompact(inventory, slot)) {
            return;
        }
        svhc$pending.computeIfAbsent(inventory, h -> new HashSet<>()).add(slot);
        if (svhc$compacting) {
            return;
        }
        svhc$compacting = true;
        try {
            while (!svhc$pending.isEmpty()) {
                IItemHandlerSimpleInserter handler = svhc$pending.keySet().iterator().next();
                Set<Integer> slots = svhc$pending.remove(handler);
                for (int pendingSlot : slots) {
                    if (svhc$canCompact(handler, pendingSlot)) {
                        original.call(handler, pendingSlot);
                    }
                }
            }
        } finally {
            svhc$pending.clear();
            svhc$compacting = false;
        }
    }

    @Unique private boolean svhc$canCompact(IItemHandler inventory, int slot) {
        return !(inventory instanceof InventoryHandler handler)
                || !"compression".equals(handler.getInventoryPartitioner().getPartBySlot(slot).getName());
    }

    @WrapOperation(method = "tryCompacting", at = @At(value = "INVOKE",
            target = "Lnet/p3pp3rf1y/sophisticatedcore/util/InventoryHelper;extractFromInventory(Lnet/minecraft/world/item/Item;ILnet/minecraftforge/items/IItemHandler;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack svhc$extractIngredients(Item item, int count, IItemHandler inventory, boolean simulate, Operation<ItemStack> original) {
        int extracted = 0;
        for (int slot = 0; slot < inventory.getSlots() && extracted < count; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (svhc$canCompact(inventory, slot) && stack.getItem() == item && !stack.hasTag()) {
                extracted += inventory.extractItem(slot, count - extracted, simulate).getCount();
            }
        }
        return new ItemStack(item, extracted);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"))
    private Iterator<Integer> svhc$drainBeforeTick(Set<Integer> slots, Operation<Iterator<Integer>> original) {
        Set<Integer> pending = new HashSet<>(slots);
        slots.clear();
        return pending.iterator();
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/Set;clear()V"))
    private void svhc$keepNewPendingSlots(Set<Integer> slots, Operation<Void> original) {
    }

}
