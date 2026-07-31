package com.runehunter.data;

import java.util.Locale;
import net.runelite.api.coords.WorldPoint;

/**
 * Definition of one catchable creature.
 *
 * <p>Most creatures borrow the model of an NPC with the same name: the numeric
 * NPC id is resolved at runtime by scanning the cache (see NpcModelCache), so
 * we never have to hardcode ids for 80+ NPCs. Secret Dex characters break that
 * one-to-one link — Jimmothy's base NPC is a Giant squirrel and the 3rd Age
 * trio are kitbashed Men — so the <b>cache name</b> (what we look up) and the
 * <b>display name</b> (what the player sees) are separate.
 *
 * <p>{@link #getNpcName()} returns the <i>display</i> name; it stays as the
 * one name-getter the UI calls, and for all 87 ordinary creatures the two names
 * are identical. Anything resolving a model out of the cache must use
 * {@link #getCacheName()}.
 */
public class CreatureDef
{
	private final String cacheName;
	private final String displayName;
	private final Tier tier;
	private final Habitat[] habitats;
	/** Optional explicit NPC id when the name is ambiguous. -1 = resolve by name. */
	private final int npcIdOverride;
	/** Secret Dex characters: hidden from the GoDex, radar and wild rolls until caught. */
	private final boolean secret;
	/** Optional kitbash instructions; null = "just use the NPC's own model". */
	private final ModelRecipe recipe;

	public CreatureDef(String npcName, Tier tier, Habitat... habitats)
	{
		this(npcName, -1, tier, habitats);
	}

	public CreatureDef(String npcName, int npcIdOverride, Tier tier, Habitat... habitats)
	{
		this(npcName, npcName, npcIdOverride, tier, false, null, habitats);
	}

	/**
	 * Full form, used by the Secret roster.
	 *
	 * @param cacheName     in-game NPC name used to resolve the base model
	 * @param displayName   name shown to the player (and the storage key source)
	 * @param npcIdOverride explicit NPC id, or -1 to resolve {@code cacheName}
	 * @param secret        hide from the GoDex/radar/wild rolls until caught
	 * @param recipe        optional kitbash recipe, or null
	 */
	public CreatureDef(String cacheName, String displayName, int npcIdOverride, Tier tier,
		boolean secret, ModelRecipe recipe, Habitat... habitats)
	{
		this.cacheName = cacheName;
		this.displayName = displayName;
		this.npcIdOverride = npcIdOverride;
		this.tier = tier;
		this.secret = secret;
		this.recipe = recipe;
		this.habitats = habitats.length == 0 ? new Habitat[]{Habitat.EVERYWHERE} : habitats;
	}

	/**
	 * Player-facing name. Identical to {@link #getCacheName()} for every
	 * non-secret creature — kept under this name so every existing UI call site
	 * keeps working and automatically shows "Jimmothy" rather than
	 * "Giant squirrel".
	 */
	public String getNpcName()
	{
		return displayName;
	}

	/** Same value as {@link #getNpcName()}, spelled out for new code. */
	public String getDisplayName()
	{
		return displayName;
	}

	/** The in-game NPC name to look up in the cache. Never shown to players. */
	public String getCacheName()
	{
		return cacheName;
	}

	public Tier getTier()
	{
		return tier;
	}

	public Habitat[] getHabitats()
	{
		return habitats;
	}

	public int getNpcIdOverride()
	{
		return npcIdOverride;
	}

	public boolean isSecret()
	{
		return secret;
	}

	/** Kitbash instructions, or null when the NPC's own model is enough. */
	public ModelRecipe getRecipe()
	{
		return recipe;
	}

	/** Stable storage key, derived from the display name. */
	public String key()
	{
		return displayName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
	}

	public boolean livesAt(WorldPoint p)
	{
		for (Habitat h : habitats)
		{
			if (h.contains(p))
			{
				return true;
			}
		}
		return false;
	}
}
