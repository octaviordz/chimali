import java.io.File;
import java.nio.file.Files;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.internal.data.CRC64;
import org.jacoco.core.tools.ExecFileLoader;

/** Writes and validates one deliberately stale class ID for an existing production class. */
public final class JaCoCoMismatchedExecutionDataWriter {
    private JaCoCoMismatchedExecutionDataWriter() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Usage: <mode> <output.exec> <internal-class-name> <class-file>");
        String mode = args[0];
        long classId = mode.equals("probe-length") ? CRC64.classId(Files.readAllBytes(new File(args[3]).toPath())) : 1L;
        ExecFileLoader writer = new ExecFileLoader();
        // JaCoCo omits execution records without a hit; a hit ensures this stale ID is serialized.
        writer.getExecutionDataStore().put(new ExecutionData(classId, args[2], new boolean[] { true }));
        writer.save(new File(args[1]), false);

        ExecFileLoader loader = new ExecFileLoader();
        loader.load(new File(args[1]));
        if (loader.getExecutionDataStore().getContents().size() != 1) {
            throw new IllegalStateException("The JaCoCo fixture did not retain its execution record");
        }
        CoverageBuilder coverage = new CoverageBuilder();
        boolean analyzerRejected = false;
        try {
            new Analyzer(loader.getExecutionDataStore(), coverage).analyzeAll(new File(args[3]));
            if (mode.equals("class-id") && coverage.getNoMatchClasses().isEmpty()) {
                throw new IllegalStateException("JaCoCo did not identify the deliberately mismatched class ID");
            }
            if (mode.equals("probe-length")) {
                throw new IllegalStateException("JaCoCo accepted the deliberately incompatible probe length");
            }
        } catch (Exception exception) {
            if (!mode.equals("probe-length")) throw exception;
            analyzerRejected = true;
        }
        if (mode.equals("probe-length") && !analyzerRejected) throw new IllegalStateException("JaCoCo accepted the deliberately incompatible probe length");
        System.out.println("JaCoCo API confirmed " + mode + " incompatibility for " + args[2]);
    }
}
