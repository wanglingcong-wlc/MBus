package com.wlc.mbusprocess;

import com.google.auto.service.AutoService;
import com.wlc.Constants;
import com.wlc.mbuslibs.MBus;
import com.wlc.mbuslibs.ThreadMode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;


/**
 * Parameterizable：表示混合类型的元素（不仅只有一种类型的Element)
 * TypeParameterElement：带有泛型参数的类、接口、方法或者构造器。
 * VariableElement：表示字段、常量、方法或构造函数。参数、局部变量、资源变量或异常参数。
 * QualifiedNameable：具有限定名称的元素
 * ExecutableElement：表示类或接口的方法、构造函数或初始化器（静态或实例），包括注释类型元素。
 * TypeElement :表示类和接口
 * PackageElement：表示包
 */

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.wlc.mbuslibs.MBus")
@SupportedOptions(value = {"MROUTER_MODULE_NAME", "MBUS_USE_INDEX"})
public class MBusProcessor extends AbstractProcessor {
  public static final String OPTION_MBUS_MODULE_NAME = "MBUS_MODULE_NAME";
  public static final String OPTION_MBUS_USE_INDEX = "MBUS_USE_INDEX";
  public static final String MBUS_INDEX = ".MBusIndex$";

  /**
   * Found subscriber methods for a class (without superclasses).
   */
  private final HashMap<TypeElement, List<ExecutableElement>> methodsByClass = new HashMap<>();
  private final Set<TypeElement> classesToSkip = new HashSet<>();

  private boolean writerRoundDone;
  private int round;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
    Messager messager = processingEnv.getMessager();
    try {
      boolean useindex = Boolean.parseBoolean(processingEnv.getOptions().get(OPTION_MBUS_USE_INDEX));

      if (!useindex) {
        return true;
      }

      String module = processingEnv.getOptions().get(OPTION_MBUS_MODULE_NAME);
      if (module == null) {
        messager.printMessage(Diagnostic.Kind.ERROR, "No option " + OPTION_MBUS_MODULE_NAME +
            " passed to annotation processor");
        return false;
      }
      String indexPackage = Constants.MBUS_INDEX_HEAD;

      round++;
      if (env.processingOver()) {
        if (!annotations.isEmpty()) {
          messager.printMessage(Diagnostic.Kind.ERROR,
              "Unexpected processing state: annotations still available after processing over");
          return false;
        }
      }
      if (annotations.isEmpty()) {
        return false;
      }

      if (writerRoundDone) {
        messager.printMessage(Diagnostic.Kind.ERROR,
            "Unexpected processing state: annotations still available after writing.");
      }
      collectSubscribers(annotations, env, messager);
      checkForSubscribersToSkip(messager, indexPackage);

      if (!methodsByClass.isEmpty()) {
        createInfoIndexFile(indexPackage + MBUS_INDEX + module);
      } else {
        messager.printMessage(Diagnostic.Kind.WARNING, "No @MBus annotations found");
      }
      writerRoundDone = true;
    } catch (RuntimeException e) {
      // IntelliJ does not handle exceptions nicely, so log and print a message
      e.printStackTrace();
      messager.printMessage(Diagnostic.Kind.ERROR, "Unexpected error in mBusAnnotationProcessor: " + e);
    }
    return true;
  }

  private void collectSubscribers(Set<? extends TypeElement> annotations, RoundEnvironment env, Messager messager) {
    for (TypeElement annotation : annotations) {
      Set<? extends Element> elements = env.getElementsAnnotatedWith(annotation);
      for (Element element : elements) {
        if (element instanceof ExecutableElement) {
          ExecutableElement method = (ExecutableElement) element;
          if (checkHasNoErrors(method, messager)) {
            TypeElement classElement = (TypeElement) method.getEnclosingElement();//每个有mbus的类
            List<ExecutableElement> list = methodsByClass.get(classElement);
            if (list == null) {
              list = new ArrayList<>();
            }
            list.add(method);
            methodsByClass.put(classElement, list);
          }
        } else {
          messager.printMessage(Diagnostic.Kind.ERROR, "@MBus is only valid for methods", element);
        }
      }
    }
  }

  private boolean checkHasNoErrors(ExecutableElement element, Messager messager) {
    if (element.getModifiers().contains(Modifier.STATIC)) {
      messager.printMessage(Diagnostic.Kind.ERROR, "Subscriber method must not be static", element);
      return false;
    }

    if (!element.getModifiers().contains(Modifier.PUBLIC)) {
      messager.printMessage(Diagnostic.Kind.ERROR, "Subscriber method must be public", element);
      return false;
    }

    List<? extends VariableElement> parameters = ((ExecutableElement) element).getParameters();
    if (parameters.size() > 1) {
      messager.printMessage(Diagnostic.Kind.ERROR, "Subscriber method must have exactly 1 parameter", element);
      return false;
    }
    return true;
  }

  /**
   * Subscriber classes should be skipped if their class or any involved event class are not visible to the index.
   */
  private void checkForSubscribersToSkip(Messager messager, String myPackage) {
    for (TypeElement skipCandidate : methodsByClass.keySet()) {
      TypeElement subscriberClass = skipCandidate;
      while (subscriberClass != null) {
        if (!isVisible(myPackage, subscriberClass)) {
          boolean added = classesToSkip.add(skipCandidate);
          if (added) {
            String msg;
            if (subscriberClass.equals(skipCandidate)) {
              msg = "Falling back to reflection because class is not public";
            } else {
              msg = "Falling back to reflection because " + skipCandidate +
                  " has a non-public super class";
            }
            messager.printMessage(Diagnostic.Kind.NOTE, msg, subscriberClass);
          }
          break;
        }
        List<ExecutableElement> methods = methodsByClass.get(subscriberClass);
        if (methods != null) {
          for (ExecutableElement method : methods) {
            String skipReason = null;
            List paramslist = method.getParameters();
            if (paramslist == null || paramslist.isEmpty()) {
              continue;
            }

            VariableElement param = method.getParameters().get(0);
            TypeMirror typeMirror = getParamTypeMirror(param, messager);
            if (!(typeMirror instanceof DeclaredType) ||
                !(((DeclaredType) typeMirror).asElement() instanceof TypeElement)) {
              skipReason = "event type cannot be processed";
            }
            if (skipReason == null) {
              TypeElement eventTypeElement = (TypeElement) ((DeclaredType) typeMirror).asElement();
              if (!isVisible(myPackage, eventTypeElement)) {
                skipReason = "event type is not public";
              }
            }
            if (skipReason != null) {
              boolean added = classesToSkip.add(skipCandidate);
              if (added) {
                String msg = "Falling back to reflection because " + skipReason;
                if (!subscriberClass.equals(skipCandidate)) {
                  msg += " (found in super class for " + skipCandidate + ")";
                }
                messager.printMessage(Diagnostic.Kind.NOTE, msg, param);
              }
              break;
            }
          }
        }
        subscriberClass = getSuperclass(subscriberClass);
      }
    }
  }

  private TypeMirror getParamTypeMirror(VariableElement param, Messager messager) {
    TypeMirror typeMirror = param.asType();
    // Check for generic type
    if (typeMirror instanceof TypeVariable) {
      TypeMirror upperBound = ((TypeVariable) typeMirror).getUpperBound();
      if (upperBound instanceof DeclaredType) {
        if (messager != null) {
          messager.printMessage(Diagnostic.Kind.NOTE, "Using upper bound type " + upperBound +
              " for generic parameter", param);
        }
        typeMirror = upperBound;
      }
    }
    return typeMirror;
  }

  private TypeElement getSuperclass(TypeElement type) {
    if (type.getSuperclass().getKind() == TypeKind.DECLARED) {
      TypeElement superclass = (TypeElement) processingEnv.getTypeUtils().asElement(type.getSuperclass());
      String name = superclass.getQualifiedName().toString();
      if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android.")|| name.startsWith("androidx.")) {
        // Skip system classes, this just degrades performance
        return null;
      } else {
        return superclass;
      }
    } else {
      return null;
    }
  }

  private String getClassString(TypeElement typeElement, String myPackage) {
    PackageElement packageElement = getPackageElement(typeElement);
    String packageString = packageElement.getQualifiedName().toString();
    String className = typeElement.getQualifiedName().toString();
    if (packageString != null && !packageString.isEmpty()) {
      if (packageString.equals(myPackage)) {
        className = cutPackage(myPackage, className);
      }
    }
    return className;
  }

  private String cutPackage(String paket, String className) {
    if (className.startsWith(paket + '.')) {
      // Don't use TypeElement.getSimpleName, it doesn't work for us with inner classes
      return className.substring(paket.length() + 1);
    } else {
      // Paranoia
      throw new IllegalStateException("Mismatching " + paket + " vs. " + className);
    }
  }

  private PackageElement getPackageElement(TypeElement subscriberClass) {
    Element candidate = subscriberClass.getEnclosingElement();
    while (!(candidate instanceof PackageElement)) {
      candidate = candidate.getEnclosingElement();
    }
    return (PackageElement) candidate;
  }

  private void writeCreateSubscriberMethods(BufferedWriter writer, List<ExecutableElement> methods,
                                            String callPrefix, String myPackage) throws IOException {
    for (ExecutableElement method : methods) {
      List<? extends VariableElement> parameters = method.getParameters();
      String paramClass = "";
      String paramHeadStr = "";
      if (parameters == null || parameters.isEmpty()) {
        paramClass = "null";
      } else {
        TypeMirror paramType = getParamTypeMirror(parameters.get(0), null);
        TypeElement paramElement = (TypeElement) processingEnv.getTypeUtils().asElement(paramType);
        paramHeadStr = getClassString(paramElement, myPackage);
        paramClass = paramHeadStr + ".class";
      }
      String methodName = method.getSimpleName().toString();

      MBus subscribe = method.getAnnotation(MBus.class);
      String eventClass = "\"" + subscribe.type() + "\"";

      //processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "qqqqqqqqqqqqqqeventclass/" + eventClass + "/" + paramClass);

      if (eventClass == null || "\"\"".equals(eventClass)) {
        if ("null".equals(paramClass)) {
          continue;
        } else {
          eventClass = "\"" + paramHeadStr + "\"";
        }
      }

      List<String> parts = new ArrayList<>();
      parts.add(callPrefix + "(\"" + methodName + "\",");
      String lineEnd = "),";
      if (!subscribe.isSticky()) {//不是粘性事件
        if (subscribe.threadMode() == ThreadMode.THREADNOW) {
          parts.add(eventClass + ",");
          parts.add(paramClass + lineEnd);
        } else {
          parts.add(eventClass + ",");
          parts.add(paramClass + ",");
          parts.add("ThreadMode." + subscribe.threadMode().name() + lineEnd);
        }
      } else {
        parts.add(eventClass + ",");
        parts.add(paramClass + ",");
        parts.add("ThreadMode." + subscribe.threadMode().name() + ",");
        parts.add(subscribe.isSticky() + lineEnd);
      }
      writeLine(writer, 3, parts.toArray(new String[parts.size()]));
    }
  }

  private void createInfoIndexFile(String index) {
    BufferedWriter writer = null;
    try {
      JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(index);
      int period = index.lastIndexOf('.');
      String myPackage = period > 0 ? index.substring(0, period) : null;
      String clazz = index.substring(period + 1);
      writer = new BufferedWriter(sourceFile.openWriter());
      if (myPackage != null) {
        writer.write("package " + myPackage + ";\n\n");
      }
      writer.write("import com.wlc.mbuslibs.ISubscriberInfo;\n");
      writer.write("import com.wlc.mbuslibs.MethodInfo;\n");
      writer.write("import com.wlc.mbuslibs.SubscriberInfo;\n");
      writer.write("import com.wlc.mbuslibs.SubscriberInfoIndex;\n\n");
      writer.write("import com.wlc.mbuslibs.ThreadMode;\n\n");
      writer.write("import java.util.HashMap;\n");
      writer.write("import java.util.Map;\n\n");
      writer.write("/** This class is generated by MBus, do not edit. */\n");
      writer.write("public class " + clazz + " implements SubscriberInfoIndex {\n");
      writer.write("    private static final Map<Class<?>, ISubscriberInfo> SUBSCRIBER_INDEX;\n\n");
      writer.write("    static {\n");
      writer.write("        SUBSCRIBER_INDEX = new HashMap<Class<?>, ISubscriberInfo>();\n\n");
      writeIndexLines(writer, myPackage);
      writer.write("    }\n\n");
      writer.write("    private static void putIndex(ISubscriberInfo info) {\n");
      writer.write("        SUBSCRIBER_INDEX.put(info.getSubscriberClass(), info);\n");
      writer.write("    }\n\n");
      writer.write("    @Override\n");
      writer.write("    public ISubscriberInfo getSubscriberInfo(Class<?> subscriberClass) {\n");
      writer.write("        ISubscriberInfo info = SUBSCRIBER_INDEX.get(subscriberClass);\n");
      writer.write("        if (info != null) {\n");
      writer.write("            return info;\n");
      writer.write("        } else {\n");
      writer.write("            return null;\n");
      writer.write("        }\n");
      writer.write("    }\n");
      writer.write("}\n");
    } catch (IOException e) {
      throw new RuntimeException("Could not write source for " + index, e);
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException e) {
          //Silent
        }
      }
    }
  }

  private void writeIndexLines(BufferedWriter writer, String myPackage) throws IOException {
    for (TypeElement subscriberTypeElement : methodsByClass.keySet()) {
      if (classesToSkip.contains(subscriberTypeElement)) {
        continue;
      }

      String subscriberClass = getClassString(subscriberTypeElement, myPackage);
      if (isVisible(myPackage, subscriberTypeElement)) {
        writeLine(writer, 2,
            "putIndex(new SubscriberInfo(" + subscriberClass + ".class,",
            "true,", "new MethodInfo[] {");
        List<ExecutableElement> methods = methodsByClass.get(subscriberTypeElement);
        writeCreateSubscriberMethods(writer, methods, "new MethodInfo", myPackage);
        writer.write("        }));\n\n");
      } else {
        writer.write("        // Subscriber not visible to index: " + subscriberClass + "\n");
      }
    }
  }

  private boolean isVisible(String myPackage, TypeElement typeElement) {
    Set<Modifier> modifiers = typeElement.getModifiers();
    boolean visible;
    if (modifiers.contains(Modifier.PUBLIC)) {
      visible = true;
    } else if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.PROTECTED)) {
      visible = false;
    } else {
      String subscriberPackage = getPackageElement(typeElement).getQualifiedName().toString();
      if (myPackage == null) {
        visible = subscriberPackage.length() == 0;
      } else {
        visible = myPackage.equals(subscriberPackage);
      }
    }
    return visible;
  }

  private void writeLine(BufferedWriter writer, int indentLevel, String... parts) throws IOException {
    writeLine(writer, indentLevel, 2, parts);
  }

  private void writeLine(BufferedWriter writer, int indentLevel, int indentLevelIncrease, String... parts)
      throws IOException {
    writeIndent(writer, indentLevel);
    int len = indentLevel * 4;
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      if (i != 0) {
        if (len + part.length() > 118) {
          writer.write("\n");
          if (indentLevel < 12) {
            indentLevel += indentLevelIncrease;
          }
          writeIndent(writer, indentLevel);
          len = indentLevel * 4;
        } else {
          writer.write(" ");
        }
      }
      writer.write(part);
      len += part.length();
    }
    writer.write("\n");
  }

  private void writeIndent(BufferedWriter writer, int indentLevel) throws IOException {
    for (int i = 0; i < indentLevel; i++) {
      writer.write("    ");
    }
  }
}

