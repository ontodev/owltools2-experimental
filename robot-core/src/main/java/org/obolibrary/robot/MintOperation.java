package org.obolibrary.robot;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLOntology;

public class MintOperation {

  public static class MintConfig {

    private String mintedIDPrefix = "http://purl.obolibrary.org/obo/EXAMPLE_";
    private String tempIDPrefix = "http://purl.obolibrary.org/temp#";
    private OWLAnnotationProperty mintedFromProperty =
        OWLManager.getOWLDataFactory()
            .getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/OMO_mintedfrom"));
    private int minIdentifier = 0;
    private int maxIdentifier = Integer.MAX_VALUE;
    private int padWidth = 7;
    private boolean keepDeprecated = false;

    public String getMintedIDPrefix() {
      return mintedIDPrefix;
    }

    public void setMintedIDPrefix(String mintedIDPrefix) {
      this.mintedIDPrefix = mintedIDPrefix;
    }

    public String getTempIDPrefix() {
      return tempIDPrefix;
    }

    public void setTempIDPrefix(String tempIDPrefix) {
      this.tempIDPrefix = tempIDPrefix;
    }

    public OWLAnnotationProperty getMintedFromProperty() {
      return mintedFromProperty;
    }

    public void setMintedFromProperty(OWLAnnotationProperty mintedFromProperty) {
      this.mintedFromProperty = mintedFromProperty;
    }

    public int getMinIdentifier() {
      return minIdentifier;
    }

    public void setMinIdentifier(int minIdentifier) {
      this.minIdentifier = minIdentifier;
    }

    public int getMaxIdentifier() {
      return maxIdentifier;
    }

    public void setMaxIdentifier(int maxIdentifier) {
      this.maxIdentifier = maxIdentifier;
    }

    public int getPadWidth() {
      return padWidth;
    }

    public void setPadWidth(int padWidth) {
      this.padWidth = padWidth;
    }

    public boolean isKeepDeprecated() {
      return keepDeprecated;
    }

    public void setKeepDeprecated(boolean keepDeprecated) {
      this.keepDeprecated = keepDeprecated;
    }
  }

  public static void mintIdentifiers(OWLOntology ontology, MintConfig config) {
    // TODO
  }
}
