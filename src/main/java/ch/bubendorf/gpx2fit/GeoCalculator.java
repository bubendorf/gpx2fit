package ch.bubendorf.gpx2fit;

import io.jenetics.jpx.WayPoint;
import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GlobalCoordinates;

public abstract class GeoCalculator {
    public static final GeodeticCalculator geoCalc = new GeodeticCalculator();
    public static final Ellipsoid reference = Ellipsoid.WGS84;

    public static double dist(final WayPoint wp1, final WayPoint wp2) {
        return dist(wp1.getLatitude().doubleValue(), wp1.getLongitude().doubleValue(),
                wp2.getLatitude().doubleValue(), wp2.getLongitude().doubleValue());
    }

    public static double dist(final ch.bubendorf.gpx2fit.WayPoint wp1, final ch.bubendorf.gpx2fit.WayPoint wp2) {
        return dist(wp1.getLat(), wp1.getLon(),
                wp2.getLat(), wp2.getLon());
    }

    public static double dist(final double lat1, final double lon1, final double lat2, final double lon2) {
        return GeoCalculator.geoCalc.calculateGeodeticCurve(GeoCalculator.reference,
                new GlobalCoordinates(lat1, lon1),
                new GlobalCoordinates(lat2, lon2)
        ).getEllipsoidalDistance();
    }
}
