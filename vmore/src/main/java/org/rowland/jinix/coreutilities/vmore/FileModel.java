package org.rowland.jinix.coreutilities.vmore;

import org.rowland.jinix.io.JinixFileInputStream;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

public class FileModel {

    private List<char[]> data = new LinkedList<char[]>();

    FileModel(InputStream is) throws IOException {
        BufferedReader input = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = input.readLine()) != null ) {
            char[] lineData = new char[line.length()];
            line.getChars(0, line.length(), lineData, 0);
            data.add(lineData);
        }
    }

    char[] getLine(int lineNumber) {

        if (lineNumber < data.size()) {
            return data.get(lineNumber);
        }

        return null;
    }

}
