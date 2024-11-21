import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.*;

public class ServiceMonitorApp {

    private static final int REFRESH_INTERVAL = 3000; // 3 Sekunden
    private static final String SERVICES_FILE = "services.txt"; // Datei mit Diensten

    private final JFrame frame;
    private final JPanel servicePanel;
    private final Map<String, JButton> serviceButtons;

    public ServiceMonitorApp() {
        frame = new JFrame("Service Status Monitor");
        servicePanel = new JPanel();
        serviceButtons = new ConcurrentHashMap<>();

        // Dynamisches Layout: Jede Zeile enthält einen Button
        servicePanel.setLayout(new GridLayout(0, 1, 5, 5)); // 0 Zeilen (dynamisch), 1 Spalte

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 600);
        frame.setLayout(new BorderLayout());

        frame.add(new JScrollPane(servicePanel), BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton stopAllButton = new JButton("stop all");
        JButton startAllButton = new JButton("start all");

        stopAllButton.addActionListener(e -> stopAllServices());
        startAllButton.addActionListener(e -> startAllServices());

        controlPanel.add(stopAllButton);
        controlPanel.add(startAllButton);

        frame.add(controlPanel, BorderLayout.SOUTH);

        loadServices();
        scheduleServiceCheck();

        frame.setVisible(true);
    }

    private void loadServices() {
        List<String> services = readServicesFromFile(SERVICES_FILE);
        servicePanel.removeAll();
        serviceButtons.clear();

        for (String service : services) {
            if (isServiceAvailable(service)) { // Prüfe, ob der Dienst existiert
                JButton button = new JButton(service + " - unknown");
                button.setFont(new Font("Arial", Font.PLAIN, 16));
                button.setEnabled(false); // Buttons sind anfangs deaktiviert
                button.addActionListener(e -> toggleService(service));
                serviceButtons.put(service, button);
                servicePanel.add(button);
            }
        }

        servicePanel.revalidate();
        servicePanel.repaint();
    }

    private void scheduleServiceCheck() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::checkServiceStatuses, 0, REFRESH_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void checkServiceStatuses() {
        List<String> services = new ArrayList<>(serviceButtons.keySet());

        for (String service : services) {
            String status = checkServiceStatus(service);
            SwingUtilities.invokeLater(() -> updateServiceButton(service, status));
        }
    }

    private List<String> readServicesFromFile(String filePath) {
        try {
            return Files.lines(Paths.get(filePath))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty()) // Ignoriere leere Zeilen
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private boolean isServiceAvailable(String serviceName) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sc", "query", serviceName});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("SERVICE_NAME")) {
                    return true; // Dienst existiert
                }
            }
        } catch (IOException e) {
            // Fehler ignorieren
        }
        return false; // Dienst existiert nicht
    }

    private String checkServiceStatus(String serviceName) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sc", "query", serviceName});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("STATE")) {
                    if (line.contains("RUNNING")) return "running";
                    if (line.contains("START_PENDING")) return "starting";
                    if (line.contains("STOP_PENDING")) return "stopping";
                    if (line.contains("STOPPED")) return "stopped";
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "unknown"; // Falls der Status nicht ermittelt werden kann
    }

    private void updateServiceButton(String serviceName, String status) {
        JButton button = serviceButtons.get(serviceName);
        if (button != null) {
            button.setText(serviceName + " - " + status);
            button.setEnabled(!status.equals("starting") && !status.equals("stopping")); // Deaktivieren bei "starting" und "stopping"

            // Farbe abhängig vom Status setzen
            switch (status) {
                case "running":
                    button.setBackground(Color.GREEN);
                    break;
                case "stopped":
                    button.setBackground(Color.RED);
                    break;
                case "starting":
                    button.setBackground(Color.YELLOW);
                    break;
                case "stopping":
                    button.setBackground(Color.ORANGE);
                    break;
                default:
                    button.setBackground(Color.LIGHT_GRAY); // Für "unknown" oder andere
                    break;
            }
        }
    }

    private void toggleService(String serviceName) {
        String currentStatus = checkServiceStatus(serviceName);
        try {
            if ("stopped".equals(currentStatus)) {
                // Startet den Dienst
                Runtime.getRuntime().exec(new String[]{"sc", "start", serviceName});
            } else if ("running".equals(currentStatus)) {
                // Stoppt den Dienst
                Runtime.getRuntime().exec(new String[]{"sc", "stop", serviceName});
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopAllServices() {
        List<String> services = new ArrayList<>(serviceButtons.keySet());
        for (String service : services) {
            try {
                Runtime.getRuntime().exec(new String[]{"sc", "stop", service});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startAllServices() {
        List<String> services = new ArrayList<>(serviceButtons.keySet());
        for (String service : services) {
            try {
                Runtime.getRuntime().exec(new String[]{"sc", "start", service});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ServiceMonitorApp::new);
    }
}
