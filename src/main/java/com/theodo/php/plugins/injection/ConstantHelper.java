package com.theodo.php.plugins.injection;

import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;

import static com.theodo.php.plugins.injection.ParameterHelper.checkElement;

class ConstantHelper {
    static boolean isConstant(PsiElement operand) {
        ConstantSourceDetection visitor = new ConstantSourceDetection();
        visitor.apply(operand);
        return visitor.isConstant;
    }

    private static class ConstantSourceDetection extends PhpElementVisitor {
        private boolean isConstant = false;


        @Override
        public void visitPhpStringLiteralExpression(StringLiteralExpression expression) {
            if( expression.isSingleQuote()) {
                isConstant = true;
            } else {
                PsiElement[] children = expression.getChildren();
                for (PsiElement child : children) {
                    if(!isConstant(child)) {
                        return;
                    }
                }
                isConstant = true;
            }
        }

        @Override
        public void visitPhpClassConstantReference(ClassConstantReference constantReference) {
            isConstant = true;
        }

        @Override
        public void visitPhpArrayAccessExpression(ArrayAccessExpression expression) {
            PhpPsiElement value = expression.getValue();
            isConstant = isConstant(value);
        }

        @Override
        public void visitPhpVariable(Variable variable) {
            isConstant = !checkElement(variable);
        }

        @Override
        public void visitPhpBinaryExpression(BinaryExpression expression) {
            if (expression instanceof ConcatenationExpression) {
                ConcatenationExpression concatenationExpression = (ConcatenationExpression) expression;
                isConstant = isConstant(concatenationExpression.getLeftOperand()) &&
                        isConstant(concatenationExpression.getRightOperand());
            }
        }
    }
}
