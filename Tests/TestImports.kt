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

class TestImports {
    @TempDir
    var directory: Path? = null

    @Throws(IOException::class)
    private fun createParser(input: String): src.Parser.Parser {
        val lex: src.Lexer.Lexer =
            src.Lexer.Lexer(ByteArrayInputStream(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        return src.Parser.Parser(lex)
    }

    @Throws(IOException::class)
    private fun compile(root: String, vararg files: String): String? {
        for (file in files) {
            val split: Int = file.indexOf('\n'.code)
            Files.writeString(directory.resolve(file.substring(0, split)), file.substring(split + 1))
        }
        val rootFile: Path = directory.resolve(root)
        return generateModule(createParser(Files.readString(rootFile)), rootFile)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testImportParsesModuleName() {
        val parser: src.Parser.Parser = createParser("import math")
        val anImport: ImportAST? = parser.parseImport()
        Assertions.assertNotNull(anImport)
        Assertions.assertEquals("math", anImport.moduleName)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testParseImportIsAbsentWithoutTheKeyword() {
        Assertions.assertNull(createParser("proc main() { ret 1 }").parseImport())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testImportWithoutNameFails() {
        Assertions.assertNull(createParser("import 42").parseImport())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testQualifiedCallParses() {
        val parser: src.Parser.Parser = createParser("math.sqrt(2.0)")
        val ast: ExprAST? = parser.parseExpression()
        val call: CallExprAST = Assertions.assertInstanceOf<CallExprAST>(CallExprAST::class.java, ast)
        Assertions.assertEquals("math.sqrt", call.callee())
        Assertions.assertEquals(1, call.arguments().size())
    }

    @org.junit.jupiter.api.Test
    fun testQualifiedReferenceThatIsNotCalledFails() {
        val error: String? = ZFixtures.failureOf("proc main() { ret math.sqrt }")
        Assertions.assertNotNull(error)
        Assertions.assertTrue(error.contains("undeclared variable"), error)
    }

    @org.junit.jupiter.api.Test
    fun testDanglingDotFails() {
        val error = errorReportedBy("math.(2.0)")
        Assertions.assertNotNull(error)
        Assertions.assertTrue(error.contains("after '.'"), error)
    }

    @org.junit.jupiter.api.Test
    fun testAssignmentToModuleMemberFails() {
        val error = errorReportedBy("math.x -> 1")
        Assertions.assertNotNull(error)
        Assertions.assertTrue(error.contains("Cannot assign"), error)
    }

    @org.junit.jupiter.api.Test
    fun testLateImportFails() {
        val reported: ByteArrayOutputStream = ByteArrayOutputStream()
        val diagnostics: PrintStream? = java.lang.System.err
        var module: ModuleAST?
        java.lang.System.setErr(PrintStream(reported, true, java.nio.charset.StandardCharsets.UTF_8))
        try {
            module = ModuleLoader.Companion.parseUnit(createParser("proc main() { ret 1 }\nimport math"), "root")
        } catch (e: IOException) {
            module = null
        } finally {
            java.lang.System.setErr(diagnostics)
        }
        Assertions.assertNull(module)
        Assertions.assertTrue(
            reported.toString(java.nio.charset.StandardCharsets.UTF_8).contains("'import' must appear"),
            reported.toString(java.nio.charset.StandardCharsets.UTF_8)
        )
    }

    private fun errorReportedBy(source: String): String? {
        val reported: ByteArrayOutputStream = ByteArrayOutputStream()
        val diagnostics: PrintStream? = java.lang.System.err
        java.lang.System.setErr(PrintStream(reported, true, java.nio.charset.StandardCharsets.UTF_8))
        try {
            createParser(source).parseExpression()
        } catch (e: IOException) {
            return null
        } finally {
            java.lang.System.setErr(diagnostics)
        }
        return if (reported.size() == 0) null else reported.toString(java.nio.charset.StandardCharsets.UTF_8)
    }


    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testModuleFunctionIsMangled() {
        val ir = compile(
            "main.z",
            "main.z\nimport math\nproc main() { ret math.square(21) }",
            "math.z\nproc square(int32 x) { ret x * 2 }"
        )

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("define internal dso_local double @math_square"), ir)
        Assertions.assertTrue(ir.contains("call double @math_square(i32 21)"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testRootFunctionNamesAreNotMangled() {
        val ir = compile("main.z", "main.z\nproc main() { ret twice(2) }\nproc twice(int32 x) { ret x * 2 }")

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("define dso_local i32 @main()"), ir)
        Assertions.assertTrue(ir.contains("@twice"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDiamondImportCompilesTheSharedModuleOnce() {
        val ir = compile(
            "main.z",
            "main.z\nimport b\nimport c\nproc main() { ret b.fb() + c.fc() }",
            "b.z\nimport d\nproc fb() { ret d.fd() }",
            "c.z\nimport d\nproc fc() { ret d.fd() }",
            "d.z\nproc fd() { ret 1 }"
        )

        Assertions.assertNotNull(ir)
        Assertions.assertEquals(
            1,
            Companion.count(ir!!, "@d_fd() alwaysinline nounwind"),
            "the shared module must be compiled once:\n" + ir
        )
        Assertions.assertEquals(2, Companion.count(ir, "tail call double @d_fd()"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testCircularImportFails() {
        val ir = compile(
            "main.z",
            "main.z\nimport b\nproc main() { ret 1 }",
            "b.z\nimport c\nproc fb() { ret 1 }",
            "c.z\nimport b\nproc fc() { ret 1 }"
        )

        Assertions.assertNull(ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testImportingTheRootFileFails() {
        Assertions.assertNull(
            compile(
                "main.z",
                "main.z\nimport math\nproc main() { ret 1 }",
                "math.z\nimport main\nproc square(int32 x) { ret x * 2 }"
            )
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMissingImportFileFails() {
        Assertions.assertNull(compile("main.z", "main.z\nimport nosuch\nproc main() { ret 1 }"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testImportsAreResolvedRelativeToTheImportingFileNotTheCwd() {
        val ir = compile(
            "main.z",
            "main.z\nimport mid\nproc main() { ret mid.twice(2) }",
            "mid.z\nimport lib\nproc twice(int32 x) { ret lib.double(x) }",
            "lib.z\nproc double(int32 x) { ret x * 2 }"
        )

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("@mid_twice"), ir)
        Assertions.assertTrue(ir.contains("@lib_double"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSameFunctionNameInTwoModulesDoesNotCollide() {
        val ir = compile(
            "main.z",
            "main.z\nimport a\nimport b\nproc main() { ret a.init() + b.init() }",
            "a.z\nproc init() { ret 1 }",
            "b.z\nproc init() { ret 2 }"
        )

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("@a_init"), ir)
        Assertions.assertTrue(ir.contains("@b_init"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testModuleCallResolvesItsOwnFunctionOverTheRoots() {
        val ir = compile(
            "main.z",
            "main.z\nimport math\nproc helper() { ret 1 }\nproc main() { ret math.twice() }",
            "math.z\nproc helper() { ret 2 }\nproc twice() { ret helper() + helper() }"
        )

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("call double @math_helper()"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStdIsLoadedFromTheCompiler() {
        val ir = compile("main.z", "main.z\nimport std\nproc main() {\n std.println(42)\n ret 0\n}")

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("call void @std_println("), ir)
        Assertions.assertTrue(ir.contains("c\"%lld\\0A\\00\""), ir)
        Assertions.assertEquals(1, Companion.count(ir!!, "declare i32 @printf("), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStdFunctionsCanBeCalledWithoutThePrefix() {
        val ir = compile("main.z", "main.z\nimport std\nproc main() {\n println(42)\n ret 0\n}")

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("call void @std_println("), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testOwnFunctionWinsOverTheStdOne() {
        val ir = compile(
            "main.z",
            "main.z\nimport std\nproc println(int64 value) -> void {\n ret\n}\nproc main() {\n println(42)\n ret 0\n}"
        )

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("call void @println("), ir)
        Assertions.assertFalse(ir.contains("call void @std_println("), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testModuleFunctionWinsOverAnImportedExtern() {
        val ir = compile(
            "main.z",
            "main.z\nimport math\nproc main() { ret math.answer() }",
            "math.z\nimport std\nproc answer() -> int32 { ret abs(-1) }\nproc abs(int32 value) -> int32 { ret 99 }"
        )

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("call i32 @math_abs(i32 %1)"), ir)
        Assertions.assertEquals(
            1,
            Companion.count(ir!!, "call i32 @abs("),
            "only the std wrapper should call C abs:\n" + ir
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnknownModuleFunctionFails() {
        Assertions.assertNull(
            compile(
                "main.z",
                "main.z\nimport math\nproc main() { ret math.nope(1) }",
                "math.z\nproc twice(int32 x) { ret x * 2 }"
            )
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStdWorksWithAUserDeclaredPrintf() {
        val ir = compile(
            "main.z",
            "main.z\nimport std\nextern printf(str | ...) -> int32\nproc main() {\n std.println(1)\n printf(\"done\")\n ret 0\n}"
        )

        Assertions.assertNotNull(ir)
        Assertions.assertEquals(1, Companion.count(ir!!, "declare i32 @printf("), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testLocalStdFileTakesPrecedence() {
        val ir = compile(
            "main.z",
            "main.z\nimport std\nproc main() { ret std.answer() }",
            "std.z\nproc answer() { ret 42 }"
        )

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("@std_answer"), ir)
        Assertions.assertFalse(ir.contains("@std_println"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMainInAModuleFails() {
        Assertions.assertNull(
            compile(
                "main.z",
                "main.z\nimport math\nproc main() { ret 1 }",
                "math.z\nproc main() { ret 2 }"
            )
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testExternCFunctionInAModuleKeepsItsPlainName() {
        val ir = compile(
            "main.z",
            "main.z\nimport libm\nproc main() { ret libm.halve(4.0) }",
            "libm.z\nextern sqrt(flt64) -> flt64\nproc halve(flt64 x) { ret sqrt(x) }"
        )

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("declare double @sqrt(double)"), ir)
        Assertions.assertTrue(ir.contains("call double @sqrt(double"), ir)
        Assertions.assertTrue(ir.contains("call double @libm_halve(double"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testExternAfterADefinitionParses() {
        val ir = compile(
            "main.z",
            "main.z\nimport math\nproc main() { ret math.twice(2) }",
            "math.z\nproc twice(int32 x) { ret x * 2 }\nextern sqrt(flt64) -> flt64"
        )

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("declare double @sqrt(double)"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSameExternInTwoModulesIsDeclaredOnce() {
        val ir = compile(
            "main.z",
            "main.z\nimport a\nimport b\nproc main() { ret a.fa() + b.fb() }",
            "a.z\nextern sqrt(flt64) -> flt64\nproc fa() { ret sqrt(4.0) }",
            "b.z\nextern sqrt(flt64) -> flt64\nproc fb() { ret sqrt(9.0) }"
        )

        Assertions.assertNotNull(ir)
        Assertions.assertEquals(1, Companion.count(ir!!, "declare double @sqrt(double)"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConflictingExternDeclarationsFail() {
        Assertions.assertNull(
            compile(
                "main.z",
                "main.z\nimport a\nimport b\nproc main() { ret 0 }",
                "a.z\nextern sqrt(flt64) -> flt64\nproc fa() { ret 1 }",
                "b.z\nextern sqrt(int32) -> int32\nproc fb() { ret 2 }"
            )
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testGlobalLiteralInitializerCompiles() {
        val ir = compile("main.z", "main.z\nint32 g -> 3\nproc main() { ret g }")

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("@g = global i32 3"), ir)
        Assertions.assertTrue(ir.contains("load i32, ptr @g"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testNonConstantGlobalInitializerFails() {
        Assertions.assertNull(compile("main.z", "main.z\nint32 g -> 1 + 2\nproc main() { ret g }"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDuplicateFunctionInAModuleFails() {
        Assertions.assertNull(
            compile(
                "main.z",
                "main.z\nimport math\nproc main() { ret 1 }",
                "math.z\nproc a() { ret 1 }\nproc a() { ret 2 }"
            )
        )
    }

    companion object {
        private fun count(ir: String, fragment: String): Int {
            var occurrences = 0
            var at: Int = ir.indexOf(fragment)
            while (at >= 0) {
                occurrences++
                at = ir.indexOf(fragment, at + 1)
            }
            return occurrences
        }
    }
}
