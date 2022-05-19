# Invalid Entity URI

**Problem:** An entity's URI is not formatted correctly. OBO entities are formatted http://purl.obolibrary.org/obo/IDSPACE_0000000. This format is assumed by many OBO tools. Often, accidental typos cause an entity to be malformed, which can cause problems for tools that deal with OBO ontologies.

**Solution:** Fix the entity OBO URI.

```
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

SELECT DISTINCT ?entity ?property ?value WHERE {
  ?entity rdf:type ?value .
  FILTER (!isBlank(?entity))
  FILTER(
    STRSTARTS(str(?entity),"https://purl.obolibrary.org/obo/") ||
    STRSTARTS(str(?entity),"http://purl.org/obo/") ||
    STRSTARTS(str(?entity),"http://www.obofoundry.org/") ||
    (STRSTARTS(str(?entity),"https://purl.obolibrary.org/") && regex(str(?entity),"http[:][/][/]purl[.]obolibrary[.]org[/][^o][^b][^o]"))
  )
}
ORDER BY ?entity
```
