package dev.hoyin1600p.sophisticated_vh_compat.compat;

import com.google.gson.JsonParser;
import dev.hoyin1600p.sophisticated_vh_compat.mixin.SophisticatedVHCompatMixinPlugin;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TooltipMixinTargetTest {
    private static final String CORE = "net.p3pp3rf1y.sophisticatedcore.client.render.ClientStorageContentsTooltip";
    private static final String STORAGE = "net.p3pp3rf1y.sophisticatedstorage.client.render.ClientStorageContentsTooltip";
    private static final String PREFIX = "dev.hoyin1600p.sophisticated_vh_compat.mixin.";

    @Test
    void supportedCoreHasOneHookAfterContentsAssignmentAndBeforeLayout() throws Exception {
        ClassNode core = readClass(CORE);
        var refresh = core.methods.stream().filter(method -> method.name.equals("refreshContents")
                && method.desc.equals("(Lnet/p3pp3rf1y/sophisticatedcore/api/IStorageWrapper;)V")).findFirst().orElseThrow();
        int assignment = -1;
        int invalidation = -1;
        int inventoryRead = -1;
        int invalidations = 0;
        int hook = -1;
        int height = -1;
        int hooks = 0;
        for (int i = 0; i < refresh.instructions.size(); i++) {
            var instruction = refresh.instructions.get(i);
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC
                    && field.name.equals("sortedContents")) {
                assignment = i;
            }
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("net/p3pp3rf1y/sophisticatedcore/api/IStorageWrapper")) {
                    if (call.name.equals("onContentsNbtUpdated")) {
                        invalidation = i;
                        invalidations++;
                    }
                    if (call.name.equals("getInventoryHandler")) {
                        inventoryRead = i;
                    }
                }
                if (call.owner.equals("net/p3pp3rf1y/sophisticatedcore/api/IStorageWrapper")
                        && call.name.equals("getUpgradeHandler")
                        && call.desc.equals("()Lnet/p3pp3rf1y/sophisticatedcore/upgrades/UpgradeHandler;")) {
                    hook = i;
                    hooks++;
                }
                if (call.name.equals("calculateHeight")) {
                    height = i;
                }
            }
        }
        assertEquals(1, hooks);
        assertEquals(1, invalidations);
        assertTrue(invalidation < inventoryRead && inventoryRead < assignment,
                "Preview replacement must follow invalidation and precede every inventory/upgrade read");
        assertTrue(assignment >= 0 && assignment < hook && hook < height);
        assertTrue(core.fields.stream().anyMatch(field -> field.name.equals("sortedContents")
                && field.desc.equals("Ljava/util/List;") && (field.access & Opcodes.ACC_STATIC) != 0));
        assertTrue(readClass(STORAGE).fields.stream().anyMatch(field -> field.name.equals("storageItem")
                && field.desc.equals("Lnet/minecraft/world/item/ItemStack;")));
    }

    @Test
    void mixinSidesAndVaultAdditionsDeduplicationAreCorrect() throws Exception {
        try (var stream = getClass().getResourceAsStream("/sophisticated_vh_compat.mixins.json")) {
            var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            String clients = config.getAsJsonArray("client").toString();
            String common = config.getAsJsonArray("mixins").toString();
            assertTrue(clients.contains("StorageContentsTooltipAccessor"));
            assertTrue(clients.contains("PackedBarrelContentsTooltipMixin"));
            assertTrue(clients.contains("BarrelDisplayModelMixin"));
            assertTrue(clients.contains("BarrelDisplayRendererMixin"));
            assertTrue(common.contains("CompactingUpgradeWrapperMixin"));
            assertTrue(common.contains("CompressionSlotDefinitionAccessor"));
            assertTrue(common.contains("CompressionInventoryPartMixin"));
            assertTrue(common.contains("InventoryHandlerExtractMixin"));
        }
        IMixinService service = mock(IMixinService.class);
        IClassBytecodeProvider bytecode = mock(IClassBytecodeProvider.class);
        when(service.getBytecodeProvider()).thenReturn(bytecode);
        when(bytecode.getClassNode(anyString())).thenReturn(new ClassNode());
        LoadingModList mods = mock(LoadingModList.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<MixinService> mixin = mockStatic(MixinService.class);
             MockedStatic<LoadingModList> loading = mockStatic(LoadingModList.class)) {
            mixin.when(MixinService::getService).thenReturn(service);
            loading.when(LoadingModList::get).thenReturn(mods);
            var plugin = new SophisticatedVHCompatMixinPlugin();
            assertTrue(plugin.shouldApplyMixin(CORE, PREFIX + "PackedBarrelContentsTooltipMixin"));
            assertTrue(plugin.shouldApplyMixin(STORAGE, PREFIX + "StorageContentsTooltipAccessor"));
            assertFalse(plugin.shouldApplyMixin(STORAGE, PREFIX + "SophisticatedStorageDisplayItemRendererMixin"));
            assertFalse(plugin.shouldApplyMixin(
                    "net.p3pp3rf1y.sophisticatedcore.upgrades.compacting.CompactingUpgradeWrapper",
                    PREFIX + "CompactingUpgradeWrapperMixin"
            ));
            assertFalse(plugin.shouldApplyMixin(
                    "net.p3pp3rf1y.sophisticatedstorage.upgrades.compression.CompressionInventoryPart",
                    PREFIX + "CompressionInventoryPartMixin"
            ));
            when(mods.getModFileById("vaultadditions")).thenReturn(null);
            assertTrue(plugin.shouldApplyMixin(
                    "net.p3pp3rf1y.sophisticatedcore.upgrades.compacting.CompactingUpgradeWrapper",
                    PREFIX + "CompactingUpgradeWrapperMixin"
            ));
            assertTrue(plugin.shouldApplyMixin(
                    "net.p3pp3rf1y.sophisticatedstorage.upgrades.compression.CompressionInventoryPart",
                    PREFIX + "CompressionInventoryPartMixin"
            ));
            String barrelModel = "net.p3pp3rf1y.sophisticatedstorage.client.render.BarrelBakedModelBase";
            String displayRenderer = "net.p3pp3rf1y.sophisticatedstorage.client.render.DisplayItemRenderer";
            assertTrue(plugin.shouldApplyMixin(barrelModel, PREFIX + "BarrelDisplayModelMixin"));
            assertTrue(plugin.shouldApplyMixin(displayRenderer, PREFIX + "BarrelDisplayRendererMixin"));
            when(bytecode.getClassNode(barrelModel)).thenThrow(new ClassNotFoundException());
            assertFalse(plugin.shouldApplyMixin(barrelModel, PREFIX + "BarrelDisplayModelMixin"));
            when(bytecode.getClassNode(STORAGE)).thenThrow(new ClassNotFoundException());
            assertFalse(plugin.shouldApplyMixin(CORE, PREFIX + "PackedBarrelContentsTooltipMixin"));
            assertFalse(plugin.shouldApplyMixin(STORAGE, PREFIX + "StorageContentsTooltipAccessor"));
        }
    }

    @Test
    void supportedRuntimeTargetsExposeAllRequiredHooks() throws Exception {
        ClassNode inventory = readClass("net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler");
        assertEquals(1, inventory.methods.stream().filter(method -> method.name.equals("extractItemInternal")).count());

        ClassNode compacting = readClass(
                "net.p3pp3rf1y.sophisticatedcore.upgrades.compacting.CompactingUpgradeWrapper"
        );
        assertEquals(1, compacting.methods.stream().filter(method -> method.name.equals("compactSlot")
                && method.desc.equals(
                "(Lnet/p3pp3rf1y/sophisticatedcore/inventory/IItemHandlerSimpleInserter;I)V"
        )).count());

        ClassNode compression = readClass(
                "net.p3pp3rf1y.sophisticatedstorage.upgrades.compression.CompressionInventoryPart"
        );
        assertEquals(1, compression.methods.stream().filter(method -> method.name.equals("updateSlotLimits")
                && method.desc.equals("(Ljava/util/Map;)V")).count());
        assertEquals(1, compression.methods.stream().filter(method -> method.name.equals("insertItem")
                && method.desc.equals(
                "(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"
        )).count());
        assertEquals(1, compression.methods.stream().filter(method -> method.name.equals("extractItem")
                && method.desc.equals(
                "(IIZLjava/util/function/ToIntFunction;)Lnet/minecraft/world/item/ItemStack;"
        )).count());

        var insertInternal = compression.methods.stream()
                .filter(method -> method.name.equals("insertIntoInternalAndCalculated"))
                .findFirst()
                .orElseThrow();
        assertEquals(5, insertInternal.instructions.size() == 0 ? 0
                : java.util.stream.StreamSupport.stream(insertInternal.instructions.spliterator(), false)
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(call -> call.name.equals("getPrevSlotMultiplier"))
                .count());

        var extract = compression.methods.stream().filter(method -> method.name.equals("extractItem")
                && method.desc.equals(
                "(IIZLjava/util/function/ToIntFunction;)Lnet/minecraft/world/item/ItemStack;"
        )).findFirst().orElseThrow();
        assertEquals(1, java.util.stream.StreamSupport.stream(extract.instructions.spliterator(), false)
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(call -> call.name.equals("extractFromInternal"))
                .count());
    }

    @Test
    void supportedStorageHasUnambiguousSporeBlossomModelHooks() throws Exception {
        ClassNode barrel = readClass("net.p3pp3rf1y.sophisticatedstorage.client.render.BarrelBakedModelBase");
        assertEquals(1, barrel.methods.stream().filter(method -> method.name.equals("addRenderedItemSide")
                && method.desc.equals("(Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/Random;Ljava/util/List;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/BakedModel;ILnet/minecraft/core/Direction;II)V")).count());
        ClassNode renderer = readClass("net.p3pp3rf1y.sophisticatedstorage.client.render.DisplayItemRenderer");
        var render = renderer.methods.stream().filter(method -> method.name.equals("renderSingleItem")).findFirst().orElseThrow();
        var models = render.localVariables.stream().filter(local -> local.desc.equals("Lnet/minecraft/client/resources/model/BakedModel;")).toList();
        assertEquals(1, models.size(), "The dynamic hook must select exactly one model local");
        int modelStores = 0;
        for (var instruction : render.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.VarInsnNode variable
                    && variable.getOpcode() == Opcodes.ASTORE && variable.var == models.get(0).index) {
                modelStores++;
            }
        }
        assertEquals(1, modelStores);
    }

    private ClassNode readClass(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
