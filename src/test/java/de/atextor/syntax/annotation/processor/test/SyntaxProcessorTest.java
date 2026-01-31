package de.atextor.syntax.annotation.processor.test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import de.atextor.syntax.annotation.processor.SyntaxProcessor;
import org.junit.Test;

import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

public class SyntaxProcessorTest {
    @Test
    public void testCompilation() {
        final Compilation compilation =
            javac()
                .withProcessors( new SyntaxProcessor() )
                .compile( JavaFileObjects.forSourceString( "HelloWorld", "final class HelloWorld {}" ) );
        assertThat( compilation ).succeededWithoutWarnings();
    }

    @Test
    public void testThatProcessorRuns() {
        final String code = """
            package de.atextor.test;

            import de.atextor.syntax.annotation.Syntax;
            import de.atextor.syntax.XML;

            class Test {
               @Syntax( XML.class ) String someString = "<hello></hello>" ;
            }
            """;

        final List<JavaFileObject> sources = List.of(
            JavaFileObjects.forSourceString( "de.atextor.test.Test", code ),
            JavaFileObjects.forResource( sourceFileUrl( "de/atextor/syntax/annotation/Syntax.java" ) ),
            JavaFileObjects.forResource( sourceFileUrl( "de/atextor/syntax/XML.java" ) )
        );

        final Compilation compilation = javac()
            .withProcessors( new SyntaxProcessor() )
            .compile( sources );

        assertThat( compilation ).succeededWithoutWarnings();
    }

    @Test
    public void testXMLSyntax() {
        final String code = """
            package de.atextor.test;

            import de.atextor.syntax.annotation.Syntax;
            import de.atextor.syntax.XML;

            class Test {
               @Syntax( XML.class ) String brokenXml = "<hello></hello" ;
            }
            """;

        final JavaFileObject testClass = JavaFileObjects.forSourceString( "de.atextor.test.Test", code );
        final List<JavaFileObject> sources = List.of(
            testClass,
            JavaFileObjects.forResource( sourceFileUrl( "de/atextor/syntax/annotation/Syntax.java" ) ),
            JavaFileObjects.forResource( sourceFileUrl( "de/atextor/syntax/XML.java" ) )
        );

        final Compilation compilation = javac()
            .withProcessors( new SyntaxProcessor() )
            .compile( sources );

        assertThat( compilation )
            .hadErrorContaining( "XML document structures must start and end within the same entity." )
            .inFile( testClass )
            .onLine( 7 );
    }

    @Test
    public void testRegExpSyntax() {
        final String code = """
            package de.atextor.test;

            import de.atextor.syntax.annotation.Syntax;
            import de.atextor.syntax.RegExp;

            class Test {
               @Syntax( RegExp.class ) String brokenRegExp = "foo[bar" ;
            }
            """;

        final JavaFileObject testClass = JavaFileObjects.forSourceString( "de.atextor.test.Test", code );
        final List<JavaFileObject> sources = List.of(
            testClass,
            JavaFileObjects.forResource( sourceFileUrl( "de/atextor/syntax/annotation/Syntax.java" ) ),
            JavaFileObjects.forResource( sourceFileUrl( "de/atextor/syntax/RegExp.java" ) )
        );

        final Compilation compilation = javac()
            .withProcessors( new SyntaxProcessor() )
            .compile( sources );

        assertThat( compilation )
            .hadErrorContaining( "Unclosed character class near index 6" )
            .inFile( testClass )
            .onLine( 7 );
    }

    @Test
    public void testTurtleSyntax() {
        final String code = """
            package de.atextor.test;

            import de.atextor.syntax.annotation.Syntax;
            import de.atextor.syntax.Turtle;

            class Test {
               @Syntax( Turtle.class ) String brokenTurtle = ":x a :y ." ;
            }
            """;

        final JavaFileObject testClass = JavaFileObjects.forSourceString( "de.atextor.test.Test", code );
        final List<JavaFileObject> sources = List.of(
            testClass,
            JavaFileObjects.forResource( sourceFileUrl( "de/atextor/syntax/annotation/Syntax.java" ) ),
            JavaFileObjects.forResource( sourceFileUrl( "de/atextor/syntax/Turtle.java" ) )
        );

        // Required by the implementation of Turtle.class.
        // Why can't we just pass the current class path? Because jena-core is not in it, because jena-core's
        // scope is declared "compile" instead of "test" (we want it to be an optional maven dependency).
        // If we'd change the scope to "test", this would also imply "compile", and also would not include a runtime
        // dependency; but then we could not declare module org.apache.jena.core as "requires static" (i.e. making the
        // module optional) in src/main/module-info.java.
        // However, when running this test, we rely on the fact that src/main has been compiled before, so the
        // file must be available in the local maven repository.
        final List<File> classPath = List.of( mavenArtifact( "org.apache.jena:jena-core:4.4.0" ) );

        final Compilation compilation = javac()
            .withProcessors( new SyntaxProcessor() )
            .withClasspath( classPath )
            .compile( sources );

        assertThat( compilation )
            .hadErrorContaining( "Undefined prefix" )
            .inFile( testClass )
            .onLine( 7 );
    }

    @Test
    public void testJSONSyntax() {
        final String code = """
            package de.atextor.test;

            import de.atextor.syntax.annotation.Syntax;
            import de.atextor.syntax.JSON;

            class Test {
               @Syntax( JSON.class ) String brokenJson = "{ \\"foo\\": \\"bar\\"" ;
            }
            """;

        final JavaFileObject testClass = JavaFileObjects.forSourceString( "de.atextor.test.Test", code );
        final List<JavaFileObject> sources = List.of(
            testClass,
            JavaFileObjects.forResource( sourceFileUrl( "de/atextor/syntax/annotation/Syntax.java" ) ),
            JavaFileObjects.forResource( sourceFileUrl( "de/atextor/syntax/JSON.java" ) )
        );

        final List<File> classPath = List.of(
            mavenArtifact( "com.fasterxml.jackson.core:jackson-databind:2.13.3" ),
            mavenArtifact( "com.fasterxml.jackson.core:jackson-core:2.13.3" )
        );

        final Compilation compilation = javac()
            .withProcessors( new SyntaxProcessor() )
            .withClasspath( classPath )
            .compile( sources );

        assertThat( compilation )
            .hadErrorContaining( "Unexpected end-of-input: expected close marker for Object" )
            .inFile( testClass )
            .onLine( 7 );
    }

    /**
     * Takes as input the artifact specifier (groupId:artifactId:version) and returns the corresponding file
     * for the jar
     *
     * @param artifact the artifact specifier
     * @return the corresponding file
     */
    private File mavenArtifact( final String artifact ) {
        final String[] parts = artifact.split( ":" );
        final String groupId = parts[0];
        final String artifactId = parts[1];
        final String version = parts[2];
        return new File( String.format( "%s/.m2/repository/%s/%s/%s/%s-%s.jar", System.getProperty( "user.home" ),
            groupId.replace( '.', '/' ), artifactId, version, artifactId, version ) );
    }

    private URL sourceFileUrl( final String path ) {
        try {
            return new File( "./src/main/java/" + path ).getCanonicalFile().toURI().toURL();
        } catch ( final IOException e ) {
            throw new RuntimeException( e );
        }
    }
}
