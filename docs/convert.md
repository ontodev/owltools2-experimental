# Convert

Ontologies are shared in different formats. The default format used by ROBOT is RDF/XML, but there are other OWL formats, RDF formats, and also the OBO file format.

    robot convert --input annotated.owl --output results/annotated.obo

The file format is determined by the extension of the output file (e.g. `.obo`), but it can also be declared with the `--format` option. Valid file formats are:
  - json - [OBO Graphs JSON](https://github.com/geneontology/obographs/)
  - obo - [OBO Format](http://purl.obolibrary.org/obo/oboformat)
  - ofn - [OWL Functional](http://www.w3.org/TR/owl2-syntax/)
  - omn - [Manchester](https://www.w3.org/TR/owl2-manchester-syntax/)
  - owl - [RDF/XML](https://www.w3.org/TR/rdf-syntax-grammar/)
  - owx - [OWL/XML](https://www.w3.org/TR/owl2-xml-serialization/)
  - ttl - [Turtle](https://www.w3.org/TR/turtle/)

In the following example we convert an input ontology to OBOGraphs JSON, explicitly specifying the target format with `--format`:

    robot convert -i ro-base.owl --format json -o results/ro-base.json

### Handling Compressed Files

All ROBOT commands support [gzip](https://www.gzip.org/) format files ending with the extension `.gz`. Any of the above formats can be compressed as long as the output ends with `.gz` (format must be specified for a compressed output):

	robot convert --input annotated.owl --format obo \
	 --output results/annotated.obo.gz

Gzip format files can also be used as input:

	robot convert --input annotated.obo.gz \
	 --output results/annotated.owl

### Converting to OBO Format

By default, the OBO writer strictly enforces [document structure rules](http://owlcollab.github.io/oboformat/doc/obo-syntax.html#4). If an ontology violates these, the convert to OBO operation will fail. These checks can be ignored by including `--check false`.

As a document is converted to OBO, you may see `ERROR MASKING ERROR` exceptions. This does not indicate failure, but it should be noted that these axioms will not be translated to OBO format. Rather, they will be included in the ontology header under `owl-axioms`. See [Untranslatable OWL axioms](http://owlcollab.github.io/oboformat/doc/obo-syntax.html#5.0.4) for more details.

The OBO output can be fine-tuned with the `--clean-obo` option. That option takes a space-separated list of keywords that each enables a customization of the OBO output. Available keywords are:
  - `drop-extra-labels`: forcefully drop supernumerary `rdfs:label` annotation, to make the ontology compliant with the OBO specification (which dictates that a class can only have one label).
  - `drop-extra-definitions`: likewise, but for `IAO:0000115` annotations (definitions).
  - `drop-extra-comments`: likewise, but for `rdfs:comment` annotations.
  - `merge-comments`: merge `rdfs:comment` annotations, when there are more than one, into a single annotation (alternative to `drop-extra-comments`).
  - `drop-untranslatable-axioms`: drop axioms that cannot be represented in OBO format, instead of writing them into the aforementioned `owl-axioms` header tag.
  - `drop-gci-axioms`: drop axioms that represent General Concept Inclusions, even if they can be legally represented in OBO format.

In addition, the following special keywords are also accepted:
  - `strict`: equivalent to `drop-extra-labels drop-extra-definitions drop-extra-comments`, to force the production of a valid OBO file by dropping supernumerary annotations as needed.
  - `true`: alias for `strict`.
  - `simple`: equivalent to `strict drop-untranslatable-axioms drop-gci-axioms`, to force the production of an OBO file that is not only valid, but also free of any `owl-axioms` header tag and GCI axioms (which, while perfectly valid with respect to the OBO specification, are not always handled correctly by all OBO parsers).

#### Examples

Convert a file to OBO and ensure the resulting file is compliant with the OBO specification, dropping supernumerary annotations if necessary:

    robot convert -i cl_module.ofn \
      --clean-obo strict \
      --output results/cl_module-strict.obo

Likewise, but with merging comments into a single one instead of dropping the supernumerary comments:

    robot convert -i cl_module.ofn \
      --clean-obo "strict merge-comments" \
      --output results/cl_module-strict-mergedcomments.obo

Convert a file to a simple variant of the OBO format (without any `owl-axioms` tag and GCI axioms):

    robot convert -i cl_module.ofn \
      --clean-obo simple \
      --output results/cl_module-simple.obo

---

## Error Messages

### Check Arg Error

`--check` only accepts `true` or `false` (not case sensitive) as arguments. By default, `--check` is true and the OBO document structure checks are performed.

### Output Error

`convert` requires exactly one `--output`. If you do not specify the `--output`, or specify more than one, ROBOT cannot proceed. If chaining commands, place `convert` last.

### Format Error

The `convert` command expects either the `--output` file name to include an extension, or a format specified by `--format`.

Correct:
```
--output release.owl
--format owl --output release
```
Incorrect:
```
--output release
```

