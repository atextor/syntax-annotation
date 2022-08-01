package de.atextor.syntax.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;

/**
 * Annotation that can be placed on a string to indicate that its initializer follows a certain syntax.
 * The passed function will be called at compile time to validate the value. The function takes the string
 * literal to be validated as input and returns Optional.empty() if the literal is valid or the error message
 * to be displayed if the literal is not valid.
 */
@Target( { FIELD, LOCAL_VARIABLE } )
@Retention( RetentionPolicy.SOURCE )
public @interface Syntax {
    Class<? extends Function<String, Optional<String>>> value();
}
