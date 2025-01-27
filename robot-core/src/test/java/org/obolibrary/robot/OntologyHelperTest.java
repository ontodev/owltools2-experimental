package org.obolibrary.robot;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;
import org.junit.Test;
import org.semanticweb.owlapi.model.*;

/** Tests for OntologyHelper. */
public class OntologyHelperTest extends CoreTest {
  /**
   * Test changing an ontology IRI.
   *
   * @throws Exception on any problem
   */
  @Test
  public void testSetOntologyIRI() throws Exception {
    OWLOntology simple = loadOntology("/simple.owl");
    OntologyHelper.setOntologyIRI(simple, "http://ontology.iri", "http://version.iri");

    IRI ontologyIRI = simple.getOntologyID().getOntologyIRI().orNull();
    IRI versionIRI = simple.getOntologyID().getVersionIRI().orNull();
    if (ontologyIRI != null) {
      assertEquals("http://ontology.iri", ontologyIRI.toString());
    } else {
      throw new Exception(String.format("Ontology IRI for %s does not exist.", "/simple.owl"));
    }
    if (versionIRI != null) {
      assertEquals("http://version.iri", versionIRI.toString());
    } else {
      throw new Exception(String.format("Version IRI for %s does not exist.", "/simple.owl"));
    }
  }

  /**
   * Test getting values.
   *
   * @throws IOException on file problem
   */
  @Test
  public void testGetValues() throws IOException {
    IRI iri = IRI.create(base + "simple.owl#test1");
    OWLOntology simple = loadOntology("/simple.owl");
    OWLDataFactory df = simple.getOWLOntologyManager().getOWLDataFactory();
    Set<String> actual = OntologyHelper.getAnnotationStrings(simple, df.getRDFSLabel(), iri);

    Set<String> expected = new HashSet<>();
    expected.add("Test 1");
    expected.add("test one");
    assertEquals(expected, actual);
  }

  /**
   * Test getting a map of labels.
   *
   * @throws IOException on file problem
   */
  @Test
  public void testGetLabels() throws IOException {
    OWLOntology simple = loadOntology("/simple.owl");
    Map<IRI, String> expected = new HashMap<>();
    expected.put(IRI.create(base + "simple.owl#test1"), "Test 1");
    Map<IRI, String> actual = OntologyHelper.getLabels(simple);
    assertEquals(expected, actual);
  }

  /**
   * Test getting a map from labels to IRIs.
   *
   * @throws IOException on file problem
   */
  @Test
  public void testGetLabelIRIs() throws IOException {
    OWLOntology simple = loadOntology("/simple.owl");
    Map<String, IRI> expected = new HashMap<>();
    expected.put("Test 1", IRI.create(base + "simple.owl#test1"));
    expected.put("test one", IRI.create(base + "simple.owl#test1"));
    Map<String, IRI> actual = OntologyHelper.getLabelIRIs(simple);
    assertEquals(expected, actual);
  }

  /**
   * Test adding and removing annotations from and ontology.
   *
   * @throws IOException on file problem
   */
  @Test
  public void testOntologyAnnotations() throws IOException {
    IOHelper ioHelper = new IOHelper();

    OWLOntology simple = loadOntology("/simple.owl");
    assertEquals(0, simple.getAnnotations().size());

    // Add plain literal
    OntologyHelper.addOntologyAnnotation(
        simple, ioHelper.createIRI(base + "foo"), IOHelper.createLiteral("FOO"));
    assertEquals(1, simple.getAnnotations().size());

    OWLAnnotation annotation = simple.getAnnotations().iterator().next();
    assertEquals(base + "foo", annotation.getProperty().getIRI().toString());
    assertEquals("FOO", OntologyHelper.getValue(annotation.getValue()));

    OntologyHelper.removeOntologyAnnotations(simple);
    assertEquals(0, simple.getAnnotations().size());
  }

  /**
   * Test collapse ontology method.
   *
   * @throws IOException on issue loading ontology or running minimize
   * @throws OWLOntologyCreationException on loading ontology
   */
  @Test
  public void testCollapse() throws IOException, OWLOntologyCreationException {
    OWLOntology ontology = loadOntology("/uberon.owl");
    OntologyHelper.collapseOntology(ontology, 3, new HashSet<>(), true);
    int after = ontology.getClassesInSignature().size();
    TestCase.assertEquals(7, after);
  }

  /**
   * Test collapse ontology method with a precious class.
   *
   * @throws IOException on issue loading ontology or running minimize
   * @throws OWLOntologyCreationException on loading ontology
   */
  @Test
  public void testCollapseWithPrecious() throws IOException, OWLOntologyCreationException {
    OWLOntology ontology = loadOntology("/uberon.owl");
    Set<IRI> precious = new HashSet<>();
    // 'skeletal joint' will be kept
    precious.add(IRI.create("http://purl.obolibrary.org/obo/UBERON_0000982"));
    OntologyHelper.collapseOntology(ontology, 3, precious, true);
    int after = ontology.getClassesInSignature().size();
    TestCase.assertEquals(8, after);
  }

  /**
   * Test dropping of supernumerary annotations.
   *
   * @throws IOException on issue loading ontology
   */
  @Test
  public void testDropExtraAnnotations() throws IOException {
    OWLOntology ontology = loadOntology("/extra-annotations.ofn");
    OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();
    OWLAnnotationProperty prop = df.getRDFSLabel();
    OntologyHelper.removeExtraAnnotations(ontology, Collections.singleton(prop.getIRI()));

    // For :test1, the "Test 1" label should be preserved because it is lexicographically before
    // "Test one"
    IRI testIRI = IRI.create(base + "extra-annotations.owl#test1");
    Set<String> actual = OntologyHelper.getAnnotationStrings(ontology, prop, testIRI);
    Set<String> expected = Collections.singleton("Test 1");
    assertEquals(expected, actual);

    // For :test2, the "Test two" label should be preserved because it has no language tag, even
    // though "Test deux" comes first lexicographically
    testIRI = IRI.create(base + "extra-annotations.owl#test2");
    actual = OntologyHelper.getAnnotationStrings(ontology, prop, testIRI);
    expected = Collections.singleton("Test two");
    assertEquals(expected, actual);
  }

  /**
   * Test merging of supernumerary annotations.
   *
   * @throws IOException on issue loading ontology
   */
  @Test
  public void testMergeExtraAnnotations() throws IOException {
    OWLOntology ontology = loadOntology("/extra-annotations.ofn");
    OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();
    OWLAnnotationProperty prop = df.getRDFSLabel();
    OntologyHelper.mergeExtraAnnotations(ontology, Collections.singleton(prop.getIRI()));

    IRI testIRI = IRI.create(base + "extra-annotations.owl#test1");
    Set<String> actual = OntologyHelper.getAnnotationStrings(ontology, prop, testIRI);
    Set<String> expected = Collections.singleton("Test 1 Test one");
    assertEquals(expected, actual);
  }
}
