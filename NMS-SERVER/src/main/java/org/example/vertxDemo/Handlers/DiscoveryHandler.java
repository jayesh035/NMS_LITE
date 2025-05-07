package org.example.vertxDemo.Handlers;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.example.vertxDemo.Database.DatabaseClient;
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
        JsonObject body = context.body().asJsonObject();

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

        String discoveryName = body.getString("discoveryName");
        String ipAddress = body.getString("ipAddress");
        Integer port = body.getInteger("port");
        JsonArray credentialsIds = body.getJsonArray("credentials_ids");


        if (credentialsIds == null || credentialsIds.isEmpty())
        {
            context.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "credentials_ids must be a non-empty array").encode());
            return;
        }

        // Insert into discovery table
        String insertDiscoveryQuery = DBQueries.createDiscovery;

        DatabaseClient.
                getPool().
                preparedQuery(insertDiscoveryQuery)
                .execute(Tuple.of(discoveryName, ipAddress, port), ar -> {
                    if (ar.succeeded())
                    {
                        Row row = ar.result().iterator().next();
                        Long discoveryId = row.getLong("id");

                        // Insert into discovery_credentials table
                        List<Tuple> junctionTuples = credentialsIds.stream()
                                .map(credId -> Tuple.of(discoveryId, ((Number)credId).longValue()))
                                .toList();

                        String insertJunction = DBQueries.createDiscoveryCredential;

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
        String idStr = context.pathParam("id");

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

        String query = DBQueries.getDiscoveryById;
        DatabaseClient.
                getPool().
                preparedQuery(query).
                execute(Tuple.of(discoveryId), ar -> {

            if (ar.succeeded())
            {
                RowSet<Row> rows = ar.result();

                if (!rows.iterator().hasNext())
                {
                    context.response().setStatusCode(404)
                            .end(new JsonObject().put("error", "Discovery not found").encode());
                    return;
                }

                Row row = rows.iterator().next();

                JsonObject discovery = new JsonObject()
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

                }).onFailure(err -> {

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
        String query = DBQueries.getAllDiscovery;

        DatabaseClient.
                getPool().
                query(query).
                execute(ar -> {

            if (ar.succeeded())
            {
                JsonArray discoveries = new JsonArray();

                for (Row row : ar.result())
                {
                    JsonObject discovery = new JsonObject()
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


    public void handleDelete(RoutingContext context) {
        String idParam = context.pathParam("id");

        if (idParam == null) {
            context.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Missing discovery ID").encode());
            return;
        }

        long discoveryId;
        try {
            discoveryId = Long.parseLong(idParam);
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Invalid discovery ID format").encode());
            return;
        }

        String deleteQuery = "DELETE FROM discovery WHERE id = $1";

        DatabaseClient.getPool().preparedQuery(deleteQuery).execute(Tuple.of(discoveryId), ar -> {
            if (ar.succeeded()) {
                if (ar.result().rowCount() == 0) {
                    context.response().setStatusCode(404)
                            .end(new JsonObject().put("error", "Discovery ID not found").encode());
                } else {
                    context.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("message", "Discovery deleted successfully").encode());
                }
            } else {
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
        String idParam = context.pathParam("id");

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

        JsonObject body = context.body().asJsonObject();

        if (body == null || !body.containsKey("ip_address") || !body.containsKey("port"))
        {
            context.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Missing ip_address or port in body").encode());
            return;
        }

        String ipAddress = body.getString("ip_address");
        int port = body.getInteger("port");
        String discoveryName = body.getString("discovery_name", null); // optional

        String updateQuery = "UPDATE discovary SET ip_address = $1, port = $2, discovery_name = $3 WHERE id = $4";

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


    public void handleRunDiscovery(RoutingContext context)
    {
        String discoveryIdStr = context.request().getParam("id");

        if (discoveryIdStr == null || discoveryIdStr.isEmpty())
        {
            context.response().setStatusCode(400).end(new JsonObject().put("error", "Missing discovery ID").encode());
            return;
        }

        long discoveryId;
        try
        {
            discoveryId = Long.parseLong(discoveryIdStr);
        }
        catch (NumberFormatException e)
        {
            context.response().setStatusCode(400).end(new JsonObject().put("error", "Invalid discovery ID format").encode());
            return;
        }

        String query = DBQueries.getDiscoveryById;

        DatabaseClient.
                getPool().
                preparedQuery(query).
                execute(Tuple.of(discoveryId), ar -> {

            if (ar.succeeded())
            {
                Row row = ar.result().iterator().next();

                if (row == null)
                {
                    context.response().setStatusCode(404).end(new JsonObject().put("error", "Discovery not found").encode());
                    return;
                }

                String ipAddress = row.getString("ip_address");
                Integer port = row.getInteger("port");

                var credentialsResult=fetchCredentials(discoveryId);


                List<String> ipList = IpUtils.extractIpList(ipAddress); // assumes helper method exists


                List<Future> ipFutures = new ArrayList<>();


                    for (String ip : ipList)
                    {

                        Promise<JsonObject> ipPromise = Promise.promise();
                        ipFutures.add(ipPromise.future());

                        JsonObject requestPayload = new JsonObject()
                                .put("ip", ip)
                                .put("port", port);

                        vertx.eventBus().
                                request("network.check", requestPayload, ar1 -> {

                            if (ar1.succeeded())
                            {
                                JsonObject result = (JsonObject) ar1.result().body();
                                ipPromise.complete(result);
                            }
                            else
                            {
//                                ar1.cause().toString();
                                logger.error(ar1.cause().getMessage());
                                ipPromise.complete(new JsonObject().put("ip", ip).put("error", "Processing failed"));
                            }
                        });
                    }

                    CompositeFuture.
                            all(ipFutures).
                            onComplete(ar2 -> {
                        if (ar2.succeeded())
                        {
                            JsonArray resultArray = new JsonArray();

                            JsonArray reachableIps = new JsonArray();

                            for (Future f : ipFutures)
                            {
                                JsonObject res = (JsonObject) f.result();
                                if (res.getBoolean("is_ip_reachable", false) &&
                                        res.getBoolean("is_port_open", false))
                                {
                                    reachableIps.add( res.getString("ip"));
                                }
                                else
                                {
//                                    resultArray.add();

//                                    String isIpReachable=res.getBoolean("is_ip_reachable", false)?"Ip reachable: True /n":"Ip reachable: False /n";
//                                    String isPortOpen=res.getBoolean("is_port_open", false)?"port reachable : True /n":"Port reachable: False /n";

                                    res.put("status", "Network Error Occured ");
//                                    insertDiscoveryResult(discoveryId,res.getString("ip"), 0,
//                                    null, false, isIpReachable+isPortOpen+"Network Error Occured");
                                    resultArray.add(res);
                                }


//                                resultArray.add(res);
                            }

                            credentialsResult.onSuccess(credentials -> {
                                // Call Go plugin for SNMP checks with each IP + credential combination
                                JsonObject pluginInput = new JsonObject()
                                        .put("ips", reachableIps)
                                        .put("credentials", credentials);

                                // Execute the Go plugin via JNI or process execution
                                vertx.executeBlocking(promise -> {

                                    try {
                                        // Call the Go plugin and get results
                                        JsonObject pluginResults = callGoSnmpPlugin(pluginInput);
                                        promise.complete(pluginResults);
                                    }
                                    catch (Exception e)
                                    {
                                        promise.fail(e);
                                    }
                                }
                                , resultHandler ->
                                        {
                                    if (resultHandler.succeeded())
                                    {
                                        JsonObject snmpResults = (JsonObject) resultHandler.result();

                                        JsonArray snmpResultsArray= snmpResults.getJsonArray("results");

                                        if(snmpResultsArray != null)
                                        {
                                            for(int i=0;i<snmpResultsArray.size() ; i++)
                                            {
                                                JsonObject obj=snmpResultsArray.getJsonObject(i);
                                                resultArray.add(obj.put("is_ip_reachable", true).put("is_port_open",true));
                                            }
                                        }


                                        for (int i = 0; i < resultArray.size(); i++)
                                        {
                                            JsonObject obj = resultArray.getJsonObject(i);

                                            String ip = obj.getString("ip");
                                            boolean isIpReachable = obj.getBoolean("is_ip_reachable", false);
                                            boolean isPortOpen = obj.getBoolean("is_port_open", false);

                                            if (isIpReachable && isPortOpen && obj.containsKey("credentials"))
                                            {
                                                JsonArray creds = obj.getJsonArray("credentials");

                                                for (int j = 0; j < creds.size(); j++)
                                                {
                                                    JsonObject credResult = creds.getJsonObject(j);
                                                    Integer credentialId = credResult.getInteger("credential_id");
                                                    boolean success = credResult.getBoolean("success", false);
                                                    String response = success ? "SNMP Success" : credResult.getString("error_message", "Unknown SNMP error");

                                                    insertDiscoveryResult(discoveryId, ip, port, credentialId, success, response);
                                                }
                                            }
                                            else
                                            {
                                                // Network error case — insert with `status = false`, and describe the error in `response`
                                                String response = "Network error: " +
                                                        "IP reachable = " + isIpReachable + ", " +
                                                        "Port open = " + isPortOpen;

                                                insertDiscoveryResult(discoveryId, ip, port, null, false, response);
                                            }
                                        }


//                                        resultArray.add(snmpResults);





                                        // Return the final response with discovery results and SNMP check results
                                        context.response()
                                                .setStatusCode(200)
                                                .putHeader("Content-Type", "application/json")
                                                .end(new JsonObject()
                                                        .put("discovery_id", discoveryId)
                                                        .put("results",resultArray)
                                                        .encode());
                                    }
                                    else
                                    {
                                        context.response().setStatusCode(500)
                                                .end(new JsonObject().put("error", "Failed to execute SNMP checks: " +
                                                        resultHandler.cause().getMessage()).encode());
                                    }
                                });
                            }).onFailure(err -> {

                                context.response().setStatusCode(500)
                                        .end(new JsonObject().put("error", "Failed to fetch credentials").encode());
                            });




                        }
                        else
                        {
                            context.response().setStatusCode(500)
                                    .end(new JsonObject().put("error", "Failed to process all IPs").encode());
                        }
                    });
            }
            else
            {
                context.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to retrieve discovery").encode());
            }
        });
    }




    private Future<List<JsonObject>> fetchCredentials(long discoveryId)
    {
        Promise<List<JsonObject>> promise = Promise.promise();

        String query = DBQueries.fetchCredentialsQuery;

        DatabaseClient.getPool().preparedQuery(query)
                .execute(Tuple.of(discoveryId), ar -> {

                    if (ar.succeeded())
                    {
                        List<JsonObject> credentials = new ArrayList<>();
                        for (Row row : ar.result())
                        {
                            String dataStr = row.getString("data");
                            JsonObject data = dataStr != null ? new JsonObject(dataStr) : null;
                            String community = data != null ? data.getString("community") : null;

                            JsonObject cred = new JsonObject()
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
        String insertQuery = DBQueries.createDiscovaryResult;

        Tuple params = Tuple.of(discoveryId, credentialId, status, response,ip);

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
