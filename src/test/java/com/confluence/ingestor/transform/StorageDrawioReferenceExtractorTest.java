package com.confluence.ingestor.transform;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StorageDrawioReferenceExtractorTest {

    @Test
    void extractsDiagramNameEmbeddedXmlAndAttachment() {
        String html = """
                <ac:structured-macro ac:name="drawio">
                  <ac:parameter ac:name="diagramName">Architecture</ac:parameter>
                  <ac:plain-text-body><mxfile><diagram><mxGraphModel><root>
                  <mxCell id="2" value="Service" vertex="1" parent="1"/>
                  </root></mxGraphModel></diagram></mxfile></ac:plain-text-body>
                </ac:structured-macro>
                <ac:structured-macro ac:name="drawio">
                  <ac:parameter ac:name="diagramName">Deployment</ac:parameter>
                  <ri:attachment ri:filename="Deployment.drawio"/>
                </ac:structured-macro>
                """;

        List<DrawioReference> references = StorageDrawioReferenceExtractor.extractReferences(html);

        assertThat(references).hasSize(2);
        assertThat(references.get(0).diagramName()).isEqualTo("Architecture");
        assertThat(references.get(0).embeddedXml()).contains("<mxfile>");
        assertThat(references.get(1).attachmentFilename()).isEqualTo("Deployment.drawio");
    }
}
