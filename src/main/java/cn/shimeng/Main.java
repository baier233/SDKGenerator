package cn.shimeng;

import javafx.util.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Scanner;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Main {
    private static final String SDK_HEADER = "##pragma once\n\ninclude <SDK.hpp>\n";
    private static final String CPP_INCLUDE_TEMPLATE = "#include \"%s.hpp\"\n";

    public static void main(String[] args) throws IOException {
/*        if (args.length != 1) {
            System.err.println("Usage: java JarToCppConverter <path-to-jar>");
            return;
        }*/
        Scanner input = new Scanner(System.in);

        System.out.println("Enter the path of the JAR file: ");
        String path = input.nextLine();
        System.out.println("jar start path:");
        String gameStartPath = input.nextLine();
//        Path jarPath = Paths.get("D:\\Code\\GradleMCP-1.8.9-main\\build\\libs\\input.jar");
        Path jarPath = Paths.get(path);
        long stime = System.currentTimeMillis();
        input.close();
        if (!Files.exists(jarPath)) {
            System.err.println("JAR file not found: " + jarPath);
            return;
        }

        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith(gameStartPath) && entry.getName().endsWith(".class")) {
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        ClassReader classReader = new ClassReader(is);
                        ClassNode classNode = new ClassNode();
                        classReader.accept(classNode, 0);

                        String className = classNode.name.replace('/', '.');
                        String outputPath = className.replace('.', '/');
                        System.out.println("Process:" + className);
                        generateCppFiles(outputDir, classNode, outputPath);
                    }
                }
            }
        }
        long etime = System.currentTimeMillis();
        System.out.printf("%d ms.", (etime - stime));
    }

    private static void generateCppFiles(File outputDir, ClassNode classNode, String outputPath) throws IOException {
        File headerFile = new File(outputDir, outputPath + ".h");
        File sourceFile = new File(outputDir, outputPath + ".cpp");

        headerFile.getParentFile().mkdirs();
        sourceFile.getParentFile().mkdirs();

        try (PrintWriter headerWriter = new PrintWriter(headerFile);
             PrintWriter sourceWriter = new PrintWriter(sourceFile)) {


            headerWriter.println(SDK_HEADER);
            headerWriter.printf("BEGIN_KLASS_DEF(%s, return SRGParser::get().getObfuscatedClassName(\"%s\"))\n",
                    classNode.name.substring(classNode.name.lastIndexOf('/') + 1), classNode.name);

            for (FieldNode field : classNode.fields) {
                boolean isStatic = (field.access & Opcodes.ACC_STATIC) != 0;
                String lambdaFuncBody =
                        String.format("return SRGParser::get().getObfuscatedFieldName(SRGParser::get().getObfuscatedClassName(\"%s\"), \"%s\")",
                        classNode.name, field.name);

                headerWriter.printf("JNI::Field<%s, %s ,DECLARE_NAME(\n" +
                                "%s\n" +
                                ")> %s{ *this};",
                        parseDesc(field.desc), isStatic ? "JNI::STATIC" : "JNI::NOT_STATIC",lambdaFuncBody, field.name);

                headerWriter.printf("/* %s */\n\n",field.desc);
            }

            for (MethodNode method : classNode.methods) {
                if ("<clinit>".equals(method.name) || "<init>".equals(method.name)){
                    continue;
                }
                if (method.name.contains("lambda")){
                    continue;
                }
                boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
                String lambdaFuncBody =
                        String.format("return SRGParser::get().getObfuscatedMethodName(\"%s\", \"%s\", \"%s\").first",
                                classNode.name, method.name, method.desc);

                Pair<String,String> returnValue = parseDescriptor(method.desc);

                headerWriter.printf("JNI::Method<%s, %s, DECLARE_NAME(\n" +
                                "%s\n" +
                                 ")%s> %s{ *this};\n\n",
                        returnValue.getValue(), isStatic ? "JNI::STATIC" : "JNI::NOT_STATIC",lambdaFuncBody,returnValue.getKey(), method.name);
            }

            headerWriter.println("END_KLASS_DEF();");

            sourceWriter.printf(CPP_INCLUDE_TEMPLATE, classNode.name);
        }
    }
    public static Pair<String,String> parseDescriptor(String descriptor) {
        String parameters = descriptor.substring(1, descriptor.indexOf(')'));
        String returnType = descriptor.substring(descriptor.indexOf(')') + 1);

        String param = parseTypes(parameters);

        String returnTypes = parseDesc(returnType);

        return new Pair<>(param,returnTypes);
    }

    private static String parseTypes(String types) {
        if (types == null || types.isEmpty()) return "";
        StringBuilder parmaBuilder = new StringBuilder();
        while (!types.isEmpty()) {
            String type;
            if (types.startsWith("L")) {
                int end = types.indexOf(';') + 1;
                type = types.substring(0, end);
                types = types.substring(end);
            } else {
                type = types.substring(0, 1);
                types = types.substring(1);
            }
            parmaBuilder.append(",").append(parseDesc(type));
        }
        return parmaBuilder.toString();
    }
    private static String parseMethodSignature(String signature) {
        int startIndex = signature.indexOf('(');
        int endIndex = signature.indexOf(')');

        String paramTypes = signature.substring(startIndex + 1, endIndex);
        String[] params = paramTypes.split(";");
        StringBuilder paramTypesBuilder = new StringBuilder();
        //(Ljava/lang/ClasskLjava/lang/Class;III)I
        for (String param : params) {
            paramTypesBuilder.append(parseDesc(param + ";")).append(", ");
        }
        //System.out.println(paramTypesBuilder);
        String returnType = parseDesc(signature.substring(endIndex + 1));

        return String.format(returnType);
    }
    private static String parseDesc(String desc) {
        if (desc== null || desc.length() == 0) return "";
        if (desc.startsWith("[")) {
            return "JNI::Array<" + parseDesc(desc.substring(1)) + ">";
        } else if (desc.startsWith("L") && desc.endsWith(";")) {
            return desc.substring(1, desc.length() - 1).substring(desc.lastIndexOf('/'));
        } else {
            switch (desc.charAt(0)) {
                case 'B':
                    return "jbyte";
                case 'C':
                    return "jchar";
                case 'D':
                    return "jdouble";
                case 'F':
                    return "jfloat";
                case 'I':
                    return "jint";
                case 'J':
                    return "jlong";
                case 'S':
                    return "jshort";
                case 'Z':
                    return "jboolean";
                case 'V':
                    return "void";
                default:
                    return desc;
            }
        }
    }
}