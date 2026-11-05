package net.neurallink.client.render;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

/** Player look plus trance, both 0 when disabled. */
public class BodyRenderState extends PlayerEntityRenderState {
	/** eased 0 to 1: head drop. */
	public float trance;
	/** Visor brightness 0 to 1: pulse or hit blink. */
	public float visorGlow;
}
