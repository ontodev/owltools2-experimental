package org.obolibrary.robot;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles inputs and outputs for the {@link MaterializeOperation}.
 *
 * @author <a href="mailto:cjmungall@lbl.gov">Chris Mungall</a>
 */
public class MaterializeCommand implements Command {
  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(MaterializeCommand.class);

  /** Store the command-line options for the command. */
  private Options options;

  /** Initialize the command. */
  public MaterializeCommand() {
    Options o = CommandLineHelper.getCommonOptions();
    o.addOption("r", "reasoner", true, "core reasoner to use: " + "(ELK, HermiT)");
    o.addOption("s", "remove-redundant-subclass-axioms", true, "remove redundant subclass axioms");
    o.addOption(
        "n",
        "create-new-ontology",
        true,
        "switch to a new ontology containing only the inferences");
    o.addOption(
        "a",
        "annotate-inferred-axioms",
        true,
        "annotate all inferred axioms (only when -n is passed)");
    o.addOption("i", "input", true, "reason ontology from a file");
    o.addOption("I", "input-iri", true, "reason ontology from an IRI");
    o.addOption("o", "output", true, "save reasoned ontology to a file");
    o.addOption("t", "term", true, "a property to materialize");
    o.addOption("T", "term-file", true, "load properties from a file");
    o.addOption(
        "c", "include-indirect", true, "if true include redundant existential restrictions");
    options = o;
  }

  /**
   * Name of the command.
   *
   * @return name
   */
  public String getName() {
    return "materialize";
  }

  /**
   * Brief description of the command.
   *
   * @return description
   */
  public String getDescription() {
    return "materialize ontology";
  }

  /**
   * Command-line usage for the command.
   *
   * @return usage
   */
  public String getUsage() {
    return "robot materialize --input <file> "
        + "--reasoner <name> "
        + "[options] "
        + "--output <file>";
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
   * Given an input state and command line arguments, run a reasoner, and add axioms to the input
   * ontology, returning a state with the updated ontology.
   *
   * @param state the state from the previous command, or null
   * @param args the command-line arguments
   * @return the state with inferred axioms added to the ontology
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
    OWLReasonerFactory reasonerFactory = CommandLineHelper.getReasonerFactory(line);

    // Override default reasoner options with command-line options
    Map<String, String> reasonerOptions = ReasonOperation.getDefaultOptions();
    for (String option : reasonerOptions.keySet()) {
      if (line.hasOption(option)) {
        reasonerOptions.put(option, line.getOptionValue(option));
      }
    }

    boolean includeIndirect = CommandLineHelper.getBooleanValue(line, "include-indirect", false);

    Set<IRI> terms = CommandLineHelper.getTerms(ioHelper, line, true);
    Set<OWLObjectProperty> properties = new HashSet<>();
    for (IRI term : terms) {
      properties.add(
          ontology.getOWLOntologyManager().getOWLDataFactory().getOWLObjectProperty(term));
    }

    MaterializeOperation.materialize(ontology, reasonerFactory, properties, reasonerOptions);

    CommandLineHelper.maybeSaveOutput(line, ontology);

    return state;
  }
}
