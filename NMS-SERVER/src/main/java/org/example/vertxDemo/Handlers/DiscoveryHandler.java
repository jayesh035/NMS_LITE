package org.example.vertxDemo.Handlers;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.example.vertxDemo.Database.DatabaseClient;
import org.example.vertxDemo.Utils.CallPlugin;
import org.example.vertxDemo.Utils.DBQueries;
import org.example.vertxDemo.Utils.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.example.vertxDemo.Utils.CallPlugin.callGoSnmpPlugin;


public class DiscoveryHandler
{

    private final Vertx vertx;
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryHandler.class);

    public DiscoveryHandler(JWTAuth jwtAuth,Vertx vertx)
    {
        this.vertx=vertx;
    }

    public void handleCreate(RoutingContext context)
    {
        var body = context.body().asJsonObject();

        // Validation
        if (body == null ||
                !body.containsKey("discoveryName") ||
                !body.containsKey("ipAddress") ||
                !body.containsKey("port") ||
                !body.containsKey("credentials_ids"))
        {
            context.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "discoveryName, ipAddress, port, and credentials_ids are required").encode());
            return;
        }

        var discoveryName = body.getString("discoveryName");
        var ipAddress = body.getString("ipAddress");
        var port = body.getInteger("port");
        var credentialsIds = body.getJsonArray("credentials_ids");


        if (credentialsIds == null || credentialsIds.isEmpty())
        {
            context.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "credentials_ids must be a non-empty array").encode());
            return;
        }

        // Insert into discovery table
        var insertDiscoveryQuery = DBQueries.createDiscovery;

        DatabaseClient.
                getPool().
                preparedQuery(insertDiscoveryQuery)
                .execute(Tuple.of(discoveryName, ipAddress, port), ar -> {
                    if (ar.succeeded())
                    {
                        var row = ar.result().iterator().next();
                        var discoveryId = row.getLong("id");

                        // Insert into discovery_credentials table
                        var junctionTuples = credentialsIds.stream()
                                .map(credId -> Tuple.of(discoveryId, ((Number)credId).longValue()))
                                .toList();

                        var insertJunction = DBQueries.createDiscoveryCredential;

                        DatabaseClient.
                                getPool().
                                preparedQuery(insertJunction).
                                executeBatch(junctionTuples, res -> {

                                    if (res.succeeded())
                                    {
                                        context.response()
                                                .setStatusCode(201)
                                                .putHeader("Content-Type", "application/json")
                                                .end(new JsonObject()
                                                        .put("message", "Discovery created successfully")
                                                        .put("discovery_id", discoveryId)
                                                        .encode());
                                    }
                                    else
                                    {
                                        logger.error("Failed to insert discovery_credentials: {}", res.cause().getMessage());
                                        context.response().setStatusCode(500)
                                                .end(new JsonObject().put("error", "Failed to insert discovery_credentials").encode());
                                    }
                                });
                    }
                    else
                    {
                        logger.error("Failed to insert discovery: {}", ar.cause().getMessage());
                        context.response().setStatusCode(500)
                                .end(new JsonObject().put("error", "Failed to insert discovery").encode());
                    }
                });
    }

    public void handleRead(RoutingContext context)
    {
        var idStr = context.pathParam("id");

        if (idStr == null || idStr.isEmpty())
        {
            context.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Missing discovery ID").encode());
            return;
        }

        long discoveryId;

        try
        {
            discoveryId = Long.parseLong(idStr);
        }
        catch (NumberFormatException e)
        {
            context.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Invalid discovery ID format").encode());
            return;
        }

        var query = DBQueries.getDiscoveryById;
        DatabaseClient.
                getPool().
                preparedQuery(query).
                execute(Tuple.of(discoveryId), ar -> {

            if (ar.succeeded())
            {
                var rows = ar.result();

                if (!rows.iterator().hasNext())
                {
                    context.response().setStatusCode(404)
                            .end(new JsonObject().put("error", "Discovery not found").encode());
                    return;
                }

                var row = rows.iterator().next();

                var discovery = new JsonObject()
                        .put("id", row.getLong("id"))
                        .put("ip_address", row.getString("ip_address"))
                        .put("port", row.getInteger("port"))
                        .put("created_at", row.getLocalDateTime("created_at").toString())
                        .put("discovery_name", row.getString("discovery_name"));

                fetchCredentials(discoveryId).onSuccess(credentials -> {

                    discovery.put("credentials", new JsonArray(credentials));
                    context.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(discovery.encode());

                }).onFailure(err ->
                {

                    context.response().setStatusCode(500)
                            .end(new JsonObject().put("error", "Failed to fetch credentials").encode());
                });

            }
            else
            {
                context.response().setStatusCode(500)
                        .end(new JsonObject().put("error", "Failed to retrieve discovery").encode());
            }
        });
    }


    public void handleList(RoutingContext context)
    {
        var query = DBQueries.getAllDiscovery;

        DatabaseClient.
                getPool().
                query(query).
                execute(ar -> {

            if (ar.succeeded())
            {
                var discoveries = new JsonArray();

                for (Row row : ar.result())
                {
                    var discovery = new JsonObject()
                            .put("id", row.getLong("id"))
                            .put("ip_address", row.getString("ip_address"))
                            .put("port", row.getInteger("port"))
                            .put("created_at", row.getLocalDateTime("created_at").toString()) // optional formatting
                            .put("discovery_name", row.getString("discovery_name"));
                    discoveries.add(discovery);
                }

                context.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(discoveries.encode());
            }
            else
            {
                context.response()
                        .setStatusCode(500)
                        .end(new JsonObject().put("error", "Failed to fetch discoveries").encode());
            }
        });
    }


    public void handleDelete(RoutingContext context)
    {
        var idParam = context.pathParam("id");

        if (idParam == null)
        {
            context.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Missing discovery ID").encode());
            return;
        }

        long discoveryId;
        try
        {
            discoveryId = Long.parseLong(idParam);
        }
        catch (NumberFormatException e)
        {
            context.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Invalid discovery ID format").encode());
            return;
        }

        var deleteQuery = "DELETE FROM discovery WHERE id = $1";

        DatabaseClient.getPool().preparedQuery(deleteQuery).execute(Tuple.of(discoveryId), ar ->
        {
            if (ar.succeeded())
            {
                if (ar.result().rowCount() == 0)
                {
                    context.response().setStatusCode(404)
                            .end(new JsonObject().put("error", "Discovery ID not found").encode());
                }
                else
                {
                    context.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("message", "Discovery deleted successfully").encode());
                }
            }
            else
            {
                context.response().setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                                .put("error", "Database error")
                                .put("details", ar.cause().getMessage())
                                .encode());
            }
        });
    }



    public void handleUpdate(RoutingContext context)
    {
        var idParam = context.pathParam("id");

        if (idParam == null)
        {
            context.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Missing discovery ID").encode());
            return;
        }

        long discoveryId;
        try
        {
            discoveryId = Long.parseLong(idParam);
        }
        catch (NumberFormatException e)
        {
            context.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Invalid discovery ID format").encode());
            return;
        }

        var body = context.body().asJsonObject();

        if (body == null || !body.containsKey("ip_address") || !body.containsKey("port"))
        {
            context.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Missing ip_address or port in body").encode());
            return;
        }

        var ipAddress = body.getString("ip_address");
        int port = body.getInteger("port");
        var discoveryName = body.getString("discovery_name", null); // optional

        var updateQuery = "UPDATE discovary SET ip_address = $1, port = $2, discovery_name = $3 WHERE id = $4";

        DatabaseClient.
                getPool().
                preparedQuery(updateQuery).
                execute(Tuple.of(ipAddress, port, discoveryName, discoveryId), ar ->
                {
                    if (ar.succeeded())
                    {
                        if (ar.result().rowCount() == 0)
                        {
                            context.response().setStatusCode(404)
                                    .end(new JsonObject().put("error", "Discovery ID not found").encode());
                        }
                        else
                        {
                            context.response().setStatusCode(200)
                                    .end(new JsonObject().put("message", "Discovery updated successfully").encode());
                        }
                    }
                    else
                    {
                        context.response().setStatusCode(500)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject()
                                        .put("error", "Database error")
                                        .put("details", ar.cause().getMessage())
                                        .encode());
                    }
                }
        );
    }


//    public void handleRunDiscovery(RoutingContext context)
//    {
//        var discoveryIdStr = context.request().getParam("id");
//
//        if (discoveryIdStr == null || discoveryIdStr.isEmpty())
//        {
//            context.response().setStatusCode(400).end(new JsonObject().put("error", "Missing discovery ID").encode());
//            return;
//        }
//
//        long discoveryId;
//        try
//        {
//            discoveryId = Long.parseLong(discoveryIdStr);
//        }
//        catch (NumberFormatException e)
//        {
//            context.response().setStatusCode(400).end(new JsonObject().put("error", "Invalid discovery ID format").encode());
//            return;
//        }
//
//        var query = DBQueries.getDiscoveryById;
//
//        DatabaseClient.
//                getPool().
//                preparedQuery(query).
//                execute(Tuple.of(discoveryId), ar -> {
//
//            if (ar.succeeded())
//            {
//                Row row = ar.result().iterator().next();
//
//                if (row == null)
//                {
//                    context.response().setStatusCode(404).end(new JsonObject().put("error", "Discovery not found").encode());
//                    return;
//                }
//
//                var ipAddress = row.getString("ip_address");
//                Integer port = row.getInteger("port");
//
//                var credentialsResult=fetchCredentials(discoveryId);
//
//
//                List<String> ipList = IpUtils.extractIpList(ipAddress); // assumes helper method exists
//
//
//                List<Future> ipFutures = new ArrayList<>();
//
//
//                    for (var ip : ipList)
//                    {
//
//                        Promise<JsonObject> ipPromise = Promise.promise();
//                        ipFutures.add(ipPromise.future());
//
//                        var requestPayload = new JsonObject()
//                                .put("ip", ip)
//                                .put("port", port);
//
//                        vertx.eventBus().
//                                request("network.check", requestPayload,new DeliveryOptions().setSendTimeout(120000), ar1 -> {
//
//                            if (ar1.succeeded())
//                            {
//                                var result = (JsonObject) ar1.result().body();
//                                ipPromise.complete(result);
//                            }
//                            else
//                            {
////                                ar1.cause().toString();
//                                logger.error(ar1.cause().getMessage());
//                                ipPromise.complete(new JsonObject().put("ip", ip).put("error", "Processing failed"));
//                            }
//                        });
//                    }
//
//                    CompositeFuture.
//                            all(ipFutures).
//                            onComplete(ar2 -> {
//                        if (ar2.succeeded())
//                        {
//                            JsonArray resultArray = new JsonArray();
//
//                            JsonArray reachableIps = new JsonArray();
//
//                            for (Future f : ipFutures)
//                            {
//                                var res = (JsonObject) f.result();
//                                if (res.getBoolean("is_ip_reachable", false) &&
//                                        res.getBoolean("is_port_open", false))
//                                {
//                                    reachableIps.add( res.getString("ip"));
//                                }
//                                else
//                                {
////                                    resultArray.add();
//
////                                    var isIpReachable=res.getBoolean("is_ip_reachable", false)?"Ip reachable: True /n":"Ip reachable: False /n";
////                                    var isPortOpen=res.getBoolean("is_port_open", false)?"port reachable : True /n":"Port reachable: False /n";
//
//                                    res.put("status", "Network Error Occured ");
////                                    insertDiscoveryResult(discoveryId,res.getString("ip"), 0,
////                                    null, false, isIpReachable+isPortOpen+"Network Error Occured");
//                                    resultArray.add(res);
//                                }
//
//
////                                resultArray.add(res);
//                            }
//
//                            credentialsResult.onSuccess(credentials -> {
//                                // Call Go plugin for SNMP checks with each IP + credential combination
//                                var pluginInput = new JsonObject()
//                                        .put("ips", reachableIps)
//                                        .put("credentials", credentials);
//
//                                // Execute the Go plugin via JNI or process execution
//                                vertx.executeBlocking(promise -> {
//
//                                    try {
//                                        // Call the Go plugin and get results
//                                        var pluginResults = callGoSnmpPlugin(pluginInput);
//                                        promise.complete(pluginResults);
//                                    }
//                                    catch (Exception e)
//                                    {
//                                        promise.fail(e);
//                                    }
//                                }
//                                , resultHandler ->
//                                        {
//                                    if (resultHandler.succeeded())
//                                    {
//                                        var snmpResults = (JsonObject) resultHandler.result();
//
//                                        JsonArray snmpResultsArray= snmpResults.getJsonArray("results");
//
//                                        if(snmpResultsArray != null)
//                                        {
//                                            for(int i=0;i<snmpResultsArray.size() ; i++)
//                                            {
//                                                var obj=snmpResultsArray.getJsonObject(i);
//                                                resultArray.add(obj.put("is_ip_reachable", true).put("is_port_open",true));
//                                            }
//                                        }
//
//
//                                        for (int i = 0; i < resultArray.size(); i++)
//                                        {
//                                            var obj = resultArray.getJsonObject(i);
//
//                                            var ip = obj.getString("ip");
//                                            boolean isIpReachable = obj.getBoolean("is_ip_reachable", false);
//                                            boolean isPortOpen = obj.getBoolean("is_port_open", false);
//
//                                            if (isIpReachable && isPortOpen && obj.containsKey("credentials"))
//                                            {
//                                                JsonArray creds = obj.getJsonArray("credentials");
//
//                                                for (int j = 0; j < creds.size(); j++)
//                                                {
//                                                    var credResult = creds.getJsonObject(j);
//                                                    Integer credentialId = credResult.getInteger("credential_id");
//                                                    boolean success = credResult.getBoolean("success", false);
//                                                    var response = success ? "SNMP Success" : credResult.getString("error_message", "Unknown SNMP error");
//
//                                                    insertDiscoveryResult(discoveryId, ip, port, credentialId, success, response);
//                                                }
//                                            }
//                                            else
//                                            {
//                                                // Network error case — insert with `status = false`, and describe the error in `response`
//                                                var response = "Network error: " +
//                                                        "IP reachable = " + isIpReachable + ", " +
//                                                        "Port open = " + isPortOpen;
//
//                                                insertDiscoveryResult(discoveryId, ip, port, null, false, response);
//                                            }
//                                        }
//
//
////                                        resultArray.add(snmpResults);
//
//
//
//
//
//                                        // Return the final response with discovery results and SNMP check results
//                                        context.response()
//                                                .setStatusCode(200)
//                                                .putHeader("Content-Type", "application/json")
//                                                .end(new JsonObject()
//                                                        .put("discovery_id", discoveryId)
//                                                        .put("results",resultArray)
//                                                        .encode());
//                                    }
//                                    else
//                                    {
//                                        context.response().setStatusCode(500)
//                                                .end(new JsonObject().put("error", "Failed to execute SNMP checks: " +
//                                                        resultHandler.cause().getMessage()).encode());
//                                    }
//                                });
//                            }).onFailure(err -> {
//
//                                context.response().setStatusCode(500)
//                                        .end(new JsonObject().put("error", "Failed to fetch credentials").encode());
//                            });
//
//
//
//
//                        }
//                        else
//                        {
//                            context.response().setStatusCode(500)
//                                    .end(new JsonObject().put("error", "Failed to process all IPs").encode());
//                        }
//                    });
//            }
//            else
//            {
//                context.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to retrieve discovery").encode());
//            }
//        });
//    }
//


        public void handleRunDiscovery(RoutingContext context)
        {
            var discoveryIdStr = context.request().getParam("id");

            if (discoveryIdStr == null || discoveryIdStr.isEmpty())
            {
                context.response().setStatusCode(400)
                        .end(new JsonObject().put("error", "Missing discovery ID").encode());
                return;
            }

            long discoveryId;
            try
            {
                discoveryId = Long.parseLong(discoveryIdStr);
            }
            catch (NumberFormatException e)
            {
                context.response().setStatusCode(400)
                        .end(new JsonObject().put("error", "Invalid discovery ID format").encode());
                return;
            }

            var query = DBQueries.getDiscoveryById;

            DatabaseClient.getPool()
                    .preparedQuery(query)
                    .execute(Tuple.of(discoveryId), ar ->
                    {

                        if (ar.succeeded())
                        {
                            RowSet<Row> rows = ar.result();

                            if (!rows.iterator().hasNext())
                            {
                                context.response().setStatusCode(404)
                                        .end(new JsonObject().put("error", "Discovery not found").encode());
                                return;
                            }

                            var row = rows.iterator().next();
                            var ipAddress = row.getString("ip_address");
                            var port = row.getInteger("port");

                            var credentialsResult = fetchCredentials(discoveryId);
                            var ipList = IpUtils.extractIpList(ipAddress);

                            logger.info("Discovered IP List: {}", ipList);
                            logger.info("Port: {}", port);

                            var ipFutures = new ArrayList<Future>();

                            for (var ip : ipList) {
                                var ipPromise = Promise.<JsonObject>promise();
                                ipFutures.add(ipPromise.future());

                                var requestPayload = new JsonObject()
                                        .put("ip", ip)
                                        .put("port", port);

                                // Register EventBus listener
                                vertx.eventBus().request("network.check", requestPayload, new DeliveryOptions().setSendTimeout(120000), ar1 ->
                                {
                                    if (ar1.succeeded())
                                    {
                                        var result = (JsonObject) ar1.result().body();
                                        ipPromise.complete(result);
                                    }
                                    else
                                    {
                                        logger.error("Network check failed for IP {}: {}", ip, ar1.cause().getMessage());
                                        ipPromise.complete(new JsonObject().put("ip", ip).put("error", "Processing failed"));
                                    }
                                });
                            }

                            CompositeFuture.all(ipFutures).onComplete(ar2 ->
                            {
                                if (ar2.succeeded())
                                {
                                    var resultArray = new JsonArray();
                                    var reachableIps = new JsonArray();

                                    for (Future f : ipFutures)
                                    {
                                        var res = (JsonObject) f.result();
                                        if (res.getBoolean("is_ip_reachable", false) && res.getBoolean("is_port_open", false))
                                        {
                                            reachableIps.add(res.getString("ip"));
                                        }
                                        else
                                        {
                                            res.put("status", "Network Error Occurred");
                                            resultArray.add(res);
                                        }
                                    }

                                    credentialsResult.onSuccess(credentials ->
                                    {
                                        var pluginInput = new JsonObject()
                                                .put("method_type", "SNMP_CHECK")
                                                .put("ips", reachableIps)
                                                .put("credentials", credentials)
                                                .put("snmp_get_data", new JsonArray());

                                        vertx.executeBlocking(promise ->
                                        {
                                            try
                                            {
                                                var pluginResults = CallPlugin.callGoSnmpPlugin(pluginInput);
                                                promise.complete(pluginResults);
                                            }
                                            catch (Exception e)
                                            {
                                                logger.error("SNMP Plugin Call Failed: {}", e.getMessage());
                                                e.printStackTrace();
                                                promise.fail(e);
                                            }
                                        }, resultHandler ->
                                        {
                                            if (resultHandler.succeeded())
                                            {
                                                var snmpResults = (JsonObject) resultHandler.result();
                                                var snmpResultsArray = snmpResults.getJsonArray("results");

                                                if (snmpResultsArray != null)
                                                {
                                                    for (var i = 0; i < snmpResultsArray.size(); i++)
                                                    {
                                                        var obj = snmpResultsArray.getJsonObject(i);
                                                        resultArray.add(obj.put("is_ip_reachable", true).put("is_port_open", true));
                                                    }
                                                }

                                                context.response()
                                                        .setStatusCode(200)
                                                        .putHeader("Content-Type", "application/json")
                                                        .end(new JsonObject()
                                                                .put("discovery_id", discoveryId)
                                                                .put("results", resultArray)
                                                                .encode());
                                            }
                                            else
                                            {
                                                logger.error("Failed to execute SNMP checks: {}", resultHandler.cause().getMessage());
                                                context.response().setStatusCode(500)
                                                        .end(new JsonObject().put("error", "Failed to execute SNMP checks: " +
                                                                resultHandler.cause().getMessage()).encode());
                                            }
                                        });
                                    }).onFailure(err ->
                                    {
                                        logger.error("Failed to fetch credentials: {}", err.getMessage());
                                        context.response().setStatusCode(500)
                                                .end(new JsonObject().put("error", "Failed to fetch credentials").encode());
                                    });
                                }
                                else
                                {
                                    logger.error("Failed to process all IPs: {}", ar2.cause().getMessage());
                                    context.response().setStatusCode(500)
                                            .end(new JsonObject().put("error", "Failed to process all IPs").encode());
                                }
                            });

                        }
                        else
                        {
                            logger.error("Failed to retrieve discovery: {}", ar.cause().getMessage());
                            context.response().setStatusCode(500)
                                    .end(new JsonObject().put("error", "Failed to retrieve discovery").encode());
                        }
                    });
        }


    private Future<List<JsonObject>> fetchCredentials(long discoveryId)
    {
        var promise = Promise.<List<JsonObject>>promise();

        var query = DBQueries.fetchCredentialsQuery;

        DatabaseClient.getPool().preparedQuery(query)
                .execute(Tuple.of(discoveryId), ar ->
                {

                    if (ar.succeeded())
                    {
                        var credentials = new ArrayList<JsonObject>();
                        for (Row row : ar.result())
                        {
                            var dataStr = row.getString("data");
                            var data = dataStr != null ? new JsonObject(dataStr) : null;
                            var community = data != null ? data.getString("community") : null;

                            var cred = new JsonObject()
                                    .put("id", row.getLong("id"))
                                    .put("system_type", row.getString("systemtype"))
                                    .put("community", community);
                            credentials.add(cred);
                        }
                        promise.complete(credentials);
                    }
                    else
                    {
                        promise.fail(ar.cause());
                    }
                });

        return promise.future();
    }


    private void insertDiscoveryResult(long discoveryId, String ip, int port,
                                       Integer credentialId, boolean status, String response)
    {
        var insertQuery = DBQueries.createDiscovaryResult;

        var params = Tuple.of(discoveryId, credentialId, status, response,ip);

        DatabaseClient.
                getPool().
                preparedQuery(insertQuery).
                execute(params, ar -> {

            if (ar.failed())
            {
                System.err.println("Failed to insert discovery result for " + ip + ": " + ar.cause().getMessage());
            }
        });
    }
}
