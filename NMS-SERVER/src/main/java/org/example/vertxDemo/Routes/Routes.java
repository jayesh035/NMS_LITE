package org.example.vertxDemo.Routes;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.example.vertxDemo.Handlers.AuthHandler;
import org.example.vertxDemo.Handlers.CredentialsHandler;
import org.example.vertxDemo.Handlers.DiscoveryHandler;
import org.example.vertxDemo.Handlers.ProvisioningHandler;
import org.example.vertxDemo.Middlewares.IpValidationMiddleware;

public class Routes
{
    public static void setupAuthRoutes(Router router,
                                       AuthHandler authHandler)
    {
        router.post("/login").handler(authHandler::handleLogin);
        router.post("/register").handler(authHandler::handleRegister);
    }


    public static void setupCredentialRoutes(Router router,
                                             CredentialsHandler credentialsHandler,
                                             JWTAuthHandler jwtAuthHandler)
    {
        // Public endpoint for creating credentials (no JWT required, adjust based on requirements)
        router.post("/credentials").handler(credentialsHandler::handleCreate);

        // Secured endpoints (require JWT)
        router.get("/credentials/:id").handler(jwtAuthHandler).handler(credentialsHandler::handleRead);
        router.get("/credentials").handler(jwtAuthHandler).handler(credentialsHandler::handleList);
        router.put("/credentials/:id").handler(jwtAuthHandler).handler(credentialsHandler::handleUpdate);
        router.delete("/credentials/:id").handler(jwtAuthHandler).handler(credentialsHandler::handleDelete);
    }


    public static void setupDiscoveryRoutes(Router router,
                                            DiscoveryHandler discoveryHandler,
                                            JWTAuthHandler jwtAuthHandler)
    {
        router.post("/discoveries")
                .handler(jwtAuthHandler)
                .handler(IpValidationMiddleware::handle)
                .handler(discoveryHandler::handleCreate);

        router.get("/discoveries").handler(jwtAuthHandler).handler(discoveryHandler::handleList);
        router.get("/discoveries/:id").handler(jwtAuthHandler).handler(discoveryHandler::handleRead);
        router.put("/discoveries/:id").handler(jwtAuthHandler).handler(discoveryHandler::handleUpdate);
        router.delete("/discoveries/:id").handler(jwtAuthHandler).handler(discoveryHandler::handleDelete);

        // Route to run a discovery task manually
        router.post("/discoveries/:id/run").handler(jwtAuthHandler).handler(discoveryHandler::handleRunDiscovery);
    }

    public static void setupProvisioningRoutes(Router router,
                                               ProvisioningHandler provisioningHandler,
                                               JWTAuthHandler jwtAuthHandler)
    {
        router.post("/provision").
                handler(jwtAuthHandler).
                handler(provisioningHandler::handleProvision);


        router.get("/provision/:id/start").
                handler(jwtAuthHandler).
                handler(provisioningHandler::handleStartPolling);

        router.get("/provision/:id").
                handler(jwtAuthHandler).
                handler(provisioningHandler::handleGetProvisionById);

        router.get("/provision/").
                handler(jwtAuthHandler).
                handler(provisioningHandler::handleListAllProvisions);

        router.put("/provision/:id").
                handler(jwtAuthHandler).
                handler(provisioningHandler::handleUpdateProvision);
    }


}