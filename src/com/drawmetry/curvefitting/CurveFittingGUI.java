package com.drawmetry.curvefitting;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 *
 * @author Erik
 */
@SuppressWarnings("serial")
public class CurveFittingGUI extends JPanel implements Runnable, ActionListener {

	private JLabel label = new JLabel();
	private PathFinder currentPathGenerator;

	private Timer ticker;

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new CurveFittingGUI());
	}

	public void run() {
		generateCircleSamplePoints();
		JFrame frame = new JFrame("Curve Fitting Demo");
		frame.setPreferredSize(new Dimension(800, 750));
		label.setText("Sample points");
		label.setHorizontalAlignment(JLabel.CENTER);
		frame.add(label, BorderLayout.NORTH);
		frame.add(this, BorderLayout.CENTER);
		frame.add(getButtons(), BorderLayout.SOUTH);
		frame.requestFocusInWindow();
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		ticker = new Timer(20, this);
	}

	private JPanel getButtons() {
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout());
		JButton button;
		button = new JButton("Circle");
		buttonPanel.add(button);
		button.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				setPathGenerator(generateCircleSamplePoints());
			}

		});
		button = new JButton("Spiral");
		buttonPanel.add(button);
		button.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				setPathGenerator(generateSpiralSamplePoints());
			}
		});
		button = new JButton("Parabola");
		buttonPanel.add(button);
		button.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				setPathGenerator(generateParabolaSamplePoints());
			}
		});
		button = new JButton("Corner");
		buttonPanel.add(button);
		button.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				setPathGenerator(generateCornerSamplePoints());
			}
		});
		button = new JButton("Animation");
		buttonPanel.add(button);
		button.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (currentPathGenerator == null) {

				} else if (ticker.isRunning()) {
					ticker.stop();
				} else {
					ticker.restart();
				}
			}
		});
		button = new JButton("Step");
		buttonPanel.add(button);
		button.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (currentPathGenerator == null) {

				} else if (ticker.isRunning()) {
					ticker.stop();
				} else {
					CurveFittingGUI.this.actionPerformed(e);
				}
			}
		});

		return buttonPanel;
	}

	private void setPathGenerator(Point2D.Double[] sp) {
		if (currentPathGenerator != null && !currentPathGenerator.isCancelled()) {
			try {
				if (ticker.isRunning()) {
					ticker.stop();
				}
				currentPathGenerator.cancel();
			} catch (InterruptedException e1) {
			}
		}
		currentPathGenerator = new PathFinder(sp);
		currentPathGenerator.attach();
		;
		repaint();
	}

	private Point2D.Double[] generateSpiralSamplePoints() {
		int num = 100;
		Point2D.Double[] sp = new Point2D.Double[num];
		for (int j = 0, i = 0; j < num; j++, i++) {
			double alpha = -5 * Math.PI * j / (num - 1);
			sp[i] = new Point2D.Double(
					3 * j * Math.cos(alpha) + getWidth() / 2,
					3 * j * Math.sin(alpha) + getHeight() / 2);
		}
		return sp;
	}

	private Point2D.Double[] generateCircleSamplePoints() {
		int num = 97;
		Point2D.Double[] sp = new Point2D.Double[num];
		for (int i = 0; i < num; i++) {
			double alpha = 2 * Math.PI * i / (num - 1);
			sp[i] = new Point2D.Double(
					300 * Math.cos(alpha) + 400,
					300 * Math.sin(alpha) + 330);
		}
		return sp;
	}

	private Point2D.Double[] generateParabolaSamplePoints() {
		int num = 81;
		Point2D.Double[] sp = new Point2D.Double[num];
		for (int i = 0; i < num; i++) {
			double delta = 600.0 / (num - 1);
			sp[i] = new Point2D.Double(
					100 + i * delta, 200 + (i * delta - 200) * (i * delta - 400) / 200.0);

		}
		return sp;
	}

	private Point2D.Double[] generateCornerSamplePoints() {
		int num = 176;
		Point2D.Double[] sp = new Point2D.Double[num];
		for (int i = 0; i < num; i++) {
			double delta = 350.0 / (num - 1);
			sp[i] = new Point2D.Double(
					20 + 2 * i * delta, 50 + Math.abs(60 - i * delta));

		}
		return sp;
	}

	@Override
	public void paintComponent(Graphics g) {
		int w = getWidth();
		int h = getHeight();
		Graphics2D g2 = (Graphics2D) g;
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, w, h);
		if (currentPathGenerator != null) {
			currentPathGenerator.paintSelf(g2);
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			currentPathGenerator.reattach();
			label.setText(currentPathGenerator.getMessage());
			repaint();
		} catch (InterruptedException e1) {
		}

	}

}
