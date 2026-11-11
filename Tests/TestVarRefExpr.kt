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

class TestVarRefExpr {
    @org.junit.jupiter.api.Test
    fun testTypeFallsBackToTheCurrentModule() {
        val builder: IRBuilder = IRBuilder()
        builder.setCurrentModule("math")
        builder.symbols().defineGlobal("math.pi", SymbolInfo("pi", ZType.FLT64, "@pi", false))

        Assertions.assertSame(ZType.FLT64, VarRefExprAST("pi").type(builder))
        Assertions.assertSame(ZType.INT32, VarRefExprAST("missing").type(builder))
        Assertions.assertSame(ZType.INT32, VarRefExprAST(".x").type(builder))
    }

    @org.junit.jupiter.api.Test
    fun testTypeOfAVariadicElement() {
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("rest", SymbolInfo("rest", ZType.INT64, "", false, true))

        Assertions.assertSame(ZType.INT64, VarRefExprAST("rest.1").type(builder))
        Assertions.assertSame(ZType.INT32, VarRefExprAST("other.1").type(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testCodeGenReadsVariablesAndModuleGlobals() {
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("x", SymbolInfo("x", ZType.INT32, "%x", false))
        val local: LLVMTextUnit? = VarRefExprAST("x").codeGen(builder)
        Assertions.assertNotNull(local)
        Assertions.assertSame(ZType.INT32, local.type)
        Assertions.assertTrue(builder.getIR().contains("load i32, ptr %x"))

        val module: IRBuilder = IRBuilder()
        module.setCurrentModule("math")
        module.symbols().defineGlobal("math.pi", SymbolInfo("pi", ZType.FLT64, "@pi", false))
        val global: LLVMTextUnit? = VarRefExprAST("pi").codeGen(module)
        Assertions.assertNotNull(global)
        Assertions.assertSame(ZType.FLT64, global.type)
    }

    @org.junit.jupiter.api.Test
    fun testReadingAVariadicParameterWithoutAnIndexFails() {
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("rest", SymbolInfo("rest", ZType.INT32, "", false, true))

        Assertions.assertNull(VarRefExprAST("rest").codeGen(builder))
        Assertions.assertTrue(builder.hasFailed())
    }

    @org.junit.jupiter.api.Test
    fun testReadingAVariadicElement() {
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("rest", SymbolInfo("rest", ZType.INT32, "", false, true))
        builder.beginVarArgFunction()

        val llvmTextUnit: LLVMTextUnit? = VarRefExprAST("rest.2").codeGen(builder)
        Assertions.assertNotNull(llvmTextUnit)
        Assertions.assertSame(ZType.INT32, llvmTextUnit.type)
        Assertions.assertTrue(llvmTextUnit.isRegister())
        Assertions.assertTrue(builder.getIR().contains("va_arg"))
    }

    @org.junit.jupiter.api.Test
    fun testVariadicElementFailures() {
        val invalidIndex: IRBuilder = IRBuilder()
        invalidIndex.symbols().define("rest", SymbolInfo("rest", ZType.INT32, "", false, true))
        invalidIndex.beginVarArgFunction()
        Assertions.assertNull(VarRefExprAST("rest.nope").codeGen(invalidIndex))
        Assertions.assertTrue(invalidIndex.hasFailed())

        val noVarArg: IRBuilder = IRBuilder()
        noVarArg.symbols().define("rest", SymbolInfo("rest", ZType.INT32, "", false, true))
        Assertions.assertNull(VarRefExprAST("rest.1").codeGen(noVarArg))
        Assertions.assertTrue(noVarArg.hasFailed())

        val notVariadic: IRBuilder = IRBuilder()
        notVariadic.symbols().define("rest", SymbolInfo("rest", ZType.INT32, "%r", false))
        Assertions.assertNull(VarRefExprAST("rest.1").codeGen(notVariadic))
        Assertions.assertTrue(notVariadic.hasFailed())
    }

    @org.junit.jupiter.api.Test
    fun testMissingVariablesAreReported() {
        val builder: IRBuilder = IRBuilder()

        Assertions.assertNull(VarRefExprAST("ghost").codeGen(builder))
        Assertions.assertTrue(builder.hasFailed())
        Assertions.assertEquals("ghost", VarRefExprAST("ghost").name())
        Assertions.assertFalse(LLVMTextUnit("1", ZType.INT32).isRegister())
    }
}
