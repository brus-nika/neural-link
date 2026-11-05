package net.neurallink.control;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

/** operator input and per-tick memory for the session. */
public final class ControlState {
	public static final int SLOTS = 3;

	public float forward;
	public float sideways;
	public boolean jump;
	public boolean sneak;
	public float yaw;
	public float pitch;

	float smoothForward;
	float smoothSideways;
	float bodyYaw;
	float glideSpeed = Movement.GLIDE_START_SPEED;
	public boolean phasing;
	boolean flying;
	public boolean frozen;
	public boolean shielded;
	boolean collecting;
	ItemStack collectedHand;
	int stungTicks;
	int waterTicks;
	int hopCooldown;
	int thrustTicks;
	int jetTicks;
	boolean crossbowShot;
	int attackPoseTicks;

	final long[] readyAt = new long[SLOTS];
	final int[] lastPress = new int[SLOTS];
	final int[] heldSince = new int[SLOTS];
	final boolean[] held = new boolean[SLOTS];
	private final List<Scheduled> scheduled = new ArrayList<>();

	private static final class Scheduled {
		int delay;
		final Runnable action;

		Scheduled(int delay, Runnable action) {
			this.delay = delay;
			this.action = action;
		}
	}

	public ControlState(MobEntity mob) {
		this.yaw = mob.getYaw();
		this.pitch = mob.getPitch();
		this.bodyYaw = mob.getYaw();
	}

	/** Returns whether input changed, look included. */
	public boolean readInput(float newForward, float newSideways, boolean newJump, boolean newSneak, float newYaw, float newPitch) {
		float clampedForward = MathHelper.clamp(newForward, -1.0F, 1.0F);
		float clampedSideways = MathHelper.clamp(newSideways, -1.0F, 1.0F);
		float wrappedYaw = MathHelper.wrapDegrees(newYaw);
		float clampedPitch = MathHelper.clamp(newPitch, -90.0F, 90.0F);
		boolean changed = clampedForward != forward || clampedSideways != sideways || newJump != jump || newSneak != sneak || wrappedYaw != yaw || clampedPitch != pitch;
		forward = clampedForward;
		sideways = clampedSideways;
		jump = newJump;
		sneak = newSneak;
		yaw = wrappedYaw;
		pitch = clampedPitch;
		return changed;
	}

	/** for a new mob after conversion: old cooldowns don't carry over. */
	public void resetFor(MobEntity mob) {
		bodyYaw = mob.getYaw();
		glideSpeed = Movement.GLIDE_START_SPEED;
		phasing = false;
		flying = false;
		frozen = false;
		shielded = false;
		collecting = false;
		collectedHand = null;
		stungTicks = 0;
		waterTicks = 0;
		hopCooldown = 0;
		thrustTicks = 0;
		jetTicks = 0;
		crossbowShot = false;
		attackPoseTicks = 0;
		scheduled.clear();
		for (int slot = 0; slot < SLOTS; slot++) {
			readyAt[slot] = 0;
			lastPress[slot] = 0;
			held[slot] = false;
		}
	}

	public long[] cooldowns() {
		return readyAt.clone();
	}

	public void restoreCooldowns(long[] saved) {
		System.arraycopy(saved, 0, readyAt, 0, SLOTS);
	}

	void schedule(int delay, Runnable action) {
		scheduled.add(new Scheduled(delay, action));
	}

	public int scheduledCount() {
		return scheduled.size();
	}

	public void tickScheduled() {
		for (Iterator<Scheduled> it = new ArrayList<>(scheduled).iterator(); it.hasNext();) {
			Scheduled task = it.next();
			if (--task.delay <= 0) {
				scheduled.remove(task);
				task.action.run();
			}
		}
	}
}
