package org.example.vertxDemo.Handlers;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.example.vertxDemo.Database.DatabaseClient;
import org.example.vertxDemo.Polling.PollingEngine;

public class ProvisioningHandler
{
    public  final  Vertx vertx;

    public ProvisioningHandler(JWTAuth jwtAuth, Vertx vertx)
    {
        this.vertx=vertx;
    }


    public void handleProvision(RoutingContext context)
    {
        JsonObject body = context.body().asJsonObject();

        // Check if required fields are present
        if (body == null || !body.containsKey("ip_address") || !body.containsKey("discovary_id"))
        {
            context.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Missing ip_address or discovary_id").encode());
            return;
        }

        String ipAddress = body.getString("ip_address");
        Long discovaryId = body.getLong("discovary_id");

        // Query to fetch the discovery result based on ip_address and discovary_id
        String query = "SELECT * FROM discovery_result WHERE ip_address = $1 AND discovary_id = $2 AND status = true";

        DatabaseClient.
                getPool().
                preparedQuery(query).
                execute(Tuple.of(ipAddress, discovaryId), ar ->
        {
            if (ar.succeeded())
            {
                var resultSet = ar.result();
                if (resultSet.rowCount() == 0)
                {
                    context.response().setStatusCode(404)
                            .end(new JsonObject().put("error", "No discovery result found").encode());
                    return;
                }

                var row = resultSet.iterator().next();
                int credentialId = row.getInteger("credential_id");

                // Fetch SNMP metrics (directly associated with credential_id or some other logic to define metrics)
                JsonObject metrics = new JsonObject();
                // Example: Adding some metrics directly
                metrics.put("sysName", 3);        // sysName with a polling interval of 60 seconds
                metrics.put("sysLocation", 4);   // sysLocation with a polling interval of 120 seconds
                metrics.put("sysDescr", 5);       // sysDescr with a polling interval of 90 seconds

                // Check if the provision record already exists for this ip_address and discovary_id
                String checkProvisionQuery = "SELECT * FROM provision WHERE ip_address = $1 AND discovary_id = $2";

                DatabaseClient.
                        getPool().
                        preparedQuery(checkProvisionQuery).
                        execute(Tuple.of(ipAddress, discovaryId), checkRes ->
                {
                    if (checkRes.succeeded())
                    {
                        var checkResultSet = checkRes.result();

                        if (checkResultSet.rowCount() > 0)
                        {
//                            // Record exists, perform an update instead of insert
//                            String updateQuery = "UPDATE provision SET credential_id = $1, metrics = $2::jsonb " +
//                                    "WHERE ip_address = $3 AND discovary_id = $4";
//
//                            DatabaseClient.getPool().preparedQuery(updateQuery).execute(
//                                    Tuple.of(credentialId, metrics.encode(), ipAddress, discovaryId),
//                                    updateRes -> {
//                                        if (updateRes.succeeded())
//                                        {
                                            context.response()
                                                    .putHeader("Content-Type", "application/json")
                                                    .end(new JsonObject().put("message", "This Ip is already Provisoned").encode());
//                                        }
//                                        else
//                                        {
//                                            context.response().setStatusCode(500)
//                                                    .end(new JsonObject().put("error", "Failed to update provision").encode());
//                                        }
//                                    });
                        }
                        else
                        {
                            // No record exists, insert a new provision record
                            String insertQuery = "INSERT INTO provision (discovary_id, ip_address, credential_id, metrics) " +
                                    "VALUES ($1, $2, $3, $4::jsonb) RETURNING provision_id";

                            DatabaseClient.
                                    getPool().
                                    preparedQuery(insertQuery).
                                    execute(Tuple.of(discovaryId, ipAddress, credentialId, metrics.encode()),
                                    insertRes ->
                                    {
                                        if (insertRes.succeeded())
                                        {
                                            long provisionId = insertRes.result().iterator().next().getLong("provision_id");

                                            JsonObject response = new JsonObject()
                                                    .put("provisioned", true)
                                                    .put("provision_id", provisionId)
                                                    .put("discovary_id", discovaryId)
                                                    .put("ip_address", ipAddress)
                                                    .put("credential_id", credentialId)
                                                    .put("metrics", metrics);

                                            context.response()
                                                    .putHeader("Content-Type", "application/json")
                                                    .end(response.encode());
                                        }
                                        else
                                        {
                                            context.response().setStatusCode(500)
                                                    .end(new JsonObject().put("error", "Failed to insert provision").encode());
                                        }
                                    });
                        }
                    }
                    else
                    {
                        context.response().setStatusCode(500)
                                .end(new JsonObject().put("error", "Database error").encode());
                    }
                });

            }
            else
            {
                context.response().setStatusCode(500)
                        .end(new JsonObject().put("error", "Database error").encode());
            }
        });
    }


    public void handleUpdateProvision(RoutingContext context)
    {
        JsonObject body = context.body().asJsonObject();
        if (!body.containsKey("provision_id") || !body.containsKey("credential_id") || !body.containsKey("metrics")) {
            context.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Missing fields").encode());
            return;
        }

        long provisionId = body.getLong("provision_id");
        int credentialId = body.getInteger("credential_id");
        JsonObject metrics = body.getJsonObject("metrics");

        String query = "UPDATE provision SET credential_id = $1, metrics = $2::jsonb WHERE provision_id = $3";

        DatabaseClient.getPool().preparedQuery(query)
                .execute(Tuple.of(credentialId, metrics.encode(), provisionId), ar -> {
                    if (ar.succeeded()) {
                        context.response()
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("message", "Provision updated").encode());
                    }
                    else
                    {
                        context.response().setStatusCode(500)
                                .end(new JsonObject().put("error", "Failed to update provision").encode());
                    }
                });
    }


    public void handleStartPolling(RoutingContext context)
    {
        String provisionIdStr = context.pathParam("id");

        try
        {
            long provisionId = Long.parseLong(provisionIdStr);

            String query = "SELECT * FROM provision WHERE provision_id = $1";

            DatabaseClient.getPool()
                    .preparedQuery(query)
                    .execute(Tuple.of(provisionId), ar -> {

                        if (ar.succeeded() && ar.result().rowCount() > 0)
                        {
                            Row row = ar.result().iterator().next();
                            String ip = row.getString("ip_address");
                            String metricsStr = row.getString("metrics");
                            String credentialsId= row.getString("credential_id");
                            String discoveryId= row.getString("discovary_id");



                            if (metricsStr != null)
                            {
//                                JsonObject provisionResponse()
                                JsonObject metrics = new JsonObject(metricsStr);  // ✅ fixed: parse directly
                                PollingEngine.startPolling(this.vertx , provisionId, ip, metrics);
                                context.response().setStatusCode(200)
                                        .end(new JsonObject().put("success", "Polling is started...").encode());
                            }
                            else
                            {
                                context.response().setStatusCode(400)
                                        .end(new JsonObject().put("error", "Metrics JSON is null").encode());
                            }
                        }
                        else
                        {
                            context.response().setStatusCode(404)
                                    .end("Provisioning ID not found");
                        }
                    });

        }
        catch (NumberFormatException e)
        {
            context.response().setStatusCode(400).end("Invalid provision ID format");
        }
    }

    public void handleListAllProvisions(RoutingContext context)
    {
        String query = "SELECT * FROM provision";

        DatabaseClient.
                getPool().
                query(query).
                execute(ar -> {

            if (ar.succeeded())
            {
                JsonArray results = new JsonArray();

                for (Row row : ar.result())
                {
                    JsonObject obj = new JsonObject()
                            .put("provision_id", row.getLong("provision_id"))
                            .put("discovary_id", row.getLong("discovary_id"))
                            .put("ip_address", row.getString("ip_address"))
                            .put("credential_id", row.getInteger("credential_id"));

                    String metricsStr = row.getString("metrics");

                    if (metricsStr != null)
                    {
                        obj.put("metrics", new JsonObject(metricsStr));
                    }
                    else
                    {
                        obj.put("metrics", new JsonObject());
                    }

                    results.add(obj);
                }
                context.response().putHeader("Content-Type", "application/json").end(results.encode());
            } else {
                context.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to fetch provisions").encode());
            }
        });
    }

    public void handleGetProvisionById(RoutingContext context) {
        String idStr = context.pathParam("id");

        try {
            long provisionId = Long.parseLong(idStr);
            String query = "SELECT * FROM provision WHERE provision_id = $1";

            DatabaseClient.getPool().preparedQuery(query).execute(Tuple.of(provisionId), ar -> {
                if (ar.succeeded() && ar.result().rowCount() > 0) {
                    Row row = ar.result().iterator().next();
                    JsonObject obj = new JsonObject()
                            .put("provision_id", row.getLong("provision_id"))
                            .put("discovary_id", row.getLong("discovary_id"))
                            .put("ip_address", row.getString("ip_address"))
                            .put("credential_id", row.getInteger("credential_id"))
                            .put("metrics", new JsonObject(row.getString("metrics")));

                    context.response().putHeader("Content-Type", "application/json").end(obj.encode());
                } else {
                    context.response().setStatusCode(404).end(new JsonObject().put("error", "Provision not found").encode());
                }
            });
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end(new JsonObject().put("error", "Invalid ID format").encode());
        }
    }



    public void handleGetProvisionStatus(RoutingContext context)
    {
        String deviceId = context.pathParam("deviceId");

        // TODO: Query provisioning status from DB

        JsonObject response = new JsonObject()
                .put("deviceId", deviceId)
                .put("status", "Provisioned"); // Replace with actual status

        context.response()
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }
}
