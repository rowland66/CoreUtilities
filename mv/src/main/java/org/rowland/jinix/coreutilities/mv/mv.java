package org.rowland.jinix.coreutilities.mv;

import org.apache.commons.cli.*;
import org.rowland.jinix.coreutilities.globutils.InvalidGlobException;
import org.rowland.jinix.coreutilities.globutils.ParseGlobResult;
import org.rowland.jinix.nio.JinixFileAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

import static org.rowland.jinix.coreutilities.globutils.GlobUtils.isGlob;
import static org.rowland.jinix.coreutilities.globutils.GlobUtils.parseGlob;

/**
 * The mv utility will move the file named by the source_file argument to the destination
 * specified by the target_file when the target_file argument does not name an existing
 * directory. The mv utility will move each file named by a source_file argument to a
 * destination file in the existing directory named by the target_dir argument. The destination
 * path for each source_file shall be the concatenation of the target directory, a single <slash>
 * character if the target did not end in a <slash>, and the last pathname component of the
 * source_file. This assumes that the final argument names an existing directory.
 */
public class mv {

    public static void main(String[] args) {
        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        args = cmdLine.getArgs();

        if (args.length < 2) {
            System.err.println("Try 'mv --help' for more information.");
            return;
        }

        Path dest = null;
        try {
            if (args.length == 2) {
                Path src = Paths.get(args[0]);
                dest = Paths.get(args[1]);

                moveFile(src, dest, cmdLine);
                return;
            }

            dest = Paths.get(args[args.length-1]);
            if (!Files.isDirectory(dest)) {
                System.err.println("mv: target '"+dest.toString()+"' is not a directory");
                return;
            }

            for (int i=0; i<(args.length-1); i++) {
                Path src = Paths.get(args[i]);
                moveFile(src, dest, cmdLine);
            }
        } catch (NoSuchFileException e) {
            System.err.println("mv: target '"+dest.toString()+"': is not a directory");
        } catch (IOException e) {
            throw new RuntimeException("IOException moving file",e);
        }
    }

    private static void moveFile(Path src, Path dest, CommandLine cmdLine) throws IOException {
        if (isGlob(src.toString())) {
            if (!Files.isDirectory(dest)) {
                System.err.println("mv: target '"+dest.toString()+"' is not a directory");
                return;
            }
            try {
                ParseGlobResult pgr = parseGlob(src);
                DirectoryStream<Path> ds = Files.newDirectoryStream(pgr.dir, pgr.fileName);
                ds.forEach(new Consumer<Path>() {
                    @Override
                    public void accept(Path path) {
                        try {
                            moveFileInner(path, dest.resolve(path.getFileName()), cmdLine);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            } catch (InvalidGlobException e) {
                System.err.println("mv: "+e.getMessage());
                return;
            }
        } else {
            moveFileInner(src, dest, cmdLine);
        }
    }

    private static void moveFileInner(Path src, Path dest, CommandLine cmdLine) throws IOException {
        try {
            if (Files.isDirectory(dest)) {
                if (cmdLine.hasOption('n')) {
                    try {
                        Files.move(src, dest.resolve(src.getFileName()));
                    } catch (FileAlreadyExistsException e) {
                        System.err.println("mv: cannot move '" + src.toString() + "': File already exists");
                    }
                } else {
                    Files.move(src, dest.resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                if (cmdLine.hasOption('n')) {
                    try {
                        Files.move(src, dest);
                    } catch (FileAlreadyExistsException e) {
                        System.err.println("mv: cannot move '" + src.toString() + "': File already exists");
                    }
                } else {
                    Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (NoSuchFileException e) {
            System.err.println("mv: cannot move '" + src.toString() + "': No such file or directory");
        }
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("f", "force", false, "do not prompt before overwriting");
        options.addOption("n", "no-clobber", false, "do not overwrite an existing file");
        options.addOption("t", "target-directory", true, "move all SOURCE arguments into directory");
        options.addOption("v", "verbose", false, "explain what is being done");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("[FILE]... TARGET_FILE | TARGET_DIR");
            formatter.printHelp("mv",
                    "move file(s) or directories",
                    options,
                    "",
                    true);
            return null;
        }
    }
}
