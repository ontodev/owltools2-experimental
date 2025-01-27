package org.obolibrary.robot;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.geneontology.reasoner.ExpressionMaterializingReasoner;
import org.geneontology.reasoner.ExpressionMaterializingReasonerFactory;
import org.obolibrary.robot.exceptions.IncoherentTBoxException;
import org.obolibrary.robot.exceptions.OntologyLogicException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Materialize SubClassOf axioms using Expression Materializing Reasoner.
 *
 * @see <a href="https://github.com/ontodev/robot/issues/7">issue 7</a>
 * @author <a href="mailto:cjmungall@lbl.gov">Chris Mungall</a>
 */
public class MaterializeOperation {

  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(MaterializeOperation.class);

  /**
   * Return a map from option name to default option value, for all the available reasoner options.
   *
   * @return a map with default values for all available options
   */
  public static Map<String, String> getDefaultOptions() {
    Map<String, String> options = new HashMap<>();
    options.put("create-new-ontology", "false");
    return options;
  }

  /**
   * Replace EquivalentClass axioms with weaker SubClassOf axioms.
   *
   * @param ontology The OWLOntology to relax
   * @param reasonerFactory reasoner factory for the reasoner that is to be wrapped
   * @param properties object properties whose existentials are to be materialized (null
   *     materializes all)
   * @param options A map of options for the operation
   * @throws OWLOntologyCreationException if ontology cannot be created
   * @throws OntologyLogicException if ontology contains logical errors
   */
  public static void materialize(
      OWLOntology ontology,
      OWLReasonerFactory reasonerFactory,
      Set<OWLObjectProperty> properties,
      Map<String, String> options)
      throws OWLOntologyCreationException, OntologyLogicException {

    // TODO: make reasonOverImportsClosure optional rather than always true
    materialize(ontology, reasonerFactory, properties, options, true);
  }

  /**
   * Replace EquivalentClass axioms with weaker SubClassOf axioms.
   *
   * @param ontology The OWLOntology to relax
   * @param reasonerFactory reasoner factory for the reasoner that is to be wrapped
   * @param properties object properties whose existentials are to be materialized (null
   *     materializes all)
   * @param options A map of options for the operation
   * @param reasonOverImportsClosure if true will first perform materialization over all ontologies
   *     in the import closure
   * @param includeIndirect if true include redundant existential restrictions
   * @throws OWLOntologyCreationException on ontology problem
   * @throws OntologyLogicException on logic problem
   */
  public static void materialize(
      OWLOntology ontology,
      OWLReasonerFactory reasonerFactory,
      Set<OWLObjectProperty> properties,
      Map<String, String> options,
      boolean reasonOverImportsClosure)
      throws OntologyLogicException, OWLOntologyCreationException {
    materialize(ontology, reasonerFactory, properties, options, reasonOverImportsClosure, false);
  }

  /**
   * Replace EquivalentClass axioms with weaker SubClassOf axioms.
   *
   * @param ontology The OWLOntology to relax
   * @param reasonerFactory reasoner factory for the reasoner that is to be wrapped
   * @param properties object properties whose existentials are to be materialized (null
   *     materializes all)
   * @param options A map of options for the operation
   * @param reasonOverImportsClosure if true will first perform materialization over all ontologies
   *     in the import closure
   * @throws OWLOntologyCreationException on ontology problem
   * @throws OntologyLogicException on logic problem
   */
  public static void materialize(
      OWLOntology ontology,
      OWLReasonerFactory reasonerFactory,
      Set<OWLObjectProperty> properties,
      Map<String, String> options,
      boolean reasonOverImportsClosure,
      boolean includeIndirect)
      throws OntologyLogicException, OWLOntologyCreationException {

    if (reasonOverImportsClosure) {
      logger.info("Materializing imported ontologies...");
      for (OWLOntology importedOntology : ontology.getImportsClosure()) {
        if (!importedOntology.equals(ontology)) {
          materialize(
              importedOntology, reasonerFactory, properties, options, false, includeIndirect);
        }
      }
    }
    logger.info("Materializing: " + ontology);

    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLDataFactory dataFactory = manager.getOWLDataFactory();

    int seconds;
    long elapsedTime;
    long startTime = System.currentTimeMillis();

    ExpressionMaterializingReasonerFactory merf =
        new ExpressionMaterializingReasonerFactory(reasonerFactory);
    ExpressionMaterializingReasoner emr = merf.createReasoner(ontology);
    ReasonerHelper.validate(emr);

    Set<OWLAxiom> newAxioms = new HashSet<>();

    // this is entirely for reporting purposes
    Map<OWLObjectProperty, Set<OWLAxiom>> newAxiomsByProperty = new HashMap<>();

    logger.info("Materializing..." + properties);
    if (properties == null || properties.size() == 0) {
      emr.materializeExpressions();
    } else {
      emr.materializeExpressions(properties);
    }

    logger.info("Materialization complete; iterating over classes");

    int i = 0;
    Imports importsFlag = Imports.EXCLUDED; // TODO - make this optional

    Set<OWLClass> signature = ontology.getClassesInSignature(importsFlag);
    Set<OWLAxiom> axiomsIncludingImports = ontology.getAxioms(Imports.INCLUDED);

    for (OWLClass c : signature) {
      logger.debug(" Materializing parents of class " + c);
      i++;
      if (i % 100 == 1) {
        logger.info(" Materializing parents of class " + i + "/" + signature.size());
      }
      if (c.equals(dataFactory.getOWLNothing())) {
        continue;
      }
      if (ontology.getAxioms(c, Imports.EXCLUDED).size() == 0) {
        logger.debug("Excluding classes not in main ontology: " + c);
        continue;
      }
      Set<OWLClassExpression> sces = emr.getSuperClassExpressions(c, !includeIndirect);
      if (!emr.isSatisfiable(c)) {
        logger.error("Ontology is not coherent! Unsatisfiable: " + c);
        throw new IncoherentTBoxException(Collections.singleton(c));
      }
      for (OWLClassExpression sce : sces) {
        logger.debug("  PARENT: " + sce);
        // do not make assertions involving Thing;
        // while valid, these are trivial
        if (sce.getSignature().contains(dataFactory.getOWLThing())) {
          logger.debug("Ignoring: " + sce);
          continue;
        }

        // avoid materializing parents with child in signature;
        // this can happen if a property P is reflexive
        // -- every class C is a subclass of P some C
        // while valid, this is trivial, so we avoid asserting
        if (sce.getSignature().contains(c)) {
          logger.debug("Signature contains base class: " + sce);
          continue;
        }

        OWLAxiom ax = dataFactory.getOWLSubClassOfAxiom(c, sce);

        // skip axioms that already exist
        if (axiomsIncludingImports.contains(ax)) {
          logger.debug("Already have: " + ax);
          continue;
        }
        newAxioms.add(ax);

        // for reporting
        for (OWLObjectProperty p : sce.getObjectPropertiesInSignature()) {
          if (!newAxiomsByProperty.containsKey(p)) {
            newAxiomsByProperty.put(p, new HashSet<>());
          }
          newAxiomsByProperty.get(p).add(ax);
        }
      }
    }

    logger.info("Adding " + newAxioms.size() + " materialized parents");

    for (OWLObjectProperty p : newAxiomsByProperty.keySet()) {
      logger.info(
          "  " + p + " generates: " + newAxiomsByProperty.get(p).size() + " materialized parents");
    }

    if (OptionsHelper.optionIsTrue(options, "create-new-ontology")) {
      ontology = manager.createOntology();
    }

    manager.addAxioms(ontology, newAxioms);
    emr.dispose();

    elapsedTime = System.currentTimeMillis() - startTime;
    seconds = (int) Math.ceil(elapsedTime / 1000);
    logger.info("Asserting materialized superclasses took {} seconds.", seconds);
  }
}
