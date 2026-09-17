package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler$Accessor", remap = false)
public abstract class UpgradeAccessorMixin {
    @Shadow @Final @Mutable private Map<Class<?>, List<?>> interfaceWrappers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void svhc$concurrentCache(CallbackInfo ci) {
        interfaceWrappers = new ConcurrentHashMap<>();
    }
}
