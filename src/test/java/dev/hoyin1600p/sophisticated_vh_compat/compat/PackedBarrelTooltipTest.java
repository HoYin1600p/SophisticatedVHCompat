package dev.hoyin1600p.sophisticated_vh_compat.compat;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryPartRegistry;
import net.p3pp3rf1y.sophisticatedcore.util.RecipeHelper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.stack.StackUpgradeItem;
import net.p3pp3rf1y.sophisticatedstorage.Config;
import net.p3pp3rf1y.sophisticatedstorage.block.IStorageBlock;
import net.p3pp3rf1y.sophisticatedstorage.upgrades.compression.CompressionInventoryPart;
import net.p3pp3rf1y.sophisticatedstorage.upgrades.compression.CompressionUpgradeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PackedBarrelTooltipTest {
    private MockedStatic<RecipeHelper> recipes;
    private Item[] denominations;
    private static StackUpgradeItem stackUpgrade;
    private static CompressionUpgradeItem compressionUpgrade;

    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Match Forge's registration phase before constructing the real upgrade fixtures.
        GameData.unfreezeData();
        Config.SERVER_SPEC.setConfig(CommentedConfig.inMemory());
        InventoryPartRegistry.registerFactory(CompressionInventoryPart.NAME, CompressionInventoryPart::new);
        stackUpgrade = new StackUpgradeItem(4, CreativeModeTab.TAB_MISC, Config.SERVER.maxUpgradesPerStorage);
        stackUpgrade.setRegistryName("sophisticated_vh_compat_test", "stack_upgrade");
        ForgeRegistries.ITEMS.register(stackUpgrade);
        compressionUpgrade = new CompressionUpgradeItem(CreativeModeTab.TAB_MISC);
        compressionUpgrade.setRegistryName("sophisticated_vh_compat_test", "compression_upgrade");
        ForgeRegistries.ITEMS.register(compressionUpgrade);
        // Plain JUnit does not run Forge's event-constructor transformer. Isolate the unrelated
        // player-clone listener registration; all preview and inventory code still runs normally.
        try (MockedStatic<EventListenerHelper> events = mockStatic(EventListenerHelper.class)) {
            events.when(() -> EventListenerHelper.getListenerList(any(Class.class))).thenReturn(new ListenerList());
            Class.forName("net.p3pp3rf1y.sophisticatedstorage.settings.StorageSettingsHandler");
        }
    }

    @BeforeEach
    void setUpRecipes() throws Exception {
        // A synthetic fourth tier plus the real iron block/ingot/nugget chain. Only recipe lookup
        // is mocked: NBT loading, inventory partitions, compression and the preview wrapper are real.
        denominations = new Item[] {Items.DIAMOND_BLOCK, Items.IRON_BLOCK, Items.IRON_INGOT, Items.IRON_NUGGET};
        recipes = Mockito.mockStatic(RecipeHelper.class);
        recipes.when(() -> RecipeHelper.getItemCompactingShapes(any(Item.class))).thenReturn(Set.of(RecipeHelper.CompactingShape.NONE));
        recipes.when(() -> RecipeHelper.getUncompactingResult(any(Item.class))).thenReturn(RecipeHelper.UncompactingResult.EMPTY);
        var resultConstructor = RecipeHelper.CompactingResult.class.getDeclaredConstructor(ItemStack.class, List.class);
        resultConstructor.setAccessible(true);
        for (int i = 1; i < denominations.length; i++) {
            Item lower = denominations[i];
            Item upper = denominations[i - 1];
            var shape = RecipeHelper.CompactingShape.THREE_BY_THREE_UNCRAFTABLE;
            var result = resultConstructor.newInstance(new ItemStack(upper), List.of());
            recipes.when(() -> RecipeHelper.getItemCompactingShapes(lower)).thenReturn(Set.of(shape));
            recipes.when(() -> RecipeHelper.getCompactingResult(lower, shape)).thenReturn(result);
            recipes.when(() -> RecipeHelper.getUncompactingResult(upper)).thenReturn(new RecipeHelper.UncompactingResult(lower, shape));
        }
    }

    @AfterEach
    void closeRecipes() {
        recipes.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void showsEveryCalculatedDenominationInSlotOrder(int slots) {
        Item[] items = java.util.Arrays.copyOfRange(denominations, 4 - slots, 4);
        CompoundTag saved = inventory(slots, true, new ItemStack(items[0], 129));
        CompoundTag before = saved.copy();

        List<ItemStack> result = PackedBarrelTooltip.getContents(barrel(slots), saved);

        assertEquals(slots, result.size());
        int count = 129;
        for (int i = 0; i < slots; i++) {
            assertSame(items[i], result.get(i).getItem());
            assertEquals(count, result.get(i).getCount());
            count *= 9;
        }
        assertEquals(before, saved, "Preview must not modify synchronized contents");
    }

    @Test
    void calculatesRemaindersWithoutChangingSavedDataOrEarlierPreviews() {
        CompoundTag saved = inventory(3, true, new ItemStack(Items.IRON_BLOCK),
                new ItemStack(Items.IRON_INGOT, 20), new ItemStack(Items.IRON_NUGGET, 40));
        CompoundTag before = saved.copy();

        List<ItemStack> first = PackedBarrelTooltip.getContents(barrel(3), saved);
        assertEquals(List.of(3, 33, 301), first.stream().map(ItemStack::getCount).toList());
        first.get(0).setCount(999);
        List<ItemStack> second = PackedBarrelTooltip.getContents(barrel(3), saved);
        assertEquals(List.of(3, 33, 301), second.stream().map(ItemStack::getCount).toList());
        assertEquals(before, saved);
    }

    @Test
    void reflectsNewContentsAfterSyncAndKeepsOtherBarrelsIndependent() {
        CompoundTag first = inventory(2, true, new ItemStack(Items.IRON_INGOT, 2));
        CompoundTag second = inventory(2, true, new ItemStack(Items.IRON_INGOT, 7));
        assertEquals(List.of(2, 18), counts(first, 2));
        assertEquals(List.of(7, 63), counts(second, 2));
        assertEquals(List.of(2, 18), counts(first, 2));
    }

    @Test
    void ordinarySlotsKeepTheirItemsNbtAndFullCounts() {
        ItemStack named = new ItemStack(Items.DIAMOND, 257);
        named.getOrCreateTag().putString("testMarker", "preserved");
        CompoundTag saved = inventory(4, false, new ItemStack(Items.DIRT), ItemStack.EMPTY,
                named, new ItemStack(Items.DIRT, 3));
        CompoundTag before = saved.copy();
        List<ItemStack> result = PackedBarrelTooltip.getContents(barrel(4), saved);
        assertEquals(3, result.size());
        assertSame(Items.DIRT, result.get(0).getItem());
        assertSame(Items.DIAMOND, result.get(1).getItem());
        assertEquals(257, result.get(1).getCount());
        assertEquals("preserved", result.get(1).getTag().getString("testMarker"));
        assertSame(Items.DIRT, result.get(2).getItem(), "Separate slots should not be merged");
        assertEquals(before, saved);
    }

    @Test
    void emptyBarrelsRemainEmptyAndUnpackedItemsAreNotHandled() {
        assertTrue(PackedBarrelTooltip.getContents(barrel(4), inventory(4, true)).isEmpty());
        assertTrue(PackedBarrelTooltip.getPreview(new ItemStack(Items.BARREL)).isEmpty());
        assertTrue(PackedBarrelTooltip.getPreview(ItemStack.EMPTY).isEmpty());
    }

    @Test
    void restoresAllSavedUpgradeSlotsAndTheBaseTimesUpgradeMultiplier() {
        CompoundTag saved = inventory(27, false, new ItemStack(Items.DIAMOND, 257));
        addUpgrades(saved);
        CompoundTag before = saved.copy();
        IStorageBlock block = barrel(27);
        when(block.getNumberOfUpgradeSlots()).thenReturn(2); // Saved metadata must win over defaults.
        when(block.getBaseStackSizeMultiplier()).thenReturn(8);

        var preview = PackedBarrelTooltip.createPreview(block, saved);
        var upgrades = preview.getUpgradeHandler().getSlotWrappers();
        assertEquals(Set.of(1, 3), upgrades.keySet());
        assertSame(compressionUpgrade, upgrades.get(1).getUpgradeStack().getItem());
        assertSame(stackUpgrade, upgrades.get(3).getUpgradeStack().getItem());
        assertEquals("saved-upgrade", upgrades.get(3).getUpgradeStack().getTag().getString("testMarker"));
        assertEquals(32, preview.getInventoryHandler().getStackSizeMultiplier());
        assertEquals(before, saved);
        upgrades.get(3).getUpgradeStack().getOrCreateTag().putString("testMarker", "changed-preview");
        assertEquals(before, saved);
    }

    @Test
    void upgradeOnlyBarrelsRetainIconsWhenThereIsNoInventoryTag() {
        CompoundTag saved = inventory(27, false);
        saved.getCompound("contents").remove("inventory");
        addUpgrades(saved);
        var preview = PackedBarrelTooltip.createPreview(barrel(27), saved);
        assertTrue(PackedBarrelTooltip.getContents(preview).isEmpty());
        assertEquals(2, preview.getUpgradeHandler().getSlotWrappers().size());
    }

    @Test
    void identifiesEveryRegularAndLimitedBarrelMaterialTier() {
        for (String tier : List.of("wood", "copper", "iron", "gold", "diamond", "netherite")) {
            String prefix = tier.equals("wood") ? "" : tier + "_";
            assertEquals(tier, PackedBarrelTooltip.getTier(new ResourceLocation("sophisticatedstorage", prefix + "barrel")).orElseThrow());
            for (int slots = 1; slots <= 4; slots++) {
                assertEquals(tier, PackedBarrelTooltip.getTier(new ResourceLocation("sophisticatedstorage", "limited_" + prefix + "barrel_" + slots)).orElseThrow());
            }
        }
        assertTrue(PackedBarrelTooltip.getTier(new ResourceLocation("othermod", "diamond_barrel")).isEmpty());
        assertTrue(PackedBarrelTooltip.getTier(new ResourceLocation("sophisticatedstorage", "diamond_chest")).isEmpty());
        assertTrue(PackedBarrelTooltip.getTier(null).isEmpty());
    }

    @Test
    void compressedContentsAndUpgradesComeFromTheSameDetachedPreview() {
        CompoundTag saved = inventory(3, true, new ItemStack(Items.IRON_BLOCK, 129));
        addUpgrades(saved);
        CompoundTag before = saved.copy();
        var preview = PackedBarrelTooltip.createPreview(barrel(3), saved);
        assertEquals(List.of(129, 1161, 10449), PackedBarrelTooltip.getContents(preview).stream().map(ItemStack::getCount).toList());
        assertEquals(2, preview.getUpgradeHandler().getSlotWrappers().size());
        assertEquals(2048, preview.getInventoryHandler().getStackSizeMultiplier());
        assertEquals(before, saved);
    }

    private void addUpgrades(CompoundTag saved) {
        CompoundTag upgrades = new CompoundTag();
        upgrades.putInt("Size", 4);
        ListTag items = new ListTag();
        CompoundTag compression = new ItemStack(compressionUpgrade).save(new CompoundTag());
        compression.putInt("Slot", 1);
        items.add(compression);
        ItemStack stack = new ItemStack(stackUpgrade);
        stack.getOrCreateTag().putString("testMarker", "saved-upgrade");
        CompoundTag stacking = stack.save(new CompoundTag());
        stacking.putInt("Slot", 3);
        items.add(stacking);
        upgrades.put("Items", items);
        saved.getCompound("contents").put("upgradeInventory", upgrades);
        saved.putInt("numberOfUpgradeSlots", 4);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void storedContentsRemainAuthoritativeAfterDirectStackChanges(int slots) throws Exception {
        Item[] items = java.util.Arrays.copyOfRange(denominations, 4 - slots, 4);
        var preview = PackedBarrelTooltip.createPreview(barrel(slots), inventory(slots, true, new ItemStack(items[0], 129)));
        var handler = preview.getInventoryHandler();
        var part = handler.getInventoryPartitioner().getPartBySlot(slots - 1);
        var refresh = CompressionInventoryPart.class.getDeclaredMethod("updateCalculatedStacks");
        refresh.setAccessible(true);
        Runnable rebuild = () -> {
            try {
                refresh.invoke(part);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        };
        int slot = slots - 1;
        long initial = storedUnits(handler, slots);
        ItemStack exposed = handler.getStackInSlot(slot);
        exposed.shrink(3);
        StorageStackUpdates.replace(exposed, rebuild, stack -> handler.setStackInSlot(slot, stack));
        assertEquals(initial - 3, storedUnits(handler, slots));
        assertEquals(initial - 3, handler.getStackInSlot(slot).getCount());

        exposed = handler.getStackInSlot(slot);
        exposed.grow(7);
        StorageStackUpdates.replace(exposed, rebuild, stack -> handler.setStackInSlot(slot, stack));
        assertEquals(initial + 4, storedUnits(handler, slots));

        ItemStack removed = handler.getStackInSlot(slot).split(64);
        StorageStackUpdates.replace(handler.getStackInSlot(slot), rebuild, stack -> handler.setStackInSlot(slot, stack));
        assertEquals(64, removed.getCount());
        assertEquals(initial - 60, storedUnits(handler, slots));

        StorageStackUpdates.replace(ItemStack.EMPTY, rebuild, stack -> handler.setStackInSlot(slot, stack));
        assertEquals(0, storedUnits(handler, slots));
        assertTrue(handler.getStackInSlot(slot).isEmpty());
    }

    private long storedUnits(net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler handler, int slots) {
        long units = 0;
        for (int slot = 0; slot < slots; slot++) {
            units = units * 9 + handler.getSlotStack(slot).getCount();
        }
        return units;
    }

    private List<Integer> counts(CompoundTag saved, int slots) {
        return PackedBarrelTooltip.getContents(barrel(slots), saved).stream().map(ItemStack::getCount).toList();
    }

    private IStorageBlock barrel(int slots) {
        IStorageBlock block = mock(IStorageBlock.class);
        when(block.getNumberOfInventorySlots()).thenReturn(slots);
        when(block.getNumberOfUpgradeSlots()).thenReturn(0);
        when(block.getBaseStackSizeMultiplier()).thenReturn(512);
        return block;
    }

    private CompoundTag inventory(int slots, boolean compression, ItemStack... stacks) {
        CompoundTag inventory = new CompoundTag();
        ListTag items = new ListTag();
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i].isEmpty()) {
                continue;
            }
            CompoundTag item = stacks[i].save(new CompoundTag());
            item.putInt("Slot", i);
            item.putInt("realCount", stacks[i].getCount());
            items.add(item);
        }
        inventory.putInt("Size", slots);
        inventory.put("Items", items);
        CompoundTag contents = new CompoundTag();
        contents.put("inventory", inventory);
        if (compression) {
            CompoundTag partitioner = new CompoundTag();
            partitioner.putIntArray("baseIndexes", new int[] {0});
            ListTag parts = new ListTag();
            parts.add(StringTag.valueOf(CompressionInventoryPart.NAME));
            partitioner.put("inventoryPartNames", parts);
            contents.put("partitioner", partitioner);
        }
        CompoundTag wrapper = new CompoundTag();
        wrapper.putInt("numberOfInventorySlots", slots);
        wrapper.putInt("numberOfUpgradeSlots", 0);
        wrapper.put("contents", contents);
        return wrapper;
    }
}
