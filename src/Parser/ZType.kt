package src.Parser

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
 * The types Z can name, and what each one is in LLVM.
 *
 *
 * `uint32` and `uint64` share their LLVM type with
 * `int32` and `int64`: LLVM does not distinguish signed from
 * unsigned integers in the type system, only in the instructions that use
 * them. The signedness is carried here so that
 * [src.AST.BinaryExprAST] picks `udiv`/`ult` over
 * `sdiv`/`slt`, and [src.AST.ExprAST.coerce] picks
 * `zext`/`uitofp` over `sext`/`sitofp`.
 *
 *
 * `str` is an `i8*`. The language has no string literals
 * yet, so the only thing a `str` can do for now is be declared and
 * passed around; it starts life as a null pointer.
 */
enum class ZType(
    private val zName: String,
    private val llvm: String,
    private val initial: String,
    private val bits: Int,
    private val kind: Kind
) : TypeRef {
    VARARG("...", "...", "null", 64, src.Parser.ZType.Kind.VARARG),
    PTR("ptr", "ptr", "null", 64, src.Parser.ZType.Kind.POINTER),
    INT32_PTR("int32*", "ptr", "null", 64, src.Parser.ZType.Kind.POINTER),
    INT64_PTR("int64*", "ptr", "null", 64, src.Parser.ZType.Kind.POINTER),
    UINT32_PTR("uint32*", "ptr", "null", 64, src.Parser.ZType.Kind.POINTER),
    UINT64_PTR("uint64*", "ptr", "null", 64, src.Parser.ZType.Kind.POINTER),
    FLT32_PTR("flt32*", "ptr", "null", 64, src.Parser.ZType.Kind.POINTER),
    FLT64_PTR("flt64*", "ptr", "null", 64, src.Parser.ZType.Kind.POINTER),
    BOOL_PTR("bool*", "ptr", "null", 64, src.Parser.ZType.Kind.POINTER),
    STR_PTR("str*", "ptr", "null", 64, src.Parser.ZType.Kind.POINTER),
    INT32("int32", "i32", "0", 32, src.Parser.ZType.Kind.SIGNED),
    INT64("int64", "i64", "0", 64, src.Parser.ZType.Kind.SIGNED),
    UINT32("uint32", "i32", "0", 32, src.Parser.ZType.Kind.UNSIGNED),
    UINT64("uint64", "i64", "0", 64, src.Parser.ZType.Kind.UNSIGNED),
    FLT32("flt32", "float", "0.0", 32, src.Parser.ZType.Kind.FLOAT),
    FLT64("flt64", "double", "0.0", 64, src.Parser.ZType.Kind.FLOAT),
    BOOL("bool", "i1", "false", 1, src.Parser.ZType.Kind.BOOL),
    STRING("str", "{ i8*, i64 }", "zeroinitializer", 0, src.Parser.ZType.Kind.STRING),
    VOID("void", "void", "", 0, src.Parser.ZType.Kind.VOID);

    /** What family a type belongs to.  */
    private enum class Kind {
        POINTER, SIGNED, UNSIGNED, FLOAT, BOOL, STRING, VOID, VARARG
    }

    /** @return the name the language spells this type with
     */
    override fun zName(): String {
        return zName
    }

    /** @return the LLVM type: `i32`, `double`, …
     */
    override fun llvm(): String {
        return llvm
    }

    /** @return the LLVM literal text for a variable declared but never assigned
     */
    override fun initial(): String {
        return initial
    }

    /** @return the width in bits
     */
    override fun bits(): Int {
        return bits
    }

    val isIntegral: Boolean
        /** @return true for the integer types: signed, unsigned, and bool
         */
        get() = kind == src.Parser.ZType.Kind.SIGNED || kind == src.Parser.ZType.Kind.UNSIGNED || kind == src.Parser.ZType.Kind.BOOL

    val isFloat: Boolean
        /** @return true for `flt32` and `flt64`
         */
        get() = kind == src.Parser.ZType.Kind.FLOAT

    val isSigned: Boolean
        /** @return true for the signed integer types
         */
        get() = kind == src.Parser.ZType.Kind.SIGNED

    val isUnsigned: Boolean
        /** @return true for `uint32` and `uint64`
         */
        get() = kind == src.Parser.ZType.Kind.UNSIGNED

    val isBoolean: Boolean
        /** @return true for a value that is a truth value rather than a number
         */
        get() = kind == src.Parser.ZType.Kind.BOOL

    val isString: Boolean
        /** @return true for `str`, an `i8*`
         */
        get() = kind == src.Parser.ZType.Kind.STRING

    val isVoid: Boolean
        /** @return true for [.VOID]: a statement that produces nothing
         */
        get() = kind == src.Parser.ZType.Kind.VOID

    /**
     * Prints a whole number in the right LLVM syntax for this type.
     *
     * @param value the value to print
     * @return literal text valid for this type
     */
    @Throws(java.lang.IllegalStateException::class)
    override fun literal(value: Long): String? {
        return when (kind) {
            src.Parser.ZType.Kind.BOOL -> (value != 0L).toString()
            src.Parser.ZType.Kind.SIGNED, src.Parser.ZType.Kind.UNSIGNED -> if (bits == 32) value.toInt()
                .toString() else value.toString()

            src.Parser.ZType.Kind.FLOAT -> floatingLiteral(value.toDouble())
            src.Parser.ZType.Kind.POINTER, src.Parser.ZType.Kind.STRING, src.Parser.ZType.Kind.VOID -> throw java.lang.IllegalStateException(
                zName + " has no literal"
            )

            src.Parser.ZType.Kind.VARARG -> throw java.lang.IllegalStateException(zName + "can't be vararg")
        }
    }

    /**
     * Prints a fractional number in the right LLVM syntax for this type.
     *
     * @param value the value to print
     * @return literal text valid for this type
     */
    @Throws(java.lang.IllegalStateException::class)
    override fun literal(value: Double): String? {
        return when (kind) {
            src.Parser.ZType.Kind.BOOL -> (value != 0.0).toString()
            src.Parser.ZType.Kind.SIGNED, src.Parser.ZType.Kind.UNSIGNED -> if (bits == 32) value.toInt()
                .toString() else value.toLong().toString()

            src.Parser.ZType.Kind.FLOAT -> floatingLiteral(value)
            src.Parser.ZType.Kind.POINTER, src.Parser.ZType.Kind.STRING, src.Parser.ZType.Kind.VOID -> throw java.lang.IllegalStateException(
                zName + " has no literal"
            )

            src.Parser.ZType.Kind.VARARG -> throw java.lang.IllegalStateException(zName + "can't be vararg")
        }
    }

    private fun floatingLiteral(value: Double): String? {
        if (bits == 64) return value.toString()

        val narrowed = value.toFloat()
        val decimal = narrowed.toString()
        if (java.lang.Float.isFinite(narrowed) && decimal.toDouble() == narrowed.toDouble()) {
            return decimal
        }
        return "0x" + String.format("%016X", java.lang.Double.doubleToRawLongBits(narrowed.toDouble()))
    }

    val isPointer: Boolean
        get() = kind == src.Parser.ZType.Kind.POINTER

    /** @return pointer to this type, or PTR (void*) for types without a variant
     */
    override fun pointerTo(): ZType {
        return when (this) {
            INT32 -> INT32_PTR
            INT64 -> INT64_PTR
            UINT32 -> UINT32_PTR
            UINT64 -> UINT64_PTR
            FLT32 -> FLT32_PTR
            FLT64 -> FLT64_PTR
            BOOL -> BOOL_PTR
            STRING -> STR_PTR
            else -> PTR
        }
    }

    /** @return the type this pointer points to, or null for PTR / non-pointers
     */
    override fun elementType(): ZType? {
        return when (this) {
            INT32_PTR -> INT32
            INT64_PTR -> INT64
            UINT32_PTR -> UINT32
            UINT64_PTR -> UINT64
            FLT32_PTR -> FLT32
            FLT64_PTR -> FLT64
            BOOL_PTR -> BOOL
            STR_PTR -> STRING
            else -> null
        }
    }

    val isStruct: Boolean
        get() = false

    val isVararg: Boolean
        get() = kind == src.Parser.ZType.Kind.VARARG

    companion object {
        /**
         * @param name a type name as written in a program
         * @return the type it names
         * @throws IllegalArgumentException if no such type exists
         */
        fun fromZName(name: String?): ZType {
            for (type in ZType.entries) {
                if (type.zName == name) return type
            }
            throw java.lang.IllegalArgumentException("no such type: " + name)
        }

        /** @return whether `name` is a type this language has
         */
        fun isType(name: String?): Boolean {
            for (type in ZType.entries) {
                if (type.zName == name) return true
            }
            return false
        }

        /**
         * The type two operands are computed in.
         *
         *
         * Same-width signed and unsigned integers unify to unsigned, the way
         * C does. Different widths pick the wider one. A string cannot be
         * combined with anything else.
         */
        fun unify(a: ZType?, b: ZType?): ZType? {
            if (a == null) return b
            if (b == null) return a
            if (a == b) return a
            if (a.isVoid) return b
            if (b.isVoid) return a

            if (a.isBoolean || b.isBoolean) {
                if (a.isBoolean && b.isBoolean) return BOOL
                throw java.lang.IllegalArgumentException("a truth value cannot be combined with " + other(a, b))
            }

            kotlin.require(!(a.isPointer || b.isPointer)) { "cannot combine " + a.zName() + " with " + b.zName() }

            kotlin.require(!(a.isString || b.isString)) { "str cannot be combined with " + other(a, b) }

            if (a.isFloat && b.isFloat) return if (a.bits() >= b.bits()) a else b
            if (a.isFloat || b.isFloat) {
                val floating = if (a.isFloat) a else b
                val integer = if (a.isFloat) b else a
                return if (integer.bits() > floating.bits()) FLT64 else floating
            }

            // Both are integers. If either is unsigned the result is unsigned,
            // at the wider of the two widths. Otherwise, both are signed.
            val width: Int = kotlin.math.max(a.bits(), b.bits())
            if (a.isUnsigned || b.isUnsigned) {
                return if (width == 64) UINT64 else UINT32
            }
            return if (width == 64) INT64 else INT32
        }

        private fun other(a: ZType, b: ZType): String {
            return (if (a.isBoolean || a.isString) b else a).zName()
        }
    }
}