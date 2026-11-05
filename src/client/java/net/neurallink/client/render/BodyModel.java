package net.neurallink.client.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.MathHelper;

/**
 * player model with trance: the head sinks, applied after vanilla's angles in model space so lying bodies and an upside-down
 * Dinnerbone still drop the chin to the chest.
 */
public class BodyModel extends PlayerEntityModel {
	/** Chin on chest. */
	private static final float SLUMP = 0.9F;
	/** lying: face down, not lifted like a swimmer. */
	private static final float SLUMP_PRONE = 0.0F;

	public BodyModel(ModelPart root, boolean thinArms) {
		super(root, thinArms);
	}

	@Override
	public void setAngles(PlayerEntityRenderState state) {
		super.setAngles(state);
		if (state instanceof BodyRenderState body && body.trance > 0.0F) {
			float down = MathHelper.lerp(state.leaningPitch, SLUMP, SLUMP_PRONE);
			head.pitch = MathHelper.lerp(body.trance, head.pitch, Math.max(head.pitch, down)); // a lower head stays
			head.yaw = MathHelper.lerp(body.trance, head.yaw, 0.0F);
		}
	}
}
