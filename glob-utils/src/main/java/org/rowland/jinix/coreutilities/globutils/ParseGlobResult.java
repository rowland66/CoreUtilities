package org.rowland.jinix.coreutilities.globutils;

import java.nio.file.Path;

/**
 * Return object for GlobUtils.parseGlob() method
 */
public class ParseGlobResult {
    public String fileName;
    public Path dir;
    public boolean isGlob;
}

