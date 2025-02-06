# Repair

ROBOT can repair certain problems encountered in ontologies. So far, this is limited to

- [updating axioms pointing to deprecated classes](#deprecated) with their replacement class (indicated using [term replaced by](http://purl.obolibrary.org/obo/IAO_0100001)) and
- [merging axiom annotations](#mergingax) about the same axiom into a single statement.

To repair an ontology (i.e. execute all repair operations implemented by `robot`) run the following command:

    robot repair \
      --input need-of-repair.owl \
      --output results/repaired.owl

This will generate a new file `results/repaired.owl`. You can compare this with the original file (either using unix `diff` or [robot diff](diff)). If the changes that were made look good then you can simply replace the source file with the repaired file (`mv results/repaired.owl need-of-repair.owl`).

In the following, we will discuss how to run the different implemented repairs individually.

<a id="deprecated"></a>

## Updating axioms pointing to deprecated classes with their replacement class

This situation can arise in a number of different ways:

 1. When you are editing an ontology in Protege, you obsolete a class, but forget to repair axioms that reference this.
 2. When you rebuild an import module using [extract](extract), and the upstream ontology has obsoleted classes that you are using.
 
For more on obsoletion workflows, see the [obsoletion guide](https://ontology-development-kit.readthedocs.io/en/latest/ObsoleteTerm.html) in the Ontology Development Kit documentation.

To update axioms pointing to deprecated classes with their replacement class an ontology run the following command:

    robot repair \
      --input need-of-repair.owl \
      --invalid-references true \
      --output results/repaired.owl

By default, annotation axioms are not migrated to replacement classes. 
However, this can be enabled for a list of annotation properties passed either as arguments to `--annotation-property` or in a term file `--annotation-properties-file`:

    robot repair \
      --input xref-need-of-repair.obo \
      --annotation-property oboInOwl:hasDbXref \
      --output results/xref-repaired.obo

<a id="mergingax"></a>

## Merging axiom annotations

Sometimes we end up with the same exact statement (synonym assertion, subclass of axiom) having different sets of axiom annotations (for example, different sources of provenance). If we want to merge these, so that all axiom annotations are combined on the same statement, we can use use:

    robot repair --input uberon_axiom_annotation_merging.ofn \
      --merge-axiom-annotations true \
      --output results/uberon_axiom_annotation_merged.ofn
