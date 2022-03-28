/*
	TrackReducer, a program reducing the number of points on a GPS-Track
    Copyright (C) 2010  Felix Schweighofer <felix.s1000@googlemail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package ch.bubendorf.gpx2fit;

import java.util.ArrayList;
import java.util.List;

import static ch.bubendorf.gpx2fit.GeoCalculator.dist;

/**
 * Reduces Tracks
 * @author Felix Schweighofer (felix.s1000@googlemail.com)
 */
public abstract class Reducer {

	public static final double EPSILON = 1E-8;

	/**
	 * Finds the TrackPoint with the biggest distance to a given line.
	 * @param line Array of type WayPoint[] where the first element is the start and the last element is the end of the line
	 * @return Index of point with the biggest distance to line
	 */
	private static int getPointIndexWithBiggestDistanceToLine(final List<WayPoint> line){
		double maxDistance = 0;
		int maxDistancePoint = 0;
		final WayPoint startPoint = line.get(0);
		final WayPoint endPoint = line.get(line.size() -1);

		for(int i=1; i<line.size() -1; i++){
			final WayPoint p = line.get(i);
			final double dist = getDistancePointLine(p, startPoint, endPoint);
			if (dist > maxDistance) {
				maxDistance = dist;
				maxDistancePoint = i;
			}
		}
		if(line.size() <= 2){
			maxDistancePoint=0;
		}
		if (maxDistance < EPSILON) {
			// Eine gerade Linie oder sonst irgend ein komischer Fall! ==> Einfach die Mitte nehmen
			maxDistancePoint = line.size() / 2;
		}
		return maxDistancePoint;
	}

	/**
	 * This deletes TrackPoints (by the rules of the Douglas-Peucker-Algorithm).
	 * It is called recursively, so be careful with big Arrays.
	 * Currently, works only with Arrays that do not contain any null-references.
	 * @param t Array of type TrackPoint[] that will be reduced;
	 *             actually, no TrackPoints will be deleted from the Array,
	 *             but TrackPoints within the tolerance will be deleted from their TrackSegments
	 * @param tolerance The tolerance in meters. No point on the resulting track will have a
	 *                     distance greater than this value to the original track.
	 */
	public static List<WayPoint> reduce(final List<WayPoint> t, final double tolerance){
		if(tolerance <= 0 || t.size() <= 2){
			return t;
		}
		final int p = getPointIndexWithBiggestDistanceToLine(t);
		final double distanceStartToEnd = dist(t.get(0), t.get(t.size() -1));
		final double distPointToLine = getDistancePointLine(t.get(p), t.get(0), t.get(t.size() - 1));
		final boolean isTooFar = distPointToLine > tolerance;

		if (isTooFar){
			// Point is too far away from line  ==> Keep it
			final List<WayPoint> t1Red = reduce(t.subList(0, p + 1), tolerance);

			final List<WayPoint> t2Red = reduce(t.subList(p, t.size()), tolerance);

			final List<WayPoint> result = new ArrayList<>(t1Red.size() + t2Red.size());
			result.addAll(t1Red);
			result.addAll(t2Red.subList(1, t2Red.size()));
			return result;
		}

		// All points are within the tolerance ==> Just return the first and the last
		return List.of(t.get(0), t.get(t.size() -1));
	}

	/**
	 * Calculates the distance from a given point to a line defined by two given points
	 *
	 * @param startPoint The start point of the line
	 * @param endPoint   The end point of the line
	 * @param point     The point whose distance to the line should be calculated
	 * @return The distance from the point to the line in m
	 */
	public static double getDistancePointLine(final WayPoint point, final WayPoint startPoint, final WayPoint endPoint) {
		/*
		 * Write coordinates into variables
		 * Coordinate system:
		 * Y (lat)
		 * ↑
		 * |
		 * |
		 * |–––––––→X (lon)
		 */
		final double startLon = startPoint.getLon();
		final double startLat = startPoint.getLat();
		final double endLon = endPoint.getLon();
		final double endLat = endPoint.getLat();
		final double pointLon = point.getLon();
		final double pointLat = point.getLat();

		// Use the distance between Start/End to the point if the start and the end are the same
		if (Math.abs(startLon - endLon) < 1e-6 && Math.abs(startLat - endLat) < 1e6) {
			return dist(startLat, startLon, pointLat, pointLon);
		}

		// Calculate the equation of an imagined line between startPoint and endPoint
		// y = ( m * x ) + t	→ m and t are unknown, the coordinates of startPoint and endPoint can be used as x and y
		final double m = (endLat - startLat) / (endLon - startLon);    // m = Δy / Δx
		final double t = startLat - m * startLon;    // t = y - m*x → Use coordinates of startPoint or endPoint as x and y

		// Calculate the equation of an imagined line cutting the line mentioned above orthogonally
		// y = ( m2 * x ) + t2	→ m2 and t2 are unknown, point's coordinates can be used as x and y
		final double m2 = -1 / m;
		final double t2 = pointLat - m2 * pointLon;

		// Calculate the coordinates of the intersection point X of the lines mentioned above
		// xLon = (m * xLat ) + t = (m2 * x) + t2
		/* X is on y=m*x+t and y=m2*x+t2
		 * m*xLat +t = m2*xLat +t2	// subtract t and m2*xLat from both sides of the equation to have all xLat on the left
		 * m*xLat - m2*xLat = t2 -t	// Factorize
		 * xLat*(m-m2) = t2 -t	// divide by (m-m2)
		 * xLat = (t2-t) / (m-m2)
		 */
		final double xLon = (t2 - t) / (m - m2);
		final double xLat = m2 * xLon + t2;    // Insert xLat into y=m*x+t

		// Calculate the distance from point to X
		return dist(point, new WayPoint(xLat, xLon));
	}
}
