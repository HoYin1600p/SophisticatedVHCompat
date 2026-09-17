package dev.hoyin1600p.sophisticated_vh_compat.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import net.minecraft.SharedConstants;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BarrelRenderCompatTest {
	@BeforeAll
	static void setup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@AfterEach
	void clearCache() {
		BarrelRenderCompat.clearCountCache();
	}

	@Test
	void countLabelsReuseLayoutAndInvalidateForFontChangesAndReloads() {
		Font font = mock(Font.class);
		StringSplitter splitter = mock(StringSplitter.class);
		when(font.getSplitter()).thenReturn(splitter);
		when(splitter.stringWidth(any(FormattedCharSequence.class))).thenReturn(12.5f);
		var label = BarrelRenderCompat.getCountLabel(font, 100000, 6);
		assertEquals(12.5f, label.width());
		assertSame(label, BarrelRenderCompat.getCountLabel(font, 100000, 6));
		verify(splitter).stringWidth(any(FormattedCharSequence.class));
		assertNotSame(label, BarrelRenderCompat.getCountLabel(font, 100000, 5));
		Font changedFont = mock(Font.class);
		when(changedFont.getSplitter()).thenReturn(splitter);
		assertNotSame(label, BarrelRenderCompat.getCountLabel(changedFont, 100000, 6));
		var beforeReload = BarrelRenderCompat.getCountLabel(font, 100000, 6);
		BarrelRenderCompat.clearCountCache();
		assertNotSame(beforeReload, BarrelRenderCompat.getCountLabel(font, 100000, 6));
	}

	@ParameterizedTest
	@ValueSource(floats = {0.25f, 0.5f, 1f})
	void fillVerticesPreserveTransformAndSpriteWrapper(float fillLevel) {
		PoseStack poses = new PoseStack();
		poses.translate(0.5, 0.5, 0.5);
		poses.mulPose(Vector3f.YP.rotationDegrees(90));
		poses.scale(3 / 80f, fillLevel * 68 / 80f, 1);
		VertexConsumer consumer = mock(VertexConsumer.class, CALLS_REAL_METHODS);
		VertexConsumer underlying = mock(VertexConsumer.class);
		when(consumer.vertex(anyDouble(), anyDouble(), anyDouble())).thenReturn(underlying);
		when(consumer.color(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(underlying);
		when(consumer.uv(anyFloat(), anyFloat())).thenReturn(underlying);
		when(consumer.normal(anyFloat(), anyFloat(), anyFloat())).thenReturn(underlying);
		Vector3f normal = new Vector3f(0, 1, 0);
		normal.transform(poses.last().normal());
		BarrelRenderCompat.renderFillVertex(consumer, poses.last(), new Vector4f(), normal, 0, 1, 3 / 128f, 0, 0.5f, 7, 9);
		verify(consumer).uv(3 / 128f, 0);
		verify(consumer).normal(normal.x(), normal.y(), normal.z());
		verify(consumer).overlayCoords(7);
		verify(consumer).uv2(9);
		verify(consumer).endVertex();
		verifyNoInteractions(underlying);
	}
}
