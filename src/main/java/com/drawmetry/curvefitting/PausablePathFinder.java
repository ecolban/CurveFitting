/*
 * PathGenerator.java
 *
 * Created on November 22, 2007, 6:29 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package com.drawmetry.curvefitting;

import Jama.Matrix;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;

/**
 * This class is a modification of PathFinder so that the computation can be
 * paused and the current state of the computation can be viewed. For this
 * reason:
 * <ul>
 * <li> <code>PathFinder</code> extends <code>Coroutine</code>, which allows the
 * execution to be paused.
 * <li>an <code>enum State</code> and associated state variables have been added
 * <li>some methods have been added for the sole purpose of accessing the state:
 * <ul>
 * <li> <code>pause</code>
 * <li> <code>getMessage</code>
 * <li> <code>calculateResidual</code>
 * <li> <code>paintSelf</code>
 * </ul>
 * </ul>
 * 
 * @author Erik
 *
 * @see #getPath
 */
public class PausablePathFinder extends Coroutine {

	private static final double FIT_TOLERANCE = 0.5;
	private static final double[][] BEZIER3COEFF = new double[][] { { -1.0, 3.0, -3.0, 1.0 },
			{ 3.0, -6.0, 3.0, 0.0 },
			{ -3.0, 3.0, 0.0, 0.0 },
			{ 1.0, 0.0, 0.0, 0.0 }
	};

	/*
	 * The following variables are only needed for viewing state
	 */
	private enum State {
		AFTER_INITIAL_PARAM, AFTER_LSF, AFTER_REPARAM, SPLIT_DETERMINATION, END
	}

	private State currentState;

	/*
	 * Other variables
	 */
	private Point2D[] samplePoints;
	private double[] initialT;
	private int start, end;
	private double[] t;
	private int farthestPointIdx;
	private double[][] controlPoints = new double[4][2];
	private double[][] position = new double[4][2];
	private double[][] velocity = new double[3][2];
	private double fitToleranceSq = FIT_TOLERANCE * FIT_TOLERANCE;
	private Path2D path;

	@SuppressWarnings("serial")
	private class CancelledException extends Exception {

	}

	public PausablePathFinder(Point2D[] samplePoints) {
		this.samplePoints = samplePoints;
	}

	/**
	 * Generates a Path2D from an array of sample points
	 *
	 * @throws InterruptedException
	 * @throws CancelledException
	 * 
	 */
	public void getPath() throws InterruptedException, CancelledException {
		start = 0;
		end = samplePoints.length - 1;
		int n = samplePoints.length;
		if (n < 2) {
			throw new IllegalArgumentException(
					"Argument samplePoints must contain at least 2 points");
		}
		path = new Path2D.Double();
		path.moveTo(samplePoints[0].getX(), samplePoints[0].getY());
		initialParametrization();
		fitBezierSeq(0, n - 1);
	}

	private void initialParametrization() {
		int n = end - start + 1;
		t = new double[n];
		t[0] = 0.0;
		if (n == samplePoints.length) {
			for (int i = 0; i + start < end; i++) {
				t[i + 1] = t[i] + samplePoints[i].distance(samplePoints[i + 1]);
			}
			for (int i = 0; i < n; i++) {
				t[i] /= t[n - 1];
			}
			t[n - 1] = 1.0;
			initialT = t.clone();
		} else {
			double offset = initialT[start];
			double totalTime = initialT[end] - offset;
			for (int i = start; i < end; i++) {
				t[i - start] = (initialT[i] - offset) / totalTime;
			}
			t[n - 1] = 1.0;
		}
	}

	private void fitBezierSeq(final int start, final int end) throws InterruptedException,
			CancelledException {
		assert end > start;
		this.start = start;
		this.end = end;
		initialParametrization();
		pause(State.AFTER_INITIAL_PARAM);
		int n = end - start + 1;
		if (n == 2) {
			// number of points == 2.
			// Fit 2 points with a line.
			path.lineTo(samplePoints[end].getX(), samplePoints[end].getY());
			return;
		}
		// Adjust the initial parametrization to the current segment
		if (n == 3) {
			// number of points == 3.
			// Fit 3 points with a quad curve.
			fitQuad(start, end);
			path.quadTo(
					controlPoints[1][0],
					controlPoints[1][1],
					controlPoints[2][0],
					controlPoints[2][1]);
			return;
		}
		// number of points >= 4
		// Fit 4 or more points with a cubic curve.
		fitCubic();
		// Find the point farthest from the curve.
		farthestPointIdx = start;
		double maxDistSq = 0.0;
		for (int i = start + 1; i < end; i++) {
			double distSq = samplePoints[i].distanceSq(position(t[i - start]));
			if (distSq > maxDistSq) {
				maxDistSq = distSq;
				farthestPointIdx = i;
			}
		}
		pause(State.SPLIT_DETERMINATION);

		if (maxDistSq > fitToleranceSq) {
			int splitIdx = farthestPointIdx;
			fitBezierSeq(start, splitIdx);
			fitBezierSeq(splitIdx, end);
		} else {
			path.curveTo(
					controlPoints[1][0],
					controlPoints[1][1],
					controlPoints[2][0],
					controlPoints[2][1],
					controlPoints[3][0],
					controlPoints[3][1]);
		}
	}

	/**
	 * Pre-cond: end = start + 2, i.e., there are only three points to fit.
	 * 
	 * @param start
	 * @param end
	 */
	private void fitQuad(final int start, final int end) {
		controlPoints[0][0] = samplePoints[start].getX();
		controlPoints[0][1] = samplePoints[start].getY();
		controlPoints[2][0] = samplePoints[end].getX();
		controlPoints[2][1] = samplePoints[end].getY();
		controlPoints[1][0] = (
				samplePoints[start + 1].getX() / (1 - t[1]) / t[1]
						- (1 - t[1]) / t[1] * controlPoints[0][0]
						- t[1] / (1 - t[1]) * controlPoints[2][0]) / 2.0;
		controlPoints[1][1] = (
				samplePoints[start + 1].getY() / (1 - t[1]) / t[1]
						- (1 - t[1]) / t[1] * controlPoints[0][1]
						- t[1] / (1 - t[1]) * controlPoints[2][1]) / 2.0;
	}

	private void fitCubic() throws InterruptedException, CancelledException {
		boolean doneFitting = false;
		double previousResidual; // = Double.MAX_VALUE;
		double residual = Double.MAX_VALUE;
		while (!doneFitting) {
			leastSquareFit();
			pause(State.AFTER_LSF);
			previousResidual = residual;
			residual = reparametrize();
			if (residual < 0.95 * previousResidual && residual > 0.25) {
				pause(State.AFTER_REPARAM);
			} else {
				doneFitting = true;
			}
		}
	}

	private void leastSquareFit() {
		Matrix matrixA = getWxTxB12();
		Matrix matrixB = getMatrixWxDminusWxTxB03xP03t();
		Matrix matrixX = matrixA.solve(matrixB);
		double[][] controlPoints12 = matrixX.getArray();
		controlPoints[0][0] = samplePoints[start].getX();
		controlPoints[0][1] = samplePoints[start].getY();
		controlPoints[1][0] = controlPoints12[0][0];
		controlPoints[1][1] = controlPoints12[0][1];
		controlPoints[2][0] = controlPoints12[1][0];
		controlPoints[2][1] = controlPoints12[1][1];
		controlPoints[3][0] = samplePoints[end].getX();
		controlPoints[3][1] = samplePoints[end].getY();

		// recompute the position array
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 4; j++) { // row
				position[j][i] = 0.0;
				for (int k = 0; k < 4; k++) { // column
					position[j][i] +=
							BEZIER3COEFF[j][k] * controlPoints[k][i];
				}
			}
		}
		// recompute the velocity array
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 3; j++) { // row
				velocity[j][i] = 0.0;
				for (int k = 0; k < 4; k++) { // column
					velocity[j][i] +=
							(3 - j) * BEZIER3COEFF[j][k] * controlPoints[k][i];
				}
			}
		}
	}

	private double reparametrize() {
		assert t[0] == 0.0;
		assert t[end - start] == 1.0;
		double res = 0.0;
		double tHat;
		Point2D p;
		Point2D v;
		Point2D pHat;
		for (int i = 1; i + start < end; i++) {
			p = position(t[i]);
			v = velocity(t[i]);
			tHat = t[i] + ((samplePoints[i + start].getX() - p.getX()) * v.getX() +
					(samplePoints[i + start].getY() - p.getY()) * v.getY())
					/ v.distanceSq(0.0, 0.0);
			pHat = position(tHat);
			double distBefore = samplePoints[i + start].distanceSq(p);
			double distAfter = samplePoints[i + start].distanceSq(pHat);
			if (distAfter < distBefore) {
				t[i] = tHat;
				res += distAfter;
			} else {
				res += distBefore;
			}
		}
		return res;
	}

	private Point2D position(double t) {
		double x = 0.0;
		double y = 0.0;
		for (int k = 0; k < 4; k++) {
			x *= t;
			x += position[k][0];
		}
		for (int k = 0; k < 4; k++) {
			y *= t;
			y += position[k][1];
		}
		return new Point2D.Double(x, y);
	}

	private Point2D velocity(double t) {
		double x = 0.0;
		double y = 0.0;
		for (int k = 0; k < 3; k++) {
			x *= t;
			x += velocity[k][0];
		}
		for (int k = 0; k < 3; k++) {
			y *= t;
			y += velocity[k][1];
		}
		return new Point2D.Double(x, y);
	}

	private Matrix getWxTxB12() {
		double[][] a = new double[t.length][2];
		int n = t.length - 1;
		for (int i = 0; i <= n; i++) {
			double wi = weight(n, i);
			for (int j = 0; j < 2; j++) {
				a[i][j] = 0.0;
				for (int k = 0; k < 4; k++) {
					a[i][j] *= t[i];
					a[i][j] += BEZIER3COEFF[k][j + 1];
				}
				a[i][j] *= wi;
			}
		}
		return new Matrix(a);
	}

	private Matrix getMatrixWxDminusWxTxB03xP03t() {
		int n = end - start;
		double[][] b03xP03 = new double[4][2];
		double a[][] = new double[n + 1][2];
		for (int k = 0; k < 4; k++) {
			b03xP03[k][0] = BEZIER3COEFF[k][0] * samplePoints[start].getX() +
					BEZIER3COEFF[k][3] * samplePoints[end].getX();
			b03xP03[k][1] = BEZIER3COEFF[k][0] * samplePoints[start].getY() +
					BEZIER3COEFF[k][3] * samplePoints[end].getY();
		}
		for (int i = 0; i <= n; i++) {
			double wi = weight(n, i);
			double txb03P03X = 0;
			double txb03P03Y = 0;
			for (int j = 0; j < 4; j++) {
				txb03P03X *= t[i];
				txb03P03Y *= t[i];
				txb03P03X += b03xP03[j][0];
				txb03P03Y += b03xP03[j][1];
			}
			a[i][0] = wi * (samplePoints[start + i].getX() - txb03P03X);
			a[i][1] = wi * (samplePoints[start + i].getY() - txb03P03Y);
		}
		return new Matrix(a);
	}

	private double weight(int n, final int i) {
		return Math.abs(n - 2.0 * i);
		// return 1.0;
	}

	/*
	 * 
	 * The remaining methods are added to allow pausing and viewing intermediate
	 * results.
	 */

	@Override
	protected void execute() throws InterruptedException {
		try {
			getPath();
			currentState = State.END;
		} catch (CancelledException e) {
		}
	}

	/**
	 * Blocks until de-blocked by an EDT event
	 * 
	 * @param state
	 *            the state of the execution when this method is called
	 * @throws InterruptedException
	 * @throws CancelledException
	 *             if the EDT cancels the execution
	 */
	private void pause(State state) throws InterruptedException, CancelledException {
		currentState = state;
		detach();
		if (isCancelled()) {
			throw new CancelledException();
		}
	}

	public String getMessage() {
		switch (currentState) {
		case AFTER_INITIAL_PARAM:
			return "Initial";
		case SPLIT_DETERMINATION:
			Point2D pos = position(t[farthestPointIdx - start]);
			return String.format("Greatest distance = %.2f",
					samplePoints[farthestPointIdx].distance(pos));
		default:
			return String.format("Residual = %.2f", calculateResidual());
		}
	}

	private double calculateResidual() {
		double result = 0.0;
		for (int i = 0; i < t.length; i++) {
			Point2D p = position(t[i]);
			result += p.distanceSq(samplePoints[start + i]);
		}
		return result;
	}

	public void paintSelf(Graphics2D g2) {
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (currentState != State.END) {
			g2.setColor(Color.BLACK);
			for (int i = start; i <= end; i++) {
				int x = (int) samplePoints[i].getX();
				int y = (int) samplePoints[i].getY();
				g2.fillRect(x - 2, y - 2, 4, 4);
				if (currentState == State.AFTER_INITIAL_PARAM) {
					g2.drawString(String.format("%.2f", t[i - start]), x, y);
				}
			}
		}
		if (currentState == State.AFTER_LSF ||
				currentState == State.AFTER_REPARAM ||
				currentState == State.SPLIT_DETERMINATION) {
			g2.setColor(Color.GRAY);
			for (int i = start; i <= end; i++) {
				Point2D p = position(t[i - start]);
				g2.draw(new Line2D.Double(p, samplePoints[i]));
			}
			g2.setColor(Color.RED);
			g2.draw(new CubicCurve2D.Double(
					controlPoints[0][0], controlPoints[0][1],
					controlPoints[1][0], controlPoints[1][1],
					controlPoints[2][0], controlPoints[2][1],
					controlPoints[3][0], controlPoints[3][1]));
		}
		if (currentState == State.SPLIT_DETERMINATION) {
			g2.setColor(Color.RED);
			Point2D pos = position(t[farthestPointIdx - start]);
			g2.drawLine((int) samplePoints[farthestPointIdx].getX(),
					(int) samplePoints[farthestPointIdx].getY(),
					(int) pos.getX(), (int) pos.getY());
		}
		if (path != null) {
			g2.setColor(Color.BLUE);
			PathIterator pi = path.getPathIterator(null);
			double[] coords = new double[6];
			while (!pi.isDone()) {
				int segmentType = pi.currentSegment(coords);
				switch (segmentType) {
				case PathIterator.SEG_CLOSE:
					break;
				case PathIterator.SEG_MOVETO:
				case PathIterator.SEG_LINETO:
					g2.fillOval((int) coords[0] - 2, (int) coords[1] - 2, 4, 4);
					break;
				case PathIterator.SEG_QUADTO:
					g2.drawOval((int) coords[0] - 2, (int) coords[1] - 2, 4, 4);
					g2.fillOval((int) coords[2] - 2, (int) coords[3] - 2, 4, 4);
					break;

				case PathIterator.SEG_CUBICTO:
					g2.drawOval((int) coords[0] - 2, (int) coords[1] - 2, 4, 4);
					g2.drawOval((int) coords[2] - 2, (int) coords[3] - 2, 4, 4);
					g2.fillOval((int) coords[4] - 2, (int) coords[5] - 2, 4, 4);
					break;
				}
				pi.next();
			}
			g2.draw(path);
		}
	}
}
