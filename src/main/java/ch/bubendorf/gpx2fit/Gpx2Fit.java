package ch.bubendorf.gpx2fit;


import ch.bubendorf.gpx2fit.fit.FitBufferEncoder;
import ch.bubendorf.gpx2fit.fit.FitEncoder;
import ch.bubendorf.gpx2fit.fit.FitFileEncoder;
import com.garmin.fit.*;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Route;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

import static com.garmin.fit.File.COURSE;
import static com.garmin.fit.Manufacturer.GARMIN;
import static java.lang.Double.isNaN;
import static java.lang.Math.max;
import static java.lang.Math.min;

// Based on https://github.com/gimportexportdevs/gexporter/blob/master/app/src/main/java/org/surfsite/gexporter/Gpx2Fit.java
public class Gpx2Fit {

    private List<WayPoint> trkPoints = Collections.emptyList();
    private List<WayPoint> rtePoints = Collections.emptyList();
    private List<WayPoint> wayPoints = Collections.emptyList();

    private final List<WayPoint> pointsToUse = new ArrayList<>();

    private final String courseName;

    Gpx2FitOptions gpx2FitOptions;

    public Gpx2Fit(final String name, final InputStream in, final Gpx2FitOptions options) throws IOException {
        courseName = name;
        gpx2FitOptions = options;

        // Load the GPX
        final GPX gpx = GPX.Reader.of(GPX.Reader.Mode.LENIENT).read(in);

        if (options.isTracks()) {
            trkPoints = gpx.tracks().flatMap(Track::segments)
                    .flatMap(TrackSegment::points)
                    .map(WayPoint::new)
                    .collect(Collectors.toList());

            if (options.getTolerance() > 0) {
                // Reduce track points using the Douglas-Peucker algorithm
                trkPoints = Reducer.reduce(trkPoints, options.getTolerance());
            }
        }

        if (options.isRoutes()) {
            rtePoints = gpx.routes().flatMap(Route::points)
                    .map(WayPoint::new)
                    .collect(Collectors.toList());
        }

        if (options.isWaypoints()) {
            wayPoints = gpx.wayPoints()
                    .map(WayPoint::new)
                    .collect(Collectors.toList());
        }

        // Per default use the TrackPoints for distance, area, etc.
        pointsToUse.addAll(trkPoints);
        if (pointsToUse.isEmpty()) {
            // If there are no TrackPoints then use the RoutePoints
            pointsToUse.addAll(rtePoints);
        }
        if (pointsToUse.isEmpty()) {
            // Else use the Waypoints
            pointsToUse.addAll(wayPoints);
        }
    }

    public String getName() {
        return courseName;
    }

    public List<WayPoint> getTrkPoints() {
        return trkPoints;
    }

    public List<WayPoint> getRtePoints() {
        return rtePoints;
    }

    public List<WayPoint> getWayPoints() {
        return wayPoints;
    }

    /**
     * Grade adjusted pace based on a study by Alberto E. Minetti on the energy cost of
     * walking and running at extreme slopes.
     * <p>
     * see Minetti, A. E. et al. (2002). Energy cost of walking and running at extreme uphill and downhill slopes.
     * Journal of Applied Physiology 93, 1039-1046, http://jap.physiology.org/content/93/3/1039.full
     */
    public double getWalkingGradeFactor(final double g) {
        return 1.0 + (g * (19.5 + g * (46.3 + g * (-43.3 + g * (-30.4 + g * 155.4))))) / 3.6;
    }

    /**
     * Convert the tracks, routes and waypoints into a FIT and write it to the OutputStream.
     *
     * @param outputStream OutputStream to write the result to
     * @throws IOException Something went wrong
     */
    public void writeFit(final OutputStream outputStream) throws IOException {
        final FitBufferEncoder encoder = new FitBufferEncoder();
        writeFit(encoder);
        final byte[] bytes = encoder.close();
        outputStream.write(bytes);
    }

    /**
     * Convert the tracks, routes and waypoints into a FIT and write it to the outfile.
     *
     * @param outfile File to write the result to.
     */
    public void writeFit(final File outfile) {
        final FitFileEncoder encoder = new FitFileEncoder(outfile);
        writeFit(encoder);
        encoder.close();
    }

    protected void writeFit(final FitEncoder encoder) {
        if (pointsToUse.size() == 0) {
            // Nothing to do
            return;
        }

        WayPoint lastWayPoint = null;
        double minEle = Double.NaN;
        double maxEle = Double.NaN;
        double totalAsc = Double.NaN;
        double totalDesc = Double.NaN;
        double totalDist = 0;
        double lastCoursePointDist = 0;
        double lastDist = 0;
        final double speed = gpx2FitOptions.getSpeed();
        double minLat = 1000.0, minLong = 1000.0;
        double maxLat = -1000.0, maxLong = -1000.0;
        boolean skipExtraCP = false;

        //Generate FileIdMessage
        // Every FIT file MUST contain a 'File ID' message as the first message
        final FileIdMesg fileIdMsg = new FileIdMesg();
        fileIdMsg.setManufacturer(GARMIN);
        fileIdMsg.setType(COURSE);
        fileIdMsg.setProduct(424242); // Was 12345
        fileIdMsg.setSerialNumber(System.nanoTime()); // Was 12345L
        fileIdMsg.setNumber(pointsToUse.hashCode());
        fileIdMsg.setTimeCreated(new DateTime(new Date()));
        encoder.write(fileIdMsg); // Encode the FileID Message

        // Every FIT COURSE file MUST contain a Course message
        final CourseMesg courseMesg = new CourseMesg();
        courseMesg.setLocalNum(0);
        courseMesg.setName(getNonNullMax(getName(), 254));
        courseMesg.setSport(Sport.GENERIC);
        encoder.write(courseMesg);

        final WayPoint startWayPoint = pointsToUse.get(0);
        final Date startDate = startWayPoint.getTime();

        final WayPoint endWayPoint = pointsToUse.get(pointsToUse.size() - 1);

        boolean forceSpeed = gpx2FitOptions.isForceSpeed();
        if (startWayPoint.getTime().getTime() == endWayPoint.getTime().getTime()) {
            if (!isNaN(speed))
                forceSpeed = true;
        }
        Date endDate;

        if (forceSpeed) {
            endDate = startDate;
        } else {
            endDate = endWayPoint.getTime();
        }

        // Determine speed, min- and max values, etc. from all waypoints
        for (final WayPoint wpt : pointsToUse) {
            final double ele = wpt.getEle();
            if (!isNaN(ele)) {
                if (minEle > ele || isNaN(minEle))
                    minEle = ele;
                if (maxEle < ele || isNaN(maxEle))
                    maxEle = ele;
            }

            minLat = min(minLat, wpt.getLat());
            minLong = min(minLong, wpt.getLon());
            maxLat = max(maxLat, wpt.getLat());
            maxLong = max(maxLong, wpt.getLon());

            double gradeSpeed = speed;
            if (lastWayPoint == null) {
                wpt.setTotalDist(0);
            } else {
                final double dist = wpt.distance(lastWayPoint);

                if (gpx2FitOptions.isUse3dDistance()) {
                    totalDist += wpt.distance3D(lastWayPoint);
                } else {
                    totalDist += dist;
                }
                wpt.setTotalDist(totalDist);

                if ((!isNaN(ele)) && (!isNaN(lastWayPoint.getEle()))) {
                    final double deltaEle = ele - lastWayPoint.getEle();
                    if (deltaEle > 0.0) {
                        if (isNaN(totalAsc))
                            totalAsc = .0;
                        totalAsc += deltaEle;
                    } else {
                        if (isNaN(totalDesc))
                            totalDesc = .0;
                        totalDesc += Math.abs(deltaEle);
                    }

                    if (gpx2FitOptions.isWalkingGrade()) {
                        final double grade = deltaEle / dist;
                        gradeSpeed = getWalkingGradeFactor(grade) * speed;
                    }
                }

                if (forceSpeed) {
                    endDate = new Date(endDate.getTime() + (long) (dist / gradeSpeed * 1000.0));
                    wpt.setTime(endDate);
                }
            }
            lastWayPoint = wpt;
        }

        // Every FIT COURSE file MUST contain a Lap message
        final LapMesg lapMesg = new LapMesg();
        lapMesg.setLocalNum(0);
        lapMesg.setTimestamp(new DateTime(startDate));
        lapMesg.setStartTime(new DateTime(startDate));
        lapMesg.setStartPositionLat(startWayPoint.getLatSemi());
        lapMesg.setStartPositionLong(startWayPoint.getLonSemi());
        lapMesg.setEndPositionLat(endWayPoint.getLatSemi());
        lapMesg.setEndPositionLong(endWayPoint.getLonSemi());

        final long duration = endDate.getTime() - startDate.getTime();
        lapMesg.setTotalTimerTime((float) (duration / 1000.0));
        lapMesg.setTotalDistance((float) totalDist);
        lapMesg.setAvgSpeed((float) (totalDist * 1000.0 / (double) duration));
        lapMesg.setTotalElapsedTime((float) (duration / 1000.0));

        if (!isNaN(totalAsc)) {
            totalAsc += 0.5;
            lapMesg.setTotalAscent((int) totalAsc);
        }
        if (!isNaN(totalDesc)) {
            totalDesc += 0.5;
            lapMesg.setTotalDescent((int) totalDesc);
        }
        if (!isNaN(maxEle)) {
            lapMesg.setMaxAltitude((float) maxEle);
        }
        if (!isNaN(minEle)) {
            lapMesg.setMinAltitude((float) minEle);
        }

        // Add the bounding box of the course in the undocumented fields
        try {
            final Constructor<Field> c = Field.class.getDeclaredConstructor(String.class, int.class, int.class,
                    double.class, double.class, String.class,
                    boolean.class, Profile.Type.class);
            c.setAccessible(true);
            lapMesg.addField(c.newInstance("bound_max_position_lat", 27, 133, 1.0D, 0.0D, "semicircles", false, Profile.Type.SINT32));
            lapMesg.addField(c.newInstance("bound_max_position_long", 28, 133, 1.0D, 0.0D, "semicircles", false, Profile.Type.SINT32));
            lapMesg.addField(c.newInstance("bound_min_position_lat", 29, 133, 1.0D, 0.0D, "semicircles", false, Profile.Type.SINT32));
            lapMesg.addField(c.newInstance("bound_min_position_long", 30, 133, 1.0D, 0.0D, "semicircles", false, Profile.Type.SINT32));
            lapMesg.setFieldValue(27, 0, WayPoint.toSemiCircles(maxLat), '\uffff');
            lapMesg.setFieldValue(28, 0, WayPoint.toSemiCircles(maxLong), '\uffff');
            lapMesg.setFieldValue(29, 0, WayPoint.toSemiCircles(minLat), '\uffff');
            lapMesg.setFieldValue(30, 0, WayPoint.toSemiCircles(minLong), '\uffff');
        } catch (final NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            // Empty
        }

        encoder.write(lapMesg);

        double cp_min_dist = totalDist / 48.0;
        if (cp_min_dist < gpx2FitOptions.getMinCoursePointDistance())
            cp_min_dist = gpx2FitOptions.getMinCoursePointDistance();

        double pt_min_dist = 0;
        if (gpx2FitOptions.getMaxPoints() != 0) {
            pt_min_dist = totalDist / gpx2FitOptions.getMaxPoints();
            if ((trkPoints.size() + rtePoints.size() + wayPoints.size()) > gpx2FitOptions.getMaxPoints()) {
                skipExtraCP = true;
            }
        }
        if (pt_min_dist < gpx2FitOptions.getMinRoutePointDistance())
            pt_min_dist = gpx2FitOptions.getMinRoutePointDistance();


        // Encode the wayPoints from the GPX
        if (!skipExtraCP && !wayPoints.isEmpty()) {
            for (final WayPoint wpt : wayPoints) {
                final CoursePointMesg cp = getCoursePointMsg(wpt);
                encoder.write(cp);
            }
        }

        // Encode the routePoints from the GPX
        if (!skipExtraCP && !rtePoints.isEmpty()) {
            for (final WayPoint wpt : rtePoints) {
                final CoursePointMesg cp = getCoursePointMsg(wpt);
                encoder.write(cp);
            }
        }

        final EventMesg eventMesg = new EventMesg();
        eventMesg.setLocalNum(0);

        eventMesg.setEvent(Event.TIMER);
        eventMesg.setEventType(EventType.START);
        eventMesg.setEventGroup((short) 0);
        eventMesg.setTimestamp(new DateTime(startDate));
        encoder.write(eventMesg);

        DateTime timestamp = new DateTime(new Date(DateTime.OFFSET));
        long lastTimestamp = startDate.getTime();

        long fakeTime = 0;
        lastWayPoint = null;

        if (gpx2FitOptions.isInjectCoursePoints()) {
            for (final WayPoint wpt : trkPoints) {
                final CoursePointMesg cp = new CoursePointMesg();
                cp.setLocalNum(0);

                fakeTime += 1;
                if (duration != 0) {
                    timestamp = new DateTime(wpt.getTime());
                } else {
                    timestamp = new DateTime(new Date(DateTime.OFFSET + fakeTime * 1000));
                }

                final double dist = wpt.getTotalDist();

                if (lastWayPoint == null) {
                    cp.setPositionLat(wpt.getLatSemi());
                    cp.setPositionLong(wpt.getLonSemi());
                    cp.setName("Start");
                    cp.setType(CoursePoint.GENERIC);

                    cp.setDistance((float) dist);
                    cp.setTimestamp(timestamp);
                    encoder.write(cp);
                }

                if (wpt.equals(endWayPoint)) {
                    cp.setPositionLat(wpt.getLatSemi());
                    cp.setPositionLong(wpt.getLonSemi());
                    cp.setName("End");
                    cp.setType(CoursePoint.GENERIC);
                    cp.setDistance((float) dist);
                    cp.setTimestamp(timestamp);
                    encoder.write(cp);
                } else if ((dist - lastCoursePointDist) > cp_min_dist) {
                    cp.setName("");
                    cp.setType(CoursePoint.GENERIC);
                    cp.setPositionLat(wpt.getLatSemi());
                    cp.setPositionLong(wpt.getLonSemi());
                    cp.setDistance((float) dist);
                    cp.setTimestamp(timestamp);
                    encoder.write(cp);
                    lastCoursePointDist = dist;
                }
                lastWayPoint = wpt;
            }

            fakeTime = 0;
            lastWayPoint = null;
        }

        // Encode the trackPoints from the GPX
        for (final WayPoint wpt : trkPoints) {
            fakeTime += 1;

            if (duration != 0) {
                timestamp = new DateTime(wpt.getTime());
            } else {
                timestamp = new DateTime(new Date(DateTime.OFFSET + fakeTime * 1000));
            }

            final double dist = wpt.getTotalDist();

            if ((lastWayPoint == null) || (dist - lastDist) > pt_min_dist) {
                final RecordMesg r = new RecordMesg();
                r.setLocalNum(0);

                r.setPositionLat(wpt.getLatSemi());
                r.setPositionLong(wpt.getLonSemi());
                r.setDistance((float) dist);
                r.setTimestamp(timestamp);

                if (!isNaN(wpt.getEle())) {
                    r.setAltitude((float) wpt.getEle());
                }

                final long l = timestamp.getDate().getTime();
                if (lastTimestamp != l) {
                    final double gSpeed = (dist - lastDist) / (l - lastTimestamp) * 1000.0;
                    r.setSpeed((float) gSpeed);
                } else {
                    r.setSpeed((float) 0.0);
                }

                encoder.write(r);
                lastDist = dist;
                lastTimestamp = l;
            }

            lastWayPoint = wpt;
        }

        final EventMesg eventMsg2 = new EventMesg();
        eventMsg2.setLocalNum(0);

        eventMsg2.setEvent(Event.TIMER);
        eventMsg2.setEventType(EventType.STOP_DISABLE_ALL);
        eventMsg2.setEventGroup((short) 0);
        //timestamp.add(2);
        eventMsg2.setTimestamp(timestamp);

        encoder.write(eventMsg2);
    }

    private CoursePointMesg getCoursePointMsg(final WayPoint wpt) {
        final CoursePointMesg cp = new CoursePointMesg();
        cp.setLocalNum(0);
        cp.setPositionLat(wpt.getLatSemi());
        cp.setPositionLong(wpt.getLonSemi());
        final String name = wpt.getName();
        cp.setName(getNonNullMax(name, 254));
        cp.setType(CoursePoint.GENERIC);
        return cp;
    }

    private static String getNonNullMax(final String input, final int maxLength) {
        final String text = Objects.requireNonNullElse(input, "");
        return text.substring(0, Math.min(text.length(), maxLength));
    }
}
