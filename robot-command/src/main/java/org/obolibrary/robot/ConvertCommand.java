package org.obolibrary.robot;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Save ontology to a different format.
 *
 * @author <a href="mailto:james@overton.ca">James A. Overton</a>
 */
public class ConvertCommand implements Command {
  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(ConvertCommand.class);

  /** Namespace for error messages. */
  private static final String NS = "convert#";

  /** Error message when the arg to --check is not true or false */
  private static final String checkArgError =
      NS + "CHECK ARG ERROR the arg to --check should be either TRUE or FALSE";

  /** Error message when no --output is provided. */
  private static final String missingOutputError = NS + "OUTPUT ERROR an output file is required";

  /** Error message when more than one --output is provided. */
  private static final String multipleOutputsError =
      NS + "OUTPUT ERROR only one output file is allowed";

  /** Error message when a --format is not specified and the --output does not have an extension. */
  private static final String missingFormatError = NS + "FORMAT ERROR an output format is required";

  /** Store the command-line options for the command. */
  private Options options;

  /** Initialize the command. */
  public ConvertCommand() {
    Options o = CommandLineHelper.getCommonOptions();
    o.addOption("i", "input", true, "convert ontology from a file");
    o.addOption("I", "input-iri", true, "convert ontology from an IRI");
    o.addOption("o", "output", true, "save ontology to a file");
    o.addOption("f", "format", true, "the format: obo, owl, ttl, owx, omn, ofn, json");
    o.addOption("c", "check", true, "if false, ignore OBO document structure checks");
    o.addOption(null, "clean-obo", true, "options for clean OBO output (false|true|strict|simple)");
    options = o;
  }

  /**
   * Name of the command.
   *
   * @return name
   */
  public String getName() {
    return "convert";
  }

  /**
   * Brief description of the command.
   *
   * @return description
   */
  public String getDescription() {
    return "convert ontology";
  }

  /**
   * Command-line usage for the command.
   *
   * @return usage
   */
  public String getUsage() {
    return "robot convert --input <file> " + "--format <format> " + "--output <file>";
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
   * Handle the command-line and file operations for the ReasonOperation.
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
   * Given an input state and command line arguments, save the ontology to a new format and return
   * the state unchanged.
   *
   * <p>Supported formats:
   *
   * <ul>
   *   <li>OBO .obo
   *   <li>RDFXML .owl
   *   <li>Turtle .ttl
   *   <li>OWLXML .owx
   *   <li>Manchester .omn
   *   <li>OWL Functional .ofn
   *   <li>OboGraphs JSON .json
   * </ul>
   *
   * @param state the state from the previous command, or null
   * @param args the command-line arguments
   * @return the input state unchanged, or a new state with the ontology
   * @throws Exception on any problem
   */
  public CommandState execute(CommandState state, String[] args) throws Exception {
    CommandLine line = CommandLineHelper.getCommandLine(getUsage(), getOptions(), args);
    if (line == null) {
      return null;
    }

    if (state == null) {
      state = new CommandState();
    }

    IOHelper ioHelper = CommandLineHelper.getIOHelper(line);
    state = CommandLineHelper.updateInputOntology(ioHelper, state, line);
    OWLOntology ontology = state.getOntology();

    String[] outputs = line.getOptionValues("output");
    // Validate the output
    if (outputs == null || outputs.length == 0) {
      throw new IllegalArgumentException(missingOutputError);
    } else if (outputs.length > 1) {
      throw new IllegalArgumentException(multipleOutputsError);
    }

    CommandLineHelper.maybeSaveOutput(line, ontology);
    return state;
  }
}
