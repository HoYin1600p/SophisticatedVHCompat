package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import dev.hoyin1600p.sophisticated_vh_compat.compat.CompressiumDisplayCompat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;

@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.client.render.DisplayItemRenderer", priority = 900)
public abstract class SophisticatedStorageDisplayItemRendererMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> LOGGED_RENDERED_ITEMS = ConcurrentHashMap.newKeySet();

    @Inject(
            method = "renderSingleItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/Minecraft;ZIILnet/minecraft/world/item/ItemStack;I)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void svhc$renderCompressiumBlock(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            Minecraft minecraft,
            boolean renderOnlyCustom,
            int displayItemIndex,
            int displayItemCount,
            ItemStack stack,
            int rotation,
            CallbackInfo ci
    ) {
        if (!(stack.getItem() instanceof BlockItem) || !CompressiumDisplayCompat.isCompressiumItem(stack)) {
            return;
        }

        BakedModel itemModel = minecraft.getItemRenderer().getModel(stack, null, minecraft.player, 0);
        float displayScale = displayItemCount == 1 ? 1.0F : 0.5F;
        float itemOffset = (float) CompressiumDisplayCompat.outwardDynamicDisplayItemOffset(displayScale);
        float itemScale = displayScale * CompressiumDisplayCompat.FIXED_DISPLAY_SCALE;
        Vector3f frontOffset = displayItemIndexFrontOffset(displayItemIndex, displayItemCount);
        ResourceLocation itemKey = Registry.ITEM.getKey(stack.getItem());
        if (itemKey != null && LOGGED_RENDERED_ITEMS.add(itemKey)) {
            LOGGER.debug("Sophisticated VH Compat rendering Sophisticated Storage Compressium display fallback for {}", itemKey);
        }

        poseStack.pushPose();
        poseStack.translate(frontOffset.x(), frontOffset.y(), -itemOffset);
        poseStack.mulPose(Vector3f.ZP.rotationDegrees(rotation));
        poseStack.scale(itemScale, itemScale, itemScale);
        renderCompressiumFace(poseStack, bufferSource, packedLight, packedOverlay, itemModel.getParticleIcon(), CompressiumDisplayCompat.layerTexture(stack));
        poseStack.popPose();
        ci.cancel();
    }

    @Inject(
            method = "getDisplayItemOffset(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/BakedModel;F)D",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void svhc$useFullCubeOffsetForCompressium(ItemStack item, BakedModel itemModel, float additionalScale, CallbackInfoReturnable<Double> cir) {
        if (!(item.getItem() instanceof BlockItem) || !CompressiumDisplayCompat.isCompressiumItem(item)) {
            return;
        }

        cir.setReturnValue(CompressiumDisplayCompat.fullCubeDisplayItemOffset(additionalScale));
    }

    private static void renderCompressiumFace(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            TextureAtlasSprite baseSprite,
            ResourceLocation layerTexture
    ) {
        VertexConsumer baseConsumer = baseSprite.wrap(bufferSource.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)));
        renderFaceQuad(poseStack, baseConsumer, packedLight, packedOverlay, 0.501F);

        Material layerMaterial = new Material(InventoryMenu.BLOCK_ATLAS, layerTexture);
        VertexConsumer layerConsumer = layerMaterial.buffer(bufferSource, RenderType::entityTranslucent);
        renderFaceQuad(poseStack, layerConsumer, packedLight, packedOverlay, 0.507F);
    }

    private static void renderFaceQuad(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay, float z) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();

        vertex(consumer, matrix, normal, -0.5F, -0.5F, z, 0.0F, 1.0F, packedLight, packedOverlay);
        vertex(consumer, matrix, normal, 0.5F, -0.5F, z, 1.0F, 1.0F, packedLight, packedOverlay);
        vertex(consumer, matrix, normal, 0.5F, 0.5F, z, 1.0F, 0.0F, packedLight, packedOverlay);
        vertex(consumer, matrix, normal, -0.5F, 0.5F, z, 0.0F, 0.0F, packedLight, packedOverlay);
    }

    private static void vertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            Matrix3f normal,
            float x,
            float y,
            float z,
            float u,
            float v,
            int packedLight,
            int packedOverlay
    ) {
        consumer.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    private static Vector3f displayItemIndexFrontOffset(int displayItemIndex, int displayItemCount) {
        if (displayItemCount <= 0 || displayItemCount > 4) {
            return new Vector3f(0.0F, 0.0F, 0.5F);
        }
        if (displayItemCount == 1) {
            return new Vector3f(0.5F, 0.5F, 0.5F);
        }

        float halfCenter = 0.25F;
        if (displayItemCount == 2) {
            return new Vector3f(0.5F, displayItemIndex == 0 ? 0.75F : halfCenter, 0.5F);
        }
        if (displayItemCount == 3) {
            float xOffset = displayItemIndex > 0 ? 0.75F - (displayItemIndex - 1) * 0.5F : 0.5F;
            return new Vector3f(xOffset, displayItemIndex == 0 ? 0.75F : halfCenter, 0.5F);
        }

        return new Vector3f(
                displayItemIndex == 0 || displayItemIndex == 2 ? 0.75F : halfCenter,
                displayItemIndex == 0 || displayItemIndex == 1 ? 0.75F : halfCenter,
                0.5F
        );
    }
}
