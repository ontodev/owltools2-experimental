package org.obolibrary.robot;

import java.util.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;

/**
 * Implements several variations on MIREOT, as first described in "MIREOT: The minimum information
 * to reference an external ontology term" (<a
 * href="http://dx.doi.org/10.3233/AO-2011-0087">link</a>).
 *
 * @author <a href="mailto:james@overton.ca">James A. Overton</a>
 */
public class MireotOperation {
  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(MireotOperation.class);

  /** Shared data factory. */
  private static final OWLDataFactory dataFactory = new OWLDataFactoryImpl();

  /** RDFS isDefinedBy annotation property. */
  private static final OWLAnnotationProperty isDefinedBy = dataFactory.getRDFSIsDefinedBy();

  /** Specify how to handle intermediates. */
  private static String intermediates;

  /** Specify if source should be annotated. */
  private static boolean annotateSource;

  /** Specify a map of sources. */
  private static Map<IRI, IRI> sourceMap;

  /**
   * Get a set of default annotation properties. Currenly includes only RDFS label.
   *
   * @return a set of annotation properties
   */
  public static Set<OWLAnnotationProperty> getDefaultAnnotationProperties() {
    Set<OWLAnnotationProperty> annotationProperties = new HashSet<>();
    annotationProperties.add(dataFactory.getRDFSLabel());
    return annotationProperties;
  }

  /**
   * Given an input ontology, an IOHelper, a set of upper IRIs, a set of lower IRIs, a set of
   * annotation properties (or null for all), and a map of extract options, get the ancestors of the
   * lower IRIs up to the upper IRIs. Include the specified annotation properties.
   *
   * @param inputOntology OWLOntology to extract from
   * @param ioHelper IOHelper to handle intermediates
   * @param upperIRIs top level IRIs
   * @param lowerIRIs bottom level IRIs
   * @param annotationProperties annotation properties to copy, or null for all
   * @param options map of extract options
   * @return extracted module
   * @throws Exception on any issue
   */
  public static OWLOntology getAncestors(
      OWLOntology inputOntology,
      IOHelper ioHelper,
      Set<IRI> upperIRIs,
      Set<IRI> lowerIRIs,
      Set<OWLAnnotationProperty> annotationProperties,
      Map<String, String> options)
      throws Exception {
    logger.debug("Extract with MIREOT ...");

    OWLOntologyManager outputManager = OWLManager.createOWLOntologyManager();

    // Get options
    setOptions(options, ioHelper);

    // The other OWLAPI extract methods use the source ontology IRI
    // so we'll use it here too.
    OWLOntology outputOntology = outputManager.createOntology(inputOntology.getOntologyID());

    // Directly copy all upper entities
    Set<OWLEntity> upperEntities = OntologyHelper.getEntities(inputOntology, upperIRIs);
    for (OWLEntity entity : upperEntities) {
      OntologyHelper.copy(inputOntology, outputOntology, entity, annotationProperties);
      if (annotateSource) {
        maybeAnnotateSource(outputOntology, outputManager, entity, sourceMap);
      }
    }

    OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
    OWLReasoner reasoner = reasonerFactory.createReasoner(inputOntology);

    // For each lower entity, get the ancestors (all or none)
    Set<OWLEntity> lowerEntities = OntologyHelper.getEntities(inputOntology, lowerIRIs);
    for (OWLEntity entity : lowerEntities) {
      OntologyHelper.copy(inputOntology, outputOntology, entity, annotationProperties);
      if ("none".equals(intermediates)) {
        copyAncestorsNoIntermediates(
            inputOntology,
            outputOntology,
            reasoner,
            upperEntities,
            entity,
            entity,
            annotationProperties);
      } else {
        copyAncestorsAllIntermediates(
            inputOntology, outputOntology, reasoner, upperEntities, entity, annotationProperties);
      }
      if (annotateSource) {
        maybeAnnotateSource(outputOntology, outputManager, entity, sourceMap);
      }
    }

    // Maybe remove unnecessary intermediates
    if (intermediates.equalsIgnoreCase("minimal")) {
      Set<IRI> precious = new HashSet<>();
      if (upperIRIs != null) {
        precious.addAll(upperIRIs);
      }
      precious.addAll(lowerIRIs);
      OntologyHelper.collapseOntology(outputOntology, ioHelper, precious);
    }

    return outputOntology;
  }

  /**
   * Given an ontology, a set of upper-level IRIs, a set of lower-level IRIs, and a set of
   * annotation properties, return a new ontology with just the named ancestors of those terms,
   * their subclass relations, and the selected annotations. The input ontology is not changed.
   *
   * @deprecated replaced by {@link #getAncestors(OWLOntology, IOHelper, Set, Set, Set, Map)}
   * @param inputOntology the ontology to extract from
   * @param upperIRIs ancestors will be copied up to and including these terms
   * @param lowerIRIs copy these terms and their superclasses
   * @param annotationProperties the annotation properties to copy; if null, all will be copied
   * @return a new ontology with the target terms and their named ancestors
   * @throws OWLOntologyCreationException on problems creating new ontology
   */
  @Deprecated
  public static OWLOntology getAncestors(
      OWLOntology inputOntology,
      Set<IRI> upperIRIs,
      Set<IRI> lowerIRIs,
      Set<OWLAnnotationProperty> annotationProperties)
      throws OWLOntologyCreationException {
    logger.debug("Extract with MIREOT ...");

    OWLOntologyManager outputManager = OWLManager.createOWLOntologyManager();
    // The other OWLAPI extract methods use the source ontology IRI
    // so we'll use it here too.
    OWLOntology outputOntology = outputManager.createOntology(inputOntology.getOntologyID());

    Set<OWLEntity> upperEntities = OntologyHelper.getEntities(inputOntology, upperIRIs);
    for (OWLEntity entity : upperEntities) {
      OntologyHelper.copy(inputOntology, outputOntology, entity, annotationProperties);
    }

    OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
    OWLReasoner reasoner = reasonerFactory.createReasoner(inputOntology);

    Set<OWLEntity> lowerEntities = OntologyHelper.getEntities(inputOntology, lowerIRIs);
    for (OWLEntity entity : lowerEntities) {
      OntologyHelper.copy(inputOntology, outputOntology, entity, annotationProperties);
      copyAncestorsAllIntermediates(
          inputOntology, outputOntology, reasoner, upperEntities, entity, annotationProperties);
    }

    return outputOntology;
  }

  /**
   * Given input and output ontologies, a set of upper entitities, a target entity, and a set of
   * annotation properties, copy the target entity and all its named ancestors (recursively) from
   * the input ontology to the output ontology, along with the specified annotations. The input
   * ontology is not changed.
   *
   * @param inputOntology the ontology to copy from
   * @param outputOntology the ontology to copy to
   * @param reasoner OWLReasoner to get superclasses and superproperties while maintaining structure
   * @param upperEntities the top level of entities, or null
   * @param entity the target entity that will have its ancestors copied
   * @param annotationProperties the annotations to copy, or null for all
   */
  private static void copyAncestorsAllIntermediates(
      OWLOntology inputOntology,
      OWLOntology outputOntology,
      OWLReasoner reasoner,
      Set<OWLEntity> upperEntities,
      OWLEntity entity,
      Set<OWLAnnotationProperty> annotationProperties) {
    OWLOntologyManager outputManager = outputOntology.getOWLOntologyManager();

    // If this is an upperEntity, copy it and return.
    if (upperEntities != null && upperEntities.contains(entity)) {
      OntologyHelper.copy(inputOntology, outputOntology, entity, annotationProperties);
      return;
    }

    // Otherwise copy ancestors recursively.
    if (entity.isOWLClass()) {
      Set<OWLClass> superclasses =
          reasoner.getSuperClasses(entity.asOWLClass(), true).getFlattened();
      for (OWLClass superclass : superclasses) {
        OntologyHelper.copy(inputOntology, outputOntology, superclass, annotationProperties);
        outputManager.addAxiom(
            outputOntology, dataFactory.getOWLSubClassOfAxiom(entity.asOWLClass(), superclass));
        copyAncestorsAllIntermediates(
            inputOntology,
            outputOntology,
            reasoner,
            upperEntities,
            superclass,
            annotationProperties);
      }
    } else if (entity.isOWLAnnotationProperty()) {
      Collection<OWLAnnotationProperty> superProperties =
          EntitySearcher.getSuperProperties(entity.asOWLAnnotationProperty(), inputOntology, true);
      for (OWLAnnotationProperty superProperty : superProperties) {
        OntologyHelper.copy(inputOntology, outputOntology, superProperty, annotationProperties);
        outputManager.addAxiom(
            outputOntology,
            dataFactory.getOWLSubAnnotationPropertyOfAxiom(
                entity.asOWLAnnotationProperty(), superProperty));
        copyAncestorsAllIntermediates(
            inputOntology,
            outputOntology,
            reasoner,
            upperEntities,
            superProperty,
            annotationProperties);
      }
    } else if (entity.isOWLObjectProperty()) {
      Set<OWLObjectPropertyExpression> superProperties =
          reasoner.getSuperObjectProperties(entity.asOWLObjectProperty(), true).getFlattened();
      for (OWLObjectPropertyExpression superexpression : superProperties) {
        if (superexpression.isAnonymous()) {
          continue;
        }
        OWLObjectProperty superProperty = superexpression.asOWLObjectProperty();
        OntologyHelper.copy(inputOntology, outputOntology, superProperty, annotationProperties);
        outputManager.addAxiom(
            outputOntology,
            dataFactory.getOWLSubObjectPropertyOfAxiom(
                entity.asOWLObjectProperty(), superProperty));
        copyAncestorsAllIntermediates(
            inputOntology,
            outputOntology,
            reasoner,
            upperEntities,
            superProperty,
            annotationProperties);
      }
    } else if (entity.isOWLDataProperty()) {
      Set<OWLDataProperty> superProperties =
          reasoner.getSuperDataProperties(entity.asOWLDataProperty(), true).getFlattened();
      for (OWLDataProperty superProperty : superProperties) {
        OntologyHelper.copy(inputOntology, outputOntology, superProperty, annotationProperties);
        outputManager.addAxiom(
            outputOntology,
            dataFactory.getOWLSubDataPropertyOfAxiom(entity.asOWLDataProperty(), superProperty));
        copyAncestorsAllIntermediates(
            inputOntology,
            outputOntology,
            reasoner,
            upperEntities,
            superProperty,
            annotationProperties);
      }
    }
  }

  /**
   * Given input and output ontologies, a set of upper entities, a target entity, a bottom entity
   * (from lower terms), and a set of annotation properties, copy the bottom entity and any
   * superclasses from the upper entities from the input ontology to the output ontology, along with
   * the specified annotations. No intermediate superclasses are included. The input ontology is not
   * changed.
   *
   * @param inputOntology the ontology to copy from
   * @param outputOntology the ontology to copy to
   * @param reasoner OWLReasoner to get superclasses and superproperties while maintaining structure
   * @param upperEntities the top level of entities, or null
   * @param entity the target entity to check if included in upper entities
   * @param bottomEntity the entity from lower terms to include
   * @param annotationProperties the annotations to copy, or null for all
   */
  private static void copyAncestorsNoIntermediates(
      OWLOntology inputOntology,
      OWLOntology outputOntology,
      OWLReasoner reasoner,
      Set<OWLEntity> upperEntities,
      OWLEntity entity,
      OWLEntity bottomEntity,
      Set<OWLAnnotationProperty> annotationProperties) {
    OWLOntologyManager outputManager = outputOntology.getOWLOntologyManager();

    // If there are no upperEntities or this is an upperEntity, copy it and return
    if (upperEntities == null || upperEntities.contains(entity)) {
      OntologyHelper.copy(inputOntology, outputOntology, entity, annotationProperties);
      return;
    }

    // Otherwise find the highest level ancestor that was included in upper-terms
    if (entity.isOWLClass()) {
      Set<OWLClass> superclasses =
          reasoner.getSuperClasses(entity.asOWLClass(), true).getFlattened();
      for (OWLClass superclass : superclasses) {
        if (upperEntities.contains(superclass)) {
          OntologyHelper.copyAnnotations(inputOntology, outputOntology, entity, null);
          outputManager.addAxiom(
              outputOntology,
              dataFactory.getOWLSubClassOfAxiom(bottomEntity.asOWLClass(), superclass));
        } else {
          copyAncestorsNoIntermediates(
              inputOntology,
              outputOntology,
              reasoner,
              upperEntities,
              superclass,
              bottomEntity,
              annotationProperties);
        }
      }
    } else if (entity.isOWLAnnotationProperty()) {
      Collection<OWLAnnotationProperty> superProperties =
          EntitySearcher.getSuperProperties(entity.asOWLAnnotationProperty(), inputOntology, true);
      for (OWLAnnotationProperty superProperty : superProperties) {
        if (upperEntities.contains(superProperty)) {
          OntologyHelper.copyAnnotations(inputOntology, outputOntology, entity, null);
          outputManager.addAxiom(
              outputOntology,
              dataFactory.getOWLSubAnnotationPropertyOfAxiom(
                  bottomEntity.asOWLAnnotationProperty(), superProperty));
        } else {
          copyAncestorsNoIntermediates(
              inputOntology,
              outputOntology,
              reasoner,
              upperEntities,
              superProperty,
              bottomEntity,
              annotationProperties);
        }
      }
    } else if (entity.isOWLObjectProperty()) {
      Set<OWLObjectPropertyExpression> superProperties =
          reasoner.getSuperObjectProperties(entity.asOWLObjectProperty(), true).getFlattened();
      for (OWLObjectPropertyExpression superexpression : superProperties) {
        if (superexpression.isAnonymous()) {
          continue;
        }
        OWLObjectProperty superProperty = superexpression.asOWLObjectProperty();
        if (upperEntities.contains(superProperty)) {
          OntologyHelper.copyAnnotations(inputOntology, outputOntology, entity, null);
          outputManager.addAxiom(
              outputOntology,
              dataFactory.getOWLSubObjectPropertyOfAxiom(
                  bottomEntity.asOWLObjectProperty(), superProperty));
        } else {
          copyAncestorsNoIntermediates(
              inputOntology,
              outputOntology,
              reasoner,
              upperEntities,
              superProperty,
              bottomEntity,
              annotationProperties);
        }
      }
    } else if (entity.isOWLDataProperty()) {
      Set<OWLDataProperty> superProperties =
          reasoner.getSuperDataProperties(entity.asOWLDataProperty(), true).getFlattened();
      for (OWLDataProperty superProperty : superProperties) {
        if (upperEntities.contains(superProperty)) {
          OntologyHelper.copyAnnotations(inputOntology, outputOntology, entity, null);
          outputManager.addAxiom(
              outputOntology,
              dataFactory.getOWLSubDataPropertyOfAxiom(
                  bottomEntity.asOWLDataProperty(), superProperty));
        } else {
          copyAncestorsNoIntermediates(
              inputOntology,
              outputOntology,
              reasoner,
              upperEntities,
              superProperty,
              bottomEntity,
              annotationProperties);
        }
      }
    }
  }

  /**
   * Given an ontology, a set of upper-level IRIs, and a set of annotation properties, return a new
   * ontology with just those terms and their named descendants, their subclass relations, and the
   * selected annotations. The input ontology is not changed.
   *
   * @param inputOntology the ontology to extract from
   * @param upperIRIs these terms and their descendants will be copied
   * @param annotationProperties the annotation properties to copy; if null, all will be copied
   * @return a new ontology with the target terms and their named ancestors
   * @throws OWLOntologyCreationException on problems creating new ontology
   */
  public static OWLOntology getDescendants(
      OWLOntology inputOntology,
      IOHelper ioHelper,
      Set<IRI> upperIRIs,
      Set<OWLAnnotationProperty> annotationProperties,
      Map<String, String> options)
      throws Exception {
    logger.debug("Extract with MIREOT ...");

    // Get options
    setOptions(options, ioHelper);

    OWLOntologyManager outputManager = OWLManager.createOWLOntologyManager();
    OWLOntology outputOntology = outputManager.createOntology();

    Set<OWLEntity> upperEntities = OntologyHelper.getEntities(inputOntology, upperIRIs);
    for (OWLEntity entity : upperEntities) {
      OntologyHelper.copy(inputOntology, outputOntology, entity, annotationProperties);
      if ("none".equals(intermediates)) {
        copyDescendantsNoIntermediates(
            inputOntology, outputOntology, entity, entity, annotationProperties);
      } else {
        copyDescendantsAllIntermediates(
            inputOntology, outputOntology, entity, annotationProperties);
      }
      if (annotateSource) {
        maybeAnnotateSource(outputOntology, outputManager, entity, sourceMap);
      }
    }

    if ("minimal".equalsIgnoreCase(intermediates)) {
      OntologyHelper.collapseOntology(outputOntology, ioHelper, upperIRIs);
    }

    return outputOntology;
  }

  /**
   * @deprecated replaced by {@link #getDescendants(OWLOntology, IOHelper, Set, Set, Map)}
   * @param inputOntology ontology to extract from
   * @param upperIRIs these terms and their descendants will be copied
   * @param annotationProperties the annotation properties to copy; if null, all will be copied
   * @return a new ontology with the target terms and their named ancestors
   * @throws OWLOntologyCreationException on problems creating new ontology
   */
  @Deprecated
  public static OWLOntology getDescendants(
      OWLOntology inputOntology,
      Set<IRI> upperIRIs,
      Set<OWLAnnotationProperty> annotationProperties)
      throws OWLOntologyCreationException {
    logger.debug("Extract with MIREOT ...");

    OWLOntologyManager outputManager = OWLManager.createOWLOntologyManager();
    OWLOntology outputOntology = outputManager.createOntology();

    Set<OWLEntity> upperEntities = OntologyHelper.getEntities(inputOntology, upperIRIs);
    for (OWLEntity entity : upperEntities) {
      OntologyHelper.copy(inputOntology, outputOntology, entity, annotationProperties);
      copyDescendantsAllIntermediates(inputOntology, outputOntology, entity, annotationProperties);
    }

    return outputOntology;
  }

  /**
   * Given input and output ontologies, a target entity, and a set of annotation properties, copy
   * the target entity and all its named ancestors (recursively) from the input ontology to the
   * output ontology, along with the specified annotations. The input ontology is not changed.
   *
   * @param inputOntology the ontology to copy from
   * @param outputOntology the ontology to copy to
   * @param entity the target entity that will have its ancestors copied
   * @param annotationProperties the annotations to copy, or null for all
   */
  private static void copyDescendantsAllIntermediates(
      OWLOntology inputOntology,
      OWLOntology outputOntology,
      OWLEntity entity,
      Set<OWLAnnotationProperty> annotationProperties) {
    OWLOntologyManager outputManager = outputOntology.getOWLOntologyManager();

    // Otherwise copy ancestors recursively.
    if (entity.isOWLClass()) {
      Collection<OWLClassExpression> subClasses =
          EntitySearcher.getSubClasses(entity.asOWLClass(), inputOntology);
      for (OWLClassExpression subExpression : subClasses) {
        if (subExpression.isAnonymous()) {
          continue;
        }
        OWLClass subClass = subExpression.asOWLClass();
        OntologyHelper.copy(inputOntology, outputOntology, subClass, annotationProperties);
        outputManager.addAxiom(
            outputOntology, dataFactory.getOWLSubClassOfAxiom(subClass, entity.asOWLClass()));
        copyDescendantsAllIntermediates(
            inputOntology, outputOntology, subClass, annotationProperties);
      }
    } else if (entity.isOWLAnnotationProperty()) {
      Collection<OWLAnnotationProperty> subProperties =
          EntitySearcher.getSubProperties(entity.asOWLAnnotationProperty(), inputOntology, true);
      for (OWLAnnotationProperty subProperty : subProperties) {
        OntologyHelper.copy(inputOntology, outputOntology, subProperty, annotationProperties);
        outputManager.addAxiom(
            outputOntology,
            dataFactory.getOWLSubAnnotationPropertyOfAxiom(
                subProperty, entity.asOWLAnnotationProperty()));
        copyDescendantsAllIntermediates(
            inputOntology, outputOntology, subProperty, annotationProperties);
      }
    } else if (entity.isOWLObjectProperty()) {
      Collection<OWLObjectPropertyExpression> superProperties =
          EntitySearcher.getSuperProperties(entity.asOWLObjectProperty(), inputOntology);
      for (OWLObjectPropertyExpression subExpression : superProperties) {
        if (subExpression.isAnonymous()) {
          continue;
        }
        OWLObjectProperty subProperty = subExpression.asOWLObjectProperty();
        OntologyHelper.copy(inputOntology, outputOntology, subProperty, annotationProperties);
        outputManager.addAxiom(
            outputOntology,
            dataFactory.getOWLSubObjectPropertyOfAxiom(subProperty, entity.asOWLObjectProperty()));
        copyDescendantsAllIntermediates(
            inputOntology, outputOntology, subProperty, annotationProperties);
      }
    } else if (entity.isOWLDataProperty()) {
      Collection<OWLDataPropertyExpression> subProperties =
          EntitySearcher.getSubProperties(entity.asOWLDataProperty(), inputOntology);
      for (OWLDataPropertyExpression subExpression : subProperties) {
        OWLDataProperty subProperty = subExpression.asOWLDataProperty();
        OntologyHelper.copy(inputOntology, outputOntology, subProperty, annotationProperties);
        outputManager.addAxiom(
            outputOntology,
            dataFactory.getOWLSubDataPropertyOfAxiom(subProperty, entity.asOWLDataProperty()));
        copyDescendantsAllIntermediates(
            inputOntology, outputOntology, subProperty, annotationProperties);
      }
    }
  }

  /**
   * Given input and output ontologies, a top entity (from upper terms), a target entity, a and a
   * set of annotation properties, copy the bottom entity and any superclasses from the upper
   * entities from the input ontology to the output ontology, along with the specified annotations.
   * No intermediate superclasses are included. The input ontology is not changed.
   *
   * @param inputOntology the ontology to copy from
   * @param outputOntology the ontology to copy to
   * @param topEntity the entity for upper terms to include
   * @param entity the target entity to check if included in upper entities
   * @param annotationProperties the annotations to copy, or null for all
   */
  private static void copyDescendantsNoIntermediates(
      OWLOntology inputOntology,
      OWLOntology outputOntology,
      OWLEntity topEntity,
      OWLEntity entity,
      Set<OWLAnnotationProperty> annotationProperties) {
    OWLOntologyManager outputManager = outputOntology.getOWLOntologyManager();

    // Otherwise find the highest level ancestor that was included in upper-terms
    if (entity.isOWLClass()) {
      Collection<OWLClassExpression> subClasses =
          EntitySearcher.getSubClasses(entity.asOWLClass(), inputOntology);
      for (OWLClassExpression subExpression : subClasses) {
        if (subExpression.isAnonymous()) {
          continue;
        }
        OWLClass subClass = subExpression.asOWLClass();
        // Find out if this class has any subclasses
        Collection<OWLClassExpression> subSubClasses =
            EntitySearcher.getSubClasses(subClass, inputOntology);
        if (subSubClasses.isEmpty()) {
          OntologyHelper.copyAnnotations(
              inputOntology, outputOntology, entity, annotationProperties);
          outputManager.addAxiom(
              outputOntology, dataFactory.getOWLSubClassOfAxiom(subClass, topEntity.asOWLClass()));
        } else {
          copyDescendantsNoIntermediates(
              inputOntology, outputOntology, topEntity, subClass, annotationProperties);
        }
      }
    } else if (entity.isOWLAnnotationProperty()) {
      Collection<OWLAnnotationProperty> subProperties =
          EntitySearcher.getSubProperties(entity.asOWLAnnotationProperty(), inputOntology, true);
      for (OWLAnnotationProperty subProperty : subProperties) {
        // Find out if this property has any subproperties
        Collection<OWLAnnotationProperty> subSubProperties =
            EntitySearcher.getSubProperties(subProperty, inputOntology);
        if (subSubProperties.isEmpty()) {
          OntologyHelper.copyAnnotations(
              inputOntology, outputOntology, entity, annotationProperties);
          outputManager.addAxiom(
              outputOntology,
              dataFactory.getOWLSubAnnotationPropertyOfAxiom(
                  subProperty, topEntity.asOWLAnnotationProperty()));
        } else {
          copyDescendantsNoIntermediates(
              inputOntology, outputOntology, topEntity, subProperty, annotationProperties);
        }
      }
    } else if (entity.isOWLObjectProperty()) {
      Collection<OWLObjectPropertyExpression> subProperties =
          EntitySearcher.getSubProperties(entity.asOWLObjectProperty(), inputOntology);
      for (OWLObjectPropertyExpression subExpression : subProperties) {
        if (subExpression.isAnonymous()) {
          continue;
        }
        OWLObjectProperty subProperty = subExpression.asOWLObjectProperty();
        // Find out if this property has any subproperties
        Collection<OWLObjectPropertyExpression> subSubProperties =
            EntitySearcher.getSubProperties(subProperty, inputOntology);
        if (subSubProperties.isEmpty()) {
          OntologyHelper.copyAnnotations(
              inputOntology, outputOntology, entity, annotationProperties);
          outputManager.addAxiom(
              outputOntology,
              dataFactory.getOWLSubObjectPropertyOfAxiom(
                  subProperty, topEntity.asOWLObjectProperty()));
        } else {
          copyDescendantsNoIntermediates(
              inputOntology, outputOntology, topEntity, subProperty, annotationProperties);
        }
      }
    } else if (entity.isOWLDataProperty()) {
      Collection<OWLDataPropertyExpression> subProperties =
          EntitySearcher.getSubProperties(entity.asOWLDataProperty(), inputOntology);
      for (OWLDataPropertyExpression subExpression : subProperties) {
        OWLDataProperty subProperty = subExpression.asOWLDataProperty();
        // Find out if this property has any subproperties
        Collection<OWLDataPropertyExpression> subSubProperties =
            EntitySearcher.getSubProperties(subProperty, inputOntology);
        if (subSubProperties.isEmpty()) {
          OntologyHelper.copyAnnotations(
              inputOntology, outputOntology, entity, annotationProperties);
          outputManager.addAxiom(
              outputOntology,
              dataFactory.getOWLSubDataPropertyOfAxiom(subProperty, topEntity.asOWLDataProperty()));
        } else {
          copyDescendantsNoIntermediates(
              inputOntology, outputOntology, topEntity, subProperty, annotationProperties);
        }
      }
    }
  }

  /**
   * Given an ontology, a manager for that ontology, an entity to annotate, and a map of source
   * replacements, add the rdfs:isDefinedBy annotation to the entity.
   *
   * @param ontology OWLOntology to add annotation in
   * @param manager OWLManager for the ontology
   * @param entity OWLEntity to add annotation on
   * @param sourceMap term-to-source map
   */
  private static void maybeAnnotateSource(
      OWLOntology ontology, OWLOntologyManager manager, OWLEntity entity, Map<IRI, IRI> sourceMap) {
    Set<OWLAnnotationValue> existingValues =
        OntologyHelper.getAnnotationValues(ontology, isDefinedBy, entity.getIRI());
    if (existingValues == null || existingValues.size() == 0) {
      manager.addAxiom(ontology, ExtractOperation.getIsDefinedBy(entity, sourceMap));
    }
  }

  /**
   * Given a map of options and an IOHelper, set the MIREOT options.
   *
   * @param options map of options
   * @param ioHelper IOHelper for getting IRIs from sources
   * @throws Exception on any issue
   */
  private static void setOptions(Map<String, String> options, IOHelper ioHelper) throws Exception {
    if (options == null) {
      options = ExtractOperation.getDefaultOptions();
    }
    intermediates = OptionsHelper.getOption(options, "intermediates", "all");
    annotateSource = OptionsHelper.optionIsTrue(options, "annotate-with-sources");
    String sourceMapPath = OptionsHelper.getOption(options, "sources");
    sourceMap = new HashMap<>();
    if (sourceMapPath != null) {
      sourceMap = ExtractOperation.getSourceMap(ioHelper, sourceMapPath);
    }
  }
}
