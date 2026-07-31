package com.runehunter.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Kitbash instructions for a creature whose mini model is not simply "the
 * NPC's own model" — the Secret Dex characters have no OSRS NPC that looks
 * like them, so we build them out of parts:
 *
 * <ul>
 *   <li>a base NPC model (resolved the normal way, from the def), plus</li>
 *   <li>zero or more <b>item</b> models merged on top (3rd Age armour), plus</li>
 *   <li>palette work: a global desaturate, exact HSL swaps, and geometry-aware
 *       {@link SlabPaint} fills that paint a slice of the model's bounding box
 *       (Jimmothy's bandit mask and ringed tail).</li>
 * </ul>
 *
 * Nothing here touches the client directly — {@code NpcModelCache} executes a
 * recipe on the client thread. Recipes are pure data so every tunable number
 * lives in one place ({@link SecretIds}) and can be hot-swapped after
 * eyeballing the result in game.
 */
public final class ModelRecipe
{
	/** Which model axis a {@link SlabPaint} slices along. */
	public enum Axis
	{
		/** East/west. */
		X,
		/** Vertical. NOTE: in Jagex model space Y grows <i>downward</i>, so
		 *  fraction 0 is the top of the model and 1 is its feet. */
		Y,
		/** North/south. */
		Z
	}

	/**
	 * Repaint every face whose centroid falls inside a fractional slab of the
	 * model's bounding box. This is how we get features the palette alone
	 * can't give us — a dark band across the eyes, rings down a tail — without
	 * shipping custom geometry.
	 */
	public static final class SlabPaint
	{
		private final Axis axis;
		private final double from;
		private final double to;
		private final short color;
		private final int stripes;

		/**
		 * @param axis    axis the slab is measured along
		 * @param from    slab start, 0..1 of the bounding box on that axis
		 * @param to      slab end, 0..1
		 * @param color   packed Jagex HSL to paint with
		 * @param stripes 0 = fill the slab solid; n &gt; 0 = paint n evenly
		 *                spaced bands inside the slab (the raccoon tail rings)
		 */
		public SlabPaint(Axis axis, double from, double to, short color, int stripes)
		{
			this.axis = axis;
			this.from = Math.min(from, to);
			this.to = Math.max(from, to);
			this.color = color;
			this.stripes = Math.max(0, stripes);
		}

		public Axis getAxis()
		{
			return axis;
		}

		public double getFrom()
		{
			return from;
		}

		public double getTo()
		{
			return to;
		}

		public short getColor()
		{
			return color;
		}

		public int getStripes()
		{
			return stripes;
		}
	}

	/** An exact palette swap, applied through {@code ModelData.recolor}. */
	public static final class Recolor
	{
		private final short from;
		private final short to;

		public Recolor(short from, short to)
		{
			this.from = from;
			this.to = to;
		}

		public short getFrom()
		{
			return from;
		}

		public short getTo()
		{
			return to;
		}
	}

	private final int[] mergeItemIds;
	private final int mergeItemScale;
	private final int mergeItemLift;
	private final boolean desaturate;
	private final Recolor[] recolors;
	private final SlabPaint[] slabs;
	private final int scaleOverride;

	private ModelRecipe(Builder b)
	{
		this.mergeItemIds = b.mergeItemIds.stream().mapToInt(Integer::intValue).toArray();
		this.mergeItemScale = b.mergeItemScale;
		this.mergeItemLift = b.mergeItemLift;
		this.desaturate = b.desaturate;
		this.recolors = b.recolors.toArray(new Recolor[0]);
		this.slabs = b.slabs.toArray(new SlabPaint[0]);
		this.scaleOverride = b.scaleOverride;
	}

	/** Item ids whose cache models get merged onto the base NPC model. */
	public int[] getMergeItemIds()
	{
		return mergeItemIds;
	}

	/** Uniform scale (native 128) applied to each merged item model. */
	public int getMergeItemScale()
	{
		return mergeItemScale;
	}

	/** Height offset for merged item models, in model units (positive = up). */
	public int getMergeItemLift()
	{
		return mergeItemLift;
	}

	/** Strip all saturation, keeping luminance — turns any base grey. */
	public boolean isDesaturate()
	{
		return desaturate;
	}

	public Recolor[] getRecolors()
	{
		return recolors;
	}

	public SlabPaint[] getSlabs()
	{
		return slabs;
	}

	/** Absolute mini scale (native 128), or -1 to use the normal size rule. */
	public int getScaleOverride()
	{
		return scaleOverride;
	}

	public static Builder builder()
	{
		return new Builder();
	}

	/** Fluent assembler — recipes read as a parts list rather than a 7-arg ctor. */
	public static final class Builder
	{
		private final List<Integer> mergeItemIds = new ArrayList<>();
		private final List<Recolor> recolors = new ArrayList<>();
		private final List<SlabPaint> slabs = new ArrayList<>();
		private int mergeItemScale = 128;
		private int mergeItemLift;
		private boolean desaturate;
		private int scaleOverride = -1;

		public Builder mergeItems(int... itemIds)
		{
			for (int id : itemIds)
			{
				if (id > 0)
				{
					mergeItemIds.add(id);
				}
			}
			return this;
		}

		public Builder itemScale(int scale)
		{
			this.mergeItemScale = Math.max(1, scale);
			return this;
		}

		public Builder itemLift(int lift)
		{
			this.mergeItemLift = lift;
			return this;
		}

		public Builder desaturate()
		{
			this.desaturate = true;
			return this;
		}

		public Builder recolor(short from, short to)
		{
			recolors.add(new Recolor(from, to));
			return this;
		}

		public Builder paint(Axis axis, double from, double to, short color)
		{
			slabs.add(new SlabPaint(axis, from, to, color, 0));
			return this;
		}

		public Builder stripe(Axis axis, double from, double to, short color, int stripes)
		{
			slabs.add(new SlabPaint(axis, from, to, color, stripes));
			return this;
		}

		public Builder scale(int absoluteScale)
		{
			this.scaleOverride = absoluteScale;
			return this;
		}

		public ModelRecipe build()
		{
			return new ModelRecipe(this);
		}
	}
}
