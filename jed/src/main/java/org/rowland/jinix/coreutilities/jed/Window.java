package org.rowland.jinix.coreutilities.jed;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import org.ahmadsoft.ropes.Rope;

import java.io.IOException;
import java.util.List;

class Window {

    private static final TextColor GRAY = new TextColor.RGB(128,128,128);

    boolean insertMode;
    boolean insertNewLine; // indicates that window is in insertMode with a new line. Needed by Undo.

    // Insert Mode Data
    TextInserter insertBuffer;
    Rope insertLine; // when insertMode is true, this is the line before text was inserted.
    int insertLineNumber; // the line number of the line where insertion began
    int insertPoint; // when insertMode is true, this is the point where insertion began

    boolean visualMode;
    BufferMark selectMark;

    boolean visualLineMode;

    int width, height;

    private EditBufferView view;
    private int top, bottom, left, right;
    private int virtualColumn; // The column that the cursor will jump back to when scrolling.

    TerminalPosition cursor;

    static int skipSpace(Rope line, int position, boolean backwards) {
        if (backwards) {
            while(position > -1 && Character.isSpaceChar(line.charAt(position))) {
                position--;
            }
            return position;
        } else {
            while (position < line.length() && Character.isSpaceChar(line.charAt(position))) {
                position++;
            }
            return position;
        }
    }

    Window(EditBuffer buffer, int left, int top, int width, int height) {
        this.view = new EditBufferView(buffer, width, height);
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.bottom = top+height-1;
        this.right = left+width-1;
        cursor = new TerminalPosition(left, top);
        this.virtualColumn = 0;
        this.insertBuffer = new TextInserter();
    }

    void setSize(int cols, int rows) throws IOException {
        int line = view.getLineIndex(cursor.getRow());
        int positionLineOffset = view.getPositionLineOffset(cursor.getRow(), cursor.getColumn());

        this.width = cols;
        this.height = rows;
        this.bottom = top+height-1;
        this.right = left+width-1;
        view.setSize(cols, rows);
        if(cursor.getRow() > view.getVirtualHeight() - 1) {
            cursor = cursor.withRow(view.getVirtualHeight()-1);
            adjustCursorToLineEnd();
            line = view.getLineIndex(cursor.getRow());
            positionLineOffset = view.getPositionLineOffset(cursor.getRow(), cursor.getColumn());
        }
        int cursorRow = view.getLineFirstRow(line) + (positionLineOffset / this.width);
        int cursorCol = positionLineOffset % this.width;
        cursor = cursor.withRow(cursorRow).withColumn(cursorCol);
        drawScreen();
    }

    String getCursorPositionString() {
        int line = view.getRowAbsoluteLine(cursor.getRow()) + 1;
        int column = view.getPositionLineOffset(cursor.getRow(), cursor.getColumn()) + 1;
        return (line +","+ column);
    }

    void enterInsertMode() {
        enterInsertMode(false);
    }

    void enterInsertMode(boolean newLine) {
        assert (insertMode == false);
        insertMode = true;
        insertNewLine = newLine;
        insertBuffer.reset();
        insertLine = getCurrentLine();
        insertLineNumber = view.getRowAbsoluteLine(cursor.getRow());
        insertPoint = getCurrentLineOffset();
    }

    void exitInsertMode(boolean adjustCursor) throws IOException {
        assert(insertMode == true);
        insertMode = false;
        if (insertBuffer.hasChanged()) {
            saveUndo();
            insertBuffer.reset();
        }
        if (adjustCursor) {
            adjustCursorToLineEnd();
            restoreCursor();
        }
    }

    void enterVisualMode() {
        visualMode = true;
        selectMark = view.getPositionMarker(cursor);
    }

    void exitVisualMode() {
        visualMode = false;
    }

    void enterVisualLineMode() {
        visualLineMode = true;
        selectMark = view.getPositionMarker(cursor);
        drawLineSelection(GRAY);
    }

    void exitVisualLineMode() {
        visualLineMode = false;
    }

    void saveUndo() {
        int line = view.getRowAbsoluteLine(cursor.getRow());
        if (insertNewLine) {
            Jed.model.pushInsertUndo(line);
        } else {
            Jed.model.pushEditUndo(line, insertPoint, insertLine);
        }
    }

    void saveUndoDeleteLine() {
        int line = view.getRowAbsoluteLine(cursor.getRow());
        Jed.model.pushDeleteUndo(line, getCurrentLine());
    }

    void saveUndoJoinLine() {
        int line = view.getRowAbsoluteLine(cursor.getRow());
        Jed.model.pushJoinUndo(line, getCurrentLine(),
                Jed.model.getLine(view.getLineMarkerForRow(cursor.getRow()), 1));
    }

    void insertCharacter(Character c) throws IOException {
        assert(insertMode);
        insertBuffer.append(c);
    }

    boolean deleteCharacter() throws IOException {
        Rope line = getCurrentLine();
        if (line.length() == 0) {
            return false;
        }
        int ropePos = getCurrentLineOffset();
        insertLine = line;
        insertNewLine = false;
        insertLineNumber = view.getRowAbsoluteLine(cursor.getRow());
        insertPoint = ropePos;
        saveUndo();
        Rope newLine = line.delete(ropePos, ropePos + 1);
        setCurrentLine(newLine);
        adjustCursorToLineEnd();
        return true;
    }

    boolean deleteLineToRight() throws IOException {
        Rope line = getCurrentLine();
        if (line.length() == 0) {
            return false;
        }
        int ropePos = getCurrentLineOffset();
        insertLine = line;
        insertNewLine = false;
        insertLineNumber = view.getRowAbsoluteLine(cursor.getRow());
        insertPoint = ropePos;
        saveUndo();
        Rope newLine = line.delete(ropePos, line.length());
        setCurrentLine(newLine);
        adjustCursorToLineEnd();
        return true;
    }

    void restoreCursor() {
        Jed.s.setCursorPosition(cursor);
    }

    private int scrollUp() {
        int preScrollVirtualHeight = view.getVirtualHeight();
        int scrolledRows = view.scrollUp();
        if (scrolledRows > 0) {
            Jed.s.scrollLines(top, top + preScrollVirtualHeight - 1, scrolledRows);
            for (int row = (height - 1); row > (preScrollVirtualHeight - scrolledRows - 1); row--) {
                drawRow(row, view.getRowText(row), false);
            }
        }
        return scrolledRows;
    }

    /**
     * Move the cursor down in the buffer a number of buffer lines. A buffer line may display over multiple rows
     *
     * @throws IOException
     */
    void cursorDown() {
        clearSelection();
        int movedRows = 0;
        int movedLines = 0;
        int currentLine = view.getLineIndex(cursor.getRow());
        int delta = 1; // TODO remove me. No need for this variable
        while (movedLines < delta && currentLine < view.getVisibleLines()-1) {
            if (movedLines == 0) {
                movedRows = view.getLineFirstRow(currentLine) - cursor.getRow();
            }
            movedRows += view.getLineRowCount(currentLine);
            movedLines++;
            currentLine++;
        }

        if (movedRows > 0) {
            cursor = cursor.withRelativeRow(movedRows);
        }

        if (movedLines < delta) {
            int bottomRows = height - cursor.getRow();
            int scrolledRows = scrollUp();
            if (scrolledRows > 0) {
                cursor = cursor.withRelativeRow(-scrolledRows + bottomRows);
            }
        }

        adjustCursorToLineEnd();

        Jed.s.setCursorPosition(cursor);
        drawSelection();
    }

    void downLine() {
        clearSelection();
        int scrolledRows = scrollUp();
        if (cursor.getRow() - scrolledRows < top) {
            cursor = cursor.withRow(top);
        } else {
            cursor = cursor.withRelativeRow(-scrolledRows);
        }
        Jed.s.setCursorPosition(cursor);
        drawSelection();
    }

    boolean downRows(int rows) throws IOException {
        int scrolledRows = view.scrollUpRows(rows);
        if (scrolledRows > 0) {
            drawScreen();
            cursor = cursor.withRow(0).withColumn(0);
            Jed.s.setCursorPosition(cursor);
            return true;
        }
        return false;
    }

    private int scrollDown(int scrollLines) {
        int scrolledRows = view.scrollDown(scrollLines);
        Jed.s.scrollLines(top, bottom, -scrolledRows);
        for (int row=top; row < (scrolledRows); row++) {
            drawRow(row, view.getRowText(row),false);
        }
        for (int row=bottom; view.getLineIndex(row) == -1; row--) {
            drawRow(row, view.getRowText(row), true);
        }
        return scrolledRows;
    }

    void cursorUp() {
        clearSelection();
        int movedLines = 0;
        int currentLine = view.getLineIndex(cursor.getRow());
        int movedRows = cursor.getRow() - view.getLineFirstRow(currentLine); // account for the current line
        while (movedLines < 1 && currentLine > 0) {
            movedLines++;
            currentLine--;
            movedRows += cursor.getRow() - movedRows - view.getLineFirstRow(currentLine);
        }

        if (movedRows > 0) {
            cursor = cursor.withRelativeRow(-movedRows);
        }

        if (movedLines < 1) {
            scrollDown(1-movedLines);
        }

        adjustCursorToLineEnd();

        Jed.s.setCursorPosition(cursor);
        drawSelection();
    }

    void upLine() {
        clearSelection();
        int scrolledRows = scrollDown(1);
        if (cursor.getRow() + scrolledRows > bottom) {
            cursor = cursor.withRow(bottom);
        } else {
            cursor = cursor.withRelativeRow(scrolledRows);
        }
        drawSelection();
    }

    boolean upRows(int rows) throws IOException {
        int scrolledRows = view.scrollDownRows(rows);
        if (scrolledRows > 0) {
            drawScreen();
            cursor = cursor.withRow(view.getVirtualHeight() - 1).withColumn(0);
            Jed.s.setCursorPosition(cursor);
            return true;
        }
        return false;
    }

    void refreshLayout() {
        view.setSize(width, height); // force a refresh of the view layout
    }

    boolean gotoLine(int absoluteLine) {
        int relativeLineIndex = view.getRelativeLineIndex(absoluteLine);
        if (relativeLineIndex > -1) {
            int lineFirstRow = view.getLineFirstRow(relativeLineIndex);
            cursor = cursor.withRow(lineFirstRow).withColumn(0);
            Jed.s.setCursorPosition(cursor);
            return false;
        }
        view.scrollToAbsoluteLine(absoluteLine);
        cursor = cursor.withRow(0).withColumn(0);
        drawScreen();
        return true;
    }

    void gotoLineMarker(LineMarker marker) throws IOException{
        view.scrollToMarker(marker);
        drawScreen();
        cursor = cursor.withRow(0).withColumn(0);
        Jed.s.setCursorPosition(cursor);
    }

    void cursorLeft() {
        cursorLeft(1);
    }

    void cursorLeft(int delta) {
        clearSelection();
        if (cursor.getColumn() - delta < left) {
            int currentRowLineIndex = view.getLineIndex(cursor.getRow());
            if (view.getLineFirstRow(currentRowLineIndex) < cursor.getRow()) {
                cursor = cursor.withColumn(right).withRelativeRow(-1);
            } else {
                cursor = cursor.withColumn(left);
            }
        } else {
            cursor = cursor.withRelativeColumn(-delta);
        }
        virtualColumn = view.getPositionLineOffset(cursor.getRow(), cursor.getColumn());
        drawSelection();
    }

    void cursorRight() {
        cursorRight( false);
    }

    void cursorRight(boolean safeOverride) {
        clearSelection();
        if (cursor.getColumn() + 1 > right) {
            cursor = cursor.withColumn(left).withRelativeRow(1);
            adjustViewForCursor();
        } else {
            cursor = cursor.withRelativeColumn(1);
        }

        virtualColumn = view.getPositionLineOffset(cursor.getRow(), cursor.getColumn(), (safeOverride || insertMode | visualMode));
        adjustCursorToLineEnd(safeOverride);

        drawSelection();
    }

    /**
     * Get the index of the line where the cursor is positioned relative to the top line visible in the view.
     *
     * @return
     */
    int getCurrentLineIndex() {
        return view.getLineIndex(cursor.getRow());
    }

    Rope getCurrentLine() {
        return view.getLineForRow(cursor.getRow());
    }

    int getCurrentLineOffset() {
        return view.getPositionLineOffset(cursor.getRow(), cursor.getColumn(), (insertMode || visualMode));
    }

    void setCurrentLine(Rope value) {
        setLine(view.getLineIndex(cursor.getRow()), cursor.getRow(), cursor.getColumn(), value);
    }

    int setLine(int lineIndex, int row, int col, Rope value) {
        int firstRow = view.getLineFirstRow(lineIndex);
        if (row == -1) row = firstRow;
        if (col == -1) col = 0;
        int preScrollVirtualHeight = view.getVirtualHeight();
        int oldLineRows = view.setLine(lineIndex, value);
        if (oldLineRows < 0) {
            int scrolledRows = -oldLineRows;
            Jed.s.scrollLines(top, top + preScrollVirtualHeight - 1, scrolledRows);
            for (int r = (height - 1); r > (preScrollVirtualHeight - scrolledRows - 1); r--) {
                drawRow(r, view.getRowText(r), false);
            }
            cursor = cursor.withRelativeRow(-scrolledRows);
            row += -scrolledRows;
            refreshRow(lineIndex, row, col);
            return 0;
        }
        if (oldLineRows == 0) {
            refreshRow(lineIndex, row, col);
            return 0;
        }

        int newLineRows = view.getLineRowCount(lineIndex);
        if (newLineRows < oldLineRows) {
            int scrollRows = oldLineRows - newLineRows;
            Jed.s.scrollLines(firstRow + scrollRows - 1, top + preScrollVirtualHeight, scrollRows);
            for (int r = bottom; r > view.getVirtualHeight() - scrollRows - 1; r--) {
                drawRow(r, view.getRowText(r), true);
            }
        } else {
            int scrollRows = newLineRows - oldLineRows;
            Jed.s.scrollLines(row, top + preScrollVirtualHeight, -scrollRows);
            for (int r = bottom; r > top + view.getVirtualHeight()-scrollRows; r--) {
                drawRow(r, view.getRowText(r), true);
            }
        }
        for (int r=row; r<firstRow+newLineRows; r++) {
            drawRow(r, view.getRowText(r), true);
        }
        return newLineRows - oldLineRows;
    }

    void insertLineAfterCurrentLine(Rope text) {
        int currentLineIndex = getCurrentLineIndex();
        int currentLineRow = view.getLineLastRow(currentLineIndex);

        int insertedTextRows = view.insertLineAtOffset(currentLineIndex+1, text);

        boolean shiftedUp = false;
        if (currentLineRow > bottom-insertedTextRows) {
            int scrolledRows = view.scrollUpRows(insertedTextRows);
            Jed.s.scrollLines(0 , view.getVirtualHeight()-1, scrolledRows);
            currentLineIndex = view.getLineIndex(cursor.getRow()-scrolledRows);
            shiftedUp = true;
        }

        int insertedLineFirstRow = view.getLineFirstRow(currentLineIndex+1);

        if (!shiftedUp) {
            Jed.s.scrollLines(insertedLineFirstRow, bottom, -insertedTextRows);
        }

        for (int row=bottom; view.getLineIndex(row) == -1; row--) {
            drawRow(row, view.getRowText(row),true);
        }

        for (int row=insertedLineFirstRow; row<insertedLineFirstRow+insertedTextRows; row++) {
            drawRow(row, view.getLineForRow(row), true);
        }

        if (shiftedUp) {
            for (int row=insertedLineFirstRow+insertedTextRows+1; row<view.getVirtualHeight(); row++) {
                drawRow(row, view.getLineForRow(row), false);
            }
        }
        cursor = cursor.withRow(insertedLineFirstRow).withColumn(left);
        if (!text.isEmpty()) {
            insertNewLine = true;
            saveUndo();
        }
        Jed.s.setCursorPosition(cursor);
    }

    void insertLineBeforeCurrentLine(Rope text) throws IOException {
        if (!text.isEmpty()) {
            insertNewLine = true;
            saveUndo();
        }
        int currentLineIndex = getCurrentLineIndex();
        int currentLineFirstRow = view.getLineFirstRow(currentLineIndex); //

        int insertedTextRows = view.insertLineAtOffset(currentLineIndex, text);

        Jed.s.scrollLines(currentLineFirstRow, bottom, -insertedTextRows);
        for (int row=bottom; view.getLineIndex(row) == -1; row--) {
            drawRow(row, view.getRowText(row),true);
        }

        for (int row=currentLineFirstRow; row<currentLineFirstRow+insertedTextRows; row++) {
            drawRow(row, view.getRowText(row), true);
        }

        cursor = cursor.withRow(currentLineFirstRow).withColumn(left);
        Jed.s.setCursorPosition(cursor);
    }

    void insertSelectionBeforeCursor(List<Rope> text) {
        insertSelection(cursor.getRow(), cursor.getColumn(), text);
    }

    void insertSelectionAfterCursor(List<Rope> text) {
        insertSelection(cursor.getRow(), cursor.getColumn()+1, text);
    }

    void insertSelection(int row, int col, List<Rope> text) {
        int lineIndex = view.getLineIndex(row);
        int position = view.getPositionLineOffset(row, col, true);
        Rope line = view.getLine(lineIndex);
        int scrolledRows = 0;
        if (text.size() > 1) {
            EditBuffer.MultiLineEditUndoEntry undoEntries = new EditBuffer.MultiLineEditUndoEntry();
            Jed.model.addEditUndo(undoEntries, view.getLineIndexAbsoluteLine(lineIndex), position, line);
            Rope tail = line.subSequence(position, line.length());
            scrolledRows += view.setLine(lineIndex, line.delete(position, line.length()).append(text.get(0)));
            for (int i=1; i<text.size(); i++) {
                Jed.model.addInsertUndo(undoEntries, lineIndex + i);
                if (i < (text.size() - 1)) {
                    scrolledRows += view.insertLineAtOffset(lineIndex + i, text.get(i));
                } else {
                    scrolledRows += view.insertLineAtOffset(lineIndex + i, text.get(i).append(tail));
                }
            }
            Jed.model.pushMultiLineUndo(undoEntries);
        } else {
            Jed.model.pushEditUndo(view.getLineIndexAbsoluteLine(lineIndex), position, line);
            scrolledRows += view.setLine(lineIndex, line.insert(position, text.get(0)));
        }

        Jed.s.scrollLines(row, bottom, -scrolledRows);
        for (int r=bottom; view.getLineIndex(r) == -1; r--) {
            drawRow(r, view.getRowText(r),true);
        }

        for (int r=row; r<row+scrolledRows+1; r++) {
            drawRow(r, view.getRowText(r), true);
        }
    }

    class DeleteSelectionProcessor implements SelectionProcessor {
        StringBuilder registerBuilder = new StringBuilder(256);
        EditBuffer.MultiLineEditUndoEntry undoEntries = new EditBuffer.MultiLineEditUndoEntry();
        int deletedRows;
        int lineNumber = -1;
        int blankRows;

        @Override
        public LineMarker processLine(LineMarker lineMarker, int lineNumber, int startPos, int endPos, TerminalPosition selectMarkPosition) {
            Rope line = Jed.model.getLine(lineMarker);

            // Processing for the first and only selected row
            if (this.lineNumber == -1 && selectMarkPosition != null) {
                this.blankRows = height - view.getVirtualHeight();
                if (startPos > 0 || endPos < line.length()) {
                    if (endPos < line.length()) {
                        registerBuilder.append(line.subSequence(startPos, endPos + 1).toString());
                        Jed.model.pushEditUndo(lineNumber, startPos, line);
                        if (view.isLineVisible(lineMarker)) {
                            setLine(view.getLineIndexForLineMarker(lineMarker),
                                    selectMarkPosition.getRow(),
                                    selectMarkPosition.getColumn(),
                                    line.delete(startPos, endPos + 1));
                        }
                    } else {
                        registerBuilder.append(line.subSequence(startPos, endPos).toString()).append('\n');
                        Jed.model.pushJoinUndo(lineNumber, line, Jed.model.getLine(lineMarker, 1));
                        if (view.isLineVisible(lineMarker)) {
                            Rope joinText = view.deleteLine(view.getLineIndexForLineMarker(lineMarker)+1);
                            int rowDelta = setLine(view.getLineIndexForLineMarker(lineMarker),
                                    selectMarkPosition.getRow(),
                                    selectMarkPosition.getColumn(),
                                    line.delete(startPos, endPos).append(joinText));
                            int deleteRows = 0;
                            if (view.getLineIndexForLineMarker(lineMarker)+1 < view.getVisibleLines()) {
                                deleteRows = view.getRowsPerLine(joinText.length());
                                Jed.s.scrollLines(view.getLineFirstRow(view.getLineIndexForLineMarker(lineMarker) + 1),
                                        bottom, deleteRows);
                            }
                            for (int row = bottom; row > (bottom - blankRows - (deleteRows - rowDelta)); row--) {
                                drawRow(row, view.getRowText(row), true);
                            }
                        }
                    }
                } else {
                    if (lineMarker.equals(Jed.model.getLastLineMarker())) {
                        Jed.model.pushDeleteUndo(lineNumber, line);
                        deletedRows += deleteLineInSelection(lineMarker, registerBuilder);
                    } else {
                        Jed.model.pushDeleteUndo(lineNumber, line);
                        LineMarker nextlineMarker = Jed.model.nextLineMarker(lineMarker);
                        deletedRows += deleteLineInSelection(lineMarker, registerBuilder);
                        if (nextlineMarker != null) {
                            int scrollRow = view.getLineFirstRow(view.getLineIndexForLineMarker(nextlineMarker));
                            Jed.s.scrollLines(scrollRow, bottom - blankRows, deletedRows); // Don't scroll the blank line characters
                        }
                    }
                    for (int r = bottom; r > (bottom - deletedRows - blankRows); r--) {
                        drawRow(r, view.getRowText(r), true);
                    }
                    lineMarker = null;
                }
                return lineMarker; // Return value used to determine if line was delete
            }

            // Processing for the last line of multiple selected lines.
            if (selectMarkPosition != null) {
                if (this.deletedRows > 0) {
                    int scrollRow = view.getLineFirstRow(view.getLineIndexForLineMarker(lineMarker));
                    if (bottom - blankRows - scrollRow > deletedRows) {
                        Jed.s.scrollLines(scrollRow, bottom - blankRows, deletedRows); // Don't scroll the blank line characters
                        for (int r = bottom; r > (bottom - deletedRows - blankRows); r--) {
                            drawRow(r, view.getRowText(r), true);
                        }
                    } else {
                        refreshRow(view.getLineIndexForLineMarker(lineMarker),
                                view.getLineFirstRow(view.getLineIndexForLineMarker(lineMarker)), 0);
                    }
                }
                this.blankRows = height - view.getVirtualHeight();

                if (endPos < line.length()) {
                    registerBuilder.append(line.subSequence(0, endPos + 1).toString());
                    Jed.model.addEditUndo(undoEntries, this.lineNumber, 0, line);
                    setLine(view.getLineIndexForLineMarker(lineMarker), -1, -1,
                            line.delete(0, endPos + 1));
                } else {
                    if (lineMarker.equals(Jed.model.getLastLineMarker())) {
                        Jed.model.addDeleteUndo(undoEntries, lineNumber, line);
                        deletedRows = deleteLineInSelection(lineMarker, registerBuilder);
                    } else {
                        Jed.model.addDeleteUndo(undoEntries, this.lineNumber, line);
                        LineMarker endLineMarker = Jed.model.nextLineMarker(lineMarker);
                        deletedRows = deleteLineInSelection(lineMarker, registerBuilder);

                        if (endLineMarker != null) {
                            int scrollRow = view.getLineFirstRow(view.getLineIndexForLineMarker(endLineMarker));
                            Jed.s.scrollLines(scrollRow, bottom - blankRows, deletedRows); // Don't scroll the blank line characters
                        }
                    }
                    for (int r=bottom; r > (bottom-deletedRows-blankRows); r--) {
                        drawRow(r, view.getRowText(r),true);
                    }
                }
                Jed.model.pushMultiLineUndo(undoEntries);
                return lineMarker;
            }

            // Processing the first line of a multiple selected lines
            if (this.lineNumber == -1) { // selectMarkPosition == null
                this.blankRows = height - view.getVirtualHeight();
                if (startPos > 0) {
                    registerBuilder.append(line.subSequence(startPos, line.length()).toString()).append('\n');
                    Jed.model.addEditUndo(undoEntries, lineNumber, startPos, line);
                    if (view.isLineVisible(lineMarker)) {
                        setLine(view.getLineIndexForLineMarker(lineMarker),
                                -1,
                                -1,
                                line.delete(startPos, line.length()));
                    }
                    lineMarker = Jed.model.nextLineMarker(lineMarker);
                    this.lineNumber = lineNumber + 1; // Increment the line number on edit for subsequent undo entries
                } else {
                    Jed.model.addDeleteUndo(undoEntries, lineNumber, line);
                    LineMarker nextlineMarker = Jed.model.nextLineMarker(lineMarker);
                    deletedRows += deleteLineInSelection(lineMarker, registerBuilder);
                    lineMarker = nextlineMarker;
                    this.lineNumber = lineNumber;
                }
                return lineMarker;
            }


            Jed.model.addDeleteUndo(undoEntries, this.lineNumber, line);
            LineMarker nextLineMarker = Jed.model.nextLineMarker(lineMarker);
            deletedRows += deleteLineInSelection(lineMarker, registerBuilder);
            return nextLineMarker;
        }

        String getRegister() {
            return registerBuilder.toString();
        }
    }

    class YankSelectionProcessor implements SelectionProcessor {
        StringBuilder registerBuilder = new StringBuilder(256);
        int lineNumber = -1;

        @Override
        public LineMarker processLine(LineMarker lineMarker, int lineNumber, int startPos, int endPos, TerminalPosition selectMarkPosition) {
            Rope line = Jed.model.getLine(lineMarker);

            // Processing for the first and only selected row
            if (this.lineNumber == -1 && selectMarkPosition != null) {
                if (startPos > 0 || endPos < line.length()) {
                    if (endPos < line.length()) {
                        registerBuilder.append(line.subSequence(startPos, endPos + 1));
                    } else {
                        registerBuilder.append(line.subSequence(startPos, endPos)).append('\n');
                    }
                } else {
                    if (lineMarker.equals(Jed.model.getLastLineMarker())) {
                        registerBuilder.append(line.subSequence(startPos, endPos)).append('\n');
                    } else {
                        registerBuilder.append(line).append('\n');
                    }
                    lineMarker = null;
                }
                return lineMarker; // Return value used to determine if line was delete
            }

            // Processing for the last line of multiple selected lines.
            if (selectMarkPosition != null) {
                if (endPos < line.length()) {
                    registerBuilder.append(line.subSequence(0, endPos + 1).toString());
                } else {
                    if (lineMarker.equals(Jed.model.getLastLineMarker())) {
                        registerBuilder.append(line.subSequence(0, endPos).toString()).append('\n');
                    } else {
                        registerBuilder.append(line).append('\n');
                    }
                }
                return lineMarker;
            }

            // Processing the first line of a multiple selected lines
            if (this.lineNumber == -1) { // selectMarkPosition == null
                if (startPos > 0) {
                    registerBuilder.append(line.subSequence(startPos, line.length())).append('\n');
                } else {
                    registerBuilder.append(line).append('\n');
                }
                lineMarker = Jed.model.nextLineMarker(lineMarker);
                this.lineNumber = lineNumber + 1; // Increment the line number on edit for subsequent undo entries
                return lineMarker;
            }

            registerBuilder.append(line).append('\n');
            this.lineNumber = lineNumber + 1; // Increment the line number on edit for subsequent undo entries
            return Jed.model.nextLineMarker(lineMarker);
        }

        String getRegister() {
            return registerBuilder.toString();
        }
    }

    String deleteCurrentSelection() {
        DeleteSelectionProcessor processor = new DeleteSelectionProcessor();
        processCurrentSelection(processor);
        return processor.getRegister();
    }

    String yankCurrentSelection() {
        YankSelectionProcessor processor = new YankSelectionProcessor();
        processCurrentSelection(processor);
        return processor.getRegister();
    }

    void processCurrentSelection(SelectionProcessor processor) {
        clearSelection();
        int startLineNumber = selectMark.lineNumber;
        int startPosition = (visualLineMode ? 0 :selectMark.position);
        int endLineNumber = view.getLineIndexAbsoluteLine(view.getLineIndex(cursor.getRow()));
        int endPosition = (visualLineMode ?
                view.getLineForRow(cursor.getRow()).length() :
                view.getPositionLineOffset(cursor.getRow(), cursor.getColumn(), true));
        if (endLineNumber < startLineNumber ||
                (endLineNumber == startLineNumber && endPosition < startPosition)) {
            // Cursor is before select mark, so reverse
            startLineNumber = endLineNumber;
            startPosition = (visualLineMode ? startPosition : endPosition);
            endLineNumber = selectMark.lineNumber;
            endPosition = (visualLineMode ? endPosition : selectMark.position);
        }
        int startLineIndex = view.getRelativeLineIndex(startLineNumber);
        LineMarker startLineMarker = Jed.model.getLineMarker(startLineNumber);
        LineMarker endLineMarker = Jed.model.getLineMarker(endLineNumber);
        LineMarker lineMarker = startLineMarker;
        TerminalPosition startCursorPosition = new TerminalPosition(0, 0);
        if (startLineIndex > -1) {
            startCursorPosition = view.getCursorBasedOnPositionInLine(startLineIndex, startPosition);
        }
        do {
            if (lineMarker.equals(startLineMarker)) {
                if (lineMarker.equals(endLineMarker)) {
                    lineMarker = processor.processLine(lineMarker, startLineNumber, startPosition, endPosition, startCursorPosition);
                    if (lineMarker == null) {
                        startLineMarker = null;
                        lineMarker = endLineMarker;
                    }
                } else {
                    lineMarker = processor.processLine(lineMarker, startLineNumber, startPosition, -1, null);
                }
            } else {
                lineMarker = processor.processLine(lineMarker, -1, -1, -1, null);
            }
        } while (!lineMarker.equals(endLineMarker));

        if (startLineMarker != null && !startLineMarker.equals(endLineMarker)) {
            processor.processLine(endLineMarker, -1, -1, endPosition, startCursorPosition);
        }

        cursor = startCursorPosition;
        if (cursor.getRow() > view.getVirtualHeight()-1) {
            cursor = cursor.withRow(view.getLineFirstRow(view.getVisibleLines()-1));
        }
        virtualColumn = view.getPositionLineOffset(cursor.getRow(), cursor.getColumn());
    }

    String deleteCurrentLine() {
        saveUndoDeleteLine();
        String rtrn = deleteLineForRow(cursor.getRow());
        Jed.s.setCursorPosition(cursor);
        return rtrn;
    }

    String deleteLineForRow(int row) {
        StringBuilder deleteText = new StringBuilder(256);
        int blankRows = height - view.getVirtualHeight();
        int deleteRows = deleteLine(view.getLineMarkerForRow(row), deleteText);
        Jed.s.scrollLines(row, bottom-blankRows, deleteRows); // Don't scroll the blank line characters
        for (int r=bottom; r > (bottom-deleteRows-blankRows); r--) {
            drawRow(r, view.getRowText(r),true);
        }
        if (row > view.getVirtualHeight()-1) {
            cursor = cursor.withRow(view.getVirtualHeight()-1);
        }
        cursor = cursor.withRow(row).withColumn(left);

        return deleteText.toString();
    }

    int deleteLineInSelection(LineMarker lineMarker, StringBuilder deleteText) {
        int deleteRows = deleteLine(lineMarker, deleteText);
        deleteText.append('\n');
        return deleteRows;
    }

    int deleteLine(LineMarker lineMarker, StringBuilder deleteText) {
        if (view.isLineVisible(lineMarker)) {
            Rope lineText = view.deleteLine(view.getLineIndexForLineMarker(lineMarker));
            int deleteRows = view.getRowsPerLine(lineText.length());
            deleteText.append(lineText);
            return deleteRows;
        } else {
            Rope lineText = Jed.model.deleteLine(lineMarker);
            deleteText.append(lineText);
            return 0;
        }
    }

    boolean joinCurrentLine() throws IOException{
        if (view.getLineMarkerForRow(cursor.getRow()).equals(Jed.model.getLastLineMarker())) {
            return false;
        }
        saveUndoJoinLine();
        Rope joinText = view.deleteLine(getCurrentLineIndex()+1);
        setCurrentLine(view.getLineForRow(cursor.getRow()).append(" "+joinText.toString().trim()));
        int deleteRows = view.getRowsPerLine(joinText.length());
        Jed.s.scrollLines(cursor.getRow()+1, bottom, deleteRows);
        for (int row=bottom; row > (bottom-deleteRows); row--) {
            drawRow(row, view.getRowText(row),false);
        }
        return true;
    }

    String yankCurrentLine() {
        Rope yankText = view.getLine(view.getLineIndex(cursor.getRow()));
        return yankText.toString();
    }

    void moveCursorToStartOfLine() {
        clearSelection();
        int index = getCurrentLineIndex();
        int row = view.getLineFirstRow(index);
        cursor = cursor.withColumn(0).withRow(row);
        virtualColumn = 0;
        drawSelection();
    }

    /**
     * Move the cursor one column right of the last character in the line. This is where it needs to be when appending.
     */
    void moveCursorToEndOfLine() {
        clearSelection();
        int index = getCurrentLineIndex();
        int row = view.getLineLastRow(index);
        int column = view.getRowText(row).length()-(insertMode || visualMode ? 0 : 1);
        cursor = cursor.withRow(row).withColumn(column);
        virtualColumn = view.getLine(index).length()-(insertMode || visualMode ? 0 : 1);
        drawSelection();
    }

    void moveCursorToPositionInLine(int position) {
        int index = getCurrentLineIndex();
        int row = view.getLineFirstRow(index);
        row += position / width;
        int column = position % width;
        cursor = cursor.withRow(row).withColumn(column);
        virtualColumn = position;
        adjustCursorToLineEnd();
        Jed.s.setCursorPosition(cursor);
    }

    void moveCursorToBeginningOfWordOrPunctuation() {
        clearSelection();
        Rope line = view.getLine(getCurrentLineIndex());
        int position = view.getPositionLineOffset(cursor.getRow(), cursor.getColumn());

        boolean findAnyNonSpace = false;
        if (Character.isSpaceChar(line.charAt(position))) {
            position = Window.skipSpace(line, position, false);
            findAnyNonSpace = true;
        }

        boolean findKeyword = !Jed.isKeyword(line.charAt(position));
        for (int i=position; i<line.length(); i++) {
            if (Character.isSpaceChar(line.charAt(i))) {
                i = Window.skipSpace(line, i, false);
                findAnyNonSpace = true;
            }
            if ((findAnyNonSpace || findKeyword) && Jed.isKeyword(line.charAt(i))) {
                cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), i);
                drawSelection();
                return;
            } else if ((findAnyNonSpace || !findKeyword) && !Jed.isKeyword(line.charAt(i))) {
                cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), i);
                drawSelection();
                return;
            }
        }

        TerminalPosition oldCursor = cursor;
        cursor = cursor.withColumn(left).withRelativeRow(1);
        if (adjustViewForCursor()) moveCursorToBeginningOfWordOrPunctuation(); else cursor = oldCursor;
        drawSelection();
    }

    void moveCursorToBeginningOfWord() {
        clearSelection();
        Rope line = view.getLine(getCurrentLineIndex());
        int position = view.getPositionLineOffset(cursor.getRow(), cursor.getColumn());

        for (int i=position; i<line.length(); i++) {
            if (Character.isSpaceChar(line.charAt(i))) {
                i = Window.skipSpace(line, i, false);
                if (i < line.length()) {
                    cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), i);
                    drawSelection();
                    return;
                }
            }
        }
        TerminalPosition oldCursor = cursor;
        cursor = cursor.withColumn(left).withRelativeRow(1);
        if (adjustViewForCursor()) moveCursorToBeginningOfWord(); else cursor = oldCursor;
        drawSelection();
    }

    void moveCursorToEndOfWordOrPunctuation() {
        clearSelection();
        Rope line = view.getLine(getCurrentLineIndex());
        int position = view.getPositionLineOffset(cursor.getRow(), cursor.getColumn())+1;
        if (position == line.length()) {
            TerminalPosition oldCursor = cursor;
            cursor = cursor.withColumn(left).withRelativeRow(1);
            if (adjustViewForCursor()) moveCursorToEndOfWordOrPunctuation(); else cursor = oldCursor;
            drawSelection();
            return;
        }
        position = Window.skipSpace(line, position, false);
        boolean findKeyword = !Jed.isKeyword(line.charAt(position));
        for (int i=position; i<line.length(); i++) {
            if (findKeyword && (Jed.isKeyword(line.charAt(i)) || Character.isSpaceChar(line.charAt(i)))) {
                cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), i-1);
                drawSelection();
                return;
            } else if (!findKeyword && !Jed.isKeyword(line.charAt(i))) {
                cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), i-1);
                drawSelection();
                return;
            }
        }
        cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), line.length()-1);
        drawSelection();
    }

    void moveCursorToEndOfWord() {
        clearSelection();
        Rope line = view.getLine(getCurrentLineIndex());
        int position = view.getPositionLineOffset(cursor.getRow(), cursor.getColumn())+1;
        if (position == line.length()) {
            TerminalPosition oldCursor = cursor;
            cursor = cursor.withColumn(left).withRelativeRow(1);
            if (adjustViewForCursor()) moveCursorToEndOfWord(); else cursor = oldCursor;
            drawSelection();
            return;
        }
        position = Window.skipSpace(line, position, false);
        for (int i=position; i<line.length(); i++) {
            if (Character.isSpaceChar(line.charAt(i))) {
                cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), i-1);
                drawSelection();
                return;
            }
        }
        cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), line.length()-1);
        drawSelection();
    }

    void moveCursorBackwardToBeginningOfWordOrPunctuation() {
        clearSelection();
        Rope line = view.getLine(getCurrentLineIndex());
        int position = view.getPositionLineOffset(cursor.getRow(), cursor.getColumn())-1;
        position = Window.skipSpace(line, position, true);

        if (position == -1) {
            TerminalPosition oldCursor = cursor;
            cursor = cursor.withColumn(right).withRelativeRow(-1);
            if (adjustViewForCursor()) moveCursorBackwardToBeginningOfWordOrPunctuation(); else cursor = oldCursor;
            drawSelection();
            return;
        }
        boolean findKeyword = !Jed.isKeyword(line.charAt(position));
        for (int i=position; i>-1; i--) {
            if (findKeyword && (Jed.isKeyword(line.charAt(i)) || Character.isSpaceChar(line.charAt(i)))) {
                cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), i+1);
                drawSelection();
                return;
            } else if (!findKeyword && !Jed.isKeyword(line.charAt(i))) {
                cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), i+1);
                drawSelection();
                return;
            }
        }
        cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), 0);
        drawSelection();
    }

    void moveCursorBackwardToBeginningOfWord() {
        clearSelection();
        Rope line = view.getLine(getCurrentLineIndex());
        int position = view.getPositionLineOffset(cursor.getRow(), cursor.getColumn())-1;
        position = Window.skipSpace(line, position, true);

        if (position == -1) {
            TerminalPosition oldCursor = cursor;
            cursor = cursor.withColumn(right).withRelativeRow(-1);
            if (adjustViewForCursor()) moveCursorBackwardToBeginningOfWord(); else cursor = oldCursor;
            drawSelection();
            return;
        }

        for (int i=position; i>-1; i--) {
            if (Character.isSpaceChar(line.charAt(i))) {
                cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), i+1);
                drawSelection();
                return;
            }
        }
        cursor = view.getCursorBasedOnPositionInLine(getCurrentLineIndex(), 0);
        drawSelection();
    }

    boolean adjustViewForCursor() {
        int scrolledRows = 0;
        if (cursor.getRow() == -1) {
            scrolledRows = scrollDown(1);
            cursor = cursor.withRelativeRow(scrolledRows);
            return (scrolledRows != 0);
        } else if (cursor.getRow() == view.getVirtualHeight()) {
            scrolledRows = scrollUp();
            cursor = cursor.withRelativeRow(-scrolledRows);
            return (scrolledRows != 0);
        }
        return true;
    }

    void refreshRow(int lineIndex, int startRow, int startColumn) {
        int currentRow = startRow;
        while (currentRow < height && lineIndex == view.getLineIndex(currentRow)) {
            drawRow(currentRow, view.getRowText(currentRow), true, startColumn);
            currentRow++;
            startColumn = 0;
        }
        /*
        if ((currentRow-1) == startRow) {
            adjustCursorToLineEnd();
            Jed.s.setCursorPosition(cursor);
        } */
    }

    void clearSelection() {
        if (visualMode) {
            drawSelection(TextColor.ANSI.BLACK);
        } else if (visualLineMode) {
            drawLineSelection(TextColor.ANSI.BLACK);
        }
    }

    void drawSelection() {
        if (visualMode) {
            drawSelection(GRAY);
        } else if (visualLineMode) {
            drawLineSelection(GRAY);
        }
    }

    private void drawSelection(TextColor background) {
        int startRow, startCol, endRow, endCol;
        if (selectMark.lineNumber < view.getRowAbsoluteLine(0)) {
            startRow = 0;
            startCol = 0;
            endRow = cursor.getRow();
            endCol = cursor.getColumn();
        } else if (selectMark.lineNumber < view.getRowAbsoluteLine(0) + view.getVisibleLines()) {
            TerminalPosition markCursor = view.getCursorBasedOnPositionInLine(
                    view.getRelativeLineIndex(selectMark.lineNumber), selectMark.position);
            if (cursor.getRow() < markCursor.getRow() ||
                    (cursor.getRow() == markCursor.getRow() && cursor.getColumn() < markCursor.getColumn()) ) {
                startRow = cursor.getRow();
                startCol = cursor.getColumn();
                endRow = markCursor.getRow();
                endCol = markCursor.getColumn();
            } else {
                startRow = markCursor.getRow();
                startCol = markCursor.getColumn();
                endRow = cursor.getRow();
                endCol = cursor.getColumn();
            }
        } else {
            startRow = cursor.getRow();
            startCol = cursor.getColumn();
            endRow = view.getVirtualHeight()-1;
            endCol = view.getRowText(view.getVirtualHeight()-1).length()-1;
        }
        int drawRow = startRow;
        while (drawRow <= Math.min(endRow, view.getVirtualHeight()-1)) {
            drawSelectedRow(drawRow, view.getRowText(drawRow), background, startRow, startCol, endRow, endCol);
            drawRow++;
        }
    }

    void drawLineSelection(TextColor background) {
        int startLine, endLine;
        if (selectMark.lineNumber < view.getRowAbsoluteLine(0)) {
            startLine = 0;
            endLine = view.getLineIndex(cursor.getRow());
        } else if (selectMark.lineNumber < view.getRowAbsoluteLine(0) + view.getVisibleLines()) {
            int markLine = view.getRelativeLineIndex(selectMark.lineNumber);
            int cursorLine = view.getLineIndex(cursor.getRow());
            if (cursorLine < markLine) {
                startLine = cursorLine;
                endLine = markLine;
            } else {
                startLine = markLine;
                endLine = cursorLine;
            }
        } else {
            startLine = view.getLineIndex(cursor.getRow());
            endLine = view.getLineIndex(view.getVirtualHeight()-1);
        }
        int drawLine = startLine;
        while (drawLine <= Math.min(endLine, view.getLineIndex(view.getVirtualHeight()-1))) {
            for (int drawRow = view.getLineFirstRow(drawLine); drawRow <= view.getLineLastRow(drawLine); drawRow++) {
                drawSelectedRow(drawRow, view.getRowText(drawRow), background, drawRow, 0, drawRow, right);
            }
            drawLine++;
        }
    }

    void drawScreen() {
        drawScreen(0);
    }

    void drawScreen(int firstRow) {
        for (int row=firstRow; row<=bottom; row++) {
            drawRow(row, view.getRowText(row), true);
        }
        Jed.s.setCursorPosition(cursor);
    }

    private void drawRow(int row, Rope lineData, boolean clearExcess) {
        drawRow(row, lineData, clearExcess, 0);
    }

    private void drawRow(int row, Rope lineData, boolean clearExcess, int startColumn) {
        int col = 0;
        for (col=startColumn; col<lineData.length(); col++) {
            Jed.s.setCharacter(col, row, new TextCharacter(lineData.charAt(col), TextColor.ANSI.WHITE, TextColor.ANSI.BLACK));
        }
        if (clearExcess) {
            for ( ; col<=right; col++) {
                Jed.s.setCharacter(col, row, Jed.BLANK);
            }
        }
    }

    private void drawSelectedRow(int row, Rope lineData, TextColor background, int startRow, int startCol, int endRow, int endCol) {
        int activeStartCol = 0, activeEndCol;
        if (row == startRow) {
            activeStartCol = startCol;
        }
        if (row == endRow) {
            activeEndCol = Math.min(endCol, lineData.length());
        } else {
            activeEndCol = lineData.length();
        }
        for (int col=activeStartCol; col<activeEndCol; col++) {
            Jed.s.setCharacter(col, row, new TextCharacter(lineData.charAt(col), TextColor.ANSI.WHITE, background));
        }
    }

    private void adjustCursorToLineEnd() {
        adjustCursorToLineEnd(false);
    }

    private void adjustCursorToLineEnd(boolean safeOverride) {
        Rope line = view.getLineForRow(cursor.getRow());
        int positionInLine = Math.min(virtualColumn,
                                      line.length()-((safeOverride | insertMode | visualMode) ? 0 : 1));
        cursor = view.getCursorBasedOnPositionInLine(view.getLineIndex(cursor.getRow()), positionInLine);
    }

    class TextInserter {
        private StringBuilder insertText;
        private int offset;

        TextInserter() {
            insertText = new StringBuilder(1024);
        }

        private Rope createNewLine() {
            Rope newInsertLine = insertLine;
            if (offset > 0) {
                newInsertLine = newInsertLine.delete(insertPoint - offset, insertPoint);
            }
            if (insertPoint - offset < newInsertLine.length() - 1) {
                newInsertLine = newInsertLine.insert(insertPoint - offset, insertBuffer.getText());
            } else {
                newInsertLine = newInsertLine.append(insertBuffer.getText());
            }
            return newInsertLine;
        }

        void append(Character c) throws IOException {
            insertText.append(c);
            setLine(view.getRelativeLineIndex(insertLineNumber), cursor.getRow(), cursor.getColumn(), createNewLine());
            cursorRight();
        }

        void delete() throws IOException {
            if (insertText.length() > 0) {
                insertText.deleteCharAt(insertText.length() - 1);
            } else {
                offset++;
            }
            cursorLeft(1);
            Jed.s.setCharacter(cursor, TextCharacter.DEFAULT_CHARACTER);
            setCurrentLine(createNewLine());
        }

        void clear() throws IOException {
            int textSize = insertText.length();
            insertText.delete(0, insertText.length());
            setCurrentLine(createNewLine());
            cursorLeft(textSize);
        }

        boolean hasChanged() {
            return (insertText.length() > 0 || offset > 0);
        }

        void reset() {
            insertText.delete(0, insertText.length());
            offset = 0;
        }

        int length() {
            return insertText.length();
        }

        String getText() {
            return insertText.toString();
        }
    }

}
