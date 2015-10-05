package com.github.anno4j.querying.tests;

import com.github.anno4j.Anno4j;
import com.github.anno4j.model.Annotation;
import com.github.anno4j.model.Body;
import com.github.anno4j.querying.QueryService;
import org.apache.marmotta.ldpath.parser.ParseException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openrdf.annotations.Iri;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.config.RepositoryConfigException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.memory.MemoryStore;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 *  Containing all tests with recursive path expressions.
 */
public class RecursivePathTest {

    private QueryService queryService = null;
    private Anno4j anno4j;

    @Before
    public void resetQueryService() throws RepositoryConfigException, RepositoryException {
        this.anno4j = new Anno4j();
        queryService = anno4j.createQueryService();
        queryService.addPrefix("ex", "http://www.example.com/schema#");
    }

    @BeforeClass
    public void setUp() throws RepositoryException, RepositoryConfigException, InstantiationException, IllegalAccessException {
        // getting a new respository instance for the following tests
        SailRepository repository = new SailRepository(new MemoryStore());
        repository.initialize();
        anno4j.setRepository(repository);


        // Persisting some data
        Annotation annotation = anno4j.createObject(Annotation.class);
        annotation.setSerializedAt("07.05.2015");
        RecursiveBody recursiveBody = anno4j.createObject(RecursiveBody.class);
        recursiveBody.setValue("Some Testing Value");
        annotation.setBody(recursiveBody);
        anno4j.createPersistenceService().persistAnnotation(annotation);

        Annotation annotation1 = anno4j.createObject(Annotation.class);
        annotation1.setAnnotatedAt("01.01.2011");
        RecursiveBody recursiveBody2 = anno4j.createObject(RecursiveBody.class);
        recursiveBody2.setValue("Another Testing Value");
        annotation1.setBody(recursiveBody2);
        anno4j.createPersistenceService().persistAnnotation(annotation1);
    }


    @Test
    /**
     * Test method for OneOrMorePath
     *
     * @see <a href="http://www.w3.org/TR/sparql11-query/#pp-language">http://www.w3.org/TR/sparql11-query/#pp-language</a>
     */
    public void oneOrMoreTest() throws RepositoryException, QueryEvaluationException, MalformedQueryException, ParseException, RepositoryConfigException {
        List<Annotation> annotations = queryService
                .setAnnotationCriteria("(oa:hasTarget)+")
                .execute();
        assertEquals(0, annotations.size());

        resetQueryService();

        annotations = queryService
                .setAnnotationCriteria("(oa:hasBody)+")
                .setBodyCriteria("ex:recursiveBodyValue", "Another Testing Value")
                .execute();
        assertEquals(1, annotations.size());
        assertEquals("Another Testing Value", ((RecursiveBody) annotations.get(0).getBody()).getValue());
    }

    @Test
    /**
     * Test method for ZeroOrMorePath.
     *
     * @see <a href="http://www.w3.org/TR/sparql11-query/#pp-language">http://www.w3.org/TR/sparql11-query/#pp-language</a>
     */
    public void zeroOrMoreTest() throws RepositoryException, QueryEvaluationException, MalformedQueryException, ParseException, RepositoryConfigException {
        List<Annotation> annotations = queryService.setAnnotationCriteria("(oa:hasBody/ex:recursiveBodyValue)*", "Some Testing Value").execute();
        assertEquals(1, annotations.size());
        assertEquals("Some Testing Value", ((RecursiveBody) annotations.get(0).getBody()).getValue());

        resetQueryService();

        annotations = queryService.setAnnotationCriteria("(oa:hasTarget)*").execute();
        assertEquals(2, annotations.size());
    }

    @Iri("http://www.example.com/schema#recursiveBody")
    public static interface RecursiveBody extends Body {
        @Iri("http://www.example.com/schema#recursiveBodyValue")
        String getValue();

        @Iri("http://www.example.com/schema#recursiveBodyValue")
        void setValue(String value);
    }
}
