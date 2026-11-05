package net.neurallink.client.hud;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.neurallink.client.mixin.MinecraftClientInvoker;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.slf4j.Logger;

/** 16x16 icon silhouette for outline and cooldown: from texture or rendered icon. Entity icons get a hexagon. */
final class IconMask {
	private static final Logger LOGGER = LogUtils.getLogger();
	static final int SIZE = 16;
	/** captured pixels per icon pixel, enough to judge coverage. */
	private static final int CAPTURE_SCALE = 4;
	private static final int CAPTURE_BUFFER = 1536;
	private static final Map<Identifier, IconMask> CACHE = new HashMap<>();
	/** items whose texture isn't named after them. */
	private static final Map<Identifier, Identifier> SPRITES = Map.of(
			Identifier.ofVanilla("crossbow"), Identifier.ofVanilla("item/crossbow_standby"),
			Identifier.ofVanilla("lily_pad"), Identifier.ofVanilla("block/lily_pad"));

	final boolean[][] pixels;
	/** One array per row: x0, x1 pairs of solid runs, x1 exclusive. */
	final int[][] rows;
	/** x, y pairs of outside pixels touching the shape. */
	final int[] outline;

	private IconMask(boolean[][] pixels) {
		this.pixels = pixels;
		this.rows = rows(pixels);
		this.outline = outline(pixels);
	}

	static IconMask of(Identifier icon) {
		return CACHE.computeIfAbsent(icon, IconMask::load);
	}

	static void clear() {
		CACHE.clear();
	}

	private static IconMask load(Identifier icon) {
		return new IconMask(Registries.ITEM.containsId(icon) ? fromItem(icon) : hexagon());
	}

	private static int[][] rows(boolean[][] pixels) {
		int[][] rows = new int[SIZE][];
		int[] runs = new int[SIZE];
		for (int y = 0; y < SIZE; y++) {
			int length = 0;
			for (int x = 0; x < SIZE; x++) {
				if (pixels[x][y] && !filled(pixels, x - 1, y)) {
					runs[length++] = x;
				}
				if (pixels[x][y] && !filled(pixels, x + 1, y)) {
					runs[length++] = x + 1;
				}
			}
			rows[y] = Arrays.copyOf(runs, length);
		}
		return rows;
	}

	private static int[] outline(boolean[][] pixels) {
		int[] found = new int[(SIZE + 2) * (SIZE + 2) * 2];
		int length = 0;
		for (int x = -1; x <= SIZE; x++) {
			for (int y = -1; y <= SIZE; y++) {
				if (!filled(pixels, x, y) && (filled(pixels, x - 1, y) || filled(pixels, x + 1, y) || filled(pixels, x, y - 1) || filled(pixels, x, y + 1))) {
					found[length++] = x;
					found[length++] = y;
				}
			}
		}
		return Arrays.copyOf(found, length);
	}

	private static boolean filled(boolean[][] pixels, int x, int y) {
		return x >= 0 && y >= 0 && x < SIZE && y < SIZE && pixels[x][y];
	}

	private static boolean[][] fromItem(Identifier item) {
		Identifier spriteId = SPRITES.getOrDefault(item, Identifier.of(item.getNamespace(), "item/" + item.getPath()));
		Sprite sprite = MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(spriteId);
		SpriteContents contents = sprite.getContents();
		if (contents.getId().equals(MissingSprite.getMissingSpriteId())) {
			try {
				return fromRender(new ItemStack(Registries.ITEM.get(item)));
			} catch (RuntimeException e) { // another mod's renderer failing mid-HUD must not crash the game
				LOGGER.warn("Could not capture the icon of {}, using a hexagon", item, e);
				return hexagon();
			}
		}
		boolean[][] mask = new boolean[SIZE][SIZE];
		for (int x = 0; x < SIZE; x++) {
			for (int y = 0; y < SIZE; y++) {
				mask[x][y] = !contents.isPixelTransparent(0, x * contents.getWidth() / SIZE, y * contents.getHeight() / SIZE);
			}
		}
		return mask;
	}

	/**
	 * blocks and 3D models have no flat texture, so the icon is rendered off screen once; a pixel counts when mostly
	 * covered.
	 */
	private static boolean[][] fromRender(ItemStack stack) {
		MinecraftClient client = MinecraftClient.getInstance();
		int side = SIZE * CAPTURE_SCALE;
		SimpleFramebuffer target = new SimpleFramebuffer(side, side, true);
		Framebuffer main = client.getFramebuffer();
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		boolean[][] mask = new boolean[SIZE][SIZE];
		try (BufferAllocator allocator = new BufferAllocator(CAPTURE_BUFFER)) {
			target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
			target.clear();
			// Item layers bind the main framebuffer, so the capture replaces it until done.
			((MinecraftClientInvoker) client).neurallink$setFramebuffer(target);
			target.beginWrite(true);
			// GUI projection over the icon alone, like GameRenderer.
			RenderSystem.backupProjectionMatrix();
			RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, SIZE, SIZE, 0.0F, 1000.0F, 21000.0F), ProjectionType.ORTHOGRAPHIC);
			modelView.pushMatrix();
			try {
				modelView.translation(0.0F, 0.0F, -11000.0F);
				DiffuseLighting.enableGuiDepthLighting();
				DrawContext context = new DrawContext(client, VertexConsumerProvider.immediate(allocator));
				context.drawItem(stack, 0, 0);
				context.draw();
			} finally {
				modelView.popMatrix();
				RenderSystem.restoreProjectionMatrix();
			}
			try (NativeImage image = new NativeImage(side, side, false)) {
				RenderSystem.bindTexture(target.getColorAttachment());
				image.loadFromTextureImage(0, false);
				for (int x = 0; x < SIZE; x++) {
					for (int y = 0; y < SIZE; y++) {
						int covered = 0;
						for (int dx = 0; dx < CAPTURE_SCALE; dx++) {
							for (int dy = 0; dy < CAPTURE_SCALE; dy++) {
								// texture comes bottom row first.
								if (image.getColorArgb(x * CAPTURE_SCALE + dx, side - 1 - (y * CAPTURE_SCALE + dy)) >>> 24 >= 0x80) {
									covered++;
								}
							}
						}
						mask[x][y] = covered * 2 >= CAPTURE_SCALE * CAPTURE_SCALE;
					}
				}
			}
		} finally {
			((MinecraftClientInvoker) client).neurallink$setFramebuffer(main); // also when capture failed before drawing
			target.delete();
			main.beginWrite(true);
		}
		return mask;
	}

	private static boolean[][] hexagon() {
		boolean[][] mask = new boolean[SIZE][SIZE];
		for (int x = 1; x < SIZE - 1; x++) {
			int edge = Math.abs(x * 2 - (SIZE - 1)) / 4;
			for (int y = edge; y < SIZE - edge; y++) {
				mask[x][y] = true;
			}
		}
		return mask;
	}
}
