package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.math.Transformation;
import com.mojang.math.Vector3f;
import dev.hoyin1600p.sophisticated_vh_compat.compat.CompressiumDisplayCompat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.QuadTransformer;
import net.minecraftforge.common.model.TransformationHelper;
import net.p3pp3rf1y.sophisticatedstorage.client.render.DisplayItemRenderer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.client.render.BarrelBakedModelBase", priority = 900)
public abstract class SophisticatedStorageBarrelBakedModelBaseMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation COMPRESSIUM_BARREL_FACE = new ResourceLocation("sophisticated_vh_compat", "compressium_barrel_face");
    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private static final QuadTransformer MOVE_TO_CORNER = new QuadTransformer(new Transformation(new Vector3f(-0.5F, -0.5F, -0.5F), null, null, null));
    private static final QuadTransformer SCALE_SMALL_BLOCK_ITEM = new QuadTransformer(new Transformation(null, null, new Vector3f(DisplayItemRenderer.SMALL_3D_ITEM_SCALE, DisplayItemRenderer.SMALL_3D_ITEM_SCALE, DisplayItemRenderer.SMALL_3D_ITEM_SCALE), null));
    private static final QuadTransformer SCALE_COMPRESSIUM_FIXED_DISPLAY = new QuadTransformer(new Transformation(null, null, new Vector3f(CompressiumDisplayCompat.FIXED_DISPLAY_SCALE, CompressiumDisplayCompat.FIXED_DISPLAY_SCALE, CompressiumDisplayCompat.FIXED_DISPLAY_SCALE), null));
    private static final Set<ResourceLocation> LOGGED_BAKED_ITEMS = ConcurrentHashMap.newKeySet();
    private static final Map<ResourceLocation, AlphaMask> ALPHA_MASK_CACHE = new ConcurrentHashMap<>();

    @Shadow(remap = false)
    protected abstract List<BakedQuad> rotateDisplayItemQuads(List<BakedQuad> quads, BlockState state);

    @Shadow(remap = false)
    private void updateTintIndexes(List<BakedQuad> quads, int displayItemIndex) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private void recalculateDirections(List<BakedQuad> quads) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private QuadTransformer getDirectionMove(ItemStack displayItem, BakedModel model, BlockState state, Direction direction, int displayItemIndex, int displayItemCount, float itemScale) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private QuadTransformer getDisplayRotation(int rotation) {
        throw new AssertionError();
    }

    @Inject(
            method = "addRenderedItemSide(Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/Random;Ljava/util/List;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/BakedModel;ILnet/minecraft/core/Direction;II)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void svhc$addCompressiumBakedDisplayQuads(
            BlockState state,
            Random rand,
            List<BakedQuad> ret,
            ItemStack displayItem,
            BakedModel model,
            int rotation,
            Direction dir,
            int displayItemIndex,
            int displayItemCount,
            CallbackInfo ci
    ) {
        if (!(displayItem.getItem() instanceof BlockItem) || !CompressiumDisplayCompat.isCompressiumItem(displayItem)) {
            return;
        }

        ResourceLocation itemKey = Registry.ITEM.getKey(displayItem.getItem());
        if (itemKey != null && LOGGED_BAKED_ITEMS.add(itemKey)) {
            LOGGER.debug("Sophisticated VH Compat baking Sophisticated Storage Compressium display fallback for {}", itemKey);
        }

        if (dir != null) {
            List<BakedQuad> quads = createCompressiumFaceQuads(model, displayItem, dir);
            quads = MOVE_TO_CORNER.processMany(quads);
            quads = new QuadTransformer(TransformationHelper.toTransformation(model.getTransforms().getTransform(ItemTransforms.TransformType.FIXED))).processMany(quads);
            quads = SCALE_COMPRESSIUM_FIXED_DISPLAY.processMany(quads);
            if (displayItemCount > 1) {
                quads = SCALE_SMALL_BLOCK_ITEM.processMany(quads);
            }
            if (rotation != 0) {
                quads = getDisplayRotation(rotation).processMany(quads);
            }

            Direction facing = state.getBlock() instanceof net.p3pp3rf1y.sophisticatedstorage.block.BarrelBlock barrelBlock ? barrelBlock.getFacing(state) : Direction.NORTH;
            quads = rotateDisplayItemQuads(quads, state);
            quads = getDirectionMove(displayItem, model, state, facing, displayItemIndex, displayItemCount, (displayItemCount == 1 ? 1.0F : DisplayItemRenderer.SMALL_3D_ITEM_SCALE) * CompressiumDisplayCompat.FIXED_DISPLAY_SCALE).processMany(quads);
            quads = pushOutFromBarrelFace(quads, facing);
            recalculateDirections(quads);
            updateTintIndexes(quads, displayItemIndex);
            ret.addAll(quads);
        }

        ci.cancel();
    }

    private static List<BakedQuad> createCompressiumFaceQuads(BakedModel model, ItemStack displayItem, Direction direction) {
        AlphaMask alphaMask = loadAlphaMask(CompressiumDisplayCompat.layerTexture(displayItem));
        if (alphaMask == null) {
            return List.of(bakeFaceQuad(model.getParticleIcon(), direction));
        }
        return bakeMergedCompressiumFaceQuads(model.getParticleIcon(), alphaMask, direction);
    }

    private static List<BakedQuad> pushOutFromBarrelFace(List<BakedQuad> quads, Direction facing) {
        Vector3f normal = new Vector3f(
                facing.getStepX() * CompressiumDisplayCompat.BARREL_FACE_OUTSET,
                facing.getStepY() * CompressiumDisplayCompat.BARREL_FACE_OUTSET,
                facing.getStepZ() * CompressiumDisplayCompat.BARREL_FACE_OUTSET
        );
        return new QuadTransformer(new Transformation(normal, null, null, null)).processMany(quads);
    }

    private static BakedQuad bakeFaceQuad(TextureAtlasSprite sprite, Direction direction) {
        return FACE_BAKERY.bakeQuad(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Vector3f(16.0F, 16.0F, 16.0F),
                new BlockElementFace(null, -1, "", new BlockFaceUV(new float[] {0.0F, 0.0F, 16.0F, 16.0F}, 0)),
                sprite,
                direction,
                BlockModelRotation.X0_Y0,
                null,
                true,
                COMPRESSIUM_BARREL_FACE
        );
    }

    private static List<BakedQuad> bakeMergedCompressiumFaceQuads(TextureAtlasSprite baseSprite, AlphaMask alphaMask, Direction direction) {
        List<BakedQuad> quads = new ArrayList<>(alphaMask.width() * alphaMask.height());
        float step = 16.0F / alphaMask.width();
        for (int y = 0; y < alphaMask.height(); y++) {
            for (int x = 0; x < alphaMask.width(); x++) {
                int alpha = alphaMask.alpha(x, y);
                float x0 = x * step;
                float x1 = (x + 1) * step;
                float y0 = 16.0F - (y + 1) * step;
                float y1 = 16.0F - y * step;
                float u0 = x * step;
                float u1 = (x + 1) * step;
                float v0 = y * step;
                float v1 = (y + 1) * step;
                int multiplier = Math.max(0, 255 - alpha);
                quads.add(withVertexColor(bakeFaceQuad(baseSprite, direction, x0, y0, x1, y1, u0, v0, u1, v1), grayVertexColor(multiplier)));
            }
        }
        return quads;
    }

    private static BakedQuad bakeFaceQuad(TextureAtlasSprite sprite, Direction direction, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        Vector3f from;
        Vector3f to;
        if (direction == Direction.UP || direction == Direction.DOWN) {
            from = new Vector3f(x0, 0.0F, y0);
            to = new Vector3f(x1, 16.0F, y1);
        } else if (direction == Direction.EAST || direction == Direction.WEST) {
            from = new Vector3f(0.0F, y0, x0);
            to = new Vector3f(16.0F, y1, x1);
        } else {
            from = new Vector3f(x0, y0, 0.0F);
            to = new Vector3f(x1, y1, 16.0F);
        }
        return FACE_BAKERY.bakeQuad(
                from,
                to,
                new BlockElementFace(null, -1, "", new BlockFaceUV(new float[] {u0, v0, u1, v1}, 0)),
                sprite,
                direction,
                BlockModelRotation.X0_Y0,
                null,
                true,
                COMPRESSIUM_BARREL_FACE
        );
    }

    private static BakedQuad withVertexColor(BakedQuad quad, int color) {
        int[] vertices = quad.getVertices().clone();
        for (int vertex = 0; vertex < 4; vertex++) {
            vertices[vertex * 8 + 3] = color;
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
    }

    private static int grayVertexColor(int value) {
        int clamped = Math.max(0, Math.min(255, value));
        return 0xFF000000 | (clamped << 16) | (clamped << 8) | clamped;
    }

    private static AlphaMask loadAlphaMask(ResourceLocation texture) {
        AlphaMask cached = ALPHA_MASK_CACHE.get(texture);
        if (cached != null) {
            return cached;
        }

        ResourceLocation textureFile = new ResourceLocation(texture.getNamespace(), "textures/" + texture.getPath() + ".png");
        try (Resource resource = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(textureFile);
             NativeImage image = NativeImage.read(resource.getInputStream())) {
            int width = image.getWidth();
            int height = image.getHeight();
            int[] alpha = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    alpha[y * width + x] = NativeImage.getA(image.getPixelRGBA(x, y));
                }
            }
            AlphaMask alphaMask = new AlphaMask(width, height, alpha);
            ALPHA_MASK_CACHE.put(texture, alphaMask);
            return alphaMask;
        } catch (IOException e) {
            LOGGER.warn("Sophisticated VH Compat could not read Compressium overlay texture {}", textureFile, e);
            return null;
        }
    }

    private record AlphaMask(int width, int height, int[] alpha) {
        int alpha(int x, int y) {
            return alpha[y * width + x];
        }
    }
}
