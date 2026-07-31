package com.runehunter.ui;

import com.runehunter.RuneHunterPlugin;
import com.runehunter.data.Archetype;
import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import com.runehunter.data.GearItem;
import com.runehunter.party.PartyHub;
import com.runehunter.storage.CollectionStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

/**
 * The Trophy Room — RuneHunter's locker/lobby screen. Undecorated dark Swing
 * window (same chrome as the PokeDev console): caught-creature list on the
 * left, a painted stone podium showcasing the selected creature in the
 * center, and the Equipment column (Helm / Body / Weapon slots + gear bank)
 * on the right.
 *
 * The podium is a self-contained inner component fed plain display data
 * (CreatureDef, level, xp, record, gear, shiny) rather than reading the
 * store — so the same component can later showcase OTHER players' creatures
 * for trades and showoffs.
 */
public class TrophyRoom extends JFrame
{
	private static final Color BG = new Color(18, 18, 18);
	private static final Color PANEL = new Color(28, 28, 28);
	private static final Color GOLD = new Color(0xE8B84A);
	private static final Color TEXT = new Color(0xD8CCB4);
	private static final Color INK = new Color(0x3E3529);
	private static final Color WOOD = new Color(0x5D4E37);
	private static final Color BORDER = new Color(120, 96, 50);
	private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

	private static final String UNEQUIP = "(unequip)";

	private final CollectionStore store;
	private final Runnable onChange;

	// image caches — misses cached as null (ImageUtil THROWS on missing files)
	private final Map<String, BufferedImage> imgCache = new HashMap<>();
	private final Map<String, BufferedImage> tintCache = new HashMap<>();
	private final Map<String, ImageIcon> iconCache = new HashMap<>();

	private final DefaultListModel<CreatureDef> listModel = new DefaultListModel<>();
	private final JList<CreatureDef> caughtList = new JList<>(listModel);
	private final PodiumPanel podium = new PodiumPanel();
	private final JLabel[] slotLabels = new JLabel[3];
	private final List<JComboBox<Object>> slotCombos = new ArrayList<>();
	private final JPanel bankPanel = new JPanel();
	private final JLabel statLine = new JLabel(" ");
	private final JPanel partyStrip = new JPanel();

	private volatile PartyHub partyHub;
	private CreatureDef selected;
	private boolean updatingUi;

	public TrophyRoom(CollectionStore store, Runnable onChange)
	{
		super("RuneHunter — Trophy Room");
		this.store = store;
		this.onChange = onChange;

		setUndecorated(true);
		setAlwaysOnTop(true);
		setSize(760, 520);
		try
		{
			setOpacity(0.97f);
		}
		catch (Exception ignored)
		{
			// translucency unsupported on this platform — stay opaque
		}

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(BG);
		root.setBorder(BorderFactory.createLineBorder(BORDER));
		root.add(buildHeader(), BorderLayout.NORTH);
		root.add(buildBody(), BorderLayout.CENTER);
		setContentPane(root);

		refresh();
	}

	// ------------------------------------------------------------------
	// window chrome (DevConsole pattern: draggable header + ✕)
	// ------------------------------------------------------------------

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(PANEL);
		header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));

		JLabel title = new JLabel("RuneHunter — Trophy Room");
		title.setForeground(GOLD);
		title.setFont(MONO.deriveFont(Font.BOLD, 13f));
		header.add(title, BorderLayout.WEST);

		header.add(new WindowCloseButton(() -> setVisible(false)), BorderLayout.EAST);

		MouseAdapter drag = new MouseAdapter()
		{
			private Point grab;

			@Override
			public void mousePressed(MouseEvent e)
			{
				grab = e.getPoint();
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				Point screen = e.getLocationOnScreen();
				setLocation(screen.x - grab.x, screen.y - grab.y);
			}
		};
		header.addMouseListener(drag);
		header.addMouseMotionListener(drag);
		return header;
	}

	// ------------------------------------------------------------------
	// body: caught list | podium | equipment
	// ------------------------------------------------------------------

	private JPanel buildBody()
	{
		JPanel body = new JPanel(new BorderLayout(6, 0));
		body.setBackground(BG);
		body.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		body.add(buildCaughtList(), BorderLayout.WEST);
		body.add(podium, BorderLayout.CENTER);
		body.add(buildEquipmentColumn(), BorderLayout.EAST);
		body.add(buildPartyStrip(), BorderLayout.SOUTH);
		return body;
	}

	/** Wiring: trophyRoom.setPartyHub(partyHub) — enables the Party strip. */
	public void setPartyHub(PartyHub hub)
	{
		this.partyHub = hub;
		refresh();
	}

	// ---- Party strip: presence + Trade / Duel / Practice ----

	private JPanel buildPartyStrip()
	{
		JPanel wrap = new JPanel(new BorderLayout(6, 0));
		wrap.setBackground(PANEL);
		wrap.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, WOOD),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)));

		JLabel title = new JLabel("Party");
		title.setFont(MONO.deriveFont(Font.BOLD, 12f));
		title.setForeground(GOLD);
		wrap.add(title, BorderLayout.WEST);

		partyStrip.setLayout(new BoxLayout(partyStrip, BoxLayout.X_AXIS));
		partyStrip.setBackground(PANEL);
		wrap.add(partyStrip, BorderLayout.CENTER);

		JButton practice = miniButton("Practice duel");
		practice.addActionListener(e ->
		{
			PartyHub hub = partyHub;
			if (hub != null)
			{
				hub.practiceDuel();
			}
		});
		wrap.add(practice, BorderLayout.EAST);
		return wrap;
	}

	private JButton miniButton(String label)
	{
		JButton b = new JButton(label);
		b.setFont(MONO.deriveFont(11f));
		b.setBackground(new Color(0x2E2A22));
		b.setForeground(TEXT);
		b.setFocusPainted(false);
		b.setMargin(new java.awt.Insets(1, 6, 1, 6));
		return b;
	}

	private void rebuildPartyStrip()
	{
		partyStrip.removeAll();
		PartyHub hub = partyHub;
		if (hub == null)
		{
			partyStrip.revalidate();
			partyStrip.repaint();
			return;
		}
		List<PartyHub.MemberInfo> members = hub.getMembers();
		if (members.isEmpty())
		{
			JLabel none = new JLabel(hub.isInParty()
				? "  No other party members yet." : "  Join a RuneLite party to trade & duel.");
			none.setFont(MONO.deriveFont(11f));
			none.setForeground(TEXT);
			partyStrip.add(none);
		}
		for (PartyHub.MemberInfo member : members)
		{
			JLabel name = new JLabel("  " + member.getName()
				+ (member.isHasRuneHunter()
					? (member.getCompanionDesc().isEmpty() ? "" : " (" + member.getCompanionDesc() + ")")
					: " (no RuneHunter)") + " ");
			name.setFont(MONO.deriveFont(11f));
			name.setForeground(member.isHasRuneHunter() ? TEXT : new Color(0x8C8478));
			partyStrip.add(name);

			if (member.isHasRuneHunter())
			{
				JButton trade = miniButton("Trade");
				trade.addActionListener(e -> hub.startTrade(member.getId(), member.getName()));
				partyStrip.add(trade);
				partyStrip.add(javax.swing.Box.createRigidArea(new Dimension(3, 1)));

				JButton duel = miniButton("Duel");
				duel.addActionListener(e -> hub.challengeDuel(member.getId(), member.getName()));
				partyStrip.add(duel);
			}
			partyStrip.add(javax.swing.Box.createRigidArea(new Dimension(8, 1)));
		}
		partyStrip.revalidate();
		partyStrip.repaint();
	}

	private JScrollPane buildCaughtList()
	{
		caughtList.setBackground(BG);
		caughtList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		caughtList.setCellRenderer(new CaughtRenderer());
		caughtList.setFixedCellHeight(30);
		caughtList.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting())
			{
				selected = caughtList.getSelectedValue();
				updateShowcase();
			}
		});

		JScrollPane scroll = new JScrollPane(caughtList,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setPreferredSize(new Dimension(190, 10));
		scroll.setBorder(BorderFactory.createLineBorder(PANEL));
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	private final class CaughtRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value,
			int index, boolean isSelected, boolean cellHasFocus)
		{
			JLabel label = (JLabel) super.getListCellRendererComponent(
				list, value, index, isSelected, cellHasFocus);
			CreatureDef d = (CreatureDef) value;
			label.setFont(MONO);
			label.setText(d.getNpcName() + "  Lv " + store.getLevel(d));
			label.setIcon(listIcon(d));
			label.setIconTextGap(6);
			label.setForeground(d.getTier().getColor());
			label.setBackground(isSelected ? WOOD : index % 2 == 0 ? BG : PANEL);
			label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 4));
			return label;
		}

		private static final long serialVersionUID = 1L;
	}

	private JPanel buildEquipmentColumn()
	{
		JPanel col = new JPanel();
		col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
		col.setBackground(BG);
		col.setPreferredSize(new Dimension(230, 10));
		col.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

		col.add(sectionTitle("Equipment"));

		String[] slotNames = {"Helm", "Body", "Weapon"};
		for (int i = 0; i < 3; i++)
		{
			final GearItem.Slot slot = GearItem.Slot.values()[i];

			// stone slot box showing the equipped item
			JPanel box = new JPanel(new BorderLayout());
			box.setBackground(new Color(0x2E2A22));
			box.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(WOOD),
				BorderFactory.createEmptyBorder(4, 8, 4, 8)));
			box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

			JLabel slotName = new JLabel(slotNames[i]);
			slotName.setFont(MONO.deriveFont(11f));
			slotName.setForeground(GOLD);
			box.add(slotName, BorderLayout.WEST);

			slotLabels[i] = new JLabel("empty");
			slotLabels[i].setFont(MONO.deriveFont(Font.BOLD, 12f));
			slotLabels[i].setForeground(TEXT);
			slotLabels[i].setHorizontalAlignment(JLabel.RIGHT);
			box.add(slotLabels[i], BorderLayout.CENTER);
			col.add(box);

			JComboBox<Object> combo = new JComboBox<>();
			combo.setFont(MONO.deriveFont(11f));
			combo.setBackground(PANEL);
			combo.setForeground(TEXT);
			combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
			combo.setRenderer(new GearComboRenderer());
			combo.addActionListener(e -> applyEquip(slot, combo));
			slotCombos.add(combo);
			col.add(combo);
			col.add(spacer(6));
		}

		statLine.setFont(MONO.deriveFont(Font.BOLD, 12f));
		statLine.setForeground(GOLD);
		statLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		col.add(statLine);
		col.add(spacer(8));

		col.add(sectionTitle("Bank"));
		bankPanel.setLayout(new BoxLayout(bankPanel, BoxLayout.Y_AXIS));
		bankPanel.setBackground(BG);
		JPanel bankWrap = new JPanel(new BorderLayout());
		bankWrap.setBackground(BG);
		bankWrap.add(bankPanel, BorderLayout.NORTH);
		JScrollPane bankScroll = new JScrollPane(bankWrap,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		bankScroll.setBorder(BorderFactory.createLineBorder(PANEL));
		bankScroll.getVerticalScrollBar().setUnitIncrement(16);
		bankScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		col.add(bankScroll);
		return col;
	}

	private static final class GearComboRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value,
			int index, boolean isSelected, boolean cellHasFocus)
		{
			JLabel label = (JLabel) super.getListCellRendererComponent(
				list, value, index, isSelected, cellHasFocus);
			label.setFont(MONO.deriveFont(11f));
			if (value instanceof GearItem)
			{
				GearItem item = (GearItem) value;
				label.setText(item.getDisplayName()
					+ statSuffix(item.getAtk(), item.getDef()));
				label.setForeground(item.getColor());
			}
			else
			{
				label.setForeground(TEXT);
			}
			label.setBackground(isSelected ? WOOD : PANEL);
			return label;
		}

		private static final long serialVersionUID = 1L;
	}

	private static String statSuffix(int atk, int def)
	{
		StringBuilder sb = new StringBuilder();
		if (atk > 0)
		{
			sb.append("  +").append(atk).append(" Atk");
		}
		if (def > 0)
		{
			sb.append("  +").append(def).append(" Def");
		}
		return sb.toString();
	}

	private JLabel sectionTitle(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(MONO.deriveFont(Font.BOLD, 13f));
		label.setForeground(GOLD);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		return label;
	}

	private Component spacer(int h)
	{
		return javax.swing.Box.createRigidArea(new Dimension(1, h));
	}

	// ------------------------------------------------------------------
	// equip handling
	// ------------------------------------------------------------------

	private void applyEquip(GearItem.Slot slot, JComboBox<Object> combo)
	{
		if (updatingUi || selected == null)
		{
			return;
		}
		Object sel = combo.getSelectedItem();
		GearItem item = sel instanceof GearItem ? (GearItem) sel : null;
		GearItem current = store.getEquipped(selected)[slot.ordinal()];
		if (item == current)
		{
			return;
		}
		store.equip(selected, slot, item);
		onChange.run();
		refresh();
	}

	// ------------------------------------------------------------------
	// refresh — safe from any thread
	// ------------------------------------------------------------------

	/** Reloads all lists from the store. Call after catches/battles. */
	public void refresh()
	{
		SwingUtilities.invokeLater(this::refreshOnEdt);
	}

	private void refreshOnEdt()
	{
		updatingUi = true;
		try
		{
			CreatureDef keep = selected;
			listModel.clear();
			for (CreatureDef d : CreatureRoster.ALL_INCLUDING_SECRETS)
			{
				if (store.isCaught(d))
				{
					listModel.addElement(d);
				}
			}
			if (keep != null && listModel.contains(keep))
			{
				caughtList.setSelectedValue(keep, true);
				selected = keep;
			}
			else if (!listModel.isEmpty())
			{
				caughtList.setSelectedIndex(0);
				selected = listModel.get(0);
			}
			else
			{
				selected = null;
			}
		}
		finally
		{
			updatingUi = false;
		}
		updateShowcase();
		rebuildPartyStrip();
	}

	/** Repopulates podium + equipment column for the current selection. */
	private void updateShowcase()
	{
		if (updatingUi)
		{
			return;
		}
		updatingUi = true;
		try
		{
			if (selected == null)
			{
				podium.show(null, 1, 0, 0, 0, new GearItem[3], false);
				for (int i = 0; i < 3; i++)
				{
					slotLabels[i].setText("empty");
					slotLabels[i].setForeground(TEXT);
					slotCombos.get(i).removeAllItems();
					slotCombos.get(i).setEnabled(false);
				}
				statLine.setText(" ");
			}
			else
			{
				GearItem[] equipped = store.getEquipped(selected);
				podium.show(selected, store.getLevel(selected), store.getXp(selected),
					store.getWins(selected), store.getLosses(selected),
					equipped, store.isShinyCaught(selected));

				List<GearItem> inventory = store.getGearInventory();
				int totalAtk = 0;
				int totalDef = 0;
				for (int i = 0; i < 3; i++)
				{
					GearItem cur = equipped[i];
					slotLabels[i].setText(cur == null ? "empty" : cur.getDisplayName());
					slotLabels[i].setForeground(cur == null ? TEXT : cur.getColor());
					if (cur != null)
					{
						totalAtk += cur.getAtk();
						totalDef += cur.getDef();
					}

					JComboBox<Object> combo = slotCombos.get(i);
					combo.removeAllItems();
					combo.addItem(UNEQUIP);
					GearItem.Slot slot = GearItem.Slot.values()[i];
					for (GearItem g : GearItem.values())
					{
						if (g.getSlot() == slot && (inventory.contains(g) || g == cur))
						{
							combo.addItem(g);
						}
					}
					combo.setSelectedItem(cur == null ? UNEQUIP : cur);
					combo.setEnabled(true);
				}
				statLine.setText("Total:  +" + totalAtk + " Atk   +" + totalDef + " Def");
			}
			rebuildBank();
		}
		finally
		{
			updatingUi = false;
		}
	}

	private void rebuildBank()
	{
		bankPanel.removeAll();
		Map<GearItem, Integer> counts = new LinkedHashMap<>();
		for (GearItem g : store.getGearInventory())
		{
			counts.merge(g, 1, Integer::sum);
		}
		if (counts.isEmpty())
		{
			JLabel empty = new JLabel("Nothing banked yet.");
			empty.setFont(MONO.deriveFont(11f));
			empty.setForeground(TEXT);
			empty.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 4));
			bankPanel.add(empty);
		}
		else
		{
			int i = 0;
			for (Map.Entry<GearItem, Integer> e : counts.entrySet())
			{
				JLabel row = new JLabel(e.getKey().getDisplayName()
					+ (e.getValue() > 1 ? "  x" + e.getValue() : "")
					+ statSuffix(e.getKey().getAtk(), e.getKey().getDef()));
				row.setFont(MONO.deriveFont(11f));
				row.setForeground(e.getKey().getColor());
				row.setOpaque(true);
				row.setBackground(i++ % 2 == 0 ? BG : PANEL);
				row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 4));
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
				bankPanel.add(row);
			}
		}
		bankPanel.revalidate();
		bankPanel.repaint();
	}

	// ------------------------------------------------------------------
	// resource helpers (missing-safe, cached)
	// ------------------------------------------------------------------

	private BufferedImage img(String resource)
	{
		if (imgCache.containsKey(resource))
		{
			return imgCache.get(resource);
		}
		BufferedImage loaded = null;
		try
		{
			loaded = ImageUtil.loadImageResource(RuneHunterPlugin.class, resource);
		}
		catch (Exception ignored)
		{
			// art slot missing — fallback drawing takes over
		}
		imgCache.put(resource, loaded);
		return loaded;
	}

	/** Tint a white-alpha silhouette by replacing RGB, keeping alpha. Cached. */
	private BufferedImage tinted(String resource, Color tint)
	{
		String key = resource + '|' + (tint.getRGB() & 0xFFFFFF);
		if (tintCache.containsKey(key))
		{
			return tintCache.get(key);
		}
		BufferedImage src = img(resource);
		BufferedImage out = null;
		if (src != null)
		{
			out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
			int rgb = tint.getRGB() & 0x00FFFFFF;
			for (int y = 0; y < src.getHeight(); y++)
			{
				for (int x = 0; x < src.getWidth(); x++)
				{
					out.setRGB(x, y, (src.getRGB(x, y) & 0xFF000000) | rgb);
				}
			}
		}
		tintCache.put(key, out);
		return out;
	}

	/** 24px tier-tinted archetype icon for the caught list. Cached. */
	private ImageIcon listIcon(CreatureDef d)
	{
		Color tint = d.getTier().getColor();
		String key = d.key() + '|' + (tint.getRGB() & 0xFFFFFF);
		ImageIcon icon = iconCache.get(key);
		if (icon != null)
		{
			return icon;
		}
		BufferedImage scaled = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		BufferedImage sil = tinted(Archetype.of(d).resource(), tint);
		if (sil != null)
		{
			g.drawImage(sil, 0, 0, 24, 24, null);
		}
		else
		{
			g.setColor(tint);
			g.fillOval(4, 4, 16, 16);
		}
		g.dispose();
		icon = new ImageIcon(scaled);
		iconCache.put(key, icon);
		return icon;
	}

	// ------------------------------------------------------------------
	// the podium — reusable showcase component (store-agnostic)
	// ------------------------------------------------------------------

	/**
	 * Paints one creature on a stone podium from plain display data. Never
	 * touches the CollectionStore — feed it any hunter's creature (future
	 * trade/showcase screens included) via show(...).
	 */
	private final class PodiumPanel extends JPanel
	{
		private static final int FPS_MS = 33; // ~30fps bob, only while showing

		private CreatureDef def;
		private int level = 1;
		private int xp;
		private int wins;
		private int losses;
		private GearItem[] gear = new GearItem[3];
		private boolean shiny;

		private int tick;
		private final Timer bobTimer = new Timer(FPS_MS, e ->
		{
			tick++;
			repaint();
		});

		PodiumPanel()
		{
			setBackground(BG);
			setBorder(BorderFactory.createLineBorder(PANEL));
			// run the bob only while actually on screen
			addHierarchyListener(e ->
			{
				if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0)
				{
					if (isShowing())
					{
						bobTimer.start();
					}
					else
					{
						bobTimer.stop();
					}
				}
			});
		}

		/** Feed the showcase; any hunter's creature works — no store reads. */
		void show(CreatureDef def, int level, int xp, int wins, int losses,
			GearItem[] gear, boolean shiny)
		{
			this.def = def;
			this.level = level;
			this.xp = xp;
			this.wins = wins;
			this.losses = losses;
			this.gear = gear == null ? new GearItem[3] : gear;
			this.shiny = shiny;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics;
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

			int w = getWidth();
			int h = getHeight();

			// dark vignette backdrop — stepped rect frames, darkest at the edge
			g.setColor(new Color(0x241F18));
			g.fillRect(0, 0, w, h);
			int[] steps = {40, 70, 100};
			for (int i = 0; i < steps.length; i++)
			{
				int inset = (steps.length - i) * Math.max(6, Math.min(w, h) / 22);
				g.setColor(new Color(0, 0, 0, steps[i]));
				g.fillRect(0, 0, w, inset);
				g.fillRect(0, h - inset, w, inset);
				g.fillRect(0, inset, inset, h - 2 * inset);
				g.fillRect(w - inset, inset, inset, h - 2 * inset);
			}

			if (def == null)
			{
				g.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
				FontMetrics fm = g.getFontMetrics();
				String msg = "Catch a creature to fill the Trophy Room.";
				g.setColor(TEXT);
				g.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
				return;
			}

			// banded stone pedestal, bottom center
			int podW = (int) (w * 0.52);
			int podH = Math.max(26, (int) (h * 0.11));
			int podX = (w - podW) / 2;
			int podY = h - podH - (int) (h * 0.13);
			drawPedestal(g, podX, podY, podW, podH);

			// creature — large tinted silhouette floating on a slow sine bob
			Color tint = shiny ? GOLD : def.getTier().getColor();
			int size = Math.min(160, (int) (h * 0.42));
			int bob = (int) (Math.sin(tick * 0.05) * size * 0.04);
			int cx = w / 2;
			int cy = podY - size / 2 - (int) (h * 0.03) + bob;

			// hover shadow on the pedestal top
			g.setColor(new Color(0, 0, 0, 100));
			g.fillOval(cx - size / 3, podY - Math.max(3, podH / 6),
				(size * 2) / 3, Math.max(6, podH / 3));

			// Generative-AI float animation, if the art slot is filled: a
			// horizontal strip of square frames (pod_<archetype>.png) cycled
			// at ~6fps on top of the sine bob. Falls back to the tinted
			// archetype silhouette until art is supplied.
			String podResource = Archetype.of(def).resource().replace("sil_", "pod_");
			BufferedImage strip = img(podResource);
			if (strip != null && strip.getHeight() > 0)
			{
				int frames = Math.max(1, strip.getWidth() / strip.getHeight());
				int fw = strip.getWidth() / frames;
				int frame = (tick / 5) % frames;
				g.drawImage(strip,
					cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2,
					frame * fw, 0, frame * fw + fw, strip.getHeight(), null);
			}
			else
			{
				BufferedImage sil = tinted(Archetype.of(def).resource(), tint);
				if (sil != null)
				{
					g.drawImage(sil, cx - size / 2, cy - size / 2, size, size, null);
				}
				else
				{
					g.setColor(tint);
					g.fillOval(cx - size / 3, cy - size / 3, (size * 2) / 3, (size * 2) / 3);
				}
			}
			if (shiny)
			{
				drawShinySparkles(g, cx, cy, size);
			}

			// name — big RuneScape bold gold, near the top
			g.setFont(FontManager.getRunescapeBoldFont().deriveFont(22f));
			FontMetrics fm = g.getFontMetrics();
			String name = (shiny ? "✨ " : "") + def.getNpcName();
			int nameY = (int) (h * 0.10) + fm.getAscent();
			g.setColor(Color.BLACK);
			g.drawString(name, cx - fm.stringWidth(name) / 2 + 2, nameY + 2);
			g.setColor(GOLD);
			g.drawString(name, cx - fm.stringWidth(name) / 2, nameY);

			// tier label in tier color
			g.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
			FontMetrics tf = g.getFontMetrics();
			String tierName = def.getTier().getDisplayName();
			int tierY = nameY + tf.getHeight() + 2;
			g.setColor(Color.BLACK);
			g.drawString(tierName, cx - tf.stringWidth(tierName) / 2 + 1, tierY + 1);
			g.setColor(def.getTier().getColor());
			g.drawString(tierName, cx - tf.stringWidth(tierName) / 2, tierY);

			// level + xp progress to the next level
			int infoY = podY + podH + Math.max(10, h / 40);
			g.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
			FontMetrics lf = g.getFontMetrics();
			String levelText = "Level " + level;
			g.setColor(TEXT);
			g.drawString(levelText, cx - podW / 2, infoY + lf.getAscent());

			String record = "W: " + wins + "   L: " + losses;
			g.setColor(TEXT);
			g.drawString(record, cx + podW / 2 - lf.stringWidth(record), infoY + lf.getAscent());

			int barY = infoY + lf.getHeight() + 4;
			drawXpBar(g, cx - podW / 2, barY, podW, 9);

			// equipped gear names, in their metal colors
			g.setFont(MONO.deriveFont(11f));
			FontMetrics gf = g.getFontMetrics();
			int gearY = barY + 16 + gf.getAscent();
			int gx = cx - podW / 2;
			for (GearItem item : gear)
			{
				if (item == null)
				{
					continue;
				}
				g.setColor(item.getColor());
				g.drawString(item.getDisplayName(), gx, gearY);
				gx += gf.stringWidth(item.getDisplayName()) + 14;
			}
		}

		private void drawPedestal(Graphics2D g, int x, int y, int w, int h)
		{
			// three stacked slabs, banded greys, light top edges — no gradients
			int slabH = h / 3;
			int[] widths = {(int) (w * 0.70), (int) (w * 0.85), w};
			Color[] faces = {new Color(0x55524B), new Color(0x44423C), new Color(0x33312C)};
			Color[] tops = {new Color(0x6B685F), new Color(0x585650), new Color(0x454440)};
			for (int i = 0; i < 3; i++)
			{
				int sw = widths[i];
				int sx = x + (w - sw) / 2;
				int sy = y + i * slabH;
				g.setColor(faces[i]);
				g.fillRect(sx, sy, sw, slabH);
				g.setColor(tops[i]);
				g.fillRect(sx, sy, sw, Math.max(2, slabH / 4));
				g.setColor(new Color(0x14100B));
				g.drawRect(sx, sy, sw - 1, slabH - 1);
			}
			// carved gold trim line on the middle slab
			g.setColor(new Color(0x7A5C1E));
			g.drawLine(x + (w - widths[1]) / 2 + 3, y + slabH + slabH / 2,
				x + (w + widths[1]) / 2 - 4, y + slabH + slabH / 2);
		}

		private void drawXpBar(Graphics2D g, int x, int y, int w, int h)
		{
			int floor = CollectionStore.xpForLevel(level);
			int ceil = CollectionStore.xpForLevel(level + 1);
			double frac = level >= 99 ? 1.0
				: Math.max(0, Math.min(1.0, (double) (xp - floor) / Math.max(1, ceil - floor)));

			g.setColor(new Color(0x201B14));
			g.fillRect(x, y, w, h);
			int fillW = (int) (w * frac);
			if (fillW > 0)
			{
				g.setColor(GOLD);
				g.fillRect(x, y, fillW, h);
				g.setColor(new Color(0x7A5C1E));
				g.fillRect(x, y + h / 2, fillW, h - h / 2);
			}
			g.setColor(new Color(0x14100B));
			g.drawRect(x, y, w - 1, h - 1);

			g.setFont(MONO.deriveFont(10f));
			FontMetrics fm = g.getFontMetrics();
			String label = level >= 99 ? "Max level" : xp + " / " + ceil + " xp";
			g.setColor(TEXT);
			g.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + h + fm.getAscent() + 1);
		}

		private void drawShinySparkles(Graphics2D g, int cx, int cy, int size)
		{
			BufferedImage spark = img("sparkle.png");
			int rise = size;
			for (int i = 0; i < 3; i++)
			{
				int cycle = (tick * 2 + i * (rise / 3)) % rise;
				int x = cx + (i - 1) * size / 3
					+ (int) (Math.sin((tick + i * 40) * 0.1) * size * 0.08);
				int y = cy + size / 3 - cycle;
				double fade = 1.0 - (double) cycle / rise;
				int s = Math.max(6, (int) (size * 0.14 * (0.6 + 0.4 * fade)));
				if (spark != null)
				{
					g.drawImage(spark, x - s / 2, y - s / 2, s, s, null);
				}
				else
				{
					g.setColor(GOLD);
					g.fillRect(x - s / 6, y - s / 2, s / 3, s);
					g.fillRect(x - s / 2, y - s / 6, s, s / 3);
				}
			}
		}

		private static final long serialVersionUID = 1L;
	}

	private static final long serialVersionUID = 1L;
}
