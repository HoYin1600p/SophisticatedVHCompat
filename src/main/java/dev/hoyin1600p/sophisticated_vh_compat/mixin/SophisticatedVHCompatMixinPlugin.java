package dev.hoyin1600p.sophisticated_vh_compat.mixin;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

public class SophisticatedVHCompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String STORAGE_CLASS = "net.p3pp3rf1y.sophisticatedstorage.block.LimitedBarrelBlock";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!isClassPresent(targetClassName)) {
            return false;
        }
        if (mixinClassName.endsWith("ControllerBlockEntityBaseMixin")) {
            return true;
        }
        if (mixinClassName.endsWith("SophisticatedStorageLimitedBarrelClientInteractionMixin")
                || mixinClassName.endsWith("PackedBarrelContentsTooltipMixin")
                || mixinClassName.endsWith("StorageContentsTooltipAccessor")) {
            if (!isClassPresent(STORAGE_CLASS)) {
                return false;
            }
        }
        if ((mixinClassName.endsWith("PackedBarrelContentsTooltipMixin")
                || mixinClassName.endsWith("StorageContentsTooltipAccessor"))
                && !isClassPresent("net.p3pp3rf1y.sophisticatedstorage.client.render.ClientStorageContentsTooltip")) {
            return false;
        }
        if (!mixinClassName.endsWith("JeiRemainderPersistenceMixin") && hasCompleteBackport()) {
            LOGGER.debug("Skipping {} because the installed Sophisticated pair already contains the backports", mixinClassName);
            return false;
        }
        return true;
    }

    private static boolean hasCompleteBackport() {
        try {
            ClassNode core = MixinService.getService().getBytecodeProvider()
                    .getClassNode("net.p3pp3rf1y.sophisticatedcore.inventory.IInventoryPartHandler");
            return core.methods.stream().anyMatch(method -> method.name.equals("onContentsChanged"))
                    && isClassPresent("net.p3pp3rf1y.sophisticatedstorage.client.render.BarrelDisplayItem");
        } catch (ClassNotFoundException | IOException e) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isClassPresent(String className) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(className);
            return true;
        } catch (ClassNotFoundException | IOException e) {
            return false;
        }
    }

}
