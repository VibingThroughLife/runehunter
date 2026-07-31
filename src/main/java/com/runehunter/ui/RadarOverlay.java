package com.runehunter.ui;

import com.runehunter.RuneHunterConfig;
import com.runehunter.RuneHunterPlugin;
import com.runehunter.data.Archetype;
import com.runehunter.data.CreatureDef;
import com.runehunter.spawn.SpawnManager;
import com.runehunter.spawn.SpawnedCreature;
import com.runehunter.storage.CollectionStore;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

/**
 * The "Nearby" tracker, Pokemon-GO style with an OSRS parchment-and-stone
 * look: tinted archetype silhouettes (dark mystery shapes for uncaught),
 * paw-print proximity pips (one paw = breathing distance, three = far), a
 * pulsing glow on the closest creature, and a sparkle marker for shinies.
 */
public class RadarOverlay extends Overlay
{
	private static final int WIDTH = 162;
	private static final int ROW_H = 24;
	private static final int HEADER_H = 24;
	private static final int PAD = 6;
	private static final int MAX_SHOWN = 3;

	private static final Color BG = new Color(24, 22, 18, 235);
	private static final Color BORDER_GOLD = new Color(120, 96, 50);
	private static final Color BORDER_DARK = new Color(12, 10, 8);
	private static final Color GOLD = new Color(0xE8B84A);
	private static final Color PARCHMENT = new Color(0xD8CCB4);
	private static final Color UNCAUGHT = new Color(0x8A8A8A);
	private static final Color ROW_BG = new Color(38, 34, 28, 160);
	private static final Color PAW_DIM = new Color(70, 62, 50);

	private final Client client;
	private final RuneHunterConfig config;
	private final SpawnManager spawnManager;
	private final CollectionStore store;

	private final Map<String, BufferedImage> tintCache = new HashMap<>();
	private BufferedImage logo;
	private BufferedImage sparkle;
	private boolean assetsLoaded;
	private int pulseTick;

	public RadarOverlay(Client client, RuneHunterConfig config, SpawnManager spawnManager, CollectionStore store)
	{
		this.client = client;
		this.config = config;
		this.spawnManager = spawnManager;
		this.store = store;
		setPosition(OverlayPosition.TOP_RIGHT);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showRadar() || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		// Secret Dex creatures are never tracked — walking past one without
		// realising is the entire point — so they're filtered out before the
		// list is even counted ("N about" must not tick up either).
		List<SpawnedCreature> spawns = new ArrayList<>();
		for (SpawnedCreature s : spawnManager.getActiveSpawns())
		{
			if (!s.getDef().isSecret())
			{
				spawns.add(s);
			}
		}
		if (spawns.isEmpty())
		{
			return null;
		}
		loadAssets();
		pulseTick++;

		WorldPoint p = player.getWorldLocation();
		spawns.sort(Comparator.comparingInt(s -> s.getWorldPoint().distanceTo(p)));
		int shown = Math.min(MAX_SHOWN, spawns.size());
		int height = HEADER_H + shown * ROW_H + PAD;

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		// card: dark stone with double border (banded, OSRS style)
		g.setColor(BG);
		g.fillRoundRect(0, 0, WIDTH, height, 8, 8);
		g.setColor(BORDER_DARK);
		g.setStroke(new BasicStroke(3));
		g.drawRoundRect(1, 1, WIDTH - 3, height - 3, 8, 8);
		g.setColor(BORDER_GOLD);
		g.setStroke(new BasicStroke(1));
		g.drawRoundRect(0, 0, WIDTH - 1, height - 1, 8, 8);

		// header: paw logo + title + count
		if (logo != null)
		{
			g.drawImage(logo, PAD, (HEADER_H - 16) / 2 + 1, 16, 16, null);
		}
		g.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
		g.setColor(BORDER_DARK);
		g.drawString("Nearby", PAD + 21, 17);
		g.setColor(GOLD);
		g.drawString("Nearby", PAD + 20, 16);

		String count = spawns.size() + " about";
		g.setFont(FontManager.getRunescapeSmallFont());
		int cw = g.getFontMetrics().stringWidth(count);
		g.setColor(PARCHMENT);
		g.drawString(count, WIDTH - PAD - cw, 16);

		// divider
		g.setColor(BORDER_GOLD);
		g.drawLine(PAD, HEADER_H - 2, WIDTH - PAD, HEADER_H - 2);

		int y = HEADER_H + 2;
		for (int i = 0; i < shown; i++)
		{
			drawRow(g, spawns.get(i), p, y, i == 0);
			y += ROW_H;
		}

		return new Dimension(WIDTH, height);
	}

	private void drawRow(Graphics2D g, SpawnedCreature s, WorldPoint playerPos, int y, boolean closest)
	{
		boolean caught = store.isCaught(s.getDef());
		int dist = s.getWorldPoint().distanceTo(playerPos);

		// row backing; the closest creature's row pulses softly
		if (closest)
		{
			double pulse = 0.5 + 0.5 * Math.sin(pulseTick * 0.12);
			Color tier = s.getDef().getTier().getColor();
			g.setColor(new Color(tier.getRed(), tier.getGreen(), tier.getBlue(),
				(int) (28 + 34 * pulse)));
			g.fillRoundRect(PAD - 2, y, WIDTH - 2 * PAD + 4, ROW_H - 3, 6, 6);
		}
		else
		{
			g.setColor(ROW_BG);
			g.fillRoundRect(PAD - 2, y, WIDTH - 2 * PAD + 4, ROW_H - 3, 6, 6);
		}

		// silhouette: tier-tinted when caught, near-black mystery when not
		BufferedImage sil = silhouette(s.getDef(), caught);
		if (sil != null)
		{
			g.drawImage(sil, PAD + 2, y + (ROW_H - 18) / 2 - 1, 18, 18, null);
		}

		// name (or ???)
		g.setFont(FontManager.getRunescapeSmallFont());
		String name = caught ? s.getDef().getNpcName() : "???";
		if (name.length() > 14)
		{
			name = name.substring(0, 13) + "…";
		}
		g.setColor(BORDER_DARK);
		g.drawString(name, PAD + 25, y + 15);
		g.setColor(caught ? s.getDef().getTier().getColor() : UNCAUGHT);
		g.drawString(name, PAD + 24, y + 14);

		// shiny sparkle beside the name
		if (s.isShiny() && sparkle != null)
		{
			int nw = g.getFontMetrics().stringWidth(name);
			g.drawImage(sparkle, PAD + 27 + nw, y + 3, 10, 10, null);
		}

		// paw pips, right-aligned: 1 paw = close (<=15), 2 = mid (<=45), 3 = far
		int paws = dist <= 15 ? 1 : dist <= 45 ? 2 : 3;
		int px = WIDTH - PAD - 3 * 10;
		for (int i = 0; i < 3; i++)
		{
			drawPaw(g, px + i * 10, y + 8, i < paws ? GOLD : PAW_DIM);
		}
	}

	/** Tiny paw print: pad + three toes. */
	private void drawPaw(Graphics2D g, int x, int y, Color color)
	{
		g.setColor(color);
		g.fillOval(x + 1, y + 2, 6, 5);      // pad
		g.fillOval(x, y - 1, 2, 3);          // toes
		g.fillOval(x + 3, y - 2, 2, 3);
		g.fillOval(x + 6, y - 1, 2, 3);
	}

	private void loadAssets()
	{
		if (assetsLoaded)
		{
			return;
		}
		assetsLoaded = true;
		logo = safeLoad("icon.png");
		sparkle = safeLoad("sparkle.png");
	}

	private static BufferedImage safeLoad(String resource)
	{
		try
		{
			return ImageUtil.loadImageResource(RuneHunterPlugin.class, resource);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private BufferedImage silhouette(CreatureDef d, boolean caught)
	{
		String key = d.key() + (caught ? ":c" : ":u");
		BufferedImage cached = tintCache.get(key);
		if (cached != null || tintCache.containsKey(key))
		{
			return cached;
		}
		BufferedImage src = safeLoad(Archetype.of(d).resource());
		BufferedImage out = null;
		if (src != null)
		{
			Color tint = caught ? d.getTier().getColor() : new Color(0x2E2C28);
			out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
			int rgb = tint.getRGB() & 0x00FFFFFF;
			for (int yy = 0; yy < src.getHeight(); yy++)
			{
				for (int xx = 0; xx < src.getWidth(); xx++)
				{
					out.setRGB(xx, yy, (src.getRGB(xx, yy) & 0xFF000000) | rgb);
				}
			}
		}
		tintCache.put(key, out);
		return out;
	}
}
