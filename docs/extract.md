# Extract

The reuse of ontology terms creates links between data, making the ontology and the data more valuable. But often you want to reuse just a subset of terms from a target ontology, not the whole thing. Here we take the filtered ontology from the previous step and extract a "STAR" module for the term 'adrenal cortex' and its supporting terms:

    robot extract --method STAR \
        --input filtered.owl \
        --term-file uberon_module.txt \
        --output results/uberon_module.owl

See <a href="/examples/uberon_module.txt" target="_blank">`uberon_module.txt`</a> for an example of a term file. Terms should be listed line by line, and comments can be included with `#`. Individual terms can be specified with `--term` followed by the CURIE.

NOTE: The `extract` command works on the input ontology, not its imports. To extract from imports you should first [merge](/merge).

The `--method` options fall into two groups: Syntactic Locality Module Extractor (SLME) and Minimum Information to Reference an External Ontology Term (MIREOT).

- STAR: use the SLME to extract a fixpoint-nested module
- TOP: use the SLME to extract a top module
- BOT: use the SLME to extract a bottom module
- MIREOT: extract a simple hierarchy of terms

## Syntactic Locality Module Extractor (SLME)

Each SLME module type takes a "seed" that you specify with `--term` and `--term-file` options. From the seed it builds a module with a "signature" that includes the seed plus any other terms required so that any logical entailments are preserved between entities (classes, properties and individuals) in the signature. For example, if an ontology implies that A is a subclass of B, and the seed contains A and B, then the module will also imply that A is a subclass of B. In other words, the module will contain all the axioms needed to provide the same entailments for the seed terms (and resulting signature) as the full ontology would.

- BOT: The BOT, or BOTTOM, -module contains mainly the terms in the seed, plus all their super-classes and the inter-relations between them. The module is called BOT (or BOTTOM) because it takes a view from the BOTTOM of the class-hierarchy upwards. Modules of this type are typically of a medium size and should be used if there is a need to include all super-classes in the module. This is the most widely used module type - when in doubt, use this one.

- TOP: The TOP-module contains mainly the terms in the seed, plus all their sub-classes and the inter-relations between them. The module is called TOP because it takes a view from the TOP of the class-hierarchy downwards. Modules of this type are typically large and should only be used if there is a need to include all sub-classes in the module.

- STAR: The STAR-module contains mainly the terms in the seed and the inter-relations between them (not necessarily sub- and super-classes). Modules of this type are typically very small and should be used if the module needs to be of minimal size containing only (or mostly) the classes in the seed file.

For more details see:

- [OWL Modularity](http://owl.cs.manchester.ac.uk/research/modularity/)
- [SLME source code](http://owlcs.github.io/owlapi/apidocs_4/uk/ac/manchester/cs/owlapi/modularity/SyntacticLocalityModuleExtractor.html)
- [ModuleType source code](http://owlcs.github.io/owlapi/apidocs_4/uk/ac/manchester/cs/owlapi/modularity/ModuleType.html)

## MIREOT

The MIREOT method preserves the hierarchy of the input ontology (subclass and subproperty relationships), but does not try to preserve the full set of logical entailments. Both "upper" (ancestor) and "lower" (descendant) limits can be specified, like this:

    robot extract --method MIREOT \
        --input uberon_fragment.owl \
        --upper-term "obo:UBERON_0000465" \
        --lower-term "obo:UBERON_0001017" \
        --lower-term "obo:UBERON_0002369" \
        --output results/uberon_mireot.owl

To specify upper and lower term files, use `--upper-terms` and `--lower-terms`. The upper terms are the upper boundaries of what will be extracted. If no upper term is specified, all terms up to the root (`owl:Thing`) will be returned. The lower term (or terms) is required; this is the limit to what will be extracted, e.g. no descendants of the lower term will be included in the result.

For more details see the [MIREOT paper](http://dx.doi.org/10.3233/AO-2011-0087).

### Intermediates

When extracting (especially with MIREOT), sometimes the hierarchy can have too many intermediate classes, making it difficult to identify relevant relationships. For example, you may end up with this after extracting `adrenal gland` and `trunk region element`:
```
 - organ
   - trunk region element
   - gland
     - endocrine gland
       - adrenal/interrenal gland
         - adrenal gland
```

By specifying how to handle these intermediates, you can reduce unnecessary intermediate classes:

* `--intermediates all`: default behavior, do not prune the ontology
* `--intermediates minimal`: only include intermediate intermediates with more than one child, as well as the top-level parents
* `--intermediates none`: do not include any intermediates
  * For MIREOT, this will only include top and bottom level classes.
  * For any SLME method, this will only include the classes directly used in the logic of the input terms

The above example, with `--intermediates minimal`, would become:
```
- organ
  - trunk region element
  - gland
    - adrenal gland
```

With `--intermediates none` (particularly with MIREOT):
```
- organ
  - trunk region element
  - adrenal gland
```

Any term specified as an input term will not be pruned.

### Ontology Annotations

You can also include ontology annotations from the input ontology with `--copy-ontology-annotations true`. By default, this is false.

    robot extract --method BOT \
      --input annotated.owl \
      --term UBERON:0000916 \
      --copy-ontology-annotations true \
      --output results/annotated_module.owl
      
### Source Annotations

`extract` provides an option to annotate extracted terms with `rdfs:isDefinedBy`. If the term already has an annotation using this property, the existing annotation will be copied and no new annotation will be added.

    robot extract --method BOT \
      --input annotated.owl \
      --term UBERON:0000916 \
      --annotate-with-source true \
      --output results/annotated_source.owl 

The object of the property is, by default, the base name of the term's IRI. For example, the IRI for `GO:0000001` (`http://purl.obolibrary.org/obo/GO_0000001`) would receive the source `http://purl.obolibrary.org/obo/go.owl`. 

Sometimes classes are adopted by other ontologies, but retain their original IRI. In this case, you can provide the path to a [term-to-source mapping file](/examples/source-map.tsv) as CSV or TSV.

    robot --prefix 'GO: http://purl.obolibrary.org/obo/GO_' \
      extract --method BOT \
      --input annotated.owl \
      --term UBERON:0000916 \
      --annotate-with-source true \
      --sources source-map.tsv \
      --output results/changed_source.owl

The mapping file can either use full IRIs:

```
http://purl.obolibrary.org/obo/BFO_0000001,http://purl.obolibrary.org/obo/ro.owl
```

Or prefixes, as long as the [prefix is valid](/global#prefixes):

```
BFO:0000001,RO
```

---

## Error Messages

### Missing MIREOT Term(s) Error

MIREOT requires either `--lower-term` or `--branch-from-term` to proceed. `--upper-term` is optional.

### Missing Lower Term(s) Error

If an `--upper-term` is specified for MIREOT, `--lower-term` (or terms) must also be specified.

### Invalid Method Error

The `--method` option only accepts: MIREOT, STAR, TOP, and BOT.

### Invalid Option Error

The following flags *should not* be used with STAR, TOP, or BOT methods:
* `--upper-term` & `--upper-terms`
* `--lower-term` & `--lower-terms`
* `--branch-from-term` & `--branch-from-terms`

### Invalid Source Map Error

The input for `--sources` must be either CSV or TSV format.

### Invalid Intermediates Error

`--intermediates` must be one of: `all`, `minimal`, or `none`.
