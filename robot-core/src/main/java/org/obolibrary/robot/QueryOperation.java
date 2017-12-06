package org.obolibrary.robot;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.sparql.core.DatasetGraph;
import com.hp.hpl.jena.sparql.core.DatasetGraphFactory;
import java.io.*;
import java.util.Map;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.ResultSetMgr;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query an ontology using SPARQL.
 *
 * @author <a href="mailto:james@overton.ca">James A. Overton</a>
 */
public class QueryOperation {
  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(QueryOperation.class);

  /**
   * Load an ontology into a DatasetGraph. The ontology is not changed. NOTE: This is not elegant!
   * It basically pipes Turtle output from OWLAPI to Jena Arq.
   *
   * @param ontology The ontology to load into the graph.
   * @return A new DatasetGraph with the ontology loaded into the default graph.
   * @throws OWLOntologyStorageException rarely
   */
  public static DatasetGraph loadOntology(OWLOntology ontology) throws OWLOntologyStorageException {
    // Load ontology into graph
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ontology.getOWLOntologyManager().saveOntology(ontology, new TurtleDocumentFormat(), out);
    DatasetGraph dsg = DatasetGraphFactory.createMem();
    RDFDataMgr.read(
        dsg.getDefaultGraph(), new ByteArrayInputStream(out.toByteArray()), Lang.TURTLE);
    return dsg;
  }

  /**
   * Execute a query and return a result set.
   *
   * @param dsg The graph to query over.
   * @param query The SPARQL query string.
   * @return the result set
   */
  public static ResultSet execQuery(DatasetGraph dsg, String query) {
    QueryExecution qexec = QueryExecutionFactory.create(query, DatasetFactory.create(dsg));
    return qexec.execSelect();
  }

  /**
   * Execute a construct query.
   *
   * @param dsg The graph to construct in.
   * @param query The SPARQL construct query string.
   * @return the result.
   */
  public static Model execConstruct(DatasetGraph dsg, String query) {
    QueryExecution exec = QueryExecutionFactory.create(query, DatasetFactory.create(dsg));
    return exec.execConstruct();
  }

  public static boolean execVerify(
      Map<File, Tuple<ResultSetRewindable, OutputStream>> queriesResults) throws IOException {
    boolean isViolation = false;
    for (File outFile : queriesResults.keySet()) {
      Tuple<ResultSetRewindable, OutputStream> resultAndStream = queriesResults.get(outFile);
      ResultSetRewindable results = resultAndStream.left();
      OutputStream outStream = resultAndStream.right();
      System.out.println(
          "Rule " + outFile.getCanonicalPath() + ": " + results.size() + " violation(s)");
      if (results.size() > 0) {
        isViolation = true;
        ResultSetMgr.write(System.err, results, Lang.CSV);
        results.reset();
      }
      System.err.print('\n');
      ResultSetMgr.write(outStream, results, Lang.CSV);
    }
    return isViolation;
  }

  /**
   * Run a query and write the result to a file.
   *
   * @param dsg The graph to query over.
   * @param query The SPARQL query string.
   * @param output The file to write to.
   * @param outputFormat The file format.
   * @throws FileNotFoundException if output file is not found
   */
  public static void runQuery(DatasetGraph dsg, String query, File output, Lang outputFormat)
      throws FileNotFoundException {
    if (outputFormat == null) {
      outputFormat = Lang.CSV;
    }
    ResultSetMgr.write(new FileOutputStream(output), execQuery(dsg, query), outputFormat);
  }

  /**
   * Run a construct query and write the result.
   *
   * @param dsg The graph to construct in.
   * @param query The SPARQL construct query string.
   * @param output The file to write to.
   * @param outputFormat The file format.
   * @throws FileNotFoundException if output file is not found
   */
  public static void runConstruct(DatasetGraph dsg, String query, File output, Lang outputFormat)
      throws FileNotFoundException {
    RDFDataMgr.write(new FileOutputStream(output), execConstruct(dsg, query), outputFormat);
  }

  /**
   * Count results.
   *
   * @param results The result set to count.
   * @return the size of the result set
   */
  public static int countResults(ResultSet results) {
    int i = 0;
    while (results.hasNext()) {
      results.next();
      i++;
    }
    return i;
  }
}
