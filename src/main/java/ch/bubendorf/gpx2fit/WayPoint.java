package ch.bubendorf.gpx2fit;

import com.garmin.fit.DateTime;
import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GlobalCoordinates;

import java.util.Date;


public class WayPoint {
    // instantiate the calculator
    private static final GeodeticCalculator geoCalc = new GeodeticCalculator();

    public static final Date RefDate = new Date(DateTime.OFFSET);

    // select a reference ellipsoid
    private static final Ellipsoid reference = Ellipsoid.WGS84;

    private double lat;
    private double lon;
    private double ele;
    private Date time;
    private double totalDist = Double.NaN;
    private final String name;

    public WayPoint(final double lat, final double lon) {
        this(null, lat, lon, 0, null);
    }

    public WayPoint(final String name, final double lat, final double lon, final double ele, final Date time) {
        this.lat = lat;
        this.lon = lon;
        this.ele = ele;
        this.time = time;
        this.name = name;
        if (this.time == null) {
            this.time = RefDate;
        }
    }

    public WayPoint(final io.jenetics.jpx.WayPoint point) {
        lat = point.getLatitude().doubleValue();
        lon = point.getLongitude().doubleValue();
        ele = point.getElevation().isPresent() ? point.getElevation().get().doubleValue() : 0;
        time = point.getTime().isPresent() ? Date.from(point.getTime().get()) : RefDate;
//        totalDist =
        name = point.getName().orElse(null);
    }

    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public int getLatSemi() {
        return toSemiCircles(lat);
    }

    public int getLonSemi() {
        return toSemiCircles(lon);
    }

    public static int toSemiCircles(final double i) {
        final double d = i * 2147483648.0 / 180.0;
        return (int) d;
    }

    public double getEle() {
        return ele;
    }

    public Date getTime() {
        return time;
    }
    public void setLat(final double lat) {
        this.lat = lat;
    }

    public void setLon(final double lon) {
        this.lon = lon;
    }

    public void setEle(final double ele) {
        this.ele = ele;
    }

    public void setTime(final Date time) {
        this.time = time;
    }

    public double distance(final WayPoint other) {
        return geoCalc.calculateGeodeticCurve(reference,
                new GlobalCoordinates(getLat(), this.getLon()),
                new GlobalCoordinates(other.getLat(), other.getLon())
        ).getEllipsoidalDistance();
    }

    public double distance3D(final WayPoint other) {
        final double d = geoCalc.calculateGeodeticCurve(reference,
                new GlobalCoordinates(getLat(), this.getLon()),
                new GlobalCoordinates(other.getLat(), other.getLon())
        ).getEllipsoidalDistance();

        if (!Double.isNaN(getEle()) && !Double.isNaN(other.getEle())) {
            final double h = (getEle() - other.getEle());
            return Math.sqrt(d * d + h * h);
        } else return d;
    }

    public double getTotalDist() {
        return totalDist;
    }

    public void setTotalDist(final double totalDist) {
        this.totalDist = totalDist;
    }

}
