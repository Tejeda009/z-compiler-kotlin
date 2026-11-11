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

abstract class ExprAST {
    /**
     * What this node produces.
     *
     */
    abstract fun type(builder: IRBuilder?): TypeRef?

    /**
     * Emits the code for this node.
     *
     * @param builder the module being built
     * @return the value produced, or `null` if generation failed — in which
     * case an error has already been reported
     */
    abstract fun codeGen(builder: IRBuilder?): LLVMTextUnit?

    /**
     * Evaluates this node at compile time when it does not need instructions.
     *
     * @param builder the module being built
     * @return the constant value, or `null` when the node needs code generation
     */
    open fun constantValue(builder: IRBuilder?): LLVMTextUnit? {
        return null
    }

    /**
     * Converts a generated llvmTextUnit to `target`, emitting the conversion.
     *
     * @return the llvmTextUnit in the requested type, or the original when it already is
     */
    open fun coerce(builder: IRBuilder, llvmTextUnit: LLVMTextUnit?, target: TypeRef?): LLVMTextUnit? {
        if (llvmTextUnit == null || target == null) return llvmTextUnit
        val from: TypeRef = llvmTextUnit.type
        if (from === target) return llvmTextUnit

        if (from.isString() || target.isString()) {
            java.lang.System.err.println("Error: cannot convert between str and " + (if (from.isString()) target.zName() else from.zName()))
            builder.markFailed()
            return null
        }

        if (from.isPointer() || target.isPointer()) {
            java.lang.System.err.println("Error: cannot convert between " + from.zName() + " and " + target.zName())
            builder.markFailed()
            return null
        }

        val result: String = builder.nextRegister()

        if (from.isFloat() && target.isFloat()) {
            // float is narrower than double; double is widened to it otherwise.
            builder.appendLine(result + " = " + (if (target.bits() < from.bits()) "fptrunc " else "fpext ") + from.llvm() + " " + llvmTextUnit.text + " to " + target.llvm())
            return LLVMTextUnit(result, target)
        }
        if (from.isFloat() && target.isIntegral()) {
            val op = if (target.isUnsigned()) "fptoui " else "fptosi "
            builder.appendLine(result + " = " + op + from.llvm() + " " + llvmTextUnit.text + " to " + target.llvm())
            return LLVMTextUnit(result, target)
        }
        if (from.isIntegral() && target.isFloat()) {
            val op = if (from.isUnsigned() || from.isBoolean()) "uitofp " else "sitofp "
            builder.appendLine(result + " = " + op + from.llvm() + " " + llvmTextUnit.text + " to " + target.llvm())
            return LLVMTextUnit(result, target)
        }
        if (from.isIntegral() && target.isIntegral()) {
            if (from.bits() > target.bits()) {
                builder.appendLine(result + " = trunc " + from.llvm() + " " + llvmTextUnit.text + " to " + target.llvm())
                return LLVMTextUnit(result, target)
            }
            if (from.bits() < target.bits()) {
                val op = if (from.isUnsigned() || from.isBoolean()) "zext " else "sext "
                builder.appendLine(result + " = " + op + from.llvm() + " " + llvmTextUnit.text + " to " + target.llvm())
                return LLVMTextUnit(result, target)
            }
            // Same width, different signedness. The bits don't change; only
            // the interpretation does. No instruction needed.
            return LLVMTextUnit(llvmTextUnit.text, target)
        }

        // Anything else (str, void) cannot be coerced.
        return llvmTextUnit
    }
}
