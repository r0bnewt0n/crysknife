package org.treblereel.gwt.crysknife.generator.definition;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.google.auto.common.MoreElements;
import org.treblereel.gwt.crysknife.generator.BeanIOCGenerator;
import org.treblereel.gwt.crysknife.generator.IOCGenerator;
import org.treblereel.gwt.crysknife.generator.api.ClassBuilder;
import org.treblereel.gwt.crysknife.generator.context.IOCContext;
import org.treblereel.gwt.crysknife.generator.point.ConstructorPoint;
import org.treblereel.gwt.crysknife.generator.point.FieldPoint;
import org.treblereel.gwt.crysknife.util.Utils;

/**
 * @author Dmitrii Tikhomirov
 * Created by treblereel 2/20/19
 */
public class BeanDefinition extends Definition {

    protected List<FieldPoint> fieldInjectionPoints = new LinkedList<>();
    protected ConstructorPoint constructorInjectionPoint;
    protected String className;
    protected String classFactoryName;
    protected String packageName;
    protected String qualifiedName;
    protected TypeElement element;
    protected Set<DeclaredType> types = new HashSet<>();

    protected BeanDefinition(TypeElement element) {
        this.element = element;
        this.className = element.getSimpleName().toString();
        this.classFactoryName = Utils.getFactoryClassName(element);
        this.packageName = Utils.getPackageName(element);
        this.qualifiedName = Utils.getQualifiedName(element);
    }

    public static BeanDefinition of(TypeElement element, IOCContext context) {
        if (context.getBeans().containsKey(element)) {
            return context.getBeans().get(element);
        }
        return new BeanDefinitionBuilder(element, context).build();
    }

    public void addExecutableDefinition(IOCGenerator generator, ExecutableDefinition definition) {
        if (executableDefinitions.containsKey(generator)) {
            executableDefinitions.get(generator).add(definition);
        } else {
            executableDefinitions.put(generator, new HashSet());
            executableDefinitions.get(generator).add(definition);
        }
    }

    public void generateDecorators(ClassBuilder builder) {
        super.generateDecorators(builder);

        executableDefinitions.forEach((gen, defs) -> defs.forEach(def -> {
            gen.generateBeanFactory(builder, def);
        }));
    }

    public void setGenerator(IOCGenerator iocGenerator) {
        if (iocGenerator == null) {
            throw new Error("Unable to set generator for " + this.toString());
        } else {
            this.generator = Optional.of(iocGenerator);
        }
    }

    public Expression generateBeanCall(IOCContext context, ClassBuilder builder, FieldPoint fieldPoint) {
        if (generator.isPresent()) {
            IOCGenerator iocGenerator = generator.get();
            return ((BeanIOCGenerator) iocGenerator).generateBeanCall(builder, fieldPoint, this);
        } else {
            if (maybeProcessableAsCommonBean(fieldPoint.getType())) {
                //we ll use direct object construction, lets ignore factory creation
                context.getBlacklist().add(fieldPoint.getType().getQualifiedName().toString());
                context.getGenerationContext().getProcessingEnvironment()
                        .getMessager()
                        .printMessage(Diagnostic.Kind.WARNING, String.format("Unable to determine bean type for %s, it will be processed as common bean ",
                                                                             fieldPoint.getType().getQualifiedName().toString()));
                return new ObjectCreationExpr().setType(new ClassOrInterfaceType()
                                                                .setName(fieldPoint.getType().getQualifiedName().toString()));
            }
            throw new Error("Unable to find generator for " + getQualifiedName());
        }
    }

    private boolean maybeProcessableAsCommonBean(TypeElement candidate) {
        if (candidate.getKind().isClass() && !candidate.getModifiers().contains(Modifier.ABSTRACT)
                && candidate.getModifiers().contains(Modifier.PUBLIC)) {
            long count = candidate.getEnclosedElements().stream().filter(elm -> {
                return elm.getKind().equals(ElementKind.CONSTRUCTOR);
            }).filter(elm -> MoreElements.asExecutable(elm).getParameters().size() == 0)
                    .filter(elm -> elm.getModifiers().contains(Modifier.PUBLIC)).count();
            if (count == 1) {
                return true;
            }
        }
        return false;
    }

    public void processInjections(IOCContext context) {
        Elements elements = context.getGenerationContext().getElements();
        elements.getAllMembers(element).forEach(mem -> {
            if (mem.getAnnotation(Inject.class) != null && (mem.getKind().equals(ElementKind.CONSTRUCTOR)
                    || mem.getKind().equals(ElementKind.FIELD))) {
                if (mem.getModifiers().contains(Modifier.STATIC)) {
                    context.getGenerationContext()
                            .getProcessingEnvironment()
                            .getMessager()
                            .printMessage(Diagnostic.Kind.ERROR,
                                          String.format("Field [%s] in [%s] must not be STATIC \n", mem, getQualifiedName()));
                    throw new Error();
                }
                if (mem.getKind().equals(ElementKind.CONSTRUCTOR)) {
                    ExecutableElement elms = MoreElements.asExecutable(mem);
                    constructorInjectionPoint = new ConstructorPoint(element.getQualifiedName().toString(), element);

                    for (int i = 0; i < elms.getParameters().size(); i++) {
                        FieldPoint field = parseField(elms.getParameters().get(i), context);
                        constructorInjectionPoint.addArgument(field);
                    }
                } else if (mem.getKind().equals(ElementKind.FIELD)) {
                    FieldPoint fiend = parseField(mem, context);
                    fieldInjectionPoints.add(fiend);
                }
            }
        });
    }

    //TODO refactoring needed here
    private FieldPoint parseField(Element type, IOCContext context) {
        FieldPoint field = FieldPoint.of(MoreElements.asVariable(type));
        if (context.getQualifiers().containsKey(field.getType())) {
            BeanDefinition bean = null;
            if (field.isNamed()) {
                bean = context.getQualifiers().get(field.getType()).get(field.getNamed());
            } else {
                for (AnnotationMirror mirror : context.getGenerationContext()
                        .getProcessingEnvironment()
                        .getElementUtils()
                        .getAllAnnotationMirrors(type)) {
                    bean = context.getQualifiers().get(field.getType()).get(mirror.getAnnotationType().toString());
                }
            }
            if (bean != null) {
                dependsOn.add(bean);
                field.setType(bean.getType());
            }
        } else if (!field.isNamed()) {
            BeanDefinition fieldBeanDefinition = context.getBeanDefinitionOrCreateAndReturn(field.getType());
            dependsOn.add(fieldBeanDefinition);
        }
        return field;
    }

    @Override
    public String toString() {
        return "BeanDefinition {" +
                " generator = [ " + (generator.isPresent() ? generator.get().getClass().getCanonicalName() : "") + " ]" +
                " ] , element= [" + element +
                " ] , dependsOn= [ " + dependsOn.stream().map(m -> Utils.getQualifiedName(m.element)).collect(Collectors.joining(", ")) +
                " ] , executables= [ " + executableDefinitions.values().stream().map(Object::toString).collect(Collectors.joining(", ")) +
                " ]}";
    }

    public Set<BeanDefinition> getDependsOn() {
        return dependsOn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Utils.getQualifiedName(element));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BeanDefinition that = (BeanDefinition) o;
        return Objects.equals(Utils.getQualifiedName(element), Utils.getQualifiedName(that.element));
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public Map<IOCGenerator, Set<ExecutableDefinition>> getExecutableDefinitions() {
        return executableDefinitions;
    }

    public String getClassFactoryName() {
        return classFactoryName;
    }

    public List<FieldPoint> getFieldInjectionPoints() {
        return fieldInjectionPoints;
    }

    public TypeElement getType() {
        return element;
    }

    public Set<DeclaredType> getDeclaredTypes() {
        return types;
    }

    public ConstructorPoint getConstructorInjectionPoint() {
        return constructorInjectionPoint;
    }

    public void setConstructorInjectionPoint(ConstructorPoint constructorInjectionPoint) {
        this.constructorInjectionPoint = constructorInjectionPoint;
    }

    private static class BeanDefinitionBuilder {

        private BeanDefinition beanDefinition;

        private Elements elements;

        private IOCContext context;

        BeanDefinitionBuilder(TypeElement element, IOCContext context) {
            this.context = context;
            this.beanDefinition = new BeanDefinition(element);
            this.elements = context.getGenerationContext().getElements();
        }

        public BeanDefinition build() {
            return beanDefinition;
        }
    }
}