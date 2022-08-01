package de.atextor.syntax;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

/**
 * Function that checks whether a given string is syntactically valid XML
 */
public class XML implements Function<String, Optional<String>> {
    @Override
    public Optional<String> apply( final String xmlString ) {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating( false );
        factory.setNamespaceAware( true );

        try {
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final Document document = builder.parse( new InputSource( new ByteArrayInputStream( xmlString.getBytes() ) ) );
        } catch ( final ParserConfigurationException | SAXException | IOException exception ) {
            return Optional.of( exception.getMessage() );
        }
        return Optional.empty();
    }
}
