package org.rowland.jinix.coreutilities.vmore;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.ProcessSignalHandler;
import org.rowland.jinix.proc.ProcessManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Visual More utility. Like Unix more, but better. Similar to less.
 */
public class VMore {

    private static final char[] EMPTY_LINE = new char[] {'~'};

    static FileModel model;
    static int topLine = 0;
    static JinixTerminal t;
    static TerminalScreen s;
    static int screenRows = 23;
    static int screenCols = 80;

    public static void main(String[] args) {

        try {
            String fileName = args[0];
            try {
                InputStream is = Files.newInputStream(Paths.get(fileName), StandardOpenOption.READ);
                model = new FileModel(is);
                is.close();
            } catch (FileNotFoundException e) {
                System.err.println(fileName + ": No such file or directory");
            } catch (IOException e) {
                System.err.println("IO Error reading file: "+ fileName);
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
            s = new TerminalScreen(t);

            s.startScreen();
            s.setCursorPosition(new TerminalPosition(0,screenRows));
            drawScreen();
            s.refresh(Screen.RefreshType.COMPLETE);

            while (true) {
                KeyStroke k = s.readInput();

                if (k.getKeyType() == KeyType.Character && (k.getCharacter() == 'q' || k.getCharacter() == 'Q')) {
                    break;
                }

                if (k.getKeyType() == KeyType.ArrowDown) {
                    topLine++;
                    s.scrollLines(0, screenRows-1, 1);
                    char[] lineData = model.getLine(topLine+screenRows-1);
                    if (lineData == null) {
                        lineData = EMPTY_LINE;
                    }
                    drawLine(screenRows-1, lineData);
                    s.refresh(Screen.RefreshType.DELTA);
                    continue;
                }

                if (k.getKeyType() == KeyType.ArrowUp && topLine > 0) {
                    topLine--;
                    s.scrollLines(0, screenRows-1, -1);
                    drawLine(0, model.getLine(topLine));
                    s.refresh(Screen.RefreshType.DELTA);
                    continue;
                }

                t.bell();
            }

            s.stopScreen();
            t.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void drawScreen() {
        int row = 0;
        for (int l=topLine; l<(topLine+23); l++) {
            drawLine(row++, model.getLine(l));
        }
    }

    private static void drawLine(int row, char[] lineData) {
        for (int col=0; col<lineData.length; col++) {
            s.setCharacter(col, row, new TextCharacter(lineData[col]));
        }
    }
}
