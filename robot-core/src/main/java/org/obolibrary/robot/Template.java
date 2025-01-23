package org.obolibrary.robot;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.obolibrary.robot.exceptions.ColumnException;
import org.obolibrary.robot.exceptions.RowParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxClassExpressionParser;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author <a href="mailto:rctauber@gmail.com">Becky Tauber</a> */
public class Template {

  /** Logger */
  private static final Logger logger = LoggerFactory.getLogger(Template.class);

  /** Template IOHelper to resolve prefixes. */
  private IOHelper ioHelper;

  /** Template QuotedEntityChecker to get entities and IRIs by label. */
  private QuotedEntityChecker checker;

  /** Manchester Syntax parser to parse class expressions. */
  private ManchesterOWLSyntaxClassExpressionParser parser;

  /** Set of axioms generated from template. */
  private Set<OWLAxiom> axioms;

  /** Name of the table. */
  private String name;

  /** Location of IDs (ID). */
  private int idColumn = -1;

  /** Location of labels (LABEL, A rdfs:label, A label). */
  private int labelColumn = -1;

  /** Location of entity types (TYPE). */
  private int typeColumn = -1;

  /** Location of class types (CLASS_TYPE). */
  private int classTypeColumn = -1;

  /** Location of property types (PROPERTY_TYPE). */
  private int propertyTypeColumn = -1;

  /** Location of property characteristic (CHARACTERISTIC). */
  private int characteristicColumn = -1;

  /** Location of individual types (INDIVIDUAL_TYPE). */
  private int individualTypeColumn = -1;

  /** Character to split property characteristics on. */
  private String characteristicSplit = null;

  /** Character to split generic types on. */
  private String typeSplit = null;

  /** List of human-readable template headers. */
  private List<String> headers;

  /** List of ROBOT template strings. */
  private List<String> templates;

  /** All other rows of the table (does not include headers and template strings). */
  private List<List<String>> tableRows;

  /** Row number tracker. Start with 2 to skip headers. */
  private int rowNum = 2;

  /** Shared data factory. */
  private final OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();

  /** Namespace for error messages. */
  private static final String NS = "template#";

  /** Error message when an annotation property has a characteristic. */
  private static final String annotationPropertyCharacteristicError =
      NS
          + "ANNOTATION PROPERTY CHARACTERISTIC ERROR annotation property '%s' should not have any characteristics at line %d, column %d in table \"%s\"";

  /** Error message when an annotation property gets a property type other than subproperty. */
  private static final String annotationPropertyTypeError =
      NS
          + "ANNOTATION PROPERTY TYPE ERROR annotation property %s type '%s' must be 'subproperty' at row %d, column %d in table \"%s\".";

  /** Error message when an invalid class type is provided. */
  private static final String classTypeError =
      NS + "CLASS TYPE ERROR class %s has unknown type '%s' at row %d, column %d in table \"%s\".";

  /** Error message when CLASS_TYPE has a SPLIT. */
  private static final String classTypeSplitError =
      NS
          + "CLASS TYPE SPLIT ERROR the SPLIT functionality should not be used for CLASS_TYPE in column %d in table \"%s\".";

  /**
   * Error message when a template column does not have a header. Expects: column number, table name
   */
  private static final String columnMismatchError =
      NS
          + "COLUMN MISMATCH ERROR the template string in column %d must have a corresponding header in table \"%s\"";

  /** Error message when a data property has a characteristic other than 'functional'. */
  private static final String dataPropertyCharacteristicError =
      NS
          + "DATA PROPERTY CHARACTERISTIC ERROR data property '%s' can only have characteristic 'functional' at line %d, column %d in table \"%s\".";

  /** Error message when an invalid individual type is provided. */
  private static final String individualTypeError =
      NS
          + "INDIVIDUAL TYPE ERROR individual %s has unknown type '%s' at row %d, column %d in table \"%s\".";

  /** Error message when INDIVIDUAL_TYPE has a SPLIT. */
  private static final String individualTypeSplitError =
      NS
          + "INDIVIDUAL TYPE SPLIT ERROR the SPLIT functionality should not be used for INDIVIDUAL_TYPE in column %d in table \"%s\".";

  /** Error message when an invalid property type is provided. */
  private static final String propertyTypeError =
      NS
          + "PROPERTY TYPE ERROR property %s has unknown type '%s' at row %d, column %d in table \"%s\".";

  /** Error message when more than one logical type is used in PROPERTY_TYPE. */
  private static final String propertyTypeSplitError =
      NS
          + "PROPERTY TYPE SPLIT ERROR the SPLIT functionality should not be used for PROPERTY_TYPE in column %d in table \"%s\".";

  /** Error message when property characteristic not valid. */
  private static final String unknownCharacteristicError =
      NS
          + "UNKNOWN CHARACTERISTIC ERROR property '%s' has unknown characteristic '%s' at line %d, column %d in table \"%s\".";

  /**
   * Error message when a template cannot be understood. Expects: table name, column number, column
   * name, template.
   */
  private static final String unknownTemplateError =
      NS
          + "UNKNOWN TEMPLATE ERROR could not interpret template string \"%4$s\" for column %2$d (\"%3$s\") in table \"%1$s\".";

  private static final String unknownEntityError =
      NS
          + "UNKNOWN ENTITY ERROR could not interpret '%1$s' in row %2$d, column %3$d (\"%4$s\") in table \"%5$s\".";

  private static final List<String> validClassTypes =
      new ArrayList<>(Arrays.asList("subclass", "disjoint", "equivalent"));

  /**
   * Given a template name and a list of rows, create a template object with a new IOHelper and
   * QuotedEntityChecker. The rows are added to the object, new labels from the rows are added to
   * the checker, and a Manchester Syntax parser is created.
   *
   * @param name template name
   * @param rows list of rows (lists)
   * @throws Exception on issue creating IOHelper or adding table to template object
   */
  public Template(@Nonnull String name, @Nonnull List<List<String>> rows) throws Exception {
    this.name = name;
    this.ioHelper = new IOHelper();
    tableRows = new ArrayList<>();
    templates = new ArrayList<>();
    headers = new ArrayList<>();
    axioms = new HashSet<>();

    checker = new QuotedEntityChecker();
    checker.setIOHelper(this.ioHelper);
    checker.addProvider(new SimpleShortFormProvider());

    // Add the contents of the tableRows
    addTable(rows);
    addEntities();
    createParser();
  }

  /**
   * Given a template name, a list of rows, and an IOHelper, create a template object with a new
   * QuotedEntityChecker. The rows are added to the object, new labels from the rows are added to
   * the checker, and a Manchester Syntax parser is created.
   *
   * @param name template name
   * @param rows list of rows (lists)
   * @param ioHelper IOHelper to resolve prefixes
   * @throws Exception on issue adding table to template object
   */
  public Template(@Nonnull String name, @Nonnull List<List<String>> rows, IOHelper ioHelper)
      throws Exception {
    this.name = name;
    this.ioHelper = ioHelper;
    tableRows = new ArrayList<>();
    templates = new ArrayList<>();
    headers = new ArrayList<>();
    axioms = new HashSet<>();

    checker = new QuotedEntityChecker();
    checker.setIOHelper(this.ioHelper);
    checker.addProvider(new SimpleShortFormProvider());
    checker.addProperty(dataFactory.getRDFSLabel());

    // Add the contents of the tableRows
    addTable(rows);
    addEntities();
    createParser();
  }

  /**
   * Given a template name, a list of rows, and an input ontology, create a template object with a
   * new IOHelper and QuotedEntityChecker populated by the input ontology. The rows are added to the
   * object, new labels from the rows are added to the checker, and a Manchester Syntax parser is
   * created.
   *
   * @param name template name
   * @param rows list of rows (lists)
   * @param inputOntology OWLOntology to get labels of entities for QuotedEntityChecker
   * @throws Exception on issue creating IOHelper or adding table to template object
   */
  public Template(@Nonnull String name, @Nonnull List<List<String>> rows, OWLOntology inputOntology)
      throws Exception {
    this.name = name;
    ioHelper = new IOHelper();
    tableRows = new ArrayList<>();
    templates = new ArrayList<>();
    headers = new ArrayList<>();
    axioms = new HashSet<>();

    checker = new QuotedEntityChecker();
    checker.setIOHelper(this.ioHelper);
    checker.addProvider(new SimpleShortFormProvider());
    checker.addProperty(dataFactory.getRDFSLabel());
    if (inputOntology != null) {
      checker.addAll(inputOntology);
    }

    // Add the contents of the tableRows
    addTable(rows);
    addEntities();
    createParser();
  }

  /**
   * Given a template name, a list of rows, an input ontology, and an IOHelper, create a template
   * object with a new QuotedEntityChecker with the IOHelper populated by the input ontology. The
   * rows are added to the object, new labels from the rows are added to the checker, and a
   * Manchester Syntax parser is created.
   *
   * @param name template name
   * @param rows list of rows (lists)
   * @param inputOntology OWLOntology to get labels of entities for QuotedEntityChecker
   * @param ioHelper IOHelper to resolve prefixes
   * @throws Exception on issue adding table to template object
   */
  public Template(
      @Nonnull String name,
      @Nonnull List<List<String>> rows,
      OWLOntology inputOntology,
      IOHelper ioHelper)
      throws Exception {
    this.name = name;
    this.ioHelper = ioHelper;
    tableRows = new ArrayList<>();
    templates = new ArrayList<>();
    headers = new ArrayList<>();
    axioms = new HashSet<>();

    checker = new QuotedEntityChecker();
    checker.setIOHelper(this.ioHelper);
    checker.addProvider(new SimpleShortFormProvider());
    checker.addProperty(dataFactory.getRDFSLabel());
    if (inputOntology != null) {
      checker.addAll(inputOntology);
    }

    // Add the contents of the tableRows
    addTable(rows);
    addEntities();
    createParser();
  }

  /**
   * Given a template name, a list of rows, an IOHelper, and a QuotedEntityChecker, create a
   * template object. The rows are added to the object, new labels from the rows are added to the
   * checker, and a Manchester Syntax parser is created.
   *
   * @param name template name
   * @param rows list of rows (lists)
   * @param inputOntology OWLOntology to get labels of entities for QuotedEntityChecker
   * @param ioHelper IOHelper to resolve prefixes
   * @param checker QuotedEntityChecker to get entities by label
   * @throws Exception on issue adding table to template object
   */
  public Template(
      @Nonnull String name,
      @Nonnull List<List<String>> rows,
      OWLOntology inputOntology,
      IOHelper ioHelper,
      QuotedEntityChecker checker)
      throws Exception {
    this.name = name;
    this.ioHelper = ioHelper;
    if (checker == null) {
      this.checker = new QuotedEntityChecker();
      this.checker.setIOHelper(this.ioHelper);
      this.checker.addProvider(new SimpleShortFormProvider());
      this.checker.addProperty(dataFactory.getRDFSLabel());
    } else {
      this.checker = checker;
    }
    tableRows = new ArrayList<>();
    templates = new ArrayList<>();
    headers = new ArrayList<>();
    axioms = new HashSet<>();

    if (inputOntology != null) {
      this.checker.addAll(inputOntology);
    }

    // Add the contents of the tableRows
    addTable(rows);
    addEntities();
    createParser();
    parser.setOWLEntityChecker(this.checker);
  }

  /**
   * Return the QuotedEntityChecker.
   *
   * @return QuotedEntityChecker
   */
  public QuotedEntityChecker getChecker() {
    return checker;
  }

  /**
   * Generate an OWLOntology based on the rows of the template.
   *
   * @return new OWLOntology
   * @throws Exception on issue parsing rows to axioms or creating new ontology
   */
  public OWLOntology generateOutputOntology() throws Exception {
    return generateOutputOntology(null, false, null);
  }

  /**
   * Generate an OWLOntology based on the rows of the template.
   *
   * @param outputIRI IRI for final ontology
   * @param force if true, do not exit on errors
   * @return new OWLOntology
   * @throws Exception on issue parsing rows to axioms or creating new ontology
   */
  public OWLOntology generateOutputOntology(String outputIRI, boolean force) throws Exception {
    return generateOutputOntology(outputIRI, force, null);
  }

  /**
   * Generate an OWLOntology with given IRI based on the rows of the template.
   *
   * @param outputIRI IRI for final ontology
   * @param force if true, do not exit on errors
   * @param errorsPath path to errors table when force=true
   * @return new OWLOntology
   * @throws Exception on issue parsing rows to axioms or creating new ontology
   */
  public OWLOntology generateOutputOntology(String outputIRI, boolean force, String errorsPath)
      throws Exception {
    boolean exitOnException = !force && errorsPath == null;
    // Set to true on first exception
    boolean hasException = false;

    List<String[]> errors = new ArrayList<>();
    errors.add(new String[] {"table", "cell", "rule ID", "message"});
    for (List<String> row : tableRows) {
      try {
        processRow(row);
      } catch (RowParseException e) {
        // Unless specified, fail on the first exception
        if (exitOnException) {
          throw e;
        }

        // otherwise print exceptions as they show up
        hasException = true;
        String message = e.getMessage().substring(e.getMessage().indexOf("#") + 1);
        logger.error(message);

        // Only add to errors table if we have a row & col num
        if (e.rowNum != -1 && e.colNum != -1) {
          errors.add(
              new String[] {this.name, IOHelper.cellToA1(e.rowNum, e.colNum), e.ruleID, message});
        }
      }
    }

    if (hasException) {
      logger.warn("Ontology created from template with errors");
      if (errorsPath != null) {
        // Write errors to file
        IOHelper.writeTable(errors, errorsPath);
        // Check if we want to force the output, otherwise exit now (as error)
        if (!force) {
          System.exit(1);
        }
      }
    }

    // Create a new ontology object to add axioms to
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLOntology outputOntology;
    if (outputIRI != null) {
      IRI iri = IRI.create(outputIRI);
      outputOntology = manager.createOntology(iri);
    } else {
      outputOntology = manager.createOntology();
    }

    manager.addAxioms(outputOntology, axioms);

    return outputOntology;
  }

  public int getRowNum() {
    return rowNum;
  }

  public void setRowNum(int rowNum) {
    this.rowNum = rowNum;
  }
  /**
   * Given a list of rows for a table, first validate the headers and template strings. Then, get
   * the location of important columns (e.g. IDs and labels). Finally, add all template rows to the
   * object.
   *
   * @param rows list of rows (lists)
   * @throws Exception on malformed template
   */
  private void addTable(List<List<String>> rows) throws Exception {
    // Get and validate headers
    headers = rows.get(0);
    templates = rows.get(1);

    for (int column = 0; column < templates.size(); column++) {
      String template = templates.get(column).trim();
      if (template.isEmpty()) {
        // If the template is empty, skip this column
        continue;
      }

      // Validate that there's a header in this column
      // It's OK if there's a header without a template
      String header;
      try {
        header = headers.get(column).trim();
      } catch (IndexOutOfBoundsException e) {
        // Template row is longer than header row
        // Which means there is at least one header missing
        throw new ColumnException(String.format(columnMismatchError, column, name));
      }
      if (header.isEmpty()) {
        // Template string is not empty
        // Header string is empty
        throw new ColumnException(String.format(columnMismatchError, column, name));
      }

      // Validate the template string
      if (!TemplateHelper.validateTemplateString(template)) {
        throw new ColumnException(
            String.format(unknownTemplateError, name, column, headers.get(column), template));
      }

      // Get the location of important columns
      // If it is an annotation, check if it resolves to RDFS label
      if (template.startsWith("A ")) {
        String property = template.substring(2);
        maybeSetLabelColumn(property, column);
      } else if (template.startsWith("AT ")) {
        String property;
        if (template.contains("^^")) {
          property = template.substring(3, template.indexOf("^^")).trim();
        } else {
          property = template.substring(3).trim();
        }
        maybeSetLabelColumn(property, column);
      } else if (template.startsWith("AL ")) {
        String property;
        if (template.contains("@")) {
          property = template.substring(3, template.indexOf("@")).trim();
        } else {
          property = template.substring(3).trim();
        }
        maybeSetLabelColumn(property, column);
      } else if (template.startsWith("AI ")) {
        String property = template.substring(3);
        maybeSetLabelColumn(property, column);
      } else if (template.equals("ID")) {
        // Unique identifier (CURIE, IRI...)
        idColumn = column;
      } else if (template.equals("LABEL")) {
        // Label identifier
        labelColumn = column;
      } else if (template.startsWith("TYPE")) {
        // Entity type
        typeColumn = column;
        if (template.contains("SPLIT=")) {
          typeSplit = template.substring(template.indexOf("SPLIT=") + 6);
        }
      } else if (template.startsWith("CLASS_TYPE")) {
        // Class expression type
        classTypeColumn = column;
        if (template.contains("SPLIT=")) {
          // Classes should only have one class type
          throw new ColumnException(String.format(classTypeSplitError, column, name));
        }
      } else if (template.startsWith("PROPERTY_TYPE")) {
        // Property expression type
        propertyTypeColumn = column;
        if (template.contains("SPLIT=")) {
          // Instances should only have one individual type
          throw new ColumnException(String.format(propertyTypeSplitError, column, name));
        }
      } else if (template.startsWith("INDIVIDUAL_TYPE")) {
        // Individual expression type
        individualTypeColumn = column;
        if (template.contains("SPLIT=")) {
          // Instances should only have one individual type
          throw new ColumnException(String.format(individualTypeSplitError, column, name));
        }
      } else if (template.startsWith("CHARACTERISTIC")) {
        // Property characteristic
        characteristicColumn = column;
        if (template.contains("SPLIT=")) {
          characteristicSplit = template.substring(template.indexOf("SPLIT=") + 6);
        }
      }
    }

    // Each template needs a way to identify the entities
    // Without one, we cannot continue
    if (idColumn == -1 && labelColumn == -1) {
      throw new ColumnException(
          "Template row must include an \"ID\" or \"LABEL\" column in table: " + name);
    }

    // Add the rest of the tableRows to Template
    for (int rowNum = 2; rowNum < rows.size(); rowNum++) {
      List<String> row = rows.get(rowNum);
      if (idColumn != -1) {
        if (row.size() > idColumn && row.get(idColumn).trim().equals("")) {
          continue;
        } else if (row.size() <= idColumn) {
          continue;
        }
      } else if (labelColumn != -1) {
        if (row.size() > labelColumn && row.get(labelColumn).equals("")) {
          continue;
        } else if (row.size() <= labelColumn) {
          continue;
        }
      }
      tableRows.add(row);
    }
  }

  /** Add the entities from the rows of the template to the QuotedEntityChecker. */
  private void addEntities() {
    for (List<String> row : tableRows) {
      addEntity(row);
    }
  }

  /** Add the entity from this row of the template to the QuotedEntityChecker. */
  private void addEntity(List<String> row) {
    String id = null;
    try {
      id = row.get(idColumn);
    } catch (IndexOutOfBoundsException e) {
      // ignore
    }

    if (id == null) {
      return;
    }

    String label = null;
    try {
      label = row.get(labelColumn);
    } catch (IndexOutOfBoundsException e) {
      // ignore
    }

    String type = null;
    if (typeColumn != -1) {
      try {
        type = row.get(typeColumn);
      } catch (IndexOutOfBoundsException e) {
        // ignore
      }
    }
    if (type == null || type.trim().isEmpty()) {
      type = "class";
    }

    IRI iri = ioHelper.createIRI(id);
    if (iri == null) {
      iri = IRI.create(id);
    }

    // Try to resolve a CURIE
    IRI typeIRI = ioHelper.createIRI(type);

    // Set to IRI string or to type string
    String typeOrIRI = type;
    if (typeIRI != null) {
      typeOrIRI = typeIRI.toString();
    }

    // Check against builtin types (ignore case), otherwise treat as individual
    OWLEntity entity;
    String lowerCaseType = typeOrIRI.toLowerCase();
    switch (lowerCaseType) {
      case "":
      case "http://www.w3.org/2002/07/owl#class":
      case "class":
        entity = dataFactory.getOWLEntity(EntityType.CLASS, iri);
        break;

      case "http://www.w3.org/2002/07/owl#objectproperty":
      case "object property":
        entity = dataFactory.getOWLEntity(EntityType.OBJECT_PROPERTY, iri);
        break;

      case "http://www.w3.org/2002/07/owl#dataproperty":
      case "data property":
        entity = dataFactory.getOWLEntity(EntityType.DATA_PROPERTY, iri);
        break;

      case "http://www.w3.org/2002/07/owl#annotationproperty":
      case "annotation property":
        entity = dataFactory.getOWLEntity(EntityType.ANNOTATION_PROPERTY, iri);
        break;

      case "http://www.w3.org/2002/07/owl#datatype":
      case "datatype":
        entity = dataFactory.getOWLEntity(EntityType.DATATYPE, iri);
        break;

      case "http://www.w3.org/2002/07/owl#individual":
      case "individual":
      case "http://www.w3.org/2002/07/owl#namedindividual":
      case "named individual":
      default:
        // Assume type is an individual (checked later)
        entity = dataFactory.getOWLEntity(EntityType.NAMED_INDIVIDUAL, iri);
        break;
    }

    if (id != null) {
      checker.add(entity, id);
    }
    if (label != null) {
      checker.add(entity, label);
    }
  }

  /** Create a Manchester Syntax parser from the OWLDataFactory and QuotedEntityChecker. */
  private void createParser() {
    this.parser = new ManchesterOWLSyntaxClassExpressionParser(dataFactory, checker);
  }

  /**
   * Process each of the table rows. First, get an entity based on ID or label. If the template
   * contains an ID column, but it is empty, skip that row. If it does not contain an ID column,
   * skip if the label is empty. Add axioms based on the entity type (class, object property, data
   * property, annotation property, datatype, or individual).
   *
   * @throws Exception on issue creating axioms from template
   */
  private void processRow(List<String> row) throws Exception {
    rowNum++;

    String id = null;
    try {
      id = row.get(idColumn);
      if (id.trim().isEmpty()) {
        id = null;
      }
    } catch (IndexOutOfBoundsException e) {
      // ignore
    }
    String label = null;
    try {
      label = row.get(labelColumn);
      if (label.trim().isEmpty()) {
        label = null;
      }
    } catch (IndexOutOfBoundsException e) {
      // ignore
    }
    String type = null;
    try {
      type = row.get(typeColumn);
      if (type.trim().isEmpty()) {
        type = null;
      }
    } catch (IndexOutOfBoundsException e) {
      // ignore
    }

    // Skip if no ID and no label
    if (id == null && label == null) {
      return;
    }

    if (type == null) {
      // Try to guess the type from already existing entities
      if (label != null) {
        OWLEntity e = checker.getOWLEntity(label);
        if (e != null) {
          type = e.getEntityType().getIRI().toString();
        }
      } else {
        OWLEntity e = checker.getOWLEntity(id);
        if (e != null) {
          type = e.getEntityType().getIRI().toString();
        }
      }
      // If the entity type is not defined
      // and the entity does not already exist
      // default to class
      if (type == null) {
        type = "class";
      }
    }

    // Create an IRI for the subject of this row
    IRI iri = getIRI(id, label);
    if (iri == null) {
      // Unable to create an IRI from the entity
      if (idColumn != -1) {
        if (id != null) {
          // Has an ID column with contents that could not be resolved
          throw new RowParseException(
              String.format(unknownEntityError, id, rowNum, idColumn + 1, "ID", name),
              rowNum,
              idColumn + 1,
              id);
        } else {
          // Has an ID column, but it's empty
          return;
        }
      } else {
        // No ID column and label could not be resolved
        throw new RowParseException(
            String.format(unknownEntityError, label, rowNum, labelColumn + 1, "LABEL", name),
            rowNum,
            labelColumn + 1,
            label);
      }
    }

    // Try to resolve a CURIE
    IRI typeIRI = ioHelper.createIRI(type);

    // Set to IRI string or to type string
    String typeOrIRI = type;
    if (typeIRI != null) {
      typeOrIRI = typeIRI.toString();
    }

    // Check against builtin types (ignore case), otherwise treat as individual
    String lowerCaseType = typeOrIRI.toLowerCase();
    switch (lowerCaseType) {
      case "http://www.w3.org/2002/07/owl#class":
      case "class":
        addClassAxioms(iri, row);
        break;

      case "http://www.w3.org/2002/07/owl#objectproperty":
      case "object property":
        addObjectPropertyAxioms(iri, row);
        break;

      case "http://www.w3.org/2002/07/owl#dataproperty":
      case "data property":
        addDataPropertyAxioms(iri, row);
        break;

      case "http://www.w3.org/2002/07/owl#annotationproperty":
      case "annotation property":
        addAnnotationPropertyAxioms(iri, row);
        break;

      case "http://www.w3.org/2002/07/owl#datatype":
      case "datatype":
        addDatatypeAxioms(iri, row);
        break;

      case "http://www.w3.org/2002/07/owl#individual":
      case "individual":
      case "http://www.w3.org/2002/07/owl#namedindividual":
      case "named individual":
      default:
        // This is a bit unsafe imo, for example in the case of where the datatype turns out to be
        // http://www.w3.org/2002/07/owl#DatatypeProperty""
        addIndividualAxioms(iri, row);
        break;
    }
  }

  /* CLASS AXIOMS */

  /**
   * Given a class IRI and the row containing the class details, generate class axioms.
   *
   * @param iri class IRI
   * @param row list of template values for given class
   * @throws Exception on issue creating class axioms from template
   */
  private void addClassAxioms(IRI iri, List<String> row) throws Exception {
    if (iri == null) {
      return;
    }
    // Add the declaration
    OWLClass cls = dataFactory.getOWLClass(iri);
    OWLDeclarationAxiom ax = dataFactory.getOWLDeclarationAxiom(cls);
    axioms.add(ax);

    String classType = null;
    if (classTypeColumn != -1) {
      try {
        classType = row.get(classTypeColumn);
      } catch (IndexOutOfBoundsException e) {
        // do nothing
      }
    }
    if (classType == null || classType.trim().isEmpty()) {
      classType = "subclass";
    } else {
      classType = classType.trim().toLowerCase();
    }

    if (!validClassTypes.contains(classType)) {
      // Unknown class type
      throw new RowParseException(
          String.format(
              classTypeError,
              cls.getIRI().getShortForm(),
              classType,
              rowNum,
              classTypeColumn + 1,
              name),
          rowNum,
          classTypeColumn + 1,
          classType);
    }

    // Iterate through all columns and add annotations as we go
    // Also collect any class expressions that will be used in logical definitions
    // We collect all of these together so that equivalent expressions can be made into an
    // intersection
    // Instead of adding them in as we iterate through
    Map<Integer, Set<OWLClassExpression>> subclassExpressionColumns = new HashMap<>();
    Map<Integer, Set<OWLClassExpression>> equivalentExpressionColumns = new HashMap<>();
    Map<Integer, Set<OWLClassExpression>> disjointExpressionColumns = new HashMap<>();
    for (int column = 0; column < templates.size(); column++) {
      String template = templates.get(column);
      String value = null;
      try {
        value = row.get(column);
      } catch (IndexOutOfBoundsException e) {
        // do nothing
      }

      if (value == null || value.trim().isEmpty()) {
        continue;
      }
      if (template.equals("AP")) {
        // split multi annotation string
        // Ex meks:p-0000000049 "(U)"|meks:p-0000000050 "KECF6"|meks:p-0000000051 "SATL,COMMO,RODNIK
        CharacterIterator it = new StringCharacterIterator(value);

        // Iterate and print current character
        String token = new String();
        List<String> annotation_list = new ArrayList<>();
        while (it.current() != CharacterIterator.DONE) {
          if (it.current() != '|') {
            token += it.current();
          } else {
            annotation_list.add(token);
            token = "";
          }
          it.next();
        }
        annotation_list.add(token);
        // ListString annotation_list[] = value.split("|");
        Set<OWLAnnotation> annotations = new HashSet<>();
        for (String annotation : annotation_list) {
          annotations.addAll(getAnnotations(template, annotation, row, column));
        }
        // MultiAnnotation
        for (OWLAnnotation annotation : annotations) {
          axioms.add(dataFactory.getOWLAnnotationAssertionAxiom(iri, annotation));
        }
      } else if (template.startsWith("A") || template.startsWith("LABEL")) {
        // Handle class annotations
        Set<OWLAnnotation> annotations = getAnnotations(template, value, row, column);
        for (OWLAnnotation annotation : annotations) {
          axioms.add(dataFactory.getOWLAnnotationAssertionAxiom(iri, annotation));
        }
      } else if (template.startsWith("SC")) {
        // Subclass expression
        subclassExpressionColumns.put(
            column,
            TemplateHelper.getClassExpressions(
                name, checker, parser, template, value, rowNum, column));
      } else if (template.startsWith("EC")) {
        // Equivalent expression
        equivalentExpressionColumns.put(
            column,
            TemplateHelper.getClassExpressions(
                name, checker, parser, template, value, rowNum, column));
      } else if (template.startsWith("DC")) {
        // Disjoint expression
        disjointExpressionColumns.put(
            column,
            TemplateHelper.getClassExpressions(
                name, checker, parser, template, value, rowNum, column));
      } else if (template.startsWith("C") && !template.startsWith("CLASS_TYPE")) {
        // Use class type to determine what to do with the expression
        switch (classType) {
          case "subclass":
            subclassExpressionColumns.put(
                column,
                TemplateHelper.getClassExpressions(
                    name, checker, parser, template, value, rowNum, column));
            break;
          case "equivalent":
            equivalentExpressionColumns.put(
                column,
                TemplateHelper.getClassExpressions(
                    name, checker, parser, template, value, rowNum, column));
            break;
          case "disjoint":
            disjointExpressionColumns.put(
                column,
                TemplateHelper.getClassExpressions(
                    name, checker, parser, template, value, rowNum, column));
            break;
          default:
            break;
        }
      }
    }

    // Add the axioms
    if (!subclassExpressionColumns.isEmpty()) {
      addSubClassAxioms(cls, subclassExpressionColumns, row);
    }
    if (!equivalentExpressionColumns.isEmpty()) {
      addEquivalentClassesAxioms(cls, equivalentExpressionColumns, row);
    }
    if (!disjointExpressionColumns.isEmpty()) {
      addDisjointClassAxioms(cls, disjointExpressionColumns, row);
    }
  }

  /**
   * Given an OWLClass, a map of column number to class expressions, and the row containing the
   * class details, generate subClassOf axioms for the class where the parents are the class
   * expressions. Maybe annotate the axioms.
   *
   * @param cls OWLClass to create subClassOf axioms for
   * @param expressionColumns map of column numbers to sets of parent class expressions
   * @param row list of template values for given class
   * @throws Exception on issue getting axiom annotations
   */
  private void addSubClassAxioms(
      OWLClass cls, Map<Integer, Set<OWLClassExpression>> expressionColumns, List<String> row)
      throws Exception {
    // Generate axioms
    for (int column : expressionColumns.keySet()) {
      // Maybe get an annotation on the expression
      Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);
      Set<OWLClassExpression> exprs = expressionColumns.get(column);
      // Each expression will be its own subclass statement
      for (OWLClassExpression expr : exprs) {
        axioms.add(dataFactory.getOWLSubClassOfAxiom(cls, expr, axiomAnnotations));
      }
    }
  }

  /**
   * Given an OWLClass, a map of column number to class expressions, and the row for this class,
   * generate an equivalentClasses axiom for the class where the equivalent is the intersection of
   * the provided class expressions. Maybe annotate the axioms.
   *
   * @param cls OWLClass to create equivalentClasses axiom for
   * @param expressionColumns map of column number to equivalent class expression
   * @param row list of template values for given class
   * @throws Exception on issue getting axiom annotations
   */
  private void addEquivalentClassesAxioms(
      OWLClass cls, Map<Integer, Set<OWLClassExpression>> expressionColumns, List<String> row)
      throws Exception {
    Set<OWLAnnotation> axiomAnnotations = new HashSet<>();
    Set<OWLClassExpression> expressions = new HashSet<>();
    for (int column : expressionColumns.keySet()) {
      // Maybe get an annotation on the expression (all annotations will be on the one intersection)
      axiomAnnotations.addAll(maybeGetAxiomAnnotations(row, column));
      // Add all expressions to the set of expressions
      expressions.addAll(expressionColumns.get(column));
    }

    if (expressions.size() == 1) {
      // Create a single equivalent class axiom (no intersection)
      axioms.add(
          dataFactory.getOWLEquivalentClassesAxiom(
              cls, expressions.iterator().next(), axiomAnnotations));
    } else if (expressions.size() > 1) {
      // Create the axiom as an intersection of the provided expressions
      OWLObjectIntersectionOf intersection = dataFactory.getOWLObjectIntersectionOf(expressions);
      axioms.add(dataFactory.getOWLEquivalentClassesAxiom(cls, intersection, axiomAnnotations));
    }
  }

  /**
   * Given an OWLClass, a map of column number to class expressions, and the row containing the
   * class details, generate disjointClasses axioms for the class where the disjoints are the class
   * expressions. Maybe annotate the axioms.
   *
   * @param cls OWLClass to create disjointClasses axioms for
   * @param expressionColumns map of column number to equivalent class expression
   * @param row list of template values for given class
   * @throws Exception on issue getting axiom annotations
   */
  private void addDisjointClassAxioms(
      OWLClass cls, Map<Integer, Set<OWLClassExpression>> expressionColumns, List<String> row)
      throws Exception {
    for (int column : expressionColumns.keySet()) {
      // Maybe get an annotation on the expression
      Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);
      Set<OWLClassExpression> exprs = expressionColumns.get(column);
      // Each expression will be its own disjoint statement
      for (OWLClassExpression expr : exprs) {
        Set<OWLClassExpression> disjoint = new HashSet<>(Arrays.asList(cls, expr));
        axioms.add(dataFactory.getOWLDisjointClassesAxiom(disjoint, axiomAnnotations));
      }
    }
  }

  /* OBJECT PROPERTY AXIOMS */

  /**
   * Given an object property IRI and the row containing the property details, generate property
   * axioms.
   *
   * @param iri object property IRI
   * @param row list of template values for given object property
   * @throws Exception on issue creating object property axioms from template
   */
  private void addObjectPropertyAxioms(IRI iri, List<String> row) throws Exception {
    // Add the declaration
    axioms.add(
        dataFactory.getOWLDeclarationAxiom(
            dataFactory.getOWLEntity(EntityType.OBJECT_PROPERTY, iri)));

    // Maybe get a property type (default subproperty)
    String propertyType = getPropertyType(row);

    // Maybe get characteristics (default none)
    List<String> characteristics = getCharacteristics(row);

    // Create the property object
    OWLObjectProperty property = dataFactory.getOWLObjectProperty(iri);

    // Handle special property types
    for (String c : characteristics) {
      switch (c.trim().toLowerCase()) {
        case "asymmetric":
          axioms.add(dataFactory.getOWLAsymmetricObjectPropertyAxiom(property));
          break;
        case "functional":
          axioms.add(dataFactory.getOWLFunctionalObjectPropertyAxiom(property));
          break;
        case "inversefunctional":
        case "inverse functional":
          axioms.add(dataFactory.getOWLInverseFunctionalObjectPropertyAxiom(property));
          break;
        case "irreflexive":
          axioms.add(dataFactory.getOWLIrreflexiveObjectPropertyAxiom(property));
          break;
        case "reflexive":
          axioms.add(dataFactory.getOWLReflexiveObjectPropertyAxiom(property));
          break;
        case "symmetric":
          axioms.add(dataFactory.getOWLSymmetricObjectPropertyAxiom(property));
          break;
        case "transitive":
          axioms.add(dataFactory.getOWLTransitiveObjectPropertyAxiom(property));
          break;
        default:
          throw new Exception(
              String.format(
                  unknownCharacteristicError,
                  property.getIRI().getShortForm(),
                  c,
                  rowNum,
                  characteristicColumn,
                  name));
      }
    }

    for (int column = 0; column < templates.size(); column++) {
      String template = templates.get(column);
      String value = null;
      try {
        value = row.get(column);
      } catch (IndexOutOfBoundsException e) {
        // do nothing
      }

      if (value == null || value.trim().isEmpty()) {
        continue;
      }

      if (template.startsWith("A") || template.startsWith("LABEL")) {
        // Handle annotations
        Set<OWLAnnotation> annotations = getAnnotations(template, value, row, column);
        for (OWLAnnotation annotation : annotations) {
          axioms.add(dataFactory.getOWLAnnotationAssertionAxiom(iri, annotation));
        }
      } else if (template.startsWith("SP")) {
        // Subproperty expressions
        Set<OWLObjectPropertyExpression> expressions =
            TemplateHelper.getObjectPropertyExpressions(
                name, checker, template, value, rowNum, column);
        addSubObjectPropertyAxioms(property, expressions, row, column);
      } else if (template.startsWith("EP")) {
        // Equivalent properties expressions
        Set<OWLObjectPropertyExpression> expressions =
            TemplateHelper.getObjectPropertyExpressions(
                name, checker, template, value, rowNum, column);
        addEquivalentObjectPropertiesAxioms(property, expressions, row, column);
      } else if (template.startsWith("DP")) {
        // Disjoint properties expressions
        Set<OWLObjectPropertyExpression> expressions =
            TemplateHelper.getObjectPropertyExpressions(
                name, checker, template, value, rowNum, column);
        addDisjointObjectPropertiesAxioms(property, expressions, row, column);
      } else if (template.startsWith("IP")) {
        // Inverse properties expressions
        Set<OWLObjectPropertyExpression> expressions =
            TemplateHelper.getObjectPropertyExpressions(
                name, checker, template, value, rowNum, column);
        addInverseObjectPropertiesAxioms(property, expressions, row, column);
      } else if (template.startsWith("P") && !template.startsWith("PROPERTY_TYPE")) {
        // Use the property type to determine what type of expression
        Set<OWLObjectPropertyExpression> expressions =
            TemplateHelper.getObjectPropertyExpressions(
                name, checker, template, value, rowNum, column);
        switch (propertyType) {
          case "subproperty":
            addSubObjectPropertyAxioms(property, expressions, row, column);
            break;
          case "equivalent":
            addEquivalentObjectPropertiesAxioms(property, expressions, row, column);
            break;
          case "disjoint":
            addDisjointObjectPropertiesAxioms(property, expressions, row, column);
            break;
          case "inverse":
            addInverseObjectPropertiesAxioms(property, expressions, row, column);
            break;
          default:
            // Unknown property type
            throw new RowParseException(
                String.format(
                    propertyTypeError, iri.getShortForm(), propertyType, rowNum, column + 1, name),
                rowNum,
                column + 1,
                value);
        }
      } else if (template.startsWith("DOMAIN")) {
        // Handle domains
        Set<OWLClassExpression> expressions =
            TemplateHelper.getClassExpressions(
                name, checker, parser, template, value, rowNum, column);
        addObjectPropertyDomains(property, expressions, row, column);
      } else if (template.startsWith("RANGE")) {
        // Handle ranges
        Set<OWLClassExpression> expressions =
            TemplateHelper.getClassExpressions(
                name, checker, parser, template, value, rowNum, column);
        addObjectPropertyRanges(property, expressions, row, column);
      }
    }
  }

  /**
   * Given an OWLObjectProperty, a set of OWLObjectPropertyExpressions, and the row containing the
   * property details, generate subPropertyOf axioms for the property where the parents are the
   * property expressions. Maybe annotate the axioms.
   *
   * @param property OWLObjectProperty to create subPropertyOf axioms for
   * @param expressions set of OWLObjectPropertyExpressions
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addSubObjectPropertyAxioms(
      OWLObjectProperty property,
      Set<OWLObjectPropertyExpression> expressions,
      List<String> row,
      int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    for (OWLObjectPropertyExpression expr : expressions) {
      axioms.add(dataFactory.getOWLSubObjectPropertyOfAxiom(property, expr, axiomAnnotations));
    }
  }

  /**
   * Given an OWLObjectProperty, a set of OWLObjectPropertyExpressions, and the row containing the
   * property details, generate equivalentProperties axioms for the property where the equivalents
   * are the property expressions. Maybe annotate the axioms.
   *
   * @param property OWLObjectProperty to create equivalentProperties axioms for
   * @param expressions set of equivalent OWLObjectPropertyExpressions
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addEquivalentObjectPropertiesAxioms(
      OWLObjectProperty property,
      Set<OWLObjectPropertyExpression> expressions,
      List<String> row,
      int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    for (OWLObjectPropertyExpression expr : expressions) {
      axioms.add(
          dataFactory.getOWLEquivalentObjectPropertiesAxiom(property, expr, axiomAnnotations));
    }
  }

  /**
   * Given an OWLObjectProperty, a set of OWLObjectPropertyExpressions, and the row containing the
   * property details, generate disjointProperties axioms for the property where the disjoints are
   * the property expressions. Maybe annotate the axioms.
   *
   * @param property OWLObjectProperty to create disjointProperties axioms for
   * @param expressions set of disjoint OWLObjectPropertyExpressions
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addDisjointObjectPropertiesAxioms(
      OWLObjectProperty property,
      Set<OWLObjectPropertyExpression> expressions,
      List<String> row,
      int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    expressions.add(property);
    axioms.add(dataFactory.getOWLDisjointObjectPropertiesAxiom(expressions, axiomAnnotations));
  }

  /**
   * Given an OWLObjectProperty, a set of OWLObjectPropertyExpressions, and the row containing the
   * property details, generate inverseProperties axioms for the property where the inverses are the
   * property expressions. Maybe annotate the axioms.
   *
   * @param property OWLObjectProperty to create inverseProperties axioms for
   * @param expressions set of inverse OWLObjectPropertyExpressions
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addInverseObjectPropertiesAxioms(
      OWLObjectProperty property,
      Set<OWLObjectPropertyExpression> expressions,
      List<String> row,
      int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    for (OWLObjectPropertyExpression expr : expressions) {
      axioms.add(dataFactory.getOWLInverseObjectPropertiesAxiom(property, expr, axiomAnnotations));
    }
  }

  /**
   * Given an OWLObjectProperty, a set of OWLClassExpressions, the row containing the property
   * details, and a column location, generate domain axioms where the domains are the class
   * expressions. Maybe annotation the axioms.
   *
   * @param property OWLObjectProperty to create domain axioms for
   * @param expressions set of domain OWLClassExpressions
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addObjectPropertyDomains(
      OWLObjectProperty property, Set<OWLClassExpression> expressions, List<String> row, int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    for (OWLClassExpression expr : expressions) {
      axioms.add(dataFactory.getOWLObjectPropertyDomainAxiom(property, expr, axiomAnnotations));
    }
  }

  /**
   * Given an OWLObjectProperty, a set of OWLClassExpressions, the row containing the property
   * details, and a column location, generate range axioms where the ranges are the class
   * expressions. Maybe annotation the axioms.
   *
   * @param property OWLObjectProperty to create range axioms for
   * @param expressions set of range OWLClassExpressions
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addObjectPropertyRanges(
      OWLObjectProperty property, Set<OWLClassExpression> expressions, List<String> row, int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    for (OWLClassExpression expr : expressions) {
      axioms.add(dataFactory.getOWLObjectPropertyRangeAxiom(property, expr, axiomAnnotations));
    }
  }

  /* DATA PROPERTY AXIOMS */

  /**
   * Given an data property IRI and the row containing the property details, generate property
   * axioms.
   *
   * @param iri data property IRI
   * @param row list of template values for given data property
   * @throws Exception on issue creating data property axioms from template
   */
  private void addDataPropertyAxioms(IRI iri, List<String> row) throws Exception {
    // Add the declaration
    axioms.add(
        dataFactory.getOWLDeclarationAxiom(
            dataFactory.getOWLEntity(EntityType.DATA_PROPERTY, iri)));

    OWLDataProperty property = dataFactory.getOWLDataProperty(iri);

    // Maybe get property type (default subproperty)
    String propertyType = getPropertyType(row);

    // Maybe get property characteristics (default empty list)
    List<String> characteristics = getCharacteristics(row);

    // Maybe add property characteristics
    for (String c : characteristics) {
      if (!c.equalsIgnoreCase("functional")) {
        throw new Exception(
            String.format(
                dataPropertyCharacteristicError,
                property.getIRI().getShortForm(),
                rowNum,
                characteristicColumn,
                name));
      }
      axioms.add(dataFactory.getOWLFunctionalDataPropertyAxiom(property));
    }

    for (int column = 0; column < templates.size(); column++) {
      String template = templates.get(column);
      String value = null;
      try {
        value = row.get(column);
      } catch (IndexOutOfBoundsException e) {
        // do nothing
      }

      if (value == null || value.trim().isEmpty()) {
        continue;
      }

      String split = null;
      if (template.contains("SPLIT=")) {
        split = template.substring(template.indexOf("SPLIT=") + 6).trim();
      }

      if (template.startsWith("A") || template.startsWith("LABEL")) {
        // Handle annotations
        Set<OWLAnnotation> annotations = getAnnotations(template, value, row, column);
        for (OWLAnnotation annotation : annotations) {
          axioms.add(dataFactory.getOWLAnnotationAssertionAxiom(iri, annotation));
        }
      } else if (template.startsWith("SP")) {
        // Subproperty expressions
        Set<OWLDataPropertyExpression> expressions =
            TemplateHelper.getDataPropertyExpressions(
                name, checker, template, value, rowNum, column);
        addSubDataPropertyAxioms(property, expressions, row, column);
      } else if (template.startsWith("EP")) {
        // Equivalent properties expressions
        Set<OWLDataPropertyExpression> expressions =
            TemplateHelper.getDataPropertyExpressions(
                name, checker, template, value, rowNum, column);
        addEquivalentDataPropertiesAxioms(property, expressions, row, column);
      } else if (template.startsWith("DP")) {
        // Disjoint properties expressions
        Set<OWLDataPropertyExpression> expressions =
            TemplateHelper.getDataPropertyExpressions(
                name, checker, template, value, rowNum, column);
        addDisjointDataPropertiesAxioms(property, expressions, row, column);
      } else if (template.startsWith("IP")) {
        // Cannot use inverse with data properties
        throw new RowParseException(
            String.format(
                propertyTypeError, iri.getShortForm(), propertyType, rowNum, column + 1, name),
            rowNum,
            column + 1,
            value);
      } else if (template.startsWith("P ") && !template.startsWith("PROPERTY_TYPE")) {
        // Use property type to handle expression type
        Set<OWLDataPropertyExpression> expressions =
            TemplateHelper.getDataPropertyExpressions(
                name, checker, template, value, rowNum, column);
        switch (propertyType) {
          case "subproperty":
            addSubDataPropertyAxioms(property, expressions, row, column);
            break;
          case "equivalent":
            addEquivalentDataPropertiesAxioms(property, expressions, row, column);
            break;
          case "disjoint":
            addDisjointDataPropertiesAxioms(property, expressions, row, column);
            break;
          default:
            // Unknown property type
            throw new RowParseException(
                String.format(
                    propertyTypeError, iri.getShortForm(), propertyType, rowNum, column + 1, name),
                rowNum,
                column + 1,
                value);
        }
      } else if (template.startsWith("DOMAIN")) {
        // Handle domains
        Set<OWLClassExpression> expressions =
            TemplateHelper.getClassExpressions(
                name, checker, parser, template, value, rowNum, column);
        addDataPropertyDomains(property, expressions, row, column);
      } else if (template.startsWith("RANGE")) {
        // Handle ranges
        Set<OWLDatatype> datatypes =
            TemplateHelper.getDatatypes(name, checker, value, split, rowNum, column);
        addDataPropertyRanges(property, datatypes, row, column);
      }
    }
  }

  /**
   * Given an OWLDataProperty, a set of OWLDataPropertyExpressions, and the row containing the
   * property details, generate subPropertyOf axioms for the property where the parents are the
   * property expressions. Maybe annotate the axioms.
   *
   * @param property OWLDataProperty to create subPropertyOf axioms for
   * @param expressions set of parent OWLDataPropertyExpressions
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addSubDataPropertyAxioms(
      OWLDataProperty property,
      Set<OWLDataPropertyExpression> expressions,
      List<String> row,
      int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    for (OWLDataPropertyExpression expr : expressions) {
      if (expr != null) {
        axioms.add(dataFactory.getOWLSubDataPropertyOfAxiom(property, expr, axiomAnnotations));
      }
    }
  }

  /**
   * Given an OWLDataProperty, a set of OWLDataPropertyExpressions, and the row containing the
   * property details, generate equivalentProperties axioms for the property where the equivalents
   * are the property expressions. Maybe annotate the axioms.
   *
   * @param property OWLDataProperty to create equivalentProperties axioms for
   * @param expressions set of equivalent OWLDataPropertyExpressions
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addEquivalentDataPropertiesAxioms(
      OWLDataProperty property,
      Set<OWLDataPropertyExpression> expressions,
      List<String> row,
      int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    for (OWLDataPropertyExpression expr : expressions) {
      axioms.add(dataFactory.getOWLEquivalentDataPropertiesAxiom(property, expr, axiomAnnotations));
    }
  }

  /**
   * Given an OWLDataProperty, a set of OWLObjectPropertyExpressions, and the row containing the
   * property details, generate disjointProperties axioms for the property where the disjoints are
   * the property expressions. Maybe annotate the axioms.
   *
   * @param property OWLDataProperty to create disjointProperties axioms for
   * @param expressions set of disjoint OWLDataPropertyExpressions
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addDisjointDataPropertiesAxioms(
      OWLDataProperty property,
      Set<OWLDataPropertyExpression> expressions,
      List<String> row,
      int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    expressions.add(property);
    axioms.add(dataFactory.getOWLDisjointDataPropertiesAxiom(expressions, axiomAnnotations));
  }

  /**
   * Given an OWLDataProperty, a set of OWLClassExpressions, the row containing the property
   * details, and a column location, generate domain axioms where the domains are the class
   * expressions. Maybe annotation the axioms.
   *
   * @param property OWLDataProperty to create domain axioms for
   * @param expressions set of domain OWLClassExpressions
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addDataPropertyDomains(
      OWLDataProperty property, Set<OWLClassExpression> expressions, List<String> row, int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    for (OWLClassExpression expr : expressions) {
      axioms.add(dataFactory.getOWLDataPropertyDomainAxiom(property, expr, axiomAnnotations));
    }
  }

  /**
   * Given an OWLObjectProperty, a set of OWLDatatypes, the row containing the property details, and
   * a column location, generate range axioms where the ranges are the datatypes. Maybe annotation
   * the axioms.
   *
   * @param property OWLObjectProperty to create range axioms for
   * @param datatypes set of range OWLDatatypes
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addDataPropertyRanges(
      OWLDataProperty property, Set<OWLDatatype> datatypes, List<String> row, int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    for (OWLDatatype datatype : datatypes) {
      axioms.add(dataFactory.getOWLDataPropertyRangeAxiom(property, datatype, axiomAnnotations));
    }
  }

  /* ANNOTATION PROPERTY AXIOMS */

  /**
   * Given an annotation property IRI and the row containing the property details, generate property
   * axioms.
   *
   * @param iri annotation property IRI
   * @param row list of template values for given annotation property
   * @throws Exception on issue creating annotation property axioms from template
   */
  private void addAnnotationPropertyAxioms(IRI iri, List<String> row) throws Exception {
    // Add the declaration
    axioms.add(
        dataFactory.getOWLDeclarationAxiom(
            dataFactory.getOWLEntity(EntityType.ANNOTATION_PROPERTY, iri)));

    String propertyType = getPropertyType(row);

    if (!propertyType.equals("subproperty")) {
      // Annotation properties can only have type "subproperty"
      throw new RowParseException(
          String.format(
              annotationPropertyTypeError, iri, propertyType, rowNum, propertyTypeColumn + 1, name),
          rowNum,
          propertyTypeColumn + 1,
          propertyType);
    }

    // Annotation properties should not have characteristics
    if (characteristicColumn != -1) {
      String propertyCharacteristicString = row.get(characteristicColumn);
      if (propertyCharacteristicString != null && !propertyCharacteristicString.trim().isEmpty()) {
        throw new RowParseException(
            String.format(
                annotationPropertyCharacteristicError,
                iri.getShortForm(),
                rowNum,
                characteristicColumn + 1,
                name),
            rowNum,
            characteristicColumn + 1,
            propertyCharacteristicString);
      }
    }

    // Create the property object
    OWLAnnotationProperty property = dataFactory.getOWLAnnotationProperty(iri);

    for (int column = 0; column < templates.size(); column++) {
      String template = templates.get(column);
      String value = null;
      try {
        value = row.get(column);
      } catch (IndexOutOfBoundsException e) {
        // do nothing
      }

      if (value == null || value.trim().isEmpty()) {
        continue;
      }

      // Maybe get the split character
      String split = null;
      if (template.contains("SPLIT=")) {
        split = template.substring(template.indexOf("SPLIT=") + 6).trim();
      }

      if (template.startsWith("A") || template.startsWith("LABEL")) {
        // Handle annotations
        Set<OWLAnnotation> annotations = getAnnotations(template, value, row, column);
        for (OWLAnnotation annotation : annotations) {
          axioms.add(dataFactory.getOWLAnnotationAssertionAxiom(iri, annotation));
        }
      } else if (template.startsWith("SP")
          || template.startsWith("P") && !template.startsWith("PROPERTY_TYPE")) {
        // Handle property logic
        Set<OWLAnnotationProperty> parents =
            TemplateHelper.getAnnotationProperties(checker, value, split, column);
        addSubAnnotationPropertyAxioms(property, parents, row, column);
      } else if (template.startsWith("DOMAIN")) {
        // Handle domains
        Set<IRI> iris = TemplateHelper.getValueIRIs(checker, value, split);
        addAnnotationPropertyDomains(property, iris, row, column);
      } else if (template.startsWith("RANGE")) {
        // Handle ranges
        Set<IRI> iris = TemplateHelper.getValueIRIs(checker, value, split);
        addAnnotationPropertyRanges(property, iris, row, column);
      }
    }
  }

  /**
   * Given an OWLAnnotationProperty, a set of OWLAnnotationProperties, and the row containing the
   * property details, generate subPropertyOf axioms for the property where the parents are the
   * other properties. Maybe annotate the axioms.
   *
   * @param property OWLObjectProperty to create subPropertyOf axioms for
   * @param parents set of parent OWLAnnotationProperties
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addSubAnnotationPropertyAxioms(
      OWLAnnotationProperty property,
      Set<OWLAnnotationProperty> parents,
      List<String> row,
      int column)
      throws Exception {
    // Maybe get an annotation on the subproperty axiom
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    for (OWLAnnotationProperty parent : parents) {
      axioms.add(
          dataFactory.getOWLSubAnnotationPropertyOfAxiom(property, parent, axiomAnnotations));
    }
  }

  /**
   * Given an OWLAnnotationProperty, a set of IRIs, the row containing the property details, and a
   * column location, generate domain axioms where the domains are the IRIs. Maybe annotation the
   * axioms.
   *
   * @param property OWLObjectProperty to create domain axioms for
   * @param iris set of domain IRIs
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addAnnotationPropertyDomains(
      OWLAnnotationProperty property, Set<IRI> iris, List<String> row, int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    for (IRI iri : iris) {
      axioms.add(dataFactory.getOWLAnnotationPropertyDomainAxiom(property, iri, axiomAnnotations));
    }
  }

  /**
   * Given an OWLAnnotationProperty, a set of IRIs, the row containing the property details, and a
   * column location, generate range axioms where the ranges are the IRIs. Maybe annotation the
   * axioms.
   *
   * @param property OWLAnnotationProperty to create range axioms for
   * @param iris set of range IRIs
   * @param row list of template values for given property
   * @param column column number of logical template string
   * @throws Exception on issue getting axiom annotations
   */
  private void addAnnotationPropertyRanges(
      OWLAnnotationProperty property, Set<IRI> iris, List<String> row, int column)
      throws Exception {
    // Maybe get an annotation on the expression
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    for (IRI iri : iris) {
      axioms.add(dataFactory.getOWLAnnotationPropertyRangeAxiom(property, iri, axiomAnnotations));
    }
  }

  /* DATATYPE AXIOMS */

  /**
   * Given a datatype IRI and the row containing the datatype details, generate datatype axioms.
   *
   * @param iri datatype IRI
   * @param row list of template values for given datatype
   * @throws Exception on issue creating datatype annotations
   */
  private void addDatatypeAxioms(IRI iri, List<String> row) throws Exception {
    // Add the declaration
    axioms.add(
        dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLEntity(EntityType.DATATYPE, iri)));

    for (int column = 0; column < templates.size(); column++) {
      String template = templates.get(column);
      String value = null;
      try {
        value = row.get(column);
      } catch (IndexOutOfBoundsException e) {
        // do nothing
      }

      if (value == null || value.trim().isEmpty()) {
        continue;
      }

      // Handle annotations
      if (template.startsWith("A")) {
        // Add the annotations to the datatype
        Set<OWLAnnotation> annotations = getAnnotations(template, value, row, column);
        for (OWLAnnotation annotation : annotations) {
          axioms.add(dataFactory.getOWLAnnotationAssertionAxiom(iri, annotation));
        }
      }
      // TODO - future support for data definitions with DT
    }
  }

  /* INDIVIDUAL AXIOMS */

  /**
   * Given an individual IRI and the row containing the individual details, generate individual
   * axioms.
   *
   * @param iri individual IRI
   * @param row list of template values for given individual
   * @throws Exception on issue creating individual axioms from template
   */
  private void addIndividualAxioms(IRI iri, List<String> row) throws Exception {
    // Should not return null, as empty defaults to a class
    String typeCol = row.get(typeColumn).trim();
    // Use the 'type' to get the class assertion for the individual
    // If it is owl:Individual or owl:NamedIndividual, it will not have a class assertion
    // There may be more than one class assertion - right now only named classes are supported
    List<String> types = new ArrayList<>();
    if (typeSplit != null) {
      for (String t : typeCol.split(Pattern.quote(typeSplit))) {
        if (!t.trim().equals("")) {
          types.add(t.trim());
        }
      }
    } else {
      types.add(typeCol.trim());
    }

    // The individualType is used to determine what kind of axioms are associated
    // e.g. different individuals, same individuals...
    // The default is just "named" individual (no special axioms)
    String individualType = "named";
    if (individualTypeColumn != -1) {
      try {
        individualType = row.get(individualTypeColumn);
      } catch (IndexOutOfBoundsException e) {
        // do nothing
      }
    }

    OWLNamedIndividual individual = dataFactory.getOWLNamedIndividual(iri);
    // Add declaration
    axioms.add(dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLNamedIndividual(iri)));

    for (String type : types) {
      // Trim for safety
      type = type.trim();

      // Try to resolve a CURIE
      IRI typeIRI = ioHelper.createIRI(type);

      // Set to IRI string or to type string
      String typeOrIRI = type;
      if (typeIRI != null) {
        typeOrIRI = typeIRI.toString();
      }

      // Add a type if the type is not owl:Individual or owl:NamedIndividual
      if (!typeOrIRI.equalsIgnoreCase("individual")
          && !typeOrIRI.equalsIgnoreCase("named individual")
          && !typeOrIRI.equalsIgnoreCase("http://www.w3.org/2002/07/owl#NamedIndividual")
          && !typeOrIRI.equalsIgnoreCase("http://www.w3.org/2002/07/owl#Individual")) {
        OWLClass typeCls = checker.getOWLClass(type);
        if (typeCls != null) {
          axioms.add(dataFactory.getOWLClassAssertionAxiom(typeCls, individual));
        } else {
          // If the class is null, assume it is a class expression
          OWLClassExpression typeExpr =
              TemplateHelper.tryParse(name, checker, parser, type, rowNum, typeColumn);
          axioms.add(dataFactory.getOWLClassAssertionAxiom(typeExpr, individual));
        }
      }
    }

    for (int column = 0; column < templates.size(); column++) {
      String template = templates.get(column);
      String split = null;
      if (template.contains("SPLIT=")) {
        split = template.substring(template.indexOf("SPLIT=") + 6).trim();
        template = template.substring(0, template.indexOf("SPLIT=")).trim();
      }

      String value = null;
      try {
        value = row.get(column);
      } catch (IndexOutOfBoundsException e) {
        // do nothing
      }

      if (value == null || value.trim().isEmpty()) {
        continue;
      }

      // Handle annotations
      if (template.startsWith("A") || template.startsWith("LABEL")) {
        if (split != null) {
          // Add the split back on for getAnnotations
          template = template + " SPLIT=" + split;
        }
        // Add the annotations to the individual
        Set<OWLAnnotation> annotations = getAnnotations(template, value, row, column);
        for (OWLAnnotation annotation : annotations) {
          axioms.add(dataFactory.getOWLAnnotationAssertionAxiom(iri, annotation));
        }
      } else if (template.startsWith("SI")) {
        // Same individuals axioms
        Set<OWLIndividual> sameIndividuals = TemplateHelper.getIndividuals(checker, value, split);
        if (!sameIndividuals.isEmpty()) {
          addSameIndividualsAxioms(individual, sameIndividuals, row, column);
        }
      } else if (template.startsWith("DI")) {
        // Different individuals axioms
        Set<OWLIndividual> differentIndividuals =
            TemplateHelper.getIndividuals(checker, value, split);
        if (!differentIndividuals.isEmpty()) {
          addDifferentIndividualsAxioms(individual, differentIndividuals, row, column);
        }
      } else if (template.startsWith("TI ")) {
        if (split != null) {
          // Add the split back on for getClassExpressions
          template = template + " SPLIT=" + split;
        }
        Set<OWLClassExpression> typeExpressions =
            TemplateHelper.getClassExpressions(
                name, checker, parser, template, value, rowNum, column);
        for (OWLClassExpression ce : typeExpressions) {
          axioms.add(dataFactory.getOWLClassAssertionAxiom(ce, individual));
        }
      } else if (template.startsWith("I") && !template.startsWith("INDIVIDUAL_TYPE")) {
        // Use individual type to determine how to handle expressions
        switch (individualType) {
          case "named":
            if (template.startsWith("I ")) {
              String propStr = template.substring(2).replace("'", "");
              OWLObjectProperty objectProperty = checker.getOWLObjectProperty(propStr);
              if (objectProperty != null) {
                Set<OWLIndividual> otherIndividuals =
                    TemplateHelper.getIndividuals(checker, value, split);
                addObjectPropertyAssertionAxioms(
                    individual, otherIndividuals, objectProperty, row, column);
                break;
              }
              OWLDataProperty dataProperty = checker.getOWLDataProperty(propStr);
              if (dataProperty != null) {
                Set<OWLLiteral> literals =
                    TemplateHelper.getLiterals(name, checker, value, split, rowNum, column);
                addDataPropertyAssertionAxioms(individual, literals, dataProperty, row, column);
                break;
              }
            }
            break;
          case "same":
            Set<OWLIndividual> sameIndividuals =
                TemplateHelper.getIndividuals(checker, value, split);
            if (!sameIndividuals.isEmpty()) {
              addSameIndividualsAxioms(individual, sameIndividuals, row, column);
            }
            break;
          case "different":
            Set<OWLIndividual> differentIndividuals =
                TemplateHelper.getIndividuals(checker, value, split);
            if (!differentIndividuals.isEmpty()) {
              addDifferentIndividualsAxioms(individual, differentIndividuals, row, column);
            }
            break;
          default:
            throw new RowParseException(
                String.format(
                    individualTypeError,
                    iri.getShortForm(),
                    individualType,
                    rowNum,
                    column + 1,
                    name),
                rowNum,
                column + 1,
                value);
        }
      }
    }
  }

  /**
   * Given an OWLIndividual, a set of OWLIndividuals, an object property expression, the row as list
   * of strings, and the column number, add each individual as the object of the object property
   * expression for that individual.
   *
   * @param individual OWLIndividual to add object property assertion axioms to
   * @param otherIndividuals set of other OWLIndividuals representing the objects of the axioms
   * @param expression OWLObjectPropertyExpression to use as property of the axioms
   * @param row list of strings
   * @param column column number
   * @throws Exception on problem handling axiom annotations
   */
  private void addObjectPropertyAssertionAxioms(
      OWLNamedIndividual individual,
      Set<OWLIndividual> otherIndividuals,
      OWLObjectPropertyExpression expression,
      List<String> row,
      int column)
      throws Exception {
    // Maybe get an annotation on the subproperty axiom
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    for (OWLIndividual other : otherIndividuals) {
      axioms.add(
          dataFactory.getOWLObjectPropertyAssertionAxiom(
              expression, individual, other, axiomAnnotations));
    }
  }

  /**
   * Given an OWLIndividual, a set of OWLLiterals, a data property expression, the row as list of
   * strings, and the column number, add each literal as the object of the data property expression
   * for that individual.
   *
   * @param individual OWLIndividual to add data property assertion axioms to
   * @param literals set of OWLLiterals representing the objects of the axioms
   * @param expression OWLDataPropertyExpression to use as property of the axioms
   * @param row list of strings
   * @param column column number
   * @throws Exception on problem handling axiom annotations
   */
  private void addDataPropertyAssertionAxioms(
      OWLNamedIndividual individual,
      Set<OWLLiteral> literals,
      OWLDataPropertyExpression expression,
      List<String> row,
      int column)
      throws Exception {
    // Maybe get an annotation on the subproperty axiom
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    for (OWLLiteral lit : literals) {
      axioms.add(
          dataFactory.getOWLDataPropertyAssertionAxiom(
              expression, individual, lit, axiomAnnotations));
    }
  }

  /**
   * Given an OWLIndividual, a set of same individuals, a row as list of strings, and a column
   * number, add the same individual axioms.
   *
   * @param individual OWLIndiviudal to add axioms to
   * @param sameIndividuals set of same individuals
   * @param row list of strings
   * @param column column number
   * @throws Exception on problem handling axiom annotations
   */
  private void addSameIndividualsAxioms(
      OWLNamedIndividual individual,
      Set<OWLIndividual> sameIndividuals,
      List<String> row,
      int column)
      throws Exception {
    // Maybe get an annotation on the subproperty axiom
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    sameIndividuals.add(individual);
    axioms.add(dataFactory.getOWLSameIndividualAxiom(sameIndividuals, axiomAnnotations));
  }

  /**
   * Given an OWLIndividual, a set of different individuals, a row as list of strings, and a column
   * number, add the different individual axioms.
   *
   * @param individual OWLIndiviudal to add axioms to
   * @param differentIndividuals set of different individuals
   * @param row list of strings
   * @param column column number
   * @throws Exception on problem handling axiom annotations
   */
  private void addDifferentIndividualsAxioms(
      OWLNamedIndividual individual,
      Set<OWLIndividual> differentIndividuals,
      List<String> row,
      int column)
      throws Exception {
    // Maybe get an annotation on the subproperty axiom
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);

    // Generate axioms
    differentIndividuals.add(individual);
    axioms.add(dataFactory.getOWLDifferentIndividualsAxiom(differentIndividuals, axiomAnnotations));
  }

  /* ANNOTATION HELPERS */

  /**
   * Given a template string, a value string, a row as a list of strings, and the column number,
   * return a set of one or more OWLAnnotations.
   *
   * @param template template string
   * @param value value of annotation(s)
   * @param row list of strings
   * @param column column number
   * @return Set of OWLAnnotations
   * @throws Exception on issue getting OWLAnnotations
   */
  private Set<OWLAnnotation> getAnnotations(
      String template, String value, List<String> row, int column) throws Exception {
    if (value == null || value.trim().equals("")) {
      return new HashSet<>();
    }

    Set<OWLAnnotation> annotations =
        TemplateHelper.getAnnotations(name, checker, template, value, rowNum, column);

    // Maybe get an annotation on the annotation
    Set<OWLAnnotation> axiomAnnotations = maybeGetAxiomAnnotations(row, column);
    if (axiomAnnotations.isEmpty()) {
      // No annotations to add
      return annotations;
    }

    // Add annotations and return fixed set
    Set<OWLAnnotation> fixedAnnotations = new HashSet<>();
    for (OWLAnnotation annotation : annotations) {
      fixedAnnotations.add(annotation.getAnnotatedAnnotation(axiomAnnotations));
    }
    return fixedAnnotations;
  }

  /**
   * Given a row as a list of strings and a column number, determine if the next column contains a
   * one or more axiom annotations. If so, return the axiom annotation or annotations as a set of
   * OWLAnnotations.
   *
   * @param row list of strings
   * @param column column number
   * @return set of OWLAnnotations, maybe empty
   * @throws Exception on issue getting the OWLAnnotations
   */
  private Set<OWLAnnotation> maybeGetAxiomAnnotations(List<String> row, int column)
      throws Exception {
    Map<Integer, List<Integer>> annotationsPlusAnnotations = new HashMap<>();
    int lastAnnotation = -1;
    while (column < row.size() - 1) {
      column++;
      // Row might be longer than column
      // That's OK, just skip it
      String template;
      try {
        template = templates.get(column);
      } catch (IndexOutOfBoundsException e) {
        break;
      }
      // Skip columsn with empty templates.
      // See https://github.com/ontodev/robot/issues/1216
      if (template.trim().equals("")) {
        continue;
      }
      Matcher m = Pattern.compile("^>.*").matcher(template);
      if (m.matches()) {

        m = Pattern.compile("^>[^>].*").matcher(template);
        if (m.matches()) {
          // This is an annotation on the original axiom
          lastAnnotation = column;
          annotationsPlusAnnotations.put(column, new ArrayList<>());
        }

        m = Pattern.compile("^>>.*").matcher(template);
        if (m.matches()) {
          // This is an annotation on the last axiom annotation
          // Update the map
          List<Integer> cols = annotationsPlusAnnotations.get(lastAnnotation);
          cols.add(column);
          annotationsPlusAnnotations.put(lastAnnotation, cols);
        }
      } else {
        // We are done getting the annotations
        break;
      }
    }

    Set<OWLAnnotation> annotations = new HashSet<>();
    for (Map.Entry<Integer, List<Integer>> annPlusAnns : annotationsPlusAnnotations.entrySet()) {
      int annColumn = annPlusAnns.getKey();
      List<Integer> moreAnns = annPlusAnns.getValue();

      String template = templates.get(annColumn);
      String value = row.get(annColumn).trim();
      if (value.isEmpty()) {
        continue;
      }
      Set<OWLAnnotation> firstAnnotations =
          TemplateHelper.getAnnotations(name, checker, template, value, rowNum, annColumn);

      if (moreAnns.isEmpty()) {
        annotations.addAll(firstAnnotations);
        continue;
      }

      Set<OWLAnnotation> moreAnnotations = new HashSet<>();
      for (int m : moreAnns) {
        template = templates.get(m);
        while (template.startsWith(">")) {
          template = template.substring(1);
        }
        value = row.get(m).trim();
        if (value.isEmpty()) {
          continue;
        }
        moreAnnotations.addAll(
            TemplateHelper.getAnnotations(name, checker, template, value, rowNum, annColumn));
      }

      for (OWLAnnotation f : firstAnnotations) {
        annotations.add(f.getAnnotatedAnnotation(moreAnnotations));
      }
    }
    return annotations;
  }

  /**
   * Given a property string (label or CURIE) and the column of that template string, determine if
   * this is RDFS label and if so, set the label column.
   *
   * @param property property string
   * @param column int column number
   */
  private void maybeSetLabelColumn(String property, int column) {
    OWLAnnotationProperty ap = checker.getOWLAnnotationProperty(property, true);
    if (ap != null) {
      if (ap.getIRI().toString().equals(dataFactory.getRDFSLabel().getIRI().toString())) {
        labelColumn = column;
      }
    }
  }

  /* OTHER HELPERS */

  /**
   * Given a string ID and a string label, with at least one of those being non-null, return an IRI
   * for the entity.
   *
   * @param id String ID of entity, maybe null
   * @param label String label of entity, maybe null
   * @return IRI of entity
   * @throws Exception if both id and label are null
   */
  private IRI getIRI(String id, String label) throws Exception {
    if (id == null && label == null) {
      // This cannot be hit by CLI users
      throw new Exception("You must specify either an ID or a label");
    }
    if (id != null) {
      return ioHelper.createIRI(id);
    }
    return checker.getIRI(label, true);
  }

  /**
   * Given a row, get the property type if it exists. If not, return default of "subproperty".
   *
   * @param row list of strings
   * @return property type
   */
  private String getPropertyType(List<String> row) {
    String propertyType = null;
    if (propertyTypeColumn != -1) {
      try {
        propertyType = row.get(propertyTypeColumn);
      } catch (IndexOutOfBoundsException e) {
        // do nothing
      }
    }
    if (propertyType == null || propertyType.trim().isEmpty()) {
      return "subproperty";
    } else {
      return propertyType.trim().toLowerCase();
    }
  }

  /**
   * Given a row, get the list of characteristics if they exist. If not, return an empty list.
   *
   * @param row list of strings
   * @return characteristics
   */
  private List<String> getCharacteristics(List<String> row) {
    if (characteristicColumn != -1 && characteristicColumn <= (row.size() - 1)) {
      // If characteristics is the last column and there are no characteristics for
      // this entry, the list will be one element short.
      String characteristicString = row.get(characteristicColumn).trim();
      if (characteristicString.equalsIgnoreCase("")) {
        return new ArrayList<>();
      } else if (characteristicSplit != null
          && characteristicString.contains(characteristicSplit)) {
        return Arrays.asList(characteristicString.split(Pattern.quote(characteristicSplit)));
      } else {
        return Collections.singletonList(characteristicString.trim());
      }
    }
    return new ArrayList<>();
  }
}
