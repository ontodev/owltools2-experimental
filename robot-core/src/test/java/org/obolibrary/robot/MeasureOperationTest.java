package org.obolibrary.robot;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import org.junit.Test;
import org.obolibrary.robot.metrics.MeasureResult;
import org.obolibrary.robot.metrics.MetricsLabels;
import org.obolibrary.robot.providers.CURIEShortFormProvider;
import org.semanticweb.elk.io.FileUtils;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

/**
 * Test metrics operation.trea
 *
 * @author <a href="mailto:nicolas.matentzoglu@gmail.com">Nicolas Matentzoglu</a>
 */
public class MeasureOperationTest extends CoreTest {

  String TESTONTOLOGY = "uberon.owl";

  /**
   * Tests essential metrics generation.
   *
   * @throws IOException on IO error
   */
  @Test
  public void testEssentialMetrics() throws IOException {
    OWLOntology ontology = loadOntology("/" + TESTONTOLOGY);
    MeasureResult result =
        MeasureOperation.getMetrics(
            ontology, "essential", new CURIEShortFormProvider(new IOHelper().getPrefixes()));
    assertEquals(313, result.getSimpleMetricValue(MetricsLabels.AXIOM_COUNT));
  }

  /**
   * Tests extended metrics generation.
   *
   * @throws IOException on IO error
   */
  @Test
  public void testExtendedMetrics() throws IOException {
    OWLOntology ontology = loadOntology("/" + TESTONTOLOGY);
    MeasureResult result =
        MeasureOperation.getMetrics(
            ontology, "extended", new CURIEShortFormProvider(new IOHelper().getPrefixes()));
    assertEquals("SID", result.getSimpleMetricValue(MetricsLabels.EXPRESSIVITY));
  }

  /**
   * Tests all metrics generation.
   *
   * @throws IOException on IO error
   */
  @Test
  public void testAllMetrics() throws IOException {
    OWLOntology ontology = loadOntology("/" + TESTONTOLOGY);
    MeasureResult result =
        MeasureOperation.getMetrics(
            ontology, "all", new CURIEShortFormProvider(new IOHelper().getPrefixes()));
    assertEquals(0, result.getSimpleMetricValue(MetricsLabels.GCI_COUNT_INCL));
  }

  /**
   * Tests all metrics generation.
   *
   * @throws IOException on IO error
   */
  @Test
  public void writeAllMetricsToTSV() throws IOException {
    OWLOntology ontology = loadOntology("/" + TESTONTOLOGY);
    OWLReasonerFactory rf = new ElkReasonerFactory();
    MeasureResult result =
        MeasureOperation.getMetrics(
            ontology, rf, "all-reasoner", new CURIEShortFormProvider(new IOHelper().getPrefixes()));
    File testFile = new File("test.tsv");
    MeasureOperation.maybeWriteResult(result, "tsv", testFile);
    assertTrue(testFile.exists());
    FileUtils.deleteRecursively(testFile);
  }

  /**
   * Tests all metrics generation.
   *
   * @throws IOException on IO error
   */
  @Test
  public void writeAllMetricsToCSV() throws IOException {
    OWLOntology ontology = loadOntology("/" + TESTONTOLOGY);
    MeasureResult result =
        MeasureOperation.getMetrics(
            ontology, "all", new CURIEShortFormProvider(new IOHelper().getPrefixes()));
    File testFile = new File("test.csv");
    MeasureOperation.maybeWriteResult(result, "csv", testFile);
    assertTrue(testFile.exists());
    FileUtils.deleteRecursively(testFile);
  }

  /**
   * Tests all metrics generation.
   *
   * @throws IOException on IO error
   */
  @Test
  public void writeAllMetricsToJSON() throws IOException {
    OWLOntology ontology = loadOntology("/" + TESTONTOLOGY);
    MeasureResult result =
        MeasureOperation.getMetrics(
            ontology, "all", new CURIEShortFormProvider(new IOHelper().getPrefixes()));
    File testFile = new File("test.json");
    MeasureOperation.maybeWriteResult(result, "json", testFile);
    assertTrue(testFile.exists());
    FileUtils.deleteRecursively(testFile);
  }

  /**
   * Tests all metrics generation.
   *
   * @throws IOException on IO error
   */
  @Test
  public void writeAllMetricsToYAML() throws IOException {
    OWLOntology ontology = loadOntology("/" + TESTONTOLOGY);
    MeasureResult result =
        MeasureOperation.getMetrics(
            ontology, "all", new CURIEShortFormProvider(new IOHelper().getPrefixes()));
    File testFile = new File("test.yml");
    MeasureOperation.maybeWriteResult(result, "yaml", testFile);
    assertTrue(testFile.exists());
    FileUtils.deleteRecursively(testFile);
  }
}
