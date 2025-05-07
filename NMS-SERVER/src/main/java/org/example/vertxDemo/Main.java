package org.example.vertxDemo;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.example.vertxDemo.Handlers.CredentialsHandler;
import org.example.vertxDemo.Handlers.DiscoveryHandler;
import org.example.vertxDemo.Handlers.ProvisioningHandler;
import org.example.vertxDemo.Routes.Routes;
import org.example.vertxDemo.Database.DatabaseClient;
import org.example.vertxDemo.Handlers.AuthHandler;
import org.example.vertxDemo.Utils.Constants;
import org.example.vertxDemo.Utils.NetworkChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends AbstractVerticle
{

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @Override
    public void start(Promise<Void> startPromise)
    {
        DatabaseClient.initialize(vertx).onSuccess(v ->
        {
            // JWT setup
            JWTAuth jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
                    .addPubSecKey(new PubSecKeyOptions()
                            .setAlgorithm("HS256")
                            .setBuffer(Constants.JWT_KEY)));

            AuthHandler authHandler = new AuthHandler(jwtAuth);
            CredentialsHandler credentialsHandler = new CredentialsHandler(jwtAuth);
            DiscoveryHandler discoveryHandler = new DiscoveryHandler(jwtAuth, vertx);
            ProvisioningHandler provisioningHandler = new ProvisioningHandler(jwtAuth, vertx);

            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create());

            Routes.setupAuthRoutes(router, authHandler);
            Routes.setupCredentialRoutes(router, credentialsHandler, JWTAuthHandler.create(jwtAuth));
            Routes.setupDiscoveryRoutes(router, discoveryHandler, JWTAuthHandler.create(jwtAuth));
            Routes.setupProvisioningRoutes(router, provisioningHandler, JWTAuthHandler.create(jwtAuth));

            DeploymentOptions workerOpts = new DeploymentOptions()
                    .setWorker(true)
                    .setInstances(8);

            vertx.deployVerticle(NetworkChecker.class.getName(), workerOpts, res -> {
                if (res.succeeded()) {
                    logger.info("NetworkChecker Worker verticle deployed: {}", res.result());

                    vertx.createHttpServer()
                            .requestHandler(router)
                            .listen(Constants.SERVER_PORT, result -> {
                                if (result.succeeded()) {
                                    logger.info("Server started on port {}", Constants.SERVER_PORT);
                                    startPromise.complete();
                                } else {
                                    logger.error("Failed to start server: {}", result.cause().getMessage());
                                    startPromise.fail(result.cause());
                                }
                            });
                } else {
                    logger.error("Failed to deploy NetworkChecker verticle: {}", res.cause());
                    startPromise.fail(res.cause());
                }
            });
        }).onFailure(err -> {
            logger.error("Failed to initialize database: {}", err.getMessage());
            startPromise.fail(err);
        });
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Main());
    }
}
