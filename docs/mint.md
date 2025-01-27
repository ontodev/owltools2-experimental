# Mint

ROBOT can mint sequential numeric identifiers for temporary IRIS. For example, in a decentralized development workflow,
curators may submit pull requests creating new terms using UUID-based identifiers, such as:

- `http://purl.obolibrary.org/temp#65969F4B-4050-4AB9-9EE4-6E136A7A335F`
- `http://purl.obolibrary.org/temp#829D9A75-572B-4F79-9A52-F74F53FDD687`

To replace occurrences of these IRIs with IRIs following a given ontology identifier scheme, you can run a command like this:

    robot mint \
      --input needs-minting.owl \
      --output results/minted.owl
      --minted-id-prefix "http://purl.obolibrary.org/obo/EXAMPLE_"
      --temp-id-prefix "http://purl.obolibrary.org/temp#"
      --minted-from-property "http://purl.obolibrary.org/obo/OMO_mintedfrom"

If, for example, the ontology already contains a term with IRI `http://purl.obolibrary.org/obo/EXAMPLE_0000004`,
this will result in all occurrences of the above IRIs being replaced with these corresponding IRIs:

- `http://purl.obolibrary.org/obo/EXAMPLE_0000005`
- `http://purl.obolibrary.org/obo/EXAMPLE_0000006`

`mint` provides some additional options to modify identifier generation:

- `--pad-width`: apply leading zeroes to minted identifiers up to this width (default `7`)
- `--min-id`: start minted identifiers from the max of either this number or the highest identifier found which is less than or equal to `max-id` (default `0`)
- `--max-id`: fail the operation if no identifier can be minted less than or equal to this number (default `Integer.MAX_VALUE`)
- `--keep-deprecated `: whether to keep temporary terms in the ontology as deprecated entities (default `false`).
  Deprecated temporary entities are linked to the minted replacement term with a [term_replaced_by](http://purl.obolibrary.org/obo/IAO_0100001) annotation.
