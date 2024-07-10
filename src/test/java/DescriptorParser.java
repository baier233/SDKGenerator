import javafx.util.Pair;

public class DescriptorParser {
	public static void main(String[] args) {
		String descriptor = "()I";
		Pair<String,String> returnValue = parseDescriptor(descriptor);
		System.out.println("Param :" + returnValue.getKey()+"\nReturn :"+returnValue.getValue());
	}

	public static Pair<String,String> parseDescriptor(String descriptor) {
		String parameters = descriptor.substring(1, descriptor.indexOf(')'));
		String returnType = descriptor.substring(descriptor.indexOf(')') + 1);

		String param = parseTypes(parameters);

		String returnTypes = parseDesc(returnType);

		return new Pair<String,String>(param,returnTypes);
	}

	private static String parseTypes(String types) {
		if (types == null || types.isEmpty()) return null;
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
			parmaBuilder.append(parseDesc(type) + ",");
		}
		return parmaBuilder.substring(0,parmaBuilder.length()-1);
	}

	private static String parseDesc(String desc) {
		if (desc== null || desc.length() == 0) return null;
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
