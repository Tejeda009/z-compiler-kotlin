package src.Main

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

class ModuleLoader(private val rootName: String?) {
    private val compiled: MutableMap<String?, ModuleAST?> = LinkedHashMap<String?, ModuleAST?>()

    private val loading: MutableSet<String?> = HashSet<String?>()

    @Throws(IOException::class)
    fun loadAll(imports: MutableList<ImportAST>, fromDir: Path?): MutableList<ModuleAST?>? {
        val dependencies: MutableList<ModuleAST?> = java.util.ArrayList<ModuleAST?>()
        for (anImport in imports) {
            if (load(anImport.moduleName, fromDir, dependencies)) return null
        }
        return dependencies
    }

    @Throws(IOException::class)
    private fun load(moduleName: String, fromDir: Path?, dependencies: MutableList<ModuleAST?>): Boolean {
        if (compiled.containsKey(moduleName)) return false

        if (moduleName == rootName) {
            if (logger.isErrorEnabled()) {
                logger.error("Error: the root file '{}' cannot be imported", moduleName)
            }
            return true
        }

        if (!loading.add(moduleName)) {
            if (logger.isErrorEnabled()) {
                logger.error("Error: circular import involving module '{}'", moduleName)
            }
            return true
        }

        try {
            val file: Path = (if (fromDir == null) Path.of(".") else fromDir).resolve(moduleName + ".z")
            val module: ModuleAST?
            if (Files.isRegularFile(file)) {
                module = parseFile(file, moduleName)
            } else if (moduleName == STD) {
                // the standard library ships inside the compiler
                module = parseStd()
            } else {
                if (logger.isErrorEnabled()) {
                    logger.error("Error: import file '{}' not found", file)
                }
                return true
            }
            if (module == null) return true

            for (nested in module.imports()) {
                if (load(nested.moduleName, file.getParent(), dependencies)) return true
            }

            compiled.put(moduleName, module)
            dependencies.add(module)
            return false
        } finally {
            loading.remove(moduleName)
        }
    }

    companion object {
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(ModuleLoader::class.java)

        private const val STD = "std"

        @Throws(IOException::class)
        fun parseFile(file: Path, moduleName: String?): ModuleAST? {
            val lexer: src.Lexer.Lexer = src.Lexer.Lexer(Files.newInputStream(file))
            return parseUnit(src.Parser.Parser(lexer), moduleName)
        }

        @Throws(IOException::class)
        private fun parseStd(): ModuleAST? {
            ModuleLoader::class.java.getResourceAsStream("/" + STD + ".z").use { input ->
                if (input == null) {
                    logger.error("Error: the standard library is missing from the compiler")
                    return null
                }
                return parseUnit(src.Parser.Parser(src.Lexer.Lexer(input)), STD)
            }
        }


        @Throws(IOException::class)
        fun parseUnit(parser: src.Parser.Parser, moduleName: String?): ModuleAST? {
            StructRegistry.clear()
            val module: ModuleAST = ModuleAST(moduleName)
            var declarationsStarted = false

            while (!parser.atEOF()) {
                val token: src.Lexer.Lexer.Tokens? =
                    src.Lexer.Lexer.Tokens.Companion.fromValues(parser.getContext().getCurTok())

                if (token == src.Lexer.Lexer.Tokens.IMPORT) {
                    if (declarationsStarted) {
                        logger.error("Error: 'import' must appear before any 'extern' or definition")
                        return null
                    }
                    val anImport: ImportAST? = parser.parseImport()
                    if (anImport == null) return null
                    module.addImport(anImport)
                    continue
                }

                declarationsStarted = true

                if (token == src.Lexer.Lexer.Tokens.EXTERN) {
                    val extern: ExternExprAST? = parser.parseExtern()
                    if (extern == null) return null
                    module.addExtern(extern)
                } else if (token == src.Lexer.Lexer.Tokens.FUNC) {
                    val function: FunctionAST? = parser.parseDefinition()
                    if (function == null) return null
                    module.addFunction(function)
                } else if (token == src.Lexer.Lexer.Tokens.STRUCT) {
                    val struct: StructDeclAST? = parser.parseStructDecl()
                    if (struct == null) return null
                    // StructRegistry already holds the type; no need to store the AST
                } else if (parser.atTypeDeclaration()) {
                    val variable: DeclaratorExprAST? = parser.parseVariable(true)
                    if (variable == null) return null
                    module.addVar(variable)
                } else {
                    if (logger.isErrorEnabled()) {
                        val found: String? =
                            if (token == null) parser.getContext().getCurTok().toString() else token.description
                        logger.error("Error: unexpected token at top level: {}", found)
                    }
                    return null
                }
            }

            return module
        }
    }
}
