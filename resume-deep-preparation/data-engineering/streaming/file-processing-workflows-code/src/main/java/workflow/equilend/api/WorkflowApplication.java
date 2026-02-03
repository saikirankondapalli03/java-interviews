package workflow.equilend.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import workflow.equilend.reportingstore.FileBackedReportingStore;
import workflow.equilend.reportingstore.InMemoryReportingStore;
import workflow.equilend.reportingstore.ReportingStore;

@SpringBootApplication
public class WorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }

    @Bean
    public ReportingStore reportingStore(@Value("${reporting.store.path:}") String reportingStorePath) {
        if (reportingStorePath != null && !reportingStorePath.trim().isEmpty()) {
            return new FileBackedReportingStore(reportingStorePath.trim());
        }
        return new InMemoryReportingStore();
    }
}
