package org.obolibrary.robot;

import java.util.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.expression.ShortFormEntityChecker;
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxClassExpressionParser;
import org.semanticweb.owlapi.manchestersyntax.renderer.ParserException;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query the ontology with a class expression. This simulates the DL Query Tab in Protege.
 *
 * @author <a href="mailto:nicolas.matentzoglu@gmail.com">Nicolas Matentzoglu</a>
 */
public class DLQueryOperation {

  /** Logger */
  private static final Logger logger = LoggerFactory.getLogger(DLQueryOperation.class);

  private static final OWLDataFactory df = OWLManager.getOWLDataFactory();

  /**
   * Query the ontology with the provided class expression.
   *
   * @param expression the expression to be queried
   * @param reasoner the initialised reasoner to query
   * @param queryTypes the query results to be included (parents, children, equivants)
   * @return explanations
   */
  public static List<OWLEntity> query(
      OWLClassExpression expression, OWLReasoner reasoner, List<String> queryTypes) {
    logger.debug("Querying: " + expression);
    List<OWLEntity> results = new ArrayList<>();
    if (queryTypes.isEmpty()) {
      logger.info("No query type supplied, using 'descendants'");
      queryTypes.add("descendants");
    }

    for (String type : queryTypes) {
      switch (type) {
        case "equivalents":
          reasoner.getEquivalentClasses(expression).getEntities().forEach(results::add);
          break;
        case "parents":
          reasoner.getSuperClasses(expression, true).getFlattened().forEach(results::add);
          results.remove(df.getOWLThing());
          break;
        case "children":
          reasoner.getSubClasses(expression, true).getFlattened().forEach(results::add);
          results.remove(df.getOWLNothing());
          break;
        case "ancestors":
          reasoner.getSuperClasses(expression, false).getFlattened().forEach(results::add);
          results.remove(df.getOWLThing());
          break;
        case "descendants":
          reasoner.getSubClasses(expression, false).getFlattened().forEach(results::add);
          results.remove(df.getOWLNothing());
          break;
        case "instances":
          reasoner.getInstances(expression, false).getFlattened().forEach(results::add);
          break;
        default:
          throw new IllegalArgumentException(type + " is not a legal query relation for dl-query");
      }
    }
    return results;
  }

  public static OWLClassExpression parseOWLClassExpression(String expression, OWLOntology ontology)
      throws ParserException {
    OWLClassExpression classExpression = null;
    BidirectionalShortFormProviderAdapter shortFormProvider =
        new BidirectionalShortFormProviderAdapter(
            OWLManager.createOWLOntologyManager(),
            Collections.singleton(ontology),
            new SimpleShortFormProvider());

    ManchesterOWLSyntaxClassExpressionParser parser =
        new ManchesterOWLSyntaxClassExpressionParser(
            ontology.getOWLOntologyManager().getOWLDataFactory(),
            new ShortFormEntityChecker(shortFormProvider));

    classExpression = parser.parse(expression);
    return classExpression;
  }
}
