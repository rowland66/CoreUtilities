package org.rowland.jinix.coreutilities.jed;

import org.ahmadsoft.ropes.Rope;
import org.ahmadsoft.ropes.RopeBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Text for editing represented as a linked list of Character arrays.
 */
class EditBuffer {

    private boolean dirty;
    private OpenLinkedList<Rope> lineList;
    private final RopeBuilder ropeBuilder = new RopeBuilder();
    private long characterCount;
    private Deque<UndoEntry> undo = new ArrayDeque(20);
    private List<EditBufferChangeListener> listeners = new LinkedList<>();

    EditBuffer() {
        lineList = new OpenLinkedList<>();
        lineList.add(ropeBuilder.build(""));
        characterCount = 0;
    }

    EditBuffer(Reader bufferData) throws IOException {
        lineList = new OpenLinkedList<>();
        BufferedReader input = new BufferedReader(bufferData);
        String line;
        characterCount = 0;
        while ((line = input.readLine()) != null ) {
            lineList.add(ropeBuilder.build(line));
            characterCount += line.length();
        }
    }

    void registerChangeListener(EditBufferChangeListener listener) {
        listeners.add(listener);
    }

    int getLineCount() {
        return lineList.size();
    }

    long getCharacterCount() {
        return characterCount;
    }

    /**
     * This is the most inefficient way to get a line marker. The line marker is opaque to the caller, but can be passed
     * to other methods in the EditBuffer to retrieve a line String.
     *
     * @param lineNumber
     */
     LineMarker getLineMarker(int lineNumber) {
         return lineList.getNodeReference(lineNumber);
     }

    /**
     * Get marker for the last line in the EditBuffer
     * @return
     */
     LineMarker getLastLineMarker() {
         return lineList.getLastNodeReference();
     }

     int getLineDifference(LineMarker minuend, LineMarker subtractend) {
         int difference = 0;
         while (minuend != subtractend) {
             subtractend = lineList.getNodeReference(((OpenLinkedList.NodeReference<Rope>) subtractend), 1);
             difference++;
         }
         return difference;
     }

    /**
     * An efficient way to get a line marker when the offset value is small.
     *
     * @param marker
     * @param offset
     * @return
     */
     LineMarker getLineMarker(LineMarker marker, int offset) {
         return lineList.getNodeReference((OpenLinkedList.NodeReference<Rope>) marker, offset);
     }

     Rope getLine(LineMarker marker) {
         return ((OpenLinkedList.NodeReference<Rope>) marker).getItem();
     }

    /**
     * Get a line Rope at an offset from the given marker
     *
     * @param marker
     * @param offset
     * @return
     */
     Rope getLine(LineMarker marker, int offset) {
         OpenLinkedList.NodeReference<Rope> ref = lineList.getNodeReference((OpenLinkedList.NodeReference<Rope>) marker, offset);
         if (ref == null) {
             return null;
         }
         return ref.getItem();
     }

    void setLine(Object marker, int offset, Rope rope) {
        OpenLinkedList.NodeReference<Rope> ref = lineList.getNodeReference((OpenLinkedList.NodeReference<Rope>) marker, offset);
        if (ref == null) {
            return;
        }
        ref.setItem(rope);
    }

    void insert(Rope text) {
        lineList.add(text);
    }

    void insertBefore(LineMarker marker, Rope text) {
        OpenLinkedList.NodeReference<Rope> ref = (OpenLinkedList.NodeReference<Rope>) marker;
        ref.insertBefore(lineList, text);
    }

    LineMarker nextLineMarker(LineMarker marker) {
        return ((OpenLinkedList.NodeReference<Rope>) marker).next();
    }

    Rope deleteLine(LineMarker marker) {
         if (((OpenLinkedList.NodeReference<Rope>) marker).getItem() == lineList.getFirst()) {
             return deleteFirst();
         } else {
             return deleteAfter(((OpenLinkedList.NodeReference<Rope>) marker).previous());
         }
    }

    Rope deleteFirst() {
         Rope rtrnValue = lineList.removeFirst();
         if (lineList.size() == 0) {
             lineList.add(ropeBuilder.build(""));
         }
         return rtrnValue;
    }

    Rope deleteAfter(LineMarker marker) {
        OpenLinkedList.NodeReference<Rope> ref = (OpenLinkedList.NodeReference<Rope>) marker;
        return ref.deleteNext(lineList);
    }

    Rope getEmptyText() {
         return ropeBuilder.build("");
    }

    Rope createText(String text) {
         return ropeBuilder.build(text);
    }

    List<Rope> createTextFromSelection(String text) {
        List<Rope> rtrnList = Arrays.stream(text.split("\n"))
                .map(Rope.BUILDER::build)
                .collect(Collectors.toList());
        if (text.endsWith("\n")) {
            rtrnList.add(Rope.BUILDER.build(""));
        }
        return rtrnList;
    }

    void pushEditUndo(int line, int insertPoint, Rope value) {
        undo.push(new EditUndoEntry(line, insertPoint, value));
    }

    void addEditUndo(MultiLineEditUndoEntry mlEntry, int line, int insertPoint, Rope value) {
         mlEntry.add(new EditUndoEntry(line, insertPoint, value));
    }

    void pushInsertUndo(int line) {
         undo.push(new InsertUndoEntry(line));
    }

    void addInsertUndo(MultiLineEditUndoEntry mlEntry, int line) {
         mlEntry.add(new InsertUndoEntry(line));
    }

    void pushDeleteUndo(int line, Rope value) {
         undo.push(new DeleteUndoEntry(line, value));
    }

    void addDeleteUndo(MultiLineEditUndoEntry mlEntry, int line, Rope value) {
         mlEntry.add(new DeleteUndoEntry(line, value));
    }

    void pushJoinUndo(int line, Rope joinedLine, Rope deletedLine) {
         undo.push(new JoinUndoEntry(line, joinedLine, deletedLine));
    }

    void addJoinUndo(MultiLineEditUndoEntry mlEntry, int line, Rope joinedLine, Rope deletedLine) {
         mlEntry.add(new JoinUndoEntry(line, joinedLine, deletedLine));
    }

    void pushMultiLineUndo(MultiLineEditUndoEntry undoEntry) {
         undo.push(undoEntry);
    }

    UndoEntry popUndo() {
         if (undo.size() == 0) {
             return null;
         }
         UndoEntry u = undo.pop();
         u.undo();
         return u;
    }

    static class MultiLineEditUndoEntry implements UndoEntry {
        List<UndoEntry> entries = new LinkedList<>();

        void add(UndoEntry entry) {
            entries.add(entry);
        }

        @Override
        public int getLine() {
            return entries.get(0).getLine();
        }

        @Override
        public int getInsertPoint() {
            return entries.get(0).getInsertPoint();
        }

        @Override
        public void undo() {
            int s = entries.size()-1;
            while (s > -1) {
                entries.get(s).undo();
                s--;
            }
        }
    }

    private class EditUndoEntry implements UndoEntry {
        int line;
        int insertPoint;
        Rope value;

        private EditUndoEntry(int line, int insertPoint, Rope value) {
            this.line = line;
            this.insertPoint = insertPoint;
            this.value = value;
        }

        @Override
        public int getInsertPoint() {
            return this.insertPoint;
        }

        @Override
         public int getLine() {
             return this.line;
         }

        @Override
         public void undo() {
             EditBuffer.this.lineList.set(line, value);
         }
    }

    private class DeleteUndoEntry implements UndoEntry{
         int line;
         Rope value;

         private DeleteUndoEntry(int line, Rope value) {
             this.line = line;
             this.value = value;
         }

        @Override
        public int getInsertPoint() {
            return 0;
        }

        @Override
        public int getLine() {
            return this.line;
        }

        @Override
        public void undo() {
            if (line == -1) {
                EditBuffer.this.lineList.add(value);
            } else {
                EditBuffer.this.lineList.add(line, value);
                EditBuffer.this.listeners.forEach(l -> l.addLine(line));
            }
        }
    }

    private class InsertUndoEntry implements UndoEntry {
        int line;

        private InsertUndoEntry(int line) {
            this.line = line;
        }

        @Override
        public int getInsertPoint() {
            return 0;
        }

        @Override
        public int getLine() {
            return line;
        }

        @Override
        public void undo() {
            EditBuffer.this.lineList.remove(line);
            EditBuffer.this.listeners.forEach(l -> l.deleteLine(line));
        }
    }

    private class JoinUndoEntry implements UndoEntry {
         int line;
         Rope joinedLine;
         Rope deletedLine;

         private JoinUndoEntry(int line, Rope joinedLine, Rope deletedLine) {
             this.line = line;
             this.joinedLine = joinedLine;
             this.deletedLine = deletedLine;
         }

        @Override
        public int getLine() {
            return line;
        }

        @Override
        public int getInsertPoint() {
            return joinedLine.length()-1;
        }

        @Override
        public void undo() {
            EditBuffer.this.lineList.set(line, joinedLine);
            EditBuffer.this.lineList.add(line+1, deletedLine);
            EditBuffer.this.listeners.forEach(l -> l.addLine(line+1));
        }
    }
}
