package net.neurallink.mixin;

import net.minecraft.entity.passive.PufferfishEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PufferfishEntity.class)
public interface PufferfishEntityAccessor {
	@Accessor("deflateTicks")
	void neurallink$setDeflateTicks(int ticks);
}
