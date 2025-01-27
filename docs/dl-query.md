# DL-Query

## Contents

1. [Overview](#overview)
2. [Query types](#query-types)

## Overview

ROBOT can execute DL queries against an ontology. The functionality closely mimics the functionality of the <a href="https://protegewiki.stanford.edu/wiki/DLQueryTab" target="_blank">DL Query Tab</a> in Protege.

The `dl-query` command can be used to query for ancestors, descendants, instances and other relatives of an OWL Class Expression that is provided in Manchester syntax.

The output is always a list of Entity IRIs. Multiple queries and output files can be supplied. For example:

    robot query --input uberon_module.owl \
      --query "'part_of' some 'subdivision of trunk'" part_of_subdiv_trunk.txt \
      --query "'part_of' some 'nervous system'" part_of_nervous_system.txt

## Query Types

The following query types are currently supported:

- equivalents: Classes that are exactly equivalent to the supplied class expression
- parents: Direct parents (superclasses) of the class expression provided
- children: Direct children (subclasses) of the class expression provided
- descendants (default): All subclasses of the class expression provided
- ancestors: All superclasses of the class expression provided
- instances: All named individuals that are instances of the class expression provided
