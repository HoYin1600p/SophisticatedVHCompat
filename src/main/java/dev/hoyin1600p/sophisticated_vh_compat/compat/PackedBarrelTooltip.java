package dev.hoyin1600p.sophisticated_vh_compat.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;
import net.p3pp3rf1y.sophisticatedstorage.block.IStorageBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.ItemContentsStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.BarrelBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.LimitedBarrelBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.item.WoodStorageBlockItem;

/** Builds the barrel's calculated slot view without initializing its shared item wrapper. */
public final class PackedBarrelTooltip {
    private static final Pattern BARREL_TIER = Pattern.compile("^(?:limited_)?(?:(copper|iron|gold|diamond|netherite)_)?barrel(?:_[1-4])?$");
    private PackedBarrelTooltip() {
    }

    public static Optional<Component> getTierLine(ItemStack storageItem) {
        if (!WoodStorageBlockItem.isPacked(storageItem)
                || !(storageItem.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof BarrelBlock barrel)) {
            return Optional.empty();
        }
        return getTier(barrel.getRegistryName()).map(tier -> new TranslatableComponent(
                "tooltip.sophisticated_vh_compat.barrel_tier",
                new TranslatableComponent("tooltip.sophisticated_vh_compat.tier." + tier).withStyle(ChatFormatting.WHITE)
        ).withStyle(ChatFormatting.GRAY));
    }

    static Optional<String> getTier(ResourceLocation blockId) {
        if (blockId == null || !blockId.getNamespace().equals("sophisticatedstorage")) {
            return Optional.empty();
        }
        var match = BARREL_TIER.matcher(blockId.getPath());
        return match.matches() ? Optional.ofNullable(match.group(1)).or(() -> Optional.of("wood")) : Optional.empty();
    }

    public static Optional<IStorageWrapper> getPreview(ItemStack storageItem) {
        if (!WoodStorageBlockItem.isPacked(storageItem)
                || !(storageItem.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof BarrelBlock barrel)) {
            return Optional.empty();
        }

        return NBTHelper.getUniqueId(storageItem, "uuid").flatMap(uuid -> {
            CompoundTag saved = ItemContentsStorage.get().getOrCreateStorageContents(uuid);
            if (!saved.contains(StorageBlockEntity.STORAGE_WRAPPER_TAG, Tag.TAG_COMPOUND)) {
                return Optional.empty();
            }
            CompoundTag wrapperTag = saved.getCompound(StorageBlockEntity.STORAGE_WRAPPER_TAG);
            // The first hover can precede the server's contents response. Let the normal refresh retry.
            CompoundTag contents = wrapperTag.getCompound(StorageWrapper.CONTENTS_TAG);
            if (!contents.contains(InventoryHandler.INVENTORY_TAG, Tag.TAG_COMPOUND)
                    && !contents.contains(UpgradeHandler.UPGRADE_INVENTORY_TAG, Tag.TAG_COMPOUND)
                    && !wrapperTag.contains("numberOfInventorySlots", Tag.TAG_INT)
                    && !wrapperTag.contains("numberOfUpgradeSlots", Tag.TAG_INT)) {
                return Optional.empty();
            }
            return Optional.of(createPreview(barrel, wrapperTag));
        });
    }

    static List<ItemStack> getContents(IStorageBlock barrel, CompoundTag savedWrapper) {
        return getContents(createPreview(barrel, savedWrapper));
    }

    static StorageWrapper createPreview(IStorageBlock barrel, CompoundTag savedWrapper) {
        PreviewStorageWrapper preview = new PreviewStorageWrapper(barrel);
        // Compression initialization may compact slots and update settings; every tag must be detached.
        preview.load(savedWrapper.copy());
        preview.onInit();
        return preview;
    }

    public static boolean usesBarrelSlotOrder(IStorageWrapper wrapper) {
        return wrapper instanceof PreviewStorageWrapper preview && preview.barrel instanceof LimitedBarrelBlock;
    }

    public static List<ItemStack> getContents(IStorageWrapper preview) {
        InventoryHandler inventory = preview.getInventoryHandler();
        List<ItemStack> contents = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                // Keep slot order and separate denominations instead of merging or sorting by count.
                contents.add(stack.copy());
            }
        }
        return contents;
    }

    private static final class PreviewStorageWrapper extends StorageWrapper {
        private final IStorageBlock barrel;

        private PreviewStorageWrapper(IStorageBlock barrel) {
            super(() -> () -> {}, () -> {}, () -> {}, barrel.getNumberOfInventorySlots());
            this.barrel = barrel;
        }

        @Override
        public int getDefaultNumberOfInventorySlots() {
            return barrel.getNumberOfInventorySlots();
        }

        @Override
        public int getDefaultNumberOfUpgradeSlots() {
            return barrel.getNumberOfUpgradeSlots();
        }

        @Override
        public int getBaseStackSizeMultiplier() {
            return barrel.getBaseStackSizeMultiplier();
        }

        @Override
        public Optional<UUID> getContentsUuid() {
            return Optional.ofNullable(contentsUuid);
        }

        @Override
        protected boolean isAllowedInStorage(ItemStack stack) {
            return false;
        }

        @Override
        protected void onUpgradeRefresh() {
            // This detached wrapper has no block, world, or external save callbacks.
        }

        @Override
        public String getStorageType() {
            return "wood_storage";
        }

        @Override
        public Component getDisplayName() {
            return TextComponent.EMPTY;
        }
    }
}
