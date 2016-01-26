package org.opencypher.generator;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.opencypher.grammar.Fixture;
import org.opencypher.grammar.Grammar;
import org.opencypher.tools.grammar.CypherGeneratorFactory;

import static java.lang.Character.charCount;

import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.opencypher.generator.GeneratorFixture.assertGenerates;
import static org.opencypher.generator.ChoicesFixture.onRepetition;
import static org.opencypher.grammar.Grammar.charactersOfSet;
import static org.opencypher.grammar.Grammar.grammar;
import static org.opencypher.grammar.Grammar.literal;
import static org.opencypher.grammar.Grammar.nonTerminal;
import static org.opencypher.grammar.Grammar.optional;
import static org.opencypher.grammar.Grammar.sequence;
import static org.opencypher.grammar.Grammar.zeroOrMore;
import static org.opencypher.tools.output.Output.output;
import static org.opencypher.tools.output.Output.stdOut;

public class GeneratorTest
{
    public final @Rule Fixture fixture = new Fixture();

    @Ignore
    @Test
    public void generateStuff() throws Exception
    {
        // given
        Grammar grammar = fixture.grammarResource( "/somegrammar.xml" );
        Generator generator = new Generator( grammar );

        // when
        Node tree = generator.generateTree( grammar.language() );

        // then
        tree.sExpression( stdOut() );
    }

    @Ignore
    @Test
    public void shouldGenerateCypher() throws Exception
    {
        Generator cypher = new CypherGeneratorFactory()
                .generator( Paths.get( "/Users/tobias/code/neo/cypher/grammar/cypher.xml" ) );
        cypher.generate( stdOut() );
    }

    @Test
    public void shouldGenerateLiteral() throws Exception
    {
        assertGenerates(
                grammar( "foo" )
                        .production( "foo", literal( "Hello" ) ),
                "Hello" );
    }

    @Test
    public void shouldGenerateSequence() throws Exception
    {
        assertGenerates(
                grammar( "foo" )
                        .production( "foo", sequence(
                                literal( "Hello" ),
                                literal( "World" ) ) ),
                "HelloWorld" );
    }

    @Test
    public void shouldFollowNonTerminals() throws Exception
    {
        assertGenerates( grammar( "foo" )
                                 .production( "foo", sequence(
                                         nonTerminal( "hello" ),
                                         nonTerminal( "world" ) ) )
                                 .production( "hello", literal( "Hello" ) )
                                 .production( "world", literal( "World" ) ),
                         "HelloWorld" );
    }

    @Test
    public void shouldGenerateOptional() throws Exception
    {
        assertGenerates( grammar( "foo" )
                                 .production( "foo", sequence(
                                         nonTerminal( "hello" ),
                                         optional( nonTerminal( "world" ) ) ) )
                                 .production( "hello", literal( "Hello" ) )
                                 .production( "world", literal( "World" ) ),
                         x -> x.skipOptional().generates( "Hello" ),
                         x -> x.includeOptional().generates( "HelloWorld" ) );
    }

    @Test
    public void shouldGenerateAlternative() throws Exception
    {
        assertGenerates( grammar( "foo" )
                                 .production( "foo", literal( "Hello" ), literal( "World" ) ),
                         x -> x.picking( "Hello" )
                               .generates( "Hello" ),
                         x -> x.picking( "World" )
                               .generates( "World" ) );
    }

    @Test
    public void shouldGenerateRepetition() throws Exception
    {
        assertGenerates( grammar( "foo" )
                                 .production( "foo", zeroOrMore( literal( "w" ) ) ),
                         x -> x.repeat( 0, onRepetition( 0 ) ).generates( "" ),
                         x -> x.repeat( 3, onRepetition( 0 ) ).generates( "www" ) );
    }

    @Test
    public void shouldGenerateCharactersFromWellKnownSet() throws Exception
    {
        assertCharacterSet( "NUL", "\0" );
        assertCharacterSet( "TAB", "\t" );
        assertCharacterSet( "LF", "\n" );
        assertCharacterSet( "CR", "\r" );
        assertCharacterSet( "FF", "\f" );
    }

    @Test
    public void shouldReplaceProductions() throws Exception
    {
        // when
        String generated = generate( grammar( "foo" )
                                             .production( "foo", nonTerminal( "bar" ) )
                                             .production( "bar", literal( "WRONG!" ) ),
                                     bar -> bar.write( "OK" ) );

        // then
        assertEquals( "OK", generated );
    }

    @Test
    public void shouldAllowContextSensitiveReplacements() throws Exception
    {
        assertEquals( "one - two",
                      generate( grammar( "lang" ).production( "lang", sequence(
                              nonTerminal( "alpha" ), literal( " - " ), nonTerminal( "beta" ) ) )
                                                 .production( "alpha", nonTerminal( "symbol" ) )
                                                 .production( "beta", nonTerminal( "symbol" ) )
                                                 .production( "symbol", literal( "<NOT REPLACED>" ) ),
                                symbol -> {
                                    switch ( symbol.node().parent().name() )
                                    {
                                    case "alpha":
                                        symbol.write( "one" );
                                        break;
                                    case "beta":
                                        symbol.write( "two" );
                                        break;
                                    default:
                                        symbol.generateDefault();
                                        break;
                                    }
                                } ) );
    }

    private void assertCharacterSet( String name, String characters )
    {
        Grammar grammar = grammar( "foo" ).production( "foo", charactersOfSet( name ) ).build();
        StringBuilder expected = new StringBuilder();
        Set<Integer> codepoints = new HashSet<>();
        for ( int i = 0, cp; i < characters.length(); i += charCount( cp ) )
        {
            cp = characters.codePointAt( i );
            expected.setLength( 0 );
            expected.appendCodePoint( cp );
            int codepoint = cp;
            assertGenerates( grammar, x -> x.picking( codepoint ).generates( expected.toString() ) );
            codepoints.add( cp );
        }
        for ( int i = codepoints.size() * 10; i-- > 0; )
        {
            assertThat( generate( grammar ).codePointAt( 0 ), isIn( codepoints ) );
        }
    }

    @SafeVarargs
    static String generate( Grammar.Builder grammar, ProductionReplacement<Void>... replacements )
    {
        return generate( grammar.build(), replacements );
    }

    @SafeVarargs
    static String generate( Grammar grammar, ProductionReplacement<Void>... replacements )
    {
        StringBuilder result = new StringBuilder();
        new Generator( grammar, replacements ).generate( output( result ) );
        return result.toString();
    }
}
