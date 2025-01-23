package org.obolibrary.robot;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.obolibrary.robot.exceptions.ColumnException;
import org.obolibrary.robot.exceptions.RowParseException;
import org.semanticweb.owlapi.OWLAPIConfigProvider;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.OWLParserException;
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxClassExpressionParser;
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxParserImpl;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.mansyntax.ManchesterOWLSyntaxParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;

/**
 * Convenience methods for working with templates.
 *
 * @author <a href="mailto:rctauber@gmail.com">Becky Tauber</a>
 */
public class TemplateHelper {

  /** Logger */
  private static final Logger logger = LoggerFactory.getLogger(TemplateHelper.class);

  /** Shared DataFactory. */
  private static OWLDataFactory dataFactory = new OWLDataFactoryImpl();

  /* Error messages */

  /** Namespace for error messages. */
  private static final String NS = "template#";

  /**
   * Error message when annotation property cannot be resolved. Expects: annotation property name.
   */
  private static final String annotationPropertyError =
      NS + "ANNOTATION PROPERTY ERROR could not handle annotation property: %s";

  /** Error message when a given class expression is not able to be parsed. */
  protected static final String manchesterParseError =
      NS
          + "MANCHESTER PARSE ERROR the expression (%s) at row %d, column %d in table \"%s\" cannot be parsed: %s";

  /** Error message when the CLASS_TYPE is not subclass or equivalent. */
  private static final String classTypeError =
      NS + "CLASS TYPE ERROR '%s' is not a valid CLASS_TYPE";

  /** Error message when datatype cannot be resolved. Expects: datatype name. */
  private static final String datatypeError =
      NS + "DATATYPE ERROR could not find datatype for '%s' at row %d, column %d in table \"%s\".";

  /** Error message when template file type is not CSV, TSV, or TAB. Expects: file name. */
  private static final String fileTypeError = NS + "FILE TYPE ERROR unrecognized file type for: %s";

  /** Error message when the template does not have an ID column. Expects: table name. */
  private static final String idError = NS + "ID ERROR an \"ID\" column is required in table %s";

  /** Error message when the IRI in an IRI annotation cannot be resolved. Expects: value. */
  private static final String iriError =
      NS + "IRI ERROR could not create IRI annotation at row %d, column %d in table \"%s\": %s";

  /**
   * Error message when a language annotation string does not include "@{lang}". Expects: template
   * string
   */
  private static final String languageFormatError =
      NS
          + "LANGUAGE FORMAT ERROR invalid language annotation template string at column %d in table \"%s\": %s";

  /** Error message when the template file does not exist. Expects: file name. */
  private static final String templateFileError =
      NS + "TEMPLATE FILE ERROR template %s does not exist";

  /**
   * Error message when a language annotation string does not include "^^{type}". Expects: template
   * string
   */
  private static final String typedFormatError =
      NS + "TYPED FORMAT ERROR invalid typed annotation string at column %d in table \"%s\": %s";

  /* OWL entity type IRIs */

  private static final String OWL = "http://www.w3.org/2002/07/owl#";

  private static final String OWL_ANNOTATION_PROPERTY = OWL + "AnnotationProperty";

  private static final String OWL_CLASS = OWL + "Class";

  private static final String OWL_DATA_PROPERTY = OWL + "DatatypeProperty";

  private static final String OWL_DATATYPE = OWL + "Datatype";

  private static final String OWL_OBJECT_PROPERTY = OWL + "ObjectProperty";

  /**
   * Given a QuotedEntityChecker, a string value, and a character to split the value string on (or
   * null), return the value or values as a set of OWLAnnotationProperties.
   *
   * @param checker QuotedEntityChecker to get properties
   * @param value value or values to parse to properties
   * @param split character to split value on or null
   * @param column the column number for logging
   * @return set of OWLAnnotationProperties
   * @throws RowParseException if property cannot be found or created
   */
  public static Set<OWLAnnotationProperty> getAnnotationProperties(
      QuotedEntityChecker checker, String value, String split, int column) throws Exception {
    List<String> allValues = getAllValues(value, split);

    Set<OWLAnnotationProperty> properties = new HashSet<>();
    for (String v : allValues) {
      String content = QuotedEntityChecker.wrap(v);
      properties.add(getAnnotationProperty(checker, content, column));
    }

    return properties;
  }

  /**
   * Find an annotation property with the given name or create one.
   *
   * @param checker used to search by rdfs:label (for example)
   * @param name the name to search for
   * @param column the column number for logging
   * @return an annotation property
   * @throws RowParseException if the name cannot be resolved
   */
  public static OWLAnnotationProperty getAnnotationProperty(
      QuotedEntityChecker checker, String name, int column) throws Exception {
    OWLAnnotationProperty property = checker.getOWLAnnotationProperty(name);
    if (property != null) {
      return property;
    }
    throw new RowParseException(String.format(annotationPropertyError, name), 2, column + 1, name);
  }

  /**
   * Create an OWLAnnotation based on the template string and cell value. Replaced by
   * getAnnotation(QuotedEntityChecker checker, String template, String value).
   *
   * @param tableName name of table
   * @param checker used to resolve the annotation property and IRIs
   * @param template the template string
   * @param value the value for the annotation
   * @param rowNum the row number for logging
   * @param column the column number for logging
   * @return OWLAnnotation, or null if template string is not supported
   * @throws RowParseException if a row value cannot be parsed
   * @throws ColumnException if a header annotation template cannot be parsed
   */
  public static Set<OWLAnnotation> getAnnotations(
      String tableName,
      QuotedEntityChecker checker,
      String template,
      String value,
      int rowNum,
      int column)
      throws Exception {
    String split = getSplit(template);
    template = getTemplate(template);

    // Trim the > if it hasn't been trimmed yet
    if (template.startsWith(">")) {
      template = template.substring(1);
    }
    if (template.equals("AP")) {
      // MultiAnnotation
      return getStringAnnotations(checker, template, split, value, column);
    } else if (template.startsWith("A ") || template.startsWith("C ")) {
      return getStringAnnotations(checker, template, split, value, column);
    } else if (template.startsWith("AT ") || template.startsWith("CT ")) {
      if (template.contains("^^")) {
        return getTypedAnnotations(tableName, checker, template, split, value, rowNum, column);
      } else {
        throw new ColumnException(String.format(typedFormatError, column, tableName, template));
      }
    } else if (template.startsWith("AL ") || template.startsWith("CL ")) {
      if (template.contains("@")) {
        return getLanguageAnnotations(checker, template, split, value, column);
      } else {
        throw new ColumnException(String.format(languageFormatError, column, tableName, template));
      }
    } else if (template.startsWith("AI ") || template.startsWith("CI ")) {
      Set<OWLAnnotation> annotations = new HashSet<>();
      if (split != null) {
        String[] values = value.split(Pattern.quote(split));
        for (String v : values) {
          annotations.add(maybeGetIRIAnnotation(checker, template, v, column));
        }
      } else {
        annotations.add(maybeGetIRIAnnotation(checker, template, value, column));
      }
      return annotations;
    } else if (template.equals("LABEL")) {
      // Handle special LABEL case
      return getStringAnnotations(checker, template, split, value, column);
    } else {
      return new HashSet<>();
    }
  }

  /**
   * Given a Manchester Syntax parser, a template string, and a template value, insert the value
   * into the template string and parse to a set of class expressions. If the template string
   * contains 'SPLIT=', the value will be split on the provided character. Otherwise, the set will
   * only have one entry.
   *
   * @param tableName name of table
   * @param parser ManchesterOWLSyntaxClassExpressionParser to parse expression
   * @param template template string
   * @param value value to replace '%' in template string
   * @param rowNum the line number
   * @param col the column number
   * @return set of OWLClassExpressions
   * @throws RowParseException if row is malformed
   */
  public static Set<OWLClassExpression> getClassExpressions(
      String tableName,
      ManchesterOWLSyntaxClassExpressionParser parser,
      String template,
      String value,
      int rowNum,
      int col)
      throws RowParseException {
    return getClassExpressions(tableName, null, parser, template, value, rowNum, col);
  }

  /**
   * Given a Quoted Entity checker, a Manchester Syntax parser, a template string, and a template
   * value, insert the value into the template string and parse to a set of class expressions. If
   * the template string contains 'SPLIT=', the value will be split on the provided character.
   * Otherwise, the set will only have one entry.
   *
   * @param tableName name of table
   * @param checker QuotedEntityChecker to resolve labels
   * @param parser ManchesterOWLSyntaxClassExpressionParser to parse expression
   * @param template template string
   * @param value value to replace '%' in template string
   * @param rowNum the line number
   * @param col the column number
   * @return set of OWLClassExpressions
   * @throws RowParseException if row is malformed
   */
  public static Set<OWLClassExpression> getClassExpressions(
      String tableName,
      QuotedEntityChecker checker,
      ManchesterOWLSyntaxClassExpressionParser parser,
      String template,
      String value,
      int rowNum,
      int col)
      throws RowParseException {
    String split = getSplit(template);
    template = getTemplate(template);

    Set<OWLClassExpression> expressions = new HashSet<>();
    if (template.startsWith("CI")) {
      // CI indicates its just an IRI
      if (split != null) {
        String[] values = value.split(Pattern.quote(split));
        for (String v : values) {
          String content = QuotedEntityChecker.wrap(v);
          expressions.add(tryParse(tableName, checker, parser, content, rowNum, col));
        }
      } else {
        String content = QuotedEntityChecker.wrap(value);
        expressions.add(tryParse(tableName, checker, parser, content, rowNum, col));
      }
    } else {
      if (split != null) {
        String[] values = value.split(Pattern.quote(split));
        for (String v : values) {
          expressions.add(getClassExpression(tableName, template, v, checker, parser, rowNum, col));
        }
      } else {
        expressions.add(
            getClassExpression(tableName, template, value, checker, parser, rowNum, col));
      }
    }
    return expressions;
  }

  /**
   * Given a QuotedEntityChecker, a template string, and a template value, return a set of data
   * property expressions based on the value or values (if SPLIT is included in the template
   * string). Note that a data property expression can ONLY be another data property, but this
   * allows support for future data property expressions.
   *
   * @param tableName name of table
   * @param checker QuotedEntityChecker to resolve entities
   * @param template template string
   * @param value template value or values
   * @param rowNum the row number for logging
   * @param column the column number for logging
   * @return set of OWLDataPropertyExpressions
   * @throws RowParseException if row is malformed
   */
  public static Set<OWLDataPropertyExpression> getDataPropertyExpressions(
      String tableName,
      QuotedEntityChecker checker,
      String template,
      String value,
      int rowNum,
      int column)
      throws RowParseException {
    String split = getSplit(template);
    template = getTemplate(template);

    // Create a parser
    ManchesterOWLSyntaxParser parser =
        new ManchesterOWLSyntaxParserImpl(
            OWLOntologyLoaderConfiguration::new, OWLManager.getOWLDataFactory());
    parser.setOWLEntityChecker(checker);

    // Maybe split values
    List<String> allValues = getAllValues(value, split);

    Set<OWLDataPropertyExpression> expressions = new HashSet<>();
    if (template.startsWith("PI")) {
      // PI indicates its just an IRI
      for (String v : allValues) {
        String content = QuotedEntityChecker.wrap(v);
        OWLDataProperty property = checker.getOWLDataProperty(content);
        expressions.add(property);
      }
    } else {
      for (String v : allValues) {
        String content = QuotedEntityChecker.wrap(v);
        // Get the template without identifier by breaking on the first space
        String sub = template.substring(template.indexOf(" ")).trim().replaceAll("%", content);
        parser.setStringToParse(sub);
        try {
          expressions.addAll(parser.parseDataPropertyList());
        } catch (OWLParserException e) {
          String cause = getManchesterErrorCause(e);
          throw new RowParseException(
              String.format(manchesterParseError, sub, rowNum, column + 1, tableName, cause),
              rowNum,
              column + 1,
              v);
        }
      }
    }

    return expressions;
  }

  /**
   * Find a datatype with the given name or create one.
   *
   * @param tableName name of table
   * @param checker used to search by rdfs:label (for example)
   * @param name the name to search for
   * @param rowNum the row number
   * @param column the column number
   * @return a datatype
   * @throws RowParseException if the name cannot be resolved
   */
  public static OWLDatatype getDatatype(
      String tableName, QuotedEntityChecker checker, String name, int rowNum, int column)
      throws RowParseException {
    OWLDatatype datatype = checker.getOWLDatatype(name);
    if (datatype != null) {
      return datatype;
    }
    throw new RowParseException(
        String.format(datatypeError, name, rowNum, column + 1, tableName),
        rowNum,
        column + 1,
        name);
  }

  /**
   * Find a datatype with the given name or create one.
   *
   * @param checker used to search by rdfs:label (for example)
   * @param name the name to search for
   * @return a datatype or null
   */
  public static OWLDatatype getDatatype(QuotedEntityChecker checker, String name) {
    return checker.getOWLDatatype(name);
  }

  /**
   * Given a QuotedEntityChecker, a string value, and a character to split the value string on (or
   * null), return the value or values as a set of OWLDatatypes.
   *
   * @param tableName name of table
   * @param checker QuotedEntityChecker to get OWLDatatypes
   * @param value value or values to parse to datatypes
   * @param split character to split value on or null
   * @param rowNum the row number
   * @param column the column number
   * @return set of OWLDatatypes
   * @throws RowParseException if datatype cannot be found or created
   */
  public static Set<OWLDatatype> getDatatypes(
      String tableName,
      QuotedEntityChecker checker,
      String value,
      String split,
      int rowNum,
      int column)
      throws RowParseException {
    List<String> allValues = getAllValues(value, split);

    Set<OWLDatatype> datatypes = new HashSet<>();
    for (String v : allValues) {
      String content = QuotedEntityChecker.wrap(v);
      datatypes.add(getDatatype(tableName, checker, content, rowNum, column));
    }

    return datatypes;
  }

  /**
   * Given a QuotedEntityChecker, a string value (maybe separated by a split character), and a split
   * character (or null), return the value or values as a set of OWLIndividuals.
   *
   * @param checker QuotedEntityChecker to get individuals by label
   * @param value string of individual or individuals by label or ID
   * @param split character to split value string on
   * @return set of OWLIndividuals
   */
  public static Set<OWLIndividual> getIndividuals(
      QuotedEntityChecker checker, String value, String split) {
    if (value == null || value.trim().isEmpty()) {
      return new HashSet<>();
    }

    Set<OWLIndividual> individuals = new HashSet<>();
    List<String> allValues = getAllValues(value, split);

    for (String v : allValues) {
      individuals.add(checker.getOWLIndividual(v));
    }

    return individuals;
  }

  /**
   * Return an IRI annotation for the given template string and string value. The template string
   * format is "AI [name]" and the value is the name of an entity or an IRI.
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param value the value for the annotation
   * @param column the column number for logging
   * @return a new annotation axiom with property and an IRI value
   * @throws RowParseException if the annotation property cannot be found or the IRI cannot be
   *     created
   */
  public static OWLAnnotation getIRIAnnotation(
      QuotedEntityChecker checker, String template, String value, int column) throws Exception {
    IRI iri = checker.getIRI(value, true);
    if (iri == null) {
      return null;
    }
    return getIRIAnnotation(checker, template, iri, column);
  }

  /**
   * Return an IRI annotation for the given template string and IRI value. The template string
   * format is "AI [name]" and the value is an IRI.
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param value the IRI value for the annotation
   * @param column the column number for logging
   * @return a new annotation axiom with property and an IRI value
   * @throws RowParseException if the annotation property cannot be found
   */
  public static OWLAnnotation getIRIAnnotation(
      QuotedEntityChecker checker, String template, IRI value, int column) throws Exception {
    String name = template.substring(2).trim();
    OWLAnnotationProperty property = getAnnotationProperty(checker, name, column);
    return dataFactory.getOWLAnnotation(property, value);
  }

  /**
   * Get a list of the IRIs defined in a set of template tables.
   *
   * @param tables a map from table names to tables
   * @param ioHelper used to find entities by name
   * @return a list of IRIs
   * @throws ColumnException when an ID column does not exist
   */
  public static List<IRI> getIRIs(Map<String, List<List<String>>> tables, IOHelper ioHelper)
      throws Exception {
    List<IRI> iris = new ArrayList<>();
    for (Map.Entry<String, List<List<String>>> table : tables.entrySet()) {
      String tableName = table.getKey();
      List<List<String>> rows = table.getValue();
      iris.addAll(getIRIs(tableName, rows, ioHelper));
    }
    return iris;
  }

  /**
   * Get a list of the IRIs defined in a template table.
   *
   * @param tableName the name of the table
   * @param rows the table of data
   * @param ioHelper used to find entities by name
   * @return a list of IRIs
   * @throws ColumnException when an ID column does not exist
   */
  public static List<IRI> getIRIs(String tableName, List<List<String>> rows, IOHelper ioHelper)
      throws Exception {
    // Find the ID column.
    List<String> templates = rows.get(1);
    int idColumn = -1;
    for (int column = 0; column < templates.size(); column++) {
      String template = templates.get(column);
      if (template == null) {
        continue;
      }
      template = template.trim();
      if (template.equals("ID")) {
        idColumn = column;
      }
    }
    if (idColumn == -1) {
      throw new ColumnException(String.format(idError, tableName));
    }

    List<IRI> iris = new ArrayList<>();
    for (int row = 2; row < rows.size(); row++) {
      String id;
      try {
        id = rows.get(row).get(idColumn);
      } catch (IndexOutOfBoundsException e) {
        continue;
      }
      if (id == null || id.trim().isEmpty()) {
        continue;
      }
      IRI iri = ioHelper.createIRI(id);
      if (iri == null) {
        continue;
      }
      iris.add(iri);
    }

    return iris;
  }

  /**
   * Return a set of language tagged annotations for the given template and value(s). The template
   * string format is "AL [name]@[lang]" and the value is any string.
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param split the character to split values on
   * @param value the value for the annotation
   * @param column the column index for the value from the template
   * @return a set of new annotation(s) with property and language tagged literal
   * @throws RowParseException if the annotation property cannot be found
   */
  public static Set<OWLAnnotation> getLanguageAnnotations(
      QuotedEntityChecker checker, String template, String split, String value, int column)
      throws Exception {
    OWLAnnotationProperty property = getAnnotationProperty(checker, template, "@", column);
    String lang = template.substring(template.indexOf("@") + 1).trim();

    Set<OWLAnnotation> annotations = new HashSet<>();
    List<String> allValues = getAllValues(value, split);

    for (String v : allValues) {
      annotations.add(dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(v, lang)));
    }

    return annotations;
  }

  /**
   * Given a QuotedEntityChecker, a string value (maybe separated by a split character), and a split
   * character (or null), return the value or values as a set of OWLLiterals.
   *
   * @param tableName name of table
   * @param checker QuotedEntityChecker to get datatypes
   * @param value string of literal or literals
   * @param split character to split value string on
   * @param rowNum the row number
   * @param column the column number
   * @return set of OWLLiterals
   * @throws RowParseException if row is malformed
   */
  public static Set<OWLLiteral> getLiterals(
      String tableName,
      QuotedEntityChecker checker,
      String value,
      String split,
      int rowNum,
      int column)
      throws RowParseException {
    Set<OWLLiteral> literals = new HashSet<>();
    List<String> allValues = getAllValues(value, split);

    for (String v : allValues) {
      if (v.contains("^^")) {
        String datatype = v.substring(v.indexOf("^^") + 2);
        v = v.substring(0, v.indexOf("^^"));
        OWLDatatype dt = getDatatype(tableName, checker, datatype, rowNum, column);
        literals.add(dataFactory.getOWLLiteral(v.trim(), dt));
      } else {
        literals.add(dataFactory.getOWLLiteral(v.trim()));
      }
    }

    return literals;
  }

  /**
   * Given a QuotedEntityChecker, a template string, and a template value, return a set of object
   * property expressions based on the value or values (if SPLIT is included in the template
   * string). Note that an object property expression can ONLY be another object property or
   * 'inverse', but this allows support for future data property expressions.
   *
   * @param tableName name of table
   * @param checker QuotedEntityChecker to resolve entities
   * @param template template string
   * @param value template value or values
   * @param rowNum the row number for logging
   * @param column the column number for logging
   * @return set of OWLDataPropertyExpressions
   * @throws RowParseException if row is malformed
   */
  public static Set<OWLObjectPropertyExpression> getObjectPropertyExpressions(
      String tableName,
      QuotedEntityChecker checker,
      String template,
      String value,
      int rowNum,
      int column)
      throws RowParseException {
    String split = getSplit(template);
    template = getTemplate(template);

    // Create a parser

    ManchesterOWLSyntaxParser parser =
        new ManchesterOWLSyntaxParserImpl(
            new OWLAPIConfigProvider(), OWLManager.getOWLDataFactory());
    parser.setOWLEntityChecker(checker);

    // Maybe split values
    List<String> allValues = getAllValues(value, split);

    Set<OWLObjectPropertyExpression> expressions = new HashSet<>();
    if (template.startsWith("PI")) {
      // PI indicates its just an IRI
      for (String v : allValues) {
        String content = QuotedEntityChecker.wrap(v);
        OWLObjectProperty property = checker.getOWLObjectProperty(content);
        expressions.add(property);
      }
    } else {
      for (String v : allValues) {
        String content = QuotedEntityChecker.wrap(v);
        // Get the template without identifier by breaking on the first space
        String sub = template.substring(template.indexOf(" ")).trim().replaceAll("%", content);
        parser.setStringToParse(sub);
        try {
          expressions.addAll(parser.parseObjectPropertyList());
        } catch (OWLParserException e) {
          String cause = getManchesterErrorCause(e);
          throw new RowParseException(
              String.format(manchesterParseError, sub, rowNum, column + 1, tableName, cause),
              rowNum,
              column + 1,
              v);
        }
      }
    }

    return expressions;
  }

  /**
   * Return a set of string annotations for the given template string and value(s).
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param split the character to split values on
   * @param value the value for the annotation
   * @param column the index of the column
   * @return a set of new annotation(s) with property and string literal value
   * @throws RowParseException if the annotation property cannot be found
   */
  public static Set<OWLAnnotation> getStringAnnotations(
      QuotedEntityChecker checker, String template, String split, String value, int column)
      throws Exception {

    OWLAnnotationProperty property;
    if (template.equals("LABEL")) {
      // Handle special LABEL case
      property = dataFactory.getRDFSLabel();
    } else if (template.equals("AP")) {
      String name = value.split(" ")[0];
      value = value.substring(name.length() + 2).replaceAll("^\"|\"$", "");
      property = getAnnotationProperty(checker, name, column);
    } else {
      String name = template.substring(1).trim();
      property = getAnnotationProperty(checker, name, column);
    }

    Set<OWLAnnotation> annotations = new HashSet<>();
    if (split != null) {
      String[] values = value.split(Pattern.quote(split));
      for (String v : values) {
        annotations.add(dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(v)));
      }
    } else {
      annotations.add(dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(value)));
    }

    return annotations;
  }

  /**
   * Return a set of typed annotations for the given template string and value(s). The template
   * string format is "AT [name]^^[datatype]" and the value is any string.
   *
   * @param tableName name of table
   * @param checker used to resolve the annotation property and datatype
   * @param template the template string
   * @param split the character to split values on
   * @param value the value for the annotation
   * @param rowNum the row number
   * @param column the column number
   * @return a set of new annotation(s) with property and typed literal value
   * @throws RowParseException if the annotation property cannot be found
   */
  public static Set<OWLAnnotation> getTypedAnnotations(
      String tableName,
      QuotedEntityChecker checker,
      String template,
      String split,
      String value,
      int rowNum,
      int column)
      throws Exception {
    OWLAnnotationProperty property = getAnnotationProperty(checker, template, "^^", column);
    String typeName = template.substring(template.indexOf("^^") + 2).trim();
    OWLDatatype datatype = getDatatype(tableName, checker, typeName, rowNum, column);

    Set<OWLAnnotation> annotations = new HashSet<>();
    if (split != null) {
      String[] values = value.split(Pattern.quote(split));
      for (String v : values) {
        annotations.add(
            dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(v, datatype)));
      }
    } else {
      annotations.add(
          dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(value, datatype)));
    }

    return annotations;
  }

  /**
   * Given a QuotedEntityChecker, a value, and a split character (or null), return the value (or
   * values) as a set of IRIs.
   *
   * @param checker QuotedEntityChecker to get IRIs
   * @param value value (or values) to parse to IRI
   * @param split character to split value on
   * @return set of IRIs
   */
  public static Set<IRI> getValueIRIs(QuotedEntityChecker checker, String value, String split) {
    List<String> allValues = getAllValues(value, split);

    Set<IRI> iris = new HashSet<>();
    for (String v : allValues) {
      IRI iri = checker.getIRI(v.trim(), true);
      if (iri != null) {
        iris.add(iri);
      }
    }
    return iris;
  }

  /**
   * Read comma-separated values from a path to a list of lists of strings.
   *
   * @param path file path to the CSV file
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readCSV(String path) throws IOException {
    return readXSV(new FileReader(path), ',');
  }

  /**
   * Read comma-separated values from a stream to a list of lists of strings.
   *
   * @param stream the stream to read from
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readCSV(InputStream stream) throws IOException {
    return readXSV(new InputStreamReader(stream), ',');
  }

  /**
   * Read comma-separated values from a reader to a list of lists of strings.
   *
   * @param reader a reader to read data from
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readCSV(Reader reader) throws IOException {
    return readXSV(reader, ',');
  }

  /**
   * Read a table from a path to a list of lists of strings.
   *
   * @param path file path to the CSV file
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readTable(String path) throws IOException {
    File file = new File(path);
    if (!file.exists()) {
      throw new IllegalArgumentException(String.format(templateFileError, file.getName()));
    }
    String extension = FilenameUtils.getExtension(file.getName());
    extension = extension.trim().toLowerCase();
    switch (extension) {
      case "csv":
        return readCSV(new FileReader(path));
      case "tsv":
      case "tab":
        return readTSV(new FileReader(path));
      default:
        throw new IOException(String.format(fileTypeError, path));
    }
  }

  /**
   * Read tab-separated values from a path to a list of lists of strings.
   *
   * @param path file path to the CSV file
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readTSV(String path) throws IOException {
    return readXSV(new FileReader(path), '\t');
  }

  /**
   * Read tab-separated values from a stream to a list of lists of strings.
   *
   * @param stream the stream to read from
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readTSV(InputStream stream) throws IOException {
    return readXSV(new InputStreamReader(stream), '\t');
  }

  /**
   * Read tab-separated values from a reader to a list of lists of strings.
   *
   * @param reader a reader to read data from
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readTSV(Reader reader) throws IOException {
    return readXSV(reader, '\t');
  }

  /**
   * Return true if the template string is valid, false otherwise.
   *
   * @param template the template string to check
   * @return true if valid, false otherwise
   */
  public static boolean validateTemplateString(String template) {
    template = template.trim();
    if (template.equals("ID")) {
      return true;
    } else if (template.equals("LABEL")) {
      return true;
    } else if (template.equals("CLASS_TYPE")) {
      return true;
    } else if (template.equals("PROPERTY_TYPE")) {
      return true;
    } else if (template.equals("DOMAIN")) {
      return true;
    } else if (template.equals("RANGE")) {
      return true;
    } else if (template.equals("INDIVIDUAL_TYPE")) {
      return true;
    } else if (template.matches("^TYPE( SPLIT=.+)?$")) {
      // TYPE can be followed by a split for individuals
      return true;
    } else if (template.matches("^CHARACTERISTIC( SPLIT=.+)?$")) {
      // CHARACTERISTIC can have a split
      // Should only be followed by SPLIT, nothing else
      return true;
    } else if (template.matches("^>{0,2}A[LTI]? .*")) {
      // Annotations can have one or two > (nested)
      // And can be A, AL, AT, or AI always followed by space
      return true;
    } else if (template.matches("^>?(C .*|CI.?|[SED]C .*)")) {
      // Classes can have one > (annotation on previous axiom - legacy support)
      // Can be C, CI (does not need to be followed by space), SC, EC, or DC
      return true;
    } else if (template.matches("^(P .*|PI.?|[SEDI]P .*)")) {
      // Properties can be P, PI (does not need to be followed by space), SP, EP, DP, or IP
      return true;
    } else if (template.equals("AP")) {
      // AP multi annotation type
      return true;
    } else
      // Individuals can be I, II (does not need to be followed by space), SI, or DI
      return template.matches("^(I .*|II.?|[TSD]I .*)");

    // TODO - future support for DT datatype axioms
  }

  /**
   * Given a Manchester class expression parser and a content string, try to parse the content
   * string. Throw a detailed exception message if parsing fails.
   *
   * @param tableName name of table
   * @param parser ManchesterOWLSyntaxClassExpressionParser to parse string
   * @param content class expression string to parse
   * @param rowNum the row number for logging
   * @param column the column number for logging
   * @return OWLClassExpression representation of the string
   * @throws RowParseException if string cannot be parsed for any reason
   */
  protected static OWLClassExpression tryParse(
      String tableName,
      ManchesterOWLSyntaxClassExpressionParser parser,
      String content,
      int rowNum,
      int column)
      throws RowParseException {
    return tryParse(tableName, null, parser, content, rowNum, column);
  }

  /**
   * Given a Quoted Entity Checker to resolve labels, a Manchester class expression parser, and a
   * content string, try to parse the content string. If the checker is not null, first try to get a
   * named class. If not found, try to parse using the parser. Throw a detailed exception message if
   * parsing fails.
   *
   * @param tableName name of table
   * @param checker QuotedEntityChecker to resolve labels
   * @param parser ManchesterOWLSyntaxClassExpressionParser to parse string
   * @param content class expression string to parse
   * @param rowNum the row number for logging
   * @param column the column number for logging
   * @return OWLClassExpression representation of the string
   * @throws RowParseException if string cannot be parsed for any reason
   */
  protected static OWLClassExpression tryParse(
      String tableName,
      QuotedEntityChecker checker,
      ManchesterOWLSyntaxClassExpressionParser parser,
      String content,
      int rowNum,
      int column)
      throws RowParseException {
    OWLClassExpression expr;
    content = content.trim();
    logger.info(String.format("Parsing expression: %s", content));
    try {
      expr = null;
      if (checker != null) {
        // If we have a checker, try to get the class by label
        // This allows class labels with single quotes
        expr = checker.getOWLClass(content, false);
        if (expr == null) {
          // If not found by label, is it just an HTTP IRI?
          if (content.startsWith("http")) {
            IRI iri = IRI.create(content);
            if (iri != null) {
              // If so, create a new class for this IRI.
              expr = checker.getOWLClass(content, true);
            }
          }
        }
      }
      if (expr == null) {
        // If not found by label, try to parse
        expr = parser.parse(content);
      }
    } catch (OWLParserException e) {
      String cause = getManchesterErrorCause(e);

      throw new RowParseException(
          String.format(manchesterParseError, content, rowNum, column + 1, tableName, cause),
          rowNum,
          column + 1,
          content);
    }
    return expr;
  }

  /**
   * Given an OWLParserException, determine if we can identify the offending term. Return that as
   * the cause along with some explanation of what is expected.
   *
   * @param e exception to get cause of
   * @return String cause of exception
   */
  private static String getManchesterErrorCause(OWLParserException e) {
    String cause = e.getMessage();
    String pattern = ".*Encountered ([^ ]*|'.*') at .+. Expected one of:\n([\\s\\S]+)";
    Pattern p = Pattern.compile(pattern, Pattern.MULTILINE);
    Matcher m = p.matcher(e.getMessage());
    if (m.find()) {
      String keyword = m.group(1);
      if (keyword.startsWith("'")) {
        keyword = keyword.substring(1, keyword.length() - 1);
      }

      // Override expected message for restrictions (we want to reference the term before the
      // restriction)
      if (Arrays.asList("some", "only", "min", "max", "exactly", "value").contains(keyword)) {
        return String.format(
            "encountered unexpected '%1$s'\n\thint: the term before '%1$s' must be a property",
            keyword);
      }

      // Otherwise print what was expected by the parser in that place
      // Some of these have hard to understand "expected" values, so we rewrite them the best we can
      String expected = m.group(2);
      List<String> expectedSplit =
          Lists.newArrayList(expected.split("\n")).stream()
              .map(String::trim)
              .filter(x -> !x.equals(""))
              .collect(Collectors.toList());
      if (expectedSplit.contains("{")) {
        expectedSplit.add(expectedSplit.indexOf("{"), "{ ... }");
        expectedSplit.remove("{");
      }
      if (expectedSplit.contains("(")) {
        expectedSplit.add(expectedSplit.indexOf("("), "( ... )");
        expectedSplit.remove("(");
      }
      expectedSplit.remove("|EOF|");

      return String.format("encountered '%s', but expected one of:\n\t", keyword)
          + String.join("\n\t", expectedSplit);
    }
    return cause;
  }

  /**
   * Given a value (maybe separated by a split character) and a split character (or null), return
   * the string as a list of values. If there is no split character, the value is the only element
   * of the list.
   *
   * @param value string to split
   * @param split character to split on, or null
   * @return values as list
   */
  private static List<String> getAllValues(String value, String split) {
    List<String> allValues = new ArrayList<>();
    if (split != null) {
      String[] values = value.split(Pattern.quote(split));
      for (String v : values) {
        allValues.add(v.trim());
      }
    } else {
      allValues.add(value.trim());
    }
    return allValues;
  }

  /**
   * Given a quoted entity checker, a template string, and a character that separates the template
   * from a tag, return the annotation property.
   *
   * @param checker QuotedEntityChecker to resolve properties
   * @param template template string
   * @param chr character to split template string
   * @param column the column number for logging
   * @return OWLAnnotationProperty
   * @throws RowParseException on issue resolving property
   */
  private static OWLAnnotationProperty getAnnotationProperty(
      QuotedEntityChecker checker, String template, String chr, int column) throws Exception {
    template = template.substring(2).trim();
    String name = template.substring(0, template.indexOf(chr)).trim();
    return getAnnotationProperty(checker, name, column);
  }

  /**
   * Given a table name, a template string, a value string, a parser, a row number, and a column
   * number, attempt to resolve a class expression for that template string and value.
   *
   * @param tableName name of table
   * @param template template string
   * @param value template value
   * @param parser Machester parser
   * @param rowNum row number of template value
   * @param col column number of template string
   * @return OWLClassExpression from template
   * @throws RowParseException on issue resolving names
   */
  private static OWLClassExpression getClassExpression(
      String tableName,
      String template,
      String value,
      QuotedEntityChecker checker,
      ManchesterOWLSyntaxClassExpressionParser parser,
      int rowNum,
      int col)
      throws RowParseException {
    String content = QuotedEntityChecker.wrap(value);
    // Get the template without identifier by breaking on the first space
    String sub;
    if (template.contains("%")) {
      sub = template.substring(template.indexOf(" ")).replaceAll("%", content);
    } else {
      sub = content;
    }
    return tryParse(tableName, checker, parser, sub, rowNum, col);
  }

  /**
   * Given a tempalte string, return the split character if it exists.
   *
   * @param template template string
   * @return split character or null
   */
  private static String getSplit(String template) {
    if (template.contains("SPLIT=")) {
      return template.substring(template.indexOf("SPLIT=") + 6).trim();
    }
    return null;
  }

  /**
   * Given a template string, return the template without a split (if it exists).
   *
   * @param template template string
   * @return template string, maybe without SPLIT
   */
  private static String getTemplate(String template) {
    if (template.contains("SPLIT=")) {
      return template.substring(0, template.indexOf("SPLIT=")).trim();
    }
    return template.trim();
  }

  /**
   * Given a checker, a template string, and a value for the template, return an IRI annotation.
   *
   * @param checker QuotedEntityChecker to resolve entities
   * @param template template string
   * @param value value to use with the template string
   * @param column the column number for logging
   * @return OWLAnnotation created from template and value
   * @throws RowParseException if entities cannot be resolved
   */
  private static OWLAnnotation maybeGetIRIAnnotation(
      QuotedEntityChecker checker, String template, String value, int column) throws Exception {
    IRI iri = checker.getIRI(value, true);
    if (iri == null) {
      iri = IRI.create(value);
    }
    return getIRIAnnotation(checker, template, iri, column);
  }

  /**
   * Given a Reader and a separator character, return the contents of the table as a list of rows.
   *
   * @param reader a reader to read data from
   * @param separator separator character
   * @return a list of lists of strings
   * @throws IOException on file reading problems
   */
  private static List<List<String>> readXSV(Reader reader, char separator) throws IOException {
    CSVReader csv =
        new CSVReaderBuilder(reader)
            .withCSVParser(new CSVParserBuilder().withSeparator(separator).build())
            .build();
    List<List<String>> rows = new ArrayList<>();
    for (String[] nextLine : csv) {
      rows.add(new ArrayList<>(Arrays.asList(nextLine)));
    }
    csv.close();
    return rows;
  }

  /**
   * Create an OWLAnnotation based on the template string and cell value. Replaced by more-specific
   * methods to get annotations by their type (e.g., getStringAnnotation).
   *
   * @param tableName name of table
   * @param checker used to resolve the annotation property
   * @param ioHelper IOHelper used to create IRIs from values
   * @param template the template string
   * @param value the value for the annotation
   * @return OWLAnnotation, or null if template string is not supported
   * @throws Exception if annotation property cannot be found
   */
  @Deprecated
  public static OWLAnnotation getAnnotation(
      String tableName,
      QuotedEntityChecker checker,
      IOHelper ioHelper,
      String template,
      String value)
      throws Exception {
    if (template.startsWith("A ")) {
      return getStringAnnotation(checker, template, value);
    } else if (template.startsWith("AT ")) {
      if (template.contains("^^")) {
        return getTypedAnnotation(checker, template, value);
      } else {
        throw new Exception(
            String.format("TYPED FORMAT ERROR invalid typed annotation string: %s", template));
      }
    } else if (template.startsWith("AL ")) {
      if (template.contains("@")) {
        return getLanguageAnnotation(checker, template, value);
      } else {
        throw new Exception(
            String.format(
                "LANGUAGE FORMAT ERROR invalid language annotation template string: %s", template));
      }
    } else if (template.startsWith("AI ")) {
      IRI iri = ioHelper.createIRI(value);
      if (iri != null) {
        return getIRIAnnotation(checker, template, iri, 0);
      } else {
        throw new RowParseException(String.format(iriError, 0, 0, tableName, value));
      }
    } else {
      return null;
    }
  }

  /**
   * Create an OWLAnnotation based on the template string and cell value. Replaced by more-specific
   * methods to get annotations by their type (e.g., getStringAnnotation).
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param value the value for the annotation
   * @return OWLAnnotation, or null if template string is not supported
   * @throws Exception if annotation cannot be created
   */
  @Deprecated
  public static OWLAnnotation getAnnotation(
      QuotedEntityChecker checker, String template, String value) throws Exception {
    if (template.startsWith("A ")) {
      return getStringAnnotation(checker, template, value);
    } else if (template.startsWith("AT ")) {
      if (template.contains("^^")) {
        return getTypedAnnotation(checker, template, value);
      } else {
        throw new Exception(
            String.format("TYPED FORMAT ERROR invalid typed annotation string: %s", template));
      }
    } else if (template.startsWith("AL ")) {
      if (template.contains("@")) {
        return getLanguageAnnotation(checker, template, value);
      } else {
        throw new Exception(
            String.format(
                "LANGUAGE FORMAT ERROR invalid language annotation template string: %s", template));
      }
    } else if (template.startsWith("AI ")) {
      return getIRIAnnotation(checker, template, value, 0);
    } else {
      return null;
    }
  }

  /**
   * Create an OWLAnnotation based on the template string and cell value. Replaced by more-specific
   * methods to get annotations by their type (e.g., getStringAnnotation).
   *
   * @param checker used to resolve the annotation property
   * @param ioHelper IOHelper used to create IRIs from values
   * @param template the template string
   * @param value the value for the annotation
   * @return OWLAnnotation, or null if template string is not supported
   * @throws Exception if annotation property cannot be found
   */
  @Deprecated
  public static OWLAnnotation getAnnotation(
      QuotedEntityChecker checker, IOHelper ioHelper, String template, String value)
      throws Exception {
    if (template.startsWith("A ")) {
      return getStringAnnotation(checker, template, value);
    } else if (template.startsWith("AT ")) {
      if (template.contains("^^")) {
        return getTypedAnnotation(checker, template, value);
      } else {
        throw new Exception(String.format(typedFormatError, 0, "", template));
      }
    } else if (template.startsWith("AL ")) {
      if (template.contains("@")) {
        return getLanguageAnnotation(checker, template, value);
      } else {
        throw new Exception(String.format(languageFormatError, 0, "", template));
      }
    } else if (template.startsWith("AI ")) {
      IRI iri = ioHelper.createIRI(value);
      if (iri != null) {
        return getIRIAnnotation(checker, template, iri, 0);
      } else {
        throw new Exception(String.format(iriError, 0, 0, "", value));
      }
    } else {
      return null;
    }
  }

  /**
   * Get a set of annotation axioms for an OWLEntity. Supports axiom annotations and axiom
   * annotation annotations.
   *
   * @deprecated TemplateOperation replaced with Template class
   * @param entity OWLEntity to annotation
   * @param annotations Set of OWLAnnotations
   * @param nested Map with top-level OWLAnnotation as key and another map (axiom OWLAnnotation, set
   *     of axiom annotation OWLAnnotations) as value
   * @return Set of OWLAnnotationAssertionAxioms
   */
  @Deprecated
  public static Set<OWLAnnotationAssertionAxiom> getAnnotationAxioms(
      OWLEntity entity,
      Set<OWLAnnotation> annotations,
      Map<OWLAnnotation, Map<OWLAnnotation, Set<OWLAnnotation>>> nested) {
    Set<OWLAnnotationAssertionAxiom> axioms = new HashSet<>();
    // Create basic annotations
    for (OWLAnnotation annotation : annotations) {
      axioms.add(dataFactory.getOWLAnnotationAssertionAxiom(entity.getIRI(), annotation));
    }
    // Create annotations with axiom annotations
    for (Entry<OWLAnnotation, Map<OWLAnnotation, Set<OWLAnnotation>>> annotationPlusAxioms :
        nested.entrySet()) {
      OWLAnnotation annotation = annotationPlusAxioms.getKey();
      Set<OWLAnnotation> axiomAnnotations = new HashSet<>();
      // For each annotation with its axiom annotations ...
      for (Entry<OWLAnnotation, Set<OWLAnnotation>> nestedSet :
          annotationPlusAxioms.getValue().entrySet()) {
        OWLAnnotation axiomAnnotation = nestedSet.getKey();
        // Check if there are annotations on the axiom annotations, and add those
        Set<OWLAnnotation> axiomAnnotationAnnotations = nestedSet.getValue();
        if (axiomAnnotationAnnotations.isEmpty()) {
          axiomAnnotations.add(axiomAnnotation);
        } else {
          OWLAnnotationProperty property = axiomAnnotation.getProperty();
          OWLAnnotationValue value = axiomAnnotation.getValue();
          axiomAnnotations.add(
              dataFactory.getOWLAnnotation(property, value, axiomAnnotationAnnotations));
        }
      }
      axioms.add(
          dataFactory.getOWLAnnotationAssertionAxiom(
              entity.getIRI(), annotation, axiomAnnotations));
    }
    return axioms;
  }

  /**
   * Given a QuotedEntityChecker, a string value, and a character to split the value string on (or
   * null), return the value or values as a set of OWLAnnotationProperties.
   *
   * @param checker QuotedEntityChecker to get properties
   * @param value value or values to parse to properties
   * @param split character to split value on or null
   * @return set of OWLAnnotationProperties
   * @throws RowParseException if property cannot be found or created
   */
  @Deprecated
  public static Set<OWLAnnotationProperty> getAnnotationProperties(
      QuotedEntityChecker checker, String value, String split) throws Exception {
    List<String> allValues = getAllValues(value, split);

    Set<OWLAnnotationProperty> properties = new HashSet<>();
    for (String v : allValues) {
      String content = QuotedEntityChecker.wrap(v);
      properties.add(getAnnotationProperty(checker, content));
    }

    return properties;
  }

  /**
   * Find an annotation property with the given name or create one.
   *
   * @param checker used to search by rdfs:label (for example)
   * @param name the name to search for
   * @return an annotation property
   * @throws RowParseException if the name cannot be resolved
   */
  @Deprecated
  public static OWLAnnotationProperty getAnnotationProperty(
      QuotedEntityChecker checker, String name) throws Exception {
    OWLAnnotationProperty property = checker.getOWLAnnotationProperty(name);
    if (property != null) {
      return property;
    }
    throw new RowParseException(String.format(annotationPropertyError, name));
  }

  /**
   * Given a set of rows, the row number, and the column number, get the content in the column for
   * the row. If there are any issues, return an empty string. If the cell is empty, return null.
   *
   * @deprecated TemplateOperation replaced with Template class
   * @param rows list of rows (lists of strings)
   * @param row row number to get ID of
   * @param column column number
   * @return content, null, or empty string.
   */
  @Deprecated
  public static String getCellContent(List<List<String>> rows, int row, Integer column) {
    String id = null;
    if (column != null && column != -1) {
      try {
        id = rows.get(row).get(column);
      } catch (IndexOutOfBoundsException e) {
        return "";
      }
      if (id == null || id.trim().isEmpty()) {
        return "";
      }
    }
    return id;
  }

  /**
   * Use type, id, and label information to get an entity from the data in a row. Requires either:
   * an id (default type is owl:Class); an id and type; or a label.
   *
   * @deprecated TemplateOperation replaced with Template class
   * @param checker for looking up labels
   * @param type the IRI of the type for this entity, or null
   * @param id the ID for this entity, or null
   * @param label the label for this entity, or null
   * @return the entity or null
   */
  @Deprecated
  public static OWLEntity getEntity(
      QuotedEntityChecker checker, IRI type, String id, String label) {

    IOHelper ioHelper = checker.getIOHelper();

    if (id != null && ioHelper != null) {
      IRI iri = ioHelper.createIRI(id);
      if (type == null) {
        type = IRI.create(OWL_CLASS);
      }
      String t = type.toString();
      switch (t) {
        case OWL_CLASS:
          return dataFactory.getOWLClass(iri);
        case OWL_ANNOTATION_PROPERTY:
          return dataFactory.getOWLAnnotationProperty(iri);
        case OWL_OBJECT_PROPERTY:
          return dataFactory.getOWLObjectProperty(iri);
        case OWL_DATA_PROPERTY:
          return dataFactory.getOWLDataProperty(iri);
        case OWL_DATATYPE:
          return dataFactory.getOWLDatatype(iri);
        default:
          return dataFactory.getOWLNamedIndividual(iri);
      }
    }

    if (label != null && type != null) {
      String t = type.toString();
      switch (t) {
        case OWL_CLASS:
          return checker.getOWLClass(label);
        case OWL_ANNOTATION_PROPERTY:
          return checker.getOWLAnnotationProperty(label);
        case OWL_OBJECT_PROPERTY:
          return checker.getOWLObjectProperty(label);
        case OWL_DATA_PROPERTY:
          return checker.getOWLDataProperty(label);
        case OWL_DATATYPE:
          return checker.getOWLDatatype(label);
        default:
          return checker.getOWLIndividual(label);
      }
    }

    if (label != null) {
      return checker.getOWLEntity(label);
    }

    return null;
  }

  /**
   * Return an IRI annotation for the given template string and string value. The template string
   * format is "AI [name]" and the value is the name of an entity or an IRI.
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param value the value for the annotation
   * @return a new annotation axiom with property and an IRI value
   * @throws RowParseException if the annotation property cannot be found or the IRI cannot be
   *     created
   */
  @Deprecated
  public static OWLAnnotation getIRIAnnotation(
      QuotedEntityChecker checker, String template, String value) throws Exception {
    IRI iri = checker.getIRI(value, true);
    if (iri == null) {
      return null;
    }
    return getIRIAnnotation(checker, template, iri);
  }

  /**
   * Return an IRI annotation for the given template string and IRI value. The template string
   * format is "AI [name]" and the value is an IRI.
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param value the IRI value for the annotation
   * @return a new annotation axiom with property and an IRI value
   * @throws RowParseException if the annotation property cannot be found
   */
  @Deprecated
  public static OWLAnnotation getIRIAnnotation(
      QuotedEntityChecker checker, String template, IRI value) throws Exception {
    String name = template.substring(2).trim();
    OWLAnnotationProperty property = getAnnotationProperty(checker, name);
    return dataFactory.getOWLAnnotation(property, value);
  }

  /**
   * Return a set of language tagged annotations for the given template and value. The template
   * string format is "AL [name]@[lang]" and the value is any string. Replaced by sets of
   * annotations to support splits.
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param value the value for the annotation
   * @return a new annotation with property and language tagged literal
   * @throws Exception if the annotation property cannot be found
   */
  @Deprecated
  public static OWLAnnotation getLanguageAnnotation(
      QuotedEntityChecker checker, String template, String value) throws Exception {
    OWLAnnotationProperty property = getAnnotationProperty(checker, template, "@", 0);
    String lang = template.substring(template.indexOf("@") + 1).trim();
    return dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(value, lang));
  }

  /**
   * Return a set of language tagged annotations for the given template and value(s). The template
   * string format is "AL [name]@[lang]" and the value is any string.
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param split the character to split values on
   * @param value the value for the annotation
   * @return a set of new annotation(s) with property and language tagged literal
   * @throws RowParseException if the annotation property cannot be found
   */
  @Deprecated
  public static Set<OWLAnnotation> getLanguageAnnotations(
      QuotedEntityChecker checker, String template, String split, String value) throws Exception {
    OWLAnnotationProperty property = getAnnotationProperty(checker, template, "@", 0);
    String lang = template.substring(template.indexOf("@") + 1).trim();

    Set<OWLAnnotation> annotations = new HashSet<>();
    List<String> allValues = getAllValues(value, split);

    for (String v : allValues) {
      annotations.add(dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(v, lang)));
    }

    return annotations;
  }

  /**
   * Get a set of OWLAxioms (subclass or equivalent) for an OWLClass. Supports axiom annotations.
   *
   * @deprecated TemplateOperation replaced with Template class
   * @param cls OWLClass to add axioms to
   * @param classType subclass or equivalent
   * @param classExpressions Set of OWLClassExpressions
   * @param annotatedExpressions Map of annotated OWLClassExpressions and the Set of OWLAnnotations
   * @return Set of OWLAxioms, or null if classType is not subclass or equivalent
   * @throws Exception if classType is not subclass or equivalent
   */
  @Deprecated
  public static Set<OWLAxiom> getLogicalAxioms(
      OWLClass cls,
      String classType,
      Set<OWLClassExpression> classExpressions,
      Map<OWLClassExpression, Set<OWLAnnotation>> annotatedExpressions)
      throws Exception {
    Set<OWLAxiom> axioms = new HashSet<>();
    switch (classType) {
      case "subclass":
        for (OWLClassExpression expression : classExpressions) {
          axioms.add(dataFactory.getOWLSubClassOfAxiom(cls, expression));
        }
        for (Entry<OWLClassExpression, Set<OWLAnnotation>> annotatedEx :
            annotatedExpressions.entrySet()) {
          axioms.add(
              dataFactory.getOWLSubClassOfAxiom(cls, annotatedEx.getKey(), annotatedEx.getValue()));
        }
        return axioms;
      case "equivalent":
        // Since it's an intersection, all annotations will be added to the same axiom
        Set<OWLAnnotation> annotations = new HashSet<>();
        for (Entry<OWLClassExpression, Set<OWLAnnotation>> annotatedEx :
            annotatedExpressions.entrySet()) {
          classExpressions.add(annotatedEx.getKey());
          annotations.addAll(annotatedEx.getValue());
        }
        OWLObjectIntersectionOf intersection =
            dataFactory.getOWLObjectIntersectionOf(classExpressions);
        OWLAxiom axiom;
        if (!annotations.isEmpty()) {
          axiom = dataFactory.getOWLEquivalentClassesAxiom(cls, intersection, annotations);
        } else {
          axiom = dataFactory.getOWLEquivalentClassesAxiom(cls, intersection);
        }
        return Sets.newHashSet(axiom);
      default:
        throw new ColumnException(String.format(classTypeError, classType));
    }
  }

  /**
   * Return a string annotation for the given template string and value. Replaced by sets of
   * annotations to support splits.
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param value the value for the annotation
   * @return a new annotation with property and string literal value
   * @throws Exception if the annotation property cannot be found
   */
  @Deprecated
  public static OWLAnnotation getStringAnnotation(
      QuotedEntityChecker checker, String template, String value) throws Exception {
    String name = template.substring(1).trim();
    OWLAnnotationProperty property = getAnnotationProperty(checker, name, 0);
    return dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(value));
  }

  /**
   * Return a set of string annotations for the given template string and value(s).
   *
   * @param checker used to resolve the annotation property
   * @param template the template string
   * @param split the character to split values on
   * @param value the value for the annotation
   * @return a set of new annotation(s) with property and string literal value
   * @throws RowParseException if the annotation property cannot be found
   */
  @Deprecated
  public static Set<OWLAnnotation> getStringAnnotations(
      QuotedEntityChecker checker, String template, String split, String value) throws Exception {

    OWLAnnotationProperty property;
    if (template.equals("LABEL")) {
      // Handle special LABEL case
      property = dataFactory.getRDFSLabel();
    } else {
      String name = template.substring(1).trim();
      property = getAnnotationProperty(checker, name);
    }

    Set<OWLAnnotation> annotations = new HashSet<>();
    if (split != null) {
      String[] values = value.split(Pattern.quote(split));
      for (String v : values) {
        annotations.add(dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(v)));
      }
    } else {
      annotations.add(dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(value)));
    }

    return annotations;
  }

  /**
   * Return a set of typed annotations for the given template string and value. The template string
   * format is "AT [name]^^[datatype]" and the value is any string. Replaced by sets of annotations
   * to support splits.
   *
   * @param checker used to resolve the annotation property and datatype
   * @param template the template string
   * @param value the value for the annotation
   * @return a new annotation with property and typed literal value
   * @throws Exception if the annotation property cannot be found
   */
  @Deprecated
  public static OWLAnnotation getTypedAnnotation(
      QuotedEntityChecker checker, String template, String value) throws Exception {
    OWLAnnotationProperty property = getAnnotationProperty(checker, template, "^^", 0);
    String typeName = template.substring(template.indexOf("^^") + 2).trim();
    OWLDatatype datatype = getDatatype(checker, typeName);
    return dataFactory.getOWLAnnotation(property, dataFactory.getOWLLiteral(value, datatype));
  }

  /**
   * Given a template string, a cell value, and an empty list, fill the list with any number of
   * values based on a SPLIT character, then return the template string without SPLIT. If there are
   * no SPLITs, only add the original cell to the values.
   *
   * @deprecated TemplateOperation replaced with Template class
   * @param template template string
   * @param cell cell contents
   * @param values empty list to fill
   * @return template string without SPLIT
   */
  @Deprecated
  public static String processSplit(String template, String cell, List<String> values) {
    // If the template contains SPLIT=X,
    // then split the cell value
    // and remove that string from the template.
    Pattern splitter = Pattern.compile("SPLIT=(\\S+)");
    Matcher matcher = splitter.matcher(template);
    if (matcher.find()) {
      Pattern split = Pattern.compile(matcher.group(1));
      values.addAll(Arrays.asList(split.split(Pattern.quote(cell))));
      template = matcher.replaceAll("").trim();
    } else {
      values.add(cell);
    }
    return template;
  }
}
