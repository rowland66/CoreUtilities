package org.rowland.jinix.coreutilities;

import org.apache.commons.cli.*;
import org.rowland.jinix.coreutilities.globutils.InvalidGlobException;
import org.rowland.jinix.coreutilities.globutils.ParseGlobResult;

import java.io.*;
import java.nio.file.*;
import java.util.function.Consumer;

import static org.rowland.jinix.coreutilities.globutils.GlobUtils.isGlob;
import static org.rowland.jinix.coreutilities.globutils.GlobUtils.parseGlob;

/**
 * Created by rsmith on 3/18/2017.
 */
public class Cp {
    public static void main(String[] args) {
        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        args = cmdLine.getArgs();

        if (args.length < 1) {
            System.err.println("cp: missing file operand");
            System.err.println("Try 'cp --help' for more information");
            return;
        }
        if (args.length < 2) {
            System.err.println("cp: missing destination file file operand after '"+args[0]+"'");
            System.err.println("Try 'mv --help' for more information.");
            return;
        }

        Path dest = null;
        try {
            if (args.length == 2) {
                Path src = Paths.get(args[0]);
                dest = Paths.get(args[1]);

                copyFile(src, dest, cmdLine);
                return;
            }

            dest = Paths.get(args[args.length-1]);
            if (!Files.isDirectory(dest)) {
                System.err.println("cp: target '"+dest.toString()+"' is not a directory");
                return;
            }

            for (int i=0; i<(args.length-1); i++) {
                Path src = Paths.get(args[i]);
                copyFile(src, dest, cmdLine);
            }
        } catch (NoSuchFileException e) {
            System.err.println("cp: target '"+dest.toString()+"': is not a directory");
        } catch (IOException e) {
            throw new RuntimeException("IOException copying file",e);
        }
    }

    private static void copyFile(Path src, Path dest, CommandLine cmdLine) throws IOException {
        if (isGlob(src.toString())) {
            if (!Files.isDirectory(dest)) {
                System.err.println("cp: target '"+dest.toString()+"' is not a directory");
                return;
            }
            try {
                ParseGlobResult pgr = parseGlob(src);
                DirectoryStream<Path> ds = Files.newDirectoryStream(pgr.dir, pgr.fileName);
                ds.forEach(new Consumer<Path>() {
                    @Override
                    public void accept(Path path) {
                        try {
                            copyFileInner(pgr.dir.resolve(path), dest.resolve(path.getFileName()), cmdLine);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            } catch (InvalidGlobException e) {
                System.err.println("cp: "+e.getMessage());
                return;
            }
        } else {
            copyFileInner(src, dest, cmdLine);
        }
    }

    private static void copyFileInner(Path src, Path dest, CommandLine cmdLine) throws IOException {
        try {
            if (Files.isDirectory(dest)) {
                if (cmdLine.hasOption('n')) {
                    try {
                        Files.copy(src, dest.resolve(src.getFileName()));
                    } catch (FileAlreadyExistsException e) {
                        System.err.println("mv: cannot move '" + src.toString() + "': File already exists");
                    } catch (UnsupportedOperationException e) {
                        copyFile(src, dest.resolve(src.getFileName()), false);
                    }
                } else {
                    try {
                        Files.copy(src, dest.resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    } catch (UnsupportedOperationException e) {
                        copyFile(src, dest.resolve(src.getFileName()), true);
                    }
                }
            } else {
                if (cmdLine.hasOption('n')) {
                    try {
                        Files.copy(src, dest);
                    } catch (FileAlreadyExistsException e) {
                        System.err.println("mv: cannot move '" + src.toString() + "': File already exists");
                    } catch (UnsupportedOperationException e) {
                        copyFile(src, dest, false);
                    }
                } else {
                    try {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    } catch (UnsupportedOperationException e) {
                        copyFile(src, dest, true);
                    }
                }
            }
        } catch (NoSuchFileException e) {
            System.err.println("cp: cannot copy '" + src.toString() + "': No such file or directory");
        }
    }

    private static void copyFile(Path src, Path dest, boolean replace) throws IOException {
        InputStream srcInputStream = new BufferedInputStream(Files.newInputStream(src, StandardOpenOption.READ));
        OutputStream destOutputStream;
        if (replace) {
            destOutputStream = new BufferedOutputStream(Files.newOutputStream(dest,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING));
        } else {
            try {
                destOutputStream = new BufferedOutputStream(Files.newOutputStream(dest,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE_NEW));
            } catch (FileAlreadyExistsException e) {
                System.err.println("cp: cannot copy '" + src.toString() + "': File already exists");
                return;
            }
        }

        try {
            byte[] b = new byte[1024];
            int br = srcInputStream.read(b);
            while (br > 0) {
                destOutputStream.write(b, 0, br);
                br = srcInputStream.read(b);
            }
        } finally {
            srcInputStream.close();
            destOutputStream.close();
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
