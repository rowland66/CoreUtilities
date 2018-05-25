package org.rowland.jinix.groovy;

import groovy.lang.GroovyShell;

import org.apache.commons.cli.*;
import org.codehaus.groovy.control.CompilationFailedException;
import org.rowland.jinix.io.JinixFile;
import org.rowland.jinix.lang.JinixRuntime;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Jinix wrapper around a Groovy shell.
 */
public class Main {

    public static void main(String[] args) {

        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        args = cmdLine.getArgs();

        if (args.length < 1) {
            System.err.println("groovy: no script file");
            return;
        }

        try {
            Path scriptPath = Paths.get(args[0]);
            if (!Files.exists(scriptPath)) {
                System.err.println("groovy: no such file: "+scriptPath.toString());
            }
            Reader scriptReader = Files.newBufferedReader(scriptPath);

            GroovyShell groovy = new GroovyShell();

            try {
                groovy.evaluate(scriptReader);
            } catch (CompilationFailedException e) {
                System.err.println("groovy: Failure compiling script: "+e.getMessage());
            }

        } catch (IOException e) {
            System.err.println("groovy: IOException: "+e.getMessage());
        }
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("[FILE]...");
            formatter.printHelp("groovy",
                    "List information about the FILEs (the current directory by default).",
                    options,
                    "",
                    true);
            return null;
        }
    }

}
