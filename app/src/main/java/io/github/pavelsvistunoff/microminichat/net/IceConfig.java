package io.github.pavelsvistunoff.microminichat.net;

import java.util.regex.Pattern;

/** Explicit ICE configuration. TURN credentials are never serialized or persisted. */
public final class IceConfig {
    private static final Pattern TURN = Pattern.compile(
            "turns?:(?:[a-zA-Z0-9](?:[a-zA-Z0-9.-]{0,251}[a-zA-Z0-9])?|\\[[0-9a-fA-F:]+\\])(?::([0-9]{1,5}))?(?:\\?transport=(udp|tcp))?");
    public final String stun;
    public final String turn;
    public final String username;
    public final String password;
    public final boolean relayOnly;

    public IceConfig(String stun, String turn, String username, String password, boolean relayOnly) {
        if (!TransportPolicy.validStun(stun) || !validTurn(turn)
                || !validCredential(username) || !validCredential(password)
                || (turn.isEmpty() && (relayOnly || !username.isEmpty() || !password.isEmpty()))
                || (!turn.isEmpty() && (username.isEmpty() || password.isEmpty()))) {
            throw new IllegalArgumentException("Invalid ICE configuration; TURN requires URL, username and password");
        }
        this.stun=stun; this.turn=turn; this.username=username; this.password=password; this.relayOnly=relayOnly;
    }

    public static IceConfig direct(String stun) { return new IceConfig(stun, "", "", "", false); }
    public boolean allowsRelay() { return !turn.isEmpty(); }

    public static boolean validTurn(String value) {
        if (value==null || value.length()>320) return false;
        if (value.isEmpty()) return true;
        java.util.regex.Matcher match=TURN.matcher(value);
        if (!match.matches()) return false;
        String port=match.group(1);
        return (port==null || (Integer.parseInt(port)>=1 && Integer.parseInt(port)<=65535))
                && !(value.startsWith("turns:") && "udp".equals(match.group(2)));
    }

    private static boolean validCredential(String value) {
        if (value==null || value.length()>512) return false;
        for (int i=0;i<value.length();i++) if (Character.isISOControl(value.charAt(i))) return false;
        return true;
    }
}
