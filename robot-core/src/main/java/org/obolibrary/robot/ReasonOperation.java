package org.obolibrary.robot;

import static org.obolibrary.robot.reason.EquivalentClassReasoningMode.ALL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.geneontology.reasoner.ExpressionMaterializingReasoner;
import org.obolibrary.robot.exceptions.OntologyLogicException;
import org.obolibrary.robot.reason.EquivalentClassReasoning;
import org.obolibrary.robot.reason.EquivalentClassReasoningMode;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.RemoveImport;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.InferredAxiomGenerator;
import org.semanticweb.owlapi.util.InferredOntologyGenerator;
import org.semanticweb.owlapi.util.InferredSubClassAxiomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reason over an ontology and add axioms.
 *
 * @author <a href="mailto:james@overton.ca">James A. Overton</a>
 */
public class ReasonOperation {
  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(ReasonOperation.class);

  /**
   * Return a map from option name to default option value, for all the available reasoner options.
   *
   * @return a map with default values for all available options
   */
  public static Map<String, String> getDefaultOptions() {
    Map<String, String> options = new HashMap<String, String>();
    options.put("remove-redundant-subclass-axioms", "true");
    options.put("create-new-ontology", "false");
    options.put("create-new-ontology-with-annotations", "false");
    options.put("annotate-inferred-axioms", "false");
    options.put("exclude-duplicate-axioms", "false");
    options.put("equivalent-classes-allowed", ALL.written());

    return options;
  }

  /**
   * Given an ontology, the name of a reasoner, and an output IRI, return the ontology with inferred
   * axioms added, using the default reasoner options.
   *
   * @param ontology the ontology to reason over
   * @param reasonerFactory the factory to create a reasoner instance from
   * @throws OWLOntologyCreationException on ontology problem
   * @throws OntologyLogicException on inconsistency or incoherency
   */
  public static void reason(OWLOntology ontology, OWLReasonerFactory reasonerFactory)
      throws OWLOntologyCreationException, OntologyLogicException {
    reason(ontology, reasonerFactory, getDefaultOptions());
  }

  /**
   * Given an ontology, the name of a reasoner, an output IRI, and an optional map of reasoner
   * options, return the ontology with inferred axioms added.
   *
   * @param ontology the ontology to reason over
   * @param reasonerFactory the factory to create a reasoner instance from
   * @param options a map of option strings, or null
   * @throws OWLOntologyCreationException on ontology problem
   * @throws OntologyLogicException on inconsistency or incoherency
   */
  public static void reason(
      OWLOntology ontology, OWLReasonerFactory reasonerFactory, Map<String, String> options)
      throws OWLOntologyCreationException, OntologyLogicException {
    logger.info("Ontology has {} axioms.", ontology.getAxioms().size());

    logger.info("Fetching labels...");

    Function<OWLNamedObject, String> labelFunc = OntologyHelper.getLabelFunction(ontology, false);

    int seconds;
    long elapsedTime;
    long startTime = System.currentTimeMillis();

    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLDataFactory dataFactory = manager.getOWLDataFactory();

    logger.info("Starting reasoning...");
    OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
    ReasonerHelper.validate(reasoner);

    logger.info("Precomputing class hierarchy...");
    reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

    EquivalentClassReasoningMode mode =
        EquivalentClassReasoningMode.from(options.getOrDefault("equivalent-classes-allowed", ""));
    logger.info("Finding equivalencies...");

    EquivalentClassReasoning equivalentReasoning =
        new EquivalentClassReasoning(ontology, reasoner, mode);
    boolean passesEquivalenceTests = equivalentReasoning.reason();
    equivalentReasoning.logReport(logger);
    if (!passesEquivalenceTests) {
      System.exit(1);
    }
    // cache the complete set of asserted axioms at the initial state.
    // we will later use this if the -x option is passed, to avoid
    // asserting inferred axioms that are duplicates of existing axioms
    Set<OWLAxiom> existingAxioms = ontology.getAxioms(Imports.INCLUDED);

    // Make sure to add the axiom generators in this way!!!
    List<InferredAxiomGenerator<? extends OWLAxiom>> gens =
        new ArrayList<InferredAxiomGenerator<? extends OWLAxiom>>();
    gens.add(new InferredSubClassAxiomGenerator());
    InferredOntologyGenerator generator = new InferredOntologyGenerator(reasoner, gens);
    logger.info("Using these axiom generators:");
    for (InferredAxiomGenerator<?> inf : generator.getAxiomGenerators()) {
      logger.info("    " + inf);
    }

    elapsedTime = System.currentTimeMillis() - startTime;
    seconds = (int) Math.ceil(elapsedTime / 1000);
    logger.info("Reasoning took {} seconds.", seconds);

    // we first place all inferred axioms into a new ontology;
    // these will be later transferred into the main ontology,
    // unless the create new ontology option is passed
    OWLOntology newAxiomOntology;
    newAxiomOntology = manager.createOntology();

    startTime = System.currentTimeMillis();
    generator.fillOntology(dataFactory, newAxiomOntology);

    if (reasoner instanceof ExpressionMaterializingReasoner) {
      logger.info("Creating expression materializing reasoner...");
      ExpressionMaterializingReasoner emr = (ExpressionMaterializingReasoner) reasoner;
      emr.materializeExpressions();
      for (OWLClass c : ontology.getClassesInSignature()) {
        Set<OWLClassExpression> sces = emr.getSuperClassExpressions(c, true);
        for (OWLClassExpression sce : sces) {
          if (!sce.getSignature().contains(dataFactory.getOWLThing())) {
            OWLAxiom ax = dataFactory.getOWLSubClassOfAxiom(c, sce);
            logger.debug("NEW:" + ax);
            manager.addAxiom(newAxiomOntology, ax);
          }
        }
      }
    }

    logger.info("Reasoning created {} new axioms.", newAxiomOntology.getAxioms().size());

    if (OptionsHelper.optionIsTrue(options, "create-new-ontology")
        || OptionsHelper.optionIsTrue(options, "create-new-ontology-with-annotations")) {
      // because the ontology is passed by reference,
      // we manipulate it in place
      // todo: set ontology id
      if (OptionsHelper.optionIsTrue(options, "create-new-ontology-with-annotations")) {
        logger.info("Placing inferred axioms with annotations into a new ontology");
        manager.removeAxioms(
            ontology,
            ontology
                .getAxioms()
                .stream()
                .filter(nonap -> !(nonap instanceof OWLAnnotationAssertionAxiom))
                .collect(Collectors.toSet()));

      } else {
        logger.info("Placing inferred axioms into a new ontology");
        manager.removeAxioms(ontology, ontology.getAxioms());
      }

      Set<OWLImportsDeclaration> oids = ontology.getImportsDeclarations();
      for (OWLImportsDeclaration oid : oids) {
        RemoveImport ri = new RemoveImport(ontology, oid);
        manager.applyChange(ri);
      }
    }

    IRI propertyIRI = null;
    OWLAnnotationValue value = null;
    if (OptionsHelper.optionIsTrue(options, "annotate-inferred-axioms")) {
      // the default is the convention used by OWLTools and GO, which is
      // the property is_inferred with a literal (note: not xsd) "true"
      propertyIRI = IRI.create("http://www.geneontology.org/formats/oboInOwl#is_inferred");
      value = dataFactory.getOWLLiteral("true");
    }
    for (OWLAxiom a : newAxiomOntology.getAxioms()) {
      if (OptionsHelper.optionIsTrue(options, "exclude-duplicate-axioms")) {
        // if this option is passed, do not add any axioms that are
        // duplicates of existing axioms present at initial state.
        // It may seem this is redundant with the
        // remove-redundant-axioms step, but this is not always the
        // case, particularly when the -n option is used.
        // See: https://github.com/ontodev/robot/issues/85

        // TODO to a check that ignores annotations
        if (existingAxioms.contains(a)) {
          logger.debug("Already have: " + a);
          continue;
        }
      }
      if (OptionsHelper.optionIsTrue(options, "exclude-duplicate-axioms")
          || OptionsHelper.optionIsTrue(options, "exclude-owl-thing")) {
        if (a.containsEntityInSignature(dataFactory.getOWLThing())) {
          logger.debug("Ignoring trivial axioms with " + "OWLThing in signature: " + a);
          continue;
        }
      }
      manager.addAxiom(ontology, a);
      if (propertyIRI != null) {
        OntologyHelper.addAxiomAnnotation(ontology, a, propertyIRI, value);
      }
    }

    if (OptionsHelper.optionIsTrue(options, "remove-redundant-subclass-axioms")) {
      removeRedundantSubClassAxioms(reasoner);
    }
    logger.info("Ontology has {} axioms after all reasoning steps.", ontology.getAxioms().size());

    elapsedTime = System.currentTimeMillis() - startTime;
    seconds = (int) Math.ceil(elapsedTime / 1000);
    logger.info("Filling took {} seconds.", seconds);
  }

  /**
   * Remove subClassAxioms where there is a more direct axiom, and the subClassAxiom does not have
   * any annotations.
   *
   * <p>Example: genotyping assay - asserted in dev: assay - inferred by reasoner: analyte assay -
   * asserted after fill: assay, analyte assay - asserted after removeRedundantSubClassAxioms:
   * analyte assay
   *
   * @param reasoner an OWL reasoner, initialized with a root ontology; the ontology will be
   *     modified
   */
  public static void removeRedundantSubClassAxioms(OWLReasoner reasoner) {
    logger.info("Removing redundant subclass axioms...");
    OWLOntology ontology = reasoner.getRootOntology();
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    OWLDataFactory dataFactory = manager.getOWLDataFactory();

    for (OWLClass thisClass : ontology.getClassesInSignature()) {
      if (thisClass.isOWLNothing() || thisClass.isOWLThing()) {
        continue;
      }

      // Use the reasoner to get all
      // the direct superclasses of this class.
      Set<OWLClass> inferredSuperClasses = new HashSet<OWLClass>();
      for (Node<OWLClass> node : reasoner.getSuperClasses(thisClass, true)) {
        for (OWLClass inferredSuperClass : node) {
          inferredSuperClasses.add(inferredSuperClass);
        }
      }

      // For each subClassAxiom,
      // if the subclass axiom does not have any annotations,
      // and the superclass is named (not anonymous),
      // and the superclass is not in the set of inferred super classes,
      // then remove that axiom.
      for (OWLSubClassOfAxiom subClassAxiom : ontology.getSubClassAxiomsForSubClass(thisClass)) {
        if (subClassAxiom.getAnnotations().size() > 0) {
          continue;
        }
        if (subClassAxiom.getSuperClass().isAnonymous()) {
          continue;
        }
        OWLClass assertedSuperClass = subClassAxiom.getSuperClass().asOWLClass();
        if (inferredSuperClasses.contains(assertedSuperClass)) {
          continue;
        }
        manager.removeAxiom(
            ontology, dataFactory.getOWLSubClassOfAxiom(thisClass, assertedSuperClass));
      }
    }
    logger.info("Ontology now has {} axioms.", ontology.getAxioms().size());
  }
}
