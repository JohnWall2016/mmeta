// copyright 2009 ActiveVideo; license: MIT; see license.txt
package jmeta;

import java.util.*;

class State {
    State prev = null;
    int pos; Object[] list; SparseArrayList<HashMap<String, Memoize>> positions;
    public State(State prev, int p, Object[] l, SparseArrayList<HashMap<String, Memoize>> m) {
        this.prev = prev;
        pos = p; list = l; positions = m;
    }
}

class Memoize {
    public Object val; public int pos; int seed = -1;
    public Memoize(Object val, int pos) { this.val = val; this.pos = pos; }
    public String toString() { return "at: "+ pos +" val: "+ val; }
}

/// Root parser object, all parsers will in the end extend BaseParser
public class BaseParser {
    public BaseParser() {
        lines.put(0, 0);
    }

    private static class ReverseComparator implements Comparator<Comparable>  {
        public int compare(Comparable a, Comparable b) {
            return -a.compareTo(b);
        }
    }

    public static String print_r(Object o) {
        StringBuffer sb = new StringBuffer();
        print_r(o, sb);
        return sb.toString();
    }
    public static void print_r(Object o, StringBuffer sb) {
        if (o instanceof ArrayList) {
            sb.append("[");
            for (int i = 0; i < ((ArrayList)o).size(); i++) {
                if (i > 0) sb.append(", ");
                print_r(((ArrayList)o).get(i), sb);
            }
            sb.append("]");
        } else if (o instanceof Object[]) {
            sb.append("[");
            for (int i = 0; i < ((Object[])o).length; i++) {
                if (i > 0) sb.append(", ");
                print_r(((Object[])o)[i], sb);
            }
            sb.append("]");
        } else {
            sb.append(o);
        }
    }
    public void _enter(String label) {
      if (debug_parse_tree) {
        String parent = parseTree.peekLast();
        if ("ws".equals(label) ||
            "skip".equals(parent)) {
          parseTree.addLast("skip");
          return;
        }
        String nodeName = "n" + nodeCount++;
        label = label.replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\n");
        System.out.println("  " + nodeName + "[label=\"" + label + "\"];");
        if (parent != null) {
          System.out.println("  " + parent + " -- " + nodeName + ";");
        }
        parseTree.addLast(nodeName);
      }
    }

    public Object _exit(Object result) {
      if (debug_parse_tree) {
        String nodeName = parseTree.removeLast();
        if ("skip".equals(nodeName)) {
          // don't print anything
        } else if (result == ERROR) {
          System.out.println("  " + nodeName + "[color=red];");
        } else if (result == GROW) {
          String growNode = "g" + nodeCount++;
          System.out.println("  " + growNode + "[label=\"GROW\",color=blue];");
          System.out.println("  " + nodeName + " -- " + growNode + ";");
          parseTree.addLast(nodeName);
        }
      }
      return result;
    }

    public static boolean tracing = false;
    public static boolean debug_parse_tree = false;
    public Object trace(Object... args) {
        if (! tracing) return args[args.length - 1];

        for (int i = 0; i < args.length; i++) {
            if (i > 0) System.out.print(" ");
            System.out.print(print_r(args[i]));
        }
        System.out.println(" at " + _pos);
        return args[args.length - 1];
    }

    /// Object indicating a parsing error
    public static final ErrorObject ERROR = new ErrorObject();
    public static final ErrorObject __ERROR__ = ERROR;
    public static final Object LEFT_REC = new Object() { public String toString() { return "LEFT_REC"; }};
    public static final Object __LEFT_REC__ = LEFT_REC;
    public static final Object GROW = new Object() { public String toString() { return "GROW"; }};
    public static final Object __GROW__ = GROW;
    public static final Memoize NOT_MEMOIZED = new Memoize(null, -1)  { public String toString() { return "not memoized"; }};
    public static final Memoize __NOT_MEMOIZED__ = NOT_MEMOIZED;

    LinkedList<Object> args;
    State _stack = null;
    SparseArrayList<HashMap<String, Memoize>> _positions;
    ArrayDeque<HashSet<String>> _lefts;
    // Use a reverse sorted TreeSet so we can use lines.tailSet(x).first() to find the
    // first value <= x;
    private TreeMap<Integer, Integer> lines = new TreeMap<Integer, Integer>(new ReverseComparator());
    private long nodeCount;
    private ArrayDeque<String> parseTree = new ArrayDeque<String>();

    public int _pos = 0;
    public int _pos() { return _pos; }
    public void _pos_set(int pos) { _pos = pos; }
    public String _string;
    public String _string() { return _string; }
    public void _string_set(String string) { _string = string; }
    public Object[] _list;
    public Object[] _list() { return _list; }
    public void _list_set(Object[] list) { _list = list; }
    private Token cached_token = new Token(-1, -1, -1, -1);

    public Object _memoize(String s, int p, Object o) {
        HashMap<String, Memoize> map = _positions.get(p);
        if (map == null) {
            map = new HashMap<String, Memoize>();
            _positions.set(p, map);
        }

        Memoize entry = map.get(s);
        if (entry == null) {
            // sometimes we don't have a entry, incase args > 0
            return trace("unmemoize:", s, o);
        } else {
            if (entry.seed != -1) {
                // if we are done growing, stop it, and remove this left recursion from stack
                if (o == ERROR || _pos <= entry.pos) {
                    _pos = entry.pos;
                    entry.seed = -1;
                    _lefts.pop();
                    return trace("< END:", s, _pos, entry.val);
                }

                // we will try to grow, reset all entries for this position, and record current result
                for (String k : _lefts.pop()) map.remove(k);
                // setup another left recursion stack
                _lefts.push(new HashSet<String>());
                // update the growing entry, and reset pos to its seed
                entry.val = o;
                entry.pos = _pos;
                _pos = entry.seed;
                return trace("<GROW:", s, _pos, o, GROW);
            }

            // if we are in a left recursive situation, mark each evaluated rule
            if (! _lefts.isEmpty()) _lefts.peek().add(s);

            entry.pos = _pos;
            entry.val = o;
            if (o == ERROR) _pos = p;
            if (o == ERROR) return trace("< err:", s, o);
            return trace("<  ok:", s, o);
        }
    }

    public Object _retrieve(String s) {
        // we cannot memoize in face of arguments
        if (! args.isEmpty()) return trace(">ntry:", s, NOT_MEMOIZED);

        int p = _pos;
        HashMap<String, Memoize> map = _positions.get(p);
        if (map == null) {
            map = new HashMap<String, Memoize>();
            _positions.set(p, map);
        }

        Memoize entry = map.get(s);
        if (entry == null) {
            // mark that we are starting with this rule
            map.put(s, new Memoize(LEFT_REC, _pos));
        } else {
            _pos = entry.pos;
            if (entry.val == LEFT_REC) {
                // notice we are diving into a left recursion, grow a seed from here, and start a left recursion stack
                entry.val = ERROR;
                entry.seed = entry.pos;
                _lefts.push(new HashSet<String>());
                return trace(">LEFT:", s, _pos, ERROR);
            }
            if (entry.val == ERROR) return trace("> err:", s, entry.val);
            return trace(">  ok:", s, entry.val);
        }
        return trace("> try:", s, NOT_MEMOIZED);
    }

    void _init() {
        _pos = 0;
        _positions = new SparseArrayList<HashMap<String, Memoize>>();
        _lefts = new ArrayDeque<HashSet<String>>();
        lines = new TreeMap<Integer, Integer>(new ReverseComparator());
        lines.put(0, 0);
        args = new LinkedList<Object>();
        init();
    }

    /// called after init(data)
    public void init() {}

    /// init parser with String, use parser.rule() to actually parse
    public void init(String s) {
        _string = s; _list = null; _init();
    }

    /// init parser with a Object[] array, @see init(String s);
    public void init(Object[] ls) {
        _string = null; _list = ls; _init();
    }

    /// init parser with a ArrayList, @see init(String s);
    public void init(ArrayList<? extends Object> as) {
        _string = null; _list = as.toArray(); _init();
    }

    public void init(List<? extends Object> as) {
        init(new ArrayList(as).toArray());
    }

    public Object parse(Object o) { return parse(o, null); }
    public Object parse(Object o, String r) {
             if (o instanceof ArrayList) init((ArrayList) o);
        else if (o instanceof Object[])  init((Object[]) o);
        else if (o instanceof String)    init((String) o);
        else if (o instanceof List)    init((List) o);
        else throw new AssertionError("parse requires a List, Object[] or String; got " + (o == null ? "null" : o.getClass().toString()));

        if (debug_parse_tree) {
          System.out.println("graph parse {");
        }

        Object _t = null;
        if (r != null) _t = _jump(r.intern());
        else _t = start();

        if (debug_parse_tree) {
          System.out.println("}");
        }

        if (_t==ERROR) throw new SyntaxError("", _pos, _string, _list);
        return _t;
    }

    /// start rule; override by creating a rule called 'start'
    public Object start() { throw new IllegalStateException("provide a rule called 'start'"); }

    public void _push(Object... as) { for (int i = as.length - 1; i >= 0; i--) args.push(as[i]); }
    public void _push(Object a) { args.push(a); }
    public Object _pop() { return args.pop(); }

    /// rule that requires a Symbol and runs the corresponding rule
    public Object apply() {
        Object r = _pop();
        if (!(r instanceof String)) { ERROR.last = "apply() must receive a string"; return ERROR; }
        try {
          return _jump((String) r);
        } catch (AssertionError e) {
          return _jump(((String) r).intern());
        }
    }

    /// hasRule; returns true or false, depending on if the given rule exists
    public boolean hasRule() {
        Object r = _pop();
        return hasRule(r);
    }

    public boolean hasRule(Object r) {
        if (!(r instanceof String)) return false;
        return (Boolean) _has(((String) r).intern());
    }

    /// str; next element must be given string
    public Object str() {
        Object r = _pop();
        if (!(r instanceof String)) throw new IllegalArgumentException("'str' must receive a String; not: "+ r);
        return _str((String) r);
    }

    /// sym; next element must be given symbol
    public Object sym() {
        Object r = _pop();
        if (!(r instanceof String)) throw new IllegalArgumentException("'sym' must receive a String; not: "+ r);
        return _sym((String) r);
    }

    /// '_'
    public Object _any() {
        if (! args.isEmpty()) return args.pop();

        if (_string != null) {
            if (_pos < _string.length()) {
                char c = _string.charAt(_pos++);
                if (c == '\n' || (c == '\r' && _cpeek() != '\n')) {
                    if (!lines.containsKey(_pos)) {
                        lines.put(_pos, lines.size());
                    }
                }
                return c;
            } else {
                return ERROR;
            }
        }
        if (_list != null)
            if (_pos < _list.length) return _exit(_list[_pos++]); else return ERROR;
        throw new IllegalStateException("no _list nor _string??");
    }

    /// empty; returns an empty string (or null when parsing lists) without consuming any input
    public Object empty() {
        if (_string != null) return "";
        return null;
    }

    /// returns current position in stream, counted by every success of apply(nl)
    public Position pos() {
        return pos(_pos);
    }
    
    public Position pos(int pos) {
        if (_string == null)
            throw new IllegalStateException("'pos' is only available in string parsing");

        int linepos = lines.tailMap(pos).firstKey();
        int line = lines.get(linepos);

        // TODO actually keep track of every apply(nl) that succeeds
        return new Position(pos, linepos, line);
    }

    public Object col() {
        if (_string == null)
            throw new IllegalStateException("'col' is only available in string parsing");
        int pos = _pos - 1;

        while (pos >= 0 && _string.charAt(pos) != '\n') pos--;
        return _pos - pos - 1;
    }

    public Object build_node(String name, List<?> children, int start_pos, int end_pos) {
        Ast node = new Ast(name, children);
        node.start_position_set(pos(start_pos));
        node.end_position_set(pos(end_pos));
        return node;
    }

    public Object build_node(String name, int start_pos, int end_pos) {
        Ast node = new Ast(name);
        node.start_position_set(pos(start_pos));
        node.end_position_set(pos(end_pos));
        return node;
    }

    public Object build_node(String name, Object children, int start_pos, int end_pos) {
        return build_node(name, (List<?>)children, start_pos, end_pos);
    }

    public String text(int start, int end) {
      return _string.substring(start, end);
    }

    public char _cpeek() {
        if (_pos < _string.length()) {
            return _string.charAt(_pos);
        } else {
            return '\0';
        }
    }

    public String _rpeek() {
      if (_pos == 0 || _string == null) {
        return "";
      }
      return _string.substring(_pos - 1, _pos);
    }

    public Object _peek() {
        if (_string != null)
            if (_pos < _string.length()) return _string.charAt(_pos); else return ERROR;
        if (_list != null)
            if (_pos < _list.length) return _list[_pos]; else return ERROR;
        throw new IllegalStateException("no _list nor _string??");
    }

    /// returns success if the end of file or list has been reached; same as `end: ~_;`
    public Object end() {
        if (_peek() == ERROR) return null; else { ERROR.last = "end of input"; return ERROR; }
    }

    public Object __end__() {
      return end();
    }

    /// '.' parses as much whitespace as possible, override the default `ws: nl | sp;` rule to define the whitespace
    public Object ws() {
        if (_string == null)
            throw new IllegalStateException("whitespace ('.') is only available in string parsing");
        do {
            if (_peek() == ERROR) return null;
            char c = _cpeek();
            if (!(c == ' ' || c == '\t' || c == '\f' || c == '\n' | c == '\r')) { break; }
            _any();
        } while (true);
        return null;
    }

    /// '"..."' parses a string when string parsing
    public Object _str(String s) {
        trace("try _str():", s);
        _enter("'"+s+"'");
        if (_string == null)
            throw new IllegalStateException("string ('\""+ s +"\"') is only available in string parsing");
        int p = _pos;
        for (int i = 0; i < s.length(); i++) {
            if (_peek() == ERROR) { _pos = p; ERROR.last = "'"+ s +"'"; return _exit(ERROR); }
            if (_cpeek() != s.charAt(i)) { _pos = p; ERROR.last = "'"+ s +"'"; return _exit(ERROR); }
            _any();
        }
        return _exit(trace(" ok _str():", s));
    }

    /// '`...' parses a string based symbols when list parsing (e.g. `new Object[] { "hello" }` matches `[ `hello ]`)
    public Object _sym(String s) {
        trace("try _sym():", s);
        if (_list == null)
            throw new IllegalStateException("symbol ('`"+ s +"') is only available in list parsing");
        if (_peek().equals(s)) { _any(); return trace(" ok _sym():",s); } else return ERROR;
    }

    public Object _char(String s) {
        _enter("["+s+"]");
        if (_string == null)
            throw new IllegalStateException("charRange is only available in string parsing");
        if (_peek() == ERROR) return _exit(ERROR);
        char c = _cpeek();
        if (s.indexOf(c) >= 0) { _any(); return _exit(c); }
        return _exit(ERROR);
    }

    /// nl; parses a single newline
    public Object nl() {
        return _char("\n\r");
    }

    /// sp; parses a single space
    public Object sp() {
        return _char(" \t\f");
    }

    public Object _charRange(char b, char e) {
        if (_string == null)
            throw new IllegalStateException("charRange is only available in string parsing");
        if (_peek() == ERROR) return ERROR;
        char c = _cpeek();
        if (c >= b && c <= e) { _any(); return c; }
        return ERROR;
    }

    /// default rule that parses [0-9]
    public Object digit() { return _charRange('0', '9'); }

    /// default rule that parses [a-zA-Z]
    public Object letter() {
        Object r = _charRange('a', 'z'); if (r != ERROR) return r;
        return _charRange('A', 'Z');
    }

    public String join(Object ls) { return join(ls, ""); }

    /// helper that folds an Array or ArrayList into a single string (using toString())
    public String join(Object ls, String sep) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if (ls instanceof Object[]) {
            for (Object o : (Object[])ls) {
                if (first) first = false; else sb.append(sep);
                sb.append(o.toString());
            }
        } else if (ls instanceof List) {
            for (Object o : (List<?>)ls) {
                if (first) first = false; else sb.append(sep);
                sb.append(o.toString());
            }
        } else if (ls != null ){
            throw new IllegalArgumentException("'join' must receive a List or Object[]. Got " + ls.getClass().getName());
        }
        return sb.toString();
    }

    // helper that concatenates two Arrays or Lists together
    public Object concat(Object ls, Object rs) {
        if (ls instanceof Object[]) {
            Object[] la = (Object[]) ls;
            if (rs instanceof Object[]) {
                Object[] ra = (Object[]) rs;
                Object na = Arrays.copyOf(la, la.length + ra.length);
                System.arraycopy(rs, 0, na, la.length, ra.length);
                return na;
            } else if (rs instanceof List) {
                List ra = (List<?>) rs;
                Object[] na = Arrays.copyOf(la, la.length + ra.size());
                for (int i = 0; i < ra.size(); i++) na[la.length + i] = ra.get(i);
                return na;
            }
        } else if (ls instanceof List) {
            List<?> la = (List<?>) ls;
            if (rs instanceof Object[]) {
                Object[] ra = (Object[]) rs;
                ArrayList na = new ArrayList(la);
                na.addAll(Arrays.asList(ra));
                return na;
            } else if (rs instanceof List) {
                List ra = (List<?>) rs;
                ArrayList na = new ArrayList(la);
                na.addAll(ra);
                return na;
            }
         }

        throw new IllegalArgumentException("'concat' must receive two Lists or Object[]s. Got " + ls.getClass().getName() + " and " + rs.getClass().getName());
    }

    public Object _listBegin() {
        if (_list == null)
            throw new IllegalStateException("list ('[ ... ]') operations only available in list parsing");

        Object ls = _peek();
        Object[] list = null;
        if (ls instanceof Object[]) {
            list = (Object[])ls;
        } else if (ls instanceof ArrayList) {
            list = ((ArrayList<?>)ls).toArray();
        } else if (ls instanceof List) {
            list = new ArrayList((List)ls).toArray();
        } else {
            return ERROR;
        }
        _any();

        _stack = new State(_stack, _pos, _list, _positions);
        _pos = 0;
        _list = list;
        _positions = new SparseArrayList<HashMap<String, Memoize>>();
        return null;
    }

    public void _listEnd() {
        _pos = _stack.pos;
        _list = _stack.list;
        _positions = _stack.positions;
        _stack = _stack.prev;
    }

    public Object _jump(String r) {
        throw new AssertionError("_jump: rule '"+ r +"' does not exist; or not properly implemented yet");
    }
    public boolean _has(String r) {
        return false;
    }
    
    public Token build_token(int type, int pos, int start) {
      return new Token(type, pos, start, _pos);
    }
    
    public Object lex() {
      return ERROR;
    }
    
    public Object _lex(int type) {
      if (cached_token.pos != _pos) {
        Object t = lex();
        if (t == ERROR) {
          cached_token = new Token(-1, _pos, _pos, _pos);
        } else {
          cached_token = (Token)t;
        }
      }
      if (cached_token.type == type) {
        _pos = cached_token.endpos;
        return cached_token;
      } else {
        _pos = cached_token.pos;
        return ERROR;
      }
    }
    
    public class Token {
      public Token(int type, int pos, int start, int end) {
        this.type = type;
        this.pos = pos;
        this.startpos = start == -1 ? pos : start;
        this.endpos = end;
      }
      
      public final int type;
      public final int pos;
      public final int startpos;
      public final int endpos;
      
      public String text() {
        return _string.substring(startpos, endpos);
      }
      
      public boolean space_seen() {
        return pos != startpos;
      }
    }
    
    
}

