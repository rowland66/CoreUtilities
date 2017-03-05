package org.rowland.jinix.jld;

import org.apache.commons.cli.*;
import org.rowland.jinix.io.JinixFile;
import org.rowland.jinix.io.JinixFileInputStream;
import org.rowland.jinix.io.JinixFileOutputStream;
import org.rowland.jinix.lang.JinixRuntime;

import java.io.*;
import java.rmi.activation.ActivationGroup_Stub;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Created by rsmith on 12/26/2016.
 */
public class Jld {
    public static void main(String[] args) {

        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        args = cmdLine.getArgs();

        String outputFile = cmdLine.getOptionValue('o', "a");

        String mainClassName = null;
        if (cmdLine.hasOption('M')) {
            mainClassName = cmdLine.getOptionValue('M');
        }

        String[] libraryFiles = cmdLine.getOptionValues('l');

        String classPathManifestString = null;
        if (libraryFiles != null) {
            StringBuilder libSb = new StringBuilder();
            for (String libraryFile : libraryFiles) {
                libSb.append(libraryFile);
                libSb.append(" ");
            }
            classPathManifestString = libSb.toString().trim();
        }
        String[] directories = cmdLine.getArgs();

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (classPathManifestString != null) {
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH,
                    classPathManifestString);
        }
        if (mainClassName != null) {
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS,
                    mainClassName);
        }
        JarOutputStream jos = null;
        try {
            jos = new JarOutputStream(new BufferedOutputStream(
                    new JinixFileOutputStream(new JinixFile(outputFile), false)), manifest);
        } catch (FileNotFoundException e) {
            System.err.println("jld: "+outputFile+": Unable to open output file.");
        } catch (IOException e) {
            System.err.println("jld: "+outputFile+": IOException opening output file.");
        }

        try {
            for (String directoryStr : directories) {
                JinixFile directory = new JinixFile(directoryStr);

                if (!directory.isDirectory()) {
                    System.err.println("jld: "+directory.getName()+": is not a directory");
                    continue;
                }
                addDirectoryFile(directory, directory, jos);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (jos != null) {
                try {
                    jos.close();
                } catch (IOException e) {
                    // Nothing to do if close fails.
                }
            }
        }
    }

    private static void  addDirectoryFile(JinixFile file, JinixFile startDirectory, JarOutputStream jos)
            throws IOException {
        String directoryName = file.getCanonicalPath();
        String startDirectoryName;
        if (startDirectory == null) {
            startDirectoryName = file.getCanonicalPath();
            startDirectory = file;
        } else {
            startDirectoryName = startDirectory.getCanonicalPath();
        }
        String nameDiff;
        if (directoryName.length() == startDirectoryName.length()) {
            nameDiff = directoryName.substring(startDirectoryName.length());
        } else {
            nameDiff = directoryName.substring(startDirectoryName.length()+1);
        }

        if (file.isDirectory()) {
            if (nameDiff.length() > 0) {
                if (!nameDiff.endsWith("/"))
                    nameDiff += "/";
                JarEntry entry = new JarEntry(nameDiff);
                entry.setTime(file.lastModified());
                jos.putNextEntry(entry);
                jos.closeEntry();
            }
            JinixFile[] dirFiles = file.listFiles();
            for (JinixFile f : dirFiles) {
                addDirectoryFile(f, startDirectory, jos);
            }
            return;
        }

        String name = nameDiff;
        JarEntry je = new JarEntry(name);
        je.setTime(file.lastModified());
        jos.putNextEntry(je);
        try {
            InputStream fileIS = new BufferedInputStream(new JinixFileInputStream(file));
            int c;
            while ((c = fileIS.read()) != -1) {
                jos.write((char) c);
            }
            fileIS.close();
            jos.closeEntry();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("o", "output", true, "The name of the output file. Default value a.jar");
        options.addOption("M", "Main", true, "The fully qualified name of the class with the executables main() method.");
        options.addOption("l", "library", true, "Add a reference to the library to the executable.");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("[DIRECTORY]...");
            formatter.printHelp("grep",
                    "Compile the classes in the given DIRECTORY(s) into an executable jar file",
                    options,
                    "",
                    true);
            return null;
        }
    }

}
