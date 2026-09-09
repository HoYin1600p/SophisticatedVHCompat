package dev.hoyin1600p.sophisticated_vh_compat;

import net.minecraftforge.network.NetworkConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SophisticatedVHCompatTest {
    @Test
    void acceptsMissingModOnEitherSide() {
        var displayTest = SophisticatedVHCompat.createNetworkCompatibilityTest();

        assertEquals(NetworkConstants.IGNORESERVERONLY, displayTest.suppliedVersion().get());
        assertTrue(displayTest.remoteVersionTest().test(null, true));
        assertTrue(displayTest.remoteVersionTest().test(null, false));
        assertTrue(displayTest.remoteVersionTest().test("0.0.2", true));
        assertTrue(displayTest.remoteVersionTest().test("different-version", true));
    }
}
