package technology.tabula.reconciliation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import technology.tabula.Table;
import technology.tabula.TextChunk;
import technology.tabula.TextElement;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.writers.CSVWriter;

public class TestReconciliationCSVWriter {

    private static final ReconciliationConfig CONFIG = ReconciliationConfig.builder()
            .rowTypeColumn(0)
            .currencyColumn(1)
            .amountColumn(2)
            .detailMarker("DETAIL")
            .subtotalMarker("SUBTOTAL")
            .totalMarker("TOTAL")
            .build();

    private static TextChunk cell(String text) {
        return new TextChunk(new TextElement(0, 0, 0, 0, null, 0, text, 0));
    }

    private static Table table(int page, String[]... rows) {
        Table table = new Table(new BasicExtractionAlgorithm());
        table.setPageNumber(page);
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                table.add(cell(rows[r][c]), r, c);
            }
        }
        return table;
    }

    @Test
    public void writesOnlyDetailRowsInOriginalOrder() throws IOException {
        Table t = table(1,
                new String[]{"TYPE", "CCY", "AMOUNT"},
                new String[]{"DETAIL", "USD", "10.00"},
                new String[]{"DETAIL", "EUR", "5.00"},
                new String[]{"SUBTOTAL", "USD", "10.00"},
                new String[]{"SUBTOTAL", "EUR", "5.00"},
                new String[]{"TOTAL", "USD", "10.00"},
                new String[]{"TOTAL", "EUR", "5.00"});

        StringBuilder out = new StringBuilder();
        CSVWriter.reconciling(CONFIG).write(out, t);

        assertEquals("DETAIL,USD,10.00\r\nDETAIL,EUR,5.00\r\n", out.toString());
    }

    @Test
    public void writesNothingWhenReconciliationFails() {
        Table t = table(3,
                new String[]{"DETAIL", "USD", "10.00"},
                new String[]{"SUBTOTAL", "USD", "11.00"},
                new String[]{"TOTAL", "USD", "11.00"});

        StringBuilder out = new StringBuilder();
        try {
            CSVWriter.reconciling(CONFIG).write(out, Arrays.asList(t));
            fail("expected ReconciliationException");
        } catch (ReconciliationException e) {
            assertEquals(0, out.length());
            assertTrue(e.getMessage().contains("page 3"));
            assertTrue(e.getMessage().contains("row 2"));
            assertTrue(e.getMessage().contains("USD"));
        } catch (IOException e) {
            fail("unexpected IOException: " + e);
        }
    }

    @Test
    public void plainCsvWriterIsUnchanged() throws IOException {
        Table t = table(1,
                new String[]{"TYPE", "CCY", "AMOUNT"},
                new String[]{"DETAIL", "USD", "10.00"},
                new String[]{"SUBTOTAL", "USD", "10.00"},
                new String[]{"TOTAL", "USD", "10.00"});

        StringBuilder out = new StringBuilder();
        new CSVWriter().write(out, t);

        assertEquals("TYPE,CCY,AMOUNT\r\nDETAIL,USD,10.00\r\nSUBTOTAL,USD,10.00\r\nTOTAL,USD,10.00\r\n",
                out.toString());
    }

    @Test
    public void multipleTablesAreWrittenInOrder() throws IOException {
        Table t1 = table(1,
                new String[]{"DETAIL", "USD", "1.00"},
                new String[]{"SUBTOTAL", "USD", "1.00"},
                new String[]{"TOTAL", "USD", "1.00"});
        Table t2 = table(2,
                new String[]{"DETAIL", "EUR", "2.00"},
                new String[]{"SUBTOTAL", "EUR", "2.00"},
                new String[]{"TOTAL", "EUR", "2.00"});

        StringBuilder out = new StringBuilder();
        CSVWriter.reconciling(CONFIG).write(out, Arrays.asList(t1, t2));

        assertEquals("DETAIL,USD,1.00\r\nDETAIL,EUR,2.00\r\n", out.toString());
    }
}
