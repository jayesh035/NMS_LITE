package org.example.vertxDemo.Polling;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.io.*;
import java.util.Map;

public class SNMPService {
    private static final Map<String, String> oidMap = Map.of(
            "sysName", ".1.3.6.1.2.1.1.5.0",
            "sysDescr", ".1.3.6.1.2.1.1.1.0",
            "sysLocation", ".1.3.6.1.2.1.1.6.0"
    );

    private static Process pluginProcess;
    private static BufferedWriter pluginWriter;
    private static BufferedReader pluginReader;

    static {
        try {
            // Start Go plugin as an external process
            pluginProcess = new ProcessBuilder("go-plugin").start(); // Replace with your actual Go binary name
            pluginWriter = new BufferedWriter(new OutputStreamWriter(pluginProcess.getOutputStream()));
            pluginReader = new BufferedReader(new InputStreamReader(pluginProcess.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void fetch(Vertx vertx, String ip, String metric, Handler<String> handler) {
        if (!oidMap.containsKey(metric)) {
            handler.handle("ERROR: Unknown metric " + metric);
            return;
        }

        vertx.executeBlocking(promise -> {
            try {
                // Write request to Go plugin
                synchronized (pluginWriter) {
                    pluginWriter.write(ip + " " + metric);
                    pluginWriter.newLine();
                    pluginWriter.flush();
                }

                // Read response
                String result;
                synchronized (pluginReader) {
                    result = pluginReader.readLine(); // Blocking read
                }

                if (result != null && !result.isEmpty()) {
                    promise.complete(result);
                } else {
                    promise.fail("Empty response from plugin");
                }
            } catch (IOException e) {
                promise.fail("Plugin communication error: " + e.getMessage());
            }
        }, res -> {
            if (res.succeeded()) {
                handler.handle((String) res.result());
            } else {
                handler.handle("SNMP error: " + res.cause().getMessage());
            }
        });
    }
}
