# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Allow interpolation of ontology IRI and version IRI within annotation values [#1241]

### Fixed
- Update owl-diff dependency for stable ordering and to avoid large string creation [#1227]
- Improve disambiguation of properties in QuotedEntityChecker [#1226]
- Skip "non-robot" columns in templates for the purposes of axiom annotations [#1216]
- Add missing filter for deprecated in lowercase_definition check [#1220]
- Bug was fixed that caused logical axioms with axiom annotations not to be processed correctly when merging axiom annotations [#1223]

## [1.9.7] - 2024-10-30

### Fixed
- Output inferred object property assertions using Whelk reasoner, by updating to Whelk 1.1.3. [#1121]

### Changed
- Update Whelk to 1.2.1 [#1221]
- Treat raw IRI as an OWLClass in logical template cells [#1211]

## [1.9.6] - 2024-05-28

### Added
- Added option to input template strings from external file [#1152]

### Changed
- Updated `duplicate_exact_syonym` [`report`] query to be case-insensitive and ignore synoyms annotated as abbreviation or acronym synonym types [#1179]
- Extend `--drop-axiom-annotations` option to support value-specific removal of axiom annotations [#1193]
- Add `--enforce-obo-format`, `--exclude-named-classes` and `--include-subclass-of` features to relax command [#1060], [#1183]
- Updated obographs to [version 0.3.1](https://github.com/geneontology/obographs/releases/tag/v0.3.1)
- Updated OWL API to 3.5.29. This includes a major update to OBO Format which now supports [IDSPACE declarations](https://github.com/owlcs/owlapi/pull/1102) (non-OBO Foundry prefixes).
- Updated Elk to version 0.6.0, see [here](https://github.com/liveontologies/elk-reasoner/issues/48#issuecomment-2130090254).

### Fixed
- '--annotate-with-source true' does not work with extract --method subset [#1160]
- Fix how Template adds entities to the QuotedEntityChecker [#1104]
- [`merge`] and 'annotate' operations '--annotate-defined-by' excludes reserved OWL 2 vocabularies [#1171]
- Handle IRIs that are not entities in export [#1168]
- Fix integration tests [#1181]
- `robot repair` is fixed to be more flexible, to enable partial repairs [#1194]
- Invalid Xrefs test has been fixed to recognise invalid CURIEs correctly [#1127]
- Fix issue with correctly determining base entities [#1108]

## [1.9.5] - 2023-09-20

### Added
- Updated ELK from 0.4.3 to 0.5.0. [#999]. This is an important change as ELK 0.5.0 is more complete than 0.4.3, which means that it will potentially uncover inferences, in particular unsatisfiable classes, which were not recognised by ELK 0.4.3.
- Add support for pluggable commands [#1119]
- Add `--drop-axiom-annotations` option to drop axiom annotations in [`remove`] and [`filter`] [#1023]

### Changed
- Migrate to OWL API 4.5.26 to deal with [broken turtle serialiser](https://github.com/ontodev/robot/issues/1129). [#1135]
- Improvements to `export` and `report` for XLSX format [#1148]

## [1.9.4] - 2023-05-23

### Changed
- Speed up unsatisfiable object-property check on certain reasoners including HermiT [#1100]
- Update HermiT from 1.3.8.413 to 1.4.5.456 [#1073]

### Fixed
- Preserve prefixes across ontology load and save [#1101]

## [1.9.3] - 2023-02-16

### Added
- Subset method added to [`extract`] [#1000]

### Changed
- Upgrade OWLAPI to 4.5.25 [#1093]

### Fixed
- Restore logging details for CLI [#1091]
- Fix OBO Serialiser bug where built in vocabulary was introduced as annotation properties [#1089]
- Fix OBO Serialiser erroneous logging [#1088]

## [1.9.2] - 2023-02-09

### Changed
- Upgrade OWLAPI to 4.5.24 [#1086]

### Fixed
- Downgrade SnakeYaml to 1.31 [#1071]
- Don't check for disjoint annotation properties [#1084]

## [1.9.1] - 2022-10-28

### Added
- [`extract`] and [`merge`] should optionally inject provenance [#977]
- Checking for empty strings in addition to missing ones in `missing_label.rq` [#1017]
- Add "domain" and "range" support to export [#1061]

### Fixed
- Fixed report serialization in JSON [#1016]
- Fix missing labels in [`diff`] output. [#1026]
- input IRI now takes catalog file into account [#1030]

## [1.9.0] - 2022-06-16

### Added
- Add new command: [`expand`] [#964]

### Fixed
- Fix OBOGraphs by updating `guava` [#1009]

## [1.8.4] - 2022-06-10

### Added
- Add `--mapping` option and support for label updates to [`rename`] [#960]

### Changed
- Optimize memory usage for update queries using `--temporary-file` switch [#978]
- Allow any case builtin `TYPE` in `template` [#971]
- Sort [`report`] violations by rule name within level [#955]

### Fixed
- Fix subClassOf cycles in related object selection [#979]
- Fix equivalent axioms in [`template`] [#973]

## [1.8.3] - 2021-12-16

### Changed
- Replace `log4j` with `logback` [#948] [#953]

### Fixed
- Fix custom [`report`] queries [#944]
- Fix methods in `Report` object for ROBOT as a library [#951]

## [1.8.2] - 2021-12-02

### Added
- Add links to query documentation for default rules in [`report`] [#879]
- Ability to restrict [`report`] to base ontology [#872]
- Add check for equivalent class with no genus to [`report`] [#865]
- Add check for illegal use of built-in vocabulary [#867]
- Add check for misused replaced-by annotation [#869]
- Add checks for undeclared synonymtype and subsettype [#870]
- Inferred axiom generators for domains and ranges [#931]

### Changed
- Split equivalent class check [#856]
- Allow Dublin Core "terms" namespace (`http://purl.org/dc/terms/`) for `description` and `title` properties on ontology. [#741]
- Allow numbers in lowercase_definition check [#866]
- Blank nodes in [`report`] are now referred to as `blank node` rather than a random identifier [#873]
- Change behaviour of [`template`] `--errors` option without `--force` [#929]
- Fail hard on bad [`reason`] `--equivalent-classes-allowed` argument [#938]

### Fixed
- Fix printing violations in [`report`] [#823]
- Fix handling of `rdf:type` in [`export`] [#834]
- Fix missing annotations from [`export`] [#850]
- Fail on unknown rule names in [`report`] [#858]
- Fix behaviour of `--preserve-structure` when using internal or external axiom selectors for [`remove`] or [`filter`] [#816]
- Fix duplicate_label_synonym [#864]
- Fix value rendering for entities in [`report`] [#874]
- Fix OBO serialisation issues when using `--check false` [#896]
- Fix `merge --inputs` patterns with parent directories [#899]
- Fix `deprecated_class_reference` [`report`] query [#902]
- Fix error handling for JSON conversion [#907]
- Fix handling of property chains when removing/filtering base axioms in [#914]
- Fix SPLIT unpacking in named individuals in [`template`] [#924]

### Changed
- Do not allow malformed IRIs to be returned by `IOHelper` [#882]

## [1.8.1] - 2021-01-27

### Fixed
- Fix handling of class expressions from [`template`] [#808]

## [1.8.0] - 2021-01-27

### Added
- Add [`measure`] command [#774]
- Added 'strict' mode for loading ontologies in [#788]
- Add --allow-missing-entities option in [`rename`] command [#793]
- Add `html-list` format option to [`export`] in [#703]

### Changed
- Explanations for inconsistent and unsatisfiable classes in [`explain`] [#779]

### Fixed
- Allow case-insensitive "SubClasses" in [`export`] header [#802]

## [1.7.2] - 2020-11-18

### Changed
- Update to whelk 1.0.4
- Run [`query`] on existing TDB dataset (instead of ontology input) in [#792]
- Improved error messages for [`template`] parse errors in [#796]

### Fixed
- Fix blank node subjects in [`report`] in [#767]
- Fixed IRI handling for [`template`] in [#783]

## [1.7.1] - 2020-10-22

### Added
- Add Whelk OWL reasoner in [#730]
- Add [`validate`] command [#691]
- Add `--errors <path>` option to [`template`] in [#713]
- Add new formats to [`report`]: HTML, JSON, and XLSX [#699]
- Add `--fail-on-violation <bool>` option to [`verify`] in [#738]
- Add `html-list` format option to [`export`] in [#703]

### Fixed
- Handle empty [`template`] property charactersitics in [#719]
- Fix referencing properties by CURIE in [`export`] in [#722]
- Fix [`validate`] `--write-all true` in [#726]
- Fix reported row number in [`validate`] error tables in [#727]
- Fix equivalent class rendering for [`template`] in [#728]
- Fix ontology IRI rendering for [`report`] in [#739]

## [1.7.0] - 2020-07-31

### Added
- Add [`export`] command [#481]
- Add JSON format to [`export`] in [#645]
- Add Excel format to [`export`] in [#646]
- Add `--create-tdb <true/false>` option to [`query`] in [#685]

### Changed
- Updated `obographs` from 0.0.8 to 0.2.1 [#657]

### Fixed
- Fix filtering axioms with multiple axiom selectors [#644]
- Fix comparator method for sorting empty strings with [`export`] in [#654]
- Fix releasing dataset after exception when running [`report`] with `--tdb true` [#659]
- Reduced time spent loading datasets for [`query`] in [#666]
- Fix writing JSON format to use `OutputStream` with [`convert`] in [#671]
- Fix IRI resolution for `template` in [#689]
- Fix MIREOT [`extract`] on overlapping class/individual entity for [#709] in [#710]
- Fix issue with `--add-prefixes` option in [#715]

## [1.6.0] - 2020-03-04

### Added
- Add [`collapse`] command [#578]
- Add support for anonymous class assertions in [`template`] in [#630]
- Add maven plugin to update OBO context [#608]
- Add more efficient mode for [`reduce`] when only considering named classes [#619]

### Changed
- Switch whitespace queries to regex [#606]
- Update nucleus import for tests [#597]

### Fixed
- Fix warning when term is in imports (extract command) [#625]
- Fix index out of bounds error in extract [#617]
- Use URIs to fix resource path issues [#603]

## [1.5.0] - 2019-11-28

### Added

- Add new [`python`] command, allowing ROBOT to be controlled from Python using [Py4j](https://www.py4j.org/).
- Add `internal`/`external` selectors for [`remove`]/[`filter`] in [#570]
- Add language selectors for [`remove`]/[`filter`] in [#574]
- Add `tautologies` and `structural-tautologies` selectors for [`remove`]/[`filter`] in [#579]
- Add catalog options for right-side of [`diff`] in [#584]

## [1.4.3] - 2019-09-12

### Fixed

- Fix excessive logging, [#567] and [#374]

## [1.4.2] - 2019-09-11

### Added

- Update [`repair`] to migrate annotations [#510]
- Allow use of Jena TDB for [`report`] in [#558]
- Add `--exclude-tautologies` option for [`reason`] in [#560]

### Changed

- Follow redirects for gzipped input IRIs [#537]

### Fixed

- Fix bug with imported axioms [#523]
- Fix index out of bounds for [`report`] `--print` in [#546]
- Fix stack overflow in [`remove`]/[`filter`] in [#547]
- Fix [`query`] when chaining with `--input-iri` [#555]
- Fix [`template`] bug with equivalent classes [#559]
- Fix [`template`] bug with nested annotation [#564]

## [1.4.1] - 2019-06-27

### Added

- Add `--tdb true` option to [`query`] for Jena TDB on-disk storage [#475]
- Add IRI pattern matching to [`remove`]/[`filter`] in [#448]
- Add `--signature` option to [`remove`]/[`filter`], improve docs [#484]
- Add more output options to [`diff`] in [#461]
- Allow specified prefixes for output [#488]

### Changed

- Made major update to [`template`] command [#403]

### Fixed

- Fix invalid reference errors for OWL built-ins [#455]
- Fix import handling for SPARQL UPDATE [#471]
- Fix [`remove`]/[`filter`] for terms not in ontology [#507]

## [1.4.0] - 2019-03-14

### Added

- Add [`rename`] command [#419], allowing you to replace lists of old IRIs with new IRIs
- Add SPARQL Update support [#352], but note the warning in the 'Update' section of the documentation at <http://robot.obolibrary.org/query>
- Add `--annotate-with-source` option for [`extract`] in [#392]
- Add `--intermediates` option for [`extract`] [#441]
- Add `--individuals` option for [`extract`] [#385]
- Add new selectors for [`remove`] and [`filter`]: `domain` and `range` [#427], `ontology` [#452]

### Changed

- Made improvements to the built-in reports [#438]

## [1.3.0] - 2019-01-18

### Added

- Add [`explain`] command, contributed by [Jim Balhoff](https://github/balhoff)

## [1.2.0] - 2018-12-06

### Added

- Add [`remove`] command for removing axioms from an ontology
- Add [`filter`] command for copying selected axioms to a new ontology
- Add `--use-graphs true` option for [`query`] allows queries over imports as named graphs [#158]
- Add `--labels` option to [`diff`] command [#363]
- Add support for gzipped files [#371]

### Changed

- **Breaking Change**: We have upgraded from Apache Jena 2.13.0 to 3.8.0 [#314], which involves the renaming of several packages and changes to the return types of `QueryOperation`. One other change we've noted is that the new Jena adds fewer `xsd:string` datatypes than the previous version.
- Upgrade to OWLAPI 4.5.6 sometimes changes the ordering of elements in RDFXML format, causing spurious differences in line order when comparing output from previous versions of ROBOT. But note that Protege5.5 uses the same version of the OWLAPI so orderings should be consistent between the two.

## [1.1.0] - 2018-08-04

### Added

- Add [`report`] command
- Add `--collapse-import-closure` option for [`merge`]: When `true` (the default) all imports will be merged and all `owl:import` statements will be removed. **Possible breaking change**: In previous versions of ROBOT, `owl:import` statements were not removed. [#275]
- Add global `--catalog FILE` option [#274]
- Add `--check` option for [`convert`] allows conversion of more OBO-format files
- Add `--include-annotations` option for [`merge`] allows better control of ontology annotations [#277]
- Add `--copy-ontology-annotations` option for [`extract`] in [#319]
- Add `--dump-unsatisfiable` option for [`reason`] [#174]

### Fixed

- improved error messages, linking to ROBOT website [#246]

## [1.0.0] - 2018-02-08

First official release of ROBOT!

[Unreleased]: https://github.com/ontodev/robot/compare/v1.9.7...HEAD
[1.9.7]: https://github.com/ontodev/robot/compare/v1.9.6...v1.9.7
[1.9.6]: https://github.com/ontodev/robot/compare/v1.9.5...v1.9.6
[1.9.5]: https://github.com/ontodev/robot/compare/v1.9.4...v1.9.5
[1.9.4]: https://github.com/ontodev/robot/compare/v1.9.3...v1.9.4
[1.9.3]: https://github.com/ontodev/robot/compare/v1.9.2...v1.9.3
[1.9.2]: https://github.com/ontodev/robot/compare/v1.9.1...v1.9.2
[1.9.1]: https://github.com/ontodev/robot/compare/v1.9.0...v1.9.1
[1.9.0]: https://github.com/ontodev/robot/compare/v1.8.4...v1.9.0
[1.8.4]: https://github.com/ontodev/robot/compare/v1.8.3...v1.8.4
[1.8.3]: https://github.com/ontodev/robot/compare/v1.8.2...v1.8.3
[1.8.2]: https://github.com/ontodev/robot/compare/v1.8.1...v1.8.2
[1.8.1]: https://github.com/ontodev/robot/compare/v1.8.0...v1.8.1
[1.8.0]: https://github.com/ontodev/robot/compare/v1.7.2...v1.8.0
[1.7.2]: https://github.com/ontodev/robot/compare/v1.7.1...v1.7.2
[1.7.1]: https://github.com/ontodev/robot/compare/v1.7.0...v1.7.1
[1.7.0]: https://github.com/ontodev/robot/compare/v1.6.0...v1.7.0
[1.6.0]: https://github.com/ontodev/robot/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/ontodev/robot/compare/v1.4.3...v1.5.0
[1.4.3]: https://github.com/ontodev/robot/compare/v1.4.2...v1.4.3
[1.4.2]: https://github.com/ontodev/robot/compare/v1.4.1...v1.4.2
[1.4.1]: https://github.com/ontodev/robot/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/ontodev/robot/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/ontodev/robot/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/ontodev/robot/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/ontodev/robot/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/ontodev/robot/releases/tag/v1.0.0

[Jim Balhoff]: https://github/balhoff

[`collapse`]: http://robot.obolibrary.org/collapse
[`convert`]: http://robot.obolibrary.org/convert
[`diff`]: http://robot.obolibrary.org/diff
[`explain`]: http://robot.obolibrary.org/explain
[`export`]: http://robot.obolibrary.org/export
[`expand`]: http://robot.obolibrary.org/expand
[`extract`]: http://robot.obolibrary.org/extract
[`filter`]: http://robot.obolibrary.org/filter
[`measure`]: http://robot.obolibrary.org/measure
[`merge`]: http://robot.obolibrary.org/merge
[`python`]: http://robot.obolibrary.org/python
[`query`]: http://robot.obolibrary.org/query
[`reason`]: http://robot.obolibrary.org/reason
[`reduce`]: http://robot.obolibrary.org/reduce
[`remove`]: http://robot.obolibrary.org/remove
[`rename`]: http://robot.obolibrary.org/rename
[`repair`]: http://robot.obolibrary.org/repair
[`report`]: http://robot.obolibrary.org/report
[`template`]: http://robot.obolibrary.org/template
[`validate`]: http://robot.obolibrary.org/validate
[`verify`]: http://robot.obolibrary.org/verify

[#1226]: https://github.com/ontodev/robot/pull/1226
[#1223]: https://github.com/ontodev/robot/pull/1223
[#1221]: https://github.com/ontodev/robot/pull/1221
[#1220]: https://github.com/ontodev/robot/issues/1220
[#1216]: https://github.com/ontodev/robot/issues/1216
[#1211]: https://github.com/ontodev/robot/pull/1211
[#1194]: https://github.com/ontodev/robot/pull/1194
[#1193]: https://github.com/ontodev/robot/pull/1193
[#1183]: https://github.com/ontodev/robot/issues/1183
[#1181]: https://github.com/ontodev/robot/pull/1181
[#1179]: https://github.com/ontodev/robot/pull/1179
[#1171]: https://github.com/ontodev/robot/pull/1171
[#1168]: https://github.com/ontodev/robot/pull/1168
[#1160]: https://github.com/ontodev/robot/pull/1160
[#1152]: https://github.com/ontodev/robot/issues/1152
[#1148]: https://github.com/ontodev/robot/pull/1148
[#1135]: https://github.com/ontodev/robot/pull/1135
[#1127]: https://github.com/ontodev/robot/pull/1127
[#1119]: https://github.com/ontodev/robot/pull/1119
[#1121]: https://github.com/ontodev/robot/issues/1121
[#1108]: https://github.com/ontodev/robot/pull/1108
[#1104]: https://github.com/ontodev/robot/pull/1104
[#1101]: https://github.com/ontodev/robot/issues/1101
[#1100]: https://github.com/ontodev/robot/pull/1100
[#1093]: https://github.com/ontodev/robot/pull/1093
[#1091]: https://github.com/ontodev/robot/issues/1091
[#1089]: https://github.com/ontodev/robot/issues/1089
[#1088]: https://github.com/ontodev/robot/issues/1088
[#1086]: https://github.com/ontodev/robot/pull/1086
[#1084]: https://github.com/ontodev/robot/issues/1084
[#1073]: https://github.com/ontodev/robot/pull/1073
[#1071]: https://github.com/ontodev/robot/pull/1071
[#1061]: https://github.com/ontodev/robot/issues/1061
[#1060]: https://github.com/ontodev/robot/issues/1060
[#1030]: https://github.com/ontodev/robot/issues/1030
[#1026]: https://github.com/ontodev/robot/issues/1026
[#1023]: https://github.com/ontodev/robot/pull/1023
[#1017]: https://github.com/ontodev/robot/issues/1017
[#1016]: https://github.com/ontodev/robot/issues/1016
[#1009]: https://github.com/ontodev/robot/issues/1009
[#1000]: https://github.com/ontodev/robot/pull/1000
[#999]: https://github.com/ontodev/robot/pull/999
[#979]: https://github.com/ontodev/robot/pull/979
[#978]: https://github.com/ontodev/robot/pull/978
[#977]: https://github.com/ontodev/robot/pull/977
[#973]: https://github.com/ontodev/robot/pull/973
[#971]: https://github.com/ontodev/robot/pull/971
[#964]: https://github.com/ontodev/robot/pull/964
[#960]: https://github.com/ontodev/robot/pull/960
[#955]: https://github.com/ontodev/robot/pull/955
[#953]: https://github.com/ontodev/robot/pull/953
[#951]: https://github.com/ontodev/robot/pull/951
[#948]: https://github.com/ontodev/robot/pull/948
[#944]: https://github.com/ontodev/robot/pull/944
[#938]: https://github.com/ontodev/robot/pull/938
[#931]: https://github.com/ontodev/robot/issues/931
[#929]: https://github.com/ontodev/robot/pull/929
[#924]: https://github.com/ontodev/robot/issues/924
[#914]: https://github.com/ontodev/robot/pull/914
[#907]: https://github.com/ontodev/robot/pull/907
[#902]: https://github.com/ontodev/robot/pull/902
[#899]: https://github.com/ontodev/robot/pull/899
[#896]: https://github.com/ontodev/robot/pull/896
[#882]: https://github.com/ontodev/robot/pull/882
[#879]: https://github.com/ontodev/robot/pull/879
[#874]: https://github.com/ontodev/robot/pull/874
[#873]: https://github.com/ontodev/robot/issues/873
[#872]: https://github.com/ontodev/robot/pull/872
[#870]: https://github.com/ontodev/robot/pull/870
[#869]: https://github.com/ontodev/robot/pull/869
[#867]: https://github.com/ontodev/robot/pull/867
[#866]: https://github.com/ontodev/robot/pull/866
[#865]: https://github.com/ontodev/robot/pull/865
[#864]: https://github.com/ontodev/robot/pull/864
[#858]: https://github.com/ontodev/robot/pull/858
[#856]: https://github.com/ontodev/robot/pull/856
[#850]: https://github.com/ontodev/robot/pull/850
[#834]: https://github.com/ontodev/robot/pull/834
[#823]: https://github.com/ontodev/robot/pull/823
[#816]: https://github.com/ontodev/robot/pull/816
[#808]: https://github.com/ontodev/robot/pull/808
[#802]: https://github.com/ontodev/robot/pull/802
[#796]: https://github.com/ontodev/robot/pull/796
[#793]: https://github.com/ontodev/robot/pull/793
[#792]: https://github.com/ontodev/robot/pull/792
[#788]: https://github.com/ontodev/robot/pull/788
[#783]: https://github.com/ontodev/robot/pull/783
[#779]: https://github.com/ontodev/robot/pull/779
[#774]: https://github.com/ontodev/robot/pull/774
[#767]: https://github.com/ontodev/robot/pull/767
[#758]: https://github.com/ontodev/robot/pull/758
[#741]: https://github.com/ontodev/robot/issues/741
[#739]: https://github.com/ontodev/robot/pull/739
[#738]: https://github.com/ontodev/robot/pull/738
[#730]: https://github.com/ontodev/robot/pull/730
[#728]: https://github.com/ontodev/robot/pull/728
[#727]: https://github.com/ontodev/robot/pull/727
[#726]: https://github.com/ontodev/robot/pull/726
[#722]: https://github.com/ontodev/robot/pull/722
[#719]: https://github.com/ontodev/robot/pull/716
[#715]: https://github.com/ontodev/robot/pull/715
[#713]: https://github.com/ontodev/robot/pull/713
[#710]: https://github.com/ontodev/robot/pull/710
[#709]: https://github.com/ontodev/robot/issues/709
[#703]: https://github.com/ontodev/robot/pull/703
[#699]: https://github.com/ontodev/robot/pull/699
[#691]: https://github.com/ontodev/robot/pull/691
[#689]: https://github.com/ontodev/robot/pull/689
[#685]: https://github.com/ontodev/robot/pull/685
[#671]: https://github.com/ontodev/robot/pull/671
[#666]: https://github.com/ontodev/robot/pull/666
[#659]: https://github.com/ontodev/robot/issues/659
[#657]: https://github.com/ontodev/robot/pull/657
[#654]: https://github.com/ontodev/robot/issues/654
[#646]: https://github.com/ontodev/robot/issues/646
[#645]: https://github.com/ontodev/robot/issues/645
[#644]: https://github.com/ontodev/robot/issues/644
[#630]: https://github.com/ontodev/robot/issues/630
[#625]: https://github.com/ontodev/robot/issues/625
[#619]: https://github.com/ontodev/robot/issues/619
[#617]: https://github.com/ontodev/robot/issues/617
[#608]: https://github.com/ontodev/robot/issues/608
[#606]: https://github.com/ontodev/robot/issues/606
[#603]: https://github.com/ontodev/robot/issues/603
[#597]: https://github.com/ontodev/robot/issues/597
[#584]: https://github.com/ontodev/robot/issues/584
[#579]: https://github.com/ontodev/robot/issues/579
[#578]: https://github.com/ontodev/robot/issues/579
[#574]: https://github.com/ontodev/robot/issues/574
[#570]: https://github.com/ontodev/robot/issues/570
[#567]: https://github.com/ontodev/robot/issues/567
[#564]: https://github.com/ontodev/robot/issues/564
[#560]: https://github.com/ontodev/robot/issues/560
[#559]: https://github.com/ontodev/robot/issues/559
[#558]: https://github.com/ontodev/robot/issues/558
[#555]: https://github.com/ontodev/robot/issues/555
[#547]: https://github.com/ontodev/robot/issues/547
[#546]: https://github.com/ontodev/robot/issues/546
[#537]: https://github.com/ontodev/robot/issues/537
[#523]: https://github.com/ontodev/robot/issues/523
[#510]: https://github.com/ontodev/robot/issues/510
[#507]: https://github.com/ontodev/robot/issues/507
[#488]: https://github.com/ontodev/robot/issues/488
[#484]: https://github.com/ontodev/robot/issues/484
[#481]: https://github.com/ontodev/robot/issues/481
[#475]: https://github.com/ontodev/robot/issues/475
[#471]: https://github.com/ontodev/robot/issues/471
[#461]: https://github.com/ontodev/robot/issues/461
[#455]: https://github.com/ontodev/robot/issues/455
[#452]: https://github.com/ontodev/robot/issues/452
[#448]: https://github.com/ontodev/robot/issues/448
[#441]: https://github.com/ontodev/robot/issues/441
[#438]: https://github.com/ontodev/robot/issues/438
[#427]: https://github.com/ontodev/robot/issues/427
[#419]: https://github.com/ontodev/robot/issues/419
[#403]: https://github.com/ontodev/robot/issues/403
[#392]: https://github.com/ontodev/robot/issues/392
[#385]: https://github.com/ontodev/robot/issues/385
[#374]: https://github.com/ontodev/robot/issues/374
[#371]: https://github.com/ontodev/robot/issues/371
[#363]: https://github.com/ontodev/robot/issues/363
[#352]: https://github.com/ontodev/robot/issues/352
[#319]: https://github.com/ontodev/robot/issues/319
[#314]: https://github.com/ontodev/robot/issues/314
[#277]: https://github.com/ontodev/robot/issues/277
[#275]: https://github.com/ontodev/robot/issues/275
[#274]: https://github.com/ontodev/robot/issues/274
[#246]: https://github.com/ontodev/robot/issues/246
[#174]: https://github.com/ontodev/robot/issues/174
[#158]: https://github.com/ontodev/robot/issues/158
