package org.rowland.jinix.coreutilities.settrans;

import org.apache.commons.cli.*;
import org.rowland.jinix.exec.InvalidExecutableException;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.NameSpace;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.EnumSet;

/**
 * Set a translator on a node in the system namespace.
 */
public class SetTrans {

    public static void main(String[] args) {

        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        if (cmdLine.hasOption("help")) {
            displayHelp();
            return;
        }

        args = cmdLine.getArgs();

        if (args.length < 1) {
            System.err.println("settrans: Insuficient number or arguments");
            return;
        }

        if (cmdLine.hasOption('f') && cmdLine.hasOption('r')) {
            System.err.println("settrans: invalid option usage, f and r are not valid together");
            return;
        }

        if (cmdLine.hasOption('r')) {
            Path node = Paths.get(args[0]);
            EnumSet<NameSpace.BindTranslatorOption> unbindOptions = EnumSet.noneOf(NameSpace.BindTranslatorOption.class);
            if (cmdLine.hasOption('a')) {
                unbindOptions.add(NameSpace.BindTranslatorOption.ACTIVATE);
            }
            if (cmdLine.hasOption('p')) {
                unbindOptions.add(NameSpace.BindTranslatorOption.PASSIVE);
            }

            if (unbindOptions.isEmpty()) {
                System.err.println("settrans: either of option a (activate) or p (passive) must be provided with option r (remove)");
                return;
            }

            try {
                JinixRuntime.getRuntime().getRootNamespace().unbindTranslator(
                        node.toAbsolutePath().toString(), unbindOptions);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        if (args.length < 2) {
            System.err.println("settrans: Insuficient number or arguments");
            return;
        }

        String node = args[0];
        Path nodePath = Paths.get(node);
        if (!Files.exists(nodePath)) {
            System.err.println("settrans: No such file: "+node);
            return;
        }
        EnumSet<NameSpace.BindTranslatorOption> bindOptions = EnumSet.noneOf(NameSpace.BindTranslatorOption.class);
        if (cmdLine.hasOption('f')) {
            bindOptions.add(NameSpace.BindTranslatorOption.FORCE);
        }
        if (cmdLine.hasOption('a')) {
            bindOptions.add(NameSpace.BindTranslatorOption.ACTIVATE);
        }
        if (cmdLine.hasOption('p')) {
            bindOptions.add(NameSpace.BindTranslatorOption.PASSIVE);
        }

        if (!(bindOptions.contains(NameSpace.BindTranslatorOption.ACTIVATE) ||
                bindOptions.contains(NameSpace.BindTranslatorOption.PASSIVE))) {
            System.err.println("settrans: either of option a (activate) or p (passive) must be provided");
            return;
        }

        String cmd = args[1];
        if (!cmd.startsWith("/")) {
            cmd = System.getProperty(JinixRuntime.JINIX_ENV_PWD) + "/" + cmd;
        }
        String[] bindTransArgs = new String[args.length-2];
        System.arraycopy(args, 2, bindTransArgs, 0, args.length-2);
        try {
            JinixRuntime.getRuntime().getRootNamespace().bindTranslator(
                    nodePath.toAbsolutePath().toString(), cmd, bindTransArgs, bindOptions);
        } catch (FileNotFoundException e) {
            System.err.println("settrans: translator failed to start no such file: " + cmd);
        } catch (InvalidExecutableException e) {
            System.err.println("settrans: translator failed to start invalid executable: " + cmd);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        return;
    }

    static private Options options = new Options();

    static {
        options.addOption("a", "active", false, "start and active translator");
        options.addOption("p", "passive", false, "establish a passive translator setting");
        options.addOption("f", "force", false, "force the operation");
        options.addOption("r", "remove", false, "remove translator (active or passive)");
        options.addOption("", "help", false, "display this usage message");
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            displayHelp();
            return null;
        }
    }

    private static void displayHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("settrans [OPTIONS]... [NODE] [TRANSLATOR] [TRANSLATOR ARGS]",
                "Attach a [TRANSLATOR] to a file system [NODE].",
                options,
                "Help on settrans can be obtained by passing in the --help option.",
                false);

    }
}
