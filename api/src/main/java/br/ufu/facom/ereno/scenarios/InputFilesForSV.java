package br.ufu.facom.ereno.scenarios;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Resolves the list of electrical-source ".out" files used by the SV creator.
 *
 * Files are discovered at runtime from the in-repository folder
 * {@code electrical-sources/<resistance>/} (default {@code res10}). Each file
 * is expected to be a 13-column merged simulation output (see
 * {@code tools/merge_electrical_sources.ps1}).
 *
 * The folder can be overridden via:
 *   - JVM property: {@code -Dereno.electricalSources.dir=<path>}
 *   - Environment variable: {@code ERENO_ELECTRICAL_SOURCES_DIR}
 *   - JVM property: {@code -Dereno.electricalSources.resistance=res50}
 *     (selects {@code electrical-sources/res50})
 */
public class InputFilesForSV {

    private static final String DEFAULT_REPO_SUBDIR = "electrical-sources";
    private static final String DEFAULT_RESISTANCE = "res10";
    private static final String FILE_PATTERN = "SILVIO_";

    public static String[] singleElectricalSourceFile = pickSingle();
    public static String[] electricalSourceFiles = discover();

    // Kept for backward compatibility with legacy Linux-only call sites.
    public static String[] singleElectricalSourceFileLinux = singleElectricalSourceFile;
    public static String[] electricalSourceFilesLinux = electricalSourceFiles;

    private static String[] discover() {
        File dir = resolveDirectory();
        File[] files = dir.listFiles((d, name) ->
                name.startsWith(FILE_PATTERN) && name.endsWith(".out"));
        if (files == null || files.length == 0) {
            throw new IllegalStateException(
                    "No electrical-source files found in: " + dir.getAbsolutePath()
                            + ". Expected files matching '" + FILE_PATTERN + "*.out'.");
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        String[] paths = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            paths[i] = files[i].getAbsolutePath();
        }
        return paths;
    }

    private static String[] pickSingle() {
        try {
            String[] all = discover();
            return new String[]{all[0]};
        } catch (RuntimeException ex) {
            return new String[0];
        }
    }

    private static File resolveDirectory() {
        String explicit = System.getProperty("ereno.electricalSources.dir",
                System.getenv("ERENO_ELECTRICAL_SOURCES_DIR"));
        if (explicit != null && !explicit.isEmpty()) {
            return new File(explicit);
        }
        String resistance = System.getProperty("ereno.electricalSources.resistance",
                DEFAULT_RESISTANCE);
        return new File(DEFAULT_REPO_SUBDIR + File.separator + resistance);
    }
}
