package org.example.vertxDemo.Utils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;

public class CallPlugin {



    public static JsonObject callGoSnmpPlugin(JsonObject input) throws Exception
    {
        // Create a temporary file for input
        var inputPath = Files.createTempFile("snmp_input_", ".json");
        Files.write(inputPath, input.encode().getBytes());

        // Create a temporary file for output
        var outputPath = Files.createTempFile("snmp_output_", ".json");

        // Execute the Go plugin as a process
        var processBuilder = new ProcessBuilder(
                "/home/jayesh/Desktop/motadata/NMS-PLUGIN/main",  // Path to your compiled Go binary
                inputPath.toString(),       // Input file path
                outputPath.toString()       // Output file path
        );

        var process = processBuilder.start();
        var exitCode = process.waitFor();

        if (exitCode != 0)
        {
            // Read error output if available
            var errorOutput = new String(process.getErrorStream().readAllBytes());
            throw new Exception("Go plugin execution failed with exit code " +
                    exitCode + ": " + errorOutput);
        }


        // Read the output file
        var result = new String(Files.readAllBytes(outputPath)).trim();

// Clean up temporary files
        Files.deleteIfExists(inputPath);
        Files.deleteIfExists(outputPath);

        if (result.startsWith("{"))
        {
            // JSON Object
            return new JsonObject(result);

        }
        else if (result.startsWith("["))
        {
            // JSON Array - wrap it inside a JsonObject for uniform return type
            JsonArray arr = new JsonArray(result);
            return new JsonObject().put("results", arr);

        }
        else
        {
            throw new Exception("Unexpected JSON output from Go plugin: " + result);
        }

    }
}
