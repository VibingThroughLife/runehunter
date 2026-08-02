package com.runehunter.game;

import com.runehunter.data.GearItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Gear a fainted creature dropped, lying on a real tile in Gielinor.
 *
 * <h2>Why it lives on a tile</h2>
 *
 * RuneHunter's whole premise is that the world is the game board. A death pile
 * belongs on the ground where you died, not in a dialog with a countdown — and it
 * is also the cheaper build, because {@code SpawnManager} already places
 * {@code RuneLiteObject}s at real tiles and re-places them on scene load. The pile
 * is just another spawned object.
 *
 * <h2>Why loot piles are safe here and weren't in 2007</h2>
 *
 * Jagex removed drop-on-death largely because players DDoSed opponents into
 * disconnecting, which dropped their items. That attack needed two things: a server
 * holding your inventory, and a disconnect that counted as a death.
 *
 * This plugin has neither. State is local config, and the duel layer already aborts
 * on a ten-second heartbeat. Hence the rule enforced by the fight layer, not here:
 * <b>disconnecting never drops your gear — only losing a fight does.</b> Knock
 * someone offline mid-fight and you get an aborted fight and nothing else.
 *
 * <h2>Timers</h2>
 *
 * Sixty seconds owner-only, then open to the party. Wall-clock, so it keeps running
 * while you are logged out — if you crash during the run back, it is gone. That is
 * the trade for having real stakes, and it is what the game actually was.
 */
public final class LootPile
{
	/** How long the pile stays open to everyone after the owner-only window. */
	public static final int OPEN_SECONDS = 120;

	private final String ownerName;
	private final List<GearItem> items;
	private final int worldX;
	private final int worldY;
	private final int plane;
	private final long droppedAt;

	public LootPile(String ownerName, List<GearItem> items, WorldPoint where, long droppedAt)
	{
		this.ownerName = ownerName;
		this.items = new ArrayList<>(items);
		this.worldX = where == null ? 0 : where.getX();
		this.worldY = where == null ? 0 : where.getY();
		this.plane = where == null ? 0 : where.getPlane();
		this.droppedAt = droppedAt;
	}

	private LootPile(String ownerName, List<GearItem> items, int x, int y, int plane, long droppedAt)
	{
		this.ownerName = ownerName;
		this.items = items;
		this.worldX = x;
		this.worldY = y;
		this.plane = plane;
		this.droppedAt = droppedAt;
	}

	public String getOwnerName()
	{
		return ownerName;
	}

	public List<GearItem> getItems()
	{
		return Collections.unmodifiableList(items);
	}

	public WorldPoint getLocation()
	{
		return new WorldPoint(worldX, worldY, plane);
	}

	public long getDroppedAt()
	{
		return droppedAt;
	}

	public boolean isEmpty()
	{
		return items.isEmpty();
	}

	/** Wall-clock millis since the pile dropped. */
	private long age(long now)
	{
		return now - droppedAt;
	}

	/** True while only the owner can see and take it. */
	public boolean isOwnerOnly(long now)
	{
		return age(now) < DeathStake.OWNER_ONLY_SECONDS * 1000L;
	}

	/** True once the pile has decayed. Contents are permanently lost. */
	public boolean isExpired(long now)
	{
		return age(now) >= (DeathStake.OWNER_ONLY_SECONDS + OPEN_SECONDS) * 1000L;
	}

	/** Seconds left before the owner-only window closes. Zero once it has. */
	public int secondsUntilOpen(long now)
	{
		final long left = DeathStake.OWNER_ONLY_SECONDS * 1000L - age(now);
		return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
	}

	/** Seconds until the pile decays for good. */
	public int secondsUntilGone(long now)
	{
		final long left = (DeathStake.OWNER_ONLY_SECONDS + OPEN_SECONDS) * 1000L - age(now);
		return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
	}

	/**
	 * Whether {@code viewerName} may see and take this pile right now.
	 *
	 * Note this is a display and interaction rule only. The authoritative transfer
	 * still happens through the nonce'd two-phase accept, on the owner's client —
	 * a modified client that renders a pile early still cannot take anything.
	 */
	public boolean canTake(String viewerName, long now)
	{
		if (isExpired(now) || items.isEmpty())
		{
			return false;
		}
		if (!isOwnerOnly(now))
		{
			return true;
		}
		return ownerName != null && ownerName.equalsIgnoreCase(viewerName);
	}

	/** Remove one item, e.g. when a player picks it up. */
	public boolean take(GearItem item)
	{
		return items.remove(item);
	}

	/** Countdown line for the overlay. */
	public String statusLine(String viewerName, long now)
	{
		if (isExpired(now))
		{
			return "Gone.";
		}
		if (isOwnerOnly(now))
		{
			final int s = secondsUntilOpen(now);
			return ownerName != null && ownerName.equalsIgnoreCase(viewerName)
				? "Yours for " + s + "s"
				: ownerName + "'s pile — open in " + s + "s";
		}
		return "Open to all — " + secondsUntilGone(now) + "s left";
	}

	// ------------------------------------------------------------------
	// Persistence — survives logout and scene reloads, like spawned creatures
	// ------------------------------------------------------------------

	/** {@code owner|x|y|plane|droppedAt|ITEM,ITEM} */
	public String serialize()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(ownerName == null ? "" : ownerName).append('|')
			.append(worldX).append('|').append(worldY).append('|').append(plane).append('|')
			.append(droppedAt).append('|');
		for (int i = 0; i < items.size(); i++)
		{
			if (i > 0)
			{
				sb.append(',');
			}
			sb.append(items.get(i).name());
		}
		return sb.toString();
	}

	/** Inverse of {@link #serialize()}. Returns null on anything malformed. */
	public static LootPile deserialize(String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return null;
		}
		final String[] p = raw.split("\\|", 6);
		if (p.length < 6)
		{
			return null;
		}
		try
		{
			final List<GearItem> items = new ArrayList<>(3);
			if (!p[5].isEmpty())
			{
				for (String name : p[5].split(","))
				{
					final GearItem g = GearItem.byName(name.trim());
					if (g != null)
					{
						items.add(g);
					}
				}
			}
			return new LootPile(p[0].isEmpty() ? null : p[0],
				items,
				Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
				Long.parseLong(p[4]));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
