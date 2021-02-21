package org.rowland.jinix.jjc;

import com.sun.tools.javac.Main;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * The Jinix Java Compiler.
 */
public class Jjc {

    public static void main(String[] args) {

        String[] newArgs = new String[args.length+6];
        newArgs[0] = "--system";
        newArgs[1] = "none";
        newArgs[2] = "--module-path";
        newArgs[3] = "/lib/jdk/modules";
        newArgs[4] = "--class-path";
        newArgs[5] = ".";
        System.arraycopy(args, 0, newArgs, 6, args.length);
        int rtrn = Main.compile(newArgs, new PrintWriter(new OutputStreamWriter(System.out)));
        switch (rtrn) {
            case 1:
                System.err.println("jjc: Completed with error");
                break;
            case 2:
                System.err.println("jjc: Bad command line arguments");
                break;
            case 3:
                System.err.println("jjc: System error or resource exhaustion");
                break;
            case 4:
                System.err.println("jjc: Abnormal termination");
                break;
        }
        System.exit(rtrn);
    }
}
