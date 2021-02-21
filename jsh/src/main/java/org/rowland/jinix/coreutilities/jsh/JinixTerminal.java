package org.rowland.jinix.coreutilities.jsh;

import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.impl.AbstractTerminal;
import org.jline.utils.InfoCmp;
import org.jline.utils.InputStreamReader;
import org.jline.utils.NonBlockingReader;
import org.jline.utils.NonBlockingReaderImpl;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.terminal.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class JinixTerminal extends AbstractTerminal {
    final static Map<SpecialCharacter, Attributes.ControlChar> controlCharMap = Map.of (
            SpecialCharacter.VEOF, Attributes.ControlChar.VEOF,
            SpecialCharacter.VEOL, Attributes.ControlChar.VEOL,
            SpecialCharacter.VERASE, Attributes.ControlChar.VERASE,
            SpecialCharacter.VINTR, Attributes.ControlChar.VINTR,
            SpecialCharacter.VKILL, Attributes.ControlChar.VKILL,
            SpecialCharacter.VQUIT, Attributes.ControlChar.VQUIT,
            SpecialCharacter.VSTART, Attributes.ControlChar.VSTART,
            SpecialCharacter.VSTOP, Attributes.ControlChar.VSTOP,
            SpecialCharacter.VSUSP, Attributes.ControlChar.VSUSP
    );

    final static Map<InputMode, Attributes.InputFlag> inputModeMap = Map.of (
            InputMode.ICRNL, Attributes.InputFlag.ICRNL,
            InputMode.IGNCR, Attributes.InputFlag.IGNCR,
            InputMode.INLCR, Attributes.InputFlag.INLCR,
            InputMode.IMAXBEL, Attributes.InputFlag.IMAXBEL,
            InputMode.IXANY, Attributes.InputFlag.IXANY,
            InputMode.IXOFF, Attributes.InputFlag.IXOFF,
            InputMode.IXON, Attributes.InputFlag.IXON
    );

    final static Map<OutputMode, Attributes.OutputFlag> outputModeMap = Map.of (
            OutputMode.OCRNL, Attributes.OutputFlag.OCRNL,
            OutputMode.ONLCR, Attributes.OutputFlag.ONLCR,
            OutputMode.ONLRET, Attributes.OutputFlag.ONLRET,
            OutputMode.ONOCR, Attributes.OutputFlag.ONOCR,
            OutputMode.OPOST, Attributes.OutputFlag.OPOST
    );

    final static Map<LocalMode, Attributes.LocalFlag> localModeMap = new HashMap<>() {{
            put(LocalMode.ECHO, Attributes.LocalFlag.ECHO);
            put(LocalMode.ECHOCTL, Attributes.LocalFlag.ECHOCTL);
            put(LocalMode.ECHOE, Attributes.LocalFlag.ECHOE);
            put(LocalMode.ECHOK, Attributes.LocalFlag.ECHOK);
            put(LocalMode.ECHOKE, Attributes.LocalFlag.ECHOKE);
            put(LocalMode.ECHONL, Attributes.LocalFlag.ECHONL);
            put(LocalMode.ICANON, Attributes.LocalFlag.ICANON);
            put(LocalMode.IEXTEN, Attributes.LocalFlag.IEXTEN);
            put(LocalMode.ISIG, Attributes.LocalFlag.ISIG);
            put(LocalMode.NOFLSH, Attributes.LocalFlag.NOFLSH);
            put(LocalMode.PENDIN, Attributes.LocalFlag.PENDIN);
            put(LocalMode.TOSTOP, Attributes.LocalFlag.TOSTOP);
    }};

    private short terminalId;
    private NonBlockingReader reader;
    private PrintWriter writer;

    public JinixTerminal(String name, String type, Charset encoding, SignalHandler signalHandler) throws IOException{
        super(name, type, encoding, signalHandler);
        this.terminalId = JinixRuntime.getRuntime().getProcessTerminalId();
        reader = new NonBlockingReaderImpl("Jinix", new BufferedReader(new InputStreamReader(System.in)));
        writer = new PrintWriter(new OutputStreamWriter(System.out));

        Optional.of(System.getProperty("jinix.terminal.term"))
                .map(term -> JinixTerminal.class.getClassLoader().getResourceAsStream(term+".term"))
                .map(is -> new BufferedReader(new InputStreamReader(is)))
                .map(br -> br.lines().collect(Collectors.joining("\n")))
                .ifPresent(caps -> InfoCmp.parseInfoCmp(caps, bools, ints, strings));
    }

    @Override
    public NonBlockingReader reader() {
        return reader;
    }

    @Override
    public PrintWriter writer() {
        return writer;
    }

    @Override
    public InputStream input() {
        return System.in;
    }

    @Override
    public OutputStream output() {
        return System.out;
    }

    @Override
    public Attributes getAttributes() {
        TerminalAttributes jinixAttributes = JinixRuntime.getRuntime().getTerminalAttributes(terminalId);
        Attributes jlineAttributes = new Attributes();
        jlineAttributes.setInputFlag(Attributes.InputFlag.BRKINT, false);
        jlineAttributes.setInputFlag(Attributes.InputFlag.INPCK, false);
        jlineAttributes.setInputFlag(Attributes.InputFlag.ICRNL, jinixAttributes.inputModes.contains(InputMode.ICRNL));
        jlineAttributes.setInputFlag(Attributes.InputFlag.INLCR, jinixAttributes.inputModes.contains(InputMode.INLCR));
        jlineAttributes.setInputFlag(Attributes.InputFlag.IGNBRK, false);
        jlineAttributes.setInputFlag(Attributes.InputFlag.IGNCR, jinixAttributes.inputModes.contains(InputMode.IGNCR));
        jlineAttributes.setInputFlag(Attributes.InputFlag.IGNPAR, false);
        jlineAttributes.setInputFlag(Attributes.InputFlag.IMAXBEL, jinixAttributes.inputModes.contains(InputMode.IMAXBEL));
        jlineAttributes.setInputFlag(Attributes.InputFlag.ISTRIP, false);
        jlineAttributes.setInputFlag(Attributes.InputFlag.IUTF8, false);
        jlineAttributes.setInputFlag(Attributes.InputFlag.IXANY, jinixAttributes.inputModes.contains(InputMode.IXANY));
        jlineAttributes.setInputFlag(Attributes.InputFlag.IXOFF, jinixAttributes.inputModes.contains(InputMode.IXOFF));
        jlineAttributes.setInputFlag(Attributes.InputFlag.IXON, jinixAttributes.inputModes.contains(InputMode.IXON));
        jlineAttributes.setInputFlag(Attributes.InputFlag.PARMRK, false);

        jlineAttributes.setOutputFlag(Attributes.OutputFlag.BSDLY, false);
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.CRDLY, false);
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.FFDLY, false);
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.NLDLY, false);
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.OCRNL, jinixAttributes.outputModes.contains(OutputMode.OCRNL));
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.OFDEL, false);
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.OFILL, false);
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.ONLCR, jinixAttributes.outputModes.contains(OutputMode.ONLCR));
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.ONLRET, jinixAttributes.outputModes.contains(OutputMode.ONLRET));
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.ONOCR, jinixAttributes.outputModes.contains(OutputMode.ONOCR));
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.ONOEOT, false);
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.OPOST, jinixAttributes.outputModes.contains(OutputMode.OPOST));
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.OXTABS, false);
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.TABDLY, false);
        jlineAttributes.setOutputFlag(Attributes.OutputFlag.VTDLY, false);

        jlineAttributes.setLocalFlag(Attributes.LocalFlag.ALTWERASE, false);
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.ECHO, jinixAttributes.localModes.contains(LocalMode.ECHO));
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.ECHOCTL, jinixAttributes.localModes.contains(LocalMode.ECHOCTL));
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.ECHOE, jinixAttributes.localModes.contains(LocalMode.ECHOE));
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.ECHOK, jinixAttributes.localModes.contains(LocalMode.ECHOK));
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.ECHOKE, jinixAttributes.localModes.contains(LocalMode.ECHOKE));
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.ECHONL, jinixAttributes.localModes.contains(LocalMode.ECHONL));
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.ECHOPRT, false);
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.EXTPROC, false);
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.FLUSHO, false);
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.ICANON, jinixAttributes.localModes.contains(LocalMode.ICANON));
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.IEXTEN, jinixAttributes.localModes.contains(LocalMode.IEXTEN));
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.ISIG, jinixAttributes.localModes.contains(LocalMode.ISIG));
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.NOFLSH, jinixAttributes.localModes.contains(LocalMode.NOFLSH));
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.NOKERNINFO, true);
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.PENDIN, jinixAttributes.localModes.contains(LocalMode.PENDIN));
        jlineAttributes.setLocalFlag(Attributes.LocalFlag.TOSTOP, jinixAttributes.localModes.contains(LocalMode.TOSTOP));

        jinixAttributes.specialCharacterMap.forEach(
                (key, value) -> jlineAttributes.setControlChar(controlCharMap.get(key), value)
        );

        return jlineAttributes;
    }

    @Override
    public void setAttributes(Attributes attributes) {
        TerminalAttributes jinixAttributes = new TerminalAttributes();
        jinixAttributes.inputModes = EnumSet.noneOf(InputMode.class);
        jinixAttributes.outputModes = EnumSet.noneOf(OutputMode.class);
        jinixAttributes.localModes = EnumSet.noneOf(LocalMode.class);
        jinixAttributes.specialCharacterMap = new HashMap<>();

        inputModeMap.entrySet().forEach(
                (e -> {if (attributes.getInputFlag(e.getValue())) jinixAttributes.inputModes.add(e.getKey());})
        );

        outputModeMap.entrySet().forEach(
                (e -> {if (attributes.getOutputFlag(e.getValue())) jinixAttributes.outputModes.add(e.getKey());})
        );

        localModeMap.entrySet().forEach(
                (e -> {if (attributes.getLocalFlag(e.getValue())) jinixAttributes.localModes.add(e.getKey());})
        );

        controlCharMap.forEach(
                (key, value) -> jinixAttributes.specialCharacterMap.put(key, (byte) attributes.getControlChar(value)));

        JinixRuntime.getRuntime().setTerminalAttributes(terminalId, jinixAttributes);
    }

    @Override
    public Size getSize() {
        int columns = JinixRuntime.getRuntime().getTerminalColumns();
        int lines = JinixRuntime.getRuntime().getTerminalLines();
        return new Size(columns, lines);
    }

    @Override
    public void setSize(Size size) {
        // Do nothing as we always get our size from the Jinix runtime.
    }
}
