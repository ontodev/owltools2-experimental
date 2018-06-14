package org.obolibrary.robot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.RemoveImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merge multiple ontologies into a single ontology.
 *
 * @author <a href="mailto:james@overton.ca">James A. Overton</a>
 */
public class MergeOperation {
  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(MergeOperation.class);

  /**
   * Given a single ontology with zero or more imports, add all the imported axioms into the
   * ontology itself, return the modified ontology.
   *
   * @param ontology the ontology to merge
   * @return the new ontology
   */
  public static OWLOntology merge(OWLOntology ontology) {
    List<OWLOntology> ontologies = new ArrayList<OWLOntology>();
    ontologies.add(ontology);
    mergeInto(ontologies, ontology, false);
    return ontology;
  }

  /**
   * Given one or more ontologies, add all their axioms (including their imports closures) into the
   * first ontology, and return the first ontology.
   *
   * <p>Note the imports directives in the first ontology is preserved, but axioms from the imports
   * closures are also added. This behavior may change, see #84
   *
   * <p>We use a list instead of a set because OWLAPI judges identity simply by the ontology IRI,
   * even if two ontologies have different axioms.
   *
   * @param ontologies the list of ontologies to merge
   * @param mergeOptions a map of option strings, or null
   * @return the first ontology
   */
  public static OWLOntology merge(List<OWLOntology> ontologies, Map<String, String> mergeOptions) {
    OWLOntology ontology = ontologies.get(0);

    // TODO - use options to set these
    mergeInto(ontologies, ontology, false, true);
    return ontology;
  }

  /**
   * @param ontologies the list of ontologies to merge
   * @return the first ontology
   */
  public static OWLOntology merge(List<OWLOntology> ontologies) {
    return merge(ontologies, null);
  }

  /**
   * Given a source ontology and a target ontology, add all the axioms from the source ontology and
   * its import closure into the target ontology. The target ontology is not itself merged, so any
   * of its imports remain distinct.
   *
   * @param ontology the source ontology to merge
   * @param targetOntology the ontology to merge axioms into
   */
  public static void mergeInto(OWLOntology ontology, OWLOntology targetOntology) {
    List<OWLOntology> ontologies = new ArrayList<OWLOntology>();
    ontologies.add(ontology);
    mergeInto(ontologies, targetOntology, false);
  }

  /**
   * Given a list of ontologies and a target ontology, add all the axioms from the listed ontologies
   * and their import closure into the target ontology. The target ontology is not itself merged, so
   * any of its imports remain distinct.
   *
   * @param ontologies the list of ontologies to merge
   * @param targetOntology the ontology to merge axioms into
   */
  public static void mergeInto(List<OWLOntology> ontologies, OWLOntology targetOntology) {
    mergeInto(ontologies, targetOntology, false);
  }

  /**
   * Given a source ontology and a target ontology, add all the axioms from the source ontology and
   * its import closure into the target ontology. The target ontology is not itself merged, so any
   * of its imports remain distinct.
   *
   * @param ontology the source ontology to merge
   * @param targetOntology the ontology to merge axioms into
   * @param includeAnnotations true if ontology annotations should be merged; annotations on imports
   *     are not merged
   */
  public static void mergeInto(
      OWLOntology ontology, OWLOntology targetOntology, boolean includeAnnotations) {
    List<OWLOntology> ontologies = new ArrayList<OWLOntology>();
    ontologies.add(ontology);
    mergeInto(ontologies, targetOntology, includeAnnotations);
  }

  public static void mergeInto(
      List<OWLOntology> ontologies, OWLOntology targetOntology, boolean includeAnnotations) {
    mergeInto(ontologies, targetOntology, includeAnnotations, false);
  }

  /**
   * Given a list of ontologies and a target ontology, add all the axioms from the listed ontologies
   * and their import closure into the target ontology. The target ontology is not itself merged, so
   * any of its imports remain distinct.
   *
   * @param ontologies the list of ontologies to merge
   * @param targetOntology the ontology to merge axioms into
   * @param includeAnnotations true if ontology annotations should be merged; annotations on imports
   *     are not merged
   * @param collapseImportsClosure true if imports closure from all ontologies included
   */
  public static void mergeInto(
      List<OWLOntology> ontologies,
      OWLOntology targetOntology,
      boolean includeAnnotations,
      boolean collapseImportsClosure) {

    List<OWLOntology> combinedOntologies = new ArrayList<>();
    combinedOntologies.addAll(ontologies);
    if (collapseImportsClosure) {
      for (OWLOntology ontology : ontologies) {
        combinedOntologies.addAll(ontology.getImportsClosure());
        removeImports(ontology);
      }
      combinedOntologies.addAll(targetOntology.getImportsClosure());
      removeImports(targetOntology);
    }

    for (OWLOntology ontology : combinedOntologies) {
      targetOntology.getOWLOntologyManager().addAxioms(targetOntology, ontology.getAxioms());
      if (includeAnnotations) {
        for (OWLAnnotation annotation : ontology.getAnnotations()) {
          OntologyHelper.addOntologyAnnotation(targetOntology, annotation);
        }
      }
      for (OWLOntology imported : ontology.getImportsClosure()) {
        targetOntology.getOWLOntologyManager().addAxioms(targetOntology, imported.getAxioms());
      }
    }
  }

  private static void removeImports(OWLOntology ontology) {
    Set<OWLImportsDeclaration> oids = ontology.getImportsDeclarations();
    for (OWLImportsDeclaration oid : oids) {
      RemoveImport ri = new RemoveImport(ontology, oid);
      ontology.getOWLOntologyManager().applyChange(ri);
    }
  }

  /**
   * Return a map from option name to default option value, for all the available merge options.
   *
   * @return a map with default values for all available options
   */
  public static Map<String, String> getDefaultOptions() {
    Map<String, String> options = new HashMap<String, String>();
    options.put("collapse-imports-closure", "false");

    return options;
  }
}
