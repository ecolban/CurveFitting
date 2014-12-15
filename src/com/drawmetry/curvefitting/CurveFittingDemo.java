package com.drawmetry.curvefitting;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.util.ArrayList;
import java.util.Stack;

import javax.swing.*;

/**
 *
 * @author Erik
 */
@SuppressWarnings("serial")
public class CurveFittingDemo extends JPanel implements Runnable, ActionListener {

    private JLabel label = new JLabel();
    private Point2D.Double[] samplePoints;
    private Bezier bezier;
    private double[] parameters;
    private int start = 0, mid, end;
    private int degree;
    private Point2D.Double[] controlPoints;
    private double residualPrevious;
    private double residualCurrent;
    private ArrayList<Segment> segments = new ArrayList<Segment>();
    private Stack<Integer> splitPoints = new Stack<Integer>();

    private static enum State {

        INITIAL,
        AFTER_PARAMETERIZATION,
        AFTER_BEZIER_FIT,
        FINAL
    }
    private State state = State.INITIAL;

    private interface Renderer {

        void render(Graphics2D g2);
    }
    private Renderer showSamplePoints = new Renderer() {

        public void render(Graphics2D g) {
            g.clearRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.BLACK);
            if (start < end) {
                for (int i = 0; i < samplePoints.length; i++) {
                    if (samplePoints[i] == null) {
                        System.out.println("i = " + i);
                    }
                    int x = (int) samplePoints[i].x;
                    int y = (int) samplePoints[i].y;
                    g.fillRect(x - 2, y - 2, 4, 4);
                }
            }
            g.setColor(Color.BLUE);
            for (Segment s : segments) {
                s.paintSelf(g);
            }
        }
    };
    private Renderer showInitialParam = new Renderer() {

        public void render(Graphics2D g) {
            showSamplePoints.render(g);
            g.setColor(Color.GRAY);
            for (int i = start; i <= end; i++) {
                int x = (int) samplePoints[i].x;
                int y = (int) samplePoints[i].y;
                g.drawString(String.format("%.2f", parameters[i]), x + 4, y - 4);
            }
        }
    };
    private Renderer showBezier = new Renderer() {

        public void render(Graphics2D g) {
            showSamplePoints.render(g);
            if (g.getColor() == Color.RED) {
                g.setColor(Color.BLUE);
            } else {
                g.setColor(Color.RED);
            }
            switch (degree) {
                case 1:
                    g.draw(new Line2D.Double(
                            controlPoints[0].x, controlPoints[0].y,
                            controlPoints[1].x, controlPoints[1].y));
                    g.setColor(Color.GRAY);
                    break;
                case 2:
                    g.draw(new QuadCurve2D.Double(
                            controlPoints[0].x, controlPoints[0].y,
                            controlPoints[1].x, controlPoints[1].y,
                            controlPoints[2].x, controlPoints[2].y));
                    g.setColor(Color.GRAY);
                    break;
                default:
                    g.draw(new CubicCurve2D.Double(
                            controlPoints[0].x, controlPoints[0].y,
                            controlPoints[1].x, controlPoints[1].y,
                            controlPoints[2].x, controlPoints[2].y,
                            controlPoints[3].x, controlPoints[3].y));
                    g.setColor(Color.GRAY);
            }
            for (int i = start; i < end; i++) {
                g.draw(new Line2D.Double(samplePoints[i], bezier.getPosition(parameters[i])));
            }
        }
    };
    private Renderer showSplitCurve = new Renderer() {

        public void render(Graphics2D g) {
            showBezier.render(g);
            g.setColor(Color.RED);
            g.setStroke(new BasicStroke(2.0F));
            g.draw(new Line2D.Double(samplePoints[mid], bezier.getPosition(parameters[mid])));
        }
    };
    private Renderer currentPainter = showSamplePoints;
    private Timer ticker;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new CurveFittingDemo());
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
                generateCircleSamplePoints();
                start = 0;
                end = samplePoints.length - 1;
                segments.clear();
                splitPoints.clear();
                currentPainter = showSamplePoints;
                state = State.INITIAL;
                CurveFittingDemo.this.repaint();
            }
        });
        button = new JButton("Spiral");
        buttonPanel.add(button);
        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                generateSpiralSamplePoints();
                start = 0;
                end = samplePoints.length - 1;
                segments.clear();
                splitPoints.clear();
                currentPainter = showSamplePoints;
                state = State.INITIAL;
                CurveFittingDemo.this.repaint();
            }
        });
        button = new JButton("Parabola");
        buttonPanel.add(button);
        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                generateParabolaSamplePoints();
                start = 0;
                end = samplePoints.length - 1;
                segments.clear();
                splitPoints.clear();
                currentPainter = showSamplePoints;
                state = State.INITIAL;
                CurveFittingDemo.this.repaint();
            }
        });
        button = new JButton("Corner");
        buttonPanel.add(button);
        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                generateCornerSamplePoints();
                start = 0;
                end = samplePoints.length - 1;
                segments.clear();
                splitPoints.clear();
                currentPainter = showSamplePoints;
                state = State.INITIAL;
                CurveFittingDemo.this.repaint();
            }
        });
        button = new JButton("Animation");
        buttonPanel.add(button);
        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                if (ticker.isRunning()) {
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
                if (ticker.isRunning()) {
                    ticker.stop();
                } else {
                    nextState();
                }
            }
        });

        return buttonPanel;
    }

    private void generateSpiralSamplePoints() {
        int num = 100;
        samplePoints = new Point2D.Double[num];
        parameters = new double[num];
        for (int j = 0, i = 0; j < num; j++, i++) {
            double alpha = -5 * Math.PI * j / (num - 1);
            samplePoints[i] = new Point2D.Double(
                    3 * j * Math.cos(alpha) + getWidth() / 2,
                    3 * j * Math.sin(alpha) + getHeight() / 2);
        }
        bezier = new Bezier(samplePoints);
    }

    private void generateCircleSamplePoints() {
        int num = 97;
        samplePoints = new Point2D.Double[num];
        parameters = new double[num];
        for (int i = 0; i < num; i++) {
            double alpha = 2 * Math.PI * i / (num - 1);
            samplePoints[i] = new Point2D.Double(
                    300 * Math.cos(alpha) + 400,
                    300 * Math.sin(alpha) + 330);
        }
        bezier = new Bezier(samplePoints);
    }

    private void generateParabolaSamplePoints() {
        int num = 81;
        samplePoints = new Point2D.Double[num];
        parameters = new double[num];
        for (int i = 0; i < num; i++) {
            double delta = 600.0 / (num - 1);
            samplePoints[i] = new Point2D.Double(
                    100 + i * delta, 200 + (i * delta - 200) * (i * delta - 400) / 200.0);


        }
        bezier = new Bezier(samplePoints);
    }

    private void generateCornerSamplePoints() {
        int num = 151;
        samplePoints = new Point2D.Double[num];
        parameters = new double[num];
        for (int i = 0; i < num; i++) {
            double delta = 600.0 / (num - 1);
            samplePoints[i] = new Point2D.Double(
                    100 + i * delta, 10 + Math.abs(200 - i * delta));

        }
        bezier = new Bezier(samplePoints);
    }

    @Override
    public void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        currentPainter.render(g2);
    }

    /**
     * State transitions:
     * [INITIAL+ --> [AFTER_PARAMETERIZATION --> AFTER_BEZIER_FIT]+ --> FINAL]+
     */
    private void nextState() {

        switch (state) {
            case INITIAL:
                if (start < end) {
                    residualCurrent = Double.MAX_VALUE;
                    parameters = bezier.parameterize(start, end);
                    currentPainter = showInitialParam;
                    label.setText("Initial Parametrization");
                    repaint();
                    state = State.AFTER_PARAMETERIZATION;
                }
                break;
            case AFTER_PARAMETERIZATION:
                residualPrevious = residualCurrent;
                controlPoints = bezier.fitBezier(start, end);
                residualCurrent = computeResidual();
                degree = controlPoints.length - 1;
                currentPainter = showBezier;
                label.setText(String.format("Least Squares / Residual = %.2f", residualCurrent));
                repaint();
                state = State.AFTER_BEZIER_FIT;
                break;
            case AFTER_BEZIER_FIT: // state after Bezier fit
                if (residualCurrent < 0.5 || 0.9 < residualCurrent / residualPrevious) {
                    mid = end - start == 1 ? end : splitPoints(start, end);
                    currentPainter = showSplitCurve;
                    label.setText(String.format("Finding the most distant point / Distance = %.2f",
                            samplePoints[mid].distance(bezier.getPosition(parameters[mid]))));
                    state = State.FINAL;
                } else {
                    residualPrevious = residualCurrent;
                    parameters = bezier.reparameterize(start, end);
                    residualCurrent = computeResidual();
                    currentPainter = showBezier;
                    label.setText(String.format("Reparametrization / Residual = %.2f", residualCurrent));
                    state = State.AFTER_PARAMETERIZATION;
                }
                repaint();
                break;
            case FINAL: // end of (re-parametrization, Bezier fit)-iterations
                if (samplePoints[mid].distanceSq(bezier.getPosition(parameters[mid])) < 0.25) {
                    addSegment();
                    start = end;
                    label.setText("Good enough!");
                    end = splitPoints.isEmpty() ? start : splitPoints.pop().intValue();
                } else {
                    splitPoints.push(Integer.valueOf(end));
                    end = mid;
                    label.setText("Not good enough");
                }
                state = State.INITIAL;
                currentPainter = showSamplePoints;
                repaint();
                break;
        }
    }

    public void actionPerformed(ActionEvent e) {
        nextState();
    }

    private void addSegment() {
        double[] d = new double[8];
        for (int i = 0; i < controlPoints.length; i++) {
            d[2 * i] = controlPoints[i].x;
            d[2 * i + 1] = controlPoints[i].y;
        }
        segments.add(new Segment(degree, d));
    }

    private double computeResidual() {
        double residual = 0.0;
        for (int i = start + 1; i < end - 1; i++) {
            residual += samplePoints[i].distanceSq(bezier.getPosition(parameters[i]));
        }
        return residual;
    }

    private int splitPoints(int start, int end) {
        assert start >= 0;
        assert end < samplePoints.length;
        assert end - start > 1;
        int split = start + 1;
        double maxDstSq = 0.0;
        for (int i = start + 1; i < end; i++) {
            double dstSq = samplePoints[i].distanceSq(bezier.getPosition(parameters[i]));
            if (dstSq > maxDstSq) {
                split = i;
                maxDstSq = dstSq;
            }
        }
        return split;
    }
    
   
}
