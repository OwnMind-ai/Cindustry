package parser

import org.cindustry.parser.*
import org.cindustry.transpiler.Type
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParserTest {

    @Test
    fun parse() {
        val file = """
            import math.pow;
            
            use message1; 
            global const number a;
            
            enum Enum {
                FIRST, SECOND
            }
            
            void main(){
                cell1[0];
                const Enum g = Enum.FIRST;
                number x = a++ * 4;
                
                for(;;)
                    doCall();

                for (x = 0; x < 4; x++) {
                    // Comment
                    print(x++, message1);
                    wait(.5);
                    
                    x = a = object.heat;
                    if(x == x + a * 10 + 5){
                        break;
                    }
                }
            }
            
            Enum foo(const number a, number b, number c, ...){
                foreach(arg in args){
                    print(arg);
                }
            
                return b++; 
            }
        """.trimIndent()

        val parser = Parser(Lexer(CharStream(file, "test.cind")))

        assertEquals(FileToken(
            "test",
            mutableListOf(ImportToken(listOf(WordToken("math"), PunctuationToken("."), WordToken("pow")))),
            mutableListOf(
                InitializationToken(WordToken("building"), WordToken("message1"), BuildingToken("message1")),
                InitializationToken(WordToken("a"), WordToken("number"), null, true)
            ),
            mutableListOf(EnumToken(WordToken("Enum"), listOf(WordToken("FIRST"), WordToken("SECOND")))),
            mutableListOf(
            FunctionDeclarationToken(WordToken("main"), WordToken("void"), listOf(), CodeBlockToken(listOf(
                ArrayAccessToken(VariableToken(WordToken("cell1")), NumberToken("0")),
                InitializationToken(WordToken("Enum"), WordToken("g"), FieldAccessToken(VariableToken(WordToken("Enum")), WordToken("FIRST")), const = true),
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
                        WordToken("x")), OperationToken.EmptySide()), VariableToken(WordToken("message1")))
                    ),

                    CallToken(WordToken("wait"), listOf(NumberToken("0.5"))),
                    OperationToken(OperatorToken("="), VariableToken(WordToken("x")),
                        OperationToken(OperatorToken("="), VariableToken(WordToken("a")),
                            FieldAccessToken(VariableToken(WordToken("object")), WordToken("heat")))),

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

            FunctionDeclarationToken(WordToken("foo"), WordToken("Enum"),
                listOf(
                    ParameterToken(WordToken("number"), WordToken("a"), true),
                    ParameterToken(WordToken("number"), WordToken("b"), false),
                    ParameterToken(WordToken("number"), WordToken("c"), true),
                    ParameterToken(WordToken(Type.VARARG.name), WordToken(""), true)
                ),
                CodeBlockToken(listOf(
                    ForEachToken("arg", "args", CodeBlockToken(listOf(
                        CallToken(WordToken("print"), listOf(VariableToken(WordToken("arg")))
                        ),
                    ))),
                    ReturnToken(WordToken("return"), OperationToken(OperatorToken("++"), VariableToken(WordToken("b")), OperationToken.EmptySide()))
                )))
        )).toString(), parser.parse("test").toString())
    }
}