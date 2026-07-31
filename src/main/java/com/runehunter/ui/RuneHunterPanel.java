package com.runehunter.ui;

import com.runehunter.RuneHunterPlugin;
import com.runehunter.data.Archetype;
import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import com.runehunter.data.OrbType;
import com.runehunter.data.Tier;
import com.runehunter.storage.CollectionStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;

/**
 * RuneHunter side panel. Two tabs: the GoDex (collection browser with
 * Pokedex-style silhouettes) and Activity (your recent catch history —
 * stored locally for now; friend feeds arrive with the Party update).
 */
public class RuneHunterPanel extends PluginPanel
{
	private static final Color GOLD = new Color(0xE8B84A);
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color UNCAUGHT_TEXT = new Color(0x8A8A8A);

	private final CollectionStore store;
	private final Consumer<String> companionSetter;
	private final Runnable trophyOpener;
	private final Runnable consoleOpener;
	/** --developer-mode only; hides the PokeDev launcher on normal clients. */
	private final boolean developerMode;

	private final JProgressBar dexBar = new JProgressBar(0, CreatureRoster.ALL.size());
	private final JLabel essenceLabel = new JLabel();
	private final JPanel orbsRow = new JPanel(new GridLayout(1, 4, 6, 0));
	private final JTextField search = new JTextField();
	private final JComboBox<String> tierFilter =
		new JComboBox<>(new String[]{"All tiers", "Common", "Uncommon", "Rare", "Epic", "Legendary"});
	private final JComboBox<String> stateFilter =
		new JComboBox<>(new String[]{"All", "Caught", "Missing", "Shiny"});
	private final JPanel listPanel = new JPanel();
	private final JPanel activityList = new JPanel();

	private final ImageIcon sparkleIcon;
	private final Map<String, ImageIcon> silCache = new HashMap<>();

	public RuneHunterPanel(CollectionStore store, Consumer<String> companionSetter,
		Runnable trophyOpener, Runnable consoleOpener, boolean developerMode)
	{
		// Unwrapped: we manage our own scroll panes so the mouse wheel works
		super(false);
		this.store = store;
		this.companionSetter = companionSetter;
		this.trophyOpener = trophyOpener;
		this.consoleOpener = consoleOpener;
		this.developerMode = developerMode;

		sparkleIcon = icon("sparkle.png");

		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Banner
		BufferedImage bannerImg = safeLoad("banner.png");
		JLabel banner;
		if (bannerImg != null)
		{
			banner = new JLabel(new ImageIcon(bannerImg));
		}
		else
		{
			banner = new JLabel("RuneHunter", SwingConstants.CENTER);
			banner.setFont(FontManager.getRunescapeBoldFont().deriveFont(22f));
			banner.setForeground(GOLD);
		}

		// Tabs
		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		MaterialTab dexTab = new MaterialTab("GoDex", tabGroup, buildGodexView());
		MaterialTab activityTab = new MaterialTab("Activity", tabGroup, buildActivityView());
		tabGroup.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
		tabGroup.addTab(dexTab);
		tabGroup.addTab(activityTab);
		tabGroup.select(dexTab);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		banner.setAlignmentX(Component.CENTER_ALIGNMENT);
		tabGroup.setAlignmentX(Component.CENTER_ALIGNMENT);
		top.add(banner);
		top.add(Box.createVerticalStrut(6));
		JPanel launchers = buildLauncherRow();
		launchers.setAlignmentX(Component.CENTER_ALIGNMENT);
		top.add(launchers);
		top.add(tabGroup);

		add(top, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		tierFilter.addActionListener(e -> rebuild());
		stateFilter.addActionListener(e -> rebuild());
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e)
			{
				rebuild();
			}

			public void removeUpdate(DocumentEvent e)
			{
				rebuild();
			}

			public void changedUpdate(DocumentEvent e)
			{
				rebuild();
			}
		});

		rebuild();
	}

	/**
	 * Quick-launch buttons. Trophy Room is always present; PokeDev only exists when
	 * RuneLite was launched with --developer-mode, so the shipped build shows a single
	 * full-width Trophy Room button and no dev surface at all.
	 */
	private JPanel buildLauncherRow()
	{
		JPanel row = new JPanel(new GridLayout(1, developerMode ? 2 : 1, 6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		row.add(launcherButton("Trophy Room",
			"View and gear up your caught creatures", trophyOpener));
		if (developerMode)
		{
			row.add(launcherButton("PokeDev",
				"Open the dev console (spawn/debug tools)", consoleOpener));
		}
		return row;
	}

	private JButton launcherButton(String label, String tooltip, Runnable action)
	{
		JButton b = new JButton(label);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setBackground(CARD);
		b.setForeground(GOLD);
		b.setFocusPainted(false);
		b.setToolTipText(tooltip);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(120, 96, 50)),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)));
		b.addActionListener(e -> action.run());
		return b;
	}

	// ---- GoDex tab ----

	private JPanel buildGodexView()
	{
		JPanel view = new JPanel(new BorderLayout(0, 6));
		view.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		dexBar.setStringPainted(true);
		dexBar.setFont(FontManager.getRunescapeSmallFont());
		dexBar.setForeground(GOLD);
		dexBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		dexBar.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR));
		dexBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		header.add(dexBar);
		header.add(Box.createVerticalStrut(4));

		essenceLabel.setIcon(sparkleIcon);
		essenceLabel.setFont(FontManager.getRunescapeSmallFont());
		essenceLabel.setForeground(new Color(0x80CBC4));
		essenceLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		header.add(essenceLabel);
		header.add(Box.createVerticalStrut(6));

		orbsRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		orbsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
		header.add(orbsRow);
		header.add(Box.createVerticalStrut(6));

		search.setFont(FontManager.getRunescapeSmallFont());
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setForeground(Color.WHITE);
		search.setCaretColor(Color.WHITE);
		search.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		search.setToolTipText("Search creatures");
		search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		header.add(search);
		header.add(Box.createVerticalStrut(6));

		JPanel filters = new JPanel(new GridLayout(1, 2, 6, 0));
		filters.setBackground(ColorScheme.DARK_GRAY_COLOR);
		styleCombo(tierFilter);
		styleCombo(stateFilter);
		filters.add(tierFilter);
		filters.add(stateFilter);
		filters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		header.add(filters);

		view.add(header, BorderLayout.NORTH);

		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		view.add(wrapScroll(listPanel), BorderLayout.CENTER);
		return view;
	}

	// ---- Activity tab ----

	private JPanel buildActivityView()
	{
		JPanel view = new JPanel(new BorderLayout(0, 6));
		view.setBackground(ColorScheme.DARK_GRAY_COLOR);

		activityList.setLayout(new BoxLayout(activityList, BoxLayout.Y_AXIS));
		activityList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		view.add(wrapScroll(activityList), BorderLayout.CENTER);
		return view;
	}

	private JScrollPane wrapScroll(JPanel content)
	{
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.add(content, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(wrap,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	private void styleCombo(JComboBox<String> combo)
	{
		combo.setFont(FontManager.getRunescapeSmallFont());
		combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		combo.setForeground(Color.WHITE);
		combo.setFocusable(false);
	}

	/** Thread-safe refresh. */
	public void refresh()
	{
		SwingUtilities.invokeLater(this::rebuild);
	}

	private void rebuild()
	{
		int caught = store.totalCaughtSpecies();
		dexBar.setValue(caught);
		dexBar.setString("GoDex  " + caught + " / " + CreatureRoster.ALL.size());
		essenceLabel.setText(" " + store.getEssence() + " essence");

		orbsRow.removeAll();
		for (OrbType t : OrbType.values())
		{
			orbsRow.add(orbCell(t));
		}

		rebuildDexList();
		rebuildActivity();

		revalidate();
		repaint();
	}

	private void rebuildDexList()
	{
		listPanel.removeAll();
		Tier tierSel = tierFilter.getSelectedIndex() <= 0
			? null : Tier.values()[tierFilter.getSelectedIndex() - 1];
		String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);

		for (Tier tier : Tier.values())
		{
			if (tierSel != null && tier != tierSel)
			{
				continue;
			}
			boolean headerAdded = false;
			int tierTotal = 0;
			int tierCaught = 0;
			for (CreatureDef d : CreatureRoster.ALL)
			{
				if (d.getTier() == tier)
				{
					tierTotal++;
					if (store.isCaught(d))
					{
						tierCaught++;
					}
				}
			}
			for (CreatureDef d : CreatureRoster.ALL)
			{
				if (d.getTier() != tier || !passesFilters(d, q))
				{
					continue;
				}
				if (!headerAdded)
				{
					listPanel.add(tierHeader(tier, tierCaught, tierTotal));
					headerAdded = true;
				}
				listPanel.add(dexRow(d));
				listPanel.add(Box.createVerticalStrut(2));
			}
			if (headerAdded)
			{
				listPanel.add(Box.createVerticalStrut(6));
			}
		}

		appendSecretSection(tierSel, q);
	}

	/**
	 * The SECRET section, pinned to the bottom of the dex.
	 *
	 * <p>It does not exist until you have caught at least one, and it only
	 * ever lists the ones you own: no placeholder rows, no total, nothing that
	 * hints at how many are out there. The dex progress bar above is untouched
	 * — secrets live outside CreatureRoster.ALL, so the total stays at 87.
	 */
	private void appendSecretSection(Tier tierSel, String q)
	{
		// secrets are Legendary-difficulty, so they belong under that filter
		if (tierSel != null && tierSel != Tier.LEGENDARY)
		{
			return;
		}
		List<CreatureDef> owned = store.caughtSecrets();
		if (owned.isEmpty())
		{
			return;
		}
		boolean headerAdded = false;
		for (CreatureDef d : owned)
		{
			if (!passesFilters(d, q))
			{
				continue;
			}
			if (!headerAdded)
			{
				listPanel.add(secretHeader(owned.size()));
				headerAdded = true;
			}
			listPanel.add(dexRow(d));
			listPanel.add(Box.createVerticalStrut(2));
		}
		if (headerAdded)
		{
			listPanel.add(Box.createVerticalStrut(6));
		}
	}

	/** Gold, sparkled section header. Shows a found-count, never a total. */
	private Component secretHeader(int found)
	{
		JPanel head = new JPanel(new BorderLayout());
		head.setBackground(ColorScheme.DARK_GRAY_COLOR);
		head.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, GOLD),
			BorderFactory.createEmptyBorder(5, 2, 3, 2)));
		head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		JLabel name = new JLabel("SECRET");
		name.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		name.setForeground(GOLD);
		name.setIcon(sparkleIcon);
		name.setIconTextGap(4);
		name.setToolTipText("Things you weren't supposed to find.");
		head.add(name, BorderLayout.WEST);

		JLabel prog = new JLabel(found + " found");
		prog.setFont(FontManager.getRunescapeSmallFont());
		prog.setForeground(GOLD);
		head.add(prog, BorderLayout.EAST);
		return head;
	}

	private void rebuildActivity()
	{
		activityList.removeAll();

		JLabel title = new JLabel("Recent catches — you");
		title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		title.setForeground(Color.WHITE);
		title.setBorder(BorderFactory.createEmptyBorder(2, 2, 4, 2));
		activityList.add(title);

		List<String[]> recent = store.recentCatches();
		if (recent.isEmpty())
		{
			JLabel empty = new JLabel("No catches yet — get hunting!");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(UNCAUGHT_TEXT);
			empty.setBorder(BorderFactory.createEmptyBorder(2, 2, 8, 2));
			activityList.add(empty);
		}
		for (String[] entry : recent)
		{
			CreatureDef d = CreatureRoster.byKey(entry[1]);
			if (d == null)
			{
				continue;
			}
			long ts;
			try
			{
				ts = Long.parseLong(entry[0]);
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			activityList.add(activityRow(d, "1".equals(entry[2]), ts));
			activityList.add(Box.createVerticalStrut(2));
		}

		activityList.add(Box.createVerticalStrut(10));
		JLabel friendsTitle = new JLabel("Friends");
		friendsTitle.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		friendsTitle.setForeground(Color.WHITE);
		friendsTitle.setBorder(BorderFactory.createEmptyBorder(2, 2, 4, 2));
		activityList.add(friendsTitle);

		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(CARD);
		card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		JLabel soon = new JLabel("<html>Catch history is stored locally for now."
			+ "<br>Friend feeds arrive with the Party update —"
			+ "<br>you'll see what your friends catch in real time.</html>");
		soon.setFont(FontManager.getRunescapeSmallFont());
		soon.setForeground(UNCAUGHT_TEXT);
		card.add(soon, BorderLayout.CENTER);
		activityList.add(card);
	}

	private Component activityRow(CreatureDef d, boolean shiny, long ts)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(CARD);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, d.getTier().getColor()),
			BorderFactory.createEmptyBorder(3, 5, 3, 6)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		JLabel sil = new JLabel(silhouette(d, true));
		row.add(sil, BorderLayout.WEST);

		JLabel label = new JLabel(d.getNpcName());
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.WHITE);
		if (shiny)
		{
			label.setIcon(sparkleIcon);
			label.setHorizontalTextPosition(SwingConstants.LEADING);
			label.setIconTextGap(4);
		}
		row.add(label, BorderLayout.CENTER);

		JLabel when = new JLabel(relativeTime(ts));
		when.setFont(FontManager.getRunescapeSmallFont());
		when.setForeground(Color.GRAY);
		row.add(when, BorderLayout.EAST);
		return row;
	}

	private static String relativeTime(long epochMillis)
	{
		long mins = Math.max(0, (System.currentTimeMillis() - epochMillis) / 60000L);
		if (mins < 1)
		{
			return "just now";
		}
		if (mins < 60)
		{
			return mins + "m ago";
		}
		long hours = mins / 60;
		if (hours < 24)
		{
			return hours + "h ago";
		}
		return (hours / 24) + "d ago";
	}

	// ---- shared bits ----

	private boolean passesFilters(CreatureDef d, String q)
	{
		boolean caught = store.isCaught(d);
		switch (stateFilter.getSelectedIndex())
		{
			case 1:
				if (!caught)
				{
					return false;
				}
				break;
			case 2:
				if (caught)
				{
					return false;
				}
				break;
			case 3:
				if (!store.isShinyCaught(d))
				{
					return false;
				}
				break;
			default:
				break;
		}
		if (!q.isEmpty())
		{
			String visible = caught ? d.getNpcName().toLowerCase(Locale.ROOT) : "???";
			return visible.contains(q);
		}
		return true;
	}

	private JPanel orbCell(OrbType t)
	{
		JPanel cell = new JPanel();
		cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
		cell.setBackground(CARD);
		cell.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

		JLabel img = new JLabel(icon("orb_" + t.name().toLowerCase(Locale.ROOT) + ".png"));
		img.setAlignmentX(Component.CENTER_ALIGNMENT);
		cell.add(img);

		JLabel count = new JLabel(Integer.toString(store.orbCount(t)));
		count.setAlignmentX(Component.CENTER_ALIGNMENT);
		count.setFont(FontManager.getRunescapeSmallFont());
		count.setForeground(store.orbCount(t) > 0 ? Color.WHITE : UNCAUGHT_TEXT);
		cell.add(count);

		cell.setToolTipText(t.getDisplayName()
			+ (t == OrbType.ELDRITCH ? " — never fails" : " — x" + t.getCatchMultiplier() + " catch power"));
		return cell;
	}

	private Component tierHeader(Tier tier, int caught, int total)
	{
		JPanel head = new JPanel(new BorderLayout());
		head.setBackground(ColorScheme.DARK_GRAY_COLOR);
		head.setBorder(BorderFactory.createEmptyBorder(4, 2, 3, 2));
		head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		JLabel name = new JLabel(tier.getDisplayName().toUpperCase(Locale.ROOT));
		name.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		name.setForeground(tier.getColor());
		head.add(name, BorderLayout.WEST);

		JLabel prog = new JLabel(caught + "/" + total);
		prog.setFont(FontManager.getRunescapeSmallFont());
		prog.setForeground(caught == total ? GOLD : Color.GRAY);
		head.add(prog, BorderLayout.EAST);
		return head;
	}

	private Component dexRow(CreatureDef d)
	{
		boolean caught = store.isCaught(d);
		boolean shiny = store.isShinyCaught(d);
		boolean isCompanion = d.key().equals(store.getCompanionKey());

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(CARD);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, caught ? d.getTier().getColor() : ColorScheme.DARKER_GRAY_HOVER_COLOR),
			BorderFactory.createEmptyBorder(3, 5, 3, 4)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		JLabel sil = new JLabel(silhouette(d, caught));
		row.add(sil, BorderLayout.WEST);

		JLabel label = new JLabel(caught ? d.getNpcName() : "???");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(caught ? Color.WHITE : UNCAUGHT_TEXT);
		if (shiny)
		{
			label.setIcon(sparkleIcon);
			label.setHorizontalTextPosition(SwingConstants.LEADING);
			label.setIconTextGap(4);
		}
		label.setToolTipText(caught
			? d.getNpcName() + " — " + d.getTier().getDisplayName()
				+ ", caught x" + store.caughtCount(d) + (shiny ? ", shiny owned" : "")
			: d.getTier().getDisplayName() + " — not yet caught");
		row.add(label, BorderLayout.CENTER);

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
		right.setOpaque(false);

		if (caught && store.caughtCount(d) > 1)
		{
			JLabel count = new JLabel("x" + store.caughtCount(d) + " ");
			count.setFont(FontManager.getRunescapeSmallFont());
			count.setForeground(Color.GRAY);
			right.add(count);
		}

		if (caught)
		{
			JButton follow = new JButton(isCompanion ? "✓" : "Follow");
			follow.setFont(FontManager.getRunescapeSmallFont());
			follow.setMargin(new java.awt.Insets(0, 6, 0, 6));
			follow.setFocusPainted(false);
			follow.setBackground(isCompanion ? d.getTier().getColor().darker() : ColorScheme.DARK_GRAY_COLOR);
			follow.setForeground(isCompanion ? Color.WHITE : Color.LIGHT_GRAY);
			follow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			follow.setToolTipText(isCompanion ? "Following you — click to dismiss" : "Set as companion");
			final boolean wasCompanion = isCompanion;
			follow.addActionListener(e ->
			{
				companionSetter.accept(wasCompanion ? null : d.key());
				rebuild();
			});
			right.add(follow);
		}

		row.add(right, BorderLayout.EAST);
		return row;
	}

	private static BufferedImage safeLoad(String resource)
	{
		try
		{
			// ImageUtil throws (not null) on missing resources
			return ImageUtil.loadImageResource(RuneHunterPlugin.class, resource);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private ImageIcon icon(String resource)
	{
		BufferedImage img = safeLoad(resource);
		return img != null ? new ImageIcon(img) : new ImageIcon();
	}

	/**
	 * Marker for the SECRET section: a gold star with a "?" cut into it.
	 * Uses sil_secret.png if the art ever lands, otherwise draws it — no
	 * silhouette PNG is required for secrets to look finished.
	 */
	private ImageIcon secretIcon()
	{
		ImageIcon cached = silCache.get("__secret");
		if (cached != null)
		{
			return cached;
		}

		BufferedImage art = safeLoad("sil_secret.png");
		BufferedImage out;
		if (art != null)
		{
			out = art;
		}
		else
		{
			out = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = out.createGraphics();
			g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
				java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
				java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			// five-pointed star, points alternating outer/inner radius
			int cx = 9;
			int cy = 9;
			int[] xs = new int[10];
			int[] ys = new int[10];
			for (int i = 0; i < 10; i++)
			{
				double r = (i % 2 == 0) ? 8.5 : 3.6;
				double a = -Math.PI / 2 + i * Math.PI / 5;
				xs[i] = (int) Math.round(cx + r * Math.cos(a));
				ys[i] = (int) Math.round(cy + r * Math.sin(a));
			}
			g.setColor(new Color(0x6B5320));
			g.fillPolygon(xs, ys, 10);
			g.setColor(GOLD);
			g.drawPolygon(xs, ys, 10);

			g.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, 10f));
			String mark = "?";
			int mw = g.getFontMetrics().stringWidth(mark);
			g.setColor(GOLD);
			g.drawString(mark, cx - mw / 2, cy + 4);
			g.dispose();
		}

		ImageIcon icon = new ImageIcon(out);
		silCache.put("__secret", icon);
		return icon;
	}

	/**
	 * Archetype silhouette tinted for state: near-black mystery shape with a
	 * "?" while uncaught, tier-colored once caught. Icons are cached.
	 */
	private ImageIcon silhouette(CreatureDef d, boolean caught)
	{
		if (d.isSecret())
		{
			return secretIcon();
		}
		String cacheKey = d.key() + (caught ? ":c" : ":u");
		ImageIcon cached = silCache.get(cacheKey);
		if (cached != null)
		{
			return cached;
		}

		BufferedImage src = safeLoad(Archetype.of(d).resource());
		if (src == null)
		{
			return new ImageIcon();
		}

		Color tint = caught ? d.getTier().getColor() : new Color(0x353535);
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		int rgb = tint.getRGB() & 0x00FFFFFF;
		for (int y = 0; y < src.getHeight(); y++)
		{
			for (int x = 0; x < src.getWidth(); x++)
			{
				int argb = src.getRGB(x, y);
				out.setRGB(x, y, (argb & 0xFF000000) | rgb);
			}
		}

		if (!caught)
		{
			java.awt.Graphics2D g = out.createGraphics();
			g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
				java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, 11f));
			g.setColor(GOLD);
			g.drawString("?", out.getWidth() - 8, out.getHeight() - 1);
			g.dispose();
		}

		ImageIcon icon = new ImageIcon(out);
		silCache.put(cacheKey, icon);
		return icon;
	}
}
