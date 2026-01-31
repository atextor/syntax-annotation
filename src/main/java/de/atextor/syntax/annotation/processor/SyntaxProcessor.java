package de.atextor.syntax.annotation.processor;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import de.atextor.syntax.annotation.Syntax;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The Java annotation processor that looks for {@link Syntax} annotations on string literals and string fields.
 */
@SupportedAnnotationTypes( { "de.atextor.syntax.annotation.Syntax" } )
@SupportedSourceVersion( SourceVersion.RELEASE_25 )
public class SyntaxProcessor extends AbstractProcessor {
    private final CompilerTaskListener compilerTaskListener = new CompilerTaskListener();

    private final SyntaxTreeTraverser syntaxTreeTraverser = new SyntaxTreeTraverser();

    private final AnnotationArgumentExtractor checkAnnotationType = new AnnotationArgumentExtractor( Syntax.class );

    private ProcessingEnvironment processingEnvironment;

    /**
     * Default constructor
     */
    public SyntaxProcessor() {
    }

    /**
     * Initialize the annotation processor
     *
     * @param processingEnvironment environment to access facilities the tool framework
     * provides to the processor
     */
    @Override
    public synchronized void init( final ProcessingEnvironment processingEnvironment ) {
        super.init( processingEnvironment );
        this.processingEnvironment = jbUnwrap( ProcessingEnvironment.class, processingEnvironment );
        JavacTask.instance( this.processingEnvironment ).addTaskListener( compilerTaskListener );
    }

    /**
     * When running annotation processing in IntelliJ IDEA 2020.3 and later, the processing environment is proxied
     * and in order to obtain the original com.sun.tools.javac.processing.JavacProcessingEnvironment - which
     * is required to call e.g. {@link Trees#instance(ProcessingEnvironment)} - this method can be used.
     * See https://youtrack.jetbrains.com/issue/IDEA-256707
     */
    private static <T> T jbUnwrap( final Class<? extends T> iface, final T wrapper ) {
        T unwrapped = null;
        try {
            final Class<?> apiWrappers = wrapper.getClass().getClassLoader()
                .loadClass( "org.jetbrains.jps.javac.APIWrappers" );
            final Method unwrapMethod = apiWrappers.getDeclaredMethod( "unwrap", Class.class, Object.class );
            unwrapped = iface.cast( unwrapMethod.invoke( null, iface, wrapper ) );
        } catch ( final Throwable ignored ) {
        }
        return unwrapped != null ? unwrapped : wrapper;
    }

    @Override
    public boolean process( final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv ) {
        // We can't do the processing here, because this method is not called for all types of annotated elements,
        // in particular local variables.
        // Actual processing is done in the CompilerTaskListener.
        return true;
    }

    /**
     * This method passes a given error message to the compiler process and stops compilation.
     *
     * @param message the message
     * @param location the location where the error occurred
     * @param root the corresponding abstract syntax tree
     */
    private void indicateError( final String message, final Tree location, final CompilationUnitTree root ) {
        Trees.instance( processingEnvironment ).printMessage( Diagnostic.Kind.ERROR, message, location, root );
    }

    /**
     * The literal values we receive from the abstract syntax tree are escaped, e.g. "\"hello\nworld\"".
     * This method extracts the corresponding unescaped string.
     *
     * @param literal the given escaped literal string
     * @return the unescaped string
     */
    private String unescapeLiteral( final String literal ) {
        return literal.substring( 1, literal.length() - 1 )
            .replaceAll( "\\\\n", "\n" )
            .replaceAll( "\\\\\"", "\\\"" );
    }

    /**
     * A task listener that checks the compiler phases: Only after the "analyze" phase is done, we'll use
     * the {@link SyntaxTreeTraverser} to traverse the abstract syntax tree.
     */
    private class CompilerTaskListener implements TaskListener {
        @Override
        public void finished( final TaskEvent event ) {
            if ( event.getKind() != TaskEvent.Kind.ANALYZE ) {
                return;
            }

            final TypeElement element = event.getTypeElement();
            final TreePath path = Trees.instance( processingEnvironment ).getPath( element );
            syntaxTreeTraverser.scan( path, path.getCompilationUnit() );
        }
    }

    /**
     * Java abstract syntax tree traverser that looks for string variables annotated with @Syntax and
     * calls the given checker function on their initializer expression
     */
    private class SyntaxTreeTraverser extends TreePathScanner<Void, CompilationUnitTree> {
        /**
         * Adds line numbers to a given (possibly multi line) string, so parser/validating errors referring
         * to line numbers can be understood more easily
         *
         * @param string the input string
         * @return the string with each of its lines prefixed with the line number
         */
        private String addLineNumbers( final String string ) {
            final String[] lines = string.split( "\n" );
            return IntStream.range( 0, lines.length )
                .mapToObj( i -> String.format( "%3d: %s\n", i + 1, lines[i] ) )
                .collect( Collectors.joining() );
        }

        /**
         * The AST node visiting method for variables, the only one we are interested in
         *
         * @param node the node being visited
         * @param compilationUnit the AST tree so that we have it available for error reporting
         * @return either this method raises a compiler error (when syntax valiation has failed) or it returns nothing
         */
        @Override
        public Void visitVariable( final VariableTree node, final CompilationUnitTree compilationUnit ) {
            final ExpressionTree initializer = node.getInitializer();
            if ( initializer == null || !node.getType().toString().equals( "String" ) ) {
                return super.visitVariable( node, compilationUnit );
            }
            final List<? extends Class<?>> checkerClasses = node.getModifiers().getAnnotations().stream()
                .map( annotation -> annotation.accept( checkAnnotationType, null ) )
                .filter( clazz -> clazz != RuntimeException.class )
                .toList();

            final String variableValue = unescapeLiteral( initializer.toString() );
            for ( final Class<?> clazz : checkerClasses ) {
                try {
                    @SuppressWarnings( "unchecked" ) final Function<String, Optional<String>> syntaxChecker =
                        (Function<String, Optional<String>>) clazz.getDeclaredConstructor().newInstance();
                    final Optional<String> errorMessage = syntaxChecker.apply( variableValue );
                    errorMessage.ifPresent( message ->
                        indicateError( String.format( "%s syntax validation failed:%n%s%n%s",
                                clazz.getSimpleName(), addLineNumbers( variableValue ), message ), node,
                            compilationUnit ) );
                } catch ( final Exception e ) {
                    // This can happen e.g. when the given syntax checker class has no default constructor, or it
                    // is not accessible. We'll ignore this.
                }
            }
            return super.visitVariable( node, compilationUnit );
        }
    }

    /**
     * Java AST tree visitor that extracts the Class&lt;T&gt;-typed argument (value()) of a given annotation.
     * On success, it returns the class object for the annotation's argument; on failure it returns RuntimeException
     * .class.
     */
    private static class AnnotationArgumentExtractor extends SimpleTreeVisitor<Class<?>, Object> {
        /**
         * The type of processed annotation
         */
        private final Class<?> annotationType;

        /**
         * Marker object used as a context when traversing the abstract syntax tree
         */
        private final Object hint = new Object();

        public AnnotationArgumentExtractor( final Class<?> annotationType ) {
            super( RuntimeException.class );
            this.annotationType = annotationType;
        }

        @Override
        public Class<?> visitAnnotation( final AnnotationTree node, final Object context ) {
            // Checks that the syntax tree element is annotated with the annotation we are looking for
            final Class<?> annotationType = node.getAnnotationType().accept( this, hint );
            if ( annotationType != RuntimeException.class
                && node.getArguments() != null
                && node.getArguments().size() == 1 ) {

                // If we reach this, this means we are here: @Foo(value() = Bar.class)
                //                                           ^^^^
                return node.getArguments().get( 0 ).accept( this, hint );
            }
            return RuntimeException.class;
        }

        @Override
        public Class<?> visitAssignment( final AssignmentTree node, final Object context ) {
            if ( context == hint ) {
                // If we reach this, this means we are here: @Foo(value() = Bar.class)
                //                                                        ^
                return node.getExpression().accept( this, context );
            }
            return RuntimeException.class;
        }

        @Override
        public Class<?> visitMemberSelect( final MemberSelectTree node, final Object context ) {
            if ( context == null || context != hint ) {
                return RuntimeException.class;
            }
            // If we reach this, this means we are here: @Foo(value() = Bar.class)
            //                                                          ^^^^^^^^^
            return accessTypeField( node ).map( typeName -> {
                try {
                    if ( !typeName.startsWith( "java.lang.Class<" ) ) {
                        return RuntimeException.class;
                    }
                    final String className = typeName.substring( 16, typeName.length() - 1 );
                    return Class.forName( className );
                } catch ( final ClassNotFoundException e ) {
                    return RuntimeException.class;
                }
            } ).orElse( RuntimeException.class );
        }

        @Override
        public Class<?> visitIdentifier( final IdentifierTree node, final Object context ) {
            if ( context == null || context != hint ) {
                return RuntimeException.class;
            }
            return accessTypeField( node ).map( typeName ->
                    typeName.equals( annotationType.getName() ) ? annotationType : RuntimeException.class )
                .orElse( RuntimeException.class );
        }

        private Optional<String> accessTypeField( final ExpressionTree node ) {
            try {
                for ( final Field field : node.getClass().getFields() ) {
                    if ( field.toString().endsWith( "JCTree.type" ) ) {
                        field.setAccessible( true );
                        final Object type = field.get( node );
                        return Optional.of( type.toString() );
                    }
                }
            } catch ( final IllegalAccessException exception ) {
                return Optional.empty();
            }
            return Optional.empty();
        }
    }
}
