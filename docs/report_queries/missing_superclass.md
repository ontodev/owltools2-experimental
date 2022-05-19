# Missing Superclass

**Problem:** A class does not have a superclass. This is not relevant for top-level classes, but may reveal orphaned children. Excludes deprecated entities.

**Solution:** Make sure there are no orphaned children - if so, assert a parent.

```sparql
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT DISTINCT ?entity ?property ?value WHERE {
  VALUES ?property { rdfs:subClassOf }
  ?entity a owl:Class .
  FILTER (!isBlank(?entity)) .
  FILTER NOT EXISTS { ?entity ?property ?value }
  FILTER NOT EXISTS { ?entity owl:deprecated true }
  FILTER EXISTS {
    ?entity ?prop2 ?object .
    FILTER (?prop2 != rdf:type)
    FILTER (?prop2 != owl:equivalentClass)
    FILTER (?prop2 != owl:disjointWith)
    FILTER (?prop2 != owl:equivalentProperty)
    FILTER (?prop2 != owl:sameAs)
    FILTER (?prop2 != owl:differentFrom)
    FILTER (?prop2 != owl:inverseOf)
  }
}
ORDER BY ?entity
```
