package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hoyin1600p.sophisticated_vh_compat.compat.SporeBlossomDisplayModel;
import dev.hoyin1600p.sophisticated_vh_compat.compat.BarrelDisplayAdjustments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedstorage.block.BarrelBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.client.render.DisplayItemRenderer", remap = false)
public abstract class BarrelDisplayRendererMixin {
    @Unique
    private boolean svhc$barrelDisplay;

    // Both entry points establish context before calling the shared private item renderer.
    @Inject(method = "renderDisplayItems", at = @At("HEAD"), require = 1)
    private void svhc$beginMultipleItems(StorageBlockEntity storage, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, boolean onlyCustom, CallbackInfo ci) {
        svhc$barrelDisplay = storage instanceof BarrelBlockEntity;
    }

    @Inject(method = "renderDisplayItem", at = @At("HEAD"), require = 1)
    private void svhc$beginSingleItem(StorageBlockEntity storage, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        svhc$barrelDisplay = storage instanceof BarrelBlockEntity;
    }

    @Inject(method = {"renderDisplayItems", "renderDisplayItem"}, at = @At("RETURN"), require = 1)
    private void svhc$endItems(CallbackInfo ci) {
        svhc$barrelDisplay = false;
    }

    @ModifyVariable(
            method = "renderSingleItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/Minecraft;ZIILnet/minecraft/world/item/ItemStack;I)V",
            at = @At("STORE"), ordinal = 0, require = 1
    )
    private BakedModel svhc$orientDynamicSporeBlossom(BakedModel model, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, Minecraft minecraft,
            boolean onlyCustom, int displayIndex, int displayCount, ItemStack stack, int rotation) {
        return svhc$barrelDisplay ? SporeBlossomDisplayModel.wrap(stack, model) : model;
    }

    @ModifyVariable(
            method = "renderSingleItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/Minecraft;ZIILnet/minecraft/world/item/ItemStack;I)V",
            at = @At(value = "STORE", ordinal = 0), ordinal = 0, require = 1
    )
    private float svhc$moveDynamicDripleafOutward(float offset, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, Minecraft minecraft,
            boolean onlyCustom, int displayIndex, int displayCount, ItemStack stack, int rotation) {
        // The renderer translates by -offset along the barrel's local outward normal.
        return svhc$barrelDisplay ? offset + (float) BarrelDisplayAdjustments.getOutwardOffset(stack) : offset;
    }

    @Inject(
            method = "getDisplayItemOffset(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/BakedModel;F)D",
            at = @At("HEAD"), cancellable = true, require = 1
    )
    private static void svhc$anchorBlossomBaseToFace(ItemStack stack, BakedModel model,
            float additionalScale, CallbackInfoReturnable<Double> cir) {
        if (model instanceof SporeBlossomDisplayModel) {
            // Applied to both rendering paths. Bounding-box placement would bury the petals again.
            cir.setReturnValue(SporeBlossomDisplayModel.FACE_CLEARANCE);
        }
    }
}
