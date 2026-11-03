
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrometheusClient {

    private static final String DEFAULT_PROMETHEUS_URL =
            "http://localhost:9090/api/v1/query";

    private static final String PROMETHEUS_URL =
            System.getenv().getOrDefault(
                    "PROMETHEUS_URL",
                    DEFAULT_PROMETHEUS_URL
            );

    private static final HttpClient client =
            HttpClient.newHttpClient();

    private static double query(String prometheusQuery) throws Exception {

        String encodedQuery =
                URLEncoder.encode(
                        prometheusQuery,
                        StandardCharsets.UTF_8
                );

        String separator =
                PROMETHEUS_URL.contains("?") ? "&" : "?";

        String url =
                PROMETHEUS_URL + separator + "query=" + encodedQuery;

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Prometheus returned HTTP "
                    + response.statusCode()
            );
        }

        String json = response.body();

        Pattern pattern =
                Pattern.compile(
                        "\"value\"\\s*:\\s*\\[.*?,\\s*\"([0-9.Ee+-]+)\"\\]"
                );

        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }

        throw new RuntimeException(
                "Metric value not found.\nPrometheus response:\n"
                + json
        );
    }

    public static double getCpuUsage() throws Exception {

        String query =
                "100 * (1 - avg by (instance) " +
                "(rate(node_cpu_seconds_total{mode=\"idle\"}[1m])))";

        return query(query);
    }

    public static double getMemoryUsage() throws Exception {

        String query =
                "100 * (1 - node_memory_MemAvailable_bytes " +
                "/ node_memory_MemTotal_bytes)";

        return query(query);
    }

    public static void main(String[] args) throws Exception {

        System.out.println(
                "Current CPU Usage: "
                + getCpuUsage()
                + "%"
        );

        System.out.println(
                "Current Memory Usage: "
                + getMemoryUsage()
                + "%"
        );
    }
}
