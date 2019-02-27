# Annotation Whitespace

**Problem:** An annotation has leading or trailing whitespace, usually a single space.

**Solution:** Re-format the annotation to remove additional whitespace.

```sparql
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT DISTINCT ?entity ?property ?value WHERE {
 {?property a owl:AnnotationProperty .
  ?entity ?property ?value .
  FILTER STRENDS(str(?value), " ")}
 UNION
 {?property a owl:AnnotationProperty .
  ?entity ?property ?value .
  FILTER STRSTARTS(str(?value), " ")}
}
ORDER BY ?entity
```
