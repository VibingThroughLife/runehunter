package com.runehunter.ui;

import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * RuneHunter dev console ("PokeDev"): a translucent, draggable, always-on-top
 * terminal window. Runs the same commands as ::rh with shell-style history
 * (up/down arrows), plus a Dex tab listing the full roster with per-creature
 * debug buttons: Spawn, Step (force one wander move), Play <animId>, Kill.
 *
 * Dev tooling only — never ships in the Hub build.
 */
public class DevConsole extends JFrame
{
	private static final Color BG = new Color(18, 18, 18);
	private static final Color PANEL = new Color(28, 28, 28);
	private static final Color GOLD = new Color(0xE8B84A);
	private static final Color TEXT = new Color(0xD8CCB4);
	private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

	/** (commandLine, outputSink) -> executes asynchronously. */
	private final BiConsumer<String, Consumer<String>> executor;

	private final JTextArea output = new JTextArea();
	private final JTextField input = new JTextField();
	private final List<String> history = new ArrayList<>();
	private int historyIndex;

	public DevConsole(BiConsumer<String, Consumer<String>> executor)
	{
		super("RuneHunter PokeDev");
		this.executor = executor;

		setUndecorated(true);
		setAlwaysOnTop(true);
		setSize(600, 440);
		try
		{
			setOpacity(0.94f);
		}
		catch (Exception ignored)
		{
			// translucency unsupported on this platform — stay opaque
		}

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(BG);
		root.setBorder(BorderFactory.createLineBorder(new Color(120, 96, 50)));

		root.add(buildHeader(), BorderLayout.NORTH);

		JTabbedPane tabs = new JTabbedPane();
		tabs.setBackground(PANEL);
		tabs.setForeground(TEXT);
		tabs.addTab("Console", buildConsoleTab());
		tabs.addTab("Toolkit", buildToolkitTab());
		tabs.addTab("Dex", buildDexTab());
		root.add(tabs, BorderLayout.CENTER);

		setContentPane(root);

		println("RuneHunter PokeDev — same commands as ::rh, no prefix needed.");
		println("Try: status | orbs | swarm 10 | lineup 2 | here dharok shiny | anim goblin 6181 | step goblin | despawn all");
		println("Up/Down = command history.");
	}

	// ---- window chrome ----

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(PANEL);
		header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));

		JLabel title = new JLabel("RuneHunter — PokeDev console");
		title.setForeground(GOLD);
		title.setFont(MONO.deriveFont(Font.BOLD, 13f));
		header.add(title, BorderLayout.WEST);

		header.add(new WindowCloseButton(() -> setVisible(false)), BorderLayout.EAST);

		// drag anywhere on the header
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

	// ---- console tab ----

	private JPanel buildConsoleTab()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setBackground(BG);
		panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		output.setEditable(false);
		output.setFont(MONO);
		output.setBackground(BG);
		output.setForeground(TEXT);
		output.setCaretColor(TEXT);
		output.setLineWrap(true);
		output.setWrapStyleWord(true);
		JScrollPane scroll = new JScrollPane(output);
		scroll.setBorder(BorderFactory.createLineBorder(PANEL));
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		panel.add(scroll, BorderLayout.CENTER);

		input.setFont(MONO);
		input.setBackground(PANEL);
		input.setForeground(Color.WHITE);
		input.setCaretColor(GOLD);
		input.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(120, 96, 50)),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		input.addActionListener(e -> submit());
		input.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_UP)
				{
					recall(-1);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_DOWN)
				{
					recall(1);
					e.consume();
				}
			}
		});
		panel.add(input, BorderLayout.SOUTH);
		return panel;
	}

	private void submit()
	{
		String line = input.getText().trim();
		input.setText("");
		if (line.isEmpty())
		{
			return;
		}
		history.add(line);
		historyIndex = history.size();
		println("> " + line);
		executor.accept(line, this::println);
	}

	private void recall(int direction)
	{
		if (history.isEmpty())
		{
			return;
		}
		historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
		input.setText(historyIndex < history.size() ? history.get(historyIndex) : "");
	}

	/** Thread-safe output append. */
	public void println(String line)
	{
		SwingUtilities.invokeLater(() ->
		{
			output.append(line + "\n");
			output.setCaretPosition(output.getDocument().getLength());
		});
	}

	// ---- toolkit tab: every common command as a clickable button ----

	private JPanel buildToolkitTab()
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(BG);
		list.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

		list.add(toolSection("Orbs & gear",
			tool("Load orb pack", "orbs"),
			tool("Big pack (100)", "orbs 100"),
			tool("Gear x5", "gear 5")));

		list.add(toolSection("Spawning",
			tool("Status", "status"),
			tool("Reseed area", "reseed"),
			tool("Swarm 10", "swarm 10"),
			tool("Despawn all", "despawn all")));

		JPanel lineupRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		lineupRow.setOpaque(false);
		for (int p = 1; p <= com.runehunter.spawn.SpawnManager.lineupPages(); p++)
		{
			lineupRow.add(miniButton("Lineup " + p, runCmd("lineup " + p)));
		}
		list.add(toolSection("Animation QA (batched pages)"));
		list.add(leftAlign(lineupRow));

		list.add(toolSection("Battles",
			tool("Start battle", "battle"),
			tool("Practice duel", "duel practice"),
			tool("Party status", "party")));

		JPanel styleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		styleRow.setOpaque(false);
		for (String s : new String[]{"melee", "ranged", "magic", "disable", "skull"})
		{
			styleRow.add(miniButton("Next: " + s, runCmd("style " + s)));
		}
		list.add(toolSection("Force next wild attack"));
		list.add(leftAlign(styleRow));

		JPanel secretRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		secretRow.setOpaque(false);
		for (String s : com.runehunter.spawn.SpawnManager.secretNames())
		{
			secretRow.add(miniButton(s, runCmd("secret " + s)));
		}
		secretRow.add(miniButton("Status", runCmd("secret status")));
		secretRow.add(miniButton("Reload models", runCmd("secret reload")));
		list.add(toolSection("Secret Dex (shh)"));
		list.add(leftAlign(secretRow));

		list.add(toolSection("Windows & reports",
			tool("Trophy Room", "trophy"),
			tool("Bug reports", "report"),
			tool("Clear reports", "report clear")));

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(BG);
		wrap.add(list, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(wrap,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		JPanel tab = new JPanel(new BorderLayout());
		tab.setBackground(BG);
		tab.add(scroll, BorderLayout.CENTER);
		return tab;
	}

	/** Section header + optional row of command buttons. Output lands in the Console tab. */
	private JPanel toolSection(String title, JButton... buttons)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setOpaque(false);
		section.setAlignmentX(0f);

		JLabel head = new JLabel(title);
		head.setFont(MONO.deriveFont(Font.BOLD, 12f));
		head.setForeground(GOLD);
		head.setAlignmentX(0f);
		head.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
		section.add(head);

		if (buttons.length > 0)
		{
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			row.setOpaque(false);
			for (JButton b : buttons)
			{
				row.add(b);
			}
			section.add(leftAlign(row));
		}
		return section;
	}

	private JPanel leftAlign(JPanel row)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setAlignmentX(0f);
		holder.add(row, BorderLayout.WEST);
		return holder;
	}

	private JButton tool(String label, String command)
	{
		return miniButton(label, runCmd(command));
	}

	private Runnable runCmd(String command)
	{
		return () -> run(command);
	}

	// ---- dex tab ----

	private JPanel buildDexTab()
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(BG);

		int i = 1;
		for (CreatureDef d : CreatureRoster.ALL)
		{
			list.add(dexRow(i++, d));
		}

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(BG);
		wrap.add(list, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(wrap,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		JPanel tab = new JPanel(new BorderLayout());
		tab.setBackground(BG);
		tab.add(scroll, BorderLayout.CENTER);
		return tab;
	}

	private JPanel dexRow(int index, CreatureDef d)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(index % 2 == 0 ? BG : PANEL);
		row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 4));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		JLabel name = new JLabel("#" + index + "  " + d.getNpcName());
		name.setFont(MONO);
		name.setForeground(d.getTier().getColor());
		row.add(name, BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		actions.setOpaque(false);

		JTextField animId = new JTextField(5);
		animId.setFont(MONO.deriveFont(11f));
		animId.setBackground(BG);
		animId.setForeground(Color.WHITE);
		animId.setCaretColor(GOLD);
		animId.setToolTipText("Animation id to test");

		actions.add(miniButton("Spawn", () -> run("here " + d.getNpcName())));
		actions.add(miniButton("Step", () -> run("step " + d.getNpcName())));
		actions.add(animId);
		actions.add(miniButton("Play", () ->
		{
			String id = animId.getText().trim();
			if (!id.isEmpty())
			{
				run("anim " + d.getNpcName() + " " + id);
			}
		}));
		actions.add(miniButton("Kill", () -> run("despawn " + d.getNpcName())));

		row.add(actions, BorderLayout.EAST);
		return row;
	}

	private JButton miniButton(String label, Runnable action)
	{
		JButton b = new JButton(label);
		b.setFont(MONO.deriveFont(11f));
		b.setBackground(PANEL);
		b.setForeground(TEXT);
		b.setFocusPainted(false);
		b.setMargin(new java.awt.Insets(0, 5, 0, 5));
		b.addActionListener(e -> action.run());
		return b;
	}

	private void run(String command)
	{
		println("> " + command);
		executor.accept(command, this::println);
	}

	private static final long serialVersionUID = 1L;
}
