import java.io.File;
import java.io.FileOutputStream;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.xml.XMLFormatter;

/** Emits deterministic JaCoCo XML from one retained class or an empty class scope. */
public final class FixtureReportGenerator {
    private FixtureReportGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: <class-file-or-dash> <report-name> <output-xml>");
        }
        String classFile = args[0];
        String reportName = args[1];
        File output = new File(args[2]);
        ExecutionDataStore executionData = new ExecutionDataStore();
        SessionInfoStore sessionInfo = new SessionInfoStore();
        CoverageBuilder coverage = new CoverageBuilder();
        if (!"-".equals(classFile)) {
            new Analyzer(executionData, coverage).analyzeAll(new File(classFile));
        }

        XMLFormatter formatter = new XMLFormatter();
        IReportVisitor visitor = formatter.createVisitor(new FileOutputStream(output));
        visitor.visitInfo(sessionInfo.getInfos(), executionData.getContents());
        visitor.visitBundle(coverage.getBundle(reportName), null);
        visitor.visitEnd();

        var bundle = coverage.getBundle(reportName);
        System.out.printf("instructions=%d/%d branches=%d/%d classes=%d%n",
                bundle.getInstructionCounter().getCoveredCount(),
                bundle.getInstructionCounter().getTotalCount(),
                bundle.getBranchCounter().getCoveredCount(),
                bundle.getBranchCounter().getTotalCount(),
                coverage.getClasses().size());
    }
}
