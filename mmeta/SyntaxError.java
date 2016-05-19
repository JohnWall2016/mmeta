// copyright 2009 ActiveVideo; license: MIT; see license.txt
package mmeta;

/// Thrown when a syntax error is found
public class SyntaxError extends Error {
    private static final long serialVersionUID = 1625531475408759945L;
    public SyntaxError(String msg, String expected, int pos, String string, Object[] list) {
        super(makeMsg(msg, expected, pos, string, list));
    }

    private static String makeMsg(String msg, String expected, int pos, String string, Object[] list) {
        if (string != null) {
            int nl = 1;
            int nlpos = 0;
            for (int i = 0; i < pos; i++) if (string.charAt(i) == '\n') { nlpos = i; nl++; }

            if (msg.length() > 0) msg = "expected "+ msg;
            else msg = "expected "+ expected;

            String s = string.substring(pos, Math.min(pos + 13, string.length()));
            if (pos >= string.length()) {
              s = "<EOF>";
            }
            int n = s.indexOf('\n');
            if (n > 0) s = s.substring(0, n);
            msg = msg + " at '"+ s +"'";

            return ""+ msg +" (at line: "+ nl +", char: "+ (pos - nlpos) +")";
        } else {
            if (msg.length() > 0) msg = "expected "+ msg;
            else msg = "expected "+ expected;

            msg = msg + " at '"+ BaseParser.print_r(list[pos]) +"'";
            return ""+ msg +" (at pos: "+ pos +")";
        }
    }
}


