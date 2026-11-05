package net.neurallink.entity;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.neurallink.config.NeuralLinkConfig;
import net.neurallink.registry.ModEntities;
import net.neurallink.session.SessionManager;

/** The player's body for a session, never saved; crash rollback uses SessionStore. Gear is cosmetic copies. */
public class BodyEntity extends MobEntity {
	private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(BodyEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
	private static final TrackedData<Byte> MODEL_PARTS = DataTracker.registerData(BodyEntity.class, TrackedDataHandlerRegistry.BYTE);
	/** connect time, so late viewers see the trance settled. */
	private static final TrackedData<Long> LINKED_AT = DataTracker.registerData(BodyEntity.class, TrackedDataHandlerRegistry.LONG);
	/** Head goes down in about a second. */
	public static final int TRANCE_TICKS = 20;
	private static final EquipmentSlot[] COPIED_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
	};
	private static final EntityDimensions CROUCHING_DIMENSIONS = EntityDimensions.changing(0.6F, 1.5F).withEyeHeight(1.27F);
	private static final EntityDimensions CRAWLING_DIMENSIONS = EntityDimensions.changing(0.6F, 0.6F).withEyeHeight(0.4F);

	/** client only: trance progress 0 to 1. */
	private float trance;
	private float prevTrance;
	private boolean tranceSeen;

	public BodyEntity(EntityType<? extends BodyEntity> type, World world) {
		super(type, world);
	}

	public static BodyEntity of(ServerPlayerEntity player) {
		BodyEntity body = new BodyEntity(ModEntities.BODY, player.getWorld());
		body.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
		body.setHeadYaw(player.getHeadYaw());
		body.setBodyYaw(player.getBodyYaw());
		body.dataTracker.set(OWNER, Optional.of(player.getUuid()));
		body.dataTracker.set(MODEL_PARTS, modelParts(player));
		body.dataTracker.set(LINKED_AT, player.getWorld().getTime());
		body.setLeftHanded(player.getMainArm() == Arm.LEFT);
		body.setSneaking(player.isInSneakingPose());
		// Not isCrawling: vanilla never marks a swimming player crawling.
		body.setPose(player.isInSwimmingPose() ? EntityPose.SWIMMING : player.isInSneakingPose() ? EntityPose.CROUCHING : EntityPose.STANDING);
		body.setCustomName(player.getName());
		body.setCustomNameVisible(true);
		body.setPersistent();
		body.setInvulnerable(player.isCreative());
		body.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(player.getMaxHealth());
		body.setHealth(player.getHealth());
		body.setFireTicks(player.getFireTicks());
		body.setAir(player.getAir());
		body.setFrozenTicks(player.getFrozenTicks());
		body.fallDistance = player.fallDistance;
		for (EquipmentSlot slot : COPIED_SLOTS) {
			ItemStack copy = player.getEquippedStack(slot).copy();
			// the totem shows but only the real one fires, on the player.
			copy.remove(DataComponentTypes.DEATH_PROTECTION);
			body.equipStack(slot, copy);
		}
		for (StatusEffectInstance effect : player.getStatusEffects()) {
			body.addStatusEffect(new StatusEffectInstance(effect));
		}
		body.setAbsorptionAmount(player.getAbsorptionAmount()); // after effects, which raise the cap and would refill it
		return body;
	}

	private static byte modelParts(ServerPlayerEntity player) {
		int parts = 0;
		for (PlayerModelPart part : PlayerModelPart.values()) {
			if (player.isPartVisible(part)) {
				parts |= part.getBitFlag();
			}
		}
		return (byte) parts;
	}

	public Optional<UUID> getOwner() {
		return dataTracker.get(OWNER);
	}

	public boolean isPartVisible(PlayerModelPart part) {
		return (dataTracker.get(MODEL_PARTS) & part.getBitFlag()) != 0;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(OWNER, Optional.empty());
		builder.add(MODEL_PARTS, (byte) 0);
		builder.add(LINKED_AT, 0L);
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) {
			tickTrance();
		} else if (!isDead() && !SessionManager.isBody(this)) {
			discard();
		}
	}

	/** The server setting eases the trance in or out; late viewers see it already done. */
	private void tickTrance() {
		if (startTrance()) {
			return;
		}
		prevTrance = trance;
		float target = NeuralLinkConfig.bodyTrance ? 1.0F : 0.0F;
		float step = 1.0F / TRANCE_TICKS;
		trance = trance < target ? Math.min(target, trance + step) : Math.max(target, trance - step);
	}

	/** on first frame or tick, since a frame can precede the first tick. */
	private boolean startTrance() {
		if (tranceSeen) {
			return false;
		}
		tranceSeen = true;
		float target = NeuralLinkConfig.bodyTrance ? 1.0F : 0.0F;
		trance = prevTrance = target * MathHelper.clamp((getWorld().getTime() - dataTracker.get(LINKED_AT)) / (float) TRANCE_TICKS, 0.0F, 1.0F);
		return true;
	}

	/** Linear 0 to 1; the renderer eases it. */
	public float trance(float tickDelta) {
		startTrance();
		return MathHelper.lerp(tickDelta, prevTrance, trance);
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	/** the OtherClientPlayerEntity formula, same draw range as the player. */
	@Override
	public boolean shouldRender(double distance) {
		double size = getBoundingBox().getAverageSideLength() * 10.0;
		if (Double.isNaN(size)) {
			size = 1.0;
		}
		size *= 64.0 * getRenderDistanceMultiplier();
		return distance < size * size;
	}

	/** Player sizes, so a body in a low gap won't suffocate. */
	@Override
	protected EntityDimensions getBaseDimensions(EntityPose pose) {
		return switch (pose) {
			case CROUCHING -> CROUCHING_DIMENSIONS;
			case SWIMMING -> CRAWLING_DIMENSIONS;
			default -> super.getBaseDimensions(pose);
		};
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canUsePortals(boolean allowVehicles) {
		return false;
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (source.getAttacker() instanceof PlayerEntity attacker
				&& getOwner().map(world.getServer().getPlayerManager()::getPlayer).filter(operator -> !operator.shouldDamagePlayer(attacker)).isPresent()) {
			return false; // the operator couldn't be hit here either
		}
		return super.damage(world, source, amount);
	}

	/** the body is the player: the same gamerules spare it. */
	@Override
	public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
		if (super.isInvulnerableTo(world, source)) {
			return true;
		} else if (source.isIn(DamageTypeTags.IS_DROWNING)) {
			return !world.getGameRules().getBoolean(GameRules.DROWNING_DAMAGE);
		} else if (source.isIn(DamageTypeTags.IS_FALL)) {
			return !world.getGameRules().getBoolean(GameRules.FALL_DAMAGE);
		} else if (source.isIn(DamageTypeTags.IS_FIRE)) {
			return !world.getGameRules().getBoolean(GameRules.FIRE_DAMAGE);
		}
		return source.isIn(DamageTypeTags.IS_FREEZING) && !world.getGameRules().getBoolean(GameRules.FREEZE_DAMAGE);
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (!getWorld().isClient) {
			SessionManager.onBodyDied(this, source);
		}
	}

	@Override
	protected void dropLoot(ServerWorld world, DamageSource source, boolean causedByPlayer) {
	}

	@Override
	protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_PLAYER_DEATH;
	}
}
