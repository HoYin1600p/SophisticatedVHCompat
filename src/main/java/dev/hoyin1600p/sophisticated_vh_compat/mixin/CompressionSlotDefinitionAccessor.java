package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(
        targets = "net.p3pp3rf1y.sophisticatedstorage.upgrades.compression.CompressionInventoryPart$SlotDefinition",
        remap = false
)
public interface CompressionSlotDefinitionAccessor {
    @Accessor("item")
    Item sophisticatedVhCompat$getItem();

    @Accessor("prevSlotMultiplier")
    int sophisticatedVhCompat$getPrevSlotMultiplier();

    @Accessor("isAccessible")
    boolean sophisticatedVhCompat$isAccessible();

    @Invoker("setSlotLimit")
    void sophisticatedVhCompat$setSlotLimit(int slotLimit);
}
