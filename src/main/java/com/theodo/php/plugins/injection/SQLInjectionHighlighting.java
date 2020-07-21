package com.theodo.php.plugins.injection;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import org.jetbrains.annotations.NotNull;


public class SQLInjectionHighlighting extends PhpInspection {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder problemsHolder, boolean onTheFly) {

        return new PhpElementVisitor() {
            public void visitPhpMethodReference(MethodReference reference) {
                super.visitPhpMethodReference(reference);
                PhpExpression classReference = reference.getClassReference();

                if (classReference != null) {
                    Project project = reference.getProject();
                    PhpType type = classReference.getType().global(project);
                    if (type.toString().contains("\\Doctrine\\ORM\\QueryBuilder")) {
                        if(reference.getName() != null && reference.getName().toUpperCase().contains("WHERE")) {
                            inspectAllParameters(reference, problemsHolder, "Doctrine QueryBuilder: ");
                        }
                    }
                    if (type.toString().contains("\\Doctrine\\ORM\\EntityManager")) {
                        if(reference.getName() != null && reference.getName().toUpperCase().contains("NATIVEQUERY")) {
                            inspectAllParameters(reference, problemsHolder, "EntityManager NativeQuery: ");
                        }
                    }
                    if (type.toString().contains("\\mysqli") && reference.getName() != null) {
                        if (reference.getName().contains("query") || reference.getName().contains("prepare")) {
                            inspectAllParameters(reference, problemsHolder, "mysqli: ");
                        }
                    }
                }
            }

            @Override
            public void visitPhpFunctionCall(FunctionReference reference) {
                if (reference.getName() != null && reference.getName().contains("mysqli_")) {
                    if (reference.getName().contains("query") || reference.getName().contains("prepare")) {
                        inspectAllParameters(reference, problemsHolder, "mysqli: ");
                    }
                }
                if (reference.getName() != null && reference.getName().contains("pg_")) {
                     if (reference.getName().contains("_query")) {
                         inspectAllParameters(reference, problemsHolder, "pg: ");
                     }
                 }
             }

        };
    }

    private void inspectAllParameters(FunctionReference functionReference,
                                      @NotNull ProblemsHolder problemsHolder,
                                      String why) {
        Function function = (Function) functionReference.resolve();
        if (function == null) return;

        ParameterList parameterList = functionReference.getParameterList();
        if (parameterList == null) {
            return;
        }

        PsiElement[] parameters = parameterList.getParameters();
        ParameterTypeHelper parameterTypeHelper = new ParameterTypeHelper(function);
        int index = 0;
        for (PsiElement parameter : parameters) {
            PhpType type = parameterTypeHelper.getType(index);
            if (PhpType.STRING.equals(type) || PhpType.MIXED.equals(type)) {
                String paramName = parameterTypeHelper.getName(index);
                PhpElementVisitor visitor = new ParameterVisitor(why, function, parameter, paramName, problemsHolder);
                visitor.apply(parameter);
            }
            index++;
        }

    }

    private final static class ParameterVisitor extends PhpElementVisitor {
        private final Function function;
        private final ProblemsHolder problemsHolder;
        private final PsiElement argument;
        private final String why;
        private final String paramName;

        ParameterVisitor(String why, Function function, PsiElement argument, String paramName, ProblemsHolder problemsHolder) {
            this.function = function;
            this.problemsHolder = problemsHolder;
            this.argument = argument;
            this.why = why;
            this.paramName = paramName;
        }

        @Override
        public void visitPhpBinaryExpression(BinaryExpression expression) {
            if (ParameterHelper.checkElement(expression)) {
                problemsHolder.registerProblem(argument,
                        why + "String Concatenation found in method '" + function.getName()
                                + "' parameter named '" + paramName + "'. This is a potential SQL injection issue. Consider using parameters instead.");
            }
        }

        // PARAMETER OF THE FUNCTION IS A VARIABLE => CHECK IF CONST
        @Override
        public void visitPhpVariable(Variable variable) {
            checkReferencedVariable(variable.resolve());
        }

        @Override
        public void visitPhpStringLiteralExpression(StringLiteralExpression expression) {
            if (ParameterHelper.checkElement(expression)) {
                logError();
            }
        }

        // PARAMETER OF THE FUNCTION IS A CLASS FIELD => CHECK IF CONST
        @Override
        public void visitPhpFieldReference(FieldReference fieldReference) {
            checkReferencedVariable(fieldReference.resolve());
        }

        // PARAMETER OF THE FUNCTION IS A CLASS METHOD CALL RESULTS => UNSAFE
        @Override
        public void visitPhpMethodReference(MethodReference reference) {
            PsiElement[] parameters = reference.getParameters();
            for (PsiElement parameter : parameters) {
                if(ParameterHelper.checkElement(parameter)){
                    logError();
                    return;
                }
            }
        }

        // PARAMETER OF THE FUNCTION IS A FUNCTION CALL RESULTS => UNSAFE
        @Override
        public void visitPhpFunctionCall(FunctionReference reference) {
            PsiElement[] parameters = reference.getParameters();
            for (PsiElement parameter : parameters) {
                if(ParameterHelper.checkElement(parameter)){
                    logError();
                    return;
                }
            }
        }

        private void checkReferencedVariable(PsiElement resolved) {
            if (ParameterHelper.checkElement(resolved)) {
                logError();
            }
        }

        private void logError() {
            problemsHolder.registerProblem(argument,
                    why + "In method '" + function.getName() + "', parameter named '" + paramName +
                            "' seems not to be constant. This may allow an assailant to use SQL injection. Consider using parameterized query instead.");
        }
    }
}