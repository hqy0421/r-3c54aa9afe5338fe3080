package technology.tabula.reconciliation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import technology.tabula.TextChunk;
import technology.tabula.Table;
import technology.tabula.extractors.BasicExtractionAlgorithm;

public class TestReconciliation {

    private static final ReconciliationConfig CONFIG = ReconciliationConfig.builder()
            .rowTypeColumn(0)
            .currencyColumn(1)
            .amountColumn(2)
            .detailMarker("DETAIL")
            .subtotalMarker("SUBTOTAL")
            .totalMarker("TOTAL")
            .build();

    // ---- helpers ----------------------------------------------------------

    private static Table table(int pageNumber, String[]... rows) {
        Table table = new Table(new BasicExtractionAlgorithm());
        table.setPageNumber(pageNumber);
        for (int r = 0; r < rows.length; r++) {
            String[] row = rows[r];
            for (int c = 0; c < row.length; c++) {
                table.add(cell(row[c]), r, c);
            }
        }
        return table;
    }

    private static TextChunk cell(String text) {
        if (text == null) {
            return new TextChunk(0, 0, 0, 0);
        }
        technology.tabula.TextElement element = new technology.tabula.TextElement(
                0, 0, 0, 0, null, 0, text, 0);
        return new TextChunk(element);
    }

    private static ReconciliationResult reconcile(Table table) {
        return new Reconciler(CONFIG).reconcile(table);
    }

    private static void assertNoErrors(ReconciliationResult result) {
        if (!result.isSuccess()) {
            fail("expected success but got: " + ReconciliationError.formatAll(result.getErrors()));
        }
    }

    // ---- amount parsing ---------------------------------------------------

    @Test
    public void parsesPlainDecimals() {
        assertEquals(new BigDecimal("1234.56"), AmountParser.parse("1234.56"));
        assertEquals(new BigDecimal("-1234.56"), AmountParser.parse("-1234.56"));
        assertEquals(new BigDecimal("12"), AmountParser.parse("+12"));
        assertEquals(new BigDecimal("0.05"), AmountParser.parse("0.05"));
        assertEquals(new BigDecimal("1.50"), AmountParser.parse("1.50"));
    }

    @Test
    public void parsesGroupedAndLocalizedAmounts() {
        assertEquals(new BigDecimal("1234.56"), AmountParser.parse("1,234.56"));
        assertEquals(new BigDecimal("1234.56"), AmountParser.parse("1.234,56"));
        assertEquals(new BigDecimal("1234.56"), AmountParser.parse("1 234,56"));
        assertEquals(new BigDecimal("1234.56"), AmountParser.parse("1'234.56"));
        assertEquals(new BigDecimal("1234567.00"), AmountParser.parse("1,234,567.00"));
        assertEquals(new BigDecimal("1234"), AmountParser.parse("1,234"));
        assertEquals(new BigDecimal("1.234"), AmountParser.parse("1.234"));
    }

    @Test
    public void parsesAccountingNegativesAndSymbols() {
        assertEquals(new BigDecimal("-1234.56"), AmountParser.parse("(1,234.56)"));
        assertEquals(new BigDecimal("-1234.56"), AmountParser.parse("-$1,234.56"));
        assertEquals(new BigDecimal("1234.56"), AmountParser.parse("$ 1,234.56"));
        assertEquals(new BigDecimal("1234.56"), AmountParser.parse("USD 1,234.56"));
        assertEquals(new BigDecimal("1234.56"), AmountParser.parse("1,234.56 EUR"));
        assertEquals(new BigDecimal("-1234.56"), AmountParser.parse("−1,234.56"));
    }

    @Test
    public void rejectsInvalidAmounts() {
        String[] invalid = {
                "", " ", "abc", "12.3.4", "1,23,456", "1,5.0",
                "12%", "1e5", "--5", "5-", "1,234.5,6", "()", "N/A", "1,234,56", "12 widgets"
        };
        for (String s : invalid) {
            try {
                AmountParser.parse(s);
                fail("expected invalid amount for: " + s);
            } catch (InvalidAmountException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }

    // ---- happy path -------------------------------------------------------

    @Test
    public void acceptsInterleavedCurrenciesWithSubtotalsAndTotals() {
        Table t = table(1,
                new String[]{"TYPE", "CCY", "AMOUNT"},
                new String[]{"DETAIL", "USD", "100.10"},
                new String[]{"DETAIL", "EUR", "50.00"},
                new String[]{"DETAIL", "USD", "20.20"},
                new String[]{"SUBTOTAL", "USD", "120.30"},
                new String[]{"DETAIL", "EUR", "25.00"},
                new String[]{"DETAIL", "USD", "9.70"},
                new String[]{"SUBTOTAL", "USD", "9.70"},
                new String[]{"SUBTOTAL", "EUR", "75.00"},
                new String[]{"TOTAL", "USD", "130.00"},
                new String[]{"TOTAL", "EUR", "75.00"});

        ReconciliationResult result = reconcile(t);
        assertNoErrors(result);

        List<ReconciledRow> rows = result.getRows();
        assertEquals(5, rows.size());
        assertEquals(new BigDecimal("100.10"), rows.get(0).getAmount());
        assertEquals("USD", rows.get(0).getCurrency());
        assertEquals(2, rows.get(0).getRowNumber());
        assertEquals(new BigDecimal("50.00"), rows.get(1).getAmount());
        assertEquals(new BigDecimal("20.20"), rows.get(2).getAmount());
        assertEquals(new BigDecimal("25.00"), rows.get(3).getAmount());
        assertEquals(new BigDecimal("9.70"), rows.get(4).getAmount());
        // Original order preserved.
        assertEquals(2, rows.get(0).getRowNumber());
        assertEquals(3, rows.get(1).getRowNumber());
        assertEquals(4, rows.get(2).getRowNumber());
        assertEquals(6, rows.get(3).getRowNumber());
        assertEquals(7, rows.get(4).getRowNumber());
    }

    @Test
    public void detailMarkerIsOptional() {
        ReconciliationConfig config = ReconciliationConfig.builder()
                .rowTypeColumn(0)
                .currencyColumn(1)
                .amountColumn(2)
                .subtotalMarker("SUBTOTAL")
                .totalMarker("TOTAL")
                .build();
        Table t = table(1,
                new String[]{"ANYTHING", "USD", "1.00"},
                new String[]{"SOMETHING ELSE", "USD", "2.00"},
                new String[]{"SUBTOTAL", "USD", "3.00"},
                new String[]{"TOTAL", "USD", "3.00"});
        ReconciliationResult result = new Reconciler(config).reconcile(t);
        assertNoErrors(result);
        assertEquals(2, result.getRows().size());
    }

    @Test
    public void unmatchedRowTypeIsIgnoredWhenDetailMarkerIsSet() {
        Table t = table(1,
                new String[]{"TYPE", "CCY", "AMOUNT"},
                new String[]{"DETAIL", "USD", "1.00"},
                new String[]{"NOTE", "ignored", "not-a-number"},
                new String[]{"SUBTOTAL", "USD", "1.00"},
                new String[]{"TOTAL", "USD", "1.00"});
        ReconciliationResult result = reconcile(t);
        assertNoErrors(result);
        assertEquals(1, result.getRows().size());
    }

    // ---- failure modes ----------------------------------------------------

    @Test
    public void subtotalMismatchProducesNoRows() {
        Table t = table(7,
                new String[]{"DETAIL", "USD", "1.00"},
                new String[]{"DETAIL", "USD", "2.00"},
                new String[]{"SUBTOTAL", "USD", "4.00"},
                new String[]{"TOTAL", "USD", "3.00"});
        ReconciliationResult result = reconcile(t);
        assertFalse(result.isSuccess());
        assertTrue(result.getRows().isEmpty());
        assertEquals(ReconciliationError.Kind.SUBTOTAL_MISMATCH, result.getErrors().get(0).getKind());
        assertEquals(7, result.getErrors().get(0).getPageNumber());
        assertEquals(3, result.getErrors().get(0).getRowNumber());
        assertEquals("USD", result.getErrors().get(0).getCurrency());
    }

    @Test
    public void subtotalOnlyChecksDetailsSincePreviousSubtotal() {
        Table t = table(1,
                new String[]{"DETAIL", "USD", "1.00"},
                new String[]{"SUBTOTAL", "USD", "1.00"},
                new String[]{"DETAIL", "USD", "2.00"},
                new String[]{"SUBTOTAL", "USD", "2.00"},
                new String[]{"TOTAL", "USD", "3.00"});
        ReconciliationResult result = reconcile(t);
        assertNoErrors(result);
    }

    @Test
    public void totalMismatchProducesNoRows() {
        Table t = table(2,
                new String[]{"DETAIL", "EUR", "10.00"},
                new String[]{"SUBTOTAL", "EUR", "10.00"},
                new String[]{"TOTAL", "EUR", "11.00"});
        ReconciliationResult result = reconcile(t);
        assertFalse(result.isSuccess());
        assertEquals(ReconciliationError.Kind.TOTAL_MISMATCH, result.getErrors().get(0).getKind());
        assertEquals(2, result.getErrors().get(0).getPageNumber());
        assertEquals(3, result.getErrors().get(0).getRowNumber());
        assertEquals("EUR", result.getErrors().get(0).getCurrency());
    }

    @Test
    public void totalChecksWholeTableNotJustLastSegment() {
        Table t = table(1,
                new String[]{"DETAIL", "USD", "1.00"},
                new String[]{"SUBTOTAL", "USD", "1.00"},
                new String[]{"DETAIL", "USD", "2.00"},
                new String[]{"SUBTOTAL", "USD", "2.00"},
                // Total of only the last segment would wrongly be accepted.
                new String[]{"TOTAL", "USD", "2.00"});
        ReconciliationResult result = reconcile(t);
        assertFalse(result.isSuccess());
        assertEquals(ReconciliationError.Kind.TOTAL_MISMATCH, result.getErrors().get(0).getKind());
    }

    @Test
    public void invalidAmountIsReportedWithPageRowCurrency() {
        Table t = table(4,
                new String[]{"DETAIL", "GBP", "n/a"},
                new String[]{"DETAIL", "GBP", "1.00"},
                new String[]{"SUBTOTAL", "GBP", "1.00"},
                new String[]{"TOTAL", "GBP", "1.00"});
        ReconciliationResult result = reconcile(t);
        assertFalse(result.isSuccess());
        ReconciliationError error = result.getErrors().get(0);
        assertEquals(ReconciliationError.Kind.INVALID_AMOUNT, error.getKind());
        assertEquals(4, error.getPageNumber());
        assertEquals(1, error.getRowNumber());
        assertEquals("GBP", error.getCurrency());
        assertTrue(result.getRows().isEmpty());
    }

    @Test
    public void unclosedSegmentAtEndOfTableIsReported() {
        Table t = table(1,
                new String[]{"DETAIL", "USD", "1.00"},
                new String[]{"SUBTOTAL", "USD", "1.00"},
                new String[]{"DETAIL", "USD", "2.00"},
                new String[]{"TOTAL", "USD", "3.00"});
        ReconciliationResult result = reconcile(t);
        assertFalse(result.isSuccess());
        boolean found = false;
        for (ReconciliationError error : result.getErrors()) {
            if (error.getKind() == ReconciliationError.Kind.UNCLOSED_SEGMENT) {
                found = true;
                assertEquals("USD", error.getCurrency());
            }
        }
        assertTrue(found);
    }

    @Test
    public void missingTotalIsReported() {
        Table t = table(1,
                new String[]{"DETAIL", "USD", "1.00"},
                new String[]{"SUBTOTAL", "USD", "1.00"});
        ReconciliationResult result = reconcile(t);
        assertFalse(result.isSuccess());
        assertEquals(ReconciliationError.Kind.MISSING_TOTAL, result.getErrors().get(0).getKind());
        assertEquals("USD", result.getErrors().get(0).getCurrency());
    }

    @Test
    public void currencyAfterTotalIsReported() {
        Table t = table(5,
                new String[]{"DETAIL", "USD", "1.00"},
                new String[]{"SUBTOTAL", "USD", "1.00"},
                new String[]{"TOTAL", "USD", "1.00"},
                new String[]{"DETAIL", "USD", "2.00"},
                new String[]{"SUBTOTAL", "USD", "2.00"});
        ReconciliationResult result = reconcile(t);
        assertFalse(result.isSuccess());
        ReconciliationError error = result.getErrors().get(0);
        assertEquals(ReconciliationError.Kind.CURRENCY_AFTER_TOTAL, error.getKind());
        assertEquals(5, error.getPageNumber());
        assertEquals(4, error.getRowNumber());
        assertEquals("USD", error.getCurrency());
    }

    @Test
    public void tablesAreReconciledIndependently() {
        Table t1 = table(1,
                new String[]{"DETAIL", "USD", "1.00"},
                new String[]{"SUBTOTAL", "USD", "1.00"},
                new String[]{"TOTAL", "USD", "1.00"});
        Table t2 = table(2,
                new String[]{"DETAIL", "USD", "5.00"},
                new String[]{"SUBTOTAL", "USD", "5.00"},
                new String[]{"TOTAL", "USD", "5.00"});

        ReconciliationResult result = new Reconciler(CONFIG)
                .reconcile(Arrays.asList(t1, t2));
        assertNoErrors(result);
        assertEquals(2, result.getRows().size());
        assertEquals(1, result.getRows().get(0).getPageNumber());
        assertEquals(2, result.getRows().get(1).getPageNumber());
    }

    @Test
    public void exactDecimalArithmeticAvoidsFloatingPointDrift() {
        Table t = table(1,
                new String[]{"DETAIL", "USD", "0.10"},
                new String[]{"DETAIL", "USD", "0.20"},
                new String[]{"SUBTOTAL", "USD", "0.30"},
                new String[]{"TOTAL", "USD", "0.30"});
        ReconciliationResult result = reconcile(t);
        assertNoErrors(result);
    }

    @Test
    public void configFromSpec() {
        ReconciliationConfig config = ReconciliationConfig.fromSpec("0,1,2,DETAIL,SUBTOTAL,TOTAL");
        assertEquals(0, config.getRowTypeColumn());
        assertEquals(1, config.getCurrencyColumn());
        assertEquals(2, config.getAmountColumn());
        assertEquals("DETAIL", config.getDetailMarker());
        assertEquals("SUBTOTAL", config.getSubtotalMarker());
        assertEquals("TOTAL", config.getTotalMarker());

        ReconciliationConfig noDetail = ReconciliationConfig.fromSpec("0,1,2,,SUBTOTAL,TOTAL");
        assertEquals(null, noDetail.getDetailMarker());
    }

    @Test
    public void rejectsConflictingMarkers() {
        try {
            new ReconciliationConfig(0, 1, 2, "SUBTOTAL", "SUBTOTAL", null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void errorsListPageRowCurrencyForEveryProblem() {
        Table t = table(9,
                new String[]{"DETAIL", "USD", "bad"},
                new String[]{"DETAIL", "EUR", "2.00"},
                new String[]{"SUBTOTAL", "EUR", "3.00"},
                new String[]{"TOTAL", "USD", "1.00"},
                new String[]{"TOTAL", "EUR", "2.00"});
        ReconciliationResult result = reconcile(t);
        assertFalse(result.isSuccess());

        List<String> messages = new ArrayList<>();
        for (ReconciliationError error : result.getErrors()) {
            messages.add(error.toString());
            assertEquals(9, error.getPageNumber());
            assertFalse(error.getCurrency().isEmpty());
            assertTrue(error.getRowNumber() > 0);
        }

        assertEquals(3, result.getErrors().size());

        ReconciliationError invalid = result.getErrors().get(0);
        assertEquals(ReconciliationError.Kind.INVALID_AMOUNT, invalid.getKind());
        assertEquals(1, invalid.getRowNumber());
        assertEquals("USD", invalid.getCurrency());

        ReconciliationError subtotal = result.getErrors().get(1);
        assertEquals(ReconciliationError.Kind.SUBTOTAL_MISMATCH, subtotal.getKind());
        assertEquals(3, subtotal.getRowNumber());
        assertEquals("EUR", subtotal.getCurrency());

        ReconciliationError total = result.getErrors().get(2);
        assertEquals(ReconciliationError.Kind.TOTAL_MISMATCH, total.getKind());
        assertEquals(4, total.getRowNumber());
        assertEquals("USD", total.getCurrency());
    }
}
