# Annotate

It's important to add metadata to an ontology before releasing it, and to update the ontology version IRI.

General annotations can be added one-by-one with `--annotation`, and the IRIs can be set with `--version-iri` and `--ontology-iri`. You can specify as many annotation flags as you'd like in one command:

    robot annotate --input edit.owl \
      --ontology-iri "https://github.com/ontodev/robot/examples/annotated.owl" \
      --version-iri "https://github.com/ontodev/robot/examples/annotated-1.owl" \
      --annotation rdfs:comment "Comment" \
      --annotation rdfs:label "Label" \
      --annotation-file annotations.ttl \
      --output results/edi-annotated.owl

Or all at once from a turtle (.ttl) file with `--annotation-file`:
<!-- DO NOT TEST -->
```
@prefix rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#> .
@prefix owl:     <http://www.w3.org/2002/07/owl#> .
@prefix example: <https://github.com/ontodev/robot/examples/> .

example:annotated.owl
  rdf:type owl:Ontology ;
  rdfs:comment "Comment from annotations.ttl file." .
```

Including at least the following annotations is recommended:
  * Ontology & version IRIs
  * Title (<a href="http://purl.org/dc/terms/title" target="_blank">`dcterms:title`</a>)
  * Description (<a href="http://purl.org/dc/terms/description" target="_blank">`dcterms:description`</a>)
  * License (<a href="http://purl.org/dc/terms/license" target="_blank">`dcterms:license`</a>)

If the `--interpolate` option is used, then some placeholders, of the form `%{NAME}`, can be used within the annotation values and will be automatically replaced by computed values. Currently supported placeholers are:

  * `%{ontology_iri}`, replaced by the ontology's IRI;
  * `%{version_iri}`, replaced by the ontology's version IRI.

Example:

    robot annotate --input fbcv-module.owl \
      --interpolate true \
      --link-annotation dc:source %{version_iri} \
      --annotation rdfs:comment "Derived from %{ontology_iri}" \
      --output results/fbcv-annotated.owl

This command can also remove all ontology annotations from your file with `--remove-annotations`. You can combine this with options to add new annotations:

    robot annotate --input annotated.owl \
      --remove-annotations \
      --annotation-file annotations.ttl \
      --output results/annotated_2.owl


## Annotating the Source

It’s also possible to annotate the ontology axioms with the ontology IRI or version IRI.

  * `--annotate-derived-from true`: annotates all axioms with the source's version IRI if it exists, else with the ontology IRI, using `prov:wasDerivedFrom`. If the axiom already has an annotation using this property (`prov:wasDerivedFrom`), the existing annotation will be kept and no new annotation will be added.

    robot annotate --input example2.owl \
      --annotate-derived-from true \
      --output results/example2_derived_from.owl

  * `--annotate-defined-by true`: annotates all entities (class, data, annotation, object property and named individual declaration axioms) with the source's IRI using `rdfs:isDefinedBy`. If the term already has an annotation using this property (`rdfs:isDefinedBy`), the existing annotation will be kept and no new annotation will be added.

    robot annotate --input example2.owl \
      --annotate-defined-by true \
      --output results/example2_defined_by.owl

---

## Error Messages

### Annotation Format Error

Arguments to `--annotation`  or `--axiom-annotation` option must follow the `PROP VALUE` format:
```
--annotation rdfs:comment "this is a comment"`
```

Arguments to `--link-annotation` option must follow the `PROP LINK` format:
```
--link-annotation dc:license https://creativecommons.org/publicdomain/zero/1.0/
```

Arguments to `--language-annotation` option must follow the `PROP VALUE LANG` format:
```
--language-annotation rdfs:label "label" en
```

Arguments to `--typed-annotation` option must follow the `PROP VALUE TYPE` format:
```
--typed-annotation rdfs:label "label" xsd:string
```

### Missing Annotation Error

No annotations were provided for the `annotate` command. The `--remove-annotations` option is also accepted in place of annotations.
