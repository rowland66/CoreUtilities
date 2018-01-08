package org.rowland.jinix.coreutilities.ps;

import org.apache.commons.cli.*;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.NameSpace;
import org.rowland.jinix.proc.ProcessData;
import org.rowland.jinix.proc.ProcessManager;

import javax.naming.Context;
import javax.naming.NamingException;
import java.rmi.RemoteException;

public class Ps {

    public static void main(String[] args) {

        try {
            CommandLine cmdLine = parseCommandLineOptions(args);
            if (cmdLine == null) return;

            int sessionId = JinixRuntime.getRuntime().getProcessSessionId();
            Context ns = JinixRuntime.getRuntime().getNamingContext();
            ProcessManager ps = (ProcessManager) ns.lookup(ProcessManager.SERVER_NAME);
            ProcessData[] pdArray = ps.getProcessData();
            System.out.println(String.format("%1$4s %2$4s %3$4s %4$4s %5$-6s %6$4s %7$-3s", "PID", "PPID", "PGID", "SESS", "TERM", "STAT", "CMD"));
            for (ProcessData pd : pdArray) {
                if (pd.parentId != -1 || cmdLine.hasOption("t")) {
                    if (cmdLine.hasOption("e") || pd.sessionId == sessionId) {
                        System.out.println(String.format("%1$4s %2$4s %3$4s %4$4s %5$-6s %6$4s %7$s",
                                pd.id,
                                pd.parentId,
                                pd.processGroupId,
                                pd.sessionId,
                                (pd.terminalId == -1 ? "?" : Integer.toString(pd.terminalId)),
                                (pd.state == ProcessManager.ProcessState.RUNNING ? "R" :
                                        (pd.state == ProcessManager.ProcessState.SUSPENDED ? "T" :
                                                (pd.state == ProcessManager.ProcessState.SHUTDOWN ? "Z" : "X"))),
                                pd.cmd));
                    }
                }
            }
        } catch (RemoteException e) {
            System.err.println("Failure retrieving process data from ProcessManager");
        } catch (NamingException e) {
            System.err.println("Internal error. Failed to find ProcessManager at: "+ProcessManager.SERVER_NAME);
        }
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("t", null, false, "include translator processes");
        options.addOption("e", null, false, "display all processes");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("");
            formatter.printHelp("ps",
                    "List process information",
                    options,
                    "",
                    true);
            return null;
        }
    }

}
