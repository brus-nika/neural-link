package net.neurallink.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.IllusionerEntity;
import net.minecraft.entity.mob.SpellcastingIllagerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.neurallink.control.Control;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** vanilla spreads copies only on damage, otherwise they stand inside the illusioner after the spell. */
@Mixin(IllusionerEntity.class)
public abstract class IllusionerEntityMixin extends SpellcastingIllagerEntity {
	private static final float COPY_MIN_DISTANCE = 2.0F;
	private static final float COPY_EXTRA_DISTANCE = 1.5F;

	@Shadow
	@Final
	private Vec3d[][] mirrorCopyOffsets;

	@Shadow
	private int mirrorSpellTimer;

	private IllusionerEntityMixin(EntityType<? extends SpellcastingIllagerEntity> type, World world) {
		super(type, world);
	}

	@Override
	public void handleStatus(byte status) {
		if (status != Control.MIRROR_STATUS) {
			super.handleStatus(status);
			return;
		}
		mirrorSpellTimer = 3;
		for (int copy = 0; copy < mirrorCopyOffsets[1].length; copy++) {
			float angle = (copy + 0.5F + random.nextFloat() * 0.5F) * MathHelper.HALF_PI;
			float distance = COPY_MIN_DISTANCE + random.nextFloat() * COPY_EXTRA_DISTANCE;
			mirrorCopyOffsets[0][copy] = Vec3d.ZERO;
			mirrorCopyOffsets[1][copy] = new Vec3d(MathHelper.cos(angle) * distance, 0.0, MathHelper.sin(angle) * distance);
		}
	}
}
