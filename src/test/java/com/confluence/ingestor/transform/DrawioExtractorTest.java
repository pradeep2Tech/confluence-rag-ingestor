package com.confluence.ingestor.transform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DrawioExtractorTest {

    private DrawioExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new DrawioExtractor();
    }

    @Test
    void extractsLabelsFromMxCells() {
        String xml = """
                <mxfile>
                  <diagram>
                    <mxGraphModel>
                      <root>
                        <mxCell id="2" value="API Gateway" vertex="1" parent="1"/>
                        <mxCell id="3" value="Database" vertex="1" parent="1"/>
                        <mxCell id="4" value="" vertex="1" parent="1"/>
                      </root>
                    </mxGraphModel>
                  </diagram>
                </mxfile>
                """;

        assertThat(extractor.looksLikeDrawioXml(xml)).isTrue();
        assertThat(extractor.extractLabels(xml)).containsExactly("API Gateway", "Database");
    }

    @Test
    void stripsHtmlFromCellValues() {
        String xml = """
                <mxfile>
                  <diagram>
                    <mxGraphModel>
                      <root>
                        <mxCell id="2" value="&lt;b&gt;Billing&lt;/b&gt; Service" vertex="1" parent="1"/>
                      </root>
                    </mxGraphModel>
                  </diagram>
                </mxfile>
                """;

        assertThat(extractor.extractLabels(xml)).containsExactly("Billing Service");
    }

    @Test
    void buildsDiagramPlaceholder() {
        ExtractedDiagram diagram = new ExtractedDiagram(
                "diagram-1", "Architecture", "diagram-1.drawio", "diagram-1.json", List.of("A"));

        assertThat(extractor.placeholder(diagram))
                .isEqualTo("[DIAGRAM:diagram-1](assets/diagrams/diagram-1.json)");
    }
}
