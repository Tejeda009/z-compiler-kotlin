package Tests

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

internal object ZFixtures {
    private val logger: org.slf4j.Logger? = org.slf4j.LoggerFactory.getLogger(ZFixtures::class.java)

    /**
     * Builds a lexer over a source string.
     *
     * @param source the program text
     * @return the lexer, positioned before the first token
     */
    fun lexerFor(source: String): src.Lexer.Lexer {
        val input: java.io.InputStream = ByteArrayInputStream(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        return src.Lexer.Lexer(input)
    }

    /**
     * Builds a parser over a source string.
     *
     * @param source the program text
     * @return the parser, positioned on the first token
     * @throws IOException if the input cannot be read
     */
    @Throws(IOException::class)
    fun parserFor(source: String): src.Parser.Parser {
        return src.Parser.Parser(lexerFor(source))
    }

    /**
     * Parses every definition in a file.
     *
     * @param source the program text
     * @return the definitions in source order, or null if one failed to parse
     * @throws IOException if the input cannot be read
     */
    @Throws(IOException::class)
    fun parseAll(source: String): MutableList<FunctionAST?>? {
        val unit = parseUnit(source)
        return if (unit == null) null else unit.functions
    }

    /**
     * Parses every declaration in a file: struct declarations, top-level
     * variables and function definitions, in source order.
     *
     * @param source the program text
     * @return the parsed unit, or null if one of its declarations failed
     * @throws IOException if the input cannot be read
     */
    @Throws(IOException::class)
    fun parseUnit(source: String): ParsedUnit? {
        val lexer: src.Lexer.Lexer = lexerFor(source)
        val parser: src.Parser.Parser = src.Parser.Parser(lexer)
        val functions: MutableList<FunctionAST> = java.util.ArrayList<FunctionAST>()
        val globals: MutableList<DeclaratorExprAST> = java.util.ArrayList<DeclaratorExprAST>()
        val structs: MutableList<StructDeclAST> = java.util.ArrayList<StructDeclAST>()

        while (true) {
            if (parser.getContext().getCurTok() == src.Lexer.Lexer.Tokens.STRUCT.value) {
                val s: StructDeclAST? = parseStruct(parser)
                if (s == null) return null
                structs.add(s)
                continue
            }

            if (parser.atTypeDeclaration()) {
                val global: DeclaratorExprAST? = parser.parseVariable(true)
                if (global == null) return null
                globals.add(global)
                continue
            }

            val reported: ByteArrayOutputStream = ByteArrayOutputStream()
            val diagnostics: PrintStream = java.lang.System.err
            val definition: FunctionAST?
            val endOfInput: Boolean

            java.lang.System.setErr(PrintStream(reported, true, java.nio.charset.StandardCharsets.UTF_8))
            try {
                definition = parser.parseDefinition()
                endOfInput = definition == null && lexer.getTok() == src.Lexer.Lexer.Tokens.EOF.value
            } finally {
                java.lang.System.setErr(diagnostics)
            }

            if (reported.size() > 0) {
                diagnostics.print(reported.toString(java.nio.charset.StandardCharsets.UTF_8))
            }

            if (definition == null) {
                return if (!endOfInput || reported.size() > 0)
                    null
                else
                    ParsedUnit(functions, globals, structs)
            }
            functions.add(definition)
        }
    }

    /**
     * Parses one struct declaration.
     *
     * @param parser the parser, positioned on `struct`
     * @return the declaration, or null on error
     * @throws IOException if the input cannot be read
     */
    @Throws(IOException::class)
    private fun parseStruct(parser: src.Parser.Parser): StructDeclAST? {
        val reported: ByteArrayOutputStream = ByteArrayOutputStream()
        val diagnostics: PrintStream = java.lang.System.err
        val struct: StructDeclAST?
        java.lang.System.setErr(PrintStream(reported, true, java.nio.charset.StandardCharsets.UTF_8))
        try {
            struct = parser.parseStructDecl()
        } finally {
            java.lang.System.setErr(diagnostics)
        }
        if (reported.size() > 0) {
            diagnostics.print(reported.toString(java.nio.charset.StandardCharsets.UTF_8))
        }
        return struct
    }

    /**
     * Generates the IR for a whole source file.
     *
     * @param source the program text
     * @return the module, or null when a definition failed to parse or to build
     */
    @Throws(IOException::class)
    fun module(source: String): String? {
        StructRegistry.clear()

        val unit = parseUnit(source)
        if (unit == null) return null

        val builder: IRBuilder = IRBuilder()
        for (s in unit.structs!!) {
            s.codeGen(builder)
        }
        for (definition in unit.functions!!) {
            if (!builder.declare(definition.prototype())) return null
        }

        for (global in unit.globals!!) {
            global.codeGen(builder)
            if (builder.hasFailed()) return null
        }

        val module: java.lang.StringBuilder = java.lang.StringBuilder()
        for (definition in unit.functions) {
            val function: LLVMTextUnit? = definition.codeGen(builder)
            if (function == null || builder.hasFailed()) return null
            module.append(function.text)
        }
        val body = module.toString()
        val globals: String = builder.getGlobals()
        return if (globals.isEmpty()) body else globals + "\n" + body
    }

    /**
     * Builds a module that is expected to fail, with its diagnostics captured.
     *
     * @param source the program text
     * @return what the compiler wrote to stderr, or null if it succeeded
     */
    fun failureOf(source: String): String? {
        val captured: ByteArrayOutputStream = ByteArrayOutputStream()
        val diagnostics: PrintStream? = java.lang.System.err
        java.lang.System.setErr(PrintStream(captured, true, java.nio.charset.StandardCharsets.UTF_8))
        try {
            return if (module(source) == null) captured.toString(java.nio.charset.StandardCharsets.UTF_8) else null
        } catch (e: IOException) {
            return null
        } finally {
            java.lang.System.setErr(diagnostics)
        }
    }

    /**
     * The result of parsing a whole file: its functions and its top-level
     * variables, in source order.
     *
     * @param functions the function definitions, in source order
     * @param globals   the top-level variable declarations, in source order
     */
    internal class ParsedUnit(
        functions: MutableList<FunctionAST>?,
        globals: MutableList<DeclaratorExprAST>?,
        structs: MutableList<StructDeclAST>?
    ) {
        val functions: MutableList<FunctionAST>?
        val globals: MutableList<DeclaratorExprAST>?
        val structs: MutableList<StructDeclAST>?

        init {
            this.functions = functions
            this.globals = globals
            this.structs = structs
        }
    }
}