package net.neurallink.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.neurallink.entity.BodyEntity;

/**
 * A body looks like its player (skin, model type). Slim arms need a separate renderer for their own armor. With bodyTrance
 * on: {@link BodyModel}, {@link VisorGlowFeatureRenderer}.
 */
public class BodyEntityRenderer extends BipedEntityRenderer<BodyEntity, PlayerEntityRenderState, PlayerEntityModel> {
	private static final int PULSE_TICKS = 40;
	private static final int BLINK_TICKS = 2;

	private final BodyEntityRenderer slim;

	public BodyEntityRenderer(EntityRendererFactory.Context context) {
		this(context, false, new BodyEntityRenderer(context, true, null));
	}

	private BodyEntityRenderer(EntityRendererFactory.Context context, boolean thinArms, BodyEntityRenderer slim) {
		super(context, new BodyModel(context.getPart(thinArms ? EntityModelLayers.PLAYER_SLIM : EntityModelLayers.PLAYER), thinArms), 0.5F);
		this.slim = slim;
		this.addFeature(new ArmorFeatureRenderer<>(this,
				new ArmorEntityModel<>(context.getPart(thinArms ? EntityModelLayers.PLAYER_SLIM_INNER_ARMOR : EntityModelLayers.PLAYER_INNER_ARMOR)),
				new ArmorEntityModel<>(context.getPart(thinArms ? EntityModelLayers.PLAYER_SLIM_OUTER_ARMOR : EntityModelLayers.PLAYER_OUTER_ARMOR)),
				context.getEquipmentRenderer()));
		this.addFeature(new VisorGlowFeatureRenderer(this,
				new ArmorEntityModel<>(context.getPart(thinArms ? EntityModelLayers.PLAYER_SLIM_OUTER_ARMOR : EntityModelLayers.PLAYER_OUTER_ARMOR))));
		this.addFeature(new CapeFeatureRenderer(this, context.getEntityModels(), context.getEquipmentModelLoader()));
	}

	@Override
	public PlayerEntityRenderState createRenderState() {
		return new BodyRenderState();
	}

	@Override
	public void updateRenderState(BodyEntity body, PlayerEntityRenderState state, float tickDelta) {
		super.updateRenderState(body, state, tickDelta);
		if (state instanceof BodyRenderState trance) {
			float linear = body.trance(tickDelta);
			trance.trance = linear * linear * (3.0F - 2.0F * linear); // eases out
			trance.visorGlow = trance.trance * visorPulse(body, tickDelta);
		}
		state.skinTextures = skin(body);
		state.hatVisible = body.isPartVisible(PlayerModelPart.HAT);
		state.jacketVisible = body.isPartVisible(PlayerModelPart.JACKET);
		state.leftPantsLegVisible = body.isPartVisible(PlayerModelPart.LEFT_PANTS_LEG);
		state.rightPantsLegVisible = body.isPartVisible(PlayerModelPart.RIGHT_PANTS_LEG);
		state.leftSleeveVisible = body.isPartVisible(PlayerModelPart.LEFT_SLEEVE);
		state.rightSleeveVisible = body.isPartVisible(PlayerModelPart.RIGHT_SLEEVE);
		state.capeVisible = body.isPartVisible(PlayerModelPart.CAPE);
	}

	/** soft 2 s pulse; blinks while the hurt tint lasts. */
	static float visorPulse(BodyEntity body, float tickDelta) {
		if (body.hurtTime > 0) {
			return body.hurtTime / BLINK_TICKS % 2 == 0 ? 1.0F : 0.1F;
		}
		float phase = (body.getWorld().getTime() % PULSE_TICKS + tickDelta) / PULSE_TICKS * MathHelper.TAU;
		return 0.7F + 0.3F * MathHelper.sin(phase);
	}

	@Override
	public Vec3d getPositionOffset(PlayerEntityRenderState state) {
		Vec3d offset = super.getPositionOffset(state);
		return state.isInSneakingPose ? offset.add(0.0, state.baseScale * -2.0F / 16.0, 0.0) : offset;
	}

	@Override
	protected BipedEntityModel.ArmPose getArmPose(BodyEntity body, Arm arm) {
		BipedEntityModel.ArmPose main = armPose(body, body.getMainHandStack());
		BipedEntityModel.ArmPose off = armPose(body, body.getOffHandStack());
		if (main.isTwoHanded()) {
			off = body.getOffHandStack().isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
		}
		return body.getMainArm() == arm ? main : off;
	}

	/** The body never uses items so only idle poses apply. */
	private static BipedEntityModel.ArmPose armPose(BodyEntity body, ItemStack stack) {
		if (stack.isEmpty()) {
			return BipedEntityModel.ArmPose.EMPTY;
		}
		return !body.handSwinging && stack.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(stack) ? BipedEntityModel.ArmPose.CROSSBOW_HOLD : BipedEntityModel.ArmPose.ITEM;
	}

	@Override
	protected void scale(PlayerEntityRenderState state, MatrixStack matrices) {
		matrices.scale(0.9375F, 0.9375F, 0.9375F);
	}

	/** BipedEntityRenderer doesn't lean, so a crawling body would render upright. Numbers from PlayerEntityRenderer. */
	@Override
	protected void setupTransforms(PlayerEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseHeight) {
		super.setupTransforms(state, matrices, bodyYaw, baseHeight);
		if (state.leaningPitch > 0.0F) {
			float lean = state.touchingWater ? -90.0F - state.pitch : -90.0F;
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(state.leaningPitch, 0.0F, lean)));
			if (state.isSwimming) {
				matrices.translate(0.0F, -1.0F, 0.3F);
			}
		}
	}

	private static SkinTextures skin(BodyEntity body) {
		return body.getOwner().map(owner -> {
			ClientPlayNetworkHandler network = MinecraftClient.getInstance().getNetworkHandler();
			PlayerListEntry entry = network == null ? null : network.getPlayerListEntry(owner);
			return entry != null ? entry.getSkinTextures() : DefaultSkinHelper.getSkinTextures(owner);
		}).orElseGet(DefaultSkinHelper::getSteve);
	}

	@Override
	public void render(PlayerEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		if (this.slim != null && state.skinTextures.model() == SkinTextures.Model.SLIM) {
			this.slim.render(state, matrices, vertices, light);
		} else {
			super.render(state, matrices, vertices, light);
		}
	}

	@Override
	public Identifier getTexture(PlayerEntityRenderState state) {
		return state.skinTextures.texture();
	}
}
