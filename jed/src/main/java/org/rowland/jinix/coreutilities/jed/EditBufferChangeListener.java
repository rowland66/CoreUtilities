package org.rowland.jinix.coreutilities.jed;

public interface EditBufferChangeListener {

    void addLine(int lineNumber);

    void deleteLine(int lineNumber);
}
