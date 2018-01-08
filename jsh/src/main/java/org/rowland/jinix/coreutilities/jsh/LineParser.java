package org.rowland.jinix.coreutilities.jsh;

import org.rowland.jinix.lang.JinixRuntime;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hand-written scanner/parser with Bash like quoting rules:
 *
 *  1. inside single quotes, all characters are printed literally.
 *  2. inside double quotes, all characters are printed literally
 *     except variables prefixed by '$' and backslashes followed by
 *     either a double quote or another backslash.
 *  3. outside of any quotes, backslashes are treated as escape
 *     characters and not printed (unless they are themselves escaped)
 *  4. quote context can switch mid-token if there is no whitespace
 *     between the two quote contexts (e.g. all'one'"token" parses as
 *     "allonetoken")
 *  5. Pipe characters '|' are recognized as command delimiters outside
 *     of quotes
 *  6. Greater than '>' and less than '<' symbols are recognized outside
 *     of quotes
 */
public class LineParser {

    static public Queue<String[]> parse(String s) {
        char SQ = '\'';
        char DQ = '"';
        char DS = '$';
        char BS = '\\';
        char PIPE = '|';
        char AMPERSAND = '&';
        char GT = '>';
        char LT = '<';
        char WS = ' ';
        char quote = 0;
        boolean esc = false;
        String out = "";
        boolean isGlob = false;
        Queue<String[]> rtrnStack = new ArrayDeque<String[]>();
        List<String> argList = new ArrayList<String>(10);

        for (int i = 0, len = s.length(); i < len; i++) {
            char c = s.charAt(i);
            isGlob = isGlob || (quote == 0 && (c == '*' || c == '?'));
            if (esc) {
                out += c;
                esc = false;
            } else if (quote > 0) {
                if (c == quote) {
                    quote = 0;
                } else if (quote == SQ) {
                    out += c;
                } else { // Double quote
                    if (c == BS) {
                        i += 1;
                        c = s.charAt(i);
                        if (c == DQ || c == BS || c == DS) {
                            out += c;
                        } else {
                            out += BS + c;
                        }
                    } else if (c == DS) {
                        ParsedVar var = new ParsedVar();
                        i += parseEnvVar(i, s, var);
                        if (var.value != null) {
                            out += var.value;
                        }
                    } else {
                        out += c;
                    }
                }
            } else if (c == DQ || c == SQ) {
                quote = c;
            } else if (c == BS) {
                esc = true;
            } else if (c == DS) {
                ParsedVar var = new ParsedVar();
                i += parseEnvVar(i, s, var);
                if (var.value != null) {
                    out += var.value;
                }
            } else if (c == PIPE || c == AMPERSAND) {
                if (out.length() > 0) {
                    argList.add(out);
                    out = "";
                }
                if (c == AMPERSAND) {
                    argList.add("&");
                }
                String[] rtrn = new String[argList.size()];
                argList.toArray(rtrn);
                rtrnStack.add(rtrn);
                argList.clear();
            } else if (c == GT) {
                if (out.length() > 0 && !(out.charAt(out.length()-1) == GT)) {
                    argList.add(out);
                    out = "";
                }
                out += c;
            } else if (c == LT) {
                if (out.length() > 0 && !(out.charAt(out.length()-1) == LT)) {
                    argList.add(out);
                    out = "";
                }
                out += c;
            } else {
                if (c == WS) {
                    if (out.length() > 0) {
                        argList.add(out);
                        out = "";
                    } // else ignore the extra whitespace
                } else {
                    out += c;
                }
            }
        }
        if (out.length() > 0) {
            argList.add(out);
        }

        String[] rtrn = new String[argList.size()];
        argList.toArray(rtrn);
        rtrnStack.add(rtrn);
        return rtrnStack;
    }


    static int parseEnvVar(int i, String s, ParsedVar v) {
        i += 1;
        int varend;
        String varname;
        //debugger
        if (s.charAt(i) == '{') {
            i += 1;
            if (s.charAt(i) == '}') {
                throw new RuntimeException("Bad substitution: " + s.substring(i - 2, 3));
            }
            varend = s.indexOf('}', i);
            if (varend < 0) {
                throw new RuntimeException("Bad substitution: " + s.substring(i));
            }
            varname = s.substring(i, varend - 1);
            i = varend;
        } else if (Pattern.matches("[*@#?$!_\\-]", Character.toString(s.charAt(i)))) {
            varname = Character.toString(s.charAt(i));
            i += 1;
        } else {
            Matcher m  = Pattern.compile("[^\\w\\d_]").matcher(s.substring(i));
            if (!m.find()) {
                varname = s.substring(i);
                i = s.length();
            } else {
                varend = m.toMatchResult().start();
                varname = s.substring(i, varend);
                i += varend - 1;
            }
        }
        v.value = System.getProperty(varname);
        return i;
    }

    private static class ParsedVar {
        String value;
    }
}
