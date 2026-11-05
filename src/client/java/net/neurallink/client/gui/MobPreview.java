package net.neurallink.client.gui;

import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Throwables;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

/** small spinning mob model for the menu; the entity never joins a world. */
public final class MobPreview {
	private static final float SPIN_RADIANS_PER_MS = 0.0012F;
	private static final int MIN_PHASE_TICKS = 50;
	private static final int MAX_EXTRA_PHASE_TICKS = 70;
	private static final int ATTACK_TICKS = 16;
	private static final float WALK_SPEED = 0.6F;
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Set<EntityType<?>> BROKEN = new HashSet<>();

	private enum Phase {
		IDLE, WALK, ATTACK
	}

	private final LivingEntity entity;
	private final float spinOffset;
	private Phase phase = Phase.IDLE;
	private int phaseTicks;
	private Identifier held;

	private MobPreview(LivingEntity entity, float spinOffset, int firstPhaseTicks) {
		this.entity = entity;
		this.spinOffset = spinOffset;
		this.phaseTicks = firstPhaseTicks;
	}

	/** Null if the type isn't living or available, then the menu draws an icon. */
	static MobPreview create(EntityType<?> type, Random random) {
		if (BROKEN.contains(type)) {
			return null;
		}
		try {
			Entity created = type.create(MinecraftClient.getInstance().world, SpawnReason.LOAD);
			if (!(created instanceof LivingEntity living)) {
				return null;
			}
			return new MobPreview(living, random.nextFloat() * MathHelper.TAU, random.nextInt(MIN_PHASE_TICKS));
		} catch (RuntimeException e) {
			markBroken(type, e);
			return null;
		}
	}

	/** mods run here on a worldless entity: a failure must cost an icon, not the game. */
	private static void markBroken(EntityType<?> type, RuntimeException e) {
		if (BROKEN.add(type)) {
			LOGGER.info("No menu preview for {}: {}", EntityType.getId(type), Throwables.getRootCause(e).toString());
		}
	}

	EntityType<?> type() {
		return entity.getType();
	}

	/** holds what the real host holds, from the card. */
	void hold(Identifier item) {
		if (!item.equals(held) && entity instanceof MobEntity mob) {
			held = item;
			mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Registries.ITEM.get(item)));
		}
	}

	void tick(Random random) {
		entity.age++;
		if (--phaseTicks <= 0) {
			phase = Phase.values()[random.nextInt(Phase.values().length)];
			phaseTicks = phase == Phase.ATTACK ? ATTACK_TICKS : MIN_PHASE_TICKS + random.nextInt(MAX_EXTRA_PHASE_TICKS);
		}
		entity.limbAnimator.updateLimbs(phase == Phase.WALK ? WALK_SPEED : 0.0F, 0.4F, 1.0F);
		if (entity instanceof MobEntity mob) {
			mob.setAttacking(phase == Phase.ATTACK);
		}
		entity.lastHandSwingProgress = entity.handSwingProgress;
		entity.handSwingProgress = phase == Phase.ATTACK ? 1.0F - (float) phaseTicks / ATTACK_TICKS : 0.0F;
	}

	/** Double on purpose: a float clock gets jerky after days of OS uptime. */
	public static float spin(long timeMs, float offset) {
		return (float) ((timeMs * (double) SPIN_RADIANS_PER_MS) % MathHelper.TAU) + offset;
	}

	/** centerX and centerY are the box middle. False means draw an icon instead. */
	boolean render(DrawContext context, int centerX, int centerY, int box) {
		float size = Math.max(entity.getHeight(), entity.getWidth());
		float scale = box / size;
		Quaternionf rotation = new Quaternionf().rotateZ(MathHelper.PI).rotateY(spin(Util.getMeasuringTimeMs(), spinOffset));
		Vector3f offset = new Vector3f(0.0F, entity.getHeight() / 2.0F, 0.0F);
		MatrixStack matrices = context.getMatrices();
		MatrixStack.Entry top = matrices.peek();
		try {
			InventoryScreen.drawEntity(context, centerX, centerY, scale, offset, rotation, null, entity);
			return true;
		} catch (RuntimeException e) {
			// drawEntity restores these only on its normal path
			while (matrices.peek() != top) {
				matrices.pop();
			}
			MinecraftClient.getInstance().getEntityRenderDispatcher().setRenderShadows(true);
			DiffuseLighting.enableGuiDepthLighting();
			markBroken(entity.getType(), e);
			return false;
		}
	}
}
