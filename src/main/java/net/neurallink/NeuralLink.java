package net.neurallink;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.neurallink.advancement.Achievements;
import net.neurallink.chip.ChipLogic;
import net.neurallink.chip.ChipRegistry;
import net.neurallink.chip.ChipTransfer;
import net.neurallink.chip.HostTracker;
import net.neurallink.command.AdminCommand;
import net.neurallink.config.NeuralLinkConfig;
import net.neurallink.network.ModNetworking;
import net.neurallink.registry.ModAttachments;
import net.neurallink.registry.ModComponents;
import net.neurallink.registry.ModEntities;
import net.neurallink.registry.ModItems;
import net.neurallink.registry.ModParticles;
import net.neurallink.registry.ModSounds;
import net.neurallink.session.SessionManager;

public class NeuralLink implements ModInitializer {
	public static final String MOD_ID = "neurallink";
	private static final int HOSTS_INTERVAL_TICKS = 10;

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModComponents.register();
		ModSounds.register();
		ModParticles.register();
		ModAttachments.register();
		ModItems.register();
		ModEntities.register();
		Achievements.register();
		ModNetworking.register();
		AdminCommand.register();

		UseEntityCallback.EVENT.register(ChipLogic::onUseEntity);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> player instanceof ServerPlayerEntity operator && SessionManager.isInSession(operator) ? ActionResult.FAIL : ActionResult.PASS);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> SessionManager.onDamaged(entity, source));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayerEntity player && source.isOf(DamageTypes.OUT_OF_WORLD) && SessionManager.isInSession(player)));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !(entity instanceof ServerPlayerEntity player && SessionManager.onOperatorDying(player, source)));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Achievements.onDeath(entity, source);
			ChipTransfer.onMobDied(entity);
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) -> HostTracker.forget(player.getUuid()));
		ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register((original, moved, from, to) -> {
			ChipRegistry.get(to.getServer()).onMobChangedWorld(moved, to);
			SessionManager.onMobMoved(original, moved);
		});

		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, context) -> ChipTransfer.onMobConverted(previous, converted));
		ServerEntityEvents.ENTITY_LOAD.register(ChipTransfer::onEntityLoaded);
		ServerEntityEvents.ENTITY_LOAD.register(SessionManager::onEntityLoaded);
		ServerEntityEvents.ENTITY_UNLOAD.register(ChipTransfer::onEntityUnloaded);

		ServerLifecycleEvents.SERVER_STARTING.register(server -> NeuralLinkConfig.load());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> NeuralLinkConfig.sync(handler.getPlayer()));

		ServerTickEvents.START_SERVER_TICK.register(server -> {
			ChipTransfer.tickSplits();
			ChipRegistry.get(server).passToWearers(server);
			ModNetworking.flushChips(server);
			SessionManager.tick(server);
			if (server.getTicks() % 20 == 0) {
				ChipRegistry.get(server).refresh(server);
			}
			if (server.getTicks() % HOSTS_INTERVAL_TICKS == 0) {
				HostTracker.tick(server);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SessionManager.onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ChipRegistry.get(server).onOwnerJoined(handler.getPlayer()));
		ServerLifecycleEvents.SERVER_STOPPING.register(SessionManager::onServerStopping);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (server.getOverworld() != null && server.getPlayerManager() != null) { // a server that failed to start stops too
				ChipRegistry.get(server).onServerStopping(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SessionManager.clear();
			HostTracker.clear();
			ModNetworking.clear();
		});
	}
}
