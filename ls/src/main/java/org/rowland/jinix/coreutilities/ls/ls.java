package org.rowland.jinix.coreutilities.ls;

import org.apache.commons.cli.*;
import org.rowland.jinix.coreutilities.globutils.InvalidGlobException;
import org.rowland.jinix.coreutilities.globutils.ParseGlobResult;
import org.rowland.jinix.io.JinixFile;
import org.rowland.jinix.lang.JinixRuntime;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.util.function.Consumer;

import static org.rowland.jinix.coreutilities.globutils.GlobUtils.parseGlob;

/**
 * For each argument that names a file of a type other than directory, ls will write
 * the name of the file as well as any requested, associated information. For each argument
 * that names a file of type directory, ls shall write the names of files contained within
 * the directory as well as any requested, associated information.
 */
public class ls {

    private static double GB = (1024 * 1024 * 1024);
    private static double MB = (1024 * 1024);
    private static double KB = 1024;

    private static boolean displayedFile; // this flag is set to false for every argument

    public static void main(String[] args) {

        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        args = cmdLine.getArgs();

        if (args.length == 0) {
            args = new String[1];
            args[0] = "*";
        }

        argLoop:
        for (String arg : args) {
            displayedFile = false;
            try {
                ParseGlobResult pgr;
                try {
                    pgr = parseGlob(Paths.get(arg));
                } catch (InvalidGlobException e) {
                    System.err.println("ls: "+e.getMessage()+": " + arg);
                    continue argLoop;
                }
                if (pgr.isGlob) {
                    displayDirectory(pgr.dir, pgr.fileName, cmdLine);
                } else {
                    Path p = Paths.get(arg);
                    if (Files.isDirectory(p)) {
                        if (cmdLine.hasOption("d")) {
                            displayFile(p, cmdLine);
                        } else {
                            pgr.isGlob = true; // indicates that we listing the full directory
                            displayDirectory(p, "*", cmdLine);
                        }
                    } else {
                        if (Files.exists(p)) {
                            displayFile(p, cmdLine);
                        } else {
                            throw new NoSuchFileException(arg);
                        }
                    }
                }

                if (!displayedFile && !pgr.isGlob) {
                    System.err.println("ls: cannot access '"+arg+"': No such file or directory");
                }

            } catch (NoSuchFileException e) {
                System.err.println("ls: cannot access '" + arg + "': No such file or directory");
                continue;
            } catch (IOException e) {

            }
        }
        System.out.flush();
    }

    private static void displayDirectory(final Path dir, String glob, CommandLine cmdLine) throws IOException {
        DirectoryStream<Path> ds = Files.newDirectoryStream(dir, glob);
        ds.forEach(new Consumer<Path>() {
            @Override
            public void accept(Path path) {
                try {
                    displayFile(dir.resolve(path), cmdLine);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private static void displayFile(Path fileName, CommandLine cmdLine) throws IOException {
        displayedFile = true;
        if (cmdLine.hasOption("l")) {
            displayFileLong(fileName, cmdLine);
        } else {
            displayFileShort(fileName);
        }
    }

    private static void displayFileShort(Path fileName) {
        System.out.println(fileName.toString());
    }

    private static void displayFileLong(Path path, CommandLine cmdList) throws IOException {

        BasicFileAttributes attr = Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();

        String fileLength;
        if (cmdList.hasOption('h')) {
            double fl = (double) attr.size();
            if (fl >= GB) {
                fileLength = String.format("%1$8.2f", (fl/GB)) + "G";
            } else if (fl >= MB) {
                fileLength = String.format("%1$8.2f", (fl/MB)) + "M";
            } else if (fl >= KB) {
                fileLength = String.format("%1$9.2f", (fl/KB)) + "K";
            } else {
                fileLength = String.format("%1$10d", attr.size());
            }
        } else {
            fileLength = String.format("%1$10d", attr.size());
        }
        System.out.println(String.format("%1$tb %1$td %1$tY  %1$tH:%1$tM:%1$tS ", attr.lastModifiedTime().toMillis()) +
                fileLength + " " + path.toString());
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("l", null, false, "use a long listing format");
        options.addOption("h", "human-readable", false, "print sizes in human readable format (e.g., 1K 234M 2G)");
        options.addOption("d", "directory", false, "list directories themselves, not their contents");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("[FILE]...");
            formatter.printHelp("ls",
                    "List information about the FILEs (the current directory by default).",
                    options,
                    "",
                    true);
            return null;
        }
    }
}