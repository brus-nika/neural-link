package net.neurallink.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.neurallink.NeuralLink;
import net.neurallink.client.hud.DetectionFrames;
import net.neurallink.client.hud.SessionHud;
import net.neurallink.client.hud.VisorEffects;
import net.neurallink.client.hud.VisorOverlay;
import net.neurallink.client.render.BodyEntityRenderer;
import net.neurallink.client.render.SpawnWall;
import net.neurallink.client.render.VisorParticle;
import net.neurallink.config.NeuralLinkConfig;
import net.neurallink.network.BodySoundPayload;
import net.neurallink.network.ChipFeedbackPayload;
import net.neurallink.network.ChipListPayload;
import net.neurallink.network.ConfigPayload;
import net.neurallink.network.HostsPayload;
import net.neurallink.network.HudStatePayload;
import net.neurallink.network.ProtocolPayload;
import net.neurallink.network.SessionStatePayload;
import net.neurallink.network.SpawnZonePayload;
import net.neurallink.network.VisorEventPayload;
import net.neurallink.registry.ModEntities;
import org.lwjgl.glfw.GLFW;

public class NeuralLinkClient implements ClientModInitializer {
	private static final String CATEGORY = "key.categories.neurallink";

	public static final KeyBinding MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.neurallink.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));
	public static final KeyBinding EXIT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.neurallink.exit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));

	/** Read directly: F is swap hands and vanilla allows one binding per key. */
	public static final KeyBinding SLOT_2_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.neurallink.slot_2", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));
	public static final KeyBinding SLOT_3_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.neurallink.slot_3", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY));

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.BODY, BodyEntityRenderer::new);
		ClientConfigurationNetworking.registerGlobalReceiver(ProtocolPayload.ID, (payload, context) -> { });
		ClientPlayNetworking.registerGlobalReceiver(SessionStatePayload.ID, (payload, context) -> ClientSession.setState(payload.active(), payload.mobId()));
		ClientPlayNetworking.registerGlobalReceiver(ChipListPayload.ID, (payload, context) -> ClientSession.onChipList(context.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(HudStatePayload.ID, (payload, context) -> SessionHud.onState(payload));
		ClientPlayNetworking.registerGlobalReceiver(HostsPayload.ID, (payload, context) -> {
			DetectionFrames.onHosts(payload);
			VisorEffects.onHosts(payload);
		});
		ClientPlayNetworking.registerGlobalReceiver(VisorEventPayload.ID, (payload, context) -> {
			SessionHud.onEvent(payload.kind(), payload.slot());
			VisorEffects.onEvent(payload.kind());
		});
		ClientPlayNetworking.registerGlobalReceiver(ChipFeedbackPayload.ID, (payload, context) -> VisorEffects.onChipResult(payload.result()));
		ClientPlayNetworking.registerGlobalReceiver(ConfigPayload.ID, (payload, context) -> NeuralLinkConfig.apply(payload));
		ClientTickEvents.END_CLIENT_TICK.register(ClientSession::tick);
		ClientConfig.load();
		VisorParticle.register();
		SessionHud.registerReloadListener();
		WorldRenderEvents.LAST.register(DetectionFrames::captureMatrices);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(SpawnWall::render);
		ClientPlayNetworking.registerGlobalReceiver(SpawnZonePayload.ID, (payload, context) -> SpawnWall.onZone(payload));
		ClientPlayNetworking.registerGlobalReceiver(BodySoundPayload.ID, (payload, context) -> BodySoundPlayer.play(payload));
		HudLayerRegistrationCallback.EVENT.register(layers -> {
			Identifier overlay = NeuralLink.id("visor_overlay");
			Identifier frames = NeuralLink.id("detection_frames");
			layers.attachLayerAfter(IdentifiedLayer.MISC_OVERLAYS, overlay, VisorOverlay::render);
			layers.attachLayerAfter(overlay, frames, DetectionFrames::render);
			layers.attachLayerAfter(IdentifiedLayer.HOTBAR_AND_BARS, NeuralLink.id("session_hud"), SessionHud::render);
			layers.addLayer(IdentifiedLayer.of(NeuralLink.id("visor_effects"), VisorEffects::render));
		});
	}
}
