package org.obolibrary.robot.metrics;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.obolibrary.robot.IOHelper;
import org.obolibrary.robot.providers.CURIEShortFormProvider;
import org.semanticweb.owlapi.metrics.AbstractOWLMetric;
import org.semanticweb.owlapi.metrics.AverageAssertedNamedSuperclassCount;
import org.semanticweb.owlapi.metrics.GCICount;
import org.semanticweb.owlapi.metrics.HiddenGCICount;
import org.semanticweb.owlapi.metrics.MaximumNumberOfNamedSuperclasses;
import org.semanticweb.owlapi.metrics.NumberOfClassesWithMultipleInheritance;
import org.semanticweb.owlapi.metrics.ReferencedClassCount;
import org.semanticweb.owlapi.metrics.ReferencedDataPropertyCount;
import org.semanticweb.owlapi.metrics.ReferencedIndividualCount;
import org.semanticweb.owlapi.metrics.ReferencedObjectPropertyCount;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLObjectHasValue;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.OWLSubObjectPropertyOfAxiom;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.profiles.OWL2DLProfile;
import org.semanticweb.owlapi.profiles.OWL2ELProfile;
import org.semanticweb.owlapi.profiles.OWL2Profile;
import org.semanticweb.owlapi.profiles.OWL2QLProfile;
import org.semanticweb.owlapi.profiles.OWL2RLProfile;
import org.semanticweb.owlapi.profiles.OWLProfile;
import org.semanticweb.owlapi.profiles.OWLProfileReport;
import org.semanticweb.owlapi.profiles.OWLProfileViolation;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.Construct;
import org.semanticweb.owlapi.util.DLExpressivityChecker;
import org.semanticweb.owlapi.util.Languages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.manchester.cs.owl.owlapi.OWLObjectOneOfImpl;

@SuppressWarnings("unused")
public class OntologyMetrics {

  private final OWLOntology ontology;
  private final OWLOntologyManager manager;
  private final List<String> owlProfileViolationsList = new ArrayList<>();
  private final Map<String, String> prefixmap = new HashMap<>();
  private final Map<String, String> prefixmapUsed = new HashMap<>();
  private final List<OWLProfileViolation> owlProfileViolations = new ArrayList<>();
  private CURIEShortFormProvider curieProvider;
  private static final Logger LOGGER = LoggerFactory.getLogger(OntologyMetrics.class);

  public OntologyMetrics(OWLOntology ontology) {
    this.ontology = ontology;
    this.manager = ontology.getOWLOntologyManager();
    try {
      this.curieProvider = new CURIEShortFormProvider(new IOHelper().getPrefixes());
      this.curieProvider.getSortedPrefixMap().forEach(e -> prefixmap.put(e.getKey(), e.getValue()));
    } catch (IOException e) {
      LOGGER.warn("Curie Provider could not be instantiated, trying without.. ", e);
    }
  }

  public OntologyMetrics(OWLOntology ontology, CURIEShortFormProvider curieShortFormProvider) {
    this.ontology = ontology;
    this.manager = ontology.getOWLOntologyManager();
    this.curieProvider = curieShortFormProvider;
    this.curieProvider.getSortedPrefixMap().forEach(e -> prefixmap.put(e.getKey(), e.getValue()));
  }

  /** @return the essential metrics */
  public MeasureResult getEssentialMetrics() {
    return getEssentialMetrics("");
  }

  /** @return the extended metrics */
  public MeasureResult getExtendedMetrics() {
    return getExtendedMetrics("");
  }

  /** @return all metrics */
  public MeasureResult getAllMetrics() {
    return getAllMetrics("");
  }

  /**
   * @param prefix a prefix to prepend to all the metrics, for example "bfo_"
   * @return essential metrics
   */
  public MeasureResult getEssentialMetrics(String prefix) {
    MeasureResult csvData = new MeasureResult();
    csvData.put(prefix + MetricsLabels.ONTOLOGY_ID, getOntologyId());
    csvData.put(prefix + MetricsLabels.ONTOLOGY_VERSION_ID, getOntologyVersionId());
    /*
    Essential entity metrics
    */
    csvData.put(prefix + MetricsLabels.SIGNATURE_SIZE_INCL, getSignatureSize(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.SIGNATURE_SIZE, getSignatureSize(Imports.EXCLUDED));
    csvData.put(prefix + MetricsLabels.CLASS_COUNT_INCL, getClassCount(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.CLASS_COUNT, getClassCount(Imports.EXCLUDED));
    csvData.put(
        prefix + MetricsLabels.OBJPROPERTY_COUNT_INCL, getObjectPropertyCount(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.OBJPROPERTY_COUNT, getObjectPropertyCount(Imports.EXCLUDED));
    csvData.put(
        prefix + MetricsLabels.INDIVIDUAL_COUNT_INCL, getIndividualsCount(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.INDIVIDUAL_COUNT, getIndividualsCount(Imports.EXCLUDED));
    csvData.put(
        prefix + MetricsLabels.DATAPROPERTY_COUNT_INCL, getDataPropertyCount(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.DATAPROPERTY_COUNT, getDataPropertyCount(Imports.EXCLUDED));
    csvData.put(
        prefix + MetricsLabels.ANNOTATION_PROP_COUNT, getAnnotationPropertyCount(Imports.EXCLUDED));
    csvData.put(
        prefix + MetricsLabels.ANNOTATION_PROP_COUNT_INCL,
        getAnnotationPropertyCount(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.DATATYPE_COUNT_INCL, getDatatypesCount(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.DATATYPE_COUNT, getDatatypesCount(Imports.EXCLUDED));

    csvData.put(prefix + MetricsLabels.ONTOLOGY_ANNOTATIONS_COUNT, getAnnotationsCount());

    /*
    Essential axiom counts
     */

    csvData.put(prefix + MetricsLabels.LOGICAL_AXIOM_COUNT, getLogicalAxiomCount(Imports.EXCLUDED));
    csvData.put(
        prefix + MetricsLabels.LOGICAL_AXIOM_COUNT_INCL, getLogicalAxiomCount(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.AXIOM_COUNT, getAxiomCount(Imports.EXCLUDED));
    csvData.put(prefix + MetricsLabels.AXIOM_COUNT_INCL, getAxiomCount(Imports.INCLUDED));

    csvData.put(prefix + MetricsLabels.TBOX_SIZE, getTBoxSize(Imports.EXCLUDED));
    csvData.put(prefix + MetricsLabels.TBOX_SIZE_INCL, getTBoxSize(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.TBOXRBOX_SIZE, getTBoxRboxSize(Imports.EXCLUDED));
    csvData.put(prefix + MetricsLabels.TBOXRBOX_SIZE_INCL, getTBoxRboxSize(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.RULE_CT, getNumberOfRules(Imports.EXCLUDED));
    csvData.put(prefix + MetricsLabels.RULE_CT_INCL, getNumberOfRules(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.RBOX_SIZE, getRBoxSize(Imports.EXCLUDED));
    csvData.put(prefix + MetricsLabels.RBOX_SIZE_INCL, getRBoxSize(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.ABOX_SIZE, getABoxSize(Imports.EXCLUDED));
    csvData.put(prefix + MetricsLabels.ABOX_SIZE_INCL, getABoxSize(Imports.INCLUDED));

    /*
    Essential expressivity metrics
     */
    csvData.put(prefix + MetricsLabels.BOOL_PROFILE_OWL2, isOWL2Profile());
    csvData.put(prefix + MetricsLabels.BOOL_PROFILE_OWL2_DL, isOWL2DLProfile());
    csvData.put(prefix + MetricsLabels.BOOL_PROFILE_OWL2_EL, isOWL2ELProfile());
    csvData.put(prefix + MetricsLabels.BOOL_PROFILE_OWL2_QL, isOWL2QLProfile());
    csvData.put(prefix + MetricsLabels.BOOL_PROFILE_OWL2_RL, isOWL2RLProfile());
    csvData.put(prefix + MetricsLabels.BOOL_PROFILE_RDFS, isRDFS());
    csvData.putMap(prefix + MetricsLabels.VIOLATION_PROFILE_OWL2_DL, getOwlprofileviolations());

    csvData.putSet(prefix + MetricsLabels.VALID_IMPORTS, getValidImports(false));
    csvData.putSet(prefix + MetricsLabels.VALID_IMPORTS_INCL, getValidImports(true));

    return csvData;
  }

  /**
   * @param prefix a prefix to prepend to all the metrics, for example "bfo_"
   * @return extended metrics
   */
  public MeasureResult getExtendedMetrics(String prefix) {
    MeasureResult csvData = new MeasureResult();
    MeasureResult essentialData = getEssentialMetrics(prefix);
    csvData.importMetrics(essentialData);

    /*
    Extended entity metrics
     */
    csvData.put(
        prefix + MetricsLabels.DATATYPE_BUILTIN_COUNT_INCL,
        getDatatypesBuiltinCount(Imports.INCLUDED));
    csvData.put(
        prefix + MetricsLabels.DATATYPE_BUILTIN_COUNT, getDatatypesBuiltinCount(Imports.EXCLUDED));
    csvData.put(
        prefix + MetricsLabels.DATATYPE_NOTBUILTIN_COUNT_INCL,
        getDatatypesNotBuiltinCount(Imports.INCLUDED));
    csvData.put(
        prefix + MetricsLabels.DATATYPE_NOTBUILTIN_COUNT,
        getDatatypesNotBuiltinCount(Imports.EXCLUDED));

    /*
    Extended expressivity metrics
     */
    csvData.put(prefix + MetricsLabels.EXPRESSIVITY, getExpressivity(false));
    csvData.put(prefix + MetricsLabels.EXPRESSIVITY_INCL, getExpressivity(true));
    csvData.putSet(prefix + MetricsLabels.CONSTRUCTS_INCL, getConstructs(true));
    csvData.putSet(prefix + MetricsLabels.CONSTRUCTS, getConstructs(false));
    csvData.putSet(prefix + MetricsLabels.AXIOM_TYPES, getAxiomTypes(Imports.EXCLUDED));
    csvData.putSet(prefix + MetricsLabels.AXIOM_TYPES_INCL, getAxiomTypes(Imports.INCLUDED));
    csvData.putMap(prefix + MetricsLabels.AXIOMTYPE_COUNT, getAxiomTypeCounts(Imports.EXCLUDED));
    csvData.putMap(
        prefix + MetricsLabels.AXIOMTYPE_COUNT_INCL, getAxiomTypeCounts(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.SYNTAX, getSyntax());
    csvData.putMap(
        prefix + MetricsLabels.CLASSEXPRESSION_COUNT,
        getOWLClassExpressionCounts(Imports.EXCLUDED));
    csvData.putMap(
        prefix + MetricsLabels.CLASSEXPRESSION_COUNT_INCL,
        getOWLClassExpressionCounts(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.SYNTAX, getSyntax());

    /*
    Entity usage
     */

    csvData.putMap(prefix + MetricsLabels.NS_USE_AXIOMS, getAxiomUsageMap(Imports.EXCLUDED));
    csvData.putMap(prefix + MetricsLabels.NS_USE_AXIOMS_INCL, getAxiomUsageMap(Imports.INCLUDED));
    csvData.putMap(prefix + MetricsLabels.NS_USE_SIGNATURE, getEntityUsageMap(Imports.EXCLUDED));
    csvData.putMap(
        prefix + MetricsLabels.NS_USE_SIGNATURE_INCL, getEntityUsageMap(Imports.INCLUDED));
    csvData.putMap(prefix + MetricsLabels.CURIE_MAP, prefixmapUsed);
    return csvData;
  }

  /**
   * @param reasoner the reasoner to use to compute the metric; it is expected that this reasoner
   *     has the same ontology loaded as the one used by this class.
   * @return simple reasoner metrics
   */
  public MeasureResult getSimpleReasonerMetrics(OWLReasoner reasoner) {
    return getSimpleReasonerMetrics("", reasoner);
  }

  /**
   * @param reasoner the reasoner to use to compute the metric; it is expected that this reasoner
   *     has the same ontology loaded as the one used by this class.
   * @return extended reasoner metrics
   */
  public MeasureResult getExtendedReasonerMetrics(OWLReasoner reasoner) {
    return getExtendedReasonerMetrics("", reasoner);
  }

  /**
   * @param prefix a prefix to prepend to all the metrics, for example "bfo_"
   * @param reasoner the reasoner to use to compute the metric; it is expected that this reasoner
   *     has the same ontology loaded as the one used by this class.
   * @return simple reasoner metrics
   */
  public MeasureResult getSimpleReasonerMetrics(String prefix, OWLReasoner reasoner) {
    MeasureResult csvData = new MeasureResult();
    csvData.put(prefix + MetricsLabels.CONSISTENT, reasoner.isConsistent());
    if (reasoner.isConsistent()) {
      csvData.put(
          prefix + MetricsLabels.UNSATISFIABLECLASSES_COUNT,
          reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom().size());
    } else {
      csvData.put(prefix + MetricsLabels.UNSATISFIABLECLASSES_COUNT, -1);
    }
    return csvData;
  }

  /**
   * @param prefix a prefix to prepend to all the metrics, for example "bfo_"
   * @param reasoner the reasoner to use to compute the metric; it is expected that this reasoner
   *     has the same ontology loaded as the one used by this class.
   * @return extended reasoner metrics
   */
  public MeasureResult getExtendedReasonerMetrics(String prefix, OWLReasoner reasoner) {
    MeasureResult csvData = new MeasureResult();
    MeasureResult extendedData = getSimpleReasonerMetrics(prefix, reasoner);
    csvData.importMetrics(extendedData);
    return csvData;
  }

  /**
   * @param prefix a prefix to prepend to all the metrics, for example "bfo_"
   * @return all metrics
   */
  public MeasureResult getAllMetrics(String prefix) {
    MeasureResult csvData = new MeasureResult();
    MeasureResult extendedData = getExtendedMetrics(prefix);
    csvData.importMetrics(extendedData);

    csvData.put(prefix + MetricsLabels.MAX_AXIOMLENGTH, getLongestAxiomLength(Imports.EXCLUDED));
    csvData.put(
        prefix + MetricsLabels.MAX_AXIOMLENGTH_INCL, getLongestAxiomLength(Imports.INCLUDED));

    csvData.put(
        prefix + MetricsLabels.AVG_ASSERT_N_SUBCLASS_INCL,
        getAverageAssertedNamedSubclasses(Imports.INCLUDED));
    csvData.put(
        prefix + MetricsLabels.AVG_ASSERT_N_SUBCLASS,
        getAverageAssertedNamedSubclasses(Imports.EXCLUDED));
    csvData.put(
        prefix + MetricsLabels.AVG_ASSERT_N_SUPERCLASS_INCL,
        getAverageAssertedNamedSuperclasses(true));
    csvData.put(
        prefix + MetricsLabels.AVG_ASSERT_N_SUPERCLASS, getAverageAssertedNamedSuperclasses(false));
    csvData.put(
        prefix + MetricsLabels.AVG_INSTANCE_PER_CLASS_INCL,
        getAverageInstancesPerClass(Imports.INCLUDED));
    csvData.put(
        prefix + MetricsLabels.AVG_INSTANCE_PER_CLASS,
        getAverageInstancesPerClass(Imports.EXCLUDED));

    csvData.put(
        prefix + MetricsLabels.CLASS_SGL_SUBCLASS_COUNT_INCL,
        getClassesWithSingleSubclassCount(Imports.INCLUDED));
    csvData.put(
        prefix + MetricsLabels.CLASS_SGL_SUBCLASS_COUNT,
        getClassesWithSingleSubclassCount(Imports.EXCLUDED));

    csvData.put(
        prefix + MetricsLabels.AXIOM_COMPLEXRHS_COUNT_INCL,
        getAxiomsWithComplexRHS(Imports.INCLUDED));
    csvData.put(
        prefix + MetricsLabels.AXIOM_COMPLEXRHS_COUNT, getAxiomsWithComplexRHS(Imports.EXCLUDED));

    csvData.put(
        prefix + MetricsLabels.TBOX_CONTAINS_NOMINALS_INCL,
        isTBoxContainsNominals(Imports.INCLUDED));
    csvData.put(
        prefix + MetricsLabels.TBOX_CONTAINS_NOMINALS, isTBoxContainsNominals(Imports.EXCLUDED));
    csvData.put(
        prefix + MetricsLabels.ABOX_CONTAINS_NOMINALS_INCL,
        isABoxContainsNominals(Imports.INCLUDED));
    csvData.put(
        prefix + MetricsLabels.ABOX_CONTAINS_NOMINALS, isABoxContainsNominals(Imports.EXCLUDED));

    csvData.put(prefix + MetricsLabels.GCI_COUNT_INCL, getGCICount(true));
    csvData.put(prefix + MetricsLabels.GCI_COUNT, getGCICount(false));
    csvData.put(prefix + MetricsLabels.GCI_HIDDEN_COUNT_INCL, getHiddenGCICount(true));
    csvData.put(prefix + MetricsLabels.GCI_HIDDEN_COUNT, getHiddenGCICount(false));

    csvData.put(
        prefix + MetricsLabels.MAX_NUM_NAMED_SUPERCLASS_INCL,
        getMaximumNumberOfNamedSuperclasses(true));
    csvData.put(
        prefix + MetricsLabels.MAX_NUM_NAMED_SUPERCLASS,
        getMaximumNumberOfNamedSuperclasses(false));
    csvData.put(
        prefix + MetricsLabels.MULTI_INHERITANCE_COUNT_INCL, getMultipleInheritanceCount(true));
    csvData.put(prefix + MetricsLabels.MULTI_INHERITANCE_COUNT, getMultipleInheritanceCount(false));

    /*    csvData.put(prefix + MetricsLabels.REF_CLASS_COUNT_INCL, getReferencedClassCount(true));
        csvData.put(prefix + MetricsLabels.REF_CLASS_COUNT, getReferencedClassCount(false));
        csvData.put(
            prefix + MetricsLabels.REF_DATAPROP_COUNT_INCL, getReferencedDataPropertyCount(true));
        csvData.put(prefix + MetricsLabels.REF_DATAPROP_COUNT, getReferencedDataPropertyCount(false));
        csvData.put(prefix + MetricsLabels.REF_INDIV_COUNT_INCL, getReferencedIndividualCount(true));
        csvData.put(prefix + MetricsLabels.REF_INDIV_COUNT, getReferencedIndividualCount(false));
        csvData.put(
            prefix + MetricsLabels.REF_OBJPROP_COUNT_INCL, getReferencedObjectPropertyCount(true));
        csvData.put(prefix + MetricsLabels.REF_OBJPROP_COUNT, getReferencedObjectPropertyCount(false));
    */
    csvData.put(
        prefix + MetricsLabels.UNDECLARED_ENTITY_COUNT_INCL,
        getUndeclaredEntitiesCount(Imports.INCLUDED));
    csvData.put(
        prefix + MetricsLabels.UNDECLARED_ENTITY_COUNT,
        getUndeclaredEntitiesCount(Imports.EXCLUDED));

    csvData.put(prefix + MetricsLabels.TAUTOLOGYCOUNT, getTautologyCount(Imports.EXCLUDED));
    csvData.put(prefix + MetricsLabels.TAUTOLOGYCOUNT_INCL, getTautologyCount(Imports.INCLUDED));
    csvData.put(prefix + MetricsLabels.CYCLE, surelyContainsCycle(Imports.EXCLUDED));
    csvData.put(prefix + MetricsLabels.CYCLE_INCL, surelyContainsCycle(Imports.INCLUDED));

    if (surelyContainsCycle(Imports.EXCLUDED)) {
      csvData.put(prefix + MetricsLabels.CYCLE, true);
    } else {
      csvData.put(prefix + MetricsLabels.CYCLE, false);
    }

    csvData.putMap(
        prefix + MetricsLabels.DATATYPE_AXIOMCOUNT,
        getDatatypesWithAxiomOccurrenceCount(Imports.EXCLUDED));
    csvData.putMap(
        prefix + MetricsLabels.DATATYPE_AXIOMCOUNT_INCL,
        getDatatypesWithAxiomOccurrenceCount(Imports.INCLUDED));
    csvData.putSet(prefix + MetricsLabels.DATATYPES, getBuiltInDatatypes(Imports.EXCLUDED));
    csvData.putSet(prefix + MetricsLabels.DATATYPES_INCL, getBuiltInDatatypes(Imports.INCLUDED));
    csvData.putSet(
        prefix + MetricsLabels.DATATYPES_NOT_BUILT_IN, getNotBuiltInDatatypes(Imports.EXCLUDED));
    csvData.putSet(
        prefix + MetricsLabels.DATATYPES_NOT_BUILT_IN_INCL,
        getNotBuiltInDatatypes(Imports.INCLUDED));

    csvData.put(
        prefix + MetricsLabels.MOST_FRQUENTLY_USED_CONCEPT_INCL,
        getMostFrequentlyUsedClassInLogicalAxioms(Imports.INCLUDED));
    csvData.put(
        prefix + MetricsLabels.MOST_FRQUENTLY_USED_CONCEPT,
        getMostFrequentlyUsedClassInLogicalAxioms(Imports.EXCLUDED));

    return csvData;
  }

  // ENTITIES

  private static int getLengthOfAxiom(OWLAxiom axiom) {
    int length = 0;

    String axiomstring = axiom.getAxiomWithoutAnnotations().toString();
    for (OWLEntity e : axiom.getSignature()) {
      length += getNumberOfOccurences(axiomstring, e.toString());
    }
    return length;
  }

  private static int getNumberOfOccurences(String haystack, String needle) {
    int length = 0;
    Pattern p = Pattern.compile(needle);
    Matcher m = p.matcher(haystack);
    while (m.find()) {
      length++;
    }
    return length;
  }

  private int getSignatureSize(Imports includeImportsClosure) {
    return getOntology().getSignature(includeImportsClosure).size();
  }

  private int getUndeclaredEntitiesCount(Imports includeImportsClosure) {
    int undeclared = 0;
    for (OWLEntity entity : getOntology().getSignature(includeImportsClosure)) {
      if (!getOntology().isDeclared(entity)) {
        undeclared++;
      }
    }
    return undeclared;
  }

  private boolean isTBoxContainsNominals(Imports b) {
    for (OWLAxiom ax : MetricsUtils.getTBoxAxioms(getLogicalAxioms(b, true))) {
      for (OWLClassExpression cl : ax.getNestedClassExpressions()) {
        if (cl instanceof OWLObjectOneOfImpl) {
          // System.out.println(ax);
          return true;
        } else if (cl instanceof OWLObjectHasValue) {
          // System.out.println(ax);
          return true;
        }
      }
    }
    return false;
  }

  private boolean isABoxContainsNominals(Imports b) {
    for (OWLAxiom ax : MetricsUtils.getABoxAxioms(getLogicalAxioms(b, true))) {
      for (OWLClassExpression cl : ax.getNestedClassExpressions()) {
        if (cl instanceof OWLObjectOneOfImpl) {
          return true;
        } else if (cl instanceof OWLObjectHasValue) {
          return true;
        }
      }
    }
    return false;
  }

  private int getClassCount(Imports includeImportsClosure) {
    return getOntology().getClassesInSignature(includeImportsClosure).size();
  }

  private int getObjectPropertyCount(Imports includeImportsClosure) {
    return getOntology().getObjectPropertiesInSignature(includeImportsClosure).size();
  }

  private int getDataPropertyCount(Imports included) {
    return getOntology().getDataPropertiesInSignature(included).size();
  }

  private int getDatatypesCount(Imports included) {
    return getOntology().getDatatypesInSignature(included).size();
  }

  private Map<String, Integer> getDatatypesWithAxiomOccurrenceCount(Imports includeImportsClosure) {
    Map<String, Integer> map = new HashMap<>();
    Set<OWLAxiom> axioms = getAxioms(includeImportsClosure);
    for (OWLAxiom axiom : axioms) {
      Set<OWLDatatype> dtypes = axiom.getDatatypesInSignature();
      for (OWLDatatype datatype : dtypes) {
        String dtname = datatype.toString();
        if (datatype.isBuiltIn()) {
          dtname = datatype.getBuiltInDatatype().toString();
        }
        if (map.containsKey(dtname)) {
          Integer itemp = map.get(dtname);
          itemp++;
          map.put(dtname, itemp);
        } else {
          map.put(dtname, 1);
        }
      }
    }
    return map;
  }

  private void countKeyUpInMap(String key, Map<String, Integer> map) {
    if (!map.containsKey(key)) {
      map.put(key, 0);
    }
    map.put(key, map.get(key) + 1);
  }

  private Map<String, Integer> getEntityUsageMap(Imports includeImportsClosure) {
    Map<String, Integer> map = new HashMap<>();
    for (OWLEntity e : getOntology().getSignature(includeImportsClosure)) {
      String iri_pre = extractPrefixForEntityOrOtherIfUnknown(e);
      countKeyUpInMap(iri_pre, map);
    }
    return map;
  }

  private String extractPrefixForEntityOrOtherIfUnknown(OWLEntity e) {
    Optional<CURIEShortFormProvider> sfpo = getCurieProvider();
    if (sfpo.isPresent()) {
      CURIEShortFormProvider sfp = sfpo.get();
      String shortform = sfp.getShortForm(e);
      if (shortform.contains(":") && !shortform.equals(e.getIRI().toString())) {
        String prefix = shortform.split(":")[0];
        prefixmapUsed.put(prefix, prefixmap.get(prefix));
        return prefix;
      }
    }

    LOGGER.info("Entity " + e.getIRI() + " does not have a known prefix.");
    return "prefix_unknown";
  }

  private Map<String, Integer> getAxiomUsageMap(Imports includeImportsClosure) {
    Map<String, Integer> map = new HashMap<>();
    Set<OWLAxiom> axioms = getAxioms(includeImportsClosure);
    for (OWLAxiom axiom : axioms) {
      Set<OWLEntity> entities = axiom.getSignature();
      for (OWLEntity e : entities) {
        String iri_pre = extractPrefixForEntityOrOtherIfUnknown(e);
        countKeyUpInMap(iri_pre, map);
      }
    }
    return map;
  }

  private Set<String> getBuiltInDatatypes(Imports included) {
    Set<String> set = new HashSet<>();
    Set<OWLDatatype> datatypes = getOntology().getDatatypesInSignature(included);
    for (OWLDatatype datatype : datatypes) {
      if (datatype.isBuiltIn()) {
        set.add(datatype.getBuiltInDatatype().toString());
      }
    }
    return set;
  }

  private Set<String> getNotBuiltInDatatypes(Imports included) {
    Set<String> set = new HashSet<>();
    Set<OWLDatatype> properties = getOntology().getDatatypesInSignature(included);
    for (OWLDatatype datatype : properties) {
      if (!datatype.isBuiltIn()) {
        set.add(datatype.toString());
      }
    }
    return set;
  }

  private int getAnnotationPropertyCount(Imports imports) {
    return getOntology().getAnnotationPropertiesInSignature(imports).size();
  }

  // AXIOMS

  private int getAnnotationsCount() {
    return getOntology().getAnnotations().size();
  }

  private int getIndividualsCount(Imports included) {
    return getOntology().getIndividualsInSignature(included).size();
  }

  private int getNumberOfRules(Imports includeImportsClosure) {
    int ct = 0;
    Set<OWLAxiom> logicalaxiom = getLogicalAxioms(includeImportsClosure, false);
    for (OWLAxiom ax : logicalaxiom) {
      if (ax.isLogicalAxiom()) {
        if (ax.getAxiomType().toString().equals("Rule")) {
          ct++;
        }
      }
    }
    return ct;
  }

  private int getAxiomsWithComplexRHS(Imports included) {
    // complex: RHS does not only contain nested conjuctions / atomic
    // classnames
    int ct = 0;
    Set<OWLAxiom> logicalaxiom = getLogicalAxioms(included, false);
    for (OWLAxiom ax : logicalaxiom) {
      if (ax instanceof OWLSubClassOfAxiom) {
        OWLSubClassOfAxiom scax = (OWLSubClassOfAxiom) ax;
        OWLClassExpression RHS = scax.getSuperClass();
        if (isComplex(RHS)) {
          ct++;
        }
      } else if (ax instanceof OWLEquivalentClassesAxiom) {
        OWLEquivalentClassesAxiom scax = (OWLEquivalentClassesAxiom) ax;
        for (OWLClassExpression ex : scax.getClassExpressions()) {
          if (isComplex(ex)) {
            ct++;
            break;
          }
        }
      }
    }
    return ct;
  }

  private double getAVGSizeOfRHS(Imports included) {
    // complex: RHS does not only contain nested conjuctions / atomic
    // classnames
    double ct = 0;
    double ct_complex = 0;
    Set<OWLAxiom> logicalaxiom = getLogicalAxioms(included, false);
    for (OWLAxiom ax : logicalaxiom) {
      if (ax instanceof OWLSubClassOfAxiom) {
        OWLSubClassOfAxiom scax = (OWLSubClassOfAxiom) ax;
        OWLClassExpression RHS = scax.getSuperClass();
        if (isComplex(RHS)) {
          // System.out.println(RHS);
          ct_complex++;
          for (OWLClassExpression ex : RHS.getNestedClassExpressions()) {
            if (isComplex(ex)) {
              ct++;
            }
          }
        }
      } else if (ax instanceof OWLEquivalentClassesAxiom) {
        OWLEquivalentClassesAxiom scax = (OWLEquivalentClassesAxiom) ax;
        for (OWLClassExpression oper : scax.getClassExpressions()) {
          if (isComplex(oper)) {
            ct_complex++;
            for (OWLClassExpression ex : oper.getNestedClassExpressions()) {
              if (isComplex(ex)) {
                ct++;
              }
            }
          }
        }
      }
    }
    return (ct / ct_complex);
  }

  private boolean isComplex(OWLClassExpression ex) {
    for (OWLClassExpression exnested : ex.getNestedClassExpressions()) {
      if (!exnested.isClassExpressionLiteral()) {
        if (!(exnested instanceof OWLObjectIntersectionOf)) {
          return true;
        }
      }
    }
    return false;
  }

  private Set<OWLAxiom> getLogicalAxioms(Imports includeImportsClosure, boolean skiprules) {
    Set<AxiomType<?>> at = new HashSet<>();
    at.addAll(AxiomType.TBoxAxiomTypes);
    at.addAll(AxiomType.RBoxAxiomTypes);
    at.addAll(AxiomType.ABoxAxiomTypes);
    return MetricsUtils.getLogicalAxioms(getOntology(), includeImportsClosure, skiprules, at);
  }

  private Set<OWLAxiom> getAxioms(Imports included) {
    return new HashSet<>(getOntology().getAxioms(included));
  }

  private int getAxiomCount(Imports includeImportsClosure) {
    return getAxioms(includeImportsClosure).size();
  }

  private int getLogicalAxiomCount(Imports includeImportsClosure) {
    return getLogicalAxioms(includeImportsClosure, true).size();
  }

  private int getTBoxSize(Imports useImportsClosure) {
    return getOntology().getTBoxAxioms(useImportsClosure).size();
  }

  private int getTBoxRboxSize(Imports useImportsClosure) {
    int i = 0;
    Set<AxiomType<?>> axty = MetricsUtils.getTBoxAxiomTypes(true);
    for (AxiomType<?> at : axty) {
      i += getOntology().getAxioms(at, useImportsClosure).size();
    }
    return i;
  }

  private int getABoxSize(Imports useImportsClosure) {
    return getOntology().getABoxAxioms(useImportsClosure).size();
  }

  private int getRBoxSize(Imports useImportsClosure) {
    return getOntology().getRBoxAxioms(useImportsClosure).size();
  }

  private Map<String, Integer> getOWLClassExpressionCounts(Imports includeImportsClosure) {
    Map<String, Integer> map = new HashMap<>();
    Set<OWLAxiom> axioms = getAxioms(includeImportsClosure);

    for (OWLAxiom ax : axioms) {
      for (OWLClassExpression exp : ax.getNestedClassExpressions()) {
        String type = exp.getClassExpressionType().getName();
        if (map.containsKey(type)) {
          Integer i = map.get(type);
          map.put(type, (i + 1));
        } else {
          map.put(type, 1);
        }
      }
    }

    return map;
  }

  private Map<String, Integer> getAxiomTypeCounts(Imports includeImportsClosure) {
    Map<String, Integer> map = new HashMap<>();
    Set<OWLAxiom> axioms = getAxioms(includeImportsClosure);

    for (OWLAxiom ax : axioms) {
      String type = ax.getAxiomType().getName();
      if (map.containsKey(type)) {
        Integer i = map.get(type);
        map.put(type, (i + 1));
      } else {
        map.put(type, 1);
      }
    }

    return map;
  }

  private int getTautologyCount(Imports includeImports) {
    int tautologies = 0;
    for (OWLAxiom ax : getLogicalAxioms(includeImports, true)) {
      if (TautologyChecker.isTautology(ax)) {
        tautologies++;
      }
    }
    return tautologies;
  }

  private Set<AxiomType<?>> getAxiomTypes(Imports includeImportsClosure) {
    Set<AxiomType<?>> axtypes = new HashSet<>();
    for (OWLAxiom ax : getAxioms(includeImportsClosure)) {
      axtypes.add(ax.getAxiomType());
    }
    return axtypes;
  }

  private boolean isOWL2Profile() {
    OWLProfile profile = new OWL2Profile();
    return profile.checkOntology(getOntology()).isInProfile();
  }

  private boolean isOWL2ELProfile() {
    OWLProfile profile = new OWL2ELProfile();
    return profile.checkOntology(getOntology()).isInProfile();
  }

  private boolean isOWL2DLProfile() {
    OWLProfile profile = new OWL2DLProfile();
    OWLProfileReport report = profile.checkOntology(getOntology());
    for (OWLProfileViolation vio : report.getViolations()) {
      String s = vio.getClass().getSimpleName();
      owlProfileViolationsList.add(s);
      owlProfileViolations.add(vio);
    }
    return report.isInProfile();
  }

  private boolean isOWL2RLProfile() {
    OWLProfile profile = new OWL2RLProfile();
    return profile.checkOntology(getOntology()).isInProfile();
  }

  private boolean isOWL2QLProfile() {
    OWLProfile profile = new OWL2QLProfile();
    return profile.checkOntology(getOntology()).isInProfile();
  }

  // REFERENCED CLASSES AND PROPERTIES

  private boolean isRDFS() {
    // TODO: verify
    boolean isRDFS = true;

    for (OWLAxiom ax : getAxioms(Imports.INCLUDED)) {
      if (ax.isLogicalAxiom()) {
        if (ax.isOfType(AxiomType.SUBCLASS_OF)) {
          OWLSubClassOfAxiom subAx = (OWLSubClassOfAxiom) ax;
          if (subAx.getSubClass().isAnonymous() || subAx.getSuperClass().isAnonymous()) {
            isRDFS = false;
            // System.out.println("SubClassAx: " + ax);
            break;
          }
        } else if (ax.isOfType(AxiomType.SUB_OBJECT_PROPERTY)) {
          OWLSubObjectPropertyOfAxiom subProp = (OWLSubObjectPropertyOfAxiom) ax;
          if (subProp.getSubProperty().isAnonymous() || subProp.getSuperProperty().isAnonymous()) {
            isRDFS = false;
            // System.out.println("SupPropertyAx: " + ax);
            break;
          }
        } else //noinspection StatementWithEmptyBody
        if (ax.isOfType(AxiomType.OBJECT_PROPERTY_DOMAIN)
            || ax.isOfType(AxiomType.OBJECT_PROPERTY_RANGE)
            || ax.isOfType(AxiomType.DATA_PROPERTY_ASSERTION)
            || ax.isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION)
            || ax.isOfType(AxiomType.DATA_PROPERTY_DOMAIN)
            || ax.isOfType(AxiomType.DATA_PROPERTY_RANGE)) {
          // do nothing
          // System.out.println(ax.getAxiomType().getName()+" axiom: "
          // + ax);
        } else if (ax.isOfType(AxiomType.CLASS_ASSERTION)) {
          OWLClassAssertionAxiom assAx = (OWLClassAssertionAxiom) ax;
          if (assAx.getClassExpression().isAnonymous()) {
            isRDFS = false;
            // System.out.println("Class Assertion: " + ax);
            break;
          }
        } else {
          // System.out.println("Some other axiom: " + ax);
          isRDFS = false;
          break;
        }
      }
    }

    return isRDFS;
  }

  private Set<String> getValidImports(boolean direct) {
    Set<String> validImports = new HashSet<>();
    Set<OWLOntology> closure = new HashSet<>();

    if (!direct) {
      closure.addAll(getOntology().getImportsClosure());
    }
    closure.add(getOntology());

    for (OWLOntology o : closure) {
      for (OWLImportsDeclaration im : o.getImportsDeclarations()) {
        String iri = im.getIRI().toString();
        validImports.add(iri);
      }
    }
    return validImports;
  }

  private int getReferencedClassCount(boolean useImportsClosure) {
    // TODO verify this
    AbstractOWLMetric<Integer> metric = new ReferencedClassCount(getOntology());
    metric.setImportsClosureUsed(useImportsClosure);
    metric.setOntology(getOntology());
    return metric.getValue();
  }

  private int getReferencedDataPropertyCount(boolean useImportsClosure) {
    // TODO verify this
    AbstractOWLMetric<Integer> metric = new ReferencedDataPropertyCount(getOntology());
    metric.setImportsClosureUsed(useImportsClosure);
    metric.setOntology(getOntology());
    return metric.getValue();
  }

  // RANDOM METRICS

  private int getReferencedIndividualCount(boolean useImportsClosure) {
    // TODO verify this
    AbstractOWLMetric<Integer> metric = new ReferencedIndividualCount(getOntology());
    metric.setImportsClosureUsed(useImportsClosure);
    metric.setOntology(getOntology());
    return metric.getValue();
  }

  private int getReferencedObjectPropertyCount(boolean useImportsClosure) {
    // TODO verify this
    AbstractOWLMetric<Integer> metric = new ReferencedObjectPropertyCount(getOntology());
    metric.setImportsClosureUsed(useImportsClosure);
    metric.setOntology(getOntology());
    return metric.getValue();
  }

  private int getMultipleInheritanceCount(boolean useImportsClosure) {
    // TODO verify this
    AbstractOWLMetric<Integer> metric = new NumberOfClassesWithMultipleInheritance(getOntology());
    metric.setImportsClosureUsed(useImportsClosure);
    metric.setOntology(getOntology());
    return metric.getValue();
  }

  private int getMaximumNumberOfNamedSuperclasses(boolean useImportsClosure) {
    // TODO verify this
    AbstractOWLMetric<Integer> metric = new MaximumNumberOfNamedSuperclasses(getOntology());
    metric.setImportsClosureUsed(useImportsClosure);
    metric.setOntology(getOntology());
    return metric.getValue();
  }

  private int getGCICount(boolean useImportsClosure) {
    // TODO verify this
    AbstractOWLMetric<Integer> metric = new GCICount(getOntology());
    metric.setImportsClosureUsed(useImportsClosure);
    metric.setOntology(getOntology());
    return metric.getValue();
  }

  private int getHiddenGCICount(boolean useImportsClosure) {
    // TODO verify this
    AbstractOWLMetric<Integer> metric = new HiddenGCICount(getOntology());
    metric.setImportsClosureUsed(useImportsClosure);
    metric.setOntology(getOntology());
    return metric.getValue();
  }

  private double getAverageAssertedNamedSuperclasses(boolean useImportsClosure) {
    // TODO verify this
    AbstractOWLMetric<Double> metric = new AverageAssertedNamedSuperclassCount(getOntology());
    metric.setImportsClosureUsed(useImportsClosure);
    metric.setOntology(getOntology());
    return metric.getValue();
  }

  private double getAverageAssertedNamedSubclasses(Imports useImportsClosure) {
    int count = 0;
    Set<OWLClass> classes = getOntology().getClassesInSignature(useImportsClosure);
    for (OWLClass c : classes) {
      count += getOntology().getSubClassAxiomsForSuperClass(c).size();
    }
    return (((double) count) / ((double) classes.size()));
  }

  private int getClassesWithSingleSubclassCount(Imports useImportsClosure) {
    int count = 0;
    for (OWLClass c : getOntology().getClassesInSignature(useImportsClosure)) {
      if (getOntology().getSubClassAxiomsForSuperClass(c).size() == 1) {
        count++;
      }
    }
    return count;
  }

  private double getAverageInstancesPerClass(Imports useImportsClosure) {
    int count = 0;
    for (OWLClass c : getOntology().getClassesInSignature(useImportsClosure)) {
      count += getOntology().getClassAssertionAxioms(c).size();
    }
    return (((double) count)
        / ((double) getOntology().getClassesInSignature(useImportsClosure).size()));
  }

  private String getSyntax() {
    Object format = getManager().getOntologyFormat(getOntology());
    if (format != null) {
      return format.toString();
    }
    return "unknown";
  }

  private String getOntologyId() {
    OWLOntologyID ontologyID = getOntology().getOntologyID();
    if (ontologyID.isAnonymous()) {
      return "anonymousId";
    } else {
      return ontologyID.getOntologyIRI().or(IRI.create("no.iri")).toString();
    }
  }

  private String getOntologyVersionId() {
    OWLOntologyID ontologyID = getOntology().getOntologyID();
    if (ontologyID.isAnonymous()) {
      return "anonymousId";
    } else {
      return ontologyID.getVersionIRI().or(IRI.create("no.iri")).toString();
    }
  }

  private String getExpressivity(boolean included) {
    Set<OWLOntology> onts = new HashSet<>();
    if (included) {
      onts.addAll(getOntology().getImportsClosure());
    } else {
      onts.add(getOntology());
    }
    DLExpressivityChecker checker = new DLExpressivityChecker(onts);
    Collection<Languages> languages = checker.expressibleInLanguages();

    if (languages == null || languages.isEmpty()) {
      LOGGER.warn("No language found for this ontology.. ");
    } else {
      Languages[] array = languages.toArray(new Languages[languages.size()]);
      Arrays.sort(array);
      return array[0].toString();
    }

    return "";
  }

  // this is highly unpleasant and I wish we had a NoSQL DB or even just a
  // stupid XML file where we could throw it all in individually
  private Set<String> getConstructs(boolean includeImportsClosure) {
    Set<OWLOntology> onts = new HashSet<>();
    if (includeImportsClosure) {
      onts.addAll(getOntology().getImportsClosure());
    } else {
      onts.add(getOntology());
    }
    DLExpressivityChecker checker = new DLExpressivityChecker(onts);
    Set<String> constructs = new HashSet<>();
    for (Construct c : checker.getConstructs()) {
      constructs.add(c.name());
    }
    return constructs;
  }

  private boolean surelyContainsCycle(Imports includeImports) {
    return OntologyCycleDetector.containsCycle(getOntology(), includeImports);
  }

  /** @return Ontology used for metrics computation */
  public OWLOntology getOntology() {
    return this.ontology;
  }

  private Optional<CURIEShortFormProvider> getCurieProvider() {
    if (this.curieProvider != null) {
      return Optional.of(this.curieProvider);
    }
    return Optional.empty();
  }

  private OWLOntologyManager getManager() {
    return this.manager;
  }

  private Map<String, Integer> getOwlprofileviolations() {
    Map<String, Integer> map = new HashMap<>();
    Set<String> uniqueviolations = new HashSet<>(owlProfileViolationsList);
    for (String vio : uniqueviolations) {
      map.put(vio, Collections.frequency(owlProfileViolationsList, vio));
    }
    return map;
  }

  private String getMostFrequentlyUsedClassInLogicalAxioms(Imports includeImportsClosure) {
    Map<String, Integer> classCountMap = new HashMap<>();
    for (OWLAxiom eachAxiom : getLogicalAxioms(includeImportsClosure, true)) {
      if (!(eachAxiom instanceof OWLSubClassOfAxiom)) {
        continue;
      }
      OWLSubClassOfAxiom sax = (OWLSubClassOfAxiom) eachAxiom;
      String saxrhs = sax.getSuperClass().toString();
      for (OWLClass eachClass : eachAxiom.getClassesInSignature()) {
        int frequency = getNumberOfOccurences(saxrhs, eachClass.toString());
        String class_sf = eachClass.getIRI().toString();
        if (classCountMap.containsKey(eachClass.toString())) {
          Integer currentClassCount = classCountMap.get(eachClass.toString());
          classCountMap.put(class_sf, currentClassCount + frequency);
        } else {
          classCountMap.put(class_sf, frequency);
        }
      }
    }
    int max = 0;
    String maxClassString = "";
    for (String eachKey : classCountMap.keySet()) {
      int eachCount = classCountMap.get(eachKey);
      if (eachCount > max) {
        max = eachCount;
        maxClassString = eachKey;
      }
    }
    return maxClassString;
  }

  private int getLongestAxiomLength(Imports includeImportsClosure) {
    int longestAxiomLength = 0;

    for (OWLAxiom eachAxiom : getLogicalAxioms(includeImportsClosure, true)) {
      int eachLength = getLengthOfAxiom(eachAxiom);
      if (eachLength > longestAxiomLength) {
        longestAxiomLength = eachLength;
      }
    }
    return longestAxiomLength;
  }

  private int getDatatypesNotBuiltinCount(Imports included) {
    return getNotBuiltInDatatypes(included).size();
  }

  private int getDatatypesBuiltinCount(Imports included) {
    return getBuiltInDatatypes(included).size();
  }

  private String getSignatureWithoutIRIs(Imports incl) {
    StringBuilder b = new StringBuilder();
    for (OWLEntity e : getOntology().getSignature(incl)) {
      if (e.isBottomEntity() || e.isTopEntity()) {
        continue;
      }
      if (e instanceof OWLClass) {
        b.append(e.getIRI().getRemainder().or("unknown")).append("; ");
      } else if (e instanceof OWLObjectProperty) {
        b.append(e.getIRI().getRemainder().or("unknown")).append("; ");
      } else if (e instanceof OWLDataProperty) {
        b.append(e.getIRI().getRemainder().or("unknown")).append("; ");
      }
    }
    return b.toString().contains(";") ? b.substring(0, b.toString().lastIndexOf(";")) : "";
  }

  private List<OWLProfileViolation> getOWLDLProfileViolations() {
    isOWL2DLProfile();
    return owlProfileViolations;
  }
}
