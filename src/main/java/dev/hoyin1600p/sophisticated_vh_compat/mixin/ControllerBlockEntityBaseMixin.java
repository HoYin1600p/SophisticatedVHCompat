package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import dev.hoyin1600p.sophisticated_vh_compat.config.ControllerRangeConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.controller.ControllerBlockEntityBase", remap = false)
public abstract class ControllerBlockEntityBaseMixin {
    @ModifyConstant(method = "isWithinRange", constant = @Constant(intValue = 15), require = 3, remap = false)
    private int svhc$useConfiguredControllerRange(int original) {
        return ControllerRangeConfig.isEnabled() ? ControllerRangeConfig.getControllerRange() : original;
    }
}
