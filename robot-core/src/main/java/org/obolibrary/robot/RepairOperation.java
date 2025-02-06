package org.obolibrary.robot;

import java.util.*;
import java.util.stream.Collectors;
import org.obolibrary.robot.checks.InvalidReferenceChecker;
import org.obolibrary.robot.checks.InvalidReferenceViolation;
import org.obolibrary.robot.checks.InvalidReferenceViolation.Category;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.util.OWLEntityRenamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Repair an ontology */
public class RepairOperation {
  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(RepairOperation.class);

  /**
   * Return a map from option name to default option value, for all the available repair options.
   *
   * @return a map with default values for all available options
   */
  public static Map<String, String> getDefaultOptions() {
    // options.put("remove-redundant-subclass-axioms", "true");
    return new HashMap<>();
  }

  /**
   * Repairs ontology
   *
   * @param ontology the OWLOntology to repair
   * @param ioHelper IOHelper to work with the ontology
   */
  public static void repair(OWLOntology ontology, IOHelper ioHelper) {
    repair(ontology, ioHelper, getDefaultOptions());
  }

  /**
   * Repairs ontology
   *
   * @param ontology the OWLOntology to repair
   * @param ioHelper IOHelper to work with the ontology
   * @param mergeAxiomAnnotations if true, merge annotations on duplicate axioms
   */
  public static void repair(
      OWLOntology ontology, IOHelper ioHelper, boolean mergeAxiomAnnotations) {
    repair(ontology, ioHelper, getDefaultOptions(), mergeAxiomAnnotations);
  }

  /**
   * Repairs ontology
   *
   * @param ontology the OWLOntology to repair
   * @param ioHelper IOHelper to work with the ontology
   * @param mergeAxiomAnnotations if true, merge annotations on duplicate axioms
   * @param migrateAnnotations set of annotation properties for which to migrate values
   */
  public static void repair(
      OWLOntology ontology,
      IOHelper ioHelper,
      boolean mergeAxiomAnnotations,
      Set<OWLAnnotationProperty> migrateAnnotations) {
    repair(ontology, ioHelper, getDefaultOptions(), mergeAxiomAnnotations, migrateAnnotations);
  }

  /**
   * Repairs ontology
   *
   * @param ontology the OWLOntology to repair
   * @param ioHelper IOHelper to work with the ontology
   * @param options map of repair options
   */
  public static void repair(OWLOntology ontology, IOHelper ioHelper, Map<String, String> options) {
    repair(ontology, ioHelper, options, false);
  }

  /**
   * Repairs ontology
   *
   * @param ontology the OWLOntology to repair
   * @param ioHelper IOHelper to work with the ontology
   * @param options map of repair options
   * @param mergeAxiomAnnotations if true, merge annotations on duplicate axioms
   */
  public static void repair(
      OWLOntology ontology,
      IOHelper ioHelper,
      Map<String, String> options,
      boolean mergeAxiomAnnotations) {
    repair(ontology, ioHelper, options, mergeAxiomAnnotations, Collections.emptySet());
  }

  /**
   * Repairs ontology
   *
   * @param ontology the OWLOntology to repair
   * @param ioHelper IOHelper to work with the ontology
   * @param options map of repair options
   * @param mergeAxiomAnnotations if true, merge annotations on duplicate axioms
   * @param migrateAnnotations set of annotation properties for which to migrate values
   */
  public static void repair(
      OWLOntology ontology,
      IOHelper ioHelper,
      Map<String, String> options,
      boolean mergeAxiomAnnotations,
      Set<OWLAnnotationProperty> migrateAnnotations) {
    Set<InvalidReferenceViolation> violations =
        InvalidReferenceChecker.getInvalidReferenceViolations(ontology, true);
    repairInvalidReferences(ioHelper, ontology, violations, migrateAnnotations);
    if (mergeAxiomAnnotations) {
      mergeAxiomAnnotations(ontology);
    }
  }

  /**
   * Given an ontology, merge the annotations of duplicate axioms to create one axiom with all
   * annotations.
   *
   * @param ontology the OWLOntology to repair
   */
  public static void mergeAxiomAnnotations(OWLOntology ontology) {
    Map<OWLAxiom, Set<OWLAnnotation>> annotsByAxiom = new HashMap<>();
    Set<OWLAxiom> origAxioms = ontology.getAxioms();

    for (OWLAxiom axiom : origAxioms) {
      annotsByAxiom
          .computeIfAbsent(axiom.getAxiomWithoutAnnotations(), k -> new HashSet<>())
          .addAll(axiom.getAnnotations());
    }

    Set<OWLAxiom> newAxioms = new HashSet<>();
    for (Map.Entry<OWLAxiom, Set<OWLAnnotation>> mergedAxiom : annotsByAxiom.entrySet()) {
      newAxioms.add(mergedAxiom.getKey().getAnnotatedAxiom(mergedAxiom.getValue()));
    }

    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    manager.removeAxioms(ontology, origAxioms);
    manager.addAxioms(ontology, newAxioms);
  }

  /**
   * Repairs invalid references
   *
   * <p>Currently only able to repair references to deprecated classes.
   *
   * <p>Assumes OBO vocabulary
   *
   * @param iohelper IOHelper to work with the ontology
   * @param ontology the OWLOntology to repair
   * @param violations set of references violations
   */
  public static void repairInvalidReferences(
      IOHelper iohelper, OWLOntology ontology, Set<InvalidReferenceViolation> violations) {
    repairInvalidReferences(iohelper, ontology, violations, Collections.emptySet());
  }

  /**
   * Repairs invalid references
   *
   * <p>Currently only able to repair references to deprecated classes.
   *
   * <p>Assumes OBO vocabulary
   *
   * @param iohelper IOHelper to work with the ontology
   * @param ontology the OWLOntology to repair
   * @param violations set of references violations
   * @param migrateAnnotations set of annotation properties for which to migrate values
   */
  public static void repairInvalidReferences(
      IOHelper iohelper,
      OWLOntology ontology,
      Set<InvalidReferenceViolation> violations,
      Set<OWLAnnotationProperty> migrateAnnotations) {
    logger.info("Invalid references: " + violations.size());
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    OWLEntityRenamer renamer = new OWLEntityRenamer(manager, ontology.getImportsClosure());
    Map<OWLEntity, IRI> renameMap = new HashMap<>();
    Set<OWLAxiom> axiomsToPreserve = new HashSet<>();

    for (InvalidReferenceViolation v : violations) {
      if (v.getCategory().equals(Category.DEPRECATED)) {
        OWLEntity obsObj = v.getReferencedObject();
        logger.info("Finding replacements for: " + v);
        IRI replacedBy = null;
        for (OWLOntology o : ontology.getImportsClosure()) {
          for (OWLAnnotationAssertionAxiom aaa : o.getAnnotationAssertionAxioms(obsObj.getIRI())) {
            // TODO: use a vocabulary class
            if (aaa.getProperty()
                .getIRI()
                .equals(IRI.create("http://purl.obolibrary.org/obo/IAO_0100001"))) {
              OWLAnnotationValue val = aaa.getValue();
              IRI valIRI = val.asIRI().orNull();
              if (valIRI != null) {
                logger.info("Using URI replacement: " + valIRI);
                replacedBy = valIRI;
              } else {
                OWLLiteral valLit = val.asLiteral().orNull();
                if (valLit != null) {
                  logger.info("Using CURIE replacement: " + valLit);
                  replacedBy = iohelper.createIRI(valLit.getLiteral());
                }
              }
            }
          }
        }
        if (replacedBy != null) {
          renameMap.put(obsObj, replacedBy);
        }
      }
    }

    for (OWLEntity obsObj : renameMap.keySet()) {
      IRI replacedBy = renameMap.get(obsObj);
      if (obsObj instanceof OWLClass) {
        axiomsToPreserve.addAll(ontology.getAxioms((OWLClass) obsObj, Imports.EXCLUDED));
      }
      if (obsObj instanceof OWLObjectProperty) {
        axiomsToPreserve.addAll(ontology.getAxioms((OWLObjectProperty) obsObj, Imports.EXCLUDED));
      }
      axiomsToPreserve.addAll(ontology.getDeclarationAxioms(obsObj));
      axiomsToPreserve.addAll(
          ontology.getAnnotationAssertionAxioms(obsObj.getIRI()).stream()
              .filter(ax -> !migrateAnnotations.contains(ax.getProperty()))
              .collect(Collectors.toSet()));
      logger.info("Replacing: " + obsObj + " -> " + replacedBy);
    }

    logger.info("PRESERVE: " + axiomsToPreserve);
    manager.removeAxioms(ontology, axiomsToPreserve);
    List<OWLOntologyChange> changes = new ArrayList<>(renamer.changeIRI(renameMap));
    manager.applyChanges(changes);
    manager.addAxioms(ontology, axiomsToPreserve);
  }
}
