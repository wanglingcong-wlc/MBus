package com.wlc.mrouteprocessor;

import com.google.auto.service.AutoService;
import com.wlc.Constants;
import com.wlc.mroute.MRoute;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
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
@SupportedAnnotationTypes("com.wlc.mroute.MRoute")
@SupportedOptions(value = {"MBUS_MODULE_NAME"})
public class MRouteProcessor extends AbstractProcessor {
  public static final String OPTION_MROUTE_INDEX = "MBUS_MODULE_NAME";
  public static final String INDEX_NAME = Constants.MROUTE_INDEX_HEAD + ".MRouteIndex$";

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
      String moduleName = processingEnv.getOptions().get(OPTION_MROUTE_INDEX);
      if (moduleName == null) {
        messager.printMessage(Diagnostic.Kind.ERROR, "No option " + OPTION_MROUTE_INDEX +
            " passed to annotation processor");
        return false;
      }
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

      if (annotations != null && !annotations.isEmpty()) {
        createInfoIndexFile(moduleName, annotations, env, messager);
      } else {
        messager.printMessage(Diagnostic.Kind.WARNING, "No @MRoute annotations found");
      }
      writerRoundDone = true;
    } catch (RuntimeException e) {
      e.printStackTrace();
      messager.printMessage(Diagnostic.Kind.ERROR, "Unexpected error in mBusAnnotationProcessor: " + e);
    }
    return true;
  }


  private String getClassString(TypeElement typeElement, String myPackage) {
    PackageElement packageElement = getPackageElement(typeElement);
    String packageString = packageElement.getQualifiedName().toString();
    String className = typeElement.getQualifiedName().toString();
    if (packageString != null && !packageString.isEmpty()) {
      if (packageString.equals(myPackage)) {
        className = cutPackage(myPackage, className);
      } else if (packageString.equals("java.lang")) {
        className = typeElement.getSimpleName().toString();
      }
    }
    return className;
  }

  private String cutPackage(String paket, String className) {
    if (className.startsWith(paket + '.')) {
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

  private void createInfoIndexFile(String moduleName, Set<? extends TypeElement> annotations, RoundEnvironment env, Messager messager) {
    BufferedWriter writer = null;
    try {
      String fullName = INDEX_NAME + moduleName;
      JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(fullName);
      int period = fullName.lastIndexOf('.');
      String myPackage = period > 0 ? fullName.substring(0, period) : null;
      String clazz = fullName.substring(period + 1);
      writer = new BufferedWriter(sourceFile.openWriter());
      if (myPackage != null) {
        writer.write("package " + myPackage + ";\n\n");
      }
      writer.write("import com.wlc.mroute.IRouteIndex;\n");
      writer.write("import java.util.Map;\n\n");
      writer.write("/** This class is generated by MRoute, do not edit. */\n");
      writer.write("public class " + clazz + " implements IRouteIndex {\n");
      writer.write("    public void getRouteInfos(Map<String, Class<?>> map){\n");
      writeIndexLines(writer, myPackage, annotations, env, messager);
      writer.write("    }\n");
      writer.write("}\n");
    } catch (IOException e) {
      throw new RuntimeException("Could not write source for " + INDEX_NAME, e);
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

  private void writeIndexLines(BufferedWriter writer, String myPackage, Set<? extends TypeElement> annotations, RoundEnvironment env, Messager messager) throws IOException {
    for (TypeElement e : annotations) {
      Set<? extends Element> elements = env.getElementsAnnotatedWith(e);//所有包含注解的元素
      for (Element element : elements) {
        if (element instanceof TypeElement) {
          String subscriberClass = getClassString((TypeElement) element, myPackage);
          if (isVisible(myPackage, (TypeElement) element)) {
            MRoute mRoute = element.getAnnotation(MRoute.class);
            writeLine(writer, 2,
                "map.put(\"" + mRoute.path() + "\", " + subscriberClass + ".class);");
          } else {
            writer.write("        // Subscriber not visible to index: " + subscriberClass + "\n");
          }
        }
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

