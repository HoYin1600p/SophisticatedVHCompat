package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.client.render.ClientStorageContentsTooltip", remap = false)
public interface StorageContentsTooltipAccessor {
    @Accessor("storageItem")
    ItemStack sophisticated_vh_compat$getStorageItem();
}
