package br.com.tonus.billing;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The listing resolves student, name and payment in batch — one query per
 * thing, not per row. Without this test the guarantee does not exist: going
 * back to N+1 breaks no behavior assertion, and the cost would only show up
 * in production.
 *
 * Separate class because `generate_statistics` changes the context
 * properties, and enabling it in `application-test.yml` would add overhead
 * and a per-session metrics summary to the log of the whole suite.
 *
 * The assertion is a ceiling, not an exact count: the right number depends
 * on Hibernate details (flush, pagination count) that may change without
 * the design changing. The ceiling separates "batch" from "per row", which
 * is what matters — with 6 distinct students, the old N+1 went past 19 queries.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"})
class InvoiceListingQueryCountIT extends AbstractBillingIntegrationTest {

    private static final int INVOICES = 6;

    /** Headroom over the batch design (invoice + subscription + student + payment + count). */
    private static final int CEILING = 10;

    @Autowired
    private SessionFactory sessionFactory;

    @Test
    void listingDoesNotQueryPerInvoice() throws Exception {
        String marker = unique("Lote");
        for (int i = 0; i < INVOICES; i++) {
            // Distinct students on purpose: Hibernate's first-level cache
            // would hide the N+1 if all invoices were from the same one.
            long studentId = createStudent(marker + "-aluno-" + i, createTeacher(marker + "-prof-" + i));
            String month = "2026-%02d".formatted(i + 1);
            long invoiceId = seedInvoice(studentId, month + "-01", "200.00", month + "-10",
                    i % 2 == 0 ? "OVERDUE" : "PAID");
            if (i % 2 == 1) {
                seedPayment(invoiceId, month + "-08", 1L);
            }
        }

        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        mockMvc.perform(get("/invoices").param("size", String.valueOf(INVOICES)).cookie(admin))
                .andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount())
                .as("consultas para montar uma página de %d faturas", INVOICES)
                .isLessThanOrEqualTo(CEILING);
    }
}
