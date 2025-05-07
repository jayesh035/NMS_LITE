package org.example.vertxDemo.Utils;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.core.json.JsonObject;

public class JwtUtils
{
    private static final JWTAuth jwtAuth;

    static
    {
        // Set up JWT Auth options using a keystore (you can also use HMAC, RSA, etc.)
        KeyStoreOptions keyStoreOptions = new KeyStoreOptions()
                .setType("jceks")
                .setPath("keystore.jceks"); // Ensure you have a keystore to sign the JWT

        JWTAuthOptions authOptions = new JWTAuthOptions().setKeyStore(keyStoreOptions);
        jwtAuth = JWTAuth.create(Vertx.vertx(), authOptions);
    }

    // Method to generate a JWT token
    public static String generateToken(String username)
    {
        JsonObject payload = new JsonObject().put("sub", username); // Add claims like username, roles, etc.

        return jwtAuth.generateToken(payload); // Token expiration time and other options can be customized
    }

    // Method to verify JWT token
    public static boolean verifyToken(String token)
    {
        try
        {
            jwtAuth.authenticate(new JsonObject().put("jwt", token));
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
