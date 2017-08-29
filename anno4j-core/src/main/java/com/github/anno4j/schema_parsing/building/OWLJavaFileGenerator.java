package com.github.anno4j.schema_parsing.building;

import com.github.anno4j.Anno4j;
import com.github.anno4j.model.impl.ResourceObject;
import com.github.anno4j.model.namespaces.OWL;
import com.github.anno4j.model.namespaces.RDF;
import com.github.anno4j.model.namespaces.RDFS;
import com.github.anno4j.model.namespaces.XSD;
import com.github.anno4j.schema.model.rdfs.RDFSClazz;
import com.github.anno4j.schema.model.rdfs.RDFSProperty;
import com.github.anno4j.schema_parsing.generation.JavaFileGenerator;
import com.github.anno4j.schema_parsing.model.BuildableRDFSClazz;
import com.github.anno4j.schema_parsing.model.BuildableRDFSProperty;
import com.github.anno4j.schema_parsing.util.StronglyConnectedComponents;
import com.github.anno4j.util.JenaSesameUtils;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.reasoner.ValidityReport;
import com.squareup.javapoet.JavaFile;
import org.mindswap.pellet.jena.PelletReasonerFactory;
import org.openrdf.model.Resource;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.UpdateExecutionException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.config.RepositoryConfigException;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;

/**
 * This class takes an RDFS or OWL DL ontology and generates Java files that can afterwards be used
 * with Anno4j.
 * RDF ontology information can be added by calls to {@code addRDF} methods.
 * Afterwards Java files can be generated by a call to {@link #generateJavaFiles(OntGenerationConfig, File)}
 * providing an appropriate configuration (see {@link OntGenerationConfig}).
 * All implicit information about the ontology is inferred before the files are generated.
 *<br><br>
 * Example:<br>
 *
 * <code>
 *     OntGenerationConfig config = new OntGenerationConfig();
 *     // Adopt your config here...
 *
 *     JavaFileGenerator generator = new OWLJavaFileGenerator();
 *     generator.addRDF("http://example.de/my_ontology.rdf.xml");
 *
 *     File outputDir = new File("out");
 *     generator.generateJavaFiles(config, outputDir);
 * </code>
 */
public class OWLJavaFileGenerator implements OntologyModelBuilder, JavaFileGenerator {

    private final OntModel model;

    /**
     * The Anno4j instance where RDFS information is persisted to.
     */
    private final Anno4j anno4j;

    /**
     * The logger used for printing progress.
     */
    private final Logger logger = LoggerFactory.getLogger(OWLJavaFileGenerator.class);

    /**
     * Initializes the generator without a connection to an external repository,
     * i.e. no ontology information is persisted.
     * @throws RepositoryConfigException Thrown if the internal repository is not correctly configured.
     * @throws RepositoryException Thrown if an error occurs regarding the setup of the internal repository.
     */
    public OWLJavaFileGenerator() throws RepositoryConfigException, RepositoryException {
        this(new Anno4j());
    }

    /**
     * Initializes the generator with an Anno4j instance which connected repository
     * will receive the ontology information (including inferred statements) after a call
     * to {@link #build()} or {@link #generateJavaFiles(OntGenerationConfig, File)}.
     * @param anno4j The Anno4j instance that will receive ontology information.
     */
    public OWLJavaFileGenerator(Anno4j anno4j) {
        this.anno4j = anno4j;

        model = ModelFactory.createOntologyModel(PelletReasonerFactory.THE_SPEC);
    }

    /**
     * Adds RDF statements to the model.
     * A subsequent call to {@link #build()} is required for committing the data to the model.
     *
     * @param rdfInput An input stream to RDF/XML data.
     * @param format     The base uri to be used when converting relative URI's to absolute URI's.
     */
    @Override
    public void addRDF(InputStream rdfInput, String format) {
        model.read(rdfInput, format);
    }

    /**
     * Adds RDF statements to the model.
     * A subsequent call to {@link #build()} is required for committing the data to the model.
     *
     * @param url  An URL to RDF data in RDF/XML format.
     * @param format The base uri to be used when converting relative URI's to absolute URI's.
     */
    @Override
    public void addRDF(String url, String format) {
        model.read(url, format);
    }

    /**
     * Adds RDF statements to the model.
     * A subsequent call to {@link #build()} is required for committing the data to the model.
     *
     * @param rdfInput An input stream to the RDF data. Its format is defined by the <code>format</code> parameter.
     * @param base     The base uri to be used when converting relative URI's to absolute URI's.
     * @param format   The format of the RDF data. One of "RDF/XML", "N-TRIPLE", "TURTLE" (or "TTL") and "N3"
     */
    @Override
    public void addRDF(InputStream rdfInput, String base, String format) {
        model.read(rdfInput, base, format);
    }

    /**
     * Adds RDF statements to the model.
     * A subsequent call to {@link #build()} is required for committing the data to the model.
     *
     * @param url    An URL to RDF data in the specified format.
     * @param base   The base uri to be used when converting relative URI's to absolute URI's.
     * @param format The format of the RDF data. One of "RDF/XML", "N-TRIPLE", "TURTLE" (or "TTL") and "N3"
     */
    @Override
    public void addRDF(String url, String base, String format) {
        model.read(url, base, format);
    }

    /**
     * Adds RDF statements to the underlying model.
     *
     * @param url URL to a RDF/XML file containing the RDF data to be added.
     */
    @Override
    public void addRDF(String url) {
        model.read(url);
    }

    /**
     * Returns the buildable named resource objects of RDFS classes that were found during
     * the last call to {@link #build()}.
     *
     * @return Returns the RDFS classes in the model built.
     */
    @Override
    public Collection<BuildableRDFSClazz> getClazzes() throws RepositoryException {
        try {
            ObjectConnection connection = anno4j.getObjectRepository().getConnection();
            ObjectQuery query = connection.prepareObjectQuery(
                    "SELECT DISTINCT ?c {" +
                            "   ?c rdfs:subClassOf+ owl:Thing . " +
                            "   FILTER( isIRI(?c) )" +
                            "}"
            );

            return query.evaluate(BuildableRDFSClazz.class).asSet();

        } catch (MalformedQueryException | QueryEvaluationException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * Returns buildable named resource objects of RDFS classes that were found during
     * the last call to {@link #build()} which are pairwise distinct,
     * i.e. that are not declared equivalent.
     * @return All pairwise distinct named classes in the repository.
     * @throws RepositoryException Thrown if an error occurs while querying the repository.
     */
    private Collection<BuildableRDFSClazz> getDistinctClasses() throws RepositoryException {
        try {
            ObjectConnection connection = anno4j.getObjectRepository().getConnection();
            ObjectQuery query = connection.prepareObjectQuery(
                    "SELECT DISTINCT ?c {\n" +
                    "   ?c rdfs:subClassOf+ owl:Thing . \n" +
                    "   MINUS {\n" +
                    "       ?e owl:equivalentClass ?c . \n" +
                    "       FILTER(str(?e) < str(?c))\n" + // Impose order on equivalence. Pick only first lexicographical
                    "   }\n" +
                    "   FILTER( isIRI(?c) )\n" +
                    "}"
            );

            return query.evaluate(BuildableRDFSClazz.class).asSet();

        } catch (MalformedQueryException | QueryEvaluationException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * Returns the extended resource objects of RDFS properties that were found during
     * the last call to {@link #build()}.
     *
     * @return Returns the RDFS properties in the model built.
     */
    @Override
    public Collection<BuildableRDFSProperty> getProperties() throws RepositoryException {
        return anno4j.findAll(BuildableRDFSProperty.class);
    }

    /**
     * Returns a validity report for the model build during the last call of {@link #build()}.
     *
     * @return The validity report for the model. Use {@link ValidityReport#isValid()} to
     * check if the model built is valid.
     * @throws IllegalStateException Thrown if the model was not previously built.
     */
    @Override
    public ValidityReport validate() {
        return model.validate();
    }

    /**
     * Builds an ontology model for the RDF data added before using <code>addRDF</code> methods.
     * After a call to this method, the classes and properties in the model can be queried
     * using {@link #getClazzes()} and {@link #getProperties()} respectively.
     *
     * @throws RDFSModelBuildingException Thrown if an error occurs during building the model.
     */
    @Override
    public void build() throws RDFSModelBuildingException {
        // Validate the model constructed so far:
        if(!model.validate().isValid()) {
            throw new RDFSModelBuildingException("The model is not valid.");
        }

        // Copy statements from model to Anno4j:
        try {
            anno4j.createObject(RDFSProperty.class, (Resource) new URIImpl(RDF.TYPE));
            anno4j.createObject(RDFSProperty.class, (Resource) new URIImpl(RDFS.LABEL));
            anno4j.createObject(RDFSProperty.class, (Resource) new URIImpl(RDFS.COMMENT));
            anno4j.createObject(RDFSProperty.class, (Resource) new URIImpl(RDFS.RANGE));
            anno4j.createObject(RDFSProperty.class, (Resource) new URIImpl(RDFS.DOMAIN));
            anno4j.createObject(RDFSProperty.class, (Resource) new URIImpl(RDFS.SUB_CLASS_OF));
            anno4j.createObject(RDFSProperty.class, (Resource) new URIImpl(OWL.EQUIVALENT_CLASS));
            anno4j.createObject(RDFSProperty.class, (Resource) new URIImpl(OWL.DISJOINT_WITH));
            anno4j.createObject(RDFSProperty.class, (Resource) new URIImpl(OWL.COMPLEMENT_OF));
            anno4j.createObject(RDFSClazz.class, (Resource) new URIImpl(OWL.THING));
            anno4j.createObject(RDFSClazz.class, (Resource) new URIImpl(RDFS.LITERAL));
            anno4j.createObject(RDFSClazz.class, (Resource) new URIImpl(RDFS.DATATYPE));
            anno4j.createObject(RDFSClazz.class, (Resource) new URIImpl(OWL.NOTHING));
            anno4j.createObject(RDFSClazz.class, (Resource) new URIImpl(OWL.CLAZZ));


            logger.debug("Inferring statements using " + model.getReasoner().getClass().getName() + ". This may take a while...");
            int statementTransferCount = 0;
            StmtIterator statementIter = model.listStatements();
            while (statementIter.hasNext()) {
                Statement jenaStatement = statementIter.nextStatement();
                anno4j.getObjectRepository()
                        .getConnection()
                        .add(JenaSesameUtils.asSesameStatement(jenaStatement));
                statementTransferCount++;
            }
            logger.debug(statementTransferCount + " statements inferred");

            anno4j.getObjectRepository().getConnection().prepareUpdate(
                    "INSERT {" +
                            "  ?c a rdfs:Class . " +
                            "} WHERE {" +
                            "   { ?p rdfs:domain ?c . } UNION { ?p rdfs:range ?c . } UNION {?c2 rdfs:subClassOf ?c . } UNION {?c rdfs:subClassOf ?c2 . }" +
                            "   UNION { ?c rdfs:subClassOf+ owl:Thing . }" +
                            "}"
            ).execute();
            anno4j.getObjectRepository().getConnection().prepareUpdate(
                    "INSERT {" +
                            "  ?p a rdf:Property . " +
                            "} WHERE {" +
                            "   { ?p a owl:DatatypeProperty . } UNION { ?p a owl:ObjectProperty . } " +
                            "   UNION { ?inv owl:inverseOf ?p . } UNION { ?p owl:inverseOf ?inv . } " +
                            "   UNION { ?p rdfs:subPropertyOf ?super . } UNION { ?sub rdfs:subPropertyOf ?p . }" +
                            "}"
            ).execute();
            // Infer the domain/range of properties that have an inverse property:
            anno4j.getObjectRepository().getConnection().prepareUpdate(
                    "INSERT {" +
                            "   ?p rdfs:domain ?v . " +
                            "} WHERE {" +
                            "   ?p a rdf:Property . " + // ?p is a Property without domain:
                            "   MINUS {" +
                            "      ?p rdfs:domain ?v2 . " +
                            "   }" +
                            "   {" + // There exists an inverse ?i:
                            "      ?p owl:inverseOf ?i . " +
                            "   } UNION {" +
                            "      ?i owl:inverseOf ?p . " +
                            "   }" +
                            "   ?i rdfs:range ?v . " + // ?i has a range, i.e. the domain of ?p
                            "}"
            ).execute();
            anno4j.getObjectRepository().getConnection().prepareUpdate(
                    "INSERT {" +
                            "   ?p rdfs:range ?v . " +
                            "} WHERE {" +
                            "   ?p a rdf:Property . " + // ?p is a Property without range:
                            "   MINUS {" +
                            "      ?p rdfs:range ?v2 . " +
                            "   }" +
                            "   {" + // There exists an inverse ?i:
                            "      ?p owl:inverseOf ?i . " +
                            "   } UNION {" +
                            "      ?i owl:inverseOf ?p . " +
                            "   }" +
                            "   ?i rdfs:domain ?v . " + // ?i has a domain, i.e. the range of ?p
                            "}"
            ).execute();
            // Properties without asserted domain/range inherit it from their superproperties:
            anno4j.getObjectRepository().getConnection().prepareUpdate(
                    "INSERT {" +
                            "  ?p ?t ?v . " +
                            "} WHERE { " +
                            "   VALUES ?t { rdfs:domain rdfs:range } " +
                            "   ?p a rdf:Property . " + // Look for properties without ?t value.
                            "   MINUS {" +
                            "       ?p ?t ?vp . " +
                            "   }" +
                            "   ?p rdfs:subPropertyOf+ ?m . " + // Select the value of superproperties.
                            "   ?m ?t ?v . " +
                            "   MINUS {" + // Remove properties that have a subproperty with a ?t value. (Ensures most specific superprop is taken)
                            "       ?m2 rdfs:subPropertyOf+ ?m . " +
                            "       ?m2 ?t ?v2 . " +
                            "       FILTER( ?m2 != ?p && ?m2 != ?m )" +
                            "   }" +
                            "}"
            ).execute();
            // Set owl:Thing as the domain of all properties which have none specified:
            anno4j.getObjectRepository().getConnection().prepareUpdate(
                    "INSERT {" +
                            "   ?p rdfs:domain owl:Thing . " +
                            "} WHERE {" +
                            "   ?p a rdf:Property . " +
                            "   FILTER NOT EXISTS {" +
                            "      ?p rdfs:domain ?c . " +
                            "   }" +
                            "}"
            ).execute();
            // Set rdfs:Literal as a superclass of datatype property ranges:
            anno4j.getObjectRepository().getConnection().prepareUpdate(
                    "INSERT {" +
                            "   ?o rdfs:subClassOf rdfs:Literal . " +
                            "   ?o a rdfs:Class . " +
                            "} WHERE {" +
                            "   ?p a owl:DatatypeProperty . " +
                            "   ?p rdfs:range ?o . " +
                            "}"
            ).execute();

            normalizeRDFSEquivalence();

        } catch (RepositoryException | IllegalAccessException | InstantiationException | MalformedQueryException | UpdateExecutionException e) {
            throw new RDFSModelBuildingException(e);
        }
    }

    private void normalizeRDFSEquivalence() throws RepositoryException {
        int normalizationCount = 0; // Number of SCCs found
        for(Collection<RDFSClazz> scc : StronglyConnectedComponents.findSCCs(anno4j.findAll(RDFSClazz.class))) {
            if(scc.size() > 1) {
                Iterator<RDFSClazz> sccIterator = scc.iterator();
                RDFSClazz root = sccIterator.next(); // Keep the first class, so skip it
                while (sccIterator.hasNext()) {
                    RDFSClazz clazz = sccIterator.next();
                    ObjectConnection connection = anno4j.getObjectRepository().getConnection();

                    try {
                        // Copy statements where the equivalent class is the subject:
                        connection.prepareUpdate(
                        "INSERT {" +
                                "   <" + root.getResourceAsString() + "> ?p ?o ." +
                                "} WHERE {" +
                                "   <" + clazz.getResourceAsString() + "> ?p ?o ." +
                                "}"
                        ).execute();
                        // Copy statements where the equivalent class is the object:
                        connection.prepareUpdate(
                        "INSERT {" +
                                "   ?s ?p <" + root.getResourceAsString() + "> ." +
                                "} WHERE {" +
                                "   ?s ?p <" + clazz.getResourceAsString() + "> ." +
                                "}"
                        ).execute();


                        // Remove the statements involving the class copied from:
                        connection.prepareUpdate(
                                "DELETE WHERE {" +
                                "   <" + clazz.getResourceAsString() +"> ?p ?o ." +
                                "}"
                        ).execute();
                        connection.prepareUpdate(
                        "DELETE WHERE {" +
                                "   ?s ?p <" + clazz.getResourceAsString() +"> ." +
                                "}"
                        ).execute();

                    } catch (UpdateExecutionException | MalformedQueryException e) {
                        throw new RepositoryException(e);
                    }
                }

                normalizationCount++;
            }
        }
        logger.debug("Found and reduced " + normalizationCount + " rdfs:subClassOf SCCs...");
    }

    /**
     * Checks if the resource is from a standard vocabulary, e.g. RDF or RDFS.
     *
     * @param resource The resource to check.
     * @return Whether the resource is from a special vocabulary.
     */
    private boolean isFromSpecialVocabulary(ResourceObject resource) {
        return resource.getResourceAsString().startsWith(RDF.NS)
                || resource.getResourceAsString().startsWith(RDFS.NS)
                || resource.getResourceAsString().startsWith(XSD.NS)
                || resource.getResourceAsString().startsWith(OWL.NS);
    }

    /**
     * A base package can be specified by {@link OntGenerationConfig} which
     * may overlap with the users output directory specified.
     * Returns the directory which contains the longest base package directory structure prefix.
     * @param directory The directory for which the base package root should be found.
     * @param config The configuration specifying the base package.
     * @return Returns the directory which contains the topmost base package or {@code directory}
     * if there is no overlap.
     */
    private File getAbsoluteOutputDirectory(File directory, OntGenerationConfig config) {
        if(!config.getBasePackage().isEmpty()) {
            // Work on the input directory with trailing slash:
            String hook = directory.getAbsolutePath();
            if(!hook.endsWith(File.separator)) {
                hook += File.separator;
            }

            // Check for maximum length match of base package at the end of the input directory:
            String[] basePackage = config.getBasePackage().split("\\.");
            for (int i = basePackage.length - 1; i >= 0; i--) {
                // Transform the current base package portion to a filesystem path part:
                StringBuilder portion = new StringBuilder();
                for (int j = 0; j <= i; j++) {
                    portion.append(basePackage[j]).append(File.separator);
                }

                // Check if the input directory ends with this specific portion:
                if(hook.endsWith(portion.toString())) {
                    return new File(hook.substring(0, hook.lastIndexOf(portion.toString())));
                }
            }
        }

        // If the base package doesn't overlap:
        return directory;
    }

    @Override
    public ObjectConnection getConnection() throws RepositoryException {
        return anno4j.getObjectRepository().getConnection();
    }


    @Override
    public void generateJavaFiles(OntGenerationConfig config, File outputDirectory) throws JavaFileGenerationException, IOException, RepositoryException {
        // Check if the output directory is actually a directory:
        if (!outputDirectory.exists()) {
            // Try to create it:
            if (!outputDirectory.mkdirs()) {
                throw new JavaFileGenerationException("The output directory " + outputDirectory.getAbsolutePath() + " could not be created.");
            }
        } else if (outputDirectory.isFile()) {
            throw new JavaFileGenerationException(outputDirectory.getAbsolutePath() + " must be a directory.");
        }

        // Process the model:
        logger.debug("Building ontology model...");
        try {
            build();
        } catch (RDFSModelBuildingException e) {
            throw new JavaFileGenerationException(e);
        }

        // Check if the model is valid:
        if(!validate().isValid()) {
            throw new JavaFileGenerationException("The built model is invalid!");
        }

        // Get the actual output directory depending on the base package set:
        outputDirectory = getAbsoluteOutputDirectory(outputDirectory, config);
        logger.debug("Generated files will be written to " + outputDirectory.getAbsolutePath() + " and base package " + config.getBasePackage());

        int clazzesGenerated = 0; // Number of classes generated for logging purposes
        Collection<BuildableRDFSClazz> clazzes = getDistinctClasses();
        for (BuildableRDFSClazz clazz : clazzes) {
            // Don't output files for classes that are from RDF/RDFS/... vocab and not for literal types:
            if (!isFromSpecialVocabulary(clazz) && !clazz.isLiteral()) {

                // Determine the package to write to:
                String clazzPackage = clazz.getJavaPackageName(config);

                JavaFile resourceObjectFile = JavaFile.builder(clazzPackage, clazz.buildTypeSpec(config))
                        .build();

                JavaFile supportFile = JavaFile.builder(clazzPackage, clazz.buildSupportTypeSpec(config))
                        .build();

                resourceObjectFile.writeTo(outputDirectory);
                supportFile.writeTo(outputDirectory);

                clazzesGenerated++;
                logger.debug("Generated Java class " + clazz.getJavaPoetClassName(config).simpleName()
                        + " for RDF class " + clazz.getResourceAsString()
                        + " (" + clazzesGenerated + " of " + clazzes.size() + ")");
            }
        }
    }
}
