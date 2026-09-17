package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.p3pp3rf1y.sophisticatedstorage.block.LimitedBarrelBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.VerticalFacing;
import dev.hoyin1600p.sophisticated_vh_compat.compat.BarrelRenderCompat;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.client.render.LimitedBarrelRenderer", remap = false)
public abstract class LimitedBarrelRendererMixin {
    @Inject(method = "renderItemCounts", at = @At("HEAD"), cancellable = true)
    private void svhc$renderCounts(LimitedBarrelBlockEntity barrel, PoseStack pose, MultiBufferSource buffers,
            int light, boolean flat, Direction facing, VerticalFacing verticalFacing, CallbackInfo ci) {
        BarrelRenderCompat.renderItemCounts(barrel, pose, buffers, light, flat, facing, verticalFacing);
        ci.cancel();
    }

    @Inject(method = "renderFillLevel", at = @At("HEAD"), cancellable = true)
    private void svhc$renderFill(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            float fill, float x, float y, boolean large, boolean translucent, CallbackInfo ci) {
        BarrelRenderCompat.renderFillLevel(pose, buffers, light, overlay, fill, x, y, large, translucent);
        ci.cancel();
    }
}
