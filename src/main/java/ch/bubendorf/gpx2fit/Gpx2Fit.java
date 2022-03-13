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

// Based on https://github.com/gimportexportdevs/gexporter/blob/master/app/src/main/java/org/surfsite/gexporter/Gpx2Fit.java
public class Gpx2Fit {

    private List<WayPoint> trkPoints = Collections.emptyList();
    private List<WayPoint> rtePoints = Collections.emptyList();
    private List<WayPoint> wayPoints = Collections.emptyList();

    private final List<WayPoint> pointsToUse = new ArrayList<>();

    private final String courseName;

    Gpx2FitOptions gpx2FitOptions;

    public Gpx2Fit(final String name, final InputStream in, final Gpx2FitOptions options) throws Exception {
        courseName = name;
        gpx2FitOptions = options;

        // Load the GPX
        final GPX gpx = GPX.Reader.of(GPX.Reader.Mode.LENIENT).read(in);

        if (options.isTracks()) {
            trkPoints = gpx.tracks().flatMap(Track::segments)
                    .flatMap(TrackSegment::points)
                    .map(WayPoint::new)
                    .collect(Collectors.toList());
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

        pointsToUse.addAll(trkPoints);
        if (pointsToUse.isEmpty()) {
            pointsToUse.addAll(rtePoints);
        }
        if (pointsToUse.isEmpty()) {
            pointsToUse.addAll(wayPoints);
        }
    }

    public String getName() {
        return courseName;
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

    public void writeFit(final OutputStream outputStream) throws IOException {
        final FitBufferEncoder encoder = new FitBufferEncoder();
        writeFit(encoder);
        final byte[] bytes = encoder.close();
        outputStream.write(bytes);
    }

    public void writeFit(final File outfile) {
        final FitFileEncoder encode = new FitFileEncoder(outfile);
        writeFit(encode);
        encode.close();
    }

    private void writeFit(final FitEncoder encoder) {
        if (pointsToUse.size() == 0) {
            return;
        }

        WayPoint last = null;
        double minEle = Double.NaN;
        double maxEle = Double.NaN;
        double totalAsc = Double.NaN;
        double totalDesc = Double.NaN;
        double totalDist = 0;
        double lcDist = 0;
        double lDist = 0;
        final double speed = gpx2FitOptions.getSpeed();
        double minLat = 1000.0, minLong = 1000.0;
        double maxLat = -1000.0, maxLong = -1000.0;
        boolean skipExtraCP = false;

/*        if (trkPoints.size() <= rtePoints.size()) {
            trkPoints.addAll(rtePoints);
            rtePoints.clear();
        }*/

        //Generate FileIdMessage
        // Every FIT file MUST contain a 'File ID' message as the first message
        final FileIdMesg fileIdMesg = new FileIdMesg();
        fileIdMesg.setManufacturer(Manufacturer.GARMIN);
        fileIdMesg.setType(com.garmin.fit.File.COURSE);
        fileIdMesg.setProduct(12345);
        fileIdMesg.setSerialNumber(12345L);
        fileIdMesg.setNumber(pointsToUse.hashCode());
        fileIdMesg.setTimeCreated(new DateTime(new Date()));
        encoder.write(fileIdMesg); // Encode the FileIDMesg

        // Every FIT COURSE file MUST contain a Course message
        final CourseMesg courseMesg = new CourseMesg();
        courseMesg.setLocalNum(0);
        courseMesg.setName(getName());
        courseMesg.setSport(Sport.GENERIC);
        encoder.write(courseMesg);

        final WayPoint firstWayPoint = pointsToUse.get(0);
        final Date startDate = firstWayPoint.getTime();

        final WayPoint lastWayPoint = pointsToUse.get(pointsToUse.size() - 1);

        boolean forceSpeed = gpx2FitOptions.isForceSpeed();
        if (firstWayPoint.getTime().getTime() == lastWayPoint.getTime().getTime()) {
            if (!Double.isNaN(speed))
                forceSpeed = true;
        }
        Date endDate;

        if (forceSpeed) {
            endDate = startDate;
        } else {
            endDate = lastWayPoint.getTime();
        }

        for (final WayPoint wpt : pointsToUse) {
            final double ele = wpt.getEle();
            if (!Double.isNaN(ele)) {
                if (minEle > ele || Double.isNaN(minEle))
                    minEle = ele;
                if (maxEle < ele || Double.isNaN(maxEle))
                    maxEle = ele;
            }

            minLat = Math.min(minLat, wpt.getLat());
            minLong = Math.min(minLong, wpt.getLon());
            maxLat = Math.max(maxLat, wpt.getLat());
            maxLong = Math.max(maxLong, wpt.getLon());

            double gspeed = speed;
            if (last == null) {
                wpt.setTotalDist(0);
            } else {
                final double d = wpt.distance(last);

                if (gpx2FitOptions.isUse3dDistance()) {
                    totalDist += wpt.distance3D(last);
                } else {
                    totalDist += d;
                }

                wpt.setTotalDist(totalDist);

                if ((!Double.isNaN(ele)) && (!Double.isNaN(last.getEle()))) {
                    final double dele = ele - last.getEle();
                    if (dele > 0.0) {
                        if (Double.isNaN(totalAsc))
                            totalAsc = .0;
                        totalAsc += dele;
                    } else {
                        if (Double.isNaN(totalDesc))
                            totalDesc = .0;
                        totalDesc += Math.abs(dele);
                    }

                    if (gpx2FitOptions.isWalkingGrade()) {
                        final double grade = dele / d;
                        gspeed = getWalkingGradeFactor(grade) * speed;
                    }
                }

                if (forceSpeed) {
                    endDate = new Date(endDate.getTime() + (long) (d / gspeed * 1000.0));
                    wpt.setTime(endDate);
                }
            }
            last = wpt;
        }

        // Every FIT COURSE file MUST contain a Lap message
        final LapMesg lapMesg = new LapMesg();
        lapMesg.setLocalNum(0);

        lapMesg.setTimestamp(new DateTime(startDate));
        lapMesg.setStartTime(new DateTime(startDate));

        lapMesg.setStartPositionLat(firstWayPoint.getLatSemi());
        lapMesg.setStartPositionLong(firstWayPoint.getLonSemi());

        lapMesg.setEndPositionLat(lastWayPoint.getLatSemi());
        lapMesg.setEndPositionLong(lastWayPoint.getLonSemi());

        final long duration = endDate.getTime() - startDate.getTime();

        lapMesg.setTotalTimerTime((float) (duration / 1000.0));
        lapMesg.setTotalDistance((float) totalDist);
        lapMesg.setAvgSpeed((float) (totalDist * 1000.0 / (double) duration));

        lapMesg.setTotalElapsedTime((float) (duration / 1000.0));

        if (!Double.isNaN(totalAsc)) {
            totalAsc += 0.5;
            lapMesg.setTotalAscent((int) totalAsc);
        }
        if (!Double.isNaN(totalDesc)) {
            totalDesc += 0.5;
            lapMesg.setTotalDescent((int) totalDesc);
        }
        if (!Double.isNaN(maxEle)) {
            lapMesg.setMaxAltitude((float) maxEle);
        }

        if (!Double.isNaN(minEle)) {
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


        if (!skipExtraCP && !wayPoints.isEmpty()) {
            for (final WayPoint wpt : wayPoints) {
                final CoursePointMesg cp = new CoursePointMesg();
                cp.setLocalNum(0);

                cp.setPositionLat(wpt.getLatSemi());
                cp.setPositionLong(wpt.getLonSemi());
                final String name = wpt.getName();
                cp.setName(Objects.requireNonNullElse(name, ""));
                cp.setType(CoursePoint.GENERIC);
                encoder.write(cp);
            }
        }

        if (!skipExtraCP && !rtePoints.isEmpty()) {
            for (final WayPoint wpt : rtePoints) {
                final CoursePointMesg cp = new CoursePointMesg();
                cp.setLocalNum(0);

                cp.setPositionLat(wpt.getLatSemi());
                cp.setPositionLong(wpt.getLonSemi());
                final String name = wpt.getName();
                cp.setName(Objects.requireNonNullElse(name, ""));
                cp.setType(CoursePoint.GENERIC);
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

        DateTime timestamp = new DateTime(new Date(WayPoint.RefMilliSec));
        long ltimestamp = startDate.getTime();

        long i = 0;
        last = null;

        if (gpx2FitOptions.isInjectCoursePoints()) {
            for (final WayPoint wpt : trkPoints) {
                final CoursePointMesg cp = new CoursePointMesg();
                cp.setLocalNum(0);

                i += 1;

                if (duration != 0)
                    timestamp = new DateTime(wpt.getTime());
                else
                    timestamp = new DateTime(new Date(WayPoint.RefMilliSec + i * 1000));

                final double dist = wpt.getTotalDist();

                if (last == null) {
                    cp.setPositionLat(wpt.getLatSemi());
                    cp.setPositionLong(wpt.getLonSemi());
                    cp.setName("Start");
                    cp.setType(CoursePoint.GENERIC);

                    cp.setDistance((float) dist);
                    cp.setTimestamp(timestamp);
                    encoder.write(cp);
                }

                if (wpt.equals(lastWayPoint)) {
                    cp.setPositionLat(wpt.getLatSemi());
                    cp.setPositionLong(wpt.getLonSemi());
                    cp.setName("End");
                    cp.setType(CoursePoint.GENERIC);
                    cp.setDistance((float) dist);
                    cp.setTimestamp(timestamp);
                    encoder.write(cp);
                } else if ((dist - lcDist) > cp_min_dist) {
                    cp.setName("");
                    cp.setType(CoursePoint.GENERIC);
                    cp.setPositionLat(wpt.getLatSemi());
                    cp.setPositionLong(wpt.getLonSemi());
                    cp.setDistance((float) dist);
                    cp.setTimestamp(timestamp);
                    encoder.write(cp);
                    lcDist = dist;
                }
                last = wpt;
            }

            i = 0;
            last = null;
        }

        for (final WayPoint wpt : trkPoints) {
            i += 1;

            if (duration != 0)
                timestamp = new DateTime(wpt.getTime());
            else
                timestamp = new DateTime(new Date(WayPoint.RefMilliSec + i * 1000));

            final double dist = wpt.getTotalDist();

            if ((last == null) || (dist - lDist) > pt_min_dist) {
                final RecordMesg r = new RecordMesg();
                r.setLocalNum(0);

                r.setPositionLat(wpt.getLatSemi());
                r.setPositionLong(wpt.getLonSemi());
                r.setDistance((float) dist);
                r.setTimestamp(timestamp);

                if (!Double.isNaN(wpt.getEle())) {
                    r.setAltitude((float) wpt.getEle());
                }
                final long l = timestamp.getDate().getTime();

                if (ltimestamp != l) {
                    final double gspeed = (dist - lDist) / (l - ltimestamp) * 1000.0;
                    r.setSpeed((float) gspeed);
                } else {
                    r.setSpeed((float) 0.0);
                }

                encoder.write(r);
                lDist = dist;
                ltimestamp = l;
            }

            last = wpt;
        }

        final EventMesg eventMesg2 = new EventMesg();
        eventMesg2.setLocalNum(0);

        eventMesg2.setEvent(Event.TIMER);
        eventMesg2.setEventType(EventType.STOP_DISABLE_ALL);
        eventMesg2.setEventGroup((short) 0);
        //timestamp.add(2);
        eventMesg2.setTimestamp(timestamp);

        encoder.write(eventMesg2);
    }
}
