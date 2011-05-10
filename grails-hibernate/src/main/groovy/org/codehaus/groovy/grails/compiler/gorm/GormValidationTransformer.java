/*
 * Copyright 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.compiler.gorm;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler;
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.codehaus.groovy.grails.commons.GrailsResourceUtils;
import org.codehaus.groovy.grails.compiler.injection.AbstractGrailsArtefactTransformer;
import org.codehaus.groovy.grails.compiler.injection.AstTransformer;
import org.codehaus.groovy.grails.compiler.injection.GrailsASTUtils;
import org.grails.datastore.gorm.GormValidationApi;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;

/**
 * Makes the validate methods statically available via an AST transformation
 *
 * @author Graeme Rocher
 * @since 1.4
 *
 */
@AstTransformer
public class GormValidationTransformer extends AbstractGrailsArtefactTransformer{

    public static final String HAS_ERRORS_METHOD = "hasErrors";
    private static final java.util.List<String> EXCLUDES = new ArrayList<String>() {{
       add("setErrors"); add("getErrors"); add(HAS_ERRORS_METHOD);
    }};

    @Override
    protected boolean requiresStaticLookupMethod() {
        return true;
    }

    @Override
    public String getArtefactType() {
        return DomainClassArtefactHandler.TYPE;
    }

    @Override
    public Class getInstanceImplementation() {
        return GormValidationApi.class;
    }

    @Override
    public Class getStaticImplementation() {
        return null;  // no static API
    }

    public boolean shouldInject(URL url) {
        return GrailsResourceUtils.isDomainClass(url);
    }

    @Override
    protected boolean isCandidateInstanceMethod(MethodNode declaredMethod) {
        return !EXCLUDES.contains(declaredMethod.getName()) && GrailsASTUtils.isCandidateInstanceMethod(declaredMethod);
    }

    @Override
    protected void performInjectionInternal(String apiInstanceProperty, SourceUnit source, ClassNode classNode) {
        final PropertyNode errorsProperty = classNode.getProperty(GrailsDomainClassProperty.ERRORS);
        if(errorsProperty == null) {

            addErrorsProperty(classNode);
        }
    }

    private void addErrorsProperty(ClassNode classNode) {
        final ArgumentListExpression errorsConstructorArgs = new ArgumentListExpression();
        errorsConstructorArgs.addExpression(THIS_EXPRESSION)
                             .addExpression(new ConstantExpression(classNode.getName()));
        final ConstructorCallExpression emptyErrorsConstructorCall = new ConstructorCallExpression(new ClassNode(BeanPropertyBindingResult.class), errorsConstructorArgs);
        classNode.addProperty(GrailsDomainClassProperty.ERRORS, Modifier.PUBLIC, new ClassNode(Errors.class), emptyErrorsConstructorCall, null, null);

        final BlockStatement methodBody = new BlockStatement();
        methodBody.addStatement(new ExpressionStatement(new MethodCallExpression(new VariableExpression(GrailsDomainClassProperty.ERRORS), HAS_ERRORS_METHOD, ZERO_ARGS)));
        classNode.addMethod(new MethodNode(HAS_ERRORS_METHOD, Modifier.PUBLIC, new ClassNode(Boolean.class), ZERO_PARAMETERS,null, methodBody));
    }
}
