package org.rowland.jinix.signal.coreutilities;

import org.apache.commons.cli.*;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.proc.ProcessManager;

/**
 * A utility that sends a signal to the process or processes specified by each pid argument.
 */
public class Signal {
    private static final String SIGNAL_KILL = "KILL";
    private static final String SIGNAL_TERM = "TERM";
    private static final String SIGNAL_HANGUP = "HUP";
    private static final String SIGNAL_STOP = "STOP";
    private static final String SIGNAL_CONT = "CONT";

    public static void main(String[] args) {
        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        args = cmdLine.getArgs();

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

            if (cmdLine.hasOption(SIGNAL_HANGUP)) {
                JinixRuntime.getRuntime().sendSignal(pid, ProcessManager.Signal.HANGUP);
                continue;
            }

            if (cmdLine.hasOption(SIGNAL_STOP)) {
                JinixRuntime.getRuntime().sendSignal(pid, ProcessManager.Signal.STOP);
                continue;
            }

            if (cmdLine.hasOption(SIGNAL_CONT)) {
                JinixRuntime.getRuntime().sendSignal(pid, ProcessManager.Signal.CONTINUE);
                continue;
            }

            System.err.println("signal: No signal provided");
            break;
        }
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption(SIGNAL_KILL, null, false, "mercilessly terminate the process(s)");
        options.addOption(SIGNAL_TERM, null, false, "ask nicely to terminate the process(s)");
        options.addOption(SIGNAL_HANGUP, null, false, "tell the process(s) about a terminal hangup");
        options.addOption(SIGNAL_STOP, null, false, "stop the process(s) execution");
        options.addOption(SIGNAL_CONT, null, false, "continue the process(s) execution");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("[PID]...");
            formatter.printHelp("signal",
                    "Send a signal to process(s) identified by PID(s).",
                    options,
                    "",
                    true);
            return null;
        }
    }

}
