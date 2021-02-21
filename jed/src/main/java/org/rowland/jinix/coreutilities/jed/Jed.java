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
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.ProcessSignalHandler;
import org.rowland.jinix.proc.ProcessManager;

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

    static TextCharacter COMMAND_PROMPT = new TextCharacter(':', TextColor.ANSI.WHITE, TextColor.ANSI.BLACK);
    static TextCharacter BLANK = new TextCharacter(' ', TextColor.ANSI.WHITE, TextColor.ANSI.BLACK);

    static String fileName;
    static EditBuffer model;
    static Window window;
    static JinixTerminal t;
    static TerminalScreen s;
    static int screenRows;
    static int screenCols;
    static String register;
    static RegisterMode registerMode;
    static boolean exit = false;
    static boolean commandMode;
    static String statusMessage;
    static StatusMode statusMode;

    // Command Mode Data
    private static StringBuilder command;
    private static TerminalPosition commandCursor;

    private static StringBuilder keyBuffer = new StringBuilder(16);

    enum StatusMode {
        NORMAL,
        BOLD,
        ALERT
    }

    enum RegisterMode {
        LINES,
        VISUAL
    }

    public static boolean isKeyword(char c) {
        return (Character.isLetterOrDigit(c) || c == '_');
    }

    public static void main(String[] args) {

        try {
            if (args.length > 0) {
                fileName = args[0];
            }

            screenCols = JinixRuntime.getRuntime().getTerminalColumns();
            screenRows = JinixRuntime.getRuntime().getTerminalLines();

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
                            s.refresh(Screen.RefreshType.COMPLETE);
                        } catch (IOException e) {
                            // Nothing can be done here, so ignore.
                        }
                        return false; // Return false so that the CONTINUE default signal handling will resume our process
                    }

                    if (signal == ProcessManager.Signal.WINCH) {
                        screenCols = JinixRuntime.getRuntime().getTerminalColumns();
                        screenRows = JinixRuntime.getRuntime().getTerminalLines();
                        try {
                            t.getTerminalSize();
                            s.doResizeIfNecessary();
                            window.setSize(screenCols, screenRows-1);
                            displayStatusLine();
                            s.refresh(Screen.RefreshType.DELTA);
                        } catch (IOException e) {
                            // Nothing to do here
                        }
                    }

                    return false;
                }
            });

            t = new JinixTerminal(System.in, System.out, Charset.defaultCharset(), UnixLikeTerminal.CtrlCBehaviour.TRAP);
            t.getInputDecoder().setTimeoutUnits(2); // This must be > 0 so that multi-character key sequences are processed correctly
            s = new TerminalScreen(t);

            s.startScreen();

            try {
                if (fileName != null) {
                    readBuffer(model, fileName);
                } else {
                    model = new EditBuffer();
                }

                window = new Window(model, 0,0, screenCols, screenRows-1);
                window.drawScreen();
                displayStatusLine();
                s.refresh(Screen.RefreshType.COMPLETE);

                while (!exit) {
                    KeyStroke k = s.readInput();

                    if (commandMode) {
                        if (k.getKeyType() == KeyType.Escape) {
                            exitCommandMode();
                            window.restoreCursor();
                            clearCommandLine();
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
                            displayStatusLine();
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
                            window.exitInsertMode(true);
                            setStatusMessage("");
                            s.setCursorPosition(window.cursor);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Backspace && !k.isCtrlDown()) {
                            if (window.getCurrentLineOffset() > 0) {
                                window.insertBuffer.delete();
                                s.setCursorPosition(window.cursor);
                                s.refresh(Screen.RefreshType.DELTA);
                            }
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Character && k.isCtrlDown() && k.getCharacter() == 'u') {
                            if (window.insertBuffer.length() > 0) {
                                window.insertBuffer.clear();
                                s.setCursorPosition(window.cursor);
                                s.refresh(Screen.RefreshType.DELTA);
                            }
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Enter && !k.isCtrlDown()) {
                            window.exitInsertMode(false);
                            window.insertLineAfterCurrentLine(model.getEmptyText());
                            window.cursorLeft(1);
                            window.enterInsertMode();
                            s.setCursorPosition(window.cursor);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.ArrowRight && !k.isCtrlDown()) {
                            if (window.getCurrentLineOffset() < window.getCurrentLine().length()) {
                                window.exitInsertMode(false);
                                window.cursorRight(true);
                                window.enterInsertMode();
                                displayStatusLine();
                                s.setCursorPosition(window.cursor);
                                s.refresh(Screen.RefreshType.DELTA);
                            }
                            continue;
                        }

                        if (k.getKeyType() == KeyType.ArrowLeft && !k.isCtrlDown()) {
                            window.exitInsertMode(false);
                            window.cursorLeft();
                            window.enterInsertMode();
                            displayStatusLine();
                            s.setCursorPosition(window.cursor);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.ArrowDown && !k.isCtrlDown()) {
                            window.exitInsertMode(false);
                            window.cursorDown();
                            window.enterInsertMode();
                            displayStatusLine();
                            s.setCursorPosition(window.cursor);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.ArrowUp && !k.isCtrlDown()) {
                            window.exitInsertMode(false);
                            window.cursorUp();
                            window.enterInsertMode();
                            displayStatusLine();
                            s.setCursorPosition(window.cursor);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Character && !k.isCtrlDown()) {
                            window.insertCharacter(k.getCharacter());
                            displayStatusLine();
                            s.setCursorPosition(window.cursor);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        continue;
                    } // Insert Mode

                    // cursor movement works in normal and visual mode
                    if (!k.isCtrlDown()) {
                        if (Character.valueOf('j').equals(k.getCharacter()) || k.getKeyType() == KeyType.ArrowDown) {
                            processMovement(window::cursorDown, false);
                            continue;
                        }

                        if (Character.valueOf('k').equals(k.getCharacter()) || k.getKeyType() == KeyType.ArrowUp) {
                            processMovement(window::cursorUp, false);
                            continue;
                        }

                        if (Character.valueOf('h').equals(k.getCharacter()) || k.getKeyType() == KeyType.ArrowLeft) {
                            processMovement(window::cursorLeft, false);
                            continue;
                        }

                        if (Character.valueOf('l').equals(k.getCharacter()) || k.getKeyType() == KeyType.ArrowRight) {
                            processMovement(window::cursorRight, false);
                            continue;
                        }

                        if (Character.valueOf('0').equals(k.getCharacter()) || k.getKeyType() == KeyType.Home) {
                            if (keyBuffer.length() == 0) {
                                window.moveCursorToStartOfLine();
                                s.setCursorPosition(window.cursor);
                                displayStatusLine();
                                s.refresh(Screen.RefreshType.DELTA);
                                continue;
                            }
                        }

                        if (Character.valueOf('$').equals(k.getCharacter()) || k.getKeyType() == KeyType.End) {
                            window.moveCursorToEndOfLine();
                            keyBuffer.delete(0, keyBuffer.length());
                            s.setCursorPosition(window.cursor);
                            displayStatusLine();
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (Character.valueOf('w').equals(k.getCharacter())) {
                            processMovement(window::moveCursorToBeginningOfWordOrPunctuation, false);
                            continue;
                        }

                        if (Character.valueOf('W').equals(k.getCharacter())) {
                            processMovement(window::moveCursorToBeginningOfWord, false);
                            continue;
                        }

                        if (Character.valueOf('e').equals(k.getCharacter())) {
                            processMovement(window::moveCursorToEndOfWordOrPunctuation, false);
                            continue;
                        }

                        if (Character.valueOf('E').equals(k.getCharacter())) {
                            processMovement(window::moveCursorToEndOfWord, false);
                            continue;
                        }

                        if (Character.valueOf('b').equals(k.getCharacter())) {
                            processMovement(window::moveCursorBackwardToBeginningOfWordOrPunctuation, false);
                            continue;
                        }

                        if (Character.valueOf('B').equals(k.getCharacter())) {
                            processMovement(window::moveCursorBackwardToBeginningOfWord, false);
                            continue;
                        }
                    }

                    if (window.visualMode || window.visualLineMode) {
                        if (k.getKeyType() == KeyType.Escape) {
                            window.clearSelection();
                            if (window.visualMode) window.exitVisualMode();
                            if (window.visualLineMode) window.exitVisualLineMode();
                            setStatusMessage("");
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'd') { // delete the current selection
                            register = window.deleteCurrentSelection();
                            registerMode = RegisterMode.VISUAL;
                            if (window.visualMode) window.exitVisualMode();
                            if (window.visualLineMode) window.exitVisualLineMode();
                            setStatusMessage("");
                            s.setCursorPosition(window.cursor);
                            displayStatusLine();
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'y') { // yank the current selection
                            register = window.yankCurrentSelection();
                            registerMode = RegisterMode.VISUAL;
                            if (window.visualMode) window.exitVisualMode();
                            if (window.visualLineMode) window.exitVisualLineMode();
                            setStatusMessage("");
                            s.setCursorPosition(window.cursor);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }

                        continue;
                    } // Visual Mode

                    if (k.getKeyType() == KeyType.Character && k.isCtrlDown()) {
                        if (k.getCharacter() == 'l') { // refresh the screen
                            window.drawScreen();
                            displayStatusLine();
                            s.refresh(Screen.RefreshType.AUTOMATIC);
                            continue;
                        }
                        if (k.getCharacter() == 'e') { // move window 1 line downwards
                            window.downLine();
                            displayStatusLine();
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }
                        if (k.getCharacter() == 'y') { // move window 1 line upwards
                            window.upLine();
                            displayStatusLine();
                            s.setCursorPosition(window.cursor);
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }
                        if (k.getCharacter() == 'd') { // move cursor 1/2 page forward (downwards)
                            window.downRows(window.height / 2);
                            displayStatusLine();
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }
                        if (k.getCharacter() == 'f') { // move cursor 1 page forward (downward)
                            if (window.downRows(window.height)) {
                                displayStatusLine();
                                s.refresh(Screen.RefreshType.AUTOMATIC);
                            }
                            continue;
                        }
                        if (k.getCharacter() == 'u') { // move cursor 1/2 page backwards (upwards)
                            window.upRows(window.height / 2);
                            displayStatusLine();
                            s.refresh(Screen.RefreshType.DELTA);
                            continue;
                        }
                        if (k.getCharacter() == 'b') { // move cursor 1 page backwards (upwards)
                            if (window.upRows(window.height)) {
                                displayStatusLine();
                                s.refresh(Screen.RefreshType.AUTOMATIC);
                            }
                            continue;
                        }
                        continue;
                    } // Ctrl Keys

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'v') {
                        window.enterVisualMode();
                        setVisualStatusMessage();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'V') {
                        window.enterVisualLineMode();
                        setVisualLineStatusMessage();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'G') {
                        if (window.gotoLine(model.getLineCount()-1)) {
                            displayStatusLine();
                            s.setCursorPosition(window.cursor);
                            s.refresh(Screen.RefreshType.AUTOMATIC);
                        }
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == ':') {
                        enterCommandMode();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'x') { // delete character to right
                        if (window.deleteCharacter()) {
                            displayStatusLine();
                            s.setCursorPosition(window.cursor);
                            s.refresh(Screen.RefreshType.DELTA);
                        }
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'D') { // delete line to the right
                        if (window.deleteLineToRight()) {
                            displayStatusLine();
                            s.setCursorPosition(window.cursor);
                            s.refresh(Screen.RefreshType.DELTA);
                        }
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'A') { // append to the end of the line
                        window.moveCursorToEndOfLine();
                        window.cursorRight(true);
                        window.enterInsertMode();
                        setInsertStatusMessage();
                        s.setCursorPosition(window.cursor);
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'a') { // append after the current column
                        window.cursorRight(true);
                        window.enterInsertMode();
                        setInsertStatusMessage();
                        s.setCursorPosition(window.cursor);
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'i') { // insert before the current column
                        window.enterInsertMode();
                        setInsertStatusMessage();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'I') { // insert at the start of the line
                        window.moveCursorToStartOfLine();
                        window.enterInsertMode();
                        setInsertStatusMessage();
                        s.setCursorPosition(window.cursor);
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'O') { // open a line above the current line
                        window.insertLineBeforeCurrentLine(model.getEmptyText());
                        window.enterInsertMode(true);
                        setInsertStatusMessage();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'o') { // open a line below the current line
                        window.insertLineAfterCurrentLine(model.getEmptyText());
                        window.enterInsertMode(true);
                        setInsertStatusMessage();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'd') { // delete the current line
                        if (Jed.keyBuffer.toString().startsWith("d")) {
                            processMovement(window::cursorDown, true);
                        } else if (Jed.keyBuffer.length() == 0) {
                            Jed.keyBuffer.append('d');
                        } else {
                            Jed.keyBuffer.delete(0, Jed.keyBuffer.length());
                        }

                        displayStatusLine();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'J') { // join the current line with the next line
                        window.joinCurrentLine();
                        s.setCursorPosition(window.cursor);
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'y') { // yank the current line into register
                        if (Jed.keyBuffer.toString().startsWith("y")) {
                            processMovement(window::cursorDown, true);
                        } else if (keyBuffer.length() == 0) {
                            Jed.keyBuffer.append('y');
                        } else {
                            Jed.keyBuffer.delete(0, Jed.keyBuffer.length());
                        }
                        displayStatusLine();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'P') { // put the register before the cursor
                        if (register != null) {
                            if (registerMode == RegisterMode.LINES) {
                                window.insertLineBeforeCurrentLine(model.createText(register));
                            } else {
                                window.insertSelectionBeforeCursor(model.createTextFromSelection(register));
                            }
                            s.refresh(Screen.RefreshType.DELTA);
                        }
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'p') { // put the register after the cursor
                        if (register != null) {
                            if (registerMode == RegisterMode.LINES) {
                                window.insertLineAfterCurrentLine(model.createText(register));
                                window.insertLineAfterCurrentLine(model.createText(register));
                            } else {
                                window.insertSelectionAfterCursor(model.createTextFromSelection(register));
                            }
                            s.refresh(Screen.RefreshType.DELTA);
                        }
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && k.getCharacter() == 'u') { // undo the last change to the buffer
                        UndoEntry undo = model.popUndo();
                        if (undo != null) {
                            window.refreshLayout();
                            window.gotoLine(undo.getLine());
                            window.moveCursorToPositionInLine(undo.getInsertPoint());
                            displayStatusLine();
                            window.drawScreen();
                            s.refresh(Screen.RefreshType.AUTOMATIC);
                        }
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Character && Character.isDigit(k.getCharacter())) {
                        keyBuffer.append(k.getCharacter());
                        displayStatusLine();
                        s.refresh(Screen.RefreshType.DELTA);
                        continue;
                    }

                    if (k.getKeyType() == KeyType.Escape) {
                        if (keyBuffer.length() > 0) {
                            keyBuffer.delete(0, keyBuffer.length());
                            displayStatusLine();
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

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    static void readBuffer(EditBuffer buffer, String readFileName) throws IOException {
        try {
            Reader r = new InputStreamReader(Files.newInputStream(Paths.get(readFileName), StandardOpenOption.READ));
            model = new EditBuffer(r);
            r.close();
            setStatusMessage("\""+readFileName+"\" "+model.getLineCount() + " lines, " + model.getCharacterCount() + " characters" );
        } catch (NoSuchFileException e) {
            model = new EditBuffer();
            setStatusMessage("New file \""+readFileName+"\"");
        } catch (IOException e) {
            System.err.println("IO Error reading file: "+ fileName);
        }
    }

    static void writeBuffer(EditBuffer buffer, String writeFileName) throws IOException {
        Writer w = new BufferedWriter(
                new OutputStreamWriter(
                        Files.newOutputStream(Paths.get(writeFileName),
                                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)));

        LineMarker marker = buffer.getLineMarker(0);

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
        setStatusMessage("\""+writeFileName+"\" "+lineCount + " lines, " + characterCount + " characters" );
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
                setStatusMessage("No file name", StatusMode.ALERT);
                return;
            }

            try {
                writeBuffer(model, fileName);
            } catch (IOException e) {
                setStatusMessage("IO failure writing file: "+fileName, StatusMode.ALERT);
                return;
            }

            if (command.equals("wq")) {
                exit = true;
            }
            return;
        }

        boolean commandIsNumber = true;
        for (char c :command.trim().toCharArray()) {
            if (!Character.isDigit(c)) {
                commandIsNumber = false;
                break;
            }
        }

        if (commandIsNumber) {
            int newLine = Integer.parseInt(command);
            if (newLine > 0 && newLine <= model.getLineCount())
            if (window.gotoLine(newLine - 1)) {
                s.refresh(Screen.RefreshType.AUTOMATIC);
            }
        }
    }

    static void enterCommandMode() {
        commandMode = true;
        command = new StringBuilder(80);
        clearCommandLine();
        s.setCharacter(0, screenRows-1, COMMAND_PROMPT);

        commandCursor = new TerminalPosition(1+ command.length(), screenRows-1);
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
            s.setCharacter(col, screenRows-1, BLANK);
        }
    }

    static String exitCommandMode() {
        assert (commandMode == true);
        commandMode = false;
        for (int c=0; c<=screenCols; c++) {
            s.setCharacter(c, screenRows-1, BLANK);
        }
        return command.toString();
    }

    static void setStatusMessage(String message){
        setStatusMessage(message, StatusMode.NORMAL);
    }

    static void setStatusMessage(String message, StatusMode mode) {
        statusMessage = message;
        statusMode = mode;
        displayStatusLine();
    }

    private static void displayStatusLine() {
        int col = 0;
        if (statusMessage != null) {
            for (char c : statusMessage.toCharArray()) {
                TextCharacter tc;
                if (statusMode == StatusMode.ALERT) {
                    tc = new TextCharacter(c, TextColor.ANSI.WHITE, TextColor.ANSI.RED, SGR.BOLD);
                } else if (statusMode == StatusMode.BOLD) {
                    tc = new TextCharacter(c, TextColor.ANSI.WHITE, TextColor.ANSI.BLACK, SGR.BOLD);
                } else {
                    tc = new TextCharacter(c, TextColor.ANSI.WHITE, TextColor.ANSI.BLACK);
                }

                s.setCharacter(col++, screenRows - 1, tc);

                if (col > (screenCols - 15)) {
                    break;
                }
            }
        }
        while (col < (screenCols - 15)) {
            s.setCharacter(col++, screenRows-1, BLANK);
        }

        TextCharacter[] cursorPositionText = TextCharacter.fromString(
                keyBuffer.toString(),
                TextColor.ANSI.WHITE,
                TextColor.ANSI.BLACK);
        for (TextCharacter c : cursorPositionText) {
            s.setCharacter(col++, screenRows - 1, c);
        }

        while (col < (screenCols - 9)) {
            s.setCharacter(col++, screenRows-1, BLANK);
        }

        if (window != null) {
            cursorPositionText = TextCharacter.fromString(
                    window.getCursorPositionString(),
                    TextColor.ANSI.WHITE,
                    TextColor.ANSI.BLACK);
            for (TextCharacter c : cursorPositionText) {
                s.setCharacter(col++, screenRows - 1, c);
            }
        }

        while (col < (screenCols)) {
            s.setCharacter(col++, screenRows-1, BLANK);
        }
    }

    private static void processMovement(Runnable action, boolean lineMode) throws IOException {
        String buffer = keyBuffer.toString();
        keyBuffer.delete(0, keyBuffer.length());
        int repeatCount = 1;
        if (lineMode) {
            repeatCount = 0;
        }
        if (!window.visualMode && !window.visualLineMode) {
            if (buffer.startsWith("d")) {
                if (buffer.length() > 1) {
                    try {
                        repeatCount = Integer.parseInt(buffer, 1, buffer.length(), 10) - (lineMode ? 1 : 0);
                    } catch (NumberFormatException e) {
                        return;
                    }
                }
                if (lineMode) {
                    window.enterVisualLineMode();
                } else {
                    window.enterVisualMode();
                }
                while (repeatCount-- > 0) {
                    action.run();
                }
                register = window.deleteCurrentSelection();
                registerMode = RegisterMode.VISUAL;
                if (lineMode) {
                    window.exitVisualLineMode();
                } else {
                    window.exitVisualMode();
                }
                s.setCursorPosition(window.cursor);
                displayStatusLine();
                s.refresh(Screen.RefreshType.DELTA);
                return;
            }
            if (buffer.startsWith("y")) {
                if (buffer.length() > 1) {
                    try {
                        repeatCount = Integer.parseInt(buffer, 1, buffer.length(), 10) - (lineMode ? 1 : 0);
                    } catch (NumberFormatException e) {
                        return;
                    }
                }
                if (lineMode) {
                    window.enterVisualLineMode();
                } else {
                    window.enterVisualMode();
                }
                while (repeatCount-- > 0) {
                    action.run();
                }
                register = window.yankCurrentSelection();
                registerMode = RegisterMode.VISUAL;
                if (lineMode) {
                    window.exitVisualLineMode();
                } else {
                    window.exitVisualMode();
                }
                s.setCursorPosition(window.cursor);
                displayStatusLine();
                s.refresh(Screen.RefreshType.DELTA);
                return;
            }
        }
        if (buffer.length() > 0) {
            try {
                repeatCount = Integer.parseInt(buffer, 0, buffer.length(), 10);
            } catch (NumberFormatException e) {
                return;
            }
        }
        while (repeatCount-- > 0) {
            action.run();
        }
        s.setCursorPosition(window.cursor);
        displayStatusLine();
        s.refresh(Screen.RefreshType.DELTA);
    }

    private static void setInsertStatusMessage() {
        setStatusMessage("-- INSERT --", StatusMode.BOLD);
    }

    private static void setVisualStatusMessage() {
        setStatusMessage("-- VISUAL --", StatusMode.BOLD);
    }

    private static void setVisualLineStatusMessage() {
        setStatusMessage("-- VISUAL LINE --", StatusMode.BOLD);
    }
}
