package com.runehunter.ui;

import com.runehunter.RuneHunterPlugin;
import com.runehunter.data.Archetype;
import com.runehunter.data.CreatureDef;
import com.runehunter.data.GearItem;
import com.runehunter.game.BattleManager;
import com.runehunter.game.BattleSource;
import com.runehunter.storage.CollectionStore;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

/**
 * Full-screen REAL-TIME battle UI in the 2004 quest-cutscene style of
 * CatchCinematicOverlay: letterbox bars, a banded-shading arena stage, tinted
 * archetype silhouettes squaring up, parchment nameplates with stepped HP
 * bars — and an OSRS prayer-flick defense loop: the wild telegraphs each
 * attack over its head (style icon + countdown pips, skull for unavoidable,
 * purple for prayer-smite), and the player clicks overhead protection
 * prayers to deflect. Classic hitsplats, a draining prayer-points bar, a
 * deflect-charged SPECIAL button and a RUN button round out the action area.
 *
 * Renders any BattleSource: wild battles (BattleManager) take priority,
 * then PvP duels (party.DuelManager via setDuelSource) — duels get the gold
 * DUEL banner, the opponent's player name on the far nameplate, FORFEIT in
 * the RUN slot, and duel verdict lines.
 *
 * Everything is procedural Java2D — no gradients, no per-frame image work.
 * All clicks act on mousePressed via the source's thread-safe request
 * methods; presses/clicks over the UI are consumed so the game never sees
 * them.
 */
public class BattleOverlay extends Overlay implements MouseListener
{
	// ---- 2004 palette ----
	private static final Color BAR = Color.BLACK;
	private static final Color DIM = new Color(0, 0, 0, 89); // ~35%
	private static final Color GOLD = new Color(0xE8B84A);
	private static final Color GOLD_DARK = new Color(0x7A5C1E);
	private static final Color PARCHMENT = new Color(0xD8CCB4);
	private static final Color PARCH_EDGE = new Color(0x8A7B5E);
	private static final Color INK = new Color(0x3E3529);
	private static final Color WOOD = new Color(0x5D4E37);
	private static final Color ROCK_EDGE = new Color(0x14100B);

	// arena sky + ground, banded (dusk over a dueling glade)
	private static final Color[] SKY_BANDS =
	{
		new Color(0x2A241B),
		new Color(0x322B20),
		new Color(0x3A3122),
		new Color(0x453A26),
	};
	private static final Color[] GRASS_BANDS =
	{
		new Color(0x55683A),
		new Color(0x4C5D34),
		new Color(0x42512D),
		new Color(0x384526),
	};
	private static final Color GRASS_EDGE = new Color(0x2C371E);
	private static final Color MOUND_TOP = new Color(0x5E7340);
	private static final Color MOUND_SIDE = new Color(0x4A3F2F);

	// stone buttons
	private static final Color STONE_FACE = new Color(0x4B4237);
	private static final Color STONE_LIGHT = new Color(0x6B5A40);
	private static final Color STONE_DARK = new Color(0x2A2318);
	private static final Color DISABLED_TEXT = new Color(0x8C8478);

	// HP bar steps
	private static final Color HP_GREEN = new Color(0x3C7A28);
	private static final Color HP_GREEN_DARK = new Color(0x2C5A1E);
	private static final Color HP_YELLOW = new Color(0xB8A030);
	private static final Color HP_YELLOW_DARK = new Color(0x8A7820);
	private static final Color HP_RED = new Color(0x8C2B20);
	private static final Color HP_RED_DARK = new Color(0x661F16);
	private static final Color HP_EMPTY = new Color(0x201B14);

	// prayer + attack-style treatment
	private static final Color PRAYER_BLUE = new Color(0x3D7FBF);
	private static final Color PRAYER_BLUE_DARK = new Color(0x2A5988);
	private static final Color STYLE_MELEE = new Color(0xB23B2E);
	private static final Color STYLE_RANGED = new Color(0x3E8C43);
	private static final Color STYLE_MAGIC = new Color(0x3F6FB5);
	private static final Color STYLE_DISABLE = new Color(0x8A4FBF);
	private static final Color STYLE_SKULL = new Color(0xD8D2C4);
	private static final Color SKULL_BG = new Color(0x4A100A);
	private static final Color SMITE_BG = new Color(0x2A123E);

	// hitsplats
	private static final Color SPLAT_RED = new Color(0x9B1C10);
	private static final Color SPLAT_BLUE = new Color(0x2A4E9B);
	private static final int SPLAT_FRAMES = 20;
	private static final int DEFLECT_FRAMES = 12;
	private static final int SMITE_FRAMES = 18;

	// verdict colors
	private static final Color DEFEAT_BLUE = new Color(0x8FA3B8);

	private static final int LUNGE_FRAMES = 15;
	private static final Stroke THIN = new BasicStroke(1.4f);
	private static final Stroke ICON = new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final Stroke FLASH = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	private final Client client;
	private final BattleManager battleManager;
	private final CollectionStore store;
	private volatile BattleSource duelSource;
	private BattleSource lastSource;

	// image caches — misses cached as null so we never re-hit the loader
	private final Map<String, BufferedImage> imgCache = new HashMap<>();
	private final Map<String, BufferedImage> tintCache = new HashMap<>();
	private final Map<Integer, Font> boldCache = new HashMap<>();
	private final Map<Integer, Font> smallCache = new HashMap<>();
	private final BufferedImage sparkle;

	// click targets, rebuilt every render (setBounds only — no allocation)
	private final Rectangle stageRect = new Rectangle();
	private final Rectangle barTopRect = new Rectangle();
	private final Rectangle barBottomRect = new Rectangle();
	private final Rectangle actionBarRect = new Rectangle();
	private final Rectangle btnFight = new Rectangle();
	private final Rectangle btnSlip = new Rectangle();
	private final Rectangle btnPrayMelee = new Rectangle();
	private final Rectangle btnPrayRanged = new Rectangle();
	private final Rectangle btnPrayMagic = new Rectangle();
	private final Rectangle btnSpecial = new Rectangle();
	private final Rectangle btnRun = new Rectangle();
	private final Rectangle btnContinue = new Rectangle();

	// reused hitsplat polygon buffers (no per-frame allocation)
	private final int[] splatXs = new int[4];
	private final int[] splatYs = new int[4];

	// arena layout anchors (set each frame by drawArena, used by effects)
	private int wildCx;
	private int wildCy;
	private int wildSize;
	private int myCx;
	private int myCy;
	private int mySize;

	// hit / lunge / flash detection
	private int prevMyHp = -1;
	private int prevWildHp = -1;
	private int myLungeFrames;
	private int wildLungeFrames;
	private int seenMyHitSeq = -1;
	private int seenWildHitSeq = -1;
	private int seenDeflectSeq = -1;
	private int seenSmiteSeq = -1;
	private int mySplatFrames;
	private int mySplatValue;
	private boolean mySplatBlue;
	private int wildSplatFrames;
	private int wildSplatValue;
	private boolean wildSplatBlue;
	private int deflectFlashFrames;
	private int smiteFlashFrames;

	// log snapshot, refreshed only when the manager's log version moves
	private List<String> logSnapshot = Collections.emptyList();
	private int seenLogVersion = -1;

	private int animTick;

	public BattleOverlay(Client client, BattleManager battleManager, CollectionStore store)
	{
		this.client = client;
		this.battleManager = battleManager;
		this.store = store;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);

		sparkle = img("sparkle.png");
	}

	/** Wiring: battleOverlay.setDuelSource(partyHub.getDuelManager()). */
	public void setDuelSource(BattleSource source)
	{
		this.duelSource = source;
	}

	/** Wild battles take priority; otherwise an active duel; else nothing. */
	private BattleSource activeSource()
	{
		if (battleManager.getState() != BattleManager.State.IDLE)
		{
			return battleManager;
		}
		BattleSource duel = duelSource;
		if (duel != null && duel.getState() != BattleManager.State.IDLE)
		{
			return duel;
		}
		return null;
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
			// art slot missing — procedural fallback takes over
		}
		imgCache.put(resource, loaded);
		return loaded;
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

	private Font boldFont(int size)
	{
		Font f = boldCache.get(size);
		if (f == null)
		{
			f = FontManager.getRunescapeBoldFont().deriveFont((float) size);
			boldCache.put(size, f);
		}
		return f;
	}

	private Font smallFont(int size)
	{
		Font f = smallCache.get(size);
		if (f == null)
		{
			f = FontManager.getRunescapeSmallFont().deriveFont((float) size);
			smallCache.put(size, f);
		}
		return f;
	}

	// ------------------------------------------------------------------
	// render
	// ------------------------------------------------------------------

	@Override
	public Dimension render(Graphics2D g)
	{
		BattleSource src = activeSource();
		if (src == null)
		{
			animTick = 0;
			myLungeFrames = 0;
			wildLungeFrames = 0;
			mySplatFrames = 0;
			wildSplatFrames = 0;
			deflectFlashFrames = 0;
			smiteFlashFrames = 0;
			resyncCounters(lastSource != null ? lastSource : battleManager);
			lastSource = null;
			clearButtons();
			stageRect.setBounds(0, 0, 0, 0);
			barTopRect.setBounds(0, 0, 0, 0);
			barBottomRect.setBounds(0, 0, 0, 0);
			actionBarRect.setBounds(0, 0, 0, 0);
			return null;
		}
		if (src != lastSource)
		{
			// source switched (battle <-> duel): resync all event counters
			lastSource = src;
			resyncCounters(src);
			mySplatFrames = 0;
			wildSplatFrames = 0;
			deflectFlashFrames = 0;
			smiteFlashFrames = 0;
			seenLogVersion = -1;
			logSnapshot = Collections.emptyList();
		}
		BattleManager.State state = src.getState();
		CreatureDef wild = src.getWild();
		CreatureDef companion = src.getCompanion();
		if (wild == null || companion == null)
		{
			return null;
		}
		animTick++;
		detectEvents(src);
		refreshLog(src);

		int w = client.getCanvasWidth();
		int h = client.getCanvasHeight();

		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// letterbox + dim
		int barH = (int) (h * 0.11);
		g.setColor(DIM);
		g.fillRect(0, barH, w, h - 2 * barH);
		g.setColor(BAR);
		g.fillRect(0, 0, w, barH);
		g.fillRect(0, h - barH, w, barH);
		barTopRect.setBounds(0, 0, w, barH);
		barBottomRect.setBounds(0, h - barH, w, barH);

		// arena stage — ~60% width, 16:9, centered, nudged up for the action bar
		int stageW = Math.min(620, (int) (w * 0.60));
		int stageH = (int) (stageW * 9.0 / 16.0);
		int sx = (w - stageW) / 2;
		int sy = (h - stageH) / 2 - (int) (h * 0.045);
		stageRect.setBounds(sx, sy, stageW, stageH);

		clearButtons();
		drawArena(g, sx, sy, stageW, stageH, wild, companion, state);
		drawStageFrame(g, sx, sy, stageW, stageH);
		drawNameplates(g, src, sx, sy, stageW, stageH, wild, companion);
		if (src.isDuel())
		{
			drawDuelBanner(g, w, barH);
		}

		if (state == BattleManager.State.FIGHT)
		{
			drawTelegraph(g, src);
		}
		drawEffects(g);

		switch (state)
		{
			case PROMPT:
				drawPrompt(g, src, w, h, sx, sy, stageW, stageH);
				break;
			case FIGHT:
				drawFightBar(g, src, w, h, barH);
				break;
			case VICTORY:
			case DEFEAT:
			case FLED:
				drawVerdict(g, src, w, h, sx, sy, stageW, stageH, barH, state);
				break;
			default:
				break;
		}

		return new Dimension(w, h);
	}

	/** Sync the seen-seq counters to a source so no stale splats replay. */
	private void resyncCounters(BattleSource src)
	{
		prevMyHp = -1;
		prevWildHp = -1;
		seenMyHitSeq = src.getMyHitSeq();
		seenWildHitSeq = src.getWildHitSeq();
		seenDeflectSeq = src.getDeflectSeq();
		seenSmiteSeq = src.getSmiteSeq();
	}

	/** Gold "DUEL" tag centered in the top letterbox bar. */
	private void drawDuelBanner(Graphics2D g, int w, int barH)
	{
		if (barH < 14)
		{
			return;
		}
		g.setFont(boldFont(Math.max(14, barH / 2)));
		FontMetrics fm = g.getFontMetrics();
		String text = "— DUEL —";
		int x = (w - fm.stringWidth(text)) / 2;
		int y = barH / 2 + (fm.getAscent() - fm.getDescent()) / 2;
		g.setColor(Color.BLACK);
		g.drawString(text, x + 2, y + 2);
		g.setColor(GOLD);
		g.drawString(text, x, y);
	}

	private void clearButtons()
	{
		btnFight.setBounds(0, 0, 0, 0);
		btnSlip.setBounds(0, 0, 0, 0);
		btnPrayMelee.setBounds(0, 0, 0, 0);
		btnPrayRanged.setBounds(0, 0, 0, 0);
		btnPrayMagic.setBounds(0, 0, 0, 0);
		btnSpecial.setBounds(0, 0, 0, 0);
		btnRun.setBounds(0, 0, 0, 0);
		btnContinue.setBounds(0, 0, 0, 0);
	}

	/** Consume the source's hit/deflect/smite events into overlay animations. */
	private void detectEvents(BattleSource src)
	{
		// lunges: the attacker steps in when its target's HP drops
		int myHp = src.getMyHp();
		int wildHp = src.getWildHp();
		if (prevWildHp >= 0 && wildHp < prevWildHp)
		{
			myLungeFrames = LUNGE_FRAMES;
		}
		if (prevMyHp >= 0 && myHp < prevMyHp)
		{
			wildLungeFrames = LUNGE_FRAMES;
		}
		prevMyHp = myHp;
		prevWildHp = wildHp;

		// hitsplats + flashes, keyed off event sequence counters
		int seq = src.getMyHitSeq();
		if (seenMyHitSeq < 0)
		{
			seenMyHitSeq = seq;
		}
		else if (seq != seenMyHitSeq)
		{
			seenMyHitSeq = seq;
			mySplatFrames = SPLAT_FRAMES;
			mySplatValue = src.getMyHitDmg();
			mySplatBlue = mySplatValue == 0; // a 0-splat is a deflect
		}
		seq = src.getWildHitSeq();
		if (seenWildHitSeq < 0)
		{
			seenWildHitSeq = seq;
		}
		else if (seq != seenWildHitSeq)
		{
			seenWildHitSeq = seq;
			wildSplatFrames = SPLAT_FRAMES;
			wildSplatValue = src.getWildHitDmg();
			wildSplatBlue = src.isWildHitDeflect(); // opponent deflected (duels)
		}
		seq = src.getDeflectSeq();
		if (seenDeflectSeq < 0)
		{
			seenDeflectSeq = seq;
		}
		else if (seq != seenDeflectSeq)
		{
			seenDeflectSeq = seq;
			deflectFlashFrames = DEFLECT_FRAMES;
		}
		seq = src.getSmiteSeq();
		if (seenSmiteSeq < 0)
		{
			seenSmiteSeq = seq;
		}
		else if (seq != seenSmiteSeq)
		{
			seenSmiteSeq = seq;
			smiteFlashFrames = SMITE_FRAMES;
		}

		if (myLungeFrames > 0)
		{
			myLungeFrames--;
		}
		if (wildLungeFrames > 0)
		{
			wildLungeFrames--;
		}
		if (mySplatFrames > 0)
		{
			mySplatFrames--;
		}
		if (wildSplatFrames > 0)
		{
			wildSplatFrames--;
		}
		if (deflectFlashFrames > 0)
		{
			deflectFlashFrames--;
		}
		if (smiteFlashFrames > 0)
		{
			smiteFlashFrames--;
		}
	}

	/** getLog() copies — only re-snapshot when the log version moves. */
	private void refreshLog(BattleSource src)
	{
		int version = src.getLogVersion();
		if (version != seenLogVersion)
		{
			seenLogVersion = version;
			logSnapshot = src.getLog();
		}
	}

	// ------------------------------------------------------------------
	// arena (banded, no gradients)
	// ------------------------------------------------------------------

	private void drawArena(Graphics2D g, int sx, int sy, int sw, int sh,
		CreatureDef wild, CreatureDef companion, BattleManager.State state)
	{
		// dusk sky bands over the top ~55%
		int skyH = (int) (sh * 0.55);
		int bandH = Math.max(1, skyH / SKY_BANDS.length);
		for (int i = 0; i < SKY_BANDS.length; i++)
		{
			g.setColor(SKY_BANDS[i]);
			g.fillRect(sx, sy + i * bandH, sw,
				i == SKY_BANDS.length - 1 ? skyH - i * bandH : bandH);
		}

		// grassy dueling ground — stepped bands down to the stage edge
		int groundY = sy + skyH;
		int groundH = sh - skyH;
		int gBandH = Math.max(1, groundH / GRASS_BANDS.length);
		for (int i = 0; i < GRASS_BANDS.length; i++)
		{
			g.setColor(GRASS_BANDS[i]);
			g.fillRect(sx, groundY + i * gBandH, sw,
				i == GRASS_BANDS.length - 1 ? groundH - i * gBandH : gBandH);
		}
		g.setColor(GRASS_EDGE);
		g.fillRect(sx, groundY, sw, Math.max(2, sh / 90));

		// deterministic grass tufts (no randomness, no allocation)
		g.setColor(GRASS_EDGE);
		for (int i = 0; i < 14; i++)
		{
			int hsh = (int) ((i * 2654435761L) & 0x7FFF);
			int tx = sx + 8 + hsh % Math.max(1, sw - 16);
			int ty = groundY + 6 + (hsh * 31) % Math.max(1, groundH - 12);
			g.drawLine(tx, ty, tx - 2, ty - 4);
			g.drawLine(tx, ty, tx + 2, ty - 4);
		}

		// wild mound — raised earth platform, upper-right of the ground
		int moundW = (int) (sw * 0.28);
		int moundH = Math.max(6, (int) (sh * 0.075));
		int moundCx = sx + (int) (sw * 0.72);
		int moundTopY = groundY - (int) (sh * 0.02);
		g.setColor(MOUND_SIDE);
		g.fillOval(moundCx - moundW / 2, moundTopY - moundH / 2 + Math.max(2, moundH / 3),
			moundW, moundH);
		g.setColor(MOUND_TOP);
		g.fillOval(moundCx - moundW / 2, moundTopY - moundH / 2, moundW, moundH);
		g.setColor(GRASS_EDGE);
		g.drawOval(moundCx - moundW / 2, moundTopY - moundH / 2, moundW, moundH);

		// companion pad — flat ring lower-left
		int padW = (int) (sw * 0.30);
		int padH = Math.max(6, (int) (sh * 0.07));
		int padCx = sx + (int) (sw * 0.26);
		int padCy = sy + sh - (int) (sh * 0.10);
		g.setColor(GRASS_BANDS[3]);
		g.fillOval(padCx - padW / 2, padCy - padH / 2, padW, padH);
		g.setColor(GRASS_EDGE);
		g.drawOval(padCx - padW / 2, padCy - padH / 2, padW, padH);

		// combatants — silhouettes facing each other, bobbing, lunging on hits
		boolean wildDown = state == BattleManager.State.VICTORY;
		boolean mineDown = state == BattleManager.State.DEFEAT;

		wildSize = (int) (sh * 0.34);
		int wildBob = (int) (Math.sin(animTick * 0.07) * sh * 0.008);
		int wildLunge = wildLungeFrames > 0
			? (int) (sh * 0.03 * Math.sin(Math.PI * wildLungeFrames / (double) LUNGE_FRAMES)) : 0;
		wildCx = moundCx - wildLunge;
		wildCy = moundTopY - wildSize / 2 + wildBob;
		drawCombatant(g, wild, wild.getTier().getColor(), wildCx, wildCy, wildSize, true, wildDown);

		mySize = (int) (sh * 0.40);
		int myBob = (int) (Math.sin(animTick * 0.07 + 1.7) * sh * 0.008);
		int myLunge = myLungeFrames > 0
			? (int) (sh * 0.03 * Math.sin(Math.PI * myLungeFrames / (double) LUNGE_FRAMES)) : 0;
		myCx = padCx + myLunge;
		myCy = padCy - mySize / 2 + myBob;
		drawCombatant(g, companion, companion.getTier().getColor(), myCx, myCy, mySize, false, mineDown);
	}

	/**
	 * One combatant silhouette centered at (cx, cy). Wild faces left (flipped),
	 * companion faces right. Fainted combatants render faded and sunk.
	 */
	private void drawCombatant(Graphics2D g, CreatureDef def, Color tint,
		int cx, int cy, int size, boolean flip, boolean fainted)
	{
		Composite oc = g.getComposite();
		if (fainted)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
			cy += size / 5;
		}

		// grounded shadow
		g.setColor(new Color(0, 0, 0, 80));
		g.fillOval(cx - size / 3, cy + size / 2 - size / 14, (size * 2) / 3, size / 7);

		BufferedImage sil = tinted(Archetype.of(def).resource(), tint);
		if (sil != null)
		{
			if (flip)
			{
				g.drawImage(sil, cx + size / 2, cy - size / 2, cx - size / 2, cy + size / 2,
					0, 0, sil.getWidth(), sil.getHeight(), null);
			}
			else
			{
				g.drawImage(sil, cx - size / 2, cy - size / 2, size, size, null);
			}
		}
		else
		{
			// missing art — plain tinted blob with an outline
			g.setColor(tint);
			g.fillOval(cx - size / 3, cy - size / 3, (size * 2) / 3, (size * 2) / 3);
			g.setColor(ROCK_EDGE);
			g.drawOval(cx - size / 3, cy - size / 3, (size * 2) / 3, (size * 2) / 3);
		}
		g.setComposite(oc);
	}

	private void drawStageFrame(Graphics2D g, int sx, int sy, int sw, int sh)
	{
		Stroke old = g.getStroke();
		g.setStroke(THIN);
		g.setColor(ROCK_EDGE);
		g.drawRect(sx - 2, sy - 2, sw + 3, sh + 3);
		g.setColor(WOOD);
		g.drawRect(sx - 1, sy - 1, sw + 1, sh + 1);
		g.setStroke(old);
	}

	// ------------------------------------------------------------------
	// nameplates + HP bars
	// ------------------------------------------------------------------

	private void drawNameplates(Graphics2D g, BattleSource src, int sx, int sy, int sw, int sh,
		CreatureDef wild, CreatureDef companion)
	{
		int plateW = (int) (sw * 0.42);
		int plateH = Math.max(40, (int) (sh * 0.17));

		// far side: "Wild X" or "<player>'s X" for duels — top-left
		String owner = src.getWildOwnerName();
		String farTitle = (owner == null || owner.isEmpty() ? "Wild " : owner + "'s ")
			+ wild.getNpcName() + "  Lv " + src.getWildLevel();
		drawNameplate(g, sx + 8, sy + 8, plateW, plateH, farTitle,
			src.getWildHp(), src.getWildMaxHp());

		// companion: bottom-right
		drawNameplate(g, sx + sw - plateW - 8, sy + sh - plateH - 8, plateW, plateH,
			companion.getNpcName() + "  Lv " + src.getCompanionLevel(),
			src.getMyHp(), src.getMyMaxHp());
	}

	private void drawNameplate(Graphics2D g, int x, int y, int w, int h,
		String title, int hp, int maxHp)
	{
		// parchment box, double stone border
		g.setColor(PARCHMENT);
		g.fillRect(x, y, w, h);
		g.setColor(PARCH_EDGE);
		g.drawRect(x, y, w - 1, h - 1);
		g.setColor(ROCK_EDGE);
		g.drawRect(x - 1, y - 1, w + 1, h + 1);

		// title
		g.setFont(boldFont(Math.max(12, h / 3)));
		FontMetrics fm = g.getFontMetrics();
		g.setColor(INK);
		g.drawString(title, x + 6, y + fm.getAscent() + 3);

		// HP bar
		int barX = x + 6;
		int barY = y + h / 2 + 2;
		int barW = w - 12;
		int barH = Math.max(7, h / 5);
		drawHpBar(g, barX, barY, barW, barH, hp, maxHp);

		// cur/max, small, right-aligned under the bar
		g.setFont(smallFont(Math.max(11, h / 4)));
		FontMetrics sm = g.getFontMetrics();
		String frac = hp + "/" + maxHp;
		g.setColor(INK);
		g.drawString(frac, x + w - 6 - sm.stringWidth(frac), barY + barH + sm.getAscent() - 1);
	}

	private void drawHpBar(Graphics2D g, int x, int y, int w, int h, int hp, int maxHp)
	{
		double frac = maxHp <= 0 ? 0 : Math.max(0, Math.min(1.0, (double) hp / maxHp));
		Color fill;
		Color band;
		if (frac > 0.5)
		{
			fill = HP_GREEN;
			band = HP_GREEN_DARK;
		}
		else if (frac > 0.25)
		{
			fill = HP_YELLOW;
			band = HP_YELLOW_DARK;
		}
		else
		{
			fill = HP_RED;
			band = HP_RED_DARK;
		}

		g.setColor(HP_EMPTY);
		g.fillRect(x, y, w, h);

		int fillW = (int) (w * frac);
		if (fillW > 0)
		{
			// banded fill: main color, darker lower half, bright top strip
			g.setColor(fill);
			g.fillRect(x, y, fillW, h);
			g.setColor(band);
			g.fillRect(x, y + h / 2, fillW, h - h / 2);
			g.setColor(new Color(255, 255, 255, 50));
			g.fillRect(x, y, fillW, Math.max(1, h / 4));
		}

		// 20% notches + dark border
		g.setColor(ROCK_EDGE);
		for (int i = 1; i < 5; i++)
		{
			int nx = x + (w * i) / 5;
			g.drawLine(nx, y, nx, y + h - 1);
		}
		g.drawRect(x, y, w - 1, h - 1);
		g.setColor(new Color(0, 0, 0, 70));
		g.drawRect(x - 1, y - 1, w + 1, h + 1);
	}

	// ------------------------------------------------------------------
	// telegraph over the wild — style icon + countdown pips to impact
	// ------------------------------------------------------------------

	private void drawTelegraph(Graphics2D g, BattleSource src)
	{
		BattleManager.AttackStyle style = src.getIncomingStyle();
		if (style == null)
		{
			return;
		}
		int ticks = Math.max(0, src.getImpactTicks());
		int box = Math.max(30, Math.min(44, wildSize / 2));
		int cx = wildCx;
		int cy = wildCy - wildSize / 2 - box / 2 - 10;

		// urgency pulse as impact closes in
		if (ticks <= 1)
		{
			box += (animTick / 3) % 2 == 0 ? 3 : 0;
		}

		boolean skull = style == BattleManager.AttackStyle.UNAVOIDABLE;
		boolean smite = style == BattleManager.AttackStyle.DISABLE;
		Color edge = styleColor(style);

		// backdrop box — dark, colored double border (dark-red field for the
		// skull, purple field for the prayer-smite)
		g.setColor(skull ? SKULL_BG : smite ? SMITE_BG : new Color(0, 0, 0, 170));
		g.fillRect(cx - box / 2, cy - box / 2, box, box);
		g.setColor(edge);
		g.drawRect(cx - box / 2, cy - box / 2, box - 1, box - 1);
		g.setColor(ROCK_EDGE);
		g.drawRect(cx - box / 2 - 1, cy - box / 2 - 1, box + 1, box + 1);

		drawStyleIcon(g, cx, cy, (int) (box * 0.72), style, edge);

		// countdown pips shrinking to impact: pace-scaled slots, lit = left
		int totalPips = Math.max(1, Math.min(8, src.getTelegraphTicks()));
		int d = Math.max(5, box / 6);
		int gap = d + 3;
		int px0 = cx - gap * (totalPips - 1) / 2;
		int py = cy + box / 2 + 5;
		for (int i = 0; i < totalPips; i++)
		{
			boolean lit = i < ticks;
			g.setColor(lit ? edge : new Color(0, 0, 0, 150));
			g.fillOval(px0 + i * gap - d / 2, py, d, d);
			g.setColor(ROCK_EDGE);
			g.drawOval(px0 + i * gap - d / 2, py, d, d);
		}
	}

	private static Color styleColor(BattleManager.AttackStyle style)
	{
		switch (style)
		{
			case MELEE: return STYLE_MELEE;
			case RANGED: return STYLE_RANGED;
			case MAGIC: return STYLE_MAGIC;
			case DISABLE: return STYLE_DISABLE;
			default: return STYLE_SKULL;
		}
	}

	/**
	 * Procedural attack-style / prayer icons, OSRS-overhead flavored:
	 * crossed swords, arrow, spellbook, slashed prayer-sun, skull.
	 */
	private void drawStyleIcon(Graphics2D g, int cx, int cy, int s, BattleManager.AttackStyle style, Color color)
	{
		Stroke old = g.getStroke();
		g.setStroke(ICON);
		g.setColor(color);
		int r = s / 2;
		switch (style)
		{
			case MELEE:
				// two crossed swords with foot crossguards
				g.drawLine(cx - r, cy + r, cx + r, cy - r);
				g.drawLine(cx - r, cy - r, cx + r, cy + r);
				g.drawLine(cx - r, cy + r - s / 4, cx - r + s / 4, cy + r);
				g.drawLine(cx + r - s / 4, cy + r, cx + r, cy + r - s / 4);
				break;

			case RANGED:
				// arrow flying up-right: shaft, head, fletching
				g.drawLine(cx - r, cy + r, cx + r - 2, cy - r + 2);
				g.drawLine(cx + r - 2, cy - r + 2, cx + r - 2 - s / 3, cy - r + 3);
				g.drawLine(cx + r - 2, cy - r + 2, cx + r - 3, cy - r + 2 + s / 3);
				g.drawLine(cx - r, cy + r, cx - r + s / 4, cy + r - 1);
				g.drawLine(cx - r, cy + r, cx - r + 1, cy + r - s / 4);
				break;

			case MAGIC:
				// spellbook: cover, page split, small star above
				g.drawRect(cx - r + 1, cy - r / 3, s - 2, r + r / 3 - 1);
				g.drawLine(cx, cy - r / 3, cx, cy + r - 1);
				g.drawLine(cx - s / 5, cy - r + s / 8, cx + s / 5, cy - r + s / 8);
				g.drawLine(cx, cy - r, cx, cy - r + s / 4);
				break;

			case DISABLE:
				// prayer sun, slashed out
				g.drawOval(cx - r / 2, cy - r / 2, r, r);
				for (int i = 0; i < 4; i++)
				{
					double ang = i * Math.PI / 2 + Math.PI / 4;
					g.drawLine(
						cx + (int) (Math.cos(ang) * r * 0.6), cy + (int) (Math.sin(ang) * r * 0.6),
						cx + (int) (Math.cos(ang) * r), cy + (int) (Math.sin(ang) * r));
				}
				g.setColor(Color.WHITE);
				g.drawLine(cx - r, cy - r, cx + r, cy + r);
				break;

			default:
				// UNAVOIDABLE: skull — dome, eyes, jaw teeth
				g.setColor(color);
				g.fillOval(cx - r + 1, cy - r, s - 2, (int) (s * 0.68));
				g.fillRect(cx - r / 2, cy + r / 5, r, r / 2);
				g.setColor(SKULL_BG);
				int eye = Math.max(2, s / 5);
				g.fillOval(cx - r / 2, cy - r / 3, eye, eye);
				g.fillOval(cx + r / 2 - eye, cy - r / 3, eye, eye);
				g.drawLine(cx - r / 4, cy + r / 4, cx - r / 4, cy + r / 2 + r / 5);
				g.drawLine(cx, cy + r / 4, cx, cy + r / 2 + r / 5);
				g.drawLine(cx + r / 4, cy + r / 4, cx + r / 4, cy + r / 2 + r / 5);
				break;
		}
		g.setStroke(old);
	}

	// ------------------------------------------------------------------
	// combat effects: hitsplats, deflect flash, smite flash
	// ------------------------------------------------------------------

	private void drawEffects(Graphics2D g)
	{
		// deflect: brief gold arc shield in front of the companion
		if (deflectFlashFrames > 0)
		{
			float a = deflectFlashFrames / (float) DEFLECT_FRAMES;
			Composite oc = g.getComposite();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, a + 0.2f)));
			Stroke old = g.getStroke();
			g.setStroke(FLASH);
			int r = (int) (mySize * 0.55) + (DEFLECT_FRAMES - deflectFlashFrames);
			g.setColor(GOLD);
			g.drawArc(myCx - r, myCy - r, r * 2, r * 2, -25, 95); // faces the wild
			g.setColor(new Color(255, 245, 210));
			g.drawArc(myCx - r + 3, myCy - r + 3, r * 2 - 6, r * 2 - 6, -5, 55);
			g.setStroke(old);
			g.setComposite(oc);
		}

		// smite: purple burst ring over the companion
		if (smiteFlashFrames > 0)
		{
			float a = smiteFlashFrames / (float) SMITE_FRAMES;
			Composite oc = g.getComposite();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
			Stroke old = g.getStroke();
			g.setStroke(FLASH);
			int r = (int) (mySize * 0.35) + (SMITE_FRAMES - smiteFlashFrames) * 2;
			g.setColor(STYLE_DISABLE);
			g.drawOval(myCx - r, myCy - r, r * 2, r * 2);
			g.drawLine(myCx - r / 2, myCy - r / 2, myCx + r / 2, myCy + r / 2);
			g.drawLine(myCx + r / 2, myCy - r / 2, myCx - r / 2, myCy + r / 2);
			g.setStroke(old);
			g.setComposite(oc);
		}

		// classic hitsplats
		if (mySplatFrames > 0)
		{
			drawHitsplat(g, myCx, myCy - mySize / 6, mySplatValue, mySplatBlue, mySplatFrames);
		}
		if (wildSplatFrames > 0)
		{
			drawHitsplat(g, wildCx, wildCy - wildSize / 6, wildSplatValue, wildSplatBlue, wildSplatFrames);
		}
	}

	/** OSRS hitsplat: red (or blue-0) starburst square + white number. */
	private void drawHitsplat(Graphics2D g, int cx, int cy, int value, boolean blue, int frames)
	{
		float a = frames > 6 ? 1f : frames / 6f;
		Composite oc = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));

		int s = 26;
		Color body = blue ? SPLAT_BLUE : SPLAT_RED;
		// starburst = square + 45°-diamond overlapped
		g.setColor(body);
		g.fillRect(cx - s / 3, cy - s / 3, (s * 2) / 3, (s * 2) / 3);
		splatXs[0] = cx;
		splatXs[1] = cx + s / 2;
		splatXs[2] = cx;
		splatXs[3] = cx - s / 2;
		splatYs[0] = cy - s / 2;
		splatYs[1] = cy;
		splatYs[2] = cy + s / 2;
		splatYs[3] = cy;
		g.fillPolygon(splatXs, splatYs, 4);
		g.setColor(new Color(0, 0, 0, 90));
		g.drawPolygon(splatXs, splatYs, 4);

		g.setFont(boldFont(13));
		FontMetrics fm = g.getFontMetrics();
		String num = Integer.toString(value);
		int tx = cx - fm.stringWidth(num) / 2;
		int ty = cy + (fm.getAscent() - fm.getDescent()) / 2;
		g.setColor(Color.BLACK);
		g.drawString(num, tx + 1, ty + 1);
		g.setColor(Color.WHITE);
		g.drawString(num, tx, ty);
		g.setComposite(oc);
	}

	// ------------------------------------------------------------------
	// PROMPT
	// ------------------------------------------------------------------

	private void drawPrompt(Graphics2D g, BattleSource src, int w, int h, int sx, int sy, int sw, int sh)
	{
		String line = src.getPromptText();
		int size = Math.max(18, Math.min(26, w / 36));
		g.setFont(boldFont(size));
		FontMetrics fm = g.getFontMetrics();
		int tx = (w - fm.stringWidth(line)) / 2;
		int ty = sy - Math.max(12, fm.getHeight() / 2);
		g.setColor(Color.BLACK);
		g.drawString(line, tx + 2, ty + 2);
		g.setColor(GOLD);
		g.drawString(line, tx, ty);

		// two choices, centered under the stage
		int bw = Math.max(120, sw / 4);
		int bh = Math.max(30, sh / 8);
		int gap = bw / 4;
		int by = sy + sh + Math.max(14, sh / 14);
		btnFight.setBounds(w / 2 - bw - gap / 2, by, bw, bh);
		btnSlip.setBounds(w / 2 + gap / 2, by, bw, bh);
		drawButton(g, btnFight, "FIGHT", true, true);
		drawButton(g, btnSlip, src.isDuel() ? "DECLINE" : "SLIP AWAY", true, false);
	}

	// ------------------------------------------------------------------
	// FIGHT — prayer buttons, prayer bar, special, run, battle log
	// ------------------------------------------------------------------

	private void drawFightBar(Graphics2D g, BattleSource src, int w, int h, int letterboxH)
	{
		int pb = Math.max(38, Math.min(52, h / 14));   // prayer button size
		int gap = Math.max(6, pb / 6);
		int specialW = pb * 2 + gap;
		int runW = (pb * 3) / 2;
		int bigGap = gap * 3;
		int totalW = pb * 3 + gap * 2 + bigGap + specialW + bigGap + runW;
		int bx = (w - totalW) / 2;
		int by = h - letterboxH - pb - Math.max(8, h / 60);
		actionBarRect.setBounds(bx - gap, by - pb / 2 - gap, totalW + gap * 2, pb + pb / 2 + gap * 2);

		BattleManager.Prayer active = src.getActivePrayer();
		int lock = src.getPrayerLockTicks();

		// three overhead prayers — one lit at a time, click to flick
		btnPrayMelee.setBounds(bx, by, pb, pb);
		btnPrayRanged.setBounds(bx + pb + gap, by, pb, pb);
		btnPrayMagic.setBounds(bx + (pb + gap) * 2, by, pb, pb);
		drawPrayerButton(g, btnPrayMelee, BattleManager.AttackStyle.MELEE,
			active == BattleManager.Prayer.MELEE, lock);
		drawPrayerButton(g, btnPrayRanged, BattleManager.AttackStyle.RANGED,
			active == BattleManager.Prayer.RANGED, lock);
		drawPrayerButton(g, btnPrayMagic, BattleManager.AttackStyle.MAGIC,
			active == BattleManager.Prayer.MAGIC, lock);

		// slim prayer-points bar above the prayer trio, visibly draining
		int prayW = pb * 3 + gap * 2;
		int prayH = Math.max(5, pb / 8);
		int prayY = by - prayH - 6;
		drawPrayerPointsBar(g, src, bx, prayY, prayW, prayH);

		// SPECIAL — energy-filled stone button, lights at full charge
		int specialX = bx + prayW + bigGap;
		btnSpecial.setBounds(specialX, by, specialW, pb);
		drawSpecialButton(g, src, btnSpecial);

		// RUN (FORFEIT in duels) — corner of the action area
		int runX = specialX + specialW + bigGap;
		int runLock = src.getRunLockTicks();
		btnRun.setBounds(runX, by + pb / 4, runW, (pb * 3) / 4);
		String runLabel = src.isDuel() ? "FORFEIT" : (runLock > 0 ? "RUN (" + runLock + ")" : "RUN");
		drawButton(g, btnRun, runLabel, runLock <= 0, false);

		// battle log strip above the action area — most recent brightest
		int lines = logSnapshot.size();
		if (lines > 0)
		{
			g.setFont(smallFont(Math.max(12, h / 48)));
			FontMetrics fm = g.getFontMetrics();
			int lineH = fm.getHeight();
			int stripH = lineH * lines + 8;
			int stripY = prayY - 8 - stripH;
			g.setColor(new Color(PARCHMENT.getRed(), PARCHMENT.getGreen(), PARCHMENT.getBlue(), 205));
			g.fillRect(bx, stripY, totalW, stripH);
			g.setColor(PARCH_EDGE);
			g.drawRect(bx, stripY, totalW - 1, stripH - 1);
			g.setColor(ROCK_EDGE);
			g.drawRect(bx - 1, stripY - 1, totalW + 1, stripH + 1);

			for (int i = 0; i < lines; i++)
			{
				// oldest first in the snapshot; fade old lines into the parchment
				int age = lines - 1 - i;
				int alpha = Math.max(70, 255 - age * 60);
				g.setColor(new Color(INK.getRed(), INK.getGreen(), INK.getBlue(), alpha));
				g.drawString(logSnapshot.get(i), bx + 6, stripY + 4 + fm.getAscent() + i * lineH);
			}
		}
	}

	/** Stone prayer toggle: overhead icon, gold ring when lit, lock countdown. */
	private void drawPrayerButton(Graphics2D g, Rectangle r, BattleManager.AttackStyle icon,
		boolean active, int lockTicks)
	{
		boolean locked = lockTicks > 0;

		// stone face with banded top light / bottom dark
		g.setColor(STONE_FACE);
		g.fillRect(r.x, r.y, r.width, r.height);
		g.setColor(STONE_LIGHT);
		g.fillRect(r.x, r.y, r.width, Math.max(2, r.height / 5));
		g.setColor(STONE_DARK);
		g.fillRect(r.x, r.y + r.height - Math.max(2, r.height / 5), r.width, Math.max(2, r.height / 5));
		g.setColor(ROCK_EDGE);
		g.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1);

		if (active && !locked)
		{
			// lit overhead: gold double ring, OSRS active-prayer style
			g.setColor(GOLD);
			g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
			g.setColor(GOLD_DARK);
			g.drawRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3);
			g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 40));
			g.fillRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4);
		}
		else
		{
			g.setColor(WOOD);
			g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
		}

		int cx = r.x + r.width / 2;
		int cy = r.y + r.height / 2;
		Color iconColor = locked ? DISABLED_TEXT : active ? GOLD : PARCHMENT;
		drawStyleIcon(g, cx, cy, (int) (r.width * 0.62), icon, iconColor);

		if (locked)
		{
			// smite lockout shroud + countdown
			Composite oc = g.getComposite();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
			g.setColor(SMITE_BG);
			g.fillRect(r.x, r.y, r.width, r.height);
			g.setComposite(oc);
			g.setColor(STYLE_DISABLE);
			g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
			g.setFont(boldFont(Math.max(13, r.height / 2)));
			FontMetrics fm = g.getFontMetrics();
			String n = Integer.toString(lockTicks);
			g.setColor(Color.BLACK);
			g.drawString(n, cx - fm.stringWidth(n) / 2 + 1, cy + fm.getAscent() / 2);
			g.setColor(Color.WHITE);
			g.drawString(n, cx - fm.stringWidth(n) / 2, cy + fm.getAscent() / 2 - 1);
		}
	}

	private void drawPrayerPointsBar(Graphics2D g, BattleSource src, int x, int y, int w, int h)
	{
		int points = src.getPrayerPoints();
		int max = Math.max(1, src.getMaxPrayerPoints());
		double frac = Math.max(0, Math.min(1.0, (double) points / max));

		g.setColor(HP_EMPTY);
		g.fillRect(x, y, w, h);
		int fillW = (int) (w * frac);
		if (fillW > 0)
		{
			g.setColor(PRAYER_BLUE);
			g.fillRect(x, y, fillW, h);
			g.setColor(PRAYER_BLUE_DARK);
			g.fillRect(x, y + h / 2, fillW, h - h / 2);
			g.setColor(new Color(255, 255, 255, 50));
			g.fillRect(x, y, fillW, Math.max(1, h / 3));
		}
		g.setColor(ROCK_EDGE);
		g.drawRect(x, y, w - 1, h - 1);

		// points readout to the left of the bar
		g.setFont(smallFont(12));
		FontMetrics fm = g.getFontMetrics();
		String label = Integer.toString(points);
		g.setColor(Color.BLACK);
		g.drawString(label, x - fm.stringWidth(label) - 5, y + h + 1);
		g.setColor(points > 0 ? PRAYER_BLUE : HP_RED);
		g.drawString(label, x - fm.stringWidth(label) - 6, y + h);
	}

	/** SPECIAL: stone button whose face fills gold with deflect energy. */
	private void drawSpecialButton(Graphics2D g, BattleSource src, Rectangle r)
	{
		int energy = src.getSpecialEnergy();
		boolean ready = src.isSpecialReady();

		g.setColor(STONE_FACE);
		g.fillRect(r.x, r.y, r.width, r.height);

		// energy fill rises from the bottom, banded gold
		int fillH = (r.height * Math.min(BattleManager.SPECIAL_MAX, energy)) / BattleManager.SPECIAL_MAX;
		if (fillH > 0)
		{
			g.setColor(ready ? GOLD : GOLD_DARK);
			g.fillRect(r.x, r.y + r.height - fillH, r.width, fillH);
			g.setColor(new Color(255, 245, 210, ready ? 90 : 40));
			g.fillRect(r.x, r.y + r.height - fillH, r.width, Math.max(1, fillH / 4));
		}
		g.setColor(STONE_LIGHT);
		g.fillRect(r.x, r.y, r.width, Math.max(2, r.height / 6));

		g.setColor(ROCK_EDGE);
		g.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1);
		if (ready && (animTick / 8) % 2 == 0)
		{
			// pulsing gold ring when charged
			g.setColor(GOLD);
			g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
			g.setColor(GOLD_DARK);
			g.drawRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3);
		}
		else
		{
			g.setColor(WOOD);
			g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
		}

		g.setFont(boldFont(Math.max(13, r.height / 3)));
		FontMetrics fm = g.getFontMetrics();
		String label = ready ? "SPECIAL!" : "SPECIAL";
		int tx = r.x + (r.width - fm.stringWidth(label)) / 2;
		int ty = r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2 - 3;
		g.setColor(Color.BLACK);
		g.drawString(label, tx + 1, ty + 1);
		g.setColor(ready ? Color.WHITE : PARCHMENT);
		g.drawString(label, tx, ty);

		// tiny energy readout under the label
		g.setFont(smallFont(Math.max(10, r.height / 4)));
		FontMetrics sf = g.getFontMetrics();
		String pct = energy + "%";
		g.setColor(ready ? INK : DISABLED_TEXT);
		g.drawString(pct, r.x + (r.width - sf.stringWidth(pct)) / 2, r.y + r.height - 3);
	}

	// ------------------------------------------------------------------
	// verdict splashes
	// ------------------------------------------------------------------

	private void drawVerdict(Graphics2D g, BattleSource src, int w, int h, int sx, int sy, int sw, int sh,
		int letterboxH, BattleManager.State state)
	{
		int cy = sy + sh / 2;
		int size = Math.max(24, Math.min(38, w / 24));
		g.setFont(boldFont(size));
		FontMetrics fm = g.getFontMetrics();
		boolean duel = src.isDuel();

		if (state == BattleManager.State.VICTORY)
		{
			drawSparkleBurst(g, w / 2, cy - fm.getHeight(), sh);
			drawSplashText(g, duel ? "YOU WIN THE DUEL!" : "VICTORY!", w / 2, cy, GOLD, fm);

			g.setFont(boldFont(Math.max(14, size / 2)));
			FontMetrics sf = g.getFontMetrics();
			int liney = cy + sf.getHeight() + 6;
			String xpLine = victoryXpLine();
			if (xpLine != null)
			{
				drawSplashText(g, xpLine, w / 2, liney, PARCHMENT, sf);
				liney += sf.getHeight() + 2;
			}
			GearItem drop = src.getLastDrop();
			if (drop != null)
			{
				drawSplashText(g, "Loot: " + drop.getDisplayName() + "!", w / 2, liney,
					drop.getColor(), sf);
			}
		}
		else if (state == BattleManager.State.DEFEAT)
		{
			// the fainted companion is already drawn faded by the arena pass
			drawSplashText(g, duel ? "DEFEATED!" : "DEFEATED...", w / 2, cy, DEFEAT_BLUE, fm);
			if (duel)
			{
				g.setFont(boldFont(Math.max(14, size / 2)));
				FontMetrics sf = g.getFontMetrics();
				drawSplashText(g, "No faint — it was a friendly duel.", w / 2,
					cy + sf.getHeight() + 6, PARCHMENT, sf);
			}
		}
		else
		{
			drawSplashText(g, duel ? "Duel ended." : "Got away safely.", w / 2, cy, PARCHMENT, fm);
		}

		int bw = Math.max(130, sw / 4);
		int bh = Math.max(28, h / 22);
		btnContinue.setBounds((w - bw) / 2, h - letterboxH - bh - Math.max(8, h / 60), bw, bh);
		drawButton(g, btnContinue, "CONTINUE", true, true);
	}

	private void drawSplashText(Graphics2D g, String text, int cx, int y, Color color, FontMetrics fm)
	{
		int x = cx - fm.stringWidth(text) / 2;
		g.setColor(Color.BLACK);
		g.drawString(text, x + 2, y + 2);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	/** "Victory! +N xp ..." line straight from the battle log, if present. */
	private String victoryXpLine()
	{
		for (int i = logSnapshot.size() - 1; i >= 0; i--)
		{
			String line = logSnapshot.get(i);
			if (line.startsWith("Victory!"))
			{
				return line;
			}
		}
		return null;
	}

	private void drawSparkleBurst(Graphics2D g, int cx, int cy, int sh)
	{
		int rise = Math.max(30, (int) (sh * 0.5));
		for (int i = 0; i < 6; i++)
		{
			int cycle = (animTick * 2 + i * (rise / 6)) % rise;
			double ang = i * Math.PI / 3;
			int x = cx + (int) (Math.cos(ang) * (sh * 0.28 + Math.sin((animTick + i * 30) * 0.09) * 6));
			int y = cy - cycle / 3 + (int) (Math.sin(ang) * sh * 0.10);
			double fade = 1.0 - (double) cycle / rise;
			int size = Math.max(6, (int) (sh * 0.06 * (0.6 + 0.4 * fade)));
			Composite oc = g.getComposite();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
				(float) Math.min(1.0, fade + 0.2)));
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

	// ------------------------------------------------------------------
	// stone buttons
	// ------------------------------------------------------------------

	private void drawButton(Graphics2D g, Rectangle r, String label, boolean enabled, boolean highlighted)
	{
		// stone face with banded top light / bottom dark (no gradients)
		g.setColor(STONE_FACE);
		g.fillRect(r.x, r.y, r.width, r.height);
		g.setColor(STONE_LIGHT);
		g.fillRect(r.x, r.y, r.width, Math.max(2, r.height / 5));
		g.setColor(STONE_DARK);
		g.fillRect(r.x, r.y + r.height - Math.max(2, r.height / 5), r.width, Math.max(2, r.height / 5));

		g.setColor(ROCK_EDGE);
		g.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1);
		if (highlighted && enabled)
		{
			g.setColor(GOLD);
			g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
			g.setColor(GOLD_DARK);
			g.drawRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3);
		}
		else
		{
			g.setColor(WOOD);
			g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
		}

		g.setFont(boldFont(Math.max(13, r.height / 2)));
		FontMetrics fm = g.getFontMetrics();
		int tx = r.x + (r.width - fm.stringWidth(label)) / 2;
		int ty = r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2;
		g.setColor(Color.BLACK);
		g.drawString(label, tx + 1, ty + 1);
		g.setColor(!enabled ? DISABLED_TEXT : highlighted ? GOLD : PARCHMENT);
		g.drawString(label, tx, ty);
	}

	// ------------------------------------------------------------------
	// mouse input — buttons act on press via BattleManager's thread-safe
	// request methods; UI area swallows presses/clicks
	// ------------------------------------------------------------------

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		BattleSource src = activeSource();
		if (src == null)
		{
			return e;
		}
		int x = e.getX();
		int y = e.getY();

		switch (src.getState())
		{
			case PROMPT:
				if (btnFight.contains(x, y))
				{
					src.accept();
					e.consume();
					return e;
				}
				if (btnSlip.contains(x, y))
				{
					src.decline();
					e.consume();
					return e;
				}
				break;

			case FIGHT:
				if (btnPrayMelee.contains(x, y))
				{
					src.clickPrayer(BattleManager.Prayer.MELEE);
					e.consume();
					return e;
				}
				if (btnPrayRanged.contains(x, y))
				{
					src.clickPrayer(BattleManager.Prayer.RANGED);
					e.consume();
					return e;
				}
				if (btnPrayMagic.contains(x, y))
				{
					src.clickPrayer(BattleManager.Prayer.MAGIC);
					e.consume();
					return e;
				}
				if (btnSpecial.contains(x, y))
				{
					// the source ignores it unless the energy is full
					src.clickSpecial();
					e.consume();
					return e;
				}
				if (btnRun.contains(x, y))
				{
					src.clickRun();
					e.consume();
					return e;
				}
				break;

			case VICTORY:
			case DEFEAT:
			case FLED:
				if (btnContinue.contains(x, y))
				{
					src.dismiss();
					e.consume();
					return e;
				}
				break;

			default:
				break;
		}

		if (overUi(x, y))
		{
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		if (activeSource() != null && overUi(e.getX(), e.getY()))
		{
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		return e;
	}

	private boolean overUi(int x, int y)
	{
		return stageRect.contains(x, y)
			|| barTopRect.contains(x, y)
			|| barBottomRect.contains(x, y)
			|| actionBarRect.contains(x, y)
			|| btnFight.contains(x, y) || btnSlip.contains(x, y)
			|| btnPrayMelee.contains(x, y) || btnPrayRanged.contains(x, y)
			|| btnPrayMagic.contains(x, y) || btnSpecial.contains(x, y)
			|| btnRun.contains(x, y) || btnContinue.contains(x, y);
	}
}
