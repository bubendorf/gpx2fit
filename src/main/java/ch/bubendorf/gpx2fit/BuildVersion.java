package ch.bubendorf.gpx2fit;

public abstract class BuildVersion {
    public static String getBuildVersion() {
        final String version = BuildVersion.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }
}
