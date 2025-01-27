package org.obolibrary.robot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for ROBOT command-line interface.
 *
 * @author <a href="mailto:james@overton.ca">James A. Overton</a>
 */
public class CommandLineInterface {
  /** A CommandManager loaded with the default set of commands. */
  private static CommandManager manager = initManager();

  /**
   * Initialize a new CommandManager.
   *
   * @return the new manager
   */
  private static CommandManager initManager() {
    CommandManager m = new CommandManager();
    m.addCommand("annotate", new AnnotateCommand());
    m.addCommand("collapse", new CollapseCommand());
    m.addCommand("convert", new ConvertCommand());
    m.addCommand("diff", new DiffCommand());
    m.addCommand("expand", new ExpandCommand());
    m.addCommand("explain", new ExplainCommand());
    m.addCommand("export", new ExportCommand());
    m.addCommand("export-prefixes", new ExportPrefixesCommand());
    m.addCommand("extract", new ExtractCommand());
    m.addCommand("filter", new FilterCommand());
    m.addCommand("materialize", new MaterializeCommand());
    m.addCommand("measure", new MeasureCommand());
    m.addCommand("merge", new MergeCommand());
    m.addCommand("mint", new MintCommand());
    m.addCommand("mirror", new MirrorCommand());
    m.addCommand("python", new PythonCommand());
    m.addCommand("query", new QueryCommand());
    m.addCommand("reason", new ReasonCommand());
    m.addCommand("reduce", new ReduceCommand());
    m.addCommand("relax", new RelaxCommand());
    m.addCommand("remove", new RemoveCommand());
    m.addCommand("rename", new RenameCommand());
    m.addCommand("repair", new RepairCommand());
    m.addCommand("report", new ReportCommand());
    m.addCommand("template", new TemplateCommand());
    m.addCommand("unmerge", new UnmergeCommand());
    m.addCommand("validate-profile", new ValidateProfileCommand());
    m.addCommand("verify", new VerifyCommand());

    PluginManager pm = new PluginManager();
    pm.addPluggableCommands(m);

    return m;
  }

  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(CommandLineInterface.class);

  /**
   * Execute the given command-line arguments, catching any exceptions.
   *
   * @param args the command-line arguments
   */
  public static void main(String[] args) {
    manager.main(args);
  }

  /**
   * Execute the given command-line arguments, throwing any exceptions.
   *
   * @param args the command-line arguments
   * @throws Exception on any problem
   */
  public static void execute(String[] args) throws Exception {
    manager.execute(null, args);
  }
}
