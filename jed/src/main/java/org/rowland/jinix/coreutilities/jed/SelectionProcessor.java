package org.rowland.jinix.coreutilities.jed;

import com.googlecode.lanterna.TerminalPosition;

public interface SelectionProcessor {
    /**
     * Process a line in a selected area.
     *
     * @param lineMarker a LineMarker for the line being processed
     * @param lineNumber the absolute line number of the line being processed
     * @param startPos the starting position of the selection in the line if the line is first line in the selection,
     *                 otherwise -1.
     * @param endPos the ending position of the selection in the line of the line is that last line in the selection,
     *               otherwise -1.
     * @param selectMarkPosition the cursor position of the beginning of the selection if the beginning of the selection
     *                           is visible on the screen, otherwise [0,0].
     * @return a LineMarker indicating the next line to process. Must be NULL when the selection is a single line that
     *         has been deleted.
     */
    LineMarker processLine(LineMarker lineMarker, int lineNumber, int startPos, int endPos, TerminalPosition selectMarkPosition);
}
