package com.runehunter.party;

import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import com.runehunter.data.GearItem;
import com.runehunter.ui.WindowCloseButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The OSRS-style trade window: two columns (you / them), a banner, an offer
 * picker, and Accept → Confirm → done staging. Same dark undecorated chrome
 * as the Trophy Room, WindowCloseButton for the ✕ (closing cancels the
 * trade). All state lives in TradeManager — this window only displays its
 * volatile getters and forwards button presses; TradeManager.Ui calls
 * self-marshal onto the EDT here.
 */
public class TradeWindow extends JFrame implements TradeManager.Ui
{
	private static final Color BG = new Color(18, 18, 18);
	private static final Color PANEL = new Color(28, 28, 28);
	private static final Color GOLD = new Color(0xE8B84A);
	private static final Color TEXT = new Color(0xD8CCB4);
	private static final Color GREEN = new Color(0x4caf50);
	private static final Color BORDER = new Color(120, 96, 50);
	private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);
	private static final String NO_OFFER = "(nothing)";

	private final TradeManager manager;

	private final JLabel banner = new JLabel(" ", JLabel.CENTER);
	private final JLabel titleLabel = new JLabel("RuneHunter — Trade");
	private final SidePanel mySide = new SidePanel("You");
	private final SidePanel theirSide = new SidePanel("Them");
	private final JComboBox<Object> offerPicker = new JComboBox<>();
	private final JButton actionButton = new JButton("Accept");
	private final JButton cancelButton = new JButton("Cancel");
	private boolean updating;

	public TradeWindow(TradeManager manager)
	{
		super("RuneHunter — Trade");
		this.manager = manager;

		setUndecorated(true);
		setAlwaysOnTop(true);
		setSize(560, 400);
		try
		{
			setOpacity(0.97f);
		}
		catch (Exception ignored)
		{
			// translucency unsupported — stay opaque
		}

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(BG);
		root.setBorder(BorderFactory.createLineBorder(BORDER));
		root.add(buildHeader(), BorderLayout.NORTH);
		root.add(buildBody(), BorderLayout.CENTER);
		root.add(buildActions(), BorderLayout.SOUTH);
		setContentPane(root);
		setLocationRelativeTo(null);
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(PANEL);
		header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));

		titleLabel.setForeground(GOLD);
		titleLabel.setFont(MONO.deriveFont(Font.BOLD, 13f));
		header.add(titleLabel, BorderLayout.WEST);
		header.add(new WindowCloseButton(() ->
		{
			manager.cancel();
			setVisible(false);
		}), BorderLayout.EAST);

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

	private JPanel buildBody()
	{
		JPanel body = new JPanel(new BorderLayout(0, 6));
		body.setBackground(BG);
		body.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

		banner.setFont(MONO.deriveFont(Font.BOLD, 12f));
		banner.setForeground(GOLD);
		body.add(banner, BorderLayout.NORTH);

		JPanel sides = new JPanel(new GridLayout(1, 2, 8, 0));
		sides.setBackground(BG);
		sides.add(mySide);
		sides.add(theirSide);
		body.add(sides, BorderLayout.CENTER);
		return body;
	}

	private JPanel buildActions()
	{
		JPanel bar = new JPanel();
		bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
		bar.setBackground(PANEL);
		bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

		JLabel offerLabel = new JLabel("Offer: ");
		offerLabel.setFont(MONO);
		offerLabel.setForeground(TEXT);
		bar.add(offerLabel);

		offerPicker.setFont(MONO.deriveFont(11f));
		offerPicker.setBackground(BG);
		offerPicker.setForeground(TEXT);
		offerPicker.setMaximumSize(new Dimension(220, 26));
		offerPicker.setRenderer(new PickerRenderer());
		offerPicker.addActionListener(e ->
		{
			if (updating)
			{
				return;
			}
			Object sel = offerPicker.getSelectedItem();
			manager.setMyOffer(sel instanceof CreatureDef ? (CreatureDef) sel : null);
		});
		bar.add(offerPicker);
		bar.add(javax.swing.Box.createHorizontalGlue());

		styleButton(actionButton);
		actionButton.addActionListener(e ->
		{
			if (manager.getStage() == TradeManager.Stage.CONFIRM)
			{
				manager.confirmTrade();
			}
			else
			{
				manager.acceptOffer();
			}
		});
		bar.add(actionButton);
		bar.add(javax.swing.Box.createRigidArea(new Dimension(6, 1)));

		styleButton(cancelButton);
		cancelButton.addActionListener(e -> manager.cancel());
		bar.add(cancelButton);
		return bar;
	}

	private void styleButton(JButton b)
	{
		b.setFont(MONO.deriveFont(Font.BOLD, 12f));
		b.setBackground(PANEL);
		b.setForeground(TEXT);
		b.setFocusPainted(false);
	}

	private static final class PickerRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value,
			int index, boolean isSelected, boolean cellHasFocus)
		{
			JLabel label = (JLabel) super.getListCellRendererComponent(
				list, value, index, isSelected, cellHasFocus);
			label.setFont(MONO.deriveFont(11f));
			if (value instanceof CreatureDef)
			{
				CreatureDef d = (CreatureDef) value;
				label.setText(d.getNpcName());
				label.setForeground(d.getTier().getColor());
			}
			else
			{
				label.setForeground(TEXT);
			}
			label.setBackground(isSelected ? new Color(0x5D4E37) : PANEL);
			return label;
		}

		private static final long serialVersionUID = 1L;
	}

	/** One trade column: name header, offered creature, status light. */
	private static final class SidePanel extends JPanel
	{
		final JLabel who = new JLabel();
		final JLabel creature = new JLabel(NO_OFFER);
		final JLabel details = new JLabel(" ");
		final JLabel gear = new JLabel(" ");
		final JLabel status = new JLabel(" ");

		SidePanel(String defaultWho)
		{
			setBackground(new Color(0x201C16));
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(0x5D4E37)),
				BorderFactory.createEmptyBorder(8, 10, 8, 10)));
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

			who.setText(defaultWho);
			who.setFont(MONO.deriveFont(Font.BOLD, 13f));
			who.setForeground(GOLD);
			add(who);
			add(javax.swing.Box.createRigidArea(new Dimension(1, 8)));

			creature.setFont(MONO.deriveFont(Font.BOLD, 13f));
			creature.setForeground(TEXT);
			add(creature);

			details.setFont(MONO.deriveFont(11f));
			details.setForeground(TEXT);
			add(details);

			gear.setFont(MONO.deriveFont(11f));
			gear.setForeground(TEXT);
			add(gear);

			add(javax.swing.Box.createVerticalGlue());
			status.setFont(MONO.deriveFont(Font.BOLD, 12f));
			status.setForeground(TEXT);
			add(status);
		}

		void show(CompanionSnapshot offer, boolean accepted, boolean confirmed, TradeManager.Stage stage)
		{
			if (offer == null)
			{
				creature.setText(NO_OFFER);
				creature.setForeground(TEXT);
				details.setText(" ");
				gear.setText(" ");
			}
			else
			{
				creature.setText(offer.getDef().getNpcName());
				creature.setForeground(offer.getDef().getTier().getColor());
				details.setText("Lv " + offer.getLevel() + "  (" + offer.getXp() + " xp)");
				StringBuilder sb = new StringBuilder();
				for (GearItem g : offer.getGear())
				{
					sb.append(sb.length() > 0 ? ", " : "").append(g.getDisplayName());
				}
				gear.setText(sb.length() == 0 ? "no gear" : sb.toString());
			}

			if (stage == TradeManager.Stage.CONFIRM ? confirmed : accepted)
			{
				status.setText(stage == TradeManager.Stage.CONFIRM ? "CONFIRMED" : "ACCEPTED");
				status.setForeground(GREEN);
			}
			else if (stage == TradeManager.Stage.DONE)
			{
				status.setText("DONE");
				status.setForeground(GREEN);
			}
			else
			{
				status.setText("waiting...");
				status.setForeground(TEXT);
			}
		}

		private static final long serialVersionUID = 1L;
	}

	// ---- TradeManager.Ui (self-marshalling) ----

	@Override
	public void open()
	{
		SwingUtilities.invokeLater(() ->
		{
			refreshNow();
			setVisible(true);
			toFront();
		});
	}

	@Override
	public void refresh()
	{
		SwingUtilities.invokeLater(this::refreshNow);
	}

	private void refreshNow()
	{
		updating = true;
		try
		{
			TradeManager.Stage stage = manager.getStage();
			titleLabel.setText("RuneHunter — Trade"
				+ (manager.getPeerName().isEmpty() ? "" : " with " + manager.getPeerName()));
			banner.setText(manager.getBanner().isEmpty() ? " " : manager.getBanner());
			theirSide.who.setText(manager.getPeerName().isEmpty() ? "Them" : manager.getPeerName());

			mySide.show(manager.getMyOffer(), manager.isMyAccept(), manager.isMyConfirm(), stage);
			theirSide.show(manager.getTheirOffer(), manager.isTheirAccept(), manager.isTheirConfirm(), stage);

			// offer picker: my tradeable creatures (spare plain copies only)
			DefaultComboBoxModel<Object> model = new DefaultComboBoxModel<>();
			model.addElement(NO_OFFER);
			for (CreatureDef d : CreatureRoster.ALL)
			{
				if (manager.getLedger().isTradeable(d))
				{
					model.addElement(d);
				}
			}
			offerPicker.setModel(model);
			CompanionSnapshot mine = manager.getMyOffer();
			offerPicker.setSelectedItem(mine == null ? NO_OFFER : mine.getDef());
			boolean negotiable = stage == TradeManager.Stage.NEGOTIATE;
			offerPicker.setEnabled(negotiable);

			if (stage == TradeManager.Stage.CONFIRM)
			{
				actionButton.setText(manager.isMyConfirm() ? "Waiting..." : "CONFIRM");
				actionButton.setForeground(manager.isMyConfirm() ? TEXT : GOLD);
				actionButton.setEnabled(!manager.isMyConfirm());
			}
			else if (negotiable)
			{
				actionButton.setText(manager.isMyAccept() ? "Waiting..." : "Accept");
				actionButton.setForeground(manager.isMyAccept() ? TEXT : GREEN);
				actionButton.setEnabled(!manager.isMyAccept());
			}
			else
			{
				actionButton.setText("Accept");
				actionButton.setEnabled(false);
			}
			cancelButton.setEnabled(stage != TradeManager.Stage.IDLE && stage != TradeManager.Stage.DONE);
		}
		finally
		{
			updating = false;
		}
	}

	private static final long serialVersionUID = 1L;
}
