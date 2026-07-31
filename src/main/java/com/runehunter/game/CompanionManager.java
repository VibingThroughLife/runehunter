package com.runehunter.game;

import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import com.runehunter.spawn.AnimationLearner;
import com.runehunter.spawn.NpcModelCache;
import com.runehunter.storage.CollectionStore;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Your caught creature follows you around (client-side). Simple tile-follow:
 * the companion hops to your previous tile whenever you move away.
 */
public class CompanionManager
{
	private final Client client;
	private final NpcModelCache modelCache;
	private final AnimationLearner animations;
	private final CollectionStore store;

	private static final int MOVE_TICKS_PER_TILE = 24; // slightly quicker than walk, keeps up

	private java.util.function.BooleanSupplier faintCheck = () -> false;

	private RuneLiteObject object;
	private String activeKey;
	private CreatureDef activeDef;
	private WorldPoint companionTile;
	private WorldPoint lastPlayerTile;
	private int currentAnim = -1;
	private int walkTicks;

	// smooth movement (client-tick lerp)
	private LocalPoint moveFrom;
	private LocalPoint moveTo;
	private WorldPoint moveTarget;
	private int moveTick;
	private int moveTicks;

	public CompanionManager(Client client, NpcModelCache modelCache,
		AnimationLearner animations, CollectionStore store)
	{
		this.client = client;
		this.modelCache = modelCache;
		this.animations = animations;
		this.store = store;
	}

	public void setFaintCheck(java.util.function.BooleanSupplier faintCheck)
	{
		this.faintCheck = faintCheck;
	}

	/** Call every game tick. */
	public void tick()
	{
		String key = store.getCompanionKey();
		if (key == null || client.getGameState() != GameState.LOGGED_IN || faintCheck.getAsBoolean())
		{
			remove();
			return;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		WorldPoint playerTile = player.getWorldLocation();

		if (!key.equals(activeKey) || object == null)
		{
			spawnCompanion(key, playerTile);
		}

		if (object == null)
		{
			return;
		}

		// The player's WorldPoint is their TRUE (server-side) tile — the tile
		// the game actually has them on, not where the model is mid-walk.
		// Follow rules against true tiles so we never overlap the player:
		// 1) player walked onto our tile -> step aside
		// 2) player moved away -> walk to the tile they just left (never their
		//    current true tile)
		if (companionTile != null && companionTile.equals(playerTile) && !isMoving())
		{
			stepAside(playerTile);
		}
		else if (companionTile == null || (!isMoving() && companionTile.distanceTo(playerTile) > 1))
		{
			WorldPoint target = lastPlayerTile != null && !lastPlayerTile.equals(companionTile)
				? lastPlayerTile : playerTile;
			if (target.equals(playerTile) && companionTile != null)
			{
				// don't walk onto the player's true tile
				target = companionTile;
			}
			if (!target.equals(companionTile))
			{
				if (companionTile != null && companionTile.distanceTo(target) <= 3)
				{
					startMove(target);
				}
				else
				{
					// too far (teleport, big gap) — snap
					LocalPoint lp = LocalPoint.fromWorld(client, target);
					if (lp != null)
					{
						object.setLocation(lp, target.getPlane());
						companionTile = target;
					}
					else
					{
						// Companion left the scene (teleport etc.) — respawn next tick
						remove();
						activeKey = null;
					}
				}
				faceToward(playerTile);
			}
		}

		// Walk animation while moving, idle when settled
		if (object != null && activeDef != null)
		{
			int idle = animations.idleAnim(activeDef);
			int walk = animations.walkAnim(activeDef);
			int desired = (isMoving() || walkTicks > 0) && walk > 0 ? walk : idle;
			setAnim(desired);
			if (walkTicks > 0)
			{
				walkTicks--;
			}
		}

		lastPlayerTile = playerTile;
	}

	/** Call every client tick: advances smooth movement. */
	public void clientTick()
	{
		if (object == null || moveTo == null)
		{
			return;
		}
		moveTick++;
		double t = Math.min(1.0, (double) moveTick / moveTicks);
		int x = (int) Math.round(moveFrom.getX() + (moveTo.getX() - moveFrom.getX()) * t);
		int y = (int) Math.round(moveFrom.getY() + (moveTo.getY() - moveFrom.getY()) * t);
		object.setLocation(new LocalPoint(x, y, client.getTopLevelWorldView()),
			client.getTopLevelWorldView().getPlane());
		if (t >= 1.0)
		{
			companionTile = moveTarget;
			moveFrom = null;
			moveTo = null;
			moveTarget = null;
		}
	}

	private boolean isMoving()
	{
		return moveTo != null;
	}

	private void startMove(WorldPoint target)
	{
		LocalPoint from = companionTile != null
			? LocalPoint.fromWorld(client, companionTile) : null;
		LocalPoint to = LocalPoint.fromWorld(client, target);
		if (from == null || to == null)
		{
			return;
		}
		moveFrom = from;
		moveTo = to;
		moveTarget = target;
		moveTick = 0;
		moveTicks = MOVE_TICKS_PER_TILE * Math.max(1, companionTile.distanceTo(target));
		// face the direction of travel
		int dx = Integer.compare(target.getX(), companionTile.getX());
		int dy = Integer.compare(target.getY(), companionTile.getY());
		faceDirection(dx, dy);
		walkTicks = 2;
	}

	/** Player stepped onto our true tile — scoot to a free adjacent tile. */
	private void stepAside(WorldPoint playerTile)
	{
		int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}};
		// prefer stepping toward where the player came FROM (they vacated it)
		if (lastPlayerTile != null && !lastPlayerTile.equals(playerTile))
		{
			startMove(lastPlayerTile);
			return;
		}
		for (int[] dir : dirs)
		{
			WorldPoint cand = new WorldPoint(playerTile.getX() + dir[0],
				playerTile.getY() + dir[1], playerTile.getPlane());
			if (LocalPoint.fromWorld(client, cand) != null)
			{
				startMove(cand);
				return;
			}
		}
	}

	private void faceDirection(int dx, int dy)
	{
		if (object == null || (dx == 0 && dy == 0))
		{
			return;
		}
		int jau = (int) Math.round(Math.atan2(-dx, -dy) / (2 * Math.PI) * 2048);
		object.setOrientation(((jau % 2048) + 2048) % 2048);
	}

	private void setAnim(int animId)
	{
		if (animId <= 0 || animId == currentAnim || object == null)
		{
			return;
		}
		AnimationController ac = new AnimationController(client, animId);
		ac.setOnFinished(AnimationController::loop);
		object.setAnimationController(ac);
		currentAnim = animId;
	}

	private void spawnCompanion(String key, WorldPoint playerTile)
	{
		remove();
		CreatureDef def = CreatureRoster.byKey(key);
		if (def == null || !store.isCaught(def))
		{
			return;
		}
		boolean shiny = store.isShinyCaught(def);
		Model model = modelCache.getModel(def, shiny);
		if (model == null)
		{
			return;
		}
		WorldPoint spot = new WorldPoint(playerTile.getX() - 1, playerTile.getY(), playerTile.getPlane());
		LocalPoint lp = LocalPoint.fromWorld(client, spot);
		if (lp == null)
		{
			return;
		}
		object = client.createRuneLiteObject();
		object.setModel(model);
		currentAnim = -1;
		int anim = animations.idleAnim(def);
		if (anim > 0)
		{
			AnimationController ac = new AnimationController(client, anim);
			ac.setOnFinished(AnimationController::loop);
			object.setAnimationController(ac);
			currentAnim = anim;
		}
		object.setLocation(lp, spot.getPlane());
		object.setActive(true);
		activeKey = key;
		activeDef = def;
		companionTile = spot;
	}

	private void faceToward(WorldPoint target)
	{
		if (companionTile == null)
		{
			return;
		}
		int dx = target.getX() - companionTile.getX();
		int dy = target.getY() - companionTile.getY();
		// JAU: 0 = south, 512 = west, 1024 = north, 1536 = east
		int orientation;
		if (Math.abs(dx) > Math.abs(dy))
		{
			orientation = dx > 0 ? 1536 : 512;
		}
		else
		{
			orientation = dy > 0 ? 1024 : 0;
		}
		object.setOrientation(orientation);
	}

	/** Force a respawn after scene reloads. */
	public void invalidate()
	{
		remove();
		activeKey = null;
		companionTile = null;
	}

	public void remove()
	{
		if (object != null)
		{
			object.setActive(false);
			object = null;
		}
		moveFrom = null;
		moveTo = null;
		moveTarget = null;
	}
}
