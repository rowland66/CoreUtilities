package org.rowland.jinix.signal.coreutilities;

import org.apache.commons.cli.*;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.proc.ProcessManager;

/**
 * A utility that sends a signal to the process or processes specified by each pid argument.
 */
public class Signal {
    private static final String SIGNAL_SHUTDOWN = "SHUTDOWN";
    private static final String SIGNAL_KILL = "KILL";
    private static final String SIGNAL_TERM = "TERM";

    public static void main(String[] args) {
        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        args = cmdLine.getArgs();

        if (cmdLine.hasOption(SIGNAL_SHUTDOWN)) {
            JinixRuntime.getRuntime().sendSignal(0, ProcessManager.Signal.SHUTDOWN);
            return;
        }

        for (String arg : args) {
            int pid;
            try {
                pid = Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                System.err.println("signal: Invalid argument: " + arg);
                continue;
            }
            if (cmdLine.hasOption(SIGNAL_KILL)) {
                JinixRuntime.getRuntime().sendSignal(pid, ProcessManager.Signal.KILL);
                continue;
            }

            if (cmdLine.hasOption(SIGNAL_TERM)) {
                JinixRuntime.getRuntime().sendSignal(pid, ProcessManager.Signal.TERMINATE);
                continue;
            }

            System.err.println("signal: No signal provided");
            break;
        }
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption(SIGNAL_KILL, null, false, "mercilessly terminate the process(s) PID");
        options.addOption(SIGNAL_TERM, null, false, "ask nicely to terminate the process(s) PID");
        options.addOption(SIGNAL_SHUTDOWN, null, false, "shutdown the system");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("[PID]...");
            formatter.printHelp("signal",
                    "Send a signal to PIDs.",
                    options,
                    "",
                    true);
            return null;
        }
    }

}
