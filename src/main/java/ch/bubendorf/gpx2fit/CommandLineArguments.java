package ch.bubendorf.gpx2fit;

import com.beust.jcommander.Parameter;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ALL")
public class CommandLineArguments {

    @Parameter
    private List<String> parameters = new ArrayList<>();

    @Parameter(names = {"-h", "--help"}, help = true)
    private boolean isHelp = false;

    public List<String> getParameters() {
        return parameters;
    }

    public boolean isHelp() {
        return isHelp;
    }

    public boolean isValid() {
        return true;
    }
}