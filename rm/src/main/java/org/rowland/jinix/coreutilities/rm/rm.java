package org.rowland.jinix.coreutilities.rm;

import org.apache.commons.cli.*;
import org.rowland.jinix.nio.JinixFileSystemProvider;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Utilily program to remove files and/or directories.
 */
public class rm {

    public static void main(String[] args) {
        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        args = cmdLine.getArgs();

        if (args.length == 0) {
            System.err.println("Try 'rm --help' for more information.");
            return;
        }

        argLoop:
        for (String arg: args) {
            String dir = "";
            String fileName = arg;
            boolean glob = false;
            if (fileName.contains("/")) {
                if (fileName.startsWith("/")) {
                    dir = "/";
                }
                String[] names = fileName.split("/");
                fileName = null;
                for (String name : names) {
                    if (isGlob(name)) {
                        if (fileName != null) {
                            System.err.println("rm: Invalid glob in argument: " + arg);
                            continue argLoop;
                        }
                        fileName = name;
                        glob = true;
                    } else {
                        if (fileName != null) {
                            System.err.println("rm: Invalid glob in argument: " + arg);
                            continue argLoop;
                        }
                        if (!name.isEmpty()) {
                            dir = dir + name + "/";
                        }
                    }
                }
            } else {
                if (isGlob(fileName)) {
                    glob = true;
                }
            }

            if (glob) {
                deleteDirectoryFiles(Paths.get(dir), fileName, cmdLine);
            } else {
                deleteFile(dir, fileName, cmdLine);
            }
        }
    }

    private static void deleteFile(String dir, String fileName, CommandLine cmdLine) {
        Path p;
        if (fileName != null) {
            p = Paths.get(dir, fileName);
        } else {
            if (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
            p = Paths.get(dir);
        }

        try {
            BasicFileAttributes attr = Files.getFileAttributeView(p, BasicFileAttributeView.class).readAttributes();
            if (attr.isDirectory()) {
                if (cmdLine.hasOption("d") || cmdLine.hasOption("r")) {
                    try {
                        Files.delete(p);
                    } catch (DirectoryNotEmptyException e) {
                        if (cmdLine.hasOption("r")) {
                            deleteDirectoryFiles(p, "*", cmdLine);
                            Files.delete(p);
                        } else {
                            System.err.println("rm: cannot remove '" + p.toString() + "': Directory not empty");
                        }
                    }
                } else {
                    System.err.println("rm: cannot remove '" + p.toString() + "': Is a directory");
                }
            } else {
                Files.delete(p);
            }
        } catch (NoSuchFileException e) {
            System.err.println("rm: cannot access '" + p.toString() + "': No such file or directory");
        } catch (IOException e) {
            System.err.println("rm: IOException removing file: "+p.toString());
        }
    }

    private static void deleteDirectoryFiles(final Path dir, String glob, CommandLine cmdLine) {
        try {
            DirectoryStream<Path> ds = Files.newDirectoryStream(dir,glob);
            ds.forEach(new Consumer<Path>() {
                @Override
                public void accept(Path path) {
                    deleteFile("", path.toString(), cmdLine);
                }
            });
        } catch (IOException e) {
            System.err.println("rm: IOException removing file: "+dir);
        }
    }

    private static boolean directoryContainsFiles(final Path dir) throws IOException {
        DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*");
        for (Path d : ds) {
            return true;
        }
        return false;
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("f", "force", false, "ignore nonexistent files and arguments, never prompt");
        options.addOption("r", "recursive", false, "remove directories and their contents recursively");
        options.addOption("d", "dir", false, "don't remove empty directories");
        options.addOption("v", "verbose", false, "explain what is being done");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("[FILE]...");
            formatter.printHelp("rm",
                    "remove file or directories",
                    options,
                    "",
                    true);
            return null;
        }
    }
    private static boolean isGlob(String str) {
        if (str.contains("*") || str.contains("?")) {
            return true;
        }
        return false;
    }

}
