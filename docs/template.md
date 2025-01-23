# Template

## Contents

1. [Overview](#overview)
2. [Template Strings](#template-strings)
    1. [Generic Template Strings](#generic-template-strings)
    2. [Class Template Strings](#class-template-strings)
    3. [Property Template Strings](#property-template-strings)
    4. [Individual Template Strings](#individual-template-strings)
3. [Merging (`--merge-before`, `--merge-after`)](#merging)
4. [Examples](#examples)

## Overview

ROBOT can convert tables to OWL format using templates. See [`template.csv`](/examples/template.csv) for an example. The approach extends the QTT method described in [Overcoming the ontology enrichment bottleneck with Quick Term Templates](http://dx.doi.org/10.3233/AO-2011-0086).

ROBOT can read comma-separated values (`.csv`) or tab-separated values (`.tsv` or `.tab`):

    robot template --template template.csv \
      --prefix "ex: http://example.com/" \
      --ontology-iri "https://github.com/ontodev/robot/examples/template.owl" \
      --output results/template.owl

Each template file must be set up in the following format:

1. **Headers**: ROBOT expects the first row to contain column names for every column used in the data. These are used to make error messages more helpful.
2. **Templates**: ROBOT expects the second row to contain template strings for each column that will be used in the OWL conversion. See below for details on template strings.
3. **Data**: ROBOT expects each of the remaining rows to correspond to a term (a class, property, or individual).

### Template Options

The `template` command accepts an optional input ontology, either using the `--input` option or from the previous command in a chain. If an input ontology is given, its `rdfs:label`s will be used when parsing the template. The `--template` or `-t` option specifies the CSV or TSV template file. Multiple templates are allowed, and the order of templates is significant. You can also specify the normal `--prefix` options, the `--output-iri` and `--version-iri`, and the usual `--output` options. See [Merging](#merging) for the three different merge options, and details on how they control the output of the command.

A template may have multiple errors in different rows and columns. By default, `template` will fail on the first error encountered. If you wish to proceed with errors, use `--force true`. This will log all row parse errors to STDERR and attempt to create an ontology anyway. Be aware that the output ontology may be missing axioms and ROBOT will complete with a `0` exit code (success).

You can also choose to write errors to a separate table using `--errors <path>`. If the path ends with `csv`, the output will be comma-separated. Otherwise, the output will be tab-separated. The errors table contains the following fields:
* **table**: table name (the `--template`)
* **cell**: A1 notation of cell location (e.g., C3)
* **rule ID**: the CURIE of the rule (`ROBOT-template:[rule-name]`, which expands to `http://robot.obolibrary.org/template#[rule-name]`)
* **message**: text description of the violated rule

If `--force true` is not included with `--errors <path>`, ROBOT will exit with a non-zero exit code (failure) and the output file will not be created.

## Template Strings

### Generic Template Strings

- `ID`: Every term must have an IRI to identify it. This can be specified with an `ID` column. Usually this will be a prefixed ID like `GO:12345`. See the `--prefix` options for details.
    - If an entity already exists in the `--input` ontology, you can refer to it by `LABEL` instead (see below).
    - Rows with no `ID` or `LABEL` will be skipped.
- `LABEL`: a special keyword to specify an `rdfs:label` that uniquely identifies the target term. This can be easier than the numeric IDs for human readers. Keep in mind:
    - The `LABEL` column will create `rdfs:label` string annotation for the entity
    - If you are creating new entities using `LABEL`, be sure to include an `ID` column as well
- `TYPE`: this is the `rdf:type` for the row. Because ROBOT is focused on ontology development, the default value is `owl:Class` and this column is optional. When creating an OWLIndividual, specify the class to which it belongs in this column.
    - `class` or `owl:Class`
    - `object property` or `owl:ObjectProperty`
    - `data property` or `owl:DataProperty`
    - `annotation property` or `owl:AnnotationProperty`
    - `datatype` or `owl:Datatype`
    - `individual`, `named individual`, `owl:Individual`, `owl:NamedIndividual`, or a defined class ID or label
- **annotations**: ROBOT can attach annotations to your term. There are four options:
    - `A` string annotation: If the template string starts with an `A` and a space then it will be interpreted as a string annotation. The rest of the template string should be the label or compact IRI of an annotation property, e.g. `label` or `rdfs:label`. The cell value will be the literal value of the annotation with type `xsd:string`. Annotation property labels do not need to be wrapped in single quotes.
    - `AT` typed annotation: If the template string starts with an `AT` and a space then it will be interpreted as a typed annotation. The `^^` characters must be used to separate the annotation property from the datatype, e.g. `rdfs:comment^^xsd:integer`. The cell value will be the typed literal value of the annotation.
    - `AL` language annotation: If the template string starts with an `AL` and a space then it will be interpreted as a language annotation. The `@` character must be used to separate the annotation property from the language code, e.g. `rdfs:comment@en`.
    - `AI` annotation IRI: If the template string starts with an `AI` and a space, then the annotation will be made as with a string annotation, except that the cell value will be interpreted as an IRI.
- `>A` (**axiom annotations**): ROBOT can also annotate logical and annotation axioms. The axiom annotation will be on the axiom created on the cell to the left of the `>A*` template string. The `>` symbol can be used in front of any valid annotation character (`>A`, `>AT`, `>AL`, `>AI`)

Sometimes you want to include zero or more values in a single spreadsheet cell, for example when you want to allow for multiple annotations or have separate logical axioms. If a template string also contains `SPLIT=|`, then ROBOT will use the `|` character to split the contents of a cell in that column and add an annotation for each result (if there are any). Instead of `|` you can specify a string of characters of your choice -- other than pure whitespace -- to split on (e.g. `SPLIT=, `).

### Class Template Strings

- **class expression**: If the template string starts with `C`, `SC`, `EC`, or `DC` followed by a space and the template string (e.g. `SC %`) then it will be interpreted as a class expression. The value of the current cell will be substituted into the template, replacing all occurrences of the percent `%` character. Then, the result will be parsed into an OWL class expression.
    - ROBOT uses the same syntax for class expressions as Protégé: [Manchester Syntax](http://www.w3.org/2007/OWL/wiki/ManchesterSyntax). This means that an entity can be referred to by its `rdfs:label` (enclosing in single quotes if it has a space in it).
    - If it does not recognize a label, ROBOT will assume that you're trying to refer to a class by its IRI (or compact IRI). This can lead to unexpected behaviour, but it allows you to refer to classes (by IRI) without loading them into the input ontology. This is particularly useful when the input ontology would be too large, such as the NCBI Taxonomy.
    - Properties in class expressions **must** be referred to by label in order to be parsed.
- `SC %`: the class will be asserted to be a subclass of the class expression in this column (same as class type `subclass`)
- `EC %`: the class will be asserted to be an equivalent class of the intersection of the `EC` class expressions in a row (same as class type `equivalent`)
- `DC %`: the class will be asserted to be disjoint with the class expression in this column (same as class type `disjoint`)
- `C %`: the class will be asserted as specified in the `CLASS_TYPE` column
- `CLASS_TYPE`: an optional column that specifies the type for all `C` columns. This allows different rows to have different types of logical definitions. Valid values are:
    - `subclass`: the values of all `C` columns will be asserted as subclasses (this is the default)
    - `equivalent`: values of all `C` columns will be taken as an intersection and asserted to be an equivalent class
    - `disjoint`: the values of all `C` columns will be asserted as disjoint classes

(A `CI` template string tells ROBOT to read the cell value as an IRI and assert it as the `CLASS_TYPE`. This is included for legacy support, and the other class template strings are preferred.)

#### Example of Class Template Strings

| Label   | Entity Type | Superclass | Disjoint Classes | Equivalent axioms |
| ------- | ----------- | ---------- | ---------------- | ----------------- |
| LABEL   | TYPE        | SC %       | DC %             | EC part_of some % |
| Class 2 | class       | Class 1    |                  |                   |
| Class 3 | class       |            |                  | Class 2           |
| Class 4 | class       | equivalent | Class 3          |                   |

Class 2 will be a subclass of Class 1. Class 3 will be equivalent to `part_of some 'Class 2'` and Class 4 will be disjoint with Class 3.

Manchester expressions can also be used within the cells. To avoid ambiguity, it's best to enclose expressions in parentheses:

| Label   | Parent                   |
| ------- | ------------------------ |
| LABEL   | SC %                     |
| Class 4 |                          |
| Class 5 | (part_of some 'Class 4') |

In this template, Class 5 would be a subclass of `part_of some 'Class 4'`.

### Property Template Strings

- `DOMAIN`: The domain to a property is a class expression in [Manchester Syntax](http://www.w3.org/2007/OWL/wiki/ManchesterSyntax) (for object and data properties). For annotation properties, the domain must be a single class specified by label, CURIE, or IRI.
- `RANGE`: The range to a property is either a class expression in [Manchester Syntax](http://www.w3.org/2007/OWL/wiki/ManchesterSyntax) (for object properties) or the name, CURIE, or IRI of a datatype (for annotation and data properties).
- `CHARACTERISTIC`: for each row of data that has a `TYPE` of object property or data property (*not* an annotation property), you can optionally specify a logical `CHARACTERISTIC` column. The column can be split, e.g. `CHARACTERISTIC SPLIT=|`, to specify multiple characteristics. Object properties can have any of the following characteristics, but only `functional` applies to data properties:
    - `functional`: the created property will be functional, meaning each entity (subject) can have at most one value
    - `inverse functional`: the created object property will be inverse functional, meaning each value can have at most one subject
    - `reflexive`: the created object property will be reflexive, meaning each subject can also be a value
    - `irreflexive`: the created object property will be irreflexive, meaning the subject cannot also be the value
    - `symmetric`: the created object property will be symmetric, meaning the subject and value can be reversed
    - `asymmetric`: the created object property will be asymmetric, meaning the subject and value cannot be reversed
    - `transitive`: the created object property will be transitive, meaning the property can be chained
- **property expression**: If the template string starts with `P`, `SP`, `EP`, `DP`, or `IP` followed by a space and the template string (e.g. `SP %`), then it will be interpreted as a property expression. The value of the current cell will be substituted into the template, replacing all occurrences of the `%` character. Then the result will be parsed into an OWL property expression. ROBOT uses the same syntax for property expressions as Protégé: [Manchester Syntax](http://www.w3.org/2007/OWL/wiki/ManchesterSyntax). If it does not recognize a name, ROBOT will assume that you're trying to refer to an entity by its IRI or CURIE. This can lead to unexpected behavior, but it allows you to refer to entities without loading them into the input ontology.
    - `SP %`: the property will be asserted to be a subproperty of the property expression in the column
    - `EP %`: the property will be asserted to be equivalent with the property expression in the column
    - `DP %`: the property will be asserted to be disjoint with the property expression in the column
    - `IP %`: the property will be asserted to be the inverse of the property expression in the column (this can only be used with object properties)
    - `P %`: the property will be asserted as specified in the `PROPERTY_TYPE` column
    - `PROPERTY_TYPE`: an optional column that specifies the type for all `P` columns. This allows different rows to have different types of logical definitions. Valid values are:
        - `subproperty`: the values of all `P` columns will be asserted as subproperties (this is the default, annotation properties can only be subproperties)
        - `equivalent`: values of all `C` columns will be asserted as equivalent properties
        - `disjoint`: the values of all `P` columns will be asserted as disjoint properties
        - `inverse`: the values of all `P` columns will be asserted as inverse properties (only applies to object properties)

#### Example of Property Template Strings

| ID   | Entity Type        | Characteristic | Super Property | Domain  | Range      |
| ---- | ------------------ | -------------- | -------------- | ------- | ---------- |
| ID   | TYPE               | CHARACTERISTIC | SP %           | DOMAIN  | RANGE      |
| OP:1 | owl:ObjectProperty |                | Property 1     | Class 1 | Class 2    |
| DP:1 | owl:DataProperty   | functional     | Property 2     | Class 2 | xsd:string |

### Individual Template Strings

If the `TYPE` is a defined class, `owl:Individual`, or `owl:NamedIndividual`, an instance will be created. If the `TYPE` does not include a defined class, that instance will have no class assertions (unless you use the `TI` template string to add an anonymous type). You may include a `SPLIT=` in `TYPE` if you wish to provide more than one class assertion for an individual.

- **class assertion**:
    - `TI %`: the individual will be asserted to be a type of the *class expression* in the column
- **individual assertion**:
    - `I <property>`: when creating an individual, replace property with an object property or data property to add assertions (either by label or CURIE). The value of each axiom will be the value of the cell in this column. For object property assertions, this is another individual. For data property assertions, this is a literal value. If using a property label here, **do not** wrap the label in single quotes.
    - `SI %`: the individual in the column will be asserted to be the same individual
    - `DI %`: the individual in the column will be asserted to be a different individual

#### Example of Individual Template Strings

| Label        | Entity Type | Individual Role      | Property Assertions | Different Individuals |
| ------------ | ----------- | -------------------- | ------------------- | --------------------- |
| LABEL        | TYPE        | TI 'has role' some % | I part_of           | DI %                  |
| Individual 1 | Class 1     | Role Class 1         | Individual 2        |                       |
| Individual 2 | Class 1     | Role Class 2         |                     | Individual 1          |

<!-- ### Datatype Template Strings -->

## Merging

The `template` command has three options for merging, which are especially useful when chaining commands. First some terminology:

- "input ontology": the ontology from the previous command or specified by the `--input` option, used to resolve terms in the template
- "result ontology": the new ontology created from the template, distinct from the input ontology
- "merged ontology": the result ontology merged into the input ontology

The three options can differ in which ontology is saved for the `--output` option and which is sent to the next command in the chain:

option       | `--output` | output
-------------|------------|-------
no merge     | result     | result
merge before | merged     | merged
merge after  | result     | merged

- no merge (default behaviour): only the result ontology will be output
- `merge-before`: the result ontology is merged into the input ontology immediately, so only the merged ontology will be output
- `merge-after`: any `--output` options apply to the result ontology, then result ontology is merged into the input ontology, and the output of the command is the merged ontology

These three options are particularly useful when chaining commands. For instance, the `merge-after` option lets you save the result ontology separately, then send the merged ontology to the next command. See [merge](/merge) for more information on merge options, including `--collapse-import-closure` and `--include-annotations`.

If the command includes `--ancestors`, the result ontology will include the ancestors (from the input ontology) of the result ontology terms. Only the labels of the ancestors will be included.

## Examples

Create an output ontology that includes the input ontology and the terms defined in the template:

    robot template --merge-before \
      --input edit.owl \
      --template part_of_template.csv \
      --output results/part_of_uberon.owl


Create two outputs -- the templated terms ([`uberon_template.owl`](/examples/uberon_template.owl)) and the input ontology merged with the output ontology with an annotation ([`uberon_v2.owl`](/examples/uberon_v2)):
<!-- DO NOT TEST -->
```
robot template --merge-after \
  --input edit.owl \
  --template uberon_template.csv \
  --output results/uberon_template.owl \
  annotate --annotation rdfs:comment "UBERON with new terms" \
  --output results/uberon_v2.owl
```

Create an output ontology that consists of the template terms plus their dependencies ([`uberon_template_2.owl`](/examples/uberon_template_2.owl)):
<!-- DO NOT TEST -->
```
robot template --ancestors --input edit.owl \
  --template uberon_template.csv \
  --ontology-iri "https://github.com/ontodev/robot/examples/uberon_template_2.owl" \
  --output results/uberon_template_2.owl
```

Create an output ontology that includes the input ontology and the terms defined in the template, but keep the import statements* ([`test_template.owl`](/examples/test_template.owl)):
<!-- DO NOT TEST -->
```
robot template --merge-before \
  --input test.owl \
  --collapse-import-closure false \
  --template uberon_template.csv \
  --output results/test_template.owl
```

ROBOT template data read from separate external file

    robot template --template animals_template.tsv \
        --external-template animals_ext_template.tsv \
        --output results/animals_ext_template.owl

Adjusted line numbers for error reporting for template data read from separate external file
<!-- DO NOT TEST -->
```
robot template --template animals_template_error.tsv \
  --ext-template animals_ext_template.tsv \
  --output results/animals_ext_template.owl
```
MEKS ROBOT multi annotation in a single column

    robot template --prefix "acme: https://shangrila.ef.zz/ontology/acme/" \
        --template multi_annotations_new.tsv \
        --external-template multi_annotations_header_new.tsv
        --output results/multi_annotations_new.owl


\* NOTE: the imports would be merged into the output if `--collapse-import-closure true` is included instead.

Further examples can be found [in the OBI repository](https://github.com/obi-ontology/obi/tree/master/src/ontology/templates)

---

## Error Messages

### Annotation Property Characteristic Error

Annotation properties should not have any value in the `CHARACTERISTIC` column, if it exists. This type of logic for annotation properties is not supported in OWL.

### Annotation Property Error

The annotation property provided could not be resolved. Check your template to ensure the provided annotation property is in a correct IRI or CURIE format. For legibility, using CURIEs is recommended, but you must ensure that the prefix is defined.

If you are using a label, make sure that the label is defined either in the template or input ontology.

```
A rdfs:label
A http://www.w3.org/2000/01/rdf-schema#label
```

### Annotation Property Type Error

The only valid `PROPERTY_TYPE` for an annotation property is `subproperty`. Other types of logic for annotation properties are not supported in OWL. If this column is left blank, it will default to `subproperty`.

### Axiom Annotation Error

An axiom annotation is an annotation on an axiom, either a class axiom or another annotation. Because of this, any time `>A` is used, an annotation must be in the previous column. Any time `>C` is used, a class expression must be in the previous column.
```
A rdfs:label,>A rdfs:comment
C %,>C rdfs:comment
```

### Class Type Error

The valid `CLASS_TYPE` values are: `subclass`, `equivalent`, and `disjoint`.

### Class Type Split Error

A class row may only use one of: `subclass`, `equivalent`, and `disjoint`. To add other types of axioms on an OWL class, use a separate row.

### Column Mismatch Error

Each column that has a template string in row two must have a header string in row one. It is OK to have a header string with no template string.

### Data Property Characteristic Error

The only valid `CHARACTERISTIC` value for a data property is `functional`. Other types of property characteristics for data properties are not supported in OWL.

### Datatype Error

The datatype provided in an `AT` template string could not be resolved. Check your template to ensure the provided datatype is in a correct IRI or CURIE format. For legibility, using CURIEs is recommended, but you must ensure that the prefix is defined.
```
AT rdfs:label^^xsd:string
AT rdfs:label^^http://www.w3.org/2001/XMLSchema#string
```

### File Type Error

The `--template` option accepts the following file types: CSV, TSV, or TAB.

### ID Error

Each template must have an ID column. Keep in mind that if the template has an ID column, but it is not filled in for a row, that row will be skipped.

### Individual Type Error

The valid `INDIVIDUAL_TYPE` values are: `named`, `same`, and `different`.

### Individual Type Split Error

An individual row may only use one of: `named`, `same`, and `different`. To add other types of axioms on an OWL individual, use a separate row.

### IRI Error

The IRI provided as the value (in a row) to an `AI` template string could not be resolved as an IRI. Check your template to ensure the provided value is in a correct IRI or CURIE format. If using CURIEs, remember to ensure the prefix is defined.

### Language Format Error

The template string for an `AL` annotation must always include `@`.
```
AL rdfs:label@en
```

### Manchester Parse Error

The provided value cannot be parsed and may not be in proper Manchester syntax. See [Manchester Syntax](http://www.w3.org/2007/OWL/wiki/ManchesterSyntax) for more details. If you are using labels, make sure the labels are defined in the `--input` ontology or using the `LABEL` column. Also ensure that all properties use a label instead of a CURIE or IRI.

When using a restriction (`some`, `only`, `min`, `max`, `exactly`, or `value`) the term that preceeds the restriction must be a property.

Terms joined using `and` or `or` must be of the same entity type, e.g., you cannot join an object property and a class in an expression.

### Merge Error

`--merge-before` and `--merge-after` cannot be used simultaneously.

### Missing Template Error

You must specify at least one template with `--template` to proceed.

### Missing Type Error

If no `CLASS_TYPE` column is included, ROBOT will default to using `subclass`. If a `CLASS_TYPE` column is included, though, each row must include a specified class type. If the `CLASS_TYPE` is left empty, this error message will be returned.

### Multiple Property Type Error

While the `PROPERTY_TYPE` column may include multiple types, only one of the logical types is allowed in each column: `subproperty`, `equivalent`, `disjoint`, or (for object properties only) `inverse`. To add other types of axioms on an OWL property, use a separate row.

### Null ID Error

An IRI cannot be created from the provided ID. This is most likely because the ID is not formatted properly, as an IRI or a CURIE.

### Property Type Error

The valid `PROPERTY_TYPE` values are: `subproperty`, `equivalent`, `disjoint`, and (for object properties only) `inverse`.

### Template File Error

The template cannot be found in the current directory. Make sure the file exists and your path is correct.

### Typed Format Error

The template string for an `AT` annotation must always include `^^`.
```
AT rdfs:label^^xsd:string
```

### Unknown Characteristic Error

An invalid `CHARACTERISTIC` value was passed. If you are providing multiple characteristics, make sure to include `SPLIT=` in your template string. Valid characteristics are:
- `functional`
- `inverse functional`
- `reflexive`
- `irreflexive`
- `symmetric`
- `asymmetric`
- `transitive`

### Unknown Template Error

Valid template strings are limited to the [described above](#template-strings). If a different template string is provided, this error message will be returned.

