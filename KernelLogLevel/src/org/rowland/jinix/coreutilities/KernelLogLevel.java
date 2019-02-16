package org.rowland.jinix.coreutilities;

import org.apache.commons.cli.*;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.logger.LogServer;
import org.rowland.jinix.logger.UnknownLoggerException;

import java.rmi.RemoteException;
import java.util.logging.Level;

/**
 * Created by rsmith on 3/25/2017.
 */
public class KernelLogLevel {

    public static void main(String[] args) {

        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        args = cmdLine.getArgs();

        try {
            LogServer server = (LogServer) JinixRuntime.getRuntime().lookup(LogServer.SERVER_NAME);
            if (cmdLine.hasOption("d") || cmdLine.hasOption("h")) {
                if (args.length == 1) {
                    try {
                        int level = server.getLevel(args[0]);
                        if (cmdLine.hasOption("h")) {
                            System.out.println(Level.parse(Integer.toString(level)).getLocalizedName());
                        } else {
                            System.out.println(Integer.toString(level));
                        }
                    } catch (UnknownLoggerException e) {
                        System.err.println("Unknown logger");
                    }
                } else {
                    for (String arg : args) {
                        try {
                            int level = server.getLevel(arg);
                            if (cmdLine.hasOption("h")) {
                                System.out.println(arg+": "+Level.parse(Integer.toString(level)).getLocalizedName());
                            } else {
                                System.out.println(arg+": "+Integer.toString(level));
                            }
                        } catch (UnknownLoggerException e) {
                            System.err.println(arg+": Unknown logger");
                        }
                    }
                }
            } else if (cmdLine.hasOption("s")) {
                String newLevel = cmdLine.getOptionValue("s");
                for (String arg : args) {
                    try {
                        server.setLevel(arg, newLevel);
                    } catch (UnknownLoggerException e) {
                        if (args.length == 1) {
                            System.err.println("Unknown logger");
                        } else {
                            System.err.println(arg + ": Unknown logger");
                        }
                    }
                }
            }
        } catch (RemoteException e) {
            System.err.println("internal error: "+(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
        }
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("d", null, false, "display the logging level");
        options.addOption("h", "human-readable", false, "display the logging level as a human readable string");
        options.addOption("s", null, true, "set the logging level");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("[SERVER]...");
            formatter.printHelp("kernelloglevel",
                    "Display or set the current kernel log level for a kernel SERVER...",
                    options,
                    "",
                    true);
            return null;
        }
    }

}
