package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;
import net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu;
import net.p3pp3rf1y.sophisticatedstorage.client.gui.StorageScreen;
import dev.hoyin1600p.sophisticated_vh_compat.compat.BarrelRenderCompat;
import dev.hoyin1600p.sophisticated_vh_compat.compat.CompressiumDisplayModel;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.client.ClientEventHandler", remap = false)
public abstract class StorageClientEventMixin {
    @Inject(method = "onRegisterReloadListeners", at = @At("TAIL"))
    private static void svhc$reloadCaches(RegisterClientReloadListenersEvent event, CallbackInfo ci) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            BarrelRenderCompat.clearCountCache();
            CompressiumDisplayModel.clearCache();
        });
    }

    @Inject(method = "tryCallSort", at = @At("HEAD"), cancellable = true)
    private static void svhc$sortOutsideSlots(Screen gui, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof StorageContainerMenu container && gui instanceof StorageScreen screen) {
            double x = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double y = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            if (screen.findSlot(x, y) == null) {
                container.sort();
                cir.setReturnValue(true);
            }
        }
    }
}
