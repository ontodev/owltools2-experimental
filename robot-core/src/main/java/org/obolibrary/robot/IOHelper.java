package org.obolibrary.robot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.jsonldjava.core.Context;
import com.github.jsonldjava.core.JsonLdApi;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.Sets;
import com.opencsv.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shared.JenaException;
import org.apache.jena.tdb.TDBFactory;
import org.geneontology.obographs.core.io.OgJsonGenerator;
import org.geneontology.obographs.core.model.GraphDocument;
import org.geneontology.obographs.owlapi.FromOwl;
import org.geneontology.obographs.owlapi.OboGraphJsonDocumentFormat;
import org.obolibrary.obo2owl.OWLAPIOwl2Obo;
import org.obolibrary.obo2owl.Obo2OWLConstants;
import org.obolibrary.oboformat.model.FrameStructureException;
import org.obolibrary.oboformat.model.OBODoc;
import org.obolibrary.oboformat.writer.OBOFormatWriter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.*;
import org.semanticweb.owlapi.io.*;
import org.semanticweb.owlapi.io.XMLUtils;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.rdf.rdfxml.renderer.IllegalElementNameException;
import org.semanticweb.owlapi.rdf.rdfxml.renderer.XMLWriterPreferences;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Provides convenience methods for working with ontology and term files.
 *
 * @author <a href="mailto:james@overton.ca">James A. Overton</a>
 */
public class IOHelper {

  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(IOHelper.class);

  /** Namespace for error messages. */
  private static final String NS = "errors#";

  /** Error message when the specified file does not exist. Expects file name. */
  static final String fileDoesNotExistError =
      NS + "FILE DOES NOT EXIST ERROR File does not exist: %s";

  /** Error message when an invalid extension is provided (file format). Expects the file format. */
  static final String invalidFormatError = NS + "INVALID FORMAT ERROR unknown format: %s";

  /** Error message when writing output fails due to bad element name. */
  private static final String invalidElementError =
      NS + "INVALID ELEMENT ERROR \"%s\" contains invalid characters";

  /** Error message when the specified file cannot be loaded. Expects the file name. */
  private static final String invalidOntologyFileError =
      NS + "INVALID ONTOLOGY FILE ERROR Could not load a valid ontology from file: %s";

  /** Error message when the specified IRI cannot be loaded. Expects the IRI string. */
  private static final String invalidOntologyIRIError =
      NS + "INVALID ONTOLOGY IRI ERROR Could not load a valid ontology from IRI: %s";

  /** Error message when the specified input stream cannot be loaded. */
  private static final String invalidOntologyStreamError =
      NS + "INVALID ONTOLOGY STREAM ERROR Could not load a valid ontology from InputStream.";

  /** Error message when an invalid prefix is provided. Expects the combined prefix. */
  static final String invalidPrefixError = NS + "INVALID PREFIX ERROR Invalid prefix string: %s";

  /** Error message when a JSON-LD context cannot be created, for any reason. */
  private static final String jsonldContextCreationError =
      NS + "JSON-LD CONTEXT CREATION ERROR Could not create the JSON-LD context.";

  /** Error message when a JSON-LD context cannot be read, for any reason. */
  private static final String jsonldContextParseError =
      NS + "JSON-LD CONTEXT PARSE ERROR Could not parse the JSON-LD context.";

  /** Error message when a graph object cannot be created from the ontology using obographs. */
  private static final String oboGraphError =
      NS
          + "OBO GRAPH ERROR Could not convert ontology to OBO Graph (see https://github.com/geneontology/obographs)";

  /** Error message when OBO cannot be saved. */
  private static final String oboStructureError =
      NS + "OBO STRUCTURE ERROR Ontology does not conform to OBO structure rules:\n%s";

  /** Error message when the ontology cannot be saved. Expects the IRI string. */
  private static final String ontologyStorageError =
      NS + "ONTOLOGY STORAGE ERROR Could not save ontology to IRI: %s";

  /** Error message when a prefix cannot be loaded. Expects the prefix and target. */
  private static final String prefixLoadError =
      NS + "PREFIX LOAD ERROR Could not load prefix '%s' for '%s'";

  /**
   * Error message when Jena cannot load a file to a dataset, probably not RDF/XML (including OWL)
   * or TTL.
   */
  private static final String syntaxError =
      NS
          + "SYNTAX ERROR unable to load '%s' with Jena - "
          + "check that this file is in RDF/XML or TTL syntax and try again.";

  /** Error message when an invalid prefix is provided. Expects the combined prefix. */
  static final String undefinedPrefixError =
      NS + "UNDEFINED PREFIX ERROR \"%s\" has unknown prefix; make sure prefix \"%s\" is defined";

  /** Error message when loader contains unparsed triples. */
  private static final String unparsedTriplesError =
      NS + "UNPARSED TRIPLES ERROR input ontology contains %d triple(s) that could not be parsed:";

  /** Optional base namespaces. */
  private Set<String> baseNamespaces = new HashSet<>();

  /** Path to default context as a resource. */
  private static String defaultContextPath = "/obo_context.jsonld";

  /** Store the current JSON-LD context. */
  private Context context = new Context();

  /** Strict parsing; fail on unparsed triples. */
  private Boolean strict = false;

  /** Store xml entities flag. */
  private Boolean useXMLEntities = false;

  /**
   * Create a new IOHelper with the default prefixes.
   *
   * @throws IOException on problem getting default context
   */
  public IOHelper() throws IOException {
    setContext(getDefaultContext());
  }

  /**
   * Create a new IOHelper with or without the default prefixes.
   *
   * @param defaults false if defaults should not be used
   * @throws IOException on problem getting default context
   */
  public IOHelper(boolean defaults) throws IOException {
    if (defaults) {
      setContext(getDefaultContext());
    } else {
      setContext();
    }
  }

  /**
   * Create a new IOHelper with the specified prefixes.
   *
   * @param map the prefixes to use
   * @throws IOException on issue parsing map
   */
  public IOHelper(Map<String, Object> map) throws IOException {
    setContext(map);
  }

  /**
   * Create a new IOHelper with prefixes from a file path.
   *
   * @param path to a JSON-LD file with a @context
   * @throws IOException on issue parsing JSON-LD file as context
   */
  public IOHelper(String path) throws IOException {
    String jsonString = FileUtils.readFileToString(new File(path), Charset.defaultCharset());
    setContext(jsonString);
  }

  /**
   * Create a new IOHelper with prefixes from a file.
   *
   * @param file a JSON-LD file with a @context
   * @throws IOException on issue reading file or setting context from file
   */
  public IOHelper(File file) throws IOException {
    String jsonString = FileUtils.readFileToString(file, Charset.defaultCharset());
    setContext(jsonString);
  }

  /**
   * Set "strict" value. If true, any loadOntology method will fail on unparsed triples or OWLAPI
   * "strict" parsing issues.
   *
   * @param strict boolean value
   */
  public void setStrict(Boolean strict) {
    this.strict = strict;
  }

  /**
   * Given an ontology, a file, and a list of prefixes, save the ontology to the file and include
   * the prefixes in the header.
   *
   * @deprecated replaced by {@link #saveOntology(OWLOntology, OWLDocumentFormat, IRI, Map,
   *     boolean)}
   * @param ontology OWLOntology to save
   * @param outputFile File to save ontology to
   * @param addPrefixes List of prefixes to add ("foo: http://foo.bar/")
   * @throws IOException On issue parsing list of prefixes or saving file
   */
  @Deprecated
  public void addPrefixesAndSave(OWLOntology ontology, File outputFile, List<String> addPrefixes)
      throws IOException {
    OWLDocumentFormat df = getFormat(FilenameUtils.getExtension(outputFile.getPath()));

    // If prefixes are not supported, just save the ontology without adding prefixes
    if (!df.isPrefixOWLOntologyFormat()) {
      logger.error("Prefixes are not supported in " + df.toString() + " (saving without prefixes)");
      saveOntology(ontology, df, IRI.create(outputFile));
      return;
    }

    // Convert prefixes to map
    Map<String, String> prefixMap = new HashMap<>();
    for (String pref : addPrefixes) {
      String[] split = pref.split(": ");
      if (split.length != 2) {
        throw new IOException(String.format(invalidPrefixError, pref));
      }
      prefixMap.put(split[0], split[1]);
    }

    addPrefixes(df, prefixMap);
    saveOntology(ontology, df, IRI.create(outputFile));
  }

  /**
   * Given a directory containing TDB mappings, remove the files and directory. If successful,
   * return true.
   *
   * @param tdbDir directory to remove
   * @return boolean indicating success
   */
  protected static boolean cleanTDB(String tdbDir) {
    File dir = new File(tdbDir);
    boolean success = true;
    if (dir.exists()) {
      String[] files = dir.list();
      if (files != null) {
        for (String file : files) {
          File f = new File(dir.getPath(), file);
          success = f.delete();
        }
      }
      // Only delete if all the files in dir were deleted
      if (success) {
        success = dir.delete();
      }
    }
    return success;
  }

  /**
   * Try to guess the location of the catalog.xml file. Looks in the directory of the given ontology
   * file for a catalog file.
   *
   * @param ontologyFile the
   * @return the guessed catalog File; may not exist!
   */
  public File guessCatalogFile(File ontologyFile) {
    String path = ontologyFile.getParent();
    String catalogPath = "catalog-v001.xml";
    if (path != null) {
      catalogPath = path + "/catalog-v001.xml";
    }
    return new File(catalogPath);
  }

  /**
   * Load an ontology from a String path, using a catalog file if available.
   *
   * @param ontologyPath the path to the ontology file
   * @return a new ontology object, with a new OWLManager
   * @throws IOException on any problem
   */
  public OWLOntology loadOntology(String ontologyPath) throws IOException {
    File ontologyFile = new File(ontologyPath);
    File catalogFile = guessCatalogFile(ontologyFile);
    if (!catalogFile.isFile()) {
      // If the catalog file does not exist, do not use catalog
      catalogFile = null;
    }
    return loadOntology(ontologyFile, catalogFile);
  }

  /**
   * Load an ontology from a String path, with option to use catalog file.
   *
   * @param ontologyPath the path to the ontology file
   * @param useCatalog when true, a catalog file will be used if one is found
   * @return a new ontology object, with a new OWLManager
   * @throws IOException on any problem
   */
  public OWLOntology loadOntology(String ontologyPath, boolean useCatalog) throws IOException {
    File ontologyFile = new File(ontologyPath);
    File catalogFile = null;
    if (useCatalog) {
      catalogFile = guessCatalogFile(ontologyFile);
    }
    return loadOntology(ontologyFile, catalogFile);
  }

  /**
   * Load an ontology from a String path, with optional catalog file.
   *
   * @param ontologyPath the path to the ontology file
   * @param catalogPath the path to the catalog file
   * @return a new ontology object, with a new OWLManager
   * @throws IOException on any problem
   */
  public OWLOntology loadOntology(String ontologyPath, String catalogPath) throws IOException {
    File ontologyFile = new File(ontologyPath);
    File catalogFile = new File(catalogPath);
    return loadOntology(ontologyFile, catalogFile);
  }

  /**
   * Load an ontology from a File, using a catalog file if available.
   *
   * @param ontologyFile the ontology file to load
   * @return a new ontology object, with a new OWLManager
   * @throws IOException on any problem
   */
  public OWLOntology loadOntology(File ontologyFile) throws IOException {
    File catalogFile = guessCatalogFile(ontologyFile);
    if (!catalogFile.isFile()) {
      // If the catalog file does not exist, do not use catalog
      catalogFile = null;
    }
    return loadOntology(ontologyFile, catalogFile);
  }

  /**
   * Load an ontology from a File, with option to use a catalog file.
   *
   * @param ontologyFile the ontology file to load
   * @param useCatalog when true, a catalog file will be used if one is found
   * @return a new ontology object, with a new OWLManager
   * @throws IOException on any problem
   */
  public OWLOntology loadOntology(File ontologyFile, boolean useCatalog) throws IOException {
    File catalogFile = null;
    if (useCatalog) {
      catalogFile = guessCatalogFile(ontologyFile);
    }
    return loadOntology(ontologyFile, catalogFile);
  }

  /**
   * Load an ontology from a File, with optional catalog File.
   *
   * @param ontologyFile the ontology file to load
   * @param catalogFile the catalog file to use
   * @return a new ontology object, with a new OWLManager
   * @throws IOException on any problem
   */
  public OWLOntology loadOntology(File ontologyFile, File catalogFile) throws IOException {
    logger.debug("Loading ontology {} with catalog file {}", ontologyFile, catalogFile);
    Object jsonObject = null;
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

    try {
      String extension = FilenameUtils.getExtension(ontologyFile.getName());
      extension = extension.trim().toLowerCase();
      if (extension.equals("yml") || extension.equals("yaml")) {
        logger.debug("Converting from YAML to JSON");
        String yamlString = FileUtils.readFileToString(ontologyFile, Charset.defaultCharset());
        jsonObject = new Yaml().load(yamlString);
      } else if (extension.equals("js") || extension.equals("json") || extension.equals("jsonld")) {
        String jsonString = FileUtils.readFileToString(ontologyFile, Charset.defaultCharset());
        jsonObject = JsonUtils.fromString(jsonString);
      }

      // Use Jena to convert a JSON-LD string to RDFXML, then load it
      if (jsonObject != null) {
        logger.debug("Converting from JSON to RDF");
        jsonObject = new JsonLdApi().expand(getContext(), jsonObject);
        String jsonString = JsonUtils.toString(jsonObject);
        Model model = ModelFactory.createDefaultModel();
        model.read(IOUtils.toInputStream(jsonString, Charset.defaultCharset()), null, "JSON-LD");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // model.write(System.out);
        model.write(output);
        byte[] data = output.toByteArray();
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        return loadOntology(input);
      }
      // Handle catalog file
      if (catalogFile != null && catalogFile.isFile()) {
        manager.setIRIMappers(Sets.newHashSet(new CatalogXmlIRIMapper(catalogFile)));
      }
      // Maybe unzip
      if (ontologyFile.getPath().endsWith(".gz")) {
        if (catalogFile == null) {
          return loadCompressedOntology(ontologyFile, null);
        } else {
          return loadCompressedOntology(ontologyFile, catalogFile.getAbsolutePath());
        }
      }

      // Otherwise load from file using default method
      return loadOntology(manager, new FileDocumentSource(ontologyFile));
    } catch (JsonLdError | OWLOntologyCreationException e) {
      throw new IOException(String.format(invalidOntologyFileError, ontologyFile.getName()), e);
    }
  }

  /**
   * Load an ontology from an InputStream, without a catalog file.
   *
   * @param ontologyStream the ontology stream to load
   * @return a new ontology object, with a new OWLManager
   * @throws IOException on any problem
   */
  public OWLOntology loadOntology(InputStream ontologyStream) throws IOException {
    return loadOntology(ontologyStream, null);
  }

  /**
   * Load an ontology from an InputStream with a catalog file.
   *
   * @param ontologyStream the ontology stream to load
   * @param catalogPath the catalog file to use or null
   * @return a new ontology object, with a new OWLManager
   * @throws IOException on any problem
   */
  public OWLOntology loadOntology(InputStream ontologyStream, String catalogPath)
      throws IOException {
    OWLOntology ontology;
    // Maybe load a catalog file
    File catalogFile = null;
    if (catalogPath != null) {
      catalogFile = new File(catalogPath);
      if (!catalogFile.isFile()) {
        throw new IOException(String.format(fileDoesNotExistError, catalogPath));
      }
    }
    try {
      OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
      if (catalogFile != null) {
        manager.setIRIMappers(Sets.newHashSet(new CatalogXmlIRIMapper(catalogFile)));
      }
      ontology = loadOntology(manager, new StreamDocumentSource(ontologyStream));
    } catch (OWLOntologyCreationException e) {
      throw new IOException(invalidOntologyStreamError, e);
    }
    return ontology;
  }

  /**
   * Given an ontology IRI, load the ontology from the IRI.
   *
   * @param ontologyIRI the ontology IRI to load
   * @return a new ontology object, with a new OWLManager
   * @throws IOException on any problem
   */
  public OWLOntology loadOntology(IRI ontologyIRI) throws IOException {
    return loadOntology(ontologyIRI, null);
  }

  /**
   * Given an IRI and a path to a catalog file, load the ontology from the IRI with the catalog.
   *
   * @param ontologyIRI the ontology IRI to load
   * @param catalogPath the catalog file to use or null
   * @return a new ontology object, with a new OWLManager
   * @throws IOException on any problem
   */
  public OWLOntology loadOntology(IRI ontologyIRI, String catalogPath) throws IOException {
    OWLOntology ontology;
    // Maybe load a catalog file
    File catalogFile = null;
    if (catalogPath != null) {
      catalogFile = new File(catalogPath);
      if (!catalogFile.isFile()) {
        throw new IOException(String.format(fileDoesNotExistError, catalogPath));
      }
    }
    try {
      OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
      // If a catalog file was loaded, set IRI mappers
      if (catalogFile != null) {
        manager.setIRIMappers(Sets.newHashSet(new CatalogXmlIRIMapper(catalogFile)));
      }
      // Maybe load a zipped ontology
      if (ontologyIRI.toString().endsWith(".gz")) {
        ontology = loadCompressedOntology(new URL(ontologyIRI.toString()), catalogPath);
      } else {
        // Otherwise load ontology as normal
        IRI documentIRI = getDocumentIRIFromMappers(manager, ontologyIRI);
        ontology = loadOntology(manager, new IRIDocumentSource(documentIRI));
      }
    } catch (OWLOntologyCreationException e) {
      throw new IOException(e);
    }
    return ontology;
  }

  /**
   * Given an ontology manager and a document source, load the ontology from the source using the
   * manager. Log unparsed triples, or throw exception if strict=true.
   *
   * @param manager OWLOntologyManager with IRI mappers to use
   * @param source OWLOntologyDocumentSource to load from
   * @return a new ontology object, with a new OWLManager
   * @throws IOException on problem with unparsed triples if strict=true
   * @throws OWLOntologyCreationException on problem loading ontology document
   */
  public OWLOntology loadOntology(OWLOntologyManager manager, OWLOntologyDocumentSource source)
      throws IOException, OWLOntologyCreationException {
    OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
    if (strict) {
      // Set strict OWLAPI parsing
      config = config.setStrict(true);
    }
    // Load the ontology
    OWLOntology loadedOntology = manager.loadOntologyFromOntologyDocument(source, config);

    // Check for unparsed triples - get the document format and then the loader metadata
    OWLDocumentFormat f = manager.getOntologyFormat(loadedOntology);
    if (f == null) {
      // This should never happen
      throw new IOException("Unable to get an OWLDocumentFormat from loaded ontology");
    }
    RDFParserMetaData metaData = (RDFParserMetaData) f.getOntologyLoaderMetaData();
    Set<RDFTriple> unparsed = metaData.getUnparsedTriples();
    Set<OWLAxiom> parsed = loadedOntology.getAxioms();
    if (unparsed.size() > 0) {
      boolean rdfReification = false;
      StringBuilder sb = new StringBuilder();
      for (RDFTriple t : unparsed) {
        // Check object to see if it's rdfs:Statement used in RDF reification
        String objectIRI;
        try {
          objectIRI = t.getObject().getIRI().toString();
        } catch (UnsupportedOperationException e) {
          // RDF Literals do not have IRIs
          objectIRI = "";
        }
        if (objectIRI.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement")) {
          rdfReification = true;
        }
        // Add triple to error lines
        sb.append("\n - ").append(t.toString().trim());
      }
      Set<IRI> undeclaredPredicates = getUndeclaredPredicates(parsed, unparsed);
      if (rdfReification) {
        // Add hint for fixing RDF reification
        sb.append(
            "\n\nHint: you may be using RDF reification - try replacing 'rdf:Statement' with 'owl:Axiom'");
      }
      if (undeclaredPredicates.size() > 0) {
        sb.append(
            "\n\nHint: you have undeclared predicates - try adding 'rdf:type' declarations to the following:");
        for (IRI p : undeclaredPredicates) {
          sb.append("\n - ").append(p.toString());
        }
      }
      sb.append("\n");

      if (strict) {
        // Fail on unparsed triples
        throw new IOException(String.format(unparsedTriplesError, unparsed.size()) + sb.toString());
      } else {
        // Log unparsed triples as errors
        logger.error(
            String.format(
                    "Input ontology contains %d triple(s) that could not be parsed:",
                    unparsed.size())
                + sb.toString());
      }
    }
    // No issues, return ontology
    return loadedOntology;
  }

  private IRI getDocumentIRIFromMappers(OWLOntologyManager m, IRI iri) {
    if (iri == null) {
      return null;
    }
    final Iterator<OWLOntologyIRIMapper> var4 = m.getIRIMappers().iterator();

    IRI documentIRI;
    do {
      if (!var4.hasNext()) {
        return iri;
      }

      OWLOntologyIRIMapper mapper = var4.next();
      documentIRI = mapper.getDocumentIRI(iri);
    } while (documentIRI == null);

    return documentIRI;
  }

  /**
   * Given a path to an RDF/XML or TTL file and a RDF language, load the file as the default model
   * of a TDB dataset backed by a directory to improve processing time. Return the new dataset.
   *
   * <p>WARNING - this creates a directory at given tdbDir location!
   *
   * @param inputPath input path of RDF/XML or TTL file
   * @param tdbDir location to put TDB mappings
   * @return Dataset instantiated with triples
   * @throws JenaException if TDB directory can't be written to
   */
  public static Dataset loadToTDBDataset(String inputPath, String tdbDir) throws JenaException {
    // First try opening existing dataset
    Dataset dataset = openTDBDataset(tdbDir);
    if (dataset != null) {
      return dataset;
    }
    dataset = TDBFactory.createDataset(tdbDir);
    logger.debug(String.format("Parsing input '%s' to dataset", inputPath));
    // Track parsing time
    long start = System.nanoTime();
    Model m;
    dataset.begin(ReadWrite.WRITE);
    try {
      m = dataset.getDefaultModel();
      RDFDataMgr.read(m, inputPath);
      dataset.commit();
    } catch (JenaException e) {
      dataset.abort();
      dataset.end();
      dataset.close();
      throw new JenaException(String.format(syntaxError, inputPath));
    } finally {
      dataset.end();
    }
    long time = (System.nanoTime() - start) / 1000000000;
    logger.debug(String.format("Parsing complete - took %s seconds", String.valueOf(time)));
    return dataset;
  }

  /**
   * Given a path to a TDB directory, load the TDB as a Dataset.
   *
   * @param tdbDir path to existing TDB directory
   * @return Dataset or null
   */
  public static Dataset openTDBDataset(String tdbDir) {
    Dataset dataset;
    if (new File(tdbDir).isDirectory()) {
      dataset = TDBFactory.createDataset(tdbDir);
      if (!dataset.isEmpty()) {
        return dataset;
      }
    }
    return null;
  }

  /**
   * Given the name of a file format, return an instance of it.
   *
   * <p>Suported formats:
   *
   * <ul>
   *   <li>OBO as 'obo'
   *   <li>RDFXML as 'owl'
   *   <li>Turtle as 'ttl'
   *   <li>OWLXML as 'owx'
   *   <li>Manchester as 'omn'
   *   <li>OWL Functional as 'ofn'
   * </ul>
   *
   * @param formatName the name of the format
   * @return an instance of the format
   * @throws IllegalArgumentException if format name is not recognized
   */
  public static OWLDocumentFormat getFormat(String formatName) throws IllegalArgumentException {
    formatName = formatName.trim().toLowerCase();
    switch (formatName) {
      case "obo":
        return new OBODocumentFormat();
      case "owl":
        return new RDFXMLDocumentFormat();
      case "ttl":
        return new TurtleDocumentFormat();
      case "owx":
        return new OWLXMLDocumentFormat();
      case "omn":
        return new ManchesterSyntaxDocumentFormat();
      case "ofn":
        return new FunctionalSyntaxDocumentFormat();
      case "json":
        return new OboGraphJsonDocumentFormat();
      default:
        throw new IllegalArgumentException(String.format(invalidFormatError, formatName));
    }
  }

  /**
   * Save an ontology to a String path.
   *
   * @param ontology the ontology to save
   * @param ontologyPath the path to save the ontology to
   * @return the saved ontology
   * @throws IOException on any problem
   */
  public OWLOntology saveOntology(OWLOntology ontology, String ontologyPath) throws IOException {
    return saveOntology(ontology, new File(ontologyPath));
  }

  /**
   * Save an ontology to a File.
   *
   * @param ontology the ontology to save
   * @param ontologyFile the file to save the ontology to
   * @return the saved ontology
   * @throws IOException on any problem
   */
  public OWLOntology saveOntology(OWLOntology ontology, File ontologyFile) throws IOException {
    return saveOntology(ontology, IRI.create(ontologyFile));
  }

  /**
   * Save an ontology to an IRI, using the file extension to determine the format.
   *
   * @param ontology the ontology to save
   * @param ontologyIRI the IRI to save the ontology to
   * @return the saved ontology
   * @throws IOException on any problem
   */
  public OWLOntology saveOntology(final OWLOntology ontology, IRI ontologyIRI) throws IOException {
    String path = ontologyIRI.toString();
    if (path.endsWith(".gz")) {
      path = path.substring(0, path.lastIndexOf("."));
    }
    String formatName = FilenameUtils.getExtension(path);
    OWLDocumentFormat format = getFormat(formatName);
    return saveOntology(ontology, format, ontologyIRI, true);
  }

  /**
   * Save an ontology in the given format to a file.
   *
   * @param ontology the ontology to save
   * @param format the ontology format to use
   * @param ontologyFile the file to save the ontology to
   * @return the saved ontology
   * @throws IOException on any problem
   */
  public OWLOntology saveOntology(
      final OWLOntology ontology, OWLDocumentFormat format, File ontologyFile) throws IOException {
    return saveOntology(ontology, format, ontologyFile, true);
  }

  /**
   * Save an ontology in the given format to a file, with the option to ignore OBO document checks.
   *
   * @param ontology the ontology to save
   * @param format the ontology format to use
   * @param ontologyFile the file to save the ontology to
   * @param checkOBO if false, ignore OBO document checks
   * @return the saved ontology
   * @throws IOException on any problem
   */
  public OWLOntology saveOntology(
      final OWLOntology ontology, OWLDocumentFormat format, File ontologyFile, boolean checkOBO)
      throws IOException {
    return saveOntology(ontology, format, IRI.create(ontologyFile), checkOBO);
  }

  /**
   * Save an ontology in the given format to an IRI.
   *
   * @param ontology the ontology to save
   * @param format the ontology format to use
   * @param ontologyIRI the IRI to save the ontology to
   * @return the saved ontology
   * @throws IOException on any problem
   */
  public OWLOntology saveOntology(
      final OWLOntology ontology, OWLDocumentFormat format, IRI ontologyIRI) throws IOException {
    return saveOntology(ontology, format, ontologyIRI, true);
  }

  /**
   * Save an ontology in the given format to a path, with the option to ignore OBO document checks.
   *
   * @param ontology the ontology to save
   * @param format the ontology format to use
   * @param ontologyPath the path to save the ontology to
   * @param checkOBO if false, ignore OBO document checks
   * @return the saved ontology
   * @throws IOException on any problem
   */
  public OWLOntology saveOntology(
      final OWLOntology ontology, OWLDocumentFormat format, String ontologyPath, boolean checkOBO)
      throws IOException {
    return saveOntology(ontology, format, IRI.create(new File(ontologyPath)), checkOBO);
  }

  /**
   * Save an ontology in the given format to an IRI, with the option to ignore OBO document checks.
   *
   * @param ontology the ontology to save
   * @param format the ontology format to use
   * @param ontologyIRI the IRI to save the ontology to
   * @param checkOBO if false, ignore OBO document checks
   * @return the saved ontology
   * @throws IOException on any problem
   */
  public OWLOntology saveOntology(
      final OWLOntology ontology, OWLDocumentFormat format, IRI ontologyIRI, boolean checkOBO)
      throws IOException {
    return saveOntology(ontology, format, ontologyIRI, null, checkOBO);
  }

  /**
   * Save an ontology in the given format to an IRI, with option to add prefixes and option to
   * ignore OBO document checks.
   *
   * @param ontology the ontology to save
   * @param format the ontology format to use
   * @param ontologyIRI the IRI to save the ontology to
   * @param addPrefixes map of prefixes to add to header
   * @param checkOBO if false, ignore OBO document checks
   * @return the saved ontology
   * @throws IOException on any problem
   */
  public OWLOntology saveOntology(
      final OWLOntology ontology,
      OWLDocumentFormat format,
      IRI ontologyIRI,
      Map<String, String> addPrefixes,
      boolean checkOBO)
      throws IOException {
    return saveOntology(
        ontology, format, ontologyIRI, addPrefixes, checkOBO, EnumSet.noneOf(OBOWriteOption.class));
  }

  /**
   * Save an ontology in the given format to an IRI, with option to add prefixes and options to
   * ignore OBO document checks and produce a "clean" OBO output.
   *
   * @param ontology the ontology to save
   * @param format the ontology format to use
   * @param ontologyIRI the IRI to save the ontology to
   * @param addPrefixes map of prefixes to add to header
   * @param checkOBO if false, ignore OBO document checks
   * @param cleanOBO optional parameters for OBO output
   * @return the saved ontology
   * @throws IOException on any problem
   */
  public OWLOntology saveOntology(
      final OWLOntology ontology,
      OWLDocumentFormat format,
      IRI ontologyIRI,
      Map<String, String> addPrefixes,
      boolean checkOBO,
      EnumSet<OBOWriteOption> cleanOBO)
      throws IOException {
    // Determine the format if not provided
    logger.debug("Saving ontology as {} with to IRI {}", format, ontologyIRI);
    XMLWriterPreferences.getInstance().setUseNamespaceEntities(getXMLEntityFlag());
    // If saving in compressed format, get byte data then save to gzip
    if (ontologyIRI.toString().endsWith(".gz")) {
      byte[] data = getOntologyFileData(ontology, format, checkOBO, cleanOBO);
      saveCompressedOntology(data, ontologyIRI);
      return ontology;
    }
    OWLDocumentFormat previousFormat = ontology.getOWLOntologyManager().getOntologyFormat(ontology);
    if (format.isPrefixOWLOntologyFormat()
        && previousFormat != null
        && previousFormat.isPrefixOWLOntologyFormat()) {
      String defaultNamespace = format.asPrefixOWLOntologyFormat().getDefaultPrefix();
      format
          .asPrefixOWLOntologyFormat()
          .copyPrefixesFrom(previousFormat.asPrefixOWLOntologyFormat());
      format.asPrefixOWLOntologyFormat().setDefaultPrefix(defaultNamespace);
    }
    // If not compressed, just save the file as-is
    if (addPrefixes != null && !addPrefixes.isEmpty()) {
      addPrefixes(format, addPrefixes);
    }
    saveOntologyFile(ontology, format, ontologyIRI, checkOBO, cleanOBO);
    return ontology;
  }

  /**
   * Extract a set of term identifiers from an input string by removing comments, trimming lines,
   * and removing empty lines. A comment is a space or newline followed by a '#', to the end of the
   * line. This excludes '#' characters in IRIs.
   *
   * @param input the String containing the term identifiers
   * @return a set of term identifier strings
   */
  public Set<String> extractTerms(String input) {
    Set<String> results = new HashSet<>();
    String[] lines = input.replaceAll("\\r", "").split("\\n");
    for (String line : lines) {
      if (line.trim().startsWith("#")) {
        continue;
      }
      String result = line.replaceFirst("($|\\s)#.*$", "").trim();
      if (!result.isEmpty()) {
        results.add(result);
      }
    }
    return results;
  }

  /**
   * Convert a row index and column index for a cell to A1 notation.
   *
   * @param rowNum row index
   * @param colNum column index
   * @return A1 notation for cell location
   */
  public static String cellToA1(int rowNum, int colNum) {
    // To store result (Excel column name)
    StringBuilder colLabel = new StringBuilder();

    while (colNum > 0) {
      // Find remainder
      int rem = colNum % 26;

      // If remainder is 0, then a
      // 'Z' must be there in output
      if (rem == 0) {
        colLabel.append("Z");
        colNum = (colNum / 26) - 1;
      } else {
        colLabel.append((char) ((rem - 1) + 'A'));
        colNum = colNum / 26;
      }
    }

    // Reverse the string and print result
    return colLabel.reverse().toString() + rowNum;
  }

  /**
   * Given a term string, use the current prefixes to create an IRI.
   *
   * @param term the term to convert to an IRI
   * @return the new IRI or null
   */
  @SuppressWarnings("unchecked")
  public IRI createIRI(String term) {
    if (term == null) {
      return null;
    }
    IRI iri;

    try {
      // This is stupid, because better methods aren't public.
      // We create a new JSON map and add one entry
      // with the term as the key and some string as the value.
      // Then we run the JsonLdApi to expand the JSON map
      // in the current context, and just grab the first key.
      // If everything worked, that key will be our expanded iri.
      Map<String, Object> jsonMap = new HashMap<>();
      jsonMap.put(term, "ignore this string");
      Object expanded = new JsonLdApi().expand(context, jsonMap);
      String result = ((Map<String, Object>) expanded).keySet().iterator().next();
      if (result != null) {
        iri = IRI.create(result);
      } else {
        iri = IRI.create(term);
      }
    } catch (Exception e) {
      logger.warn("Could not create IRI for {}", term);
      if (e.getMessage() != null) {
        logger.warn(e.getMessage());
      }
      return null;
    }

    if (iri.toString().startsWith("urn:")) {
      // A URN is not a valid URL, so we check it with regex
      Pattern urnPattern =
          Pattern.compile(
              "^urn:[a-z0-9][a-z0-9-]{0,31}:([a-z0-9()+,\\-.:=@;$_!*']|%[0-9a-f]{2})+$",
              Pattern.CASE_INSENSITIVE);
      if (!urnPattern.matcher(iri.toString()).matches()) {
        // Not a valid URN (RFC2141)
        return null;
      }
    } else {
      try {
        // Check for malformed URL, e.g., a CURIE was returned or the value passed has spaces
        new URL(iri.toString());
      } catch (MalformedURLException e) {
        // Invalid IRI
        return null;
      }
    }

    return iri;
  }

  /**
   * Given a term string, use the current prefixes to create an IRI.
   *
   * @deprecated replaced by {@link #createIRI(String)}
   * @param term the term to convert to an IRI
   * @param qName if true, validate that the IRI expands to a QName
   * @return the new IRI or null
   */
  @Deprecated
  public IRI createIRI(String term, boolean qName) {
    IRI iri = createIRI(term);
    // Check that this is a valid QName
    if (qName && !iri.getRemainder().isPresent()) {
      return null;
    }
    return iri;
  }

  /**
   * Given a set of term identifier strings, return a set of IRIs.
   *
   * @param terms the set of term identifier strings
   * @return the set of IRIs
   * @throws IllegalArgumentException if term identifier is not a valid IRI
   */
  public Set<IRI> createIRIs(Set<String> terms) throws IllegalArgumentException {
    Set<IRI> iris = new HashSet<>();
    for (String term : terms) {
      IRI iri = createIRI(term);
      if (iri != null) {
        iris.add(iri);
      } else {
        // Warn and continue
        logger.warn("{} is not a valid IRI.", term);
      }
    }
    return iris;
  }

  /**
   * Create an OWLLiteral.
   *
   * @param value the lexical value
   * @return a literal
   */
  public static OWLLiteral createLiteral(String value) {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = manager.getOWLDataFactory();
    return df.getOWLLiteral(value);
  }

  /**
   * Create an OWLLiteral with a language tag.
   *
   * @param value the lexical value
   * @param lang the language tag
   * @return a literal
   */
  public static OWLLiteral createTaggedLiteral(String value, String lang) {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = manager.getOWLDataFactory();
    return df.getOWLLiteral(value, lang);
  }

  /**
   * Create a typed OWLLiteral.
   *
   * @param value the lexical value
   * @param type the type IRI string
   * @return a literal
   */
  public OWLLiteral createTypedLiteral(String value, String type) {
    IRI iri = createIRI(type);
    return createTypedLiteral(value, iri);
  }

  /**
   * Create a typed OWLLiteral.
   *
   * @param value the lexical value
   * @param type the type IRI
   * @return a literal
   */
  public OWLLiteral createTypedLiteral(String value, IRI type) {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = manager.getOWLDataFactory();
    OWLDatatype datatype = df.getOWLDatatype(type);
    return df.getOWLLiteral(value, datatype);
  }

  /**
   * Parse a set of IRIs from a space-separated string, ignoring '#' comments.
   *
   * @param input the string containing the IRI strings
   * @return the set of IRIs
   * @throws IllegalArgumentException if term identifier is not a valid IRI
   */
  public Set<IRI> parseTerms(String input) throws IllegalArgumentException {
    return createIRIs(extractTerms(input));
  }

  /**
   * Load a map of prefixes from the "@context" of a JSON-LD string.
   *
   * @param jsonString the JSON-LD string
   * @return a map from prefix name strings to prefix IRI strings
   * @throws IOException on any problem
   */
  @SuppressWarnings("unchecked")
  public static Context parseContext(String jsonString) throws IOException {
    try {
      Object jsonObject = JsonUtils.fromString(jsonString);
      if (!(jsonObject instanceof Map)) {
        throw new IOException(jsonldContextParseError);
      }
      Map<String, Object> jsonMap = (Map<String, Object>) jsonObject;
      if (!jsonMap.containsKey("@context")) {
        throw new IOException(jsonldContextParseError);
      }
      Object jsonContext = jsonMap.get("@context");
      return new Context().parse(jsonContext);
    } catch (Exception e) {
      throw new IOException(jsonldContextParseError, e);
    }
  }

  /**
   * Add a base namespace to the IOHelper.
   *
   * @param baseNamespace namespace to add to bases.
   */
  public void addBaseNamespace(String baseNamespace) {
    baseNamespaces.add(baseNamespace);
  }

  /**
   * Add a set of base namespaces to the IOHelper from file. Each base namespace should be on its
   * own line.
   *
   * @param baseNamespacePath path to base namespace file
   * @throws IOException if file does not exist
   */
  public void addBaseNamespaces(String baseNamespacePath) throws IOException {
    File prefixFile = new File(baseNamespacePath);
    if (!prefixFile.exists()) {
      throw new IOException(String.format(fileDoesNotExistError, baseNamespacePath));
    }

    List<String> lines = FileUtils.readLines(new File(baseNamespacePath), Charset.defaultCharset());
    for (String l : lines) {
      baseNamespaces.add(l.trim());
    }
  }

  /**
   * Get the base namespaces.
   *
   * @return set of base namespaces
   */
  public Set<String> getBaseNamespaces() {
    return baseNamespaces;
  }

  /**
   * Get a copy of the default context.
   *
   * @return a copy of the current context
   * @throws IOException if default context file cannot be read
   */
  public Context getDefaultContext() throws IOException {
    InputStream stream = IOHelper.class.getResourceAsStream(defaultContextPath);
    String jsonString = IOUtils.toString(stream, Charset.defaultCharset());
    return parseContext(jsonString);
  }

  /**
   * Get a copy of the current context.
   *
   * @return a copy of the current context
   */
  public Context getContext() {
    return this.context.clone();
  }

  /** Set an empty context. */
  public void setContext() {
    this.context = new Context();
  }

  /**
   * Set the current JSON-LD context to the given context.
   *
   * @param context the new JSON-LD context
   */
  public void setContext(Context context) {
    if (context == null) {
      setContext();
    } else {
      this.context = context;
    }
  }

  /**
   * Set the current JSON-LD context to the given context.
   *
   * @param jsonString the new JSON-LD context as a JSON string
   * @throws IOException on issue parsing JSON
   */
  public void setContext(String jsonString) throws IOException {
    this.context = parseContext(jsonString);
  }

  /**
   * Set the current JSON-LD context to the given map.
   *
   * @param map a map of strings for the new JSON-LD context
   * @throws IOException on issue parsing JSON
   */
  public void setContext(Map<String, Object> map) throws IOException {
    try {
      this.context = new Context().parse(map);
    } catch (JsonLdError e) {
      throw new IOException(jsonldContextParseError, e);
    }
  }

  /**
   * Set whether or not XML entities will be swapped into URIs in saveOntology XML output formats.
   *
   * @param entityFlag value to set
   */
  public void setXMLEntityFlag(Boolean entityFlag) {
    try {
      this.useXMLEntities = entityFlag;
    } catch (Exception e) {
      logger.warn("Could not set useXMLEntities {}", entityFlag);
      logger.warn(e.getMessage());
    }
  }

  /**
   * Get the useXMLEntities flag.
   *
   * @return boolean useXMLEntities flag
   */
  public Boolean getXMLEntityFlag() {
    return this.useXMLEntities;
  }

  /**
   * Make an OWLAPI DefaultPrefixManager from a map of prefixes.
   *
   * @param prefixes a map from prefix name strings to prefix IRI strings
   * @return a new DefaultPrefixManager
   */
  public static DefaultPrefixManager makePrefixManager(Map<String, String> prefixes) {
    DefaultPrefixManager pm = new DefaultPrefixManager();
    for (Map.Entry<String, String> entry : prefixes.entrySet()) {
      pm.setPrefix(entry.getKey() + ":", entry.getValue());
    }
    return pm;
  }

  /**
   * Get a prefix manager with the current prefixes.
   *
   * @return a new DefaultPrefixManager
   */
  public DefaultPrefixManager getPrefixManager() {
    return makePrefixManager(context.getPrefixes(false));
  }

  /**
   * Add a prefix mapping as a single string "foo: http://example.com#".
   *
   * @param combined both prefix and target
   * @throws IllegalArgumentException on malformed input
   * @throws IOException if prefix cannot be parsed
   */
  public void addPrefix(String combined) throws IllegalArgumentException, IOException {
    String[] results = combined.split(":", 2);
    if (results.length < 2) {
      throw new IllegalArgumentException(String.format(invalidPrefixError, combined));
    }
    addPrefix(results[0], results[1]);
  }

  /**
   * Add a prefix mapping to the current JSON-LD context, as a prefix string and target string.
   * Rebuilds the context.
   *
   * @param prefix the short prefix to add; should not include ":"
   * @param target the IRI string that is the target of the prefix
   * @throws IOException if prefix cannot be parsed
   */
  public void addPrefix(String prefix, String target) throws IOException {
    try {
      context.put(prefix.trim(), target.trim());
      context.remove("@base");
      setContext((Map<String, Object>) context);
    } catch (Exception e) {
      throw new IOException(String.format(prefixLoadError, prefix, target), e);
    }
  }

  /**
   * Given a path to a JSON-LD prefix file, add the prefix mappings in the file to the current
   * JSON-LD context.
   *
   * @param prefixPath path to JSON-LD prefix file to add
   * @throws IOException if the file does not exist or cannot be read
   */
  public void addPrefixes(String prefixPath) throws IOException {
    File prefixFile = new File(prefixPath);
    if (!prefixFile.exists()) {
      throw new IOException(String.format(fileDoesNotExistError, prefixPath));
    }
    Context context1 =
        parseContext(FileUtils.readFileToString(prefixFile, Charset.defaultCharset()));
    addPrefixes(context1);
  }

  /**
   * Given a Context, add the prefix mappings to the current JSON-LD context.
   *
   * @param context1 Context to add
   * @throws IOException if the Context cannot be set
   */
  public void addPrefixes(Context context1) throws IOException {
    context.putAll(context1.getPrefixes(false));
    context.remove("@base");
    setContext((Map<String, Object>) context);
  }

  /**
   * Get a copy of the current prefix map.
   *
   * @return a copy of the current prefix map
   */
  public Map<String, String> getPrefixes() {
    return this.context.getPrefixes(false);
  }

  /**
   * Set the current prefix map.
   *
   * @param map the new map of prefixes to use
   * @throws IOException on issue parsing map to context
   */
  public void setPrefixes(Map<String, Object> map) throws IOException {
    setContext(map);
  }

  /**
   * Return the current prefixes as a JSON-LD string.
   *
   * @return the current prefixes as a JSON-LD string
   * @throws IOException on any error
   */
  public String getContextString() throws IOException {
    try {
      Object compact =
          JsonLdProcessor.compact(
              JsonUtils.fromString("{}"), context.getPrefixes(false), new JsonLdOptions());
      return JsonUtils.toPrettyString(compact);
    } catch (Exception e) {
      throw new IOException(jsonldContextCreationError, e);
    }
  }

  /**
   * Write the current context as a JSON-LD file.
   *
   * @param path the path to write the context
   * @throws IOException on any error
   */
  public void saveContext(String path) throws IOException {
    saveContext(new File(path));
  }

  /**
   * Write the current context as a JSON-LD file.
   *
   * @param file the file to write the context
   * @throws IOException on any error
   */
  public void saveContext(File file) throws IOException {
    FileWriter writer = new FileWriter(file);
    writer.write(getContextString());
    writer.close();
  }

  /**
   * Determine if a string is a CURIE. Note that a valid CURIE is not always a valid QName. Adapted
   * from:
   *
   * @see org.semanticweb.owlapi.io.XMLUtils#isQName(CharSequence)
   * @param s Character sequence to check
   * @return true if valid CURIE
   */
  public static boolean isValidCURIE(CharSequence s) {
    if (s == null || 0 >= s.length()) {
      // string is null or empty
      return false;
    }
    boolean inLocal = false;
    for (int i = 0; i < s.length(); ) {
      int codePoint = Character.codePointAt(s, i);
      if (codePoint == ':') {
        if (inLocal) {
          // Second colon - illegal
          return false;
        }
        inLocal = true;
      } else {
        if (!inLocal) {
          // Check for valid NS characters
          if (!XMLUtils.isXMLNameStartCharacter(codePoint)) {
            return false;
          }
        } else {
          // Check for valid local characters
          if (!XMLUtils.isXMLNameChar(codePoint)) {
            return false;
          }
        }
      }
      i += Character.charCount(codePoint);
    }
    return true;
  }

  /**
   * Read comma-separated values from a path to a list of lists of strings.
   *
   * @param path file path to the CSV file
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readCSV(String path) throws IOException {
    return TemplateHelper.readCSV(path);
  }

  /**
   * Read comma-separated values from a stream to a list of lists of strings.
   *
   * @param stream the stream to read from
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readCSV(InputStream stream) throws IOException {
    return TemplateHelper.readCSV(stream);
  }

  /**
   * Read comma-separated values from a reader to a list of lists of strings.
   *
   * @param reader a reader to read data from
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readCSV(Reader reader) throws IOException {
    return TemplateHelper.readCSV(reader);
  }

  /**
   * Read tab-separated values from a path to a list of lists of strings.
   *
   * @param path file path to the CSV file
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readTSV(String path) throws IOException {
    return TemplateHelper.readTSV(path);
  }

  /**
   * Read tab-separated values from a stream to a list of lists of strings.
   *
   * @param stream the stream to read from
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readTSV(InputStream stream) throws IOException {
    return TemplateHelper.readTSV(stream);
  }

  /**
   * Read tab-separated values from a reader to a list of lists of strings.
   *
   * @param reader a reader to read data from
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readTSV(Reader reader) throws IOException {
    return TemplateHelper.readTSV(reader);
  }

  /**
   * Read a table from a path to a list of lists of strings.
   *
   * @param path file path to the CSV file
   * @return a list of lists of strings
   * @throws IOException on file or reading problems
   */
  public static List<List<String>> readTable(String path) throws IOException {
    return TemplateHelper.readTable(path);
  }

  /**
   * Write a table from a list of arrays.
   *
   * @param table List of arrays to write
   * @param path path to write to
   * @throws IOException on file or writing problems
   */
  public static void writeTable(List<String[]> table, String path) throws IOException {
    char separator = '\t';
    if (path.endsWith(".csv")) {
      separator = ',';
    }
    writeTable(table, new File(path), separator);
  }

  /**
   * Write a table from a list of arrays.
   *
   * @param file File to write to
   * @param table List of arrays to write
   * @param separator table separator
   * @throws IOException on problem making Writer object or auto-closing CSVWriter
   */
  public static void writeTable(List<String[]> table, File file, char separator)
      throws IOException {
    try (Writer w = new FileWriter(file)) {
      writeTable(table, w, separator);
    }
  }

  /**
   * Write a table from a list of arrays.
   *
   * @param writer Writer object to write to
   * @param table List of arrays to write
   * @param separator table separator
   * @throws IOException on problem auto-closing writer
   */
  public static void writeTable(List<String[]> table, Writer writer, char separator)
      throws IOException {
    try (CSVWriter csv =
        new CSVWriter(
            writer,
            separator,
            CSVWriter.DEFAULT_QUOTE_CHARACTER,
            CSVWriter.DEFAULT_ESCAPE_CHARACTER,
            CSVWriter.DEFAULT_LINE_END)) {
      csv.writeAll(table, false);
    }
  }

  /**
   * Given a document format and a map of prefixes to add, add the prefixes to the document.
   *
   * @param df OWLDocumentFormat
   * @param addPrefixes map of prefix to namespace to add
   */
  private void addPrefixes(OWLDocumentFormat df, Map<String, String> addPrefixes) {
    if (!df.isPrefixOWLOntologyFormat()) {
      // Warn on non-prefix document format (i.e. OBO)
      logger.warn(
          String.format(
              "Unable to add prefixes to %s document - saving without prefixes", df.toString()));
      return;
    }
    PrefixDocumentFormat pf = df.asPrefixOWLOntologyFormat();
    for (Map.Entry<String, String> pref : addPrefixes.entrySet()) {
      pf.setPrefix(pref.getKey(), pref.getValue());
    }
  }

  /**
   * Given a URL, check if the URL returns a redirect and return that new URL. Continue following
   * redirects until there are no more redirects.
   *
   * @param url URL to follow redirects
   * @return URL after all redirects
   * @throws IOException on issue making URL connection
   */
  private URL followRedirects(URL url) throws IOException {
    // Check if the URL redirects
    if (url.toString().startsWith("ftp")) {
      // Trying to open HttpURLConnection on FTP will throw exception
      return url;
    }
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    int status = conn.getResponseCode();
    boolean redirect = false;
    if (status != HttpURLConnection.HTTP_OK) {
      if (status == HttpURLConnection.HTTP_MOVED_TEMP
          || status == HttpURLConnection.HTTP_MOVED_PERM
          || status == HttpURLConnection.HTTP_SEE_OTHER) {
        redirect = true;
      }
    }

    if (redirect) {
      // Get the new URL and then check that for redirect
      String newURL = conn.getHeaderField("Location");
      logger.info(String.format("<%s> redirecting to <%s>...", url.toString(), newURL));
      if (newURL.startsWith("ftp")) {
        // No more redirects
        return new URL(newURL);
      } else {
        // Check again if there is another redirect
        return followRedirects(new URL(newURL));
      }
    } else {
      // Otherwise just return the URL
      return url;
    }
  }

  /**
   * Given an ontology, a document format, a boolean indicating to check OBO formatting, and a set
   * of options for OBO output, return the ontology file in the OWLDocumentFormat as a byte array.
   *
   * @param ontology OWLOntology to save
   * @param format OWLDocumentFormat to save in
   * @param checkOBO boolean indicating to check OBO formatting
   * @param cleanOBO optional parameters for OBO output
   * @return byte array of formatted ontology data
   * @throws IOException on any problem
   */
  private byte[] getOntologyFileData(
      final OWLOntology ontology,
      OWLDocumentFormat format,
      boolean checkOBO,
      EnumSet<OBOWriteOption> cleanOBO)
      throws IOException {
    byte[] data;
    // first handle any non-official output formats.
    // currently this is just OboGraphs JSON format
    if (format instanceof OboGraphJsonDocumentFormat) {
      FromOwl fromOwl = new FromOwl();
      GraphDocument gd = fromOwl.generateGraphDocument(ontology);
      String doc = OgJsonGenerator.render(gd);
      data = doc.getBytes();
    } else if (format instanceof OBODocumentFormat && (!checkOBO || !cleanOBO.isEmpty())) {
      OBODoc oboOntology = makeCleanOBODocument(ontology, cleanOBO);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(baos))) {
        OBOFormatWriter oboWriter = new OBOFormatWriter();
        oboWriter.setCheckStructure(checkOBO);
        oboWriter.write(oboOntology, bw);
      }
      data = baos.toByteArray();
    } else {
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        ontology.getOWLOntologyManager().saveOntology(ontology, format, baos);
        data = baos.toByteArray();
      } catch (IOException | OWLOntologyStorageException e) {
        // TODO
        throw new IOException(e);
      }
    }
    return data;
  }

  /**
   * Given a set of parsed OWLAxioms and a set of unparsed RDF triples, get any predicates used in
   * the unparsed set that are not builtins (OWL, RDF, RDFS) and do not have declarations in the
   * parsed axioms.
   *
   * @param parsedAxioms Set of parsed OWLAxioms from loaded ontology
   * @param unparsedTriples Set of unparsed RDF triples from loaded ontology
   * @return set of IRIs of any undeclared predicates
   */
  private static Set<IRI> getUndeclaredPredicates(
      Set<OWLAxiom> parsedAxioms, Set<RDFTriple> unparsedTriples) {
    Set<IRI> checkPredicates = new HashSet<>();
    for (RDFTriple t : unparsedTriples) {
      IRI pIRI = t.getPredicate().getIRI();
      if (pIRI.toString().startsWith("http://www.w3.org/2002/07/owl#")
          || pIRI.toString().startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#")
          || pIRI.toString().startsWith("http://www.w3.org/2000/01/rdf-schema#")) {
        // Skip OWL, RDF, RDFS ...
        continue;
      }
      checkPredicates.add(t.getPredicate().getIRI());
    }
    // Look for types
    for (OWLAxiom a : parsedAxioms) {
      if (!a.getAxiomType().equals(AxiomType.DECLARATION)) {
        continue;
      }
      OWLDeclarationAxiom dec = (OWLDeclarationAxiom) a;
      IRI eIRI = dec.getEntity().getIRI();
      checkPredicates.remove(eIRI);
    }
    return checkPredicates;
  }

  /**
   * Given a gzipped ontology file and a catalog path, load the ontology from a zip input stream.
   *
   * @param gzipFile compressed File to load ontology from
   * @param catalogPath the path to the catalog file or null
   * @return a new ontology object with a new OWLManager
   * @throws IOException on any problem
   */
  private OWLOntology loadCompressedOntology(File gzipFile, String catalogPath) throws IOException {
    FileInputStream fis = new FileInputStream(gzipFile);
    GZIPInputStream gis = new GZIPInputStream(fis);
    return loadOntology(gis, catalogPath);
  }

  /**
   * Given the URL to a gzipped ontology and a catalog path, load the ontology from a zip input
   * stream.
   *
   * @param url URL to load from
   * @param catalogPath the path to the catalog file or null
   * @return a new ontology object with a new OWLManager
   * @throws IOException on any problem
   */
  private OWLOntology loadCompressedOntology(URL url, String catalogPath) throws IOException {
    // Check for redirects
    url = followRedirects(url);

    // Open an input stream
    InputStream is;
    try {
      is = new BufferedInputStream(url.openStream(), 1024);
    } catch (FileNotFoundException e) {
      throw new IOException(String.format(invalidOntologyIRIError, url));
    }
    GZIPInputStream gis = new GZIPInputStream(is);
    return loadOntology(gis, catalogPath);
  }

  /**
   * Given an ontology, a format, an IRI to save to, a boolean indicating to check OBO formatting
   * and a set of options for OBO output, save the ontology in the given format to a file at the
   * IRI.
   *
   * @param ontology OWLOntology to save
   * @param format OWLDocumentFormat to save in
   * @param ontologyIRI IRI to save to
   * @param checkOBO boolean indicating to check OBO formatting
   * @param cleanOBO optional parameters for OBO output
   * @throws IOException on any problem
   */
  private void saveOntologyFile(
      final OWLOntology ontology,
      OWLDocumentFormat format,
      IRI ontologyIRI,
      boolean checkOBO,
      EnumSet<OBOWriteOption> cleanOBO)
      throws IOException {
    // first handle any non-official output formats.
    // currently this is just OboGraphs JSON format
    format.setParameter(OBODocumentFormat.VALIDATION, checkOBO);

    if (format instanceof OboGraphJsonDocumentFormat) {
      FromOwl fromOwl = new FromOwl();
      GraphDocument gd;
      try {
        gd = fromOwl.generateGraphDocument(ontology);
      } catch (Exception e) {
        throw new IOException(oboGraphError);
      }
      File outfile = new File(ontologyIRI.toURI());
      ObjectMapper mapper = new ObjectMapper();
      mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
      ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
      writer.writeValue(new FileOutputStream(outfile), gd);
    } else if (format instanceof OBODocumentFormat && !cleanOBO.isEmpty()) {
      // OBO format can be handled natively by the OWLAPI ontology manager, but in "clean" mode we
      // need to call the OBO converter ourselves
      OBODoc oboDoc = makeCleanOBODocument(ontology, cleanOBO);
      OBOFormatWriter oboWriter = new OBOFormatWriter();
      oboWriter.setCheckStructure(checkOBO);
      oboWriter.write(oboDoc, new File(ontologyIRI.toURI()));
    } else {
      // use native save functionality
      try {
        ontology.getOWLOntologyManager().saveOntology(ontology, format, ontologyIRI);
      } catch (OWLOntologyStorageException e) {
        // Determine if its caused by an OBO Format error
        if (format instanceof OBODocumentFormat
            && e.getCause() instanceof FrameStructureException) {
          throw new IOException(
              String.format(oboStructureError, e.getCause().getMessage()), e.getCause());
        }
        if (e.getCause() instanceof IllegalElementNameException) {
          IllegalElementNameException e2 = (IllegalElementNameException) e.getCause();
          String element = e2.getElementName();
          if (isValidCURIE(element)) {
            String prefix = element.split(":")[0];
            throw new IOException(String.format(undefinedPrefixError, e2.getElementName(), prefix));
          } else {
            throw new IOException(String.format(invalidElementError, element));
          }
        }
        throw new IOException(String.format(ontologyStorageError, ontologyIRI), e);
      } catch (NullPointerException e) {
        if (format instanceof OBODocumentFormat && !checkOBO) {
          // Critical OBO structure error
          throw new IOException(
              "OBO STRUCTURE ERROR ontology cannot be saved in OBO format. Please use '--check true' to see cause.");
        } else {
          // Unknown null pointer exception
          throw new IOException(String.format(ontologyStorageError, ontologyIRI), e);
        }
      }
    }
  }

  /**
   * Given a formatted ontology as a byte array and an IRI, save the data to the IRI as a gzipped
   * file.
   *
   * @param data byte array of ontology
   * @param ontologyIRI IRI to save to
   * @throws IOException on any problem
   */
  private void saveCompressedOntology(byte[] data, IRI ontologyIRI) throws IOException {
    File f = new File(ontologyIRI.toURI());
    boolean newFile = f.createNewFile();
    FileOutputStream fos = new FileOutputStream(f);
    BufferedOutputStream bos = new BufferedOutputStream(fos);
    try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
      gos.write(data, 0, data.length);
    } catch (IOException e) {
      if (!newFile) {
        f.delete();
      }
    }
  }

  /**
   * Convert an ontology to an OBO document, tweaking the ontology as needed to produce a "clean"
   * output.
   *
   * <p>Note that apart from the removal of "untranslatable axioms", which only affects the OBO
   * document and leaves the original ontology untouched, all operations performed here irreversibly
   * modify the ontology.
   *
   * @param ontology the ontology to convert
   * @param options option set dictating what should be cleaned in the ontology
   * @return the resulting OBO document
   */
  private OBODoc makeCleanOBODocument(OWLOntology ontology, EnumSet<OBOWriteOption> options) {
    if (options.contains(OBOWriteOption.DROP_GCI_AXIOMS)) {
      Set<OWLClassAxiom> gciAxioms = ontology.getGeneralClassAxioms();
      if (!gciAxioms.isEmpty()) {
        ontology.getOWLOntologyManager().removeAxioms(ontology, gciAxioms);
        logger.warn("Removing {} GCI axioms", gciAxioms.size());
      }
    }

    Set<IRI> dropExtraProperties = new HashSet<>();
    if (options.contains(OBOWriteOption.DROP_EXTRA_LABELS)) {
      dropExtraProperties.add(OWLRDFVocabulary.RDFS_LABEL.getIRI());
    }
    if (options.contains(OBOWriteOption.DROP_EXTRA_DEFINITIONS)) {
      dropExtraProperties.add(Obo2OWLConstants.Obo2OWLVocabulary.IRI_IAO_0000115.getIRI());
    }
    if (options.contains(OBOWriteOption.DROP_EXTRA_COMMENTS)) {
      dropExtraProperties.add(OWLRDFVocabulary.RDFS_COMMENT.getIRI());
    }
    if (!dropExtraProperties.isEmpty()) {
      OntologyHelper.removeExtraAnnotations(ontology, dropExtraProperties);
    }

    if (options.contains(OBOWriteOption.MERGE_COMMENTS)) {
      Set<IRI> mergeProperties = new HashSet<>();
      mergeProperties.add(OWLRDFVocabulary.RDFS_COMMENT.getIRI());
      OntologyHelper.mergeExtraAnnotations(ontology, mergeProperties);
    }

    // Dropping the unstranslatable axioms is delegated to the OWLAPI's built-in feature
    OWLAPIOwl2Obo oboConverter = new OWLAPIOwl2Obo(ontology.getOWLOntologyManager());
    oboConverter.setDiscardUntranslatable(
        options.contains(OBOWriteOption.DROP_UNSTRANSLATABLE_AXIOMS));
    return oboConverter.convert(ontology);
  }
}
