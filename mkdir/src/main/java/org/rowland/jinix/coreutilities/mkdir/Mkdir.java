package org.rowland.jinix.coreutilities.mkdir;

import org.apache.commons.cli.*;
import org.rowland.jinix.lang.JinixRuntime;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utility to create a new file system directory.
 */
public class Mkdir {

    public static void main(String[] args) {
        CommandLine cmdLine = parseCommandLineOptions(args);

        args = cmdLine.getArgs();

        if (args.length == 0) {
            System.err.println("mkdir: missing operand");
            return;
        }

        for (String arg : args) {

            try {
                if (cmdLine.hasOption("p")) {
                    Files.createDirectories(Paths.get(arg));
                } else {
                    Files.createDirectory(Paths.get(arg));
                }
                if (cmdLine.hasOption("v")) {
                    System.err.println("mkdir: created directory '"+arg+"'");
                }
            } catch (FileAlreadyExistsException e) {
                System.err.println("mkdir: cannot create directory '"+arg+"': File exists");
            } catch (IOException e) {
                System.err.println("mkdir: IOException creating directory '"+arg+"'");
                e.printStackTrace(System.err);
            }
        }
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("p", "parents", false, "no error if existing, make parent directories as needed");
        options.addOption("v", "verbose", false, "print message for each created directory");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("[DIRECTORY]...");
            formatter.printHelp("mkdir",
                    "Create the DIRECTORY(ies), if they do not already exist.",
                    options,
                    "Jinix coreutils",
                    true);
            return null;
        }
    }

}
