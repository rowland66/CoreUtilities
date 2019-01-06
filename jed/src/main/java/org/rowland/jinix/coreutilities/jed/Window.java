package org.rowland.jinix.coreutilities.jed;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TextCharacter;
import org.ahmadsoft.ropes.Rope;

import java.io.IOException;

class Window {

    boolean insertMode;

    // Insert Mode Data
    TextInserter insertBuffer;
    Rope insertLine; // when insertMode is true, this is the line before text was inserted.
    int insertPoint; // when insertMode is true, this is the point where insertion began

    int width, height;

    private EditBufferView view;
    private int top, bottom, left, right;
    private TerminalPosition cursor;
    private int virtualColumn; // The column that the cursor will jump back to when scrolling.

    Window(EditBuffer buffer, int left, int top, int width, int height) {
        this.view = new EditBufferView(buffer, width, height);
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.bottom = top+height-1;
        this.right = left+width-1;
        cursor = new TerminalPosition(left, top);
        this.virtualColumn = -1;
    }

    void enterInsertMode() {
        assert (insertMode == false);
        insertMode = true;
        insertBuffer = new TextInserter();
        insertLine = getCurrentLine();
        insertPoint = getCurrentLineOffset();
    }

    void exitInsertMode() throws IOException {
        assert(insertMode == true);
        insertMode = false;
        if (insertBuffer.getText().length() == 0) {
            setCurrentLine(insertLine);
            return;
        }
        setCurrentLine(insertLine.insert(insertPoint, insertBuffer.getText()));
    }


    void restoreCursor() {
        Jed.s.setCursorPosition(cursor);
    }

    private int scrollUp(int scrollLines) {
        int preScrollVirtualHeight = view.getVirtualHeight();
        int preScrollBlankRows = height - view.getVirtualHeight();
        int scrolledRows = view.scrollUp(scrollLines);
        Jed.s.scrollLines(top, top+preScrollVirtualHeight-1, scrolledRows-preScrollBlankRows);
        for (int row=(height-1); row > (height-scrolledRows-1); row--) {
            drawRow(row, view.getRowText(row),false);
        }
        return scrolledRows;
    }

    /**
     * Move the cursor down in the buffer a number of buffer lines. A buffer line may display over multiple rows
     *
     * @param delta
     * @throws IOException
     */
    void cursorDown(int delta) throws IOException {
        int movedRows = 0;
        int movedLines = 0;
        int currentLine = view.getLineIndex(cursor.getRow());
        while (movedLines < delta && currentLine < view.getVisibleLines()-1) {
            movedRows += view.getLineRowCount(currentLine);
            movedLines++;
            currentLine++;
        }

        if (movedRows > 0) {
            cursor = cursor.withRelativeRow(movedRows);
        }

        if (movedLines < delta && view.getLine(currentLine+1) != null) {
            int scrolledRows = scrollUp(delta-movedLines);
            cursor = cursor.withRow(bottom-scrolledRows+1);
        }

        adjustCursorToLineEnd();

        Jed.s.setCursorPosition(cursor);
    }

    void downLine(int scrollLines) {
        int scrolledRows = scrollUp(scrollLines);
        if (cursor.getRow() - scrolledRows < top) {
            cursor = cursor.withRow(top);
        } else {
            cursor = cursor.withRelativeRow(-scrolledRows);
        }
        Jed.s.setCursorPosition(cursor);
    }

    void downRows(int rows) throws IOException {
        int scrolledRows = view.scrollUpRows(rows);
        if (scrolledRows > 0) {
            drawScreen();
            cursor = cursor.withRow(0).withColumn(0);
            Jed.s.setCursorPosition(cursor);
        }
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

    void cursorUp(int delta) throws IOException {
        int movedRows = 0;
        int movedLines = 0;
        int currentLine = view.getLineIndex(cursor.getRow());
        while (movedLines < delta && currentLine > 0) {
            movedRows += view.getLineRowCount(currentLine-1);
            movedLines++;
            currentLine--;
        }

        if (movedRows > 0) {
            cursor = cursor.withRelativeRow(-movedRows);
        }

        if (movedLines < delta) {
            scrollDown(delta-movedLines);
        }

        adjustCursorToLineEnd();

        Jed.s.setCursorPosition(cursor);
    }

    void upLine(int scrollLines) {
        int scrolledRows = scrollDown(scrollLines);
        if (cursor.getRow() + scrolledRows > bottom) {
            cursor = cursor.withRow(bottom);
        } else {
            cursor = cursor.withRelativeRow(scrolledRows);
        }
        Jed.s.setCursorPosition(cursor);
    }

    void upRows(int rows) throws IOException {
        int scrolledRows = view.scrollDownRows(rows);
        if (scrolledRows > 0) {
            drawScreen();
            cursor = cursor.withRow(view.getVirtualHeight() - 1).withColumn(0);
            Jed.s.setCursorPosition(cursor);
        }
    }

    void cursorLeft(int delta) throws IOException {
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
        virtualColumn = -1;
        Jed.s.setCursorPosition(cursor);

    }

    void cursorRight(int delta) throws IOException {
        cursorRight(delta, true);
    }

    void cursorRight(int delta, boolean safe) throws IOException {
        if (cursor.getColumn() + delta > right) {
            int currentRowLineIndex = view.getLineIndex(cursor.getRow());
            if (view.getLineLastRow(currentRowLineIndex) > cursor.getRow()) {
                cursor = cursor.withColumn(left).withRelativeRow(1);
            }
        } else {
            cursor = cursor.withRelativeColumn(delta);
        }

        virtualColumn = -1;
        if (safe) adjustCursorToLineEnd();

        Jed.s.setCursorPosition(cursor);
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
        return view.getLineOffset(cursor.getRow(), cursor.getColumn());
    }

    void setCurrentLine(Rope value) throws IOException {
        int oldLineRows = view.setLineForRow(cursor.getRow(), value);
        if (oldLineRows == 0) {
            refreshCurrentRow();
            return;
        }
        drawScreen(cursor.getRow());
    }

    void insertLineAfterCurrentLine(Rope text) throws IOException {
        int currentLineIndex = getCurrentLineIndex();
        int insertedTextRows = view.getRowsPerLine(text.length());
        view.insertLineAfter(currentLineIndex, text);
        int insertedLineFirstRow = view.getLineFirstRow(currentLineIndex+1); //

        Jed.s.scrollLines(insertedLineFirstRow, bottom, -insertedTextRows);
        for (int row=bottom; view.getLineIndex(row) == -1; row--) {
            drawRow(row, view.getRowText(row),true);
        }

        for (int row=insertedLineFirstRow; row<insertedLineFirstRow+insertedTextRows; row++) {
            drawRow(row, view.getLineForRow(row), true);
        }
        cursor = cursor.withRow(insertedLineFirstRow).withColumn(left);
        Jed.s.setCursorPosition(cursor);
    }

    void insertLineBeforeCurrentLine(Rope text) throws IOException {
        int currentLineIndex = getCurrentLineIndex();
        int currentLineFirstRow = view.getLineFirstRow(currentLineIndex); //
        int insertedTextRows = view.getRowsPerLine(text.length());
        view.insertLineAfter(currentLineIndex-1, text);

        Jed.s.scrollLines(currentLineFirstRow, bottom, -insertedTextRows);
        for (int row=bottom; view.getLineIndex(row) == -1; row--) {
            drawRow(row, view.getRowText(row),true);
        }

        for (int row=currentLineFirstRow; row<currentLineFirstRow+insertedTextRows; row++) {
            drawRow(row, view.getLineForRow(row), true);
        }

        cursor = cursor.withRow(currentLineFirstRow).withColumn(left);
        Jed.s.setCursorPosition(cursor);
    }

    String deleteCurrentLine() {
        int blankRows = height - view.getVirtualHeight();
        Rope deleteText = view.deleteLine(getCurrentLineIndex());
        int deleteRows = view.getRowsPerLine(deleteText.length());
        Jed.s.scrollLines(cursor.getRow(), bottom-blankRows, deleteRows); // Don't scroll the blank line characters
        for (int row=bottom; row > (bottom-deleteRows-blankRows); row--) {
            drawRow(row, view.getRowText(row),false);
        }
        if (cursor.getRow() > view.getVirtualHeight()-1) {
            cursor = cursor.withRow(view.getVirtualHeight()-1);
        }
        cursor = cursor.withRow(cursor.getRow()).withColumn(left);
        Jed.s.setCursorPosition(cursor);
        return deleteText.toString();
    }

    void joinCurrentLine() throws IOException{
        Rope joinText = view.deleteLine(getCurrentLineIndex()+1);
        setCurrentLine(view.getLineForRow(cursor.getRow()).append(" "+joinText.toString().trim()));
        int deleteRows = view.getRowsPerLine(joinText.length());
        Jed.s.scrollLines(cursor.getRow()+1, bottom, deleteRows);
        for (int row=bottom; row > (bottom-deleteRows); row--) {
            drawRow(row, view.getRowText(row),false);
        }

    }

    String yankCurrentLine() {
        Rope yankText = view.getLine(view.getLineIndex(cursor.getRow()));
        return yankText.toString();
    }

    void moveCursorToStartOfLine() throws IOException {
        cursor = cursor.withColumn(0);
        virtualColumn = -1;
        Jed.s.setCursorPosition(cursor);
    }

    /**
     * Move the cursor one column right of the last character in the line. This is where it needs to be when appending.
     *
     * @throws IOException
     */
    void moveCursorToEndOfLine() throws IOException{
        int index = getCurrentLineIndex();
        int row = view.getLineLastRow(index);
        int column = view.getRowText(row).length()-1;
        cursor = cursor.withRow(row).withColumn(column);
        virtualColumn = -1;
        Jed.s.setCursorPosition(cursor);
    }

    void refreshCurrentRow() throws IOException{
        int currentRow = cursor.getRow();
        int startColumn = cursor.getColumn();
        int lineIndex = view.getLineIndex(currentRow);
        while (lineIndex == view.getLineIndex(currentRow)) {
            drawRow(currentRow, view.getRowText(currentRow), true, startColumn);
            currentRow++;
            startColumn = 0;
        }

        if ((currentRow-1) == cursor.getRow()) {
            adjustCursorToLineEnd();
            Jed.s.setCursorPosition(cursor);
        }
    }

    void drawScreen() throws IOException {
        drawScreen(0);
    }

    void drawScreen(int firstRow) throws IOException {
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
            Jed.s.setCharacter(col, row, new TextCharacter(lineData.charAt(col)));
        }
        if (clearExcess) {
            for ( ; col<=right; col++) {
                Jed.s.setCharacter(col, row, Jed.BLANK);
            }
        }
    }

    private void adjustCursorToLineEnd() {
        Rope line = view.getRowText(cursor.getRow());
        if (insertMode) {
            return; // in insert mode moving 1 column past the end of the line is allowed.
        }
        int currentLineFirstRow = view.getLineFirstRow(view.getLineIndex(cursor.getRow()));
        int positionInLine = cursor.getRow() - currentLineFirstRow + cursor.getColumn();
        if (positionInLine >= line.length()) {
            if (virtualColumn == -1) {
                virtualColumn = cursor.getColumn();
            }
            cursor = cursor.withColumn((line.length() == 0 ? 0 : line.length()-1));
        } else if (virtualColumn != -1){
            cursor = cursor.withColumn(Math.min(virtualColumn, line.length()-1));
        }
    }

    class TextInserter {
        private StringBuilder insertText;
        private int insertTextLength;

        TextInserter() {
            insertText = new StringBuilder(256);
            insertTextLength = 0;
            insertPoint = getCurrentLineOffset();
        }

        void append(Character c) throws IOException {
            if (insertTextLength < insertText.length()) {
                insertText.replace(insertTextLength, insertTextLength+1, Character.toString(c));
            } else {
                insertText.append(c);
            }

            insertTextLength++;

            if (insertPoint >= insertLine.length()) {
                setCurrentLine(insertLine.append(insertText));
            } else {
                setCurrentLine(insertLine.insert(insertPoint, insertText));
            }
        }

        void delete() {
            if (insertTextLength > 0)
                insertTextLength--;
        }

        int clear() {
            int rtrn = insertTextLength;
            insertTextLength = 0;
            return rtrn;
        }

        int length() {
            return insertTextLength;
        }

        String getText() {
            return insertText.toString().substring(0, insertTextLength);
        }
    }

}
