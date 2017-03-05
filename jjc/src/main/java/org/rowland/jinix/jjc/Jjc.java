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
        newArgs[0] = "-endorseddirs";
        newArgs[1] = "/bin/jdk/lib/endorsed";
        newArgs[2] = "-bootclasspath";
        newArgs[3] = "/bin/jdk/jre/lib/resources.jar" + File.pathSeparator +
            "/bin/jdk/jre/lib/rt.jar" + File.pathSeparator +
            "/bin/jdk/jre/lib/sunrsasign.jar" + File.pathSeparator +
            "/bin/jdk/jre/lib/jsse.jar" + File.pathSeparator +
            "/bin/jdk/jre/lib/jce.jar" + File.pathSeparator +
            "/bin/jdk/jre/lib/charsets.jar" + File.pathSeparator +
            "/bin/jdk/jre/lib/jfr.jar";
        newArgs[4] = "-extdirs";
        newArgs[5] = "/bin/jdk/jre/lib/ext";
        //newArgs[6] = "-proc:none";
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
