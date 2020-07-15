package com.theodo.php.plugins.injection;

import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocParamTag;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ParameterTypeHelper {

    private final Map<Integer, PhpType> typePerParameter = new HashMap<>();
    private final Map<Integer, String> namePerParameter = new HashMap<>();

    ParameterTypeHelper(Function function) {
        if(function != null) {
            getParametersType(function);
        }
    }

    private void getParametersType(Function function) {

        function = findMethodDefinitionInInterface(function);

        Parameter[] parameters = function.getParameters();
        getParameterTypesFromMethod(parameters);
        getParameterTypesFromPhpDoc(function);
    }

    private void getParameterTypesFromPhpDoc(Function function) {
        if (typePerParameter.isEmpty()) {
            PhpDocComment docComment = function.getDocComment();
            if (docComment != null) {
                List<PhpDocParamTag> paramTags = docComment.getParamTags();
                int index = 0;
                for (PhpDocParamTag paramTag : paramTags) {
                    namePerParameter.put(index, paramTag.getVarName());
                    typePerParameter.put(index++, paramTag.getType());
                }
            }
        }
    }

    private void getParameterTypesFromMethod(Parameter[] parameters) {
        int index = 0;
        for (Parameter parameter : parameters) {
            namePerParameter.put(index, parameter.getName());
            typePerParameter.put(index++, parameter.getType());
        }
    }

    private Function findMethodDefinitionInInterface(Function function) {
        if(function instanceof Method){
            Method method = (Method) function;
            PhpClass containingClass = method.getContainingClass();
            if(containingClass == null) return function;

            PhpClass[] implementedInterfaces = containingClass.getImplementedInterfaces();
            for (PhpClass implementedInterface : implementedInterfaces) {
                Method methodByName = implementedInterface.findMethodByName(function.getName());
                if(methodByName != null) return methodByName;
            }
        }
        return function;
    }

    PhpType getType(int paramIndex){
        PhpType phpType = typePerParameter.get(paramIndex);
        if(phpType == null) return PhpType.MIXED;
        return phpType;
    }

    String getName(int paramIndex){
        String name = namePerParameter.get(paramIndex);
        if(name == null) return "???";
        return name;
    }
}
