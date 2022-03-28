package ch.bubendorf.gpx2fit;

import com.beust.jcommander.Parameter;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ALL")
public class CommandLineArguments {

    @Parameter(description="[input file|-] [output file|-]")
    private List<String> parameters = new ArrayList<>();

    @Parameter(names = {"-h", "--help"}, description="Show this help", help = true)
    private boolean help = false;

    @Parameter(names = {"-v", "--version"}, description="Show the version info and exit", help = true)
    private boolean showVersion = false;

    @Parameter(names = {"-d", "--tolerance"}, description="Min distance/tolerance of the track points to keep")
    private double tolerance = 0;

    @Parameter(names = {"-t", "--track"}, description="Process only Tracks (<trk / trkseg>)")
    private boolean tracks = false;

    @Parameter(names = {"-r", "--route"}, description="Process only Routes (<rte>)")
    private boolean routes = false;

    @Parameter(names = {"-w", "--waypoint"}, description="Process only Waypoints (<wpts>)")
    private boolean waypoints = false;

    public List<String> getParameters() {
        return parameters;
    }

    public void complete() {
        if (!tracks && !routes && !waypoints) {
            tracks = true;
            routes = true;
            waypoints = true;
        }
    }

    public boolean isHelp() {
        return help;
    }

    public boolean isShowVersion() {
        return showVersion;
    }

    public double getTolerance() {
        return tolerance;
    }

    public boolean isTracks() {
        return tracks;
    }

    public boolean isRoutes() {
        return routes;
    }

    public boolean isWaypoints() {
        return waypoints;
    }

    public boolean isValid() {
        return true;
    }
}