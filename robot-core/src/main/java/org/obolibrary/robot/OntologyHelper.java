package org.obolibrary.robot;

import com.google.common.base.Optional;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.obolibrary.robot.checks.InvalidReferenceChecker;
import org.obolibrary.robot.export.RendererType;
import org.obolibrary.robot.providers.QuotedAnnotationValueShortFormProvider;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.manchestersyntax.renderer.ManchesterOWLSyntaxObjectRenderer;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.ReferencedEntitySetProvider;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides convenience methods for working with OWL ontologies.
 *
 * @author <a href="mailto:james@overton.ca">James A. Overton</a>
 */
public class OntologyHelper {
  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(OntologyHelper.class);

  /** Namespace for general ontology error messages. */
  private static final String NS = "errors#";

  /** Error message when an unsupported axiom type is requested. Expects axiom Class. */
  private static final String axiomTypeError =
      NS + "AXIOM TYPE ERROR cannot annotate axioms of type: %s";

  /** Error message when the ontology does not contain any of the terms. */
  private static final String emptyTermsError =
      NS + "EMPTY TERMS ERROR ontology does not contain input terms";

  /** Error message when entity does not exist in the ontology. */
  private static final String missingEntityError =
      NS + "MISSING ENTITY ERROR ontology does not contain entity: %s";

  /** Error message when one IRI represents more than one entity. */
  private static final String multipleEntitiesError =
      NS + "MULTIPLE ENTITIES ERROR multiple entities represented by: %s";

  /** Error message when an import ontology does not have an IRI. */
  private static final String nullIRIError =
      NS + "NULL IRI ERROR import ontology does not have an IRI";

  /**
   * Given an ontology, an entity, annotates the entity with the given annotation in the ontology.
   *
   * @param ontology the ontology to modify
   * @param owlEntity the entity to annotate
   * @param property annotation property
   * @param value the IRI or literal value to add
   * @param overload when false: if the entity already has an annotation with the given property,
   *     doesn't add the annotation. When true, adds the annotation anyway.
   */
  public static void addEntityAnnotation(
      OWLOntology ontology,
      OWLEntity owlEntity,
      OWLAnnotationProperty property,
      OWLAnnotationValue value,
      boolean overload) {
    OWLAnnotationAssertionAxiom existingAnnotation = null;
    if (!overload) {
      existingAnnotation =
          EntitySearcher.getAnnotationAssertionAxioms(owlEntity.getIRI(), ontology).stream()
              .filter(a -> a.getProperty().getIRI().equals(property.getIRI()))
              .findFirst()
              .orElse(null);
    }
    if (overload || existingAnnotation == null) {
      OWLOntologyManager manager = ontology.getOWLOntologyManager();
      OWLDataFactory factory = manager.getOWLDataFactory();
      OWLAnnotation annotation = factory.getOWLAnnotation(property, value);
      OWLAxiom ax = factory.getOWLAnnotationAssertionAxiom(owlEntity.getIRI(), annotation);
      manager.applyChange(new AddAxiom(ontology, ax));
    }
  }

  /**
   * Given an ontology, an axiom, and a set of annotations, annotate the axiom with the annotations
   * in the ontology.
   *
   * <p>Note that as axioms are immutable, the axiom is removed and replaced with a new one.
   *
   * @param ontology the ontology to modify
   * @param axiom the axiom to annotate
   * @param property annotation property to add
   * @param value the IRI or literal value to add
   * @param overload when false: if the axiom already has an annotation with the given propertyIRI,
   *     doesn't add the annotation. When true, adds the annotation anyway.
   */
  public static void addAxiomAnnotation(
      OWLOntology ontology,
      OWLAxiom axiom,
      OWLAnnotationProperty property,
      OWLAnnotationValue value,
      boolean overload) {
    OWLAnnotation existingAnnotation = null;
    if (!overload) {
      existingAnnotation =
          axiom.getAnnotations().stream()
              .filter(a -> a.getProperty().getIRI().equals(property.getIRI()))
              .findFirst()
              .orElse(null);
    }
    if (overload || existingAnnotation == null) {
      OWLOntologyManager manager = ontology.getOWLOntologyManager();
      OWLDataFactory factory = manager.getOWLDataFactory();
      OWLAnnotation annotation = factory.getOWLAnnotation(property, value);

      OWLAxiom newAxiom = axiom.getAnnotatedAxiom(new HashSet<>(Arrays.asList(annotation)));
      manager.addAxiom(ontology, newAxiom);
      manager.removeAxiom(ontology, axiom);
    }
  }

  /**
   * Given an ontology, an axiom, a property IRI, and a value string, add an annotation to this
   * ontology with that property and value.
   *
   * <p>Note that as axioms are immutable, the axiom is removed and replaced with a new one.
   *
   * @param ontology the ontology to modify
   * @param axiom the axiom to annotate
   * @param propertyIRI the IRI of the property to add
   * @param value the IRI or literal value to add
   */
  public static void addAxiomAnnotation(
      OWLOntology ontology, OWLAxiom axiom, IRI propertyIRI, OWLAnnotationValue value) {
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    OWLDataFactory df = manager.getOWLDataFactory();

    OWLAnnotationProperty property = df.getOWLAnnotationProperty(propertyIRI);
    OWLAnnotation annotation = df.getOWLAnnotation(property, value);
    addAxiomAnnotation(ontology, axiom, Collections.singleton(annotation));
  }

  /**
   * Given an ontology, an axiom, and a set of annotations, annotate the axiom with the annotations
   * in the ontology.
   *
   * <p>Note that as axioms are immutable, the axiom is removed and replaced with a new one.
   *
   * @param ontology the ontology to modify
   * @param axiom the axiom to annotate
   * @param annotations the set of annotation to add to the axiom
   */
  public static void addAxiomAnnotation(
      OWLOntology ontology, OWLAxiom axiom, Set<OWLAnnotation> annotations) {
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    OWLDataFactory factory = manager.getOWLDataFactory();
    OWLAxiom newAxiom;
    if (axiom instanceof OWLSubClassOfAxiom) {
      OWLSubClassOfAxiom x = ((OWLSubClassOfAxiom) axiom);
      newAxiom = factory.getOWLSubClassOfAxiom(x.getSubClass(), x.getSuperClass(), annotations);
      logger.debug("ANNOTATED: " + newAxiom);
    } else {
      // TODO - See https://github.com/ontodev/robot/issues/67
      throw new UnsupportedOperationException(String.format(axiomTypeError, axiom.getClass()));
    }
    manager.removeAxiom(ontology, axiom);
    manager.addAxiom(ontology, newAxiom);
  }

  /**
   * Given an ontology, an annotation property IRI, and an annotation value, annotate all axioms in
   * the ontology with that property and value.
   *
   * @param ontology the ontology to modify
   * @param propertyIRI the IRI of the property to add
   * @param value the IRI or literal value to add
   */
  public static void addAxiomAnnotations(
      OWLOntology ontology, IRI propertyIRI, OWLAnnotationValue value) {
    for (OWLAxiom a : ontology.getAxioms()) {
      addAxiomAnnotation(ontology, a, propertyIRI, value);
    }
  }

  /**
   * Given an ontology, a property IRI, and a value string, add an annotation to this ontology with
   * that property and value.
   *
   * @param ontology the ontology to modify
   * @param propertyIRI the IRI of the property to add
   * @param value the IRI or literal value to add
   */
  public static void addOntologyAnnotation(
      OWLOntology ontology, IRI propertyIRI, OWLAnnotationValue value) {
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    OWLDataFactory df = manager.getOWLDataFactory();

    OWLAnnotationProperty property = df.getOWLAnnotationProperty(propertyIRI);
    OWLAnnotation annotation = df.getOWLAnnotation(property, value);
    addOntologyAnnotation(ontology, annotation);
  }

  /**
   * Annotate the ontology with the annotation.
   *
   * @param ontology the ontology to modify
   * @param annotation the annotation to add
   */
  public static void addOntologyAnnotation(OWLOntology ontology, OWLAnnotation annotation) {
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    AddOntologyAnnotation addition = new AddOntologyAnnotation(ontology, annotation);
    manager.applyChange(addition);
  }

  /**
   * Given an ontology and a set of IRIs that must be retained, remove intermediate superclasses
   * (classes that only have one child) and update the subclass relationships to preserve structure.
   * The ontology passed in is updated. Do not perform collapse multiple times (some intermediate
   * classes will remain after only one pass).
   *
   * @param ontology ontology to remove intermediates in
   * @param precious set of OWLEntities that should not be removed
   * @throws OWLOntologyCreationException on problem creating ontology
   */
  public static void collapseOntology(OWLOntology ontology, Set<IRI> precious)
      throws OWLOntologyCreationException {
    collapseOntology(ontology, 2, precious, false);
  }

  /**
   * Given an ontology, a threshold, and a set of precious IRIs (or empty set), minimize the input
   * ontology's class hierarchy based on the threshold. The threshold is the minimum number of child
   * classes that an intermediate class should have. Any intermediate class that has less than the
   * threshold number of children will be removed and its children will become children of the next
   * level up. Bottom-level and top-level classes are not removed. Any class with an IRI in the
   * precious set is not removed. If not repeat, collapse will only be performed once, meaning that
   * some intermediate classes may remain.
   *
   * @param ontology OWLOntology to minimize
   * @param threshold minimum number of child classes
   * @param precious set of IRIs to keep
   * @param repeat if true, repeat collapsing until no intermediate classes remain
   * @throws OWLOntologyCreationException on problem creating copy to span gaps
   */
  public static void collapseOntology(
      OWLOntology ontology, int threshold, Set<IRI> precious, boolean repeat)
      throws OWLOntologyCreationException {
    OWLOntology copy =
        OWLManager.createOWLOntologyManager().copyOntology(ontology, OntologyCopy.DEEP);
    logger.debug("Classes before collapsing: " + ontology.getClassesInSignature().size());

    Set<OWLObject> removeClasses = getClassesToRemove(ontology, threshold, precious);

    boolean collapsedOnce = false;

    // Remove axioms based on classes
    // Get all axioms that involve these classes
    // Continue to get remove classes until there's no more to remove
    while (!removeClasses.isEmpty()) {
      if (collapsedOnce && !repeat) {
        break;
      }
      Set<OWLAxiom> axiomsToRemove =
          RelatedObjectsHelper.getPartialAxioms(ontology, removeClasses, null);
      OWLOntologyManager manager = ontology.getOWLOntologyManager();
      manager.removeAxioms(ontology, axiomsToRemove);
      // Span gaps to maintain hierarchy
      manager.addAxioms(
          ontology, RelatedObjectsHelper.spanGaps(copy, OntologyHelper.getObjects(ontology)));
      // Repeat until there's no more to remove
      removeClasses = getClassesToRemove(ontology, threshold, precious);
      collapsedOnce = true;
    }
    logger.debug("Classes after collapsing: " + ontology.getClassesInSignature().size());
  }

  /**
   * Given an ontology, a threshold, and a set of precious IRIs (or empty set), return the classes
   * to remove to minimize the class hierarchy. Top-level and bottom-level classes are not removed.
   * Any class with a precious IRI is not removed. Any class with a number of named subclasses that
   * is less than the threshold will be removed.
   *
   * @param ontology OWLOntology to minimize
   * @param threshold minimum number of child classes
   * @param precious set of IRIs to keep
   */
  private static Set<OWLObject> getClassesToRemove(
      OWLOntology ontology, int threshold, Set<IRI> precious) {
    Set<OWLClass> classes = ontology.getClassesInSignature();
    Set<OWLObject> remove = new HashSet<>();

    for (OWLClass cls : classes) {
      if (cls.isOWLThing() || precious.contains(cls.getIRI())) {
        // Ignore if the IRI is in precious or is OWL Thing
        continue;
      }

      // Check for superclasses
      Set<OWLSubClassOfAxiom> superAxioms = ontology.getSubClassAxiomsForSubClass(cls);
      boolean hasNamedSuperclass = false;
      for (OWLSubClassOfAxiom superAx : superAxioms) {
        OWLClassExpression expr = superAx.getSuperClass();
        if (!expr.isAnonymous() && !expr.asOWLClass().isOWLThing()) {
          hasNamedSuperclass = true;
          break;
        }
      }

      if (!hasNamedSuperclass) {
        // Also ignore if there are no named superclasses
        // Or just no superclasses in general
        // This means it is directly placed under owl:Thing
        continue;
      }

      Set<OWLSubClassOfAxiom> subAxioms = ontology.getSubClassAxiomsForSuperClass(cls);
      int scCount = 0;
      for (OWLSubClassOfAxiom subAx : subAxioms) {
        OWLClassExpression expr = subAx.getSubClass();
        if (!expr.isAnonymous()) {
          // Only count the named subclasses
          scCount++;
        }
      }

      if (scCount != 0 && scCount < threshold) {
        // If the class has subclasses, but LESS subclasses than the threshold,
        // add it to the set of classes to be removed
        remove.add(cls);
      }
    }

    return remove;
  }

  /**
   * Given input and output ontologies, a target entity, and a set of annotation properties, copy
   * the target entity from the input ontology to the output ontology, along with the specified
   * annotations. If the entity is already in the outputOntology, then return without making any
   * changes. The input ontology is not changed.
   *
   * @param inputOntology the ontology to copy from
   * @param outputOntology the ontology to copy to
   * @param entity the target entity that will have its ancestors copied
   * @param annotationProperties the annotations to copy
   */
  public static void copy(
      OWLOntology inputOntology,
      OWLOntology outputOntology,
      OWLEntity entity,
      Set<OWLAnnotationProperty> annotationProperties) {
    OWLDataFactory dataFactory = inputOntology.getOWLOntologyManager().getOWLDataFactory();
    // Don't copy OWLThing
    if (entity == dataFactory.getOWLThing()) {
      return;
    }
    // Don't copy OWLNothing
    if (entity == dataFactory.getOWLNothing()) {
      return;
    }
    // Don't copy existing terms
    if (outputOntology.containsEntityInSignature(entity)) {
      return;
    }

    // Add declaration
    OWLOntologyManager outputManager = outputOntology.getOWLOntologyManager();
    if (entity.isOWLAnnotationProperty()) {
      outputManager.addAxiom(
          outputOntology, dataFactory.getOWLDeclarationAxiom(entity.asOWLAnnotationProperty()));
    } else if (entity.isOWLObjectProperty()) {
      outputManager.addAxiom(
          outputOntology, dataFactory.getOWLDeclarationAxiom(entity.asOWLObjectProperty()));
    } else if (entity.isOWLDataProperty()) {
      outputManager.addAxiom(
          outputOntology, dataFactory.getOWLDeclarationAxiom(entity.asOWLDataProperty()));
    } else if (entity.isOWLDatatype()) {
      outputManager.addAxiom(
          outputOntology, dataFactory.getOWLDeclarationAxiom(entity.asOWLDatatype()));
    } else if (entity.isOWLClass()) {
      outputManager.addAxiom(
          outputOntology, dataFactory.getOWLDeclarationAxiom(entity.asOWLClass()));
    } else if (entity.isOWLNamedIndividual()) {
      outputManager.addAxiom(
          outputOntology, dataFactory.getOWLDeclarationAxiom(entity.asOWLNamedIndividual()));
    }

    // Copy the axioms
    copyAnnotations(inputOntology, outputOntology, entity, annotationProperties);
  }

  /**
   * Given an input ontology, an output ontology, an entity to copy annotations of, and the
   * annotation properties to copy (or null for all), copy annotations of the entity from the input
   * to the output ontology.
   *
   * @param inputOntology OWLOntology to copy from
   * @param outputOntology OWLOntology to copy to
   * @param entity OWLEntity to copy annotations of
   * @param annotationProperties Set of annotation properties to copy, or null for all
   */
  public static void copyAnnotations(
      OWLOntology inputOntology,
      OWLOntology outputOntology,
      OWLEntity entity,
      Set<OWLAnnotationProperty> annotationProperties) {
    OWLOntologyManager outputManager = outputOntology.getOWLOntologyManager();
    Set<OWLAnnotationAssertionAxiom> axioms =
        inputOntology.getAnnotationAssertionAxioms(entity.getIRI());
    for (OWLAnnotationAssertionAxiom axiom : axioms) {
      if (annotationProperties == null || annotationProperties.contains(axiom.getProperty())) {
        // Copy the annotation property and then the axiom.
        copy(inputOntology, outputOntology, axiom.getProperty(), annotationProperties);
        outputManager.addAxiom(outputOntology, axiom);
      }
    }
  }

  /**
   * Given an ontology and a set of IRIs, filter the set of IRIs to only include those that exist in
   * the ontology. Always include terms in imports.
   *
   * @param ontology the ontology to check for IRIs
   * @param IRIs Set of IRIs to filter
   * @param allowEmpty boolean specifying if an empty set can be returned
   * @return Set of filtered IRIs
   */
  public static Set<IRI> filterExistingTerms(
      OWLOntology ontology, Set<IRI> IRIs, boolean allowEmpty) {
    return filterExistingTerms(ontology, IRIs, allowEmpty, Imports.INCLUDED);
  }

  /**
   * Given an ontology and a set of IRIs, filter the set of IRIs to only include those that exist in
   * the ontology. Maybe include terms in imports.
   *
   * @param ontology the ontology to check for IRIs
   * @param IRIs Set of IRIs to filter
   * @param allowEmpty boolean specifying if an empty set can be returned
   * @param imports Imports INCLUDED or EXCLUDED
   * @return Set of filtered IRIs
   */
  public static Set<IRI> filterExistingTerms(
      OWLOntology ontology, Set<IRI> IRIs, boolean allowEmpty, Imports imports) {
    Set<IRI> missingIRIs = new HashSet<>();
    for (IRI iri : IRIs) {
      if (!ontology.containsEntityInSignature(iri, imports)) {
        logger.warn("Ontology does not contain {}", iri.toQuotedString());
        missingIRIs.add(iri);
      }
    }

    if (missingIRIs.containsAll(IRIs) && !allowEmpty) {
      throw new IllegalArgumentException(emptyTermsError);
    }
    return IRIs;
  }

  /**
   * Get all named OWLObjects from an input ontology.
   *
   * @param ontology OWLOntology to retrieve objects from
   * @return set of objects
   */
  public static Set<OWLObject> getNamedObjects(OWLOntology ontology) {
    Set<OWLObject> objects = new HashSet<>();
    // TODO - include or exclude imports?
    for (OWLAxiom axiom : ontology.getAxioms(Imports.EXCLUDED)) {
      objects.addAll(getNamedObjects(axiom));
    }
    return objects;
  }

  /**
   * Get all named OWLObjects associated with an axiom. This is builds on getSignature() by
   * including annotation subjects, properties, and values.
   *
   * @param axiom The axiom to check
   * @return The set of objects
   */
  public static Set<OWLObject> getNamedObjects(OWLAxiom axiom) {
    Set<OWLObject> objects = new HashSet<>(axiom.getSignature());

    // Add annotations if the axiom is annotated
    if (axiom.isAnnotated()) {
      for (OWLAnnotation annotation : axiom.getAnnotations()) {
        objects.add(annotation.getProperty());
        if (annotation.getValue().isIRI()) {
          objects.add(annotation.getValue());
        }
      }
    }

    // The following are special cases
    // where there might be something anonymous that we want to include
    // in addition to the (named) entities in the signature.
    if (axiom instanceof OWLAnnotationAssertionAxiom) {
      OWLAnnotationAssertionAxiom a = (OWLAnnotationAssertionAxiom) axiom;
      objects.add(a.getSubject());
    }

    return objects;
  }

  /**
   * Given an OWLAxiom, return all the IRIs in the signature. This is an add-on to the getSignature
   * method to include OWLAnnotationAssertionAxioms.
   *
   * @param axiom OWLAxiom to get signature of
   * @return IRIs used in OWLAxiom
   */
  public static Set<IRI> getIRIsInSignature(OWLAxiom axiom) {
    Set<IRI> sigIRIs = new HashSet<>();
    if (axiom instanceof OWLAnnotationAssertionAxiom) {
      // Special handler for annotations to look at IRIs
      OWLAnnotationAssertionAxiom a = (OWLAnnotationAssertionAxiom) axiom;

      // Add the property IRI to signature
      sigIRIs.add(a.getProperty().getIRI());
      if (a.getSubject().isIRI()) {
        // If the subject is an IRI, add that too (it probably is)
        sigIRIs.add((IRI) a.getSubject());
      }

      if (a.getValue().isIRI()) {
        // Only add the value if its an IRI
        sigIRIs.add((IRI) a.getValue());
      }
    } else {
      // Just get the signature of all other types of axioms
      Set<OWLEntity> sig = axiom.getSignature();
      for (OWLEntity e : sig) {
        if (!e.isAnonymous()) {
          // Get the IRIs
          sigIRIs.add(e.getIRI());
        }
      }
    }
    return sigIRIs;
  }

  /**
   * Get all OWLObjects from an input ontology.
   *
   * @param ontology OWLOntology to retrieve objects from
   * @return set of objects
   */
  public static Set<OWLObject> getObjects(OWLOntology ontology) {
    Set<OWLObject> objects = new HashSet<>();
    // TODO - include or exclude imports?
    for (OWLAxiom axiom : ontology.getAxioms(Imports.EXCLUDED)) {
      objects.addAll(getObjects(axiom));
    }
    return objects;
  }

  /**
   * Get all OWLObjects associated with an axiom. This is builds on getSignature() by including
   * anonymous objects.
   *
   * @param axiom The axiom to check
   * @return The set of objects
   */
  public static Set<OWLObject> getObjects(OWLAxiom axiom) {
    Set<OWLObject> objects = new HashSet<>(axiom.getSignature());

    // The following are special cases
    // where there might be something anonymous that we want to include
    // in addition to the (named) entities in the signature.
    if (axiom instanceof OWLClassAssertionAxiom) {
      OWLClassAssertionAxiom a = (OWLClassAssertionAxiom) axiom;
      objects.add(a.getClassExpression());
    } else if (axiom instanceof OWLDisjointUnionAxiom) {
      OWLDisjointUnionAxiom a = (OWLDisjointUnionAxiom) axiom;
      objects.addAll(a.getClassExpressions());
    } else if (axiom instanceof OWLEquivalentDataPropertiesAxiom) {
      OWLEquivalentDataPropertiesAxiom a = (OWLEquivalentDataPropertiesAxiom) axiom;
      objects.addAll(a.asSubDataPropertyOfAxioms());
    } else if (axiom instanceof OWLNaryClassAxiom) {
      OWLNaryClassAxiom a = (OWLNaryClassAxiom) axiom;
      objects.addAll(a.getClassExpressions());
    } else if (axiom instanceof OWLSameIndividualAxiom) {
      OWLSameIndividualAxiom a = (OWLSameIndividualAxiom) axiom;
      objects.addAll(a.getAnonymousIndividuals());
    } else if (axiom instanceof OWLNaryIndividualAxiom) {
      OWLNaryIndividualAxiom a = (OWLNaryIndividualAxiom) axiom;
      objects.addAll(a.getIndividuals());
    } else if (axiom instanceof OWLNaryPropertyAxiom) {
      OWLNaryPropertyAxiom a = (OWLNaryPropertyAxiom) axiom;
      objects.addAll(a.getDataPropertiesInSignature());
      objects.addAll(a.getObjectPropertiesInSignature());
    } else if (axiom instanceof OWLNegativeObjectPropertyAssertionAxiom) {
      OWLNegativeObjectPropertyAssertionAxiom a = (OWLNegativeObjectPropertyAssertionAxiom) axiom;
      objects.addAll(a.getAnonymousIndividuals());
    } else if (axiom instanceof OWLObjectPropertyAssertionAxiom) {
      OWLObjectPropertyAssertionAxiom a = (OWLObjectPropertyAssertionAxiom) axiom;
      objects.addAll(a.getAnonymousIndividuals());
    } else if (axiom instanceof OWLObjectPropertyAxiom) {
      OWLObjectPropertyAxiom a = (OWLObjectPropertyAxiom) axiom;
      objects.addAll(a.getNestedClassExpressions());
    } else if (axiom instanceof OWLSubClassOfAxiom) {
      OWLSubClassOfAxiom a = (OWLSubClassOfAxiom) axiom;
      objects.add(a.getSuperClass());
      objects.add(a.getSubClass());
    } else if (axiom instanceof OWLUnaryPropertyAxiom) {
      OWLUnaryPropertyAxiom a = (OWLUnaryPropertyAxiom) axiom;
      objects.add(a.getProperty());
    } else if (axiom instanceof OWLHasKeyAxiom) {
      OWLHasKeyAxiom a = (OWLHasKeyAxiom) axiom;
      objects.add(a.getClassExpression());
      objects.addAll(a.getPropertyExpressions());
    }

    // TODO - cannot have anonymous components?
    // OWLNegativeDataPropertyAssertionAxiom
    // OWLDataPropertyAssertionAxiom
    // OWLSubDataPropertyOfAxiom
    // OWLSubAnnotationPropertyOfAxiom

    // TODO - covered by OWLObjectPropertyAxiom
    // OWLObjectPropertyDomainAxiom
    // OWLObjectPropertyRangeAxiom
    // OWLObjectPropertyCharacteristicAxiom
    // OWLReflexiveObjectPropertyAxiom
    // OWLSubObjectPropertyOfAxiom
    // OWLSymmetricObjectPropertyAxiom
    // OWLTransitiveObjectPropertyAxiom
    // OWLEquivalentObjectPropertiesAxiom
    // OWLInverseObjectPropertiesAxiom

    // TODO - only need OWLObjectProperty...
    // OWLPropertyAssertionAxiom<P,O>
    // OWLPropertyAxiom
    // OWLPropertyDomainAxiom<P>
    // OWLPropertyRangeAxiom<P,R>
    // OWLSubPropertyAxiom<P>
    // OWLSubPropertyChainOfAxiom

    return objects;
  }

  /**
   * Given an ontology, an entity, and an empty set, fill the set with axioms representing any
   * anonymous superclasses in the line of ancestors.
   *
   * @param ontology the ontology to search
   * @param entity the entity to search ancestors of
   * @return set of OWLAxioms
   */
  public static Set<OWLAxiom> getAnonymousAncestorAxioms(OWLOntology ontology, OWLEntity entity) {
    Set<OWLAxiom> anons = new HashSet<>();
    OWLDataFactory dataFactory = ontology.getOWLOntologyManager().getOWLDataFactory();
    if (entity.isOWLClass()) {
      for (OWLClassExpression e : EntitySearcher.getSuperClasses(entity.asOWLClass(), ontology)) {
        if (e.isAnonymous()) {
          anons.add(dataFactory.getOWLSubClassOfAxiom(entity.asOWLClass(), e));
        } else {
          getAnonymousAncestorAxioms(ontology, dataFactory, e.asOWLClass(), anons);
        }
      }
    } else if (entity.isOWLObjectProperty()) {
      for (OWLObjectPropertyExpression e :
          EntitySearcher.getSuperProperties(entity.asOWLObjectProperty(), ontology)) {
        if (e.isAnonymous()) {
          anons.add(dataFactory.getOWLSubObjectPropertyOfAxiom(entity.asOWLObjectProperty(), e));
        } else {
          getAnonymousAncestorAxioms(ontology, dataFactory, e.asOWLObjectProperty(), anons);
        }
      }
    } else if (entity.isOWLDataProperty()) {
      for (OWLDataPropertyExpression e :
          EntitySearcher.getSuperProperties(entity.asOWLDataProperty(), ontology)) {
        if (e.isAnonymous()) {
          anons.add(dataFactory.getOWLSubDataPropertyOfAxiom(entity.asOWLDataProperty(), e));
        } else {
          getAnonymousAncestorAxioms(ontology, dataFactory, e.asOWLDataProperty(), anons);
        }
      }
    }
    return anons;
  }

  /**
   * Given an ontology, a data factory, a class, and an empty set, fill the set with axioms
   * containing any anonymous classes referenced in the ancestor of the class.
   *
   * @param ontology the ontology to search
   * @param dataFactory the data factory to create axioms
   * @param cls the class to search ancestors of
   * @param anons set of OWLAxioms to fill
   */
  private static void getAnonymousAncestorAxioms(
      OWLOntology ontology, OWLDataFactory dataFactory, OWLClass cls, Set<OWLAxiom> anons) {
    getAnonymousEquivalentAxioms(ontology, dataFactory, cls, anons);
    for (OWLClassExpression e : EntitySearcher.getSuperClasses(cls, ontology)) {
      if (e.isAnonymous()) {
        anons.add(dataFactory.getOWLSubClassOfAxiom(cls, e));
      } else {
        getAnonymousAncestorAxioms(ontology, dataFactory, e.asOWLClass(), anons);
      }
    }
  }

  /**
   * Given an ontology, a data factory, an object property, and an empty set, fill the set with
   * axioms containing any anonymous classes referenced in the ancestor of the class.
   *
   * @param ontology the ontology to search
   * @param dataFactory the data factory to create axioms
   * @param property the object property to search ancestors of
   * @param anons set of OWLAxioms to fill
   */
  private static void getAnonymousAncestorAxioms(
      OWLOntology ontology,
      OWLDataFactory dataFactory,
      OWLObjectProperty property,
      Set<OWLAxiom> anons) {
    getAnonymousEquivalentAxioms(ontology, dataFactory, property, anons);
    for (OWLObjectPropertyExpression e : EntitySearcher.getSuperProperties(property, ontology)) {
      if (e.isAnonymous()) {
        anons.add(dataFactory.getOWLSubObjectPropertyOfAxiom(property, e));
      } else {
        getAnonymousAncestorAxioms(ontology, dataFactory, e.asOWLObjectProperty(), anons);
      }
    }
  }

  /**
   * Given an ontology, a data factory, a data property, and an empty set, fill the set with axioms
   * containing any anonymous classes referenced in the ancestor of the class.
   *
   * @param ontology the ontology to search
   * @param dataFactory the data factory to create axioms
   * @param property the data property to search ancestors of
   * @param anons set of OWLAxioms to fill
   */
  private static void getAnonymousAncestorAxioms(
      OWLOntology ontology,
      OWLDataFactory dataFactory,
      OWLDataProperty property,
      Set<OWLAxiom> anons) {
    getAnonymousEquivalentAxioms(ontology, dataFactory, property, anons);
    for (OWLDataPropertyExpression e : EntitySearcher.getSuperProperties(property, ontology)) {
      if (e.isAnonymous()) {
        anons.add(dataFactory.getOWLSubDataPropertyOfAxiom(property, e));
      } else {
        getAnonymousAncestorAxioms(ontology, dataFactory, e.asOWLDataProperty(), anons);
      }
    }
  }

  /**
   * Given an ontology and an entity, return a set of axioms containing any anonymous entities
   * referenced in the descendants of the entity. Includes supers and equivalents.
   *
   * @param ontology the ontology to search
   * @param entity the entity to search descendants of
   * @return set of OWLAxioms
   */
  public static Set<OWLAxiom> getAnonymousDescendantAxioms(OWLOntology ontology, OWLEntity entity) {
    Set<OWLAxiom> anons = new HashSet<>();
    OWLDataFactory dataFactory = ontology.getOWLOntologyManager().getOWLDataFactory();
    if (entity.isOWLClass()) {
      for (OWLClassExpression e : EntitySearcher.getSubClasses(entity.asOWLClass(), ontology)) {
        getAnonymousDescendantAxioms(ontology, dataFactory, e.asOWLClass(), anons);
      }
    } else if (entity.isOWLObjectProperty()) {
      for (OWLObjectPropertyExpression e :
          EntitySearcher.getSubProperties(entity.asOWLObjectProperty(), ontology)) {
        getAnonymousDescendantAxioms(ontology, dataFactory, e.asOWLObjectProperty(), anons);
      }
    } else if (entity.isOWLDataProperty()) {
      for (OWLDataPropertyExpression e :
          EntitySearcher.getSubProperties(entity.asOWLDataProperty(), ontology)) {
        getAnonymousDescendantAxioms(ontology, dataFactory, e.asOWLDataProperty(), anons);
      }
    }
    return anons;
  }

  /**
   * Given an ontology, a data factory, a class, and an empty set, fill the set with axioms
   * containing any anonymous classes referenced in the descendants of the entity. Also includes the
   * entity itself.
   *
   * @param ontology the ontology to search
   * @param dataFactory a data factory to create axioms
   * @param cls the class to search descendants of
   * @param anons set of OWLAxioms to fill
   */
  private static void getAnonymousDescendantAxioms(
      OWLOntology ontology, OWLDataFactory dataFactory, OWLClass cls, Set<OWLAxiom> anons) {
    getAnonymousEquivalentAxioms(ontology, dataFactory, cls, anons);
    for (OWLClassExpression e : EntitySearcher.getSubClasses(cls, ontology)) {
      OWLClass subclass = e.asOWLClass();
      for (OWLClassExpression se : EntitySearcher.getSuperClasses(subclass, ontology)) {
        if (se.isAnonymous()) {
          anons.add(dataFactory.getOWLSubClassOfAxiom(subclass, se));
        }
      }
      getAnonymousDescendantAxioms(ontology, dataFactory, subclass, anons);
    }
  }

  /**
   * Given an ontology, a data factory, an object property, and an empty set, fill the set with
   * axioms containing any anonymous classes referenced in the descendants of the entity. Also
   * includes the entity itself.
   *
   * @param ontology the ontology to search
   * @param dataFactory a data factory to create axioms
   * @param property the object property to search descendants of
   * @param anons set of OWLAxioms to fill
   */
  private static void getAnonymousDescendantAxioms(
      OWLOntology ontology,
      OWLDataFactory dataFactory,
      OWLObjectProperty property,
      Set<OWLAxiom> anons) {
    getAnonymousEquivalentAxioms(ontology, dataFactory, property, anons);
    for (OWLObjectPropertyExpression e : EntitySearcher.getSubProperties(property, ontology)) {
      OWLObjectProperty subproperty = e.asOWLObjectProperty();
      for (OWLObjectPropertyExpression se :
          EntitySearcher.getSuperProperties(subproperty, ontology)) {
        if (se.isAnonymous()) {
          anons.add(dataFactory.getOWLSubObjectPropertyOfAxiom(subproperty, se));
        }
      }
      getAnonymousDescendantAxioms(ontology, dataFactory, subproperty, anons);
    }
  }

  /**
   * Given an ontology, a data factory, a data property, and an empty set, fill the set with axioms
   * containing any anonymous classes referenced in the descendants of the entity. Also includes the
   * entity itself.
   *
   * @param ontology the ontology to search
   * @param dataFactory a data factory to create axioms
   * @param property the data property to search descendants of
   * @param anons set of OWLAxioms to fill
   */
  private static void getAnonymousDescendantAxioms(
      OWLOntology ontology,
      OWLDataFactory dataFactory,
      OWLDataProperty property,
      Set<OWLAxiom> anons) {
    getAnonymousEquivalentAxioms(ontology, dataFactory, property, anons);
    for (OWLDataPropertyExpression e : EntitySearcher.getSubProperties(property, ontology)) {
      OWLDataProperty subproperty = e.asOWLDataProperty();
      for (OWLDataPropertyExpression se :
          EntitySearcher.getSuperProperties(subproperty, ontology)) {
        if (se.isAnonymous()) {
          anons.add(dataFactory.getOWLSubDataPropertyOfAxiom(subproperty, se));
        }
      }
      getAnonymousDescendantAxioms(ontology, dataFactory, subproperty, anons);
    }
  }

  /**
   * Given an ontology and an entity, return a set of axioms containing any anonymous entities
   * referenced in equivalent entities.
   *
   * @param ontology the ontology to search
   * @param entity the entity to search equivalents of
   * @return set of OWLAxioms
   */
  public static Set<OWLAxiom> getAnonymousEquivalentAxioms(OWLOntology ontology, OWLEntity entity) {
    Set<OWLAxiom> anons = new HashSet<>();
    OWLDataFactory dataFactory = ontology.getOWLOntologyManager().getOWLDataFactory();
    if (entity.isOWLClass()) {
      getAnonymousEquivalentAxioms(ontology, dataFactory, entity.asOWLClass(), anons);
    } else if (entity.isOWLObjectProperty()) {
      getAnonymousEquivalentAxioms(ontology, dataFactory, entity.asOWLObjectProperty(), anons);
    } else if (entity.isOWLDataProperty()) {
      getAnonymousEquivalentAxioms(ontology, dataFactory, entity.asOWLDataProperty(), anons);
    }
    return anons;
  }

  /**
   * Given an ontology, a data factory, a class, and an empty set, fill the set with any axioms
   * containing any anonymous classes referenced in equivalent classes.
   *
   * @param ontology the ontology to search
   * @param dataFactory a data factory to create axioms
   * @param cls the class to search equivalents of
   * @param anons set of OWLAxioms to fill
   */
  private static void getAnonymousEquivalentAxioms(
      OWLOntology ontology, OWLDataFactory dataFactory, OWLClass cls, Set<OWLAxiom> anons) {
    for (OWLClassExpression e : EntitySearcher.getEquivalentClasses(cls, ontology)) {
      if (e.isAnonymous()) {
        anons.add(dataFactory.getOWLEquivalentClassesAxiom(cls, e));
      } else if (e.asOWLClass() != cls) {
        getAnonymousEquivalentAxioms(ontology, dataFactory, e.asOWLClass(), anons);
      }
    }
  }

  /**
   * Given an ontology, a data factory, an object property, and an empty set, fill the set with any
   * axioms containing any anonymous properties referenced in equivalent properties.
   *
   * @param ontology the ontology to search
   * @param dataFactory a data factory to create axioms
   * @param property the object property to search equivalents of
   * @param anons set of OWLAxioms to fill
   */
  private static void getAnonymousEquivalentAxioms(
      OWLOntology ontology,
      OWLDataFactory dataFactory,
      OWLObjectProperty property,
      Set<OWLAxiom> anons) {
    for (OWLObjectPropertyExpression e :
        EntitySearcher.getEquivalentProperties(property, ontology)) {
      if (e.isAnonymous()) {
        anons.add(dataFactory.getOWLEquivalentObjectPropertiesAxiom(property, e));
      } else if (e.asOWLObjectProperty() != property) {
        getAnonymousEquivalentAxioms(ontology, dataFactory, e.asOWLObjectProperty(), anons);
      }
    }
  }

  /**
   * Given an ontology, a data factory, a data property, and an empty set, fill the set with any
   * axioms containing any anonymous properties referenced in equivalent properties.
   *
   * @param ontology the ontology to search
   * @param dataFactory a data factory to create axioms
   * @param property the data property to search equivalents of
   * @param anons set of OWLAxioms to fill
   */
  private static void getAnonymousEquivalentAxioms(
      OWLOntology ontology,
      OWLDataFactory dataFactory,
      OWLDataProperty property,
      Set<OWLAxiom> anons) {
    for (OWLDataPropertyExpression e : EntitySearcher.getEquivalentProperties(property, ontology)) {
      if (e.isAnonymous()) {
        anons.add(dataFactory.getOWLEquivalentDataPropertiesAxiom(property, e));
      } else if (e.asOWLDataProperty() != property) {
        getAnonymousEquivalentAxioms(ontology, dataFactory, e.asOWLDataProperty(), anons);
      }
    }
  }

  /**
   * Given an ontology and an entity, return a set of axioms only corresponding to anonymous
   * parents.
   *
   * @param ontology the ontology to search
   * @param entity the entity to search parents of
   * @return set of OWLAxioms
   */
  public static Set<OWLAxiom> getAnonymousParentAxioms(OWLOntology ontology, OWLEntity entity) {
    Set<OWLAxiom> anons = new HashSet<>();
    OWLDataFactory dataFactory = ontology.getOWLOntologyManager().getOWLDataFactory();
    if (entity.isOWLClass()) {
      for (OWLClassExpression e : EntitySearcher.getSuperClasses(entity.asOWLClass(), ontology)) {
        if (e.isAnonymous()) {
          anons.add(dataFactory.getOWLSubClassOfAxiom(entity.asOWLClass(), e));
        }
      }
    } else if (entity.isOWLObjectProperty()) {
      for (OWLObjectPropertyExpression e :
          EntitySearcher.getSuperProperties(entity.asOWLObjectProperty(), ontology)) {
        if (e.isAnonymous()) {
          anons.add(dataFactory.getOWLSubObjectPropertyOfAxiom(entity.asOWLObjectProperty(), e));
        }
      }
    } else if (entity.isOWLDataProperty()) {
      for (OWLDataPropertyExpression e :
          EntitySearcher.getSuperProperties(entity.asOWLDataProperty(), ontology)) {
        if (e.isAnonymous()) {
          anons.add(dataFactory.getOWLSubDataPropertyOfAxiom(entity.asOWLDataProperty(), e));
        }
      }
    }
    return anons;
  }

  /**
   * Given an ontology, and an optional set of annotation properties, return a set of annotation
   * assertion axioms for those properties, for all subjects.
   *
   * @param ontology the ontology to search (including imports closure)
   * @param property an annotation property
   * @return a filtered set of annotation assertion axioms
   */
  public static Set<OWLAnnotationAssertionAxiom> getAnnotationAxioms(
      OWLOntology ontology, OWLAnnotationProperty property) {
    Set<OWLAnnotationProperty> properties = new HashSet<>();
    properties.add(property);
    return getAnnotationAxioms(ontology, properties, null);
  }

  /**
   * Given an ontology, an optional set of annotation properties, and an optional set of subject,
   * return a set of annotation assertion axioms for those subjects and those properties.
   *
   * @param ontology the ontology to search (including imports closure)
   * @param property an annotation property
   * @param subject an annotation subject IRIs
   * @return a filtered set of annotation assertion axioms
   */
  public static Set<OWLAnnotationAssertionAxiom> getAnnotationAxioms(
      OWLOntology ontology, OWLAnnotationProperty property, IRI subject) {
    Set<OWLAnnotationProperty> properties = new HashSet<>();
    properties.add(property);
    Set<IRI> subjects = null;
    if (subject != null) {
      subjects = new HashSet<>();
      subjects.add(subject);
    }
    return getAnnotationAxioms(ontology, properties, subjects);
  }

  /**
   * Given an ontology, an optional set of annotation properties, and an optional set of subject,
   * return a set of annotation assertion axioms for those subjects and those properties.
   *
   * @param ontology the ontology to search (including imports closure)
   * @param properties a set of annotation properties, or null if all properties should be included
   * @param subjects a set of annotation subject IRIs, or null if all subjects should be included
   * @return a filtered set of annotation assertion axioms
   */
  public static Set<OWLAnnotationAssertionAxiom> getAnnotationAxioms(
      OWLOntology ontology, Set<OWLAnnotationProperty> properties, Set<IRI> subjects) {
    Set<OWLAnnotationAssertionAxiom> results = new HashSet<>();

    for (OWLAxiom axiom : ontology.getAxioms()) {
      if (!(axiom instanceof OWLAnnotationAssertionAxiom)) {
        continue;
      }
      OWLAnnotationAssertionAxiom aaa = (OWLAnnotationAssertionAxiom) axiom;
      if (properties != null && !properties.contains(aaa.getProperty())) {
        continue;
      }
      OWLAnnotationSubject subject = aaa.getSubject();
      if (subjects == null) {
        results.add(aaa);
      } else if (subject instanceof IRI && subjects.contains(subject)) {
        results.add(aaa);
      }
    }
    return results;
  }

  /**
   * Given an ontology, an optional set of annotation properties, and an optional set of subject,
   * return the alphanumerically first annotation value string for those subjects and those
   * properties.
   *
   * @param ontology the ontology to search (including imports closure)
   * @param property an annotation property
   * @param subject an annotation subject IRIs
   * @return the first annotation string
   */
  public static String getAnnotationString(
      OWLOntology ontology, OWLAnnotationProperty property, IRI subject) {
    Set<OWLAnnotationProperty> properties = new HashSet<>();
    properties.add(property);
    Set<IRI> subjects = new HashSet<>();
    subjects.add(subject);
    return getAnnotationString(ontology, properties, subjects);
  }

  /**
   * Given an ontology, an optional set of annotation properties, and an optional set of subject,
   * return the alphanumerically first annotation value string for those subjects and those
   * properties.
   *
   * @param ontology the ontology to search (including imports closure)
   * @param properties a set of annotation properties, or null if all properties should be included
   * @param subjects a set of annotation subject IRIs, or null if all subjects should be included
   * @return the first annotation string
   */
  public static String getAnnotationString(
      OWLOntology ontology, Set<OWLAnnotationProperty> properties, Set<IRI> subjects) {
    Set<String> valueSet = getAnnotationStrings(ontology, properties, subjects);
    List<String> valueList = new ArrayList<>(valueSet);
    Collections.sort(valueList);
    String value = null;
    if (valueList.size() > 0) {
      value = valueList.get(0);
    }
    return value;
  }

  /**
   * Given an ontology, an optional set of annotation properties, and an optional set of subject,
   * return a set of strings for those subjects and those properties.
   *
   * @param ontology the ontology to search (including imports closure)
   * @param property an annotation property
   * @param subject an annotation subject IRIs
   * @return a filtered set of annotation strings
   */
  public static Set<String> getAnnotationStrings(
      OWLOntology ontology, OWLAnnotationProperty property, IRI subject) {
    Set<OWLAnnotationProperty> properties = new HashSet<>();
    properties.add(property);
    Set<IRI> subjects = new HashSet<>();
    subjects.add(subject);
    return getAnnotationStrings(ontology, properties, subjects);
  }

  /**
   * Given an ontology, an optional set of annotation properties, and an optional set of subject,
   * return a set of strings for those subjects and those properties.
   *
   * @param ontology the ontology to search (including imports closure)
   * @param properties a set of annotation properties, or null if all properties should be included
   * @param subjects a set of annotation subject IRIs, or null if all subjects should be included
   * @return a filtered set of annotation strings
   */
  public static Set<String> getAnnotationStrings(
      OWLOntology ontology, Set<OWLAnnotationProperty> properties, Set<IRI> subjects) {
    Set<String> results = new HashSet<>();
    Set<OWLAnnotationValue> values = getAnnotationValues(ontology, properties, subjects);
    for (OWLAnnotationValue value : values) {
      results.add(getValue(value));
    }
    return results;
  }

  /**
   * Given an ontology, an optional set of annotation properties, and an optional set of subject,
   * return a set of annotation values for those subjects and those properties.
   *
   * @param ontology the ontology to search (including imports closure)
   * @param property an annotation property
   * @param subject an annotation subject IRIs
   * @return a filtered set of annotation values
   */
  public static Set<OWLAnnotationValue> getAnnotationValues(
      OWLOntology ontology, OWLAnnotationProperty property, IRI subject) {
    Set<OWLAnnotationProperty> properties = new HashSet<>();
    properties.add(property);
    Set<IRI> subjects = new HashSet<>();
    subjects.add(subject);
    return getAnnotationValues(ontology, properties, subjects);
  }

  /**
   * Given an ontology, an optional set of annotation properties, and an optional set of subject,
   * return a set of annotation values for those subjects and those properties.
   *
   * @param ontology the ontology to search (including imports closure)
   * @param properties a set of annotation properties, or null if all properties should be included
   * @param subjects a set of annotation subject IRIs, or null if all subjects should be included
   * @return a filtered set of annotation values
   */
  public static Set<OWLAnnotationValue> getAnnotationValues(
      OWLOntology ontology, Set<OWLAnnotationProperty> properties, Set<IRI> subjects) {
    Set<OWLAnnotationValue> results = new HashSet<>();
    Set<OWLAnnotationAssertionAxiom> axioms = getAnnotationAxioms(ontology, properties, subjects);
    for (OWLAnnotationAssertionAxiom axiom : axioms) {
      results.add(axiom.getValue());
    }
    return results;
  }

  /**
   * Given an ontology and an IRI, get the OWLEntity object represented by the IRI. If it does not
   * exist in the ontology, throw an exception.
   *
   * @param ontology OWLOntology to retrieve from
   * @param iri IRI to get type of
   * @return OWLEntity
   * @throws Exception if IRI is not in ontology
   */
  public static OWLEntity getEntity(OWLOntology ontology, IRI iri) throws Exception {
    return getEntity(ontology, iri, false);
  }

  /**
   * Given an ontology, an IRI, and a boolean specifying if a null return value is allowed, get the
   * OWLEntity object represented by the IRI.
   *
   * @param ontology OWLOntology to retrieve from
   * @param iri IRI to get type of
   * @param allowNull if true, do not throw exception if IRI does not exist in ontology
   * @return OWLEntity
   * @throws Exception if allowNull is false and IRI is not in ontology
   */
  public static OWLEntity getEntity(OWLOntology ontology, IRI iri, boolean allowNull)
      throws Exception {
    // Get the OWLEntity for the IRI
    Set<OWLEntity> owlEntities = ontology.getEntitiesInSignature(iri);
    // Check that there is exactly one entity, throw exception if not
    if (owlEntities.size() == 0) {
      if (allowNull) {
        logger.warn("{} does not exist.", iri.toQuotedString());
        return null;
      } else {
        throw new Exception(String.format(missingEntityError, iri.toString()));
      }
    } else if (owlEntities.size() > 1) {
      throw new Exception(String.format(multipleEntitiesError, iri.toString()));
    }
    return owlEntities.iterator().next();
  }

  /**
   * Given an ontology and a set of term IRIs, return a set of entities for those IRIs. The input
   * ontology is not changed.
   *
   * @param ontology the ontology to search
   * @param iris the IRIs of the entities to find
   * @return a set of OWLEntities with the given IRIs
   */
  public static Set<OWLEntity> getEntities(OWLOntology ontology, Set<IRI> iris) {
    Set<OWLEntity> entities = new HashSet<>();
    if (iris == null) {
      return entities;
    }
    for (IRI iri : iris) {
      OWLEntity entity;
      try {
        entity = getEntity(ontology, iri, true);
      } catch (Exception e) {
        // This block shouldn't get hit, but just in case, skip this entity
        entity = null;
      }
      if (entity != null) {
        // Do not add null entries
        entities.add(entity);
      }
    }
    return entities;
  }

  /**
   * Given an ontology, return a set of all the entities in its signature.
   *
   * @param ontology the ontology to search
   * @return a set of all entities in the ontology
   */
  public static Set<OWLEntity> getEntities(OWLOntology ontology) {
    Set<OWLOntology> ontologies = new HashSet<>();
    ontologies.add(ontology);
    ReferencedEntitySetProvider resp = new ReferencedEntitySetProvider(ontologies);
    return resp.getEntities();
  }

  /**
   * Given an ontology, return a set of IRIs for all the entities in its signature.
   *
   * @param ontology the ontology to search
   * @return a set of IRIs for all entities in the ontology
   */
  public static Set<IRI> getIRIs(OWLOntology ontology) {
    Set<IRI> iris = new HashSet<>();
    for (OWLEntity entity : getEntities(ontology)) {
      iris.add(entity.getIRI());
    }
    return iris;
  }

  /**
   * Generates a function that returns a label string for any named object in the ontology
   *
   * @param ontology to use
   * @param useIriAsDefault if true then label-less classes will return IRI
   * @return function mapping object to label
   */
  public static Function<OWLNamedObject, String> getLabelFunction(
      OWLOntology ontology, boolean useIriAsDefault) {
    Map<IRI, String> labelMap = new HashMap<>();
    for (OWLAnnotationAssertionAxiom ax : ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION)) {
      if (ax.getProperty().isLabel()
          && ax.getSubject() instanceof IRI
          && ax.getValue() instanceof OWLLiteral) {
        OWLLiteral lit = ax.getValue().asLiteral().orNull();
        if (lit == null) {
          continue;
        }
        labelMap.put((IRI) ax.getSubject(), lit.getLiteral());
      }
    }
    return (obj) -> {
      String label;
      if (labelMap.containsKey(obj.getIRI())) {
        label = labelMap.get(obj.getIRI());
      } else if (useIriAsDefault) {
        label = obj.getIRI().toString();
      } else {
        label = null;
      }

      return label;
    };
  }

  /**
   * Given an ontology, return a map from rdfs:label to IRIs. Includes labels asserted in for all
   * imported ontologies. Duplicates overwrite existing with a warning.
   *
   * @param ontology the ontology to use
   * @return a map from label strings to IRIs
   */
  public static Map<String, IRI> getLabelIRIs(OWLOntology ontology) {
    Map<String, IRI> results = new HashMap<>();
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    OWLAnnotationProperty rdfsLabel = manager.getOWLDataFactory().getRDFSLabel();
    Set<OWLAnnotationAssertionAxiom> axioms = getAnnotationAxioms(ontology, rdfsLabel);
    for (OWLAnnotationAssertionAxiom axiom : axioms) {
      String value = getValue(axiom);
      if (value == null) {
        continue;
      }
      OWLAnnotationSubject subject = axiom.getSubject();
      if (!(subject instanceof IRI)) {
        continue;
      }
      if (results.containsKey(value)) {
        logger.warn("Duplicate rdfs:label \"" + value + "\" for subject " + subject);
      }
      results.put(value, (IRI) subject);
    }
    return results;
  }

  /**
   * Given an ontology, return a map from IRIs to rdfs:labels. Includes labels asserted in for all
   * imported ontologies. If there are multiple labels, use the alphanumerically first.
   *
   * @param ontology the ontology to use
   * @return a map from IRIs to label strings
   */
  public static Map<IRI, String> getLabels(OWLOntology ontology) {
    logger.info("Fetching labels for " + ontology.getOntologyID());
    Map<IRI, String> results = new HashMap<>();
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    OWLAnnotationProperty rdfsLabel = manager.getOWLDataFactory().getRDFSLabel();
    Set<OWLOntology> ontologies = new HashSet<>();
    ontologies.add(ontology);
    ReferencedEntitySetProvider resp = new ReferencedEntitySetProvider(ontologies);
    logger.info("iterating through entities...");
    for (OWLEntity entity : resp.getEntities()) {
      String value = getAnnotationString(ontology, rdfsLabel, entity.getIRI());
      if (value != null) {
        results.put(entity.getIRI(), value);
      }
    }
    logger.info("Results: " + results.size());
    return results;
  }

  /**
   * Given an annotation value, return its datatype, or null.
   *
   * @param value the value to check
   * @return the datatype, or null if the value has none
   */
  public static OWLDatatype getType(OWLAnnotationValue value) {
    if (value instanceof OWLLiteral) {
      return ((OWLLiteral) value).getDatatype();
    }
    return null;
  }

  /**
   * Given an annotation value, return the IRI of its datatype, or null.
   *
   * @param value the value to check
   * @return the IRI of the datatype, or null if the value has none
   */
  public static IRI getTypeIRI(OWLAnnotationValue value) {
    OWLDatatype datatype = getType(value);
    if (datatype == null) {
      return null;
    } else {
      return datatype.getIRI();
    }
  }

  /**
   * Given an OWLAnnotationValue, return its value as a string.
   *
   * @param value the OWLAnnotationValue to get the string value of
   * @return the string value
   */
  public static String getValue(OWLAnnotationValue value) {
    String result = null;
    if (value instanceof OWLLiteral) {
      result = ((OWLLiteral) value).getLiteral();
    }
    return result;
  }

  /**
   * Given an OWLAnnotation, return its value as a string.
   *
   * @param annotation the OWLAnnotation to get the string value of
   * @return the string value
   */
  public static String getValue(OWLAnnotation annotation) {
    return getValue(annotation.getValue());
  }

  /**
   * Given an OWLAnnotationAssertionAxiom, return its value as a string.
   *
   * @param axiom the OWLAnnotationAssertionAxiom to get the string value of
   * @return the string value
   */
  public static String getValue(OWLAnnotationAssertionAxiom axiom) {
    return getValue(axiom.getValue());
  }

  /**
   * Given a set of OWLAnnotations, return the first string value as determined by natural string
   * sorting.
   *
   * @param annotations a set of OWLAnnotations to get the value of
   * @return the first string value
   */
  public static String getValue(Set<OWLAnnotation> annotations) {
    Set<String> valueSet = getValues(annotations);
    List<String> valueList = new ArrayList<>(valueSet);
    Collections.sort(valueList);
    String value = null;
    if (valueList.size() > 0) {
      value = valueList.get(0);
    }
    return value;
  }

  /**
   * Given a set of OWLAnnotations, return a set of their value strings.
   *
   * @param annotations a set of OWLAnnotations to get the value of
   * @return a set of the value strings
   */
  public static Set<String> getValues(Set<OWLAnnotation> annotations) {
    Set<String> results = new HashSet<>();
    for (OWLAnnotation annotation : annotations) {
      String value = getValue(annotation);
      if (value != null) {
        results.add(value);
      }
    }
    return results;
  }

  /**
   * Given an OWLAxiom and a set of OWLAxiom class, determine if the axiom's class is an extension
   * of at least one of the given set.
   *
   * @param axiom the OWLAxiom to check
   * @param axiomTypes classes of axioms desired
   * @return true if axiom is an instantiation of one of the classes in set
   */
  public static boolean extendsAxiomTypes(
      OWLAxiom axiom, Set<Class<? extends OWLAxiom>> axiomTypes) {
    for (Class<? extends OWLAxiom> axiomType : axiomTypes) {
      if (axiomType.isAssignableFrom(axiom.getClass())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Given an OWLAxiom class and a set of OWLAxiom classes, determine if the axiom class provided
   * extends at least one of the classes in the set.
   *
   * @param axiom the OWLAxiom class to check
   * @param axiomTypes classes of axioms desired
   * @return true if axiom class extends one of the classes in set
   */
  public static boolean extendsAxiomTypes(
      Class<? extends OWLAxiom> axiom, Set<Class<? extends OWLAxiom>> axiomTypes) {
    for (Class<? extends OWLAxiom> at : axiomTypes) {
      if (at.isAssignableFrom(axiom)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Given an ontology, remove the import declarations.
   *
   * @param ontology OWLOntology to remove imports from
   */
  public static void removeImports(OWLOntology ontology) {
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    for (OWLImportsDeclaration i : ontology.getImportsDeclarations()) {
      manager.applyChange(new RemoveImport(ontology, i));
    }
  }

  /**
   * Remove all annotations on this ontology. Just annotations on the ontology itself, not
   * annotations on its classes, etc.
   *
   * @param ontology the ontology to modify
   */
  public static void removeOntologyAnnotations(OWLOntology ontology) {
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    for (OWLAnnotation annotation : ontology.getAnnotations()) {
      RemoveOntologyAnnotation remove = new RemoveOntologyAnnotation(ontology, annotation);
      manager.applyChange(remove);
    }
  }

  /**
   * Render a Manchester string for an OWLObject.
   *
   * @param object OWLObject to render
   * @param provider ShortFormProvider to resolve entities
   * @return String rendering of OWLObject based on renderer type
   */
  public static String renderManchester(OWLObject object, ShortFormProvider provider) {
    return renderManchester(object, provider, RendererType.OBJECT_RENDERER);
  }

  /**
   * Render a Manchester string for an OWLObject. The RendererType will determine what Manchester
   * OWL Syntax renderer will be created.
   *
   * @param object OWLObject to render
   * @param provider ShortFormProvider to resolve entities
   * @param rt RendererType to use to render Manchester
   * @return String rendering of OWLObject based on renderer type
   */
  public static String renderManchester(
      OWLObject object, ShortFormProvider provider, RendererType rt) {
    ManchesterOWLSyntaxObjectRenderer renderer;
    StringWriter sw = new StringWriter();

    if (object instanceof OWLAnnotationValue) {
      // Just return the value of literal annotations, don't render
      OWLAnnotationValue v = (OWLAnnotationValue) object;
      if (v.isLiteral()) {
        OWLLiteral lit = v.asLiteral().orNull();
        if (lit != null) {
          return lit.getLiteral();
        }
      }
    }

    if (provider instanceof QuotedAnnotationValueShortFormProvider && object.isAnonymous()) {
      // Handle quoting
      QuotedAnnotationValueShortFormProvider qavsfp =
          (QuotedAnnotationValueShortFormProvider) provider;
      qavsfp.toggleQuoting();
      if (rt == RendererType.OBJECT_HTML_RENDERER) {
        renderer = new ManchesterOWLSyntaxObjectHTMLRenderer(sw, qavsfp);
      } else {
        // Default renderer
        renderer = new ManchesterOWLSyntaxObjectRenderer(sw, qavsfp);
      }
      object.accept(renderer);
      qavsfp.toggleQuoting();
    } else {
      if (rt == RendererType.OBJECT_HTML_RENDERER) {
        renderer = new ManchesterOWLSyntaxObjectHTMLRenderer(sw, provider);
      } else {
        // Default renderer
        renderer = new ManchesterOWLSyntaxObjectRenderer(sw, provider);
      }
      object.accept(renderer);
    }
    return sw.toString().replace("\n", "").replaceAll(" +", " ");
  }

  /**
   * Set the ontology IRI and version IRI using strings.
   *
   * @param ontology the ontology to change
   * @param ontologyIRIString the ontology IRI string, or null for no change
   * @param versionIRIString the version IRI string, or null for no change
   */
  public static void setOntologyIRI(
      OWLOntology ontology, String ontologyIRIString, String versionIRIString) {
    IRI ontologyIRI = null;
    if (ontologyIRIString != null) {
      ontologyIRI = IRI.create(ontologyIRIString);
    }

    IRI versionIRI = null;
    if (versionIRIString != null) {
      versionIRI = IRI.create(versionIRIString);
    }

    setOntologyIRI(ontology, ontologyIRI, versionIRI);
  }

  /**
   * Set the ontology IRI and version IRI.
   *
   * @param ontology the ontology to change
   * @param ontologyIRI the new ontology IRI, or null for no change
   * @param versionIRI the new version IRI, or null for no change
   */
  public static void setOntologyIRI(OWLOntology ontology, IRI ontologyIRI, IRI versionIRI) {
    OWLOntologyID currentID = ontology.getOntologyID();

    // Get rid of optionals when changing to OWLAPI 5
    Optional<IRI> ont;
    Optional<IRI> version;

    if (ontologyIRI == null && versionIRI == null) {
      // don't change anything
      return;
    } else if (ontologyIRI == null) {
      ont = currentID.getOntologyIRI();
      version = Optional.of(versionIRI);
    } else if (versionIRI == null) {
      version = currentID.getVersionIRI();
      ont = Optional.of(ontologyIRI);
    } else {
      ont = Optional.of(ontologyIRI);
      version = Optional.of(versionIRI);
    }

    OWLOntologyID newID = new OWLOntologyID(ont, version);
    SetOntologyID setID = new SetOntologyID(ontology, newID);
    ontology.getOWLOntologyManager().applyChange(setID);
  }

  /**
   * Given an ontology, remove any dangling entities.
   *
   * @param ontology the ontology to trim
   */
  public static void trimOntology(OWLOntology ontology) {
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    Set<OWLObject> objects = new HashSet<>();
    for (OWLAxiom axiom : ontology.getAxioms()) {
      objects.addAll(getObjects(axiom));
    }
    Set<OWLObject> trimObjects = new HashSet<>();
    for (OWLObject object : objects) {
      if (object instanceof OWLEntity) {
        OWLEntity entity = (OWLEntity) object;
        if (InvalidReferenceChecker.isDangling(ontology, entity)) {
          logger.debug("Trimming %s", entity.getIRI());
          trimObjects.add(object);
        }
      }
    }
    Set<Class<? extends OWLAxiom>> type = new HashSet<>();
    type.add(OWLAxiom.class);
    Set<OWLAxiom> axioms = RelatedObjectsHelper.getPartialAxioms(ontology, trimObjects, type);
    manager.removeAxioms(ontology, axioms);
  }

  /**
   * Removes all of the axiom annotations for the given annotation properties.
   *
   * @param ontology OWLOntology to remove axiom annotations
   * @param properties List of annotation property IRIs to remove related axiom annotations.
   */
  public static void removeAxiomAnnotations(OWLOntology ontology, List<IRI> properties) {
    Map<IRI, String> annotationsToDrop = new HashMap<>();
    properties.forEach(iri -> annotationsToDrop.put(iri, null));
    removeAxiomAnnotations(ontology, annotationsToDrop);
  }

  /**
   * Removes all of the axiom annotations for the given annotation properties.
   *
   * @param ontology OWLOntology to remove axiom annotations
   * @param annotationsToDrop Map with keys being IRIs to drop and values to restrict what is
   *     deleted.
   */
  public static void removeAxiomAnnotations(
      OWLOntology ontology, Map<IRI, String> annotationsToDrop) {
    OWLDataFactory owlDataFactory = ontology.getOWLOntologyManager().getOWLDataFactory();

    for (IRI iri : annotationsToDrop.keySet()) {
      OWLAnnotationProperty property = owlDataFactory.getOWLAnnotationProperty(iri);
      String value = annotationsToDrop.get(iri);
      OntologyHelper.removeAxiomAnnotations(ontology, property, value);
    }
  }

  /**
   * Removes all of the axiom annotations for the given annotation property.
   *
   * @param ontology OWLOntology to remove axiom annotations
   * @param property Annotation property to remove related axiom annotations.
   */
  public static void removeAxiomAnnotations(OWLOntology ontology, OWLAnnotationProperty property) {
    removeAxiomAnnotations(ontology, property, null);
  }

  /**
   * Removes all of the axiom annotations for the given annotation property and an optional value
   * pattern The value pattern should follow
   * https://robot.obolibrary.org/remove#pattern-subset-selectors
   *
   * @param ontology OWLOntology to remove axiom annotations
   * @param property Annotation property to remove related axiom annotations.
   * @param value a value or value pattern the restrict what is deleted. Can be null.
   */
  public static void removeAxiomAnnotations(
      OWLOntology ontology, OWLAnnotationProperty property, String value) {
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    for (OWLAxiom axiom : ontology.getAxioms(Imports.EXCLUDED)) {
      Set<OWLAnnotation> annotationsToRemove = new HashSet<>();
      Set<OWLAnnotation> allAxiomAnnotations = axiom.getAnnotations();
      for (OWLAnnotation annotation : allAxiomAnnotations) {
        if (annotation.getProperty().equals(property)) {
          String annotationValue = getAnnotationValueAsStringOrNull(annotation);
          if (value == null) {
            // If there is no value set, we assume all annotations for the property should be
            // removed
            annotationsToRemove.add(annotation);
          } else if (value.startsWith("~")) {
            // If a value is set, and it starts with ~, we assume it is a regex pattern
            String patternString = value.substring(1).replace("'", "");
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(annotationValue);
            if (matcher.find()) {
              annotationsToRemove.add(annotation);
            }
          } else {
            // If a value is set, and it does not start with ~, we assume its an exact value
            String processed_value = value.replace("'", "");
            if (annotationValue.equals(processed_value)) {
              annotationsToRemove.add(annotation);
            } else {
              logger.warn(
                  "Annotation assertion value pattern must start with ~' to indicate a regex pattern"
                      + "or else correspond to the exact value. "
                      + "The value is: "
                      + value
                      + ". Other patterns are not supported.");
            }
          }
        }
      }
      if (!annotationsToRemove.isEmpty()) {
        Set<OWLAnnotation> axiomAnnotations = new HashSet<>(allAxiomAnnotations);
        axiomAnnotations.removeAll(annotationsToRemove);
        OWLAxiom cleanedAxiom =
            axiom.getAxiomWithoutAnnotations().getAnnotatedAxiom(axiomAnnotations);

        manager.removeAxiom(ontology, axiom);
        manager.addAxiom(ontology, cleanedAxiom);
      }
    }
  }

  private static String getAnnotationValueAsStringOrNull(OWLAnnotation annotation) {
    OWLAnnotationValue v = annotation.getValue();
    if (v.isIRI()) {
      return v.asIRI().get().toString();
    } else if (v.isLiteral()) {
      return v.asLiteral().get().getLiteral().toString();
    } else if (v.isIndividual()) {
      return v.asAnonymousIndividual().get().toString();
    } else {
      return null;
    }
  }

  /**
   * Removes all axiom annotations from the given ontology.
   *
   * @param ontology OWLOntology to remove axiom annotations
   */
  public static void removeAllAxiomAnnotations(OWLOntology ontology) {
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    for (OWLAxiom axiom : ontology.getAxioms(Imports.EXCLUDED)) {
      OWLAxiom cleanedAxiom = axiom.getAxiomWithoutAnnotations();
      manager.removeAxiom(ontology, axiom);
      manager.addAxiom(ontology, cleanedAxiom);
    }
  }

  /**
   * Removes supernumerary annotations on all entities in the given ontology.
   *
   * <p>This method iterates over all entities (excluding imported entities) of the given ontology;
   * if an entity is found to have more than one annotation for each of the indicated properties,
   * all annotations beyond the first one are removed.
   *
   * @param ontology OWLOntology from which to remove the supernumary annotations
   * @param properties set of annotation property IRIs to remove
   */
  public static void removeExtraAnnotations(OWLOntology ontology, Set<IRI> properties) {
    Set<OWLAnnotationAssertionAxiom> axiomsToRemove = new HashSet<>();
    for (OWLEntity ent : ontology.getSignature(Imports.EXCLUDED)) {
      IRI entity = ent.getIRI();
      Map<IRI, Set<OWLAnnotationAssertionAxiom>> annotsByProperty =
          getAnnotationAssertions(ontology, entity, properties);
      for (IRI property : properties) {
        Set<OWLAnnotationAssertionAxiom> axioms = annotsByProperty.get(property);
        if (axioms != null && axioms.size() > 1) {
          OWLAnnotationAssertionAxiom axiomToKeep = null;
          // If only one annotation is language-agnostic, we keep this one
          int axiomsWithoutLanguageTag = 0;
          for (OWLAnnotationAssertionAxiom ax : axioms) {
            OWLAnnotationValue value = ax.getValue();
            // Non-literal annotations count as language-agnostic
            if (!value.isLiteral() || !value.asLiteral().get().hasLang()) {
              axiomsWithoutLanguageTag += 1;
              axiomToKeep = ax;
            }
          }
          if (axiomsWithoutLanguageTag != 1) {
            // Can't use language tags to pick up the annotation to keep, so sort them and pick
            // whichever comes out first
            axiomToKeep = sortAxioms(axioms).get(0);
          }
          axioms.remove(axiomToKeep);
          axiomsToRemove.addAll(axioms);
          logger.warn(
              "Removing {} extra <{}> annotation(s) on <{}>", axioms.size(), property, entity);
        }
      }
    }
    if (!axiomsToRemove.isEmpty()) {
      ontology.getOWLOntologyManager().removeAxioms(ontology, axiomsToRemove);
    }
  }

  /**
   * Merges supernumerary annotations on all entities in the given ontology, using a single space
   * character between each annotation value.
   *
   * @param ontology OWLOntology in which to merge supernumerary annotations
   * @param properties set of annotation property IRIs to merge
   */
  public static void mergeExtraAnnotations(OWLOntology ontology, Set<IRI> properties) {
    mergeExtraAnnotations(ontology, properties, " ");
  }

  /**
   * Merges supernumerary annotations on all entities in the given ontology, using a custom
   * separator between each annotation value.
   *
   * <p>This method iterates over all entities (excluding imported entities) of the given ontology;
   * if an entity is found to have more than one annotation for each of the indicated properties,
   * they are merged into a single annotation.
   *
   * @param ontology OWLOntology in which to merge supernumerary annotations
   * @param properties set of annotation property IRIs to merge
   * @param separator Separator string to insert between each annotation value (may be {@code null})
   */
  public static void mergeExtraAnnotations(
      OWLOntology ontology, Set<IRI> properties, String separator) {
    Set<OWLAnnotationAssertionAxiom> axiomsToRemove = new HashSet<>();
    Set<OWLAnnotationAssertionAxiom> axiomsToAdd = new HashSet<>();
    OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
    for (OWLEntity ent : ontology.getSignature(Imports.EXCLUDED)) {
      IRI entity = ent.getIRI();
      Map<IRI, Set<OWLAnnotationAssertionAxiom>> annotsByProperty =
          getAnnotationAssertions(ontology, entity, properties);
      for (IRI property : properties) {
        Set<OWLAnnotationAssertionAxiom> axioms = annotsByProperty.get(property);
        if (axioms != null && axioms.size() > 1) {
          // Check that all annotations have literal values only
          int nonLiteralValues = 0;
          for (OWLAnnotationAssertionAxiom ax : axioms) {
            if (!ax.getValue().isLiteral()) {
              nonLiteralValues += 1;
            }
          }
          if (nonLiteralValues != 0) {
            logger.warn("Cannot merge non-literal <{}> annotations on <{}>", property, entity);
            continue;
          }
          // Build the merged annotation axiom
          StringBuilder sb = new StringBuilder();
          Set<OWLAnnotation> axiomAnnotations = new HashSet<>();
          boolean first = true;
          for (OWLAnnotationAssertionAxiom ax : sortAxioms(axioms)) {
            if (!first && separator != null) {
              sb.append(separator);
            } else {
              first = false;
            }
            sb.append(getValue(ax));
            axiomAnnotations.addAll(ax.getAnnotations());
          }
          axiomsToAdd.add(
              factory.getOWLAnnotationAssertionAxiom(
                  factory.getOWLAnnotationProperty(property),
                  entity,
                  factory.getOWLLiteral(sb.toString()),
                  axiomAnnotations));
          axiomsToRemove.addAll(axioms);
          logger.warn("Merging {} <{}> annotations on <{}>", axioms.size(), property, ent.getIRI());
        }
      }
    }

    if (!axiomsToRemove.isEmpty()) {
      ontology.getOWLOntologyManager().removeAxioms(ontology, axiomsToRemove);
      ontology.getOWLOntologyManager().addAxioms(ontology, axiomsToAdd);
    }
  }

  /**
   * Gets all annotation assertion axioms on a given entity that uses one of the indicated
   * annotation properties.
   *
   * @param ontology OWLOntology from which to get the annotation axioms
   * @param entity the subject for which to retrieve the annotations
   * @param properties the annotation properties to look for
   * @return a map associating property IRIs to annotation assertion axioms
   */
  private static Map<IRI, Set<OWLAnnotationAssertionAxiom>> getAnnotationAssertions(
      OWLOntology ontology, IRI entity, Set<IRI> properties) {
    Map<IRI, Set<OWLAnnotationAssertionAxiom>> annotsByProperty = new HashMap<>();
    for (OWLAnnotationAssertionAxiom ax : ontology.getAnnotationAssertionAxioms(entity)) {
      IRI property = ax.getProperty().getIRI();
      annotsByProperty.computeIfAbsent(property, k -> new HashSet<>()).add(ax);
    }
    return annotsByProperty;
  }

  /**
   * Turns a set of annotation axioms into an ordered list.
   *
   * @param axioms set of axioms to order
   * @return an ordered list
   */
  private static List<OWLAnnotationAssertionAxiom> sortAxioms(
      Set<OWLAnnotationAssertionAxiom> axioms) {
    List<OWLAnnotationAssertionAxiom> asList = new ArrayList<>(axioms);
    asList.sort((a, b) -> a.compareTo(b));
    return asList;
  }
}
