package com.crest.client.core.security;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

public class UrlValidator {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("UrlValidator");
    
    private static final String[] BLOCKED_SCHEMES = {"file", "jar", "ftp"};
    private static final String[] PRIVATE_IP_RANGES = {
        "127.", "0.", "10.",
        "192.168.", "172.16.", "172.17.", "172.18.", "172.19.",
        "172.20.", "172.21.", "172.22.", "172.23.", "172.24.",
        "172.25.", "172.26.", "172.27.", "172.28.", "172.29.",
        "172.30.", "172.31.",
        "169.254.",
        "100.64.", "100.65.", "100.66.", "100.67.",
        "100.68.", "100.69.", "100.70.", "100.71.",
        "100.72.", "100.73.", "100.74.", "100.75.",
        "100.76.", "100.77.", "100.78.", "100.79.",
        "100.80.", "100.81.", "100.82.", "100.83.",
        "100.84.", "100.85.", "100.86.", "100.87.",
        "100.88.", "100.89.", "100.90.", "100.91.",
        "100.92.", "100.93.", "100.94.", "100.95.",
        "100.96.", "100.97.", "100.98.", "100.99.",
        "100.100.", "100.101.", "100.102.", "100.103.",
        "100.104.", "100.105.", "100.106.", "100.107.",
        "100.108.", "100.109.", "100.110.", "100.111.",
        "100.112.", "100.113.", "100.114.", "100.115.",
        "100.116.", "100.117.", "100.118.", "100.119.",
        "100.120.", "100.121.", "100.122.", "100.123.",
        "100.124.", "100.125.", "100.126.", "100.127."
    };
    
    public static boolean isUrlAllowed(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            
            if (scheme == null) {
                LOGGER.security("Blocked URL with no scheme: {}", url);
                return false;
            }
            
            for (String blocked : BLOCKED_SCHEMES) {
                if (scheme.equalsIgnoreCase(blocked)) {
                    LOGGER.security("Blocked URL with scheme {}: {}", scheme, url);
                    return false;
                }
            }
            
            String host = uri.getHost();
            if (host == null) {
                return true;
            }
            
            host = host.toLowerCase();
            
            if (host.equals("localhost") || host.equals("::1") || host.equals("0:0:0:0:0:0:0:1")) {
                LOGGER.security("Blocked localhost URL: {}", url);
                return false;
            }
            
            if (host.startsWith("fe80:") || host.startsWith("fc00:") || host.startsWith("fd00:") || host.startsWith("ff")) {
                LOGGER.security("Blocked IPv6 private/link-local URL: {}", url);
                return false;
            }
            
            try {
                InetAddress addr = InetAddress.getByName(host);
                String ip = addr.getHostAddress();
                
                for (String range : PRIVATE_IP_RANGES) {
                    if (ip.startsWith(range)) {
                        LOGGER.security("Blocked private IP URL: {}", url);
                        return false;
                    }
                }
                
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || 
                    addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
                    LOGGER.security("Blocked special-use IP URL: {}", url);
                    return false;
                }
            } catch (UnknownHostException e) {
                LOGGER.debug("Could not resolve host for URL validation: {}", host);
            }
            
            return true;
            
        } catch (Exception e) {
            LOGGER.security("Invalid URL format: {}", e, url);
            return false;
        }
    }
    
    public static boolean isRtmpUrl(String url) {
        if (StringUtils.isBlank(url)) return false;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equals("rtmp") || scheme.equals("rtmps"));
        } catch (Exception e) {
            return false;
        }
    }
}
