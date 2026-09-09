package dev.hoyin1600p.sophisticated_vh_compat;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;

@Mod(SophisticatedVHCompat.MOD_ID)
public class SophisticatedVHCompat {
    public static final String MOD_ID = "sophisticated_vh_compat";

    public SophisticatedVHCompat() {
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
