package com.runehunter.spawn;

import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import com.runehunter.data.ModelRecipe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.JagexColor;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;
import net.runelite.client.callback.ClientThread;

/**
 * Resolves roster creatures to NPC ids by scanning the cache (no hardcoded id
 * tables), and builds shrunken, optionally shiny-recolored models.
 *
 * <p>A creature may also carry a {@link ModelRecipe} (the Secret Dex
 * kitbashes): extra item models merged onto the NPC base, a global desaturate,
 * exact palette swaps, and geometry-aware slab painting. Recipes run here,
 * always on the client thread, between the NPC's own recolours and the mini
 * scale.
 */
public class NpcModelCache
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NpcModelCache.class);

	private static final int MAX_NPC_ID = 16000;
	private static final int SCAN_CHUNK = 800;
	/** Native model scale is 128; this is the mini size for a 1x1 NPC. */
	private static final int BASE_MINI_SCALE = 70;
	/** Hue rotation applied to shiny variants (Jagex HSL packed hue is 6 bits). */
	private static final int SHINY_HUE_SHIFT = 10;

	/**
	 * Per-creature absolute scale overrides (native = 128) for models whose
	 * cache size lies about their visual bulk (QA: abyssal demon towers over
	 * everything at the size-1 default).
	 */
	private static final Map<String, Integer> SCALE_OVERRIDES = new HashMap<>();
	static
	{
		SCALE_OVERRIDES.put("abyssal_demon", 42);
	}

	private final Client client;
	private final ClientThread clientThread;

	private final Map<String, Integer> nameToId = new HashMap<>();
	/** Keys whose resolved id is a combat-leveled, interactible variant. */
	private final Set<String> goodIds = new HashSet<>();
	private final Map<String, Model> modelCache = new HashMap<>();
	private boolean scanComplete;
	private boolean scanning;
	private int scanCursor;
	/** A plain "Man" NPC id, resolved during the scan — used for the catch-sequence catcher. */
	private int manId = -1;

	public boolean isScanComplete()
	{
		return scanComplete;
	}

	/** How many roster creatures have been resolved to an NPC id so far. */
	public int resolvedCount()
	{
		return nameToId.size();
	}

	public NpcModelCache(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	/** Kick off (or resume) the chunked cache scan on the client thread. */
	public void startScan(Runnable onComplete)
	{
		if (scanComplete)
		{
			onComplete.run();
			return;
		}
		if (scanning)
		{
			return;
		}
		scanning = true;
		clientThread.invokeLater(() -> scanChunk(onComplete));
	}

	private void scanChunk(Runnable onComplete)
	{
		Set<String> wanted = new HashSet<>();
		for (CreatureDef d : CreatureRoster.ALL_INCLUDING_SECRETS)
		{
			// Pinned ids never need the scan — and skipping them stops the
			// three 3rd Age secrets (all cache-named "Man") fighting over one
			// entry. resolveId() honours the override regardless.
			if (d.getNpcIdOverride() >= 0)
			{
				continue;
			}
			// keep scanning names until we've found a combat-leveled variant
			if (!goodIds.contains(d.key()))
			{
				wanted.add(d.getCacheName().toLowerCase(Locale.ROOT));
			}
		}
		if (wanted.isEmpty() || scanCursor >= MAX_NPC_ID)
		{
			scanComplete = true;
			scanning = false;
			log.info("RuneHunter npc scan complete, resolved {}/{} creatures",
				nameToId.size(), CreatureRoster.ALL.size());
			onComplete.run();
			return;
		}

		int end = Math.min(scanCursor + SCAN_CHUNK, MAX_NPC_ID);
		for (; scanCursor < end; scanCursor++)
		{
			try
			{
				NPCComposition comp = client.getNpcDefinition(scanCursor);
				if (comp == null || comp.getName() == null || comp.getModels() == null)
				{
					continue;
				}
				String name = comp.getName().toLowerCase(Locale.ROOT);
				if (manId < 0 && name.equals("man") && comp.getCombatLevel() == 2)
				{
					manId = scanCursor;
				}
				if (!wanted.contains(name))
				{
					continue;
				}
				CreatureDef def = CreatureRoster.byCacheNameIncludingSecrets(comp.getName());
				if (def == null || def.getNpcIdOverride() >= 0)
				{
					continue;
				}
				// Prefer real combat variants over quest/scenery lookalikes
				// (the first cache id named "Goblin" can be a squatting quest
				// prop with a different rig). A combat-leveled, interactible
				// variant may replace an earlier zero-combat pick.
				boolean fighty = comp.getCombatLevel() > 0 && comp.isInteractible();
				Integer existing = nameToId.get(def.key());
				if (existing == null)
				{
					nameToId.put(def.key(), scanCursor);
					if (fighty)
					{
						goodIds.add(def.key());
					}
				}
				else if (fighty && !goodIds.contains(def.key()))
				{
					nameToId.put(def.key(), scanCursor);
					goodIds.add(def.key());
				}
			}
			catch (Exception e)
			{
				// out-of-range or bad definition; ignore
			}
		}
		clientThread.invokeLater(() -> scanChunk(onComplete));
	}

	public Integer resolveId(CreatureDef def)
	{
		if (def.getNpcIdOverride() >= 0)
		{
			return def.getNpcIdOverride();
		}
		return nameToId.get(def.key());
	}

	/**
	 * Build (or fetch cached) mini model for a creature. Must be called on the
	 * client thread. Returns null if the id is unresolved or models missing.
	 */
	public Model getModel(CreatureDef def, boolean shiny)
	{
		String cacheKey = def.key() + (shiny ? ":shiny" : "");
		Model cached = modelCache.get(cacheKey);
		if (cached != null)
		{
			return cached;
		}

		Integer id = resolveId(def);
		if (id == null)
		{
			return null;
		}

		try
		{
			NPCComposition comp = client.getNpcDefinition(id);
			if (comp == null || comp.getModels() == null)
			{
				return null;
			}

			int[] modelIds = comp.getModels();
			ModelData[] parts = new ModelData[modelIds.length];
			for (int i = 0; i < modelIds.length; i++)
			{
				ModelData md = client.loadModelData(modelIds[i]);
				if (md == null)
				{
					return null; // not in cache yet; try again later
				}
				parts[i] = md;
			}

			ModelData merged = parts.length == 1 ? parts[0] : client.mergeModels(parts);
			// Clone before mutating so we never corrupt cached model data used by real NPCs
			merged = merged.shallowCopy().cloneVertices().cloneColors();

			// Apply the NPC's own recolors (many NPCs share base models with palette swaps)
			short[] from = comp.getColorToReplace();
			short[] to = comp.getColorToReplaceWith();
			if (from != null && to != null)
			{
				for (int i = 0; i < Math.min(from.length, to.length); i++)
				{
					merged.recolor(from[i], to[i]);
				}
			}

			ModelRecipe recipe = def.getRecipe();
			if (recipe != null)
			{
				merged = applyRecipe(merged, recipe);
			}

			if (shiny)
			{
				applyShinyPalette(merged);
			}

			int size = Math.max(1, comp.getSize());
			int scale = Math.max(18, BASE_MINI_SCALE / size);
			Integer tweak = SCALE_OVERRIDES.get(def.key());
			if (tweak != null)
			{
				scale = tweak;
			}
			if (recipe != null && recipe.getScaleOverride() > 0)
			{
				scale = recipe.getScaleOverride();
			}
			merged.scale(scale, scale, scale);

			Model lit = merged.light();
			modelCache.put(cacheKey, lit);
			return lit;
		}
		catch (Exception e)
		{
			log.warn("RuneHunter failed building model for {}", def.getNpcName(), e);
			return null;
		}
	}

	// ------------------------------------------------------------------
	// Kitbash recipes (Secret Dex)
	// ------------------------------------------------------------------

	/**
	 * Run a {@link ModelRecipe} over a base model: merge item models, then do
	 * the palette work. Client thread only. Returns the model to carry on with
	 * (which may be a different instance after a merge).
	 */
	private ModelData applyRecipe(ModelData base, ModelRecipe recipe)
	{
		ModelData md = base;

		int[] itemIds = recipe.getMergeItemIds();
		if (itemIds.length > 0)
		{
			List<ModelData> parts = new ArrayList<>();
			parts.add(md);
			for (int itemId : itemIds)
			{
				ModelData piece = loadItemModel(itemId, recipe);
				if (piece != null)
				{
					parts.add(piece);
				}
			}
			if (parts.size() > 1)
			{
				// mergeModels hands back a fresh ModelData, but clone anyway:
				// everything after this point writes into its arrays.
				md = client.mergeModels(parts.toArray(new ModelData[0]))
					.shallowCopy().cloneVertices().cloneColors();
			}
		}

		if (recipe.isDesaturate())
		{
			desaturate(md);
		}
		for (ModelRecipe.Recolor r : recipe.getRecolors())
		{
			md.recolor(r.getFrom(), r.getTo());
		}
		for (ModelRecipe.SlabPaint slab : recipe.getSlabs())
		{
			paintSlab(md, slab);
		}
		return md;
	}

	/**
	 * The cache model for an item, with the item's own palette swaps applied.
	 * Note this is the <i>inventory</i> model — the equipped ("wear") models
	 * aren't exposed by the RuneLite API — so item pieces may need
	 * {@code itemScale}/{@code itemLift} tuning to sit on a body.
	 */
	private ModelData loadItemModel(int itemId, ModelRecipe recipe)
	{
		try
		{
			ItemComposition item = client.getItemDefinition(itemId);
			if (item == null || item.getInventoryModel() <= 0)
			{
				return null;
			}
			ModelData piece = client.loadModelData(item.getInventoryModel());
			if (piece == null)
			{
				return null;
			}
			piece = piece.shallowCopy().cloneVertices().cloneColors();
			short[] from = item.getColorToReplace();
			short[] to = item.getColorToReplaceWith();
			if (from != null && to != null)
			{
				for (int i = 0; i < Math.min(from.length, to.length); i++)
				{
					piece.recolor(from[i], to[i]);
				}
			}
			int s = recipe.getMergeItemScale();
			if (s != 128)
			{
				piece.scale(s, s, s);
			}
			// Yaw first, then lift — rotating after translating would swing the
			// piece around the body's axis instead of its own.
			switch (recipe.getMergeItemYaw())
			{
				case 90:
					piece.rotateY90Ccw();
					break;
				case 180:
					piece.rotateY180Ccw();
					break;
				case 270:
					piece.rotateY270Ccw();
					break;
				default:
					break;
			}
			if (recipe.getMergeItemLift() != 0)
			{
				// Jagex Y grows downward, so "up" is negative
				piece.translate(0, -recipe.getMergeItemLift(), 0);
			}
			return piece;
		}
		catch (Exception e)
		{
			log.warn("RuneHunter failed loading item model {}", itemId, e);
			return null;
		}
	}

	/** Strip saturation from every face colour, keeping luminance. */
	private static void desaturate(ModelData md)
	{
		short[] colors = md.getFaceColors();
		if (colors == null)
		{
			return;
		}
		Set<Short> unique = new HashSet<>();
		for (short c : colors)
		{
			unique.add(c);
		}
		for (short c : unique)
		{
			short grey = JagexColor.packHSL(0, 0, JagexColor.unpackLuminance(c));
			if (grey != c)
			{
				md.recolor(c, grey);
			}
		}
	}

	/**
	 * Repaint every face whose centroid falls inside a fractional slab of the
	 * model's bounding box — the raccoon mask and tail rings, and the 3rd age
	 * white/gold body.
	 *
	 * <p>Writes straight into the face-colour array rather than going through
	 * {@code recolor}, because the target is a region of geometry, not a
	 * colour. Safe because the caller has already {@code cloneColors()}'d, so
	 * the array belongs to this ModelData and not to the shared cache entry.
	 */
	private static void paintSlab(ModelData md, ModelRecipe.SlabPaint slab)
	{
		short[] colors = md.getFaceColors();
		int[] fa = md.getFaceIndices1();
		int[] fb = md.getFaceIndices2();
		int[] fc = md.getFaceIndices3();
		if (colors == null || colors.length == 0 || fa == null || fb == null || fc == null)
		{
			return;
		}

		float[] axis;
		switch (slab.getAxis())
		{
			case X:
				axis = md.getVerticesX();
				break;
			case Z:
				axis = md.getVerticesZ();
				break;
			case Y:
			default:
				axis = md.getVerticesY();
				break;
		}
		if (axis == null || axis.length == 0)
		{
			return;
		}

		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		int verts = Math.min(axis.length, md.getVerticesCount());
		for (int i = 0; i < verts; i++)
		{
			min = Math.min(min, axis[i]);
			max = Math.max(max, axis[i]);
		}
		float span = max - min;
		if (span <= 0.001f)
		{
			return;
		}

		int faces = Math.min(colors.length, Math.min(fa.length, Math.min(fb.length, fc.length)));
		for (int i = 0; i < faces; i++)
		{
			int i1 = fa[i];
			int i2 = fb[i];
			int i3 = fc[i];
			if (i1 < 0 || i2 < 0 || i3 < 0 || i1 >= verts || i2 >= verts || i3 >= verts)
			{
				continue;
			}
			double f = ((axis[i1] + axis[i2] + axis[i3]) / 3f - min) / span;
			if (f < slab.getFrom() || f > slab.getTo())
			{
				continue;
			}
			if (slab.getStripes() > 0)
			{
				double slabSpan = slab.getTo() - slab.getFrom();
				if (slabSpan <= 0)
				{
					continue;
				}
				double local = (f - slab.getFrom()) / slabSpan;
				// 2n bands across the slab; paint the even ones, leave the odd
				// ones the body colour — that alternation is the tail ring
				int band = (int) Math.floor(local * slab.getStripes() * 2);
				if ((band & 1) != 0)
				{
					continue;
				}
			}
			colors[i] = slab.getColor();
		}
	}

	/** Rotate the hue of every face color: cheap, distinctive shiny recolor. */
	private void applyShinyPalette(ModelData md)
	{
		short[] colors = md.getFaceColors();
		if (colors == null)
		{
			return;
		}
		Set<Short> unique = new HashSet<>();
		for (short c : colors)
		{
			unique.add(c);
		}
		for (short c : unique)
		{
			int hue = (c >> 10) & 0x3F;
			int satLight = c & 0x3FF;
			int newHue = (hue + SHINY_HUE_SHIFT) & 0x3F;
			short recolored = (short) ((newHue << 10) | satLight);
			md.recolor(c, recolored);
		}
	}

	public void clearModels()
	{
		modelCache.clear();
	}

	/**
	 * Drop the cached models for one creature so the next build re-runs its
	 * recipe. Used by the dev "secret reload" path when tuning ids and colours.
	 */
	public void clearModel(CreatureDef def)
	{
		modelCache.remove(def.key());
		modelCache.remove(def.key() + ":shiny");
	}

	/**
	 * Mini "Man" model for the catch sequence (the poor sap who shimmies
	 * across the creature MEP2-style). Built on demand, cached. Client thread.
	 */
	public Model getCatcherModel()
	{
		Model cached = modelCache.get("__catcher");
		if (cached != null)
		{
			return cached;
		}
		if (manId < 0)
		{
			return null;
		}
		try
		{
			NPCComposition comp = client.getNpcDefinition(manId);
			if (comp == null || comp.getModels() == null)
			{
				return null;
			}
			int[] modelIds = comp.getModels();
			ModelData[] parts = new ModelData[modelIds.length];
			for (int i = 0; i < modelIds.length; i++)
			{
				ModelData md = client.loadModelData(modelIds[i]);
				if (md == null)
				{
					return null;
				}
				parts[i] = md;
			}
			ModelData merged = parts.length == 1 ? parts[0] : client.mergeModels(parts);
			merged = merged.shallowCopy().cloneVertices().cloneColors();
			short[] from = comp.getColorToReplace();
			short[] to = comp.getColorToReplaceWith();
			if (from != null && to != null)
			{
				for (int i = 0; i < Math.min(from.length, to.length); i++)
				{
					merged.recolor(from[i], to[i]);
				}
			}
			merged.scale(84, 84, 84);
			Model lit = merged.light();
			modelCache.put("__catcher", lit);
			return lit;
		}
		catch (Exception e)
		{
			log.warn("RuneHunter failed building catcher model", e);
			return null;
		}
	}
}
