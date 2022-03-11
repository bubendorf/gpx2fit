package ch.bubendorf.gpx2fit;

import com.beust.jcommander.JCommander;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

public class Main {

    private final static CommandLineArguments cmdArgs = new CommandLineArguments();

    public static void main(final String[] args) throws Exception {
        String currentPath = new java.io.File(".").getCanonicalPath();
        System.out.println("Current dir:" + currentPath);

        final JCommander jCommander = new JCommander(cmdArgs);
        jCommander.setAllowAbbreviatedOptions(true);
        jCommander.parse(args);

        if (cmdArgs.isHelp()) {
            jCommander.usage();
            System.exit(1);
        }

        if (!cmdArgs.isValid()) {
            System.exit(2);
        }

        final List<String> parameters = cmdArgs.getParameters();
        final String inputFile = parameters.get(0);
        final String outputFile = parameters.get(1);

        final Gpx2FitOptions options = new Gpx2FitOptions();
        final FileInputStream inputStream = new FileInputStream(inputFile);
        Gpx2Fit gpx2fit = new Gpx2Fit(inputFile, inputStream, options);

        gpx2fit.writeFit(new File(outputFile));
    }
}
