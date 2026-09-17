package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.p3pp3rf1y.sophisticatedstorage.block.LimitedBarrelBlock;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.block.LimitedBarrelBlock", remap = false)
public abstract class LimitedBarrelInteractionMixin {
    @Inject(method = "trySneakItemInteraction", at = @At(value = "INVOKE",
            target = "Lnet/p3pp3rf1y/sophisticatedstorage/block/LimitedBarrelBlock;tryToDyeAll(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z"), cancellable = true)
    private void svhc$openFront(Player player, InteractionHand hand, BlockState state, Level level,
            BlockPos pos, BlockHitResult hit, ItemStack held, CallbackInfoReturnable<Boolean> cir) {
        LimitedBarrelBlock barrel = (LimitedBarrelBlock) (Object) this;
        if (hand == InteractionHand.MAIN_HAND && held.isEmpty() && hit.getDirection() == barrel.getFacing(state)) {
            cir.setReturnValue(barrel.use(state, level, pos, player, hand, hit).consumesAction());
        }
    }
}
