package org.obolibrary.robot;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.github.jsonldjava.core.Context;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.geneontology.reasoner.ExpressionMaterializingReasonerFactory;
import org.geneontology.whelk.owlapi.WhelkOWLReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.slf4j.LoggerFactory;
import uk.ac.manchester.cs.jfact.JFactFactory;

/** Convenience methods for working with command line options. */
public class CommandLineHelper {
  /** Logger. */
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CommandLineHelper.class);

  /** Namespace for general input error messages. */
  private static final String NS = "errors#";

  /** Error message when a boolean value is not "true" or "false". Expects option name. */
  private static final String booleanValueError =
      NS + "BOOLEAN VALUE ERROR arg for %s must be true or false";

  /** Error message when --input is provided in a chained command. */
  private static final String chainedInputError =
      NS + "CHAINED INPUT ERROR do not use an --input option for chained commands";

  /** Error message when an invalid IRI is provided. Expects the entry field and term. */
  private static final String invalidIRIError =
      NS + "INVALID IRI ERROR %1$s \"%2$s\" is not a valid CURIE or IRI";

  /** Error message when user provides an invalid reasoner. Expects reasonerName in formatting. */
  private static final String invalidReasonerError =
      NS + "INVALID REASONER ERROR unknown reasoner: %s";

  /** Error message when no input ontology is provided for methods that accept multiple inputs. */
  private static final String missingRequirementError = NS + "MISSING REQUIREMENT ERROR %s";

  /** Error message when no input ontology is provided. */
  private static final String missingInputError = NS + "MISSING INPUT ERROR an --input is required";

  /** Error message when no input ontology is provided for methods that accept multiple inputs. */
  private static final String missingInputsError =
      NS + "MISSING INPUT ERROR at least one --input is required";

  /** Error message when input terms are not provided. */
  private static final String missingTermsError =
      NS + "MISSING TERMS ERROR term(s) are required with --term or --term-file";

  /** Error message when more than one --input is specified, excluding merge and unmerge. */
  private static final String multipleInputsError =
      NS + "MULITPLE INPUTS ERROR only one --input is allowed";

  /** Error message when the --inputs pattern does not include * or ?, or is not quoted */
  private static final String wildcardError =
      NS + "WILDCARD ERROR --inputs argument must be a quoted wildcard pattern";

  /**
   * Given a single string, return a list of strings split at whitespace but allowing for quoted
   * values, as a command-line parser does.
   *
   * @param toProcess the string to parse
   * @return a string list, split at whitespace, with quotation marks removed
   * @throws Exception on parsing problems
   */
  public static List<String> parseArgList(String toProcess) throws Exception {
    return new ArrayList<>(Arrays.asList(parseArgs(toProcess)));
  }

  /**
   * Given a single string, return an array of strings split at whitespace but allowing for quoted
   * values, as a command-line parser does. Adapted from org.apache.tools.ant.types.Commandline
   *
   * @param toProcess the string to parse
   * @return a string array, split at whitespace, with quotation marks removed
   * @throws Exception on parsing problems
   */
  public static String[] parseArgs(String toProcess) throws Exception {
    if (toProcess == null || toProcess.length() == 0) {
      return new String[0];
    }

    // parse with a simple finite state machine
    final int normal = 0;
    final int inQuote = 1;
    final int inDoubleQuote = 2;
    int state = normal;
    StringTokenizer tok = new StringTokenizer(toProcess, "\"\' ", true);
    Vector<String> v = new Vector<>();
    StringBuffer current = new StringBuffer();
    boolean lastTokenHasBeenQuoted = false;

    while (tok.hasMoreTokens()) {
      String nextTok = tok.nextToken();
      switch (state) {
        case inQuote:
          if ("\'".equals(nextTok)) {
            lastTokenHasBeenQuoted = true;
            state = normal;
          } else {
            current.append(nextTok);
          }
          break;
        case inDoubleQuote:
          if ("\"".equals(nextTok)) {
            lastTokenHasBeenQuoted = true;
            state = normal;
          } else {
            current.append(nextTok);
          }
          break;
        default:
          if ("\'".equals(nextTok)) {
            state = inQuote;
          } else if ("\"".equals(nextTok)) {
            state = inDoubleQuote;
          } else if (" ".equals(nextTok)) {
            if (lastTokenHasBeenQuoted || current.length() != 0) {
              v.addElement(current.toString());
              current = new StringBuffer();
            }
          } else {
            current.append(nextTok);
          }
          lastTokenHasBeenQuoted = false;
          break;
      }
    }

    if (lastTokenHasBeenQuoted || current.length() != 0) {
      v.addElement(current.toString());
    }

    if (state == inQuote || state == inDoubleQuote) {
      throw new Exception("unbalanced quotes in " + toProcess);
    }

    String[] args = new String[v.size()];
    v.copyInto(args);
    return args;
  }

  /**
   * Given a command line, return the values for an option as a list. This is just a convenience
   * function for turning an array into a list.
   *
   * @param line the command line to use
   * @param name the name of the option to find
   * @return the option values as a list of strings, maybe empty
   */
  public static List<String> getOptionValues(CommandLine line, String name) {
    List<String> valueList = new ArrayList<>();
    String[] valueArray = line.getOptionValues(name);
    if (valueArray != null) {
      valueList = new ArrayList<>(Arrays.asList(valueArray));
    }
    return valueList;
  }

  /**
   * Given a command line and an index, return the argument at that index.
   *
   * @param line the command line to use
   * @param index the index of the argument
   * @return the argument at the given index
   */
  public static String getIndexValue(CommandLine line, int index) {
    String result = null;
    List<String> arguments = Arrays.asList(line.getArgs());
    if (arguments.size() > index) {
      result = arguments.get(index);
    }
    return result;
  }

  /**
   * Given a command line, return its first argument, which should be the name of the command to
   * execute.
   *
   * @param line the command line to use
   * @return the name of the command to execute
   */
  public static String getCommand(CommandLine line) {
    return getIndexValue(line, 0);
  }

  /**
   * Return true if a command line include the option with the given name, or the command with the
   * given name.
   *
   * @param line the command line to use
   * @param name the name of the flag or argument to check for
   * @return true if a flag or first argument match the given name, false otherwise
   */
  public static boolean hasFlagOrCommand(CommandLine line, String name) {
    if (line.hasOption(name)) {
      return true;
    }
    String command = getCommand(line);
    return command != null && command.equals(name);
  }

  /**
   * Given a command line, get the 'axioms' option(s) and make sure all are properly split and
   * return one axiom selector per list entry.
   *
   * @param line the command line to use
   * @return cleaned list of input axiom type strings
   */
  public static List<String> cleanAxiomStrings(CommandLine line) {
    List<String> axiomTypeStrings = getOptionalValues(line, "axioms");

    if (axiomTypeStrings.isEmpty()) {
      axiomTypeStrings.add("all");
    }

    // Split if it's one arg with spaces
    List<String> axiomTypeFixedStrings = new ArrayList<>();
    for (String axiom : axiomTypeStrings) {
      if (axiom.contains(" ")) {
        axiomTypeFixedStrings.addAll(Arrays.asList(axiom.split(" ")));
      } else {
        axiomTypeFixedStrings.add(axiom);
      }
    }

    return axiomTypeFixedStrings;
  }

  /**
   * Given a command line and an IOHelper, return a list of base namespaces from the '--base-iri'
   * option.
   *
   * @param line the command line to use
   * @param ioHelper the IOHelper to resolve prefixes
   * @return list of full base namespaces
   */
  public static List<String> getBaseNamespaces(CommandLine line, IOHelper ioHelper) {
    List<String> bases = new ArrayList<>();
    if (!line.hasOption("base-iri")) {
      return bases;
    }

    Map<String, String> prefixMap = ioHelper.getPrefixes();
    for (String base : line.getOptionValues("base-iri")) {
      if (!base.contains(":")) {
        String expanded = prefixMap.getOrDefault(base, null);
        if (expanded != null) {
          bases.add(expanded);
        } else {
          logger.error(String.format("Unknown prefix: '%s'", base));
        }
      } else {
        bases.add(base);
      }
    }

    return bases;
  }

  /**
   * Given a command line, an argument name, the boolean default value, and boolean if the arg is
   * optional, return the value of the command-line option 'name'.
   *
   * @param line the command line to use
   * @param name the name of the option to find
   * @param defaultValue the default value to use if the option is not provided
   * @param optionalArg if true, the option without an arg will return true
   * @return the option value as boolean, or the default if not found
   */
  public static boolean getBooleanValue(
      CommandLine line, String name, boolean defaultValue, boolean optionalArg) {
    if (line.hasOption(name)) {
      if (CommandLineHelper.getOptionalValue(line, name) == null) {
        return true;
      } else {
        return CommandLineHelper.getBooleanValue(line, name, defaultValue);
      }
    } else {
      return false;
    }
  }

  /**
   * Given a command line, return the value of --axioms as a set of classes that extend OWLAxiom.
   *
   * @deprecated split into methods {@link #cleanAxiomStrings(CommandLine)} and others in {@link
   *     org.obolibrary.robot.RelatedObjectsHelper}
   * @param line the command line to use
   * @return set of OWLAxiom types
   */
  @Deprecated
  public static Set<Class<? extends OWLAxiom>> getAxiomValues(CommandLine line) {
    Set<Class<? extends OWLAxiom>> axiomTypes = new HashSet<>();
    List<String> axiomTypeStrings = cleanAxiomStrings(line);
    // Then get the actual types
    for (String axiom : axiomTypeStrings) {
      Set<Class<? extends OWLAxiom>> addTypes = RelatedObjectsHelper.getAxiomValues(axiom);
      if (addTypes != null) {
        axiomTypes.addAll(addTypes);
      }
    }
    return axiomTypes;
  }

  /**
   * Get the boolean value of a command-line option with the given name.
   *
   * @param line the command line to use
   * @param name the name of the option to find
   * @param defaultValue the default value to use
   * @return the option value as boolean, or the default if not found
   */
  public static boolean getBooleanValue(CommandLine line, String name, boolean defaultValue) {
    String val = getDefaultValue(line, name, String.valueOf(defaultValue));
    if ("true".equals(val)) {
      return true;
    } else if ("false".equals(val)) {
      return false;
    } else {
      throw new IllegalArgumentException(String.format(booleanValueError, name));
    }
  }

  /**
   * Get the value of the command-line option with the given name.
   *
   * @param line the command line to use
   * @param name the name of the option to find
   * @return the option value as a string, or null if not found
   */
  public static String getOptionalValue(CommandLine line, String name) {
    return getDefaultValue(line, name, null);
  }

  /**
   * Get the value of the command-line options with the given name.
   *
   * @param line the command line to use
   * @param name the name of the option to find
   * @return the option value as a list of strings, maybe empty
   */
  public static List<String> getOptionalValues(CommandLine line, String name) {
    if (line.hasOption(name)) {
      return new ArrayList<>(Arrays.asList(line.getOptionValues(name)));
    } else {
      return new ArrayList<>();
    }
  }

  /**
   * Get the value of the command-line option with the given name, or return a default value if the
   * option is not found.
   *
   * @param line the command line to use
   * @param name the name of the option to find
   * @param defaultValue the default value to use
   * @return the option value as a string, or the default if the option not found
   */
  public static String getDefaultValue(CommandLine line, String name, String defaultValue) {
    String result = defaultValue;
    if (line.hasOption(name)) {
      // Only one arg should be passed per option
      result = line.getOptionValue(name);
    }
    return result;
  }

  /**
   * Get the value of the command-line option with the given name, or throw an
   * IllegalArgumentException with a given message if the option is not found.
   *
   * @param line the command line to use
   * @param name the name of the option to find
   * @param message the message for the exception
   * @return the option value as a string
   * @throws IllegalArgumentException if the option is not found
   */
  public static String getRequiredValue(CommandLine line, String name, String message)
      throws IllegalArgumentException {
    String result = getOptionalValue(line, name);
    if (result == null) {
      throw new IllegalArgumentException(String.format(missingRequirementError, message));
    }
    return result;
  }

  /**
   * Given a command line, return an initialized IOHelper. The --prefix, --add-prefix, --prefixes,
   * --add-prefixes, --noprefixes, --xml-entities, and --base options are handled.
   *
   * @param line the command line to use
   * @return an initialized IOHelper
   * @throws IOException on issue creating IOHelper with given context
   */
  public static IOHelper getIOHelper(CommandLine line) throws IOException {
    IOHelper ioHelper;
    String prefixes = getOptionalValue(line, "prefixes");
    if (prefixes != null) {
      ioHelper = new IOHelper(prefixes);
    } else {
      ioHelper = new IOHelper(!line.hasOption("noprefixes"));
    }
    prefixes = getOptionalValue(line, "add-prefixes");
    if (prefixes != null) {
      ioHelper.addPrefixes(prefixes);
    }

    for (String prefix : getOptionalValues(line, "prefix")) {
      ioHelper.addPrefix(prefix);
    }

    for (String prefix : getOptionalValues(line, "add-prefix")) {
      ioHelper.addPrefix(prefix);
    }

    ioHelper.setXMLEntityFlag(line.hasOption("xml-entities"));
    ioHelper.setStrict(line.hasOption("strict"));

    return ioHelper;
  }

  /**
   * Given an IOHelper and a command line, check for required options and return a loaded input
   * ontology. Currently handles --input and --input-iri options.
   *
   * @param ioHelper the IOHelper to load the ontology with
   * @param line the command line to use
   * @return the input ontology
   * @throws IllegalArgumentException if requires options are missing
   * @throws IOException if the ontology cannot be loaded
   */
  public static OWLOntology getInputOntology(IOHelper ioHelper, CommandLine line)
      throws IllegalArgumentException, IOException {
    // Get the catalog if specified by --catalog
    String catalogPath = getOptionalValue(line, "catalog");
    return getInputOntology(ioHelper, line, catalogPath);
  }

  /**
   * Given an IOHelper, a command line, and a path to a catalog, return an OWLOntology loaded from
   * input or input-iri using the specified catalog (or null).
   *
   * @param ioHelper the IOHelper to load the ontology with
   * @param line the command line to use
   * @param catalogPath the catalog to use to load imports
   * @return the input ontology
   * @throws IllegalArgumentException if requires options are missing
   * @throws IOException if the ontology cannot be loaded
   */
  public static OWLOntology getInputOntology(
      IOHelper ioHelper, CommandLine line, String catalogPath)
      throws IllegalArgumentException, IOException {
    List<String> inputOntologyPaths = getOptionalValues(line, "input");
    List<String> inputOntologyIRIs = getOptionalValues(line, "input-iri");

    int check = inputOntologyPaths.size() + inputOntologyIRIs.size();
    if (check > 1) {
      throw new IllegalArgumentException(multipleInputsError);
    }

    if (!inputOntologyPaths.isEmpty()) {
      if (catalogPath != null) {
        return ioHelper.loadOntology(inputOntologyPaths.get(0), catalogPath);
      } else {
        return ioHelper.loadOntology(inputOntologyPaths.get(0));
      }
    } else if (!inputOntologyIRIs.isEmpty()) {
      if (catalogPath != null) {
        return ioHelper.loadOntology(IRI.create(inputOntologyIRIs.get(0)), catalogPath);
      } else {
        return ioHelper.loadOntology(IRI.create(inputOntologyIRIs.get(0)));
      }
    } else {
      // Both input options are empty
      throw new IllegalArgumentException(missingInputError);
    }
  }

  /**
   * Given an IOHelper and a command line, check for required options and return a list of loaded
   * input ontologies. Currently handles --input, --input-iri, and --inputs options.
   *
   * @param ioHelper the IOHelper to load the ontology with
   * @param line the command line to use
   * @param allowEmpty if an empty list may be returned (when chaining commands, an input was
   *     already provided)
   * @return the list of input ontologies
   * @throws IllegalArgumentException if requires options are missing
   * @throws IOException if the ontology cannot be loaded
   */
  public static List<OWLOntology> getInputOntologies(
      IOHelper ioHelper, CommandLine line, boolean allowEmpty)
      throws IllegalArgumentException, IOException {
    List<OWLOntology> inputOntologies = new ArrayList<>();
    String catalogPath = getOptionalValue(line, "catalog");

    if (catalogPath != null) {
      inputOntologies.addAll(getInputOntologies(ioHelper, line, catalogPath));
    } else {
      inputOntologies.addAll(getInputOntologies(ioHelper, line));
    }

    if (inputOntologies.isEmpty() && !allowEmpty) {
      throw new IllegalArgumentException(missingInputsError);
    }

    return inputOntologies;
  }

  /**
   * Given an IOHelper, a state object, and a command line, update the state with the ontology.
   *
   * @param ioHelper the IOHelper to load the ontology with
   * @param state the input state, maybe null
   * @param line the command line to use
   * @return the updated state
   * @throws IllegalArgumentException if requires options are missing
   */
  public static CommandState updateInputOntology(
      IOHelper ioHelper, CommandState state, CommandLine line) throws IllegalArgumentException {
    return updateInputOntology(ioHelper, state, line, true);
  }

  /**
   * Given an IOHelper, a state object, a command line, and a "required" flag, update the state with
   * the ontology. If the state contains an ontology, use it. If the state is null or does not
   * contain an ontology, use the `--input` option. If the state has an ontology and there's an
   * `--input` throw an exception warning the use to use two commands instead of a chain of
   * commands.
   *
   * @param ioHelper the IOHelper to load the ontology with
   * @param state the input state, maybe null
   * @param line the command line to use
   * @param required when true, throw an exception if ontology is not found
   * @return the updated state
   * @throws IllegalArgumentException if requires options are missing
   */
  public static CommandState updateInputOntology(
      IOHelper ioHelper, CommandState state, CommandLine line, boolean required)
      throws IllegalArgumentException {
    if (state != null && state.getOntology() != null) {
      if (line.hasOption("input") || line.hasOption("input-IRI")) {
        throw new IllegalArgumentException(chainedInputError);
      } else {
        return state;
      }
    }

    // If normal --input is used, save the path
    String ontologyPath = CommandLineHelper.getOptionalValue(line, "input");
    if (ontologyPath != null) {
      state.setOntologyPath(ontologyPath);
    }

    // If a --catalog is provided, save the catalog
    String catalogPath = CommandLineHelper.getOptionalValue(line, "catalog");
    if (catalogPath != null) {
      state.setCatalogPath(catalogPath);
    }

    OWLOntology ontology = null;
    try {
      ontology = getInputOntology(ioHelper, line, catalogPath);
    } catch (Exception e) {
      if (required) {
        // Throw message from IOHelper
        throw new IllegalArgumentException(e);
      }
    }

    if (state == null) {
      state = new CommandState();
    }
    state.setOntology(ontology);
    return state;
  }

  /**
   * Given a command line, check for the required options and return a File for saving data.
   *
   * @param line the command line to use
   * @return the File for output; may be null; may not exist!
   * @throws IllegalArgumentException if required options are not found
   */
  public static File getOutputFile(CommandLine line) throws IllegalArgumentException {
    String outputPath = getOptionalValue(line, "output");
    File outputFile = null;
    if (outputPath != null) {
      outputFile = new File(outputPath);
    }
    return outputFile;
  }

  /**
   * Given a command line, check for the required options and return an IRI to be used as the
   * OntologyIRI for the output ontology.
   *
   * @param line the command line to use
   * @return the IRI for the output ontology, or null
   */
  public static IRI getOutputIRI(CommandLine line) {
    String outputIRIString = getOptionalValue(line, "output-iri");
    IRI outputIRI = null;
    if (outputIRIString != null) {
      outputIRI = IRI.create(outputIRIString);
    }
    return outputIRI;
  }

  /**
   * Given a command line and an ontology, for each `--output` option (if any), save a copy of the
   * ontology to the specified path.
   *
   * @param line the command lien to use
   * @param ontology the ontology to save
   * @throws IOException on any problem
   */
  public static void maybeSaveOutput(CommandLine line, OWLOntology ontology) throws IOException {
    IOHelper ioHelper = getIOHelper(line);
    // Determine if OBO structure should be enforced
    boolean checkOBO = CommandLineHelper.getBooleanValue(line, "check", true);

    // And if we should produce "clean" OBO output
    EnumSet<OBOWriteOption> cleanOBO =
        OBOWriteOption.fromString(line.getOptionValue("clean-obo", "false"));

    // Determine if prefixes should be added to the header of output
    // Create a map of these to include in output (or an empty map)
    Map<String, String> addPrefixes = getAddPrefixes(line);

    // Get an output format or null
    // If null, format will be guessed from the output path
    String format = CommandLineHelper.getOptionalValue(line, "format");
    OWLDocumentFormat df;
    if (format != null) {
      df = IOHelper.getFormat(format);
    } else {
      df = null;
    }

    // Save outputs
    for (String path : getOptionValues(line, "output")) {
      try {
        // maybe guess the document format
        OWLDocumentFormat thisDf = df;
        if (thisDf == null) {
          if (path.endsWith(".gz")) {
            path = path.substring(0, path.lastIndexOf("."));
          }
          String formatName = FilenameUtils.getExtension(path);
          thisDf = IOHelper.getFormat(formatName);
        }
        ioHelper.saveOntology(
            ontology, thisDf, IRI.create(new File(path)), addPrefixes, checkOBO, cleanOBO);
      } catch (IllegalArgumentException e) {
        // Exception from getFormat -- invalid format
        throw new IllegalArgumentException(
            String.format(IOHelper.invalidFormatError, path.substring(path.lastIndexOf(".") + 1)),
            e);
      }
    }
  }

  /**
   * Try to create an IRI from a string input. If the term is not in a valid format (null), an
   * IllegalArgumentException is thrown to prevent null from being passed into other methods.
   *
   * @param ioHelper IOHelper to use
   * @param term the term to convert to an IRI
   * @param field the field in which the term was entered, for reporting
   * @return the new IRI if successful
   */
  public static IRI maybeCreateIRI(IOHelper ioHelper, String term, String field) {
    IRI iri = ioHelper.createIRI(term);
    if (iri == null) {
      throw new IllegalArgumentException(String.format(invalidIRIError, field, term));
    }
    return iri;
  }

  /**
   * Given a command line, get a map of all prefixes to use and add to the output.
   *
   * @param line the command line to use
   * @return a map of prefixes to add to output
   * @throws IOException if the prefixes are not formatted correctly or a JSON file cannot be read
   */
  public static Map<String, String> getAddPrefixes(CommandLine line) throws IOException {
    Map<String, String> addPrefixes = new HashMap<>();
    if (line.hasOption("add-prefix")) {
      for (String pref : CommandLineHelper.getOptionalValues(line, "add-prefix")) {
        String[] split = pref.split(": ");
        if (split.length != 2) {
          throw new IOException(String.format(IOHelper.invalidPrefixError, pref));
        }
        addPrefixes.put(split[0], split[1]);
      }
    }
    if (line.hasOption("add-prefixes")) {
      for (String prefixFilePath : CommandLineHelper.getOptionalValues(line, "add-prefixes")) {
        File prefixFile = new File(prefixFilePath);
        if (!prefixFile.exists()) {
          throw new IOException(String.format(IOHelper.fileDoesNotExistError, prefixFilePath));
        }
        Context json =
            IOHelper.parseContext(FileUtils.readFileToString(prefixFile, Charset.defaultCharset()));
        addPrefixes.putAll(json.getPrefixes(false));
      }
    }
    return addPrefixes;
  }

  /**
   * Given an IOHelper and a command line, check for the required options and return a set of IRIs
   * for terms. Handles --terms and --term-file options.
   *
   * @param ioHelper the IOHelper to use for loading the terms
   * @param line the command line to use
   * @return a set of term IRIs
   * @throws IllegalArgumentException if the required options are not found
   * @throws IOException if the term file cannot be loaded
   */
  public static Set<IRI> getTerms(IOHelper ioHelper, CommandLine line)
      throws IllegalArgumentException, IOException {
    return getTerms(ioHelper, line, false);
  }

  /**
   * As getTerms, but allow the list to be empty.
   *
   * @param ioHelper the IOHelper to use for loading the terms
   * @param line the command line to use
   * @param allowEmpty true if empty lists of properties are allowed
   * @return a set of term IRIs
   * @throws IllegalArgumentException if the required options are not found
   * @throws IOException if the term file cannot be loaded
   */
  public static Set<IRI> getTerms(IOHelper ioHelper, CommandLine line, boolean allowEmpty)
      throws IllegalArgumentException, IOException {
    Set<IRI> terms = getTerms(ioHelper, line, "term", "term-file");

    if (terms.size() == 0 && !allowEmpty) {
      throw new IllegalArgumentException(missingTermsError);
    }
    return terms;
  }

  /**
   * Given an IOHelper and a command line, and the names of two options, check for the required
   * options and return a set of IRIs for terms. Handles single term options and term-file options.
   * Allows empty returns.
   *
   * @param ioHelper the IOHelper to use for loading the terms
   * @param line the command line to use
   * @param singles the option name for single terms, or null
   * @param paths the option name for term file paths, or null
   * @return a set of term IRIs
   * @throws IllegalArgumentException if the required options are not found
   * @throws IOException if the term file cannot be loaded
   */
  public static Set<IRI> getTerms(IOHelper ioHelper, CommandLine line, String singles, String paths)
      throws IllegalArgumentException, IOException {
    Set<String> termStrings = new HashSet<>();
    if (singles != null) {
      termStrings.addAll(getOptionValues(line, singles));
    }
    if (paths != null) {
      for (String path : getOptionValues(line, paths)) {
        termStrings.add(FileUtils.readFileToString(new File(path), Charset.defaultCharset()));
      }
    }

    Set<IRI> terms = new HashSet<>();
    for (String termString : termStrings) {
      terms.addAll(ioHelper.parseTerms(termString));
    }

    return terms;
  }

  /**
   * Given a string of a reasoner name from user input, return the reasoner factory. If the user
   * input is not valid, throw IllegalArgumentExcepiton. By default, EMR is not allowed.
   *
   * @param line the command line to use
   * @return OWLReasonerFactory if successful
   */
  public static OWLReasonerFactory getReasonerFactory(CommandLine line) {
    return getReasonerFactory(line, false);
  }

  /**
   * Given a string of a reasoner name from user input, return the reasoner factory. If the user
   * input is not valid, throw IllegalArgumentExcepiton.
   *
   * @param line the command line to use
   * @param allowEMR boolean specifying if EMR can be returned
   * @return OWLReasonerFactory if successful
   */
  public static OWLReasonerFactory getReasonerFactory(CommandLine line, boolean allowEMR) {
    // ELK is the default reasoner
    String reasonerName = getDefaultValue(line, "reasoner", "ELK").trim().toLowerCase();
    logger.info("Reasoner: " + reasonerName);

    if (reasonerName.equals("structural")) {
      return new org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory();
    } else if (reasonerName.equals("hermit")) {
      return new org.semanticweb.HermiT.ReasonerFactory();
    } else if (reasonerName.equals("jfact")) {
      return new JFactFactory();
      // Reason must change behavior with EMR, so not all commands can use it
    } else if (reasonerName.equals("emr") && allowEMR) {
      ElkReasonerFactory innerReasonerFactory = new org.semanticweb.elk.owlapi.ElkReasonerFactory();
      return new ExpressionMaterializingReasonerFactory(innerReasonerFactory);
    } else if (reasonerName.equals("elk")) {
      return new org.semanticweb.elk.owlapi.ElkReasonerFactory();
    } else if (reasonerName.equals("whelk")) {
      return new WhelkOWLReasonerFactory();
    } else {
      throw new IllegalArgumentException(String.format(invalidReasonerError, reasonerName));
    }
  }

  /**
   * Print a help message for a command.
   *
   * @param usage the usage information for the command
   * @param options the command line options for the command
   */
  public static void printHelp(String usage, Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp(usage, options);
  }

  /**
   * Print the ROBOT version
   *
   * @throws IOException on issue getting info from JAR
   */
  public static void printVersion() throws IOException {
    Properties p = new Properties();
    // The resource can be accessed from the class, except when running as a JAR
    URL resource =
        CommandLineHelper.class
            .getClassLoader()
            .getResource("/META-INF/maven/org.obolibrary.robot/robot-command/pom.properties");
    if (resource != null) {
      URI uri;
      try {
        uri = resource.toURI();
      } catch (URISyntaxException e) {
        throw new IOException(e);
      }
      File f = new File(uri);
      InputStream is = new FileInputStream(f);
      p.load(is);
    } else {
      // Brute-force to get properties file from JAR
      // This will be used any time `robot --version` is entered on command line
      String cls = CommandLineHelper.class.getName().replace(".", "/") + ".class";
      resource = CommandLineHelper.class.getClassLoader().getResource(cls);
      if (resource == null) {
        throw new IOException(
            "Cannot access version information from JAR. The resource does not exist.");
      }
      if (resource.getProtocol().equals("jar")) {
        // Get the JAR path and open as JAR file
        String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
        try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"))) {
          ZipEntry entry =
              jar.getEntry("META-INF/maven/org.obolibrary.robot/robot-command/pom.properties");
          if (entry != null) {
            InputStream is = jar.getInputStream(entry);
            p.load(is);
          } else {
            throw new IOException(
                "Cannot access version information from JAR. The properties file does not exist.");
          }
        }
      }
    }
    String version = p.getProperty("version");
    System.out.println("ROBOT version " + version);
  }

  /**
   * Create a new Options object with shared options for 'help' and 'version'.
   *
   * @return a new Options object with some options added
   */
  public static Options getCommonOptions() {
    Options o = new Options();
    o.addOption("h", "help", false, "print usage information");
    o.addOption("V", "version", false, "print version information");
    o.addOption("v", "verbose", false, "increased logging");
    o.addOption("vv", "very-verbose", false, "high logging");
    o.addOption("vvv", "very-very-verbose", false, "maximum logging, including stack traces");
    o.addOption(null, "catalog", true, "use catalog from provided file");
    o.addOption("p", "prefix", true, "add a prefix 'foo: http://bar'");
    o.addOption("P", "prefixes", true, "use prefixes from JSON-LD file");
    o.addOption(null, "noprefixes", false, "do not use default prefixes");
    o.addOption(null, "add-prefix", true, "add prefix 'foo: http://bar' to the output");
    o.addOption(null, "add-prefixes", true, "add JSON-LD prefixes to the output");
    o.addOption("x", "xml-entities", false, "use entity substitution with ontology XML output");
    o.addOption(null, "strict", false, "use strict parsing when loading an ontology");
    return o;
  }

  /**
   * Parse the command line, handle help and other common options, and return null or a CommandLine.
   *
   * @param usage the usage string for this command
   * @param options the command-line options for this command
   * @param args the command-line arguments provided
   * @param stopAtNonOption same as CommandLineParser
   * @return a new CommandLine object or null
   * @throws ParseException if the arguments cannot be parsed
   * @throws IOException on issue printing version
   */
  public static CommandLine maybeGetCommandLine(
      String usage, Options options, String[] args, boolean stopAtNonOption)
      throws ParseException, IOException {
    CommandLineParser parser = new DefaultParser();
    CommandLine line = parser.parse(options, args, stopAtNonOption);

    Level level;
    if (line.hasOption("very-very-verbose")) {
      level = Level.DEBUG;
    } else if (line.hasOption("very-verbose")) {
      level = Level.INFO;
    } else if (line.hasOption("verbose")) {
      level = Level.WARN;
    } else {
      level = Level.ERROR;
    }
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    root.setLevel(level);

    if (hasFlagOrCommand(line, "help")) {
      printHelp(usage, options);
      return null;
    }
    if (hasFlagOrCommand(line, "version")) {
      printVersion();
      return null;
    }

    return line;
  }

  /**
   * Parse the command line, handle help and other common options, (May exit!) and return a
   * CommandLine.
   *
   * @param usage the usage string for this command
   * @param options the command-line options for this command
   * @param args the command-line arguments provided
   * @return a new CommandLine object or exit(0)
   * @throws ParseException if the arguments cannot be parsed
   * @throws IOException on issue printing version
   */
  public static CommandLine getCommandLine(String usage, Options options, String[] args)
      throws ParseException, IOException {
    CommandLine line = maybeGetCommandLine(usage, options, args, false);
    if (line == null) {
      System.exit(0);
    }
    return line;
  }

  /**
   * Shared method for dealing with exceptions, printing help, and exiting. Currently prints the
   * error message, stack trace (DEBUG), usage, and then exits.
   *
   * @param exception the exception to handle
   */
  public static void handleException(Exception exception) {
    ExceptionHelper.handleException(exception);
    System.exit(1);
  }

  /**
   * Shared method for dealing with exceptions, printing help, and exiting. Currently prints the
   * error message, stack trace (DEBUG), usage, and then exits.
   *
   * @param usage the usage string for this command; WARN: not used
   * @param options the command-line options for this command; WARN: not used
   * @param exception the exception to handle
   */
  public static void handleException(String usage, Options options, Exception exception) {
    ExceptionHelper.handleException(exception);
    System.exit(1);
  }

  /**
   * Given an input string, return a list of the string split on whitespace, while ignoring any
   * whitespace in single string quotes.
   *
   * @param selects String of select options to split
   * @return List of split strings
   */
  protected static List<String> splitSelects(String selects) {
    List<String> split = new ArrayList<>();
    Matcher m = Pattern.compile("([^\\s]+=.*'[^']+'[^\\s']*|[^\\s']+)").matcher(selects);
    while (m.find()) {
      String s = m.group(1).trim();
      split.add(s);
    }
    return split;
  }

  /**
   * Given a wildcard pattern as string, return an array of files matching that pattern.
   *
   * @param pattern wildcard pattern to match
   * @return array of files
   * @throws IllegalArgumentException on bad pattern
   */
  private static File[] getFilesByPattern(String pattern) throws IllegalArgumentException {
    if (!pattern.contains("*") && !pattern.contains("?")) {
      throw new IllegalArgumentException(wildcardError);
    }
    // Get the parent directory path
    File filePattern = new File(pattern);
    String dir = filePattern.getParent();
    if (dir == null) {
      dir = ".";
    }
    // Make sure this directory exists
    if (!new File(dir).isDirectory()) {
      // Warn user, but continue (empty input checked later)
      logger.error("'{}' is not a valid directory for --inputs pattern", dir);
      return new File[] {};
    }
    FileFilter fileFilter = new WildcardFileFilter(filePattern.getName());
    File[] files = new File(dir).listFiles(fileFilter);
    if (files == null || files.length < 1) {
      // Warn user, but continue (empty input checked later)
      logger.error("No files match pattern: {}", pattern);
      return new File[] {};
    }
    return files;
  }

  /**
   * Given an IOHelper and a command line, check input options and return a list of loaded input
   * ontologies.
   *
   * @param ioHelper the IOHelper to load the ontology with
   * @param line the command line to use
   * @return the list of input ontologies
   * @throws IllegalArgumentException on bad pattern
   * @throws IOException if the ontology cannot be loaded
   */
  public static List<OWLOntology> getInputOntologies(IOHelper ioHelper, CommandLine line)
      throws IllegalArgumentException, IOException {
    List<OWLOntology> inputOntologies = new ArrayList<>();
    // Check for input files
    List<String> inputOntologyPaths = getOptionalValues(line, "input");
    for (String inputOntologyPath : inputOntologyPaths) {
      inputOntologies.add(ioHelper.loadOntology(inputOntologyPath));
    }
    // Check for input IRIs
    List<String> inputOntologyIRIs = getOptionalValues(line, "input-iri");
    for (String inputOntologyIRI : inputOntologyIRIs) {
      inputOntologies.add(ioHelper.loadOntology(IRI.create(inputOntologyIRI)));
    }
    // Check for input patterns (wildcard)
    String pattern = getOptionalValue(line, "inputs");
    if (pattern != null) {
      for (File inputOntologyFile : getFilesByPattern(pattern)) {
        inputOntologies.add(ioHelper.loadOntology(inputOntologyFile));
      }
    }
    return inputOntologies;
  }

  /**
   * Given an IOHelper, a command line, and the path to a catalog file, check input options and
   * return a list of loaded input ontologies with the catalog file.
   *
   * @param ioHelper the IOHelper to load the ontology with
   * @param line the command line to use
   * @param catalogPath the catalog file to use
   * @return the list of input ontologies
   * @throws IOException if the ontology cannot be loaded
   */
  public static List<OWLOntology> getInputOntologies(
      IOHelper ioHelper, CommandLine line, String catalogPath) throws IOException {
    List<OWLOntology> inputOntologies = new ArrayList<>();
    // Check for input files
    List<String> inputOntologyPaths = getOptionalValues(line, "input");
    for (String inputOntologyPath : inputOntologyPaths) {
      inputOntologies.add(ioHelper.loadOntology(inputOntologyPath, catalogPath));
    }
    // Check for input IRIs
    List<String> inputOntologyIRIs = getOptionalValues(line, "input-iri");
    for (String inputOntologyIRI : inputOntologyIRIs) {
      inputOntologies.add(ioHelper.loadOntology(IRI.create(inputOntologyIRI), catalogPath));
    }
    // Check for input patterns (wildcard)
    String pattern = getOptionalValue(line, "inputs");
    if (pattern != null) {
      File catalogFile = new File(catalogPath);
      for (File inputOntologyFile : getFilesByPattern(pattern)) {
        inputOntologies.add(ioHelper.loadOntology(inputOntologyFile, catalogFile));
      }
    }
    return inputOntologies;
  }

  /**
   * Given an IOHelper and a list of unparsed patterns to select annotation assertions to be dropped
   * build a Map of property IRIs, and value patterns for further processing.
   *
   * @param ioHelper the IOHelper to load the ontology with
   * @param dropParameters The list of unparsed command line parameters describing the annotations
   *     to select
   * @return A map of IRI to annotation value to drop
   * @throws IOException if annotation cannot be created
   */
  public static Map<IRI, String> createAnnotationToDropMap(
      IOHelper ioHelper, List<String> dropParameters) throws IOException {
    Map<IRI, String> annotationsToDrop = new HashMap<>();

    for (String dropParameter : dropParameters) {
      if (dropParameter.equalsIgnoreCase("all")) {
        // ignore, we communicate this through the dropParameters list
      } else if (dropParameter.contains("=")) {
        String[] parts = dropParameter.split("=");
        String annotationIRI = parts[0];
        String annotationValue = parts[1];
        IRI iri =
            CommandLineHelper.maybeCreateIRI(ioHelper, annotationIRI, "drop-axiom-annotations");
        annotationsToDrop.put(iri, annotationValue);
      } else {
        IRI iri =
            CommandLineHelper.maybeCreateIRI(ioHelper, dropParameter, "drop-axiom-annotations");
        annotationsToDrop.put(iri, null);
      }
    }
    return annotationsToDrop;
  }
}
