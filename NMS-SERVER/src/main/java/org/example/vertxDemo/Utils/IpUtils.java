package org.example.vertxDemo.Utils;

import java.net.InetAddress;
import java.util.*;
import java.util.regex.*;

public class IpUtils
{
    public static List<String> extractIpList(String input)
    {
        var ips = new ArrayList<String>();

        // Range format: A.B.C.start-end
        var rangePattern = Pattern.compile("^(\\d+\\.\\d+\\.\\d+)\\.(\\d+)-(\\d+)$");
        var matcher = rangePattern.matcher(input);

        if (matcher.matches())
        {
            var base = matcher.group(1);
            var start = Integer.parseInt(matcher.group(2));
            var end = Integer.parseInt(matcher.group(3));

            for (var i = start; i <= end; i++)
            {
                ips.add(base + "." + i);
            }
        }
        // CIDR format
        else if (input.contains("/"))
        {
            ips.addAll(expandCidr(input));
        }
        // Single IP
        else
        {
            ips.add(input);
        }

        return ips;
    }

    // Basic CIDR to IP expansion (IPv4 only)
    private static List<String> expandCidr(String cidr)
    {
        var result = new ArrayList<String>();
        try
        {
            var parts = cidr.split("/");
            var prefix = Integer.parseInt(parts[1]);
            var mask = 0xffffffff << (32 - prefix);

            var baseAddress = InetAddress.getByName(parts[0]);
            var bytes = baseAddress.getAddress();
            var baseIp = ((bytes[0] & 0xFF) << 24) |
                    ((bytes[1] & 0xFF) << 16) |
                    ((bytes[2] & 0xFF) << 8) |
                    (bytes[3] & 0xFF);

            var numberOfIps = 1 << (32 - prefix);

            for (var i = 0; i < numberOfIps; i++)
            {
                var currentIp = (baseIp & mask) + i;
                result.add(String.format("%d.%d.%d.%d",
                        (currentIp >> 24) & 0xFF,
                        (currentIp >> 16) & 0xFF,
                        (currentIp >> 8) & 0xFF,
                        currentIp & 0xFF));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace(); // Log or handle CIDR parse error
        }
        return result;
    }
}
