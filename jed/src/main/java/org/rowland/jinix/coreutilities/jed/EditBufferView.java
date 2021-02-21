package org.rowland.jinix.coreutilities.jed;

import com.googlecode.lanterna.TerminalPosition;
import org.ahmadsoft.ropes.Rope;
import org.ahmadsoft.ropes.RopeBuilder;

/**
 * A view of an EditBuffer. The view maintains a size and position within its underlying EditBuffer.
 */
public class EditBufferView {

    private static Rope EMPTY_LINE = (new RopeBuilder()).build("@");
    private EditBuffer buffer;
    private int topLineIndex; // Index of the top line in the EditBuffer
    private LineMarker topLineRef; //EditBuffer marker for the top visible line
    private LineMarker bottomLineRef; //EditBuffer marker for the last visible line
    private int width, height;
    private int[] rowLine; // a mapping from the screen row to the buffer line offset
    private int virtualHeight; // the number of rows visible in view. May be less than height if multi-row lines
    private int visibleLines; // the number of lines visible in the view.

    EditBufferView(EditBuffer buf, int width, int height) {
        buffer = buf;
        this.width = width;
        this.height = height;
        topLineIndex = 0;
        topLineRef = buffer.getLineMarker(0);
        rowLine = new int[height];
        buffer.registerChangeListener(new LineChangeListener());
        layout();
    }

    void setSize(int cols, int rows) {
        this.width = cols;
        this.height = rows;
        this.rowLine = new int[rows];
        layout();
    }

    /**
     * Get the line in the EditBuffer for the given screen row
     * @param row the screen row
     * @return
     */
    int getRowAbsoluteLine(int row) {
        int line = rowLine[row];
        if (line == -1) {
            return -1;
        }

        return topLineIndex + line;
    }

    int getLineIndexAbsoluteLine(int lineIndex) {
        return topLineIndex + lineIndex;
    }

    int getPositionLineOffset(int row, int column) {
        return getPositionLineOffset(row, column, false);
    }

    /**
     * Get the position in a line for a given pair of screen coordinates
     * @param row
     * @param column
     * @return
     */
    int getPositionLineOffset(int row, int column, boolean insertMode) {
        int line = rowLine[row];
        if (line == -1) {
            return -1;
        }

        int rowLinePos = row - getLineFirstRow(getLineIndex(row));

        Rope lineRope = buffer.getLine(topLineRef, line);

        return Math.min((rowLinePos)*width + column, Math.max(0, lineRope.length()-(insertMode ? 0 : 1)));
    }

    TerminalPosition getCursorBasedOnPositionInLine(int lineIndex, int offset) {
        int row = getLineFirstRow(lineIndex);
        row += offset / width;
        int col = offset % width;
        return new TerminalPosition(col, row);
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

    BufferMark getPositionMarker(TerminalPosition cursor) {
        return new BufferMark(
                getRowAbsoluteLine(cursor.getRow()),
                getPositionLineOffset(cursor.getRow(), cursor.getColumn())
            );
    }

    /**
     * Scroll the edit buffer rows down so that the EditBufferView shows rows earlier in the buffer.
     *
     * @param scrollRows
     * @return
     */
    int scrollDownRows(int scrollRows) {
        int scrolledRows = 0;
        LineMarker previousTopLineRef = topLineRef;
        while (scrolledRows < scrollRows) {
            topLineRef = buffer.getLineMarker(topLineRef, -1);
            if (topLineRef.equals(previousTopLineRef)) {
                break;
            }
            topLineIndex--;
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
        LineMarker previousTopLineRef = topLineRef;
        while (scrolledLines < scrollLines) {
            topLineRef = buffer.getLineMarker(topLineRef, -1);
            if (topLineRef.equals(previousTopLineRef)) {
                break;
            }
            topLineIndex--;
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
        while (scrolledRows < scrollRows && !bottomLineRef.equals(buffer.getLastLineMarker())) {
            scrolledRows += getRowsPerLine(buffer.getLine(topLineRef).length());
            topLineRef = buffer.getLineMarker(topLineRef, 1);
            topLineIndex++;
            layout();
        }

        return scrolledRows;
    }

    /**
     * Scroll the next line into view and return number of rows scrolled.
     * @return
     */
    int scrollUp() {
        int scrolledRows = 0;
        LineMarker previousBottomLineRef = bottomLineRef;
        while (!bottomLineRef.equals(buffer.getLastLineMarker())) {
            scrolledRows += getRowsPerLine(buffer.getLine(topLineRef).length());
            topLineRef = buffer.getLineMarker(topLineRef, 1);
            topLineIndex++;
            layout();

            if (!bottomLineRef.equals(previousBottomLineRef)) {
                break;
            }
        }
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
        return getLine(index);
    }

    LineMarker getLineMarkerForRow(int row) {
        int index = getLineIndex(row);
        if (index == -1) {
            return null;
        }
        return getLineMarkerForLineIndex(index);
    }

    LineMarker getLineMarkerForLineIndex(int lineIndex) {
        return buffer.getLineMarker(topLineRef, lineIndex);
    }

    int getLineIndexForLineMarker(LineMarker lineMarker) {
        int lineIndex = 0;
        LineMarker marker = topLineRef;
        while(!marker.equals(bottomLineRef)) {
            if (marker.equals(lineMarker)) {
                return lineIndex;
            }
            marker = buffer.nextLineMarker(marker);
            lineIndex++;
        }
        if (marker.equals(lineMarker)) {
            return lineIndex;
        } else {
            return -1;
        }
    }

    int getRelativeLineIndex(int absoluteLine) {
        if (isLineVisible(absoluteLine)) {
            return absoluteLine-topLineIndex;
        }
        return -1;
    }

    /**
     * Replace the line for a row and return the number of rows used by the old line if it differs from the
     * new line. If the rows required by the line changes, then update the layout.
     *
     * @param row
     * @param value
     * @return the number rows required by the old line if different from the new line and no scrolling, or
     *         -scrolledRows if scrolling was required.
     */
    int setLineForRow(int row, Rope value) {
        int index = getLineIndex(row);
        if (index == -1) { // if row is empty, then
            index = getLineIndex(row - 1);
        }
        return setLine(index, value);
    }

    int setLine(int lineIndex, Rope value) {
        int newLineRows = getRowsPerLine(value.length());
        int oldLineRows = getRowsPerLine(getLine(lineIndex).length());
        buffer.setLine(topLineRef, lineIndex, value);
        if (newLineRows != oldLineRows) {
            int lineFirstRow = getLineFirstRow(lineIndex);
            if (lineFirstRow + newLineRows > height) {
                int scrolledRows = scrollUpRows(lineFirstRow + newLineRows - height);
                layout();
                return -scrolledRows;
            }
            layout();
            return oldLineRows;
        }
        return 0;
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

    void scrollToAbsoluteLine(int absoluteLine) {
        scrollToMarker(buffer.getLineMarker(absoluteLine));
        topLineIndex=absoluteLine;
    }

    void scrollToMarker(LineMarker marker) {
        topLineRef = marker;
        layout();
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

    boolean isLineVisible(int lineIndex) {
        return (lineIndex >= topLineIndex && (lineIndex - topLineIndex) < getVisibleLines());
    }

    boolean isLineVisible(LineMarker lineMarker) {
        LineMarker marker = topLineRef;
        while(!marker.equals(bottomLineRef)) {
            if (marker.equals(lineMarker)) {
                return true;
            }
            marker = buffer.nextLineMarker(marker);
        }
        return marker.equals(lineMarker);
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

    int insertLineAtOffset(int lineOffset, Rope text) {
        LineMarker insertPoint = buffer.getLineMarker(topLineRef, lineOffset);
        // if the buffer is empty or we are inserting before the first line
        if (insertPoint == null) {
            buffer.insert(text);
        } else {
            buffer.insertBefore(insertPoint, text);
            if (lineOffset == 0) {
                topLineRef = buffer.getLineMarker(topLineRef, -1);
            }
        }
        layout();
        if (isLineVisible(topLineIndex+lineOffset)) {
            return getRowsPerLine(text.length());
        } else {
            return 0;
        }

    }

    Rope deleteLine(int lineIndex) {
        Rope rtrnValue;
        LineMarker deletePoint = buffer.getLineMarker(topLineRef, lineIndex-1);
        if (deletePoint.equals(buffer.getLineMarker(topLineRef, lineIndex))) {
            rtrnValue = buffer.deleteFirst();
            topLineRef = buffer.getLineMarker(0);
            topLineIndex = 0;
        } else {
            if (lineIndex == 0) {
                if (topLineRef.equals(buffer.getLastLineMarker())) {
                    topLineRef = buffer.getLineMarker(topLineRef, -1);
                    topLineIndex--;
                } else {
                    topLineRef = buffer.getLineMarker(topLineRef, 1);
                    topLineIndex++;
                }
            }
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
        while (i > -1 && rowLine[i] == -1) {
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

    class LineChangeListener implements EditBufferChangeListener {
        @Override
        public void addLine(int lineNumber) {
            if (lineNumber <= topLineIndex) {
                topLineIndex++;
            }
        }

        @Override
        public void deleteLine(int lineNumber) {
            if (lineNumber <= topLineIndex) {
                topLineIndex--;
            }
        }
    }
}
