package net.neurallink.registry;

import java.util.UUID;

import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Uuids;
import net.neurallink.NeuralLink;
import net.neurallink.item.ChipBinding;

public final class ModComponents {
	public static final ComponentType<ChipBinding> CHIP_BINDING = Registry.register(Registries.DATA_COMPONENT_TYPE, NeuralLink.id("chip_binding"),
			ComponentType.<ChipBinding>builder().codec(ChipBinding.CODEC).packetCodec(ChipBinding.PACKET_CODEC).build());
	public static final ComponentType<UUID> HELMET_ID = Registry.register(Registries.DATA_COMPONENT_TYPE, NeuralLink.id("helmet_id"),
			ComponentType.<UUID>builder().codec(Uuids.CODEC).packetCodec(Uuids.PACKET_CODEC).build());

	private ModComponents() {
	}

	public static void register() {
	}
}
