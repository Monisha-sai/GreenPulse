
public class CarbonIntensityModel {

    /*
     * GreenPulse Regional Carbon Intensity Model
     *
     * Unit:
     * kg CO2 / kWh
     *
     * These values are based on FY 2024-25 regional
     * emission-factor values computed from CEA V21.0
     * plant-level data.
     *
     * IMPORTANT:
     * These are simulation locations for GreenPulse.
     * They do not represent the actual physical location
     * of the VMware servers.
     *
     * The regional factors are model inputs and are not
     * live grid carbon-intensity measurements.
     */

    // =========================================================
    // REGIONAL CARBON INTENSITIES
    // =========================================================

    // Northern Grid
    private static final double NORTHERN = 0.7335;

    // Southern Grid
    private static final double SOUTHERN = 0.8086;

    // Western Grid
    private static final double WESTERN = 0.8900;


    // =========================================================
    // SERVER LOCATION MAPPING
    // =========================================================

    /*
     * Server-1 -> Northern
     * Server-2 -> Southern
     * Server-3 -> Western
     */

    public static double getCarbonIntensity(String serverName) {

        switch (serverName) {

            case "Server-1":
                return NORTHERN;

            case "Server-2":
                return SOUTHERN;

            case "Server-3":
                return WESTERN;

            default:
                throw new IllegalArgumentException(
                    "Unknown server: " + serverName
                );
        }
    }


    // =========================================================
    // CARBON CALCULATION
    // =========================================================

    public static double calculateCarbon(
            double energyKWh,
            String serverName) {

        if (energyKWh < 0 || !Double.isFinite(energyKWh)) {
            throw new IllegalArgumentException(
                "Energy must be a finite, non-negative value."
            );
        }

        double carbonIntensity =
                getCarbonIntensity(serverName);

        return energyKWh * carbonIntensity;
    }


    // =========================================================
    // GET SERVER REGION
    // =========================================================

    public static String getRegion(String serverName) {

        switch (serverName) {

            case "Server-1":
                return "Northern";

            case "Server-2":
                return "Southern";

            case "Server-3":
                return "Western";

            default:
                throw new IllegalArgumentException(
                    "Unknown server: " + serverName
                );
        }
    }


    // =========================================================
    // DISPLAY MODEL
    // =========================================================

    public static void printModel() {

        System.out.printf(
            "Server-1 -> Northern -> %.4f kg CO2/kWh%n",
            NORTHERN
        );

        System.out.printf(
            "Server-2 -> Southern -> %.4f kg CO2/kWh%n",
            SOUTHERN
        );

        System.out.printf(
            "Server-3 -> Western -> %.4f kg CO2/kWh%n",
            WESTERN
        );
    }
}
