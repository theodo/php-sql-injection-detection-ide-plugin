package com.theodo.php.plugins.injection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;

import static com.theodo.php.plugins.injection.ConstantHelper.isConstant;

class ParameterHelper {
    static boolean checkElement(PsiElement psiElement) {
        if(psiElement == null) return false;

        DetectIssuesVisitor visitor = new DetectIssuesVisitor();
        visitor.apply(psiElement);
        return visitor.hasIssue;
    }


    private static class DetectIssuesVisitor extends PhpElementVisitor {
        private boolean hasIssue = false;

        @Override
        public void visitPhpBinaryExpression(BinaryExpression expression) {
            hasIssue = !isConstant(expression);
        }


        @Override
        public void visitPhpVariable(Variable variable) {
            if(!variable.isDeclaration() && variable.resolve() != null){
                // Jump to Declaration and test it
                hasIssue = checkElement(variable.resolve());
            } else {
                // All variable assignments must come from constant sources otherwise it's dangerous
                SearchNonConstAssignments searchNonConstAssignments = new SearchNonConstAssignments();
                if (searchNonConstAssignments.check(variable)) {
                    hasIssue = true;
                }
            }
        }

        @Override
        public void visitPhpField(Field field) {
            hasIssue = true;
        }

        @Override
        public void visitPhpParameter(Parameter parameter) {
            hasIssue = true;
        }

        @Override
        public void visitPhpFunctionCall(FunctionReference reference) {
            hasIssue = true;
        }

        @Override
        public void visitPhpStringLiteralExpression(StringLiteralExpression expression) {
            if(expression.isSingleQuote()) return;

            // CHECK for variables in String Interpolation
            PsiElement[] children = expression.getChildren();
            for (PsiElement child : children) {
                if(checkElement(child)){
                    hasIssue = true;
                    return;
                }
            }
        }
    }

    private static class SearchNonConstAssignments {
        private boolean atLeastOneNonConstant = false;

        private boolean check(Variable variable) {
            atLeastOneNonConstant = false;
            Query<PsiReference> search = ReferencesSearch.search(variable);
            search.findAll().forEach(psiReference -> {
                if (psiReference instanceof Variable && ((Variable) psiReference).isDeclaration()) {
                    PsiElement parent = ((Variable) psiReference).getParent();
                    if (parent instanceof AssignmentExpression) {
                        AssignmentExpression assignmentExpression = (AssignmentExpression) parent;
                        PhpPsiElement value = assignmentExpression.getValue();
                        boolean constant = isConstant(value);
                        if (!constant) {
                            atLeastOneNonConstant = true;
                        }
                    }
                }
            });
            return atLeastOneNonConstant;
        }
    }
}
