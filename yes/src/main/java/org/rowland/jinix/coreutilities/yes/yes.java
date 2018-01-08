package org.rowland.jinix.coreutilities.yes;


import org.apache.commons.cli.*;

/**
 * A simple utility to repeatedly output a string.
 */
public class yes {

    public static void main(String[] args) {
        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        args = cmdLine.getArgs();

        StringBuffer outputBuffer = new StringBuffer();
        String output;

        if (args.length == 0) {
            output = "y";
        } else {
            for (String arg : args) {
                outputBuffer.append(arg).append(" ");
            }
            output = outputBuffer.substring(0, outputBuffer.length() - 1);
        }

        while(true) {
            System.out.println(output);
        }
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("help", null, false, "display this help and exit");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("[STRING]...");
            formatter.printHelp("yes",
                    "Reapeatedly output a line with all specified STRING(s), or 'y'.",
                    options,
                    "",
                    true);
            return null;
        }
    }
}