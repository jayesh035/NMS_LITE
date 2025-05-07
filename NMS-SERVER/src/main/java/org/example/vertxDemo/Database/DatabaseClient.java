package org.example.vertxDemo.Database;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlConnection;
import org.example.vertxDemo.Utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseClient {

    private static Pool pool;
    private static final Logger logger = LoggerFactory.getLogger(DatabaseClient.class);

    // Add a flag to track whether the pool is fully initialized
    private static boolean initialized = false;

    public static Future<Void> initialize(Vertx vertx) {
        Promise<Void> promise = Promise.promise();

        if (initialized) {
            logger.warn("Database pool already initialized.");
            promise.complete();
            return promise.future();
        }

        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(Constants.DB_PORT)
                .setHost(Constants.DB_HOST)
                .setDatabase(Constants.DB_NAME)
                .setUser(Constants.DB_USERNAME)
                .setPassword(Constants.DB_PASSWORD);

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        pool = Pool.pool(vertx, connectOptions, poolOptions);
        logger.info("Database connection pool initialized.");

        pool.getConnection(res -> {
            if (res.succeeded()) {
                SqlConnection connection = res.result();
                connection.query("SELECT 1").execute(queryRes -> {
                    if (queryRes.succeeded()) {
                        logger.info("Successfully connected to PostgreSQL!");
                        initialized = true;
                        promise.complete();
                    } else {
                        logger.error("Database query failed: {}", queryRes.cause().getMessage());
                        promise.fail(queryRes.cause());
                    }
                    connection.close();
                });
            } else {
                logger.error("Failed to connect to PostgreSQL: {}", res.cause().getMessage());
                promise.fail(res.cause());
            }
        });

        return promise.future();
    }

    public static synchronized Pool getPool() {
        if (!initialized) {
            logger.error("Database client is not initialized or pool is closed.");
            throw new IllegalStateException("DatabaseClient not initialized! Call initialize() first.");
        }
        return pool;
    }
}
