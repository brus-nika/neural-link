package net.neurallink.mixin;

import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.WitherEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WitherEntity.class)
public interface WitherEntityAccessor {
	@Accessor("bossBar")
	ServerBossBar neurallink$bossBar();
}
