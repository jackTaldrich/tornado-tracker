import com.google.gson.Gson;

import java.io.IOException;
import java.lang.Thread;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TornadoMap {
    private static final String PROCESSED_ALERTS_FILE = "processed_alerts.txt";
    private static final Set<String> processedAlerts = new HashSet<>();
    private static final List<String> formattedAlerts = new ArrayList<>();
    private static final Logger LOGGER = Logger.getLogger(TornadoMap.class.getName());

    public static void main(String[] args) {
        boolean keepRunning = true;

        while (keepRunning) {
            try {
                loadProcessedAlerts();

                String tornadoData = fetchTornadoData();
                String timeStamp = new SimpleDateFormat("MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
                System.out.println(timeStamp + ": " + processTornadoData(tornadoData));

                saveProcessedAlerts();

                Thread.sleep(Duration.ofMinutes(1));
            } catch (Exception e) {
                keepRunning = false;
            }
        }
    }

    private static void loadProcessedAlerts() {
        try {
            processedAlerts.clear();
            List<String> lines = Files.readAllLines(Paths.get(PROCESSED_ALERTS_FILE));
            for (String line : lines) {
                String[] parts = line.split(",");
                // Assuming the first part is the effective timestamp
                processedAlerts.add(parts[0].trim());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not load processed alerts: ", e);
        }
    }

    private static void saveProcessedAlerts() {
        try {
            Files.write(Paths.get(PROCESSED_ALERTS_FILE), formattedAlerts);
            formattedAlerts.clear(); // Clear the list after saving
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not save processed alerts: ", e);
        }
    }


    public static String processTornadoData(String jsonData) {
        Gson gson = new Gson();
        Alert alert = gson.fromJson(jsonData, Alert.class);

        StringBuilder output = new StringBuilder();
        boolean tornadoFound = false;

        for (Feature feature : alert.features) {
            AlertProperties props = feature.properties;
            if (props.event != null && props.event.contains("Tornado")) {
                if (!processedAlerts.contains(props.effective)) {
                    tornadoFound = true; // Update this flag when a new alert is found

                    String formattedTime = convertTimeToMdt(props.effective);
                    String alertString = formattedTime + "," +
                            props.event + "," +
                            props.areaDesc + "," +
                            props.severity + "," +
                            props.urgency;

                    output.append(alertString).append("\n");

                    // Add to list for saving
                    formattedAlerts.add(alertString);

                    // Mark as processed
                    processedAlerts.add(props.effective);
                }
            }
        }

        if (!tornadoFound) {
            return "No new tornadoes found :(";
        } else {
            return output.toString();
        }
    }

    private static String fetchTornadoData() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.weather.gov/alerts/active")) // Replace with actual API URL
                .header("accept", "application/json") // Set header if required
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception Caught: ", e);
            return null;
        }
    }

    private static String convertTimeToMdt(String time) {
        ZonedDateTime utcTime = ZonedDateTime.parse(time);

        ZonedDateTime mdtTime = utcTime.withZoneSameInstant(ZoneId.of("America/Denver"));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy HH:mm:ss");
        return mdtTime.format(formatter);
    }

    static public class Alert {
        private final Feature[] features;

        private Alert(Feature[] features) {
            this.features = features;
        }
    }

    static class AlertProperties {
        String event;
        String severity;
        String urgency;
        String areaDesc;
        String effective;

        @Override
        public String toString() {
            return "AlertProperties{" +
                    "event='" + event + '\'' +
                    ", severity='" + severity + '\'' +
                    ", urgency='" + urgency + '\'' +
                    ", areaDesc='" + areaDesc + '\'' +
                    ", effective='" + effective + '\'' +
                    '}';
        }

        // other methods
    }

    static class Feature {
        AlertProperties properties;
    }
}