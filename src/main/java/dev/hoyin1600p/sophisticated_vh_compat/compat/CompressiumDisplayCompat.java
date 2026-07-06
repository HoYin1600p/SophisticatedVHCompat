package dev.hoyin1600p.sophisticated_vh_compat.compat;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class CompressiumDisplayCompat {
    public static final String NAMESPACE = "compressium";
    public static final float FIXED_DISPLAY_SCALE = 0.5F;
    public static final float BARREL_FACE_OUTSET = 1.0F / 64.0F;

    private static final double FULL_CUBE_MAX_Z_FROM_CENTER = 0.5D;

    private CompressiumDisplayCompat() {
    }

    public static boolean isCompressiumItem(ItemStack item) {
        ResourceLocation itemKey = Registry.ITEM.getKey(item.getItem());
        return itemKey != null && NAMESPACE.equals(itemKey.getNamespace());
    }

    public static double fullCubeDisplayItemOffset(float additionalScale) {
        return ((FIXED_DISPLAY_SCALE * (2.0D / 15.95D)) - FULL_CUBE_MAX_Z_FROM_CENTER) * additionalScale;
    }

    public static double outwardDynamicDisplayItemOffset(float additionalScale) {
        return fullCubeDisplayItemOffset(additionalScale) - BARREL_FACE_OUTSET;
    }

    public static ResourceLocation layerTexture(ItemStack item) {
        return new ResourceLocation(NAMESPACE, "block/layer_" + tier(item));
    }

    private static int tier(ItemStack item) {
        ResourceLocation itemKey = Registry.ITEM.getKey(item.getItem());
        if (itemKey == null) {
            return 1;
        }

        String path = itemKey.getPath();
        int tierSeparator = path.lastIndexOf('_');
        if (tierSeparator < 0 || tierSeparator + 1 >= path.length()) {
            return 1;
        }

        try {
            return Math.max(1, Math.min(9, Integer.parseInt(path.substring(tierSeparator + 1))));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
