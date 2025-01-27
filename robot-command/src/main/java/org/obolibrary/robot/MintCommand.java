package org.obolibrary.robot;

import java.io.IOException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;

public class MintCommand implements Command {

  /** Store the command-line options for the command. */
  private Options options;

  public MintCommand() {
    Options o = CommandLineHelper.getCommonOptions();
    o.addOption("i", "input", true, "load ontology from a file");
    o.addOption("I", "input-iri", true, "load ontology from an IRI");
    o.addOption("o", "output", true, "save processed ontology to a file");
    o.addOption(null, "minted-id-prefix", true, "IRI prefix to prepend to minted identifiers");
    o.addOption(
        null,
        "minted-from-property",
        true,
        "property IRI used to link minted identifiers to temporary identifiers");
    o.addOption(null, "temp-id-prefix", true, "IRI prefix indicating temporary identifiers");
    o.addOption(
        null,
        "min-id",
        true,
        "start minted identifiers from the max of either this number or the highest identifier found which is less than or equal to max-id");
    o.addOption(
        null,
        "max-id",
        true,
        "fail if no identifier can be minted less than or equal to this number");
    o.addOption(
        null, "pad-width", true, "apply leading zeroes to minted identifiers up to this width");
    o.addOption(
        null,
        "keep-deprecated",
        true,
        "keep temporary terms in the ontology as deprecated entities");
    options = o;
  }

  @Override
  public String getName() {
    return "mint";
  }

  @Override
  public String getDescription() {
    return "replace temporary identifiers with newly minted official identifiers";
  }

  @Override
  public String getUsage() {
    return "robot mint --input <file> --minted-id-prefix <iri-prefix> --temp-id-prefix <iri-prefix> --minted-from-property <iri> --min-id <integer> --max-id <integer> --pad-width <integer> --keep-deprecated <bool> --output <file>";
  }

  @Override
  public Options getOptions() {
    return options;
  }

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
    IOHelper ioHelper = CommandLineHelper.getIOHelper(line);
    if (state == null) {
      state = new CommandState();
    }
    state = CommandLineHelper.updateInputOntology(ioHelper, state, line);
    OWLOntology ontology = state.getOntology();

    MintOperation.MintConfig config = parseConfig(line);
    MintOperation.mintIdentifiers(ontology, config);

    CommandLineHelper.maybeSaveOutput(line, ontology);
    state.setOntology(ontology);
    return state;
  }

  private MintOperation.MintConfig parseConfig(CommandLine line)
      throws IOException, NumberFormatException {
    IOHelper ioHelper = CommandLineHelper.getIOHelper(line);
    MintOperation.MintConfig config = new MintOperation.MintConfig();
    config.setMintedIDPrefix(
        CommandLineHelper.getDefaultValue(line, "minted-id-prefix", config.getMintedIDPrefix()));
    config.setTempIDPrefix(
        CommandLineHelper.getDefaultValue(line, "temp-id-prefix", config.getTempIDPrefix()));
    String propertyID = CommandLineHelper.getDefaultValue(line, "minted-from-property", "");
    if (!propertyID.isEmpty()) {
      IRI propertyIRI =
          CommandLineHelper.maybeCreateIRI(ioHelper, propertyID, "minted-from-property");
      config.setMintedFromProperty(
          OWLManager.getOWLDataFactory().getOWLAnnotationProperty(propertyIRI));
    }
    String minID = CommandLineHelper.getDefaultValue(line, "min-id", "");
    if (!minID.isEmpty()) {
      config.setMinIdentifier(Integer.parseInt(minID));
    }
    String maxID = CommandLineHelper.getDefaultValue(line, "max-id", "");
    if (!maxID.isEmpty()) {
      config.setMaxIdentifier(Integer.parseInt(maxID));
    }
    String padding = CommandLineHelper.getDefaultValue(line, "pad-width", "");
    if (!padding.isEmpty()) {
      config.setPadWidth(Integer.parseInt(padding));
    }
    config.setKeepDeprecated(
        CommandLineHelper.getBooleanValue(line, "keep-deprecated", config.isKeepDeprecated()));
    return config;
  }
}
