/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 * (Download Jama package from http://math.nist.gov/javanumerics/jama/)
 */
package com.drawmetry.curvefitting;

import Jama.Matrix;
import java.awt.geom.Point2D;

/**
 * The Bezier class instance is used to fit an array of sample points with one
 * or more Bezier curves of degree &le; 3. An instance changes state as methods
 * are called to progress through the different steps of curve fitting. The
 * steps are: <ol> <li> Create an instance of Bezier by calling the constructor
 * with the sample point array as argument. <li> Call the
 * <code>parameterize()</code> method to perform initial parameterization. The
 * arguments of this method specify the interval of sample points to
 * parameterize. <li> Call the
 * <code>fitBezier()</code> method to find the best fit under the current
 * parameterization. <li> Call the
 * <code>reparameterize()</code> method to improve the parameterization. <li>
 * Repeat the two steps above until no significant improvement is realized.
 * </ol>
 *
 * If the resulting Bezier curve is not satisfactory, try partitioning the
 * sample points into contiguous subsets and repeat steps 2 through 5 on each
 * subset.
 *
 * @author Erik
 */
public class Bezier
{

    private Point2D.Double[] samplePoints;
    private static double[][] bezier3Coeff = new double[][]{
        {-1.0, 3.0, -3.0, 1.0},
        {3.0, -6.0, 3.0, 0.0},
        {-3.0, 3.0, 0.0, 0.0},
        {1.0, 0.0, 0.0, 0.0}
    };
    private static double[][] bezier2Coeff = new double[][]{
        {1.0, -2.0, 1.0},
        {-2.0, 2.0, 0.0},
        {1.0, 0.0, 0.0}
    };
    private static double[][] bezier1Coeff = new double[][]{
        {-1.0, 1.0},
        {1.0, 0.0}
    };
    private double[] parameters;
    private int degree;
    private double[][] controlPoints;
    private double[][] position = new double[4][2]; // Bezier times control points (BxP)
    private double[][] velocity = new double[3][2]; // Bezier derivative times control points (B'xP)
    private Matrix matrixWxD; //Weight times Sample points
    private Matrix matrixWxTxB; // Weight times Time times Bezier
    private Matrix matrixP; // control points, closest to a solution of equation: matrixWxTxB x matrixP = matrixWxD

    /**
     * Constructor.
     *
     * @param samplePoints a Point2D.Double array with the sample points to fit.
     */
    public Bezier(Point2D.Double[] samplePoints) {
        this.samplePoints = samplePoints;
        this.parameters = new double[samplePoints.length];
    }

    /**
     * Assigns a parameter <i>t</i> in the interval [0,1] to each sample point
     * in the interval [start, end]. The first sample point in this interval is
     * assigned the value 0.0, and the last sample point is assigned the value
     * 1.0. The increase in the parameter value from one sample point to the
     * next is proportional to the distance between the two sample points.
     *
     * @param start the index of the first point in the interval that is
     * parameterized.
     * @param end the index of the last point in the interval that is
     * parameterized.
     * @return an array with the assigned parameters in the interval [start,
     * end]
     * @throws IllegalArgumentException if not
     * <code>0 &le; start &lt; end &lt; samplePoints.length</code>, where
     * <code>samplePoints</code> is the value passed in the constructor of this
     * instance of Bezier.
     */
    public double[] parameterize(int start, int end) throws IllegalArgumentException {
        if (start < 0 || end <= start || samplePoints.length <= end) {
            throw new IllegalArgumentException("The arguments must be such that"
                    + " 0 <= start < end < samplePoints.length");
        }
        parameters[start] = 0.0;
        for (int i = start; i < end; i++) {
            parameters[i + 1] = parameters[i] + samplePoints[i].distance(samplePoints[i + 1]);
        }
        for (int i = start; i < end; i++) {
            parameters[i] /= parameters[end];
        }
        parameters[end] = 1.0;
        return parameters.clone();
    }

    /**
     * Assigns a parameter <i>t</i> in the interval [0,1] to each sample point
     * in the interval last parameterized such that new parameterization results
     * in a better fit with the Bezier curve last generated compared to the
     * parameterization used to generate that Bezier curve. This method may only
     * be called after a call to
     * <code>fitBezier()</code> or an
     * <code>IllegalStateException</code> will be thrown.
     *
     * @return an array with the assigned parameters in the interval
     * [start,end].
     * @throws IllegalStateException if a Bezier curve has not yet been
     * generated.
     */
    public double[] reparameterize(int start, int end) throws IllegalStateException {

        if (controlPoints == null) {
            throw new IllegalStateException("Call fitBezier() prior to calling this method.");
        } else {
            for (int i = 0; i < controlPoints.length; i++) {
                if (controlPoints[i] == null) {
                    throw new IllegalStateException("Call fitBezier() prior to calling this method.");
                }
            }
        }
        double tHat;
        double t_i;
        Point2D.Double p;
        Point2D.Double v;
        Point2D.Double d_i;
        for (int i = start + 1; i < end; i++) {
            t_i = parameters[i];
            p = getPosition(t_i);
            v = getVelocity(t_i);
            d_i = samplePoints[i];
            tHat = t_i + ((d_i.x - p.x) * v.x + (d_i.y - p.y) * v.y)
                    / v.distanceSq(0.0, 0.0);
            if (d_i.distanceSq(p) > d_i.distanceSq(getPosition(tHat))) {
                parameters[i] = tHat;
            }
        }
        return parameters.clone();
    }

    /**
     * Generates a Bezier curve that minimizes the sum of weighed squares of the
     * distances between each sample point and its corresponding point on the
     * generated curve under the current parameterization. Higher weights are
     * assigned to the points near the start and end of the interval, and lesser
     * weights to the points in the middle of the interval, to get a better fit
     * near the start and end of the interval.
     *
     * @return the control points of the generated Bezier curve.
     * @throws IllegalStateException if no interval has been parameterized yet.
     */
    public Point2D.Double[] fitBezier(int start, int end) throws IllegalStateException {
        if (start == end) { //initial value of start and end is 0.
            throw new IllegalStateException("Need to parameterize before calling this method.");
        }
        degree = Math.min(end - start, 3);
        switch (degree) {
            case 1:
                fitBezier1(start, end);
                break;
            case 2:
                fitBezier2(start, end);
                break;
            default:
                fitBezier3(start, end);
        }
        Point2D.Double[] ctrlPoints = new Point2D.Double[controlPoints.length];
        for (int i = 0; i < ctrlPoints.length; i++) {
            ctrlPoints[i] = new Point2D.Double(controlPoints[i][0], controlPoints[i][1]);
        }
        return ctrlPoints;
    }

    private void fitBezier3(int start, int end) {
        assert start >= 0;
        assert end < samplePoints.length;
        int numPoints = end - start + 1;
        assert numPoints >= 4;
        // Compute matrixWxTxB
        double[][] a = new double[numPoints][4];
        for (int i = 0; i < numPoints; i++) {
            double t_i = parameters[start + i];
            double w_i = weight(numPoints, i);
            for (int j = 0; j < 4; j++) {
                a[i][j] = 0.0;
                for (int k = 0; k < 4; k++) {
                    a[i][j] *= t_i;
                    a[i][j] += bezier3Coeff[k][j];
                }
                a[i][j] *= w_i;
            }
        }
        
        matrixWxTxB = new Matrix(a);
        if (matrixWxTxB.rank() < 4) {
        	degree = 2;
        	fitBezier2(start, end);
        	return;
        }

        double[][] a12 = new double[a.length][2];
        double[][] a03 = new double[a.length][2];
        for (int i = 0; i < a.length; i++) {
            a12[i][0] = a[i][1];
            a12[i][1] = a[i][2];
            a03[i][0] = a[i][0];
            a03[i][1] = a[i][3];
        }

        Matrix matrixWxTxB12 = new Matrix(a12);

        a = new double[numPoints][2];
        for (int i = 0; i < numPoints; i++) {
            double w_i = weight(numPoints, i);
            Point2D.Double d_i = samplePoints[start + i];
            a[i][0] = w_i * d_i.x - a03[i][0] * samplePoints[start].x - a03[i][1] * samplePoints[end].x;
            a[i][1] = w_i * d_i.y - a03[i][0] * samplePoints[start].y - a03[i][1] * samplePoints[end].y;
        }
        matrixWxD = new Matrix(a);
        matrixP = matrixWxTxB12.solve(matrixWxD); //Finds matrixP that yields minimal residual
        controlPoints = new double[4][2];
        controlPoints[0][0] = samplePoints[start].x;
        controlPoints[0][1] = samplePoints[start].y;
        controlPoints[1][0] = matrixP.get(0, 0);
        controlPoints[1][1] = matrixP.get(0, 1);
        controlPoints[2][0] = matrixP.get(1, 0);
        controlPoints[2][1] = matrixP.get(1, 1);
        controlPoints[3][0] = samplePoints[end].x;
        controlPoints[3][1] = samplePoints[end].y;

//        controlPoints = matrixP.getArray();

        // recompute the getPosition array
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 4; j++) { //row
                position[j][i] = 0.0;
                for (int k = 0; k < 4; k++) { //column
                    position[j][i] += bezier3Coeff[j][k] * controlPoints[k][i];
                }
            }
        }
        //recompute the velocity array
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) { //row
                velocity[j][i] = 0.0;
                for (int k = 0; k < 4; k++) { //column
                    velocity[j][i] += (3 - j) * bezier3Coeff[j][k] * controlPoints[k][i];
                }
            }
        }
    }

    private void fitBezier2(int start, int end) {
        assert start >= 0;
        assert end < samplePoints.length;
        int numPoints = end - start + 1;
        assert numPoints >= 3;
        // Compute matrixWxT*B
        double[][] a = new double[numPoints][3];
        for (int i = 0; i < numPoints; i++) {
            double w_i = weight(numPoints, i);
            double t_i = parameters[start + i];
            for (int j = 0; j < 3; j++) {
                a[i][j] = 0.0;
                for (int k = 0; k < 3; k++) {
                    a[i][j] *= t_i;
                    a[i][j] += bezier2Coeff[k][j];
                }
                a[i][j] *= w_i;
            }
        }
        matrixWxTxB = new Matrix(a);
        if (!matrixWxTxB.lu().isNonsingular()) {
            degree = 1;
            fitBezier1(start, end);
            return;
        }
        matrixWxD = new Matrix(numPoints, 2);
        for (int i = 0; i < numPoints; i++) {
            double w_i = weight(numPoints, i);
            Point2D.Double d_i = samplePoints[start + i];
            matrixWxD.set(i, 0, w_i * d_i.x);
            matrixWxD.set(i, 1, w_i * d_i.y);
        }
        matrixP = matrixWxTxB.solve(matrixWxD);
        controlPoints = matrixP.getArray();
        //recompute the getPosition array
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) { //row
                position[j][i] = 0.0;
                for (int k = 0; k < 3; k++) { //column
                    position[j][i] += bezier2Coeff[j][k] * controlPoints[k][i];
                }
            }
        }

        //recompute the velocity array
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) { //row
                velocity[j][i] = 0.0;
                for (int k = 0; k < 3; k++) { //column
                    velocity[j][i] += (2 - j) * bezier2Coeff[j][k] * controlPoints[k][i];
                }
            }
        }
    }

    private void fitBezier1(int start, int end) {
        assert start >= 0;
        assert end <= samplePoints.length;
        int numPoints = end - start + 1;
        assert numPoints >= 2;
        // Compute matrixWxT*B
        double[][] a = new double[numPoints][2];
        for (int i = 0; i < numPoints; i++) {
            double wi = weight(numPoints, i);
            for (int j = 0; j < 2; j++) {
                a[i][j] = 0.0;
                for (int k = 0; k < 2; k++) {
                    a[i][j] *= parameters[start + i];
                    a[i][j] += bezier1Coeff[k][j];
                }
                a[i][j] *= wi;
            }
        }
        matrixWxTxB = new Matrix(a);
        matrixWxD = new Matrix(numPoints, 2);
        for (int i = 0; i < numPoints; i++) {
            double wi = weight(numPoints, i);
            matrixWxD.set(i, 0, wi * samplePoints[start + i].x);
            matrixWxD.set(i, 1, wi * samplePoints[start + i].y);
        }
        matrixP = matrixWxTxB.solve(matrixWxD);
        controlPoints = matrixP.getArray();

        //recompute the getPosition array
        for (int i = 0; i
                < 2; i++) {
            for (int j = 0; j < 2; j++) { //row
                position[j][i] = 0.0;
                for (int k = 0; k < 2; k++) { //column
                    position[j][i] +=
                            bezier1Coeff[j][k] * controlPoints[k][i];
                }
            }
        }

        //recompute the velocity array
        for (int i = 0; i
                < 2; i++) {
            for (int j = 0; j < 1; j++) { //row
                velocity[j][i] = 0.0;
                for (int k = 0; k < 2; k++) { //column
                    velocity[j][i] +=
                            (1 - j) * bezier1Coeff[j][k] * controlPoints[k][i];
                }
            }
        }
    }

    /**
     * This method returns <i>B</i> (<i>t</i> ), where <i>B</i> :[0,1] &rarr;
     * <b>R</b><sup>2</sup> is the Bezier curve generated when
     * <code>fitBezier()</code> was last called. If
     * <code>fitBezier()</code> is not called before calling this method, an
     * <code>IllegalStateException</code> is thrown.
     *
     * @param t a double value between 0 and 1.
     * @return the position at
     * <code>t</code> of the last generated Bezier curve.
     * @throws IllegalStateException if a Bezier curve has not yet been
     * generated.
     */
    public Point2D.Double getPosition(double t) throws IllegalStateException {
        if (controlPoints == null) {
            throw new IllegalStateException("Call fitBezier() prior to calling this method.");
        } else {
            for (int i = 0; i < controlPoints.length; i++) {
                if (controlPoints[i] == null) {
                    throw new IllegalStateException("Call fitBezier() prior to calling this method.");
                }
            }
        }
        assert position != null;
        double x = 0.0;
        double y = 0.0;
        for (int k = 0; k <= degree; k++) {
            x *= t;
            x += position[k][0];
            y *= t;
            y += position[k][1];
        }
        return new Point2D.Double(x, y);
    }

    private Point2D.Double getVelocity(double t) {
        assert velocity != null;
        double x = 0.0;
        double y = 0.0;
        for (int k = 0; k < degree; k++) {
            x *= t;
            x += velocity[k][0];
            y *= t;
            y += velocity[k][1];
        }
        return new Point2D.Double(x, y);
    }

    private double weight(int n, final int i) {
        n--;
        if (i == 0 || i == n) {
            return 1E10;
        } else {
            return (Math.abs(n - (2.0 * i)) + 1.0) / n;
        }
//        return 1.0;
    }
}
