# Search Service

This service handles search traffic and is the service
you're most directly interacting with when visiting
[search.marginalia.nu](https://search.marginalia.nu). 

## Central classes

* [SearchService](src/main/java/nu/marginalia/search/SearchService.java) receives REST requests and delegates to the 
appropriate services.

* [CommandEvaluator](src/main/java/nu/marginalia/search/command/CommandEvaluator.java) interprets a search query and acts
upon it, dealing with special operations like `browse:` or `site:`.

* [SearchQueryIndexService](src/main/java/nu/marginalia/search/svc/SearchQueryIndexService.java) parses a search query, passes it to the index service, and
then decorates the search results so that they can be rendered.
