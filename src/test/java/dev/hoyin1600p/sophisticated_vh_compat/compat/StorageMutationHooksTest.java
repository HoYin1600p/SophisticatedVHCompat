package dev.hoyin1600p.sophisticated_vh_compat.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.hoyin1600p.sophisticated_vh_compat.mixin.InventoryHandlerMixin;
import dev.hoyin1600p.sophisticated_vh_compat.mixin.JeiTransferMixin;
import dev.hoyin1600p.sophisticated_vh_compat.mixin.StorageContainerMenuMixin;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.p3pp3rf1y.sophisticatedcore.inventory.IInventoryPartHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryPartitioner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageMutationHooksTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void refreshCannotOverwriteTheRequestedReplacement() {
        ItemStack exposed = new ItemStack(Items.IRON_INGOT, 17);
        AtomicReference<ItemStack> written = new AtomicReference<>();
        StorageStackUpdates.replace(exposed, () -> exposed.setCount(90), written::set);
        assertEquals(17, written.get().getCount());
        assertNotSame(exposed, written.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 64, 193})
    void hotbarSelectionSurvivesSlotReplacementAndPersistsSplits(int count) throws Throwable {
        var mixin = mock(StorageContainerMenuMixin.class, CALLS_REAL_METHODS);
        Slot slot = mock(Slot.class);
        ItemStack stored = new ItemStack(Items.IRON_INGOT, count);
        stored.getOrCreateTag().putString("marker", "kept");
        LocalRef<ItemStack> selection = new LocalRef<>() {
            private ItemStack value;
            public ItemStack get() { return value; }
            public void set(ItemStack value) { this.value = value; }
        };
        Operation<ItemStack> read = args -> stored;
        ItemStack detached = (ItemStack) call(StorageContainerMenuMixin.class, mixin,
                "svhc$detachHotbarSelection", slot, read, ClickType.SWAP, selection);
        assertNotSame(stored, detached);
        if (count > 64) {
            ItemStack hotbar = detached.split(64);
            Operation<Void> changed = args -> { fail("Expected a slot replacement"); return null; };
            call(StorageContainerMenuMixin.class, mixin, "svhc$writeSplitSelection", slot, changed, ClickType.SWAP, selection);
            verify(slot).set(detached);
            assertEquals(count, hotbar.getCount() + detached.getCount());
        } else {
            stored.setCount(0);
            assertEquals(count, detached.getCount());
        }
        assertEquals("kept", detached.getTag().getString("marker"));
        assertSame(stored, call(StorageContainerMenuMixin.class, mixin,
                "svhc$detachHotbarSelection", slot, read, ClickType.PICKUP, selection));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 64})
    void jeiFallbackReceivesOnlyUnacceptedItems(int accepted) throws Throwable {
        ItemStack grid = new ItemStack(Items.IRON_INGOT, 64);
        grid.getOrCreateTag().putString("marker", "kept");
        Operation<Integer> insert = args -> {
            assertSame(grid, args[2]);
            assertEquals(64, grid.getCount());
            return accepted;
        };
        int added = (int) call(JeiTransferMixin.class, null, "svhc$consumeAcceptedItems", null, List.of(), grid, insert);
        int returnedOrDropped = added < grid.getCount() ? grid.getCount() : 0;
        assertEquals(64, accepted + returnedOrDropped);
        if (accepted < 64) assertEquals("kept", grid.getTag().getString("marker"));
    }

    @Test
    void physicalWritesDoNotReapplyVirtualChangesAndExceptionsRestoreNotifications() throws Throwable {
        var mixin = mock(InventoryHandlerMixin.class, CALLS_REAL_METHODS);
        var partitioner = mock(InventoryPartitioner.class);
        var part = mock(IInventoryPartHandler.class, withSettings().extraInterfaces(CompressionContentsListener.class));
        when(partitioner.getPartBySlot(0)).thenReturn(part);
        var field = InventoryHandlerMixin.class.getDeclaredField("inventoryPartitioner");
        field.setAccessible(true);
        field.set(mixin, partitioner);
        Operation<Void> write = args -> {
            try {
                call(InventoryHandlerMixin.class, mixin, "svhc$reconcile", 0, new CallbackInfo("test", false));
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
            throw new IllegalStateException("write failed");
        };
        assertThrows(IllegalStateException.class, () -> call(InventoryHandlerMixin.class, mixin,
                "svhc$writeStoredSlot", 0, ItemStack.EMPTY, write));
        verify((CompressionContentsListener) part, never()).svhc$commitStackMutation(0);
        call(InventoryHandlerMixin.class, mixin, "svhc$reconcile", 0, new CallbackInfo("test", false));
        verify((CompressionContentsListener) part).svhc$commitStackMutation(0);
    }

    private static Object call(Class<?> type, Object instance, String name, Object... args) throws Throwable {
        var method = Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        method.setAccessible(true);
        try {
            return method.invoke(instance, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
