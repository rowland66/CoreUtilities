package org.rowland.jinix.coreutilities.jsh;

import org.rowland.jinix.exec.InvalidExecutableException;
import org.rowland.jinix.io.*;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.JinixSystem;
import org.rowland.jinix.naming.FileChannel;

import java.io.*;
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

    public static void main(String[] args) {

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

        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
            while(true) {
                System.out.print(">");
                System.out.flush();
                String cmdLine = input.readLine();

                cmdLine = cmdLine.trim();
                if (cmdLine.isEmpty()) {
                    continue;
                }

                Queue<String[]> cmdQueue = LineParser.parse(cmdLine);
                try {
                    executeCmd(cmdQueue, null, 0);
                } catch (CommandExecutionException e) {
                    System.err.println(e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("jsh: I/O Error reading input");
        }
    }

    private static void executeCmd(Queue<String[]> cmdQueue, JinixFileDescriptor previousCmdOutput, int executeCmdCnt)
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
                return;
            }
            if (cmd[0].equals("exit")) {
                System.exit(0);
            }

            if (cmd[0].equals("cd")) {
                if (cmd.length > 0) {
                    changeWorkingDirectory(cmd[1]);
                }
                return;
            }

            if (cmd[0].equals("pwd")) {
                cmdOutput.println(System.getProperty(JinixRuntime.JINIX_ENV_PWD));
                return;
            }

            if (cmd[0].equals("set")) {
                if (cmd.length == 1) {
                    for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
                        System.out.println((String)entry.getKey()+"="+(String)entry.getValue());
                    }
                    return;
                }

                String arg=cmd[1].trim();
                if (!arg.contains("=")) {
                    System.out.println(cmd[1]+"="+System.getProperty(cmd[1]));
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
            cmd[0] = expandCmd(cmd[0]);
            if (cmd[0] == null) {
                return;
            }

            ArrayList<String> execArgsList = new ArrayList<String>(cmd.length-1);
            List<Redirection> redirectionList = new LinkedList<Redirection>();
            for (int i=1; i<cmd.length; i++) {
                if (cmd[i].contains(INPUT_REDIRECT) ||
                        cmd[i].contains(OUTPUT_REDIRECT)) {
                    try {
                        i = parseRedirection(i, cmd, redirectionList);
                    } catch (IOException e) {
                        throw new CommandExecutionException(cmd[0]+": "+e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new CommandExecutionException(cmd[0]+": "+e.getMessage());
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
                runtime.exec(JinixSystem.getJinixProperties(), cmd[0], execArgs,
                        cmdInputFd, cmdOutputFd, cmdErrorFd);

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
                    while (executeCmdCnt > 0) {
                        runtime.waitForChild();
                        executeCmdCnt--;
                    }
                } else {
                    try {
                        executeCmd(cmdQueue, cmdOutputPipedInputFd, executeCmdCnt);
                    } catch (CommandExecutionException e) {
                        while (executeCmdCnt > 0) {
                            runtime.waitForChild();
                            executeCmdCnt--;
                        }
                        throw e;
                    }
                }
            } catch (FileNotFoundException e) {
                throw new CommandExecutionException("'"+cmd+"' is not recognized as an internal or external command.");
            } catch (InvalidExecutableException e) {
                throw new CommandExecutionException("'"+e.getInvalidFile()+"' is not recognized as an internal or external command.");
            }
        } catch (CommandExecutionException e) {
            executeCmdCnt--;
            throw e;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e); // This should never happen.
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

    private static String expandCmd(String cmd) throws CommandExecutionException {

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
                throw new CommandExecutionException("'"+cmd+"' is not recognized as an internal or external command.");
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
            throw new CommandExecutionException("jsh: cd: " + newDirectoryString + " No such file or directory");
        }
        if (!newDirectoryFile.isDirectory()) {
            throw new CommandExecutionException("jsh: cd: "+newDirectoryString+" Not a directory");
        }

        try {
            JinixSystem.setJinixProperty(JinixRuntime.JINIX_ENV_PWD, newDirectoryFile.getCanonicalPath());
        } catch (IOException e) {
            throw new CommandExecutionException("jsh: cd: "+e.getMessage());
        }
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
     * @param redirectionString
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

        private CommandExecutionException(String message) {
            super(message);
        }
    }
}
