package net.neurallink.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.neurallink.NeuralLink;

public final class ModParticles {
	public static final SimpleParticleType SPARK = register("spark");
	public static final SimpleParticleType LINK = register("link");
	public static final SimpleParticleType REFUSE = register("refuse");

	private ModParticles() {
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(Registries.PARTICLE_TYPE, NeuralLink.id(name), FabricParticleTypes.simple());
	}

	public static void register() {
	}
}
