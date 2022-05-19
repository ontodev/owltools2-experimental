# Duplicate Label

**Problem:** Two different subjects have been assigned the same label. This causes ambiguity.

**OBO Foundry Principle:** [12 - Naming Conventions](http://www.obofoundry.org/principles/fp-012-naming-conventions.html)

**Solution:** Avoid ambiguity by assigning distinct labels to each subject.

```sparql
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT DISTINCT ?entity ?property ?value WHERE {
  VALUES ?property {rdfs:label}
  ?entity ?property ?value .
  ?entity2 ?property ?value .
  FILTER (?entity != ?entity2)
  FILTER (!isBlank(?entity))
  FILTER (!isBlank(?entity2))
  FILTER NOT EXISTS { ?entity owl:deprecated true .
                      ?entity2 owl:deprecated true }
}
ORDER BY DESC(UCASE(str(?value)))
```
