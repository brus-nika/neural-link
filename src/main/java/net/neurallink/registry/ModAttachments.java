package net.neurallink.registry;

import java.util.UUID;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Uuids;
import net.neurallink.NeuralLink;

@SuppressWarnings("UnstableApiUsage")
public final class ModAttachments {
	public static final AttachmentType<UUID> CHIP = AttachmentRegistry.createPersistent(NeuralLink.id("chip"), Uuids.CODEC);
	/** Host's pre-session NoGravity, saved with the flags so a crash keeps both or neither. */
	public static final AttachmentType<Boolean> ORIGINAL_NO_GRAVITY = AttachmentRegistry.createPersistent(NeuralLink.id("original_no_gravity"), Codec.BOOL);

	private ModAttachments() {
	}

	public static void register() {
	}
}
