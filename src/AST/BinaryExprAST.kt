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

class BinaryExprAST(private val op: String, left: ExprAST, right: ExprAST) : ExprAST() {
    private val left: ExprAST
    private val right: ExprAST

    init {
        this.left = left
        this.right = right
    }

    /** @return the first character of the operator, as written in the source
     */
    fun operator(): Char {
        return op.get(0)
    }

    /** @return the left operand
     */
    fun left(): ExprAST? {
        return left
    }

    /** @return the right operand
     */
    fun right(): ExprAST? {
        return right
    }

    val isComparison: Boolean
        get() = when (op) {
            "<", "<=", ">", ">=", "==", "!=" -> true
            else -> false
        }

    private fun operandType(builder: IRBuilder): TypeRef? {
        val a: TypeRef = left.type(builder)
        val b: TypeRef = right.type(builder)
        if (a.isStruct() || b.isStruct()) {
            logger.error("Error: struct cannot be used in a binary expression")
            builder.markFailed()
            return ZType.INT32
        }
        try {
            return ZType.Companion.unify(a as ZType, b as ZType)
        } catch (e: java.lang.IllegalArgumentException) {
            logger.error("Error: {}", e.message)
            return a
        }
    }

    override fun type(builder: IRBuilder): TypeRef? {
        return operandType(builder)
    }

    override fun codeGen(builder: IRBuilder): LLVMTextUnit? {
        val lhs: LLVMTextUnit? = left.codeGen(builder)
        if (lhs == null) {
            builder.markFailed()
            return null
        }
        val rhs: LLVMTextUnit? = right.codeGen(builder)
        if (rhs == null) {
            builder.markFailed()
            return null
        }

        val common: TypeRef? = operandType(builder)
        val a: LLVMTextUnit? = left.coerce(builder, lhs, common)
        val b: LLVMTextUnit? = right.coerce(builder, rhs, common)
        if (a == null || b == null) return null

        if (op == "+" && a.type.isString() && b.type.isString()) {
            return emitStringConcat(builder, a, b)
        }

        if (this.isComparison) {
            val ordered: Boolean = a.type.isFloat()
            val cmpFlags = if (ordered && "fast" == builder.fastMathFlags()) "fast " else ""
            val compared: String = builder.nextRegister()
            builder.appendLine(
                compared + " = " + (if (ordered) "fcmp " + cmpFlags else "icmp ") + predicate(
                    ordered,
                    a.type
                ) + " " + a.type.llvm() + " " + a.text + ", " + b.text
            )
            val result: String = builder.nextRegister()
            builder.appendLine(result + " = zext i1 " + compared + " to i32")
            builder.setRegisterType(result, ZType.INT32.llvm())
            return LLVMTextUnit(result, ZType.INT32)
        }

        val opcode = arithmeticOpcode(a.type)
        if (opcode == null) {
            logger.error("Unsupported operation: {}", op)
            builder.markFailed()
            return null
        }
        val flags = if (a.type.isFloat() && !builder.fastMathFlags().isEmpty()) " " + builder.fastMathFlags() else ""
        val result: String = builder.nextRegister()
        builder.appendLine(result + " = " + opcode + flags + " " + a.type.llvm() + " " + a.text + ", " + b.text)

        builder.setRegisterType(result, a.type.llvm())
        return LLVMTextUnit(result, a.type)
    }

    fun predicate(ordered: Boolean, type: TypeRef): String {
        if (ordered) {
            return when (op) {
                "<" -> "olt"
                "<=" -> "ole"
                ">" -> "ogt"
                ">=" -> "oge"
                "==" -> "oeq"
                "!=" -> "une"
                else -> throw java.lang.IllegalStateException("not a comparison: " + op)
            }
        }
        val unsigned: Boolean = type.isUnsigned()
        return when (op) {
            "<" -> if (unsigned) "ult" else "slt"
            "<=" -> if (unsigned) "ule" else "sle"
            ">" -> if (unsigned) "ugt" else "sgt"
            ">=" -> if (unsigned) "uge" else "sge"
            "==" -> "eq"
            "!=" -> "ne"
            else -> throw java.lang.IllegalStateException("not a comparison: " + op)
        }
    }

    private fun arithmeticOpcode(type: TypeRef?): String? {
        if (type !is ZType) {
            return null
        }
        if (type.isFloat()) {
            return when (op) {
                "+" -> "fadd"
                "-" -> "fsub"
                "*" -> "fmul"
                "/" -> "fdiv"
                "%" -> "frem"
                else -> null
            }
        }
        val unsigned = type.isUnsigned() || type.isBoolean()
        return when (op) {
            "+" -> if (unsigned) "add" else "add nsw"
            "-" -> if (unsigned) "sub" else "sub nsw"
            "*" -> if (unsigned) "mul" else "mul nsw"
            "/" -> if (unsigned) "udiv" else "sdiv"
            "%" -> if (unsigned) "urem" else "srem"
            else -> null
        }
    }

    /**
     * Concatenates two fat strings.
     *
     *
     * `a` and `b` are `{ i8*, i64 }` values. The
     * bytes are copied into a fresh block from the bump allocator, then
     * the result is built as a new `{ ptr, len_a + len_b }`. No
     * `strlen`: the lengths are already in the struct.
     */
    private fun emitStringConcat(builder: IRBuilder, a: LLVMTextUnit, b: LLVMTextUnit): LLVMTextUnit {
        val aPtr: String = builder.nextRegister()
        builder.appendLine(aPtr + " = extractvalue { i8*, i64 } " + a.text + ", 0")
        val aLen: String = builder.nextRegister()
        builder.appendLine(aLen + " = extractvalue { i8*, i64 } " + a.text + ", 1")

        val bPtr: String = builder.nextRegister()
        builder.appendLine(bPtr + " = extractvalue { i8*, i64 } " + b.text + ", 0")
        val bLen: String = builder.nextRegister()
        builder.appendLine(bLen + " = extractvalue { i8*, i64 } " + b.text + ", 1")

        val total: String = builder.nextRegister()
        builder.appendLine(total + " = add i64 " + aLen + ", " + bLen)

        val allocSize: String = builder.nextRegister()
        builder.appendLine(allocSize + " = add i64 " + total + ", 1")

        val buf: String? = builder.emitBumpAlloc(allocSize)
        builder.emitAssumeAfterAlloc(buf, allocSize)

        builder.emitMemcpy(buf, aPtr, aLen)

        val bufB: String = builder.nextRegister()
        builder.appendLine(bufB + " = getelementptr inbounds i8, ptr " + buf + ", i64 " + aLen)
        builder.emitMemcpy(bufB, bPtr, bLen)

        val nullSlot: String = builder.nextRegister()
        builder.appendLine(nullSlot + " = getelementptr inbounds i8, ptr " + buf + ", i64 " + total)
        builder.appendLine("store i8 0, ptr " + nullSlot + ", align 1")

        val r1: String = builder.nextRegister()
        builder.appendLine(r1 + " = insertvalue { i8*, i64 } undef, ptr " + buf + ", 0")
        val r2: String = builder.nextRegister()
        builder.appendLine(r2 + " = insertvalue { i8*, i64 } " + r1 + ", i64 " + total + ", 1")

        builder.setRegisterType(r2, ZType.STRING.llvm())
        return LLVMTextUnit(r2, ZType.STRING)
    }

    companion object {
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(BinaryExprAST::class.java)
    }
}
