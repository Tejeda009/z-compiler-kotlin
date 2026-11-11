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
 * A conditional: `if (cond) { } eif (cond) { } else { }`.
 *
 *
 * `eif` is the language's `else if`. There can be any
 * number of them; the final `else` is optional.
 */
class IfAST(branches: MutableList<Branch?>, elseBody: ExprAST?) : ExprAST() {
    /**
     * One condition and its body. The first is the `if`, the rest are `eif`s.
     */
    class Branch(cond: ExprAST?, body: ExprAST?) {
        val cond: ExprAST?
        val body: ExprAST?

        init {
            this.cond = cond
            this.body = body
        }
    }

    private val branches: MutableList<Branch?>
    private val elseBody: ExprAST? // may be null

    init {
        this.branches = java.util.List.copyOf<Branch?>(branches)
        this.elseBody = elseBody
    }

    /** @return the `if` and `eif` branches, in order
     */
    fun branches(): MutableList<Branch?> {
        return branches
    }

    /** @return the `else` body, or null if there is none
     */
    fun elseBody(): ExprAST? {
        return elseBody
    }

    override fun type(builder: IRBuilder?): ZType {
        return ZType.VOID
    }

    override fun codeGen(builder: IRBuilder): LLVMTextUnit? {
        val condLabels: MutableList<String?> = java.util.ArrayList<String?>()
        val thenLabels: MutableList<String?> = java.util.ArrayList<String?>()
        for (i in branches.indices) {
            condLabels.add(builder.nextLabel("if.cond"))
            thenLabels.add(builder.nextLabel("if.then"))
        }
        val elseLabel: String? = if (elseBody != null) builder.nextLabel("if.else") else null
        val endLabel: String = builder.nextLabel("if.end")

        builder.emitBr(condLabels.getFirst())

        for (i in branches.indices) {
            builder.emitLabel(condLabels.get(i))
            val condReg: String? = Conditions.emitI1(builder, branches.get(i)!!.cond)
            if (condReg == null) {
                builder.markFailed()
                return null
            }

            val falseTarget: String
            if (i + 1 < branches.size) {
                falseTarget = condLabels.get(i + 1)!!
            } else if (elseBody != null) {
                falseTarget = elseLabel!!
            } else {
                falseTarget = endLabel
            }
            builder.emitCondBr(condReg, thenLabels.get(i), falseTarget)

            builder.emitLabel(thenLabels.get(i))
            branches.get(i)!!.body.codeGen(builder)
            if (builder.hasFailed()) return null
            if (!builder.isTerminated()) {
                builder.emitBr(endLabel)
            }
        }

        if (elseBody != null) {
            builder.emitLabel(elseLabel)
            elseBody.codeGen(builder)
            if (builder.hasFailed()) return null
            if (!builder.isTerminated()) {
                builder.emitBr(endLabel)
            }
        }

        builder.emitLabel(endLabel)
        return null
    }
}