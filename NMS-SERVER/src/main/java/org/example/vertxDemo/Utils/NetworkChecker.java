package org.example.vertxDemo.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

public class NetworkChecker extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(NetworkChecker.class);
    @Override
    public void start()
    {
        vertx.eventBus().consumer("network.check", msg ->
        {
            JsonObject data = (JsonObject) msg.body();
            String ip = data.getString("ip");
            int port = data.getInteger("port");

            boolean reachable = NetworkChecker.isHostReachable(ip);
            boolean portOpen = reachable && NetworkChecker.isPortOpen(ip, port);

            JsonObject result = new JsonObject()
                    .put("ip", ip)
                    .put("is_ip_reachable", reachable)
                    .put("is_port_open", portOpen);

            msg.reply(result);
        });
    }



    public static boolean isHostReachable(String ipAddress)
    {
        try
        {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win"))
            {
                // Windows: -n for number of pings, -w for timeout in ms
                pb = new ProcessBuilder("ping", "-n", "1", "-w", "1000", ipAddress);
            }
            else
            {
                // Linux/macOS: -c for count, -W for timeout in seconds
                pb = new ProcessBuilder("ping", "-c", "1", "-W", "1", ipAddress);
            }

            Process process = pb.start();
            int returnCode = process.waitFor();
            return returnCode == 0;
        }
        catch (IOException | InterruptedException e)
        {

            logger.error(e.getMessage());
            return false;
        }
    }


    public static boolean isPortOpen(String ipAddress, int port)
    {
        try
        {
            String command = String.format("nc -zvu %s %d", ipAddress, port);
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        }
        catch (IOException | InterruptedException e)
        {
            logger.error(e.getMessage());
            return false;
        }
    }

    // Main method for testing
    public static void main(String[] args)
    {
        String ip = "10.20.41.66"; // Replace with your target IP
        int port = 161;            // Replace with the port to check

        boolean reachable = isHostReachable(ip);
        boolean portOpen = isPortOpen(ip, port);

        System.out.println("Ping " + ip + ": " + (reachable ? "Reachable" : "Unreachable"));
        System.out.println("Port " + port + " on " + ip + ": " + (portOpen ? "Open" : "Closed"));
    }
}


//
