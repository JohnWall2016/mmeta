// copyright 2009 ActiveVideo; license: MIT; see license.txt
package mmeta;

public class RuleFailure extends RuntimeException {
    public String last = "";
    public int pos = 0;
    public String toString() { return "ERROR.last: " + last + " at " + pos; }
}

