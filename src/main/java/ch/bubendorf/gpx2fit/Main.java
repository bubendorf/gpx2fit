package ch.bubendorf.gpx2fit;

import com.beust.jcommander.JCommander;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

public class Main {

    private final static CommandLineArguments cmdArgs = new CommandLineArguments();

    public static void main(final String[] args) throws Exception {
//        String currentPath = new java.io.File(".").getCanonicalPath();
//        System.out.println("Current dir:" + currentPath);

        final JCommander jCommander = new JCommander(cmdArgs);
        jCommander.setAllowAbbreviatedOptions(true);
        jCommander.parse(args);
        cmdArgs.complete();

        if (cmdArgs.isHelp()) {
            jCommander.usage();
            System.exit(1);
        }

        if (!cmdArgs.isValid()) {
            System.exit(2);
        }

        final List<String> parameters = cmdArgs.getParameters();
        final String inputFile = parameters.size() < 1 ? "-" : parameters.get(0);
        final String outputFile = parameters.size() < 2 ? "-" : parameters.get(1);

        final Gpx2FitOptions options = new Gpx2FitOptions();
        options.setTracks(cmdArgs.isTracks());
        options.setRoutes(cmdArgs.isRoutes());
        options.setWaypoints(cmdArgs.isWaypoints());
        final InputStream inputStream = "-".equals(inputFile) ? System.in : new FileInputStream(inputFile);
        final Gpx2Fit gpx2fit = new Gpx2Fit(inputFile, inputStream, options);

        if ("-".equals(outputFile)) {
            gpx2fit.writeFit(System.out);
        } else {
            gpx2fit.writeFit(new File(outputFile));
        }
    }
}
