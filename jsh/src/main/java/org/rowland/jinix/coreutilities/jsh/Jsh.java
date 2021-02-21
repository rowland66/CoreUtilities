package org.rowland.jinix.coreutilities.jsh;

import org.jline.builtins.Completers;
import org.jline.reader.LineReader;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.reader.impl.completer.FileNameCompleter;
import org.jline.terminal.Terminal;
import org.rowland.jinix.exec.InvalidExecutableException;
import org.rowland.jinix.io.*;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.JinixSystem;
import org.rowland.jinix.lang.ProcessSignalHandler;
import org.rowland.jinix.proc.ProcessManager;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.*;

/**
 * A basic command line shell. J is for Java.
 */
public class Jsh {

    private static final String INPUT_REDIRECT = "<";
    private static final String OUTPUT_REDIRECT = ">";
    private static final Set<String> BUILTINS = new HashSet<>(Arrays.asList("exit", "cd", "pwd", "set", "jobs", "fg", "bg"));
    private static Job currentJob = null;
    private static Job previousJob = null;
    private static Job foregroundJob = null;
    private static JobMap jobMap = new JobMap();
    private static List<String> promptMessageList = new ArrayList<>(5);
    private static Terminal term;

    public static void main(String[] args) {

        // Special handling for the console where the shell process will be a process group leader and will not be
        // allowed to call setProcessSessionId();
        if (JinixRuntime.getRuntime().getPid() != JinixRuntime.getRuntime().getProcessGroupId()) {
            JinixRuntime.getRuntime().setProcessSessionId();
        }

        if (args.length > 0) {
            String initDirecotry = args[0];
            JinixFile f = new JinixFile(initDirecotry);
            if (f.exists() && f.isDirectory()) {
                JinixSystem.setJinixProperty(JinixRuntime.JINIX_ENV_PWD, initDirecotry);
            } else {
                System.err.println("Invalid initial directory: "+initDirecotry);
                return;
            }
        }

        LineReader lineReader = null;
        try {
            term = new JinixTerminal("Jsh Terminal", "xterm-256color", Charset.defaultCharset(), new TerminalSignalHandler());
            lineReader = new LineReaderImpl(term);
            lineReader.setOpt(LineReader.Option.USE_FORWARD_SLASH);
            ((LineReaderImpl) lineReader).setCompleter(new JinixFileNameCompleter());
        } catch (IOException e) {
            System.err.println("jsh: I/O Initializing JLine Terminal");
        }

        // Set up event handler
        JinixRuntime.getRuntime().registerSignalHandler(new ProcessSignalHandler() {
            @Override
            public boolean handleSignal(ProcessManager.Signal signal) {
                if (signal == ProcessManager.Signal.CHILD) {
                    ProcessManager.ChildEvent childEvent = JinixRuntime.getRuntime().waitForChild(false);
                    if (childEvent != null) {
                        handleChildEvent(childEvent);
                    }
                    return true;
                }
                if (signal == ProcessManager.Signal.TSTOP) {
                    if (foregroundJob != null) {
                        JinixRuntime.getRuntime().sendSignalProcessGroup(foregroundJob.processGroupId, ProcessManager.Signal.STOP);
                    }
                    return true;
                }
                if (signal == ProcessManager.Signal.TERMINATE) {
                    if (foregroundJob != null) {
                        JinixRuntime.getRuntime().sendSignalProcessGroup(foregroundJob.processGroupId, ProcessManager.Signal.TERMINATE);
                    }
                }
                if (signal == ProcessManager.Signal.HANGUP) {
                    for (Job j : jobMap.jobList()) {
                        JinixRuntime.getRuntime().sendSignalProcessGroup(j.processGroupId, ProcessManager.Signal.HANGUP);
                    }
                }
                if (signal == ProcessManager.Signal.WINCH) {
                    term.raise(Terminal.Signal.WINCH);
                }
                return false;
            }
        });

        JinixRuntime.getRuntime().setForegroundProcessGroupId(JinixRuntime.getRuntime().getProcessGroupId());

        try {
            while(true) {
                try {
                    boolean executeCmdInBackground = false;

                    // Synchronize the output and the clearing of the list so that messages added during signal handling
                    // are not lost.
                    synchronized (promptMessageList) {
                        if (!promptMessageList.isEmpty()) {
                            for (String promptMessage : promptMessageList) {
                                System.out.println(promptMessage);
                            }
                            promptMessageList.clear();
                        }
                    }

                    //System.out.print(">");
                    //System.out.flush();

                    //enable child wait interupts here
                    String cmdLine = lineReader.readLine(">");
                    //String cmdLine = input.readLine();
                    //disable child interrupts here

                    if (cmdLine == null) {
                        System.exit(0);
                    }

                    cmdLine = cmdLine.trim();
                    if (cmdLine.isEmpty()) {
                        continue;
                    }

                    if (cmdLine.endsWith("&")) {
                        cmdLine = cmdLine.substring(0, cmdLine.length() - 1).trim();
                        executeCmdInBackground = true;
                    } else {
                        JinixRuntime.getRuntime().setForegroundProcessGroupId(-1);
                    }

                    Queue<String[]> cmdQueue = LineParser.parse(cmdLine);
                    int execJobId = 0;
                    try {
                        execJobId = executeCmd(cmdQueue, null, -1, 0, null);
                    } catch (CommandExecutionException e) {
                        JinixRuntime.getRuntime().setForegroundProcessGroupId(JinixRuntime.getRuntime().getProcessGroupId());
                        System.err.println(e.getMessage());
                        execJobId = e.getJobId();
                    }

                    if (execJobId > 0 && !executeCmdInBackground || foregroundJob != null) {
                        if (execJobId > 0) {
                            foregroundJob = jobMap.getByJobId(execJobId);
                        }

                        JinixRuntime.getRuntime().setForegroundProcessGroupId(foregroundJob.processGroupId);
                        while (foregroundJob.isActive()) {
                            ProcessManager.ChildEvent childEvent = JinixRuntime.getRuntime().waitForChild(false);
                            handleChildEvent(childEvent);
                        }
                        if (foregroundJob.isSuspended()) {
                            if (foregroundJob != currentJob) {
                                previousJob = currentJob;
                                currentJob = foregroundJob;
                            }
                        }
                        foregroundJob = null;
                    }

                    if (execJobId > 0 && executeCmdInBackground) {
                        previousJob = currentJob;
                        currentJob = jobMap.getByJobId(execJobId);
                    }
                    JinixRuntime.getRuntime().setForegroundProcessGroupId(JinixRuntime.getRuntime().getProcessGroupId());
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("jsh: I/O Error reading input");
        }
    }

    private static Job handleChildEvent(ProcessManager.ChildEvent childEvent) {
        Job j = jobMap.getByProcessGroupId(childEvent.getProcessGroupId());
        switch (childEvent.getState()) {
            case SHUTDOWN:
                j.shutdownProcessCount++;
                break;
            case SUSPENDED:
                j.suspendedProcessCount++;
                break;
        }
        if (j.isSuspended()) {
            promptMessageList.add(displayJob(j, ""));
        }
        if (j.isDone()) {
            jobMap.removeByProcessGroupId(j.processGroupId);
            if (j != foregroundJob) {
                promptMessageList.add(displayJob(j, ""));
            }
        }
        return j;
    }

    private static int executeCmd(Queue<String[]> cmdQueue, JinixFileDescriptor previousCmdOutput, int processGroupId, int executeCmdCnt, Job job)
            throws CommandExecutionException, RemoteException {
        JinixRuntime runtime = JinixRuntime.getRuntime();


        String[] cmd = cmdQueue.remove();
        executeCmdCnt++;

        InputStream cmdInput = null;
        try {
            JinixFileDescriptor cmdInputFd;
            if (previousCmdOutput == null) {
                cmdInput = System.in;
                cmdInputFd = null;
            } else {
                cmdInputFd = previousCmdOutput;
                cmdInput = new JinixFileInputStream(previousCmdOutput);
            }

            JinixFileDescriptor cmdOutputFd;
            JinixFileDescriptor cmdOutputPipedInputFd;
            PrintStream cmdOutput;
            if (cmdQueue.isEmpty()) {
                cmdOutput = System.out;
                cmdOutputFd = null;
                cmdOutputPipedInputFd = null;
            } else {
                JinixPipe fdPair = JinixRuntime.getRuntime().pipe();
                cmdOutputFd = fdPair.getOutputFileDescriptor();
                cmdOutput = new PrintStream(
                        new JinixFileOutputStream(cmdOutputFd));
                cmdOutputPipedInputFd = fdPair.getInputFileDescriptor();
            }

            JinixFileDescriptor cmdErrorFd = null;
            OutputStream cmdError = System.err;

            if (cmd.length == 0 || cmd[0].equals("")) {
                return 0;
            }

            String expandedCmd = null;
            boolean builtinCmd = false;
            if (!BUILTINS.contains(cmd[0])) {
                expandedCmd = expandCmd(cmd[0], job);
                if (expandedCmd == null) {
                    return 0;
                }
            } else {
                expandedCmd = cmd[0];
                builtinCmd = true;
            }

            ArrayList<String> execArgsList = new ArrayList<String>(cmd.length-1);
            List<Redirection> redirectionList = new LinkedList<Redirection>();
            for (int i=1; i<cmd.length; i++) {
                if (cmd[i].contains(INPUT_REDIRECT) ||
                        cmd[i].contains(OUTPUT_REDIRECT)) {
                    try {
                        i = parseRedirection(i, cmd, redirectionList);
                    } catch (IOException e) {
                        throw new CommandExecutionException(cmd[0]+": "+e.getMessage(), (job != null ? job.jobId : 0));
                    } catch (IllegalArgumentException e) {
                        throw new CommandExecutionException(cmd[0]+": "+e.getMessage(), (job != null ? job.jobId : 0));
                    }
                } else {
                    execArgsList.add(cmd[i]);
                }
            }
            String[] execArgs = new String[execArgsList.size()];
            execArgsList.toArray(execArgs);

            for (Redirection r : redirectionList) {
                if (r.fileDescriptorIndex == 0) {
                    cmdInputFd = r.fd;
                }
                if (r.fileDescriptorIndex == 1) {
                    cmdOutputFd = r.fd;
                }
                if (r.fileDescriptorIndex == 2) {
                    cmdErrorFd = r.fd;
                }
            }

            try {
                int pid = -1;
                if (builtinCmd) {
                    JinixRuntime.getRuntime().setForegroundProcessGroupId(JinixRuntime.getRuntime().getProcessGroupId());
                    processBuiltins(expandedCmd, execArgs, cmdOutput, cmdError);
                    if (foregroundJob == null) { // The fg and bg builtins needs special handling because the set the foreground job.
                        JinixRuntime.getRuntime().setForegroundProcessGroupId(-1);
                    }
                } else {
                    pid = runtime.exec(JinixSystem.getJinixProperties(), expandedCmd, execArgs,
                            processGroupId, // the first command value of -1 begins a new process group
                            cmdInputFd, cmdOutputFd, cmdErrorFd);
                }

                if (processGroupId == -1 && pid > -1) {
                    if (pid > -1) {
                        processGroupId = pid;
                    }
                }

                if (pid > -1) {
                    if (job == null) {
                        job = new Job();
                        job.processGroupId = processGroupId;
                        job.cmdArray.add(cmd);
                        job.pidList.add(pid);
                        job.jobId = jobMap.add(job);
                    } else {
                        job.cmdArray.add(cmd);
                        job.pidList.add(pid);
                    }
                }

                // If we opened any of these file descriptors, we need to close them now since exec()
                // just duplicated them for the process it started.
                if (cmdInputFd != null) {
                    cmdInputFd.close();
                }
                if (cmdOutputFd != null) {
                    cmdOutputFd.close();
                }
                if (cmdErrorFd != null) {
                    cmdErrorFd.close();
                }

                if (cmdQueue.isEmpty()) {
                    if (pid > -1) {
                        return job.jobId;
                    } else {
                        return -1;
                    }
                } else {
                    if (pid > -1) {
                        return executeCmd(cmdQueue, cmdOutputPipedInputFd, processGroupId, executeCmdCnt, job);
                    } else {
                        System.err.println("jsh: builtins not supported in jobs");
                        return -1;
                    }
                }
            } catch (FileNotFoundException e) {
                throw new CommandExecutionException("'"+cmd[0]+"' is not recognized as an internal or external command.", (job != null ? job.jobId : 0));
            } catch (InvalidExecutableException e) {
                throw new CommandExecutionException("'"+e.getInvalidFile()+"' is not recognized as an internal or external command.", (job != null ? job.jobId : 0));
            }
        } catch (CommandExecutionException e) {
            executeCmdCnt--;
            throw e;
        } finally {
            if (previousCmdOutput != null) {
                try {
                    cmdInput.close();
                } catch (IOException e) {
                    System.err.println("jsh: I/O Error closing input pipe");
                }
            }
        }
    }

    private static void processBuiltins(String cmd, String[] args, PrintStream cmdOutput, OutputStream cmdError) throws CommandExecutionException {
        if (cmd.equals("exit")) {
            System.exit(0);
        }

        if (cmd.equals("cd")) {
            if (args.length > 0) {
                changeWorkingDirectory(args[0]);
            }
            return;
        }

        if (cmd.equals("pwd")) {
            cmdOutput.println(System.getProperty(JinixRuntime.JINIX_ENV_PWD));
            return;
        }

        if (cmd.equals("set")) {
            if (args.length == 0) {
                for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
                    System.out.println((String)entry.getKey()+"="+(String)entry.getValue());
                }
                return;
            }

            String arg=args[0].trim();
            if (!arg.contains("=")) {
                System.out.println(cmd+"="+System.getProperty(cmd));
                return;
            }
            if (arg.indexOf('=') == arg.length()-1) {
                JinixSystem.setJinixProperty(arg, null);
                return;
            }

            String[] tokens = arg.split("=");
            if (tokens.length == 2) {
                JinixSystem.setJinixProperty(tokens[0].trim(), tokens[1].trim());
                return;
            }
            System.err.println("jsh: Error processing set command");
            return;
        }

        if (cmd.equals("jobs")) {
            String options = "";
            List<String> jobSpecList = new ArrayList<>(5);
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("-") && args[i].length() > 1) {
                    options = options + args[i].substring(1);
                } else {
                    jobSpecList.add(args[i]);
                }
            }

            List<Job> jobList = new ArrayList<>(5);
            if (jobSpecList.isEmpty()) {
                for (Job j : jobMap.jobList()) {
                    String jobLine = displayJob(j, options);
                    if (jobLine != null) {
                        System.out.println(jobLine);
                    }
                }
            } else {
                for (String jobSpec : jobSpecList) {
                    Job j = parseJobSpec(jobSpec);
                    if (j != null) {
                        String jobLine = displayJob(j, options);
                        if (jobLine != null) {
                            System.out.println(jobLine);
                        }
                    } else {
                        System.err.println("-jsh: jobs: " + jobSpec + ": no such job");
                    }
                }
            }
            return;
        }

        if (cmd.equals("fg")) {
            String jobSpec;
            if (args.length > 0) {
                jobSpec = args[0];
            } else {
                jobSpec = "%+";
            }
            Job j = parseJobSpec(jobSpec);

            if (j == null) {
                System.err.println("-jsh: fg: "+jobSpec + ": no such job");
            } else {
                foregroundJob = j;
                foregroundJob.suspendedProcessCount = 0;
                JinixRuntime.getRuntime().setForegroundProcessGroupId(foregroundJob.processGroupId);
                JinixRuntime.getRuntime().sendSignalProcessGroup(j.processGroupId, ProcessManager.Signal.CONTINUE);
            }
            return;
        }

        if (cmd.equals("bg")) {
            String jobSpec;
            if (args.length > 0) {
                jobSpec = args[0];
            } else {
                jobSpec = "%+";
            }
            Job j = parseJobSpec(jobSpec);

            if (j == null) {
                System.err.println("-jsh: bg: "+jobSpec + ": no such job");
            } else {
                j.suspendedProcessCount = 0;
                JinixRuntime.getRuntime().sendSignalProcessGroup(j.processGroupId, ProcessManager.Signal.CONTINUE);
            }
            return;
        }
    }

    private static String displayJob(Job j, String options) {

        if (options.contains("r") && !j.isActive()) {
            return null;
        }
        if (options.contains("s") && !j.isSuspended()) {
            return null;
        }
        if (options.contains("p")) {
            System.out.println(j.processGroupId);
            return null;
        }
        String jobIndicator = " ";
        if (j == currentJob) jobIndicator = "+";
        if (j == previousJob) jobIndicator = "-";

        String status = "Running";
        if (j.isDone()) status = "Done";
        if (j.isSuspended()) status = "Stopped";

        if (options.contains("l")) {
            return String.format("[%1$d]%5$1s  %4$5d %2$-10s %3$s",
                    j.jobId,
                    status,
                    j.cmdLine(),
                    j.processGroupId,
                    jobIndicator);
        } else {
            return String.format("[%1$d]%4$1s  %2$-10s %3$s",
                    j.jobId,
                    status,
                    j.cmdLine(),
                    jobIndicator);
        }
    }

    private static Job parseJobSpec(String jobSpec) {
        Job j = null;
        try {
            if (jobSpec.startsWith("%")) {
                jobSpec = jobSpec.substring(1);
            }
            int jobId = Integer.parseInt(jobSpec);
            j = jobMap.getByJobId(jobId);
        } catch (NumberFormatException e) {
            if ((jobSpec.equals("+") || jobSpec.equals("%")) && currentJob != null && jobMap.getByJobId(currentJob.jobId) != null) {
                j = currentJob;
            } else if (jobSpec.equals("-") && previousJob != null && jobMap.getByJobId(previousJob.jobId) != null) {
                j = previousJob;
            } else {
                j = jobMap.getByCmdLine(jobSpec);
            }
        }
        return j;
    }

    private static String expandCmd(String cmd, Job job) throws CommandExecutionException {

        String pathExtStr = System.getProperty(JinixRuntime.JINIX_PATH_EXT);

        if (cmd.contains("/")) {
            if (!cmd.startsWith("/")) {
                cmd = System.getProperty(JinixRuntime.JINIX_ENV_PWD) + "/" + cmd;
            }
        } else {
            String pathCmd = findCmdInPath(cmd);
            if (pathCmd == null && !cmd.contains(".")) {
                if (pathExtStr != null && pathExtStr.length() > 0) {
                    String[] pathExts = pathExtStr.split(":");
                    for (String pathExt : pathExts) {
                        pathCmd = findCmdInPath(cmd + "." + pathExt);
                        if (pathCmd != null) break;
                    }
                }
            }

            if (pathCmd == null) {
                throw new CommandExecutionException("'"+cmd+"' is not recognized as an internal or external command.", (job != null ? job.jobId : 0));
            }

            if (pathCmd != null) {
                cmd = pathCmd;
            }
        }

        return cmd;
    }

    private static void changeWorkingDirectory(String newDirectoryString) throws CommandExecutionException {

        Path absoluteDirectory = Paths.get(newDirectoryString);
        if (!absoluteDirectory.isAbsolute()) {
            String wd = System.getProperty(JinixRuntime.JINIX_ENV_PWD);
            absoluteDirectory = Paths.get(wd).resolve(newDirectoryString);
        }

        JinixFile newDirectoryFile = new JinixFile(absoluteDirectory.toString());
        if (!newDirectoryFile.exists()) {
            throw new CommandExecutionException("jsh: cd: " + newDirectoryString + " No such file or directory", 0);
        }
        if (!newDirectoryFile.isDirectory()) {
            throw new CommandExecutionException("jsh: cd: "+newDirectoryString+" Not a directory", 0);
        }

        JinixSystem.setJinixProperty(JinixRuntime.JINIX_ENV_PWD, newDirectoryFile.getCanonicalPath());
    }

    private static String findCmdInPath(String cmd) {
        String cmdPath = System.getProperty(JinixRuntime.JINIX_PATH);
        String[] cmdPathTokens = cmdPath.split(":");
        for (String cmdPathToken : cmdPathTokens) {
            String fullCmdPath = cmdPathToken+"/"+cmd;
            JinixFile f = new JinixFile(fullCmdPath);
            if (f.exists() && !f.isDirectory()) {
                return fullCmdPath;
            }
        }

        return null;
    }

    private static class Redirection {
        static final byte REDIRECTION_FLAG_APPEND = 0x1;

        int fileDescriptorIndex;
        JinixFileDescriptor fd;
        byte flags;
    }

    /**
     * Parse a redirection token. The parsing may consume the next token, so we return the index to
     * continue from in the cmdToken array.
     *
     * @param index
     * @param cmdToken
     * @param rtrnRedirectionList
     * @throws IOException
     */
    private static int parseRedirection(int index, String[] cmdToken, List<Redirection> rtrnRedirectionList)
        throws IOException
    {
        String redirectionString = cmdToken[index].trim();
        if (redirectionString.equals(INPUT_REDIRECT) ||
                redirectionString.equals(OUTPUT_REDIRECT) ||
                redirectionString.equals(OUTPUT_REDIRECT+OUTPUT_REDIRECT)) {
            if (index == cmdToken.length-1) {
                throw new IllegalArgumentException("Invalid redirection missing redirection file");
            }
            redirectionString = redirectionString + cmdToken[++index].trim();
        }
        String fileName = null;
        if (redirectionString.contains(INPUT_REDIRECT)) {
            Redirection r = new Redirection();
            r.fileDescriptorIndex = 0;
            fileName = redirectionString.substring(redirectionString.indexOf(INPUT_REDIRECT)+1).trim();
            JinixFile inputFile = new JinixFile(fileName);
            if (!inputFile.exists()) {
                throw new IllegalArgumentException("Invalid input redirection: file does not exist: "+fileName);
            }
            if (!inputFile.isFile()) {
                throw new IllegalArgumentException("Invalid input redirection: invalid file: "+fileName);
            }
            r.fd = (new JinixFileInputStream(inputFile)).getFD();
            rtrnRedirectionList.add(r);
        } else if (redirectionString.contains(OUTPUT_REDIRECT)) {
            int fdIndex = 1;
            byte flags = 0;
            boolean redirectAppend = false;
            int redirectFileNameIndex = redirectionString.indexOf(OUTPUT_REDIRECT) + 1;
            if (redirectionString.indexOf(OUTPUT_REDIRECT) > 0) {
                if (redirectionString.substring(0, redirectionString.indexOf(OUTPUT_REDIRECT)).equals("&")) {
                    fdIndex = -1;
                } else {
                    String fdIndexString = redirectionString.substring(0, redirectionString.indexOf(OUTPUT_REDIRECT));
                    try {
                        fdIndex = Integer.parseInt(fdIndexString);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid redirect file descriptor index value: " + fdIndexString);
                    }
                }
            }
            if (redirectionString.substring(redirectFileNameIndex).startsWith(OUTPUT_REDIRECT)) {
                flags = (byte) (flags | Redirection.REDIRECTION_FLAG_APPEND);
                redirectAppend = true;
                redirectFileNameIndex++;
            }
            fileName = redirectionString.substring(redirectFileNameIndex).trim();
            Redirection r = new Redirection();
            r.fileDescriptorIndex = (fdIndex == -1 ? 1 : fdIndex);
            r.flags = flags;
            JinixFile outputFile = new JinixFile(fileName);
            if (outputFile.exists() && !outputFile.isFile()) {
                throw new IllegalArgumentException("Invalid output redirection: invalid file: "+fileName);
            }
            if (!outputFile.exists()) {
                try {
                    outputFile.createNewFile();
                } catch (IOException e) {
                    throw new IOException("Failure creating redirection output file: "+fileName, e);
                }
            }
            r.fd = (new JinixFileOutputStream(outputFile, redirectAppend)).getFD();
            rtrnRedirectionList.add(r);

            if (fdIndex == -1) {
                Redirection r2 = new Redirection();
                r2.fileDescriptorIndex = 2;
                r2.flags = r.flags;
                r2.fd = r.fd;
                rtrnRedirectionList.add(r2);
            }
        }
        return index;
    }

    private static class CommandExecutionException extends Exception {
        private int jobId;
        private CommandExecutionException(String message, int jobId) {
            super(message);
            this.jobId = jobId;
        }

        int getJobId() {
            return this.jobId;
        }
    }

    static class Job {
        int processGroupId;
        int jobId;
        int suspendedProcessCount;
        int shutdownProcessCount;
        List<String[]> cmdArray = new LinkedList<>();
        List<Integer> pidList = new LinkedList<>();

        boolean isDone() {
            return (pidList.size() - shutdownProcessCount) == 0;
        }

        boolean isSuspended() {
            return !isDone() && (pidList.size() - shutdownProcessCount - suspendedProcessCount) == 0;
        }

        boolean isActive() {
            return !isDone() && !isSuspended();
        }

        String cmdLine() {
            StringBuilder rtrn = new StringBuilder(64);
            for (String[] cmd : cmdArray) {
                if (rtrn.length() > 0) rtrn.append("| ");
                for (String arg : cmd) {
                    rtrn.append(arg).append(" ");
                }
            }
            return rtrn.toString();
        }
    }

    private static class TerminalSignalHandler implements Terminal.SignalHandler {
        @Override
        public void handle(Terminal.Signal signal) {
            // The JLine TerminalSignalHandler is not used.
        }
    }

    public static class JinixFileNameCompleter extends Completers.FileNameCompleter {

        @Override
        protected Path getUserDir() {
            return Paths.get(System.getProperty(JinixRuntime.JINIX_ENV_PWD));
        }
    }
}
