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
 * A for loop: `for (type name -> init | cond | op) { body }`.
 *
 *
 * The loop variable is declared in the current scope, initialized,
 * checked before each iteration, updated at the end of each iteration,
 * and stays visible after the loop.
 */
class ForAST(varType: TypeRef, varName: String?, init: ExprAST?, cond: ExprAST?, update: ExprAST, body: ExprAST) :
    ExprAST() {
    private val varType: TypeRef
    private val varName: String?
    private val init: ExprAST?
    private val cond: ExprAST?
    private val update: ExprAST
    private val body: ExprAST

    init {
        this.varType = varType
        this.varName = varName
        this.init = init
        this.cond = cond
        this.update = update
        this.body = body
    }

    /** @return the declared type of the loop variable
     */
    fun varType(): TypeRef? {
        return varType
    }

    /** @return the loop variable name
     */
    fun varName(): String? {
        return varName
    }

    /** @return the initializer expression
     */
    fun init(): ExprAST? {
        return init
    }

    /** @return the loop condition
     */
    fun cond(): ExprAST? {
        return cond
    }

    /** @return the update expression
     */
    fun update(): ExprAST? {
        return update
    }

    /** @return the loop body
     */
    fun body(): ExprAST? {
        return body
    }

    override fun type(builder: IRBuilder?): TypeRef {
        return ZType.VOID
    }

    override fun codeGen(builder: IRBuilder): LLVMTextUnit? {
        builder.symbols().enterScope()
        try {
            if (builder.symbols().lookup(varName) != null) {
                logger.error("Error: Shadowing or local redeclaration of : {}", varName)
                builder.markFailed()
                return null
            }
            val slot: String = builder.emitAlloca(varName, varType.llvm())
            builder.symbols().define(varName, SymbolInfo(varName, varType, slot, false))

            if (init != null) {
                if (verify(builder, slot, init)) return null
            } else {
                builder.emitStore(varType.initial(), varType.llvm(), slot)
            }

            val condLabel: String = builder.nextLabel("for.cond")
            val bodyLabel: String = builder.nextLabel("for.body")
            val incLabel: String = builder.nextLabel("for.inc")
            val endLabel: String = builder.nextLabel("for.end")


            builder.emitBr(condLabel)
            builder.emitLabel(condLabel)


            val condReg: String? = Conditions.emitI1(builder, cond)
            if (condReg == null) {
                builder.markFailed()
                return null
            }
            builder.appendLine("br i1 " + condReg + ", label %" + bodyLabel + ", label %" + endLabel + ", !prof !{!\"branch_weights\", i32 1000, i32 1}")


            builder.emitLabel(bodyLabel)
            builder.enterLoop(incLabel, endLabel)
            builder.appendLine("call void @llvm.assume(i1 " + condReg + ")")
            val loop: src.Codegen.Loop
            try {
                body.codeGen(builder)
            } finally {
                loop = builder.exitLoop()
            }
            if (builder.hasFailed()) return null
            // the update is only needed when the body can reach it
            if (builder.isTerminated() && !loop.isContinued()) {
                builder.emitLabel(endLabel)
                return null
            }
            if (!builder.isTerminated()) {
                builder.emitBr(incLabel)
            }

            builder.emitLabel(incLabel)
            if (verify(builder, slot, update)) return null
            builder.emitBrWithLoopMetadata(condLabel, 4, 4)

            builder.emitLabel(endLabel)
            return null
        } finally {
            builder.symbols().exitScope()
        }
    }

    private fun verify(builder: IRBuilder, slot: String, init: ExprAST): Boolean {
        val produced: LLVMTextUnit? = init.codeGen(builder)
        if (produced == null) {
            builder.markFailed()
            return true
        }
        val fitted: LLVMTextUnit? = init.coerce(builder, produced, varType)
        if (fitted == null) {
            builder.markFailed()
            return true
        }
        builder.emitStore(fitted.text, varType.llvm(), slot)
        return false
    }

    companion object {
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(ForAST::class.java)
    }
}