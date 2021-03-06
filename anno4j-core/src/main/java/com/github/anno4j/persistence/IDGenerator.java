package com.github.anno4j.persistence;

import org.openrdf.model.Resource;

/**
 * Interface for IDGenerators. IDGenerators provide a method to generate a unique ressource for internal RDF nodes.
 */
public interface IDGenerator {

    /**
     * Generates a unique resource.
     * @return a Resource containing a unique identifier.
     */
    Resource generateID();
}
