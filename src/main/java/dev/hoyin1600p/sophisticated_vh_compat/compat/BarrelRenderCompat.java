package dev.hoyin1600p.sophisticated_vh_compat.compat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedcore.util.CountAbbreviator;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.BarrelBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.LimitedBarrelBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.LimitedBarrelBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockBase;
import net.p3pp3rf1y.sophisticatedstorage.block.VerticalFacing;

import java.util.List;

import static net.minecraft.client.Minecraft.UNIFORM_FONT;

import net.p3pp3rf1y.sophisticatedstorage.client.render.DisplayItemRenderer;

public final class BarrelRenderCompat {
	public static final Material FILL_INDICATORS_TEXTURE = new Material(InventoryMenu.BLOCK_ATLAS, SophisticatedStorage.getRL("entity/fill_indicators"));
	private static final float MULTIPLE_ITEMS_FONT_SCALE = 1 / 96f;
	private static final float SINGLE_ITEM_FONT_SCALE = 1 / 48f;
	private static final Style COUNT_DISPLAY_STYLE = Style.EMPTY.withFont(UNIFORM_FONT).withBold(true);
	private static final Cache<Long, CountLabel> COUNT_LABELS = CacheBuilder.newBuilder().maximumSize(4096).build();
	private static Font cachedFont;

private BarrelRenderCompat() {}
public static void renderItemCounts(LimitedBarrelBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, boolean flatTop, Direction horizontalFacing, VerticalFacing verticalFacing) {
		if (!blockEntity.shouldShowCounts()) {
			return;
		}

		poseStack.pushPose();

		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(DisplayItemRenderer.getNorthBasedRotation(horizontalFacing.getOpposite()));// because of the font flipping
		if (verticalFacing != VerticalFacing.NO) {
			poseStack.mulPose(DisplayItemRenderer.getNorthBasedRotation(verticalFacing.getDirection().getOpposite()));// because of the font flipping
		}
		poseStack.translate(0.5, -0.5, 0.5);

		List<Integer> slotCounts = blockEntity.getSlotCounts();
		float countDisplayYOffset = -(slotCounts.size() == 1 ? 0.25f : 0.11f);
		for (int displayItemIndex = 0; displayItemIndex < slotCounts.size(); displayItemIndex++) {
			int count = slotCounts.get(displayItemIndex);
			if (count <= 0) {
				continue;
			}

			poseStack.pushPose();
			Vector3f frontOffset = DisplayItemRenderer.getDisplayItemIndexFrontOffset(displayItemIndex, slotCounts.size());

			double xTranslation = -frontOffset.x();
			float yTranslation = frontOffset.y() + countDisplayYOffset;
			double zTranslation = 0.001 - (flatTop ? 0 : 0.75 / 16D);
			poseStack.translate(xTranslation, yTranslation, zTranslation);

			float scale = slotCounts.size() == 1 ? SINGLE_ITEM_FONT_SCALE : MULTIPLE_ITEMS_FONT_SCALE;
			poseStack.scale(scale, -scale, scale);
			Font font = Minecraft.getInstance().font;
			CountLabel label = getCountLabel(font, count, slotCounts.size() == 1 ? 6 : 5);
			poseStack.translate(-label.width() / 2f, 0, 0);
			font.drawInBatch(label.text(), 0, 0, blockEntity.getSlotColor(displayItemIndex), false, poseStack.last().pose(), bufferSource, false, 0, packedLight);

			poseStack.popPose();
		}
		poseStack.popPose();
	}

public static void clearCountCache() {
		COUNT_LABELS.invalidateAll();
		cachedFont = null;
	}

static CountLabel getCountLabel(Font font, int count, int maxCharacters) {
		if (cachedFont != font) {
			clearCountCache();
			cachedFont = font;
		}
		long key = ((long) maxCharacters << 32) | (count & 0xFFFFFFFFL);
		CountLabel label = COUNT_LABELS.getIfPresent(key);
		if (label == null) {
			FormattedCharSequence text = new TextComponent(CountAbbreviator.abbreviate(count, maxCharacters)).withStyle(COUNT_DISPLAY_STYLE).getVisualOrderText();
			label = new CountLabel(text, font.getSplitter().stringWidth(text));
			COUNT_LABELS.put(key, label);
		}
		return label;
	}

public static void renderFillLevel(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, float fillLevel, float x, float y, boolean large, boolean translucentRender) {
		poseStack.pushPose();
		poseStack.translate(x + 1/16F/5F, y + 1/16F/5F, 0);
		int barHeight = large ? 14 : 6;
		poseStack.scale(1 / 16F / 5F * 3, fillLevel * 1 / 16F / 5F * (barHeight * 5 - 2), 1);
		VertexConsumer vertexConsumer;
		if (translucentRender) {
			//noinspection resource
			TextureAtlasSprite sprite = FILL_INDICATORS_TEXTURE.sprite();
			vertexConsumer = sprite.wrap(bufferSource.getBuffer(RenderType.entityTranslucent(sprite.atlas().location())));
		} else {
			vertexConsumer = FILL_INDICATORS_TEXTURE.buffer(bufferSource, RenderType::entityCutoutNoCull);
		}
		PoseStack.Pose pose = poseStack.last();
		Vector4f position = new Vector4f();
		Vector3f normal = new Vector3f(0, 1, 0);
		normal.transform(pose.normal());
		float minU = large ? 0 : 3 / 128F;
		float maxV = large ? 68 / 128F : 28 / 128F;
		float maxU = minU + 3 / 128F;
		float minV = (1 - fillLevel) * maxV;
		float alpha = translucentRender ? 0.5F : 1;
		renderFillVertex(vertexConsumer, pose, position, normal, 0, 1, maxU, minV, alpha, packedOverlay, packedLight);
		renderFillVertex(vertexConsumer, pose, position, normal, 0, 0, maxU, maxV, alpha, packedOverlay, packedLight);
		renderFillVertex(vertexConsumer, pose, position, normal, 1, 0, minU, maxV, alpha, packedOverlay, packedLight);
		renderFillVertex(vertexConsumer, pose, position, normal, 1, 1, minU, minV, alpha, packedOverlay, packedLight);

		poseStack.popPose();
	}

static void renderFillVertex(VertexConsumer consumer, PoseStack.Pose pose, Vector4f position, Vector3f normal, float x, float y, float u, float v, float alpha, int packedOverlay, int packedLight) {
		position.set(x, y, 0, 1);
		position.transform(pose.pose());
		consumer.vertex(position.x(), position.y(), position.z());
		consumer.color(1, 1, 1, alpha);
		consumer.uv(u, v);
		consumer.overlayCoords(packedOverlay);
		consumer.uv2(packedLight);
		consumer.normal(normal.x(), normal.y(), normal.z());
		consumer.endVertex();
	}
record CountLabel(FormattedCharSequence text, float width) {}
}
