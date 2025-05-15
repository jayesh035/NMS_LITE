package org.example.vertxDemo;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.example.vertxDemo.Database.DatabaseClient;
import org.example.vertxDemo.Verticles.PollingEngine;
import org.example.vertxDemo.Verticles.HttpServerVerticle;
import org.example.vertxDemo.Verticles.NetworkChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main
{

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args)
    {
        // Create a single Vertx instance with default options
        var vertx = Vertx.vertx(new VertxOptions());

        // Initialize the database first
        DatabaseClient.initialize(vertx).onSuccess(v ->
        {

            // No worker option and single instance for all verticles
            var defaultOptions = new DeploymentOptions().setInstances(1);

            // Deploy NetworkChecker verticle (event loop thread)
            vertx.deployVerticle(NetworkChecker.class.getName(), defaultOptions)
                    .compose(id ->
                    {
                        logger.info("NetworkCheckerVerticle deployed: {}", id);

                        // Deploy HttpServerVerticle (event loop thread)
                        return vertx.deployVerticle(HttpServerVerticle.class.getName(), defaultOptions);
                    })
                    .compose(id ->
                    {
                        logger.info("HttpServerVerticle deployed: {}", id);

                        // Deploy PollingEngine verticle (event loop thread)
                        return vertx.deployVerticle(PollingEngine.class.getName(), defaultOptions);
                    })
                    .onSuccess(id ->
                    {
                        logger.info("PollingEngineVerticle deployed: {}", id);
                        logger.info("All verticles deployed successfully");
                    })
                    .onFailure(err ->
                    {
                        logger.error("Failed to deploy verticles: {}", err.getMessage());
                        vertx.close(closeRes ->
                        {
                            if (closeRes.succeeded())
                            {
                                logger.info("Vert.x instance shut down");
                            }
                            else
                            {
                                logger.error("Failed to shut down Vert.x instance: {}", closeRes.cause().getMessage());
                            }
                        });
                    });

        }).onFailure(err ->
        {
            logger.error("Failed to initialize database: {}", err.getMessage());
        });
    }
}
