package org.rowland.jinix.coreutilities.cat;

import org.apache.commons.cli.*;

import java.io.*;
import java.util.Map;
import java.util.Properties;

/**
 * The cat utility shall read files in sequence and shall write their contents to the standard
 * output in the same sequence.
 */
public class Cat {

    public static void main(String[] args) {

        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        args = cmdLine.getArgs();
        int c, i = 0;
        int lineCounter = 1;
        int prevChar = 0;
        byte[] lineBuffer = new byte[2048];

        for (String arg : args) {
            InputStream is = null;
            try {
                if (arg.equals("-")) {
                    is = new BufferedInputStream(System.in);
                } else {
                    File f = new File(arg);
                    if (f.isDirectory()) {
                        System.err.println("cat: "+arg+": Is a directory");
                        continue;
                    }
                    is = new BufferedInputStream(new FileInputStream(f));
                }

                i = 0;
                while ((c = is.read()) > -1) {
                    if (prevChar == 0x0a && cmdLine.hasOption("n")) {
                        System.out.print(String.format("% 10d: ",lineCounter++));
                    }

                    if(c == 0x0a && cmdLine.hasOption("E")) {
                        lineBuffer[i++] = '$';
                    }

                    if (lineCounter==1 && cmdLine.hasOption("n")) {
                        System.out.print(String.format("% 10d: ",lineCounter++));
                    }

                    lineBuffer[i++] = (byte) c;

                    if (c == 0x0a || i == 2048) {
                        System.out.write(lineBuffer, 0, i);
                        if (System.out.checkError()) {
                            System.err.println("cat: IOException writing output");
                            is.close();
                            return;
                        }
                        i=0;
                    }
                    prevChar = c;
                }
            } catch (FileNotFoundException e) {
                System.err.println("cat: "+arg+": No such file or directory");
            } catch (IOException e) {
                System.err.println("cat: "+arg+": IOException reading input: "+e.getMessage());
            } finally {
                if (i > 0) {
                    System.out.write(lineBuffer, 0, i);
                }
                System.out.flush();
                try {
                    if (is != null) is.close();
                } catch (IOException e) {
                    System.err.println("cat: "+arg+" IOException closing input stream");
                }
            }
        }
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("E", "show-ends", false, "disaply $ at the end of each line");
        options.addOption("n", "number", false, "number all output lines");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("cat [OPTION]... [FILE]...",
                    "Concatenate FILE(s), or standard input, to standard output.",
                    options,
                    "With no FILE, or when FILE is -, read standard input.",
                    false);
            return null;
        }
    }
}
