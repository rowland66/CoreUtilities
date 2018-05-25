package org.rowland.jinix.ui;

import com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.terminal.LocalMode;
import org.rowland.jinix.terminal.TerminalAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class JinixTerminal extends UnixLikeTerminal {

    short termId;
    TerminalAttributes oldAttributes = null;

    public JinixTerminal(InputStream terminalInput,
                            OutputStream terminalOutput,
                            Charset terminalCharset,
                            CtrlCBehaviour terminalCtrlCBehaviour)
            throws IOException
    {
        super(terminalInput,terminalOutput,terminalCharset,terminalCtrlCBehaviour);

        termId = JinixRuntime.getRuntime().getProcessTerminalId();

        realAcquire();
    }

    @Override
    protected void acquire() throws IOException {
        // Hack!
    }

    public void realAcquire() throws IOException {
        super.acquire();
    }

    @Override
    protected void registerTerminalResizeListener(Runnable onResize) throws IOException {

    }

    @Override
    protected void saveTerminalSettings() throws IOException {
        if (oldAttributes == null) {
            oldAttributes = JinixRuntime.getRuntime().getTerminalAttributes(termId);
        }
    }

    @Override
    protected void restoreTerminalSettings() throws IOException {
        JinixRuntime.getRuntime().setTerminalAttributes(termId, oldAttributes);
    }

    @Override
    protected void keyEchoEnabled(boolean enabled) throws IOException {
        TerminalAttributes attr = JinixRuntime.getRuntime().getTerminalAttributes(termId);

        if (attr.localModes.contains(LocalMode.ECHO) && !enabled) {
            attr.localModes.remove(LocalMode.ECHO);
            JinixRuntime.getRuntime().setTerminalAttributes(termId, attr);
        }

        if (!attr.localModes.contains(LocalMode.ECHO) && enabled) {
            attr.localModes.add(LocalMode.ECHO);
            JinixRuntime.getRuntime().setTerminalAttributes(termId, attr);
        }
    }

    @Override
    protected void canonicalMode(boolean enabled) throws IOException {
        TerminalAttributes attr = JinixRuntime.getRuntime().getTerminalAttributes(termId);
        if (attr.localModes.contains(LocalMode.ICANON) && !enabled) {
            attr.localModes.remove(LocalMode.ICANON);
            JinixRuntime.getRuntime().setTerminalAttributes(termId, attr);
            return;
        }

        if (!attr.localModes.contains(LocalMode.ICANON) && enabled) {
            attr.localModes.add(LocalMode.ICANON);
            JinixRuntime.getRuntime().setTerminalAttributes(termId, attr);
            return;
        }
    }

    @Override
    protected void keyStrokeSignalsEnabled(boolean enabled) throws IOException {

    }
}
