package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import dev.hoyin1600p.sophisticated_vh_compat.compat.SporeBlossomDisplayModel;
import dev.hoyin1600p.sophisticated_vh_compat.compat.BarrelDisplayAdjustments;
import java.util.List;
import java.util.Random;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.client.render.BarrelBakedModelBase", remap = false)
public abstract class BarrelDisplayModelMixin {
    @ModifyVariable(
            method = "addRenderedItemSide(Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/Random;Ljava/util/List;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/BakedModel;ILnet/minecraft/core/Direction;II)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1
    )
    private BakedModel svhc$orientSporeBlossom(BakedModel model, BlockState state, Random random,
            List<BakedQuad> quads, ItemStack stack, BakedModel originalModel, int rotation,
            Direction side, int displayIndex, int displayCount) {
        // Native scaling, slot positions, user rotation, facing, lighting and tinting still apply.
        return SporeBlossomDisplayModel.wrap(stack, model);
    }

    @ModifyVariable(
            method = "getDirectionMove(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;IIF)Lnet/minecraftforge/client/model/QuadTransformer;",
            at = @At(value = "STORE", ordinal = 0), ordinal = 0, require = 1
    )
    private double svhc$moveDripleafOutward(double offset, ItemStack stack, BakedModel model,
            BlockState state, Direction facing, int displayIndex, int displayCount, float itemScale) {
        // Add once, before the native recessed-face adjustment and facing translation.
        return offset + BarrelDisplayAdjustments.getOutwardOffset(stack);
    }
}
