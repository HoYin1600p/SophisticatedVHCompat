package dev.hoyin1600p.sophisticated_vh_compat;

import com.mojang.logging.LogUtils;
import dev.hoyin1600p.sophisticated_vh_compat.command.ControllerRangeCommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

@Mod(SophisticatedVHCompat.MOD_ID)
public class SophisticatedVHCompat {
    public static final String MOD_ID = "sophisticated_vh_compat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SophisticatedVHCompat() {
        MinecraftForge.EVENT_BUS.addListener(ControllerRangeCommand::register);
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                SophisticatedVHCompat::createNetworkCompatibilityTest
        );
    }

    static IExtensionPoint.DisplayTest createNetworkCompatibilityTest() {
        return new IExtensionPoint.DisplayTest(
                () -> NetworkConstants.IGNORESERVERONLY,
                (remoteVersion, isServerConnection) -> true
        );
    }
}
