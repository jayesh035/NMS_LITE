package org.example.vertxDemo.Handlers;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.example.vertxDemo.Database.DatabaseClient;
import org.example.vertxDemo.Utils.DBQueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CredentialsHandler
{
    private static final Logger logger = LoggerFactory.getLogger(CredentialsHandler.class);

    public CredentialsHandler(JWTAuth jwtAuth)
    {

    }

    // Create a new credential
    public void handleCreate(RoutingContext context)
    {
        JsonObject body = context.body().asJsonObject();
        if (body == null || !body.containsKey("credential_name") || !body.containsKey("systemtype") || !body.containsKey("data"))
        {
            context.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "credential_name, systemtype, and data are required").encode());
            return;
        }


        String credentialName = body.getString("credential_name");
        String systemType = body.getString("systemtype");
        JsonObject data = body.getJsonObject("data");

        // Conditional validation based on systemType
        switch (systemType.toUpperCase())
        {
            case "SNMP_V1":
            case "SNMP_V2C":

                if (!data.containsKey("community") || data.getString("community").isEmpty())
                {
                    context.response()
                            .setStatusCode(400)
                            .end(new JsonObject().put("error", "Field 'community' is required for " + systemType).encode());
                    return;
                }
                break;

            case "SNMP_V3":

                if (!data.containsKey("username") || data.getString("username").isEmpty() ||
                        !data.containsKey("password") || data.getString("password").isEmpty() ||
                        !data.containsKey("security_level") || data.getString("security_level").isEmpty() ||
                        !data.containsKey("encryption_protocol") || data.getString("encryption_protocol").isEmpty())
                {
                    context.response()
                            .setStatusCode(400)
                            .end(new JsonObject().put("error", "Fields 'username', 'password', 'security_level', and 'encryption_protocol' are required for SNMP_V3").encode());
                    return;
                }
                break;

            default:
                context.response()
                        .setStatusCode(400)
                        .end(new JsonObject().put("error", "Unsupported systemtype: " + systemType).encode());
                return;
        }

        String insertQuery = DBQueries.createCredentials;

        DatabaseClient.getPool()
                .preparedQuery(insertQuery)
                .execute(Tuple.of(credentialName, systemType, data.encode()), ar ->
                {
                    if (ar.succeeded())
                    {
                        Long id = ar.result().iterator().next().getLong("id");
                        context.response()
                                .setStatusCode(201)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject()
                                        .put("id", id)
                                        .put("credential_name", credentialName)
                                        .put("systemtype", systemType)
                                        .put("data", data)
                                        .encode());
                        logger.info("Credential created successfully: {}", credentialName);
                    }
                    else
                    {
                        logger.error("Failed to create credential: {}", ar.cause().getMessage());
                        context.response()
                                .setStatusCode(500)
                                .end(new JsonObject().put("error", "Failed to create credential").encode());
                    }
                });
    }

    // Read a single credential by ID
    public void handleRead(RoutingContext context)
    {
        String id = context.pathParam("id");

        if (id == null || !id.matches("\\d+"))
        {
            context.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "Valid ID is required").encode());
            return;
        }

        String selectQuery = DBQueries.selectCredentialById;
        DatabaseClient.getPool()
                .preparedQuery(selectQuery)
                .execute(Tuple.of(Long.parseLong(id)), ar ->
                {
                    if (ar.succeeded() && ar.result().rowCount() > 0)
                    {
                        Row row = ar.result().iterator().next();
                        JsonObject response = new JsonObject()
                                .put("id", row.getLong("id"))
                                .put("credential_name", row.getString("credential_name"))
                                .put("systemtype", row.getString("systemtype"))
                                .put("data", new JsonObject(row.getString("data")));
                        context.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end(response.encode());
                        logger.info("Credential retrieved: ID {}", id);
                    }
                    else
                    {
                        context.response()
                                .setStatusCode(404)
                                .end(new JsonObject().put("error", "Credential not found").encode());
                    }
                });
    }

    // List all credentials (admin-only)
    public void handleList(RoutingContext context)
    {
        String selectQuery = DBQueries.selectAllCredentials;
        DatabaseClient.getPool()
                .preparedQuery(selectQuery)
                .execute(ar ->
                {
                    if (ar.succeeded())
                    {
                        JsonArray credentials = new JsonArray();
                        for (Row row : ar.result())
                        {
                            credentials.add(new JsonObject()
                                    .put("id", row.getLong("id"))
                                    .put("credential_name", row.getString("credential_name"))
                                    .put("systemtype", row.getString("systemtype"))
                                    .put("data", new JsonObject(row.getString("data"))));
                        }

                        context.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end(credentials.encode());
                        logger.info("Retrieved {} credentials", credentials.size());
                    }
                    else
                    {
                        logger.error("Failed to list credentials: {}", ar.cause().getMessage());
                        context.response()
                                .setStatusCode(500)
                                .end(new JsonObject().put("error", "Failed to list credentials").encode());
                    }
                });
    }

    // Update a credential
    public void handleUpdate(RoutingContext context)
    {
        String id = context.pathParam("id");

        if (id == null || !id.matches("\\d+"))
        {
            context.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "Valid ID is required").encode());
            return;
        }

        JsonObject body = context.body().asJsonObject();

        if (body == null)
        {
            context.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "Request body is missing").encode());
            return;
        }

        String credentialName = body.getString("credential_name");
        String systemType = body.getString("systemtype");
        JsonObject data = body.getJsonObject("data");

        String updateQuery = DBQueries.updateCredential;
        DatabaseClient.getPool()
                .preparedQuery(updateQuery)
                .execute(Tuple.of(credentialName, systemType, data != null ? data.encode() : null, Long.parseLong(id)), ar ->
                {
                    if (ar.succeeded() && ar.result().rowCount() > 0)
                    {
                        context.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("message", "Credential updated successfully").encode());
                        logger.info("Credential updated: ID {}", id);
                    }
                    else
                    {
                        context.response()
                                .setStatusCode(404)
                                .end(new JsonObject().put("error", "Credential not found").encode());
                    }
                });
    }

    // Delete a credential
    public void handleDelete(RoutingContext context)
    {
        String id = context.pathParam("id");
        if (id == null || !id.matches("\\d+"))
        {
            context.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "Valid ID is required").encode());
            return;
        }

        String deleteQuery = DBQueries.deleteCredentialById;
        DatabaseClient.getPool()
                .preparedQuery(deleteQuery)
                .execute(Tuple.of(Long.parseLong(id)), ar ->
                {
                    if (ar.succeeded() && ar.result().rowCount() > 0)
                    {
                        context.response()
                                .setStatusCode(204)
                                .end();
                        logger.info("Credential deleted: ID {}", id);
                    }
                    else
                    {
                        context.response()
                                .setStatusCode(404)
                                .end(new JsonObject().put("error", "Credential not found").encode());
                    }
                });
    }
}