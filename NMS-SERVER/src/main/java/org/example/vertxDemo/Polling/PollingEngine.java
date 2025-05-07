package org.example.vertxDemo.Polling;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.example.vertxDemo.Utils.CallPlugin.callGoSnmpPlugin;

public class PollingEngine {

    private static final Map<Long, Map<Integer, Long>> activeTimers = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(PollingEngine.class);

    public static void startPolling(Vertx vertx, long provisionId, String ipAddress, JsonObject metrics)
    {
        if (activeTimers.containsKey(provisionId))
        {
            logger.info("Already polling for provisionId: {}", provisionId);
            return;
        }

        Map<Integer, Set<String>> intervalToMetrics = new HashMap<>();

        for (String metric : metrics.fieldNames())
        {
            try
            {
                int interval = metrics.getInteger(metric);
                intervalToMetrics.computeIfAbsent(interval, k -> new HashSet<>()).add(metric);
            }
            catch (ClassCastException e)
            {
                logger.error("Invalid interval for metric {}: {}", metric, metrics.getValue(metric));
            }
        }

        Map<Integer, Long> timers = new HashMap<>();

        for (Map.Entry<Integer, Set<String>> entry : intervalToMetrics.entrySet()) {
            int interval = entry.getKey();
            Set<String> metricGroup = entry.getValue();

            long timerId = vertx.setPeriodic(interval * 1000L, id ->
            {
                vertx.executeBlocking(promise -> {
                    try
                    {
                        JsonObject pluginInput = new JsonObject()
                                .put("ip", ipAddress)
                                .put("metrics", new ArrayList<>(metricGroup));  // batch metrics

                        JsonObject pluginResults = callGoSnmpPlugin(pluginInput);
                        promise.complete(pluginResults);
                    }
                    catch (Exception e)
                    {
                        promise.fail(e);
                    }
                }, asyncResult -> {

                    if (asyncResult.succeeded())
                    {
                        // handle successful result
                    }
                    else
                    {
                        logger.error("Polling failed for provisionId {}: {}", provisionId, asyncResult.cause().getMessage());
                    }
                });
            });

            timers.put(interval, timerId);
        }

        activeTimers.put(provisionId, timers);
    }

    public static void stopPolling(Vertx vertx, long provisionId)
    {
        Map<Integer, Long> timers = activeTimers.remove(provisionId);
        if (timers != null)
        {
            timers.values().forEach(vertx::cancelTimer);
            logger.info("Stopped polling for provisionId: {}", provisionId);
        }
    }
}
