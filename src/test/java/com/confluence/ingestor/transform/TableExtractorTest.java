package com.confluence.ingestor.transform;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TableExtractorTest {

    private TableExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new TableExtractor();
    }

    @Test
    void simpleTableIsNotComplex() {
        Element table = parseTable("""
                <table>
                  <tr><th>Name</th><th>Value</th></tr>
                  <tr><td>foo</td><td>bar</td></tr>
                </table>
                """);

        assertThat(extractor.isComplex(table)).isFalse();
        assertThat(extractor.toMarkdownTable(table))
                .contains("| Name | Value |")
                .contains("| foo | bar |");
    }

    @Test
    void colspanMakesTableComplex() {
        Element table = parseTable("""
                <table>
                  <tr><th>Name</th><th colspan="2">Merged</th></tr>
                  <tr><td>foo</td><td>a</td><td>b</td></tr>
                </table>
                """);

        assertThat(extractor.isComplex(table)).isTrue();
    }

    @Test
    void nestedTableIsComplex() {
        Element table = parseTable("""
                <table>
                  <tr>
                    <td>
                      <table><tr><td>inner</td></tr></table>
                    </td>
                  </tr>
                </table>
                """);

        assertThat(extractor.isComplex(table)).isTrue();
    }

    @Test
    void extractPreservesCellSpans() {
        Element table = parseTable("""
                <table>
                  <tr><th>Name</th><th colspan="2">Merged</th></tr>
                  <tr><td>foo</td><td>a</td><td>b</td></tr>
                </table>
                """);

        ExtractedTable extracted = extractor.extract(table, "table-1");

        assertThat(extracted.tableId()).isEqualTo("table-1");
        assertThat(extracted.fileName()).isEqualTo("table-1.json");
        assertThat(extracted.complex()).isTrue();
        assertThat(extracted.rows()).hasSize(2);
        assertThat(extracted.rows().getFirst().cells().get(1).colspan()).isEqualTo(2);
        assertThat(extractor.placeholder(extracted))
                .isEqualTo("[TABLE:table-1](assets/tables/table-1.json)");
    }

    private static Element parseTable(String html) {
        return Jsoup.parse("<root>" + html + "</root>", "", Parser.xmlParser())
                .selectFirst("table");
    }
}
