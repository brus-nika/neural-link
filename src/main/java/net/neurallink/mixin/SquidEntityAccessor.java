package net.neurallink.mixin;

import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SquidEntity.class)
public interface SquidEntityAccessor {
	@Accessor("swimVec")
	void neurallink$setSwimVec(Vec3d value);

	@Invoker("squirt")
	void neurallink$squirt();
}
