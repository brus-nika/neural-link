package net.neurallink.control;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.neurallink.mixin.ShulkerEntityInvoker;
import net.neurallink.mixin.WitchEntityAccessor;

/** calls vanilla logic wherever it exists. */
final class CasterAbilities {
	private static final double BULLET_RANGE = 32.0;
	private static final int SHULKER_OPEN_PEEK = 100;
	private static final int TELEPORT_ATTEMPTS = 20;
	private static final int FANGS = 12;
	private static final int FANG_SEARCH = 3;
	private static final int VEXES = 3;
	private static final double TELEPORT_RANGE = 32.0;
	private static final double BLOCK_REACH = 5.0;
	private static final int FRIENDS_RANGE = 6;
	/** potions the vanilla witch uses. */
	private static final List<RegistryEntry<Potion>> WITCH_THROWN = List.of(Potions.HARMING, Potions.POISON, Potions.SLOWNESS, Potions.WEAKNESS);
	private static final List<RegistryEntry<Potion>> WITCH_DRUNK = List.of(Potions.HEALING, Potions.SWIFTNESS, Potions.FIRE_RESISTANCE, Potions.WATER_BREATHING);

	static final Ability WITCH_POTION = context -> {
		if (context.mob() instanceof WitchEntity witch && witch.isDrinking()) {
			return false;
		}
		ItemStack stack = PotionContentsComponent.createStack(Items.SPLASH_POTION, Util.getRandom(WITCH_THROWN, context.mob().getRandom()));
		PotionEntity potion = new PotionEntity(context.world(), context.mob(), stack);
		potion.setVelocity(context.look().x, context.look().y + 0.2, context.look().z, 0.75F, 1.0F);
		context.world().spawnEntity(potion);
		AbilitySupport.sound(context, SoundEvents.ENTITY_WITCH_THROW);
		return true;
	};

	/** Vanilla drinking: tickMovement applies the effect. */
	static final Ability WITCH_DRINK = context -> {
		if (!(context.mob() instanceof WitchEntity witch) || witch.isDrinking()) {
			return false;
		}
		ItemStack potion = PotionContentsComponent.createStack(Items.POTION, Util.getRandom(WITCH_DRUNK, witch.getRandom()));
		witch.equipStack(EquipmentSlot.MAINHAND, potion);
		((WitchEntityAccessor) witch).neurallink$setDrinkTimeLeft(potion.getMaxUseTime(witch));
		witch.setDrinking(true);
		AbilitySupport.sound(context, SoundEvents.ENTITY_WITCH_DRINK);
		return true;
	};

	static final Ability EVOKER_FANGS = context -> {
		MobEntity mob = context.mob();
		Vec3d flat = Vec3d.fromPolar(0.0F, context.state().yaw);
		for (int i = 0; i < FANGS; i++) {
			Vec3d point = mob.getPos().add(flat.multiply(1.25 * (i + 1)));
			Double ground = groundBelow(context.world(), BlockPos.ofFloored(point));
			if (ground != null) {
				context.world().spawnEntity(new EvokerFangsEntity(context.world(), point.x, ground, point.z, context.state().yaw * 0.017453292F, i, mob));
			}
		}
		AbilitySupport.sound(context, SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK);
		return true;
	};

	static final Ability EVOKER_VEXES = context -> {
		MobEntity mob = context.mob();
		for (int i = 0; i < VEXES; i++) {
			VexEntity vex = EntityType.VEX.create(context.world(), SpawnReason.MOB_SUMMONED);
			if (vex != null) {
				vex.refreshPositionAndAngles(mob.getX(), mob.getEyeY(), mob.getZ(), mob.getYaw(), 0.0F);
				vex.initialize(context.world(), context.world().getLocalDifficulty(mob.getBlockPos()), SpawnReason.MOB_SUMMONED, null);
				vex.setOwner(mob);
				vex.setBounds(mob.getBlockPos());
				vex.setLifeTicks(20 * (30 + mob.getRandom().nextInt(90)));
				context.world().spawnEntity(vex);
			}
		}
		AbilitySupport.sound(context, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON);
		return true;
	};

	static final Ability ILLUSIONER_BLINDNESS = context -> {
		LivingEntity target = AbilitySupport.lookedAt(context, 24.0);
		if (target == null) {
			return false;
		}
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 400));
		AbilitySupport.sound(context, SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL);
		return true;
	};

	/** the renderer draws copies while invisible. */
	static final Ability ILLUSIONER_MIRROR = context -> {
		context.mob().addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 240));
		context.world().sendEntityStatus(context.mob(), Control.MIRROR_STATUS);
		AbilitySupport.sound(context, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE);
		return true;
	};

	static final Ability SHULKER_BULLET = context -> {
		LivingEntity target = AbilitySupport.lookedAt(context, BULLET_RANGE);
		if (target == null) {
			return false;
		}
		if (context.mob() instanceof ShulkerEntity shulker && ((ShulkerEntityInvoker) shulker).neurallink$getPeekAmount() == 0) {
			((ShulkerEntityInvoker) shulker).neurallink$setPeekAmount(SHULKER_OPEN_PEEK);
		}
		context.world().spawnEntity(new ShulkerBulletEntity(context.world(), context.mob(), target, Direction.Axis.Y));
		AbilitySupport.sound(context, SoundEvents.ENTITY_SHULKER_SHOOT);
		return true;
	};

	static final Ability SHULKER_CLOSE = new Ability() {
		@Override
		public boolean press(AbilityContext context) {
			if (!(context.mob() instanceof ShulkerEntity shulker)) {
				return false;
			}
			ShulkerEntityInvoker invoker = (ShulkerEntityInvoker) shulker;
			invoker.neurallink$setPeekAmount(invoker.neurallink$getPeekAmount() > 0 ? 0 : SHULKER_OPEN_PEEK);
			return true;
		}

		@Override
		public boolean active(MobEntity mob, ControlState state) {
			return mob instanceof ShulkerEntity shulker && ((ShulkerEntityInvoker) shulker).neurallink$getPeekAmount() == 0;
		}
	};

	/** Vanilla teleport often fails; under control it must work if there's room. */
	static final Ability SHULKER_TELEPORT = context -> {
		if (context.mob() instanceof ShulkerEntity shulker) {
			for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
				if (((ShulkerEntityInvoker) shulker).neurallink$tryTeleport()) {
					return true;
				}
			}
		}
		return false;
	};

	static final Ability ENDERMAN_TELEPORT = context -> {
		BlockHitResult hit = AbilitySupport.raycast(context, TELEPORT_RANGE, RaycastContext.ShapeType.COLLIDER);
		Vec3d point = hit.getType() == HitResult.Type.MISS ? hit.getPos() : Vec3d.ofBottomCenter(hit.getBlockPos().offset(hit.getSide()));
		MobEntity mob = context.mob();
		if (!mob.teleport(point.x, point.y, point.z, true)) {
			return false;
		}
		AbilitySupport.sound(context, SoundEvents.ENTITY_ENDERMAN_TELEPORT);
		return true;
	};

	static final Ability ENDERMAN_BLOCK = context -> {
		if (!(context.mob() instanceof EndermanEntity enderman) || !AbilitySupport.griefing(context)) {
			return false;
		}
		ServerWorld world = context.world();
		BlockState carried = enderman.getCarriedBlock();
		// flowers have no collision but placed blocks must pass through grass.
		BlockHitResult hit = AbilitySupport.raycast(context, BLOCK_REACH, carried == null ? RaycastContext.ShapeType.OUTLINE : RaycastContext.ShapeType.COLLIDER);
		if (hit.getType() == HitResult.Type.MISS) {
			return false;
		}
		if (carried == null) {
			BlockState state = world.getBlockState(hit.getBlockPos());
			if (!state.isIn(BlockTags.ENDERMAN_HOLDABLE) || !context.operator().canModifyAt(world, hit.getBlockPos())) {
				return false;
			}
			world.removeBlock(hit.getBlockPos(), false);
			enderman.setCarriedBlock(state.getBlock().getDefaultState());
			return true;
		}
		BlockPos target = hit.getBlockPos().offset(hit.getSide());
		BlockState placed = Block.postProcessState(carried, world, target);
		if (placed.isAir() || !world.getBlockState(target).isReplaceable() || !placed.canPlaceAt(world, target) || !world.canPlace(placed, target, ShapeContext.absent())
				|| !context.operator().canModifyAt(world, target)) {
			return false;
		}
		world.setBlockState(target, placed);
		enderman.setCarriedBlock(null);
		return true;
	};

	static final Ability VEX_PHASE = new ToggleAbility(context -> context.state().phasing = true, context -> context.state().phasing = false, (mob, state) -> state.phasing);

	static final Ability SILVERFISH_CALL = context -> {
		if (!AbilitySupport.griefing(context)) {
			return false;
		}
		boolean called = false;
		BlockPos center = context.mob().getBlockPos();
		for (BlockPos pos : BlockPos.iterate(center.add(-FRIENDS_RANGE, -FRIENDS_RANGE, -FRIENDS_RANGE), center.add(FRIENDS_RANGE, FRIENDS_RANGE, FRIENDS_RANGE))) {
			if (context.world().getBlockState(pos).getBlock() instanceof InfestedBlock) {
				context.world().breakBlock(pos, true, context.mob());
				called = true;
			}
		}
		return called;
	};

	/** Hides in session: out of sight and frozen. */
	static final Ability SILVERFISH_HIDE = new ToggleAbility(context -> {
		AbilitySupport.freeze(context, true);
		context.mob().setInvisible(true);
	}, context -> {
		AbilitySupport.freeze(context, false);
		context.mob().setInvisible(false);
	}, (mob, state) -> state.frozen);

	private CasterAbilities() {
	}

	/** height of solid floor for a fang, or null. */
	private static Double groundBelow(ServerWorld world, BlockPos start) {
		for (int dy = FANG_SEARCH; dy >= -FANG_SEARCH; dy--) {
			BlockPos pos = start.up(dy);
			VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
			double top = shape.isEmpty() ? 0.0 : shape.getMax(Direction.Axis.Y);
			if (top < 1.0 && world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)) {
				return pos.getY() + top;
			}
		}
		return null;
	}
}
