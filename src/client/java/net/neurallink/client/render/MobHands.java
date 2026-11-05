package net.neurallink.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * first person in a session shows the mob's held items. Vanilla renderFirstPersonItem takes only a player, so its transforms
 * are copied; item models keep other mods working.
 */
public final class MobHands {
	private static final int ATTACK_RECOVERY_TICKS = 20;
	private static final float EQUIP_STEP = 0.4F;
	private static final float LOOK_FOLLOW = 0.5F;

	private static float equipProgress = 1.0F;
	private static float lastEquip = 1.0F;
	private static ItemStack lastMainHand = ItemStack.EMPTY;
	private static int ticksSinceSwing = ATTACK_RECOVERY_TICKS;
	private static boolean wasSwinging;
	private static int drinkTicks;

	private MobHands() {
	}

	/** Like a player: item dips on hit, rises back, lowers on change. */
	public static void tick(LivingEntity mob) {
		trackLook(MinecraftClient.getInstance().player);
		drinkTicks = mob instanceof WitchEntity witch && witch.isDrinking() ? drinkTicks + 1 : 0;
		lastEquip = equipProgress;
		if (mob.handSwinging && !wasSwinging) {
			ticksSinceSwing = 0;
		}
		wasSwinging = mob.handSwinging;
		ticksSinceSwing++;
		ItemStack main = mob.getMainHandStack();
		float recovery = Math.min(1.0F, (float) ticksSinceSwing / ATTACK_RECOVERY_TICKS);
		float target = ItemStack.areEqual(lastMainHand, main) ? recovery * recovery * recovery : 0.0F;
		equipProgress += MathHelper.clamp(target - equipProgress, -EQUIP_STEP, EQUIP_STEP);
		if (equipProgress < 0.1F) {
			lastMainHand = main.copy();
		}
	}

	public static void render(HeldItemRenderer renderer, float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertices, int light) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!(client.getCameraEntity() instanceof LivingEntity mob)) {
			return;
		}
		light = client.getEntityRenderDispatcher().getLight(mob, tickDelta); // the arg lights at the local player, left at session start
		matrices.push();
		applyLookLag(client.player, tickDelta, matrices);
		float swing = mob.getHandSwingProgress(tickDelta);
		Arm mainArm = mob.getMainArm();
		float equip = MathHelper.lerp(tickDelta, lastEquip, equipProgress);
		renderHand(renderer, mob, Hand.MAIN_HAND, mainArm, swing, equip, tickDelta, matrices, vertices, light);
		renderHand(renderer, mob, Hand.OFF_HAND, mainArm.getOpposite(), 0.0F, 1.0F, tickDelta, matrices, vertices, light);
		matrices.pop();
		vertices.draw();
	}

	/** vanilla smooths look only with the camera on the player; otherwise the item drifts away for good. */
	private static void trackLook(ClientPlayerEntity player) {
		player.lastRenderYaw = player.renderYaw;
		player.lastRenderPitch = player.renderPitch;
		player.renderPitch += (player.getPitch() - player.renderPitch) * LOOK_FOLLOW;
		player.renderYaw += (player.getYaw() - player.renderYaw) * LOOK_FOLLOW;
	}

	private static void applyLookLag(ClientPlayerEntity player, float tickDelta, MatrixStack matrices) {
		float pitch = MathHelper.lerp(tickDelta, player.lastRenderPitch, player.renderPitch);
		float yaw = MathHelper.lerp(tickDelta, player.lastRenderYaw, player.renderYaw);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((player.getPitch(tickDelta) - pitch) * 0.1F));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((player.getYaw(tickDelta) - yaw) * 0.1F));
	}

	private static void renderHand(HeldItemRenderer renderer, LivingEntity mob, Hand hand, Arm arm, float swing, float equip, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertices, int light) {
		ItemStack stack = mob.getStackInHand(hand);
		if (stack.isEmpty()) {
			return;
		}
		boolean right = arm == Arm.RIGHT;
		float side = right ? 1.0F : -1.0F;
		matrices.push();
		if (drinkTicks > 0 && hand == Hand.MAIN_HAND) {
			// Witches drink without item use so time is counted here.
			int max = stack.getMaxUseTime(mob);
			applyEating(Math.max(0.0F, max - drinkTicks - tickDelta + 1.0F), max, side, matrices);
			applyHandPosition(side, 1.0F, matrices);
		} else if (mob.isUsingItem() && mob.getItemUseTimeLeft() > 0 && mob.getActiveHand() == hand) {
			applyUse(mob, stack, side, equip, tickDelta, matrices);
		} else {
			applySwing(side, swing, equip, matrices);
			if (stack.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(stack) && swing < 0.001F && hand == Hand.MAIN_HAND) {
				matrices.translate(side * -0.641864F, 0.0F, 0.0F);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 10.0F));
			}
		}
		ModelTransformationMode mode = right ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
		renderer.renderItem(mob, stack, mode, !right, matrices, vertices, light);
		matrices.pop();
	}

	private static void applyHandPosition(float side, float equip, MatrixStack matrices) {
		matrices.translate(side * 0.56F, -0.52F + (1.0F - equip) * -0.6F, -0.72F);
	}

	private static void applySwing(float side, float swing, float equip, MatrixStack matrices) {
		float root = MathHelper.sqrt(swing);
		matrices.translate(side * -0.4F * MathHelper.sin(root * MathHelper.PI), 0.2F * MathHelper.sin(root * MathHelper.TAU), -0.2F * MathHelper.sin(swing * MathHelper.PI));
		applyHandPosition(side, equip, matrices);
		float arc = MathHelper.sin(swing * swing * MathHelper.PI);
		float lift = MathHelper.sin(root * MathHelper.PI);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45.0F + arc * -20.0F)));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * lift * -20.0F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(lift * -80.0F));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
	}

	private static void applyUse(LivingEntity mob, ItemStack stack, float side, float equip, float tickDelta, MatrixStack matrices) {
		float ticks = stack.getMaxUseTime(mob) - (mob.getItemUseTimeLeft() - tickDelta + 1.0F);
		UseAction action = stack.getUseAction();
		if (action == UseAction.BOW) {
			applyHandPosition(side, equip, matrices);
			matrices.translate(side * -0.2785682F, 0.18344387F, 0.15731531F);
			rotate(matrices, side, -13.935F, 35.3F);
			float pull = ticks / 20.0F;
			applyPull(side, ticks, Math.min(1.0F, (pull * pull + pull * 2.0F) / 3.0F), 0.04F, matrices);
		} else if (action == UseAction.CROSSBOW) {
			applyHandPosition(side, equip, matrices);
			matrices.translate(side * -0.4785682F, -0.094387F, 0.05731531F);
			rotate(matrices, side, -11.935F, 65.3F);
			applyPull(side, ticks, Math.min(1.0F, ticks / CrossbowItem.getPullTime(stack, mob)), 0.04F, matrices);
		} else if (action == UseAction.SPEAR) {
			applyHandPosition(side, equip, matrices);
			matrices.translate(side * -0.5F, 0.7F, 0.1F);
			rotate(matrices, side, -55.0F, 35.3F);
			applyPull(side, ticks, Math.min(1.0F, ticks / 10.0F), 0.2F, matrices);
		} else if (action == UseAction.EAT || action == UseAction.DRINK) {
			applyEating(mob.getItemUseTimeLeft() - tickDelta + 1.0F, stack.getMaxUseTime(mob), side, matrices);
			applyHandPosition(side, equip, matrices);
		} else {
			applyHandPosition(side, equip, matrices);
		}
	}

	private static void rotate(MatrixStack matrices, float side, float pitch, float yaw) {
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * yaw));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -9.785F));
	}

	private static void applyPull(float side, float ticks, float pull, float push, MatrixStack matrices) {
		if (pull > 0.1F) {
			matrices.translate(0.0F, MathHelper.sin((ticks - 0.1F) * 1.3F) * (pull - 0.1F) * 0.004F, 0.0F);
		}
		matrices.translate(0.0F, 0.0F, pull * push);
		matrices.scale(1.0F, 1.0F, 1.0F + pull * 0.2F);
		matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(side * 45.0F));
	}

	private static void applyEating(float left, int max, float side, MatrixStack matrices) {
		float progress = left / max;
		if (progress < 0.8F) {
			matrices.translate(0.0F, MathHelper.abs(MathHelper.cos(left / 4.0F * MathHelper.PI) * 0.1F), 0.0F);
		}
		float raise = 1.0F - (float) Math.pow(progress, 27.0);
		matrices.translate(raise * 0.6F * side, raise * -0.5F, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * raise * 90.0F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(raise * 10.0F));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * raise * 30.0F));
	}
}
