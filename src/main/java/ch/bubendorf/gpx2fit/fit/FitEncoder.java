package ch.bubendorf.gpx2fit.fit;

import com.garmin.fit.Mesg;
import com.garmin.fit.MesgDefinition;
import com.garmin.fit.MesgDefinitionListener;
import com.garmin.fit.MesgListener;

import java.util.List;

public interface FitEncoder extends MesgListener, MesgDefinitionListener {
    void write(MesgDefinition mesgDefinition);

    void write(Mesg mesg);

    void write(List<? extends Mesg> mesgs);
}
