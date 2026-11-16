package com.otectus.runicclasses;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The committed class data is exactly what {@code tools/generate_classes.py} produces: the generator is
 * re-run in check mode, so the JSON, the lang file and {@code ClassKit.java} can never drift from the
 * one source. Skipped (not failed) on a machine without {@code python3}.
 */
class ClassDataParityTest {

    @Test
    void generatedFilesMatchTheGenerator() throws IOException, InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder("python3", "tools/generate_classes.py", "--check").redirectErrorStream(true).start();
        } catch (IOException noPython) {
            Assumptions.abort("python3 is not available: " + noPython.getMessage());
            return;
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("the generator did not finish within 60 seconds");
        }
        assertEquals(0, process.exitValue(), "generated class data is stale; run python3 tools/generate_classes.py\n" + output);
    }
}
