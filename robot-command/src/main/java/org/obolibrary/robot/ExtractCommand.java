package org.obolibrary.robot;

import com.google.common.collect.Sets;
import java.util.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles inputs and outputs for the {@link ExtractOperation}.
 *
 * @author <a href="mailto:james@overton.ca">James A. Overton</a>
 */
public class ExtractCommand implements Command {
  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(ExtractCommand.class);

  /** Namespace for error messages. */
  private static final String NS = "extract#";

  /** Error message when lower or branch terms are not specified with MIREOT. */
  private static final String missingMireotTermsError =
      NS
          + "MISSING MIREOT TERMS ERROR "
          + "either lower term(s) or branch term(s) must be specified for MIREOT";

  /** Error message when only upper terms are specified with MIREOT. */
  private static final String missingLowerTermError =
      NS
          + "MISSING LOWER TERMS ERROR "
          + "lower term(s) must be specified with upper term(s) for MIREOT";

  /** Error message when user provides invalid extraction method. */
  private static final String invalidMethodError =
      NS + "INVALID METHOD ERROR method must be: MIREOT, STAR, TOP, BOT";

  /** Error message when a MIREOT option is used for SLME. */
  private static final String invalidOptionError =
      NS
          + "INVALID OPTION ERROR "
          + "only --term or --term-file can be used to specify extract term(s) "
          + "for STAR, TOP, or BOT";

  /** Set of valid SLME extraction methods. */
  private static final Set<String> validMethods = Sets.newHashSet("star", "bot", "top");

  /** Store the command-line options for the command. */
  private Options options;

  /** Initialze the command. */
  public ExtractCommand() {
    Options o = CommandLineHelper.getCommonOptions();
    o.addOption("i", "input", true, "load ontology from a file");
    o.addOption("I", "input-iri", true, "load ontology from an IRI");
    o.addOption("o", "output", true, "save ontology to a file");
    o.addOption("O", "output-iri", true, "set OntologyIRI for output");
    o.addOption("m", "method", true, "extract method to use");
    o.addOption("t", "term", true, "term to extract");
    o.addOption("T", "term-file", true, "load terms from a file");
    o.addOption("u", "upper-term", true, "upper level term to extract");
    o.addOption("U", "upper-terms", true, "upper level terms to extract");
    o.addOption("l", "lower-term", true, "lower level term to extract");
    o.addOption("L", "lower-terms", true, "lower level terms to extract");
    o.addOption("b", "branch-from-term", true, "root term of branch to extract");
    o.addOption("B", "branch-from-terms", true, "root terms of branches to extract");
    o.addOption("c", "copy-ontology-annotations", true, "if true, include ontology annotations");
    o.addOption("f", "force", true, "if true, warn on empty input terms instead of fail");
    o.addOption("a", "annotate-with-source", true, "if true, annotate terms with rdfs:isDefinedBy");
    o.addOption("s", "sources", true, "specify a mapping file of term to source ontology");
    o.addOption("n", "intermediates", true, "specify how to handle intermediate entities");
    options = o;
  }

  /**
   * Name of the command.
   *
   * @return name
   */
  public String getName() {
    return "extract";
  }

  /**
   * Brief description of the command.
   *
   * @return description
   */
  public String getDescription() {
    return "extract terms from an ontology";
  }

  /**
   * Command-line usage for the command.
   *
   * @return usage
   */
  public String getUsage() {
    return "robot extract --input <file> "
        + "--term-file <file> "
        + "--output <file> "
        + "--output-iri <iri>";
  }

  /**
   * Command-line options for the command.
   *
   * @return options
   */
  public Options getOptions() {
    return options;
  }

  /**
   * Handle the command-line and file operations for the ExtractOperation.
   *
   * @param args strings to use as arguments
   */
  public void main(String[] args) {
    try {
      execute(null, args);
    } catch (Exception e) {
      CommandLineHelper.handleException(e);
    }
  }

  /**
   * Given an input state and command line arguments, extract a new ontology and return an new
   * state. The input ontology is not changed.
   *
   * @param state the state from the previous command, or null
   * @param args the command-line arguments
   * @return a new state with the extracted ontology
   * @throws Exception on any problem
   */
  public CommandState execute(CommandState state, String[] args) throws Exception {
    OWLOntology outputOntology;

    CommandLine line = CommandLineHelper.getCommandLine(getUsage(), getOptions(), args);
    if (line == null) {
      return null;
    }

    IOHelper ioHelper = CommandLineHelper.getIOHelper(line);
    state = CommandLineHelper.updateInputOntology(ioHelper, state, line);
    OWLOntology inputOntology = state.getOntology();

    IRI outputIRI = CommandLineHelper.getOutputIRI(line);
    if (outputIRI == null) {
      outputIRI = inputOntology.getOntologyID().getOntologyIRI().orNull();
    }

    // Override default reasoner options with command-line options
    Map<String, String> extractOptions = ExtractOperation.getDefaultOptions();
    for (String option : extractOptions.keySet()) {
      if (line.hasOption(option)) {
        extractOptions.put(option, line.getOptionValue(option));
      }
    }

    // Get method, make sure it has been specified
    String method =
        CommandLineHelper.getRequiredValue(line, "method", "method of extraction must be specified")
            .trim()
            .toLowerCase();

    boolean force = CommandLineHelper.getBooleanValue(line, "force", false);

    if (method.equals("mireot")) {
      List<OWLOntology> outputOntologies = new ArrayList<>();
      // Get terms from input (ensuring that they are in the input ontology)
      // It's okay for any of these to return empty (allowEmpty = true)
      // Checks for empty sets later
      Set<IRI> upperIRIs =
          OntologyHelper.filterExistingTerms(
              inputOntology,
              CommandLineHelper.getTerms(ioHelper, line, "upper-term", "upper-terms"),
              true);
      if (upperIRIs.size() == 0) {
        upperIRIs = null;
      }
      Set<IRI> lowerIRIs =
          OntologyHelper.filterExistingTerms(
              inputOntology,
              CommandLineHelper.getTerms(ioHelper, line, "lower-term", "lower-terms"),
              true);
      if (lowerIRIs.size() == 0) {
        lowerIRIs = null;
      }
      Set<IRI> branchIRIs =
          OntologyHelper.filterExistingTerms(
              inputOntology,
              CommandLineHelper.getTerms(ioHelper, line, "branch-from-term", "branch-from-terms"),
              true);
      if (branchIRIs.size() == 0) {
        branchIRIs = null;
      }

      // Need branch IRIs or lower IRIs to proceed
      if (branchIRIs == null && lowerIRIs == null) {
        throw new IllegalArgumentException(missingMireotTermsError);
      } else {
        // First check for lower IRIs, upper IRIs can be null or not
        if (lowerIRIs != null) {
          outputOntologies.add(
              MireotOperation.getAncestors(
                  inputOntology, ioHelper, upperIRIs, lowerIRIs, null, extractOptions));
          // If there are no lower IRIs, there shouldn't be any upper IRIs
        } else if (upperIRIs != null) {
          throw new IllegalArgumentException(missingLowerTermError);
        }
        // Check for branch IRIs
        if (branchIRIs != null) {
          outputOntologies.add(
              MireotOperation.getDescendants(
                  inputOntology, ioHelper, branchIRIs, null, extractOptions));
        }
      }
      outputOntology = MergeOperation.merge(outputOntologies);
    } else if (validMethods.contains(method.toLowerCase())) {
      // upper-term, lower-term, and branch-from term should not be used
      List<String> mireotTerms =
          Arrays.asList(
              CommandLineHelper.getOptionalValue(line, "upper-term"),
              CommandLineHelper.getOptionalValue(line, "upper-terms"),
              CommandLineHelper.getOptionalValue(line, "lower-term"),
              CommandLineHelper.getOptionalValue(line, "lower-terms"),
              CommandLineHelper.getOptionalValue(line, "branch-from-term"),
              CommandLineHelper.getOptionalValue(line, "branch-from-terms"));
      for (String mt : mireotTerms) {
        if (mt != null) {
          throw new IllegalArgumentException(invalidOptionError);
        }
      }
      // Make sure the terms exist in the input ontology
      Set<IRI> terms =
          OntologyHelper.filterExistingTerms(
              inputOntology, CommandLineHelper.getTerms(ioHelper, line), force);
      outputOntology =
          ExtractOperation.extract(inputOntology, ioHelper, terms, outputIRI, extractOptions);
    } else {
      throw new Exception(invalidMethodError);
    }

    // Maybe copy ontology annotations
    boolean copyOntologyAnnotations =
        CommandLineHelper.getBooleanValue(line, "copy-ontology-annotations", false);
    if (copyOntologyAnnotations) {
      for (OWLAnnotation annotation : inputOntology.getAnnotations()) {
        OntologyHelper.addOntologyAnnotation(outputOntology, annotation);
      }
    }

    CommandLineHelper.maybeSaveOutput(line, outputOntology);

    state.setOntology(outputOntology);
    return state;
  }
}
