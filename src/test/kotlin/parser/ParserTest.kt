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
                @cell1[0];
                number x = a++ * 4;
                
                for(;;)
                    doCall();

                for (x = 0; x < 4; x++) {
                    // Comment
                    print(x++, @message1);
                    wait(.5);
                    
                    x = a = @object.heat;
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
                InitializationToken(WordToken("building"), WordToken("message1"), BuildingToken("message1")),
                InitializationToken(WordToken("a"), WordToken("number"), null)
            ),
            listOf(
            FunctionDeclarationToken(WordToken("main"), WordToken("void"), listOf(), CodeBlockToken(listOf(
                ArrayAccessToken(BuildingToken("cell1"), NumberToken("0")),
                InitializationToken(WordToken("number"), WordToken("x"), OperationToken(
                    OperatorToken("*"), OperationToken(OperatorToken("++"), VariableToken(WordToken("a")), OperationToken.EmptySide()), NumberToken("4"))),
                ForToken(
                    null,null,null,
                    CodeBlockToken(listOf(CallToken(WordToken("doCall"), listOf())))
                ),
                ForToken(
                    OperationToken(OperatorToken("="), VariableToken(WordToken("x")), NumberToken("0")),
                    OperationToken(OperatorToken("<"), VariableToken(WordToken("x")), NumberToken("4")),
                    OperationToken(OperatorToken("++"), VariableToken(WordToken("x")), OperationToken.EmptySide()),

                    CodeBlockToken(listOf(
                    CallToken(WordToken("print"), listOf(OperationToken(OperatorToken("++"), VariableToken(
                        WordToken("x")), OperationToken.EmptySide()), BuildingToken("message1")
                    )),

                    CallToken(WordToken("wait"), listOf(NumberToken("0.5"))),
                    OperationToken(OperatorToken("="), VariableToken(WordToken("x")),
                        OperationToken(OperatorToken("="), VariableToken(WordToken("a")),
                            FieldAccessToken(BuildingToken("object"), WordToken("heat")))),

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