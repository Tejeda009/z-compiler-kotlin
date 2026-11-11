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

class TestAstCodeGen {
    private class Literal(type: TypeRef, text: String) : ExprAST() {
        private val type: TypeRef
        private val text: String

        init {
            this.type = type
            this.text = text
        }

        override fun type(builder: IRBuilder?): TypeRef {
            return type
        }

        override fun codeGen(builder: IRBuilder?): LLVMTextUnit {
            return LLVMTextUnit(text, type)
        }

        override fun constantValue(builder: IRBuilder?): LLVMTextUnit {
            return codeGen(builder)
        }
    }

    private class ImpureConstant : ExprAST() {
        override fun type(builder: IRBuilder?): TypeRef {
            return ZType.INT32
        }

        override fun codeGen(builder: IRBuilder): LLVMTextUnit {
            return constantValue(builder)
        }

        override fun constantValue(builder: IRBuilder): LLVMTextUnit {
            builder.appendLine("%impure = add i32 1, 2")
            return LLVMTextUnit("3", ZType.INT32)
        }
    }

    @AfterEach
    fun clean() {
        StructRegistry.clear()
    }

    @org.junit.jupiter.api.Test
    fun testFieldAccessTypePaths() {
        val point: StructType = StructType(
            "Point",
            java.util.List.of<src.Parser.StructType.Field>(
                src.Parser.StructType.Field("x", ZType.INT64),
                src.Parser.StructType.Field(
                    "inner",
                    StructType(
                        "Inner",
                        java.util.List.of<src.Parser.StructType.Field?>(src.Parser.StructType.Field("v", ZType.BOOL))
                    )
                )
            )
        )
        val builder: IRBuilder = IRBuilder()
        builder.symbols().defineGlobal("math.sqrt", SymbolInfo("sqrt", ZType.FLT32, "@sqrt", false))
        builder.symbols().define("p", SymbolInfo("p", point, "%p", false))

        Assertions.assertSame(ZType.FLT32, FieldAccessExprAST(VarRefExprAST("math"), "sqrt").type(builder))
        Assertions.assertSame(ZType.INT64, FieldAccessExprAST(VarRefExprAST("p"), "x").type(builder))
        Assertions.assertSame(ZType.INT32, FieldAccessExprAST(VarRefExprAST("p"), "missing").type(builder))
        Assertions.assertSame(
            ZType.BOOL,
            FieldAccessExprAST(FieldAccessExprAST(VarRefExprAST("p"), "inner"), "v").type(builder)
        )
        Assertions.assertSame(
            ZType.INT32,
            FieldAccessExprAST(FieldAccessExprAST(VarRefExprAST("p"), "inner"), "missing").type(builder)
        )
        Assertions.assertSame(ZType.INT32, FieldAccessExprAST(NumberExprAST("1", 1), "x").type(builder))
        Assertions.assertSame(
            ZType.INT32,
            FieldAccessExprAST(FieldAccessExprAST(NumberExprAST("1", 1), "x"), "y").type(builder)
        )
    }

    @org.junit.jupiter.api.Test
    fun testModuleVariableFieldAccess() {
        val builder: IRBuilder = IRBuilder()
        builder.symbols().defineGlobal("math.sqrt", SymbolInfo("sqrt", ZType.FLT64, "@sqrt", false))

        val llvmTextUnit: LLVMTextUnit? = FieldAccessExprAST(VarRefExprAST("math"), "sqrt").codeGen(builder)

        Assertions.assertNotNull(llvmTextUnit)
        Assertions.assertSame(ZType.FLT64, llvmTextUnit.type)
        Assertions.assertTrue(builder.getIR().contains("load double, ptr @sqrt"), builder.getIR())
    }

    @org.junit.jupiter.api.Test
    fun testFieldAccessCodeGenFailsWithoutAStructBase() {
        Assertions.assertNull(FieldAccessExprAST(NumberExprAST("1", 1), "x").codeGen(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testExternAccessorsAndCodeGen() {
        val prototype: PrototypeAST = PrototypeAST(
            "sqrt",
            java.util.List.of<src.AST.PrototypeAST.Param?>(src.AST.PrototypeAST.Param("flt64", "", false))
        )
        val extern: ExternExprAST = ExternExprAST(prototype, "flt64")
        val builder: IRBuilder = IRBuilder()

        Assertions.assertSame(prototype, extern.getProto())
        Assertions.assertEquals("flt64", extern.getType())
        Assertions.assertSame(ZType.VOID, extern.type(builder))
        val declaration: LLVMTextUnit? = extern.codeGen(builder)
        Assertions.assertNotNull(declaration)
        Assertions.assertTrue(declaration.text.contains("declare double @sqrt(double)"), declaration.text)
        Assertions.assertTrue(Objects.requireNonNull<LLVMTextUnit?>(extern.codeGen(builder)).text.isEmpty())
    }

    @org.junit.jupiter.api.Test
    fun testExternConflict() {
        val prototype: PrototypeAST = PrototypeAST(
            "sqrt",
            java.util.List.of<src.AST.PrototypeAST.Param?>(src.AST.PrototypeAST.Param("flt64", "", false))
        )
        val builder: IRBuilder = IRBuilder()
        builder.declare(prototype)

        Assertions.assertNull(ExternExprAST(prototype, "flt64").codeGen(builder))
        Assertions.assertTrue(builder.hasFailed())
    }

    @org.junit.jupiter.api.Test
    fun testCallAccessorsAndTypes() {
        val argument: NumberExprAST = NumberExprAST("1", 1)
        val call: CallExprAST = CallExprAST("f", java.util.List.of<ExprAST?>(argument))

        Assertions.assertEquals("f", call.callee())
        Assertions.assertEquals(1, call.arguments().size())
        Assertions.assertSame(ZType.FLT64, call.type(IRBuilder()))

        val external: IRBuilder = IRBuilder()
        external.declareExtern(
            PrototypeAST(
                "printf",
                java.util.List.of<src.AST.PrototypeAST.Param>(
                    src.AST.PrototypeAST.Param("str", "", false),
                    src.AST.PrototypeAST.Param("...", "", false)
                )
            ), ZType.INT32
        )
        Assertions.assertSame(
            ZType.INT32,
            CallExprAST("printf", java.util.List.of<ExprAST?>(StringExprAST("x"))).type(external)
        )

        val defined: IRBuilder = IRBuilder()
        defined.declare(PrototypeAST("f", java.util.List.of<src.AST.PrototypeAST.Param?>(), ZType.INT64))
        Assertions.assertSame(ZType.INT64, CallExprAST("f", java.util.List.of<ExprAST?>()).type(defined))

        val main: IRBuilder = IRBuilder()
        main.declare(PrototypeAST("main", java.util.List.of<src.AST.PrototypeAST.Param?>()))
        Assertions.assertSame(ZType.INT32, CallExprAST("main", java.util.List.of<ExprAST?>()).type(main))

        val floating: IRBuilder = IRBuilder()
        floating.declare(PrototypeAST("f", java.util.List.of<src.AST.PrototypeAST.Param?>()))
        Assertions.assertSame(ZType.FLT64, CallExprAST("f", java.util.List.of<ExprAST?>()).type(floating))
    }

    @org.junit.jupiter.api.Test
    fun testCallToAnUnknownFunctionFails() {
        Assertions.assertNull(CallExprAST("ghost", java.util.List.of<ExprAST?>()).codeGen(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testVariadicCallConvertsArgumentTypes() {
        val builder: IRBuilder = IRBuilder()
        builder.declareExtern(
            PrototypeAST(
                "printf",
                java.util.List.of<src.AST.PrototypeAST.Param>(
                    src.AST.PrototypeAST.Param("str", "", false),
                    src.AST.PrototypeAST.Param("...", "", false)
                )
            ), ZType.INT32
        )

        val result: LLVMTextUnit? = CallExprAST(
            "printf",
            java.util.List.of<ExprAST?>(
                Tests.TestAstCodeGen.Literal(ZType.STRING, "@format"),
                Tests.TestAstCodeGen.Literal(ZType.FLT32, "1.5"),
                Tests.TestAstCodeGen.Literal(ZType.BOOL, "true")
            )
        ).codeGen(builder)

        Assertions.assertNotNull(result)
        Assertions.assertTrue(builder.getIR().contains("call i32 (i8*, ...) @printf"), builder.getIR())
    }

    @org.junit.jupiter.api.Test
    fun testVariadicCallNeedsTheFixedArguments() {
        val builder: IRBuilder = IRBuilder()
        builder.declareExtern(
            PrototypeAST(
                "printf",
                java.util.List.of<src.AST.PrototypeAST.Param>(
                    src.AST.PrototypeAST.Param("str", "", false),
                    src.AST.PrototypeAST.Param("...", "", false)
                )
            ), ZType.INT32
        )

        Assertions.assertNull(CallExprAST("printf", java.util.List.of<ExprAST?>()).codeGen(builder))
    }

    @org.junit.jupiter.api.Test
    fun testVariadicCallWithoutFixedParameters() {
        val builder: IRBuilder = IRBuilder()
        builder.declareExtern(
            PrototypeAST(
                "log",
                java.util.List.of<src.AST.PrototypeAST.Param?>(src.AST.PrototypeAST.Param("...", "", false))
            ), ZType.INT32
        )

        val result: LLVMTextUnit? =
            CallExprAST("log", java.util.List.of<ExprAST?>(NumberExprAST("1", 1))).codeGen(builder)

        Assertions.assertNotNull(result)
        Assertions.assertTrue(builder.getIR().contains("call i32 (...) @log"), builder.getIR())
    }

    @org.junit.jupiter.api.Test
    fun testStructCannotBePassedToAnUntypedVararg() {
        val point: StructType = StructType("Point", java.util.List.of<src.Parser.StructType.Field?>())
        val builder: IRBuilder = IRBuilder()
        builder.declareExtern(
            PrototypeAST(
                "printf",
                java.util.List.of<src.AST.PrototypeAST.Param>(
                    src.AST.PrototypeAST.Param("str", "", false),
                    src.AST.PrototypeAST.Param("...", "", false)
                )
            ), ZType.INT32
        )

        Assertions.assertNull(
            CallExprAST(
                "printf",
                java.util.List.of<ExprAST>(
                    Tests.TestAstCodeGen.Literal(ZType.STRING, "@format"),
                    Tests.TestAstCodeGen.Literal(point, "point")
                )
            ).codeGen(builder)
        )
    }

    @org.junit.jupiter.api.Test
    fun testCallToMainUsesInt32() {
        val builder: IRBuilder = IRBuilder()
        builder.declare(PrototypeAST("main", java.util.List.of<src.AST.PrototypeAST.Param?>()))

        val result: LLVMTextUnit? = CallExprAST("main", java.util.List.of<ExprAST?>()).codeGen(builder)

        Assertions.assertNotNull(result)
        Assertions.assertSame(ZType.INT32, result.type)
    }

    @org.junit.jupiter.api.Test
    fun testVoidCallProducesNoValue() {
        val builder: IRBuilder = IRBuilder()
        builder.declare(PrototypeAST("f", java.util.List.of<src.AST.PrototypeAST.Param?>(), ZType.VOID))

        Assertions.assertNull(CallExprAST("f", java.util.List.of<ExprAST?>()).codeGen(builder))
        Assertions.assertTrue(builder.getIR().contains("call void @f()"), builder.getIR())
    }

    @org.junit.jupiter.api.Test
    fun testFunctionAccessorsAndReturnType() {
        val main: PrototypeAST = PrototypeAST("main", java.util.List.of<src.AST.PrototypeAST.Param?>())
        val function: FunctionAST = FunctionAST(main, RetAST(ZType.VOID))

        Assertions.assertSame(main, function.prototype())
        Assertions.assertSame(ZType.INT32, function.returnType())
        Assertions.assertSame(ZType.VOID, function.type(IRBuilder()))
        Assertions.assertSame(
            ZType.FLT64,
            FunctionAST(
                PrototypeAST("f", java.util.List.of<src.AST.PrototypeAST.Param?>()),
                RetAST(ZType.VOID)
            ).returnType()
        )
        Assertions.assertSame(
            ZType.INT64,
            FunctionAST(
                PrototypeAST("f", java.util.List.of<src.AST.PrototypeAST.Param?>(), ZType.INT64),
                RetAST(ZType.VOID)
            ).returnType()
        )
    }

    @org.junit.jupiter.api.Test
    fun testMainFallOffEmitsEachDefault() {
        Assertions.assertTrue(
            generate(
                PrototypeAST(
                    "main",
                    java.util.List.of<src.AST.PrototypeAST.Param?>(),
                    ZType.BOOL
                ), null
            ).contains("ret i1 false")
        )
        Assertions.assertTrue(
            generate(
                PrototypeAST(
                    "main",
                    java.util.List.of<src.AST.PrototypeAST.Param?>(),
                    ZType.STRING
                ), null
            ).contains("ret { i8*, i64 } zeroinitializer")
        )
        Assertions.assertTrue(
            generate(
                PrototypeAST(
                    "main",
                    java.util.List.of<src.AST.PrototypeAST.Param?>(),
                    ZType.INT32_PTR
                ), null
            ).contains("ret ptr null")
        )
        Assertions.assertTrue(
            generate(
                PrototypeAST(
                    "main",
                    java.util.List.of<src.AST.PrototypeAST.Param?>(),
                    ZType.FLT32
                ), null
            ).contains("ret float 0.0")
        )
        Assertions.assertTrue(
            generate(
                PrototypeAST(
                    "main",
                    java.util.List.of<src.AST.PrototypeAST.Param?>(),
                    ZType.FLT64
                ), null
            ).contains("ret double 0.0")
        )
        Assertions.assertTrue(
            generate(
                PrototypeAST(
                    "main",
                    java.util.List.of<src.AST.PrototypeAST.Param?>(),
                    ZType.INT32
                ), null
            ).contains("ret i32 0")
        )
        Assertions.assertTrue(
            generate(
                PrototypeAST(
                    "main",
                    java.util.List.of<src.AST.PrototypeAST.Param?>(),
                    ZType.VOID
                ), null
            ).contains("ret void")
        )
    }

    @org.junit.jupiter.api.Test
    fun testNonVoidFunctionFallingOffTheEndFails() {
        Assertions.assertNull(
            FunctionAST(
                PrototypeAST(
                    "f",
                    java.util.List.of<src.AST.PrototypeAST.Param?>(),
                    ZType.INT32
                ), null
            ).codeGen(IRBuilder())
        )
    }

    @org.junit.jupiter.api.Test
    fun testFunctionWithBodyThatFails() {
        Assertions.assertNull(
            FunctionAST(
                PrototypeAST("main", java.util.List.of<src.AST.PrototypeAST.Param?>()),
                VarRefExprAST("ghost")
            ).codeGen(IRBuilder())
        )
    }

    @org.junit.jupiter.api.Test
    fun testDuplicateParametersFail() {
        val prototype: PrototypeAST = PrototypeAST(
            "f",
            java.util.List.of<src.AST.PrototypeAST.Param>(
                src.AST.PrototypeAST.Param("int32", "x", false),
                src.AST.PrototypeAST.Param("int32", "x", false)
            ),
            ZType.VOID
        )

        Assertions.assertNull(FunctionAST(prototype, null).codeGen(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testUnknownParameterTypeFails() {
        val prototype: PrototypeAST = PrototypeAST(
            "f",
            java.util.List.of<src.AST.PrototypeAST.Param?>(src.AST.PrototypeAST.Param("Nope", "x", false)),
            ZType.VOID
        )

        Assertions.assertThrows<java.lang.IllegalArgumentException>(
            java.lang.IllegalArgumentException::class.java,
            org.junit.jupiter.api.function.Executable { FunctionAST(prototype, null).codeGen(IRBuilder()) })
    }

    @org.junit.jupiter.api.Test
    fun testVariadicFunctionSignature() {
        val prototype: PrototypeAST = PrototypeAST(
            "sum",
            java.util.List.of<src.AST.PrototypeAST.Param>(
                src.AST.PrototypeAST.Param("int32", "count", false),
                src.AST.PrototypeAST.Param("int32", "rest", true)
            ),
            ZType.VOID
        )

        val text = generate(prototype, null)

        Assertions.assertTrue(text.contains("@sum(i32 %count, ...)"), text)
        Assertions.assertTrue(text.contains("llvm.va_start"), text)
    }

    @org.junit.jupiter.api.Test
    fun testForAccessors() {
        val init: NumberExprAST = NumberExprAST("0", 0)
        val cond: NumberExprAST = NumberExprAST("1", 1)
        val update: NumberExprAST = NumberExprAST("1", 1)
        val body: BlockAST = BlockAST(java.util.List.of<ExprAST?>())
        val loop: ForAST = ForAST(ZType.INT32, "i", init, cond, update, body)

        Assertions.assertSame(ZType.INT32, loop.varType())
        Assertions.assertEquals("i", loop.varName())
        Assertions.assertSame(init, loop.init())
        Assertions.assertSame(cond, loop.cond())
        Assertions.assertSame(update, loop.update())
        Assertions.assertSame(body, loop.body())
        Assertions.assertSame(ZType.VOID, loop.type(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testForWithoutAnInitialiser() {
        val loop: ForAST = ForAST(
            ZType.INT32,
            "i",
            null,
            NumberExprAST("1", 1),
            NumberExprAST("1", 1),
            BlockAST(java.util.List.of<ExprAST?>())
        )
        val builder: IRBuilder = IRBuilder()

        Assertions.assertNull(loop.codeGen(builder))
        Assertions.assertFalse(builder.hasFailed())
    }

    @org.junit.jupiter.api.Test
    fun testForRejectsAShadowedVariable() {
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("i", SymbolInfo("i", ZType.INT32, "%i", false))
        val loop: ForAST = ForAST(
            ZType.INT32,
            "i",
            NumberExprAST("0", 0),
            NumberExprAST("1", 1),
            NumberExprAST("1", 1),
            BlockAST(java.util.List.of<ExprAST?>())
        )

        Assertions.assertNull(loop.codeGen(builder))
        Assertions.assertTrue(builder.hasFailed())
    }

    @org.junit.jupiter.api.Test
    fun testForFailuresInEachPart() {
        Assertions.assertNull(
            ForAST(
                ZType.INT32,
                "i",
                VarRefExprAST("ghost"),
                NumberExprAST("1", 1),
                NumberExprAST("1", 1),
                BlockAST(java.util.List.of<ExprAST?>())
            ).codeGen(IRBuilder())
        )
        Assertions.assertNull(
            ForAST(
                ZType.INT32,
                "i",
                StringExprAST("text"),
                NumberExprAST("1", 1),
                NumberExprAST("1", 1),
                BlockAST(java.util.List.of<ExprAST?>())
            ).codeGen(IRBuilder())
        )
        Assertions.assertNull(
            ForAST(
                ZType.INT32,
                "i",
                NumberExprAST("0", 0),
                VarRefExprAST("ghost"),
                NumberExprAST("1", 1),
                BlockAST(java.util.List.of<ExprAST?>())
            ).codeGen(IRBuilder())
        )
        Assertions.assertNull(
            ForAST(
                ZType.INT32,
                "i",
                NumberExprAST("0", 0),
                NumberExprAST("1", 1),
                VarRefExprAST("ghost"),
                BlockAST(java.util.List.of<ExprAST?>())
            ).codeGen(IRBuilder())
        )
        Assertions.assertNull(
            ForAST(
                ZType.INT32,
                "i",
                NumberExprAST("0", 0),
                NumberExprAST("1", 1),
                NumberExprAST("1", 1),
                VarRefExprAST("ghost")
            ).codeGen(IRBuilder())
        )
    }

    @org.junit.jupiter.api.Test
    fun testForBodyThatReturnsSkipsTheUpdate() {
        val loop: ForAST = ForAST(
            ZType.INT32,
            "i",
            NumberExprAST("0", 0),
            NumberExprAST("1", 1),
            NumberExprAST("1", 1),
            RetAST(NumberExprAST("1", 1))
        )
        val builder: IRBuilder = IRBuilder()
        builder.setReturnType(ZType.INT32)

        Assertions.assertNull(loop.codeGen(builder))
        Assertions.assertFalse(builder.hasFailed())
    }

    @org.junit.jupiter.api.Test
    fun testDeclaratorAccessors() {
        val local: DeclaratorExprAST = DeclaratorExprAST(ZType.INT32, "x", NumberExprAST("1", 1), false, false)

        Assertions.assertSame(ZType.INT32, local.declaredType())
        Assertions.assertEquals("x", local.name())
        Assertions.assertSame(ZType.INT32, local.type(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testLocalDeclarations() {
        val builder: IRBuilder = IRBuilder()
        Assertions.assertNull(DeclaratorExprAST(ZType.INT32, "x", null, false, false).codeGen(builder))
        Assertions.assertTrue(builder.getEntryAllocas().contains("alloca i32"), builder.getEntryAllocas())

        Assertions.assertNull(
            DeclaratorExprAST(
                ZType.INT32,
                "y",
                NumberExprAST("1", 1),
                false,
                false
            ).codeGen(IRBuilder())
        )
        Assertions.assertNull(
            DeclaratorExprAST(ZType.INT32, "z", VarRefExprAST("ghost"), false, false).codeGen(
                IRBuilder()
            )
        )
        Assertions.assertNull(
            DeclaratorExprAST(ZType.STRING, "s", NumberExprAST("1", 1), false, false).codeGen(
                IRBuilder()
            )
        )
    }

    @org.junit.jupiter.api.Test
    fun testGlobalDeclarations() {
        val builder: IRBuilder = IRBuilder()
        Assertions.assertNull(DeclaratorExprAST(ZType.INT32, "g", NumberExprAST("3", 3), false, true).codeGen(builder))
        Assertions.assertTrue(builder.getGlobals().contains("@g = global i32 3"), builder.getGlobals())

        Assertions.assertNull(DeclaratorExprAST(ZType.INT32, "h", null, false, true).codeGen(IRBuilder()))

        Assertions.assertNull(DeclaratorExprAST(ZType.INT32, "g", NumberExprAST("3", 3), false, true).codeGen(builder))

        Assertions.assertNull(
            DeclaratorExprAST(
                ZType.INT32,
                "g",
                VarRefExprAST("ghost"),
                false,
                true
            ).codeGen(IRBuilder())
        )

        Assertions.assertNull(
            DeclaratorExprAST(
                ZType.STRING,
                "s",
                NumberExprAST("1", 1),
                false,
                true
            ).codeGen(IRBuilder())
        )

        Assertions.assertNull(
            DeclaratorExprAST(
                ZType.INT32,
                "impure",
                ImpureConstant(),
                false,
                true
            ).codeGen(IRBuilder())
        )
    }

    @org.junit.jupiter.api.Test
    fun testGlobalNamesAreQualifiedByTheModule() {
        val builder: IRBuilder = IRBuilder()
        builder.setCurrentModule("m")

        Assertions.assertNull(DeclaratorExprAST(ZType.INT32, "g", NumberExprAST("3", 3), false, true).codeGen(builder))
        Assertions.assertTrue(builder.getGlobals().contains("@m_g = global i32 3"), builder.getGlobals())
        Assertions.assertNotNull(builder.symbols().lookup("m.g"))
    }

    companion object {
        private fun generate(prototype: PrototypeAST, body: ExprAST?): String {
            val llvmTextUnit: LLVMTextUnit? = FunctionAST(prototype, body).codeGen(IRBuilder())
            Assertions.assertNotNull(llvmTextUnit)
            return llvmTextUnit.text
        }
    }
}
