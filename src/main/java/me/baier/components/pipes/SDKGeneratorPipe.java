package me.baier.components.pipes;

import me.baier.ClassesResolver;
import me.baier.entity.NamePipe;
import me.baier.entity.Pair;
import me.baier.entity.Pipe;
import me.baier.entity.Tuple;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.zip.ZipOutputStream;

public class SDKGeneratorPipe extends NamePipe {
	private static final String SDK_HEADER = "#pragma once\n\n#include <SDK.hpp>\n";
	private static final String CPP_INCLUDE_TEMPLATE = "#include \"%s.hpp\"\n";
	public SDKGeneratorPipe(String preName) {
		super(preName);
	}

	@Override
	public void init(ClassesResolver blastObfuscate, ClassNode classNode) {

	}

	private final HashMap<String, HashSet<String>> cachedClassNodeMethod = new HashMap<>();

	private HashSet<String> analyzeExtendMethod(final ClassesResolver blastObfuscate, final ClassNode superClass) {
		final HashSet<String> ignoredMethod = new HashSet<>();
		if (superClass != null) {
			if (superClass.superName != null) ignoredMethod.addAll(analyzeExtendMethod(blastObfuscate, blastObfuscate.getNode(superClass.superName)));
			if (cachedClassNodeMethod.get(superClass.name) == null) {
				for (final var method : superClass.methods) {
					if ("<clinit>".equals(method.name) || "<init>".equals(method.name)) continue;
					ignoredMethod.add(method.name + method.desc);
				}
				cachedClassNodeMethod.put(superClass.name, ignoredMethod);
			} else {
				return cachedClassNodeMethod.get(superClass.name);
			}
		}
		return ignoredMethod;
	}

	HashMap<String,String> getImplementedMethods(ClassesResolver blastObfuscate, ClassNode classNode){
		HashMap<String,String> result = new HashMap<>();
		if (classNode == null) return result;
		if (classNode.interfaces.size() > 0 ){
			for (String className : classNode.interfaces.stream().toList()){
				//System.out.println("Class " + classNode.name + " implement " + className);
				result.putAll(getImplementedMethods(blastObfuscate,blastObfuscate.getNode(className)));
			}
		}
		for (MethodNode method : classNode.methods){
			String key = method.name+method.desc;
			if (result.getOrDefault(key,null) != null) continue;
			//System.out.println("Putting "+key +" in " + classNode.name );
			result.put(key,classNode.name);
		}
		return result;
	}

	@Override
	public void process(ClassesResolver blastObfuscate, ClassNode classNode) throws Exception {
		HashMap<String,String> pairList = getImplementedMethods(blastObfuscate,classNode);
		final var ignored = analyzeExtendMethod(blastObfuscate, blastObfuscate.getNode(classNode.superName));
		String outputPath = classNode.name.replace('.', '/');
		File headerFile = new File(blastObfuscate.getOutputDir(), outputPath + ".h");
		//File sourceFile = new File(blastObfuscate.getOutputDir(), outputPath + ".cpp");

		headerFile.getParentFile().mkdirs();
		//sourceFile.getParentFile().mkdirs();

		try (PrintWriter headerWriter = new PrintWriter(headerFile)) {


			headerWriter.println(SDK_HEADER);
			headerWriter.printf("BEGIN_KLASS_DEF(%s, return SRGParser::get().getObfuscatedClassName(\"%s\"))\n",
					classNode.name.substring(classNode.name.lastIndexOf('/') + 1), classNode.name);

			for (FieldNode field : classNode.fields) {
				boolean isStatic = (field.access & Opcodes.ACC_STATIC) != 0;
				String lambdaFuncBody =
						String.format("return SRGParser::get().getObfuscatedFieldName(\"%s\", \"%s\")",
								classNode.name, field.name);

				headerWriter.printf("JNI::Field<%s, %s ,DECLARE_NAME(\n" +
								"%s\n" +
								")> %s{ *this};",
						parseDesc(field.desc), isStatic ? "JNI::STATIC" : "JNI::NOT_STATIC",lambdaFuncBody, field.name);

				headerWriter.printf("/* %s */\n\n",field.desc);
			}
			int numConstructor = 0;
			for (MethodNode method : classNode.methods) {
				if ("<clinit>".equals(method.name)){
					continue;
				}
				String methodName = method.name;
				boolean isConstructor = false;
				if ("<init>".equals(method.name)){
					methodName = "constructor_" + Integer.toString(numConstructor++);
					isConstructor = true;
				}
				if (method.name.contains("lambda")){
					continue;
				}

				String nameClass = pairList.getOrDefault(method.name+method.desc,null);
				if (nameClass == null) {
					nameClass = classNode.name;
				}else if (!(Objects.equals(classNode.name, nameClass))){
					System.out.println("Replace "+classNode.name + " into "+ nameClass);
				}

				if (ignored.contains(method.name + method.desc)) continue;

				if (isConstructor){
					Pair<String,String> returnValue = parseDescriptor(method.desc);
					headerWriter.printf("JNI::ConstructorMethod<%s> %s {*this};",returnValue.getKey(),methodName);
				}else{

					boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
					String lambdaFuncBody =
							String.format("return SRGParser::get().getObfuscatedMethodName(\"%s\", \"%s\", \"%s\").first",
									nameClass, method.name, method.desc);

					Pair<String,String> returnValue = parseDescriptor(method.desc);

					headerWriter.printf("JNI::Method<%s, %s, DECLARE_NAME(\n" +
									"%s\n" +
									")%s> %s{ *this};\n\n",
							returnValue.getValue(), isStatic ? "JNI::STATIC" : "JNI::NOT_STATIC",lambdaFuncBody,returnValue.getKey(), method.name);
				}

			}

			headerWriter.println("END_KLASS_DEF();");

			//sourceWriter.printf(CPP_INCLUDE_TEMPLATE, classNode.name);
		}
	}

	@Override
	public void finish(ClassesResolver blastObfuscate, ZipOutputStream outputStream) throws IOException {

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
