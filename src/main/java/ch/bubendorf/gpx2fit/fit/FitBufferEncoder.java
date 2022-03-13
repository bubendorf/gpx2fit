package ch.bubendorf.gpx2fit.fit;

import com.garmin.fit.BufferEncoder;
import com.garmin.fit.Fit;

public class FitBufferEncoder extends BufferEncoder implements FitEncoder{
    public FitBufferEncoder() {
        super(Fit.ProtocolVersion.V2_0);
    }
}
