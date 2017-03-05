package org.rowland.jinix.groovy;

import groovy.lang.GroovyShell;

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
}
