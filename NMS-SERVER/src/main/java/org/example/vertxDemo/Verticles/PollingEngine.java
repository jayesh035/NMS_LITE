//package org.example.vertxDemo.Polling;
//
//import io.vertx.core.Vertx;
//import io.vertx.core.json.JsonArray;
//import io.vertx.core.json.JsonObject;
//import org.example.vertxDemo.Utils.CallPlugin;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class PollingEngine {
//    private static final Set<Long> activeTimers = Collections.newSetFromMap(new ConcurrentHashMap<>());
//    private static final Logger logger = LoggerFactory.getLogger(PollingEngine.class);
//    private static final Map<Long, List<String>> metricsTimes = new ConcurrentHashMap<>();
//    private static final Map<Long, Long> lastExecutionTime = new ConcurrentHashMap<>();
//
//    public static void startPolling(Vertx vertx, long provisionId, JsonObject finalMetrics) {
//        if (!activeTimers.add(provisionId)) {
//            logger.info("Already polling for provisionId: {}", provisionId);
//            return;
//        }
//
//        logger.info("Starting polling for provisionId: {} with metrics: {}", provisionId, finalMetrics);
//
//        for (String metricKey : finalMetrics.fieldNames()) {
//            Object value = finalMetrics.getValue(metricKey);
//            Long timeInterval = parseMetricValue(value, metricKey);
//
//            if (timeInterval != null) {
//                // Store the full metric key as is - we'll parse it during execution
//                metricsTimes.computeIfAbsent(timeInterval, k -> Collections.synchronizedList(new ArrayList<>()))
//                        .add(metricKey);
//
//                logger.info("Added metric: {} with interval: {} seconds", metricKey, timeInterval);
//            }
//        }
//    }
//
//    private static Long parseMetricValue(Object value, String metric) {
//        try {
//            if (value instanceof Integer) {
//                return ((Integer) value).longValue();
//            } else if (value instanceof Long) {
//                return (Long) value;
//            } else if (value instanceof String) {
//                return Long.parseLong((String) value);
//            } else {
//                logger.error("Unsupported value type for metric: {} - {}", metric, value.getClass().getName());
//            }
//        } catch (NumberFormatException e) {
//            logger.error("Failed to parse metric value {} for metric {}", value, metric);
//        }
//        return null;
//    }
//
////    public static void globalTimer(Vertx vertx) {
////        vertx.setPeriodic(1000, id -> {
////            AtomicInteger count= new AtomicInteger();
////            if (!metricsTimes.isEmpty()) {
////                List<String> metricsToProcess = new ArrayList<>();
////                Map<Long, List<String>> tempMetricsTimes = new ConcurrentHashMap<>();
////
////                metricsTimes.forEach((timeInterval, metrics) -> {
////                    long currentTime = System.currentTimeMillis();
////                    long lastTime = lastExecutionTime.getOrDefault(timeInterval, 0L);
////
////                    // If this is the first execution or if enough time has passed
////                    if (lastTime == 0 || (currentTime - lastTime) >= timeInterval * 1000) {
////                        logger.info("Polling triggered for interval {} seconds", timeInterval);
////                        metricsToProcess.addAll(metrics);
////                        lastExecutionTime.put(timeInterval, currentTime);
////                    }
////
////                    // Always keep metrics in the map for next cycle
////
////                    tempMetricsTimes.put(timeInterval, metrics);
////                });
////
////                count.getAndIncrement();
////                logger.error("time is {} sec", count);
////                // Update metrics map
////                metricsTimes.clear();
////                metricsTimes.putAll(tempMetricsTimes);
////
////                if (!metricsToProcess.isEmpty()) {
////                    handleCallGoPlugin(metricsToProcess);
////                }
////            }
////        });
////    }
//
//    public static void globalTimer(Vertx vertx) {
//        // Define the counter OUTSIDE the periodic callback
//        AtomicInteger count = new AtomicInteger();
//
//        vertx.setPeriodic(1000, id -> {
//            if (!metricsTimes.isEmpty()) {
//                List<String> metricsToProcess = new ArrayList<>();
//                Map<Long, List<String>> tempMetricsTimes = new ConcurrentHashMap<>();
//
//                metricsTimes.forEach((timeInterval, metrics) -> {
//                    long currentTime = System.currentTimeMillis();
//                    long lastTime = lastExecutionTime.getOrDefault(timeInterval, 0L);
//
//                    // If this is the first execution or if enough time has passed
//                    if (lastTime == 0 || (currentTime - lastTime) >= timeInterval * 1000) {
//                        logger.info("Polling triggered for interval {} seconds", timeInterval);
//                        metricsToProcess.addAll(metrics);
//                        lastExecutionTime.put(timeInterval, currentTime);
//                    }
//
//                    // Always keep metrics in the map for next cycle
//                    tempMetricsTimes.put(timeInterval, metrics);
//                });
//
//                // Increment the counter once per timer execution (not per time interval)
////                int currentCount = count.incrementAndGet();
////                logger.error("Timer execution count: {} sec", currentCount);
//
//                // Update metrics map
//                metricsTimes.clear();
//                metricsTimes.putAll(tempMetricsTimes);
//
//                if (!metricsToProcess.isEmpty()) {
//                    handleCallGoPlugin(metricsToProcess);
//                }
//            }
//        });
//    }
//    private static void handleCallGoPlugin(List<String> metricsToProcess) {
//        JsonObject goPluginRequestData = new JsonObject()
//                .put("method_type", "SNMP_GET")
//                .put("ips", new JsonArray())
//                .put("credentials", new JsonArray());
//
//        JsonArray snmpGetData = new JsonArray();
//        JsonArray ipAddresses = new JsonArray();
//        Map<String, Set<String>> uniqueCredentials = new HashMap<>();
//
//        for (String metricKey : metricsToProcess) {
//            // Parse the key which contains all needed parameters
//            // Format: "discoveryId,ip,port,communityString,snmpMetric"
//            String[] parts = metricKey.split(",");
//
//            if (parts.length >= 5) {
//                String discoveryId = parts[0];
//                String ip = parts[1];
//                String port = parts[2];
//                String communityString = parts[3];
//                String snmpMetric = parts[4];
//
//                try {
//                    int discoveryIdInt = Integer.parseInt(discoveryId);
//                    int portInt = Integer.parseInt(port);
//
//                    JsonObject requestDetails = new JsonObject()
//                            .put("ip", ip)
//                            .put("discovery_id", discoveryIdInt)
//                            .put("port", portInt)
//                            .put("community_string", communityString)
//                            .put("snmp_metric", snmpMetric);
//
//                    snmpGetData.add(requestDetails);
//
//                    // Add to unique IPs list
//                    if (!ipAddresses.contains(ip)) {
//                        ipAddresses.add(ip);
//                    }
//
//                    // Track unique credentials for this IP
//                    uniqueCredentials.computeIfAbsent(ip, k -> new HashSet<>())
//                            .add(communityString);
//
//                    logger.debug("Added request for metric: {} ({}:{})", snmpMetric, ip, portInt);
//                } catch (NumberFormatException e) {
//                    logger.error("Failed to parse numeric values in metric key: {}", metricKey, e);
//                }
//            } else {
//                logger.error("Invalid metric key format: {}", metricKey);
//            }
//        }
//
//        // Process unique credentials
//        JsonArray credentials = new JsonArray();
//        int credentialId = 1;
//        for (Map.Entry<String, Set<String>> entry : uniqueCredentials.entrySet()) {
//            for (String community : entry.getValue()) {
//                credentials.add(new JsonObject()
//                        .put("id", credentialId++)
//                        .put("system_type", "snmp")
//                        .put("community", community));
//            }
//        }
//
//        goPluginRequestData.put("snmp_get_data", snmpGetData);
//        goPluginRequestData.put("ips", ipAddresses);
//        goPluginRequestData.put("credentials", credentials);
//
//        try {
//            logger.debug("Calling Go SNMP plugin with request: {}", goPluginRequestData);
//            JsonObject result = CallPlugin.callGoSnmpPlugin(goPluginRequestData);
//            logger.info("Go Plugin Response received");
//
//            // Parse the response
//            Map<String, Map<String, String>> parsedResults = parsePluginResponse(result);
//            logger.info("Parsed results for {} IPs", parsedResults.size());
//
//            // Process results (e.g., store in database, trigger alerts, etc.)
//            // This would typically be implemented based on your application's needs
//        } catch (Exception e) {
//            logger.error("Error while calling Go Plugin: {}", e.getMessage());
//            throw new RuntimeException(e);
//        }
//    }
//
//    /**
//     * Parse the response from the Go SNMP plugin
//     * @param response The JSON response from the plugin
//     * @return A map of IP addresses to their metric results
//     */
//    private static Map<String, Map<String, String>> parsePluginResponse(JsonObject response) {
//        Map<String, Map<String, String>> results = new HashMap<>();
//
//        if (response != null && response.containsKey("results")) {
//            JsonArray ipResults = response.getJsonArray("results");
//
//            for (int i = 0; i < ipResults.size(); i++) {
//                JsonObject ipResult = ipResults.getJsonObject(i);
//                String ip = ipResult.getString("ip");
//
//                if (ipResult.containsKey("results")) {
//                    JsonObject metricResults = ipResult.getJsonObject("results");
//                    Map<String, String> ipMetrics = new HashMap<>();
//
//                    for (String metricName : metricResults.fieldNames()) {
//                        String metricValue = metricResults.getString(metricName);
//                        ipMetrics.put(metricName, metricValue);
//                        logger.debug("Received metric result for {}: {}", metricName, metricValue);
//                    }
//
//                    results.put(ip, ipMetrics);
//                    logger.info("Processed {} metrics for IP {}", ipMetrics.size(), ip);
//                }
//            }
//        }
//
//        return results;
//    }
//
//    public static void stopPolling(Vertx vertx, long provisionId) {
//        if (activeTimers.remove(provisionId)) {
//            logger.info("Stopped polling for provisionId: {}", provisionId);
//        } else {
//            logger.warn("ProvisionId {} was not found in active timers", provisionId);
//        }
//    }
//}


package org.example.vertxDemo.Verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.vertxDemo.Utils.CallPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PollingEngine extends AbstractVerticle
{

    private static final Logger logger = LoggerFactory.getLogger(PollingEngine.class);
    private static final Set<Long> activeTimers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<Long, List<String>> metricsTimes = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastExecutionTime = new ConcurrentHashMap<>();
    private AtomicInteger count = new AtomicInteger();

    @Override
    public void start(Promise<Void> startPromise)
    {
        logger.info("PollingEngine verticle starting...");
        vertx.setPeriodic(1000, id ->
        {
            if (!metricsTimes.isEmpty())
            {
                var metricsToProcess = new ArrayList<String>();
                var tempMetricsTimes = new ConcurrentHashMap<Long, List<String>>();

                metricsTimes.forEach((timeInterval, metrics) ->
                {
                    var currentTime = System.currentTimeMillis();
                    var lastTime = lastExecutionTime.getOrDefault(timeInterval, 0L);

                    if (lastTime == 0 || (currentTime - lastTime) >= timeInterval * 1000)
                    {
                        logger.info("Polling triggered for interval {} seconds", timeInterval);
                        metricsToProcess.addAll(metrics);
                        lastExecutionTime.put(timeInterval, currentTime);
                    }

                    tempMetricsTimes.put(timeInterval, metrics);
                });

                metricsTimes.clear();
                metricsTimes.putAll(tempMetricsTimes);

                if (!metricsToProcess.isEmpty())
                {
                    // IMPORTANT: wrap blocking call in executeBlocking
                    vertx.executeBlocking(promise ->
                    {
                        handleCallGoPlugin(metricsToProcess);
                        promise.complete();
                        }
                        , res ->
                    {
                        if (res.failed())
                        {
                            logger.error("Error during Go plugin call", res.cause());
                        }
                    });
                }
            }
        });

        startPromise.complete();
    }

    public static void startPolling(long provisionId, JsonObject finalMetrics)
    {
        if (!activeTimers.add(provisionId))
        {
            logger.info("Already polling for provisionId: {}", provisionId);
            return;
        }

        logger.info("Starting polling for provisionId: {} with metrics: {}", provisionId, finalMetrics);

        for (String metricKey : finalMetrics.fieldNames())
        {
            Object value = finalMetrics.getValue(metricKey);
            Long timeInterval = parseMetricValue(value, metricKey);

            if (timeInterval != null)
            {
                metricsTimes.computeIfAbsent(timeInterval, k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(metricKey);

                logger.info("Added metric: {} with interval: {} seconds", metricKey, timeInterval);
            }
        }
    }

    private static Long parseMetricValue(Object value, String metric)
    {
        try
        {
            if (value instanceof Integer)
            {
                return ((Integer) value).longValue();
            }
            else if (value instanceof Long)
            {
                return (Long) value;
            }
            else if (value instanceof String)
            {
                return Long.parseLong((String) value);
            }
            else
            {
                logger.error("Unsupported value type for metric: {} - {}", metric, value.getClass().getName());
            }
        }
        catch (NumberFormatException e)
        {
            logger.error("Failed to parse metric value {} for metric {}", value, metric);
        }
        return null;
    }

    private void handleCallGoPlugin(List<String> metricsToProcess)
    {
        var goPluginRequestData = new JsonObject()
                .put("method_type", "SNMP_GET")
                .put("ips", new JsonArray())
                .put("credentials", new JsonArray());

        var snmpGetData = new JsonArray();
        var ipAddresses = new JsonArray();
        var uniqueCredentials = new HashMap<String, Set<String>>();

        for (String metricKey : metricsToProcess)
        {
            var parts = metricKey.split(",");

            if (parts.length >= 5)
            {
                var discoveryId = parts[0];
                var ip = parts[1];
                var port = parts[2];
                var communityString = parts[3];
                var snmpMetric = parts[4];

                try
                {
                    var discoveryIdInt = Integer.parseInt(discoveryId);
                    var portInt = Integer.parseInt(port);

                    var requestDetails = new JsonObject()
                            .put("ip", ip)
                            .put("discovery_id", discoveryIdInt)
                            .put("port", portInt)
                            .put("community_string", communityString)
                            .put("snmp_metric", snmpMetric);

                    snmpGetData.add(requestDetails);

                    if (!ipAddresses.contains(ip))
                    {
                        ipAddresses.add(ip);
                    }

                    uniqueCredentials.computeIfAbsent(ip, k -> new HashSet<>())
                            .add(communityString);

                    logger.debug("Added request for metric: {} ({}:{})", snmpMetric, ip, portInt);
                }
                catch (NumberFormatException e)
                {
                    logger.error("Failed to parse numeric values in metric key: {}", metricKey, e);
                }
            }
            else
            {
                logger.error("Invalid metric key format: {}", metricKey);
            }
        }

        var credentials = new JsonArray();
        var credentialId = 1;
        for (Map.Entry<String, Set<String>> entry : uniqueCredentials.entrySet())
        {
            for (String community : entry.getValue())
            {
                credentials.add(new JsonObject()
                        .put("id", credentialId++)
                        .put("system_type", "snmp")
                        .put("community", community));
            }
        }

        goPluginRequestData.put("snmp_get_data", snmpGetData);
        goPluginRequestData.put("ips", ipAddresses);
        goPluginRequestData.put("credentials", credentials);

        try
        {
            logger.debug("Calling Go SNMP plugin with request: {}", goPluginRequestData);
            var result = CallPlugin.callGoSnmpPlugin(goPluginRequestData);
            logger.info("Go Plugin Response received");

            var parsedResults = parsePluginResponse(result);
            logger.info("Parsed results for {} IPs", parsedResults.size());
        }
        catch (Exception e)
        {
            logger.error("Error while calling Go Plugin: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private Map<String, Map<String, String>> parsePluginResponse(JsonObject response)
    {
        var results = new HashMap<String, Map<String, String>>();

        if (response != null && response.containsKey("results"))
        {
            var ipResults = response.getJsonArray("results");

            for (var i = 0; i < ipResults.size(); i++)
            {
                var ipResult = ipResults.getJsonObject(i);
                var ip = ipResult.getString("ip");

                if (ipResult.containsKey("results"))
                {
                    var metricResults = ipResult.getJsonObject("results");
                    var ipMetrics = new HashMap<String, String>();

                    for (String metricName : metricResults.fieldNames())
                    {
                        var metricValue = metricResults.getString(metricName);
                        ipMetrics.put(metricName, metricValue);
                        logger.debug("Received metric result for {}: {}", metricName, metricValue);
                    }

                    results.put(ip, ipMetrics);
                    logger.info("Processed {} metrics for IP {}", ipMetrics.size(), ip);
                }
            }
        }

        return results;
    }

    public void stopPolling(long provisionId)
    {
        if (activeTimers.remove(provisionId))
        {
            logger.info("Stopped polling for provisionId: {}", provisionId);
        }
        else
        {
            logger.warn("ProvisionId {} was not found in active timers", provisionId);
        }
    }
}
