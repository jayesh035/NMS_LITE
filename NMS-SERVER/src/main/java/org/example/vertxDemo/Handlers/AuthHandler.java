package org.example.vertxDemo.Handlers;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;
import org.example.vertxDemo.Database.DatabaseClient;
import org.example.vertxDemo.Utils.DBQueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthHandler
{
    private static final Logger logger = LoggerFactory.getLogger(AuthHandler.class);
    private final JWTAuth jwtAuth;

    public AuthHandler(JWTAuth jwtAuth)
    {
        this.jwtAuth = jwtAuth;
    }

    // Handle user login
    public void handleLogin(RoutingContext context)
    {
        JsonObject body = context.body().asJsonObject();
        if (body == null || !body.containsKey("email") || !body.containsKey("password"))
        {
            context.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "Email and password are required").encode());
            return;
        }

        String email = body.getString("email");
        String password = body.getString("password");

        String selectQuery = DBQueries.login;
        DatabaseClient.getPool()
                .preparedQuery(selectQuery)
                .execute(Tuple.of(email, password), ar -> {

                    if (ar.succeeded() && ar.result().rowCount() > 0)
                    {
                        JsonObject claims = new JsonObject().put("email", email);
                        String token = jwtAuth.generateToken(

                                claims,
                                new JWTOptions().setExpiresInMinutes(60)

                        );

                        context.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("token", token).encode());
                        logger.info("User logged in successfully: {}", email);
                    }
                    else
                    {
                        logger.error("Login failed: Invalid credentials for email {}", email);
                        context.response()
                                .setStatusCode(401)
                                .end(new JsonObject().put("error", "Invalid credentials").encode());
                    }
                });
    }

    // Handle user registration
    public void handleRegister(RoutingContext context)
    {
        JsonObject body = context.body().asJsonObject();

        if (body == null)
        {
            context.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "Request body is missing or not valid JSON").encode());
            return;
        }

        String email = body.getString("email");
        String password = body.getString("password");
        String username = body.getString("username");

        if (email == null || password == null || username == null)
        {
            context.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "Email, password, and username are required").encode());
            return;
        }

        String insertQuery = DBQueries.registration;

        DatabaseClient.getPool()
                .preparedQuery(insertQuery)
                .execute(Tuple.of(email, password, username), ar -> {

                    if (ar.succeeded())
                    {
                        JsonObject claims = new JsonObject().put("email", email);
                        String token = jwtAuth.generateToken(
                                claims,
                                new JWTOptions().setExpiresInMinutes(60)
                        );
                        context.response()
                                .setStatusCode(201)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("token", token).encode());
                        logger.info("User registered successfully: {}", email);
                    }
                    else
                    {
                        logger.error("Registration failed: {}", ar.cause().getMessage());
                        context.response()
                                .setStatusCode(500)
                                .end(new JsonObject().put("error", "Failed to register user").encode());
                    }
                });
    }
}