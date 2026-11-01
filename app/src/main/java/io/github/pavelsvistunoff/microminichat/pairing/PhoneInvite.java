package io.github.pavelsvistunoff.microminichat.pairing;

import java.net.InetAddress;
import java.net.URI;
import java.util.Base64;

/** Invitation capability. Never log it; parsing never connects or resolves DNS. */
public final class PhoneInvite {
    public static final int MAX_CHARS=512;
    public final String host, fingerprint;
    public final int port;
    private final byte[] token;
    public PhoneInvite(String host,int port,String fingerprint,byte[] token) {
        if (!validIp(host) || port<1 || port>65535 || fingerprint==null
                || !fingerprint.matches("[0-9a-f]{64}") || token==null || token.length!=32) {
            throw new IllegalArgumentException("INVALID_PHONE_INVITATION");
        }
        this.host=host;this.port=port;this.fingerprint=fingerprint;this.token=token.clone();
    }
    public byte[] token() { return token.clone(); }
    public String encode() {
        try { return new URI("microminichat",null,host,port,"/pair/v1",null,
                fingerprint+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(token)).toASCIIString(); }
        catch (java.net.URISyntaxException e) { throw new IllegalArgumentException("INVALID_PHONE_INVITATION"); }
    }
    public static PhoneInvite decode(String value) {
        if (value==null || value.length()>MAX_CHARS) throw new IllegalArgumentException("INVALID_PHONE_INVITATION");
        try {
            URI uri=new URI(value.trim());
            if (!"microminichat".equals(uri.getScheme()) || !"/pair/v1".equals(uri.getPath())
                    || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getHost()==null
                    || uri.getRawFragment()==null || !uri.getRawFragment().matches("[0-9a-f]{64}\\.[A-Za-z0-9_-]{43}"))
                throw new IllegalArgumentException();
            String host=uri.getHost();
            if (host.startsWith("[")) host=host.substring(1,host.length()-1);
            String[] parts=uri.getRawFragment().split("\\.");
            PhoneInvite result=new PhoneInvite(host,uri.getPort(),parts[0],Base64.getUrlDecoder().decode(parts[1]));
            if (!result.encode().equals(value.trim())) throw new IllegalArgumentException();
            return result;
        } catch (Exception e) { throw new IllegalArgumentException("INVALID_PHONE_INVITATION"); }
    }
    public static boolean validIp(String host) {
        if (host==null || host.isEmpty() || host.length()>45) return false;
        if (host.contains(":")) {
            if (!host.matches("[0-9a-fA-F:]+")) return false;
            try { return InetAddress.getByName(host) instanceof java.net.Inet6Address; }
            catch (Exception e) { return false; }
        }
        String[] parts=host.split("\\.",-1);
        if (parts.length!=4) return false;
        for (String part:parts) {
            if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part)>255) return false;
        }
        return true;
    }
}
