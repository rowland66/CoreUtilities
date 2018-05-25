package org.rowland.jinix.coreutilities.yes;


import org.apache.commons.cli.*;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.translator.TimeKeeper;

import javax.naming.Context;
import javax.naming.NamingException;
import java.rmi.RemoteException;
import java.util.Date;


/**
 * A simple utility to repeatedly output a string.
 */
public class yes {

    public static void main(String[] args) {
        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        System.out.println("Start");

        Thread testThread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i=0; i<10; i++) {
                    System.out.println(i);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, "Test Thread");

        testThread.start();

        System.out.println("Thread started");

        /**
        try {
            Context ctx = JinixRuntime.getRuntime().getNamingContext();
            TimeKeeper keeper = (TimeKeeper) ctx.lookup("/home/testTrans");

            keeper.setTimeOffset(60*60*1000);

            TimeKeeper.TimeWithOffset currentOffset = keeper.getTimeWithOffset();

            System.out.println("Current time: " + new Date(currentOffset.getTime()));
            System.out.println("Current offset: " + currentOffset.getOffset());
        } catch (NamingException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        */
        /**
        args = cmdLine.getArgs();

        StringBuffer outputBuffer = new StringBuffer();
        String output;

        if (args.length == 0) {
            output = "y";
        } else {
            for (String arg : args) {
                outputBuffer.append(arg).append(" ");
            }
            output = outputBuffer.substring(0, outputBuffer.length() - 1);
        }

        while(true) {
            System.out.println(output);
        }
        */
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("help", null, false, "display this help and exit");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("[STRING]...");
            formatter.printHelp("yes",
                    "Reapeatedly output a line with all specified STRING(s), or 'y'.",
                    options,
                    "",
                    true);
            return null;
        }
    }
}