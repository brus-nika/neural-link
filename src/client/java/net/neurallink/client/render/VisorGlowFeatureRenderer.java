package net.neurallink.client.render;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.TriState;
import net.minecraft.util.math.ColorHelper;
import net.neurallink.NeuralLink;
import net.neurallink.client.gui.VisorTheme;
import net.neurallink.registry.ModItems;

/**
 * trance visor glows in the highlight colour: glass redrawn over the helmet at full light, visible in the dark and under
 * shaders.
 */
public class VisorGlowFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
	/** A tenth of a texture pixel off the helmet, enough to win the depth test. */
	private static final float GLASS_LIFT = 1.01F;
	/**
	 * vanilla eyes layer, but additive (1.21.4's blends translucent and would darken the glass) and with the armor view
	 * offset, so the lift holds at any distance.
	 */
	private static final RenderLayer GLOW = RenderLayer.of("neurallink_visor_glow", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
			VertexFormat.DrawMode.QUADS, 1536, false, true, RenderLayer.MultiPhaseParameters.builder()
					.program(RenderPhase.EYES_PROGRAM)
					.texture(new RenderPhase.Texture(NeuralLink.id("textures/entity/body/visor_glow.png"), TriState.FALSE, false))
					.transparency(RenderPhase.ADDITIVE_TRANSPARENCY)
					.writeMaskState(RenderPhase.COLOR_MASK)
					.layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
					.build(false));

	private final ArmorEntityModel<PlayerEntityRenderState> helmet;

	public VisorGlowFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context, ArmorEntityModel<PlayerEntityRenderState> helmet) {
		super(context);
		this.helmet = helmet;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
		if (!(state instanceof BodyRenderState body) || body.visorGlow <= 0.0F || !state.equippedHeadStack.isOf(ModItems.NEURAL_HELMET)) {
			return; // pumpkin or other helmet: no visor
		}
		getContextModel().copyTransforms(helmet);
		helmet.setVisible(false);
		helmet.head.visible = true;
		helmet.head.xScale = GLASS_LIFT;
		helmet.head.yScale = GLASS_LIFT;
		helmet.head.zScale = GLASS_LIFT;
		// Additive: brightness scales the colour, 0 adds nothing.
		int color = ColorHelper.getArgb(255, Math.round(ColorHelper.getRed(VisorTheme.HIGHLIGHT) * body.visorGlow),
				Math.round(ColorHelper.getGreen(VisorTheme.HIGHLIGHT) * body.visorGlow), Math.round(ColorHelper.getBlue(VisorTheme.HIGHLIGHT) * body.visorGlow));
		helmet.render(matrices, vertices.getBuffer(GLOW), LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, color);
	}
}
