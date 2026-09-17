package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.common.CommonEventHandler", remap = false)
public abstract class StorageInteractionEventMixin {
    @Inject(method = "onSneakItemBlockInteraction", at = @At("TAIL"))
    private void svhc$acknowledgeInteraction(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        if (event.isCanceled()) {
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getWorld().isClientSide));
        }
    }
}
