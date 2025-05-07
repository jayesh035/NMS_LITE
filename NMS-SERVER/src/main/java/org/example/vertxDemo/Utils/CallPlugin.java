package org.example.vertxDemo.Utils;

import io.vertx.core.json.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;

public class CallPlugin {



    public static JsonObject callGoSnmpPlugin(JsonObject input) throws Exception
    {
        // Create a temporary file for input
        Path inputPath = Files.createTempFile("snmp_input_", ".json");
        Files.write(inputPath, input.encode().getBytes());

        // Create a temporary file for output
        Path outputPath = Files.createTempFile("snmp_output_", ".json");

        // Execute the Go plugin as a process
        ProcessBuilder processBuilder = new ProcessBuilder(
                "/home/jayesh/Desktop/motadata/NMS-PLUGIN/go_plugin",  // Path to your compiled Go binary
                inputPath.toString(),       // Input file path
                outputPath.toString()       // Output file path
        );

        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode != 0)
        {
            // Read error output if available
            String errorOutput = new String(process.getErrorStream().readAllBytes());
            throw new Exception("Go plugin execution failed with exit code " +
                    exitCode + ": " + errorOutput);
        }

        // Read the output file
        String result = new String(Files.readAllBytes(outputPath));

        // Clean up temporary files
        Files.deleteIfExists(inputPath);
        Files.deleteIfExists(outputPath);

        return new JsonObject(result);
    }
}
