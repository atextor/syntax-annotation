module de.atextor.syntax.annotation.test {
    requires jdk.compiler;
    requires java.xml;
    requires org.apache.jena.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires compile.testing;
    requires junit;
    requires de.atextor.syntax.annotation;
    exports de.atextor.syntax.annotation.processor.test;
}