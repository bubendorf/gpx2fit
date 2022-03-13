package ch.bubendorf.gpx2fit.fit;

import com.garmin.fit.FileEncoder;
import com.garmin.fit.Fit;

import java.io.File;

public class FitFileEncoder extends FileEncoder implements FitEncoder {
    public FitFileEncoder(final File outfile) {
        super(outfile, Fit.ProtocolVersion.V2_0);
    }
}
