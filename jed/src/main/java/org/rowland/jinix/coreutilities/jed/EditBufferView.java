package org.rowland.jinix.coreutilities.jed;

import com.googlecode.lanterna.TextCharacter;
import org.ahmadsoft.ropes.Rope;
import org.ahmadsoft.ropes.RopeBuilder;

/**
 * A view of an EditBuffer. The view maintains a size and position within its underlying EditBuffer.
 */
public class EditBufferView {

    private static Rope EMPTY_LINE = (new RopeBuilder()).build("@");
    private EditBuffer buffer;
    private Object topLineRef; //EditBuffer marker for the top visible line
    private Object bottomLineRef; //EditBuffer marker for the last visible line
    private int width, height;
    private int[] rowLine; // a mapping from the screen row to the buffer line offset
    private int virtualHeight; // the number of rows visible in view. May be less than height if multi-row lines
    private int visibleLines; // the number of lines visible in the view.

    EditBufferView(EditBuffer buf, int width, int height) {
        buffer = buf;
        this.width = width;
        this.height = height;
        topLineRef = buffer.getLineMarker(0);
        rowLine = new int[height];
        layout();
    }

    /**
     * Get a screen row as an array of TextCharacters
     * @param row
     * @return
     */
    Rope getRowText(int row) {
        int line = rowLine[row];
        if (line == -1) {
            return EMPTY_LINE;
        }

        int rowLinePos = 1;
        while (row > 0 && rowLine[--row] == line) {
            rowLinePos++;
        }
        Rope lineRope = buffer.getLine(topLineRef, line);

        return lineRope.subSequence((rowLinePos-1)*width, Math.min(rowLinePos*width, lineRope.length()));
    }

    /**
     * Scroll the edit buffer rows down so that the EditBufferView shows rows earlier in the buffer.
     *
     * @param scrollRows
     * @return
     */
    int scrollDownRows(int scrollRows) {
        int scrolledRows = 0;
        Object previousTopLineRef = topLineRef;
        while (scrolledRows < scrollRows) {
            topLineRef = buffer.getLineMarker(topLineRef, -1);
            if (topLineRef.equals(previousTopLineRef)) {
                break;
            }
            scrolledRows += getRowsPerLine(buffer.getLine(topLineRef).length());
            previousTopLineRef = topLineRef;
        }
        layout();
        return scrolledRows;
    }

    /**
     * Scroll the EditBuffer rows down by the given number of lines so that the EditBufferView shows rows earlier in the
     * file. A line may cover several rows, so this method behaves differently than scrollDownRows().
     *
     * @param scrollLines
     * @return
     */
    int scrollDown(int scrollLines) {
        int scrolledRows = 0;
        int scrolledLines = 0;
        Object previousTopLineRef = topLineRef;
        while (scrolledLines < scrollLines) {
            topLineRef = buffer.getLineMarker(topLineRef, -1);
            if (topLineRef.equals(previousTopLineRef)) {
                break;
            }
            scrolledRows += getRowsPerLine(buffer.getLine(topLineRef).length());
            scrolledLines++;
            previousTopLineRef = topLineRef;
        }
        layout();
        return scrolledRows;
    }

    /**
     * Scroll the edit buffer rows up so that the EditBufferView shows rows later in the buffer.
     *
     * @param scrollRows
     * @return
     */
    int scrollUpRows(int scrollRows) {
        int scrolledRows = 0;
        Object previousBottomLineRef = bottomLineRef;
        topLineRef = null;
        while (scrolledRows < scrollRows && topLineRef != buffer.getLineMarker(1)) {
            topLineRef = buffer.getLineMarker(topLineRef, 1);
            if (topLineRef == null) {
                topLineRef = bottomLineRef;
            }
            if (bottomLineRef.equals(previousBottomLineRef)) {
                break;
            }
            previousBottomLineRef = bottomLineRef;
            scrolledRows += getRowsPerLine(buffer.getLine(bottomLineRef).length());
        }

        //scrolledRows += setTopLineRef();
        layout();
        return scrolledRows;
    }

    int scrollUp(int scrollLines) {
        int scrolledRows = 0;
        int scrolledLines = 0;
        Object previousBottomLineRef = bottomLineRef;
        while (scrolledLines < scrollLines) {
            bottomLineRef = buffer.getLineMarker(bottomLineRef, 1);
            if (bottomLineRef.equals(previousBottomLineRef)) {
                break;
            }
            scrolledRows += getRowsPerLine(buffer.getLine(bottomLineRef).length());
            scrolledLines++;
        }

        scrolledRows += setTopLineRef();
        layout();
        return scrolledRows;
    }

    int getVirtualHeight() {
        return virtualHeight;
    }

    int getVisibleLines() {
        return visibleLines;
    }

    int getLineIndex(int row) {
        return rowLine[row];
    }

    Rope getLine(int index) {
        return buffer.getLine(topLineRef, index);
    }

    Rope getLineForRow(int row) {
        int index = getLineIndex(row);
        if (index == -1) {
            return null;
        }
        return buffer.getLine(topLineRef, index);
    }

    /**
     * Replace the line for a row and return the number of rows used by the old line if it differs from the
     * new line. If the rows required by the line changes, then update the layout.
     *
     * @param row
     * @param value
     * @return the number rows required by the old line
     */
    int setLineForRow(int row, Rope value) {
        int newLineRows = getRowsPerLine(value.length());
        int index = getLineIndex(row);
        if (index == -1) {
            return 0;
        }
        int oldLineRows = getRowsPerLine(getLine(index).length());
        buffer.setLine(topLineRef, index, value);
        if (newLineRows != oldLineRows) {
            layout();
            return oldLineRows;
        }
        return 0;
    }

    int getLineOffset(int row, int column) {
        int line = rowLine[row];
        if (line == -1) {
            return -1;
        }
        int rowLinePos = 0;
        while (row > 0 && rowLine[--row] == line) {
            rowLinePos++;
        }
        Rope lineRope = buffer.getLine(topLineRef, line);

        return Math.min((rowLinePos)*width + column, lineRope.length());
    }

    int getLineFirstRow(int index) {
        int i=0;
        for (i=0; i<rowLine.length && rowLine[i] > -1; i++) {
            if (rowLine[i] == index) {
                return i;
            }
        }
        throw new RuntimeException("Invalid row index: "+index);
    }

    int getLineLastRow(int index) {
        boolean indexFound = false;
        int i=0;
        for (i=0; i<rowLine.length && rowLine[i] > -1; i++) {
            if (rowLine[i] == index) {
                indexFound = true;
                continue;
            }
            if (indexFound) {
                return i-1;
            }
        }
        if (indexFound) {
            return (i - 1);
        }
        throw new RuntimeException("Invalid row index: "+index);
    }

    int getLineRowCount(int index) {
        boolean indexFound = false;
        int rowCount = 0;
        int i=0;
        for (i=0; i<rowLine.length && rowLine[i] > -1; i++) {
            if (rowLine[i] == index) {
                indexFound = true;
                rowCount++;
                continue;
            }
            if (indexFound) {
                return rowCount;
            }
        }
        if (indexFound) {
            return rowCount;
        }
        throw new RuntimeException("Invalid row index: "+index);
    }

    void insertLineAfter(int lineIndex, Rope text) {
        Object insertPoint = buffer.getLineMarker(topLineRef, lineIndex);
        if (insertPoint == null || insertPoint.equals(topLineRef)) { // this means we inserted after the line before the first line
            buffer.insertFirst(text);
            topLineRef = buffer.getLineMarker(0);
        } else {
            buffer.insertAfter(insertPoint, text);
        }
        layout();
    }

    Rope deleteLine(int lineIndex) {
        Rope rtrnValue;
        Object deletePoint = buffer.getLineMarker(topLineRef, lineIndex-1);
        if (deletePoint.equals(buffer.getLineMarker(topLineRef, lineIndex))) {
            rtrnValue = buffer.deleteFirst();
            topLineRef = buffer.getLineMarker(0);
        } else {
            rtrnValue = buffer.deleteAfter(deletePoint);
        }
        layout();
        return rtrnValue;
    }

    private int getFirstLineRowCount() {
        int firstLine = rowLine[0];
        int i = 0;
        int count = 0;
        while(rowLine[i++] == firstLine) {
            count++;
        }
        return count;
    }

    private int getLastLineRowCount() {
        int count = 0;
        int i = height-1;
        while (rowLine[i] == -1) {
            count++;
            i--;
        }
        int firstLine = rowLine[i];
        while(rowLine[i--] == firstLine) {
            count++;
        }
        return count;
    }

    /**
     * When we scroll up, we move the bottomLineRef. This method set the topLineRef, and if the top line is multiple
     * lines, returns the number of rows to add at the bottom to show a full top line.
     *
     * @return
     */
    private int setTopLineRef() {
        int rowsRemaining = height;
        int lineOffset = 0;
        int currentRow = 0;
        topLineRef = bottomLineRef;
        while (rowsRemaining > 0) {
            Rope rope = buffer.getLine(bottomLineRef, lineOffset);
            int rowsPerLine = getRowsPerLine(rope.length());
            if (rowsPerLine > rowsRemaining) {
                break;
            }
            rowsRemaining -= rowsPerLine;
            lineOffset--;
        }
        topLineRef = buffer.getLineMarker(bottomLineRef, lineOffset+1);
        return rowsRemaining;
    }

    private void layout() {
        int rowsRemaining = height;
        int lineOffset = 0;
        int currentRow = 0;
        while (rowsRemaining > 0) {
            Rope rope = buffer.getLine(topLineRef, lineOffset);
            if (rope == null) {
                for (int i=0; i<rowsRemaining; i++) {
                    rowLine[currentRow+i] = -1;
                }
                rowsRemaining = 0;
                continue;
            }
            int rowsPerLine = getRowsPerLine(rope.length());
            if (rowsPerLine > rowsRemaining) {
                for (int i=0; i<rowsRemaining; i++) {
                    rowLine[currentRow+i] = -1;
                }
                rowsRemaining = 0;
            } else {
                for (int i=0; i<rowsPerLine; i++) {
                    rowLine[currentRow+i] = lineOffset;
                }
                currentRow += rowsPerLine;
                rowsRemaining -= rowsPerLine;
                lineOffset++;
            }
        }
        visibleLines = lineOffset;
        bottomLineRef = buffer.getLineMarker(topLineRef, lineOffset-1);
        calculateVirtualHeight();
    }

    private void calculateVirtualHeight() {
        int count = 0;
        int i = height-1;
        while (rowLine[i] == -1) {
            count++;
            i--;
        }
        virtualHeight = height - count;
    }

    int getRowsPerLine(int characterCount) {
        if (characterCount == 0) {
            return 1;
        }
        return Math.floorDiv(characterCount, width) + ((characterCount % width) > 0 ? 1 : 0);
    }
}
