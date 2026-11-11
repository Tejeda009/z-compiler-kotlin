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

class TestSymbolTable {
    @org.junit.jupiter.api.Test
    fun testSymbolInfoGettersAndCounters() {
        val info: SymbolInfo = SymbolInfo("x", ZType.INT32, "%x", true)

        Assertions.assertEquals("x", info.getName())
        Assertions.assertSame(ZType.INT32, info.getType())
        Assertions.assertEquals("i32", info.getLlvmType())
        Assertions.assertEquals("%x", info.getPointerReg())
        Assertions.assertTrue(info.isConstant())
        Assertions.assertFalse(info.isVarArg())
        Assertions.assertFalse(info.isUsed())
        info.setUsed(true)
        Assertions.assertTrue(info.isUsed())
        Assertions.assertEquals(0, info.getCallCount())
        info.incrementCallCount()
        Assertions.assertEquals(1, info.getCallCount())
    }

    @org.junit.jupiter.api.Test
    fun testVarArgSymbolInfo() {
        val info: SymbolInfo = SymbolInfo("rest", ZType.FLT64, "", false, true)

        Assertions.assertTrue(info.isVarArg())
        Assertions.assertFalse(info.isConstant())
    }

    @org.junit.jupiter.api.Test
    fun testScopesAndLookups() {
        val table: src.Codegen.SymbolTable = src.Codegen.SymbolTable()
        val global: SymbolInfo = SymbolInfo("g", ZType.INT32, "%g", false)

        Assertions.assertEquals(1, table.getDepth())
        table.defineGlobal("g", global)
        Assertions.assertSame(global, table.lookup("g"))
        Assertions.assertFalse(global.isUsed())

        table.enterScope()
        Assertions.assertEquals(2, table.getDepth())
        val local: SymbolInfo = SymbolInfo("x", ZType.INT64, "%x", false)
        Assertions.assertTrue(table.define("x", local))
        Assertions.assertFalse(table.define("x", local))
        Assertions.assertSame(local, table.lookupCurrentScope("x"))
        Assertions.assertSame(local, table.lookup("x"))
        Assertions.assertSame(global, table.lookup("g"))
        Assertions.assertNull(table.lookupCurrentScope("g"))
        Assertions.assertNull(table.lookup("missing"))

        table.exitScope()
        Assertions.assertEquals(1, table.getDepth())
        Assertions.assertNull(table.lookupCurrentScope("x"))
        table.exitScope()
        Assertions.assertEquals(1, table.getDepth())
    }
}
