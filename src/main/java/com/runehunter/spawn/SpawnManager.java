package com.runehunter.spawn;

import com.runehunter.RuneHunterConfig;
import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import com.runehunter.data.Tier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.AnimationController;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;

/**
 * Deterministic, serverless spawn system. Spawns are seeded from
 * (world, mapRegion, 30-minute UTC window) so every RuneHunter player on the same
 * world sees the same creatures in the same hiding spots during a window.
 * Species-lure spawns are personal extras layered on top.
 */
public class SpawnManager
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SpawnManager.class);

	private static final long WINDOW_MILLIS = 30L * 60L * 1000L;
	private static final int SHINY_DENOMINATOR = 512;
	private static final int PLACEMENT_ATTEMPTS = 40;

	/**
	 * Secret Dex rarity. Secrets roll on their own channel, seeded separately
	 * from the normal spawn table so the two never correlate.
	 *
	 * <p>The roll is <b>per loaded region</b>, which keeps a secret a property
	 * of (world, region, window) rather than of whoever happens to be standing
	 * there — two players in the same place see the same one, and it doesn't
	 * teleport around as you walk and the loaded region set changes. A scene is
	 * normally 4 map regions, so the chance a given 30-minute window puts a
	 * secret in your scene is 1 - (1 - 1/1400)^4 ~= 1/350, which is the brief's
	 * number. At most one secret per region per window.
	 */
	private static final int SECRET_REGION_DENOMINATOR = 1400;
	/** Dev-boost denominator: near-certain, so the thing is actually testable. */
	private static final int SECRET_DENOMINATOR_DEV = 6;
	/**
	 * Relative weights within a secret roll, in CreatureRoster.SECRET order:
	 * Zanik / Jimmothy / Marcus equal, the three 3rd Age at a third of that
	 * each — so a secret is a 3rd Age one 1 time in 4.
	 */
	private static final int[] SECRET_WEIGHTS = {3, 3, 3, 1, 1, 1};
	/** Max tiles a wandering mini strays from its spawn tile. */
	private static final int WANDER_LEASH = 3;
	/** Client ticks (~20ms) to cross one tile — matches in-game walk speed. */
	private static final int MOVE_TICKS_PER_TILE = 30;

	private final Client client;
	private final ClientThread clientThread;
	private final RuneHunterConfig config;
	private final NpcModelCache modelCache;
	private final AnimationLearner animations;

	private final List<SpawnedCreature> activeSpawns = new ArrayList<>();

	public List<SpawnedCreature> getActiveSpawns()
	{
		return activeSpawns;
	}

	/** creature key -> lure expiry epoch millis */
	private final Map<String, Long> lures = new HashMap<>();

	private long currentWindow = -1;
	private int lastBaseX = -1;
	private int lastBaseY = -1;

	public SpawnManager(Client client, ClientThread clientThread, RuneHunterConfig config,
		NpcModelCache modelCache, AnimationLearner animations)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.modelCache = modelCache;
		this.animations = animations;
	}

	public static long windowIndex()
	{
		return Instant.now().toEpochMilli() / WINDOW_MILLIS;
	}

	/** Seconds until the current spawn window rolls over. */
	public static long windowSecondsLeft()
	{
		long now = Instant.now().toEpochMilli();
		return ((currentWindowStart(now) + WINDOW_MILLIS) - now) / 1000L;
	}

	private static long currentWindowStart(long now)
	{
		return (now / WINDOW_MILLIS) * WINDOW_MILLIS;
	}

	/** Call every game tick from the plugin. */
	public void tick()
	{
		if (client.getGameState() != GameState.LOGGED_IN || !modelCache.isScanComplete())
		{
			return;
		}

		long window = windowIndex();
		int baseX = client.getTopLevelWorldView().getBaseX();
		int baseY = client.getTopLevelWorldView().getBaseY();
		boolean sceneChanged = baseX != lastBaseX || baseY != lastBaseY;

		if (window != currentWindow || sceneChanged)
		{
			currentWindow = window;
			lastBaseX = baseX;
			lastBaseY = baseY;
			reseed();
		}

		wander();
	}

	/** Call every client tick: advances smooth wander movement. */
	public void clientTick()
	{
		if (activeSpawns.isEmpty())
		{
			return;
		}
		for (SpawnedCreature s : activeSpawns)
		{
			if (s.advanceMove(client.getTopLevelWorldView()))
			{
				setAnim(s, animations.idleAnim(s.getDef()));
			}
		}
	}

	/** Each game tick, idle minis occasionally amble one tile within their leash. */
	private void wander()
	{
		if (!config.wander())
		{
			return;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (SpawnedCreature s : activeSpawns)
		{
			if (s.isMoving() || r.nextInt(5) != 0)
			{
				continue;
			}
			// No walk animation = no wandering: gliding around in an idle pose
			// reads as a moonwalking bug (QA: kurask, lizardman shaman)
			if (animations.walkAnim(s.getDef()) <= 0)
			{
				continue;
			}
			int dx = r.nextInt(-1, 2);
			int dy = r.nextInt(-1, 2);
			if (dx == 0 && dy == 0)
			{
				continue;
			}
			WorldPoint cur = s.getWorldPoint();
			WorldPoint target = new WorldPoint(cur.getX() + dx, cur.getY() + dy, cur.getPlane());
			if (s.getAnchor().distanceTo(target) > WANDER_LEASH)
			{
				continue;
			}
			LocalPoint from = LocalPoint.fromWorld(client.getTopLevelWorldView(), cur);
			LocalPoint to = LocalPoint.fromWorld(client.getTopLevelWorldView(), target);
			if (from == null || to == null || !isWalkable(to))
			{
				continue;
			}
			s.getObject().setOrientation(jauFor(dx, dy));
			s.beginMove(from, to, target, MOVE_TICKS_PER_TILE * Math.max(Math.abs(dx), Math.abs(dy)));
			int walk = animations.walkAnim(s.getDef());
			if (walk > 0)
			{
				setAnim(s, walk);
			}
		}
	}

	private void setAnim(SpawnedCreature s, int animId)
	{
		if (animId <= 0 || animId == s.getCurrentAnim())
		{
			return;
		}
		AnimationController ac = new AnimationController(client, animId);
		ac.setOnFinished(AnimationController::loop);
		s.getObject().setAnimationController(ac);
		s.setCurrentAnim(animId);
	}

	/** JAU orientation for a movement direction (0=S, 512=W, 1024=N, 1536=E). */
	private static int jauFor(int dx, int dy)
	{
		int jau = (int) Math.round(Math.atan2(-dx, -dy) / (2 * Math.PI) * 2048);
		return ((jau % 2048) + 2048) % 2048;
	}

	public static final int LINEUP_PAGE_SIZE = 15;

	/** Number of lineup pages covering the roster. */
	public static int lineupPages()
	{
		return (CreatureRoster.ALL.size() + LINEUP_PAGE_SIZE - 1) / LINEUP_PAGE_SIZE;
	}

	/**
	 * Dev/test: despawn everything and place one PAGE of the roster (15
	 * creatures, 5 per row, 3-tile spacing) in a numbered grid for animation
	 * QA. Batched so 87 animated models don't tank the framerate. Returns how
	 * many placed. Page is 1-based.
	 */
	public int spawnLineup(int page)
	{
		despawnAll();
		if (client.getLocalPlayer() == null)
		{
			return 0;
		}
		int from = (page - 1) * LINEUP_PAGE_SIZE;
		int to = Math.min(CreatureRoster.ALL.size(), from + LINEUP_PAGE_SIZE);
		WorldPoint p = client.getLocalPlayer().getWorldLocation();
		int placed = 0;
		for (int i = from; i < to; i++)
		{
			CreatureDef def = CreatureRoster.ALL.get(i);
			if (modelCache.resolveId(def) == null)
			{
				continue;
			}
			int slot = i - from;
			int col = slot % 5;
			int rowN = slot / 5;
			WorldPoint base = new WorldPoint(p.getX() - 6 + col * 3, p.getY() + 2 + rowN * 3, p.getPlane());
			boolean ok = place(def, base, false, true);
			if (!ok)
			{
				for (int dx = -1; dx <= 1 && !ok; dx++)
				{
					for (int dy = -1; dy <= 1 && !ok; dy++)
					{
						ok = place(def, new WorldPoint(base.getX() + dx, base.getY() + dy, base.getPlane()),
							false, true);
					}
				}
			}
			if (ok)
			{
				placed++;
			}
		}
		return placed;
	}

	// -- dev prop explorer: view arbitrary cache models in the world --
	private RuneLiteObject propObject;
	private int propModelId = -1;

	public int getPropModelId()
	{
		return propModelId;
	}

	/** Dev/test: show a raw cache model 2 tiles east of the player. */
	public boolean showProp(int modelId)
	{
		hideProp();
		if (client.getLocalPlayer() == null)
		{
			return false;
		}
		try
		{
			net.runelite.api.ModelData md = client.loadModelData(modelId);
			if (md == null)
			{
				return false;
			}
			Model lit = md.shallowCopy().cloneVertices().cloneColors().light();
			WorldPoint p = client.getLocalPlayer().getWorldLocation();
			WorldPoint wp = new WorldPoint(p.getX() + 2, p.getY(), p.getPlane());
			LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), wp);
			if (lp == null)
			{
				return false;
			}
			propObject = client.createRuneLiteObject();
			propObject.setModel(lit);
			propObject.setLocation(lp, wp.getPlane());
			propObject.setActive(true);
			propModelId = modelId;
			return true;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	public void hideProp()
	{
		if (propObject != null)
		{
			propObject.setActive(false);
			propObject = null;
		}
	}

	/**
	 * Dev/test: force one wander step on the first active spawn matching the
	 * name fragment (even if it has no walk animation). Returns its name or null.
	 */
	public String forceStep(String nameFragment)
	{
		String frag = nameFragment.toLowerCase(java.util.Locale.ROOT);
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (SpawnedCreature s : activeSpawns)
		{
			if (!s.getDef().getNpcName().toLowerCase(java.util.Locale.ROOT).contains(frag) || s.isMoving())
			{
				continue;
			}
			for (int attempt = 0; attempt < 8; attempt++)
			{
				int dx = r.nextInt(-1, 2);
				int dy = r.nextInt(-1, 2);
				if (dx == 0 && dy == 0)
				{
					continue;
				}
				WorldPoint cur = s.getWorldPoint();
				WorldPoint tgt = new WorldPoint(cur.getX() + dx, cur.getY() + dy, cur.getPlane());
				LocalPoint from = LocalPoint.fromWorld(client.getTopLevelWorldView(), cur);
				LocalPoint to = LocalPoint.fromWorld(client.getTopLevelWorldView(), tgt);
				if (from == null || to == null || !isWalkable(to))
				{
					continue;
				}
				s.getObject().setOrientation(jauFor(dx, dy));
				s.beginMove(from, to, tgt, MOVE_TICKS_PER_TILE);
				int walk = animations.walkAnim(s.getDef());
				if (walk > 0)
				{
					setAnim(s, walk);
				}
				return s.getDef().getNpcName();
			}
			return s.getDef().getNpcName();
		}
		return null;
	}

	/**
	 * Dev/test: apply an arbitrary animation id to the nearest active spawn
	 * whose name contains the fragment. Returns the creature name, or null.
	 */
	public String applyTestAnim(String nameFragment, int animId)
	{
		String frag = nameFragment.toLowerCase(java.util.Locale.ROOT);
		for (SpawnedCreature s : activeSpawns)
		{
			if (s.getDef().getNpcName().toLowerCase(java.util.Locale.ROOT).contains(frag))
			{
				AnimationController ac = new AnimationController(client, animId);
				ac.setOnFinished(AnimationController::loop);
				s.getObject().setAnimationController(ac);
				s.setCurrentAnim(animId);
				return s.getDef().getNpcName();
			}
		}
		return null;
	}

	/** Dev/test: scatter many random creatures around the player. Returns count placed. */
	public int spawnSwarm(int count)
	{
		if (client.getLocalPlayer() == null)
		{
			return 0;
		}
		List<CreatureDef> resolved = new ArrayList<>();
		for (CreatureDef d : CreatureRoster.ALL)
		{
			if (modelCache.resolveId(d) != null)
			{
				resolved.add(d);
			}
		}
		if (resolved.isEmpty())
		{
			return 0;
		}
		WorldPoint p = client.getLocalPlayer().getWorldLocation();
		ThreadLocalRandom r = ThreadLocalRandom.current();
		int placed = 0;
		for (int i = 0; i < count; i++)
		{
			CreatureDef def = resolved.get(r.nextInt(resolved.size()));
			boolean shiny = r.nextInt(shinyDenominator()) == 0;
			for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++)
			{
				WorldPoint wp = new WorldPoint(
					p.getX() + r.nextInt(-14, 15),
					p.getY() + r.nextInt(-14, 15),
					p.getPlane());
				if (place(def, wp, shiny, true))
				{
					placed++;
					break;
				}
			}
		}
		return placed;
	}

	/** Despawn everything and roll fresh seeded spawns for the loaded scene. */
	public void reseed()
	{
		despawnAll();
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		int world = client.getWorld();
		long window = windowIndex();
		int cap = Math.max(1, config.spawnCap());

		// Secrets roll first so a busy region's spawn cap can never crowd one
		// out — the whole point is that they're findable when they do appear.
		rollSecrets(world, window);

		for (int region : client.getTopLevelWorldView().getMapRegions())
		{
			if (activeSpawns.size() >= cap)
			{
				break;
			}
			Random rng = new Random(mix(world, region, window));

			int spawnRolls = rng.nextInt(100) < 65 ? 1 : 2;
			if (config.devSpawnBoost())
			{
				spawnRolls += 4;
			}
			for (int i = 0; i < spawnRolls && activeSpawns.size() < cap; i++)
			{
				CreatureDef def = pickWeighted(rng, region, false);
				if (def != null)
				{
					trySpawnInRegion(rng, region, def, false);
				}
			}

			// Small independent chance a legendary stalks this region this window
			if (rng.nextInt(config.devSpawnBoost() ? 2 : 8) == 0 && activeSpawns.size() < cap)
			{
				CreatureDef legendary = pickWeighted(rng, region, true);
				if (legendary != null && trySpawnInRegion(rng, region, legendary, false))
				{
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
						"You sense a powerful presence nearby...", null);
				}
			}
		}

		log.info("RuneHunter reseed: {} active spawns (cap {}, window {}, world {})",
			activeSpawns.size(), cap, window, world);
	}

	// ------------------------------------------------------------------
	// Secret Dex channel
	// ------------------------------------------------------------------

	/**
	 * The independent secret roll: one deterministic check per loaded region,
	 * seeded from its own mix so it never correlates with the normal table.
	 * Silent by design — no chat line, and the radar skips secrets, so walking
	 * past one without noticing is exactly the intended experience.
	 */
	private void rollSecrets(int world, long window)
	{
		if (CreatureRoster.SECRET.isEmpty())
		{
			return;
		}
		int denominator = config.devSpawnBoost() ? SECRET_DENOMINATOR_DEV : SECRET_REGION_DENOMINATOR;
		for (int region : client.getTopLevelWorldView().getMapRegions())
		{
			Random rng = new Random(secretMix(world, region, window));
			if (rng.nextInt(denominator) != 0)
			{
				continue;
			}
			CreatureDef def = pickSecret(rng);
			if (def == null)
			{
				continue;
			}
			if (trySpawnInRegion(rng, region, def, false, false))
			{
				log.debug("RuneHunter secret rolled: {} in region {} (window {})",
					def.getDisplayName(), region, window);
			}
		}
	}

	/** Weighted pick across CreatureRoster.SECRET using {@link #SECRET_WEIGHTS}. */
	private static CreatureDef pickSecret(Random rng)
	{
		List<CreatureDef> secrets = CreatureRoster.SECRET;
		int total = 0;
		for (int i = 0; i < secrets.size(); i++)
		{
			total += secretWeight(i);
		}
		if (total <= 0)
		{
			return null;
		}
		int roll = rng.nextInt(total);
		for (int i = 0; i < secrets.size(); i++)
		{
			roll -= secretWeight(i);
			if (roll < 0)
			{
				return secrets.get(i);
			}
		}
		return secrets.get(secrets.size() - 1);
	}

	/** Weight for a secret by roster index; anything past the table weighs 1. */
	private static int secretWeight(int index)
	{
		return index < SECRET_WEIGHTS.length ? SECRET_WEIGHTS[index] : 1;
	}

	/**
	 * Dev/test: force-spawn a secret next to the player by display-name
	 * fragment ("zanik", "jimmothy", "marcus", "3a mage", "3rd age warrior").
	 * Returns the display name placed, or null if nothing matched or no tile
	 * or model was available. Client thread.
	 */
	public String spawnSecret(String keyFragment)
	{
		CreatureDef def = CreatureRoster.secretMatching(keyFragment);
		if (def == null)
		{
			return null;
		}
		modelCache.clearModel(def);
		return spawnNear(def, false) ? def.getDisplayName() : null;
	}

	/** Display names of every secret, for dev command help text. */
	public static List<String> secretNames()
	{
		List<String> out = new ArrayList<>();
		for (CreatureDef d : CreatureRoster.SECRET)
		{
			out.add(d.getDisplayName());
		}
		return out;
	}

	/**
	 * Dev/test: one line per secret — resolved NPC id, whether its model
	 * actually builds, and how many are live right now. Client thread (it
	 * builds models). This is the fast way to find out that an id in
	 * SecretIds is wrong without hunting for the thing in the world.
	 */
	public List<String> secretStatus()
	{
		List<String> out = new ArrayList<>();
		for (CreatureDef d : CreatureRoster.SECRET)
		{
			Integer id = modelCache.resolveId(d);
			String model;
			try
			{
				model = modelCache.getModel(d, false) != null ? "model ok" : "MODEL FAILED";
			}
			catch (Exception e)
			{
				model = "MODEL ERROR: " + e.getClass().getSimpleName();
			}
			int live = 0;
			for (SpawnedCreature s : activeSpawns)
			{
				if (s.getDef() == d)
				{
					live++;
				}
			}
			out.add(String.format("  %-16s npc=%-8s %-12s idle=%d walk=%d live=%d",
				d.getDisplayName(),
				id == null ? "unresolved" : id.toString(),
				model,
				animations.idleAnim(d), animations.walkAnim(d), live));
		}
		return out;
	}

	/**
	 * Dev/test: drop every cached secret model so the next spawn re-runs its
	 * recipe. Pair with an edit to SecretIds when tuning ids and colours.
	 */
	public void reloadSecretModels()
	{
		for (CreatureDef d : CreatureRoster.SECRET)
		{
			modelCache.clearModel(d);
		}
		despawnMatchingSecrets();
	}

	/** Despawn every live secret (used by reloadSecretModels). */
	private void despawnMatchingSecrets()
	{
		List<SpawnedCreature> doomed = new ArrayList<>();
		for (SpawnedCreature s : activeSpawns)
		{
			if (s.getDef().isSecret())
			{
				doomed.add(s);
			}
		}
		for (SpawnedCreature s : doomed)
		{
			despawn(s);
		}
	}

	/**
	 * Dev/test helper: guaranteed spawn on the nearest placeable tile around the
	 * player, spiraling outward. Returns false if no tile or model was available.
	 */
	public boolean spawnNear(CreatureDef def, boolean shiny)
	{
		if (client.getLocalPlayer() == null)
		{
			return false;
		}
		WorldPoint p = client.getLocalPlayer().getWorldLocation();
		for (int r = 2; r <= 10; r++)
		{
			for (int dx = -r; dx <= r; dx++)
			{
				for (int dy = -r; dy <= r; dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) != r)
					{
						continue;
					}
					WorldPoint wp = new WorldPoint(p.getX() + dx, p.getY() + dy, p.getPlane());
					if (place(def, wp, shiny, true))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Personal (non-seeded) spawn near the player, triggered by species lures. */
	public void personalSpawnNearPlayer(CreatureDef def)
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}
		WorldPoint p = client.getLocalPlayer().getWorldLocation();
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int i = 0; i < PLACEMENT_ATTEMPTS; i++)
		{
			WorldPoint wp = new WorldPoint(
				p.getX() + r.nextInt(-12, 13),
				p.getY() + r.nextInt(-12, 13),
				p.getPlane());
			boolean shiny = r.nextInt(shinyDenominator()) == 0;
			if (place(def, wp, shiny, true))
			{
				return;
			}
		}
	}

	public void addLure(CreatureDef def)
	{
		lures.put(def.key(), System.currentTimeMillis() + 10L * 60L * 1000L);
	}

	public boolean isLured(CreatureDef def)
	{
		Long until = lures.get(def.key());
		return until != null && until > System.currentTimeMillis();
	}

	private int shinyDenominator()
	{
		return config.devShinyBoost() ? 16 : SHINY_DENOMINATOR;
	}

	private CreatureDef pickWeighted(Random rng, int region, boolean legendaryOnly)
	{
		WorldPoint center = regionCenter(region);
		List<CreatureDef> eligible = new ArrayList<>();
		int totalWeight = 0;
		for (CreatureDef d : CreatureRoster.ALL)
		{
			boolean legendary = d.getTier() == Tier.LEGENDARY;
			if (legendaryOnly != legendary)
			{
				continue;
			}
			if (!d.livesAt(center))
			{
				continue;
			}
			eligible.add(d);
			totalWeight += weightOf(d);
		}
		if (eligible.isEmpty() || totalWeight <= 0)
		{
			return null;
		}
		int roll = rng.nextInt(totalWeight);
		for (CreatureDef d : eligible)
		{
			roll -= weightOf(d);
			if (roll < 0)
			{
				return d;
			}
		}
		return eligible.get(eligible.size() - 1);
	}

	private int weightOf(CreatureDef d)
	{
		int w = d.getTier().getSpawnWeight();
		return isLured(d) ? w * 5 : w;
	}

	private boolean trySpawnInRegion(Random rng, int region, CreatureDef def, boolean personal)
	{
		return trySpawnInRegion(rng, region, def, personal, true);
	}

	private boolean trySpawnInRegion(Random rng, int region, CreatureDef def, boolean personal,
		boolean allowShiny)
	{
		int rx = (region >> 8) << 6;
		int ry = (region & 0xFF) << 6;
		// Secrets have no shiny variant — the secret IS the flex
		boolean shiny = allowShiny && rng.nextInt(shinyDenominator()) == 0;
		for (int i = 0; i < PLACEMENT_ATTEMPTS; i++)
		{
			WorldPoint wp = new WorldPoint(rx + rng.nextInt(64), ry + rng.nextInt(64), 0);
			if (place(def, wp, shiny, personal))
			{
				return true;
			}
		}
		return false;
	}

	private boolean place(CreatureDef def, WorldPoint wp, boolean shiny, boolean personal)
	{
		if (def.isSecret())
		{
			shiny = false; // belt and braces: no secret is ever shiny
		}
		if (wp.getPlane() != client.getTopLevelWorldView().getPlane())
		{
			return false;
		}
		LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), wp);
		if (lp == null || !isWalkable(lp))
		{
			return false;
		}
		Model model = modelCache.getModel(def, shiny);
		if (model == null)
		{
			return false;
		}

		RuneLiteObject obj = client.createRuneLiteObject();
		obj.setModel(model);
		int idleAnim = animations.idleAnim(def);
		if (idleAnim > 0)
		{
			AnimationController ac = new AnimationController(client, idleAnim);
			ac.setOnFinished(AnimationController::loop);
			obj.setAnimationController(ac);
		}
		obj.setLocation(lp, wp.getPlane());
		obj.setOrientation(ThreadLocalRandom.current().nextInt(2048));
		obj.setActive(true);

		SpawnedCreature sc = new SpawnedCreature(def, shiny, wp, obj, model, personal);
		sc.setCurrentAnim(idleAnim > 0 ? idleAnim : -1);
		activeSpawns.add(sc);
		return true;
	}

	private boolean isWalkable(LocalPoint lp)
	{
		CollisionData[] maps = client.getTopLevelWorldView().getCollisionMaps();
		if (maps == null)
		{
			return false;
		}
		CollisionData data = maps[client.getTopLevelWorldView().getPlane()];
		if (data == null)
		{
			return false;
		}
		int x = lp.getSceneX();
		int y = lp.getSceneY();
		if (x < 0 || y < 0 || x >= 104 || y >= 104)
		{
			return false;
		}
		int flags = data.getFlags()[x][y];
		return (flags & (CollisionDataFlag.BLOCK_MOVEMENT_FULL
			| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR
			| CollisionDataFlag.BLOCK_MOVEMENT_OBJECT)) == 0;
	}

	private WorldPoint regionCenter(int region)
	{
		int rx = (region >> 8) << 6;
		int ry = (region & 0xFF) << 6;
		return new WorldPoint(rx + 32, ry + 32, 0);
	}

	public void despawn(SpawnedCreature s)
	{
		s.getObject().setActive(false);
		activeSpawns.remove(s);
	}

	/** Dev/test: despawn spawns whose name contains the fragment. Returns count. */
	public int despawnMatching(String nameFragment)
	{
		String frag = nameFragment.toLowerCase(java.util.Locale.ROOT);
		List<SpawnedCreature> doomed = new ArrayList<>();
		for (SpawnedCreature s : activeSpawns)
		{
			if (s.getDef().getNpcName().toLowerCase(java.util.Locale.ROOT).contains(frag))
			{
				doomed.add(s);
			}
		}
		for (SpawnedCreature s : doomed)
		{
			despawn(s);
		}
		return doomed.size();
	}

	public void despawnAll()
	{
		for (SpawnedCreature s : activeSpawns)
		{
			s.getObject().setActive(false);
		}
		activeSpawns.clear();
	}

	/**
	 * Seed for the secret channel. Same shape as {@link #mix} but with
	 * different constants, so a region that rolls a fat normal spawn table
	 * carries no information about whether it also hides a secret.
	 */
	private static long secretMix(int world, int region, long window)
	{
		long h = 0xA24BAED4963EE407L;
		h ^= world * 0xC2B2AE3D27D4EB4FL;
		h = Long.rotateLeft(h, 23);
		h ^= region * 0x165667B19E3779F9L;
		h = Long.rotateLeft(h, 41);
		h ^= window * 0x27D4EB2F165667C5L;
		h ^= h >>> 33;
		return h;
	}

	private static long mix(int world, int region, long window)
	{
		long h = 0x9E3779B97F4A7C15L;
		h ^= world * 0xBF58476D1CE4E5B9L;
		h = Long.rotateLeft(h, 27);
		h ^= region * 0x94D049BB133111EBL;
		h = Long.rotateLeft(h, 31);
		h ^= window * 0xD6E8FEB86659FD93L;
		return h;
	}
}
