package parser

import org.cindustry.parser.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParserTest {

    @Test
    fun parse() {
        val file = """
            use @message1; 
            global const number a;
            
            void main(){
                @cell1[0];
                const number g = 100;
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
            
            number foo(const number a, number b, number c){
                b++; 
            }
        """.trimIndent()

        val parser = Parser(Lexer(CharStream(file)))

        assertEquals(FileToken(
            listOf(
                InitializationToken(WordToken("building"), WordToken("message1"), BuildingToken("message1")),
                InitializationToken(WordToken("a"), WordToken("number"), null, true)
            ),
            listOf(
            FunctionDeclarationToken(WordToken("main"), WordToken("void"), listOf(), CodeBlockToken(listOf(
                ArrayAccessToken(BuildingToken("cell1"), NumberToken("0")),
                InitializationToken(WordToken("number"), WordToken("g"), NumberToken("100"), const = true),
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
                listOf(
                    ParameterToken(WordToken("number"), WordToken("a"), true),
                    ParameterToken(WordToken("number"), WordToken("b"), false),
                    ParameterToken(WordToken("number"), WordToken("c"), true)
                ),
                CodeBlockToken(listOf(
                    OperationToken(OperatorToken("++"), VariableToken(WordToken("b")), OperationToken.EmptySide())
                )))
        )).toString(), parser.parse().toString())
    }
}