package com.runehunter.data;

/**
 * The OSRS experience curve, exactly.
 *
 * Uses Jagex's real formula so a virtual 99 costs the same 13,034,431 xp a real
 * one does, and the shape matches muscle memory: level 60 is 2% of the way to 99,
 * and the last nine levels are nearly half the grind. Players feel that curve
 * without being told about it, and any other curve would feel wrong to them.
 *
 *   xp(L) = floor( sum(i=1..L-1) floor(i + 300 * 2^(i/7)) / 4 )
 */
public final class SkillXp
{
	public static final int MAX_LEVEL = 99;
	public static final int MAX_XP = 200_000_000;

	/** XP_TABLE[L] = total xp required to reach level L. XP_TABLE[1] == 0. */
	private static final int[] XP_TABLE = new int[MAX_LEVEL + 1];

	static
	{
		double acc = 0;
		XP_TABLE[1] = 0;
		for (int level = 1; level < MAX_LEVEL; level++)
		{
			acc += Math.floor(level + 300 * Math.pow(2, level / 7.0));
			XP_TABLE[level + 1] = (int) Math.floor(acc / 4);
		}
	}

	private SkillXp()
	{
	}

	/** Total xp needed to reach {@code level}. Clamped to 1..99. */
	public static int xpForLevel(int level)
	{
		return XP_TABLE[Math.max(1, Math.min(MAX_LEVEL, level))];
	}

	/** Level for a given total xp. Binary search — this runs on every xp drop. */
	public static int levelForXp(int xp)
	{
		if (xp <= 0)
		{
			return 1;
		}
		int lo = 1;
		int hi = MAX_LEVEL;
		while (lo < hi)
		{
			int mid = (lo + hi + 1) >>> 1;
			if (XP_TABLE[mid] <= xp)
			{
				lo = mid;
			}
			else
			{
				hi = mid - 1;
			}
		}
		return lo;
	}

	/** XP still needed for the next level, or 0 at 99. */
	public static int xpToNextLevel(int xp)
	{
		int level = levelForXp(xp);
		return level >= MAX_LEVEL ? 0 : XP_TABLE[level + 1] - xp;
	}

	/** Progress through the current level, 0.0-1.0. Drives the panel's xp bar. */
	public static double progressInLevel(int xp)
	{
		int level = levelForXp(xp);
		if (level >= MAX_LEVEL)
		{
			return 1.0;
		}
		int base = XP_TABLE[level];
		int next = XP_TABLE[level + 1];
		return next == base ? 1.0 : (double) (xp - base) / (double) (next - base);
	}

	/** True if adding {@code gained} to {@code before} crosses a level boundary. */
	public static boolean isLevelUp(int before, int gained)
	{
		return levelForXp(before + gained) > levelForXp(before);
	}
}
