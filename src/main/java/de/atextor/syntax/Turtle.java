package de.atextor.syntax;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import java.io.StringReader;
import java.util.Optional;
import java.util.function.Function;

/**
 * Function that checks whether a given string is syntactically valid RDF/Turtle
 */
public class Turtle implements Function<String, Optional<String>> {
    @Override
    public Optional<String> apply( final String turtleString ) {
        final Model model = ModelFactory.createDefaultModel();
        final StringReader stringReader = new StringReader( turtleString );
        try {
            model.read( stringReader, "", "TTL" );
        } catch ( final Exception exception ) {
            return Optional.of( exception.getMessage() );
        }
        return Optional.empty();
    }
}
