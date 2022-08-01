package de.atextor.syntax;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Function that checks whether a given string is syntactically valid JSON
 */
public class JSON implements Function<String, Optional<String>> {
    @Override
    public Optional<String> apply( final String jsonString ) {
        final ObjectMapper mapper = new ObjectMapper();
        try {
            @SuppressWarnings( "unchecked" ) final Map<String, String> map = mapper.readValue( jsonString, Map.class );
            return Optional.empty();
        } catch ( final JsonProcessingException exception ) {
            return Optional.of( exception.getMessage() );
        }
    }
}
