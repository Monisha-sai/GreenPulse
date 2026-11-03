/**
 * GreenPulse synthetic time-varying carbon-intensity model.
 *
 * IMPORTANT:
 * - This is a synthetic demonstration profile, not live or measured grid data.
 * - simulationTimeSeconds is measured in simulated seconds from the simulation's
 *   time origin. One simulated hour = 3600 seconds.
 * - Hourly values are linearly interpolated, including between hour 23 and 00.
 *
 * Save as: TimeVaryingCarbonIntensityModel.java
 * Compile:  javac TimeVaryingCarbonIntensityModel.java
 */
public final class TimeVaryingCarbonIntensityModel {

    private static final double SECONDS_PER_HOUR = 3600.0;

    private TimeVaryingCarbonIntensityModel() {
        // Utility class; do not instantiate.
    }

    private static final class RegionProfile {
        final String region;
        final double[] hourlyIntensity;

        RegionProfile(String region, double[] hourlyIntensity) {
            if (hourlyIntensity == null || hourlyIntensity.length != 24) {
                throw new IllegalArgumentException(
                    "Each carbon profile must contain exactly 24 hourly values."
                );
            }
            this.region = region;
            this.hourlyIntensity = hourlyIntensity.clone();
        }
    }

    /*
     * Synthetic hourly intensity profiles in kg CO2/kWh.
     * The values are illustrative and intentionally vary over the day so that
     * dynamic scheduling can be tested. Replace them with documented grid data
     * before making real-world carbon claims.
     */
    private static final RegionProfile NORTHERN = new RegionProfile(
        "Northern",
        new double[] {
            0.70, 0.69, 0.68, 0.67, 0.66, 0.65,
            0.64, 0.65, 0.68, 0.71, 0.73, 0.74,
            0.75, 0.74, 0.72, 0.70, 0.69, 0.71,
            0.74, 0.76, 0.75, 0.73, 0.72, 0.71
        }
    );

    private static final RegionProfile SOUTHERN = new RegionProfile(
        "Southern",
        new double[] {
            0.82, 0.81, 0.80, 0.79, 0.78, 0.76,
            0.73, 0.70, 0.67, 0.64, 0.62, 0.61,
            0.60, 0.61, 0.63, 0.66, 0.70, 0.75,
            0.80, 0.84, 0.86, 0.87, 0.85, 0.83
        }
    );

    private static final RegionProfile WESTERN = new RegionProfile(
        "Western",
        new double[] {
            0.91, 0.90, 0.89, 0.88, 0.87, 0.86,
            0.85, 0.84, 0.85, 0.87, 0.89, 0.90,
            0.91, 0.92, 0.93, 0.92, 0.91, 0.90,
            0.89, 0.90, 0.92, 0.93, 0.92, 0.91
        }
    );

    /**
     * Returns the synthetic carbon intensity for a server at a simulated time.
     *
     * @param serverName server identifier (Server-1, Server-2, or Server-3)
     * @param simulationTimeSeconds simulated timestamp in seconds
     * @return kg CO2/kWh, linearly interpolated between hourly profile points
     */
    public static double getCarbonIntensity(
            String serverName, double simulationTimeSeconds) {

        if (!Double.isFinite(simulationTimeSeconds)) {
            throw new IllegalArgumentException(
                "Simulation time must be a finite number."
            );
        }

        RegionProfile profile = profileFor(serverName);

        // Support negative times safely as well as normal non-negative times.
        double hours = simulationTimeSeconds / SECONDS_PER_HOUR;
        double wrappedHour = hours % 24.0;
        if (wrappedHour < 0.0) {
            wrappedHour += 24.0;
        }

        int hour0 = (int) Math.floor(wrappedHour);
        int hour1 = (hour0 + 1) % 24;
        double fraction = wrappedHour - hour0;

        double first = profile.hourlyIntensity[hour0];
        double second = profile.hourlyIntensity[hour1];

        return first + (second - first) * fraction;
    }

    /**
     * Compatibility method for older code that calls
     * getCarbonIntensity(serverName) without a timestamp.
     *
     * This returns the original fixed regional factor from the current
     * GreenPulse configuration. Dynamic scheduling/accounting must call the
     * two-argument method with a timestamp.
     */
    public static double getCarbonIntensity(String serverName) {
        switch (normalizeServerName(serverName)) {
            case "SERVER-1":
                return 0.7335;
            case "SERVER-2":
                return 0.8086;
            case "SERVER-3":
                return 0.8900;
            default:
                throw new IllegalArgumentException(
                    "Unknown server: " + serverName
                    + ". Expected Server-1, Server-2, or Server-3."
                );
        }
    }

    /** Returns the region name associated with a known server. */
    public static String getRegion(String serverName) {
        return profileFor(serverName).region;
    }

    /** Returns a defensive copy of the 24 hourly values for a server. */
    public static double[] getHourlyProfile(String serverName) {
        return profileFor(serverName).hourlyIntensity.clone();
    }

    private static RegionProfile profileFor(String serverName) {
        switch (normalizeServerName(serverName)) {
            case "SERVER-1":
                return NORTHERN;
            case "SERVER-2":
                return SOUTHERN;
            case "SERVER-3":
                return WESTERN;
            default:
                throw new IllegalArgumentException(
                    "Unknown server: " + serverName
                    + ". Expected Server-1, Server-2, or Server-3."
                );
        }
    }

    private static String normalizeServerName(String serverName) {
        if (serverName == null) {
            throw new IllegalArgumentException("Server name cannot be null.");
        }
        return serverName.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Small standalone smoke test: java TimeVaryingCarbonIntensityModel */
    public static void main(String[] args) {
        String[] servers = {"Server-1", "Server-2", "Server-3"};
        double[] testHours = {0.0, 6.0, 12.0, 18.0, 23.5};

        System.out.println("GREENPULSE SYNTHETIC CARBON PROFILE");
        System.out.println("Units: kg CO2/kWh");
        System.out.println("These values are illustrative, not live grid measurements.");
        System.out.println();

        for (String server : servers) {
            System.out.println(server + " (" + getRegion(server) + ")");
            for (double hour : testHours) {
                double timeSeconds = hour * SECONDS_PER_HOUR;
                System.out.printf(
                    java.util.Locale.ROOT,
                    "  Hour %4.1f: %.4f%n",
                    hour,
                    getCarbonIntensity(server, timeSeconds)
                );
            }
            System.out.printf(
                java.util.Locale.ROOT,
                "  Fixed compatibility factor: %.4f%n%n",
                getCarbonIntensity(server)
            );
        }
    }
}
