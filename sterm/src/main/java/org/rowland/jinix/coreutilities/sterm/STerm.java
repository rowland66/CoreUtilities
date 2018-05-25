package org.rowland.jinix.coreutilities.sterm;

import org.apache.commons.cli.*;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.terminal.*;

import java.util.Collections;
import java.util.EnumSet;

public class STerm {

    private static String[] CTRL_CHARACTER_NAME = new String[] {
            "<undef>",
            "^A",
            "^B",
            "^C",
            "^D",
            "^E",
            "^F",
            "^G",
            "^H",
            "^I",
            "^J",
            "^K",
            "^L",
            "^M",
            "^N",
            "^O",
            "^P",
            "^Q",
            "^R",
            "^S",
            "^T",
            "^U",
            "^V",
            "^W",
            "^X",
            "^Y",
            "^Z",
            "^[",
            "^\\",
            "^]",
            "^^",
            "^_"
        };

    public static void main(String[] args) {

        CommandLine cmdLine = parseCommandLineOptions(args);
        if (cmdLine == null) return;

        short termId = JinixRuntime.getRuntime().getProcessTerminalId();
        TerminalAttributes attributes = JinixRuntime.getRuntime().getTerminalAttributes(termId);

        if (cmdLine.hasOption("a")) {

            System.out.println("Terminal: " + termId);
            System.out.println("Input Modes  : " + attributes.inputModes.toString());
            System.out.println("Output Modes : " + attributes.outputModes.toString());
            System.out.println("Local Modes  : " + attributes.localModes.toString());
            System.out.print("Special Chars: ");

            int perLineCounter = 0;
            for (SpecialCharacter sc : EnumSet.allOf(SpecialCharacter.class)) {
                String ctrlCharName;
                if (attributes.specialCharacterMap.get(sc) < CTRL_CHARACTER_NAME.length) {
                    ctrlCharName = CTRL_CHARACTER_NAME[attributes.specialCharacterMap.get(sc)];
                } else {
                    ctrlCharName = Integer.toHexString(attributes.specialCharacterMap.get(sc).byteValue());
                }
                System.out.print(sc.toString() + " = " + ctrlCharName + ";");
                if (perLineCounter < 3) {
                    System.out.print(" ");
                    perLineCounter++;
                } else {
                    System.out.println();
                    System.out.print("               ");
                    perLineCounter = 0;
                }
            }
            System.out.println();
            return;
        }

        if (cmdLine.getArgs().length > 0) {
            for (String mode : cmdLine.getArgs()) {
                boolean set = true;
                if (mode.endsWith("-")) {
                    set = false;
                    mode = mode.substring(0, mode.length()-1);
                } else if (mode.endsWith("+")) {
                    mode = mode.substring(0, mode.length()-1);
                }

                InputMode i;
                OutputMode o;
                LocalMode l;

                try {
                    i = InputMode.valueOf(mode);
                    if (attributes.inputModes.contains(i) && !set) {
                        attributes.inputModes.remove(i);
                        continue;
                    }
                    if (!attributes.inputModes.contains(i) && set) {
                        attributes.inputModes.add(i);
                        continue;
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore and continue to output modes.
                }

                try {
                    o = OutputMode.valueOf(mode);
                    if (attributes.outputModes.contains(o) && !set) {
                        attributes.outputModes.remove(o);
                        continue;
                    }
                    if (!attributes.outputModes.contains(o) && set) {
                        attributes.outputModes.add(o);
                        continue;
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore and continue to local modes.
                }

                try {
                    l = LocalMode.valueOf(mode);
                    if (attributes.localModes.contains(l) && !set) {
                        attributes.localModes.remove(l);
                        continue;
                    }
                    if (!attributes.localModes.contains(l) && set) {
                        attributes.localModes.add(l);
                        continue;
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore and print error.
                }

                System.err.println("Unknown terminal mode: "+mode);
            }

            JinixRuntime.getRuntime().setTerminalAttributes(termId, attributes);
        }
    }

    private static CommandLine parseCommandLineOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption("a", "all", false, "print all current settings");

        try {
            CommandLine cmdLine = parser.parse(options, args);
            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setArgName("[MODE]...");
            formatter.printHelp("sterm",
                    "Print or change terminal characteristics.",
                    options,
                    "",
                    true);
            return null;
        }
    }
}
