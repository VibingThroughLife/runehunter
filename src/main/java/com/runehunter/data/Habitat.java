package com.runehunter.data;

import net.runelite.api.coords.WorldPoint;

/**
 * Coarse geographic zones used to build regional spawn tables. Bounds are
 * approximate surface-world rectangles (inclusive), intentionally generous.
 */
public enum Habitat
{
	EVERYWHERE(null),
	MISTHALIN(new int[][]{{3086, 3136, 3327, 3520}}),
	ASGARNIA(new int[][]{{2880, 3200, 3086, 3520}}),
	KANDARIN(new int[][]{{2432, 3264, 2880, 3520}}),
	MORYTANIA(new int[][]{{3392, 3136, 3711, 3583}}),
	WILDERNESS(new int[][]{{2944, 3520, 3392, 3967}}),
	DESERT(new int[][]{{3136, 2624, 3520, 3200}}),
	KARAMJA(new int[][]{{2688, 2880, 2988, 3270}}),
	FREMENNIK(new int[][]{{2560, 3584, 2816, 3903}}),
	KEBOS(new int[][]{{1152, 3392, 1920, 3903}}),
	TIRANNWN(new int[][]{{2112, 3136, 2432, 3327}});

	private final int[][] bounds;

	Habitat(int[][] bounds)
	{
		this.bounds = bounds;
	}

	public boolean contains(WorldPoint p)
	{
		if (bounds == null)
		{
			return true;
		}
		for (int[] b : bounds)
		{
			if (p.getX() >= b[0] && p.getY() >= b[1] && p.getX() <= b[2] && p.getY() <= b[3])
			{
				return true;
			}
		}
		return false;
	}
}
