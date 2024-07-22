package parser

import org.cindustry.parser.*
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class ParserTest {

    @Test
    fun parse() {
        val file = """
            void main(){
                number x = 0;

                 for (x = 0; x < 4; x++) {
                    // Comment
                    print(x++, @message1);
                    wait(0.5);
                    
                    if(x == 3 * 10 + 5){
                        break;
                    }
                }
            }
            
            number foo(number a, number b){
            }
        """.trimIndent()

        val parser = Parser(Lexer(CharStream(file)))

        assertEquals(FileToken(listOf(
            FunctionDeclarationToken(WordToken("main"), WordToken("void"), listOf(), CodeBlockToken(listOf(
                InitializationToken(WordToken("number"), WordToken("x"), NumberToken("0")),
                ForToken(
                    OperationToken(OperatorToken("="), VariableToken(WordToken("x")), NumberToken("0")),
                    OperationToken(OperatorToken("<"), VariableToken(WordToken("x")), NumberToken("4")),
                    OperationToken(OperatorToken("++"), VariableToken(WordToken("x")), OperationToken.EmptySide()),

                    CodeBlockToken(listOf(
                    CallToken(WordToken("print"), listOf(OperationToken(OperatorToken("++"), VariableToken(
                        WordToken("x")), OperationToken.EmptySide()),
                        OperationToken(OperatorToken("@"), OperationToken.EmptySide(), VariableToken(WordToken("message1")))
                    )),

                    CallToken(WordToken("wait"), listOf(NumberToken("0.5"))),

                    IfToken(OperationToken(OperatorToken("=="), VariableToken(WordToken("x")),
                        OperationToken(OperatorToken("+"), OperationToken(OperatorToken("*"), NumberToken("3"), NumberToken("10")), NumberToken("5"))),
                        CodeBlockToken(listOf(
                            ReturnToken(WordToken("break"), null)
                        )), null
                    )
                )))
            ))),

            FunctionDeclarationToken(WordToken("foo"), WordToken("number"),
                listOf(ParameterToken(WordToken("number"), WordToken("a")),ParameterToken(WordToken("number"), WordToken("b"))),
                CodeBlockToken(listOf()))
        )).toString(), parser.parse().toString())
    }
}