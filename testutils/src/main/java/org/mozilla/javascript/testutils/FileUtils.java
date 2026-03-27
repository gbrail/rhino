package org.mozilla.javascript.testutils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FileUtils {
    public static File findTestFile(String name, String... dirs) throws IOException {
        System.err.println("Walking files...");
        try (Stream<Path> stream = Files.walk(Path.of("."))) {
            stream
                    .filter(Files::isRegularFile) // Only include files, skip directories
                    .forEach(System.err::println);
        } catch (IOException e) {
            System.err.println("Error walking the path: " + e.getMessage());
        }
        System.err.println("Walked");
        for (String dir : dirs) {
            File file = new File(dir, name);
            if (file.exists()) {
                return file;
            }
        }
        throw new IOException("File not found: " + new File(dirs[dirs.length - 1], name));
    }
}
