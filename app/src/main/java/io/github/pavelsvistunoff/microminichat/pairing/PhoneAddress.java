package io.github.pavelsvistunoff.microminichat.pairing;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.LinkAddress;
import java.net.InetAddress;

public final class PhoneAddress {
    private PhoneAddress() { }
    /** No external lookup. Prefer a global IPv6 address; detection does not prove reachability. */
    public static String detected(Context context) {
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties properties=manager.getLinkProperties(manager.getActiveNetwork());
        if(properties==null)return "";
        String fallback="";
        for(LinkAddress link:properties.getLinkAddresses()) {
            InetAddress ip=link.getAddress();
            if(ip.isLoopbackAddress() || ip.isLinkLocalAddress() || ip.isMulticastAddress() || ip.isAnyLocalAddress())continue;
            String host=ip.getHostAddress();if(host==null || !PhoneInvite.validIp(host))continue;
            if(ip instanceof java.net.Inet6Address && (ip.getAddress()[0]&0xe0)==0x20)return host;
            if(ip instanceof java.net.Inet4Address)fallback=host;
        }
        return fallback;
    }
}
