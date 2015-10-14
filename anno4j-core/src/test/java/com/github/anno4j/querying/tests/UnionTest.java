package com.github.anno4j.querying.tests;

import com.github.anno4j.Anno4j;
import com.github.anno4j.model.Annotation;
import com.github.anno4j.model.Body;
import com.github.anno4j.querying.QueryService;
import org.apache.marmotta.ldpath.parser.ParseException;
import org.junit.Before;
import org.junit.Test;
import org.openrdf.annotations.Iri;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.config.RepositoryConfigException;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by schlegel on 09/10/15.
 */
public class UnionTest {

    private QueryService queryService = null;
    private Anno4j anno4j;

    @Before
    public void resetQueryService() throws RepositoryException, RepositoryConfigException {
        this.anno4j = new Anno4j();
        queryService = anno4j.createQueryService();
        queryService.addPrefix("ex", "http://www.example.com/schema#");
    }

    @Test
    public void testUnionBody() throws RepositoryException, QueryEvaluationException, MalformedQueryException, ParseException, InstantiationException, IllegalAccessException {
        // Persisting some data
        Annotation annotation =  anno4j.createObject(Annotation.class);
        annotation.setSerializedAt("07.05.2015");
        UnionTestBody1 unionTestBody1 = anno4j.createObject(UnionTestBody1.class);
        unionTestBody1.setValue("Value1");
        annotation.setBody(unionTestBody1);
        anno4j.createPersistenceService().persistAnnotation(annotation);

        Annotation annotation1 =  anno4j.createObject(Annotation.class);
        annotation1.setAnnotatedAt("01.01.2011");
        UnionTestBody2 unionTestBody2 = anno4j.createObject(UnionTestBody2.class);
        unionTestBody2.setValue("Value2");
        annotation1.setBody(unionTestBody2);
        anno4j.createPersistenceService().persistAnnotation(annotation1);

        List<Annotation> annotations = queryService
                .addCriteria("oa:hasBody[is-a ex:unionBody1] | oa:hasBody[is-a ex:unionBody2]")
                .execute();
        
        assertEquals(2, annotations.size());
    }

    @Iri("http://www.example.com/schema#unionBody1")
    public static interface UnionTestBody1 extends Body {

        @Iri("http://www.example.com/schema#value")
        String getValue();

        @Iri("http://www.example.com/schema#value")
        void setValue(String value);
    }

    @Iri("http://www.example.com/schema#unionBody2")
    public static interface UnionTestBody2 extends Body {
        @Iri("http://www.example.com/schema#value")
        String getValue();

        @Iri("http://www.example.com/schema#value")
        void setValue(String value);
    }
}