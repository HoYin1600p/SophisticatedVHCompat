package dev.hoyin1600p.sophisticated_vh_compat.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.BakedModelWrapper;

/** A barrel-local model view; the shared inventory/world model is never changed. */
public final class SporeBlossomDisplayModel extends BakedModelWrapper<BakedModel> {
    public static final double FACE_CLEARANCE = 1.0 / 1024.0;
    private static final float ATTACHMENT_Y = 15.9F / 16.0F;
    private final ItemTransforms transforms;

    private SporeBlossomDisplayModel(BakedModel original) {
        super(original);
        ItemTransforms source = original.getTransforms();
        Vector3f scale = source.getTransform(ItemTransforms.TransformType.FIXED).scale;
        // The vanilla base lies at y=15.9/16. Rotate hanging (-Y) petals toward the
        // north-facing display's exterior (-Z), then anchor the base at local z=0.
        ItemTransform fixed = new ItemTransform(new Vector3f(90, 0, 0),
                new Vector3f(0, 0, -(ATTACHMENT_Y - 0.5F) * scale.y()), scale);
        transforms = new ItemTransforms(source.thirdPersonLeftHand, source.thirdPersonRightHand,
                source.firstPersonLeftHand, source.firstPersonRightHand, source.head,
                source.gui, source.ground, fixed, source.moddedTransforms);
    }

    public static BakedModel wrap(ItemStack stack, BakedModel model) {
        return stack.is(Items.SPORE_BLOSSOM) && !(model instanceof SporeBlossomDisplayModel)
                ? new SporeBlossomDisplayModel(model) : model;
    }

    @Override
    public ItemTransforms getTransforms() {
        return transforms;
    }

    @Override
    public boolean doesHandlePerspectives() {
        return true;
    }

    @Override
    public BakedModel handlePerspective(ItemTransforms.TransformType type, PoseStack poseStack) {
        if (type == ItemTransforms.TransformType.FIXED) {
            // Forge requires a pushed pose to recognize a supplied camera transformation.
            return ForgeHooksClient.handlePerspective(this, type, poseStack);
        }
        return originalModel.handlePerspective(type, poseStack);
    }
}
