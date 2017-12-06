package org.obolibrary.robot;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

/** Tests for DiffOperation. */
public class DiffOperationTest extends CoreTest {
  /**
   * Compare one ontology to itself.
   *
   * @throws IOException on file problem
   */
  @Test
  public void testCompareIdentical() throws IOException {
    OWLOntology simple = loadOntology("/simple.owl");
    assertIdentical(simple, simple);
  }

  /**
   * Compare one ontology to a modified copy.
   *
   * @throws IOException on file problem
   * @throws OWLOntologyCreationException on ontology problem
   */
  @Test
  public void testCompareModified() throws IOException, OWLOntologyCreationException {
    OWLOntology simple = loadOntology("/simple.owl");
    Set<OWLOntology> onts = new HashSet<OWLOntology>();
    onts.add(simple);
    OWLOntologyManager manager = simple.getOWLOntologyManager();
    OWLDataFactory df = manager.getOWLDataFactory();
    OWLOntology simple1 = manager.createOntology(IRI.create(base + "simple1.owl"), onts);
    IRI test1 = IRI.create(base + "simple.owl#test1");
    manager.addAxiom(
        simple1,
        df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), test1, df.getOWLLiteral("TEST #1")));

    StringWriter writer = new StringWriter();
    boolean actual = DiffOperation.compare(simple, simple1, writer);
    System.out.println(writer.toString());
    assertEquals(false, actual);
    String expected = IOUtils.toString(this.getClass().getResourceAsStream("/simple1.diff"));
    assertEquals(expected, writer.toString());
  }
}
