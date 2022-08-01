module de.atextor.syntax.annotation {
    requires jdk.compiler;
    requires java.xml;
    requires static org.apache.jena.core;
    requires static com.fasterxml.jackson.databind;
    exports de.atextor.syntax.annotation;
    exports de.atextor.syntax.annotation.processor;
}