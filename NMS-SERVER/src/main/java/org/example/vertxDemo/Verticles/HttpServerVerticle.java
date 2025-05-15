package org.example.vertxDemo.Verticles;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.example.vertxDemo.Handlers.AuthHandler;
import org.example.vertxDemo.Handlers.CredentialsHandler;
import org.example.vertxDemo.Handlers.DiscoveryHandler;
import org.example.vertxDemo.Handlers.ProvisioningHandler;
import org.example.vertxDemo.Routes.Routes;
import org.example.vertxDemo.Utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(HttpServerVerticle.class);
    private JWTAuth jwtAuth;

    @Override
    public void start(Promise<Void> startPromise)
    {
        // Setup JWT Authentication provider
        jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setBuffer(Constants.JWT_KEY)));

        // Initialize handlers with jwtAuth and vertx if needed
        var authHandler = new AuthHandler(jwtAuth);
        var credentialsHandler = new CredentialsHandler(jwtAuth);
        var discoveryHandler = new DiscoveryHandler(jwtAuth, vertx);
        var provisioningHandler = new ProvisioningHandler(jwtAuth, vertx);

        var router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Setup routes, attach JWT auth handlers for protected routes
        Routes.setupAuthRoutes(router, authHandler);
        Routes.setupCredentialRoutes(router, credentialsHandler, JWTAuthHandler.create(jwtAuth));
        Routes.setupDiscoveryRoutes(router, discoveryHandler, JWTAuthHandler.create(jwtAuth));
        Routes.setupProvisioningRoutes(router, provisioningHandler, JWTAuthHandler.create(jwtAuth));

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(Constants.SERVER_PORT, res ->
                {
                    if (res.succeeded())
                    {
                        logger.info("HTTP Server started on port {}", Constants.SERVER_PORT);
                        startPromise.complete();
                    }
                    else
                    {
                        logger.error("Failed to start HTTP Server: {}", res.cause().getMessage());
                        startPromise.fail(res.cause());
                    }
                });
    }
}
