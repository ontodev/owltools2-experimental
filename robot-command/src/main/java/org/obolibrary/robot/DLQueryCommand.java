package org.obolibrary.robot;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.obolibrary.robot.exceptions.InconsistentOntologyException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles inputs and outputs for the {@link DLQueryOperation}.
 *
 * @author <a href="mailto:nicolas.matentzoglu@gmail.com">Nicolas Matentzoglu</a>
 */
public class DLQueryCommand implements Command {
  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(DLQueryCommand.class);

  private static final String NS = "dl-query#";

  private static final List<String> LEGAL_RELATIONS =
      Arrays.asList("equivalents", "ancestors", "descendants", "instances", "parents", "children");
  OWLDataFactory df = OWLManager.getOWLDataFactory();

  private static final String maxTypeError = NS + "MAX TYPE ERROR --max ('%s') must be an integer";
  private static final String illegalRelationError =
      NS + "ILLEGAL RELATION ERROR: %s. Must be one of " + String.join(" ", LEGAL_RELATIONS) + ".";
  private static final String missingQueryArgumentError =
      NS + "MISSING QUERY ARGUMENT ERROR: must have a valid --query.";

  /** Error message when --query does not have two arguments. */
  private static final String missingOutputError =
      NS + "MISSING OUTPUT ERROR --%s requires two arguments: query and output";

  /** Error message when a query is not provided */
  private static final String missingQueryError =
      NS + "MISSING QUERY ERROR at least one query must be provided";

  /** Store the command-line options for the command. */
  private Options options;

  public DLQueryCommand() {
    Options o = CommandLineHelper.getCommonOptions();
    o.addOption("i", "input", true, "load ontology from a file");
    o.addOption("I", "input-iri", true, "load ontology from an IRI");
    o.addOption("r", "reasoner", true, "reasoner to use: ELK, HermiT, JFact");

    Option opt = new Option("q", "query", true, "the DL query to run");
    opt.setArgs(2);
    o.addOption(opt);

    o.addOption(
        "s",
        "select",
        true,
        "select what relations to query: equivalents, parents, children, ancestors, descendants, instances");
    o.addOption("o", "output", true, "save ontology containing only explanation axioms to a file");
    options = o;
  }

  @Override
  public String getName() {
    return "dl-query";
  }

  @Override
  public String getDescription() {
    return "query the ontology with the given class expression";
  }

  @Override
  public String getUsage() {
    return "robot dl-query --input <file> --query <expression> --output <output>";
  }

  @Override
  public Options getOptions() {
    return options;
  }

  /**
   * Handle the command-line and file operations for the DLQueryOperation.
   *
   * @param args strings to use as arguments
   */
  @Override
  public void main(String[] args) {
    try {
      execute(null, args);
    } catch (Exception e) {
      CommandLineHelper.handleException(e);
    }
  }

  @Override
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

    OWLReasonerFactory reasonerFactory = CommandLineHelper.getReasonerFactory(line, true);
    List<String> selects = CommandLineHelper.getOptionalValues(line, "select");

    List<List<String>> queries = getQueries(line);
    for (List<String> q : queries) {
      queryOntology(q, ontology, reasonerFactory, selects);
    }

    state.setOntology(ontology);
    CommandLineHelper.maybeSaveOutput(line, ontology);
    return state;
  }

  private void queryOntology(
      List<String> q,
      OWLOntology ontology,
      OWLReasonerFactory reasonerFactory,
      List<String> selects)
      throws InconsistentOntologyException, IOException {
    OWLReasoner r = reasonerFactory.createReasoner(ontology);
    String query = q.get(0);
    File output = new File(q.get(1));
    OWLClassExpression classExpression = DLQueryOperation.parseOWLClassExpression(query, ontology);
    if (r.isConsistent()) {
      List<OWLEntity> entities = DLQueryOperation.query(classExpression, r, selects);
      writeQueryResultsToFile(output, entities);
    } else {
      throw new InconsistentOntologyException();
    }
  }

  /**
   * Given a command line, get a list of queries.
   *
   * @param line CommandLine with options
   * @return List of queries
   */
  private static List<List<String>> getQueries(CommandLine line) {
    // Collect all queries as (queryPath, outputPath) pairs.
    List<List<String>> queries = new ArrayList<>();
    List<String> qs = CommandLineHelper.getOptionalValues(line, "query");
    for (int i = 0; i < qs.size(); i += 2) {
      try {
        queries.add(qs.subList(i, i + 2));
      } catch (IndexOutOfBoundsException e) {
        throw new IllegalArgumentException(String.format(missingOutputError, "query"));
      }
    }
    if (queries.isEmpty()) {
      throw new IllegalArgumentException(missingQueryError);
    }
    return queries;
  }

  private void writeQueryResultsToFile(File output, List<OWLEntity> results) throws IOException {
    Collections.sort(results);
    FileUtils.writeLines(output, results);
  }
}
