package org.rowland.jinix.coreutilities.jed;
public interface UndoEntry {
    int getLine();
    int getInsertPoint();
    void undo();
}
