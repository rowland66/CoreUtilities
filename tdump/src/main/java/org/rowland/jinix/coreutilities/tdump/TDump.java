package org.rowland.jinix.coreutilities.tdump;

import io.airlift.airline.*;
import io.airlift.airline.model.CommandMetadata;
import org.rowland.jinix.lang.JinixRuntime;

import javax.inject.Inject;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.rmi.RMIConnection;
import javax.management.remote.rmi.RMIConnector;
import javax.management.remote.rmi.RMIServer;
import java.io.IOException;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

@Command(name="tdump", description="Process thread dump utility")
public class TDump {

    @Inject
    private HelpOption helpOption;

    @Inject
    private CommandMetadata commandMetadata;

    @Arguments(title="process ID", description="process ID of the process to thread dump", required=true)
    private String pidArg;

    private static String pid;

    public static void main(String[] args) {

        TDump tdump = null;
        SingleCommand<TDump> command = SingleCommand.singleCommand(TDump.class);
        try {
            tdump = command.parse(args);
        } catch (ParseArgumentsMissingException | ParseArgumentsUnexpectedException e) {

            System.err.println(e.getMessage());
            return;
        }

        if (tdump.helpOption.showHelpIfRequested()) {
            return;
        }

        tdump.run();
    }

    private void run() {
        pid = pidArg;

        try {
            Integer.parseInt(pid);
        } catch (NumberFormatException e) {
            System.err.println("Invalid argument: "+pid);
            return;
        }

        RMIServer remoteMBeanServer = (RMIServer) JinixRuntime.getRuntime().lookup("/proc/" + pid + "/mbeans");

        if (remoteMBeanServer == null) {
            System.err.println("Unknown pid: "+pid);
            return;
        }

        try {
            JMXConnector connector = new RMIConnector(remoteMBeanServer, null);
            connector.connect();
            try {
                ObjectName objectName = new ObjectName("java.lang", "type", "Threading");
                ThreadMXBean mxbeanProxy = JMX.newMXBeanProxy(connector.getMBeanServerConnection(),
                        objectName,  ThreadMXBean.class);
                ThreadInfo[] threadInfo = mxbeanProxy.dumpAllThreads(false, false);
                printThreadInfo(threadInfo);
            } finally {
                connector.close();
            }
        } catch (MalformedObjectNameException e){
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void printThreadInfo(ThreadInfo[] threadInfo) {
        System.out.println("Thread Dump for Jinix process: "+pid);
        for (ThreadInfo ti : threadInfo) {
            System.out.println("\""+ti.getThreadName()+"\""+" #"+ti.getThreadId());
            for (StackTraceElement st : ti.getStackTrace()) {
                System.out.println("   "+st.toString());
            }
            System.out.println();
        }
    }
}
