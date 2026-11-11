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

class TestIRBuilder {
    @org.junit.jupiter.api.Test
    fun testNamesAndRegisterTracking() {
        val builder: IRBuilder = IRBuilder()

        builder.setCurrentModule("math")
        Assertions.assertEquals("math", builder.currentModule())
        Assertions.assertEquals("math.x", builder.qualify("x"))
        Assertions.assertEquals("a.b", builder.qualify("a.b"))
        Assertions.assertEquals("math_x", builder.irNameOf("x"))
        Assertions.assertEquals("a_b", builder.irName("a.b"))

        val reg: String = builder.emitLoad("i32", "%p")
        Assertions.assertEquals("i32", builder.getRegisterType(reg))
        Assertions.assertNull(builder.getRegisterType("%missing"))
        builder.setRegisterType("%x", "double")
        Assertions.assertEquals("double", builder.getRegisterType("%x"))
    }

    @org.junit.jupiter.api.Test
    fun testQualifyWithoutAModule() {
        val builder: IRBuilder = IRBuilder()

        Assertions.assertNull(builder.currentModule())
        Assertions.assertEquals("x", builder.qualify("x"))
        Assertions.assertEquals("x", builder.irNameOf("x"))
    }

    @org.junit.jupiter.api.Test
    fun testResolveVariants() {
        val builder: IRBuilder = IRBuilder()
        val root: PrototypeAST = prototype("root")
        val dotted: PrototypeAST = prototype("mod.fn")
        val std: PrototypeAST = prototype("std.sqrt")
        val external: PrototypeAST = prototype("cfunc", src.AST.PrototypeAST.Param("flt64", "", false))

        Assertions.assertTrue(builder.declare(root))
        Assertions.assertTrue(builder.declare(dotted))
        Assertions.assertTrue(builder.declare(std))
        builder.declareExtern(external, ZType.FLT64)

        Assertions.assertEquals("root", builder.resolve("root").irName)
        Assertions.assertEquals("mod_fn", builder.resolve("mod.fn").irName)
        Assertions.assertNull(builder.resolve("other.fn"))
        Assertions.assertEquals("std_sqrt", builder.resolve("std.sqrt").irName)
        Assertions.assertEquals("cfunc", builder.resolve("std.cfunc").irName)
        Assertions.assertNull(builder.resolve("std.missing"))
        Assertions.assertEquals("std_sqrt", builder.resolve("sqrt").irName)
        Assertions.assertEquals("cfunc", builder.resolve("cfunc").irName)
        Assertions.assertSame(ZType.FLT64, builder.resolve("cfunc").externReturnType)
    }

    @org.junit.jupiter.api.Test
    fun testResolveInsideAModule() {
        val builder: IRBuilder = IRBuilder()
        val external: PrototypeAST = prototype("cfunc", src.AST.PrototypeAST.Param("int32", "", false))
        val own: PrototypeAST = prototype("m.own")

        builder.setCurrentModule("m")
        builder.declareExtern(external, ZType.INT32)
        Assertions.assertTrue(builder.declare(own))

        Assertions.assertEquals("cfunc", builder.resolve("cfunc").irName)
        Assertions.assertEquals("m_own", builder.resolve("own").irName)
        Assertions.assertNull(builder.resolve("missing"))
    }

    @org.junit.jupiter.api.Test
    fun testResolveFallsBackWhenAModuleExternCannotBeUsed() {
        val builder: IRBuilder = IRBuilder()

        builder.setCurrentModule("m")
        Assertions.assertNull(builder.resolve("cfunc"))
        Assertions.assertEquals("m", builder.currentModule())
    }

    @org.junit.jupiter.api.Test
    fun testDeclareRejectsDuplicatesAndConflicts() {
        val builder: IRBuilder = IRBuilder()
        val first: PrototypeAST = prototype("f", src.AST.PrototypeAST.Param("int32", "", false))

        Assertions.assertEquals(ExternDeclaration.DECLARED, builder.declareExtern(first, ZType.INT32))
        Assertions.assertEquals(ExternDeclaration.ALREADY_DECLARED, builder.declareExtern(first, ZType.INT32))
        Assertions.assertEquals(ExternDeclaration.CONFLICT, builder.declareExtern(first, ZType.INT64))
        builder.declare(prototype("g"))
        Assertions.assertFalse(builder.declare(prototype("g")))
        val plain: IRBuilder = IRBuilder()
        plain.declare(prototype("h"))
        Assertions.assertEquals(ExternDeclaration.CONFLICT, plain.declareExtern(prototype("h"), ZType.INT32))
    }

    @org.junit.jupiter.api.Test
    fun testReturnConversionsAndErrors() {
        val builder: IRBuilder = IRBuilder()

        builder.setReturnType(ZType.VOID)
        builder.emitReturn(LLVMTextUnit("1", ZType.INT32))
        Assertions.assertTrue(builder.isTerminated())
        Assertions.assertTrue(builder.getIR().contains("ret void"))

        builder.reset()
        builder.setReturnType(ZType.INT32)
        builder.emitReturn(null)
        Assertions.assertTrue(builder.getIR().contains("ret i32 281"))

        builder.reset()
        builder.setReturnType(ZType.INT32)
        builder.emitReturn(LLVMTextUnit("1", ZType.INT32_PTR))
        Assertions.assertTrue(builder.hasFailed())
        Assertions.assertFalse(builder.isTerminated())

        builder.reset()
        builder.setReturnType(ZType.INT32_PTR)
        builder.emitReturn(LLVMTextUnit("1", ZType.INT32))
        Assertions.assertTrue(builder.hasFailed())

        builder.reset()
        builder.setReturnType(ZType.INT32)
        builder.emitReturn(LLVMTextUnit("1.5", ZType.FLT64))
        Assertions.assertTrue(builder.getIR().contains("fptosi double 1.5 to i32"))

        builder.reset()
        builder.setReturnType(ZType.UINT32)
        builder.emitReturn(LLVMTextUnit("1.5", ZType.FLT64))
        Assertions.assertTrue(builder.getIR().contains("fptoui double 1.5 to i32"))

        builder.reset()
        builder.setReturnType(ZType.FLT64)
        builder.emitReturn(LLVMTextUnit("1", ZType.UINT32))
        Assertions.assertTrue(builder.getIR().contains("uitofp i32 1 to double"))

        builder.reset()
        builder.setReturnType(ZType.FLT32)
        builder.emitReturn(LLVMTextUnit("1.5", ZType.FLT64))
        Assertions.assertTrue(builder.getIR().contains("fptrunc double 1.5 to float"))

        builder.reset()
        builder.setReturnType(ZType.FLT64)
        builder.emitReturn(LLVMTextUnit("1.5", ZType.FLT32))
        Assertions.assertTrue(builder.getIR().contains("fpext float 1.5 to double"))

        builder.reset()
        builder.setReturnType(ZType.INT32)
        builder.emitReturn(LLVMTextUnit("1", ZType.INT64))
        Assertions.assertTrue(builder.getIR().contains("trunc i64 1 to i32"))

        builder.reset()
        builder.setReturnType(ZType.INT64)
        builder.emitReturn(LLVMTextUnit("1", ZType.INT32))
        Assertions.assertTrue(builder.getIR().contains("sext i32 1 to i64"))

        builder.reset()
        builder.setReturnType(ZType.STRING)
        builder.emitReturn(LLVMTextUnit("1", ZType.INT32))
        Assertions.assertTrue(builder.hasFailed())
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testConversionOpcodeRejectsSameWidthSignedness() {
        val method: java.lang.reflect.Method =
            IRBuilder::class.java.getDeclaredMethod("conversionOpcode", TypeRef::class.java, TypeRef::class.java)
        method.setAccessible(true)

        Assertions.assertNull(method.invoke(null, ZType.INT32, ZType.UINT32))
        Assertions.assertEquals("zext ", method.invoke(null, ZType.UINT32, ZType.INT64))
        Assertions.assertEquals("sext ", method.invoke(null, ZType.INT32, ZType.INT64))
    }

    @org.junit.jupiter.api.Test
    fun testBareReturnAndToString() {
        val builder: IRBuilder = IRBuilder()

        builder.emitBareReturn("i32 0")
        Assertions.assertTrue(builder.getIR().contains("ret i32 0"))
        Assertions.assertEquals(builder.getIR(), builder.toString())
    }

    @org.junit.jupiter.api.Test
    fun testVarArgLifecycle() {
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("rest", SymbolInfo("rest", ZType.INT32, "", false, true))

        Assertions.assertNull(builder.loadVarArg("rest", 1))
        Assertions.assertNull(builder.loadVarArg("ghost", 1))
        builder.symbols().define("x", SymbolInfo("x", ZType.INT32, "%x", false))
        Assertions.assertNull(builder.loadVarArg("x", 1))

        builder.beginVarArgFunction()
        builder.beginVarArgFunction()
        Assertions.assertNull(builder.loadVarArg("rest", 0))
        Assertions.assertNull(builder.loadVarArg("x", 1))
        val value: String? = builder.loadVarArg("rest", 2)
        Assertions.assertNotNull(value)
        Assertions.assertTrue(builder.getGlobals().contains("declare void @llvm.va_start(ptr)"))
        Assertions.assertTrue(builder.getIR().contains("va_arg ptr"))

        builder.setReturnType(ZType.VOID)
        builder.emitReturn(null)
        Assertions.assertTrue(builder.getIR().contains("llvm.va_end"))
    }

    @org.junit.jupiter.api.Test
    fun testStringConstantEscaping() {
        val builder: IRBuilder = IRBuilder()
        val name: String? =
            builder.emitStringConstant(byteArrayOf('"'.code.toByte(), '\\'.code.toByte(), '\n'.code.toByte(), 0x7F))

        Assertions.assertEquals(".str.0", name)
        // The escaped bytes are in the .bytes global.
        Assertions.assertTrue(builder.getGlobals().contains("\\22"))
        Assertions.assertTrue(builder.getGlobals().contains("\\5C"))
        Assertions.assertTrue(builder.getGlobals().contains("\\0A"))
        Assertions.assertTrue(builder.getGlobals().contains("\\7F"))
        // The fat global is a { i8*, i64 } pair.
        Assertions.assertTrue(builder.getGlobals().contains("@.str.0 = private unnamed_addr constant { i8*, i64 }"))
        // strlen is gone (length is in the struct).
        Assertions.assertFalse(builder.getGlobals().contains("@strlen"))
        Assertions.assertEquals(
            ".str.1",
            builder.emitStringConstant("hi".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        )
    }

    @org.junit.jupiter.api.Test
    fun testGlobalsAndStructDeclaration() {
        val builder: IRBuilder = IRBuilder()
        val point: StructType = StructType(
            "Point", java.util.List.of<src.Parser.StructType.Field>(
                src.Parser.StructType.Field("x", ZType.INT32),
                src.Parser.StructType.Field("y", ZType.FLT64)
            )
        )

        builder.declareStruct(point)
        builder.declareStruct(point)
        Assertions.assertEquals(1, builder.getGlobals().split("%Point = type", -1).size - 1)
        Assertions.assertTrue(builder.getGlobals().contains("%Point = type { i32, double }"))
        Assertions.assertEquals("%g", builder.emitAllocaGlobal("g", "i32"))
        Assertions.assertTrue(builder.getIR().contains("%g = global i32"))
        builder.appendToGlobals("extra\n")
        Assertions.assertTrue(builder.getGlobals().contains("extra"))
        Assertions.assertEquals("%p", builder.emitAlloca("p", "i32"))
        builder.emitStore("1", "i32", "%p")
        Assertions.assertTrue(builder.getEntryAllocas().contains("%p = alloca i32"))
        Assertions.assertTrue(builder.getIR().contains("store i32 1, ptr %p"))

        Assertions.assertEquals("%i", builder.emitAlloca("i", "i32"))
        Assertions.assertEquals("%i.1", builder.emitAlloca("i", "i32"))
        Assertions.assertEquals("%i.2", builder.emitAlloca("i", "i32"))
        Assertions.assertTrue(builder.getEntryAllocas().contains("%i.1 = alloca i32"))
        Assertions.assertTrue(builder.getEntryAllocas().contains("%i.2 = alloca i32"))
        Assertions.assertEquals("l.0", builder.nextLabel("l"))
        builder.emitLabel("l.0")
        builder.emitBr("l.1")
        builder.emitCondBr("%c", "l.2", "l.3")
        Assertions.assertTrue(builder.getIR().contains("br i1 %c"))
    }

    @org.junit.jupiter.api.Test
    fun testAlignmentOfAggregateTypes() {
        val builder: IRBuilder = IRBuilder()
        val point: StructType = StructType(
            "AlignPoint", java.util.List.of<src.Parser.StructType.Field>(
                src.Parser.StructType.Field("x", ZType.INT32),
                src.Parser.StructType.Field("y", ZType.FLT64)
            )
        )

        builder.emitAlloca("s", "{ i8*, i64 }")
        builder.emitAlloca("a", "[4 x i16]")
        StructRegistry.register(point)
        try {
            builder.emitAlloca("p", "%AlignPoint")
        } finally {
            StructRegistry.clear()
        }

        val allocas: String = builder.getEntryAllocas()
        Assertions.assertTrue(allocas.contains("%s = alloca { i8*, i64 }, align 8"), allocas)
        Assertions.assertTrue(allocas.contains("%a = alloca [4 x i16], align 2"), allocas)
        Assertions.assertTrue(allocas.contains("%p = alloca %AlignPoint, align 8"), allocas)
    }

    @org.junit.jupiter.api.Test
    fun testLoopAndResetHelpers() {
        val builder: IRBuilder = IRBuilder()

        builder.enterLoop("continue", "break")
        Assertions.assertNotNull(builder.currentLoop())
        Assertions.assertEquals("continue", builder.currentLoop().continueLabel())
        Assertions.assertEquals("break", builder.currentLoop().breakLabel())
        Assertions.assertSame(builder.currentLoop(), builder.exitLoop())
        Assertions.assertNull(builder.currentLoop())

        val reg: String = builder.nextRegister()
        builder.appendLine("x")
        builder.setRegisterType(reg, "i32")
        builder.reset()
        Assertions.assertEquals("", builder.getEntryAllocas())
        Assertions.assertEquals("%1", builder.nextRegister())
        Assertions.assertFalse(builder.isTerminated())
        Assertions.assertNull(builder.getRegisterType(reg))
        Assertions.assertEquals("", builder.getIR())
    }

    companion object {
        private fun prototype(name: String, vararg params: src.AST.PrototypeAST.Param?): PrototypeAST {
            return PrototypeAST(name, java.util.List.of<src.AST.PrototypeAST.Param?>(*params))
        }
    }
}
