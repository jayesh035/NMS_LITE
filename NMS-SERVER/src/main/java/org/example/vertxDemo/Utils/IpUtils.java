package org.example.vertxDemo.Utils;

import java.net.InetAddress;
import java.util.*;
import java.util.regex.*;

public class IpUtils
{
    public static List<String> extractIpList(String input)
    {
        List<String> ips = new ArrayList<>();

        // Range format: A.B.C.start-end
        Pattern rangePattern = Pattern.compile("^(\\d+\\.\\d+\\.\\d+)\\.(\\d+)-(\\d+)$");
        Matcher matcher = rangePattern.matcher(input);

        if (matcher.matches())
        {
            String base = matcher.group(1);
            int start = Integer.parseInt(matcher.group(2));
            int end = Integer.parseInt(matcher.group(3));

            for (int i = start; i <= end; i++)
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
        List<String> result = new ArrayList<>();
        try
        {
            String[] parts = cidr.split("/");
            int prefix = Integer.parseInt(parts[1]);
            int mask = 0xffffffff << (32 - prefix);

            InetAddress baseAddress = InetAddress.getByName(parts[0]);
            byte[] bytes = baseAddress.getAddress();
            int baseIp = ((bytes[0] & 0xFF) << 24) |
                    ((bytes[1] & 0xFF) << 16) |
                    ((bytes[2] & 0xFF) << 8) |
                    (bytes[3] & 0xFF);

            int numberOfIps = 1 << (32 - prefix);

            for (int i = 0; i < numberOfIps; i++)
            {
                int currentIp = (baseIp & mask) + i;
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
