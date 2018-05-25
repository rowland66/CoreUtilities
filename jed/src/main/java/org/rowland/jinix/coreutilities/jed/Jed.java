package org.rowland.jinix.coreutilities.jed;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import org.ahmadsoft.ropes.Rope;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.ProcessSignalHandler;
import org.rowland.jinix.proc.ProcessManager;
import org.rowland.jinix.ui.JinixTerminal;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Jinix line editor much like vi.
 */
public class Jed {

    static TextCharacter COMMAND_PROMPT = new TextCharacter(':');
    static TextCharacter BLANK = new TextCharacter(' ');

    static String fileName;
    static EditBuffer model;
    static Window window;
    static JinixTerminal t;
    static TerminalScreen s;
    static int screenRows = 23;
    static int screenCols = 80;
    static String register;
    static boolean exit = false;
    static boolean commandMode;

    // Command Mode Data
    private static StringBuilder command;
    private static TerminalPosition commandCursor;

    public static void main(String[] args) {

        try {
            if (args.length > 0) {
                fileName = args[0];
            }

            JinixRuntime.getRuntime().registerSignalHandler(new ProcessSignalHandler() {
                @Override
                public boolean handleSignal(ProcessManager.Signal signal) {
                    if (signal == ProcessManager.Signal.TSTOP) {
                        try {
                            s.stopScreen(false);
                            t.close();
                        } catch (IOException e) {
                            // Nothing can be done here, so ignore.
                        }
                        return false; // Return false so that the TSTOP default signal handling will suspend our process
                    }

                    if (signal == ProcessManager.Signal.CONTINUE) {
                        try {
                            t.realAcquire();
                            s.startScreen();
                        } catch (IOException e) {
                            // Nothing can be done here, so ignore.
                        }
                        return false; // Return false so that the CONTINUE default signal handling will resume our process
                    }

                    return false;
                }
            });

            t = new JinixTerminal(System.in, System.out, Charset.defaultCharset(), UnixLikeTerminal.CtrlCBehaviour.TRAP);
            t.getInputDecoder().setTimeoutUnits(1);
            s = new TerminalScreen(t);

            s.startScreen();

            try {
                if (fileName != null) {
                    readBuffer(model, fileName);
                } else {
                    model = new EditBuffer();
                }

                window = new Window(model, 0,0, screenCols, screenRows);
                window.drawScreen();
                s.refresh(Screen.RefreshType.COMPLETE);


                while (!exit) {
                    KeyStroke k = s.readInput();

                    if (commandMode) {
                        if (k.getKeyType() == KeyType.Escape) {
                            exitCommandMode();
                            window.restoreCursor();
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Backspace) {
                            removeCommandCharacter();
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Enter) {
                            String command = exitCommandMode();
                            executeCommand(command);
                            window.restoreCursor();
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Character) {
                            addCommandCharacter(k.getCharacter());
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }
                        continue;
                    }

                    if (window.insertMode) {
                        if (k.getKeyType() == KeyType.Escape) {
                            window.exitInsertMode();
                            window.cursorLeft(1);
                            window.refreshCurrentRow();
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Backspace && !k.isCtrlDown()) {
                            if (window.insertBuffer.length() == 0) {
                                continue;
                            }
                            window.insertBuffer.delete();
                            window.cursorLeft(1);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Character && k.isCtrlDown() && k.getCharacter() == 'u') {
                            if (window.insertBuffer.length() == 0) {
                                continue;
                            }
                            window.cursorLeft(window.insertBuffer.clear());
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Enter && !k.isCtrlDown()) {
                            window.exitInsertMode();
                            window.cursorLeft(1);
                            window.refreshCurrentRow();
                            window.insertLineAfterCurrentLine(model.getEmptyText());
                            window.enterInsertMode();
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Character && !k.isCtrlDown()) {
                            window.insertBuffer.append(k.getCharacter());
                            window.refreshCurrentRow();
                            window.cursorRight(1);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }
                    } // Insert Mode

                    if (k.getKeyType() == KeyType.Character && k.isCtrlDown()) {
                        if (k.getCharacter() == 'l') { // move window 1 line downwards
                            window.drawScreen();
                            s.refresh(Screen.RefreshType.COMPLETE);
                            continue;
                        }
                        if (k.getCharacter() == 'e') { // move window 1 line downwards
                            window.downLine(1);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }
                        if (k.getCharacter() == 'd') { // move window 1 line Downwards (1/2 rows)
                            window.downRows(window.height / 2);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }
                        if (k.getCharacter() == 'f') { // move window 1 page forward (downward)
                            window.downRows(window.height);
                            s.refresh(Screen.RefreshType.COMPLETE);
                            continue;
                        }
                        if (k.getCharacter() == 'y') { // move window 1 line upwards
                            window.upLine(1);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }
                        if (k.getCharacter() == 'u') { // move window 1 line Upwards (1/2 rows)
                            window.upRows(window.height / 2);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }
                        if (k.getCharacter() == 'b') { // move windows 1 page backward (upward)
                            window.upRows(window.height);
                            s.refresh(Screen.RefreshType.COMPLETE);
                            continue;
                        }
                    } // Ctrl Keys

                    if (k.getKeyType() == KeyType.ArrowDown) {
                        window.cursorDown(1);
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.ArrowUp) {
                        window.cursorUp(1);
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.ArrowLeft) {
                        window.cursorLeft(1);
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.ArrowRight) {
                        window.cursorRight(1);
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Home) {
                        window.moveCursorToStartOfLine();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.End) {
                        window.moveCursorToEndOfLine();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == ':') {
                        enterCommandMode();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'x') { // delete character to right
                        Rope line = window.getCurrentLine();
                        if (line.length() > 0) {
                            int ropePos = window.getCurrentLineOffset();
                            Rope newLine = line.delete(ropePos, ropePos + 1);
                            window.setCurrentLine(newLine);
                            window.refreshCurrentRow();
                            s.refresh(Screen.RefreshType.DELTA);
                        }
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'D') { // delete line to the right
                        Rope line = window.getCurrentLine();
                        if (line.length() > 0) {
                            int ropePos = window.getCurrentLineOffset();
                            Rope newLine = line.delete(ropePos, line.length());
                            window.setCurrentLine(newLine);
                            window.refreshCurrentRow();
                            s.refresh(Screen.RefreshType.DELTA);
                        }
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'A') { // append to the end of the line
                        window.moveCursorToEndOfLine();
                        window.cursorRight(1, false);
                        window.enterInsertMode();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'a') { // append after the current column
                        window.cursorRight(1, false);
                        window.enterInsertMode();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'i') { // insert before the current column
                        window.enterInsertMode();
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'I') { // insert at the start of the line
                        window.cursorLeft(window.getCurrentLineOffset());
                        window.enterInsertMode();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'O') { // open a line above the current line
                        window.insertLineBeforeCurrentLine(model.getEmptyText());
                        window.enterInsertMode();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'o') { // open a line below the current line
                        window.insertLineAfterCurrentLine(model.getEmptyText());
                        window.enterInsertMode();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'd') { // delete the current line
                        register = window.deleteCurrentLine();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'J') { // join the current line with the next line
                        window.joinCurrentLine();
                        window.refreshCurrentRow();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'y') { // yank the current line into register
                        register = window.yankCurrentLine();
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'P') { // put the register line on the current line
                        if (register != null) {
                            window.insertLineBeforeCurrentLine(model.createText(register));
                            window.refreshCurrentRow();
                            s.refresh(Screen.RefreshType.DELTA);
                        }
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'p') { // put the register line on the next line
                        if (register != null) {
                            window.insertLineAfterCurrentLine(model.createText(register));
                            window.refreshCurrentRow();
                            s.refresh(Screen.RefreshType.DELTA);
                        }
                        continue;
                    }

                    //t.bell();
                }
            } finally {
                s.stopScreen();
                t.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    static void readBuffer(EditBuffer buffer, String readFileName) throws IOException {
        try {
            Reader r = new InputStreamReader(Files.newInputStream(Paths.get(readFileName), StandardOpenOption.READ));
            model = new EditBuffer(r);
            r.close();
            displayMessage("\""+readFileName+"\" "+model.getLineCount() + " lines, " + model.getCharacterCount() + " characters" );
        } catch (NoSuchFileException e) {
            model = new EditBuffer();
            displayMessage("New file \""+readFileName+"\"");
        } catch (IOException e) {
            System.err.println("IO Error reading file: "+ fileName);
        }
    }

    static void writeBuffer(EditBuffer buffer, String writeFileName) throws IOException {
        Writer w = new BufferedWriter(
                new OutputStreamWriter(
                        Files.newOutputStream(Paths.get(writeFileName),
                                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)));

        Object marker = buffer.getLineMarker(0);

        int lineCount = 0;
        long characterCount = 0;
        while (marker != null) {
            String line = buffer.getLine(marker).toString();
            w.write(line);
            w.write('\n');
            marker = buffer.getLineMarker(marker, 1);
            lineCount++;
            characterCount += (line.length() + 1);
        }
        w.close();
        displayMessage("\""+writeFileName+"\" "+lineCount + " lines, " + characterCount + " characters" );
    }

    static void executeCommand(String command) throws IOException {

        String[] cmd = command.split(" ");

        if (cmd[0].equals("q")) {
            exit = true;
            return;
        }

        if (cmd[0].equals("w") || cmd[0].equals("wq")) {

            if (cmd.length > 1) {
                fileName = cmd[1];
            }

            if (fileName == null) {
                displayMessage("No file name", true);
                return;
            }

            try {
                writeBuffer(model, fileName);
            } catch (IOException e) {
                displayMessage("IO failure writing file: "+fileName, true);
                return;
            }

            if (command.equals("wq")) {
                exit = true;
            }
            return;
        }
    }

    static void enterCommandMode() {
        commandMode = true;
        command = new StringBuilder(80);
        clearCommandLine();
        s.setCharacter(0, screenRows, COMMAND_PROMPT);

        commandCursor = new TerminalPosition(1+ command.length(), screenRows);
        s.setCursorPosition(commandCursor);
    }

    static void addCommandCharacter(char c) {
        command.append(c);
        s.setCharacter(commandCursor, new TextCharacter(c));
        commandCursor = commandCursor.withRelativeColumn(1);
        s.setCursorPosition(commandCursor);
    }

    static void removeCommandCharacter() {
        if (command.length() == 0) {
            return;
        }
        command.delete(command.length()-1, command.length());
        commandCursor = commandCursor.withRelativeColumn(-1);
        s.setCharacter(commandCursor, BLANK);
        s.setCursorPosition(commandCursor);
    }

    static void clearCommandLine() {
        for (int col=0; col<screenCols; col++) {
            s.setCharacter(col, screenRows, BLANK);
        }
    }

    static String exitCommandMode() {
        assert (commandMode == true);
        commandMode = false;
        for (int c=0; c<=screenCols; c++) {
            s.setCharacter(c, screenRows, BLANK);
        }
        return command.toString();
    }

    static void displayMessage(String message) throws IOException {
        displayMessage(message, false);
    }

    static void displayMessage(String message, boolean alert) throws IOException {
        int col = 0;
        for (char c : message.toCharArray()) {
            TextCharacter tc;
            if (alert) {
                tc = new TextCharacter(c, TextColor.ANSI.WHITE, TextColor.ANSI.RED, SGR.BOLD);
            } else {
                tc = new TextCharacter(c);
            }
            s.setCharacter(col++, screenRows, tc);
        }
    }
}
