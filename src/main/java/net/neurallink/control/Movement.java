package net.neurallink.control;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.mob.CreakingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.ArmadilloEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.neurallink.mixin.SquidEntityAccessor;

/** Tuned so each mob feels vanilla. */
public enum Movement {
	WALK {
		@Override
		void apply(MobEntity mob, ControlState state) {
			walk(mob, state, 1.0F);
		}
	},
	/** villager and trader: vanilla AI halves the attribute. */
	CALM {
		@Override
		void apply(MobEntity mob, ControlState state) {
			walkAt(mob, state, 0.25F, 1.0F);
		}
	},
	/** Evoker and illusioner: attribute 0.5 at AI multiplier 0.6. */
	CASTER {
		@Override
		void apply(MobEntity mob, ControlState state) {
			walkAt(mob, state, 0.3F, 1.0F);
		}
	},
	/** Breeze: vanilla AI multiplier 0.6. */
	BREEZE {
		@Override
		void apply(MobEntity mob, ControlState state) {
			walkAt(mob, state, 0.38F, 1.0F);
		}
	},
	CLIMB {
		@Override
		void apply(MobEntity mob, ControlState state) {
			walk(mob, state, 1.0F);
			if (mob.isClimbing()) {
				double climb = state.jump ? 0.2 : state.sneak ? -0.15 : 0.0;
				mob.setVelocity(mob.getVelocity().x, climb, mob.getVelocity().z);
			}
		}
	},
	LAVA {
		@Override
		void apply(MobEntity mob, ControlState state) {
			boolean onLava = mob.isInLava() || mob.getWorld().getFluidState(mob.getBlockPos().down()).isIn(FluidTags.LAVA);
			walk(mob, state, onLava ? 1.6F : 0.5F);
		}
	},
	HOP {
		@Override
		void apply(MobEntity mob, ControlState state) {
			face(mob, state, TURN_FAST);
			stopWalking(mob);
			state.hopCooldown = Math.max(0, state.hopCooldown - 1);
			if (state.jump && mob.isOnGround() && state.hopCooldown == 0) {
				int size = mob instanceof SlimeEntity slime ? slime.getSize() : 1;
				mob.jump();
				Vec3d look = Vec3d.fromPolar(0.0F, state.yaw);
				mob.addVelocity(look.multiply(0.2 + 0.08 * size));
				mob.velocityModified = true;
				state.hopCooldown = 12 + 4 * size;
			}
		}
	},
	SKIP {
		@Override
		void apply(MobEntity mob, ControlState state) {
			face(mob, state, TURN_FAST);
			stopWalking(mob);
			state.hopCooldown = Math.max(0, state.hopCooldown - 1);
			Vec3d direction = planar(state);
			if ((direction.lengthSquared() > 0.0 || state.jump) && mob.isOnGround() && state.hopCooldown == 0) {
				mob.jump();
				mob.addVelocity(direction.multiply(0.3));
				mob.velocityModified = true;
				state.hopCooldown = 6;
			}
		}
	},
	GLIDE(true) {
		@Override
		void apply(MobEntity mob, ControlState state) {
			state.bodyYaw = MathHelper.stepUnwrappedAngleTowards(state.bodyYaw, state.yaw, TURN_HEAVY);
			state.glideSpeed += MathHelper.sin(state.pitch * MathHelper.RADIANS_PER_DEGREE) * 0.03F - 0.002F;
			state.glideSpeed = MathHelper.clamp(state.glideSpeed, 0.25F, GLIDE_MAX_SPEED);
			mob.setYaw(state.bodyYaw);
			mob.setHeadYaw(state.bodyYaw);
			mob.setBodyYaw(state.bodyYaw);
			mob.setPitch(-state.pitch); // the phantom stores pitch inverted
			stopWalking(mob);
			mob.setVelocity(Vec3d.fromPolar(state.pitch, state.bodyYaw).multiply(state.glideSpeed));
			syncSteering(mob);
		}
	},
	/** Ghast: drag leaves 0.38 of speed, giving vanilla 0.25 blocks per tick. */
	HOVER(true) {
		@Override
		void apply(MobEntity mob, ControlState state) {
			fly(mob, state, 0.66, 0.06);
		}
	},
	/** tickMovement damps lift, so vertical input is scaled up. */
	WITHER(true) {
		@Override
		void apply(MobEntity mob, ControlState state) {
			if (mob instanceof WitherEntity wither) {
				BossAbilities.tickWither(wither);
			}
			steer(mob, state, input3d(state).multiply(0.66, 2.5, 0.66), 0.06, TURN_HEAVY);
		}
	},
	/** The dragon flies toward a point set by EnderDragonEntityMixin; only lift is added here. */
	DRAGON(true) {
		@Override
		void apply(MobEntity mob, ControlState state) {
			if (mob instanceof EnderDragonEntity dragon && dragon.getPhaseManager().getCurrent().getType() != PhaseType.DYING) {
				dragon.getPhaseManager().setPhase(PhaseType.HOVER);
			}
			mob.addVelocity(0.0, -MathHelper.sin(state.pitch * MathHelper.RADIANS_PER_DEGREE) * DRAGON_CLIMB, 0.0);
		}
	},
	FLUTTER(true) {
		@Override
		void apply(MobEntity mob, ControlState state) {
			fly(mob, state, 0.28, 0.35);
		}
	},
	BAT(true) {
		@Override
		void apply(MobEntity mob, ControlState state) {
			if (mob instanceof BatEntity bat && bat.isRoosting()) {
				bat.setRoosting(false); // its AI normally wakes it
			}
			Vec3d jitter = new Vec3d(mob.getRandom().nextFloat() - 0.5F, mob.getRandom().nextFloat() - 0.5F, mob.getRandom().nextFloat() - 0.5F).multiply(0.12);
			Vec3d input = input3d(state);
			Vec3d direction = input.lengthSquared() > 0.0 ? input : Vec3d.fromPolar(state.pitch, state.yaw).multiply(0.6);
			steer(mob, state, direction.multiply(0.3).add(jitter), 0.5, TURN_FAST);
		}
	},
	/** on land vanilla flops and suffocates them. */
	FISH {
		@Override
		void apply(MobEntity mob, ControlState state) {
			face(mob, state, TURN_FAST);
			stopWalking(mob);
			if (mob.isTouchingWater()) {
				swim(mob, state, 0.3, 0.3);
			}
		}
	},
	/** Swims in pushes; the tentacle impulse needs swimVec or zeroes velocity. */
	SQUID {
		@Override
		void apply(MobEntity mob, ControlState state) {
			face(mob, state, TURN_FAST);
			stopWalking(mob);
			if (!(mob instanceof SquidEntity squid)) {
				return;
			}
			state.thrustTicks = Math.max(0, state.thrustTicks - 1);
			Vec3d look = Vec3d.fromPolar(state.pitch, state.yaw);
			if (state.jetTicks > 0) {
				state.jetTicks--;
				mob.setVelocity(look.multiply(JET_SPEED));
				mob.velocityModified = true;
				((SquidEntityAccessor) squid).neurallink$setSwimVec(look.multiply(JET_SPEED));
				return;
			}
			if (state.thrustTicks == 0 && state.forward > 0.0F && mob.isTouchingWater()) {
				state.thrustTicks = THRUST_PERIOD;
				mob.setVelocity(look.multiply(0.5));
				mob.velocityModified = true;
			}
			((SquidEntityAccessor) squid).neurallink$setSwimVec(state.forward > 0.0F ? look.multiply(0.3) : Vec3d.ZERO);
		}
	},
	SWIM {
		@Override
		void apply(MobEntity mob, ControlState state) {
			waterOrLand(mob, state, 0.22, LAND_CRAWL);
		}
	},
	DOLPHIN {
		@Override
		void apply(MobEntity mob, ControlState state) {
			waterOrLand(mob, state, 0.45, LAND_CRAWL);
			if (state.jump && mob.isTouchingWater() && mob.getWorld().getFluidState(mob.getBlockPos().up()).isEmpty()) {
				mob.addVelocity(0.0, 0.5, 0.0);
				mob.velocityModified = true;
			}
		}
	},
	TURTLE {
		@Override
		void apply(MobEntity mob, ControlState state) {
			waterOrLand(mob, state, 0.3, 0.14F);
		}
	},
	/** walks at vanilla flee speed, jumps only via its ability. */
	FROG {
		@Override
		void apply(MobEntity mob, ControlState state) {
			waterOrLand(mob, state, 0.3, 0.2F);
			mob.setJumping(false);
		}
	},
	ARMADILLO {
		@Override
		void apply(MobEntity mob, ControlState state) {
			if (mob instanceof ArmadilloEntity armadillo && armadillo.isNotIdle()) {
				stopWalking(mob);
				if (armadillo.shouldSwitchToScaredState()) {
					armadillo.setState(ArmadilloEntity.State.SCARED); // the brain normally does this
				}
			} else {
				walk(mob, state, 1.0F);
			}
		}
	},
	BLAZE {
		@Override
		void apply(MobEntity mob, ControlState state) {
			mob.setNoGravity(state.flying);
			if (state.flying) {
				fly(mob, state, 0.2, 0.2);
			} else {
				walk(mob, state, 1.0F);
			}
		}
	},
	IMMOBILE {
		@Override
		void apply(MobEntity mob, ControlState state) {
			mob.setHeadYaw(state.yaw);
			mob.setPitch(state.pitch);
			stopWalking(mob);
		}
	};

	static final float GLIDE_START_SPEED = 0.4F;
	static final float GLIDE_MAX_SPEED = 0.9F;
	private static final int THRUST_PERIOD = 24;
	private static final double JET_SPEED = 0.9;
	private static final double DRAGON_CLIMB = 0.04;
	private static final float LAND_CRAWL = 0.15F;
	private static final double SURFACE_DAMPING = 0.4;
	private static final double SURFACE_MARGIN = 0.3;
	private static final double MAX_RISE = 0.12;
	private static final double BANK_REACH = 0.5;
	private static final double BANK_CLEARANCE = 0.25;
	private static final double BANK_PROBE = 0.9;
	private static final float TURN_FAST = 45.0F;
	private static final float TURN_HEAVY = 6.0F;
	private static final float ACCEL_FAST = 0.45F;
	private static final float ACCEL_HEAVY = 0.12F;
	private static final float HEAVY_WIDTH = 1.3F;

	private final boolean flies;

	Movement() {
		this(false);
	}

	Movement(boolean flies) {
		this.flies = flies;
	}

	public boolean flies() {
		return flies;
	}

	/** Space and Shift move vertically now; the HUD shows them only then. */
	boolean vertical(MobEntity mob, ControlState state) {
		return switch (this) {
			case HOVER, WITHER, FLUTTER, BAT -> true;
			case FISH, SWIM, DOLPHIN, TURTLE, FROG -> mob.isTouchingWater();
			case CLIMB -> mob.isClimbing();
			case BLAZE -> state.flying;
			default -> false;
		};
	}

	abstract void apply(MobEntity mob, ControlState state);

	/** a frozen creaking stones whole, head too. */
	static void hold(MobEntity mob, ControlState state) {
		if (!(mob instanceof CreakingEntity)) {
			mob.setHeadYaw(state.yaw);
			mob.setPitch(state.pitch);
		}
		stopWalking(mob);
	}

	private static boolean heavy(MobEntity mob) {
		return mob.getWidth() >= HEAVY_WIDTH;
	}

	private static void face(MobEntity mob, ControlState state, float maxTurn) {
		state.bodyYaw = MathHelper.stepUnwrappedAngleTowards(state.bodyYaw, state.yaw, maxTurn);
		mob.setYaw(state.bodyYaw);
		mob.setBodyYaw(state.bodyYaw);
		mob.setHeadYaw(state.yaw);
		mob.setPitch(state.pitch);
	}

	private static void stopWalking(MobEntity mob) {
		mob.setMovementSpeed(0.0F);
		mob.setForwardSpeed(0.0F);
		mob.setSidewaysSpeed(0.0F);
		mob.setJumping(false);
	}

	private static void walk(MobEntity mob, ControlState state, float scale) {
		walkAt(mob, state, (float) mob.getAttributeValue(EntityAttributes.MOVEMENT_SPEED), scale);
	}

	/** For mobs whose attribute exceeds real speed, like the frog and axolotl. */
	private static void walkAt(MobEntity mob, ControlState state, float speed, float scale) {
		boolean heavy = heavy(mob);
		face(mob, state, heavy ? TURN_HEAVY : TURN_FAST);
		float accel = heavy ? ACCEL_HEAVY : ACCEL_FAST;
		state.smoothForward = MathHelper.lerp(accel, state.smoothForward, state.forward);
		state.smoothSideways = MathHelper.lerp(accel, state.smoothSideways, state.sideways);
		// as vanilla MoveControl: input equals attribute speed or the mob runs too fast.
		float length = MathHelper.sqrt(state.smoothForward * state.smoothForward + state.smoothSideways * state.smoothSideways);
		float input = speed * scale / Math.max(1.0F, length); // no faster on a diagonal
		// setMovementSpeed also sets forwardSpeed, so it goes first.
		mob.setMovementSpeed(speed);
		mob.setForwardSpeed(state.smoothForward * input);
		mob.setSidewaysSpeed(state.smoothSideways * input);
		boolean bank = mob.horizontalCollision && canClimbOut(mob, state);
		boolean floats = floats(mob) && !bank;
		boolean sinks = mob instanceof IronGolemEntity && !bank && mob.getFluidHeight(FluidTags.WATER) > mob.getSwimHeight();
		mob.setJumping(state.jump && !floats && !sinks);
		double rise = mob.getVelocity().y;
		if (mob.isTouchingWater() && !bank && rise > 0.0) {
			// vanilla pops swimmers up at walls faster than it notices the surface, so they'd fly out.
			mob.setVelocity(mob.getVelocity().withAxis(Direction.Axis.Y, floats ? rise * SURFACE_DAMPING : Math.min(rise, MAX_RISE)));
		}
	}

	/** Shore ahead, not a wall: only then hop out. */
	private static boolean canClimbOut(MobEntity mob, ControlState state) {
		Vec3d ahead = planar(state).multiply(BANK_REACH);
		double up = mob.getFluidHeight(FluidTags.WATER) + BANK_CLEARANCE;
		Box feet = mob.getBoundingBox().withMaxY(mob.getY() + BANK_PROBE);
		return ahead.lengthSquared() > 0.0 && mob.getWorld().isSpaceEmpty(mob, feet.offset(ahead.x, up, ahead.z));
	}

	/** as vanilla SwimGoal: no lift at the surface or the mob leaps out. */
	private static boolean floats(MobEntity mob) {
		return mob.isTouchingWater() && !mob.isOnGround() && mob.getFluidHeight(FluidTags.WATER) <= mob.getSwimHeight() + SURFACE_MARGIN;
	}

	private static Vec3d planar(ControlState state) {
		Vec3d forward = Vec3d.fromPolar(0.0F, state.yaw);
		Vec3d right = new Vec3d(-forward.z, 0.0, forward.x);
		Vec3d direction = forward.multiply(state.forward).add(right.multiply(-state.sideways));
		return direction.lengthSquared() > 1.0 ? direction.normalize() : direction;
	}

	private static Vec3d input3d(ControlState state) {
		Vec3d forward = Vec3d.fromPolar(state.pitch, state.yaw);
		Vec3d flat = Vec3d.fromPolar(0.0F, state.yaw);
		Vec3d right = new Vec3d(-flat.z, 0.0, flat.x);
		double up = state.jump ? 1.0 : state.sneak ? -1.0 : 0.0;
		Vec3d direction = forward.multiply(state.forward).add(right.multiply(-state.sideways)).add(0.0, up, 0.0);
		return direction.lengthSquared() > 1.0 ? direction.normalize() : direction;
	}

	private static void fly(MobEntity mob, ControlState state, double speed, double inertia) {
		steer(mob, state, input3d(state).multiply(speed), inertia, heavy(mob) ? TURN_HEAVY : TURN_FAST);
	}

	private static void steer(MobEntity mob, ControlState state, Vec3d target, double inertia, float maxTurn) {
		face(mob, state, maxTurn);
		stopWalking(mob);
		mob.setVelocity(mob.getVelocity().lerp(target, inertia));
		syncSteering(mob);
	}

	private static void swim(MobEntity mob, ControlState state, double speed, double inertia) {
		mob.setVelocity(mob.getVelocity().lerp(input3d(state).multiply(speed), inertia));
		syncSteering(mob);
	}

	/** Vanilla skips velocity for the Wither and bat but the client Wither turns by it. */
	private static void syncSteering(MobEntity mob) {
		if (!mob.getType().alwaysUpdateVelocity()) {
			mob.velocityModified = true;
		}
	}

	private static void waterOrLand(MobEntity mob, ControlState state, double swimSpeed, float landSpeed) {
		if (mob.isTouchingWater()) {
			face(mob, state, TURN_FAST);
			stopWalking(mob);
			swim(mob, state, swimSpeed, 0.25);
		} else {
			walkAt(mob, state, landSpeed, 1.0F);
		}
	}
}
