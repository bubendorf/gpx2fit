# gpx2fit

Command line GPX to Garmin FIT converter

A simple command line tool to convert GPX (GPS Exchange Format) 
files into Garmin FIT (Flexible and Interoperable Data Transfer) files.
Based on https://github.com/gimportexportdevs/gexporter


## Prerequisites

- Java runtime environment 17 or newer
- Garmin FIT SDK

## Build

- Download and install the Garmin FIT SDK (https://developer.garmin.com/fit/overview/) into folder FitSDKRelease_21.67.00
- Clone this repo
- run "gradle build" ==> The resulting file build/libs/gpx2fit-1.0-all.jar is all you need.


## Usage

```
Usage: java -jar gpx2fit-1.0-all.jar [options] [input file|-] [output file|-]
Options:
    -h, --help
      Show this help
    -r, --route
      Process only Routes (<rte>)
      Default: false
    -t, --track
      Process only Tracks (<trk / trkseg>)
      Default: false
    -v, --version
      Show the version info and exit
    -w, --waypoint
      Process only Waypoints (<wpts>)
      Default: false

```

