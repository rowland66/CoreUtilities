package org.rowland.jinix.coreutilities.globutils;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by rsmith on 2/15/2017.
 */
public class GlobUtils {
    public static ParseGlobResult parseGlob(Path fullPath) throws InvalidGlobException {
        ParseGlobResult rtrn = new ParseGlobResult();

        rtrn.dir = Paths.get("");
        if (fullPath.isAbsolute()) {
            rtrn.dir = Paths.get(FileSystems.getDefault().getSeparator());
        }

        rtrn.fileName = null;
        for (Path pathName : fullPath) {
            String name = pathName.toString();
            if (isGlob(name)) {
                if (rtrn.fileName != null) {
                    throw new InvalidGlobException("Invalid glob in argument");
                }
                rtrn.fileName = name;
                rtrn.isGlob = true;
            } else {
                if (rtrn.fileName != null) {
                    throw new InvalidGlobException("Invalid glob in argument");
                }
                if (!name.isEmpty()) {
                    rtrn.dir.resolve(name);
                }
            }
        }
        return rtrn;
    }

    public static boolean isGlob(String str) {
        if (str.contains("*") || str.contains("?")) {
            return true;
        }
        return false;
    }
}
