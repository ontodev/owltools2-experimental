# Label Formatting

**Problem:** Formatting characters are used in a label. This may cause issues when trying to reference the entity by label.

**Solution:** Remove formatting characters from label.

```sparql
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT DISTINCT ?entity ?property ?value WHERE {
 {
  VALUES ?property {rdfs:label}
  ?entity ?property ?value .
  FILTER regex(?value, "\n")
 }
 UNION
 {
  VALUES ?property {rdfs:label}
  ?entity ?property ?value .
  FILTER regex(?value, "\t")
 }
}
ORDER BY ?entity
```
