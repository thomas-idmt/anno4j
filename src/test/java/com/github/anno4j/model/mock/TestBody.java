package com.github.anno4j.model.mock;

import com.github.anno4j.model.Body;
import org.openrdf.annotations.Iri;
import org.openrdf.repository.object.LangString;

/**
 * Created by schlegel on 06/05/15.
 */
@Iri("http://www.example.com/schema#bodyType")
public class TestBody extends Body {

    public TestBody() {
    }

    @Iri("http://www.example.com/schema#value")
    private String value;

    @Iri("http://www.example.com/schema#langValue")
    private LangString langValue;

    public LangString getLangValue() {
        return langValue;
    }

    public void setLangValue(LangString langValue) {
        this.langValue = langValue;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "TestBody{" +
                "value='" + value + '\'' +
                '}';
    }
}
