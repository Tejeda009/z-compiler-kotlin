package src.Lexer

import src.AST.ExprAST
import src.Codegen.IRBuilder
import src.Parser.ZType
import src.AST.LLVMTextUnit
import src.AST.Conditions
import src.Parser.TypeRef
import src.AST.ForAST
import src.Codegen.SymbolTable.SymbolInfo
import java.util.Objects
import src.AST.NumberExprAST
import src.AST.BlockAST
import src.AST.DeclaratorExprAST
import src.AST.ImportAST
import src.AST.ExternExprAST
import src.AST.FunctionAST
import java.util.Collections
import src.AST.BinaryExprAST
import src.Codegen.IRBuilder.ResolvedSignature
import src.AST.PrototypeAST
import src.AST.CallExprAST
import src.Parser.StructType
import src.Parser.StructRegistry
import src.AST.AddressOfExprAST
import src.AST.VarRefExprAST
import src.AST.FreeExprAST
import kotlin.streams.asStream
import src.AST.LoopJumpAST
import src.AST.AllocExprAST
import src.AST.DerefExprAST
import src.AST.AssignExprAST
import src.Codegen.IRBuilder.ExternDeclaration
import src.AST.StructLiteralAST
import src.AST.FieldAccessExprAST
import src.AST.AssignThroughPointerExprAST
import java.util.concurrent.Callable
import java.io.File
import java.nio.file.Files
import src.Main.CompilerCmd
import java.io.ByteArrayInputStream
import java.util.Locale
import java.io.IOException
import java.nio.file.Path
import src.AST.ModuleAST
import src.Main.ModuleLoader
import java.util.LinkedHashMap
import java.util.HashSet
import src.AST.StructDeclAST
import java.util.HashMap
import src.Parser.ParserContext
import src.Parser.TypeParser
import src.Parser.BranchesParser
import src.Parser.LoopParser
import src.Parser.RetParser
import src.Parser.StructParser
import src.Parser.VariableParser
import src.Parser.DefinitionParser
import src.AST.IfAST
import src.AST.RetAST
import src.AST.ArrayAST
import src.AST.ParenExprAST
import src.AST.ArrayIdxExprAST
import src.AST.StringExprAST
import src.AST.BoolExprAST
import src.AST.UnaryExprAST
import java.util.Deque
import src.Codegen.TypeLayout
import org.junit.jupiter.api.Assertions
import Tests.ZFixtures
import Tests.ZFixtures.ParsedUnit
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.jupiter.api.io.TempDir
import Tests.TestEndToEnd
import Tests.TestStdLib
import Tests.TestBinding
import Tests.TestCodeGen
import org.junit.jupiter.api.Disabled
import Tests.TestImports
import org.junit.jupiter.api.AfterEach
import Tests.TestAstNodes.VariableNode
import Tests.TestAstNodes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest
import Tests.TestExamples
import java.util.Optional
import java.util.stream.Collectors
import Tests.TestIRBuilder
import Tests.TestMoreLexer
import Tests.TestAstCodeGen
import Tests.TestAstCodeGen.ImpureConstant
import Tests.TestMoreParser
import src.Main.CompilerCmdTest
import Tests.TestAstCoverage
import src.AST.AstInternalsTest.NullCoerce
import Tests.TestModuleLoader
import src.Parser.ParserInternalsTest
import Tests.TestGeneratedIrIsAccepted

/**
 * Parses the source code and returns various Tokens, if it's unknown returns the ASCII value
 */
class Lexer(input: java.io.InputStream) {
    /**
     * Enum of Tokens
     */
    enum class Tokens // constructor for the Tokens
        (
        /**
         * Attributes: description is to identify the token; the value is the return value
         */
        val description: String, val value: Int
    ) {
        FREE("free", -52),
        ALLOC("alloc", -51),
        STRUCT("struct", -50),
        VARARG("...", -49),
        EXTERN("extern", -48),
        CONTINUE("continue", -47),
        BREAK("break", -46),
        FALSE("false", -45),
        TRUE("true", -44),
        MOD("%", -43),
        NE("!=", -42),
        EQ("==", -41),
        PTR("ptr", -40),
        GE(">=", -39),
        LE("<=", -38),
        VOID("void", -37),
        STRING_LITERAL("string-literal", -36),
        CN("cn", -35),
        IDENTIFIER("id", -34),
        AND("&", -33),
        SAME("=", -32),
        EOF("eof", -31),
        ASSIGN("->", -30),
        PARAMS("|", -29),
        ARRAY_CLOSE("]", -28),
        ARRAY_OPEN("[", -27),
        IMPORT("import", -26),
        MORE(">", -25),
        LESS("<", -24),
        CLOSE_FUNC("}", -23),
        OPEN_FUNC("{", -22),
        CLOSE_PAR(")", -21),
        OPEN_PAR("(", -20),
        FOR("for", -19),
        ELSE("else", -18),
        EIF("eif", -17),
        IF("if", -16),
        SUB("-", -15),
        ADD("+", -14),
        DIV("/", -13),
        MUL("*", -12),
        UINT64("uint64", -11),
        UINT32("uint32", -10),
        BOOL("bool", -9),
        STRING("str", -8),
        FLOAT64("flt64", -7),
        FLOAT32("flt32", -6),
        INT32("int32", -5),
        RET("ret", -4),
        NUMBER("unused", -3),  // number returns
        INT64("int64", -2),
        FUNC("proc", -1),
        MUT("mut", -53);

        companion object {
            // helper method to get the token from the value
            fun fromValues(value: Int): Tokens? {
                return java.util.Arrays.stream<Tokens?>(src.Lexer.Lexer.Tokens.entries.toTypedArray())
                    .filter { token: Tokens? -> token!!.value == value }.findFirst().orElse(null)
            }
        }
    }

    /**
     * lastChar is used to store the last char lol, IdentifierStr is used to store the String value of the identifier token (es. variable)
     * NumVal is used to store the value of the number token (es. 10)
     */
    private var lastChar: Int = ' '.code

    /** a '.' already read while looking for a vararg, still to be returned  */
    private var pendingDot = false
    var identifierStr: String? = null
        private set
    var numVal: Double = 0.0
        private set

    /** the number exactly as it was written, which NumVal cannot represent  */
    var numberText: String? = null
        private set

    /** true when the literal had a decimal point or an exponent  */
    var numIsFloat: Boolean = false
        private set

    /** true when the literal was written without one  */
    var numIsInteger: Boolean = false
        private set

    /** the contents of a string literal, after escape processing  */
    var stringVal: String? = null
        private set

    // stream for the input
    private val input: java.io.InputStream

    /**
     * Constructor for the Lexer
     * @param input stream for the input
     */
    init {
        this.input = input
    }

    @Throws(IOException::class)
    private fun read() {
        lastChar = input.read()
    }

    @get:Throws(IOException::class)
    val tok: Int
        /**
         * Returns the next token
         * @return the next token
         * @throws IOException if an I/O error occurs
         */
        get() {
            // a second dot left over from something that was not a vararg

            if (pendingDot) {
                pendingDot = false
                return '.'.code
            }

            // skips whitespaces
            while (java.lang.Character.isWhitespace(lastChar)) {
                read()
            }

            // EOF for some fucking reason in java is -1
            if (lastChar == -1) {
                return src.Lexer.Lexer.Tokens.EOF.value
            }

            // assignment operator ->
            if (lastChar == '-'.code) {
                read()

                if (lastChar == '>'.code) {
                    read()
                    return src.Lexer.Lexer.Tokens.ASSIGN.value
                }

                return src.Lexer.Lexer.Tokens.SUB.value
            }

            // chars
            if (java.lang.Character.isAlphabetic(lastChar)) {
                val sb: java.lang.StringBuilder = java.lang.StringBuilder()

                do {
                    sb.append(lastChar.toChar())
                    read()
                } while (java.lang.Character.isLetterOrDigit(lastChar))

                identifierStr = sb.toString()

                // hashmap should be faster
                val token: Tokens? = src.Lexer.Lexer.Companion.keywordMap.get(identifierStr)
                if (token != null && token != src.Lexer.Lexer.Tokens.IDENTIFIER && token != src.Lexer.Lexer.Tokens.NUMBER && token != src.Lexer.Lexer.Tokens.EOF) {
                    return token.value
                }

                if (lastChar == '.'.code) {
                    read()
                    pendingDot = true
                }

                // if it's not a word it must be an identifier ig
                return src.Lexer.Lexer.Tokens.IDENTIFIER.value
            }

            // Numbers.
            if (java.lang.Character.isDigit(lastChar) || lastChar == '.'.code) {
                val numStr: java.lang.StringBuilder = java.lang.StringBuilder()
                var floating = false

                if (lastChar == '.'.code) {
                    read()
                    if (lastChar == '.'.code) {
                        read()
                        if (lastChar == '.'.code) {
                            read()
                            return src.Lexer.Lexer.Tokens.VARARG.value
                        }

                        pendingDot = true
                        return '.'.code
                    }
                    if (!java.lang.Character.isDigit(lastChar)) {
                        return '.'.code
                    }

                    numStr.append('.')
                    floating = true
                }

                do {
                    if (lastChar == '.'.code || lastChar == 'e'.code || lastChar == 'E'.code) {
                        floating = true
                    }
                    numStr.append(lastChar.toChar())
                    read()
                } while (java.lang.Character.isDigit(lastChar) || lastChar == '.'.code || lastChar == 'e'.code || lastChar == 'E'.code)

                numberText = numStr.toString()
                try {
                    numVal = numberText.toDouble()
                } catch (e: java.lang.NumberFormatException) {
                    src.Lexer.Lexer.Companion.logger.error("Error: malformed number: {}", numberText)
                    numVal = 0.0
                }
                numIsFloat = floating
                numIsInteger = !floating
                return src.Lexer.Lexer.Tokens.NUMBER.value
            }

            // String literals: "hello\n"
            if (lastChar == '"'.code) {
                val sb: java.lang.StringBuilder = java.lang.StringBuilder()
                lastChar = input.read()
                while (lastChar != '"'.code && lastChar != -1) {
                    if (lastChar == '\\'.code) {
                        lastChar = input.read()
                        when (lastChar) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            '\\' -> sb.append('\\')
                            '"' -> sb.append('"')
                            '0' -> sb.append('\u0000')
                            else -> sb.append(lastChar.toChar())
                        }
                    } else {
                        sb.append(lastChar.toChar())
                    }
                    lastChar = input.read()
                }
                if (lastChar == -1) {
                    src.Lexer.Lexer.Companion.logger.error("Error: unterminated string literal")
                    return src.Lexer.Lexer.Tokens.EOF.value
                }
                lastChar = input.read() // consume closing quote
                stringVal = sb.toString()
                return src.Lexer.Lexer.Tokens.STRING_LITERAL.value
            }


            // Comments
            if (lastChar == '/'.code) {
                read()

                if (lastChar == '/'.code) {
                    do {
                        read()
                    } while (lastChar != -1 && lastChar != '\n'.code && lastChar != '\r'.code)

                    return getTok()
                }

                // Multi-line comments
                if (lastChar == '*'.code) {
                    read()
                    var previous = 0
                    while (lastChar != -1 && !(previous == '*'.code && lastChar == '/'.code)) {
                        previous = lastChar
                        read()
                    }

                    if (lastChar == -1) {
                        src.Lexer.Lexer.Companion.logger.error("Error: unterminated comment")
                        return src.Lexer.Lexer.Tokens.EOF.value
                    }

                    read() // consume closing slash
                    return getTok()
                }

                return src.Lexer.Lexer.Tokens.DIV.value
            }

            // two-character operators
            if (lastChar == '<'.code || lastChar == '>'.code || lastChar == '='.code || lastChar == '!'.code) {
                val first = lastChar
                read()

                if (lastChar == '='.code) {
                    read()
                    return when (first) {
                        '<' -> src.Lexer.Lexer.Tokens.LE.value
                        '>' -> src.Lexer.Lexer.Tokens.GE.value
                        '=' -> src.Lexer.Lexer.Tokens.EQ.value
                        else -> src.Lexer.Lexer.Tokens.NE.value
                    }
                }

                return when (first) {
                    '<' -> src.Lexer.Lexer.Tokens.LESS.value
                    '>' -> src.Lexer.Lexer.Tokens.MORE.value
                    '=' -> src.Lexer.Lexer.Tokens.SAME.value
                    else -> first
                }
            }

            // symbols
            val symbol: Tokens? =
                src.Lexer.Lexer.Companion.keywordMap.get(java.lang.Character.toString(lastChar.toChar()))

            if (symbol != null) {
                read()
                return symbol.value
            }

            // returns the ASCII if unrecognized
            val ThisChar = lastChar

            // next one
            read()

            return ThisChar
        }

    companion object {
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(src.Lexer.Lexer::class.java)

        /**
         * HashMap to store the keywords and their corresponding Tokens
         */
        private val keywordMap: MutableMap<String?, Tokens?> = HashMap<String?, Tokens?>()

        // using static like this is like a constructor that executes itself on the declaration
        init {
            for (t in src.Lexer.Lexer.Tokens.entries) {
                src.Lexer.Lexer.Companion.keywordMap.put(t.description, t)
            }
        }
    }
}