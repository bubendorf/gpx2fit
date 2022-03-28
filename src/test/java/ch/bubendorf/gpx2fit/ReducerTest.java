package ch.bubendorf.gpx2fit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReducerTest {

    @Test
    public void reduce() {
        final WayPoint p1 = new WayPoint(47, 7);
        final WayPoint p2 = new WayPoint(47.1, 8);
        final WayPoint p3 = new WayPoint(47.2, 9);
        final WayPoint p4 = new WayPoint(47.4, 10);
        final WayPoint p5 = new WayPoint(47.8, 11);

        final List<WayPoint> trk0 = new ArrayList<>();
        final List<WayPoint> red0 = Reducer.reduce(trk0, 1000);
        assertEquals(0, red0.size());

        final List<WayPoint> trk1 = List.of(p1);
        final List<WayPoint> red1 = Reducer.reduce(trk1, 1000);
        assertEquals(1, red1.size());

        final List<WayPoint> trk2 = List.of(p1, p2);
        final List<WayPoint> red2 = Reducer.reduce(trk2, 1000);
        assertEquals(2, red2.size());

        final List<WayPoint> trk3 = List.of(p1, p2, p3);
        final List<WayPoint> red3 = Reducer.reduce(trk3, 1000);
        assertEquals(2, red3.size());

        final List<WayPoint> trk4 = List.of(p1, p2, p3, p4);
        final List<WayPoint> red4 = Reducer.reduce(trk4, 1000);
        assertEquals(3, red4.size());

        final List<WayPoint> trk5 = List.of(p1, p2, p3, p4, p5);
        final List<WayPoint> red5_1 = Reducer.reduce(trk5, 1000);
        assertEquals(4, red5_1.size());

        final List<WayPoint> red5_2 = Reducer.reduce(trk5, 30000);
        assertEquals(2, red5_2.size());
    }
}
