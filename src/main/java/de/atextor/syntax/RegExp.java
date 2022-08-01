package de.atextor.syntax;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Function that checks whether a given string is a syntactically valid regular expression
 */
public class RegExp implements Function<String, Optional<String>> {
    @Override
    public Optional<String> apply( final String regExpString ) {
        try {
            Pattern.compile( regExpString );
            return Optional.empty();
        } catch ( final PatternSyntaxException e ) {
            return Optional.of( e.getMessage() );

        }
    }
}
