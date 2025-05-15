//package org.example.vertxDemo.Verticles;
//import io.vertx.core.AbstractVerticle;
//import io.vertx.core.json.JsonObject;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import java.io.IOException;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class NetworkChecker extends AbstractVerticle {
//    private static final Logger logger = LoggerFactory.getLogger(NetworkChecker.class);
//    private static final ExecutorService executor = Executors.newFixedThreadPool(10);
//
//    @Override
//    public void start() {
//        vertx.eventBus().consumer("network.check", msg -> {
//            var data = (JsonObject) msg.body();
//            var ip = data.getString("ip");
//            var port = data.getInteger("port");
//
//            var hostReachableFuture = CompletableFuture.supplyAsync(() -> isHostReachable(ip), executor);
//            var portOpenFuture = hostReachableFuture.thenApplyAsync(reachable ->
//            {
//                if (reachable)
//                {
//                    return isPortOpen(ip, port);
//                }
//                return false;
//            }, executor);
//
//            // Combine results
//            portOpenFuture.thenAcceptBoth(hostReachableFuture, (isPortOpen, isReachable) ->
//            {
//                var result = new JsonObject()
//                        .put("ip", ip)
//                        .put("is_ip_reachable", isReachable)
//                        .put("is_port_open", isPortOpen);
//
//                msg.reply(result);
//            }).exceptionally(ex ->
//            {
//                logger.error("Error during network check: {}", ex.getMessage());
//                JsonObject errorResult = new JsonObject()
//                        .put("ip", ip)
//                        .put("error", "Failed to check network status: " + ex.getMessage());
//                msg.reply(errorResult);
//                return null;
//            });
//        });
//    }
//
//    public static boolean isHostReachable(String ipAddress)
//    {
//        try
//        {
//            String os = System.getProperty("os.name").toLowerCase();
//            ProcessBuilder pb;
//
//            if (os.contains("win"))
//            {
//                pb = new ProcessBuilder("ping", "-n", "1", "-w", "1000", ipAddress);
//            }
//            else
//            {
//                pb = new ProcessBuilder("ping", "-c", "1", "-W", "1", ipAddress);
//            }
//
//            Process process = pb.start();
//            int returnCode = process.waitFor();
//            return returnCode == 0;
//        }
//        catch (IOException | InterruptedException e)
//        {
//            logger.error("Ping failed for IP: {}, Reason: {}", ipAddress, e.getMessage());
//            return false;
//        }
//    }
//
//    public static boolean isPortOpen(String ipAddress, int port)
//    {
//        try
//        {
//            String command = String.format("nc -zvu %s %d", ipAddress, port);
//            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
//            pb.redirectErrorStream(true);
//            Process process = pb.start();
//            int exitCode = process.waitFor();
//            return exitCode == 0;
//        }
//        catch (IOException | InterruptedException e)
//        {
//            logger.error("Port check failed for IP: {}, Port: {}, Reason: {}", ipAddress, port, e.getMessage());
//            return false;
//        }
//    }
//}
package org.example.vertxDemo.Verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkChecker extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(NetworkChecker.class);

    @Override
    public void start() {
        vertx.eventBus().consumer("network.check", msg ->
        {
            var data = (JsonObject) msg.body();
            var ip = data.getString("ip");
            var port = data.getInteger("port");

            if (ip == null || port == null)
            {
                msg.fail(400, "IP and port must be provided");
                return;
            }

            executeHostReachable(ip)
                    .compose(isReachable ->
                    {
                        if (isReachable)
                        {
                            return executePortOpen(ip, port)
                                    .map(isPortOpen -> new JsonObject()
                                            .put("ip", ip)
                                            .put("is_ip_reachable", true)
                                            .put("is_port_open", isPortOpen));
                        }
                        else
                        {
                            return Future.succeededFuture(new JsonObject()
                                    .put("ip", ip)
                                    .put("is_ip_reachable", false)
                                    .put("is_port_open", false));
                        }
                    })
                    .onSuccess(result -> msg.reply(result))
                    .onFailure(err ->
                    {
                        logger.error("Error during network check: {}", err.getMessage());
                        JsonObject errorResult = new JsonObject()
                                .put("ip", ip)
                                .put("error", "Failed to check network status: " + err.getMessage());
                        msg.reply(errorResult);
                    });
        });
    }

    private Future<Boolean> executeHostReachable(String ipAddress)
    {
        var promise = Promise.<Boolean>promise();
        vertx.executeBlocking(promiseFuture ->
        {
            var reachable = isHostReachable(ipAddress);
            promiseFuture.complete(reachable);
        }, false, promise);

        return promise.future();
    }

    private Future<Boolean> executePortOpen(String ipAddress, int port)
    {
        var promise = Promise.<Boolean>promise();
        vertx.executeBlocking(promiseFuture ->
        {
            var portOpen = isPortOpen(ipAddress, port);
            promiseFuture.complete(portOpen);
        }, false, promise);

        return promise.future();
    }

    private boolean isHostReachable(String ipAddress)
    {
        try
        {
            var os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win"))
            {
                pb = new ProcessBuilder("ping", "-n", "1", "-w", "1000", ipAddress);
            }
            else
            {
                pb = new ProcessBuilder("ping", "-c", "1", "-W", "1", ipAddress);
            }

            var process = pb.start();
            var returnCode = process.waitFor();
            return returnCode == 0;
        }
        catch (Exception e)
        {
            logger.error("Ping failed for IP: {}, Reason: {}", ipAddress, e.getMessage());
            return false;
        }
    }

    private boolean isPortOpen(String ipAddress, int port)
    {
        try
        {
            var command = String.format("nc -zvu %s %d", ipAddress, port);
            var pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true);
            var process = pb.start();
            var exitCode = process.waitFor();

            return exitCode == 0;
        }
        catch (Exception e)
        {
            logger.error("Port check failed for IP: {}, Port: {}, Reason: {}", ipAddress, port, e.getMessage());
            return false;
        }
    }
}
