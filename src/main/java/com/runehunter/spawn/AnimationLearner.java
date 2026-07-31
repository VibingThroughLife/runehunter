package com.runehunter.spawn;

import com.runehunter.data.CreatureAnims;
import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.NPC;
import net.runelite.client.config.ConfigManager;

/**
 * Animation source with two layers, most-trustworthy first:
 *
 * 1. CURATED — the compile-time CreatureAnims table, generated from a full
 *    cache dump (dumpAnims): the exact standing/walking animation the real
 *    NPC id uses. Complete for the whole roster.
 * 2. OBSERVED — idle+walk pose animations recorded live from an NPC whose id
 *    matches the exact id our model was built from. Safety net for future
 *    roster additions that haven't been dumped yet, and self-heals if Jagex
 *    reworks a rig between dumps.
 *
 * Persisted under "anim2_<key>" = "idle,walk" (the old polluted "anim_" keys
 * are deliberately ignored).
 */
public class AnimationLearner
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AnimationLearner.class);

	private static final String GROUP = "runehunter";

	private final ConfigManager configManager;
	private NpcModelCache modelCache;

	/** key -> [idle, walk], observed from the id-matched live NPC. */
	private final Map<String, int[]> observed = new HashMap<>();

	public AnimationLearner(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** Wire after construction (learner and cache are created together). */
	public void setModelCache(NpcModelCache modelCache)
	{
		this.modelCache = modelCache;
	}

	public void load()
	{
		for (CreatureDef d : CreatureRoster.ALL_INCLUDING_SECRETS)
		{
			String v = configManager.getConfiguration(GROUP, "anim2_" + d.key());
			if (v == null)
			{
				continue;
			}
			String[] parts = v.split(",");
			try
			{
				observed.put(d.key(), new int[]{
					Integer.parseInt(parts[0]),
					parts.length > 1 ? Integer.parseInt(parts[1]) : -1});
			}
			catch (NumberFormatException ignored)
			{
			}
		}
	}

	/** Call on NpcSpawned. Records only from the exact id our model uses. */
	public void observe(NPC npc)
	{
		if (npc == null || npc.getName() == null || modelCache == null)
		{
			return;
		}
		CreatureDef def = CreatureRoster.byNpcName(npc.getName());
		if (def == null || observed.containsKey(def.key()))
		{
			return;
		}
		Integer resolvedId = modelCache.resolveId(def);
		if (resolvedId == null || npc.getId() != resolvedId)
		{
			return; // different variant — its rig may not match our model
		}
		int idle = npc.getIdlePoseAnimation();
		int walk = npc.getWalkAnimation();
		if (idle <= 0)
		{
			return;
		}
		observed.put(def.key(), new int[]{idle, walk > 0 ? walk : -1});
		configManager.setConfiguration(GROUP, "anim2_" + def.key(), idle + "," + walk);
		log.debug("RuneHunter observed anims for {} (id {}): idle={} walk={}",
			def.getNpcName(), resolvedId, idle, walk);
	}

	/** Curated dump table first, observed (id-matched) fallback, else -1. */
	public int idleAnim(CreatureDef def)
	{
		int curated = CreatureAnims.idle(def.key());
		if (curated > 0)
		{
			return curated;
		}
		int[] o = observed.get(def.key());
		return o != null && o[0] > 0 ? o[0] : -1;
	}

	/** Curated dump table first, observed (id-matched) fallback, else -1. */
	public int walkAnim(CreatureDef def)
	{
		int curated = CreatureAnims.walk(def.key());
		if (curated > 0)
		{
			return curated;
		}
		int[] o = observed.get(def.key());
		return o != null && o[1] > 0 ? o[1] : -1;
	}
}
