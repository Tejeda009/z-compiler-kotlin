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

class TestCodeGen {
    @Throws(IOException::class)
    private fun codeGen(code: String): String? {
        return ZFixtures.module(code)
    }

    private fun assertCodegenFails(code: String) {
        Assertions.assertNotNull(ZFixtures.failureOf(code), "expected the compilation to fail")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSimpleFunction() {
        val code = "proc test() { ret 3 + 4 }"

        Assertions.assertEquals(
            """
                define internal dso_local double @test() alwaysinline nounwind nosync {
                entry:
                 %1 = add nsw i32 3, 4
                 %2 = sitofp i32 %1 to double
                 ret double %2
                }
                
                """.trimIndent(), codeGen(code)
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionWithReturnStatement() {
        val code = "proc main() { ret 40 + 32 }"

        Assertions.assertEquals(
            """
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %1 = add nsw i32 40, 32
                 ret i32 %1
                }
                
                """.trimIndent(), codeGen(code)
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionWithReturnNonMain() {
        val code = "proc foo() { ret 10 * 2 }"

        Assertions.assertEquals(
            """
                define internal dso_local double @foo() alwaysinline nounwind nosync {
                entry:
                 %1 = mul nsw i32 10, 2
                 %2 = sitofp i32 %1 to double
                 ret double %2
                }
                
                """.trimIndent(), codeGen(code)
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVarsWithoutAssignment() {
        val code = "proc main() { int32 var \n ret 0 }"

        Assertions.assertEquals(
            """
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %var = alloca i32, align 4
                 store i32 0, ptr %var, align 4, !tbaa !5002
                 ret i32 0
                }
                
                """.trimIndent(), codeGen(code)
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVarsWithAssignment() {
        val code = "proc main() { int32 var -> 3 \n ret 0 }"

        Assertions.assertEquals(
            """
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %var = alloca i32, align 4
                 store i32 3, ptr %var, align 4, !tbaa !5002
                 ret i32 0
                }
                
                """.trimIndent(), codeGen(code)
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVarsWithAssignmentWithRet() {
        val code = "proc main() { int32 var -> 3 \n ret var }"

        Assertions.assertEquals(
            """
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %var = alloca i32, align 4
                 store i32 3, ptr %var, align 4, !tbaa !5002
                 %1 = load i32, ptr %var, align 4, !tbaa !5002
                 ret i32 %1
                }
                
                """.trimIndent(), codeGen(code)
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMoreVars() {
        val code = "proc main() { int32 var\n int32 var2\n ret 0 }"

        Assertions.assertEquals(
            """
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %var = alloca i32, align 4
                 %var2 = alloca i32, align 4
                 store i32 0, ptr %var, align 4, !tbaa !5002
                 store i32 0, ptr %var2, align 4, !tbaa !5002
                 ret i32 0
                }
                
                """.trimIndent(), codeGen(code)
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVarsWithDifferentIntegers() {
        val code = "proc main() { int32 var -> 3\n int64 var2 -> 4\n ret 0 }"

        Assertions.assertEquals(
            """
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                !5003 = !{!"i64", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %var = alloca i32, align 4
                 %var2 = alloca i64, align 8
                 store i32 3, ptr %var, align 4, !tbaa !5002
                 store i64 4, ptr %var2, align 8, !tbaa !5003
                 ret i32 0
                }
                
                """.trimIndent(), codeGen(code)
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVarsWithDifferentTypesWithRet() {
        val code = "proc main() { int32 var -> 3\n flt32 var2 -> 4\n flt64 var3 -> 4\n ret var3 }"

        Assertions.assertEquals(
            """
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                !5003 = !{!"float", !5001, i64 0}
                !5004 = !{!"double", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %var = alloca i32, align 4
                 %var2 = alloca float, align 4
                 %var3 = alloca double, align 8
                 store i32 3, ptr %var, align 4, !tbaa !5002
                 store float 4.0, ptr %var2, align 4, !tbaa !5003
                 store double 4.0, ptr %var3, align 8, !tbaa !5004
                 %1 = load double, ptr %var3, align 8, !tbaa !5004
                 %2 = fptosi double %1 to i32
                 ret i32 %2
                }
                
                """.trimIndent(), codeGen(code)
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConstantDefinitionWithDifferentTypes() {
        val code = "proc main() { cn int32 const -> 3\n cn flt32 floatConst -> 1.0\n ret 0}"

        Assertions.assertEquals(
            """
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                !5003 = !{!"float", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %const = alloca i32, align 4
                 %floatConst = alloca float, align 4
                 store i32 3, ptr %const, align 4, !tbaa !5002
                 store float 1.0, ptr %floatConst, align 4, !tbaa !5003
                 ret i32 0
                }
                
                """.trimIndent(), codeGen(code)
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConstantDefinitionWithRet() {
        val code = "proc main() { cn int32 const -> 3\n cn flt32 floatConst -> 1.0\n ret const}"

        Assertions.assertEquals(
            """
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                !5003 = !{!"float", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %const = alloca i32, align 4
                 %floatConst = alloca float, align 4
                 store i32 3, ptr %const, align 4, !tbaa !5002
                 store float 1.0, ptr %floatConst, align 4, !tbaa !5003
                 %1 = load i32, ptr %const, align 4, !tbaa !5002
                 ret i32 %1
                }
                
                """.trimIndent(), codeGen(code)
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFloatLiteralKeepsItsPoint() {
        val code = "proc main() { flt64 pi -> 3.14\n ret pi }"

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("store double 3.14,"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testLargeIntegerKeepsEveryDigit() {
        val code = "proc main() { int64 big -> 9007199254740993\n ret big }"

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("store i64 9007199254740993,"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testLargeIntegerIntoInt32Truncates() {
        val code = "proc main() { ret 9007199254740993 }"

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("ret i32 1"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionCallInsideMain() {
        val code = "proc foo() { ret 1 }\nproc main() { foo() \n ret 0 }"

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("call double @foo()"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionWithParameters() {
        val code = "proc foo(int32 x | int32 y) { ret x + y }"

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("@foo(i32 %x, i32 %y)"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testCallWithParameters() {
        val code = "proc foo(int32 x | int32 y) { ret x + y }\nproc main() { ret foo(1 | 2) }"

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("call double @foo(i32 1, i32 2)"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSimpleForLoop() {
        val code: String = """
                 proc main() {
                  for (int32 i -> 0 | i < 10 | i + 1) {
                    int32 x -> 5
                  }
                  ret 0
                }
                """.trimIndent()

        Assertions.assertEquals(
            """
!5000 = !{!"Z TBAA"}
!5001 = !{!"omnipotent char", !5000, i64 0}
!5002 = !{!"i32", !5001, i64 0}
!1001 = !{!1001, !1002, !1003}
!1002 = !{!"llvm.loop.vectorize.enable", i1 true}
!1003 = !{!"llvm.loop.unroll.count", i32 4}

define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
entry:
 %i = alloca i32, align 4
 %x = alloca i32, align 4
 store i32 0, ptr %i, align 4, !tbaa !5002
 br label %for.cond.0
 for.cond.0:
 %1 = load i32, ptr %i, align 4, !tbaa !5002
 %2 = icmp slt i32 %1, 10
 br i1 %2, label %for.body.1, label %for.end.3, !prof !{!"branch_weights", i32 1000, i32 1}
 for.body.1:
 store i32 5, ptr %x, align 4, !tbaa !5002
 br label %for.inc.2
 for.inc.2:
 %3 = load i32, ptr %i, align 4, !tbaa !5002
 %4 = add nsw i32 %3, 1
 store i32 %4, ptr %i, align 4, !tbaa !5002
 br label %for.cond.0, !llvm.loop !1001
 for.end.3:
 ret i32 0
}
                
                """.trimIndent(), codeGen(code)
        )
    }

    @org.junit.jupiter.api.Test
    fun testForLoopVariableIsScopedToTheLoop() {
        val code = "proc main() { for (int32 i -> 0 | i < 3 | i + 1) { }\n ret i }"
        assertCodegenFails(code)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testOuterVariableRemainsAccessibleAfterInnerBlock() {
        val code = "proc main() { int32 x -> 3\n if (true) { int32 y -> 4 }\n ret x }"
        Assertions.assertNotNull(codeGen(code))
    }

    @org.junit.jupiter.api.Test
    fun testVariableDeclaredInsideIfIsOutOfScope() {
        val code = "proc main() { if (true) { int32 x -> 3 } ret x }"
        assertCodegenFails(code)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testOuterVariableRemainsAccessibleInsideIfBlock() {
        val code = "proc main() { int32 x -> 3 if (true) { ret x } }"
        Assertions.assertNotNull(codeGen(code))
    }

    @org.junit.jupiter.api.Test
    fun testInnerIfVariableIsNotAccessibleInOuterIf() {
        val code = "proc main() { if (true) { if (true) { int32 x -> 3 } ret x } }"
        assertCodegenFails(code)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFloatForLoopComparesAsFloat() {
        val code = "proc main() {\n for (flt64 i -> 0.0 | i < 2.0 | i + 0.5) { }\n ret 0\n}"

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("fcmp olt double %1, 2.0"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIntegerForLoopAgainstAFloatBoundIsComparedAsFloat() {
        val code = "proc main() {\n for (int32 i -> 0 | i < 2.5 | i + 1) { }\n ret 0\n}"

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("%2 = sitofp i32 %1 to double"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("fcmp olt double %2, 2.5"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testReturnInsideForLoopSkipsTheIncrement() {
        val code: String = """
                proc find(int32 n) {
                  for (int32 i -> 0 | i < n | i + 1) {
                    ret i
                  }
                  ret 0
                }
                """.trimIndent()

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("ret double"), llvmIR)
        Assertions.assertFalse(llvmIR.contains("for.inc"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBreakJumpsToTheEndOfTheLoop() {
        val code: String = """
                proc main() {
                  for (int32 i -> 0 | i < 10 | i + 1) {
                    if (i == 3) { break }
                  }
                  ret 0
                }
                """.trimIndent()

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("br label %for.end.3"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testContinueJumpsToTheUpdate() {
        val code: String = """
                proc main() {
                  for (int32 i -> 0 | i < 10 | i + 1) {
                    if (i == 3) { continue }
                    int32 x -> i
                  }
                  ret 0
                }
                """.trimIndent()

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertEquals(2, llvmIR.split("br label %for.inc.2", -1).size - 1, llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testContinueAsTheLastStatementKeepsTheUpdate() {
        val code = "proc main() {\n for (int32 i -> 0 | i < 10 | i + 1) {\n  continue\n }\n ret 0\n}"

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("for.inc.2:"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBreakLeavesOnlyTheInnermostLoop() {
        val code: String = """
                proc main() {
                  for (int32 i -> 0 | i < 3 | i + 1) {
                    for (int32 j -> 0 | j < 3 | j + 1) {
                      break
                    }
                  }
                  ret 0
                }
                """.trimIndent()

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("br label %for.end.7"), llvmIR)
        Assertions.assertFalse(llvmIR.contains("br label %for.end.3"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    fun testBreakOutsideALoopFails() {
        assertCodegenFails("proc main() {\n break\n ret 0\n}")
    }

    @org.junit.jupiter.api.Test
    fun testContinueOutsideALoopFails() {
        assertCodegenFails("proc main() {\n continue\n ret 0\n}")
    }

    @org.junit.jupiter.api.Test
    fun testStatementAfterBreakFails() {
        assertCodegenFails(
            """
                proc main() {
                  for (int32 i -> 0 | i < 3 | i + 1) {
                    break
                    int32 x -> 1
                  }
                  ret 0
                }
                """.trimIndent()
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testEmptyMainReturnsZero() {
        Assertions.assertEquals(
            """
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 ret i32 0
                }
                
                """.trimIndent(), codeGen("proc main() { }")
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnaryMinusOnInt() {
        val llvmIR = codeGen("proc main() { ret -5 + 3 }")

        Assertions.assertEquals(
            """
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %1 = sub i32 0, 5
                 %2 = add nsw i32 %1, 3
                 ret i32 %2
                }
                
                """.trimIndent(), llvmIR
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnaryMinusOnFloat() {
        val llvmIR = codeGen("proc main() { ret -5.5 }")

        Assertions.assertEquals(
            """
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %1 = fneg contract double 5.5
                 %2 = fptosi double %1 to i32
                 ret i32 %2
                }
                
                """.trimIndent(), llvmIR
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDivisionOfIntegersStaysInteger() {
        Assertions.assertTrue(codeGen("proc main() { ret 7 / 2 }").contains("div i32 7, 2"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDivisionOfFloatsUsesFloatingPoint() {
        Assertions.assertTrue(codeGen("proc main() { ret 7.0 / 2.0 }").contains("fdiv contract double 7.0, 2.0"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testComparisonWidensToInt32() {
        val llvmIR = codeGen("proc main() { ret 3 < 4 }")

        Assertions.assertEquals(
            """
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %1 = icmp slt i32 3, 4
                 %2 = zext i1 %1 to i32
                 ret i32 %2
                }
                
                """.trimIndent(), llvmIR
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIntegerArgumentIsConvertedForAFloatParameter() {
        val llvmIR = codeGen("proc half(flt64 x) { ret x / 2 }\nproc main() { ret half(21) }")

        Assertions.assertTrue(llvmIR.contains("sitofp i32 21 to double"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("call double @half(double"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testRecursiveCall() {
        val llvmIR = codeGen("proc fact(int32 n) { ret n * fact(n - 1) }\nproc main() { ret fact(5) }")

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("call double @fact(i32"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testCallNestedInAnArgument() {
        val llvmIR = codeGen(
            "proc a(int32 n) { ret n + 1 }\nproc b(int32 n) { ret a(a(n)) }\nproc main() { ret b(1) }"
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertEquals(2, Companion.countOccurrences(llvmIR!!, "call double @a("))
        Assertions.assertTrue(llvmIR.contains("call double @a(i32 %3)"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testCallToAFunctionDefinedLaterInTheFile() {
        val llvmIR = codeGen("proc main() { ret twice(21) }\nproc twice(int32 x) { ret x * 2 }")

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("call double @twice(i32 21)"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testCallResultIsConvertedForTheReturningType() {
        val llvmIR = codeGen("proc one() { ret 1 }\nproc main() { ret one() }")

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("fptosi double %1 to i32"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    fun testShadowedDeclarationFails() {
        val diagnostics: String? = ZFixtures.failureOf("proc main() { int32 x -> 1\n int32 x -> 2\n ret x }")

        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("local"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testUndeclaredVariableFails() {
        val diagnostics: String? = ZFixtures.failureOf("proc main() { ret ghost }")

        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("undeclared"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testCallToUnknownFunctionFails() {
        val diagnostics: String? = ZFixtures.failureOf("proc main() { ret nope(1) }")

        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("unknown function"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testCallWithTooManyArgumentsFails() {
        val diagnostics: String? = ZFixtures.failureOf("proc foo(int32 x) { ret x }\nproc main() { ret foo(1 | 2) }")

        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("takes 1 argument"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testCallWithTooFewArgumentsFails() {
        assertCodegenFails("proc foo(int32 x | int32 y) { ret x }\nproc main() { ret foo(1) }")
    }

    @org.junit.jupiter.api.Test
    fun testFunctionThatFallsOffTheEndFails() {
        val diagnostics: String? = ZFixtures.failureOf("proc foo() { 3 + 4 }")

        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("without returning"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testStatementAfterReturnFails() {
        assertCodegenFails("proc main() { ret 0\n ret 1 }")
    }

    @org.junit.jupiter.api.Test
    fun testDuplicateParameterFails() {
        assertCodegenFails("proc foo(int32 x | int32 x) { ret x }")
    }

    @org.junit.jupiter.api.Test
    fun testFailedStatementStopsTheWholeFunction() {
        assertCodegenFails("proc main() { int32 a -> 1\n ret ghost }")
    }

    @org.junit.jupiter.api.Test
    fun testFailedInitialiserStopsTheDeclaration() {
        assertCodegenFails("proc main() { int32 a -> ghost\n ret 0 }")
    }

    @org.junit.jupiter.api.Test
    fun testShadowingAParameterFails() {
        assertCodegenFails("proc f(int32 x) { int32 x -> 1\n ret x }")
    }

    @org.junit.jupiter.api.Test
    fun testVariableUsedBeforeItIsDeclaredFails() {
        assertCodegenFails("proc main() { ret x\n int32 x -> 1 }")
    }

    @org.junit.jupiter.api.Test
    fun testTwoFunctionsWithTheSameNameFail() {
        assertCodegenFails("proc a() { ret 1 }\nproc a() { ret 2 }\nproc main() { ret a() }")
    }

    @org.junit.jupiter.api.Test
    fun testAFailureInsideALoopBodyFails() {
        assertCodegenFails("proc main() {\n for (int32 i -> 0 | i < 3 | i + 1) {\n  ret ghost\n }\n ret 0\n}")
    }

    @org.junit.jupiter.api.Test
    fun testAFailureInALoopInitialiserFails() {
        assertCodegenFails("proc main() {\n for (int32 i -> ghost | i < 3 | i + 1) { }\n ret 0\n}")
    }

    @org.junit.jupiter.api.Test
    fun testAFailureInALoopUpdateFails() {
        assertCodegenFails("proc main() {\n for (int32 i -> 0 | i < 3 | ghost) { }\n ret 0\n}")
    }

    @org.junit.jupiter.api.Test
    fun testAFailureInTheLoopConditionFails() {
        assertCodegenFails("proc main() {\n for (int32 i -> 0 | ghost | i + 1) { }\n ret 0\n}")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testCallingTheSameFunctionTwiceEmitsTwoCalls() {
        val llvmIR = codeGen("proc a() { ret 1 }\nproc main() { ret a() + a() }")

        Assertions.assertNotNull(llvmIR)
        Assertions.assertEquals(2, Companion.countOccurrences(llvmIR!!, "call double @a()"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testACallWhoseResultIsDiscardedIsStillEmitted() {
        val llvmIR = codeGen("proc a() { ret 1 }\nproc main() { a()\n ret 0 }")

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("call double @a()"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAConstantCanBeAnArgument() {
        val llvmIR = codeGen(
            "proc add(int32 x | int32 y) { ret x + y }\nproc main() { cn int32 k -> 2\n ret add(40 | k) }"
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("call double @add(i32 40, i32 %1)"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMixingAnIntegerAndAFloatWidensToFloat() {
        Assertions.assertEquals(
            """
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %1 = fadd contract double 1.0, 2.5
                 %2 = fptosi double %1 to i32
                 ret i32 %2
                }
                
                """.trimIndent(), codeGen("proc main() { ret 1 + 2.5 }")
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFloatVariableIsReadAsItsOwnType() {
        Assertions.assertEquals(
            """
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"float", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %a = alloca float, align 4
                 store float 1.5, ptr %a, align 4, !tbaa !5002
                 %1 = load float, ptr %a, align 4, !tbaa !5002
                 %2 = fptosi float %1 to i32
                 ret i32 %2
                }
                
                """.trimIndent(), codeGen("proc main() { flt32 a -> 1.5\n ret a }")
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFloatVariableIsWidenedWhenUsedAsAnArgument() {
        val llvmIR = codeGen(
            "proc id(flt64 x) { ret x }\nproc main() { flt32 a -> 1.5\n ret id(a) }"
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("fpext float %1 to double"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testEmptyFileProducesNoModule() {
        Assertions.assertEquals("", codeGen(""))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSimpleIf() {
        val llvmIR = codeGen("proc main() { if (1 < 2) { ret 1 } ret 0 }")

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("if.cond."), llvmIR)
        Assertions.assertTrue(llvmIR.contains("if.then."), llvmIR)
        Assertions.assertTrue(llvmIR.contains("if.end."), llvmIR)
        Assertions.assertTrue(llvmIR.contains("icmp slt i32 1, 2"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfElse() {
        val llvmIR = codeGen(
            "proc main() { if (1 > 2) { ret 1 } else { ret 0 } }"
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("if.else."), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfEifElse() {
        val llvmIR = codeGen(
            "proc main() { if (1 > 2) { ret 1 } eif (2 > 3) { ret 2 } else { ret 3 } }"
        )

        Assertions.assertNotNull(llvmIR)
        // " if.cond." (leading space) matches only the label definition lines,
        // not the "br label %if.cond.N" lines.
        Assertions.assertEquals(2, Companion.countOccurrences(llvmIR!!, " if.cond."))
        Assertions.assertTrue(llvmIR.contains("if.else."), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfWithFloatCondition() {
        val llvmIR = codeGen(
            "proc main() { if (1.0 < 2.0) { ret 1 } ret 0 }"
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("fcmp olt double 1.0, 2.0"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfBranchWithoutReturnFallsThrough() {
        // The outer then-branch does not return, so it must fall through
        // to the outer if.end.
        val llvmIR = codeGen(
            "proc main() { if (1 < 2) { if (2 < 3) { ret 1 } }\n ret 0 }"
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("br label %if.end."), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testNestedIf() {
        val llvmIR = codeGen(
            "proc main() { if (1 < 2) { if (2 < 3) { ret 1 } } ret 0 }"
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertEquals(2, Companion.countOccurrences(llvmIR!!, " if.cond."))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfWithReturnOnlySkipsEndBranch() {
        val llvmIR = codeGen(
            "proc main() { if (1 < 2) { ret 1 } ret 0 }"
        )

        // The then-branch ends in `ret`, so no fallthrough br is emitted there.
        Assertions.assertEquals(
            """
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 br label %if.cond.0
                 if.cond.0:
                 %1 = icmp slt i32 1, 2
                 br i1 %1, label %if.then.1, label %if.end.2
                 if.then.1:
                 ret i32 1
                 if.end.2:
                 ret i32 0
                }
                
                """.trimIndent(), llvmIR
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfElseWithRet() {
        val llvmIR = codeGen("proc main() { if (1 < 2) {\n} else {\n ret 0\n}ret 1\n}\n")

        Assertions.assertNotNull(llvmIR)
        Assertions.assertEquals(
            """
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 br label %if.cond.0
                 if.cond.0:
                 %1 = icmp slt i32 1, 2
                 br i1 %1, label %if.then.1, label %if.else.2
                 if.then.1:
                 br label %if.end.3
                 if.else.2:
                 ret i32 0
                 if.end.3:
                 ret i32 1
                }
                
                """.trimIndent(), llvmIR
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnsignedDeclarations() {
        val llvmIR = codeGen("proc main() { uint32 a -> 3\n uint64 b -> 4\n ret 0 }")

        Assertions.assertTrue(llvmIR.contains("%a = alloca i32"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("store i32 3, ptr %a"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("%b = alloca i64"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("store i64 4, ptr %b"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnsignedDivisionUsesUdiv() {
        val llvmIR = codeGen("proc main() { uint32 a -> 10\n uint32 b -> 3\n ret a / b }")
        Assertions.assertTrue(llvmIR.contains("udiv i32"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSignedDivisionStillUsesSdiv() {
        val llvmIR = codeGen("proc main() { int32 a -> 10\n int32 b -> 3\n ret a / b }")
        Assertions.assertTrue(llvmIR.contains("sdiv i32"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnsignedComparisonUsesUlt() {
        val llvmIR = codeGen("proc main() { uint32 a -> 10\n ret a < 5 }")
        Assertions.assertTrue(llvmIR.contains("icmp ult i32"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSignedComparisonStillUsesSlt() {
        val llvmIR = codeGen("proc main() { int32 a -> 10\n ret a < 5 }")
        Assertions.assertTrue(llvmIR.contains("icmp slt i32"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnsignedWidensWithZext() {
        val llvmIR = codeGen("proc main() { uint32 a -> 1\n uint64 b -> a\n ret 0 }")
        Assertions.assertTrue(llvmIR.contains("zext i32"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSignedWidensWithSext() {
        val llvmIR = codeGen("proc main() { int32 a -> 1\n int64 b -> a\n ret 0 }")
        Assertions.assertTrue(llvmIR.contains("sext i32"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnsignedToFloatUsesUitofp() {
        val llvmIR = codeGen("proc main() { uint32 a -> 1\n flt64 b -> a\n ret 0 }")
        Assertions.assertTrue(llvmIR.contains("uitofp i32"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSignedToFloatUsesSitofp() {
        val llvmIR = codeGen("proc main() { int32 a -> 1\n flt64 b -> a\n ret 0 }")
        Assertions.assertTrue(llvmIR.contains("sitofp i32"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStringDeclarationStartsWithZeroInitializer() {
        val llvmIR = codeGen("proc main() { str s\n ret 0 }")

        Assertions.assertTrue(llvmIR.contains("%s = alloca { i8*, i64 }"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("store { i8*, i64 } zeroinitializer, ptr %s"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStringParameter() {
        val llvmIR = codeGen("proc take(str s) { ret 0 }")
        Assertions.assertTrue(llvmIR.contains("@take({ i8*, i64 } %s)"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUint32Parameter() {
        val llvmIR = codeGen("proc take(uint32 n) { ret 0 }")
        Assertions.assertTrue(llvmIR.contains("@take(i32 %n)"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStringLiteralIsPutInAGlobal() {
        val llvmIR = codeGen("proc main() { str s -> \"hello\"\n ret 0 }")

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(
            llvmIR.contains("@.str.0.bytes = private unnamed_addr constant [6 x i8] c\"hello\\00\""),
            llvmIR
        )
        Assertions.assertTrue(llvmIR.contains("@.str.0 = private unnamed_addr constant { i8*, i64 }"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("load { i8*, i64 }, ptr @.str.0"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStringDeclarationStoresTheFatStruct() {
        val llvmIR = codeGen("proc main() { str s -> \"hi\"\n ret 0 }")

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("%s = alloca { i8*, i64 }"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("store { i8*, i64 } %"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStringConcatenationUsesTheAllocator() {
        val llvmIR = codeGen(
            "proc main() { str a -> \"hello\"\n str b -> \" world\"\n str c -> a + b\n ret 0 }"
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("@.arena = global [1048576 x i8] zeroinitializer"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("@.arena.used = global i64 0"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("@.free_list = global ptr null"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("define ptr @zalloc(i64 %size)"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("define void @zfree(ptr %p)"), llvmIR)
        // Lengths come from the struct, not from strlen.
        Assertions.assertTrue(llvmIR.contains("extractvalue { i8*, i64 }"), llvmIR)
        Assertions.assertFalse(llvmIR.contains("@strlen"), llvmIR)
        // The bytes are copied with llvm.memcpy.
        Assertions.assertTrue(llvmIR.contains("call void @llvm.memcpy.p0.p0.i64(ptr"), llvmIR)
        // The result is built as a new fat struct.
        Assertions.assertTrue(llvmIR.contains("insertvalue { i8*, i64 }"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("call ptr @zalloc(i64"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testProgramWithoutStringsHasNoArena() {
        val llvmIR = codeGen("proc main() { ret 0 }")

        Assertions.assertFalse(llvmIR.contains("@.arena"), llvmIR)
        Assertions.assertFalse(llvmIR.contains("@.str."), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStringArgumentPassesAFatStruct() {
        val llvmIR = codeGen(
            "proc id(str s) { ret 0 }\nproc main() { ret 0 }"
        )

        Assertions.assertTrue(llvmIR.contains("@id({ i8*, i64 } %s)"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStrReturnTypeAppearsInSignature() {
        val llvmIR = codeGen(
            "proc greet(str s) -> str { ret s }\nproc main() { ret 0 }"
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("define internal dso_local { i8*, i64 } @greet({ i8*, i64 } %s)"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testInt32ReturnType() {
        val llvmIR = codeGen("proc answer() -> int32 { ret 42 }")

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("define internal dso_local i32 @answer()"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("ret i32 42"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testCallingAStrReturningFunction() {
        val llvmIR = codeGen(
            """
                        proc greet(str s) -> str { ret s }
                        proc main() { str r -> greet("hi")
                         ret 0 }
                         """.trimIndent()
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("call { i8*, i64 } @greet({ i8*, i64 }"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    fun testReturningAnIntFromAStrFunctionFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            "proc foo() -> str { ret 42 }"
        )

        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("cannot convert between str and"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testReturningStrFromAnIntFunctionFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            "proc foo() -> int32 { str s -> \"x\"\n ret s }"
        )

        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("cannot convert between str and"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMainWithStrReturnFallsOffWithZero() {
        val llvmIR = codeGen("proc main() -> str { }")

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("ret { i8*, i64 } zeroinitializer"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testPointerParameterCodegen() {
        val llvmIR = codeGen(
            """
                        proc inc(mut int32* p) { ret 0 }
                        proc main() { int32 x -> 0
                         inc(&x)
                         ret 0 }
                         """.trimIndent()
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("define internal dso_local double @inc(ptr noalias %p)"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("call double @inc(ptr %x)"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testNonMutPointerParameterAllowsAlias() {
        val llvmIR = codeGen(
            """
                        proc read(int32* a | int32* b) { ret 0 }
                        proc main() { int32 x -> 0
                         read(&x | &x)
                         ret 0 }
                         """.trimIndent()
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("define internal dso_local double @read(ptr %a, ptr %b)"), llvmIR)
        Assertions.assertFalse(llvmIR.contains("noalias"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    fun testPassingAliasToMutPointerFails() {
        assertCodegenFails(
            """
                        proc copy(mut int32* dst | int32* src) { ret 0 }
                        proc main() { int32 x -> 0
                         copy(&x | &x)
                         ret 0 }
                         """.trimIndent()
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testPassingDistinctVariablesToMutPointerSucceeds() {
        val llvmIR = codeGen(
            """
                        proc copy(mut int32* dst | int32* src) { ret 0 }
                        proc main() { int32 a -> 0
                         int32 b -> 1
                         copy(&a | &b)
                         ret 0 }
                         """.trimIndent()
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(
            llvmIR.contains("define internal dso_local double @copy(ptr noalias %dst, ptr %src)"),
            llvmIR
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDerefInFunction() {
        val llvmIR = codeGen(
            """
                        proc get(int32* p) -> int32 { ret *p }
                        proc main() { int32 x -> 42
                         ret get(&x) }
                         """.trimIndent()
        )

        Assertions.assertTrue(llvmIR.contains("load i32, ptr"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAssignThroughPointer() {
        val llvmIR = codeGen(
            """
                        proc set(int32* p) -> int32 { *p -> 1
                         ret 0 }
                        proc main() { int32 x -> 0
                         set(&x)
                         ret x }
                         """.trimIndent()
        )

        Assertions.assertNotNull(llvmIR, "compilation failed")
        Assertions.assertTrue(llvmIR.contains("store i32 1"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    fun testPointerTypeMismatchFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                        proc inc(int32* p) { ret 0 }
                        proc main() { flt64 x -> 0.0
                         inc(&x)
                         ret 0 }
                         """.trimIndent()
        )

        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("cannot convert between"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVoidFunction() {
        val llvmIR = codeGen("proc test() -> void {\nint32 test -> 0\n}\nproc main() {\n}\n")
        Assertions.assertNotNull(llvmIR, "compilation failed")
        Assertions.assertTrue(llvmIR.contains("ret void"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("ret i32 0"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testEasterEggRet() {
        val ir = codeGen("proc main() {\nret\n}\n")
        Assertions.assertNotNull(ir, "compilation failed")
        Assertions.assertTrue(ir.contains("ret i32 281"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructDeclarationAndFieldAccess() {
        val code: String = """
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    ret p.x
                }
                """.trimIndent()

        val llvmIR = codeGen(code)

        Assertions.assertNotNull(llvmIR, "compilation failed")
        Assertions.assertTrue(llvmIR.contains("%Point = type { i32, i32 }"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("insertvalue %Point undef, i32 1, 0"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("insertvalue %Point"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("getelementptr inbounds %Point"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    fun testStructFieldAccessFailsOnUnknownField() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    ret p.z
                }
                """.trimIndent()
        )

        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("has no field 'z'"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testFieldAccessOnNonStructFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                proc main() {
                    int32 x -> 1
                    ret x.field
                }
                """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("is not a struct"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testFieldAccessOnUndeclaredVariableFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            "proc main() { ret ghost.x }"
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("undeclared variable"), diagnostics)
    }

    @Disabled("Needs postfix .field on (expr) — follow-up PR")
    @org.junit.jupiter.api.Test
    fun testFieldAccessNeedsVariableBase() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { int32 x }
                proc main() {
                    ret (Point{1}).x
                }
                """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("field access needs a variable base"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFieldAccessOnNestedStruct() {
        val llvmIR = codeGen(
            """
                struct Inner { int32 v }
                struct Outer { Inner inner | int32 y }
                proc main() {
                    Outer o -> Outer{Inner{7} | 2}
                    ret o.inner.v
                }
                """.trimIndent()
        )
        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("getelementptr inbounds %Outer"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("getelementptr inbounds %Inner"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    fun testStructLiteralUnknownTypeFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            "proc main() { ret Unknown{1} }"
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("Unknown struct"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testStructLiteralWrongArityFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { int32 x | int32 y }
                proc main() { ret Point{1} }
                """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("expects 2 values"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testStructLiteralCoerceFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { str name | int32 y }
                proc main() { Point p -> Point{1 | 2}
                 ret p.y }
                 """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("cannot convert between str"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructDeclOnlyModule() {
        val llvmIR = codeGen("struct Point { int32 x | int32 y }")
        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("%Point = type { i32, i32 }"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructAsGlobalVariable() {
        val llvmIR = codeGen(
            """
                struct Point { int32 x | int32 y }
                Point origin -> Point{0 | 0}
                proc main() { ret 0 }
                """.trimIndent()
        )
        Assertions.assertNotNull(llvmIR, "compilation failed")
        Assertions.assertTrue(llvmIR.contains("@origin = global %Point { i32 0, i32 0 }"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructAsParameterAndReturn() {
        val llvmIR = codeGen(
            """
                struct Point { int32 x | int32 y }
                proc id(Point p) -> Point { ret p }
                proc main() {
                    Point p -> Point{1 | 2}
                    Point q -> id(p)
                    ret q.x
                }
                """.trimIndent()
        )
        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("@id(%Point %p)"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("call %Point @id(%Point"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    fun testFunctionReturningStructFallsOffFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { int32 x | int32 y }
                proc f() -> Point { }
                """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        // No `ret`: FunctionAST reports that the function can reach the end
        // without returning.
        Assertions.assertTrue(diagnostics.contains("without returning"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testStructInComparisonFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    if (p < p) { ret 1 }
                    ret 0
                }
                """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("struct cannot be used in a comparison"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testStructInArithmeticFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    ret p + p
                }
                """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("struct cannot be used in a binary expression"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testStructComparedWithIntFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    ret p < 5
                }
                """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("struct cannot be used in a binary expression"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testStructUnaryMinusFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    ret -p
                }
                """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("cannot"), diagnostics)
    }

    @Disabled("Needs postfix .field on (expr) — follow-up PR")
    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructPointerFieldAccess() {
        val llvmIR = codeGen(
            """
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    Point* pp -> &p
                    ret (*pp).x
                }
                """.trimIndent()
        )
        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("getelementptr inbounds %Point"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    fun testStructLiteralWithUndeclaredFieldValueFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{ghost | 2}
                    ret 0
                }
                """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("undeclared variable"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testStructLiteralWithWrongFieldTypeFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { str name | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    ret 0
                }
                """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("cannot convert between str"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testStructGlobalWithWrongArityFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { int32 x | int32 y }
                Point origin -> Point{0}
                proc main() { ret 0 }
                """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("constant expression"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testStructGlobalWithNonConstantFieldFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                struct Point { int32 x | int32 y }
                proc f() -> int32 { ret 5 }
                Point origin -> Point{f() | 0}
                proc main() { ret 0 }
                """.trimIndent()
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("constant expression"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testVariableMissingNameFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            "proc main() { int32\n ret 0 }"
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("Expected name after type"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testCallArgCodeGenFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            "proc foo(int32 x) { ret x }\nproc main() { foo(ghost) }"
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("undeclared variable"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testTwoConcatenationsEmitTheAllocatorOnce() {
        val llvmIR = codeGen(
            """
                proc main() {
                    str a -> "hello"
                    str b -> " world"
                    str c -> a + b
                    str d -> c + "!"
                    ret 0
                }
                """.trimIndent()
        )

        Assertions.assertNotNull(llvmIR)
        // The arena, its cursor, and the free list are emitted once.
        Assertions.assertEquals(1, Companion.countOccurrences(llvmIR!!, "@.arena = global"), llvmIR)
        Assertions.assertEquals(1, Companion.countOccurrences(llvmIR, "@.arena.used = global"), llvmIR)
        Assertions.assertEquals(1, Companion.countOccurrences(llvmIR, "@.free_list = global"), llvmIR)
        // The two allocator functions are emitted once.
        Assertions.assertEquals(1, Companion.countOccurrences(llvmIR, "define ptr @zalloc"), llvmIR)
        Assertions.assertEquals(1, Companion.countOccurrences(llvmIR, "define void @zfree"), llvmIR)
        // Both concatenations call @zalloc.
        Assertions.assertEquals(2, Companion.countOccurrences(llvmIR, "call ptr @zalloc(i64"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAllocReturnsAPointer() {
        val llvmIR = codeGen(
            """
                proc main() {
                    ptr p -> alloc(64)
                    ret 0
                }
                """.trimIndent()
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("call ptr @zalloc(i64 64)"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFreeCallsZfree() {
        val llvmIR = codeGen(
            """
                proc main() {
                    ptr p -> alloc(64)
                    free(p)
                    ret 0
                }
                """.trimIndent()
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("call ptr @zalloc(i64 64)"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("call void @zfree(ptr"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAllocSizeIsAnExpression() {
        val llvmIR = codeGen(
            """
                proc main() {
                    int32 n -> 32
                    ptr p -> alloc(n * 2)
                    ret 0
                }
                """.trimIndent()
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("call ptr @zalloc(i64"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    fun testFreeingANonPointerFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            """
                proc main() {
                    int32 x -> 1
                    free(x)
                    ret 0
                }
                """.trimIndent()
        )

        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("free expects a pointer"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFreeingAStringFreesTheDataPointer() {
        val llvmIR = codeGen(
            """
                proc main() {
                    str llvmTextUnit -> "hi"
                    free(llvmTextUnit)
                    ret 0
                }
                """.trimIndent()
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertTrue(llvmIR.contains("extractvalue { i8*, i64 }"), llvmIR)
        Assertions.assertTrue(llvmIR.contains("call void @zfree(ptr"), llvmIR)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAllocatorIsEmittedOnce() {
        val llvmIR = codeGen(
            """
                proc main() {
                    ptr a -> alloc(8)
                    ptr b -> alloc(16)
                    free(a)
                    free(b)
                    ret 0
                }
                """.trimIndent()
        )

        Assertions.assertNotNull(llvmIR)
        Assertions.assertEquals(1, Companion.countOccurrences(llvmIR!!, "define ptr @zalloc"), llvmIR)
        Assertions.assertEquals(1, Companion.countOccurrences(llvmIR, "define void @zfree"), llvmIR)
        Assertions.assertEquals(2, Companion.countOccurrences(llvmIR, "call ptr @zalloc"), llvmIR)
        Assertions.assertEquals(2, Companion.countOccurrences(llvmIR, "call void @zfree"), llvmIR)
    }

    companion object {
        private fun countOccurrences(haystack: String, needle: String): Int {
            var count = 0
            var at: Int = haystack.indexOf(needle)
            while (at >= 0) {
                count++
                at = haystack.indexOf(needle, at + needle.length())
            }
            return count
        }
    }
}