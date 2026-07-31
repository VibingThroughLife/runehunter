package com.runehunter.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

/**
 * A window-chrome close button that paints its own X — no font glyphs, so it
 * renders identically on every platform (the old JButton("✕") showed a
 * broken box on some setups). Windows-style hover: red field, white cross.
 */
public class WindowCloseButton extends JComponent
{
	private static final Color IDLE_CROSS = new Color(0xD8CCB4);
	private static final Color HOVER_BG = new Color(0xC03A2B);
	private static final Color HOVER_CROSS = Color.WHITE;

	private final Runnable onClose;
	private boolean hover;
	private boolean pressed;

	public WindowCloseButton(Runnable onClose)
	{
		this.onClose = onClose;
		Dimension size = new Dimension(34, 22);
		setPreferredSize(size);
		setMinimumSize(size);
		setToolTipText("Close");
		setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				hover = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = false;
				pressed = false;
				repaint();
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				pressed = true;
				repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				boolean fire = pressed && hover;
				pressed = false;
				repaint();
				if (fire)
				{
					onClose.run();
				}
			}
		};
		addMouseListener(mouse);
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int w = getWidth();
		int h = getHeight();
		if (hover)
		{
			g.setColor(pressed ? HOVER_BG.darker() : HOVER_BG);
			g.fillRect(0, 0, w, h);
		}

		g.setColor(hover ? HOVER_CROSS : IDLE_CROSS);
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int s = 4; // half-size of the cross
		int cx = w / 2;
		int cy = h / 2;
		g.drawLine(cx - s, cy - s, cx + s, cy + s);
		g.drawLine(cx - s, cy + s, cx + s, cy - s);
		g.dispose();
	}

	private static final long serialVersionUID = 1L;
}
