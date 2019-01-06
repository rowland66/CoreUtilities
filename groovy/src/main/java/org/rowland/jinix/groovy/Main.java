package org.rowland.jinix.groovy;

import groovy.lang.GroovyShell;

import org.apache.commons.cli.*;
import org.codehaus.groovy.control.CompilationFailedException;
import org.rowland.jinix.lang.JinixRuntime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Jinix wrapper around a Groovy shell.
 */
public class Main {

    private static final String CLASSPATH_SHEBANG = "//!groovy-classpath:";

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

            processGroovyClasspath(scriptPath);

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

    private static void processGroovyClasspath(Path scriptFilePath) {
        try {
            BufferedReader scriptReader = Files.newBufferedReader(scriptFilePath);
            List<String> libraryNames;
            try {
                String line = scriptReader.readLine();
                libraryNames = new ArrayList(16);
                while (line.startsWith("#!")) {
                    if (line.startsWith(CLASSPATH_SHEBANG)) {
                        String classPath = line.substring(CLASSPATH_SHEBANG.length());
                        String[] lib = classPath.split("\\s");
                        libraryNames.addAll(Arrays.asList(lib));
                    }
                    line = scriptReader.readLine();
                }
            } finally {
                scriptReader.close();
            }

            for (String jarFile : libraryNames) {
                JinixRuntime.getRuntime().addLibraryToClassloader(jarFile);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
