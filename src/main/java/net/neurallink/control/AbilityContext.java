package net.neurallink.control;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public record AbilityContext(ServerPlayerEntity operator, MobEntity mob, ServerWorld world, ControlState state) {
	public Vec3d look() {
		return Vec3d.fromPolar(state.pitch, state.yaw);
	}

	public Vec3d eyes() {
		return mob.getEyePos();
	}
}
