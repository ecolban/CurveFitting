package com.drawmetry.curvefitting;

import java.awt.Graphics2D;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.QuadCurve2D;

/**
 *
 * @author Erik
 */
class Segment {
    private int degree;
    private double[] coords = new double[8];

    Segment(int degree, double[] coords) {
        this.degree = degree;
        this.coords = coords.clone();
    }

    void paintSelf(Graphics2D g2) {
        switch(degree) {
            case 1:
                g2.draw(new Line2D.Double(
                        coords[0], coords[1],
                        coords[2], coords[3]));
                g2.fill(new Ellipse2D.Double(coords[0] - 2, coords[1] - 2, 4, 4));
                g2.fill(new Ellipse2D.Double(coords[2] - 2, coords[3] - 2, 4, 4));
                break;
            case 2:
                g2.draw(new QuadCurve2D.Double(
                        coords[0], coords[1],
                        coords[2], coords[3],
                        coords[4], coords[5]));
                g2.fill(new Ellipse2D.Double(coords[0] - 2, coords[1] - 2, 4, 4));
                g2.draw(new Ellipse2D.Double(coords[2] - 2, coords[3] - 2, 4, 4));
                g2.fill(new Ellipse2D.Double(coords[4] - 2, coords[5] - 2, 4, 4));
                break;
            case 3:
                g2.draw(new CubicCurve2D.Double(
                        coords[0], coords[1],
                        coords[2], coords[3],
                        coords[4], coords[5],
                        coords[6], coords[7]));
                g2.fill(new Ellipse2D.Double(coords[0] - 3, coords[1] - 3, 6, 6));
                g2.draw(new Ellipse2D.Double(coords[2] - 3, coords[3] - 3, 6, 6));
                g2.draw(new Ellipse2D.Double(coords[4] - 3, coords[5] - 3, 6, 6));
                g2.fill(new Ellipse2D.Double(coords[6] - 3, coords[7] - 3, 6, 6));
                break;
            default:
        }
    }

}
