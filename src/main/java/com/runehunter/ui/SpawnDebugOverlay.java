package com.runehunter.ui;

import com.runehunter.RuneHunterConfig;
import com.runehunter.spawn.SpawnManager;
import com.runehunter.spawn.SpawnedCreature;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Dev/test overlay: marks every active spawn's tile in the 3D scene with its
 * name and tier color, plus the model's clickbox when available. Makes it
 * trivial to verify placement and rendering while playtesting.
 */
public class SpawnDebugOverlay extends Overlay
{
	private static final Color TILE_FILL = new Color(255, 0, 255, 40);

	private final Client client;
	private final RuneHunterConfig config;
	private final SpawnManager spawnManager;

	public SpawnDebugOverlay(Client client, RuneHunterConfig config, SpawnManager spawnManager)
	{
		this.client = client;
		this.config = config;
		this.spawnManager = spawnManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.devMarkSpawns() || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		for (SpawnedCreature s : spawnManager.getActiveSpawns())
		{
			LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), s.getWorldPoint());
			if (lp == null)
			{
				continue;
			}

			Color color = s.getDef().getTier().getColor();

			Polygon tile = Perspective.getCanvasTilePoly(client, lp);
			if (tile != null)
			{
				graphics.setColor(color);
				graphics.setStroke(new BasicStroke(2));
				graphics.draw(tile);
				graphics.setColor(TILE_FILL);
				graphics.fill(tile);
			}

			try
			{
				Shape clickbox = Perspective.getClickbox(client, client.getTopLevelWorldView(),
					s.getModel(), s.getObject().getOrientation(), lp.getX(), lp.getY(),
					Perspective.getTileHeight(client, lp, s.getWorldPoint().getPlane()));
				if (clickbox != null)
				{
					graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 120));
					graphics.draw(clickbox);
				}
			}
			catch (Exception ignored)
			{
				// clickbox math can fail off-screen; markers still drawn
			}

			int idx = com.runehunter.data.CreatureRoster.ALL_INCLUDING_SECRETS.indexOf(s.getDef()) + 1;
			String label = "#" + idx + " " + s.displayName()
				+ " (" + s.getWorldPoint().getX() + "," + s.getWorldPoint().getY() + ")";
			Point text = Perspective.getCanvasTextLocation(client, graphics, lp, label, 80);
			if (text != null)
			{
				OverlayUtil.renderTextLocation(graphics, text, label, color);
			}
		}

		return null;
	}
}
