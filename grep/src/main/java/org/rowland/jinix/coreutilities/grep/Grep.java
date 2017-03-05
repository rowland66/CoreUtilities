package org.rowland.jinix.coreutilities.grep;

import org.apache.commons.cli.*;
import org.rowland.jinix.io.JinixFile;
import org.rowland.jinix.io.JinixFileInputStream;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The grep utility shall search the input files, selecting lines matching the pattern; The pattern is
 * specified by the the pattern argument. By default, an input line shall be selected if any pattern,
 * treated as an entire regular expression as implemented by the Java java.util.regex package classes,
 * matches any part of the line excluding the terminating <newline>; a null JRE shall match every line.
 * By default, each selected input line shall be written to the standard output.
 */
public class Grep {

    public static void main(String[] args) {

        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        args = cmdLine.getArgs();

        String patternString = args[0];

        Pattern pattern = Pattern.compile(patternString);

        if (args.length == 1) {
            String[] newArgs = new String[2];
            newArgs[0] = args[0];
            newArgs[1] = "-";
            args = newArgs;
        }

        for (int i = 1; i < args.length; i++) {

            InputStream is;
            if (args[i].equals("-")) {
                is = System.in;
            } else {
                JinixFile f = new JinixFile(args[i]);

                try {
                    is = new BufferedInputStream(new JinixFileInputStream(f));
                } catch (FileNotFoundException e) {
                    System.err.println("grep: " + args[i] + ": File not found");
                    continue;
                }
            }

            try {
                String arg = null;
                if (cmdLine.hasOption('H') ||
                        (args.length > 2 && !cmdLine.hasOption('h'))) {
                    arg = args[i];
                }
                grepFile(arg, is, pattern, cmdLine);
            } catch (IOException e) {
                System.err.println("grep: " + args[i] + ": Error reading file");
                e.printStackTrace();
            }

            try {
                is.close();
            } catch (IOException e) {
                System.err.println("grep: " + args[i] + ": Error closing file");
            }
        }

        System.out.flush();
    }

    private static void grepFile(String arg, InputStream fileStream, Pattern pattern, CommandLine cmdLine) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(fileStream));

        int matchCount = 0, lineCount = 0;
        String line;
        while ((line = is.readLine()) != null) {
            lineCount++;
            Matcher m = pattern.matcher(line);
            boolean match = m.find();
            if ((match && !cmdLine.hasOption('v')) || (!match && cmdLine.hasOption('v'))) {
                matchCount++;
                if (!cmdLine.hasOption('c')) {
                    if (arg != null) {
                        System.out.print(arg+":");
                    }
                    if (cmdLine.hasOption('n')) {
                        System.out.print(lineCount+":");
                    }
                    System.out.println(line);
                }
            }
        }
        if (cmdLine.hasOption('c')) {
            if (arg != null) {
                System.out.print(arg+":");
            }
            System.out.println(matchCount);
        }
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("c", "count", false, "Suppress normal output; instead print a count of matching lines for each input file.");
        options.addOption("H", "with-filename", false, "Print the filename for each match.");
        options.addOption("h", "no-filename", false, "Suppress  the  prefixing  of  filenames  on output when multiple files are searched.");
        options.addOption("n", "line-number", false, "Prefix each line of output with the line number within its input file.");
        options.addOption("v", "invert-match", false, "Invert the sense of matching, to select non-matching lines.");


        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("PATTERN [FILE]...");
            formatter.printHelp("grep",
                    "Search FILE(s), or standard input, for lines that match the regular expression PATTERN. By default, grep prints the matching lines.",
                    options,
                    "With no FILE, or when FILE is -, read standard input.",
                    true);
            return null;
        }
    }

}
