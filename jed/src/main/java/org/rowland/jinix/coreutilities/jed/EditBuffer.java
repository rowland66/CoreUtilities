package org.rowland.jinix.coreutilities.jed;

import org.ahmadsoft.ropes.Rope;
import org.ahmadsoft.ropes.RopeBuilder;
import org.rowland.jinix.IllegalOperationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * Text for editing represented as a linked list of Character arrays.
 */
class EditBuffer {

    private boolean dirty;
    private OpenLinkedList<Rope> lineList;
    private RopeBuilder ropeBuilder = new RopeBuilder();
    private long characterCount;
    private Deque<RedoEntry> redo = new ArrayDeque(20);

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
     Object getLineMarker(int lineNumber) {
         return lineList.getNodeReference(lineNumber);
     }

    /**
     * An efficient way to get a line marker when the offset value is small.
     *
     * @param marker
     * @param offset
     * @return
     */
     Object getLineMarker(Object marker, int offset) {
         return lineList.getNodeReference((OpenLinkedList.NodeReference<Rope>) marker, offset);
     }

     Rope getLine(Object marker) {
         return ((OpenLinkedList.NodeReference<Rope>) marker).getItem();
     }

    /**
     * Get a line Rope at an offset from the given marker
     *
     * @param marker
     * @param offset
     * @return
     */
     Rope getLine(Object marker, int offset) {
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

    void insertFirst(Rope text) {
        if (lineList.isEmpty()) {
            lineList.set(0, text);
        } else {
            lineList.add(0, text);
        }
    }

    void insertAfter(Object marker, Rope text) {
        OpenLinkedList.NodeReference<Rope> ref = (OpenLinkedList.NodeReference<Rope>) marker;
        ref.insertAfter(text);
    }

    Rope deleteFirst() {
         Rope rtrnValue = lineList.removeFirst();
         lineList.add(ropeBuilder.build(""));
         return rtrnValue;
    }

    Rope deleteAfter(Object marker) {
        OpenLinkedList.NodeReference<Rope> ref = (OpenLinkedList.NodeReference<Rope>) marker;
        return ref.deleteNext();
    }

    Rope getEmptyText() {
         return ropeBuilder.build("");
    }

    Rope createText(String text) {
         return ropeBuilder.build(text);
    }

    private static class RedoEntry {
         int line;
         Rope value;

         private RedoEntry(int line, Rope value) {
             this.line = line;
             this.value = value;
         }
    }
}
