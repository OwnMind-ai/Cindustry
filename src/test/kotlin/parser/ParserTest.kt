package parser

import org.cindustry.parser.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParserTest {

    @Test
    fun parse() {
        val file = """
            use @message1; 
            global number a;
            
            void main(){
                number x = a++ * 4;

                for (x = 0; x < 4; x++) {
                    // Comment
                    print(x++, @message1);
                    wait(.5);
                    
                    x = a = 5;
                    if(x == x + a * 10 + 5){
                        break;
                    }
                }
            }
            
            number foo(number a, number b){
            }
        """.trimIndent()

        val parser = Parser(Lexer(CharStream(file)))

        assertEquals(FileToken(
            listOf(
                InitializationToken(WordToken("message1"), WordToken("building"), BuildingToken("message1")),
                InitializationToken(WordToken("a"), WordToken("number"), null)
            ),
            listOf(
            FunctionDeclarationToken(WordToken("main"), WordToken("void"), listOf(), CodeBlockToken(listOf(
                InitializationToken(WordToken("number"), WordToken("x"), OperationToken(
                    OperatorToken("*"), OperationToken(OperatorToken("++"), VariableToken(WordToken("a")), OperationToken.EmptySide()), NumberToken("4"))),
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
                    OperationToken(OperatorToken("="), VariableToken(WordToken("x")),
                        OperationToken(OperatorToken("="), VariableToken(WordToken("a")), NumberToken("5"))),

                    IfToken(OperationToken(OperatorToken("=="), VariableToken(WordToken("x")),
                        OperationToken(OperatorToken("+"), VariableToken(WordToken("x")),
                            OperationToken(OperatorToken("+"),
                                OperationToken(OperatorToken("*"), VariableToken(WordToken("a")), NumberToken("10")),
                                NumberToken("5")))),
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