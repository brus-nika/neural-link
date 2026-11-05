package net.neurallink.client.render;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.neurallink.registry.ModParticles;

/** sparks, pairing and refusal marks. Self-lit indicators. */
public class VisorParticle extends SpriteBillboardParticle {
	private static final int FULL_BRIGHT = 0xF000F0;

	private final SpriteProvider sprites;

	private VisorParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites, float size, int age, float gravity) {
		super(world, x, y, z, vx, vy, vz);
		this.sprites = sprites;
		this.scale = size;
		this.maxAge = age;
		this.gravityStrength = gravity;
		this.velocityX = vx;
		this.velocityY = vy;
		this.velocityZ = vz;
		this.collidesWithWorld = false;
		setSpriteForAge(sprites);
	}

	public static void register() {
		ParticleFactoryRegistry registry = ParticleFactoryRegistry.getInstance();
		registry.register(ModParticles.SPARK, sprites -> factory(sprites, 0.08F, 14, 0.35F));
		registry.register(ModParticles.LINK, sprites -> factory(sprites, 0.06F, 22, -0.05F));
		registry.register(ModParticles.REFUSE, sprites -> factory(sprites, 0.3F, 20, 0.0F));
	}

	private static ParticleFactory<SimpleParticleType> factory(SpriteProvider sprites, float size, int age, float gravity) {
		return (type, world, x, y, z, vx, vy, vz) -> new VisorParticle(world, x, y, z, vx, vy, vz, sprites, size, age, gravity);
	}

	@Override
	public void tick() {
		super.tick();
		setSpriteForAge(sprites);
		alpha = 1.0F - (float) age / maxAge;
	}

	@Override
	protected int getBrightness(float tint) {
		return FULL_BRIGHT;
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}
}
