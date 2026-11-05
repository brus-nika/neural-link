package net.neurallink.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import net.neurallink.session.SpawnZone;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** the spawn zone is a wall for a controlled host; every kind of move goes through here. */
@Mixin(Entity.class)
public abstract class SpawnZoneEntityMixin {
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
	private Vec3d neurallink$stopAtSpawnZone(Vec3d movement, MovementType type) {
		return SpawnZone.limit((Entity) (Object) this, movement);
	}
}
