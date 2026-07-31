package com.runehunter.spawn;

import com.runehunter.data.CreatureDef;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Model;
import net.runelite.api.RuneLiteObject;

/** One live mini-creature placed in the scene, with lightweight wander state. */
public class SpawnedCreature
{
	private final CreatureDef def;
	private final boolean shiny;
	private final RuneLiteObject object;
	private final Model model;
	/** Personal spawns come from species lures and are not shared/seeded. */
	private final boolean personal;
	/** Tile the creature was placed on; the leash center for wandering. */
	private final WorldPoint anchor;

	private WorldPoint worldPoint;
	private int failedAttempts;
	private int currentAnim = -1;

	// -- wander movement state (client-tick interpolation) --
	private LocalPoint moveFrom;
	private LocalPoint moveTo;
	private WorldPoint moveTarget;
	private int moveTick;
	private int moveTicks;

	public SpawnedCreature(CreatureDef def, boolean shiny, WorldPoint worldPoint,
		RuneLiteObject object, Model model, boolean personal)
	{
		this.def = def;
		this.shiny = shiny;
		this.worldPoint = worldPoint;
		this.anchor = worldPoint;
		this.object = object;
		this.model = model;
		this.personal = personal;
	}

	public CreatureDef getDef()
	{
		return def;
	}

	public boolean isShiny()
	{
		return shiny;
	}

	public WorldPoint getWorldPoint()
	{
		return worldPoint;
	}

	public WorldPoint getAnchor()
	{
		return anchor;
	}

	public RuneLiteObject getObject()
	{
		return object;
	}

	public Model getModel()
	{
		return model;
	}

	public boolean isPersonal()
	{
		return personal;
	}

	public int getFailedAttempts()
	{
		return failedAttempts;
	}

	public void setFailedAttempts(int failedAttempts)
	{
		this.failedAttempts = failedAttempts;
	}

	public int getCurrentAnim()
	{
		return currentAnim;
	}

	public void setCurrentAnim(int currentAnim)
	{
		this.currentAnim = currentAnim;
	}

	public boolean isMoving()
	{
		return moveTo != null;
	}

	public void beginMove(LocalPoint from, LocalPoint to, WorldPoint target, int clientTicks)
	{
		this.moveFrom = from;
		this.moveTo = to;
		this.moveTarget = target;
		this.moveTick = 0;
		this.moveTicks = Math.max(1, clientTicks);
	}

	/**
	 * Advance one client tick of movement; repositions the object along the
	 * path. Returns true when the move just completed.
	 */
	public boolean advanceMove(net.runelite.api.WorldView view)
	{
		if (moveTo == null)
		{
			return false;
		}
		moveTick++;
		double t = Math.min(1.0, (double) moveTick / moveTicks);
		int x = (int) Math.round(moveFrom.getX() + (moveTo.getX() - moveFrom.getX()) * t);
		int y = (int) Math.round(moveFrom.getY() + (moveTo.getY() - moveFrom.getY()) * t);
		object.setLocation(new LocalPoint(x, y, view), view.getPlane());
		if (t >= 1.0)
		{
			worldPoint = moveTarget;
			moveFrom = null;
			moveTo = null;
			moveTarget = null;
			return true;
		}
		return false;
	}

	public String displayName()
	{
		return (shiny ? "Shiny " : "") + def.getNpcName();
	}
}
