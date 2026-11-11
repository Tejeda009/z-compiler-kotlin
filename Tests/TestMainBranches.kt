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

class TestMainBranches {
    @TempDir
    var directory: Path? = null

    @Throws(IOException::class)
    private fun generate(source: String, file: Path): String? {
        return generateModule(ZFixtures.parserFor(source), file)
    }

    @org.junit.jupiter.api.Test
    fun testMainClassCanBeConstructed() {
        Assertions.assertNotNull(Main())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSourceWithoutAFileNameStillCompiles() {
        val ir = generate("proc main() { ret 1 }", Path.of("/"))

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("define dso_local i32 @main()"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testParseFailureReturnsNull() {
        Assertions.assertNull(generate("42", directory.resolve("bad.z")))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructsAreDeclaredInTheGlobalSection() {
        val ir = generate("struct Point { int32 x | int32 y }\nproc main() { ret 1 }", directory.resolve("main.z"))

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("%Point = type { i32, i32 }"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConflictingExternsFail() {
        val source = "extern sqrt(flt64) -> flt64\nextern sqrt(flt64) -> int32\nproc main() { ret 1 }"

        Assertions.assertNull(generate(source, directory.resolve("main.z")))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDuplicateRootDefinitionsFail() {
        val source = "proc f() { ret 1 }\nproc f() { ret 2 }\nproc main() { ret 1 }"

        Assertions.assertNull(generate(source, directory.resolve("main.z")))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAStructOnlyModuleIsEmpty() {
        Assertions.assertNull(generate("struct Point { int32 x }", directory.resolve("main.z")))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBrokenModuleGlobalsFail() {
        Files.writeString(directory.resolve("bad.z"), "int32 g -> f()\n")

        Assertions.assertNull(generate("import bad\nproc main() { ret 1 }", directory.resolve("main.z")))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testModuleGlobalsAreEmitted() {
        Files.writeString(directory.resolve("library.z"), "int32 g -> 7\n")

        val ir = generate("import library\nproc main() { ret 1 }", directory.resolve("main.z"))

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("@library_g = global i32 7"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBrokenModuleFunctionsFail() {
        Files.writeString(directory.resolve("bad.z"), "proc bad() { ret ghost }\n")

        Assertions.assertNull(generate("import bad\nproc main() { ret 1 }", directory.resolve("main.z")))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testModuleMainIsRejected() {
        Files.writeString(directory.resolve("bad.z"), "proc main() { ret 1 }\n")

        Assertions.assertNull(generate("import bad\nproc main() { ret 1 }", directory.resolve("main.z")))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMissingImportFails() {
        Assertions.assertNull(generate("import absent\nproc main() { ret 1 }", directory.resolve("main.z")))
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testTargetMetadataIncludesDataLayout() {
        val command: java.lang.Class<*> = java.lang.Class.forName("src.Main.CompilerCmd")
        val addTargetTriple: java.lang.reflect.Method = command.getDeclaredMethod("addTargetTriple", String::class.java)
        addTargetTriple.setAccessible(true)

        val ir = addTargetTriple.invoke(null, "define i32 @main() { ret i32 0 }\n") as String

        Assertions.assertTrue(ir.contains("target triple = \""), ir)
        Assertions.assertTrue(ir.contains("target datalayout = \""), ir)
        Assertions.assertTrue(ir.indexOf("target triple =") < ir.indexOf("target datalayout ="), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testExistingTargetDataLayoutIsPreserved() {
        val command: java.lang.Class<*> = java.lang.Class.forName("src.Main.CompilerCmd")
        val addTargetTriple: java.lang.reflect.Method = command.getDeclaredMethod("addTargetTriple", String::class.java)
        addTargetTriple.setAccessible(true)
        val ir = ("target triple = \"existing-target\"\n"
                + "target datalayout = \"e-p:64:64\"\n"
                + "define i32 @main() { ret i32 0 }\n")

        Assertions.assertEquals(ir, addTargetTriple.invoke(null, ir))
    }
}
