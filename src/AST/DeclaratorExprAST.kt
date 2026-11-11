package src.AST

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
 *
 */
class DeclaratorExprAST(type: TypeRef, name: String?, initialiser: ExprAST?, constant: Boolean, globals: Boolean) :
    ExprAST() {
    private val type: TypeRef
    private val name: String?
    private val initialiser: ExprAST?
    private val constant: Boolean
    private val global: Boolean

    init {
        this.type = type
        this.name = name
        this.initialiser = initialiser
        this.constant = constant
        this.global = globals
    }

    /** @return the declared type
     */
    fun declaredType(): TypeRef? {
        return type
    }

    /** @return the declared name
     */
    fun name(): String? {
        return name
    }

    override fun type(builder: IRBuilder?): TypeRef? {
        return type
    }

    override fun codeGen(builder: IRBuilder): LLVMTextUnit? {
        if (global) return genGlobal(builder)

        if (builder.symbols().lookup(name) != null) {
            logger.error("Error: Shadowing or redeclaration of the local variable: {}", name)
            builder.markFailed()
            return null
        }

        val text: String?
        if (initialiser == null) {
            text = type.initial()
        } else {
            val llvmTextUnit: LLVMTextUnit? = initialiser.codeGen(builder)
            if (llvmTextUnit == null) {
                builder.markFailed()
                return null
            }
            val fitted: LLVMTextUnit? = initialiser.coerce(builder, llvmTextUnit, type)
            if (fitted == null) return null
            text = fitted.text
        }

        val slot: String = builder.emitAlloca(name, type.llvm())
        builder.emitStore(text, type.llvm(), slot)
        builder.symbols().define(name, SymbolInfo(name, type, slot, constant))
        return null
    }

    private fun genGlobal(builder: IRBuilder): LLVMTextUnit? {
        val key: String? = builder.qualify(name) // es. "library.global"
        if (builder.symbols().lookup(key) != null) {
            logger.error("Error: global '{}' is defined more than once", key)
            builder.markFailed()
            return null
        }

        var init: String? = type.initial()
        if (initialiser != null) {
            val emittedBefore: Int = builder.getIR().length
            val llvmTextUnit: LLVMTextUnit? = initialiser.constantValue(builder)
            if (llvmTextUnit == null) {
                logger.error("Error: global '{}' must be initialized with a constant expression", key)
                builder.markFailed()
                return null
            }
            val fitted: LLVMTextUnit? = initialiser.coerce(builder, llvmTextUnit, type)
            if (fitted == null) {
                builder.markFailed()
                return null
            }
            if (builder.getIR().length != emittedBefore) {
                logger.error("Error: global '{}' must be initialized with a constant expression", key)
                builder.markFailed()
                return null
            }
            init = fitted.text
        }

        val irName = "@" + builder.irNameOf(name) // es. "@library_global"
        builder.appendToGlobals(irName + " = global " + type.llvm() + " " + init + "\n")
        builder.symbols().defineGlobal(key, SymbolInfo(name, type, irName, constant))
        return null
    }

    companion object {
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(DeclaratorExprAST::class.java)
    }
}
