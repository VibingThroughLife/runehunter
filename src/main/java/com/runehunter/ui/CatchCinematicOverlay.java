package com.runehunter.ui;

import com.runehunter.RuneHunterConfig;
import com.runehunter.RuneHunterPlugin;
import com.runehunter.data.Archetype;
import com.runehunter.game.CatchManager;
import com.runehunter.spawn.SpawnedCreature;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

/**
 * Full-screen catch cutscene: the Temple of Light wall crossing (MEP2), 2004
 * quest-cutscene style. A dark chasm viewed side-on — near ledge, three wooden
 * peg handholds across a sheer rock face, far ledge where the snared creature
 * waits inside a glowing orb cage. Our Man leaps peg to peg (one catch check
 * per peg); pass all three and he claims the orb, fail one and he takes the
 * famous handhold fall while the cage shatters.
 *
 * Everything is drawn procedurally with Java2D. Optional drop-in art slots
 * (resources/com/runehunter/) upgrade the visuals when present:
 *   cine_backdrop.png       — stage backdrop, stretched to the stage rect
 *   cine_man_stand.png      — horizontal strip, 2 equal-width frames
 *   cine_man_jump.png       — horizontal strip, 4 frames
 *   cine_man_hang.png       — horizontal strip, 4 frames
 *   cine_man_fall.png       — horizontal strip, 4 frames
 *   cine_man_cheer.png      — horizontal strip, 4 frames
 * Missing slots fall back to the built-in procedural Man and stage.
 */
public class CatchCinematicOverlay extends Overlay
{
	// ---- 2004 palette ----
	private static final Color BAR = Color.BLACK;
	private static final Color DIM = new Color(0, 0, 0, 89); // ~35%
	private static final Color GOLD = new Color(0xE8B84A);
	private static final Color PARCHMENT = new Color(0xD8CCB4);

	private static final Color ROCK_EDGE = new Color(0x14100B);
	private static final Color WALL_BASE = new Color(0x2A241B);
	private static final Color[] WALL_BANDS =
	{
		new Color(0x322B20),
		new Color(0x3E3529),
		new Color(0x37301F),
		new Color(0x2E281E),
	};
	private static final Color LEDGE_TOP = new Color(0x6B5A40);
	private static final Color LEDGE_FILL = new Color(0x4A3F2F);
	private static final Color LEDGE_FACE = new Color(0x3E3529);
	private static final Color CRACK = new Color(0x1C1710);

	private static final Color PEG_WOOD = new Color(0x5D4E37);
	private static final Color PEG_DARK = new Color(0x2A2318);
	private static final Color PEG_LIGHT = new Color(0x7A6748);

	private static final Color SKIN = new Color(0xC79C6E);
	private static final Color TUNIC = new Color(0xB09468);
	private static final Color TROUSERS = new Color(0x5D4E37);
	private static final Color OUTLINE = new Color(0x241C12);

	// ---- procedural man poses ----
	private static final int POSE_STAND = 0;
	private static final int POSE_JUMP = 1;
	private static final int POSE_HANG = 2;
	private static final int POSE_FALL = 3;
	private static final int POSE_CHEER = 4;

	private final Client client;
	private final RuneHunterConfig config;
	private final CatchManager catchManager;

	// image cache — misses are cached as null so we never re-hit the loader
	private final Map<String, BufferedImage> imgCache = new HashMap<>();
	private final Map<String, BufferedImage> tintCache = new HashMap<>();
	private final Map<String, BufferedImage[]> stripCache = new HashMap<>();
	private final Map<Integer, Font> fontCache = new HashMap<>();

	private final BufferedImage backdrop;
	private final BufferedImage sparkle;

	// per-stage-size cached derived state (rebuilt only on resize)
	private int cachedStageW = -1;
	private Stroke limbStroke;
	private Stroke thinStroke;
	private Stroke shardStroke;

	private int animTick;

	public CatchCinematicOverlay(Client client, RuneHunterConfig config, CatchManager catchManager)
	{
		this.client = client;
		this.config = config;
		this.catchManager = catchManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);

		backdrop = img("cine_backdrop.png");
		sparkle = img("sparkle.png");
	}

	// ------------------------------------------------------------------
	// resource helpers (missing-safe — ImageUtil THROWS on missing files)
	// ------------------------------------------------------------------

	private BufferedImage img(String resource)
	{
		if (imgCache.containsKey(resource))
		{
			return imgCache.get(resource);
		}
		BufferedImage loaded = null;
		try
		{
			loaded = ImageUtil.loadImageResource(RuneHunterPlugin.class, resource);
		}
		catch (Exception ignored)
		{
			// optional art slot not filled yet — procedural fallback takes over
		}
		imgCache.put(resource, loaded);
		return loaded;
	}

	private BufferedImage[] strip(String resource, int frames)
	{
		if (stripCache.containsKey(resource))
		{
			return stripCache.get(resource);
		}
		BufferedImage sheet = img(resource);
		BufferedImage[] out = null;
		if (sheet != null && frames > 0 && sheet.getWidth() >= frames)
		{
			int fw = sheet.getWidth() / frames;
			out = new BufferedImage[frames];
			for (int i = 0; i < frames; i++)
			{
				out[i] = sheet.getSubimage(i * fw, 0, fw, sheet.getHeight());
			}
		}
		stripCache.put(resource, out);
		return out;
	}

	/** Tint a white-alpha silhouette by replacing RGB, keeping alpha. Cached. */
	private BufferedImage tinted(String resource, Color tint)
	{
		String key = resource + '|' + (tint.getRGB() & 0xFFFFFF);
		if (tintCache.containsKey(key))
		{
			return tintCache.get(key);
		}
		BufferedImage src = img(resource);
		BufferedImage out = null;
		if (src != null)
		{
			out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
			int rgb = tint.getRGB() & 0x00FFFFFF;
			for (int y = 0; y < src.getHeight(); y++)
			{
				for (int x = 0; x < src.getWidth(); x++)
				{
					out.setRGB(x, y, (src.getRGB(x, y) & 0xFF000000) | rgb);
				}
			}
		}
		tintCache.put(key, out);
		return out;
	}

	private Font runeFont(int size)
	{
		Font f = fontCache.get(size);
		if (f == null)
		{
			f = FontManager.getRunescapeBoldFont().deriveFont((float) size);
			fontCache.put(size, f);
		}
		return f;
	}

	// ------------------------------------------------------------------
	// render
	// ------------------------------------------------------------------

	@Override
	public Dimension render(Graphics2D g)
	{
		CatchManager.Phase phase = catchManager.getPhase();
		if (!config.cinematicCatch() || phase == CatchManager.Phase.IDLE)
		{
			animTick = 0;
			return null;
		}
		SpawnedCreature target = catchManager.getTarget();
		if (target == null)
		{
			return null;
		}
		animTick++;

		int w = client.getCanvasWidth();
		int h = client.getCanvasHeight();
		double progress = catchManager.getPhaseProgress();

		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// letterbox eases in during the throw
		double barEase = phase == CatchManager.Phase.THROW
			? easeOut(Math.min(1.0, progress * 2.5)) : 1.0;
		int barH = (int) (h * 0.11 * barEase);
		g.setColor(DIM);
		g.fillRect(0, barH, w, h - 2 * barH);
		g.setColor(BAR);
		g.fillRect(0, 0, w, barH);
		g.fillRect(0, h - barH, w, barH);

		// stage geometry — capped width, 16:9-ish, centered
		int stageW = Math.min(560, (int) (w * 0.62));
		int stageH = (int) (stageW * 9.0 / 16.0);
		int sx = (w - stageW) / 2;
		int sy = (h - stageH) / 2 - (int) (h * 0.02);
		rebuildStrokes(stageW);

		StageLayout st = new StageLayout(sx, sy, stageW, stageH);

		Shape oldClip = g.getClip();
		g.clipRect(sx, sy, stageW, stageH);
		drawStage(g, st);

		int seg = catchManager.getSegmentType();
		int peg = catchManager.getSegmentPeg();

		if (phase == CatchManager.Phase.THROW)
		{
			drawOrbCage(g, st, target, easeIn(clamp01((progress - 0.82) / 0.18)));
			drawManStanding(g, st, st.leftStandX, false);
			drawThrownOrb(g, st, progress);
		}
		else // CROSSING
		{
			switch (seg)
			{
				case CatchManager.SEG_INTRO:
					drawOrbCage(g, st, target, 1.0);
					drawManStanding(g, st, st.leftStandX, false);
					break;

				case CatchManager.SEG_JUMP:
					drawOrbCage(g, st, target, 1.0);
					drawManJump(g, st, peg, progress);
					break;

				case CatchManager.SEG_GRIP:
					drawOrbCage(g, st, target, 1.0);
					drawManHanging(g, st, peg);
					break;

				case CatchManager.SEG_FALL:
					drawCageShatter(g, st, target, progress);
					drawManFalling(g, st, peg, progress);
					break;

				case CatchManager.SEG_CHEER:
					drawOpenCage(g, st);
					drawManStanding(g, st, st.rightStandX, true);
					drawCheerSparkles(g, st);
					break;

				default:
					drawOrbCage(g, st, target, 1.0);
					break;
			}
		}

		g.setClip(oldClip);
		drawStageFrame(g, st);
		drawCheckPips(g, st, catchManager.getChecksPassed());
		drawNarration(g, w, barH, barEase, narrationLine(phase, seg, peg, target));

		return new Dimension(w, h);
	}

	// ------------------------------------------------------------------
	// stage layout
	// ------------------------------------------------------------------

	/** All stage-space measurements, computed once per frame (cheap ints). */
	private static final class StageLayout
	{
		final int sx;
		final int sy;
		final int w;
		final int h;
		final int ledgeW;
		final int ledgeTopY;    // walkable surface of both ledges
		final int gapLeft;      // right edge of left ledge
		final int gapRight;     // left edge of right ledge
		final int pegY;         // peg height on the wall face
		final int[] pegX = new int[3];
		final int manH;
		final int leftStandX;
		final int rightStandX;
		final int orbX;
		final int orbY;
		final int orbR;

		StageLayout(int sx, int sy, int w, int h)
		{
			this.sx = sx;
			this.sy = sy;
			this.w = w;
			this.h = h;
			ledgeW = (int) (w * 0.165);
			ledgeTopY = sy + (int) (h * 0.60);
			gapLeft = sx + ledgeW;
			gapRight = sx + w - ledgeW;
			pegY = sy + (int) (h * 0.40);
			int gapW = gapRight - gapLeft;
			pegX[0] = gapLeft + gapW / 4;
			pegX[1] = gapLeft + gapW / 2;
			pegX[2] = gapLeft + (3 * gapW) / 4;
			manH = (int) (h * 0.155);
			leftStandX = gapLeft - (int) (manH * 0.35);
			orbR = (int) (h * 0.115);
			orbX = gapRight + (int) (ledgeW * 0.55);
			orbY = ledgeTopY - orbR - (int) (h * 0.01);
			rightStandX = gapRight + (int) (ledgeW * 0.22);
		}
	}

	private void rebuildStrokes(int stageW)
	{
		if (stageW == cachedStageW)
		{
			return;
		}
		cachedStageW = stageW;
		float u = stageW / 560f;
		limbStroke = new BasicStroke(Math.max(2f, 3.2f * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		thinStroke = new BasicStroke(Math.max(1f, 1.4f * u));
		shardStroke = new BasicStroke(Math.max(1f, 2f * u), BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
	}

	// ------------------------------------------------------------------
	// stage drawing (procedural, stepped/banded shading — no gradients)
	// ------------------------------------------------------------------

	private void drawStage(Graphics2D g, StageLayout st)
	{
		if (backdrop != null)
		{
			g.drawImage(backdrop, st.sx, st.sy, st.w, st.h, null);
			drawPegs(g, st);
			return;
		}

		// cavern air behind everything
		g.setColor(WALL_BASE);
		g.fillRect(st.sx, st.sy, st.w, st.h);

		// sheer rock wall face across the gap — horizontal banded shading
		int wallBottom = st.sy + (int) (st.h * 0.78);
		int bandH = Math.max(1, (wallBottom - st.sy) / WALL_BANDS.length);
		for (int i = 0; i < WALL_BANDS.length; i++)
		{
			g.setColor(WALL_BANDS[i]);
			g.fillRect(st.gapLeft, st.sy + i * bandH, st.gapRight - st.gapLeft,
				i == WALL_BANDS.length - 1 ? wallBottom - (st.sy + i * bandH) : bandH);
		}

		// deterministic cracks in the wall face (no randomness, no allocation)
		g.setColor(CRACK);
		g.setStroke(thinStroke);
		int gapW = st.gapRight - st.gapLeft;
		for (int i = 0; i < 9; i++)
		{
			int hsh = (i * 2654435761L & 0x7FFFFFFF) > 0 ? (int) ((i * 2654435761L) & 0x7FFF) : 1;
			int cx = st.gapLeft + (hsh % Math.max(1, gapW - 12)) + 6;
			int cy = st.sy + ((hsh * 31) % Math.max(1, (wallBottom - st.sy) - 30)) + 8;
			int len = 8 + (hsh % 14);
			int kink = ((hsh >> 3) % 7) - 3;
			g.drawLine(cx, cy, cx + kink, cy + len / 2);
			g.drawLine(cx + kink, cy + len / 2, cx + kink / 2, cy + len);
		}

		// darkened seams where the wall meets each ledge
		g.setColor(new Color(0, 0, 0, 70));
		g.fillRect(st.gapLeft, st.sy, Math.max(2, st.w / 90), wallBottom - st.sy);
		g.fillRect(st.gapRight - Math.max(2, st.w / 90), st.sy, Math.max(2, st.w / 90), wallBottom - st.sy);

		// chasm below the wall: stepped bands falling to pure black
		int chasmTop = wallBottom;
		int steps = 4;
		int stepH = Math.max(1, (st.sy + st.h - chasmTop) / steps);
		for (int i = 0; i < steps; i++)
		{
			g.setColor(new Color(0, 0, 0, 120 + i * 45));
			g.fillRect(st.gapLeft, chasmTop + i * stepH, gapW, stepH + 1);
		}
		g.setColor(Color.BLACK);
		g.fillRect(st.gapLeft, chasmTop + steps * stepH, gapW, st.sy + st.h - (chasmTop + steps * stepH));

		drawLedge(g, st, st.sx, st.ledgeW, false);
		drawLedge(g, st, st.gapRight, st.ledgeW, true);
		drawPegs(g, st);
	}

	private void drawLedge(Graphics2D g, StageLayout st, int x, int lw, boolean right)
	{
		int top = st.ledgeTopY;
		int bottom = st.sy + st.h;

		// rocky mass with a slight lip protruding into the gap
		int lip = Math.max(3, st.w / 110);
		int lx = right ? x - lip : x;
		int lwFull = lw + lip;

		g.setColor(LEDGE_FILL);
		g.fillRect(lx, top, lwFull, (int) (st.h * 0.14));
		g.setColor(LEDGE_FACE);
		g.fillRect(lx, top + (int) (st.h * 0.14), lwFull, (int) (st.h * 0.12));

		// stepped darkening down into the chasm
		int faceTop = top + (int) (st.h * 0.26);
		int steps = 3;
		int stepH = Math.max(1, (bottom - faceTop) / steps);
		for (int i = 0; i < steps; i++)
		{
			g.setColor(new Color(0, 0, 0, 90 + i * 55));
			g.fillRect(lx, faceTop, lwFull, bottom - faceTop);
			faceTop += stepH;
		}

		// walkable top highlight edge
		g.setColor(LEDGE_TOP);
		g.fillRect(lx, top, lwFull, Math.max(2, st.h / 90));

		// broken cliff-edge silhouette on the gap side
		g.setColor(ROCK_EDGE);
		int edgeX = right ? lx : lx + lwFull - 2;
		g.fillRect(edgeX, top, 2, (int) (st.h * 0.3));
	}

	private void drawPegs(Graphics2D g, StageLayout st)
	{
		int pw = Math.max(8, (int) (st.w * 0.026));
		int ph = Math.max(3, (int) (st.h * 0.02));
		for (int i = 0; i < 3; i++)
		{
			int x = st.pegX[i] - pw / 2;
			int y = st.pegY - ph / 2;
			g.setColor(PEG_DARK);
			g.fillRoundRect(x - 1, y - 1, pw + 2, ph + 2, ph, ph);
			g.setColor(PEG_WOOD);
			g.fillRoundRect(x, y, pw, ph, ph, ph);
			g.setColor(PEG_LIGHT);
			g.fillRect(x + 1, y, pw - 2, Math.max(1, ph / 3));
			// shadow cast on the wall below the peg
			g.setColor(new Color(0, 0, 0, 60));
			g.fillRect(x, y + ph + 1, pw, 2);
		}
	}

	private void drawStageFrame(Graphics2D g, StageLayout st)
	{
		// simple stone-interface double border, quest-cutscene style
		g.setStroke(thinStroke);
		g.setColor(ROCK_EDGE);
		g.drawRect(st.sx - 2, st.sy - 2, st.w + 3, st.h + 3);
		g.setColor(new Color(0x5D4E37));
		g.drawRect(st.sx - 1, st.sy - 1, st.w + 1, st.h + 1);
	}

	// ------------------------------------------------------------------
	// orb cage / prize
	// ------------------------------------------------------------------

	private void drawOrbCage(Graphics2D g, StageLayout st, SpawnedCreature target, double alpha)
	{
		if (alpha <= 0)
		{
			return;
		}
		Composite oc = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp01(alpha)));

		Color tier = target.isShiny() ? GOLD : target.getDef().getTier().getColor();

		// grounded shadow
		g.setColor(new Color(0, 0, 0, 90));
		g.fillOval(st.orbX - st.orbR, st.ledgeTopY - Math.max(2, st.orbR / 6),
			st.orbR * 2, Math.max(4, st.orbR / 3));

		// concentric glow — stepped translucent rings, brightest inner
		int[] ringAlpha = {28, 46, 70};
		for (int i = 0; i < 3; i++)
		{
			int r = st.orbR + (2 - i) * Math.max(3, st.orbR / 5);
			g.setColor(new Color(tier.getRed(), tier.getGreen(), tier.getBlue(), ringAlpha[i]));
			g.fillOval(st.orbX - r, st.orbY - r, r * 2, r * 2);
		}

		// glass sphere body
		g.setColor(new Color(PARCHMENT.getRed(), PARCHMENT.getGreen(), PARCHMENT.getBlue(), 46));
		g.fillOval(st.orbX - st.orbR, st.orbY - st.orbR, st.orbR * 2, st.orbR * 2);

		// captive silhouette, tier tinted, gentle bob
		SpawnedCreature t = target;
		BufferedImage sil = tinted(Archetype.of(t.getDef()).resource(), tier);
		if (sil != null)
		{
			int size = (int) (st.orbR * 1.25);
			int bob = (int) (Math.sin(animTick * 0.08) * st.orbR * 0.06);
			g.drawImage(sil, st.orbX - size / 2, st.orbY - size / 2 + bob, size, size, null);
		}

		// sphere rim + top-left highlight arc
		g.setStroke(thinStroke);
		g.setColor(new Color(PARCHMENT.getRed(), PARCHMENT.getGreen(), PARCHMENT.getBlue(), 150));
		g.drawOval(st.orbX - st.orbR, st.orbY - st.orbR, st.orbR * 2, st.orbR * 2);
		g.setColor(new Color(255, 250, 235, 130));
		g.drawArc(st.orbX - st.orbR + Math.max(2, st.orbR / 6), st.orbY - st.orbR + Math.max(2, st.orbR / 6),
			(int) (st.orbR * 1.1), (int) (st.orbR * 1.1), 70, 70);

		g.setComposite(oc);
	}

	/** After the catch: the empty cage sits open, creature claimed. */
	private void drawOpenCage(Graphics2D g, StageLayout st)
	{
		g.setColor(new Color(0, 0, 0, 90));
		g.fillOval(st.orbX - st.orbR, st.ledgeTopY - Math.max(2, st.orbR / 6),
			st.orbR * 2, Math.max(4, st.orbR / 3));
		g.setStroke(thinStroke);
		g.setColor(new Color(PARCHMENT.getRed(), PARCHMENT.getGreen(), PARCHMENT.getBlue(), 120));
		// two split-shell arcs
		g.drawArc(st.orbX - st.orbR, st.orbY - st.orbR + st.orbR / 4, st.orbR * 2, st.orbR * 2, 200, 140);
		g.drawArc(st.orbX - st.orbR, st.orbY - st.orbR - st.orbR / 4, st.orbR * 2, st.orbR * 2, 20, 140);
		g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 40));
		g.fillOval(st.orbX - st.orbR / 2, st.orbY - st.orbR / 2, st.orbR, st.orbR);
	}

	private void drawCageShatter(Graphics2D g, StageLayout st, SpawnedCreature target, double t)
	{
		// glass shard lines burst outward and fade over the first half
		double shard = clamp01(t / 0.5);
		if (shard < 1.0)
		{
			int a = (int) (200 * (1 - shard));
			g.setColor(new Color(PARCHMENT.getRed(), PARCHMENT.getGreen(), PARCHMENT.getBlue(), a));
			g.setStroke(shardStroke);
			for (int i = 0; i < 8; i++)
			{
				double ang = i * Math.PI / 4 + 0.35;
				double r0 = st.orbR * (0.35 + shard * 1.1);
				double r1 = r0 + st.orbR * (0.35 + 0.25 * ((i & 1) == 0 ? 1 : 0.4));
				g.drawLine(
					st.orbX + (int) (Math.cos(ang) * r0), st.orbY + (int) (Math.sin(ang) * r0),
					st.orbX + (int) (Math.cos(ang) * r1), st.orbY + (int) (Math.sin(ang) * r1));
			}
		}

		// freed creature slides off-stage right, fading
		Color tier = target.isShiny() ? GOLD : target.getDef().getTier().getColor();
		BufferedImage sil = tinted(Archetype.of(target.getDef()).resource(), tier);
		if (sil != null && t < 0.9)
		{
			Composite oc = g.getComposite();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1 - clamp01(t / 0.9))));
			int size = (int) (st.orbR * 1.25);
			int x = st.orbX + (int) (easeIn(t) * st.w * 0.45);
			g.drawImage(sil, x - size / 2, st.orbY - size / 2, size, size, null);
			g.setComposite(oc);
		}
	}

	// ------------------------------------------------------------------
	// the thrown orb (THROW phase)
	// ------------------------------------------------------------------

	private void drawThrownOrb(Graphics2D g, StageLayout st, double t)
	{
		if (t > 0.85)
		{
			return; // arrived — the cage takes over
		}
		String orbName = catchManager.getThrownOrb() == null
			? "unpowered" : catchManager.getThrownOrb().name().toLowerCase();
		BufferedImage orb = img("orb_" + orbName + ".png");

		double x0 = st.sx + st.w * 0.06;
		double y0 = st.sy + st.h * 0.92;
		double x1 = st.orbX;
		double y1 = st.orbY;
		double tt = t / 0.85;
		double x = x0 + (x1 - x0) * tt;
		double y = y0 + (y1 - y0) * tt - st.h * 0.55 * 4 * tt * (1 - tt);

		int size = (int) (st.orbR * 0.9);
		AffineTransform old = g.getTransform();
		g.translate(x, y);
		g.rotate(tt * Math.PI * 5);
		if (orb != null)
		{
			g.drawImage(orb, -size / 2, -size / 2, size, size, null);
		}
		else
		{
			g.setColor(GOLD);
			g.fillOval(-size / 2, -size / 2, size, size);
			g.setColor(OUTLINE);
			g.drawOval(-size / 2, -size / 2, size, size);
		}
		g.setTransform(old);
	}

	// ------------------------------------------------------------------
	// the Man — anchors and movement
	// ------------------------------------------------------------------

	/** Body-center x for anchor index: -1 left ledge, 0..2 pegs, 3 right ledge. */
	private double anchorX(StageLayout st, int idx)
	{
		if (idx < 0)
		{
			return st.leftStandX;
		}
		if (idx > 2)
		{
			return st.rightStandX;
		}
		return st.pegX[idx];
	}

	/** Body-center y for anchor index. */
	private double anchorY(StageLayout st, int idx)
	{
		if (idx < 0 || idx > 2)
		{
			return st.ledgeTopY - st.manH * 0.5;
		}
		return st.pegY + st.manH * 0.42; // hanging: hands at the peg, body below
	}

	private void drawManStanding(Graphics2D g, StageLayout st, int x, boolean cheering)
	{
		double bob = Math.sin(animTick * (cheering ? 0.22 : 0.09)) * st.manH * (cheering ? 0.05 : 0.02);
		double cy = st.ledgeTopY - st.manH * 0.5 - Math.max(0, cheering ? bob : bob);
		String stripName = cheering ? "cine_man_cheer.png" : "cine_man_stand.png";
		BufferedImage[] frames = strip(stripName, cheering ? 4 : 2);
		if (frames != null)
		{
			int f = (animTick / (cheering ? 8 : 20)) % frames.length;
			drawStripFrame(g, frames[f], x, cy, st.manH, 0);
		}
		else
		{
			drawProcMan(g, x, cy, st.manH, cheering ? POSE_CHEER : POSE_STAND, 0, 1.0);
		}
	}

	private void drawManJump(Graphics2D g, StageLayout st, int peg, double t)
	{
		int from = peg - 1;
		double x0 = anchorX(st, from);
		double y0 = anchorY(st, from);
		double x1 = anchorX(st, peg);
		double y1 = anchorY(st, peg);

		// anticipation squash before leaving the anchor
		double squashWindow = 0.12;
		double x;
		double y;
		double squash = 1.0;
		int pose;
		if (t < squashWindow)
		{
			x = x0;
			y = y0;
			squash = 1.0 - 0.14 * Math.sin((t / squashWindow) * Math.PI);
			pose = from < 0 || from > 2 ? POSE_STAND : POSE_HANG;
		}
		else
		{
			double m = (t - squashWindow) / (1 - squashWindow);
			double e = easeInOut(m);
			x = x0 + (x1 - x0) * e;
			y = y0 + (y1 - y0) * e - st.h * 0.13 * 4 * e * (1 - e); // parabolic hop
			pose = POSE_JUMP;
		}

		BufferedImage[] frames = strip("cine_man_jump.png", 4);
		if (frames != null && pose == POSE_JUMP)
		{
			int f = Math.min(3, (int) (t * 4));
			drawStripFrame(g, frames[f], x, y, st.manH, 0);
		}
		else if (pose == POSE_HANG)
		{
			drawManHangAt(g, st, from, squash);
		}
		else
		{
			AffineTransform old = g.getTransform();
			g.translate(x, y);
			g.scale(1.0, squash);
			drawProcMan(g, 0, 0, st.manH, pose, 0, 1.0);
			g.setTransform(old);
		}
	}

	private void drawManHanging(Graphics2D g, StageLayout st, int peg)
	{
		drawManHangAt(g, st, peg, 1.0);
	}

	private void drawManHangAt(Graphics2D g, StageLayout st, int peg, double squash)
	{
		if (peg < 0 || peg > 2)
		{
			return;
		}
		double sway = Math.sin(animTick * 0.12) * Math.toRadians(6);
		AffineTransform old = g.getTransform();
		g.translate(st.pegX[peg], st.pegY);
		g.rotate(sway);
		g.scale(1.0, squash);
		BufferedImage[] frames = strip("cine_man_hang.png", 4);
		if (frames != null)
		{
			int f = (animTick / 10) % 4;
			drawStripFrame(g, frames[f], 0, st.manH * 0.42, st.manH, 0);
		}
		else
		{
			drawProcMan(g, 0, st.manH * 0.42, st.manH, POSE_HANG, sway, 1.0);
		}
		g.setTransform(old);
	}

	private void drawManFalling(Graphics2D g, StageLayout st, int peg, double t)
	{
		double x0 = anchorX(st, peg);
		double y0 = anchorY(st, peg);
		double fallDist = (st.sy + st.h + st.manH * 1.6) - y0;
		double y = y0 + fallDist * t * t; // gravity
		double x = x0 + st.manH * 0.3 * t; // slight outward drift
		double rot = t * Math.PI * 2.4;

		BufferedImage[] frames = strip("cine_man_fall.png", 4);
		AffineTransform old = g.getTransform();
		g.translate(x, y);
		g.rotate(rot);
		if (frames != null)
		{
			int f = (animTick / 4) % 4;
			drawStripFrame(g, frames[f], 0, 0, st.manH, 0);
		}
		else
		{
			drawProcMan(g, 0, 0, st.manH, POSE_FALL, 0, 1.0);
		}
		g.setTransform(old);
	}

	private void drawStripFrame(Graphics2D g, BufferedImage frame, double cx, double cy, int manH, double rot)
	{
		int fh = manH;
		int fw = (int) ((double) frame.getWidth() / frame.getHeight() * fh);
		AffineTransform old = g.getTransform();
		g.translate(cx, cy);
		if (rot != 0)
		{
			g.rotate(rot);
		}
		g.drawImage(frame, -fw / 2, -fh / 2, fw, fh, null);
		g.setTransform(old);
	}

	// ------------------------------------------------------------------
	// procedural Man — filled head, tunic, trousers, jointed thick limbs
	// ------------------------------------------------------------------

	/**
	 * Draws the Man centered at (cx, cy) in the current transform, manH tall.
	 * Local body space: 44 units tall, y grows downward, origin at body center.
	 */
	private void drawProcMan(Graphics2D g, double cx, double cy, int manH, int pose, double sway, double alpha)
	{
		double u = manH / 44.0;
		AffineTransform old = g.getTransform();
		g.translate(cx, cy);

		Stroke oldStroke = g.getStroke();
		g.setStroke(limbStroke);

		// -- limb endpoints per pose, in body units --
		// arms: shoulder -> elbow -> hand; legs: hip -> knee -> foot
		double[] la;
		double[] ra;
		double[] ll;
		double[] rl;
		switch (pose)
		{
			case POSE_JUMP:
				la = new double[]{-5, -9, -9, -15, -12, -20};
				ra = new double[]{5, -9, 9, -14, 12, -19};
				ll = new double[]{-3, 2, -6, 10, -9, 15};
				rl = new double[]{3, 2, 7, 9, 11, 13};
				break;
			case POSE_HANG:
				la = new double[]{-5, -9, -4, -16, -2, -24};
				ra = new double[]{5, -9, 4, -16, 2, -24};
				ll = new double[]{-3, 2, -3, 12, -4 + Math.sin(animTick * 0.12 + 1.1) * 2, 21};
				rl = new double[]{3, 2, 3, 12, 4 + Math.sin(animTick * 0.12) * 2, 21};
				break;
			case POSE_FALL:
				la = new double[]{-5, -9, -10, -14, -13, -19};
				ra = new double[]{5, -9, 11, -10, 14, -4};
				ll = new double[]{-3, 2, -8, 8, -12, 13};
				rl = new double[]{3, 2, 7, 10, 10, 17};
				break;
			case POSE_CHEER:
			{
				double pump = Math.sin(animTick * 0.22) * 2;
				la = new double[]{-5, -9, -8, -16, -10, -24 - pump};
				ra = new double[]{5, -9, 8, -16, 10, -24 + pump};
				ll = new double[]{-3, 2, -3.5, 11, -4, 20};
				rl = new double[]{3, 2, 3.5, 11, 4, 20};
				break;
			}
			case POSE_STAND:
			default:
				la = new double[]{-5, -9, -6, -2, -6, 5};
				ra = new double[]{5, -9, 6, -2, 6, 5};
				ll = new double[]{-3, 2, -3.5, 11, -4, 20};
				rl = new double[]{3, 2, 3.5, 11, 4, 20};
				break;
		}

		// legs first (behind the tunic hem)
		g.setColor(TROUSERS);
		drawLimb(g, u, ll);
		drawLimb(g, u, rl);

		// torso: tan tunic trapezoid with a dark outline
		int[] tx = {r(-6 * u), r(6 * u), r(7 * u), r(-7 * u)};
		int[] ty = {r(-11 * u), r(-11 * u), r(4 * u), r(4 * u)};
		g.setColor(TUNIC);
		g.fillPolygon(tx, ty, 4);
		g.setStroke(thinStroke);
		g.setColor(OUTLINE);
		g.drawPolygon(tx, ty, 4);
		// belt band
		g.setColor(TROUSERS);
		g.fillRect(r(-7 * u), r(1.5 * u), r(14 * u), Math.max(1, r(2 * u)));

		// arms over the tunic
		g.setStroke(limbStroke);
		g.setColor(SKIN);
		drawLimb(g, u, la);
		drawLimb(g, u, ra);

		// head: filled skin circle, outlined, tiny hair cap
		int hr = Math.max(3, r(5 * u));
		g.setColor(SKIN);
		g.fillOval(r(-5 * u), r(-21 * u) - hr / 2, hr * 2, hr * 2);
		g.setColor(new Color(0x4A3520));
		g.fillArc(r(-5 * u), r(-21 * u) - hr / 2, hr * 2, hr * 2, 30, 120);
		g.setStroke(thinStroke);
		g.setColor(OUTLINE);
		g.drawOval(r(-5 * u), r(-21 * u) - hr / 2, hr * 2, hr * 2);

		g.setStroke(oldStroke);
		g.setTransform(old);
	}

	/** Draws a two-segment limb: {x0,y0, x1,y1, x2,y2} in body units. */
	private void drawLimb(Graphics2D g, double u, double[] p)
	{
		g.drawLine(r(p[0] * u), r(p[1] * u), r(p[2] * u), r(p[3] * u));
		g.drawLine(r(p[2] * u), r(p[3] * u), r(p[4] * u), r(p[5] * u));
	}

	private static int r(double v)
	{
		return (int) Math.round(v);
	}

	// ------------------------------------------------------------------
	// cheer sparkles, pips, narration
	// ------------------------------------------------------------------

	private void drawCheerSparkles(Graphics2D g, StageLayout st)
	{
		int rise = (int) (st.h * 0.30);
		for (int i = 0; i < 3; i++)
		{
			int cycle = (animTick * 2 + i * (rise / 3)) % rise;
			int x = st.orbX + (i - 1) * (int) (st.orbR * 0.9)
				+ (int) (Math.sin((animTick + i * 40) * 0.1) * st.orbR * 0.2);
			int y = st.orbY + st.orbR / 2 - cycle;
			double fade = 1.0 - (double) cycle / rise;
			int size = Math.max(6, (int) (st.h * 0.045 * (0.6 + 0.4 * fade)));
			Composite oc = g.getComposite();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp01(fade + 0.2)));
			if (sparkle != null)
			{
				g.drawImage(sparkle, x - size / 2, y - size / 2, size, size, null);
			}
			else
			{
				g.setColor(GOLD);
				g.fillRect(x - size / 6, y - size / 2, size / 3, size);
				g.fillRect(x - size / 2, y - size / 6, size, size / 3);
			}
			g.setComposite(oc);
		}
	}

	private void drawCheckPips(Graphics2D g, StageLayout st, int passed)
	{
		int d = Math.max(9, st.w / 46);
		int gap = d * 2;
		int cx = st.sx + st.w / 2;
		int y = st.sy + st.h + d;
		for (int i = 0; i < 3; i++)
		{
			int x = cx - gap - d / 2 + i * gap;
			g.setColor(i < passed ? GOLD : new Color(0x3E3529));
			g.fillOval(x, y, d, d);
			g.setColor(i < passed ? new Color(0x7A5C1E) : ROCK_EDGE);
			g.setStroke(thinStroke);
			g.drawOval(x, y, d, d);
			if (i < passed)
			{
				g.setColor(new Color(255, 245, 210, 160));
				g.fillOval(x + d / 4, y + d / 5, Math.max(2, d / 4), Math.max(2, d / 4));
			}
		}
	}

	private String narrationLine(CatchManager.Phase phase, int seg, int peg, SpawnedCreature target)
	{
		if (phase == CatchManager.Phase.THROW)
		{
			String orb = catchManager.getThrownOrb() == null
				? "orb" : catchManager.getThrownOrb().getDisplayName().toLowerCase();
			return "You throw the " + orb + "...";
		}
		switch (seg)
		{
			case CatchManager.SEG_INTRO:
				return "Steady...";
			case CatchManager.SEG_JUMP:
				if (peg >= 3)
				{
					return "The last leap!";
				}
				// fall through to the per-peg lines
			case CatchManager.SEG_GRIP:
				switch (peg)
				{
					case 0:
						return "First handhold!";
					case 1:
						return "Halfway across...";
					default:
						return "It's holding... it's holding...";
				}
			case CatchManager.SEG_FALL:
				return "He lost his grip!";
			case CatchManager.SEG_CHEER:
				return (target.isShiny() ? "✨ " : "")
					+ "Gotcha! " + target.displayName() + " was caught!";
			default:
				return "";
		}
	}

	private void drawNarration(Graphics2D g, int w, int barH, double barEase, String text)
	{
		if (text.isEmpty() || barH < 12)
		{
			return;
		}
		int size = Math.max(16, Math.min(22, w / 42));
		g.setFont(runeFont(size));
		FontMetrics fm = g.getFontMetrics();
		int tw = fm.stringWidth(text);
		int x = (w - tw) / 2;
		int y = barH / 2 + (fm.getAscent() - fm.getDescent()) / 2;

		Composite oc = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp01(barEase)));
		g.setColor(Color.BLACK);
		g.drawString(text, x + 2, y + 2);
		g.setColor(GOLD);
		g.drawString(text, x, y);
		g.setComposite(oc);
	}

	// ------------------------------------------------------------------
	// easing
	// ------------------------------------------------------------------

	private static double clamp01(double v)
	{
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	private static double easeIn(double t)
	{
		return t * t;
	}

	private static double easeOut(double t)
	{
		return 1 - (1 - t) * (1 - t);
	}

	private static double easeInOut(double t)
	{
		return t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t);
	}
}
