package com.confluence.ingestor.transform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlToMarkdownServiceTest {

    private HtmlToMarkdownService service;

    @BeforeEach
    void setUp() {
        service = new HtmlToMarkdownService(new TableExtractor());
    }

    @Test
    void convertsHeadingsParagraphsAndLists() {
        String html = """
                <h2>Section</h2>
                <p>Intro with <strong>bold</strong> and <em>italic</em>.</p>
                <ul>
                  <li>One</li>
                  <li>Two</li>
                </ul>
                """;

        String markdown = service.toMarkdown(html, "Sample Page");

        assertThat(markdown).startsWith("# Sample Page");
        assertThat(markdown).contains("## Section");
        assertThat(markdown).contains("**bold**");
        assertThat(markdown).contains("*italic*");
        assertThat(markdown).contains("- One");
        assertThat(markdown).contains("- Two");
    }

    @Test
    void convertsCodeMacroAndTable() {
        String html = """
                <ac:structured-macro ac:name="code">
                  <ac:plain-text-body>System.out.println("hi");</ac:plain-text-body>
                </ac:structured-macro>
                <table>
                  <tr><th>Name</th><th>Value</th></tr>
                  <tr><td>foo</td><td>bar</td></tr>
                </table>
                """;

        String markdown = service.toMarkdown(html, "Tech");

        assertThat(markdown).contains("```");
        assertThat(markdown).contains("System.out.println(\"hi\");");
        assertThat(markdown).contains("| Name | Value |");
        assertThat(markdown).contains("| foo | bar |");
    }

    @Test
    void convertsImageMacroToAssetPlaceholder() {
        String html = """
                <ac:image>
                  <ri:attachment ri:filename="diagram.png"/>
                </ac:image>
                """;

        String markdown = service.toMarkdown(html, "Diagram Page");

        assertThat(markdown).contains("![diagram.png](assets/diagram.png)");
    }

    @Test
    void complexTableBecomesPlaceholderAndExtractedArtifact() {
        String html = """
                <p>Before table</p>
                <table>
                  <tr><th>Name</th><th colspan="2">Merged</th></tr>
                  <tr><td>foo</td><td>a</td><td>b</td></tr>
                </table>
                <p>After table</p>
                """;

        HtmlToMarkdownService.MarkdownConversionResult result =
                service.toMarkdownWithTables(html, "Table Page");

        assertThat(result.extractedTables()).hasSize(1);
        assertThat(result.extractedTables().getFirst().tableId()).isEqualTo("table-1");
        assertThat(result.markdown()).contains("[TABLE:table-1](assets/tables/table-1.json)");
        assertThat(result.markdown()).contains("Before table");
        assertThat(result.markdown()).contains("After table");
        assertThat(result.markdown()).doesNotContain("| Merged |");
    }
}
