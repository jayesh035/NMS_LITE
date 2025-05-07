package org.example.vertxDemo.Middlewares;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;


public class IpValidationMiddleware
{
    public static void handle(RoutingContext context)
    {
        JsonObject body = context.body().asJsonObject();

        String ipAddress = body.getString("ipAddress");

        if (ipAddress == null || !isValidIPAddressInput(ipAddress))
        {
            context.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "Invalid IP address format Or More then 1024 IP addresses").encode());
            return;
        }

        context.next(); // move to next handler if valid
    }

    private static boolean isValidIPAddressInput(String ipAddress)
    {
        // Match: single IP OR CIDR OR A.B.C.start-end format
       final String singleIpRegex = "^((\\d{1,3}\\.){3}\\d{1,3})$";
       final String cidrRegex = "^((\\d{1,3}\\.){3}\\d{1,3})/(\\d{1,2})$";
       final String rangeRegex = "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\.(\\d{1,3})-(\\d{1,3})$";

        if (ipAddress.matches(singleIpRegex))
        {
            return isValidIP(ipAddress);
        }
        else if (ipAddress.matches(cidrRegex))
        {
            String[] parts = ipAddress.split("/");
            return isValidIP(parts[0]) && Integer.parseInt(parts[1]) >= 0 && Integer.parseInt(parts[1]) <= 32 && Integer.parseInt(parts[1])>=22;
        }
        else if (ipAddress.matches(rangeRegex))
        {
            String[] mainParts = ipAddress.split("\\.");
            String prefix = mainParts[0] + "." + mainParts[1] + "." + mainParts[2];
            String lastPart = mainParts[3];
            String[] rangeParts = lastPart.split("-");
            int start = Integer.parseInt(rangeParts[0]);
            int end = Integer.parseInt(rangeParts[1]);
            return start >= 0 && end >= start && end <= 255;
        }

        return false;
    }
    private static boolean isValidIP(String ip)
    {
        String[] parts = ip.split("\\.");

        if (parts.length != 4) return false;

        for (String part : parts)
        {
            int val = Integer.parseInt(part);
            if (val < 0 || val > 255) return false;
        }
        return true;
    }
}
